package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.RegistrationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {

    private final RegistrationService registration;

    public RegistrationController(
            RegistrationService registration
    ) {
        this.registration = registration;
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

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 150) String fullName
    ) {
        @Override public String toString() { return "RegisterRequest[redacted]"; }
    }

    public record RegistrationResponse(
            UUID userId,
            String email,
            String status
    ) {
    }

}
