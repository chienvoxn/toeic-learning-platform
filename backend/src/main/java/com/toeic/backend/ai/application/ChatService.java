package com.toeic.backend.ai.application;

import com.toeic.backend.ai.application.ChatContracts.*;
import com.toeic.backend.ai.infrastructure.*;
import com.toeic.backend.content.application.ContentAccessPolicy;
import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Điều phối quyền, quota, lịch sử và RAG; luôn gọi AI ngoài transaction dữ liệu. */
@Service
public class ChatService {
    private final ChatRepository store;
    private final RagClient rag;
    private final ContentAccessPolicy access;
    private final TransactionTemplate tx;
    private final int dailyLimit,minuteLimit;
    public ChatService(ChatRepository store,RagClient rag,ContentAccessPolicy access,TransactionTemplate tx,
                       @Value("${app.ai.daily-limit:30}") int dailyLimit,@Value("${app.ai.minute-limit:5}") int minuteLimit) {
        this.store=store;this.rag=rag;this.access=access;this.tx=tx;this.dailyLimit=dailyLimit;this.minuteLimit=minuteLimit;
    }
    private UUID actor(String permission) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !(auth.getPrincipal() instanceof SessionPrincipal principal)
                || auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals(permission)))
            throw new AccessDeniedException("AI permission required");
        return principal.userId();
    }
    public List<Conversation> list(int page) { return store.list(actor("ai:chat:self"),page); }
    public Conversation create() {
        UUID actor=actor("ai:chat:self"),id=UUID.randomUUID();
        return tx.execute(status->{
            store.jdbc().queryForList("select user_id from users where user_id=? for update",actor);
            if(store.jdbc().queryForObject("select count(*) from ai_conversations where user_id=?",Integer.class,actor)>=100)
                throw new ChatFailure(429,"AI_CONVERSATION_LIMIT","Đã đạt giới hạn 100 hội thoại.");
            store.jdbc().update("insert into ai_conversations(conversation_id,user_id,title) values(?,?,?)",id,actor,"Hội thoại mới");
            return store.list(actor,0).stream().filter(c->c.id().equals(id)).findFirst().orElseThrow();
        });
    }
    public List<Message> messages(UUID conversation,int page) {
        store.own(actor("ai:chat:self"),conversation);
        return store.messages(conversation,page).stream().map(m->{
            if(m.answer()==null) return m;
            var filtered=visible(m.answer());
            return new Message(m.id(),m.role(),filtered.answer(),m.createdAt(),filtered);
        }).toList();
    }
    private Answer visible(Answer answer) {
        Set<UUID> allowed=new HashSet<>(store.scope(access.canReadMemberContent()).stream().map(Evidence::documentId).toList());
        if(answer.citations().stream().anyMatch(c->!allowed.contains(c.documentId())))
            return new Answer("Nguồn của câu trả lời này không còn khả dụng.",false,ChatContracts.NO_SOURCE,List.of());
        return answer;
    }
    private record Reservation(Answer replay,List<History> history) {}
    public Answer ask(UUID conversation,UUID request,String question) {
        UUID actor=actor("ai:chat:self");
        var reservation=tx.execute(status->{
            // Serialize reservations per user, including quota and multiple conversations.
            store.jdbc().queryForList("select user_id from users where user_id=? for update",actor);
            store.own(actor,conversation);
            var existing=store.jdbc().queryForList("select * from ai_chat_requests where request_id=?",request);
            if(!existing.isEmpty()) {
                var row=existing.getFirst();
                if(!actor.equals(row.get("user_id")) || !conversation.equals(row.get("conversation_id")) || !question.equals(row.get("question")))
                    throw new ChatFailure(409,"AI_REQUEST_CONFLICT","Mã yêu cầu đã được dùng cho nội dung khác.");
                if("COMPLETED".equals(row.get("status"))) return new Reservation(store.decode((String)row.get("response_json")),List.of());
                throw new ChatFailure(409,"AI_REQUEST_RECORDED","Yêu cầu đã được ghi nhận. Hãy tải lại lịch sử hoặc gửi câu hỏi mới.");
            }
            if(store.jdbc().queryForObject("select count(*) from ai_chat_requests where conversation_id=? and status='PENDING' and created_at>now()-interval '2 minutes'",Integer.class,conversation)>0)
                throw new ChatFailure(409,"AI_BUSY","Hội thoại đang xử lý một câu hỏi.");
            int daily=store.jdbc().queryForObject("select count(*) from ai_chat_requests where user_id=? and created_at>now()-interval '24 hours'",Integer.class,actor);
            int minute=store.jdbc().queryForObject("select count(*) from ai_chat_requests where user_id=? and created_at>now()-interval '1 minute'",Integer.class,actor);
            if(daily>=dailyLimit || minute>=minuteLimit) throw new ChatFailure(429,"AI_QUOTA_EXCEEDED","Đã đạt giới hạn hỏi đáp. Vui lòng thử lại sau.");
            var history=store.messages(conversation,0).stream().filter(m->m.answer()==null || visible(m.answer()).grounded())
                    .map(m->new History(m.role(),m.content().substring(0,Math.min(4000,m.content().length())))).toList();
            history=history.subList(Math.max(0,history.size()-12),history.size());
            store.jdbc().update("insert into ai_chat_requests(request_id,conversation_id,user_id,question,status) values(?,?,?,?,'PENDING')",request,conversation,actor,question);
            return new Reservation(null,history);
        });
        if(reservation.replay()!=null) return visible(reservation.replay());
        try {
            var scope=store.scope(access.canReadMemberContent());
            if(scope.size()>500) throw new ChatFailure(503,"AI_SCOPE_LIMIT","Cần thu hẹp phạm vi học liệu trước khi hỏi đáp.");
            var remote=scope.isEmpty()?null:rag.ask(question,scope.stream().map(Evidence::documentId).toList(),reservation.history(),request);
            Answer answer=remote==null?new Answer(ChatContracts.NO_SOURCE,false,ChatContracts.NO_SOURCE,List.of()):validate(remote,scope);
            // Recheck visibility after the remote call, before returning or persisting generated text.
            answer=visible(answer);
            final Answer result=answer;
            tx.executeWithoutResult(status->{
                store.own(actor,conversation);
                UUID userMessage=UUID.randomUUID(),assistantMessage=UUID.randomUUID();
                store.jdbc().update("insert into ai_messages(message_id,conversation_id,role,content,created_at) values(?,?,'user',?,clock_timestamp())",userMessage,conversation,question);
                store.jdbc().update("insert into ai_messages(message_id,conversation_id,role,content,created_at) values(?,?,'assistant',?,clock_timestamp())",assistantMessage,conversation,result.answer());
                store.jdbc().update("insert into ai_citation_snapshots(message_id,response_json) values(?,?)",assistantMessage,store.encode(result));
                store.jdbc().update("update ai_conversations set title=case when title='Hội thoại mới' then ? else title end,updated_at=now() where conversation_id=?",question.substring(0,Math.min(80,question.length())),conversation);
                store.jdbc().update("update ai_chat_requests set status='COMPLETED',response_json=?,finished_at=now() where request_id=?",store.encode(result),request);
                var usage=remote==null?null:remote.usage();
                store.jdbc().update("insert into ai_request_logs(request_id,conversation_id,user_id,provider,model,prompt_tokens,completion_tokens,total_tokens,latency_ms,status,created_at) values(?,?,?,?,?,?,?,?,?,'COMPLETED',now())",
                        request,conversation,actor,usage==null?"none":usage.provider(),usage==null?"none":usage.chatModel(),usage==null?0:usage.inputTokens(),usage==null?0:usage.outputTokens(),usage==null?0:usage.totalTokens(),usage==null?0:usage.latencyMs());
            });
            return result;
        } catch(RuntimeException exception) {
            tx.executeWithoutResult(status->store.jdbc().update("update ai_chat_requests set status='FAILED',error_code=?,finished_at=now() where request_id=? and status='PENDING'",exception instanceof ChatFailure f?f.code:"AI_INTERNAL_ERROR",request));
            throw exception;
        }
    }
    public static Answer validate(RemoteAnswer response,List<Evidence> scope) {
        if(response==null || response.answer()==null || response.answer().isBlank() || response.answer().length()>16000 || response.citations()==null
                || response.citations().size()>10 || (response.grounded() && response.citations().isEmpty())
                || (!response.grounded() && (!response.citations().isEmpty() || !ChatContracts.NO_SOURCE.equals(response.warning()))))
            throw invalid();
        var sources=new HashMap<UUID,Evidence>(); scope.forEach(s->sources.put(s.documentId(),s));
        var citations=response.citations().stream().map(c->{
            var source=sources.get(c.documentId());
            if(source==null || c.chunkId()==null || c.title()==null || c.excerpt()==null || c.excerpt().length()>20000) throw invalid();
            return new Citation(c.documentId(),c.chunkId(),source.contentId(),source.contentVersionId(),c.title(),c.excerpt(),c.page());
        }).toList();
        return response.grounded()?new Answer(response.answer(),true,null,citations):new Answer(ChatContracts.NO_SOURCE,false,ChatContracts.NO_SOURCE,List.of());
    }
    private static ChatFailure invalid() { return new ChatFailure(502,"AI_INVALID_RESPONSE","AI trả về nguồn hoặc nội dung không hợp lệ."); }
    public void index(UUID content) {
        actor("ai:knowledge:manage");
        var source=store.source(content);
        rag.index(source.version(),source.title(),source.text());
        store.jdbc().update("insert into ai_indexed_versions(content_version_id,document_id) values(?,?) on conflict(content_version_id) do update set indexed_at=now()",source.version(),source.version());
    }
}
