package com.advertisingmarketplace.messaging;

import com.advertisingmarketplace.offer.Offer;
import com.advertisingmarketplace.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10, updatable = false)
    private MessageType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_event", length = 30, updatable = false)
    private SystemEvent systemEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id")
    private Offer offer;

    @Column(name = "body", columnDefinition = "text")
    private String text;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Message(Conversation conversation, User sender, MessageType type, SystemEvent systemEvent, Offer offer,
            String text, Instant now) {
        this.id = UUID.randomUUID();
        this.conversation = conversation;
        this.sender = sender;
        this.type = type;
        this.systemEvent = systemEvent;
        this.offer = offer;
        this.text = text;
        this.createdAt = now;
    }

    public static Message user(Conversation conversation, User sender, String text, Instant now) {
        return new Message(conversation, sender, MessageType.USER, null, null, text, now);
    }

    public static Message system(Conversation conversation, User actor, SystemEvent event, Offer offer, String text,
            Instant now) {
        return new Message(conversation, actor, MessageType.SYSTEM, event, offer, text, now);
    }

    public boolean isSentBy(UUID userId) {
        return sender != null && sender.getId().equals(userId);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void edit(String newText, Instant now) {
        this.text = newText;
        this.editedAt = now;
    }

    public void delete(Instant now) {
        this.text = null;
        this.deletedAt = now;
    }
}
