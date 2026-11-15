package com.advertisingmarketplace.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.jwt.secret=" + TestSecurityProperties.SECRET,
        "app.security.bootstrap-admin.email=" + AbstractIntegrationTest.ADMIN_EMAIL,
        "app.security.bootstrap-admin.password=" + AbstractIntegrationTest.ADMIN_PASSWORD,
        "app.security.login.max-failed-attempts=3",
        "app.images.cleanup-cron=-",
        "app.security.refresh-token-cleanup-cron=-",
        "app.exchange-rates.refresh-cron=-",
        "app.images.orphan-retention=0s",
        "app.messaging.max-messages-per-minute=20",
        "app.security.login.max-failed-attempts-per-account=6",
        "app.images.max-uploads-per-hour=5",
        "app.social.redirect-uri=https://app.marketplace.test/settings/connections/callback",
        "app.social.sync-cron=-",
        "app.deals.auto-complete-cron=-",
        "app.security.login-attempt-cleanup-cron=-",
        "app.notifications.email.cron=-",
        "app.notifications.email.delay=0s",
        "app.notifications.email.frontend-url=https://app.marketplace.test"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ExternalServicesConfiguration.class})
public abstract class AbstractIntegrationTest {

    protected static final String ADMIN_EMAIL = "admin@marketplace.test";
    protected static final String ADMIN_PASSWORD = "Admin-Passw0rd";
    protected static final String PASSWORD = "Sup3r-secret";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @org.springframework.boot.test.web.server.LocalServerPort
    protected int port;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM reviews");
        jdbcTemplate.update("DELETE FROM deliveries");
        jdbcTemplate.update("DELETE FROM deals");
        jdbcTemplate.update("DELETE FROM social_accounts");
        jdbcTemplate.update("DELETE FROM login_attempts");
        jdbcTemplate.update("DELETE FROM message_reports");
        jdbcTemplate.update("DELETE FROM user_blocks");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM offers");
        jdbcTemplate.update("DELETE FROM images");
        jdbcTemplate.update("DELETE FROM postings");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM users WHERE email <> ?", ADMIN_EMAIL);
    }

    protected String signup(String fullName, String email, String userType) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                        .header("X-Token-Delivery", "body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","email":"%s","phone":"+40712345678",
                                 "userType":"%s","password":"%s"}
                                """.formatted(fullName, email, userType, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    protected String login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Token-Delivery", "body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    protected String adminAccessToken() throws Exception {
        return accessToken(login(ADMIN_EMAIL, ADMIN_PASSWORD));
    }

    protected org.springframework.test.web.servlet.ResultActions upload(String accessToken, byte[] content)
            throws Exception {
        return mockMvc.perform(bearer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/v1/images")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "upload.bin",
                        "application/octet-stream", content)), accessToken));
    }

    protected String uploadImage(String accessToken, byte[] content) throws Exception {
        String body = upload(accessToken, content)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    protected static String accessToken(String authResponse) {
        return JsonPath.read(authResponse, "$.accessToken");
    }

    protected static String refreshToken(String authResponse) {
        return JsonPath.read(authResponse, "$.refreshToken");
    }

    protected static String userId(String authResponse) {
        return JsonPath.read(authResponse, "$.user.id");
    }

    protected static <T extends AbstractMockHttpServletRequestBuilder<?>> T bearer(T request, String accessToken) {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        return request;
    }
}
