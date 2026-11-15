package com.advertisingmarketplace.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.support.TestFiles;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class MessagingApiIT extends MessagingTestSupport {

    @Test
    void streamerStartsConversationAndBothSidesChat() throws Exception {
        String body = start(accessToken(streamer), postingId, "Hi! I stream 4 nights a week.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.posting.id").value(postingId))
                .andExpect(jsonPath("$.postingCreator.fullName").value("Acme Brand"))
                .andExpect(jsonPath("$.counterpart.fullName").value("Jane Streamer"))
                .andExpect(jsonPath("$.otherParticipant.fullName").value("Acme Brand"))
                .andExpect(jsonPath("$.lastMessage.text").value("Hi! I stream 4 nights a week."))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andReturn().getResponse().getContentAsString();
        String conversation = JsonPath.read(body, "$.id");

        start(accessToken(streamer), postingId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversation));

        mockMvc.perform(bearer(get("/api/v1/conversations/unread-count"), accessToken(brand)))
                .andExpect(jsonPath("$.unreadMessages").value(1))
                .andExpect(jsonPath("$.conversationsWithUnread").value(1));
        mockMvc.perform(bearer(get("/api/v1/conversations"), accessToken(brand)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].unreadCount").value(1))
                .andExpect(jsonPath("$.content[0].otherParticipant.fullName").value("Jane Streamer"));

        sentId(send(accessToken(brand), conversation, "Great, what are your rates?"));
        mockMvc.perform(bearer(post("/api/v1/conversations/{id}/read", conversation), accessToken(streamer)))
                .andExpect(status().isNoContent());

        mockMvc.perform(bearer(get("/api/v1/conversations/{id}", conversation), accessToken(brand)))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.otherParticipantLastReadAt").isNotEmpty());
        messages(accessToken(streamer), conversation)
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].text").value("Great, what are your rates?"))
                .andExpect(jsonPath("$.messages[0].sender.fullName").value("Acme Brand"))
                .andExpect(jsonPath("$.messages[1].text").value("Hi! I stream 4 nights a week."))
                .andExpect(jsonPath("$.nextBefore").doesNotExist());
    }

    @Test
    void historyIsPagedWithACursor() throws Exception {
        String conversation = conversationId(accessToken(streamer), postingId);
        for (int i = 1; i <= 5; i++) {
            sentId(send(accessToken(i % 2 == 0 ? brand : streamer), conversation, "message " + i));
        }

        String first = mockMvc.perform(bearer(get("/api/v1/conversations/{id}/messages", conversation),
                        accessToken(brand)).param("limit", "2"))
                .andExpect(jsonPath("$.messages[*].text").value(org.hamcrest.Matchers.contains("message 5", "message 4")))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.nextBefore");
        String second = mockMvc.perform(bearer(get("/api/v1/conversations/{id}/messages", conversation),
                        accessToken(brand)).param("limit", "2").param("before", cursor))
                .andExpect(jsonPath("$.messages[*].text").value(org.hamcrest.Matchers.contains("message 3", "message 2")))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(bearer(get("/api/v1/conversations/{id}/messages", conversation), accessToken(brand))
                        .param("limit", "2").param("before", (String) JsonPath.read(second, "$.nextBefore")))
                .andExpect(jsonPath("$.messages[*].text").value(org.hamcrest.Matchers.contains("message 1")))
                .andExpect(jsonPath("$.nextBefore").doesNotExist());
    }

    @Test
    void conversationRules() throws Exception {
        start(accessToken(brand), postingId, null).andExpect(status().isForbidden());
        String otherBrand = signup("Other Brand", "other@example.com", "CUSTOMER");
        start(accessToken(otherBrand), postingId, null).andExpect(status().isForbidden());
        start(adminAccessToken(), postingId, null).andExpect(status().isForbidden());

        String draft = createPosting(accessToken(brand), "CUSTOMER_POSTING", "DRAFT");
        start(accessToken(streamer), draft, null).andExpect(status().isNotFound());

        String conversation = conversationId(accessToken(streamer), postingId);
        String outsider = signup("Bob Streamer", "bob@example.com", "MARKETER");
        mockMvc.perform(bearer(get("/api/v1/conversations/{id}", conversation), accessToken(outsider)))
                .andExpect(status().isNotFound());
        send(accessToken(outsider), conversation, "hi").andExpect(status().isNotFound());
        messages(accessToken(outsider), conversation).andExpect(status().isNotFound());
        send(accessToken(streamer), conversation, "").andExpect(status().isBadRequest());
    }

    @Test
    void messagesCanBeEditedAndDeletedBySender() throws Exception {
        String conversation = conversationId(accessToken(streamer), postingId);
        String message = sentId(send(accessToken(streamer), conversation, "500 EUR"));

        mockMvc.perform(bearer(put("/api/v1/messages/{id}", message), accessToken(brand))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"5 EUR\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(bearer(put("/api/v1/messages/{id}", message), accessToken(streamer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"600 EUR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("600 EUR"))
                .andExpect(jsonPath("$.editedAt").isNotEmpty());

        mockMvc.perform(bearer(delete("/api/v1/messages/{id}", message), accessToken(streamer)))
                .andExpect(status().isNoContent());
        messages(accessToken(brand), conversation)
                .andExpect(jsonPath("$.messages[0].deleted").value(true))
                .andExpect(jsonPath("$.messages[0].text").doesNotExist());
    }

    @Test
    void imagesCanBeSharedAndAreOnlyVisibleToParticipants() throws Exception {
        String conversation = conversationId(accessToken(streamer), postingId);
        String image = uploadImage(accessToken(streamer), TestFiles.png());

        mockMvc.perform(bearer(post("/api/v1/conversations/{id}/messages", conversation), accessToken(streamer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"My stats\",\"imageIds\":[\"%s\"]}".formatted(image)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images[0].id").value(image))
                .andExpect(jsonPath("$.images[0].url").isNotEmpty());

        mockMvc.perform(bearer(get("/api/v1/images/{id}", image), accessToken(brand))).andExpect(status().isOk());
        String outsider = signup("Bob", "bob@example.com", "MARKETER");
        mockMvc.perform(bearer(get("/api/v1/images/{id}", image), accessToken(outsider)))
                .andExpect(status().isNotFound());

        mockMvc.perform(bearer(post("/api/v1/conversations/{id}/messages", conversation), accessToken(streamer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[\"%s\"]}".formatted(image)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blockingStopsMessagesAndOffersUntilUnblocked() throws Exception {
        String conversation = conversationId(accessToken(streamer), postingId);

        mockMvc.perform(bearer(put("/api/v1/blocks/{id}", userId(streamer)), accessToken(brand)))
                .andExpect(status().isNoContent());
        mockMvc.perform(bearer(get("/api/v1/blocks"), accessToken(brand)))
                .andExpect(jsonPath("$[0].user.fullName").value("Jane Streamer"));

        send(accessToken(streamer), conversation, "hello?").andExpect(status().isForbidden());
        send(accessToken(brand), conversation, "bye").andExpect(status().isForbidden());
        mockMvc.perform(bearer(post("/api/v1/postings/{id}/offers", postingId), accessToken(streamer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"x\",\"priceFrom\":1,\"priceTo\":2}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(bearer(delete("/api/v1/blocks/{id}", userId(streamer)), accessToken(brand)))
                .andExpect(status().isNoContent());
        sentId(send(accessToken(streamer), conversation, "hello again"));
        mockMvc.perform(bearer(put("/api/v1/blocks/{id}", userId(brand)), accessToken(brand)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletedUsersAppearAsDeletedAndCantBeMessaged() throws Exception {
        String conversation = conversationId(accessToken(streamer), postingId);
        sentId(send(accessToken(streamer), conversation, "my last message"));

        mockMvc.perform(bearer(delete("/api/v1/users/{id}", userId(streamer)), accessToken(streamer)))
                .andExpect(status().isNoContent());

        mockMvc.perform(bearer(get("/api/v1/conversations/{id}", conversation), accessToken(brand)))
                .andExpect(jsonPath("$.otherParticipant.fullName").value("Deleted user"))
                .andExpect(jsonPath("$.otherParticipant.id").doesNotExist());
        messages(accessToken(brand), conversation)
                .andExpect(jsonPath("$.messages[0].text").value("my last message"))
                .andExpect(jsonPath("$.messages[0].sender.fullName").value("Deleted user"));
        send(accessToken(brand), conversation, "are you there?").andExpect(status().isConflict());
    }

    @Test
    void sendingIsRateLimited() throws Exception {
        String conversation = conversationId(accessToken(streamer), postingId);
        for (int i = 0; i < 20; i++) {
            sentId(send(accessToken(streamer), conversation, "spam " + i));
        }

        send(accessToken(streamer), conversation, "one too many")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
        sentId(send(accessToken(brand), conversation, "the other side is unaffected"));
    }

    @Test
    void contactDetailsAreNotMaskedByDefault() throws Exception {
        String conversation = conversationId(accessToken(streamer), postingId);

        send(accessToken(streamer), conversation, "jane@example.com")
                .andExpect(jsonPath("$.text").value("jane@example.com"));
    }

    @Test
    void notificationSettings() throws Exception {
        mockMvc.perform(bearer(get("/api/v1/users/me/notification-settings"), accessToken(brand)))
                .andExpect(jsonPath("$.emailMessageNotifications").value(true));
        mockMvc.perform(bearer(put("/api/v1/users/me/notification-settings"), accessToken(brand))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"emailMessageNotifications\":false}"))
                .andExpect(jsonPath("$.emailMessageNotifications").value(false));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT email_message_notifications FROM users WHERE email = 'brand@example.com'", Boolean.class))
                .isFalse();
    }

    @Test
    void conversationsCanBeFilteredByPosting() throws Exception {
        String otherPosting = createPosting(accessToken(brand), "CUSTOMER_POSTING", "OPEN");
        conversationId(accessToken(streamer), postingId);
        conversationId(accessToken(streamer), otherPosting);

        mockMvc.perform(bearer(get("/api/v1/conversations"), accessToken(streamer)))
                .andExpect(jsonPath("$.content", hasSize(2)));
        mockMvc.perform(bearer(get("/api/v1/conversations"), accessToken(streamer)).param("postingId", otherPosting))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].posting.id").value(otherPosting));
        assertThat(List.of(postingId, otherPosting)).doesNotHaveDuplicates();
    }
}
