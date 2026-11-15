package com.advertisingmarketplace.auth;

import com.advertisingmarketplace.auth.dto.AuthResponse;
import com.advertisingmarketplace.auth.dto.ChangePasswordRequest;
import com.advertisingmarketplace.auth.dto.LoginRequest;
import com.advertisingmarketplace.auth.dto.RefreshTokenRequest;
import com.advertisingmarketplace.auth.dto.SignupRequest;
import com.advertisingmarketplace.security.CurrentUser;
import com.advertisingmarketplace.user.UserService;
import com.advertisingmarketplace.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final RefreshTokenCookies cookies;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.signup(request);
        return respond(ResponseEntity.created(URI.create("/api/v1/users/" + response.user().id())), response,
                httpRequest);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        return respond(ResponseEntity.ok(), authService.login(request, httpRequest.getRemoteAddr()), httpRequest);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        String token = refreshTokenFrom(request, httpRequest);
        return respond(ResponseEntity.ok(), authService.refresh(token), httpRequest);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        String token = request != null ? request.refreshToken() : cookies.read(httpRequest).orElse(null);
        if (token != null) {
            if (request == null) {
                requireCsrfHeader(httpRequest);
            }
            authService.logout(token);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Authentication authentication) {
        authService.logoutAll(CurrentUser.id(authentication));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    private String refreshTokenFrom(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        if (request != null) {
            return request.refreshToken();
        }
        String token = cookies.read(httpRequest).orElseThrow(InvalidRefreshTokenException::new);
        requireCsrfHeader(httpRequest);
        return token;
    }

    private static void requireCsrfHeader(HttpServletRequest httpRequest) {
        if (httpRequest.getHeader(RefreshTokenCookies.CSRF_HEADER) == null) {
            throw new MissingCsrfHeaderException();
        }
    }

    private ResponseEntity<AuthResponse> respond(ResponseEntity.BodyBuilder builder, AuthResponse response,
            HttpServletRequest httpRequest) {
        return builder.cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookies.create(response.refreshToken()))
                .body(RefreshTokenCookies.wantsTokenInBody(httpRequest) ? response : response.withoutRefreshToken());
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return userService.getById(CurrentUser.id(authentication));
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        authService.changePassword(CurrentUser.id(authentication), request);
    }
}
