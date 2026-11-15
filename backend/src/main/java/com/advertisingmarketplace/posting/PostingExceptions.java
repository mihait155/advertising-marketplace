package com.advertisingmarketplace.posting;

import com.advertisingmarketplace.common.exception.ApiException;
import com.advertisingmarketplace.common.exception.NotFoundException;
import com.advertisingmarketplace.user.UserType;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;

@UtilityClass
public class PostingExceptions {

    public static class PostingNotFoundException extends NotFoundException {

        public PostingNotFoundException(UUID id) {
            super("Posting with id " + id + " was not found");
        }
    }

    public static class CategoryNotAllowedException extends ApiException {

        public CategoryNotAllowedException(UserType userType, PostingCategory category) {
            super(HttpStatus.FORBIDDEN, userType + " users cannot publish " + category + " postings");
        }
    }

    public static class NotPostingOwnerException extends ApiException {

        public NotPostingOwnerException() {
            super(HttpStatus.FORBIDDEN, "Only the creator of a posting or an admin can change it");
        }
    }

    public static class PostingClosedException extends ApiException {

        public PostingClosedException() {
            super(HttpStatus.CONFLICT, "Closed postings cannot be edited; reopen the posting first");
        }
    }

    public static class CurrencyChangeConflictException extends ApiException {

        public CurrencyChangeConflictException() {
            super(HttpStatus.CONFLICT,
                    "The pay currency cannot change while there are pending offers in another currency");
        }
    }

    public static class InvalidPayFilterException extends ApiException {

        public InvalidPayFilterException() {
            super(HttpStatus.BAD_REQUEST, "minPay must not be greater than maxPay");
        }
    }

    public static class UnsupportedSortException extends ApiException {

        public UnsupportedSortException(String property) {
            super(HttpStatus.BAD_REQUEST,
                    "Cannot sort by '" + property + "'; use createdAt, updatedAt, title or pay");
        }
    }

    public static class InvalidPostingStatusTransitionException extends ApiException {

        public InvalidPostingStatusTransitionException(PostingStatus from, PostingStatus to) {
            super(HttpStatus.CONFLICT, "A posting cannot go from " + from + " to " + to);
        }
    }
}
