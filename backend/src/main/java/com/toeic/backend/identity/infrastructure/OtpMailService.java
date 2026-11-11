package com.toeic.backend.identity.infrastructure;

import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.toeic.backend.identity.application.RegistrationException;
import com.toeic.backend.identity.application.PasswordResetProperties;

@Service
public class OtpMailService {
    private final JavaMailSender sender;
    private final PasswordResetProperties properties;

    public OtpMailService(
            JavaMailSender sender,
            PasswordResetProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    public void sendPasswordReset(String email, String code) {
        var message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email);
        message.setSubject("ENGLINGO - Dat lai mat khau");
        message.setText(
                "Ma OTP dat lai mat khau cua ban: " + code
                + "\nMa het han sau "
                + properties.otpTtlSeconds()
                + " giay. Khong chia se ma nay voi nguoi khac."
        );

        try {
            sender.send(message);
        } catch (MailException ex) {
            throw new RegistrationException(
                    "MAIL_UNAVAILABLE",
                    "Chua gui duoc email. Vui long thu lai.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }
}
