package com.advertisingmarketplace.image;

import com.advertisingmarketplace.common.observability.MarketplaceMetrics;
import com.advertisingmarketplace.image.ImageExceptions.EmptyFileException;
import com.advertisingmarketplace.image.ImageExceptions.FileTooLargeException;
import com.advertisingmarketplace.image.ImageExceptions.ImageNotFoundException;
import com.advertisingmarketplace.image.ImageExceptions.InfectedFileException;
import com.advertisingmarketplace.image.ImageExceptions.UnsupportedImageTypeException;
import com.advertisingmarketplace.image.ImageExceptions.UploadRateLimitException;
import com.advertisingmarketplace.image.dto.ImageResponse;
import com.advertisingmarketplace.image.scan.ScanResult;
import com.advertisingmarketplace.image.scan.VirusScanner;
import com.advertisingmarketplace.image.storage.ObjectStorage;
import com.advertisingmarketplace.user.UserNotFoundException;
import com.advertisingmarketplace.user.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final VirusScanner virusScanner;
    private final ObjectStorage storage;
    private final ImageProperties properties;
    private final Clock clock;
    private final MarketplaceMetrics metrics;

    public ImageResponse upload(UUID ownerId, byte[] content) {
        if (content.length == 0) {
            metrics.imageUpload("empty");
            throw new EmptyFileException();
        }
        if (content.length > properties.maxSize().toBytes()) {
            metrics.imageUpload("too_large");
            throw new FileTooLargeException(properties.maxSize().toBytes());
        }
        var owner = userRepository.findById(ownerId).orElseThrow(() -> new UserNotFoundException(ownerId));
        Instant hourAgo = clock.instant().minus(Duration.ofHours(1));
        if (imageRepository.countByOwnerIdAndCreatedAtAfter(ownerId, hourAgo) >= properties.maxUploadsPerHour()) {
            metrics.imageUpload("rate_limited");
            throw new UploadRateLimitException();
        }

        ScanResult scan;
        try {
            scan = metrics.observe("marketplace.images.scan", "clamav scan", () -> virusScanner.scan(content));
        } catch (RuntimeException e) {
            metrics.imageUpload("scan_unavailable");
            throw e;
        }
        if (!scan.clean()) {
            log.warn("Rejected upload from user {}: virus scanner found {}", ownerId, scan.signature());
            metrics.imageUpload("infected");
            throw new InfectedFileException();
        }
        ImageType type = ImageType.detect(content).orElse(null);
        if (type == null) {
            metrics.imageUpload("unsupported_type");
            throw new UnsupportedImageTypeException();
        }
        byte[] stored = ImageMetadataStripper.strip(content, type);

        String key = "images/" + UUID.randomUUID() + "." + type.extension();
        metrics.observe("marketplace.images.store", "s3 put", () -> {
            storage.put(key, stored, type.contentType());
            return key;
        });
        try {
            Image image = imageRepository.save(
                    new Image(owner, key, type.contentType(), stored.length, sha256(stored), clock.instant()));
            metrics.imageUpload("stored");
            return toResponse(image);
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public ImageResponse get(UUID imageId, UUID viewerId, boolean viewerIsAdmin) {
        Image image = imageRepository.findById(imageId).orElseThrow(() -> new ImageNotFoundException(imageId));
        boolean visible = viewerIsAdmin || image.isOwnedBy(viewerId)
                || (image.getPosting() != null && image.getPosting().isVisibleTo(viewerId, false))
                || (image.getMessage() != null && image.getMessage().getConversation().isParticipant(viewerId));
        if (!visible) {
            throw new ImageNotFoundException(imageId);
        }
        return toResponse(image);
    }

    public ImageResponse toResponse(Image image) {
        ObjectStorage.SignedUrl url = storage.signedGetUrl(image.getStorageKey());
        return new ImageResponse(image.getId(), image.getContentType(), image.getSizeBytes(), url.url(),
                url.expiresAt(), image.getCreatedAt());
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete object {} after failed upload; the orphan cleanup will not see it", key, e);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
