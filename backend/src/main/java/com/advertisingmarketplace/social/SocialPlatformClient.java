package com.advertisingmarketplace.social;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public abstract class SocialPlatformClient {

    protected final RestClient http;
    protected final JsonMapper json;
    protected final SocialProperties.Provider config;
    private final URI redirectUri;

    protected SocialPlatformClient(RestClient http, JsonMapper json, SocialProperties.Provider config,
            URI redirectUri) {
        this.http = http;
        this.json = json;
        this.config = config;
        this.redirectUri = redirectUri;
    }

    public abstract SocialProvider provider();

    public abstract ChannelSnapshot fetchChannel(String accessToken);

    protected abstract Map<String, String> extraAuthorizationParameters();

    public boolean isConfigured() {
        return config != null && config.isConfigured();
    }

    public URI authorizationUrl(String state) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", config.clientId());
        params.put("redirect_uri", redirectUri.toString());
        params.put("response_type", "code");
        params.put("scope", config.scope() == null ? "" : config.scope());
        params.put("state", state);
        params.putAll(extraAuthorizationParameters());
        String query = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return URI.create(config.authUrl() + "?" + query);
    }

    public OAuthTokens exchangeCode(String code) {
        MultiValueMap<String, String> form = baseForm();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri.toString());
        try {
            return tokens(post(form), null);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new SocialExceptions.AuthorizationFailedException(provider());
            }
            throw new SocialExceptions.ProviderUnavailableException(provider(), e);
        }
    }

    public OAuthTokens refresh(String refreshToken) {
        MultiValueMap<String, String> form = baseForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        try {
            return tokens(post(form), refreshToken);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 401) {
                throw new SocialExceptions.InvalidGrantException(e);
            }
            throw new SocialExceptions.ProviderUnavailableException(provider(), e);
        }
    }

    protected JsonNode get(URI uri, String accessToken, Map<String, String> headers) {
        try {
            String body = http.get().uri(uri)
                    .headers(h -> {
                        h.setBearerAuth(accessToken);
                        headers.forEach(h::set);
                    })
                    .retrieve()
                    .body(String.class);
            return json.readTree(body);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new SocialExceptions.ProviderUnavailableException(provider(), e);
        }
    }

    private MultiValueMap<String, String> baseForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        return form;
    }

    private JsonNode post(MultiValueMap<String, String> form) {
        try {
            String body = http.post().uri(config.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return json.readTree(body);
        } catch (ResourceAccessException e) {
            throw new SocialExceptions.ProviderUnavailableException(provider(), e);
        }
    }

    private static OAuthTokens tokens(JsonNode node, String previousRefreshToken) {
        String refresh = node.hasNonNull("refresh_token") ? node.get("refresh_token").asString() : previousRefreshToken;
        return new OAuthTokens(node.get("access_token").asString(), refresh);
    }

    protected static long asLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? 0 : Long.parseLong(value.asString());
    }
}
