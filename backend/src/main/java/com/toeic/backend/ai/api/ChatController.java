package com.toeic.backend.ai.api;

import com.toeic.backend.ai.application.*;
import com.toeic.backend.ai.application.ChatContracts.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1")
public class ChatController {
    private final ChatService chat;
    public ChatController(ChatService chat) { this.chat=chat; }
    public record Ask(@NotNull UUID requestId,@NotBlank @Size(max=4000) String question) {}
    @GetMapping("/ai/conversations")
    public List<Conversation> list(@RequestParam(defaultValue="0") @Min(0) @Max(1000) int page) { return chat.list(page); }
    @PostMapping("/ai/conversations")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Conversation create() { return chat.create(); }
    @GetMapping("/ai/conversations/{id}/messages")
    public List<Message> messages(@PathVariable UUID id,@RequestParam(defaultValue="0") @Min(0) @Max(1000) int page) { return chat.messages(id,page); }
    @PostMapping("/ai/conversations/{id}/messages")
    public Answer ask(@PathVariable UUID id,@Valid @RequestBody Ask body) { return chat.ask(id,body.requestId(),body.question()); }
    @PostMapping("/admin/ai/contents/{id}/index")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void index(@PathVariable UUID id) { chat.index(id); }
    @ExceptionHandler(ChatFailure.class)
    public ResponseEntity<?> failure(ChatFailure failure) { return ResponseEntity.status(failure.status).body(Map.of("code",failure.code,"message",failure.getMessage(),"traceId",UUID.randomUUID().toString(),"details",List.of())); }
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    public ResponseEntity<?> validation(Exception ignored) { return ResponseEntity.badRequest().body(Map.of("code","VALIDATION_ERROR","message","Dữ liệu hỏi đáp không hợp lệ.","traceId",UUID.randomUUID().toString(),"details",List.of())); }
}
