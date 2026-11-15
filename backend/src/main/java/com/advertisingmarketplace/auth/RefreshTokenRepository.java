package com.advertisingmarketplace.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokedReason = :reason "
            + "where t.user.id = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now,
            @Param("reason") RevocationReason reason);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff or t.revokedAt < :cutoff")
    int deleteExpiredOrRevokedBefore(@Param("cutoff") Instant cutoff);

    long countByUserIdAndRevokedAtIsNull(UUID userId);
}
