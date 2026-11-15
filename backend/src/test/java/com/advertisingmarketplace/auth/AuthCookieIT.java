package com.advertisingmarketplace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class AuthCookieIT extends AbstractIntegrationTest {

    private static final String EMAIL = "jane@example.com";

    @BeforeEach
    void createUser() throws Exception {
        signup("Jane", EMAIL, "MARKETER");
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private ResultActions browserLogin(String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(from(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, password)));
    }

    private static Cookie refreshCookie(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie("am_refresh");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    @Test
    void browserSessionsUseAnHttpOnlyCookieInsteadOfTheBody() throws Exception {
        MockHttpServletResponse login = browserLogin(PASSWORD, "127.0.0.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();
        Cookie first = refreshCookie(login);
        assertThat(first.isHttpOnly()).isTrue();
        assertThat(first.getSecure()).isTrue();
        assertThat(first.getPath()).isEqualTo("/api/v1/auth");
        assertThat(login.getHeader("Set-Cookie")).contains("SameSite=Strict");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(first)).andExpect(status().isForbidden());

        MockHttpServletResponse refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(first)
                        .header("X-Requested-With", "fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();
        Cookie second = refreshCookie(refreshed);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(first).header("X-Requested-With", "fetch"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(second).header("X-Requested-With", "fetch"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheCookieSession() throws Exception {
        Cookie cookie = refreshCookie(browserLogin(PASSWORD, "127.0.0.1").andReturn().getResponse());

        MockHttpServletResponse logout = mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie)
                        .header("X-Requested-With", "fetch"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        assertThat(logout.getCookie("am_refresh").getMaxAge()).isZero();
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie).header("X-Requested-With", "fetch"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lockoutIsPerClientWithAWiderAccountLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            browserLogin("wrong-passw0rd", "198.51.100.1").andExpect(status().isUnauthorized());
        }
        browserLogin(PASSWORD, "198.51.100.1").andExpect(status().isTooManyRequests());

        browserLogin(PASSWORD, "198.51.100.2").andExpect(status().isOk());
        for (int i = 0; i < 3; i++) {
            browserLogin("wrong-passw0rd", "198.51.100.3").andExpect(status().isUnauthorized());
        }

        browserLogin(PASSWORD, "198.51.100.4").andExpect(status().isTooManyRequests());
        assertThat(jdbcTemplate.queryForObject("SELECT failures FROM login_attempts WHERE attempt_key = ?",
                Integer.class, "account:" + EMAIL)).isEqualTo(6);
    }
}
