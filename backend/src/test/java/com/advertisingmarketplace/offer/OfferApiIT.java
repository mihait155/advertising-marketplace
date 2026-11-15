package com.advertisingmarketplace.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class OfferApiIT extends AbstractIntegrationTest {

    private String brand;
    private String streamer;
    private String otherStreamer;
    private String otherBrand;
    private String postingId;

    @BeforeEach
    void setUp() throws Exception {
        brand = signup("Acme Brand", "brand@example.com", "CUSTOMER");
        otherBrand = signup("Other Brand", "other-brand@example.com", "CUSTOMER");
        streamer = signup("Jane Streamer", "jane@example.com", "MARKETER");
        otherStreamer = signup("Bob Streamer", "bob@example.com", "MARKETER");
        postingId = createPosting(accessToken(brand), "CUSTOMER_POSTING", "OPEN");
    }

    private String createPosting(String token, String category, String status) throws Exception {
        String body = mockMvc.perform(bearer(post("/api/v1/postings"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"%s","status":"%s","title":"Need gaming streamers",
                                 "text":"Promote our new game","payFrom":200,"payTo":1000,"payCurrency":"EUR"}
                                """.formatted(category, status)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions makeOffer(String token, String posting, String priceFrom, String priceTo) throws Exception {
        return mockMvc.perform(bearer(post("/api/v1/postings/{id}/offers", posting), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"text":"Two sponsored streams with a shout-out","priceFrom":%s,"priceTo":%s}
                        """.formatted(priceFrom, priceTo)));
    }

    private String offerId(ResultActions result) throws Exception {
        return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.id");
    }

    private ResultActions setStatus(String token, String offerId, String status) throws Exception {
        return mockMvc.perform(bearer(patch("/api/v1/offers/{id}/status", offerId), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"%s\"}".formatted(status)));
    }

    private ResultActions accept(String token, String offerId, String amount) throws Exception {
        return mockMvc.perform(bearer(patch("/api/v1/offers/{id}/status", offerId), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\",\"amount\":%s}".formatted(amount)));
    }

    @Test
    void marketerMakesOfferAndCustomerAcceptsIt() throws Exception {
        String offer = offerId(makeOffer(accessToken(streamer), postingId, "400", "600.50"));

        mockMvc.perform(bearer(get("/api/v1/offers/{id}", offer), accessToken(brand)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.text").value("Two sponsored streams with a shout-out"))
                .andExpect(jsonPath("$.priceFrom").value(400.0))
                .andExpect(jsonPath("$.priceTo").value(600.5))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.posting.payCurrency").value("EUR"))
                .andExpect(jsonPath("$.posting.id").value(postingId))
                .andExpect(jsonPath("$.posting.creator.id").value(userId(brand)))
                .andExpect(jsonPath("$.offerer.id").value(userId(streamer)))
                .andExpect(jsonPath("$.offerer.fullName").value("Jane Streamer"));

        setStatus(accessToken(streamer), offer, "ACCEPTED").andExpect(status().isForbidden());
        accept(accessToken(brand), offer, "500")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        setStatus(accessToken(brand), offer, "REJECTED").andExpect(status().isConflict());
        setStatus(accessToken(streamer), offer, "WITHDRAWN").andExpect(status().isConflict());
    }

    @Test
    void customerMakesOfferOnMarketerPosting() throws Exception {
        String marketerPosting = createPosting(accessToken(streamer), "MARKETER_POSTING", "OPEN");

        offerId(makeOffer(accessToken(brand), marketerPosting, "500", "500"));
        makeOffer(accessToken(otherStreamer), marketerPosting, "500", "500")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only CUSTOMER users can make offers on MARKETER_POSTING postings"));
    }

    @Test
    void offerRules() throws Exception {
        makeOffer(accessToken(brand), postingId, "1", "2").andExpect(status().isForbidden());
        makeOffer(accessToken(otherBrand), postingId, "1", "2").andExpect(status().isForbidden());
        makeOffer(adminAccessToken(), postingId, "1", "2").andExpect(status().isForbidden());
        makeOffer(accessToken(streamer), postingId, "900", "100").andExpect(status().isBadRequest());

        offerId(makeOffer(accessToken(streamer), postingId, "100", "200"));
        makeOffer(accessToken(streamer), postingId, "150", "250")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "You already have a pending offer on this posting; update or withdraw it"));
    }

    @Test
    void currencyRules() throws Exception {
        mockMvc.perform(bearer(post("/api/v1/postings/{id}/offers", postingId), accessToken(streamer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"x\",\"priceFrom\":1,\"priceTo\":2,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The offer must be in the posting's currency (EUR)"));

        String unpriced = JsonPath.read(mockMvc.perform(bearer(post("/api/v1/postings"), accessToken(otherBrand))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"CUSTOMER_POSTING\",\"status\":\"OPEN\",\"title\":\"t\",\"text\":\"x\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        makeOffer(accessToken(streamer), unpriced, "1", "2")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("currency is required because the posting has no pay currency"));
        mockMvc.perform(bearer(post("/api/v1/postings/{id}/offers", unpriced), accessToken(streamer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"x\",\"priceFrom\":1,\"priceTo\":2,\"currency\":\"gbp\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("GBP"));

        offerId(makeOffer(accessToken(streamer), postingId, "100", "200"));
        mockMvc.perform(bearer(put("/api/v1/postings/{id}", postingId), accessToken(brand))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"CUSTOMER_POSTING","title":"t","text":"x","payFrom":1,"payCurrency":"USD"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void withdrawnOfferAllowsANewOne() throws Exception {
        String first = offerId(makeOffer(accessToken(streamer), postingId, "100", "200"));

        setStatus(accessToken(brand), first, "WITHDRAWN").andExpect(status().isForbidden());
        setStatus(accessToken(streamer), first, "WITHDRAWN").andExpect(status().isOk());

        offerId(makeOffer(accessToken(streamer), postingId, "150", "250"));
    }

    @Test
    void draftAndClosedPostingsDoNotAcceptOffers() throws Exception {
        String draft = createPosting(accessToken(brand), "CUSTOMER_POSTING", "DRAFT");
        makeOffer(accessToken(streamer), draft, "1", "2").andExpect(status().isNotFound());

        String pending = offerId(makeOffer(accessToken(streamer), postingId, "100", "200"));
        String other = offerId(makeOffer(accessToken(otherStreamer), postingId, "100", "200"));
        mockMvc.perform(bearer(patch("/api/v1/postings/{id}/status", postingId), accessToken(brand))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk());

        makeOffer(accessToken(otherStreamer), postingId, "1", "2").andExpect(status().isConflict());
        mockMvc.perform(bearer(put("/api/v1/offers/{id}", pending), accessToken(streamer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Revised\",\"priceFrom\":1,\"priceTo\":2}"))
                .andExpect(status().isConflict());
        accept(accessToken(brand), pending, "150").andExpect(status().isConflict());
        setStatus(accessToken(brand), other, "REJECTED").andExpect(status().isOk());
    }

    @Test
    void offererCanRevisePendingOffer() throws Exception {
        String offer = offerId(makeOffer(accessToken(streamer), postingId, "100", "200"));

        mockMvc.perform(bearer(put("/api/v1/offers/{id}", offer), accessToken(streamer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Three streams instead\",\"priceFrom\":300,\"priceTo\":300}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Three streams instead"))
                .andExpect(jsonPath("$.priceFrom").value(300.0));

        mockMvc.perform(bearer(put("/api/v1/offers/{id}", offer), accessToken(brand))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Cheaper\",\"priceFrom\":1,\"priceTo\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void offersArePrivateToTheTwoPartiesAndAdmins() throws Exception {
        String janes = offerId(makeOffer(accessToken(streamer), postingId, "100", "200"));
        offerId(makeOffer(accessToken(otherStreamer), postingId, "150", "300"));

        mockMvc.perform(bearer(get("/api/v1/postings/{id}/offers", postingId), accessToken(brand)))
                .andExpect(jsonPath("$.content", hasSize(2)));
        mockMvc.perform(bearer(get("/api/v1/postings/{id}/offers", postingId), adminAccessToken()))
                .andExpect(jsonPath("$.content", hasSize(2)));
        mockMvc.perform(bearer(get("/api/v1/postings/{id}/offers", postingId), accessToken(streamer)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(janes));
        mockMvc.perform(bearer(get("/api/v1/postings/{id}/offers", postingId), accessToken(otherBrand)))
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(bearer(get("/api/v1/offers/{id}", janes), accessToken(otherStreamer)))
                .andExpect(status().isNotFound());
        mockMvc.perform(bearer(get("/api/v1/offers/{id}", janes), accessToken(otherBrand)))
                .andExpect(status().isNotFound());
    }

    @Test
    void myOffersAcrossPostingsWithStatusFilter() throws Exception {
        String secondPosting = createPosting(accessToken(otherBrand), "CUSTOMER_POSTING", "OPEN");
        String first = offerId(makeOffer(accessToken(streamer), postingId, "100", "200"));
        offerId(makeOffer(accessToken(streamer), secondPosting, "100", "200"));
        setStatus(accessToken(brand), first, "REJECTED").andExpect(status().isOk());

        mockMvc.perform(bearer(get("/api/v1/offers/mine"), accessToken(streamer)))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mockMvc.perform(bearer(get("/api/v1/offers/mine"), accessToken(streamer)).param("status", "REJECTED"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(first));
        mockMvc.perform(bearer(get("/api/v1/offers/mine"), accessToken(otherStreamer)))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void offersAreDeletedWithTheirPostingOrOfferer() throws Exception {
        offerId(makeOffer(accessToken(streamer), postingId, "100", "200"));
        String secondPosting = createPosting(accessToken(otherBrand), "CUSTOMER_POSTING", "OPEN");
        offerId(makeOffer(accessToken(otherStreamer), secondPosting, "100", "200"));

        mockMvc.perform(bearer(delete("/api/v1/postings/{id}", postingId), accessToken(brand)))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM offers", Integer.class)).isEqualTo(1);

        mockMvc.perform(bearer(delete("/api/v1/users/{id}", userId(otherStreamer)), accessToken(otherStreamer)))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM offers", Integer.class)).isZero();
    }

    @Test
    void databaseEnforcesOnePendingOfferPerUserAndPosting() {
        String insert = "INSERT INTO offers (id, posting_id, offerer_id, body, status, price_from, price_to, currency) "
                + "VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'x', 'PENDING', 1, 2, 'EUR')";
        jdbcTemplate.update(insert, postingId, userId(streamer));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> jdbcTemplate.update(insert, postingId, userId(streamer)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
