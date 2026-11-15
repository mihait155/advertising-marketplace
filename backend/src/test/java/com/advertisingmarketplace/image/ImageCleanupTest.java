package com.advertisingmarketplace.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.advertisingmarketplace.image.storage.ObjectStorage;
import com.advertisingmarketplace.image.storage.StorageException;
import com.advertisingmarketplace.support.MutableClock;
import com.advertisingmarketplace.support.TestEntities;
import com.advertisingmarketplace.user.UserType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.util.unit.DataSize;

class ImageCleanupTest {

    private final ImageRepository repository = mock(ImageRepository.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-02T10:00:00Z"));
    private final ImageCleanup cleanup = new ImageCleanup(repository, storage,
            new ImageProperties(DataSize.ofMegabytes(10), 20, 60, Duration.ofHours(24)), clock);

    @Test
    void deletesObjectThenRowForEachOrphanOlderThanRetention() {
        Image first = TestEntities.image(TestEntities.user(UserType.MARKETER));
        Image second = TestEntities.image(TestEntities.user(UserType.CUSTOMER));
        when(repository.findOrphansBefore(Instant.parse("2026-01-01T10:00:00Z"), Limit.of(100)))
                .thenReturn(List.of(first, second));

        assertThat(cleanup.purgeOrphans()).isEqualTo(2);

        verify(storage).delete(first.getStorageKey());
        verify(storage).delete(second.getStorageKey());
        verify(repository).delete(first);
        verify(repository).delete(second);
    }

    @Test
    void keepsRowWhenObjectDeletionFailsSoItIsRetried() {
        Image image = TestEntities.image(TestEntities.user(UserType.MARKETER));
        when(repository.findOrphansBefore(any(), any())).thenReturn(List.of(image));
        doThrow(new StorageException("down", null)).when(storage).delete(image.getStorageKey());

        assertThat(cleanup.purgeOrphans()).isZero();

        verify(repository, never()).delete(any(Image.class));
    }
}
