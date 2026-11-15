package com.advertisingmarketplace.social;

import java.net.URI;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class YouTubeClient extends SocialPlatformClient {

    public YouTubeClient(SocialHttp socialHttp, JsonMapper json, SocialProperties properties) {
        super(socialHttp.client(), json, properties.youtube(), properties.redirectUri());
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.YOUTUBE;
    }

    @Override
    protected Map<String, String> extraAuthorizationParameters() {
        return Map.of("access_type", "offline", "prompt", "consent", "include_granted_scopes", "true");
    }

    @Override
    public ChannelSnapshot fetchChannel(String accessToken) {
        JsonNode items = get(URI.create(config.apiUrl() + "/channels?part=snippet,statistics&mine=true"), accessToken,
                Map.of()).get("items");
        if (items == null || items.isEmpty()) {
            throw new SocialExceptions.AuthorizationFailedException(provider());
        }
        JsonNode channel = items.get(0);
        JsonNode snippet = channel.get("snippet");
        JsonNode statistics = channel.get("statistics");
        String id = channel.get("id").asString();
        String customUrl = snippet.hasNonNull("customUrl") ? snippet.get("customUrl").asString() : null;
        JsonNode thumbnail = snippet.path("thumbnails").path("default").path("url");
        return new ChannelSnapshot(id, customUrl != null ? customUrl : id, snippet.get("title").asString(),
                customUrl != null ? "https://www.youtube.com/" + customUrl : "https://www.youtube.com/channel/" + id,
                thumbnail.isMissingNode() ? null : thumbnail.asString(),
                statistics.path("hiddenSubscriberCount").asBoolean(false) ? 0 : asLong(statistics, "subscriberCount"),
                asLong(statistics, "viewCount"), asLong(statistics, "videoCount"));
    }
}
