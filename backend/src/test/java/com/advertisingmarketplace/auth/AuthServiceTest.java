package com.advertisingmarketplace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.advertisingmarketplace.auth.dto.AuthResponse;
import com.advertisingmarketplace.auth.dto.ChangePasswordRequest;
import com.advertisingmarketplace.auth.dto.LoginRequest;
import com.advertisingmarketplace.auth.dto.SignupRequest;
import com.advertisingmarketplace.common.observability.MarketplaceMetrics;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserNotFoundException;
import com.advertisingmarketplace.user.UserRepository;
import com.advertisingmarketplace.user.UserService;
import com.advertisingmarketplace.user.UserType;
import com.advertisingmarketplace.user.dto.CreateUserRequest;
import com.advertisingmarketplace.user.dto.UserResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final String IP = "203.0.113.7";
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final UserService userService = mock(UserService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final AccessTokenService accessTokenService = mock(AccessTokenService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userService, userRepository, passwordEncoder, accessTokenService,
                refreshTokenService, loginAttemptService, MarketplaceMetrics.noop());
        when(accessTokenService.issue(any(), any(), any()))
                .thenReturn(new AccessTokenService.IssuedAccessToken("access", NOW.plus(Duration.ofMinutes(15))));
        when(refreshTokenService.issue(any()))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh", NOW.plus(Duration.ofDays(30))));
    }

    private User storedUser(String email, String rawPassword) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getEmail()).thenReturn(email);
        when(user.getUserType()).thenReturn(UserType.MARKETER);
        when(user.getPasswordHash()).thenReturn(passwordEncoder.encode(rawPassword));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void signupCreatesUserAndReturnsTokens() {
        UUID id = UUID.randomUUID();
        when(userService.create(any())).thenReturn(
                new UserResponse(id, "Jane", "jane@example.com", null, UserType.MARKETER, NOW, NOW));

        AuthResponse response = authService.signup(
                new SignupRequest("Jane", "jane@example.com", null, UserType.MARKETER, "Sup3r-secret"));

        ArgumentCaptor<CreateUserRequest> captor = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(userService).create(captor.capture());
        assertThat(captor.getValue().password()).isEqualTo("Sup3r-secret");
        assertThat(captor.getValue().userType()).isEqualTo(UserType.MARKETER);
        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().id()).isEqualTo(id);
    }

    @Test
    void loginWithCorrectPasswordReturnsTokensAndResetsAttempts() {
        User user = storedUser("jane@example.com", "Sup3r-secret");

        AuthResponse response = authService.login(new LoginRequest(" Jane@Example.com ", "Sup3r-secret"), IP);

        assertThat(response.accessToken()).isEqualTo("access");
        verify(accessTokenService).issue(user.getId(), "jane@example.com", UserType.MARKETER);
        verify(loginAttemptService).checkAllowed("jane@example.com", IP);
        verify(loginAttemptService).recordSuccess("jane@example.com", IP);
    }

    @Test
    void loginWithWrongPasswordFailsAndRecordsAttempt() {
        storedUser("jane@example.com", "Sup3r-secret");

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "wrong-passw0rd"), IP))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(loginAttemptService).recordFailure("jane@example.com", IP);
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void loginWithUnknownEmailFailsWithSameError() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "Sup3r-secret"), IP))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(loginAttemptService).recordFailure("ghost@example.com", IP);
    }

    @Test
    void lockedOutEmailIsRejectedBeforePasswordCheck() {
        doThrow(new TooManyLoginAttemptsException(Duration.ofMinutes(5)))
                .when(loginAttemptService).checkAllowed("jane@example.com", IP);

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "Sup3r-secret"), IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void refreshConsumesTokenAndIssuesNewPair() {
        User user = storedUser("jane@example.com", "x");
        when(refreshTokenService.consume("old")).thenReturn(user);

        AuthResponse response = authService.refresh("old");

        assertThat(response.refreshToken()).isEqualTo("refresh");
        verify(refreshTokenService).issue(user);
    }

    @Test
    void logoutAllRevokesEverySession() {
        UUID id = UUID.randomUUID();

        authService.logoutAll(id);

        verify(refreshTokenService).revokeAll(id, RevocationReason.LOGOUT_ALL);
    }

    @Test
    void logoutRevokesToken() {
        authService.logout("refresh");

        verify(refreshTokenService).revoke("refresh");
    }

    @Test
    void changePasswordRequiresCurrentPassword() {
        User user = storedUser("jane@example.com", "Sup3r-secret");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.changePassword(user.getId(),
                new ChangePasswordRequest("wrong-passw0rd", "N3w-password")))
                .isInstanceOf(IncorrectCurrentPasswordException.class);
        verify(user, never()).setPasswordHash(any());
        verify(refreshTokenService, never()).revokeAll(any(), any());
    }

    @Test
    void changePasswordStoresNewHashAndRevokesAllSessions() {
        User user = storedUser("jane@example.com", "Sup3r-secret");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        authService.changePassword(user.getId(), new ChangePasswordRequest("Sup3r-secret", "N3w-password"));

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(user).setPasswordHash(hash.capture());
        assertThat(hash.getValue()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches("N3w-password", hash.getValue())).isTrue();
        verify(refreshTokenService).revokeAll(eq(user.getId()), eq(RevocationReason.PASSWORD_CHANGED));
    }

    @Test
    void changePasswordForMissingUserFails() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword(id,
                new ChangePasswordRequest("Sup3r-secret", "N3w-password")))
                .isInstanceOf(UserNotFoundException.class);
    }
}
