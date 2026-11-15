package com.advertisingmarketplace.messaging;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

abstract class MessagingTestSupport extends AbstractIntegrationTest {

    protected String brand;
    protected String streamer;
    protected String postingId;

    @BeforeEach
    void setUpParties() throws Exception {
        brand = signup("Acme Brand", "brand@example.com", "CUSTOMER");
        streamer = signup("Jane Streamer", "jane@example.com", "MARKETER");
        postingId = createPosting(accessToken(brand), "CUSTOMER_POSTING", "OPEN");
    }

    protected String createPosting(String token, String category, String status) throws Exception {
        String body = mockMvc.perform(bearer(post("/api/v1/postings"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"%s","status":"%s","title":"Need gaming streamers","text":"x",
                                 "payFrom":500,"payTo":1000,"payCurrency":"EUR"}
                                """.formatted(category, status)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    protected ResultActions start(String token, String posting, String text) throws Exception {
        return mockMvc.perform(bearer(post("/api/v1/postings/{id}/conversations", posting), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(text == null ? "{}" : "{\"text\":\"%s\"}".formatted(text)));
    }

    protected String conversationId(String token, String posting) throws Exception {
        return JsonPath.read(start(token, posting, null).andReturn().getResponse().getContentAsString(), "$.id");
    }

    protected ResultActions send(String token, String conversationId, String text) throws Exception {
        return mockMvc.perform(bearer(post("/api/v1/conversations/{id}/messages", conversationId), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"%s\"}".formatted(text)));
    }

    protected String sentId(ResultActions result) throws Exception {
        return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.id");
    }

    protected ResultActions messages(String token, String conversationId) throws Exception {
        return mockMvc.perform(bearer(get("/api/v1/conversations/{id}/messages", conversationId), token));
    }
}
