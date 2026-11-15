package com.advertisingmarketplace.offer;

import com.advertisingmarketplace.common.money.Currencies;
import com.advertisingmarketplace.common.observability.MarketplaceMetrics;
import com.advertisingmarketplace.deal.Deal;
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
import com.advertisingmarketplace.posting.PostingExceptions.PostingNotFoundException;
import com.advertisingmarketplace.posting.PostingRepository;
import com.advertisingmarketplace.posting.PostingStatus;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserNotFoundException;
import com.advertisingmarketplace.user.UserRepository;
import com.advertisingmarketplace.user.UserType;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfferService {

    private final OfferRepository offerRepository;
    private final PostingRepository postingRepository;
    private final UserRepository userRepository;
    private final MessagingService messagingService;
    private final BlockService blockService;
    private final DealService dealService;
    private final DealRepository dealRepository;
    private final MarketplaceMetrics metrics;

    @Transactional
    public OfferResponse create(UUID postingId, UUID offererId, OfferRequest request) {
        User offerer = userRepository.findById(offererId).orElseThrow(() -> new UserNotFoundException(offererId));
        Posting posting = findVisiblePosting(postingId, offererId, false);
        if (offerer.getUserType() == UserType.ADMIN) {
            throw new AdminOfferException();
        }
        if (posting.isCreatedBy(offererId)) {
            throw new OwnPostingOfferException();
        }
        if (posting.getStatus() != PostingStatus.OPEN) {
            throw new PostingNotOpenException();
        }
        if (posting.getCategory().offerMakerType() != offerer.getUserType()) {
            throw new OfferMakerNotAllowedException(posting.getCategory());
        }
        if (blockService.isBlockedEitherWay(offererId, posting.getCreator().getId())) {
            throw new BlockedException();
        }
        if (offerRepository.existsByPostingIdAndOffererIdAndStatus(postingId, offererId, OfferStatus.PENDING)) {
            throw new DuplicatePendingOfferException();
        }
        String currency = resolveCurrency(request, posting);
        Offer offer = offerRepository.saveAndFlush(new Offer(posting, offerer, request.text().strip(),
                request.priceFrom(), request.priceTo(), currency));
        messagingService.recordOfferEvent(offer, SystemEvent.OFFER_MADE, offerer);
        metrics.offerCreated();
        return toResponse(offer);
    }

    public OfferResponse get(UUID id, UUID viewerId, boolean viewerIsAdmin) {
        return toResponse(findVisible(id, viewerId, viewerIsAdmin));
    }

    public Page<OfferResponse> listForPosting(UUID postingId, OfferStatus status, UUID viewerId,
            boolean viewerIsAdmin, Pageable pageable) {
        Posting posting = findVisiblePosting(postingId, viewerId, viewerIsAdmin);
        UUID offererFilter = viewerIsAdmin || posting.isCreatedBy(viewerId) ? null : viewerId;
        return offerRepository.findForPosting(postingId, offererFilter, status, pageable).map(this::toResponse);
    }

    public Page<OfferResponse> listMine(UUID offererId, OfferStatus status, Pageable pageable) {
        return offerRepository.findByOfferer(offererId, status, pageable).map(this::toResponse);
    }

    @Transactional
    public OfferResponse update(UUID id, UUID actorId, boolean actorIsAdmin, OfferRequest request) {
        Offer offer = findVisible(id, actorId, actorIsAdmin);
        if (!offer.isMadeBy(actorId)) {
            throw new OfferActionNotAllowedException("Only the user who made an offer can edit it");
        }
        requirePending(offer);
        if (offer.getPosting().getStatus() != PostingStatus.OPEN) {
            throw new PostingNotOpenException();
        }
        String currency = resolveCurrency(request, offer.getPosting());
        offer.setText(request.text().strip());
        offer.setCurrency(currency);
        offer.setPriceFrom(request.priceFrom());
        offer.setPriceTo(request.priceTo());
        Offer saved = offerRepository.saveAndFlush(offer);
        messagingService.recordOfferEvent(saved, SystemEvent.OFFER_UPDATED, offer.getOfferer());
        return toResponse(saved);
    }

    @Transactional
    public OfferResponse changeStatus(UUID id, UUID actorId, boolean actorIsAdmin, OfferStatus target) {
        return changeStatus(id, actorId, actorIsAdmin, target, null);
    }

    @Transactional
    public OfferResponse changeStatus(UUID id, UUID actorId, boolean actorIsAdmin, OfferStatus target,
            BigDecimal agreedAmount) {
        Offer offer = findVisible(id, actorId, actorIsAdmin);
        requirePending(offer);
        switch (target) {
            case ACCEPTED, REJECTED -> {
                if (!actorIsAdmin && !offer.getPosting().isCreatedBy(actorId)) {
                    throw new OfferActionNotAllowedException(
                            "Only the creator of the posting can accept or reject offers");
                }
                if (target == OfferStatus.ACCEPTED && offer.getPosting().getStatus() != PostingStatus.OPEN) {
                    throw new PostingNotOpenException();
                }
            }
            case WITHDRAWN -> {
                if (!actorIsAdmin && !offer.isMadeBy(actorId)) {
                    throw new OfferActionNotAllowedException("Only the user who made an offer can withdraw it");
                }
            }
            case PENDING -> throw new InvalidOfferStatusException();
        }
        BigDecimal amount = target == OfferStatus.ACCEPTED ? DealService.agreedAmount(offer, agreedAmount) : null;
        offer.setStatus(target);
        Offer saved = offerRepository.saveAndFlush(offer);
        metrics.offerDecided(target.name().toLowerCase());
        if (amount != null) {
            dealService.startFromOffer(saved, amount);
        }
        messagingService.recordOfferEvent(saved, switch (target) {
            case ACCEPTED -> SystemEvent.OFFER_ACCEPTED;
            case REJECTED -> SystemEvent.OFFER_REJECTED;
            default -> SystemEvent.OFFER_WITHDRAWN;
        }, userRepository.getReferenceById(actorId));
        return toResponse(saved);
    }

    private static String resolveCurrency(OfferRequest request, Posting posting) {
        String requested = Currencies.normalize(request.currency());
        String postingCurrency = posting.getPayCurrency();
        String currency;
        if (postingCurrency != null) {
            if (requested != null && !requested.equals(postingCurrency)) {
                throw new CurrencyMismatchException(postingCurrency);
            }
            currency = postingCurrency;
        } else if (requested != null) {
            currency = requested;
        } else {
            throw new CurrencyRequiredException();
        }
        Currencies.requireAmountsFit(currency, request.priceFrom(), request.priceTo());
        return currency;
    }

    private OfferResponse toResponse(Offer offer) {
        UUID dealId = offer.getStatus() == OfferStatus.ACCEPTED
                ? dealRepository.findByOfferId(offer.getId()).map(Deal::getId).orElse(null)
                : null;
        return OfferResponse.from(offer, dealId);
    }

    private static void requirePending(Offer offer) {
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new OfferNotPendingException(offer.getStatus());
        }
    }

    private Offer findVisible(UUID id, UUID viewerId, boolean viewerIsAdmin) {
        return offerRepository.findDetailedById(id)
                .filter(offer -> offer.isVisibleTo(viewerId, viewerIsAdmin))
                .orElseThrow(() -> new OfferNotFoundException(id));
    }

    private Posting findVisiblePosting(UUID postingId, UUID viewerId, boolean viewerIsAdmin) {
        return postingRepository.findWithCreatorById(postingId)
                .filter(posting -> posting.isVisibleTo(viewerId, viewerIsAdmin))
                .orElseThrow(() -> new PostingNotFoundException(postingId));
    }
}
