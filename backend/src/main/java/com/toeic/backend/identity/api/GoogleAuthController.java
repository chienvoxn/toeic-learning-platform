package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/google")
public class GoogleAuthController {
    private final GoogleAuthService google;
    private final GoogleAuthProperties properties;
    public GoogleAuthController(GoogleAuthService google, GoogleAuthProperties properties) { this.google = google; this.properties = properties; }

    @PostMapping("/challenge")
    public ResponseEntity<ChallengeResponse> challenge() {
        var challenge = google.challenge();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(challenge.challengeId(), challenge.binding(), Duration.ofMinutes(5)))
                .body(new ChallengeResponse(challenge.challengeId(), challenge.clientId(), challenge.nonce(), 300));
    }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthService.LoginResult> login(@Valid @RequestBody GoogleRequest body, HttpServletRequest request) {
        String binding = null;
        if (request.getCookies() != null) for (var cookie : request.getCookies()) {
            if (cookie.getName().equals(cookieName(body.challengeId()))) binding = cookie.getValue();
        }
        var result = google.login(body.challengeId(), body.credential(), binding, body.currentPassword());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(body.challengeId(), "", Duration.ZERO)).body(result);
    }
    private static String cookieName(UUID id) { return "toeic_google_" + id; }
    private String cookie(UUID id, String value, Duration maxAge) {
        return ResponseCookie.from(cookieName(id), value).httpOnly(true).secure(properties.secureCookie())
                .sameSite("Strict").path("/api/v1/auth/google").maxAge(maxAge).build().toString();
    }
    public record ChallengeResponse(UUID challengeId, String clientId, String nonce, int expiresIn) {
        @Override public String toString() { return "ChallengeResponse[redacted]"; }
    }
    public record GoogleRequest(@NotNull UUID challengeId, @NotBlank @Size(max = 16384) String credential,
                                @Size(max = 72) String currentPassword) {
        @Override public String toString() { return "GoogleRequest[redacted]"; }
    }
}
