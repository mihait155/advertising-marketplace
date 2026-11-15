package com.advertisingmarketplace.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class FakeOAuthServer implements AutoCloseable {

    public static final String CLIENT_ID = "test-client";
    public static final String CLIENT_SECRET = "test-secret";

    private final HttpServer server;
    public final AtomicLong twitchFollowers = new AtomicLong(3400);
    public final AtomicLong youtubeSubscribers = new AtomicLong(128_000);
    public final AtomicReference<String> twitchUserId = new AtomicReference<>("1001");
    public final AtomicBoolean refreshRevoked = new AtomicBoolean(false);
    public final List<Map<String, String>> tokenRequests = new CopyOnWriteArrayList<>();
    private final AtomicLong tokenCounter = new AtomicLong();

    public FakeOAuthServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/twitch/token", this::token);
        server.createContext("/youtube/token", this::token);
        server.createContext("/twitch/helix/users", exchange -> respond(exchange, 200, """
                {"data":[{"id":"%s","login":"mayaplays","display_name":"MayaPlays",
                          "profile_image_url":"https://static.example/maya.png"}]}
                """.formatted(twitchUserId.get())));
        server.createContext("/twitch/helix/channels/followers", exchange -> respond(exchange, 200,
                "{\"total\":" + twitchFollowers.get() + ",\"data\":[]}"));
        server.createContext("/youtube/v3/channels", exchange -> respond(exchange, 200, """
                {"items":[{"id":"UC123","snippet":{"title":"Maya Plays","customUrl":"@mayaplays",
                  "thumbnails":{"default":{"url":"https://yt.example/maya.jpg"}}},
                  "statistics":{"subscriberCount":"%d","viewCount":"9500000","videoCount":"312",
                                "hiddenSubscriberCount":false}}]}
                """.formatted(youtubeSubscribers.get())));
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void token(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = Arrays.stream(body.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> p.length > 1 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8)
                        : ""));
        tokenRequests.add(form);
        if (!CLIENT_ID.equals(form.get("client_id")) || !CLIENT_SECRET.equals(form.get("client_secret"))) {
            respond(exchange, 401, "{\"error\":\"invalid_client\"}");
            return;
        }
        if ("refresh_token".equals(form.get("grant_type")) && refreshRevoked.get()) {
            respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        if ("authorization_code".equals(form.get("grant_type")) && "bad-code".equals(form.get("code"))) {
            respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        long n = tokenCounter.incrementAndGet();
        respond(exchange, 200, """
                {"access_token":"access-%d","refresh_token":"refresh-%d","expires_in":3600,"token_type":"bearer"}
                """.formatted(n, n));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
