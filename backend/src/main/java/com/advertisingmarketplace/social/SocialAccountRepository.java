package com.advertisingmarketplace.social;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {

    List<SocialAccount> findAllByUserIdOrderByFollowersDesc(UUID userId);

    Optional<SocialAccount> findByUserIdAndProvider(UUID userId, SocialProvider provider);

    Optional<SocialAccount> findByProviderAndExternalId(SocialProvider provider, String externalId);

    @Query("""
            select a.id from SocialAccount a
            where a.encryptedRefreshToken is not null and a.needsReconnect = false
            order by a.lastSyncedAt
            """)
    List<UUID> findSyncable(Limit limit);
}
