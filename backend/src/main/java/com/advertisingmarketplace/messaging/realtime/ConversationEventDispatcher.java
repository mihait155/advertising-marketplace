package com.advertisingmarketplace.messaging.realtime;

import com.advertisingmarketplace.messaging.MessagingService;
import com.advertisingmarketplace.messaging.dto.MessageResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConversationEventDispatcher {

    public static final String USER_QUEUE = "/queue/conversation-events";

    private final SseHub sseHub;
    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;
    private final MessagingService messagingService;

    public void dispatch(ConversationEvent event) {
        List<UUID> localRecipients = event.recipientIds().stream()
                .filter(id -> sseHub.isConnected(id) || userRegistry.getUser(id.toString()) != null)
                .toList();
        if (localRecipients.isEmpty()) {
            return;
        }
        MessageResponse message = event.messageId() == null ? null : messagingService.loadMessage(event.messageId());
        ConversationEventPayload payload = new ConversationEventPayload(event.type(), event.conversationId(), message,
                event.actorId(), event.at());
        for (UUID recipient : localRecipients) {
            sseHub.send(recipient, payload);
            messagingTemplate.convertAndSendToUser(recipient.toString(), USER_QUEUE, payload);
        }
    }
}
