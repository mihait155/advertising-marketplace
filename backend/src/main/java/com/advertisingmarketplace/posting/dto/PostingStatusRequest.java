package com.advertisingmarketplace.posting.dto;

import com.advertisingmarketplace.posting.PostingStatus;
import jakarta.validation.constraints.NotNull;

public record PostingStatusRequest(@NotNull PostingStatus status) {
}
