package com.advertisingmarketplace.messaging;

import com.advertisingmarketplace.messaging.dto.ConversationResponse;
import com.advertisingmarketplace.messaging.dto.EditMessageRequest;
import com.advertisingmarketplace.messaging.dto.MessagePage;
import com.advertisingmarketplace.messaging.dto.MessageResponse;
import com.advertisingmarketplace.messaging.dto.SendMessageRequest;
import com.advertisingmarketplace.messaging.dto.StartConversationRequest;
import com.advertisingmarketplace.messaging.dto.UnreadCountResponse;
import com.advertisingmarketplace.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MessagingController {

    private final MessagingService messagingService;

    @PostMapping("/postings/{postingId}/conversations")
    public ResponseEntity<ConversationResponse> start(@PathVariable UUID postingId,
            @Valid @RequestBody(required = false) StartConversationRequest request, Authentication authentication) {
        MessagingService.StartResult result = messagingService.start(postingId, CurrentUser.id(authentication),
                request == null ? null : request.text());
        URI location = URI.create("/api/v1/conversations/" + result.conversation().id());
        return result.created()
                ? ResponseEntity.created(location).body(result.conversation())
                : ResponseEntity.ok().location(location).body(result.conversation());
    }

    @GetMapping("/conversations")
    public PagedModel<ConversationResponse> list(@RequestParam(required = false) UUID postingId,
            @PageableDefault(size = 20) Pageable pageable, Authentication authentication) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), Sort.by(Sort.Order.desc("lastMessageAt"), Sort.Order.desc("id")));
        return new PagedModel<>(messagingService.list(CurrentUser.id(authentication), postingId, sorted));
    }

    @GetMapping("/conversations/unread-count")
    public UnreadCountResponse unreadCount(Authentication authentication) {
        return messagingService.unreadCount(CurrentUser.id(authentication));
    }

    @GetMapping("/conversations/{id}")
    public ConversationResponse get(@PathVariable UUID id, Authentication authentication) {
        return messagingService.get(id, CurrentUser.id(authentication));
    }

    @GetMapping("/conversations/{id}/messages")
    public MessagePage messages(@PathVariable UUID id, @RequestParam(required = false) UUID before,
            @RequestParam(defaultValue = "50") int limit, Authentication authentication) {
        return messagingService.messages(id, CurrentUser.id(authentication), before, limit);
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<MessageResponse> send(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {
        MessageResponse message = messagingService.send(id, CurrentUser.id(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    @PostMapping("/conversations/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID id, Authentication authentication) {
        messagingService.markRead(id, CurrentUser.id(authentication));
    }

    @PutMapping("/messages/{id}")
    public MessageResponse edit(@PathVariable UUID id, @Valid @RequestBody EditMessageRequest request,
            Authentication authentication) {
        return messagingService.edit(id, CurrentUser.id(authentication), request.text());
    }

    @DeleteMapping("/messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication authentication) {
        messagingService.delete(id, CurrentUser.id(authentication));
    }
}
