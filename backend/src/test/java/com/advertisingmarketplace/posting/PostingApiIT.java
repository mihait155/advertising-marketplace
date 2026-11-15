package com.advertisingmarketplace.posting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.image.ImageCleanup;
import com.advertisingmarketplace.support.AbstractIntegrationTest;
import com.advertisingmarketplace.support.ExternalServicesConfiguration;
import com.advertisingmarketplace.support.TestFiles;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

class PostingApiIT extends AbstractIntegrationTest {

    @Autowired
    private S3Client s3;

    @Autowired
    private ImageCleanup imageCleanup;

    private String marketer;
    private String customer;

    @BeforeEach
    void signUp() throws Exception {
        marketer = signup("Jane Streamer", "jane@example.com", "MARKETER");
        customer = signup("Acme Brand", "brand@example.com", "CUSTOMER");
    }

    private ResultActions createPosting(String token, String category, String title, String text) throws Exception {
        return createPosting(token, category, "OPEN", title, text);
    }

    private ResultActions createPosting(String token, String category, String status, String title, String text)
            throws Exception {
        return mockMvc.perform(bearer(post("/api/v1/postings"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"category":"%s","status":"%s","title":"%s","text":"%s","payFrom":250,"payTo":750.5,"payCurrency":"EUR"}
                        """.formatted(category, status, title, text)));
    }

    private ResultActions changeStatus(String token, String id, String status) throws Exception {
        return mockMvc.perform(bearer(patch("/api/v1/postings/{id}/status", id), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"%s\"}".formatted(status)));
    }

    private String createdId(ResultActions result) throws Exception {
        return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.id");
    }

    private boolean objectExists(String imageId) {
        String key = storageKey(imageId);
        try {
            s3.headObject(b -> b.bucket(ExternalServicesConfiguration.BUCKET).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private String storageKey(String imageId) {
        return jdbcTemplate.queryForObject("SELECT storage_key FROM images WHERE id = ?::uuid", String.class, imageId);
    }

    @Test
    void marketerPublishesPostingWithEmbeddedImages() throws Exception {
        String token = accessToken(marketer);
        String banner = uploadImage(token, TestFiles.png());
        String screenshot = uploadImage(token, TestFiles.png());
        String text = "Ad slots on my stream.\\n![banner](image:%s)\\nStats: ![](image:%s)".formatted(banner, screenshot);

        String id = createdId(createPosting(token, "MARKETER_POSTING", "Stream sponsorship", text));

        mockMvc.perform(bearer(get("/api/v1/postings/{id}", id), accessToken(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("MARKETER_POSTING"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.payFrom").value(250.0))
                .andExpect(jsonPath("$.payTo").value(750.5))
                .andExpect(jsonPath("$.payCurrency").value("EUR"))
                .andExpect(jsonPath("$.title").value("Stream sponsorship"))
                .andExpect(jsonPath("$.text").value(text.replace("\\n", "\n")))
                .andExpect(jsonPath("$.creator.id").value(userId(marketer)))
                .andExpect(jsonPath("$.creator.fullName").value("Jane Streamer"))
                .andExpect(jsonPath("$.creator.userType").value("MARKETER"))
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andExpect(jsonPath("$.images[0].id").value(banner))
                .andExpect(jsonPath("$.images[1].id").value(screenshot))
                .andExpect(jsonPath("$.images[0].url").isNotEmpty());

        mockMvc.perform(bearer(get("/api/v1/images/{id}", banner), accessToken(customer)))
                .andExpect(status().isOk());
    }

    @Test
    void categoryMustMatchTheCreatorsUserType() throws Exception {
        createPosting(accessToken(marketer), "CUSTOMER_POSTING", "Wrong", "x")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("MARKETER users cannot publish CUSTOMER_POSTING postings"));
        createPosting(accessToken(customer), "MARKETER_POSTING", "Wrong", "x")
                .andExpect(status().isForbidden());
        createdId(createPosting(accessToken(customer), "CUSTOMER_POSTING", "Need 10 influencers", "Budget 5k"));
        createdId(createPosting(adminAccessToken(), "MARKETER_POSTING", "Featured", "Admin post"));
    }

    @Test
    void cannotEmbedSomeoneElsesImageOrExternalImages() throws Exception {
        String customersImage = uploadImage(accessToken(customer), TestFiles.png());

        createPosting(accessToken(marketer), "MARKETER_POSTING", "Stolen", "![x](image:" + customersImage + ")")
                .andExpect(status().isBadRequest());
        createPosting(accessToken(marketer), "MARKETER_POSTING", "Tracker", "![x](https://evil.example/pixel.gif)")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Only uploaded images")));
    }

    @Test
    void anImageCanOnlyBeEmbeddedInOnePosting() throws Exception {
        String token = accessToken(marketer);
        String image = uploadImage(token, TestFiles.png());
        createdId(createPosting(token, "MARKETER_POSTING", "First", "![](image:" + image + ")"));

        createPosting(token, "MARKETER_POSTING", "Second", "![](image:" + image + ")")
                .andExpect(status().isBadRequest());
    }

    @Test
    void browsingFiltersByCategoryAndCreator() throws Exception {
        createdId(createPosting(accessToken(marketer), "MARKETER_POSTING", "Offer 1", "x"));
        createdId(createPosting(accessToken(marketer), "MARKETER_POSTING", "Offer 2", "x"));
        createdId(createPosting(accessToken(customer), "CUSTOMER_POSTING", "Request 1", "x"));
        String viewer = accessToken(customer);

        mockMvc.perform(bearer(get("/api/v1/postings"), viewer).param("category", "MARKETER_POSTING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title").value("Offer 2"));
        mockMvc.perform(bearer(get("/api/v1/postings"), viewer).param("creatorId", userId(customer)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Request 1"));
        mockMvc.perform(bearer(get("/api/v1/postings"), viewer).param("size", "2"))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void onlyCreatorOrAdminCanEditOrDelete() throws Exception {
        String id = createdId(createPosting(accessToken(marketer), "MARKETER_POSTING", "Mine", "x"));
        String update = """
                {"category":"MARKETER_POSTING","title":"Edited","text":"y","payFrom":100,"payTo":200,"payCurrency":"USD"}
                """;

        mockMvc.perform(bearer(put("/api/v1/postings/{id}", id), accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isForbidden());
        mockMvc.perform(bearer(delete("/api/v1/postings/{id}", id), accessToken(customer)))
                .andExpect(status().isForbidden());

        mockMvc.perform(bearer(put("/api/v1/postings/{id}", id), accessToken(marketer))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Edited"));
        mockMvc.perform(bearer(delete("/api/v1/postings/{id}", id), adminAccessToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(bearer(get("/api/v1/postings/{id}", id), accessToken(marketer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void draftsAreHiddenFromOthersUntilPublished() throws Exception {
        String token = accessToken(marketer);
        String image = uploadImage(token, TestFiles.png());
        String draftId = createdId(createPosting(token, "MARKETER_POSTING", "DRAFT", "Work in progress",
                "![](image:" + image + ")"));
        String other = accessToken(customer);

        mockMvc.perform(bearer(get("/api/v1/postings/{id}", draftId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(bearer(get("/api/v1/postings/{id}", draftId), other)).andExpect(status().isNotFound());
        mockMvc.perform(bearer(get("/api/v1/images/{id}", image), other)).andExpect(status().isNotFound());
        mockMvc.perform(bearer(get("/api/v1/postings"), other)).andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(bearer(get("/api/v1/postings"), token)).andExpect(jsonPath("$.content", hasSize(1)));
        mockMvc.perform(bearer(get("/api/v1/postings"), adminAccessToken()))
                .andExpect(jsonPath("$.content", hasSize(1)));

        changeStatus(token, draftId, "OPEN").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(bearer(get("/api/v1/postings/{id}", draftId), other)).andExpect(status().isOk());
        mockMvc.perform(bearer(get("/api/v1/images/{id}", image), other)).andExpect(status().isOk());
    }

    @Test
    void statusLifecycle() throws Exception {
        String token = accessToken(marketer);
        String id = createdId(createPosting(token, "MARKETER_POSTING", "DRAFT", "Lifecycle", "x"));
        String update = """
                {"category":"MARKETER_POSTING","title":"Edited","text":"y"}
                """;

        changeStatus(token, id, "CLOSED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A posting cannot go from DRAFT to CLOSED"));
        changeStatus(accessToken(customer), id, "OPEN").andExpect(status().isNotFound());
        changeStatus(token, id, "OPEN").andExpect(status().isOk());
        changeStatus(token, id, "DRAFT").andExpect(status().isConflict());
        changeStatus(accessToken(customer), id, "CLOSED").andExpect(status().isForbidden());
        changeStatus(token, id, "CLOSED").andExpect(status().isOk());

        mockMvc.perform(bearer(put("/api/v1/postings/{id}", id), token)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict());

        changeStatus(token, id, "OPEN").andExpect(status().isOk());
        mockMvc.perform(bearer(put("/api/v1/postings/{id}", id), token)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payFrom").doesNotExist());

        mockMvc.perform(bearer(get("/api/v1/postings"), accessToken(customer)).param("status", "CLOSED"))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(bearer(get("/api/v1/postings"), accessToken(customer)).param("status", "OPEN"))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void createDefaultsToDraftAndValidatesPayRange() throws Exception {
        String token = accessToken(marketer);

        mockMvc.perform(bearer(post("/api/v1/postings"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MARKETER_POSTING\",\"title\":\"t\",\"text\":\"x\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.payFrom").doesNotExist())
                .andExpect(jsonPath("$.payCurrency").doesNotExist());

        mockMvc.perform(bearer(post("/api/v1/postings"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"MARKETER_POSTING","title":"t","text":"x","payFrom":100}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.payCurrencyConsistent").exists());

        mockMvc.perform(bearer(post("/api/v1/postings"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"MARKETER_POSTING","title":"t","text":"x","payFrom":900,"payTo":100}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supportedCurrenciesAreListed() throws Exception {
        mockMvc.perform(bearer(get("/api/v1/currencies"), accessToken(marketer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].code").value("EUR"))
                .andExpect(jsonPath("$[0].name").value("Euro"))
                .andExpect(jsonPath("$[0].fractionDigits").value(2))
                .andExpect(jsonPath("$[3].code").value("RON"));
    }

    @Test
    void removedAndUnusedImagesAreDeletedFromStorageByCleanup() throws Exception {
        String token = accessToken(marketer);
        String kept = uploadImage(token, TestFiles.png());
        String removed = uploadImage(token, TestFiles.png());
        String neverUsed = uploadImage(token, TestFiles.png());
        String id = createdId(createPosting(token, "MARKETER_POSTING", "Post",
                "![](image:%s) ![](image:%s)".formatted(kept, removed)));

        mockMvc.perform(bearer(put("/api/v1/postings/{id}", id), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MARKETER_POSTING\",\"title\":\"Post\",\"text\":\"![](image:%s)\"}"
                                .formatted(kept)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images", hasSize(1)));
        String removedKey = storageKey(removed);
        String neverUsedKey = storageKey(neverUsed);

        assertThat(imageCleanup.purgeOrphans()).isEqualTo(2);

        assertThat(objectExists(kept)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM images WHERE id IN (?::uuid, ?::uuid)",
                Integer.class, removed, neverUsed)).isZero();
        for (String key : new String[] {removedKey, neverUsedKey}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> s3.headObject(b -> b.bucket(ExternalServicesConfiguration.BUCKET).key(key)))
                    .isInstanceOf(NoSuchKeyException.class);
        }
    }

    @Test
    void deletingAPostingOrItsCreatorEventuallyDeletesTheImages() throws Exception {
        String token = accessToken(marketer);
        String first = uploadImage(token, TestFiles.png());
        String second = uploadImage(token, TestFiles.png());
        String postingA = createdId(createPosting(token, "MARKETER_POSTING", "A", "![](image:" + first + ")"));
        createdId(createPosting(token, "MARKETER_POSTING", "B", "![](image:" + second + ")"));

        mockMvc.perform(bearer(delete("/api/v1/postings/{id}", postingA), token)).andExpect(status().isNoContent());
        mockMvc.perform(bearer(delete("/api/v1/users/{id}", userId(marketer)), token))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM postings", Integer.class)).isZero();
        assertThat(objectExists(first)).isTrue();

        assertThat(imageCleanup.purgeOrphans()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM images", Integer.class)).isZero();
    }
}
