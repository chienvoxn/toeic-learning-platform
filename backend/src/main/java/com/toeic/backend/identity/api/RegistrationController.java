package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.EmailVerificationService;
import com.toeic.backend.identity.application.RegistrationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {

    private final RegistrationService registration;
    private final EmailVerificationService verification;

    public RegistrationController(
            RegistrationService registration,
            EmailVerificationService verification
    ) {
        this.registration = registration;
        this.verification = verification;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        var user = registration.register(
                request.email(),
                request.password(),
                request.fullName()
        );

        return new RegistrationResponse(
                user.getUserId(),
                user.getEmail(),
                user.getStatus()
        );
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request
    ) {
        verification.verify(request.email(), request.otp());

        return new MessageResponse(
                "Email da duoc xac minh thanh cong."
        );
    }

    @PostMapping("/resend-otp")
    public MessageResponse resendOtp(
            @Valid @RequestBody ResendOtpRequest request
    ) {
        verification.resend(request.email());

        return new MessageResponse(
                "Neu tai khoan dang cho xac minh, ma OTP se duoc gui."
        );
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 150) String fullName
    ) {
    }

    public record VerifyEmailRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String otp
    ) {
    }

    public record ResendOtpRequest(
            @NotBlank @Email @Size(max = 255) String email
    ) {
    }

    public record RegistrationResponse(
            UUID userId,
            String email,
            String status
    ) {
    }

    public record MessageResponse(String message) {
    }
}