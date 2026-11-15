package com.advertisingmarketplace.image.storage;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final S3Properties properties;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    void createBucketIfConfigured() {
        if (!properties.createBucket()) {
            return;
        }
        try {
            s3.headBucket(b -> b.bucket(properties.bucket()));
        } catch (NoSuchBucketException e) {
            s3.createBucket(b -> b.bucket(properties.bucket()));
            log.info("Created bucket {}", properties.bucket());
        } catch (SdkException e) {
            log.warn("Could not check/create bucket {}: {}", properties.bucket(), e.getMessage());
        }
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            s3.putObject(b -> b.bucket(properties.bucket())
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) content.length)
                            .contentDisposition("inline")
                            .cacheControl("private, max-age=3600")
                            .serverSideEncryption(properties.endpoint() == null || properties.endpoint().isBlank()
                                    ? ServerSideEncryption.AES256
                                    : null),
                    RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new StorageException("Failed to store object " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(b -> b.bucket(properties.bucket()).key(key));
        } catch (SdkException e) {
            throw new StorageException("Failed to delete object " + key, e);
        }
    }

    @Override
    public SignedUrl signedGetUrl(String key) {
        PresignedGetObjectRequest request = presigner.presignGetObject(b -> b
                .signatureDuration(properties.signedUrlTtl())
                .getObjectRequest(get -> get.bucket(properties.bucket()).key(key)));
        try {
            return new SignedUrl(request.url().toURI(), clock.instant().plus(properties.signedUrlTtl()).truncatedTo(ChronoUnit.SECONDS));
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
