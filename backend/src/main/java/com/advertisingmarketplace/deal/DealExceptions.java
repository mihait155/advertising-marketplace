package com.advertisingmarketplace.deal;

import com.advertisingmarketplace.common.exception.ApiException;
import com.advertisingmarketplace.common.exception.NotFoundException;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;

@UtilityClass
public class DealExceptions {

    public static class DealNotFoundException extends NotFoundException {

        public DealNotFoundException(UUID id) {
            super("Deal with id " + id + " was not found");
        }
    }

    public static class DealActionNotAllowedException extends ApiException {

        public DealActionNotAllowedException(String message) {
            super(HttpStatus.FORBIDDEN, message);
        }
    }

    public static class InvalidDealStateException extends ApiException {

        public InvalidDealStateException(DealStatus status, String action) {
            super(HttpStatus.CONFLICT, "A deal that is " + status + " can't be " + action);
        }
    }

    public static class ActiveDealsException extends ApiException {

        public ActiveDealsException(String message) {
            super(HttpStatus.CONFLICT, message);
        }
    }

    public static class InvalidAgreedAmountException extends ApiException {

        public InvalidAgreedAmountException(String message) {
            super(HttpStatus.BAD_REQUEST, message);
        }
    }
}
