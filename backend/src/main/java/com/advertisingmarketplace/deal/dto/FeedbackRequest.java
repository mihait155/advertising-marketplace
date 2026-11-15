package com.advertisingmarketplace.deal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(@NotBlank @Size(max = 2_000) String feedback) {
}
