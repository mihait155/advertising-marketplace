package com.advertisingmarketplace.image;

import com.advertisingmarketplace.common.exception.ApiException;
import com.advertisingmarketplace.common.exception.NotFoundException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;

@UtilityClass
public class ImageExceptions {

    public static class ImageNotFoundException extends NotFoundException {

        public ImageNotFoundException(UUID id) {
            super("Image with id " + id + " was not found");
        }
    }

    public static class EmptyFileException extends ApiException {

        public EmptyFileException() {
            super(HttpStatus.BAD_REQUEST, "The uploaded file is empty");
        }
    }

    public static class FileTooLargeException extends ApiException {

        public FileTooLargeException(long maxBytes) {
            super(HttpStatus.CONTENT_TOO_LARGE, "The uploaded file exceeds the maximum size of " + maxBytes + " bytes");
        }
    }

    public static class UnsupportedImageTypeException extends ApiException {

        public UnsupportedImageTypeException() {
            super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only JPEG, PNG, GIF and WebP images are accepted");
        }
    }

    public static class UploadRateLimitException extends ApiException {

        public UploadRateLimitException() {
            super(HttpStatus.TOO_MANY_REQUESTS, "You've uploaded a lot of images recently; try again later");
        }

        @Override
        public Optional<Duration> retryAfter() {
            return Optional.of(Duration.ofMinutes(10));
        }
    }

    public static class InfectedFileException extends ApiException {

        public InfectedFileException() {
            super(HttpStatus.UNPROCESSABLE_CONTENT, "The file was rejected by the virus scanner");
        }
    }
}
