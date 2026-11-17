package com.toeic.backend.ai.infrastructure;

import com.toeic.backend.ai.application.ChatContracts.*;
import com.toeic.backend.ai.application.ChatFailure;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Gọi API AI nội bộ bằng khóa riêng, giới hạn thời gian và lọc lỗi provider. */
@Component
public class RagClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json;
    private final String base, serviceKey, adminKey;
    public RagClient(ObjectMapper json, @Value("${app.ai.base-url:http://127.0.0.1:8001}") String base,
                     @Value("${app.ai.service-key:}") String serviceKey, @Value("${app.ai.admin-key:}") String adminKey) {
        this.json=json; this.base=base.replaceAll("/$",""); this.serviceKey=serviceKey; this.adminKey=adminKey;
    }
    private String send(String path, String method, Object body, String key, UUID trace) {
        if (key.isBlank()) throw new ChatFailure(503,"AI_NOT_CONFIGURED","AI Tutor chưa được cấu hình.");
        try {
            var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(40))
                    .header("X-API-Key",key).header("X-Request-ID",trace.toString())
                    .header("Content-Type","application/json")
                    .method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200 || response.statusCode()>=300)
                throw new ChatFailure(503,"AI_UNAVAILABLE","AI Tutor tạm thời không khả dụng. Vui lòng thử lại sau.");
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ChatFailure(503,"AI_UNAVAILABLE","Yêu cầu AI đã bị gián đoạn.");
        } catch (ChatFailure exception) { throw exception; }
        catch (Exception exception) { throw new ChatFailure(503,"AI_UNAVAILABLE","Không kết nối được AI Tutor."); }
    }
    public RemoteAnswer ask(String question, List<UUID> scope, List<History> history, UUID requestId) {
        String body=send("/internal/v1/tutor/ask","POST",Map.of("question",question,"allowedDocumentIds",scope,
                "history",history,"mode","ask","language","vi"),serviceKey,requestId);
        try { return json.readValue(body,RemoteAnswer.class); }
        catch(Exception exception) { throw new ChatFailure(502,"AI_INVALID_RESPONSE","Phản hồi AI không hợp lệ."); }
    }
    public void index(UUID version, String title, String text) {
        send("/internal/v1/documents","PUT",Map.of("documentId",version,"title",title,"text",text),adminKey,UUID.randomUUID());
    }
}
