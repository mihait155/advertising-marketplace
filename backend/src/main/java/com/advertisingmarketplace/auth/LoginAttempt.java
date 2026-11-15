package com.advertisingmarketplace.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "login_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginAttempt {

    @Id
    @Column(name = "attempt_key", length = 400)
    private String key;

    @Column(name = "failures", nullable = false)
    private int failures;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;
}
