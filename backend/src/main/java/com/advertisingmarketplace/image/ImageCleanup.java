package com.advertisingmarketplace.image;

import com.advertisingmarketplace.image.storage.ObjectStorage;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageCleanup {

    private static final int BATCH_SIZE = 100;

    private final ImageRepository imageRepository;
    private final ObjectStorage storage;
    private final ImageProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${app.images.cleanup-cron:0 0 * * * *}")
    public int purgeOrphans() {
        int deleted = 0;
        List<Image> batch;
        do {
            batch = imageRepository.findOrphansBefore(
                    clock.instant().minus(properties.orphanRetention()), Limit.of(BATCH_SIZE));
            int deletedInBatch = 0;
            for (Image image : batch) {
                try {
                    storage.delete(image.getStorageKey());
                    imageRepository.delete(image);
                    deletedInBatch++;
                } catch (RuntimeException e) {
                    log.warn("Could not delete orphaned image {}; will retry on the next run", image.getId(), e);
                }
            }
            deleted += deletedInBatch;
            if (deletedInBatch == 0) {
                break;
            }
        } while (batch.size() == BATCH_SIZE);
        if (deleted > 0) {
            log.info("Deleted {} orphaned images", deleted);
        }
        return deleted;
    }
}
