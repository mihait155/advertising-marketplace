package com.advertisingmarketplace.messaging.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class StompAuthInterceptorTest {

    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final StompAuthInterceptor interceptor = new StompAuthInterceptor(decoder);
    private final MessageChannel channel = mock(MessageChannel.class);

    private static Message<byte[]> frame(StompCommand command, String destination, String authorization,
            Authentication user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connectWithValidTokenSetsTheUser() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("good").header("alg", "HS256").subject(userId.toString())
                .claim("roles", List.of("MARKETER")).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(decoder.decode("good")).thenReturn(jwt);
        Message<byte[]> message = frame(StompCommand.CONNECT, null, "Bearer good", null);

        interceptor.preSend(message, channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        assertThat(accessor.getUser()).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(accessor.getUser().getName()).isEqualTo(userId.toString());
    }

    @Test
    void connectWithoutOrWithBadTokenIsRejected() {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("bad"));

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, "Bearer bad", null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscriptionsAreLimitedToOwnUserQueues() {
        Authentication user = mock(Authentication.class);

        interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/conversation-events", null, user), channel);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/queue/conversation-events", null,
                user), channel)).isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/x", null, null),
                channel)).isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void sendsAreLimitedToApplicationDestinations() {
        Authentication user = mock(Authentication.class);

        interceptor.preSend(frame(StompCommand.SEND, "/app/conversations/1/typing", null, user), channel);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/user/" + UUID.randomUUID()
                + "/queue/conversation-events", null, user), channel)).isInstanceOf(MessageDeliveryException.class);
    }
}
