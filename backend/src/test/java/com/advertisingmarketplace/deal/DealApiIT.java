package com.advertisingmarketplace.deal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class DealApiIT extends AbstractIntegrationTest {

    @Autowired
    private DealService dealService;

    private String brand;
    private String streamer;
    private String postingId;
    private String offerId;

    @BeforeEach
    void setUp() throws Exception {
        brand = signup("Acme Brand", "brand@example.com", "CUSTOMER");
        streamer = signup("Jane Streamer", "jane@example.com", "MARKETER");
        String posting = mockMvc.perform(bearer(post("/api/v1/postings"), accessToken(brand))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"CUSTOMER_POSTING","status":"OPEN","title":"Launch streams","text":"x",
                                 "payFrom":300,"payTo":800,"payCurrency":"EUR"}
                                """))
                .andReturn().getResponse().getContentAsString();
        postingId = JsonPath.read(posting, "$.id");
        String offer = mockMvc.perform(bearer(post("/api/v1/postings/{id}/offers", postingId), accessToken(streamer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Two streams\",\"priceFrom\":400,\"priceTo\":600}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        offerId = JsonPath.read(offer, "$.id");
    }

    private ResultActions accept(String amount) throws Exception {
        return mockMvc.perform(bearer(patch("/api/v1/offers/{id}/status", offerId), accessToken(brand))
                .contentType(MediaType.APPLICATION_JSON)
                .content(amount == null ? "{\"status\":\"ACCEPTED\"}"
                        : "{\"status\":\"ACCEPTED\",\"amount\":" + amount + "}"));
    }

    private String startDeal() throws Exception {
        String offer = accept("500").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(offer, "$.dealId");
    }

    private ResultActions deliver(String deal, String links) throws Exception {
        return mockMvc.perform(bearer(post("/api/v1/deals/{id}/deliveries", deal), accessToken(streamer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"links\":[" + links + "],\"note\":\"Both streams are up\"}"));
    }

    private ResultActions review(String token, String deal, int rating, String text) throws Exception {
        return mockMvc.perform(bearer(post("/api/v1/deals/{id}/reviews", deal), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":%d,\"text\":\"%s\"}".formatted(rating, text)));
    }

    private List<String> conversationEvents() throws Exception {
        String conversations = mockMvc.perform(bearer(get("/api/v1/conversations"), accessToken(brand)))
                .andReturn().getResponse().getContentAsString();
        String conversation = JsonPath.read(conversations, "$.content[0].id");
        String messages = mockMvc.perform(bearer(get("/api/v1/conversations/{id}/messages", conversation),
                accessToken(brand))).andReturn().getResponse().getContentAsString();
        return JsonPath.read(messages, "$.messages[*].systemEvent");
    }

    @Test
    void acceptingNeedsAnAmountInsideTheOfferedRange() throws Exception {
        accept(null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("amount is required when accepting a price range"));
        accept("700").andExpect(status().isBadRequest());
        accept("500.555").andExpect(status().isBadRequest());

        String deal = startDeal();

        mockMvc.perform(bearer(get("/api/v1/deals/{id}", deal), accessToken(brand)))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.amount").value(500.0))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.myRole").value("CUSTOMER"))
                .andExpect(jsonPath("$.marketer.fullName").value("Jane Streamer"))
                .andExpect(jsonPath("$.customer.fullName").value("Acme Brand"))
                .andExpect(jsonPath("$.conversationId").isNotEmpty());
        mockMvc.perform(bearer(get("/api/v1/deals"), accessToken(streamer)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].myRole").value("MARKETER"));
        String outsider = accessToken(signup("Bob", "bob@example.com", "MARKETER"));
        mockMvc.perform(bearer(get("/api/v1/deals/{id}", deal), outsider)).andExpect(status().isNotFound());
    }

    @Test
    void deliveryRevisionAndApproval() throws Exception {
        String deal = startDeal();

        deliver(deal, "\"javascript:alert(1)\"").andExpect(status().isBadRequest());
        mockMvc.perform(bearer(post("/api/v1/deals/{id}/deliveries", deal), accessToken(brand))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"links\":[\"https://twitch.tv/v/1\"]}"))
                .andExpect(status().isForbidden());
        deliver(deal, "\"https://twitch.tv/videos/1\",\"https://twitch.tv/videos/2\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.autoCompleteAt").isNotEmpty())
                .andExpect(jsonPath("$.deliveries[0].links", hasSize(2)));

        mockMvc.perform(bearer(post("/api/v1/deals/{id}/request-changes", deal), accessToken(brand))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"feedback\":\"Please pin the link\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.deliveries[0].status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.deliveries[0].feedback").value("Please pin the link"));
        mockMvc.perform(bearer(post("/api/v1/deals/{id}/approve", deal), accessToken(brand)))
                .andExpect(status().isConflict());

        deliver(deal, "\"https://twitch.tv/videos/3\"").andExpect(status().isOk());
        mockMvc.perform(bearer(post("/api/v1/deals/{id}/approve", deal), accessToken(streamer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(bearer(post("/api/v1/deals/{id}/approve", deal), accessToken(brand)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.deliveries", hasSize(2)))
                .andExpect(jsonPath("$.deliveries[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.reviews.canReview").value(true));

        assertThat(conversationEvents()).containsSubsequence("DEAL_COMPLETED", "DELIVERY_SUBMITTED",
                "CHANGES_REQUESTED", "DELIVERY_SUBMITTED", "OFFER_ACCEPTED", "OFFER_MADE");
    }

    @Test
    void deliveriesAreApprovedAutomaticallyAfterTheDeadline() throws Exception {
        String deal = startDeal();
        deliver(deal, "\"https://youtu.be/abc\"").andExpect(status().isOk());
        jdbcTemplate.update("UPDATE deals SET auto_complete_at = now() - interval '1 minute' WHERE id = ?::uuid", deal);

        assertThat(dealService.autoCompleteDueDeals()).isEqualTo(1);

        mockMvc.perform(bearer(get("/api/v1/deals/{id}", deal), accessToken(streamer)))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.deliveries[0].status").value("APPROVED"));
        assertThat(conversationEvents().getFirst()).isEqualTo("DEAL_COMPLETED");
    }

    @Test
    void cancellationAndDeletionGuards() throws Exception {
        String deal = startDeal();

        mockMvc.perform(bearer(delete("/api/v1/postings/{id}", postingId), accessToken(brand)))
                .andExpect(status().isConflict());
        mockMvc.perform(bearer(delete("/api/v1/users/{id}", userId(streamer)), accessToken(streamer)))
                .andExpect(status().isConflict());

        mockMvc.perform(bearer(post("/api/v1/deals/{id}/cancel", deal), accessToken(streamer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Scheduling conflict\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Scheduling conflict"))
                .andExpect(jsonPath("$.cancelledBy.fullName").value("Jane Streamer"));
        deliver(deal, "\"https://youtu.be/abc\"").andExpect(status().isConflict());

        mockMvc.perform(bearer(delete("/api/v1/postings/{id}", postingId), accessToken(brand)))
                .andExpect(status().isNoContent());
        mockMvc.perform(bearer(get("/api/v1/deals/{id}", deal), accessToken(brand)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postingTitle").value("Launch streams"))
                .andExpect(jsonPath("$.postingId").doesNotExist());
    }

    @Test
    void reviewsAreDoubleBlindAndFeedTheProfile() throws Exception {
        String deal = startDeal();
        review(accessToken(brand), deal, 5, "Too early").andExpect(status().isConflict());
        deliver(deal, "\"https://twitch.tv/videos/1\"").andExpect(status().isOk());
        mockMvc.perform(bearer(post("/api/v1/deals/{id}/approve", deal), accessToken(brand)))
                .andExpect(status().isOk());

        review(accessToken(streamer), deal, 4, "Clear brief, quick approval").andExpect(status().isCreated());
        review(accessToken(streamer), deal, 5, "again").andExpect(status().isConflict());
        review(accessToken(streamer), deal, 6, "out of range").andExpect(status().isBadRequest());

        mockMvc.perform(bearer(get("/api/v1/users/{id}/profile", userId(brand)), accessToken(streamer)))
                .andExpect(jsonPath("$.ratingCount").value(0))
                .andExpect(jsonPath("$.recentReviews", hasSize(0)))
                .andExpect(jsonPath("$.completedDeals").value(1));
        mockMvc.perform(bearer(get("/api/v1/deals/{id}", deal), accessToken(brand)))
                .andExpect(jsonPath("$.reviews.theirsSubmitted").value(true))
                .andExpect(jsonPath("$.reviews.theirs").doesNotExist())
                .andExpect(jsonPath("$.reviews.canReview").value(true));

        review(accessToken(brand), deal, 5, "Great streams, on time").andExpect(status().isCreated());

        mockMvc.perform(bearer(get("/api/v1/users/{id}/profile", userId(brand)), accessToken(streamer)))
                .andExpect(jsonPath("$.ratingAverage").value(4.0))
                .andExpect(jsonPath("$.ratingCount").value(1))
                .andExpect(jsonPath("$.recentReviews[0].text").value("Clear brief, quick approval"))
                .andExpect(jsonPath("$.recentReviews[0].reviewer.fullName").value("Jane Streamer"));
        mockMvc.perform(bearer(get("/api/v1/users/{id}/profile", userId(streamer)), accessToken(brand)))
                .andExpect(jsonPath("$.ratingAverage").value(5.0))
                .andExpect(jsonPath("$.userType").value("MARKETER"));
        mockMvc.perform(bearer(get("/api/v1/deals/{id}", deal), accessToken(brand)))
                .andExpect(jsonPath("$.reviews.theirs.rating").value(4))
                .andExpect(jsonPath("$.reviews.mine.rating").value(5))
                .andExpect(jsonPath("$.reviews.canReview").value(false));
    }

    @Test
    void reviewsBecomeVisibleAfterTheBlindPeriodEvenIfOnlyOneSideReviewed() throws Exception {
        String deal = startDeal();
        deliver(deal, "\"https://twitch.tv/videos/1\"").andExpect(status().isOk());
        mockMvc.perform(bearer(post("/api/v1/deals/{id}/approve", deal), accessToken(brand)));
        review(accessToken(brand), deal, 3, "Okay").andExpect(status().isCreated());

        jdbcTemplate.update("UPDATE deals SET completed_at = now() - interval '15 days' WHERE id = ?::uuid", deal);

        mockMvc.perform(bearer(get("/api/v1/users/{id}/reviews", userId(streamer)), accessToken(brand)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].rating").value(3));
    }
}
