package com.advertisingmarketplace.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.advertisingmarketplace.messaging.notification.MessageNotificationJob;
import com.advertisingmarketplace.support.Mailpit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class NotificationIT extends MessagingTestSupport {

    @Autowired
    private MessageNotificationJob job;

    @Autowired
    private Mailpit mailpit;

    @BeforeEach
    void clearMailbox() throws Exception {
        mailpit.clear();
    }

    @Test
    void unreadMessagesAreEmailedOnce() throws Exception {
        String conversation = conversationId(accessToken(streamer), postingId);
        sentId(send(accessToken(streamer), conversation, "Hi, I'd love to promote your game"));
        sentId(send(accessToken(streamer), conversation, "I have 50k followers"));

        assertThat(job.run()).isEqualTo(1);

        List<Mailpit.Email> emails = mailpit.emails();
        assertThat(emails).hasSize(1);
        Mailpit.Email email = emails.getFirst();
        assertThat(email.to()).containsExactly("brand@example.com");
        assertThat(email.subject()).isEqualTo("2 new messages from Jane Streamer about \"Need gaming streamers\"");
        assertThat(email.text())
                .contains("Hi Acme Brand,")
                .contains("- Jane Streamer: Hi, I'd love to promote your game")
                .contains("- Jane Streamer: I have 50k followers")
                .contains("https://app.marketplace.test/conversations/" + conversation);

        assertThat(job.run()).isZero();
        assertThat(mailpit.emails()).hasSize(1);

        sentId(send(accessToken(streamer), conversation, "Any news?"));
        assertThat(job.run()).isEqualTo(1);
        assertThat(mailpit.emails()).hasSize(2);
    }

    @Test
    void readMessagesAndOwnMessagesAreNotEmailed() throws Exception {
        String conversation = conversationId(accessToken(streamer), postingId);
        sentId(send(accessToken(streamer), conversation, "Hello"));
        mockMvc.perform(bearer(post("/api/v1/conversations/{id}/read", conversation), accessToken(brand)));

        assertThat(job.run()).isZero();
        assertThat(mailpit.emails()).isEmpty();
    }

    @Test
    void usersCanOptOut() throws Exception {
        mockMvc.perform(bearer(put("/api/v1/users/me/notification-settings"), accessToken(brand))
                .contentType(MediaType.APPLICATION_JSON).content("{\"emailMessageNotifications\":false}"));
        String conversation = conversationId(accessToken(streamer), postingId);
        sentId(send(accessToken(streamer), conversation, "Hello"));

        assertThat(job.run()).isZero();
        assertThat(mailpit.emails()).isEmpty();

        mockMvc.perform(bearer(put("/api/v1/users/me/notification-settings"), accessToken(brand))
                .contentType(MediaType.APPLICATION_JSON).content("{\"emailMessageNotifications\":true}"));
        assertThat(job.run()).isZero();
    }

    @Test
    void systemMessagesAreIncluded() throws Exception {
        mockMvc.perform(bearer(post("/api/v1/postings/{id}/offers", postingId), accessToken(streamer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"3 streams\",\"priceFrom\":800,\"priceTo\":1200}"));

        assertThat(job.run()).isEqualTo(1);

        assertThat(mailpit.emails().getFirst().text())
                .contains("- Jane Streamer: Jane Streamer made an offer: 800 – 1,200 EUR");
    }
}
