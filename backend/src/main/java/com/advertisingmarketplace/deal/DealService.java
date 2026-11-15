package com.advertisingmarketplace.deal;

import com.advertisingmarketplace.common.money.Currencies;
import com.advertisingmarketplace.common.observability.MarketplaceMetrics;
import com.advertisingmarketplace.deal.DealExceptions.DealActionNotAllowedException;
import com.advertisingmarketplace.deal.DealExceptions.DealNotFoundException;
import com.advertisingmarketplace.deal.DealExceptions.InvalidAgreedAmountException;
import com.advertisingmarketplace.deal.DealExceptions.InvalidDealStateException;
import com.advertisingmarketplace.deal.dto.DealResponse;
import com.advertisingmarketplace.deal.dto.DeliveryRequest;
import com.advertisingmarketplace.deal.dto.DeliveryResponse;
import com.advertisingmarketplace.messaging.ConversationRepository;
import com.advertisingmarketplace.messaging.MessagingService;
import com.advertisingmarketplace.messaging.SystemEvent;
import com.advertisingmarketplace.offer.Offer;
import com.advertisingmarketplace.posting.PostingCategory;
import com.advertisingmarketplace.review.ReviewService;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserRepository;
import com.advertisingmarketplace.user.dto.UserSummary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealService {

    private static final int AUTO_COMPLETE_BATCH = 100;

    private final DealRepository dealRepository;
    private final DeliveryRepository deliveryRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final MessagingService messagingService;
    private final ReviewService reviewService;
    private final DealProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final MarketplaceMetrics metrics;

    public static BigDecimal agreedAmount(Offer offer, BigDecimal requested) {
        if (requested == null) {
            if (offer.getPriceFrom().compareTo(offer.getPriceTo()) == 0) {
                return offer.getPriceFrom();
            }
            throw new InvalidAgreedAmountException("amount is required when accepting a price range");
        }
        if (requested.compareTo(offer.getPriceFrom()) < 0 || requested.compareTo(offer.getPriceTo()) > 0) {
            throw new InvalidAgreedAmountException("amount must be between " + offer.getPriceFrom().toPlainString()
                    + " and " + offer.getPriceTo().toPlainString());
        }
        Currencies.requireAmountsFit(offer.getCurrency(), requested);
        return requested;
    }

    @Transactional
    public Deal startFromOffer(Offer offer, BigDecimal amount) {
        boolean marketerOffered = offer.getPosting().getCategory() == PostingCategory.CUSTOMER_POSTING;
        User marketer = marketerOffered ? offer.getOfferer() : offer.getPosting().getCreator();
        User customer = marketerOffered ? offer.getPosting().getCreator() : offer.getOfferer();
        Deal deal = dealRepository.save(new Deal(offer, marketer, customer, amount, now()));
        metrics.dealEvent("started");
        return deal;
    }

    public Page<DealResponse> list(UUID userId, DealStatus status, Pageable pageable) {
        return dealRepository.findForParticipant(userId, status, pageable).map(deal -> toResponse(deal, userId, false));
    }

    public DealResponse get(UUID id, UUID viewerId, boolean viewerIsAdmin) {
        return toResponse(findVisible(id, viewerId, viewerIsAdmin), viewerId, true);
    }

    @Transactional
    public DealResponse deliver(UUID id, UUID actorId, DeliveryRequest request) {
        Deal deal = findVisible(id, actorId, false);
        requireRole(deal, actorId, DealRole.MARKETER, "Only the marketer can submit a delivery");
        requireStatus(deal, DealStatus.IN_PROGRESS, "delivered");
        Instant now = now();
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        deliveryRepository.save(new Delivery(deal, request.links().stream().map(String::strip).toList(), note, now));
        deal.markDelivered(now, now.plus(properties.autoApproveAfter()));
        metrics.dealEvent("delivered");
        record(deal, SystemEvent.DELIVERY_SUBMITTED, actorId, " submitted the delivery for review");
        return toResponse(deal, actorId, true);
    }

    @Transactional
    public DealResponse approve(UUID id, UUID actorId) {
        Deal deal = findVisible(id, actorId, false);
        requireRole(deal, actorId, DealRole.CUSTOMER, "Only the advertiser can approve the delivery");
        requireStatus(deal, DealStatus.DELIVERED, "approved");
        complete(deal);
        metrics.dealEvent("approved");
        record(deal, SystemEvent.DEAL_COMPLETED, actorId, " approved the delivery. The deal is complete");
        return toResponse(deal, actorId, true);
    }

    @Transactional
    public DealResponse requestChanges(UUID id, UUID actorId, String feedback) {
        Deal deal = findVisible(id, actorId, false);
        requireRole(deal, actorId, DealRole.CUSTOMER, "Only the advertiser can request changes");
        requireStatus(deal, DealStatus.DELIVERED, "sent back for changes");
        deliveryRepository.findFirstByDealIdOrderBySubmittedAtDesc(id)
                .ifPresent(delivery -> delivery.requestChanges(feedback.strip(), now()));
        deal.markInProgress();
        metrics.dealEvent("changes_requested");
        record(deal, SystemEvent.CHANGES_REQUESTED, actorId, " requested changes: " + feedback.strip());
        return toResponse(deal, actorId, true);
    }

    @Transactional
    public DealResponse cancel(UUID id, UUID actorId, boolean actorIsAdmin, String reason) {
        Deal deal = findVisible(id, actorId, actorIsAdmin);
        boolean allowed = deal.getStatus() == DealStatus.IN_PROGRESS
                || (actorIsAdmin && deal.getStatus() == DealStatus.DELIVERED);
        if (!allowed) {
            throw new InvalidDealStateException(deal.getStatus(), "cancelled");
        }
        deal.markCancelled(userRepository.getReferenceById(actorId), reason.strip(), now());
        metrics.dealEvent("cancelled");
        record(deal, SystemEvent.DEAL_CANCELLED, actorId, " cancelled the deal: " + reason.strip());
        return toResponse(deal, actorId, true);
    }

    @Scheduled(cron = "${app.deals.auto-complete-cron:0 */15 * * * *}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int autoCompleteDueDeals() {
        List<UUID> due = dealRepository.findDueForAutoCompletion(DealStatus.DELIVERED, now(),
                Limit.of(AUTO_COMPLETE_BATCH));
        int completed = 0;
        for (UUID id : due) {
            if (autoComplete(id)) {
                completed++;
            }
        }
        if (completed > 0) {
            log.info("Auto-completed {} deals", completed);
        }
        return completed;
    }

    private boolean autoComplete(UUID id) {
        try {
            Boolean done = transactionTemplate.execute(status -> dealRepository.findDetailedById(id)
                    .filter(deal -> deal.getStatus() == DealStatus.DELIVERED)
                    .map(deal -> {
                        complete(deal);
                        metrics.dealEvent("auto_approved");
                        messagingService.recordDealEvent(deal.getOffer(), SystemEvent.DEAL_COMPLETED, null,
                                "The delivery was approved automatically after "
                                        + properties.autoApproveAfter().toDays() + " days. The deal is complete");
                        return true;
                    })
                    .orElse(false));
            return Boolean.TRUE.equals(done);
        } catch (OptimisticLockingFailureException e) {
            log.info("Deal {} changed while auto-completing it; skipping", id);
            return false;
        }
    }

    private void complete(Deal deal) {
        deliveryRepository.findFirstByDealIdOrderBySubmittedAtDesc(deal.getId()).ifPresent(d -> d.approve(now()));
        deal.markCompleted(now());
    }

    private void record(Deal deal, SystemEvent event, UUID actorId, String action) {
        User actor = userRepository.getReferenceById(actorId);
        String name = userRepository.findById(actorId).map(User::getFullName).orElse(UserSummary.DELETED_USER_NAME);
        messagingService.recordDealEvent(deal.getOffer(), event, actor, name + action);
    }

    private static void requireRole(Deal deal, UUID actorId, DealRole role, String message) {
        if (deal.roleOf(actorId).orElse(null) != role) {
            throw new DealActionNotAllowedException(message);
        }
    }

    private static void requireStatus(Deal deal, DealStatus expected, String action) {
        if (deal.getStatus() != expected) {
            throw new InvalidDealStateException(deal.getStatus(), action);
        }
    }

    private Deal findVisible(UUID id, UUID viewerId, boolean viewerIsAdmin) {
        return dealRepository.findDetailedById(id)
                .filter(deal -> viewerIsAdmin || deal.isParticipant(viewerId))
                .orElseThrow(() -> new DealNotFoundException(id));
    }

    private DealResponse toResponse(Deal deal, UUID viewerId, boolean detailed) {
        List<DeliveryResponse> deliveries = detailed
                ? deliveryRepository.findAllByDealIdOrderBySubmittedAtDesc(deal.getId()).stream()
                        .map(DeliveryResponse::from).toList()
                : List.of();
        UUID conversationId = detailed && deal.getOffer() != null && deal.getPosting() != null
                ? conversationRepository.findByPostingIdAndCounterpartKey(deal.getPosting().getId(),
                        deal.getOffer().getOfferer().getId()).map(c -> c.getId()).orElse(null)
                : null;
        return new DealResponse(
                deal.getId(),
                deal.getOffer() == null ? null : deal.getOffer().getId(),
                deal.getPosting() == null ? null : deal.getPosting().getId(),
                deal.getPostingTitle(),
                UserSummary.fromNullable(deal.getMarketer()),
                UserSummary.fromNullable(deal.getCustomer()),
                deal.roleOf(viewerId).orElse(null),
                deal.getAmount(),
                deal.getCurrency(),
                deal.getStatus(),
                deal.getCreatedAt(),
                deal.getDeliveredAt(),
                deal.getCompletedAt(),
                deal.getCancelledAt(),
                deal.getAutoCompleteAt(),
                deal.getCancelledBy() == null ? null : UserSummary.from(deal.getCancelledBy()),
                deal.getCancelReason(),
                conversationId,
                deliveries,
                detailed ? reviewService.stateFor(deal, viewerId) : null);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
