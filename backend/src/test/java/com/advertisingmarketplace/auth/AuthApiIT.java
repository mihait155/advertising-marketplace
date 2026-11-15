package com.advertisingmarketplace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AuthApiIT extends AbstractIntegrationTest {

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .header("X-Token-Delivery", "body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private ResultActions me(String accessToken) throws Exception {
        return mockMvc.perform(bearer(get("/api/v1/auth/me"), accessToken));
    }

    @Test
    void signupThenUseAccessToken() throws Exception {
        String auth = signup("Jane Streamer", "Jane@Example.com", "MARKETER");

        me(accessToken(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.userType").value("MARKETER"));
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        signup("Jane", "jane@example.com", "CUSTOMER");

        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'jane@example.com'", String.class);
        assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
    }

    @Test
    void refreshTokenIsStoredOnlyAsHash() throws Exception {
        String refresh = refreshToken(signup("Jane", "jane@example.com", "CUSTOMER"));

        Integer plain = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, refresh);
        Integer hashed = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, TokenHasher.sha256(refresh));
        assertThat(plain).isZero();
        assertThat(hashed).isEqualTo(1);
    }

    @Test
    void cannotSignUpAsAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Mallory","email":"m@example.com","userType":"ADMIN","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email = 'm@example.com'", Integer.class)).isZero();
    }

    @Test
    void signupWithExistingEmailReturns409() throws Exception {
        signup("Jane", "jane@example.com", "CUSTOMER");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Jane 2","email":"JANE@example.com","userType":"MARKETER",
                                 "password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithCorrectCredentialsIsCaseInsensitiveOnEmail() throws Exception {
        signup("Jane", "jane@example.com", "CUSTOMER");

        String auth = login("JANE@example.com", PASSWORD);

        me(accessToken(auth)).andExpect(status().isOk());
    }

    @Test
    void loginWithWrongPasswordOrUnknownEmailReturnsSame401() throws Exception {
        signup("Jane", "jane@example.com", "CUSTOMER");

        for (String body : new String[] {
                "{\"email\":\"jane@example.com\",\"password\":\"wrong-passw0rd\"}",
                "{\"email\":\"ghost@example.com\",\"password\":\"" + PASSWORD + "\"}"}) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        }
    }

    @Test
    void repeatedFailedLoginsLockTheAccountTemporarily() throws Exception {
        signup("Jane", "locked@example.com", "CUSTOMER");
        String wrong = "{\"email\":\"locked@example.com\",\"password\":\"wrong-passw0rd\"}";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"locked@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void refreshRotatesTokensAndDetectsReuse() throws Exception {
        String first = signup("Jane", "jane@example.com", "MARKETER");
        String firstRefresh = refreshToken(first);

        String second = refresh(firstRefresh)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondRefresh = refreshToken(second);
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);
        me(accessToken(second)).andExpect(status().isOk());

        refresh(firstRefresh).andExpect(status().isUnauthorized());
        refresh(secondRefresh).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String auth = signup("Jane", "jane@example.com", "MARKETER");

        logout(refreshToken(auth)).andExpect(status().isNoContent());

        refresh(refreshToken(auth)).andExpect(status().isUnauthorized());
        logout(refreshToken(auth)).andExpect(status().isNoContent());
        logout("unknown-token").andExpect(status().isNoContent());
    }

    @Test
    void logoutOnlyEndsThatSession() throws Exception {
        signup("Jane", "jane@example.com", "MARKETER");
        String laptop = login("jane@example.com", PASSWORD);
        String phone = login("jane@example.com", PASSWORD);

        logout(refreshToken(laptop)).andExpect(status().isNoContent());

        refresh(refreshToken(laptop)).andExpect(status().isUnauthorized());
        refresh(refreshToken(phone)).andExpect(status().isOk());
    }

    @Test
    void logoutAllEndsEverySession() throws Exception {
        signup("Jane", "jane@example.com", "MARKETER");
        String laptop = login("jane@example.com", PASSWORD);
        String phone = login("jane@example.com", PASSWORD);

        mockMvc.perform(bearer(post("/api/v1/auth/logout-all"), accessToken(laptop)))
                .andExpect(status().isNoContent());

        refresh(refreshToken(laptop)).andExpect(status().isUnauthorized());
        refresh(refreshToken(phone)).andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRequiresCurrentPasswordAndEndsAllSessions() throws Exception {
        String auth = signup("Jane", "jane@example.com", "MARKETER");

        mockMvc.perform(bearer(post("/api/v1/auth/change-password"), accessToken(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-passw0rd\",\"newPassword\":\"N3w-password\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(bearer(post("/api/v1/auth/change-password"), accessToken(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"N3w-password\"}".formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        refresh(refreshToken(auth)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
        login("jane@example.com", "N3w-password");
    }

    @Test
    void forgedOrGarbageAccessTokensAreRejected() throws Exception {
        String token = accessToken(signup("Jane", "jane@example.com", "MARKETER"));
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        me(tampered).andExpect(status().isUnauthorized());
        me("garbage").andExpect(status().isUnauthorized());
        me(refreshToken(signup("Bob", "bob@example.com", "CUSTOMER"))).andExpect(status().isUnauthorized());
    }

    @Test
    void bootstrapAdminCanLogInWithAdminRole() throws Exception {
        me(adminAccessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userType").value("ADMIN"))
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
    }

    @Test
    void deletedUsersRefreshTokensStopWorking() throws Exception {
        String auth = signup("Jane", "jane@example.com", "MARKETER");

        mockMvc.perform(bearer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/users/{id}", userId(auth)), accessToken(auth)))
                .andExpect(status().isNoContent());

        refresh(refreshToken(auth)).andExpect(status().isUnauthorized());
    }
}
