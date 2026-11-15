package com.advertisingmarketplace.messaging;

import com.advertisingmarketplace.common.exception.ApiException;
import com.advertisingmarketplace.common.exception.NotFoundException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;


@UtilityClass
public class MessagingExceptions {

    public static class ConversationNotFoundException extends NotFoundException {

        public ConversationNotFoundException(UUID id) {
            super("Conversation with id " + id + " was not found");
        }
    }

    public static class MessageNotFoundException extends NotFoundException {

        public MessageNotFoundException(UUID id) {
            super("Message with id " + id + " was not found");
        }
    }

    public static class ConversationNotAllowedException extends ApiException {

        public ConversationNotAllowedException(String message) {
            super(HttpStatus.FORBIDDEN, message);
        }
    }

    public static class BlockedException extends ApiException {

        public BlockedException() {
            super(HttpStatus.FORBIDDEN, "You can't message this user");
        }
    }

    public static class ParticipantGoneException extends ApiException {

        public ParticipantGoneException() {
            super(HttpStatus.CONFLICT, "The other participant has deleted their account");
        }
    }

    public static class MessageNotEditableException extends ApiException {

        public MessageNotEditableException(String message) {
            super(HttpStatus.FORBIDDEN, message);
        }
    }

    public static class InvalidMessageException extends ApiException {

        public InvalidMessageException(String message) {
            super(HttpStatus.BAD_REQUEST, message);
        }
    }

    @Getter
    public static class MessageRateLimitException extends ApiException {

        private final Duration retryAfter;

        public MessageRateLimitException(Duration retryAfter) {
            super(HttpStatus.TOO_MANY_REQUESTS, "You are sending messages too quickly; slow down");
            this.retryAfter = retryAfter;
        }

        @Override
        public Optional<Duration> retryAfter() {
            return Optional.of(retryAfter);
        }
    }

    public static class InvalidStreamTicketException extends ApiException {

        public InvalidStreamTicketException() {
            super(HttpStatus.UNAUTHORIZED, "The stream ticket is missing, invalid or expired");
        }
    }
}
