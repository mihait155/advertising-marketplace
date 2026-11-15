package com.advertisingmarketplace.messaging;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @EntityGraph(attributePaths = {"conversation", "conversation.posting", "conversation.creator",
            "conversation.counterpart", "sender"})
    Optional<Message> findDetailedById(UUID id);

    @EntityGraph(attributePaths = "sender")
    @Query("select m from Message m where m.conversation.id = :conversationId order by m.createdAt desc, m.id desc")
    List<Message> findLatest(@Param("conversationId") UUID conversationId, Limit limit);

    @EntityGraph(attributePaths = "sender")
    @Query("""
            select m from Message m
            where m.conversation.id = :conversationId
              and (m.createdAt < :beforeAt or (m.createdAt = :beforeAt and m.id < :beforeId))
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findBefore(@Param("conversationId") UUID conversationId, @Param("beforeAt") Instant beforeAt,
            @Param("beforeId") UUID beforeId, Limit limit);

    @EntityGraph(attributePaths = "sender")
    @Query("""
            select m from Message m
            where m.conversation.id = :conversationId and m.deletedAt is null
              and (m.sender is null or m.sender.id <> :recipientId)
              and m.createdAt > :after
            order by m.createdAt
            """)
    List<Message> findUnreadFor(@Param("conversationId") UUID conversationId, @Param("recipientId") UUID recipientId,
            @Param("after") Instant after);

    long countBySenderIdAndTypeAndCreatedAtAfter(UUID senderId, MessageType type, Instant after);

    @Query(UNREAD_SELECT + " and c.id in :conversationIds group by m.conversation.id")
    List<Object[]> countUnreadByConversation(@Param("userId") UUID userId,
            @Param("conversationIds") Collection<UUID> conversationIds);

    @Query(UNREAD_SELECT + " group by m.conversation.id")
    List<Object[]> countUnreadForUser(@Param("userId") UUID userId);

    String UNREAD_SELECT = """
            select m.conversation.id, count(m) from Message m join m.conversation c
            where m.deletedAt is null
              and (m.sender is null or m.sender.id <> :userId)
              and ((c.creator.id = :userId and (c.creatorLastReadAt is null or m.createdAt > c.creatorLastReadAt))
                or (c.counterpart.id = :userId
                    and (c.counterpartLastReadAt is null or m.createdAt > c.counterpartLastReadAt)))
            """;
}
