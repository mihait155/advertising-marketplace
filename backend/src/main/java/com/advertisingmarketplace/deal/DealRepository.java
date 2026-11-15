package com.advertisingmarketplace.deal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealRepository extends JpaRepository<Deal, UUID> {

    @EntityGraph(attributePaths = {"marketer", "customer", "offer", "offer.offerer", "posting", "cancelledBy"})
    Optional<Deal> findDetailedById(UUID id);

    Optional<Deal> findByOfferId(UUID offerId);

    boolean existsByPostingIdAndStatusIn(UUID postingId, Collection<DealStatus> statuses);

    @Query("""
            select count(d) > 0 from Deal d
            where (d.marketer.id = :userId or d.customer.id = :userId) and d.status in :statuses
            """)
    boolean existsForParticipantWithStatusIn(@Param("userId") UUID userId,
            @Param("statuses") Collection<DealStatus> statuses);

    @EntityGraph(attributePaths = {"marketer", "customer"})
    @Query("""
            select d from Deal d
            where (d.marketer.id = :userId or d.customer.id = :userId)
              and (:status is null or d.status = :status)
            """)
    Page<Deal> findForParticipant(@Param("userId") UUID userId, @Param("status") DealStatus status,
            Pageable pageable);

    @Query("select d.id from Deal d where d.status = :status and d.autoCompleteAt <= :now order by d.autoCompleteAt")
    List<UUID> findDueForAutoCompletion(@Param("status") DealStatus status, @Param("now") Instant now, Limit limit);

    @Query("""
            select count(d) from Deal d
            where (d.marketer.id = :userId or d.customer.id = :userId) and d.status = :status
            """)
    long countForParticipant(@Param("userId") UUID userId, @Param("status") DealStatus status);
}
