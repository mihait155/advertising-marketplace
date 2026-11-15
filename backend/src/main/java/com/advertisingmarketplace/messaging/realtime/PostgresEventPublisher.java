package com.advertisingmarketplace.messaging.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class PostgresEventPublisher implements ConversationEventPublisher {

    public static final String CHANNEL = "conversation_events";

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void publish(ConversationEvent event) {
        String payload = jsonMapper.writeValueAsString(event);
        jdbcTemplate.query("select pg_notify(?, ?)", rs -> null, CHANNEL, payload);
    }
}
