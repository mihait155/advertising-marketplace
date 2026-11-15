package com.advertisingmarketplace.messaging;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ReportApiIT extends MessagingTestSupport {

    private ResultActions report(String token, String messageId, String reason) throws Exception {
        return mockMvc.perform(bearer(post("/api/v1/messages/{id}/reports", messageId), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"%s\"}".formatted(reason)));
    }

    private ResultActions resolve(String token, String reportId, String resolution) throws Exception {
        return mockMvc.perform(bearer(post("/api/v1/admin/reports/{id}/resolve", reportId), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"%s\"}".formatted(resolution)));
    }

    @Test
    void reportedMessageCanBeRemovedByAnAdmin() throws Exception {
        String conversation = conversationId(accessToken(streamer), postingId);
        String abusive = sentId(send(accessToken(streamer), conversation, "pay me outside the platform or else"));

        String reportBody = report(accessToken(brand), abusive, "Threatening and off-platform payment")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        String reportId = JsonPath.read(reportBody, "$.id");
        report(accessToken(brand), abusive, "again").andExpect(status().isConflict());

        mockMvc.perform(bearer(get("/api/v1/admin/reports"), accessToken(brand))).andExpect(status().isForbidden());

        String admin = adminAccessToken();
        mockMvc.perform(bearer(get("/api/v1/admin/reports"), admin))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].message.text").value("pay me outside the platform or else"))
                .andExpect(jsonPath("$.content[0].reporter.fullName").value("Acme Brand"))
                .andExpect(jsonPath("$.content[0].conversationId").value(conversation));
        mockMvc.perform(bearer(get("/api/v1/admin/conversations/{id}/messages", conversation), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(1)));

        resolve(admin, reportId, "MESSAGE_DELETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("MESSAGE_DELETED"))
                .andExpect(jsonPath("$.resolvedBy.fullName").value("Administrator"));
        resolve(admin, reportId, "DISMISSED").andExpect(status().isConflict());

        messages(accessToken(brand), conversation)
                .andExpect(jsonPath("$.messages[0].deleted").value(true))
                .andExpect(jsonPath("$.messages[0].text").doesNotExist());
        mockMvc.perform(bearer(get("/api/v1/admin/reports"), admin)).andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(bearer(get("/api/v1/admin/reports"), admin).param("status", "RESOLVED"))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void reportRules() throws Exception {
        String conversation = conversationId(accessToken(streamer), postingId);
        String own = sentId(send(accessToken(brand), conversation, "my own message"));
        String outsider = signup("Bob", "bob@example.com", "MARKETER");

        report(accessToken(brand), own, "x").andExpect(status().isBadRequest());
        report(accessToken(outsider), own, "x").andExpect(status().isNotFound());
        report(accessToken(streamer), own, "").andExpect(status().isBadRequest());

        mockMvc.perform(bearer(post("/api/v1/postings/{id}/offers", postingId), accessToken(streamer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"x\",\"priceFrom\":1,\"priceTo\":2}"));
        String system = JsonPath.read(messages(accessToken(brand), conversation).andReturn().getResponse()
                .getContentAsString(), "$.messages[0].id");
        report(accessToken(brand), system, "x").andExpect(status().isBadRequest());
    }

    @Test
    void adminsCanOnlyReadReportedConversationsAndCanDismiss() throws Exception {
        String conversation = conversationId(accessToken(streamer), postingId);
        String message = sentId(send(accessToken(streamer), conversation, "borderline"));
        String admin = adminAccessToken();

        mockMvc.perform(bearer(get("/api/v1/admin/conversations/{id}/messages", conversation), admin))
                .andExpect(status().isNotFound());

        String reportId = JsonPath.read(report(accessToken(brand), message, "rude").andReturn().getResponse()
                .getContentAsString(), "$.id");
        resolve(admin, reportId, "DISMISSED")
                .andExpect(jsonPath("$.resolution").value("DISMISSED"));
        messages(accessToken(brand), conversation).andExpect(jsonPath("$.messages[0].text").value("borderline"));
    }
}
