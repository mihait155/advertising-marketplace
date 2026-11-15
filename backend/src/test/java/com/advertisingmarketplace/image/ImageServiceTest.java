package com.advertisingmarketplace.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.advertisingmarketplace.common.observability.MarketplaceMetrics;
import com.advertisingmarketplace.image.ImageExceptions.EmptyFileException;
import com.advertisingmarketplace.image.ImageExceptions.FileTooLargeException;
import com.advertisingmarketplace.image.ImageExceptions.ImageNotFoundException;
import com.advertisingmarketplace.image.ImageExceptions.InfectedFileException;
import com.advertisingmarketplace.image.ImageExceptions.UnsupportedImageTypeException;
import com.advertisingmarketplace.image.dto.ImageResponse;
import com.advertisingmarketplace.image.scan.ScanResult;
import com.advertisingmarketplace.image.scan.VirusScanUnavailableException;
import com.advertisingmarketplace.image.scan.VirusScanner;
import com.advertisingmarketplace.image.storage.ObjectStorage;
import com.advertisingmarketplace.image.storage.StorageException;
import com.advertisingmarketplace.posting.PostingCategory;
import com.advertisingmarketplace.posting.PostingStatus;
import com.advertisingmarketplace.support.MutableClock;
import com.advertisingmarketplace.support.TestEntities;
import com.advertisingmarketplace.support.TestFiles;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserRepository;
import com.advertisingmarketplace.user.UserType;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;

class ImageServiceTest {

    private final ImageRepository imageRepository = mock(ImageRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final VirusScanner scanner = mock(VirusScanner.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final ImageService service = new ImageService(imageRepository, userRepository, scanner, storage,
            new ImageProperties(DataSize.ofKilobytes(1), 20, 3, Duration.ofHours(24)), clock, MarketplaceMetrics.noop());

    private final User owner = TestEntities.user(UserType.MARKETER);

    @BeforeEach
    void setUp() {
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(storage.signedGetUrl(anyString())).thenAnswer(inv -> new ObjectStorage.SignedUrl(
                URI.create("https://bucket.example/" + inv.getArgument(0)), clock.instant().plusSeconds(3600)));
        when(imageRepository.save(any(Image.class))).thenAnswer(inv -> TestEntities.withId(
                inv.getArgument(0), UUID.randomUUID()));
    }

    @Test
    void cleanImageIsScannedThenStoredThenRecorded() {
        byte[] png = TestFiles.png();
        when(scanner.scan(png)).thenReturn(ScanResult.ok());

        ImageResponse response = service.upload(owner.getId(), png);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        var order = org.mockito.Mockito.inOrder(scanner, storage, imageRepository);
        order.verify(scanner).scan(png);
        order.verify(storage).put(key.capture(), eq(png), eq("image/png"));
        order.verify(imageRepository).save(any(Image.class));
        assertThat(key.getValue()).matches("images/[0-9a-f-]{36}\\.png");

        ArgumentCaptor<Image> saved = ArgumentCaptor.forClass(Image.class);
        verify(imageRepository).save(saved.capture());
        assertThat(saved.getValue().getOwner()).isSameAs(owner);
        assertThat(saved.getValue().getSizeBytes()).isEqualTo(png.length);
        assertThat(saved.getValue().getSha256()).hasSize(64);
        assertThat(saved.getValue().isAttached()).isFalse();
        assertThat(saved.getValue().getOrphanedSince()).isEqualTo(clock.instant());
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.url().toString()).endsWith(key.getValue());
    }

    @Test
    void infectedFileIsNeverStored() {
        byte[] eicar = TestFiles.eicar();
        when(scanner.scan(eicar)).thenReturn(ScanResult.infected("Eicar-Test-Signature"));

        assertThatThrownBy(() -> service.upload(owner.getId(), eicar)).isInstanceOf(InfectedFileException.class);
        verifyNoInteractions(storage);
        verify(imageRepository, never()).save(any());
    }

    @Test
    void scannerUnavailableFailsClosed() {
        byte[] png = TestFiles.png();
        when(scanner.scan(png)).thenThrow(new VirusScanUnavailableException("down"));

        assertThatThrownBy(() -> service.upload(owner.getId(), png))
                .isInstanceOf(VirusScanUnavailableException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void cleanButNonImageFileIsRejectedAfterScanning() {
        byte[] pdf = "%PDF-1.7 ...".getBytes();
        when(scanner.scan(pdf)).thenReturn(ScanResult.ok());

        assertThatThrownBy(() -> service.upload(owner.getId(), pdf))
                .isInstanceOf(UnsupportedImageTypeException.class);
        verify(scanner).scan(pdf);
        verifyNoInteractions(storage);
    }

    @Test
    void emptyAndOversizedFilesAreRejectedBeforeScanning() {
        assertThatThrownBy(() -> service.upload(owner.getId(), new byte[0])).isInstanceOf(EmptyFileException.class);
        assertThatThrownBy(() -> service.upload(owner.getId(), new byte[1025]))
                .isInstanceOf(FileTooLargeException.class);
        verifyNoInteractions(scanner, storage);
    }

    @Test
    void storedObjectIsRemovedIfDatabaseInsertFails() {
        byte[] png = TestFiles.png();
        when(scanner.scan(png)).thenReturn(ScanResult.ok());
        when(imageRepository.save(any(Image.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.upload(owner.getId(), png)).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), any());
        verify(storage).delete(key.getValue());
    }

    @Test
    void storageFailureIsReported() {
        byte[] png = TestFiles.png();
        when(scanner.scan(png)).thenReturn(ScanResult.ok());
        doThrow(new StorageException("s3 down", null)).when(storage).put(anyString(), any(), anyString());

        assertThatThrownBy(() -> service.upload(owner.getId(), png)).isInstanceOf(StorageException.class);
        verify(imageRepository, never()).save(any());
    }

    @Test
    void unattachedImageIsOnlyVisibleToOwnerAndAdmins() {
        Image image = TestEntities.image(owner);
        when(imageRepository.findById(image.getId())).thenReturn(Optional.of(image));

        assertThat(service.get(image.getId(), owner.getId(), false).id()).isEqualTo(image.getId());
        assertThat(service.get(image.getId(), UUID.randomUUID(), true).id()).isEqualTo(image.getId());
        assertThatThrownBy(() -> service.get(image.getId(), UUID.randomUUID(), false))
                .isInstanceOf(ImageNotFoundException.class);
    }

    @Test
    void imageInDraftPostingIsOnlyVisibleToOwnerAndAdmins() {
        Image image = TestEntities.image(owner);
        image.attachTo(TestEntities.posting(owner, PostingCategory.MARKETER_POSTING, PostingStatus.DRAFT, "x"));
        when(imageRepository.findById(image.getId())).thenReturn(Optional.of(image));

        assertThat(service.get(image.getId(), owner.getId(), false).id()).isEqualTo(image.getId());
        assertThat(service.get(image.getId(), UUID.randomUUID(), true).id()).isEqualTo(image.getId());
        assertThatThrownBy(() -> service.get(image.getId(), UUID.randomUUID(), false))
                .isInstanceOf(ImageNotFoundException.class);
    }

    @Test
    void attachedImageIsVisibleToEveryone() {
        Image image = TestEntities.image(owner);
        image.attachTo(TestEntities.posting(owner, PostingCategory.MARKETER_POSTING, "x"));
        when(imageRepository.findById(image.getId())).thenReturn(Optional.of(image));

        assertThat(service.get(image.getId(), UUID.randomUUID(), false).id()).isEqualTo(image.getId());
    }
}
