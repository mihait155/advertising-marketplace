package com.advertisingmarketplace.posting;

import static com.advertisingmarketplace.support.WithJwt.as;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.common.money.MoneyConfig;
import com.advertisingmarketplace.posting.PostingExceptions.CategoryNotAllowedException;
import com.advertisingmarketplace.posting.PostingExceptions.NotPostingOwnerException;
import com.advertisingmarketplace.posting.dto.PostingResponse;
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

@WebMvcTest(PostingController.class)
@Import({SecurityConfig.class, MoneyConfig.class})
class PostingControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String BODY = """
            {"category":"MARKETER_POSTING","title":"Sponsored stream slot","text":"60s ad read on my channel"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostingService postingService;

    private static PostingResponse posting(UUID id) {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new PostingResponse(id, PostingCategory.MARKETER_POSTING, PostingStatus.OPEN, "Sponsored stream slot",
                "60s ad read on my channel", new BigDecimal("500.00"), new BigDecimal("900.00"), "EUR",
                new UserSummary(USER_ID, "Jane", UserType.MARKETER), List.of(), now, now);
    }

    @Test
    void createReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(postingService.create(eq(USER_ID), any())).thenReturn(posting(id));

        mockMvc.perform(post("/api/v1/postings").with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/postings/" + id))
                .andExpect(jsonPath("$.category").value("MARKETER_POSTING"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.payFrom").value(500.00))
                .andExpect(jsonPath("$.payTo").value(900.00))
                .andExpect(jsonPath("$.payCurrency").value("EUR"))
                .andExpect(jsonPath("$.creator.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.images").isArray());
    }

    @Test
    void createValidatesBody() throws Exception {
        mockMvc.perform(post("/api/v1/postings").with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"text\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("category")))
                .andExpect(jsonPath("$.errors", hasKey("title")))
                .andExpect(jsonPath("$.errors", hasKey("text")));

        verifyNoInteractions(postingService);
    }

    @Test
    void createRejectsTooLongText() throws Exception {
        String text = "a".repeat(20_001);

        mockMvc.perform(post("/api/v1/postings").with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MARKETER_POSTING\",\"title\":\"t\",\"text\":\"" + text + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("text")));
    }

    @Test
    void wrongCategoryForUserIs403WithReason() throws Exception {
        when(postingService.create(any(), any()))
                .thenThrow(new CategoryNotAllowedException(UserType.CUSTOMER, PostingCategory.MARKETER_POSTING));

        mockMvc.perform(post("/api/v1/postings").with(as(USER_ID, UserType.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("CUSTOMER users cannot publish MARKETER_POSTING postings"));
    }

    @Test
    void postingsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/postings")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/postings").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(postingService);
    }

    @Test
    void listPassesFilters() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(postingService.list(eq(new PostingFilter(PostingCategory.CUSTOMER_POSTING, PostingStatus.OPEN, creatorId,
                new BigDecimal("100"), new BigDecimal("500.5"), "usd")), eq(USER_ID), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(posting(UUID.randomUUID()))));

        mockMvc.perform(get("/api/v1/postings").with(as(USER_ID, UserType.MARKETER))
                        .param("category", "CUSTOMER_POSTING").param("status", "OPEN")
                        .param("creatorId", creatorId.toString())
                        .param("minPay", "100").param("maxPay", "500.5").param("currency", "usd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void updatePassesActorAndAdminFlag() throws Exception {
        UUID id = UUID.randomUUID();
        when(postingService.update(eq(id), eq(USER_ID), eq(true), any())).thenReturn(posting(id));

        mockMvc.perform(put("/api/v1/postings/{id}", id).with(as(USER_ID, UserType.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void createRejectsInvalidPayRangeAndClosedStatus() throws Exception {
        mockMvc.perform(post("/api/v1/postings").with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"MARKETER_POSTING","status":"CLOSED","title":"t","text":"x",
                                 "payFrom":900,"payTo":100}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("payRangeOrdered")))
                .andExpect(jsonPath("$.errors", hasKey("initialStatusAllowed")));

        mockMvc.perform(post("/api/v1/postings").with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"MARKETER_POSTING","title":"t","text":"x","payFrom":-1,"payTo":1.234}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("payFrom")))
                .andExpect(jsonPath("$.errors", hasKey("payTo")))
                .andExpect(jsonPath("$.errors", hasKey("payCurrencyConsistent")));

        mockMvc.perform(post("/api/v1/postings").with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"MARKETER_POSTING","title":"t","text":"x","payFrom":1,"payCurrency":"XYZ"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.payCurrency").value("must be one of EUR, USD, GBP, RON"));

        verifyNoInteractions(postingService);
    }

    @Test
    void listValidatesPayFilterParameters() throws Exception {
        mockMvc.perform(get("/api/v1/postings").with(as(USER_ID, UserType.MARKETER))
                        .param("minPay", "-5").param("currency", "BTC"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(postingService);
    }

    @Test
    void changeStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(postingService.changeStatus(id, USER_ID, false, PostingStatus.CLOSED)).thenReturn(posting(id));

        mockMvc.perform(patch("/api/v1/postings/{id}/status", id).with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/postings/{id}/status", id).with(as(USER_ID, UserType.MARKETER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPassesViewer() throws Exception {
        UUID id = UUID.randomUUID();
        when(postingService.get(id, USER_ID, true)).thenReturn(posting(id));

        mockMvc.perform(get("/api/v1/postings/{id}", id).with(as(USER_ID, UserType.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void nonOwnerDeleteIs403() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new NotPostingOwnerException()).when(postingService).delete(id, USER_ID, false);

        mockMvc.perform(delete("/api/v1/postings/{id}", id).with(as(USER_ID, UserType.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/postings/{id}", id).with(as(USER_ID, UserType.MARKETER)))
                .andExpect(status().isNoContent());

        verify(postingService).delete(id, USER_ID, false);
    }
}
