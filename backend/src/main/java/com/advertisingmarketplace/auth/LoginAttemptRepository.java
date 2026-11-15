package com.advertisingmarketplace.auth;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into login_attempts (attempt_key, failures, window_start)
            values (:key, 1, :now)
            on conflict (attempt_key) do update set
                failures = case when login_attempts.window_start <= :cutoff then 1
                                else login_attempts.failures + 1 end,
                window_start = case when login_attempts.window_start <= :cutoff then :now
                                    else login_attempts.window_start end
            """)
    void recordFailure(@Param("key") String key, @Param("now") Instant now, @Param("cutoff") Instant cutoff);

    @Modifying
    @Query("delete from LoginAttempt a where a.windowStart <= :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
