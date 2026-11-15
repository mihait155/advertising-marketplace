package com.advertisingmarketplace.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

class RealtimeIT extends MessagingTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private SimpUserRegistry userRegistry;

    private final HttpClient http = HttpClient.newHttpClient();
    private final java.util.List<WebSocketStompClient> clients = new java.util.ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void stopClients() {
        clients.forEach(WebSocketStompClient::stop);
        clients.clear();
    }

    private String ticket(String token) throws Exception {
        String body = mockMvc.perform(bearer(post("/api/v1/conversations/stream-tickets"), token))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.ticket");
    }

    private record SseEvent(String name, String data) {
    }

    private BlockingQueue<SseEvent> openStream(String query, String authorization, Stream<String>[] holder)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + "/api/v1/conversations/events" + query))
                .header("Accept", "text/event-stream");
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        HttpResponse<Stream<String>> response = http.send(request.build(), HttpResponse.BodyHandlers.ofLines());
        assertThat(response.statusCode()).isEqualTo(200);
        holder[0] = response.body();
        BlockingQueue<SseEvent> events = new LinkedBlockingQueue<>();
        Thread.ofVirtual().start(() -> {
            String[] name = {null};
            response.body().forEach(line -> {
                if (line.startsWith("event:")) {
                    name[0] = line.substring(6).strip();
                } else if (line.startsWith("data:") && name[0] != null) {
                    events.add(new SseEvent(name[0], line.substring(5).strip()));
                    name[0] = null;
                }
            });
        });
        SseEvent ready = events.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(ready).isNotNull();
        assertThat(ready.name()).isEqualTo("ready");
        return events;
    }

    private SseEvent next(BlockingQueue<SseEvent> events, String name) throws InterruptedException {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            SseEvent event = events.poll(200, TimeUnit.MILLISECONDS);
            if (event != null && event.name().equals(name)) {
                return event;
            }
        }
        throw new AssertionError("No " + name + " event received");
    }

    @Test
    @SuppressWarnings("unchecked")
    void serverSentEventsDeliverNewMessagesUsingATicket() throws Exception {
        String conversation = conversationId(accessToken(streamer), postingId);
        Stream<String>[] stream = new Stream[1];
        BlockingQueue<SseEvent> events = openStream("?ticket=" + ticket(accessToken(brand)), null, stream);
        try {
            String message = sentId(send(accessToken(streamer), conversation, "Live hello"));

            SseEvent event = next(events, "MESSAGE_CREATED");
            assertThat((String) JsonPath.read(event.data(), "$.conversationId")).isEqualTo(conversation);
            assertThat((String) JsonPath.read(event.data(), "$.message.id")).isEqualTo(message);
            assertThat((String) JsonPath.read(event.data(), "$.message.text")).isEqualTo("Live hello");
            assertThat((String) JsonPath.read(event.data(), "$.actorId")).isEqualTo(userId(streamer));

            mockMvc.perform(bearer(post("/api/v1/conversations/{id}/read", conversation), accessToken(streamer)));
            SseEvent read = next(events, "CONVERSATION_READ");
            assertThat((String) JsonPath.read(read.data(), "$.actorId")).isEqualTo(userId(streamer));
        } finally {
            stream[0].close();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void streamAlsoAcceptsABearerHeader() throws Exception {
        Stream<String>[] stream = new Stream[1];
        openStream("", "Bearer " + accessToken(brand), stream);
        stream[0].close();
    }

    @Test
    void streamRejectsMissingInvalidAndAccessTokenTickets() throws Exception {
        for (String query : new String[] {"", "?ticket=garbage", "?ticket=" + accessToken(brand)}) {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(
                    "http://localhost:" + port + "/api/v1/conversations/events" + query)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(query).isEqualTo(401);
        }
    }

    private StompSession connect(String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        clients.add(client);
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        return client.connectAsync(URI.create("ws://localhost:" + port + "/ws"), new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() { }).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String userId) throws Exception {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/conversation-events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (userRegistry.findSubscriptions(s -> s.getSession().getUser().getName().equals(userId)
                && s.getDestination().equals("/user/queue/conversation-events")).isEmpty()) {
            assertThat(Instant.now()).isBefore(deadline);
            Thread.sleep(50);
        }
        return received;
    }

    @Test
    @SuppressWarnings("unchecked")
    void webSocketDeliversMessagesAndTypingIndicators() throws Exception {
        String conversation = conversationId(accessToken(streamer), postingId);
        StompSession brandSession = connect(accessToken(brand));
        StompSession streamerSession = connect(accessToken(streamer));
        try {
            BlockingQueue<Map<String, Object>> brandEvents = subscribe(brandSession, userId(brand));
            BlockingQueue<Map<String, Object>> streamerEvents = subscribe(streamerSession, userId(streamer));

            sentId(send(accessToken(streamer), conversation, "Over the socket"));
            Map<String, Object> created = brandEvents.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(created).isNotNull().containsEntry("type", "MESSAGE_CREATED");
            assertThat(((Map<String, Object>) created.get("message")).get("text")).isEqualTo("Over the socket");
            assertThat(streamerEvents.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                    .containsEntry("type", "MESSAGE_CREATED");

            brandSession.send("/app/conversations/" + conversation + "/typing", Map.of());
            Map<String, Object> typing = streamerEvents.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(typing).isNotNull()
                    .containsEntry("type", "TYPING")
                    .containsEntry("actorId", userId(brand))
                    .containsEntry("conversationId", conversation);
            assertThat(brandEvents.poll(500, TimeUnit.MILLISECONDS)).isNull();
        } finally {
            brandSession.disconnect();
            streamerSession.disconnect();
        }
    }

    @Test
    void webSocketRequiresAValidToken() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> connect("garbage")).isInstanceOf(Exception.class);
    }
}
