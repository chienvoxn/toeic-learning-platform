package com.toeic.backend.identity.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.toeic.backend.identity.application.PasswordResetService;
import com.toeic.backend.identity.application.RegistrationException;
import com.toeic.backend.identity.application.RegistrationService;
import com.toeic.backend.identity.domain.User;

class RegistrationControllerTest {

    private final RegistrationService registration =
            mock(RegistrationService.class);

    private final PasswordResetService verification =
            mock(PasswordResetService.class);

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
                .standaloneSetup(
                        new RegistrationController(registration), new PasswordResetController(verification)
                )
                .setControllerAdvice(new RegistrationExceptionHandler())
                .build();
    }

    private User activeUser(String email, String passwordHash, String name) {
        var user = new User(email, passwordHash, name);
        user.activateWithoutEmailVerification(java.time.OffsetDateTime.now());
        return user;
    }
    @Test
    void returnsCreatedWithoutLeakingHashes() throws Exception {
        when(registration.register(anyString(), anyString(), anyString()))
                .thenReturn(activeUser(
                        "learner@example.com",
                        "secret-hash",
                        "Learner"
                ));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com",
                                  "password": "TestPass123!",
                                  "fullName": "Learner"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.otp").doesNotExist())
                .andExpect(jsonPath("$.otpHash").doesNotExist());
    }

    @Test
    void rejectsInvalidRegistrationBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "bad",
                                  "password": "123",
                                  "fullName": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(registration);
    }

    @Test
    void rejectsInvalidOtpFormat() throws Exception {
        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com",
                                  "otp": "12ab", "newPassword": "ValidPassword123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(verification);
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        when(registration.register(anyString(), anyString(), anyString()))
                .thenThrow(new RegistrationException(
                        "EMAIL_EXISTS",
                        "Email exists",
                        HttpStatus.CONFLICT
                ));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com",
                                  "password": "TestPass123!",
                                  "fullName": "Learner"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
    }
}