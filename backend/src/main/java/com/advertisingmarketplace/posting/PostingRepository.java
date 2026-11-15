package com.advertisingmarketplace.posting;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostingRepository extends JpaRepository<Posting, UUID> {

    @EntityGraph(attributePaths = "creator")
    Optional<Posting> findWithCreatorById(UUID id);

    @EntityGraph(attributePaths = "creator")
    @Query("""
            select p from Posting p
            where (:category is null or p.category = :category)
              and (:status is null or p.status = :status)
              and (:creatorId is null or p.creator.id = :creatorId)
              and (:includeAllDrafts = true or p.status <> :draft or p.creator.id = :viewerId)
              and (:minPayEur is null or p.payToEur >= :minPayEur or (p.payToEur is null and p.payFromEur is not null))
              and (:maxPayEur is null or p.payFromEur <= :maxPayEur or (p.payFromEur is null and p.payToEur is not null))
            """)
    Page<Posting> search(@Param("category") PostingCategory category, @Param("status") PostingStatus status,
            @Param("creatorId") UUID creatorId, @Param("viewerId") UUID viewerId,
            @Param("includeAllDrafts") boolean includeAllDrafts, @Param("draft") PostingStatus draft,
            @Param("minPayEur") BigDecimal minPayEur, @Param("maxPayEur") BigDecimal maxPayEur, Pageable pageable);

    default Page<Posting> searchVisible(PostingSearch search, UUID viewerId, boolean viewerIsAdmin, Pageable pageable) {
        return search(search.category(), search.status(), search.creatorId(), viewerId, viewerIsAdmin,
                PostingStatus.DRAFT, search.minPayEur(), search.maxPayEur(), pageable);
    }

    @Modifying
    @Query(nativeQuery = true, value = """
            update postings p
            set pay_from_eur = round(p.pay_from / r.units_per_eur, 2),
                pay_to_eur   = round(p.pay_to / r.units_per_eur, 2)
            from exchange_rates r
            where r.currency = p.pay_currency
            """)
    int recomputeEurAmounts();
}
