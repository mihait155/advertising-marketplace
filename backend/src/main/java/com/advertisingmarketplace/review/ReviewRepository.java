package com.advertisingmarketplace.review;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    String VISIBLE = """
            r.reviewee.id = :userId
              and (r.deal.completedAt <= :revealCutoff
                   or exists (select 1 from Review other where other.deal = r.deal and other.id <> r.id))
            """;

    @EntityGraph(attributePaths = "reviewer")
    List<Review> findAllByDealId(UUID dealId);

    boolean existsByDealIdAndRevieweeId(UUID dealId, UUID revieweeId);

    @EntityGraph(attributePaths = {"reviewer", "deal"})
    @Query("select r from Review r where " + VISIBLE)
    Page<Review> findVisibleFor(@Param("userId") UUID userId, @Param("revealCutoff") Instant revealCutoff,
            Pageable pageable);

    @Query("select coalesce(avg(r.rating), 0), count(r) from Review r where " + VISIBLE)
    List<Object[]> ratingSummary(@Param("userId") UUID userId, @Param("revealCutoff") Instant revealCutoff);
}
