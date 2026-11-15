package com.advertisingmarketplace.messaging.safety;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record UserBlockId(
        @Column(name = "blocker_id") UUID blockerId,
        @Column(name = "blocked_id") UUID blockedId) implements Serializable {
}
