package com.toeic.backend.identity.application;

import com.toeic.backend.identity.domain.AuthSession;
import com.toeic.backend.identity.domain.User;
import com.toeic.backend.identity.infrastructure.AuthSessionRepository;
import com.toeic.backend.identity.infrastructure.LearningProfileRepository;
import com.toeic.backend.identity.infrastructure.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private AuthSessionRepository sessions;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private SessionToken tokens;

    @Mock
    private LearningProfileRepository learningProfiles;

    private AuthService authService;
    private Clock clock;

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "Test@123456";
    private static final String PASSWORD_HASH = "stored-password-hash";
    private static final String TOKEN_HASH = "a".repeat(64);

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
            Instant.parse("2026-11-08T08:00:00Z"),
            ZoneOffset.UTC
        );

        when(encoder.encode(anyString())).thenReturn("dummy-hash");

        AuthProperties properties = new AuthProperties(900);

        authService = new AuthService(
            users,
            sessions,
            encoder,
            tokens,
            properties,
            clock,
            learningProfiles
        );
    }

    @Test
    void login_withValidCredentials_shouldCreateSession() {

        // Arrange
        User user = new User(
            EMAIL,
            PASSWORD_HASH,
            "API Test User"
        );

        user.verifyEmail();

        when(users.findForLogin(EMAIL))
            .thenReturn(Optional.of(user));

        when(encoder.matches(PASSWORD, PASSWORD_HASH))
            .thenReturn(true);

        when(tokens.generate())
            .thenReturn("valid-test-token");

        when(tokens.hash("valid-test-token"))
            .thenReturn(TOKEN_HASH);

        when(learningProfiles.findByUserId(user.getUserId()))
            .thenReturn(Optional.empty());

        // Act
        AuthService.LoginResult result =
            authService.login(EMAIL, PASSWORD);

        // Assert: Login result
        assertNotNull(result);
        assertEquals("valid-test-token", result.accessToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(900, result.expiresIn());
        assertNotNull(result.user());

        // Assert: Session saved
        ArgumentCaptor<AuthSession> sessionCaptor =
            ArgumentCaptor.forClass(AuthSession.class);

        verify(sessions, times(1))
            .save(sessionCaptor.capture());

        AuthSession savedSession =
            sessionCaptor.getValue();

        OffsetDateTime now = OffsetDateTime.now(clock);

        assertEquals(user, savedSession.getUser());
        assertEquals(now.plusSeconds(900), savedSession.getExpiresAt());
        assertEquals(now, user.getLastLoginAt());

        // Verify dependent operations
        verify(users).findForLogin(EMAIL);
        verify(encoder).matches(PASSWORD, PASSWORD_HASH);
        verify(tokens).generate();
        verify(tokens).hash("valid-test-token");
    }

@Test
void login_withWrongPassword_shouldRejectCredentials() {

    // Arrange: Create an active user
    User user = new User(
        EMAIL,
        PASSWORD_HASH,
        "API Test User"
    );

    user.verifyEmail();

    when(users.findForLogin(EMAIL))
        .thenReturn(Optional.of(user));

    when(encoder.matches("WrongPass123!", PASSWORD_HASH))
        .thenReturn(false);

    // Act & Assert: Login must be rejected
    assertThrows(
        AuthException.class,
        () -> authService.login(EMAIL, "WrongPass123!")
    );

    // Verify: No session should be created
    verify(users).findForLogin(EMAIL);
    verify(encoder).matches("WrongPass123!", PASSWORD_HASH);
    verify(sessions, never()).save(any(AuthSession.class));
    verify(tokens, never()).generate();
    }

@Test
void login_withNonexistentEmail_shouldRejectCredentials() {

    // Arrange: Email does not exist
    String nonexistentEmail = "unknown@example.com";
    String password = "ValidPass123!";

    when(users.findForLogin(nonexistentEmail))
        .thenReturn(Optional.empty());

    // AuthService uses the dummy hash when a user is absent
    when(encoder.matches(eq(password), anyString()))
        .thenReturn(false);

    // Act & Assert: Login must be rejected
    assertThrows(
        AuthException.class,
        () -> authService.login(nonexistentEmail, password)
    );

    // Verify: User lookup and password check happened
    verify(users).findForLogin(nonexistentEmail);
    verify(encoder).matches(eq(password), anyString());

    // No token or session should be created
    verify(tokens, never()).generate();
    verify(sessions, never()).save(any(AuthSession.class));
}

@Test
void login_withUnverifiedAccount_shouldRejectCredentials() {

    // Arrange: Create an account that has not verified its email
    User user = new User(
        EMAIL,
        PASSWORD_HASH,
        "API Test User"
    );

    // Do not call user.verifyEmail()
    // The account status remains PENDING
    assertEquals("PENDING", user.getStatus());

    when(users.findForLogin(EMAIL))
        .thenReturn(Optional.of(user));

    // Password is correct
    when(encoder.matches(PASSWORD, PASSWORD_HASH))
        .thenReturn(true);

    // Act & Assert: Login must be rejected
    assertThrows(
        AuthException.class,
        () -> authService.login(EMAIL, PASSWORD)
    );

    // Verify that credentials were checked
    verify(users).findForLogin(EMAIL);
    verify(encoder).matches(PASSWORD, PASSWORD_HASH);

    // No access token or session should be created
    verify(tokens, never()).generate();
    verify(sessions, never()).save(any(AuthSession.class));

    // Account must remain unverified
    assertEquals("PENDING", user.getStatus());
}

@Test
void login_withSuspendedAccount_shouldRejectCredentials() {

    // Arrange: Create an active user
    User user = new User(
        EMAIL,
        PASSWORD_HASH,
        "API Test User"
    );

    user.verifyEmail();

    // Simulate a suspended account
    org.springframework.test.util.ReflectionTestUtils
        .setField(user, "status", "SUSPENDED");

    assertEquals("SUSPENDED", user.getStatus());

    when(users.findForLogin(EMAIL))
        .thenReturn(Optional.of(user));

    // Correct password
    when(encoder.matches(PASSWORD, PASSWORD_HASH))
        .thenReturn(true);

    // Act & Assert: Login must be rejected
    assertThrows(
        AuthException.class,
        () -> authService.login(EMAIL, PASSWORD)
    );

    // Verify: No access token or session created
    verify(users).findForLogin(EMAIL);
    verify(encoder).matches(PASSWORD, PASSWORD_HASH);
    verify(tokens, never()).generate();
    verify(sessions, never()).save(any(AuthSession.class));

    // User remains suspended
    assertEquals("SUSPENDED", user.getStatus());
}

@Test
void login_withEmptyPassword_shouldRejectCredentials() {

    // Arrange: Empty password
    String emptyPassword = "";

    User user = new User(
        EMAIL,
        PASSWORD_HASH,
        "API Test User"
    );

    user.verifyEmail();

    when(users.findForLogin(EMAIL))
        .thenReturn(Optional.of(user));

    when(encoder.matches(emptyPassword, PASSWORD_HASH))
        .thenReturn(false);

    // Act & Assert: Login must be rejected
    assertThrows(
        AuthException.class,
        () -> authService.login(EMAIL, emptyPassword)
    );

    // Verify: Password was checked
    verify(users).findForLogin(EMAIL);
    verify(encoder).matches(emptyPassword, PASSWORD_HASH);

    // No token or session should be created
    verify(tokens, never()).generate();
    verify(sessions, never()).save(any(AuthSession.class));
}

@Test
void login_withOversizedPassword_shouldRejectCredentials() {

    // Arrange: Password exceeds bcrypt's 72-byte limit
    String oversizedPassword = "A".repeat(73);

    assertEquals(
        73,
        oversizedPassword.getBytes(
            java.nio.charset.StandardCharsets.UTF_8
        ).length
    );

    assertFalse(
        com.toeic.backend.identity.domain.PasswordPolicy
            .fitsBcrypt(oversizedPassword)
    );

    // Act & Assert: Login must be rejected
    assertThrows(
        AuthException.class,
        () -> authService.login(EMAIL, oversizedPassword)
    );

    // Verify: Reject before querying repositories
    verifyNoInteractions(users);
    verifyNoInteractions(sessions);
    verifyNoInteractions(learningProfiles);

    // No password matching or token creation
    verify(encoder, never()).matches(anyString(), anyString());
    verifyNoInteractions(tokens);
}

@Test
void login_withUppercaseEmail_shouldNormalizeAndSucceed() {

    // Arrange: Email contains uppercase letters
    String uppercaseEmail = "TEST@EXAMPLE.COM";

    User user = new User(
        EMAIL,
        PASSWORD_HASH,
        "API Test User"
    );

    user.verifyEmail();

    // Repository expects normalized lowercase email
    when(users.findForLogin(EMAIL))
        .thenReturn(Optional.of(user));

    when(encoder.matches(PASSWORD, PASSWORD_HASH))
        .thenReturn(true);

    when(tokens.generate())
        .thenReturn("valid-test-token");

    when(tokens.hash("valid-test-token"))
        .thenReturn(TOKEN_HASH);

    when(learningProfiles.findByUserId(user.getUserId()))
        .thenReturn(Optional.empty());

    // Act: Login with uppercase email
    AuthService.LoginResult result =
        authService.login(uppercaseEmail, PASSWORD);

    // Assert: Login succeeds
    assertNotNull(result);
    assertEquals("valid-test-token", result.accessToken());
    assertEquals("Bearer", result.tokenType());
    assertEquals(900, result.expiresIn());

    // Verify: Lowercase email is used
    verify(users).findForLogin(EMAIL);
    verify(users, never()).findForLogin(uppercaseEmail);

    // Verify: Session is created
    verify(sessions).save(any(AuthSession.class));
    verify(tokens).generate();
    verify(encoder).matches(PASSWORD, PASSWORD_HASH);
}

@Test
void login_withLearningProfile_shouldRetrieveProfileAndSucceed() {

    // Arrange: Create an active account
    User user = new User(
        EMAIL,
        PASSWORD_HASH,
        "API Test User"
    );

    user.verifyEmail();

    when(users.findForLogin(EMAIL))
        .thenReturn(Optional.of(user));

    when(encoder.matches(PASSWORD, PASSWORD_HASH))
        .thenReturn(true);

    when(tokens.generate())
        .thenReturn("valid-test-token");

    when(tokens.hash("valid-test-token"))
        .thenReturn(TOKEN_HASH);

    // Simulate an existing Learning Profile
    com.toeic.backend.identity.domain.LearningProfile profile =
        mock(com.toeic.backend.identity.domain.LearningProfile.class);

    when(learningProfiles.findByUserId(user.getUserId()))
        .thenReturn(Optional.of(profile));

    // Act
    AuthService.LoginResult result =
        authService.login(EMAIL, PASSWORD);

    // Assert: Login succeeds
    assertNotNull(result);
    assertEquals("valid-test-token", result.accessToken());
    assertEquals("Bearer", result.tokenType());
    assertEquals(900, result.expiresIn());
    assertNotNull(result.user());

    // Verify Learning Profile was retrieved
    verify(learningProfiles).findByUserId(user.getUserId());
    verify(profile).getTargetScore();

    // Verify session creation
    verify(sessions).save(any(AuthSession.class));
    verify(tokens).generate();

    // Verify last login time was updated
    assertEquals(
        OffsetDateTime.now(clock),
        user.getLastLoginAt()
    );
}

@Test
void logout_withValidSession_shouldRevokeSession() {

    // Arrange: Prepare a session ID
    java.util.UUID sessionId = java.util.UUID.randomUUID();

    OffsetDateTime expectedTime = OffsetDateTime.now(clock);

    // Act: Logout
    authService.logout(sessionId);

    // Assert: Session is revoked at the expected time
    verify(sessions, times(1))
        .revoke(sessionId, expectedTime);

    // No other session repository operations
    verifyNoMoreInteractions(sessions);

    // No new token or login request
    verifyNoInteractions(users);
    verifyNoInteractions(tokens);
}


}
