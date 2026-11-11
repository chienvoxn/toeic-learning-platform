package com.toeic.backend.identity.infrastructure;

import com.toeic.backend.identity.application.*;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Kiểm chữ ký bằng JWKS chính thức và các claim OIDC, không tin dữ liệu profile từ client. */
@Component
@EnableConfigurationProperties(GoogleAuthProperties.class)
public class GoogleIdTokenVerifier implements GoogleTokenVerifier {
    private final GoogleAuthProperties properties;
    private final JwtDecoder decoder;
    private final Clock clock;

    @Autowired
    public GoogleIdTokenVerifier(GoogleAuthProperties properties, Clock clock) {
        this(properties, createDecoder(clock), clock);
    }
    public GoogleIdTokenVerifier(GoogleAuthProperties properties, JwtDecoder decoder, Clock clock) {
        this.properties = properties; this.decoder = decoder; this.clock = clock;
    }
    private static JwtDecoder createDecoder(Clock clock) {
        var http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(Duration.ofSeconds(3)); http.setReadTimeout(Duration.ofSeconds(5));
        var decoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(new RestTemplate(http)).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock); decoder.setJwtValidator(timestamps);
        return decoder;
    }
    @Override public Identity verify(String credential) {
        if (!properties.enabled()) throw GoogleAuthException.disabled();
        try {
            var jwt = decoder.decode(credential);
            var now = clock.instant();
            if (!"RS256".equals(jwt.getHeaders().get("alg"))
                    || !Set.of("https://accounts.google.com", "accounts.google.com").contains(jwt.getClaimAsString("iss"))
                    || !jwt.getAudience().contains(properties.clientId())
                    || jwt.getAudience().size() > 1 && jwt.getClaimAsString("azp") == null
                    || jwt.getClaimAsString("azp") != null && !properties.clientId().equals(jwt.getClaimAsString("azp"))
                    || jwt.getExpiresAt() == null || !now.isBefore(jwt.getExpiresAt())
                    || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(now.plusSeconds(60))
                    || !Boolean.TRUE.equals(jwt.getClaims().get("email_verified"))) throw GoogleAuthException.invalid();
            var subject = jwt.getSubject();
            var emailClaim = jwt.getClaimAsString("email");
            var nonce = jwt.getClaimAsString("nonce");
            if (subject == null || subject.isBlank() || subject.length() > 255 || emailClaim == null
                    || emailClaim.length() > 255 || !emailClaim.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                    || nonce == null || !nonce.matches("[A-Za-z0-9_-]{43}")) throw GoogleAuthException.invalid();
            var email = com.toeic.backend.identity.domain.EmailAddress.normalize(emailClaim);
            var name = jwt.getClaimAsString("name");
            if (name == null || name.isBlank()) name = email.substring(0, email.indexOf('@'));
            name = name.strip(); if (name.length() > 150) name = name.substring(0, 150);
            var domain = jwt.getClaimAsString("hd");
            return new Identity(subject, email, name, nonce, email.endsWith("@gmail.com") || domain != null && !domain.isBlank());
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw GoogleAuthException.invalid();
        }
    }
}
