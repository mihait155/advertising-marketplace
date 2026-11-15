package com.advertisingmarketplace.messaging.realtime;

import com.advertisingmarketplace.messaging.MessagingProperties;
import java.io.IOException;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@RequiredArgsConstructor
public class SseHub implements SmartLifecycle {

    private final MessagingProperties properties;
    private final Map<UUID, Deque<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private volatile boolean running;

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        emitters.values().forEach(userEmitters -> userEmitters.forEach(SseEmitter::complete));
        emitters.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        Deque<SseEmitter> userEmitters = emitters.computeIfAbsent(userId, id -> new ConcurrentLinkedDeque<>());
        userEmitters.addLast(emitter);
        while (userEmitters.size() > properties.maxStreamsPerUser()) {
            SseEmitter oldest = userEmitters.pollFirst();
            if (oldest != null) {
                oldest.complete();
            }
        }
        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        send(emitter, userId, SseEmitter.event().name("ready").data(Map.of("userId", userId),
                MediaType.APPLICATION_JSON));
        return emitter;
    }

    public int connectionCount() {
        return emitters.values().stream().mapToInt(Deque::size).sum();
    }

    public boolean isConnected(UUID userId) {
        Deque<SseEmitter> userEmitters = emitters.get(userId);
        return userEmitters != null && !userEmitters.isEmpty();
    }

    public void send(UUID userId, ConversationEventPayload payload) {
        Deque<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }
        for (SseEmitter emitter : userEmitters) {
            send(emitter, userId, SseEmitter.event()
                    .name(payload.type().name())
                    .data(payload, MediaType.APPLICATION_JSON));
        }
    }

    @Scheduled(fixedRateString = "${app.messaging.stream-heartbeat-ms:25000}")
    public void heartbeat() {
        emitters.forEach((userId, userEmitters) -> userEmitters
                .forEach(emitter -> send(emitter, userId, SseEmitter.event().comment("keep-alive"))));
    }

    private void send(SseEmitter emitter, UUID userId, SseEmitter.SseEventBuilder event) {
        try {
            synchronized (emitter) {
                emitter.send(event);
            }
        } catch (IOException | IllegalStateException e) {
            remove(userId, emitter);
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, userEmitters) -> {
            userEmitters.remove(emitter);
            return userEmitters.isEmpty() ? null : userEmitters;
        });
    }
}
