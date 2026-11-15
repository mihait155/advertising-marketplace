package com.advertisingmarketplace.deal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.advertisingmarketplace.common.observability.MarketplaceMetrics;
import com.advertisingmarketplace.messaging.ConversationRepository;
import com.advertisingmarketplace.messaging.MessagingService;
import com.advertisingmarketplace.review.ReviewService;
import com.advertisingmarketplace.support.MutableClock;
import com.advertisingmarketplace.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class DealServiceAutoCompleteTest {

    private final DealRepository dealRepository = mock(DealRepository.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final DealService service = new DealService(dealRepository, mock(DeliveryRepository.class),
            mock(UserRepository.class), mock(ConversationRepository.class), mock(MessagingService.class),
            mock(ReviewService.class), new DealProperties(Duration.ofDays(7), Duration.ofDays(30), Duration.ofDays(14)),
            transactionTemplate, new MutableClock(Instant.parse("2026-01-01T10:00:00Z")), MarketplaceMetrics.noop());

    @Test
    @SuppressWarnings("unchecked")
    void aDealChangedConcurrentlyIsSkippedWithoutAbortingTheBatch() {
        UUID contested = UUID.randomUUID();
        UUID free = UUID.randomUUID();
        when(dealRepository.findDueForAutoCompletion(eq(DealStatus.DELIVERED), any(Instant.class), any(Limit.class)))
                .thenReturn(List.of(contested, free));
        when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Deal.class, contested))
                .thenReturn(true);

        assertThat(service.autoCompleteDueDeals()).isEqualTo(1);
    }
}
