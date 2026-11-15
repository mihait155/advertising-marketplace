package com.advertisingmarketplace.social;

import java.net.URI;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TwitchClient extends SocialPlatformClient {

    public TwitchClient(SocialHttp socialHttp, JsonMapper json, SocialProperties properties) {
        super(socialHttp.client(), json, properties.twitch(), properties.redirectUri());
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.TWITCH;
    }

    @Override
    protected Map<String, String> extraAuthorizationParameters() {
        return Map.of("force_verify", "true");
    }

    @Override
    public ChannelSnapshot fetchChannel(String accessToken) {
        Map<String, String> headers = Map.of("Client-Id", config.clientId());
        JsonNode user = get(URI.create(config.apiUrl() + "/users"), accessToken, headers).get("data").get(0);
        String id = user.get("id").asString();
        JsonNode followers = get(URI.create(config.apiUrl() + "/channels/followers?broadcaster_id=" + id), accessToken,
                headers);
        String login = user.get("login").asString();
        return new ChannelSnapshot(id, login, user.get("display_name").asString(), "https://www.twitch.tv/" + login,
                user.hasNonNull("profile_image_url") ? user.get("profile_image_url").asString() : null,
                asLong(followers, "total"), null, null);
    }
}
