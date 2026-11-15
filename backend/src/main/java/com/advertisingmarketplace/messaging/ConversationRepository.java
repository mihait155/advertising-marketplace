package com.advertisingmarketplace.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @EntityGraph(attributePaths = {"posting", "creator", "counterpart"})
    Optional<Conversation> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"posting", "creator", "counterpart"})
    Optional<Conversation> findByPostingIdAndCounterpartKey(UUID postingId, UUID counterpartKey);

    @EntityGraph(attributePaths = {"posting", "creator", "counterpart"})
    List<Conversation> findAllByPostingId(UUID postingId);

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into conversations (id, posting_id, creator_id, counterpart_id, counterpart_key, created_at,
                                       last_message_at)
            values (:id, :postingId, :creatorId, :counterpartId, :counterpartId, :now, :now)
            on conflict (posting_id, counterpart_key) do nothing
            """)
    int insertIfAbsent(@Param("id") UUID id, @Param("postingId") UUID postingId, @Param("creatorId") UUID creatorId,
            @Param("counterpartId") UUID counterpartId, @Param("now") Instant now);

    @EntityGraph(attributePaths = {"posting", "creator", "counterpart", "lastMessage", "lastMessage.sender"})
    @Query("""
            select c from Conversation c
            where (c.creator.id = :userId or c.counterpart.id = :userId)
              and (:postingId is null or c.posting.id = :postingId)
            """)
    Page<Conversation> findForParticipant(@Param("userId") UUID userId, @Param("postingId") UUID postingId,
            Pageable pageable);

    @Query(nativeQuery = true, value = """
            select c.id from conversations c
            where c.creator_id is not null
              and exists (select 1 from messages m
                          where m.conversation_id = c.id and m.deleted_at is null
                            and m.sender_id is distinct from c.creator_id
                            and m.created_at > coalesce(c.creator_last_read_at, '-infinity'::timestamptz)
                            and m.created_at > coalesce(c.creator_notified_up_to, '-infinity'::timestamptz)
                            and m.created_at <= :threshold)
            limit :limit
            for update skip locked
            """)
    List<UUID> lockCreatorsToNotify(@Param("threshold") Instant threshold, @Param("limit") int limit);

    @Query(nativeQuery = true, value = """
            select c.id from conversations c
            where c.counterpart_id is not null
              and exists (select 1 from messages m
                          where m.conversation_id = c.id and m.deleted_at is null
                            and m.sender_id is distinct from c.counterpart_id
                            and m.created_at > coalesce(c.counterpart_last_read_at, '-infinity'::timestamptz)
                            and m.created_at > coalesce(c.counterpart_notified_up_to, '-infinity'::timestamptz)
                            and m.created_at <= :threshold)
            limit :limit
            for update skip locked
            """)
    List<UUID> lockCounterpartsToNotify(@Param("threshold") Instant threshold, @Param("limit") int limit);
}
