package com.toeic.backend.identity.application;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.toeic.backend.identity.infrastructure.GoogleIdTokenVerifier;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.junit.jupiter.api.Assertions.*;

class GoogleIdTokenVerifierTest {
    private static final String CLIENT = "test-client.apps.googleusercontent.com";
    private static final String NONCE = "n".repeat(43);
    private static RSAKey key;
    private static JwtEncoder encoder;
    private GoogleIdTokenVerifier verifier;
    private Instant now;

    @BeforeAll static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate()).keyID("unit-test").build();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }
    @BeforeEach void setup() throws Exception {
        now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        verifier = new GoogleIdTokenVerifier(new GoogleAuthProperties(CLIENT, false), decoder, Clock.fixed(now, ZoneOffset.UTC));
    }
    private String signed(Map<String, Object> overrides) {
        var claims = JwtClaimsSet.builder().issuer("https://accounts.google.com").subject("google-subject")
                .audience(List.of(CLIENT)).issuedAt(now.minusSeconds(30)).expiresAt(now.plusSeconds(300))
                .claim("email", "learner@gmail.com").claim("email_verified", true).claim("name", "Learner")
                .claim("nonce", NONCE).claims(values -> values.putAll(overrides)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("unit-test").build(), claims)).getTokenValue();
    }
    @Test void validSignatureAndClaimsAreAccepted() {
        var identity = verifier.verify(signed(Map.of()));
        assertEquals("google-subject", identity.subject()); assertEquals("learner@gmail.com", identity.email());
        assertEquals(NONCE, identity.nonce()); assertTrue(identity.authoritativeEmail());
    }
    @Test void wrongIssuerAudienceAuthorizedPartyAndNonceAreRejected() {
        for (var claims : List.of(Map.<String, Object>of("iss", "https://attacker.invalid"), Map.<String, Object>of("aud", List.of("other-client")),
                Map.<String, Object>of("azp", "other-client"), Map.<String, Object>of("aud", List.of(CLIENT, "other-client")),
                Map.<String, Object>of("nonce", "short"), Map.<String, Object>of("email_verified", false))) {
            assertThrows(GoogleAuthException.class, () -> verifier.verify(signed(claims)));
        }
    }
    @Test void expiredAndFutureIssuedTokensAreRejected() {
        assertThrows(GoogleAuthException.class, () -> verifier.verify(signed(Map.of("exp", now.minusSeconds(1)))));
        assertThrows(GoogleAuthException.class, () -> verifier.verify(signed(Map.of("iat", now.plusSeconds(120)))));
    }
    @Test void alteredSignatureIsRejected() {
        var parts = signed(Map.of()).split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        assertThrows(GoogleAuthException.class, () -> verifier.verify(String.join(".", parts)));
    }
    @Test void bothGoogleIssuerFormsWork() {
        assertEquals("google-subject", verifier.verify(signed(Map.of("iss", "accounts.google.com"))).subject());
    }
    @Test void thirdPartyEmailNeedsAdditionalProofButWorkspaceIsAuthoritative() {
        assertFalse(verifier.verify(signed(Map.of("email", "learner@example.com"))).authoritativeEmail());
        assertTrue(verifier.verify(signed(Map.of("email", "learner@example.com", "hd", "example.com"))).authoritativeEmail());
    }
    @Test void disabledFeatureNeverAcceptsTokens() {
        var disabled = new GoogleIdTokenVerifier(new GoogleAuthProperties("", false), token -> { throw new AssertionError(); }, Clock.systemUTC());
        assertEquals("GOOGLE_NOT_CONFIGURED", assertThrows(GoogleAuthException.class, () -> disabled.verify("unused")).getCode());
    }
}
