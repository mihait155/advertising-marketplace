package com.advertisingmarketplace.auth;

import com.advertisingmarketplace.auth.dto.AuthResponse;
import com.advertisingmarketplace.auth.dto.ChangePasswordRequest;
import com.advertisingmarketplace.auth.dto.LoginRequest;
import com.advertisingmarketplace.auth.dto.SignupRequest;
import com.advertisingmarketplace.common.observability.MarketplaceMetrics;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserNotFoundException;
import com.advertisingmarketplace.user.UserRepository;
import com.advertisingmarketplace.user.UserService;
import com.advertisingmarketplace.user.dto.CreateUserRequest;
import com.advertisingmarketplace.user.dto.UserResponse;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final MarketplaceMetrics metrics;
    @Getter(value = AccessLevel.PRIVATE, lazy = true)
    private final String dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        UserResponse created = userService.create(new CreateUserRequest(
                request.fullName(), request.email(), request.phone(), request.userType(), request.password()));
        return issueTokens(userRepository.getReferenceById(created.id()), created);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp) {
        String email = UserService.normalizeEmail(request.email());
        try {
            loginAttemptService.checkAllowed(email, clientIp);
        } catch (TooManyLoginAttemptsException e) {
            metrics.login("locked");
            throw e;
        }

        User user = userRepository.findByEmail(email).orElse(null);
        String hash = user != null ? user.getPasswordHash() : getDummyPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordMatches) {
            loginAttemptService.recordFailure(email, clientIp);
            metrics.login("bad_credentials");
            throw new InvalidCredentialsException();
        }
        loginAttemptService.recordSuccess(email, clientIp);
        metrics.login("success");

        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return issueTokens(user, UserResponse.from(user));
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse refresh(String refreshToken) {
        User user = refreshTokenService.consume(refreshToken);
        return issueTokens(user, UserResponse.from(user));
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional
    public void logoutAll(UUID userId) {
        refreshTokenService.revokeAll(userId, RevocationReason.LOGOUT_ALL);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IncorrectCurrentPasswordException();
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAll(userId, RevocationReason.PASSWORD_CHANGED);
    }

    private AuthResponse issueTokens(User user, UserResponse userResponse) {
        AccessTokenService.IssuedAccessToken access =
                accessTokenService.issue(userResponse.id(), userResponse.email(), userResponse.userType());
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user);
        return new AuthResponse(AuthResponse.BEARER, access.value(), access.expiresAt(),
                refresh.value(), refresh.expiresAt(), userResponse);
    }
}
