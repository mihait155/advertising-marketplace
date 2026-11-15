package com.advertisingmarketplace.offer;

import com.advertisingmarketplace.common.exception.ApiException;
import com.advertisingmarketplace.common.exception.NotFoundException;
import com.advertisingmarketplace.posting.PostingCategory;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;

@UtilityClass
public class OfferExceptions {

    public static class OfferNotFoundException extends NotFoundException {

        public OfferNotFoundException(UUID id) {
            super("Offer with id " + id + " was not found");
        }
    }

    public static class PostingNotOpenException extends ApiException {

        public PostingNotOpenException() {
            super(HttpStatus.CONFLICT, "Offers can only be made on, changed on or accepted for OPEN postings");
        }
    }

    public static class OwnPostingOfferException extends ApiException {

        public OwnPostingOfferException() {
            super(HttpStatus.FORBIDDEN, "You cannot make an offer on your own posting");
        }
    }

    public static class OfferMakerNotAllowedException extends ApiException {

        public OfferMakerNotAllowedException(PostingCategory category) {
            super(HttpStatus.FORBIDDEN,
                    "Only " + category.offerMakerType() + " users can make offers on " + category + " postings");
        }
    }

    public static class DuplicatePendingOfferException extends ApiException {

        public DuplicatePendingOfferException() {
            super(HttpStatus.CONFLICT, "You already have a pending offer on this posting; update or withdraw it");
        }
    }

    public static class OfferNotPendingException extends ApiException {

        public OfferNotPendingException(OfferStatus status) {
            super(HttpStatus.CONFLICT, "The offer is " + status + " and can no longer be changed");
        }
    }

    public static class OfferActionNotAllowedException extends ApiException {

        public OfferActionNotAllowedException(String message) {
            super(HttpStatus.FORBIDDEN, message);
        }
    }

    public static class InvalidOfferStatusException extends ApiException {

        public InvalidOfferStatusException() {
            super(HttpStatus.BAD_REQUEST, "status must be ACCEPTED, REJECTED or WITHDRAWN");
        }
    }

    public static class CurrencyMismatchException extends ApiException {

        public CurrencyMismatchException(String postingCurrency) {
            super(HttpStatus.BAD_REQUEST, "The offer must be in the posting's currency (" + postingCurrency + ")");
        }
    }

    public static class CurrencyRequiredException extends ApiException {

        public CurrencyRequiredException() {
            super(HttpStatus.BAD_REQUEST, "currency is required because the posting has no pay currency");
        }
    }

    public static class AdminOfferException extends ApiException {

        public AdminOfferException() {
            super(HttpStatus.FORBIDDEN, "Admins cannot make offers");
        }
    }
}
