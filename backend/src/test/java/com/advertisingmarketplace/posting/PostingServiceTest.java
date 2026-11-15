package com.advertisingmarketplace.posting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.advertisingmarketplace.common.money.AmountPrecisionException;
import com.advertisingmarketplace.common.money.ExchangeRateService;
import com.advertisingmarketplace.common.observability.MarketplaceMetrics;
import com.advertisingmarketplace.deal.DealRepository;
import com.advertisingmarketplace.image.Image;
import com.advertisingmarketplace.image.ImageProperties;
import com.advertisingmarketplace.image.ImageRepository;
import com.advertisingmarketplace.image.ImageService;
import com.advertisingmarketplace.image.scan.VirusScanner;
import com.advertisingmarketplace.image.storage.ObjectStorage;
import com.advertisingmarketplace.messaging.MessagingService;
import com.advertisingmarketplace.messaging.SystemEvent;
import com.advertisingmarketplace.offer.OfferRepository;
import com.advertisingmarketplace.offer.OfferStatus;
import com.advertisingmarketplace.posting.EmbeddedImages.InvalidPostingContentException;
import com.advertisingmarketplace.posting.PostingExceptions.CategoryNotAllowedException;
import com.advertisingmarketplace.posting.PostingExceptions.CurrencyChangeConflictException;
import com.advertisingmarketplace.posting.PostingExceptions.InvalidPayFilterException;
import com.advertisingmarketplace.posting.PostingExceptions.InvalidPostingStatusTransitionException;
import com.advertisingmarketplace.posting.PostingExceptions.NotPostingOwnerException;
import com.advertisingmarketplace.posting.PostingExceptions.PostingClosedException;
import com.advertisingmarketplace.posting.PostingExceptions.PostingNotFoundException;
import com.advertisingmarketplace.posting.PostingExceptions.UnsupportedSortException;
import com.advertisingmarketplace.posting.dto.CreatePostingRequest;
import com.advertisingmarketplace.posting.dto.PostingResponse;
import com.advertisingmarketplace.posting.dto.UpdatePostingRequest;
import com.advertisingmarketplace.support.MutableClock;
import com.advertisingmarketplace.support.TestEntities;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserRepository;
import com.advertisingmarketplace.user.UserType;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.unit.DataSize;

class PostingServiceTest {

    private final PostingRepository postingRepository = mock(PostingRepository.class);
    private final ImageRepository imageRepository = mock(ImageRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final ImageProperties imageProperties = new ImageProperties(DataSize.ofMegabytes(10), 2, 60, Duration.ofHours(24));
    private final ImageService imageService = new ImageService(imageRepository, userRepository,
            mock(VirusScanner.class), storage, imageProperties, clock, MarketplaceMetrics.noop());
    private final OfferRepository offerRepository = mock(OfferRepository.class);
    private final ExchangeRateService exchangeRateService = mock(ExchangeRateService.class);
    private final MessagingService messagingService = mock(MessagingService.class);
    private final DealRepository dealRepository = mock(DealRepository.class);
    private final PostingService service = new PostingService(postingRepository, imageRepository, offerRepository,
            userRepository, imageService, imageProperties, exchangeRateService, messagingService, dealRepository, clock);
    private final Map<String, BigDecimal> unitsPerEur = Map.of("EUR", BigDecimal.ONE, "USD", new BigDecimal("1.25"));

    private final User marketer = TestEntities.user(UserType.MARKETER);
    private final User customer = TestEntities.user(UserType.CUSTOMER);
    private final User admin = TestEntities.user(UserType.ADMIN);

    @BeforeEach
    void setUp() {
        for (User user : List.of(marketer, customer, admin)) {
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        }
        when(postingRepository.save(any(Posting.class)))
                .thenAnswer(inv -> TestEntities.withId(inv.getArgument(0), UUID.randomUUID()));
        when(storage.signedGetUrl(anyString())).thenAnswer(inv -> new ObjectStorage.SignedUrl(
                URI.create("https://bucket.example/" + inv.getArgument(0)), clock.instant().plusSeconds(3600)));
        imagesExist();
        when(exchangeRateService.toEur(any(), any())).thenAnswer(inv -> {
            BigDecimal amount = inv.getArgument(0);
            String currency = inv.getArgument(1);
            if (amount == null || currency == null || !unitsPerEur.containsKey(currency)) {
                return Optional.empty();
            }
            return Optional.of(amount.divide(unitsPerEur.get(currency), 2, java.math.RoundingMode.HALF_EVEN));
        });
        when(exchangeRateService.toEurOrThrow(any(), any())).thenAnswer(inv -> inv.getArgument(0) == null ? null
                : exchangeRateService.toEur(inv.getArgument(0), inv.getArgument(1)).orElseThrow());
    }

    private void imagesExist(Image... images) {
        when(imageRepository.findAllById(any())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            return List.of(images).stream().filter(i -> ids.contains(i.getId())).toList();
        });
    }

    private Posting existing(User creator, PostingCategory category, PostingStatus status, String text,
            Image... attached) {
        Posting posting = TestEntities.posting(creator, category, status, text);
        for (Image image : attached) {
            image.attachTo(posting);
        }
        when(postingRepository.findWithCreatorById(posting.getId())).thenReturn(Optional.of(posting));
        when(imageRepository.findAllByPostingIdOrderByCreatedAt(posting.getId())).thenReturn(List.of(attached));
        return posting;
    }

    private static CreatePostingRequest create(PostingCategory category, PostingStatus status, String text) {
        return new CreatePostingRequest(category, status, "Title", text, null, null, null);
    }

    private static UpdatePostingRequest update(PostingCategory category, String title, String text) {
        return new UpdatePostingRequest(category, title, text, null, null, null);
    }

    @Nested
    class Create {

        @ParameterizedTest
        @CsvSource({
                "MARKETER, MARKETER_POSTING, true",
                "MARKETER, CUSTOMER_POSTING, false",
                "CUSTOMER, CUSTOMER_POSTING, true",
                "CUSTOMER, MARKETER_POSTING, false",
                "ADMIN, MARKETER_POSTING, true",
                "ADMIN, CUSTOMER_POSTING, true"
        })
        void categoryMustMatchCreatorType(UserType type, PostingCategory category, boolean allowed) {
            User creator = switch (type) {
                case MARKETER -> marketer;
                case CUSTOMER -> customer;
                case ADMIN -> admin;
            };
            CreatePostingRequest request = create(category, null, "Text");

            if (allowed) {
                assertThat(service.create(creator.getId(), request).category()).isEqualTo(category);
            } else {
                assertThatThrownBy(() -> service.create(creator.getId(), request))
                        .isInstanceOf(CategoryNotAllowedException.class);
                verify(postingRepository, never()).save(any());
            }
        }

        @Test
        void defaultsToDraftAndStoresPayRange() {
            PostingResponse response = service.create(marketer.getId(), new CreatePostingRequest(
                    PostingCategory.MARKETER_POSTING, null, "Title", "Text",
                    new BigDecimal("500.00"), new BigDecimal("1500.00"), "eur"));

            assertThat(response.status()).isEqualTo(PostingStatus.DRAFT);
            assertThat(response.payCurrency()).isEqualTo("EUR");
            ArgumentCaptor<Posting> saved = ArgumentCaptor.forClass(Posting.class);
            verify(postingRepository).save(saved.capture());
            assertThat(saved.getValue().getPayFromEur()).isEqualByComparingTo("500");
            assertThat(saved.getValue().getPayToEur()).isEqualByComparingTo("1500");
            assertThat(response.payFrom()).isEqualByComparingTo("500");
            assertThat(response.payTo()).isEqualByComparingTo("1500");
        }

        @Test
        void canBePublishedImmediately() {
            assertThat(service.create(marketer.getId(),
                    create(PostingCategory.MARKETER_POSTING, PostingStatus.OPEN, "Text")).status())
                    .isEqualTo(PostingStatus.OPEN);
        }

        @Test
        void attachesEmbeddedImagesAndReturnsThemInTextOrder() {
            Image first = TestEntities.image(marketer);
            Image second = TestEntities.image(marketer);
            imagesExist(first, second);
            String text = "Intro ![b](image:%s) middle ![a](image:%s)".formatted(second.getId(), first.getId());

            PostingResponse response = service.create(marketer.getId(),
                    create(PostingCategory.MARKETER_POSTING, PostingStatus.OPEN, text));

            assertThat(first.isAttached()).isTrue();
            assertThat(second.isAttached()).isTrue();
            assertThat(first.getOrphanedSince()).isNull();
            assertThat(response.images()).extracting(i -> i.id()).containsExactly(second.getId(), first.getId());
            assertThat(response.images().getFirst().url()).isNotNull();
            assertThat(response.creator().id()).isEqualTo(marketer.getId());
            assertThat(response.creator().userType()).isEqualTo(UserType.MARKETER);
        }

        @Test
        void rejectsImagesOfOtherUsers() {
            Image foreign = TestEntities.image(customer);
            imagesExist(foreign);

            assertThatThrownBy(() -> service.create(marketer.getId(), create(PostingCategory.MARKETER_POSTING, null,
                    "![x](image:" + foreign.getId() + ")")))
                    .isInstanceOf(InvalidPostingContentException.class)
                    .hasMessageContaining("does not exist or cannot be used");
            assertThat(foreign.isAttached()).isFalse();
        }

        @Test
        void rejectsUnknownImages() {
            assertThatThrownBy(() -> service.create(marketer.getId(), create(PostingCategory.MARKETER_POSTING, null,
                    "![x](image:" + UUID.randomUUID() + ")")))
                    .isInstanceOf(InvalidPostingContentException.class);
        }

        @Test
        void rejectsImageAlreadyUsedInAnotherPosting() {
            Image image = TestEntities.image(marketer);
            image.attachTo(TestEntities.posting(marketer, PostingCategory.MARKETER_POSTING, "x"));
            imagesExist(image);

            assertThatThrownBy(() -> service.create(marketer.getId(), create(PostingCategory.MARKETER_POSTING, null,
                    "![x](image:" + image.getId() + ")")))
                    .isInstanceOf(InvalidPostingContentException.class);
        }

        @Test
        void enforcesMaximumImagesPerPosting() {
            Image a = TestEntities.image(marketer);
            Image b = TestEntities.image(marketer);
            Image c = TestEntities.image(marketer);
            imagesExist(a, b, c);
            String text = "![](image:%s)![](image:%s)![](image:%s)".formatted(a.getId(), b.getId(), c.getId());

            assertThatThrownBy(() -> service.create(marketer.getId(),
                    create(PostingCategory.MARKETER_POSTING, null, text)))
                    .isInstanceOf(InvalidPostingContentException.class)
                    .hasMessage("A posting can embed at most 2 images");
        }
    }

    @Nested
    class Visibility {

        @Test
        void draftIsOnlyVisibleToCreatorAndAdmins() {
            Posting draft = existing(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.DRAFT, "x");

            assertThat(service.get(draft.getId(), marketer.getId(), false).status()).isEqualTo(PostingStatus.DRAFT);
            assertThat(service.get(draft.getId(), admin.getId(), true).id()).isEqualTo(draft.getId());
            assertThatThrownBy(() -> service.get(draft.getId(), customer.getId(), false))
                    .isInstanceOf(PostingNotFoundException.class);
        }

        @Test
        void openAndClosedPostingsAreVisibleToEveryone() {
            Posting open = existing(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.OPEN, "x");
            Posting closed = existing(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.CLOSED, "x");

            assertThat(service.get(open.getId(), customer.getId(), false).id()).isEqualTo(open.getId());
            assertThat(service.get(closed.getId(), customer.getId(), false).id()).isEqualTo(closed.getId());
        }

        @Test
        void listPassesViewerSoOtherUsersDraftsAreExcluded() {
            when(postingRepository.searchVisible(any(), any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of()));

            service.list(new PostingFilter(PostingCategory.MARKETER_POSTING, PostingStatus.OPEN, null, null, null, null),
                    customer.getId(), false, PageRequest.of(0, 20));

            verify(postingRepository).searchVisible(
                    eq(new PostingSearch(PostingCategory.MARKETER_POSTING, PostingStatus.OPEN, null, null, null)),
                    eq(customer.getId()), eq(false), any());
        }

        @Test
        void listLoadsImagesForTheWholePageInOneQuery() {
            Image image = TestEntities.image(marketer);
            Posting withImage = TestEntities.posting(marketer, PostingCategory.MARKETER_POSTING,
                    "![](image:" + image.getId() + ")");
            image.attachTo(withImage);
            Posting withoutImage = TestEntities.posting(customer, PostingCategory.CUSTOMER_POSTING, "plain");
            when(postingRepository.searchVisible(any(), any(), anyBoolean(), any()))
                    .thenReturn(new PageImpl<>(List.of(withImage, withoutImage)));
            when(imageRepository.findAllByPostingIdIn(any())).thenReturn(List.of(image));

            var page = service.list(PostingFilter.none(), customer.getId(), false, PageRequest.of(0, 20));

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getContent().get(0).images()).hasSize(1);
            assertThat(page.getContent().get(1).images()).isEmpty();
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
            verify(imageRepository).findAllByPostingIdIn(ids.capture());
            assertThat(ids.getValue()).containsExactly(withImage.getId(), withoutImage.getId());
        }
    }

    @Nested
    class PayFilterAndSort {

        @Test
        void payFilterIsConvertedToEurFromTheRequestedCurrency() {
            when(postingRepository.searchVisible(any(), any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of()));

            service.list(new PostingFilter(null, null, null, new BigDecimal("100"), new BigDecimal("250"), "usd"),
                    customer.getId(), false, PageRequest.of(0, 20));

            ArgumentCaptor<PostingSearch> search = ArgumentCaptor.forClass(PostingSearch.class);
            verify(postingRepository).searchVisible(search.capture(), any(), anyBoolean(), any());
            assertThat(search.getValue().minPayEur()).isEqualByComparingTo("80");
            assertThat(search.getValue().maxPayEur()).isEqualByComparingTo("200");
        }

        @Test
        void payFilterDefaultsToEur() {
            when(postingRepository.searchVisible(any(), any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of()));

            service.list(new PostingFilter(null, null, null, new BigDecimal("100"), null, null), customer.getId(), false,
                    PageRequest.of(0, 20));

            ArgumentCaptor<PostingSearch> search = ArgumentCaptor.forClass(PostingSearch.class);
            verify(postingRepository).searchVisible(search.capture(), any(), anyBoolean(), any());
            assertThat(search.getValue().minPayEur()).isEqualByComparingTo("100");
            assertThat(search.getValue().maxPayEur()).isNull();
        }

        @Test
        void minPayMustNotExceedMaxPay() {
            assertThatThrownBy(() -> service.list(
                    new PostingFilter(null, null, null, new BigDecimal("300"), new BigDecimal("100"), null),
                    customer.getId(), false, PageRequest.of(0, 20)))
                    .isInstanceOf(InvalidPayFilterException.class);
        }

        @Test
        void paySortUsesEurAmountsAndAddsNewestFirstTieBreaker() {
            Sort desc = PostingService.resolveSort(PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "pay"))).getSort();
            Sort asc = PostingService.resolveSort(PageRequest.of(0, 5, Sort.by("pay", "title"))).getSort();

            assertThat(desc.toList()).extracting(Sort.Order::getProperty)
                    .containsExactly("coalesce(p.payToEur, p.payFromEur, -1)", "createdAt");
            assertThat(desc.toList().getFirst().isDescending()).isTrue();
            assertThat(asc.toList()).extracting(Sort.Order::getProperty)
                    .containsExactly("coalesce(p.payFromEur, p.payToEur, 999999999999.99)", "title", "createdAt");
        }

        @Test
        void unknownSortPropertiesAreRejected() {
            assertThatThrownBy(() -> PostingService.resolveSort(PageRequest.of(0, 5, Sort.by("creator.passwordHash"))))
                    .isInstanceOf(UnsupportedSortException.class)
                    .hasMessage("Cannot sort by 'creator.passwordHash'; use createdAt, updatedAt, title or pay");
        }
    }

    @Nested
    class Update {

        @Test
        void creatorCanUpdateAndRemovedImagesAreDetached() {
            Image kept = TestEntities.image(marketer);
            Image removed = TestEntities.image(marketer);
            Image added = TestEntities.image(marketer);
            Posting posting = existing(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.OPEN,
                    "![](image:%s)![](image:%s)".formatted(kept.getId(), removed.getId()), kept, removed);
            imagesExist(kept, removed, added);
            clock.advance(Duration.ofMinutes(5));

            PostingResponse response = service.update(posting.getId(), marketer.getId(), false,
                    new UpdatePostingRequest(PostingCategory.MARKETER_POSTING, "New title",
                            "![](image:%s) ![](image:%s)".formatted(added.getId(), kept.getId()),
                            new BigDecimal("10"), null, "USD"));

            assertThat(response.title()).isEqualTo("New title");
            assertThat(response.payFrom()).isEqualByComparingTo("10");
            assertThat(response.payTo()).isNull();
            assertThat(response.payCurrency()).isEqualTo("USD");
            assertThat(posting.getPayFromEur()).isEqualByComparingTo("8");
            assertThat(posting.getPayToEur()).isNull();
            assertThat(response.images()).extracting(i -> i.id()).containsExactly(added.getId(), kept.getId());
            assertThat(kept.getPosting()).isSameAs(posting);
            assertThat(added.getPosting()).isSameAs(posting);
            assertThat(removed.isAttached()).isFalse();
            assertThat(removed.getOrphanedSince()).isEqualTo(clock.instant());
        }

        @Test
        void currencyCannotChangeWhilePendingOffersUseAnotherCurrency() {
            Posting posting = existing(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.OPEN, "x");
            posting.setPayFrom(new BigDecimal("100"));
            posting.setPayCurrency("EUR");
            when(offerRepository.existsByPostingIdAndStatusAndCurrencyNot(posting.getId(), OfferStatus.PENDING, "USD"))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.update(posting.getId(), marketer.getId(), false,
                    new UpdatePostingRequest(PostingCategory.MARKETER_POSTING, "T", "x", new BigDecimal("100"), null,
                            "USD")))
                    .isInstanceOf(CurrencyChangeConflictException.class);
            assertThat(posting.getPayCurrency()).isEqualTo("EUR");
        }

        @Test
        void amountsMustFitTheCurrencysDecimals() {
            Posting posting = existing(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.OPEN, "x");

            assertThatThrownBy(() -> service.update(posting.getId(), marketer.getId(), false,
                    new UpdatePostingRequest(PostingCategory.MARKETER_POSTING, "T", "x", new BigDecimal("100.5"), null,
                            "JPY")))
                    .isInstanceOf(AmountPrecisionException.class)
                    .hasMessage("Amounts in JPY can have at most 0 decimals");
        }

        @Test
        void closedPostingCannotBeEdited() {
            Posting posting = existing(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.CLOSED, "x");

            assertThatThrownBy(() -> service.update(posting.getId(), marketer.getId(), false,
                    update(PostingCategory.MARKETER_POSTING, "Edited", "x")))
                    .isInstanceOf(PostingClosedException.class);
        }

        @Test
        void otherUsersCannotUpdate() {
            Posting posting = existing(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.OPEN, "x");

            assertThatThrownBy(() -> service.update(posting.getId(), customer.getId(), false,
                    update(PostingCategory.MARKETER_POSTING, "Hacked", "x")))
                    .isInstanceOf(NotPostingOwnerException.class);
            assertThat(posting.getTitle()).isEqualTo("Title");
        }

        @Test
        void adminCanUpdateButCategoryMustStillSuitTheCreator() {
            Posting posting = existing(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN, "x");

            assertThat(service.update(posting.getId(), admin.getId(), true,
                    update(PostingCategory.CUSTOMER_POSTING, "Moderated", "x")).title()).isEqualTo("Moderated");
            assertThatThrownBy(() -> service.update(posting.getId(), admin.getId(), true,
                    update(PostingCategory.MARKETER_POSTING, "Moderated", "x")))
                    .isInstanceOf(CategoryNotAllowedException.class);
        }

        @Test
        void missingPostingIs404() {
            UUID id = UUID.randomUUID();
            when(postingRepository.findWithCreatorById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(id, marketer.getId(), false,
                    update(PostingCategory.MARKETER_POSTING, "T", "x")))
                    .isInstanceOf(PostingNotFoundException.class);
        }
    }

    @Nested
    class ChangeStatus {

        @Test
        void creatorPublishesClosesAndReopens() {
            Posting posting = existing(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.DRAFT, "x");

            assertThat(service.changeStatus(posting.getId(), marketer.getId(), false, PostingStatus.OPEN).status())
                    .isEqualTo(PostingStatus.OPEN);
            assertThat(service.changeStatus(posting.getId(), marketer.getId(), false, PostingStatus.CLOSED).status())
                    .isEqualTo(PostingStatus.CLOSED);
            assertThat(service.changeStatus(posting.getId(), marketer.getId(), false, PostingStatus.OPEN).status())
                    .isEqualTo(PostingStatus.OPEN);
            verify(messagingService).recordPostingEvent(eq(posting), eq(SystemEvent.POSTING_CLOSED), any());
            verify(messagingService).recordPostingEvent(eq(posting), eq(SystemEvent.POSTING_REOPENED), any());
        }

        @Test
        void invalidTransitionIsRejected() {
            Posting posting = existing(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.OPEN, "x");

            assertThatThrownBy(() -> service.changeStatus(posting.getId(), marketer.getId(), false,
                    PostingStatus.DRAFT))
                    .isInstanceOf(InvalidPostingStatusTransitionException.class)
                    .hasMessage("A posting cannot go from OPEN to DRAFT");
        }

        @Test
        void onlyCreatorOrAdminCanChangeStatus() {
            Posting posting = existing(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.OPEN, "x");

            assertThatThrownBy(() -> service.changeStatus(posting.getId(), customer.getId(), false,
                    PostingStatus.CLOSED))
                    .isInstanceOf(NotPostingOwnerException.class);
            assertThat(service.changeStatus(posting.getId(), admin.getId(), true, PostingStatus.CLOSED).status())
                    .isEqualTo(PostingStatus.CLOSED);
        }
    }

    @Nested
    class Delete {

        @Test
        void creatorDeletesAndImagesBecomeOrphans() {
            Image image = TestEntities.image(marketer);
            Posting posting = existing(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.OPEN,
                    "![](image:" + image.getId() + ")", image);

            service.delete(posting.getId(), marketer.getId(), false);

            verify(postingRepository).delete(posting);
            assertThat(image.isAttached()).isFalse();
            assertThat(image.getOrphanedSince()).isEqualTo(clock.instant());
        }

        @Test
        void adminCanDeleteAnyPosting() {
            Posting posting = existing(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN, "x");

            service.delete(posting.getId(), admin.getId(), true);

            verify(postingRepository).delete(posting);
        }

        @Test
        void otherUsersCannotDelete() {
            Posting posting = existing(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN, "x");

            assertThatThrownBy(() -> service.delete(posting.getId(), marketer.getId(), false))
                    .isInstanceOf(NotPostingOwnerException.class);
            verify(postingRepository, never()).delete(any());
        }
    }
}
