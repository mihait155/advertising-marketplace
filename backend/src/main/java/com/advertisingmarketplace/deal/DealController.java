package com.advertisingmarketplace.deal;

import com.advertisingmarketplace.deal.dto.CancelRequest;
import com.advertisingmarketplace.deal.dto.DealResponse;
import com.advertisingmarketplace.deal.dto.DeliveryRequest;
import com.advertisingmarketplace.deal.dto.FeedbackRequest;
import com.advertisingmarketplace.review.ReviewService;
import com.advertisingmarketplace.review.dto.ReviewRequest;
import com.advertisingmarketplace.review.dto.ReviewResponse;
import com.advertisingmarketplace.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deals")
@RequiredArgsConstructor
public class DealController {

    private final DealService dealService;
    private final ReviewService reviewService;

    @GetMapping
    public PagedModel<DealResponse> list(@RequestParam(required = false) DealStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return new PagedModel<>(dealService.list(CurrentUser.id(authentication), status, pageable));
    }

    @GetMapping("/{id}")
    public DealResponse get(@PathVariable UUID id, Authentication authentication) {
        return dealService.get(id, CurrentUser.id(authentication), CurrentUser.isAdmin(authentication));
    }

    @PostMapping("/{id}/deliveries")
    public DealResponse deliver(@PathVariable UUID id, @Valid @RequestBody DeliveryRequest request,
            Authentication authentication) {
        return dealService.deliver(id, CurrentUser.id(authentication), request);
    }

    @PostMapping("/{id}/approve")
    public DealResponse approve(@PathVariable UUID id, Authentication authentication) {
        return dealService.approve(id, CurrentUser.id(authentication));
    }

    @PostMapping("/{id}/request-changes")
    public DealResponse requestChanges(@PathVariable UUID id, @Valid @RequestBody FeedbackRequest request,
            Authentication authentication) {
        return dealService.requestChanges(id, CurrentUser.id(authentication), request.feedback());
    }

    @PostMapping("/{id}/cancel")
    public DealResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest request,
            Authentication authentication) {
        return dealService.cancel(id, CurrentUser.id(authentication), CurrentUser.isAdmin(authentication),
                request.reason());
    }

    @PostMapping("/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request,
            Authentication authentication) {
        return reviewService.review(id, CurrentUser.id(authentication), request);
    }
}
