package com.advertisingmarketplace.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.advertisingmarketplace.common.observability.MarketplaceMetrics;
import com.advertisingmarketplace.image.Image;
import com.advertisingmarketplace.image.ImageProperties;
import com.advertisingmarketplace.image.ImageRepository;
import com.advertisingmarketplace.image.ImageService;
import com.advertisingmarketplace.image.scan.VirusScanner;
import com.advertisingmarketplace.image.storage.ObjectStorage;
import com.advertisingmarketplace.messaging.MessagingExceptions.BlockedException;
import com.advertisingmarketplace.messaging.MessagingExceptions.ConversationNotAllowedException;
import com.advertisingmarketplace.messaging.MessagingExceptions.ConversationNotFoundException;
import com.advertisingmarketplace.messaging.MessagingExceptions.InvalidMessageException;
import com.advertisingmarketplace.messaging.MessagingExceptions.MessageNotEditableException;
import com.advertisingmarketplace.messaging.MessagingExceptions.MessageRateLimitException;
import com.advertisingmarketplace.messaging.dto.MessageResponse;
import com.advertisingmarketplace.messaging.dto.SendMessageRequest;
import com.advertisingmarketplace.messaging.realtime.ConversationEvent;
import com.advertisingmarketplace.messaging.realtime.ConversationEventPublisher;
import com.advertisingmarketplace.messaging.realtime.ConversationEventType;
import com.advertisingmarketplace.messaging.safety.BlockService;
import com.advertisingmarketplace.messaging.safety.ContactDetailsMasker;
import com.advertisingmarketplace.offer.Offer;
import com.advertisingmarketplace.posting.Posting;
import com.advertisingmarketplace.posting.PostingCategory;
import com.advertisingmarketplace.posting.PostingRepository;
import com.advertisingmarketplace.posting.PostingStatus;
import com.advertisingmarketplace.support.MutableClock;
import com.advertisingmarketplace.support.TestEntities;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserRepository;
import com.advertisingmarketplace.user.UserType;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;

class MessagingServiceTest {

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final PostingRepository postingRepository = mock(PostingRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ImageRepository imageRepository = mock(ImageRepository.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final BlockService blockService = mock(BlockService.class);
    private final ConversationEventPublisher publisher = mock(ConversationEventPublisher.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final MessagingProperties properties = new MessagingProperties(3, 2, true, Duration.ofSeconds(60),
            Duration.ofMinutes(30), 5);
    private final ImageService imageService = new ImageService(imageRepository, userRepository,
            mock(VirusScanner.class), storage, new ImageProperties(DataSize.ofMegabytes(10), 20, 60, Duration.ofHours(1)),
            clock, MarketplaceMetrics.noop());
    private final MessagingService service = new MessagingService(conversationRepository, messageRepository,
            postingRepository, userRepository, imageRepository, imageService, blockService,
            new ContactDetailsMasker(properties), publisher, properties, clock, MarketplaceMetrics.noop());

    private final User brand = TestEntities.user(UserType.CUSTOMER);
    private final User streamer = TestEntities.user(UserType.MARKETER);
    private final User otherStreamer = TestEntities.user(UserType.MARKETER);
    private final User admin = TestEntities.user(UserType.ADMIN);
    private Posting posting;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        for (User user : List.of(brand, streamer, otherStreamer, admin)) {
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        }
        posting = TestEntities.posting(brand, PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN, "x");
        when(postingRepository.findWithCreatorById(posting.getId())).thenReturn(Optional.of(posting));
        conversation = new Conversation(UUID.randomUUID(), posting, brand, streamer, clock.instant());
        when(conversationRepository.findDetailedById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.countUnreadByConversation(any(), any())).thenReturn(List.of());
        when(storage.signedGetUrl(anyString())).thenAnswer(inv -> new ObjectStorage.SignedUrl(
                URI.create("https://bucket.example/" + inv.getArgument(0)), clock.instant().plusSeconds(3600)));
        when(imageRepository.findAllById(any())).thenReturn(List.of());
    }

    @Nested
    class Start {

        @Test
        void streamerStartsConversationOnCustomerPosting() {
            when(conversationRepository.findByPostingIdAndCounterpartKey(posting.getId(), streamer.getId()))
                    .thenReturn(Optional.empty(), Optional.of(conversation));
            when(conversationRepository.insertIfAbsent(any(), eq(posting.getId()), eq(brand.getId()),
                    eq(streamer.getId()), any())).thenReturn(1);

            MessagingService.StartResult result = service.start(posting.getId(), streamer.getId(), null);

            assertThat(result.created()).isTrue();
            assertThat(result.conversation().otherParticipant().id()).isEqualTo(brand.getId());
            assertThat(result.conversation().posting().id()).isEqualTo(posting.getId());
        }

        @Test
        void existingConversationIsReturnedEvenOnClosedPosting() {
            posting.setStatus(PostingStatus.CLOSED);
            when(conversationRepository.findByPostingIdAndCounterpartKey(posting.getId(), streamer.getId()))
                    .thenReturn(Optional.of(conversation));

            MessagingService.StartResult result = service.start(posting.getId(), streamer.getId(), null);

            assertThat(result.created()).isFalse();
            verify(conversationRepository, never()).insertIfAbsent(any(), any(), any(), any(), any());
        }

        @Test
        void rules() {
            when(conversationRepository.findByPostingIdAndCounterpartKey(any(), any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.start(posting.getId(), brand.getId(), null))
                    .isInstanceOf(ConversationNotAllowedException.class);
            assertThatThrownBy(() -> service.start(posting.getId(), admin.getId(), null))
                    .isInstanceOf(ConversationNotAllowedException.class);
            User otherBrand = TestEntities.user(UserType.CUSTOMER);
            when(userRepository.findById(otherBrand.getId())).thenReturn(Optional.of(otherBrand));
            assertThatThrownBy(() -> service.start(posting.getId(), otherBrand.getId(), null))
                    .isInstanceOf(ConversationNotAllowedException.class)
                    .hasMessage("Only MARKETER users can contact the creator of a CUSTOMER_POSTING posting");

            when(blockService.isBlockedEitherWay(streamer.getId(), brand.getId())).thenReturn(true);
            assertThatThrownBy(() -> service.start(posting.getId(), streamer.getId(), null))
                    .isInstanceOf(BlockedException.class);

            posting.setStatus(PostingStatus.CLOSED);
            assertThatThrownBy(() -> service.start(posting.getId(), otherStreamer.getId(), null))
                    .isInstanceOf(ConversationNotAllowedException.class)
                    .hasMessage("New conversations can only be started on OPEN postings");
        }
    }

    @Nested
    class Send {

        @Test
        void storesMasksAndPublishesToBothParticipants() {
            MessageResponse response = service.send(conversation.getId(), streamer.getId(),
                    new SendMessageRequest("  write me at jane@example.com  ", null));

            assertThat(response.text()).isEqualTo("write me at [contact details hidden]");
            assertThat(response.sender().id()).isEqualTo(streamer.getId());
            assertThat(conversation.getLastMessage().getId()).isEqualTo(response.id());
            assertThat(conversation.lastReadAt(streamer.getId())).isEqualTo(response.createdAt());

            ArgumentCaptor<ConversationEvent> event = ArgumentCaptor.forClass(ConversationEvent.class);
            verify(publisher).publish(event.capture());
            assertThat(event.getValue().type()).isEqualTo(ConversationEventType.MESSAGE_CREATED);
            assertThat(event.getValue().messageId()).isEqualTo(response.id());
            assertThat(event.getValue().recipientIds()).containsExactly(brand.getId(), streamer.getId());
        }

        @Test
        void attachesOwnUnusedImages() {
            Image image = TestEntities.image(streamer);
            when(imageRepository.findAllById(any())).thenAnswer(inv -> {
                Collection<UUID> ids = inv.getArgument(0);
                return ids.contains(image.getId()) ? List.of(image) : List.of();
            });

            MessageResponse response = service.send(conversation.getId(), streamer.getId(),
                    new SendMessageRequest(null, List.of(image.getId())));

            assertThat(image.getMessage().getId()).isEqualTo(response.id());
            assertThat(response.images()).extracting(i -> i.id()).containsExactly(image.getId());
            assertThat(response.text()).isNull();
        }

        @Test
        void rejectsForeignOrUsedImagesAndTooMany() {
            Image foreign = TestEntities.image(brand);
            when(imageRepository.findAllById(any())).thenReturn(List.of(foreign));

            assertThatThrownBy(() -> service.send(conversation.getId(), streamer.getId(),
                    new SendMessageRequest(null, List.of(foreign.getId()))))
                    .isInstanceOf(InvalidMessageException.class);
            assertThatThrownBy(() -> service.send(conversation.getId(), streamer.getId(),
                    new SendMessageRequest(null, List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))))
                    .isInstanceOf(InvalidMessageException.class)
                    .hasMessage("A message can have at most 2 images");
        }

        @Test
        void outsidersCantSeeTheConversation() {
            assertThatThrownBy(() -> service.send(conversation.getId(), otherStreamer.getId(),
                    new SendMessageRequest("hi", null)))
                    .isInstanceOf(ConversationNotFoundException.class);
        }

        @Test
        void blockedUsersCantSend() {
            when(blockService.isBlockedEitherWay(streamer.getId(), brand.getId())).thenReturn(true);

            assertThatThrownBy(() -> service.send(conversation.getId(), streamer.getId(),
                    new SendMessageRequest("hi", null)))
                    .isInstanceOf(BlockedException.class);
            verify(messageRepository, never()).save(any());
        }

        @Test
        void rateLimited() {
            when(messageRepository.countBySenderIdAndTypeAndCreatedAtAfter(streamer.getId(), MessageType.USER,
                    clock.instant().minus(Duration.ofMinutes(1)))).thenReturn(3L);

            assertThatThrownBy(() -> service.send(conversation.getId(), streamer.getId(),
                    new SendMessageRequest("hi", null)))
                    .isInstanceOf(MessageRateLimitException.class)
                    .satisfies(e -> assertThat(((MessageRateLimitException) e).retryAfter())
                            .contains(Duration.ofMinutes(1)));
        }
    }

    @Nested
    class EditAndDelete {

        private Message message;

        @BeforeEach
        void setUp() {
            message = Message.user(conversation, streamer, "hello", clock.instant());
            when(messageRepository.findDetailedById(message.getId())).thenReturn(Optional.of(message));
            when(imageRepository.findAllByMessageId(message.getId())).thenReturn(List.of());
        }

        @Test
        void senderEditsAndDeletes() {
            clock.advance(Duration.ofMinutes(1));

            assertThat(service.edit(message.getId(), streamer.getId(), " hi there ").text()).isEqualTo("hi there");
            assertThat(message.getEditedAt()).isEqualTo(clock.instant());

            service.delete(message.getId(), streamer.getId());
            assertThat(message.isDeleted()).isTrue();
        }

        @Test
        void otherParticipantCantEditOrDelete() {
            assertThatThrownBy(() -> service.edit(message.getId(), brand.getId(), "x"))
                    .isInstanceOf(MessageNotEditableException.class);
            assertThatThrownBy(() -> service.delete(message.getId(), brand.getId()))
                    .isInstanceOf(MessageNotEditableException.class);
        }

        @Test
        void deletingDetachesImages() {
            Image image = TestEntities.image(streamer);
            image.attachTo(message);
            when(imageRepository.findAllByMessageId(message.getId())).thenReturn(List.of(image));

            service.delete(message.getId(), streamer.getId());

            assertThat(image.isAttached()).isFalse();
            assertThat(image.getOrphanedSince()).isEqualTo(clock.instant());
        }
    }

    @Test
    void offerEventsCreateTheConversationAndAddASystemMessage() {
        Offer offer = TestEntities.offer(posting, streamer);
        when(conversationRepository.findByPostingIdAndCounterpartKey(posting.getId(), streamer.getId()))
                .thenReturn(Optional.empty(), Optional.of(conversation));

        service.recordOfferEvent(offer, SystemEvent.OFFER_MADE, streamer);

        verify(conversationRepository).insertIfAbsent(any(), eq(posting.getId()), eq(brand.getId()),
                eq(streamer.getId()), any());
        Message message = conversation.getLastMessage();
        assertThat(message.getType()).isEqualTo(MessageType.SYSTEM);
        assertThat(message.getSystemEvent()).isEqualTo(SystemEvent.OFFER_MADE);
        assertThat(message.getOffer()).isSameAs(offer);
        assertThat(message.getText()).isEqualTo("User MARKETER made an offer: 100 – 200 EUR");
    }

    @Test
    void markReadPublishesReceipt() {
        clock.advance(Duration.ofMinutes(2));

        service.markRead(conversation.getId(), brand.getId());

        assertThat(conversation.lastReadAt(brand.getId())).isEqualTo(clock.instant());
        ArgumentCaptor<ConversationEvent> event = ArgumentCaptor.forClass(ConversationEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().type()).isEqualTo(ConversationEventType.CONVERSATION_READ);
    }

    @Test
    void typingGoesOnlyToTheOtherParticipant() {
        service.typing(conversation.getId(), streamer.getId());

        ArgumentCaptor<ConversationEvent> event = ArgumentCaptor.forClass(ConversationEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().type()).isEqualTo(ConversationEventType.TYPING);
        assertThat(event.getValue().recipientIds()).containsExactly(brand.getId());
    }
}
