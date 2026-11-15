package com.advertisingmarketplace.messaging.realtime;

public interface ConversationEventPublisher {

    void publish(ConversationEvent event);
}
