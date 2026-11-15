package com.advertisingmarketplace.messaging.realtime;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostgresEventListener implements SmartLifecycle {

    private static final int POLL_TIMEOUT_MS = 2_000;
    private static final Duration RECONNECT_DELAY = Duration.ofSeconds(2);

    private final DataSource dataSource;
    private final JsonMapper jsonMapper;
    private final ConversationEventDispatcher dispatcher;

    private volatile boolean running;
    private volatile Thread thread;

    @Override
    public void start() {
        running = true;
        thread = Thread.ofPlatform().name("conversation-events-listener").daemon().start(this::loop);
    }

    @Override
    public void stop() {
        running = false;
        Thread current = thread;
        if (current != null) {
            current.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running) {
            try (Connection connection = dataSource.getConnection()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("LISTEN " + PostgresEventPublisher.CHANNEL);
                }
                if (!connection.getAutoCommit()) {
                    connection.commit();
                }
                PGConnection pg = connection.unwrap(PGConnection.class);
                log.info("Listening for conversation events");
                while (running) {
                    PGNotification[] notifications = pg.getNotifications(POLL_TIMEOUT_MS);
                    if (notifications == null) {
                        continue;
                    }
                    for (PGNotification notification : notifications) {
                        handle(notification.getParameter());
                    }
                }
            } catch (SQLException e) {
                if (running) {
                    log.warn("Conversation event listener lost its connection; reconnecting: {}", e.getMessage());
                    sleep();
                }
            }
        }
    }

    private void handle(String payload) {
        try {
            dispatcher.dispatch(jsonMapper.readValue(payload, ConversationEvent.class));
        } catch (RuntimeException e) {
            log.warn("Could not dispatch conversation event", e);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(RECONNECT_DELAY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
