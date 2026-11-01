package com.toeic.backend.identity.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.toeic.backend.identity.domain.EmailVerificationOtp;
import com.toeic.backend.identity.domain.User;
import com.toeic.backend.identity.infrastructure.EmailVerificationOtpRepository;
import com.toeic.backend.identity.infrastructure.OtpMailService;
import com.toeic.backend.identity.infrastructure.UserRepository;

@Service
public class EmailVerificationService {

    private final UserRepository users;
    private final EmailVerificationOtpRepository otps;
    private final PasswordEncoder encoder;
    private final OtpMailService mail;
    private final RegistrationProperties properties;
    private final Clock clock;
    private final SecureRandom random;

    public EmailVerificationService(
            UserRepository users,
            EmailVerificationOtpRepository otps,
            PasswordEncoder encoder,
            OtpMailService mail,
            RegistrationProperties properties,
            Clock clock,
            SecureRandom random
    ) {
        this.users = users;
        this.otps = otps;
        this.encoder = encoder;
        this.mail = mail;
        this.properties = properties;
        this.clock = clock;
        this.random = random;
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void issue(User user) {
        var now = OffsetDateTime.now(clock);

        otps.invalidateUnused(user.getUserId(), now);

        String rawCode = String.format(
                Locale.ROOT,
                "%06d",
                random.nextInt(1_000_000)
        );

        var otp = new EmailVerificationOtp(
                user,
                encoder.encode(rawCode),
                now,
                now.plusSeconds(properties.otpTtlSeconds())
        );

        otps.saveAndFlush(otp);
        mail.sendVerification(user.getEmail(), rawCode);
    }

    @Transactional
    public void resend(String email) {
        var optional = users.findForVerification(normalizeEmail(email));

        // Unknown or non-pending accounts receive generic success.
        if (optional.isEmpty()
                || !"PENDING".equals(optional.get().getStatus())) {
            return;
        }

        var user = optional.get();
        var now = OffsetDateTime.now(clock);

        var latest = otps
                .findFirstByUser_UserIdOrderByCreatedAtDescOtpIdDesc(
                        user.getUserId()
                );

        if (latest.isPresent()
                && now.isBefore(
                        latest.get().getCreatedAt().plusSeconds(
                                properties.resendCooldownSeconds()
                        )
                )) {
            throw new RegistrationException(
                    "OTP_RESEND_TOO_SOON",
                    "Vui long doi truoc khi gui lai ma.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        issue(user);
    }

    @Transactional(noRollbackFor = OtpRejectedException.class)
    public void verify(String email, String rawCode) {
        var user = users.findForVerification(normalizeEmail(email))
                .orElseThrow(() -> rejected(
                        "OTP_INVALID",
                        "Email hoac OTP khong hop le."
                ));

        if (!"PENDING".equals(user.getStatus())) {
            throw rejected(
                    "ACCOUNT_NOT_PENDING",
                    "Tai khoan khong o trang thai cho xac minh."
            );
        }

        var otp = otps
                .findFirstByUser_UserIdOrderByCreatedAtDescOtpIdDesc(
                        user.getUserId()
                )
                .orElseThrow(() -> rejected(
                        "OTP_INVALID",
                        "Email hoac OTP khong hop le."
                ));

        var now = OffsetDateTime.now(clock);

        if (!now.isBefore(otp.getExpiresAt())) {
            throw rejected(
                    "OTP_EXPIRED",
                    "OTP da het han. Vui long yeu cau ma moi."
            );
        }

        if (!otp.canUse(now, properties.maxAttempts())) {
            throw rejected(
                    "OTP_UNAVAILABLE",
                    "OTP da dung hoac da vuot so lan thu."
            );
        }

        if (!encoder.matches(rawCode, otp.getOtpHash())) {
            otp.recordFailedAttempt(now, properties.maxAttempts());
            otps.saveAndFlush(otp);

            throw rejected(
                    "OTP_INVALID",
                    "Email hoac OTP khong hop le."
            );
        }

        otp.markUsed(now, properties.maxAttempts());
        user.verifyEmail();

        otps.save(otp);
        users.save(user);
    }

    private OtpRejectedException rejected(
            String code, String message
    ) {
        return new OtpRejectedException(
                code,
                message,
                HttpStatus.BAD_REQUEST
        );
    }
}