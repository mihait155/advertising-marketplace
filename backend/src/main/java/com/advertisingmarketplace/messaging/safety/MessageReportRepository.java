package com.advertisingmarketplace.messaging.safety;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReportRepository extends JpaRepository<MessageReport, UUID> {

    boolean existsByMessageIdAndReporterId(UUID messageId, UUID reporterId);

    boolean existsByMessageConversationId(UUID conversationId);

    List<MessageReport> findAllByMessageIdAndStatus(UUID messageId, ReportStatus status);

    @EntityGraph(attributePaths = {"message", "message.conversation", "message.sender", "reporter", "resolvedBy"})
    Page<MessageReport> findAllByStatus(ReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"message", "message.conversation", "message.sender", "reporter", "resolvedBy"})
    Optional<MessageReport> findDetailedById(UUID id);
}
