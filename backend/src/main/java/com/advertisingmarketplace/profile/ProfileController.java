package com.advertisingmarketplace.profile;

import com.advertisingmarketplace.deal.DealRepository;
import com.advertisingmarketplace.deal.DealStatus;
import com.advertisingmarketplace.review.ReviewService;
import com.advertisingmarketplace.review.dto.ReviewResponse;
import com.advertisingmarketplace.social.SocialAccountService;
import com.advertisingmarketplace.social.dto.SocialAccountResponse;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserNotFoundException;
import com.advertisingmarketplace.user.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/{id}")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final ReviewService reviewService;
    private final SocialAccountService socialAccountService;
    private final DealRepository dealRepository;

    @GetMapping("/profile")
    @Transactional(readOnly = true)
    public ProfileResponse profile(@PathVariable UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        ReviewService.RatingSummary rating = reviewService.summaryFor(id);
        List<SocialAccountResponse> accounts = socialAccountService.accountsOf(id);
        List<ReviewResponse> reviews = reviewService.visibleFor(id,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        return new ProfileResponse(user.getId(), user.getFullName(), user.getUserType(), user.getCreatedAt(),
                rating.average(), rating.count(), dealRepository.countForParticipant(id, DealStatus.COMPLETED),
                accounts.stream().mapToLong(SocialAccountResponse::followers).sum(), accounts, reviews);
    }

    @GetMapping("/reviews")
    public PagedModel<ReviewResponse> reviews(@PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(reviewService.visibleFor(id, pageable));
    }
}
