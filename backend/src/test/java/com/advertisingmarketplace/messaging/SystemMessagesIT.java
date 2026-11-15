package com.advertisingmarketplace.messaging;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SystemMessagesIT extends MessagingTestSupport {

    @Test
    void offerLifecycleShowsUpInTheConversation() throws Exception {
        String offerBody = mockMvc.perform(bearer(post("/api/v1/postings/{id}/offers", postingId), accessToken(streamer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"3 streams\",\"priceFrom\":800,\"priceTo\":1200}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String offer = JsonPath.read(offerBody, "$.id");

        String list = mockMvc.perform(bearer(get("/api/v1/conversations"), accessToken(brand)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].unreadCount").value(1))
                .andExpect(jsonPath("$.content[0].lastMessage.type").value("SYSTEM"))
                .andReturn().getResponse().getContentAsString();
        String conversation = JsonPath.read(list, "$.content[0].id");

        sentId(send(accessToken(brand), conversation, "Can you do 1000 flat?"));
        mockMvc.perform(bearer(patch("/api/v1/offers/{id}/status", offer), accessToken(brand))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\",\"amount\":1000}"))
                .andExpect(status().isOk());
        mockMvc.perform(bearer(patch("/api/v1/postings/{id}/status", postingId), accessToken(brand))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk());

        messages(accessToken(streamer), conversation)
                .andExpect(jsonPath("$.messages", hasSize(4)))
                .andExpect(jsonPath("$.messages[0].systemEvent").value("POSTING_CLOSED"))
                .andExpect(jsonPath("$.messages[0].text").value("Acme Brand closed the posting"))
                .andExpect(jsonPath("$.messages[1].systemEvent").value("OFFER_ACCEPTED"))
                .andExpect(jsonPath("$.messages[1].offerId").value(offer))
                .andExpect(jsonPath("$.messages[1].text").value("Acme Brand accepted the offer: 800 – 1,200 EUR"))
                .andExpect(jsonPath("$.messages[2].type").value("USER"))
                .andExpect(jsonPath("$.messages[3].systemEvent").value("OFFER_MADE"))
                .andExpect(jsonPath("$.messages[3].text").value("Jane Streamer made an offer: 800 – 1,200 EUR"))
                .andExpect(jsonPath("$.messages[3].sender.fullName").value("Jane Streamer"));

        sentId(send(accessToken(streamer), conversation, "Thanks! Talk soon."));
    }
}
