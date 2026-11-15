package com.advertisingmarketplace.posting;

import com.advertisingmarketplace.common.money.Currencies;
import com.advertisingmarketplace.common.money.ExchangeRateService;
import com.advertisingmarketplace.deal.DealExceptions.ActiveDealsException;
import com.advertisingmarketplace.deal.DealRepository;
import com.advertisingmarketplace.deal.DealStatus;
import com.advertisingmarketplace.image.Image;
import com.advertisingmarketplace.image.ImageProperties;
import com.advertisingmarketplace.image.ImageRepository;
import com.advertisingmarketplace.image.ImageService;
import com.advertisingmarketplace.image.dto.ImageResponse;
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
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserNotFoundException;
import com.advertisingmarketplace.user.UserRepository;
import com.advertisingmarketplace.user.dto.UserSummary;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostingService {

    private static final String NO_PAY_ASC = "999999999999.99";
    private static final String NO_PAY_DESC = "-1";

    private final PostingRepository postingRepository;
    private final ImageRepository imageRepository;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final ImageService imageService;
    private final ImageProperties imageProperties;
    private final ExchangeRateService exchangeRateService;
    private final MessagingService messagingService;
    private final DealRepository dealRepository;
    private final Clock clock;

    @Transactional
    public PostingResponse create(UUID creatorId, CreatePostingRequest request) {
        User creator = userRepository.findById(creatorId).orElseThrow(() -> new UserNotFoundException(creatorId));
        requireCategoryAllowed(creator, request.category());
        String text = request.text().strip();
        List<Image> images = resolveEmbeddedImages(text, creator, null);

        String currency = Currencies.normalize(request.payCurrency());
        Currencies.requireAmountsFit(currency, request.payFrom(), request.payTo());

        Posting posting = new Posting(creator, request.category(), request.initialStatus(),
                request.title().strip(), text, request.payFrom(), request.payTo(), currency);
        applyEurAmounts(posting);
        posting = postingRepository.save(posting);
        Posting saved = posting;
        images.forEach(image -> image.attachTo(saved));
        return toResponse(saved, images);
    }

    public PostingResponse get(UUID id, UUID viewerId, boolean viewerIsAdmin) {
        Posting posting = findVisible(id, viewerId, viewerIsAdmin);
        return toResponse(posting, imageRepository.findAllByPostingIdOrderByCreatedAt(id));
    }

    public Page<PostingResponse> list(PostingFilter filter, UUID viewerId, boolean viewerIsAdmin, Pageable pageable) {
        if (filter.minPay() != null && filter.maxPay() != null && filter.minPay().compareTo(filter.maxPay()) > 0) {
            throw new InvalidPayFilterException();
        }
        String currency = filter.currency() == null ? ExchangeRateService.BASE_CURRENCY
                : Currencies.normalize(filter.currency());
        PostingSearch search = new PostingSearch(filter.category(), filter.status(), filter.creatorId(),
                exchangeRateService.toEurOrThrow(filter.minPay(), currency),
                exchangeRateService.toEurOrThrow(filter.maxPay(), currency));
        Page<Posting> page = postingRepository.searchVisible(search, viewerId, viewerIsAdmin, resolveSort(pageable));
        List<UUID> ids = page.getContent().stream().map(Posting::getId).toList();
        Map<UUID, List<Image>> imagesByPosting = ids.isEmpty() ? Map.of()
                : imageRepository.findAllByPostingIdIn(ids).stream()
                        .collect(Collectors.groupingBy(image -> image.getPosting().getId()));
        return page.map(posting -> toResponse(posting, imagesByPosting.getOrDefault(posting.getId(), List.of())));
    }

    @Transactional
    public PostingResponse update(UUID id, UUID actorId, boolean actorIsAdmin, UpdatePostingRequest request) {
        Posting posting = findVisible(id, actorId, actorIsAdmin);
        requireOwnerOrAdmin(posting, actorId, actorIsAdmin);
        if (posting.getStatus() == PostingStatus.CLOSED) {
            throw new PostingClosedException();
        }
        requireCategoryAllowed(posting.getCreator(), request.category());
        String currency = Currencies.normalize(request.payCurrency());
        Currencies.requireAmountsFit(currency, request.payFrom(), request.payTo());
        if (currency != null && !currency.equals(posting.getPayCurrency())
                && offerRepository.existsByPostingIdAndStatusAndCurrencyNot(id, OfferStatus.PENDING, currency)) {
            throw new CurrencyChangeConflictException();
        }
        String text = request.text().strip();
        List<Image> images = resolveEmbeddedImages(text, posting.getCreator(), posting);

        Set<UUID> keep = images.stream().map(Image::getId).collect(Collectors.toSet());
        imageRepository.findAllByPostingIdOrderByCreatedAt(id).stream()
                .filter(image -> !keep.contains(image.getId()))
                .forEach(image -> image.detach(clock.instant()));
        images.forEach(image -> image.attachTo(posting));

        posting.setCategory(request.category());
        posting.setTitle(request.title().strip());
        posting.setText(text);
        posting.setPayFrom(request.payFrom());
        posting.setPayTo(request.payTo());
        posting.setPayCurrency(currency);
        applyEurAmounts(posting);
        postingRepository.saveAndFlush(posting);
        return toResponse(posting, images);
    }

    @Transactional
    public PostingResponse changeStatus(UUID id, UUID actorId, boolean actorIsAdmin, PostingStatus target) {
        Posting posting = findVisible(id, actorId, actorIsAdmin);
        requireOwnerOrAdmin(posting, actorId, actorIsAdmin);
        if (!posting.getStatus().canTransitionTo(target)) {
            throw new InvalidPostingStatusTransitionException(posting.getStatus(), target);
        }
        PostingStatus previous = posting.getStatus();
        posting.setStatus(target);
        postingRepository.saveAndFlush(posting);
        if (target == PostingStatus.CLOSED) {
            messagingService.recordPostingEvent(posting, SystemEvent.POSTING_CLOSED, userRepository.getReferenceById(actorId));
        } else if (previous == PostingStatus.CLOSED) {
            messagingService.recordPostingEvent(posting, SystemEvent.POSTING_REOPENED,
                    userRepository.getReferenceById(actorId));
        }
        return toResponse(posting, imageRepository.findAllByPostingIdOrderByCreatedAt(id));
    }

    @Transactional
    public void delete(UUID id, UUID actorId, boolean actorIsAdmin) {
        Posting posting = findVisible(id, actorId, actorIsAdmin);
        requireOwnerOrAdmin(posting, actorId, actorIsAdmin);
        if (dealRepository.existsByPostingIdAndStatusIn(id, DealStatus.ACTIVE)) {
            throw new ActiveDealsException("Finish or cancel the active deals on this posting before deleting it");
        }
        imageRepository.findAllByPostingIdOrderByCreatedAt(id).forEach(image -> image.detach(clock.instant()));
        imageRepository.flush();
        postingRepository.delete(posting);
    }

    private void applyEurAmounts(Posting posting) {
        posting.setPayFromEur(exchangeRateService.toEur(posting.getPayFrom(), posting.getPayCurrency()).orElse(null));
        posting.setPayToEur(exchangeRateService.toEur(posting.getPayTo(), posting.getPayCurrency()).orElse(null));
    }

    static Pageable resolveSort(Pageable pageable) {
        Sort sort = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            Sort next = switch (order.getProperty()) {
                case "pay" -> order.isAscending()
                        ? JpaSort.unsafe(Sort.Direction.ASC, "coalesce(p.payFromEur, p.payToEur, " + NO_PAY_ASC + ")")
                        : JpaSort.unsafe(Sort.Direction.DESC, "coalesce(p.payToEur, p.payFromEur, " + NO_PAY_DESC + ")");
                case "createdAt", "updatedAt", "title" -> Sort.by(order);
                default -> throw new UnsupportedSortException(order.getProperty());
            };
            sort = sort.and(next);
        }
        sort = sort.and(Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private List<Image> resolveEmbeddedImages(String text, User creator, Posting posting) {
        Set<UUID> ids = EmbeddedImages.referencedIn(text);
        if (ids.size() > imageProperties.maxPerPosting()) {
            throw new InvalidPostingContentException(
                    "A posting can embed at most " + imageProperties.maxPerPosting() + " images");
        }
        Map<UUID, Image> found = imageRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Image::getId, image -> image));
        for (UUID imageId : ids) {
            Image image = found.get(imageId);
            boolean usable = image != null
                    && image.isOwnedBy(creator.getId())
                    && (!image.isAttached() || (posting != null && image.getPosting().getId().equals(posting.getId())));
            if (!usable) {
                throw new InvalidPostingContentException("Image " + imageId + " does not exist or cannot be used");
            }
        }
        return ids.stream().map(found::get).toList();
    }

    private static void requireCategoryAllowed(User creator, PostingCategory category) {
        if (!category.canBeCreatedBy(creator.getUserType())) {
            throw new CategoryNotAllowedException(creator.getUserType(), category);
        }
    }

    private static void requireOwnerOrAdmin(Posting posting, UUID actorId, boolean actorIsAdmin) {
        if (!actorIsAdmin && !posting.isCreatedBy(actorId)) {
            throw new NotPostingOwnerException();
        }
    }

    private Posting findVisible(UUID id, UUID viewerId, boolean viewerIsAdmin) {
        return postingRepository.findWithCreatorById(id)
                .filter(posting -> posting.isVisibleTo(viewerId, viewerIsAdmin))
                .orElseThrow(() -> new PostingNotFoundException(id));
    }

    private PostingResponse toResponse(Posting posting, List<Image> images) {
        List<UUID> order = List.copyOf(EmbeddedImages.referencedIn(posting.getText()));
        List<ImageResponse> imageResponses = images.stream()
                .sorted(Comparator.comparingInt(image -> order.indexOf(image.getId())))
                .map(imageService::toResponse)
                .toList();
        return new PostingResponse(
                posting.getId(),
                posting.getCategory(),
                posting.getStatus(),
                posting.getTitle(),
                posting.getText(),
                posting.getPayFrom(),
                posting.getPayTo(),
                posting.getPayCurrency(),
                UserSummary.from(posting.getCreator()),
                imageResponses,
                posting.getCreatedAt(),
                posting.getUpdatedAt());
    }
}
