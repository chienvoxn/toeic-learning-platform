package com.toeic.backend.identity.application;

import com.toeic.backend.identity.domain.*;
import com.toeic.backend.identity.infrastructure.*;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** OTP chỉ dùng khôi phục mật khẩu: khóa user, giới hạn lần thử và thu hồi mọi phiên khi thành công. */
@Service
public class PasswordResetService {
    private final UserRepository users;
    private final PasswordResetOtpRepository otps;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final OtpMailService mail;
    private final PasswordResetProperties properties;
    private final Clock clock;
    private final SecureRandom random;
    private final String dummyHash;

    public PasswordResetService(UserRepository users, PasswordResetOtpRepository otps, AuthSessionRepository sessions,
            PasswordEncoder encoder, OtpMailService mail, PasswordResetProperties properties, Clock clock, SecureRandom random) {
        this.users = users; this.otps = otps; this.sessions = sessions; this.encoder = encoder;
        this.mail = mail; this.properties = properties; this.clock = clock; this.random = random;
        this.dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public void request(String email) {
        var code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        var hash = encoder.encode(code);
        var optional = users.findForLogin(EmailAddress.normalize(email));
        if (optional.isEmpty() || !eligible(optional.get())) return;
        var user = optional.get(); var now = OffsetDateTime.now(clock);
        var latest = otps.findFirstByUser_UserIdOrderByCreatedAtDescOtpIdDesc(user.getUserId());
        if (latest.isPresent() && now.isBefore(latest.get().getCreatedAt().plusSeconds(properties.resendCooldownSeconds()))) return;
        otps.invalidateUnused(user.getUserId(), now);
        otps.saveAndFlush(new PasswordResetOtp(user, hash, now, now.plusSeconds(properties.otpTtlSeconds())));
        mail.sendPasswordReset(user.getEmail(), code);
    }

    @Transactional(noRollbackFor = OtpRejectedException.class)
    public void reset(String email, String code, String newPassword) {
        if (!PasswordPolicy.validNewPassword(newPassword)) throw new RegistrationException("VALIDATION_ERROR", "Mật khẩu phải có 8–72 ký tự và tối đa 72 byte UTF-8.", HttpStatus.BAD_REQUEST);
        var optional = users.findForLogin(EmailAddress.normalize(email));
        if (optional.isEmpty() || !eligible(optional.get())) { encoder.matches(code, dummyHash); throw invalid(); }
        var user = optional.get(); var now = OffsetDateTime.now(clock);
        var latest = otps.findFirstByUser_UserIdOrderByCreatedAtDescOtpIdDesc(user.getUserId());
        if (latest.isEmpty() || !latest.get().canUse(now, properties.maxAttempts())) { encoder.matches(code, dummyHash); throw invalid(); }
        var otp = latest.get();
        if (!encoder.matches(code, otp.getOtpHash())) {
            otp.recordFailedAttempt(now, properties.maxAttempts()); otps.saveAndFlush(otp);
            throw invalid();
        }
        otp.markUsed(now, properties.maxAttempts());
        user.changePasswordHash(encoder.encode(newPassword), now);
        user.recordEmailProof(now);
        otps.invalidateUnused(user.getUserId(), now);
        sessions.revokeAllForUser(user.getUserId(), now);
    }
    private static boolean eligible(User user) { return "ACTIVE".equals(user.getStatus()) && user.getPasswordHash() != null; }
    private static OtpRejectedException invalid() { return new OtpRejectedException("PASSWORD_RESET_INVALID", "Email hoặc mã OTP không đúng, đã hết hạn hoặc không còn sử dụng được.", HttpStatus.BAD_REQUEST); }
}
