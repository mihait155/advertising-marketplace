package com.advertisingmarketplace.messaging.safety;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlockId> {

    List<UserBlock> findAllByIdBlockerIdOrderByCreatedAtDesc(UUID blockerId);

    @Query("""
            select count(b) > 0 from UserBlock b
            where (b.id.blockerId = :a and b.id.blockedId = :b) or (b.id.blockerId = :b and b.id.blockedId = :a)
            """)
    boolean existsBetween(@Param("a") UUID a, @Param("b") UUID b);
}
