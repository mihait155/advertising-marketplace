package com.advertisingmarketplace.image;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImageRepository extends JpaRepository<Image, UUID> {

    List<Image> findAllByPostingIdOrderByCreatedAt(UUID postingId);

    List<Image> findAllByPostingIdIn(Collection<UUID> postingIds);

    List<Image> findAllByMessageIdIn(Collection<UUID> messageIds);

    List<Image> findAllByMessageId(UUID messageId);

    long countByOwnerIdAndCreatedAtAfter(UUID ownerId, Instant after);

    @Query("select i from Image i where i.posting is null and i.message is null "
            + "and (i.orphanedSince is null or i.orphanedSince < :cutoff) order by i.createdAt")
    List<Image> findOrphansBefore(@Param("cutoff") Instant cutoff, Limit limit);
}
