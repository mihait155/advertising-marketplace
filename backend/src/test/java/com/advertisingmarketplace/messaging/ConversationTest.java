package com.advertisingmarketplace.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.advertisingmarketplace.posting.PostingCategory;
import com.advertisingmarketplace.support.TestEntities;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationTest {

    private final User creator = TestEntities.user(UserType.CUSTOMER);
    private final User counterpart = TestEntities.user(UserType.MARKETER);
    private final Instant now = Instant.parse("2026-01-01T10:00:00Z");
    private final Conversation conversation = new Conversation(UUID.randomUUID(),
            TestEntities.posting(creator, PostingCategory.CUSTOMER_POSTING, "x"), creator, counterpart, now);

    @Test
    void knowsItsParticipants() {
        assertThat(conversation.isParticipant(creator.getId())).isTrue();
        assertThat(conversation.isParticipant(counterpart.getId())).isTrue();
        assertThat(conversation.isParticipant(UUID.randomUUID())).isFalse();
        assertThat(conversation.isParticipant(null)).isFalse();
        assertThat(conversation.otherParticipant(creator.getId())).contains(counterpart);
        assertThat(conversation.otherParticipant(counterpart.getId())).contains(creator);
        assertThat(conversation.participantIds()).containsExactly(creator.getId(), counterpart.getId());
        assertThat(conversation.getCounterpartKey()).isEqualTo(counterpart.getId());
        assertThat(conversation.getLastMessageAt()).isEqualTo(now);
    }

    @Test
    void readStateIsPerParticipantAndOnlyMovesForward() {
        conversation.markRead(creator.getId(), now.plusSeconds(10));
        conversation.markRead(creator.getId(), now.plusSeconds(5));
        conversation.markRead(counterpart.getId(), now.plusSeconds(3));

        assertThat(conversation.lastReadAt(creator.getId())).isEqualTo(now.plusSeconds(10));
        assertThat(conversation.otherLastReadAt(creator.getId())).isEqualTo(now.plusSeconds(3));
        assertThat(conversation.lastReadAt(counterpart.getId())).isEqualTo(now.plusSeconds(3));
    }

    @Test
    void tracksLastMessage() {
        Message message = Message.user(conversation, creator, "hi", now.plusSeconds(60));

        conversation.messageAdded(message);

        assertThat(conversation.getLastMessage()).isSameAs(message);
        assertThat(conversation.getLastMessageAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void messagesCanBeEditedAndSoftDeleted() {
        Message message = Message.user(conversation, creator, "hi", now);

        message.edit("hello", now.plusSeconds(1));
        assertThat(message.getText()).isEqualTo("hello");
        assertThat(message.getEditedAt()).isEqualTo(now.plusSeconds(1));

        message.delete(now.plusSeconds(2));
        assertThat(message.isDeleted()).isTrue();
        assertThat(message.getText()).isNull();
        assertThat(message.isSentBy(creator.getId())).isTrue();
    }
}
