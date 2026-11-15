package com.advertisingmarketplace.offer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    @EntityGraph(attributePaths = {"posting", "posting.creator", "offerer"})
    Optional<Offer> findDetailedById(UUID id);

    boolean existsByPostingIdAndOffererIdAndStatus(UUID postingId, UUID offererId, OfferStatus status);

    boolean existsByPostingIdAndStatusAndCurrencyNot(UUID postingId, OfferStatus status, String currency);

    @EntityGraph(attributePaths = {"posting", "posting.creator", "offerer"})
    @Query("""
            select o from Offer o
            where o.posting.id = :postingId
              and (:offererId is null or o.offerer.id = :offererId)
              and (:status is null or o.status = :status)
            """)
    Page<Offer> findForPosting(@Param("postingId") UUID postingId, @Param("offererId") UUID offererId,
            @Param("status") OfferStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"posting", "posting.creator", "offerer"})
    @Query("""
            select o from Offer o
            where o.offerer.id = :offererId
              and (:status is null or o.status = :status)
            """)
    Page<Offer> findByOfferer(@Param("offererId") UUID offererId, @Param("status") OfferStatus status,
            Pageable pageable);
}
