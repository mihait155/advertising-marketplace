package com.advertisingmarketplace.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.support.AbstractIntegrationTest;
import com.advertisingmarketplace.support.FakeOAuthServer;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class SocialApiIT extends AbstractIntegrationTest {

    @Autowired
    private FakeOAuthServer oauth;

    @Autowired
    private SocialAccountService socialAccountService;

    private String streamer;

    @BeforeEach
    void setUp() {
        oauth.twitchFollowers.set(3400);
        oauth.twitchUserId.set("1001");
        oauth.refreshRevoked.set(false);
        oauth.tokenRequests.clear();
    }

    private Map<String, String> authorize(String token, String provider) throws Exception {
        String body = mockMvc.perform(bearer(post("/api/v1/social/{provider}/authorize", provider), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        URI url = URI.create(JsonPath.read(body, "$.url"));
        return Arrays.stream(url.getRawQuery().split("&")).map(p -> p.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
    }

    private ResultActions connect(String token, String code, String state) throws Exception {
        return mockMvc.perform(bearer(post("/api/v1/social/connect"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\",\"state\":\"%s\"}".formatted(code, state)));
    }

    @Test
    void marketerConnectsTwitchAndYoutubeAndStatsAppearOnTheProfile() throws Exception {
        streamer = accessToken(signup("Maya", "maya@example.com", "MARKETER"));
        mockMvc.perform(bearer(get("/api/v1/social/providers"), streamer))
                .andExpect(jsonPath("$[0].provider").value("TWITCH"))
                .andExpect(jsonPath("$[0].available").value(true));

        Map<String, String> params = authorize(streamer, "TWITCH");
        assertThat(params).containsEntry("client_id", FakeOAuthServer.CLIENT_ID)
                .containsEntry("response_type", "code")
                .containsEntry("redirect_uri", "https://app.marketplace.test/settings/connections/callback")
                .containsKey("state");
        connect(streamer, "good-code", params.get("state"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("TWITCH"))
                .andExpect(jsonPath("$.handle").value("mayaplays"))
                .andExpect(jsonPath("$.followers").value(3400))
                .andExpect(jsonPath("$.profileUrl").value("https://www.twitch.tv/mayaplays"));
        assertThat(oauth.tokenRequests.getFirst()).containsEntry("code", "good-code")
                .containsEntry("grant_type", "authorization_code")
                .containsEntry("redirect_uri", "https://app.marketplace.test/settings/connections/callback");

        connect(streamer, "good-code", authorize(streamer, "YOUTUBE").get("state"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.followers").value(128000))
                .andExpect(jsonPath("$.totalViews").value(9500000))
                .andExpect(jsonPath("$.profileUrl").value("https://www.youtube.com/@mayaplays"));

        mockMvc.perform(bearer(get("/api/v1/users/{id}/profile", userId(signupOrLogin())), streamer))
                .andExpect(jsonPath("$.verifiedReach").value(131400))
                .andExpect(jsonPath("$.accounts", hasSize(2)))
                .andExpect(jsonPath("$.accounts[0].provider").value("YOUTUBE"));

        String stored = jdbcTemplate.queryForObject(
                "SELECT encrypted_refresh_token FROM social_accounts WHERE provider = 'TWITCH'", String.class);
        assertThat(stored).startsWith("v1:").doesNotContain("refresh-");
    }

    private String signupOrLogin() throws Exception {
        return login("maya@example.com", PASSWORD);
    }

    @Test
    void stateCannotBeReusedByAnotherUserOrForged() throws Exception {
        streamer = accessToken(signup("Maya", "maya@example.com", "MARKETER"));
        String other = accessToken(signup("Bob", "bob@example.com", "MARKETER"));
        String state = authorize(streamer, "TWITCH").get("state");

        connect(other, "good-code", state).andExpect(status().isBadRequest());
        connect(streamer, "good-code", "forged").andExpect(status().isBadRequest());
        connect(streamer, "bad-code", state).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Twitch didn't accept the authorization; please try again"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM social_accounts", Integer.class)).isZero();
    }

    @Test
    void onlyMarketersConnectAndAChannelBelongsToOneAccount() throws Exception {
        String brand = accessToken(signup("Acme", "acme@example.com", "CUSTOMER"));
        mockMvc.perform(bearer(post("/api/v1/social/TWITCH/authorize"), brand)).andExpect(status().isForbidden());

        streamer = accessToken(signup("Maya", "maya@example.com", "MARKETER"));
        connect(streamer, "good-code", authorize(streamer, "TWITCH").get("state")).andExpect(status().isCreated());
        String impostor = accessToken(signup("Imposter", "imp@example.com", "MARKETER"));
        connect(impostor, "good-code", authorize(impostor, "TWITCH").get("state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This Twitch channel is connected to another account"));
    }

    @Test
    void dailySyncUpdatesStatsAndFlagsRevokedAccounts() throws Exception {
        streamer = accessToken(signup("Maya", "maya@example.com", "MARKETER"));
        connect(streamer, "good-code", authorize(streamer, "TWITCH").get("state")).andExpect(status().isCreated());

        oauth.twitchFollowers.set(5200);
        assertThat(socialAccountService.syncAll()).isEqualTo(1);
        mockMvc.perform(bearer(get("/api/v1/social/accounts"), streamer))
                .andExpect(jsonPath("$[0].followers").value(5200))
                .andExpect(jsonPath("$[0].needsReconnect").value(false));
        assertThat(oauth.tokenRequests.getLast()).containsEntry("grant_type", "refresh_token")
                .containsEntry("refresh_token", "refresh-1");

        oauth.refreshRevoked.set(true);
        oauth.twitchFollowers.set(9999);
        assertThat(socialAccountService.syncAll()).isZero();
        String accounts = mockMvc.perform(bearer(get("/api/v1/social/accounts"), streamer))
                .andExpect(jsonPath("$[0].followers").value(5200))
                .andExpect(jsonPath("$[0].needsReconnect").value(true))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(bearer(delete("/api/v1/social/accounts/{id}", (String) JsonPath.read(accounts, "$[0].id")),
                        streamer))
                .andExpect(status().isNoContent());
        mockMvc.perform(bearer(get("/api/v1/social/accounts"), streamer)).andExpect(jsonPath("$", hasSize(0)));
    }
}
