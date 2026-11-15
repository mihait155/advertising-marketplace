package com.advertisingmarketplace.auth;

import static com.advertisingmarketplace.support.WithJwt.as;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.auth.dto.AuthResponse;
import com.advertisingmarketplace.auth.dto.ChangePasswordRequest;
import com.advertisingmarketplace.auth.dto.LoginRequest;
import com.advertisingmarketplace.auth.dto.SignupRequest;
import com.advertisingmarketplace.security.SecurityConfig;
import com.advertisingmarketplace.user.EmailAlreadyInUseException;
import com.advertisingmarketplace.user.UserService;
import com.advertisingmarketplace.user.UserType;
import com.advertisingmarketplace.user.dto.UserResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, RefreshTokenCookies.class})
class AuthControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    private static UserResponse user() {
        return new UserResponse(USER_ID, "Jane", "jane@example.com", null, UserType.MARKETER, NOW, NOW);
    }

    private static AuthResponse authResponse() {
        return new AuthResponse("Bearer", "access-token", NOW.plusSeconds(900), "refresh-token",
                NOW.plusSeconds(86400), user());
    }

    @Test
    void signupIsPublicAndReturns201WithTokens() throws Exception {
        when(authService.signup(any(SignupRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Token-Delivery", "body")
                        .content("""
                                {"fullName":"Jane","email":"jane@example.com","userType":"MARKETER",
                                 "password":"Sup3r-secret"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/" + USER_ID))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.user.id").value(USER_ID.toString()));
    }

    @Test
    void refreshTokenIsSetAsHttpOnlyCookieAndLeftOutOfTheBody() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"Sup3r-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("am_refresh=refresh-token"),
                        org.hamcrest.Matchers.containsString("Path=/api/v1/auth"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("Secure"),
                        org.hamcrest.Matchers.containsString("SameSite=Strict"))));
    }

    @Test
    void refreshWithCookieRequiresTheCsrfHeader() throws Exception {
        when(authService.refresh("from-cookie")).thenReturn(authResponse());
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("am_refresh", "from-cookie");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie).header("X-Requested-With", "fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "fetch"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsTheCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("am_refresh", "from-cookie"))
                        .header("X-Requested-With", "fetch"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        verify(authService).logout("from-cookie");
    }

    @Test
    void signupAsAdminIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Mallory","email":"m@example.com","userType":"ADMIN",
                                 "password":"Sup3r-secret"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("selfRegistrableUserType")));

        verifyNoInteractions(authService);
    }

    @Test
    void signupWithWeakPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Jane","email":"jane@example.com","userType":"CUSTOMER",
                                 "password":"password"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("password")));
    }

    @Test
    void signupWithTakenEmailReturns409() throws Exception {
        when(authService.signup(any())).thenThrow(new EmailAlreadyInUseException("jane@example.com"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Jane","email":"jane@example.com","userType":"CUSTOMER",
                                 "password":"Sup3r-secret"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void loginReturnsTokens() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"Sup3r-secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void loginWithBadCredentialsReturns401() throws Exception {
        when(authService.login(any(), any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void lockedOutLoginReturns429WithRetryAfter() throws Exception {
        when(authService.login(any(), any())).thenThrow(new TooManyLoginAttemptsException(Duration.ofSeconds(90)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"whatever1"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "90"));
    }

    @Test
    void loginWithMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("email")))
                .andExpect(jsonPath("$.errors", hasKey("password")));
    }

    @Test
    void refreshReturnsNewTokens() throws Exception {
        when(authService.refresh("refresh-token")).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Token-Delivery", "body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void refreshWithInvalidTokenReturns401() throws Exception {
        when(authService.refresh("bad")).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"bad"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutIsPublicAndReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-token"}
                                """))
                .andExpect(status().isNoContent());

        verify(authService).logout("refresh-token");
    }

    @Test
    void logoutAllRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-all"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout-all").with(as(USER_ID, UserType.MARKETER)))
                .andExpect(status().isNoContent());

        verify(authService).logoutAll(USER_ID);
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        when(userService.getById(USER_ID)).thenReturn(user());

        mockMvc.perform(get("/api/v1/auth/me").with(as(USER_ID, UserType.MARKETER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordForCurrentUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/change-password").with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Sup3r-secret","newPassword":"N3w-password"}
                                """))
                .andExpect(status().isNoContent());

        verify(authService).changePassword(eq(USER_ID), eq(new ChangePasswordRequest("Sup3r-secret", "N3w-password")));
    }

    @Test
    void changePasswordWithWrongCurrentPasswordReturns400() throws Exception {
        org.mockito.Mockito.doThrow(new IncorrectCurrentPasswordException())
                .when(authService).changePassword(eq(USER_ID), any());

        mockMvc.perform(post("/api/v1/auth/change-password").with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"wrong","newPassword":"N3w-password"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Current password is incorrect"));
    }
}
