package com.advertisingmarketplace.deal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    List<Delivery> findAllByDealIdOrderBySubmittedAtDesc(UUID dealId);

    Optional<Delivery> findFirstByDealIdOrderBySubmittedAtDesc(UUID dealId);
}
