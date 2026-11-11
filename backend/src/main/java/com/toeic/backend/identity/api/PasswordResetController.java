package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class PasswordResetController {
    private final PasswordResetService reset;
    public PasswordResetController(PasswordResetService reset) { this.reset = reset; }

    @PostMapping("/forgot-password")
    public ResponseEntity<Message> forgot(@Valid @RequestBody ForgotPasswordRequest body) {
        try { reset.request(body.email()); }
        catch (RegistrationException exception) {
            // Delivery failure rolls back the OTP. Do not expose account existence through a different response.
            if (!"MAIL_UNAVAILABLE".equals(exception.getCode())) throw exception;
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Message("Nếu email có tài khoản dùng mật khẩu, mã OTP đặt lại mật khẩu sẽ được gửi. Vui lòng kiểm tra hộp thư hoặc thử lại sau ít phút."));
    }
    @PostMapping("/reset-password")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest body) {
        reset.reset(body.email(), body.otp(), body.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) { }
    public record ResetPasswordRequest(@NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String otp,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
        @Override public String toString() { return "ResetPasswordRequest[redacted]"; }
    }
    public record Message(String message) { }
}
