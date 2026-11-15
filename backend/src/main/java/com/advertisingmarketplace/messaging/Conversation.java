package com.advertisingmarketplace.messaging;

import com.advertisingmarketplace.posting.Posting;
import com.advertisingmarketplace.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posting_id", nullable = false, updatable = false)
    private Posting posting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterpart_id")
    private User counterpart;

    @Column(name = "counterpart_key", nullable = false, updatable = false)
    private UUID counterpartKey;

    @Column(name = "creator_last_read_at")
    private Instant creatorLastReadAt;

    @Column(name = "counterpart_last_read_at")
    private Instant counterpartLastReadAt;

    @Column(name = "creator_notified_up_to")
    private Instant creatorNotifiedUpTo;

    @Column(name = "counterpart_notified_up_to")
    private Instant counterpartNotifiedUpTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_message_id")
    private Message lastMessage;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Conversation(UUID id, Posting posting, User creator, User counterpart, Instant now) {
        this.id = id;
        this.posting = posting;
        this.creator = creator;
        this.counterpart = counterpart;
        this.counterpartKey = counterpart.getId();
        this.createdAt = now;
        this.lastMessageAt = now;
    }

    public boolean isParticipant(UUID userId) {
        return isCreator(userId) || isCounterpart(userId);
    }

    public boolean isCreator(UUID userId) {
        return userId != null && creator != null && creator.getId().equals(userId);
    }

    public boolean isCounterpart(UUID userId) {
        return userId != null && counterpart != null && counterpart.getId().equals(userId);
    }

    public Optional<User> otherParticipant(UUID userId) {
        return Optional.ofNullable(isCreator(userId) ? counterpart : creator);
    }

    public Stream<UUID> participantIds() {
        return Stream.of(creator, counterpart).filter(Objects::nonNull).map(User::getId);
    }

    public Instant lastReadAt(UUID userId) {
        return isCreator(userId) ? creatorLastReadAt : counterpartLastReadAt;
    }

    public Instant otherLastReadAt(UUID userId) {
        return isCreator(userId) ? counterpartLastReadAt : creatorLastReadAt;
    }

    public void markRead(UUID userId, Instant at) {
        if (isCreator(userId) && (creatorLastReadAt == null || at.isAfter(creatorLastReadAt))) {
            creatorLastReadAt = at;
        } else if (isCounterpart(userId) && (counterpartLastReadAt == null || at.isAfter(counterpartLastReadAt))) {
            counterpartLastReadAt = at;
        }
    }

    public Instant notifiedUpTo(UUID userId) {
        return isCreator(userId) ? creatorNotifiedUpTo : counterpartNotifiedUpTo;
    }

    public void markNotified(UUID userId, Instant upTo) {
        if (isCreator(userId)) {
            creatorNotifiedUpTo = upTo;
        } else if (isCounterpart(userId)) {
            counterpartNotifiedUpTo = upTo;
        }
    }

    public void messageAdded(Message message) {
        this.lastMessage = message;
        this.lastMessageAt = message.getCreatedAt();
    }
}
