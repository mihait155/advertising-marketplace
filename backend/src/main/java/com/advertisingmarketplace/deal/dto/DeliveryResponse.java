package com.advertisingmarketplace.deal.dto;

import com.advertisingmarketplace.deal.Delivery;
import com.advertisingmarketplace.deal.DeliveryStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeliveryResponse(UUID id, List<String> links, String note, DeliveryStatus status, String feedback,
        Instant submittedAt, Instant reviewedAt) {

    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(delivery.getId(), delivery.linkList(), delivery.getNote(), delivery.getStatus(),
                delivery.getFeedback(), delivery.getSubmittedAt(), delivery.getReviewedAt());
    }
}
