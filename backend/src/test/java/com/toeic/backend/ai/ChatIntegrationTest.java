package com.toeic.backend.ai;

import com.toeic.backend.ai.application.*;
import com.toeic.backend.ai.application.ChatContracts.*;
import com.toeic.backend.ai.infrastructure.RagClient;
import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class ChatIntegrationTest {
    @Autowired ChatService chat;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired TransactionTemplate tx;
    @MockitoBean RagClient rag;
    UUID actor,other,content,version;
    @BeforeEach void setup() {
        actor=UUID.randomUUID();other=UUID.randomUUID();content=UUID.randomUUID();version=UUID.randomUUID();
        for(UUID id:List.of(actor,other)) jdbc.update("insert into users(user_id,email,password_hash,full_name,status,created_at,updated_at) values(?,?,?,'RAG test','ACTIVE',now(),now())",id,id+"@rag.invalid","fixture-only-no-login");
        jdbc.update("insert into learning_contents(content_id,category_id,author_id,title,status,visibility,created_at,updated_at) values(?,(select min(category_id) from content_categories),?,'Grammar','PUBLISHED','PUBLIC',now(),now())",content,actor);
        jdbc.update("insert into learning_content_versions(content_version_id,content_id,version_number,title,body,status,published_at,created_by,created_at,updated_at) values(?,?,1,'Grammar','Present perfect reference','PUBLISHED',now()-interval '1 day',?,now(),now())",version,content,actor);
        jdbc.update("update learning_contents set current_published_version_id=? where content_id=?",version,content);
        login(actor,"ai:chat:self","ai:knowledge:manage","content:read");
    }
    void login(UUID id,String... permissions) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SessionPrincipal(UUID.randomUUID(),id,List.of("LEARNER"),List.of(permissions)),null,
                Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }
    void index() { chat.index(content);clearInvocations(rag); }
    RemoteAnswer answer(UUID document) { return new RemoteAnswer("Use have + past participle.",true,null,
            List.of(new RemoteCitation(document,UUID.randomUUID(),"Grammar","Present perfect reference",null)),new RemoteUsage("google","test-model",10,20,30,5)); }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        jdbc.update("delete from ai_citation_snapshots where message_id in(select m.message_id from ai_messages m join ai_conversations c using(conversation_id) where c.user_id in (?,?))",actor,other);
        jdbc.update("delete from ai_request_logs where user_id in (?,?)",actor,other);
        jdbc.update("delete from ai_chat_requests where user_id in (?,?)",actor,other);
        jdbc.update("delete from ai_conversations where user_id in (?,?)",actor,other);
        jdbc.update("delete from ai_indexed_versions where content_version_id=?",version);
        jdbc.update("update learning_contents set current_published_version_id=null where content_id=?",content);
        jdbc.update("delete from learning_content_versions where content_version_id=?",version);
        jdbc.update("delete from learning_contents where content_id=?",content);
        jdbc.update("delete from users where user_id in (?,?)",actor,other);
    }
    @Test void savesPinnedSourcesAndReplaysWithoutCallingProviderAgain() {
        index(); var conversation=chat.create();var request=UUID.randomUUID();
        when(rag.ask(anyString(),anyList(),anyList(),any())).thenAnswer(invocation->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return answer(version);});
        var result=chat.ask(conversation.id(),request,"How does present perfect work?");
        assertTrue(result.grounded()); assertEquals(version,result.citations().getFirst().contentVersionId());
        assertEquals(result,chat.ask(conversation.id(),request,"How does present perfect work?"));
        verify(rag,times(1)).ask(anyString(),eq(List.of(version)),eq(List.of()),eq(request));
        assertEquals(2,chat.messages(conversation.id(),0).size());
        assertEquals(1,jdbc.queryForObject("select count(*) from ai_request_logs where request_id=?",Integer.class,request));
        assertThrows(ChatFailure.class,()->chat.ask(conversation.id(),request,"Different question"));
    }
    @Test void anotherUserCannotReadOrWriteConversation() {
        var conversation=chat.create();login(other,"ai:chat:self");
        assertEquals(404,assertThrows(ChatFailure.class,()->chat.messages(conversation.id(),0)).status);
        assertEquals(404,assertThrows(ChatFailure.class,()->chat.ask(conversation.id(),UUID.randomUUID(),"Hello")).status);
        verifyNoInteractions(rag);
    }
    @Test void noScopeReturnsWarningWithoutProviderAndPrivateSourcesStayHidden() {
        index();jdbc.update("update learning_contents set visibility='PRIVATE' where content_id=?",content);
        var conversation=chat.create();var result=chat.ask(conversation.id(),UUID.randomUUID(),"Explain this");
        assertFalse(result.grounded());assertEquals(ChatContracts.NO_SOURCE,result.warning());assertTrue(result.citations().isEmpty());verifyNoInteractions(rag);
    }
    @Test void basicGreetingIsAnsweredLocallyAndSavedWithoutSourceWarning() {
        var conversation=chat.create();
        var result=chat.ask(conversation.id(),UUID.randomUUID(),"  Xin chào!!!  ");

        assertTrue(result.answer().startsWith("Xin chào!"));
        assertFalse(result.grounded());
        assertNull(result.warning());
        assertTrue(result.citations().isEmpty());
        assertEquals(2,chat.messages(conversation.id(),0).size());
        verifyNoInteractions(rag);
    }
    @Test void revokedSourcesHideSavedAnswerAndAreRemovedFromFutureHistory() {
        index();when(rag.ask(anyString(),anyList(),anyList(),any())).thenReturn(answer(version));
        var conversation=chat.create();chat.ask(conversation.id(),UUID.randomUUID(),"Explain");
        jdbc.update("update learning_contents set visibility='PRIVATE' where content_id=?",content);
        var messages=chat.messages(conversation.id(),0);
        assertFalse(messages.getLast().content().contains("have +"));assertTrue(messages.getLast().answer().citations().isEmpty());
    }
    @Test void revokedDuringCallDoesNotReturnGeneratedText() {
        index();when(rag.ask(anyString(),anyList(),anyList(),any())).thenAnswer(invocation->{jdbc.update("update learning_contents set visibility='PRIVATE' where content_id=?",content);return answer(version);});
        var result=chat.ask(chat.create().id(),UUID.randomUUID(),"Explain");assertFalse(result.grounded());assertFalse(result.answer().contains("have +"));
    }
    @Test void invalidCitationOrProviderFailureDoesNotPersistAssistantOrLeakProviderError() {
        index();var conversation=chat.create();when(rag.ask(anyString(),anyList(),anyList(),any())).thenReturn(answer(UUID.randomUUID()));
        assertEquals(502,assertThrows(ChatFailure.class,()->chat.ask(conversation.id(),UUID.randomUUID(),"Explain")).status);
        assertTrue(chat.messages(conversation.id(),0).isEmpty());
        when(rag.ask(anyString(),anyList(),anyList(),any())).thenThrow(new ChatFailure(503,"AI_UNAVAILABLE","Unavailable"));
        assertEquals(503,assertThrows(ChatFailure.class,()->chat.ask(conversation.id(),UUID.randomUUID(),"Explain again")).status);
        assertTrue(chat.messages(conversation.id(),0).isEmpty());
    }
    @Test void minuteQuotaIncludesFailedRequests() {
        var conversation=chat.create();
        for(int i=0;i<5;i++) chat.ask(conversation.id(),UUID.randomUUID(),"Question "+i);
        assertEquals(429,assertThrows(ChatFailure.class,()->chat.ask(conversation.id(),UUID.randomUUID(),"Sixth")).status);
    }
    @Test void permissionRequiredEvenForDirectServiceCall() {
        login(actor);assertThrows(org.springframework.security.access.AccessDeniedException.class,chat::create);
    }
    @Test void anonymousHttpRequestRejected() throws Exception {
        SecurityContextHolder.clearContext();mvc.perform(post("/api/v1/ai/conversations")).andExpect(status().isUnauthorized());
    }
}
