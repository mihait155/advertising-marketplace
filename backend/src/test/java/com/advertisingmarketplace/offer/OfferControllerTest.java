package com.advertisingmarketplace.offer;

import static com.advertisingmarketplace.support.WithJwt.as;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.common.money.MoneyConfig;
import com.advertisingmarketplace.offer.OfferExceptions.DuplicatePendingOfferException;
import com.advertisingmarketplace.offer.OfferExceptions.OfferNotPendingException;
import com.advertisingmarketplace.offer.dto.OfferResponse;
import com.advertisingmarketplace.posting.PostingCategory;
import com.advertisingmarketplace.posting.PostingStatus;
import com.advertisingmarketplace.security.SecurityConfig;
import com.advertisingmarketplace.user.UserType;
import com.advertisingmarketplace.user.dto.UserSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OfferController.class)
@Import({SecurityConfig.class, MoneyConfig.class})
class OfferControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID POSTING_ID = UUID.randomUUID();
    private static final String BODY = """
            {"text":"Three sponsored streams","priceFrom":300,"priceTo":450.50}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OfferService offerService;

    private static OfferResponse offer(UUID id, OfferStatus status) {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new OfferResponse(id,
                new OfferResponse.PostingSummary(POSTING_ID, "Need streamers", PostingCategory.CUSTOMER_POSTING,
                        PostingStatus.OPEN, "EUR", new UserSummary(UUID.randomUUID(), "Acme", UserType.CUSTOMER)),
                new UserSummary(USER_ID, "Jane", UserType.MARKETER), "Three sponsored streams", status,
                new BigDecimal("300.00"), new BigDecimal("450.50"), "EUR", null, now, now);
    }

    @Test
    void createReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(offerService.create(eq(POSTING_ID), eq(USER_ID), any())).thenReturn(offer(id, OfferStatus.PENDING));

        mockMvc.perform(post("/api/v1/postings/{postingId}/offers", POSTING_ID).with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/offers/" + id))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.priceFrom").value(300.00))
                .andExpect(jsonPath("$.priceTo").value(450.50))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.posting.id").value(POSTING_ID.toString()))
                .andExpect(jsonPath("$.offerer.id").value(USER_ID.toString()));
    }

    @Test
    void createValidatesBody() throws Exception {
        mockMvc.perform(post("/api/v1/postings/{postingId}/offers", POSTING_ID).with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\",\"priceFrom\":500,\"priceTo\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("text")))
                .andExpect(jsonPath("$.errors", hasKey("priceRangeOrdered")));

        mockMvc.perform(post("/api/v1/postings/{postingId}/offers", POSTING_ID).with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"x\",\"currency\":\"BTC\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("priceFrom")))
                .andExpect(jsonPath("$.errors", hasKey("priceTo")))
                .andExpect(jsonPath("$.errors", hasKey("currency")));

        verifyNoInteractions(offerService);
    }

    @Test
    void duplicatePendingOfferIs409() throws Exception {
        when(offerService.create(any(), any(), any())).thenThrow(new DuplicatePendingOfferException());

        mockMvc.perform(post("/api/v1/postings/{postingId}/offers", POSTING_ID).with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void offersRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/offers/mine")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/postings/{postingId}/offers", POSTING_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(offerService);
    }

    @Test
    void listForPostingPassesViewerAndFilter() throws Exception {
        when(offerService.listForPosting(eq(POSTING_ID), eq(OfferStatus.PENDING), eq(USER_ID), eq(false),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(offer(UUID.randomUUID(), OfferStatus.PENDING))));

        mockMvc.perform(get("/api/v1/postings/{postingId}/offers", POSTING_ID).with(as(USER_ID, UserType.CUSTOMER))
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void listMine() throws Exception {
        when(offerService.listMine(eq(USER_ID), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(offer(UUID.randomUUID(), OfferStatus.ACCEPTED))));

        mockMvc.perform(get("/api/v1/offers/mine").with(as(USER_ID, UserType.MARKETER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("ACCEPTED"));
    }

    @Test
    void getUpdateAndChangeStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(offerService.get(id, USER_ID, false)).thenReturn(offer(id, OfferStatus.PENDING));
        when(offerService.update(eq(id), eq(USER_ID), eq(false), any())).thenReturn(offer(id, OfferStatus.PENDING));
        when(offerService.changeStatus(id, USER_ID, false, OfferStatus.WITHDRAWN, null))
                .thenReturn(offer(id, OfferStatus.WITHDRAWN));

        mockMvc.perform(get("/api/v1/offers/{id}", id).with(as(USER_ID, UserType.MARKETER)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/offers/{id}", id).with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/offers/{id}/status", id).with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"WITHDRAWN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void changingDecidedOfferIs409() throws Exception {
        UUID id = UUID.randomUUID();
        when(offerService.changeStatus(id, USER_ID, false, OfferStatus.ACCEPTED, null))
                .thenThrow(new OfferNotPendingException(OfferStatus.REJECTED));

        mockMvc.perform(patch("/api/v1/offers/{id}/status", id).with(as(USER_ID, UserType.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The offer is REJECTED and can no longer be changed"));
    }
}
