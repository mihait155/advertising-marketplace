package com.advertisingmarketplace.messaging;

import com.advertisingmarketplace.common.observability.MarketplaceMetrics;
import com.advertisingmarketplace.image.Image;
import com.advertisingmarketplace.image.ImageRepository;
import com.advertisingmarketplace.image.ImageService;
import com.advertisingmarketplace.image.dto.ImageResponse;
import com.advertisingmarketplace.messaging.MessagingExceptions.BlockedException;
import com.advertisingmarketplace.messaging.MessagingExceptions.ConversationNotAllowedException;
import com.advertisingmarketplace.messaging.MessagingExceptions.ConversationNotFoundException;
import com.advertisingmarketplace.messaging.MessagingExceptions.InvalidMessageException;
import com.advertisingmarketplace.messaging.MessagingExceptions.MessageNotEditableException;
import com.advertisingmarketplace.messaging.MessagingExceptions.MessageNotFoundException;
import com.advertisingmarketplace.messaging.MessagingExceptions.MessageRateLimitException;
import com.advertisingmarketplace.messaging.MessagingExceptions.ParticipantGoneException;
import com.advertisingmarketplace.messaging.dto.ConversationResponse;
import com.advertisingmarketplace.messaging.dto.MessagePage;
import com.advertisingmarketplace.messaging.dto.MessageResponse;
import com.advertisingmarketplace.messaging.dto.SendMessageRequest;
import com.advertisingmarketplace.messaging.dto.UnreadCountResponse;
import com.advertisingmarketplace.messaging.realtime.ConversationEvent;
import com.advertisingmarketplace.messaging.realtime.ConversationEventPublisher;
import com.advertisingmarketplace.messaging.realtime.ConversationEventType;
import com.advertisingmarketplace.messaging.safety.BlockService;
import com.advertisingmarketplace.messaging.safety.ContactDetailsMasker;
import com.advertisingmarketplace.offer.Offer;
import com.advertisingmarketplace.posting.Posting;
import com.advertisingmarketplace.posting.PostingExceptions.PostingNotFoundException;
import com.advertisingmarketplace.posting.PostingRepository;
import com.advertisingmarketplace.posting.PostingStatus;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserNotFoundException;
import com.advertisingmarketplace.user.UserRepository;
import com.advertisingmarketplace.user.UserType;
import com.advertisingmarketplace.user.dto.UserSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessagingService {

    public static final int MAX_PAGE_SIZE = 100;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PostingRepository postingRepository;
    private final UserRepository userRepository;
    private final ImageRepository imageRepository;
    private final ImageService imageService;
    private final BlockService blockService;
    private final ContactDetailsMasker contactDetailsMasker;
    private final ConversationEventPublisher eventPublisher;
    private final MessagingProperties properties;
    private final Clock clock;
    private final MarketplaceMetrics metrics;

    public record StartResult(ConversationResponse conversation, boolean created) {
    }

    @Transactional
    public StartResult start(UUID postingId, UUID userId, String firstMessage) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        Posting posting = postingRepository.findWithCreatorById(postingId)
                .filter(p -> p.isVisibleTo(userId, false))
                .orElseThrow(() -> new PostingNotFoundException(postingId));
        if (user.getUserType() == UserType.ADMIN) {
            throw new ConversationNotAllowedException("Admins can't start conversations");
        }
        if (posting.isCreatedBy(userId)) {
            throw new ConversationNotAllowedException("You can't start a conversation on your own posting");
        }
        if (posting.getCategory().offerMakerType() != user.getUserType()) {
            throw new ConversationNotAllowedException("Only " + posting.getCategory().offerMakerType()
                    + " users can contact the creator of a " + posting.getCategory() + " posting");
        }
        if (blockService.isBlockedEitherWay(userId, posting.getCreator().getId())) {
            throw new BlockedException();
        }
        var existing = conversationRepository.findByPostingIdAndCounterpartKey(postingId, userId);
        if (existing.isEmpty() && posting.getStatus() != PostingStatus.OPEN) {
            throw new ConversationNotAllowedException("New conversations can only be started on OPEN postings");
        }
        boolean created = existing.isEmpty() && conversationRepository.insertIfAbsent(UUID.randomUUID(), postingId,
                posting.getCreator().getId(), userId, now()) == 1;
        Conversation conversation = existing.orElseGet(() -> conversationRepository
                .findByPostingIdAndCounterpartKey(postingId, userId).orElseThrow());
        if (firstMessage != null && !firstMessage.isBlank()) {
            send(conversation, user, firstMessage, List.of());
        }
        return new StartResult(toResponse(conversation, userId), created);
    }

    public Page<ConversationResponse> list(UUID userId, UUID postingId, Pageable pageable) {
        Page<Conversation> page = conversationRepository.findForParticipant(userId, postingId, pageable);
        List<UUID> ids = page.getContent().stream().map(Conversation::getId).toList();
        Map<UUID, Long> unread = ids.isEmpty() ? Map.of() : toCounts(messageRepository.countUnreadByConversation(userId, ids));
        return page.map(c -> toResponse(c, userId, unread.getOrDefault(c.getId(), 0L)));
    }

    public ConversationResponse get(UUID conversationId, UUID userId) {
        return toResponse(findForParticipant(conversationId, userId), userId);
    }

    public UnreadCountResponse unreadCount(UUID userId) {
        Map<UUID, Long> counts = toCounts(messageRepository.countUnreadForUser(userId));
        return new UnreadCountResponse(counts.values().stream().mapToLong(Long::longValue).sum(), counts.size());
    }

    public MessagePage messages(UUID conversationId, UUID userId, UUID before, int limit) {
        findForParticipant(conversationId, userId);
        return page(conversationId, before, limit);
    }

    public MessagePage page(UUID conversationId, UUID before, int limit) {
        int size = Math.clamp(limit, 1, MAX_PAGE_SIZE);
        List<Message> messages;
        if (before == null) {
            messages = messageRepository.findLatest(conversationId, Limit.of(size));
        } else {
            Message cursor = messageRepository.findById(before)
                    .filter(m -> m.getConversation().getId().equals(conversationId))
                    .orElseThrow(() -> new InvalidMessageException("Unknown cursor message " + before));
            messages = messageRepository.findBefore(conversationId, cursor.getCreatedAt(), cursor.getId(),
                    Limit.of(size));
        }
        List<MessageResponse> responses = toResponses(messages, true);
        UUID next = messages.size() == size ? messages.getLast().getId() : null;
        return new MessagePage(responses, next);
    }

    @Transactional
    public MessageResponse send(UUID conversationId, UUID senderId, SendMessageRequest request) {
        Conversation conversation = findForParticipant(conversationId, senderId);
        User sender = userRepository.findById(senderId).orElseThrow(() -> new UserNotFoundException(senderId));
        return send(conversation, sender, request.text(), request.imageIdsOrEmpty());
    }

    private MessageResponse send(Conversation conversation, User sender, String text, List<UUID> imageIds) {
        User other = conversation.otherParticipant(sender.getId()).orElseThrow(ParticipantGoneException::new);
        if (blockService.isBlockedEitherWay(sender.getId(), other.getId())) {
            throw new BlockedException();
        }
        enforceRateLimit(sender.getId());
        List<Image> images = resolveImages(sender.getId(), imageIds);
        String body = text == null || text.isBlank() ? null : contactDetailsMasker.apply(text.strip());

        Message message = messageRepository.save(Message.user(conversation, sender, body, now()));
        images.forEach(image -> image.attachTo(message));
        conversation.messageAdded(message);
        conversation.markRead(sender.getId(), message.getCreatedAt());
        publish(ConversationEventType.MESSAGE_CREATED, conversation, message.getId(), sender.getId());
        metrics.messageSent(images.isEmpty() ? "text" : "image");
        return toResponse(message, images, true);
    }

    @Transactional
    public MessageResponse edit(UUID messageId, UUID userId, String text) {
        Message message = findMessageForParticipant(messageId, userId);
        if (message.getType() != MessageType.USER || !message.isSentBy(userId) || message.isDeleted()) {
            throw new MessageNotEditableException("You can only edit your own messages");
        }
        message.edit(contactDetailsMasker.apply(text.strip()), now());
        publish(ConversationEventType.MESSAGE_UPDATED, message.getConversation(), messageId, userId);
        return toResponse(message, imageRepository.findAllByMessageId(messageId), true);
    }

    @Transactional
    public void delete(UUID messageId, UUID userId) {
        Message message = findMessageForParticipant(messageId, userId);
        if (message.getType() != MessageType.USER || !message.isSentBy(userId)) {
            throw new MessageNotEditableException("You can only delete your own messages");
        }
        deleteMessage(message, userId);
    }

    @Transactional
    public void deleteAsModerator(UUID messageId, UUID moderatorId) {
        Message message = messageRepository.findDetailedById(messageId)
                .orElseThrow(() -> new MessageNotFoundException(messageId));
        deleteMessage(message, moderatorId);
    }

    private void deleteMessage(Message message, UUID actorId) {
        if (message.isDeleted()) {
            return;
        }
        Instant now = now();
        message.delete(now);
        imageRepository.findAllByMessageId(message.getId()).forEach(image -> image.detach(now));
        publish(ConversationEventType.MESSAGE_DELETED, message.getConversation(), message.getId(), actorId);
    }

    @Transactional
    public void markRead(UUID conversationId, UUID userId) {
        Conversation conversation = findForParticipant(conversationId, userId);
        conversation.markRead(userId, now());
        publish(ConversationEventType.CONVERSATION_READ, conversation, null, userId);
    }

    public void typing(UUID conversationId, UUID userId) {
        Conversation conversation = findForParticipant(conversationId, userId);
        conversation.otherParticipant(userId).ifPresent(other -> eventPublisher.publish(new ConversationEvent(
                ConversationEventType.TYPING, conversationId, null, userId, List.of(other.getId()), now())));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordOfferEvent(Offer offer, SystemEvent event, User actor) {
        Posting posting = offer.getPosting();
        User offerer = offer.getOfferer();
        UUID creatorId = posting.getCreator().getId();
        if (conversationRepository.findByPostingIdAndCounterpartKey(posting.getId(), offerer.getId()).isEmpty()) {
            conversationRepository.insertIfAbsent(UUID.randomUUID(), posting.getId(), creatorId, offerer.getId(), now());
        }
        Conversation conversation = conversationRepository
                .findByPostingIdAndCounterpartKey(posting.getId(), offerer.getId()).orElseThrow();
        addSystemMessage(conversation, actor, event, offer, describe(event, actor, offer));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDealEvent(Offer offer, SystemEvent event, User actor, String text) {
        if (offer == null) {
            return;
        }
        Posting posting = offer.getPosting();
        conversationRepository.findByPostingIdAndCounterpartKey(posting.getId(), offer.getOfferer().getId())
                .ifPresent(conversation -> addSystemMessage(conversation, actor, event, offer, text));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPostingEvent(Posting posting, SystemEvent event, User actor) {
        for (Conversation conversation : conversationRepository.findAllByPostingId(posting.getId())) {
            addSystemMessage(conversation, actor, event, null, describe(event, actor, null));
        }
    }

    @Transactional(readOnly = true)
    public MessageResponse loadMessage(UUID messageId) {
        return messageRepository.findDetailedById(messageId)
                .map(message -> toResponse(message, imageRepository.findAllByMessageId(messageId), true))
                .orElse(null);
    }

    private void addSystemMessage(Conversation conversation, User actor, SystemEvent event, Offer offer, String text) {
        Message message = messageRepository.save(Message.system(conversation, actor, event, offer, text, now()));
        conversation.messageAdded(message);
        publish(ConversationEventType.MESSAGE_CREATED, conversation, message.getId(),
                actor == null ? null : actor.getId());
    }

    private static String describe(SystemEvent event, User actor, Offer offer) {
        String name = actor == null ? UserSummary.DELETED_USER_NAME : actor.getFullName();
        String price = offer == null ? "" : ": " + formatPrice(offer);
        return switch (event) {
            case OFFER_MADE -> name + " made an offer" + price;
            case OFFER_UPDATED -> name + " updated the offer" + price;
            case OFFER_ACCEPTED -> name + " accepted the offer" + price;
            case OFFER_REJECTED -> name + " rejected the offer" + price;
            case OFFER_WITHDRAWN -> name + " withdrew the offer" + price;
            case POSTING_CLOSED -> name + " closed the posting";
            case POSTING_REOPENED -> name + " reopened the posting";
            case DELIVERY_SUBMITTED, CHANGES_REQUESTED, DEAL_COMPLETED, DEAL_CANCELLED -> name + " updated the deal";
        };
    }

    private static String formatPrice(Offer offer) {
        String from = formatAmount(offer.getPriceFrom(), offer.getCurrency());
        String to = formatAmount(offer.getPriceTo(), offer.getCurrency());
        return (from.equals(to) ? from : from + " – " + to) + " " + offer.getCurrency();
    }

    private static String formatAmount(BigDecimal amount, String currency) {
        boolean whole = amount.stripTrailingZeros().scale() <= 0;
        int digits = whole ? 0 : Math.max(0, Currency.getInstance(currency).getDefaultFractionDigits());
        NumberFormat format = NumberFormat.getNumberInstance(Locale.ENGLISH);
        format.setMinimumFractionDigits(digits);
        format.setMaximumFractionDigits(digits);
        format.setRoundingMode(RoundingMode.HALF_EVEN);
        return format.format(amount);
    }

    private void enforceRateLimit(UUID senderId) {
        long recent = messageRepository.countBySenderIdAndTypeAndCreatedAtAfter(senderId, MessageType.USER,
                now().minus(Duration.ofMinutes(1)));
        if (recent >= properties.maxMessagesPerMinute()) {
            throw new MessageRateLimitException(Duration.ofMinutes(1));
        }
    }

    private List<Image> resolveImages(UUID senderId, List<UUID> imageIds) {
        Set<UUID> ids = new LinkedHashSet<>(imageIds);
        if (ids.size() > properties.maxImagesPerMessage()) {
            throw new InvalidMessageException("A message can have at most " + properties.maxImagesPerMessage()
                    + " images");
        }
        Map<UUID, Image> found = imageRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Image::getId, image -> image));
        for (UUID id : ids) {
            Image image = found.get(id);
            if (image == null || !image.isOwnedBy(senderId) || image.isAttached()) {
                throw new InvalidMessageException("Image " + id + " does not exist or cannot be used");
            }
        }
        return ids.stream().map(found::get).toList();
    }

    private void publish(ConversationEventType type, Conversation conversation, UUID messageId, UUID actorId) {
        eventPublisher.publish(new ConversationEvent(type, conversation.getId(), messageId, actorId,
                conversation.participantIds().toList(), now()));
    }

    Conversation findForParticipant(UUID conversationId, UUID userId) {
        return conversationRepository.findDetailedById(conversationId)
                .filter(c -> c.isParticipant(userId))
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
    }

    private Message findMessageForParticipant(UUID messageId, UUID userId) {
        return messageRepository.findDetailedById(messageId)
                .filter(m -> m.getConversation().isParticipant(userId))
                .orElseThrow(() -> new MessageNotFoundException(messageId));
    }

    private ConversationResponse toResponse(Conversation conversation, UUID viewerId) {
        Map<UUID, Long> unread = toCounts(messageRepository.countUnreadByConversation(viewerId,
                List.of(conversation.getId())));
        return toResponse(conversation, viewerId, unread.getOrDefault(conversation.getId(), 0L));
    }

    private ConversationResponse toResponse(Conversation conversation, UUID viewerId, long unread) {
        Posting posting = conversation.getPosting();
        Message last = conversation.getLastMessage();
        return new ConversationResponse(
                conversation.getId(),
                new ConversationResponse.PostingSummary(posting.getId(), posting.getTitle(), posting.getCategory(),
                        posting.getStatus()),
                UserSummary.fromNullable(conversation.getCreator()),
                UserSummary.fromNullable(conversation.getCounterpart()),
                UserSummary.fromNullable(conversation.otherParticipant(viewerId).orElse(null)),
                last == null ? null : toResponse(last, List.of(), false),
                unread,
                conversation.otherLastReadAt(viewerId),
                conversation.getLastMessageAt(),
                conversation.getCreatedAt());
    }

    List<MessageResponse> toResponses(List<Message> messages, boolean withImages) {
        Map<UUID, List<Image>> images = !withImages || messages.isEmpty() ? Map.of()
                : imageRepository.findAllByMessageIdIn(messages.stream().map(Message::getId).toList()).stream()
                        .collect(Collectors.groupingBy(image -> image.getMessage().getId()));
        return messages.stream().map(m -> toResponse(m, images.getOrDefault(m.getId(), List.of()), withImages))
                .toList();
    }

    MessageResponse toResponse(Message message, Collection<Image> images, boolean withImages) {
        List<ImageResponse> imageResponses = !withImages || message.isDeleted() ? List.of()
                : images.stream().sorted(Comparator.comparing(Image::getCreatedAt))
                        .map(imageService::toResponse).toList();
        return new MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getType(),
                message.getSystemEvent(),
                message.getType() == MessageType.SYSTEM && message.getSender() == null ? null
                        : UserSummary.fromNullable(message.getSender()),
                message.getText(),
                message.getOffer() == null ? null : message.getOffer().getId(),
                imageResponses,
                message.isDeleted(),
                message.getCreatedAt(),
                message.getEditedAt());
    }

    private static Map<UUID, Long> toCounts(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
