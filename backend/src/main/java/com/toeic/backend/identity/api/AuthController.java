package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.AuthService;
import com.toeic.backend.identity.application.SessionPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public AuthService.LoginResult login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal SessionPrincipal principal) {
        auth.logout(principal.sessionId());
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 255) String email,
                               @NotBlank @Size(max = 72) String password) {
        @Override
        public String toString() { return "LoginRequest[redacted]"; }
    }
}
