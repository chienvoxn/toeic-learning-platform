package com.toeic.backend.identity.application;

import com.toeic.backend.identity.domain.*;
import com.toeic.backend.identity.infrastructure.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Xác minh Google, ràng buộc trình duyệt, liên kết có mật khẩu và cấp phiên trong cùng transaction. */
@Service
public class GoogleAuthService {
    private final GoogleAuthProperties properties;
    private final GoogleTokenVerifier verifier;
    private final GoogleChallengeRepository challenges;
    private final GoogleIdentityRepository identities;
    private final UserRepository users;
    private final RoleRepository roles;
    private final SessionToken tokens;
    private final SessionIssuer issuer;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public GoogleAuthService(GoogleAuthProperties properties, GoogleTokenVerifier verifier,
            GoogleChallengeRepository challenges, GoogleIdentityRepository identities, UserRepository users,
            RoleRepository roles, SessionToken tokens, SessionIssuer issuer, PasswordEncoder encoder, Clock clock) {
        this.properties = properties; this.verifier = verifier; this.challenges = challenges;
        this.identities = identities; this.users = users; this.roles = roles; this.tokens = tokens;
        this.issuer = issuer; this.encoder = encoder; this.clock = clock;
    }
    @Transactional
    public Challenge challenge() {
        if (!properties.enabled()) throw GoogleAuthException.disabled();
        var now = OffsetDateTime.now(clock);
        challenges.deleteExpired(now);
        var id = UUID.randomUUID(); var nonce = tokens.generate(); var binding = tokens.generate();
        challenges.save(new GoogleLoginChallenge(id, tokens.hash(nonce), tokens.hash(binding), now));
        return new Challenge(id, properties.clientId(), nonce, binding);
    }
    @Transactional
    public AuthService.LoginResult login(UUID challengeId, String credential, String binding, String currentPassword) {
        if (!properties.enabled()) throw GoogleAuthException.disabled();
        if (binding == null || !binding.matches("[A-Za-z0-9_-]{43}")) throw GoogleAuthException.invalid();
        var challenge = challenges.findLocked(challengeId).orElseThrow(GoogleAuthException::invalid);
        var now = OffsetDateTime.now(clock);
        if (!challenge.usable(now) || !same(challenge.getBindingHash(), tokens.hash(binding))) throw GoogleAuthException.invalid();
        var identity = verifier.verify(credential);
        if (!same(challenge.getNonceHash(), tokens.hash(identity.nonce()))) throw GoogleAuthException.invalid();
        var linked = identities.findById(identity.subject());
        User user;
        if (linked.isPresent()) {
            user = users.findForUpdate(linked.get().getUser().getUserId()).orElseThrow(GoogleAuthException::invalid);
            if (!"ACTIVE".equals(user.getStatus())) throw GoogleAuthException.invalid();
        } else {
            var existing = users.findForLogin(identity.email());
            if (existing.isPresent()) {
                user = existing.get();
                if (!"ACTIVE".equals(user.getStatus())) throw new GoogleAuthException("GOOGLE_ACCOUNT_NOT_ACTIVE", "Tài khoản hiện chưa hoạt động. Vui lòng liên hệ quản trị viên.", HttpStatus.CONFLICT);
                if (user.getPasswordHash() == null || identities.existsByUserUserId(user.getUserId())) throw GoogleAuthException.invalid();
                if (currentPassword == null || currentPassword.isBlank()) throw new GoogleAuthException("GOOGLE_LINK_REQUIRED", "Email này đã có tài khoản. Nhập mật khẩu hiện tại để xác nhận liên kết Google.", HttpStatus.CONFLICT);
                if (!PasswordPolicy.fitsBcrypt(currentPassword) || !encoder.matches(currentPassword, user.getPasswordHash())) throw GoogleAuthException.invalid();
            } else {
                var learner = roles.findByName("LEARNER").orElseThrow(() -> new GoogleAuthException("ROLE_NOT_CONFIGURED", "Chưa cấu hình vai trò người học.", HttpStatus.SERVICE_UNAVAILABLE));
                user = User.fromGoogle(identity.email(), identity.fullName(), now, identity.authoritativeEmail());
                user.addRole(learner); users.saveAndFlush(user);
            }
            identities.saveAndFlush(new GoogleIdentity(identity.subject(), user, now));
        }
        now = OffsetDateTime.now(clock);
        if (!challenge.usable(now)) throw GoogleAuthException.invalid();
        challenge.consume(now);
        return issuer.issue(user);
    }
    private static boolean same(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }
    public record Challenge(UUID challengeId, String clientId, String nonce, String binding) {
        @Override public String toString() { return "GoogleChallenge[redacted]"; }
    }
}
