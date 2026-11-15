package com.advertisingmarketplace.messaging.realtime;

import com.advertisingmarketplace.security.CurrentUser;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class RealtimeController {

    private final SseHub sseHub;
    private final StreamTicketService ticketService;

    @PostMapping("/stream-tickets")
    public ResponseEntity<StreamTicketService.StreamTicket> ticket(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ticketService.issue(CurrentUser.id(authentication)));
    }

    @GetMapping("/events")
    public ResponseEntity<SseEmitter> events(@RequestParam(required = false) String ticket,
            Authentication authentication) {
        Optional<UUID> userId = authentication instanceof JwtAuthenticationToken
                ? Optional.of(CurrentUser.id(authentication))
                : ticketService.validate(ticket);
        if (userId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .body(sseHub.register(userId.get()));
    }
}
