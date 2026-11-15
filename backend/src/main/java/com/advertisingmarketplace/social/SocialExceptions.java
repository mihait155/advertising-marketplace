package com.advertisingmarketplace.social;

import com.advertisingmarketplace.common.exception.ApiException;
import com.advertisingmarketplace.common.exception.NotFoundException;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;

@UtilityClass
public class SocialExceptions {

    public static class ProviderNotAvailableException extends ApiException {

        public ProviderNotAvailableException(SocialProvider provider) {
            super(HttpStatus.CONFLICT, provider.displayName() + " connections aren't configured on this server");
        }
    }

    public static class InvalidOAuthStateException extends ApiException {

        public InvalidOAuthStateException() {
            super(HttpStatus.BAD_REQUEST, "The connection link expired or doesn't belong to you; please try again");
        }
    }

    public static class AuthorizationFailedException extends ApiException {

        public AuthorizationFailedException(SocialProvider provider) {
            super(HttpStatus.BAD_REQUEST, provider.displayName() + " didn't accept the authorization; please try again");
        }
    }

    public static class ProviderUnavailableException extends ApiException {

        public ProviderUnavailableException(SocialProvider provider, Throwable cause) {
            super(HttpStatus.BAD_GATEWAY, "Couldn't reach " + provider.displayName() + "; please try again later", cause);
        }
    }

    public static class InvalidGrantException extends RuntimeException {

        public InvalidGrantException(Throwable cause) {
            super(cause);
        }
    }

    public static class ChannelAlreadyLinkedException extends ApiException {

        public ChannelAlreadyLinkedException(SocialProvider provider) {
            super(HttpStatus.CONFLICT, "This " + provider.displayName() + " channel is connected to another account");
        }
    }

    public static class MarketersOnlyException extends ApiException {

        public MarketersOnlyException() {
            super(HttpStatus.FORBIDDEN, "Only marketers can connect audience accounts");
        }
    }

    public static class SocialAccountNotFoundException extends NotFoundException {

        public SocialAccountNotFoundException(UUID id) {
            super("Connected account " + id + " was not found");
        }
    }
}
