package com.advertisingmarketplace.messaging.realtime;

import com.advertisingmarketplace.messaging.MessagingService;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class TypingController {

    private final MessagingService messagingService;

    @MessageMapping("/conversations/{conversationId}/typing")
    public void typing(@DestinationVariable UUID conversationId, Principal principal) {
        messagingService.typing(conversationId, UUID.fromString(principal.getName()));
    }
}
