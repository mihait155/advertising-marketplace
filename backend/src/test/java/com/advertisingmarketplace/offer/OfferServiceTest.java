package com.advertisingmarketplace.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.advertisingmarketplace.common.observability.MarketplaceMetrics;
import com.advertisingmarketplace.deal.DealRepository;
import com.advertisingmarketplace.deal.DealService;
import com.advertisingmarketplace.messaging.MessagingExceptions.BlockedException;
import com.advertisingmarketplace.messaging.MessagingService;
import com.advertisingmarketplace.messaging.SystemEvent;
import com.advertisingmarketplace.messaging.safety.BlockService;
import com.advertisingmarketplace.offer.OfferExceptions.AdminOfferException;
import com.advertisingmarketplace.offer.OfferExceptions.CurrencyMismatchException;
import com.advertisingmarketplace.offer.OfferExceptions.CurrencyRequiredException;
import com.advertisingmarketplace.offer.OfferExceptions.DuplicatePendingOfferException;
import com.advertisingmarketplace.offer.OfferExceptions.InvalidOfferStatusException;
import com.advertisingmarketplace.offer.OfferExceptions.OfferActionNotAllowedException;
import com.advertisingmarketplace.offer.OfferExceptions.OfferMakerNotAllowedException;
import com.advertisingmarketplace.offer.OfferExceptions.OfferNotFoundException;
import com.advertisingmarketplace.offer.OfferExceptions.OfferNotPendingException;
import com.advertisingmarketplace.offer.OfferExceptions.OwnPostingOfferException;
import com.advertisingmarketplace.offer.OfferExceptions.PostingNotOpenException;
import com.advertisingmarketplace.offer.dto.OfferRequest;
import com.advertisingmarketplace.offer.dto.OfferResponse;
import com.advertisingmarketplace.posting.Posting;
import com.advertisingmarketplace.posting.PostingCategory;
import com.advertisingmarketplace.posting.PostingExceptions.PostingNotFoundException;
import com.advertisingmarketplace.posting.PostingRepository;
import com.advertisingmarketplace.posting.PostingStatus;
import com.advertisingmarketplace.support.TestEntities;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserRepository;
import com.advertisingmarketplace.user.UserType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class OfferServiceTest {

    private final OfferRepository offerRepository = mock(OfferRepository.class);
    private final PostingRepository postingRepository = mock(PostingRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MessagingService messagingService = mock(MessagingService.class);
    private final BlockService blockService = mock(BlockService.class);
    private final DealService dealService = mock(DealService.class);
    private final DealRepository dealRepository = mock(DealRepository.class);
    private final OfferService service = new OfferService(offerRepository, postingRepository, userRepository,
            messagingService, blockService, dealService, dealRepository, MarketplaceMetrics.noop());

    private final User marketer = TestEntities.user(UserType.MARKETER);
    private final User otherMarketer = TestEntities.user(UserType.MARKETER);
    private final User customer = TestEntities.user(UserType.CUSTOMER);
    private final User admin = TestEntities.user(UserType.ADMIN);

    private final OfferRequest request = new OfferRequest(" I can do 3 sponsored streams ",
            new BigDecimal("300.00"), new BigDecimal("450.00"), "EUR");

    @BeforeEach
    void setUp() {
        for (User user : List.of(marketer, otherMarketer, customer, admin)) {
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        }
        when(offerRepository.saveAndFlush(any(Offer.class))).thenAnswer(inv -> {
            Offer offer = inv.getArgument(0);
            return offer.getId() == null ? TestEntities.withId(offer, UUID.randomUUID()) : offer;
        });
    }

    private Posting posting(User creator, PostingCategory category, PostingStatus status) {
        Posting posting = TestEntities.posting(creator, category, status, "text");
        when(postingRepository.findWithCreatorById(posting.getId())).thenReturn(Optional.of(posting));
        return posting;
    }

    private Offer offer(Posting posting, User offerer, OfferStatus status) {
        Offer offer = TestEntities.offer(posting, offerer);
        offer.setStatus(status);
        when(offerRepository.findDetailedById(offer.getId())).thenReturn(Optional.of(offer));
        return offer;
    }

    @Nested
    class Create {

        @Test
        void marketerOffersOnCustomerPosting() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);

            OfferResponse response = service.create(posting.getId(), marketer.getId(), request);

            ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
            verify(offerRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getPosting()).isSameAs(posting);
            assertThat(saved.getValue().getOfferer()).isSameAs(marketer);
            assertThat(response.status()).isEqualTo(OfferStatus.PENDING);
            assertThat(response.text()).isEqualTo("I can do 3 sponsored streams");
            assertThat(response.priceFrom()).isEqualByComparingTo("300");
            assertThat(response.priceTo()).isEqualByComparingTo("450");
            assertThat(response.posting().id()).isEqualTo(posting.getId());
            assertThat(response.offerer().id()).isEqualTo(marketer.getId());
            verify(messagingService).recordOfferEvent(saved.getValue(), SystemEvent.OFFER_MADE, marketer);
        }

        @Test
        void blockedUsersCannotMakeOffers() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            when(blockService.isBlockedEitherWay(marketer.getId(), customer.getId())).thenReturn(true);

            assertThatThrownBy(() -> service.create(posting.getId(), marketer.getId(), request))
                    .isInstanceOf(BlockedException.class);
            verify(offerRepository, never()).saveAndFlush(any());
        }

        @Test
        void customerOffersOnMarketerPosting() {
            Posting posting = posting(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.OPEN);

            assertThat(service.create(posting.getId(), customer.getId(), request).offerer().userType())
                    .isEqualTo(UserType.CUSTOMER);
        }

        @Test
        void currencyDefaultsToThePostingsCurrency() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            posting.setPayFrom(new BigDecimal("100"));
            posting.setPayCurrency("GBP");

            OfferResponse response = service.create(posting.getId(), marketer.getId(),
                    new OfferRequest("x", BigDecimal.ONE, BigDecimal.TEN, null));

            assertThat(response.currency()).isEqualTo("GBP");
        }

        @Test
        void currencyMustMatchThePostingsCurrency() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            posting.setPayFrom(new BigDecimal("100"));
            posting.setPayCurrency("GBP");

            assertThatThrownBy(() -> service.create(posting.getId(), marketer.getId(),
                    new OfferRequest("x", BigDecimal.ONE, BigDecimal.TEN, "usd")))
                    .isInstanceOf(CurrencyMismatchException.class)
                    .hasMessage("The offer must be in the posting's currency (GBP)");
        }

        @Test
        void currencyIsRequiredWhenThePostingHasNone() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);

            assertThatThrownBy(() -> service.create(posting.getId(), marketer.getId(),
                    new OfferRequest("x", BigDecimal.ONE, BigDecimal.TEN, null)))
                    .isInstanceOf(CurrencyRequiredException.class);
            assertThat(service.create(posting.getId(), marketer.getId(),
                    new OfferRequest("x", BigDecimal.ONE, BigDecimal.TEN, "ron")).currency()).isEqualTo("RON");
        }

        @Test
        void sameSideCannotOffer() {
            Posting posting = posting(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.OPEN);

            assertThatThrownBy(() -> service.create(posting.getId(), otherMarketer.getId(), request))
                    .isInstanceOf(OfferMakerNotAllowedException.class)
                    .hasMessage("Only CUSTOMER users can make offers on MARKETER_POSTING postings");
        }

        @Test
        void cannotOfferOnOwnPosting() {
            Posting posting = posting(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.OPEN);

            assertThatThrownBy(() -> service.create(posting.getId(), marketer.getId(), request))
                    .isInstanceOf(OwnPostingOfferException.class);
        }

        @Test
        void adminsCannotOffer() {
            Posting posting = posting(marketer, PostingCategory.MARKETER_POSTING, PostingStatus.OPEN);

            assertThatThrownBy(() -> service.create(posting.getId(), admin.getId(), request))
                    .isInstanceOf(AdminOfferException.class);
        }

        @Test
        void closedPostingDoesNotAcceptOffers() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.CLOSED);

            assertThatThrownBy(() -> service.create(posting.getId(), marketer.getId(), request))
                    .isInstanceOf(PostingNotOpenException.class);
            verify(offerRepository, never()).saveAndFlush(any());
        }

        @Test
        void draftPostingIsInvisible() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.DRAFT);

            assertThatThrownBy(() -> service.create(posting.getId(), marketer.getId(), request))
                    .isInstanceOf(PostingNotFoundException.class);
        }

        @Test
        void onlyOnePendingOfferPerUserAndPosting() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            when(offerRepository.existsByPostingIdAndOffererIdAndStatus(posting.getId(), marketer.getId(),
                    OfferStatus.PENDING)).thenReturn(true);

            assertThatThrownBy(() -> service.create(posting.getId(), marketer.getId(), request))
                    .isInstanceOf(DuplicatePendingOfferException.class);
        }
    }

    @Nested
    class Read {

        @Test
        void offerIsVisibleToOffererPostingCreatorAndAdminOnly() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            Offer offer = offer(posting, marketer, OfferStatus.PENDING);

            assertThat(service.get(offer.getId(), marketer.getId(), false).id()).isEqualTo(offer.getId());
            assertThat(service.get(offer.getId(), customer.getId(), false).id()).isEqualTo(offer.getId());
            assertThat(service.get(offer.getId(), admin.getId(), true).id()).isEqualTo(offer.getId());
            assertThatThrownBy(() -> service.get(offer.getId(), otherMarketer.getId(), false))
                    .isInstanceOf(OfferNotFoundException.class);
        }

        @Test
        void postingCreatorSeesAllOffersOthersOnlyTheirOwn() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            when(offerRepository.findForPosting(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
            PageRequest page = PageRequest.of(0, 20);

            service.listForPosting(posting.getId(), null, customer.getId(), false, page);
            verify(offerRepository).findForPosting(posting.getId(), null, null, page);

            service.listForPosting(posting.getId(), OfferStatus.PENDING, marketer.getId(), false, page);
            verify(offerRepository).findForPosting(posting.getId(), marketer.getId(), OfferStatus.PENDING, page);

            service.listForPosting(posting.getId(), null, admin.getId(), true, page);
            verify(offerRepository, org.mockito.Mockito.times(2)).findForPosting(posting.getId(), null, null, page);
        }

        @Test
        void listMineFiltersByOfferer() {
            when(offerRepository.findByOfferer(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

            service.listMine(marketer.getId(), OfferStatus.ACCEPTED, PageRequest.of(0, 5));

            verify(offerRepository).findByOfferer(eq(marketer.getId()), eq(OfferStatus.ACCEPTED), any());
        }
    }

    @Nested
    class Update {

        @Test
        void offererCanEditPendingOffer() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            Offer offer = offer(posting, marketer, OfferStatus.PENDING);

            OfferResponse response = service.update(offer.getId(), marketer.getId(), false,
                    new OfferRequest("Revised", new BigDecimal("350"), new BigDecimal("350"), "EUR"));

            assertThat(response.text()).isEqualTo("Revised");
            assertThat(response.priceFrom()).isEqualByComparingTo("350");
            assertThat(response.priceTo()).isEqualByComparingTo("350");
        }

        @Test
        void postingCreatorCannotEditTheOffer() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            Offer offer = offer(posting, marketer, OfferStatus.PENDING);

            assertThatThrownBy(() -> service.update(offer.getId(), customer.getId(), false, request))
                    .isInstanceOf(OfferActionNotAllowedException.class);
        }

        @Test
        void decidedOfferCannotBeEdited() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            Offer offer = offer(posting, marketer, OfferStatus.ACCEPTED);

            assertThatThrownBy(() -> service.update(offer.getId(), marketer.getId(), false, request))
                    .isInstanceOf(OfferNotPendingException.class)
                    .hasMessage("The offer is ACCEPTED and can no longer be changed");
        }

        @Test
        void offerOnClosedPostingCannotBeEdited() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.CLOSED);
            Offer offer = offer(posting, marketer, OfferStatus.PENDING);

            assertThatThrownBy(() -> service.update(offer.getId(), marketer.getId(), false, request))
                    .isInstanceOf(PostingNotOpenException.class);
        }
    }

    @Nested
    class ChangeStatus {

        @ParameterizedTest
        @EnumSource(value = OfferStatus.class, names = {"ACCEPTED", "REJECTED"})
        void postingCreatorDecides(OfferStatus decision) {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            Offer offer = offer(posting, marketer, OfferStatus.PENDING);

            when(userRepository.getReferenceById(customer.getId())).thenReturn(customer);

            assertThat(service.changeStatus(offer.getId(), customer.getId(), false, decision,
                    decision == OfferStatus.ACCEPTED ? new BigDecimal("150") : null).status())
                    .isEqualTo(decision);
            verify(messagingService).recordOfferEvent(offer,
                    decision == OfferStatus.ACCEPTED ? SystemEvent.OFFER_ACCEPTED : SystemEvent.OFFER_REJECTED, customer);
        }

        @ParameterizedTest
        @EnumSource(value = OfferStatus.class, names = {"ACCEPTED", "REJECTED"})
        void offererCannotDecideOwnOffer(OfferStatus decision) {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            Offer offer = offer(posting, marketer, OfferStatus.PENDING);

            assertThatThrownBy(() -> service.changeStatus(offer.getId(), marketer.getId(), false, decision))
                    .isInstanceOf(OfferActionNotAllowedException.class);
            assertThat(offer.getStatus()).isEqualTo(OfferStatus.PENDING);
        }

        @Test
        void acceptingStartsADealWithAnAmountInsideTheRange() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            Offer offer = offer(posting, marketer, OfferStatus.PENDING);
            when(userRepository.getReferenceById(customer.getId())).thenReturn(customer);

            assertThatThrownBy(() -> service.changeStatus(offer.getId(), customer.getId(), false,
                    OfferStatus.ACCEPTED, null))
                    .isInstanceOf(com.advertisingmarketplace.deal.DealExceptions.InvalidAgreedAmountException.class)
                    .hasMessage("amount is required when accepting a price range");
            assertThatThrownBy(() -> service.changeStatus(offer.getId(), customer.getId(), false,
                    OfferStatus.ACCEPTED, new BigDecimal("250")))
                    .hasMessage("amount must be between 100.00 and 200.00");
            assertThat(offer.getStatus()).isEqualTo(OfferStatus.PENDING);

            service.changeStatus(offer.getId(), customer.getId(), false, OfferStatus.ACCEPTED, new BigDecimal("180"));

            verify(dealService).startFromOffer(offer, new BigDecimal("180"));
        }

        @Test
        void offererWithdraws() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            Offer offer = offer(posting, marketer, OfferStatus.PENDING);

            assertThat(service.changeStatus(offer.getId(), marketer.getId(), false, OfferStatus.WITHDRAWN).status())
                    .isEqualTo(OfferStatus.WITHDRAWN);
        }

        @Test
        void postingCreatorCannotWithdrawSomeoneElsesOffer() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            Offer offer = offer(posting, marketer, OfferStatus.PENDING);

            assertThatThrownBy(() -> service.changeStatus(offer.getId(), customer.getId(), false,
                    OfferStatus.WITHDRAWN))
                    .isInstanceOf(OfferActionNotAllowedException.class);
        }

        @Test
        void adminCanModerate() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            Offer offer = offer(posting, marketer, OfferStatus.PENDING);

            assertThat(service.changeStatus(offer.getId(), admin.getId(), true, OfferStatus.REJECTED).status())
                    .isEqualTo(OfferStatus.REJECTED);
        }

        @Test
        void cannotAcceptOnClosedPostingButCanReject() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.CLOSED);
            Offer first = offer(posting, marketer, OfferStatus.PENDING);
            Offer second = offer(posting, otherMarketer, OfferStatus.PENDING);

            assertThatThrownBy(() -> service.changeStatus(first.getId(), customer.getId(), false,
                    OfferStatus.ACCEPTED))
                    .isInstanceOf(PostingNotOpenException.class);
            assertThat(service.changeStatus(second.getId(), customer.getId(), false, OfferStatus.REJECTED).status())
                    .isEqualTo(OfferStatus.REJECTED);
        }

        @Test
        void decidedOfferIsFinal() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            Offer offer = offer(posting, marketer, OfferStatus.REJECTED);

            assertThatThrownBy(() -> service.changeStatus(offer.getId(), customer.getId(), false,
                    OfferStatus.ACCEPTED))
                    .isInstanceOf(OfferNotPendingException.class);
        }

        @Test
        void cannotSetBackToPending() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            Offer offer = offer(posting, marketer, OfferStatus.PENDING);

            assertThatThrownBy(() -> service.changeStatus(offer.getId(), customer.getId(), false,
                    OfferStatus.PENDING))
                    .isInstanceOf(InvalidOfferStatusException.class);
        }

        @Test
        void outsidersGet404() {
            Posting posting = posting(customer, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN);
            Offer offer = offer(posting, marketer, OfferStatus.PENDING);

            assertThatThrownBy(() -> service.changeStatus(offer.getId(), otherMarketer.getId(), false,
                    OfferStatus.WITHDRAWN))
                    .isInstanceOf(OfferNotFoundException.class);
        }
    }
}
