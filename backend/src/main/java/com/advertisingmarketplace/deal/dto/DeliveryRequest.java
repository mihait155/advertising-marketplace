package com.advertisingmarketplace.deal.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DeliveryRequest(
        @NotEmpty @Size(max = 10) List<@Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) link") String> links,
        @Size(max = 2_000) String note) {
}
