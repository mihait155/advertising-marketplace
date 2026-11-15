package com.advertisingmarketplace.deal;

import java.util.EnumSet;
import java.util.Set;

public enum DealStatus {

    IN_PROGRESS,
    DELIVERED,
    COMPLETED,
    CANCELLED;

    public static final Set<DealStatus> ACTIVE = EnumSet.of(IN_PROGRESS, DELIVERED);
}
