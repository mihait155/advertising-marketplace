package com.advertisingmarketplace.offer;

import com.advertisingmarketplace.offer.dto.OfferRequest;
import com.advertisingmarketplace.offer.dto.OfferResponse;
import com.advertisingmarketplace.offer.dto.OfferStatusRequest;
import com.advertisingmarketplace.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offerService;

    @PostMapping("/postings/{postingId}/offers")
    public ResponseEntity<OfferResponse> create(@PathVariable UUID postingId, @Valid @RequestBody OfferRequest request,
            Authentication authentication) {
        OfferResponse offer = offerService.create(postingId, CurrentUser.id(authentication), request);
        return ResponseEntity.created(URI.create("/api/v1/offers/" + offer.id())).body(offer);
    }

    @GetMapping("/postings/{postingId}/offers")
    public PagedModel<OfferResponse> listForPosting(
            @PathVariable UUID postingId,
            @RequestParam(required = false) OfferStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return new PagedModel<>(offerService.listForPosting(postingId, status, CurrentUser.id(authentication),
                CurrentUser.isAdmin(authentication), pageable));
    }

    @GetMapping("/offers/mine")
    public PagedModel<OfferResponse> listMine(
            @RequestParam(required = false) OfferStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return new PagedModel<>(offerService.listMine(CurrentUser.id(authentication), status, pageable));
    }

    @GetMapping("/offers/{id}")
    public OfferResponse get(@PathVariable UUID id, Authentication authentication) {
        return offerService.get(id, CurrentUser.id(authentication), CurrentUser.isAdmin(authentication));
    }

    @PutMapping("/offers/{id}")
    public OfferResponse update(@PathVariable UUID id, @Valid @RequestBody OfferRequest request,
            Authentication authentication) {
        return offerService.update(id, CurrentUser.id(authentication), CurrentUser.isAdmin(authentication), request);
    }

    @PatchMapping("/offers/{id}/status")
    public OfferResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody OfferStatusRequest request,
            Authentication authentication) {
        return offerService.changeStatus(id, CurrentUser.id(authentication), CurrentUser.isAdmin(authentication),
                request.status(), request.amount());
    }
}
