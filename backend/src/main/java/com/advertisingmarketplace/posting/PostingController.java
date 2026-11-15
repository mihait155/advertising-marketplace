package com.advertisingmarketplace.posting;

import com.advertisingmarketplace.common.money.SupportedCurrency;
import com.advertisingmarketplace.posting.dto.CreatePostingRequest;
import com.advertisingmarketplace.posting.dto.PostingResponse;
import com.advertisingmarketplace.posting.dto.PostingStatusRequest;
import com.advertisingmarketplace.posting.dto.UpdatePostingRequest;
import com.advertisingmarketplace.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/postings")
@RequiredArgsConstructor
public class PostingController {

    private final PostingService postingService;

    @PostMapping
    public ResponseEntity<PostingResponse> create(@Valid @RequestBody CreatePostingRequest request,
            Authentication authentication) {
        PostingResponse posting = postingService.create(CurrentUser.id(authentication), request);
        return ResponseEntity.created(URI.create("/api/v1/postings/" + posting.id())).body(posting);
    }

    @GetMapping("/{id}")
    public PostingResponse get(@PathVariable UUID id, Authentication authentication) {
        return postingService.get(id, CurrentUser.id(authentication), CurrentUser.isAdmin(authentication));
    }

    @GetMapping
    public PagedModel<PostingResponse> list(
            @RequestParam(required = false) PostingCategory category,
            @RequestParam(required = false) PostingStatus status,
            @RequestParam(required = false) UUID creatorId,
            @RequestParam(required = false) @PositiveOrZero BigDecimal minPay,
            @RequestParam(required = false) @PositiveOrZero BigDecimal maxPay,
            @RequestParam(required = false) @SupportedCurrency String currency,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        PostingFilter filter = new PostingFilter(category, status, creatorId, minPay, maxPay, currency);
        return new PagedModel<>(postingService.list(filter, CurrentUser.id(authentication),
                CurrentUser.isAdmin(authentication), pageable));
    }

    @PutMapping("/{id}")
    public PostingResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePostingRequest request,
            Authentication authentication) {
        return postingService.update(id, CurrentUser.id(authentication), CurrentUser.isAdmin(authentication), request);
    }

    @PatchMapping("/{id}/status")
    public PostingResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody PostingStatusRequest request,
            Authentication authentication) {
        return postingService.changeStatus(id, CurrentUser.id(authentication), CurrentUser.isAdmin(authentication),
                request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        postingService.delete(id, CurrentUser.id(authentication), CurrentUser.isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }
}
