package com.toeic.backend.identity.application;

import com.toeic.backend.identity.domain.PasswordPolicy;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toeic.backend.identity.domain.User;
import com.toeic.backend.identity.infrastructure.RoleRepository;
import com.toeic.backend.identity.infrastructure.UserRepository;

@Service
public class RegistrationService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final EmailVerificationService verification;

    public RegistrationService(
            UserRepository users,
            RoleRepository roles,
            PasswordEncoder encoder,
            EmailVerificationService verification
    ) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.verification = verification;
    }

    @Transactional
    public User register(
            String email, String password, String fullName
    ) {
        String normalizedEmail =
                EmailVerificationService.normalizeEmail(email);

        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new RegistrationException(
                    "EMAIL_EXISTS",
                    "Email da duoc su dung.",
                    HttpStatus.CONFLICT
            );
        }

        if (!PasswordPolicy.fitsBcrypt(password)) {
            throw new RegistrationException(
                    "PASSWORD_TOO_LONG",
                    "Mat khau khong duoc vuot 72 byte UTF-8.",
                    HttpStatus.BAD_REQUEST
            );
        }

        var learner = roles.findByName("LEARNER")
                .orElseThrow(() -> new RegistrationException(
                        "ROLE_NOT_CONFIGURED",
                        "Chua cau hinh role LEARNER.",
                        HttpStatus.SERVICE_UNAVAILABLE
                ));

        var user = new User(
                normalizedEmail,
                encoder.encode(password),
                fullName.strip()
        );

        user.addRole(learner);
        users.saveAndFlush(user);
        verification.issue(user);

        return user;
    }
}
