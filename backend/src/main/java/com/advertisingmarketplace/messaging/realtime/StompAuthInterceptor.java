package com.advertisingmarketplace.messaging.realtime;

import com.advertisingmarketplace.security.SecurityConfig;
import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;


@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    static final String USER_QUEUE_PREFIX = "/user/queue/";
    static final String APP_PREFIX = "/app/";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter converter;

    public StompAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
        this.converter = SecurityConfig.jwtAuthenticationConverter();
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT, STOMP -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> requireDestination(accessor, USER_QUEUE_PREFIX);
            case SEND -> requireDestination(accessor, APP_PREFIX);
            default -> {
            }
        }
        return message;
    }

    private Principal authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing bearer token in CONNECT frame");
        }
        try {
            AbstractAuthenticationToken authentication = converter.convert(jwtDecoder.decode(header.substring(7)));
            return authentication;
        } catch (JwtException e) {
            throw new MessageDeliveryException("Invalid or expired access token");
        }
    }

    private static void requireDestination(StompHeaderAccessor accessor, String prefix) {
        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("Not authenticated");
        }
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(prefix)) {
            throw new MessageDeliveryException("Destination not allowed: " + destination);
        }
    }
}
