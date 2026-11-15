package com.advertisingmarketplace.user;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class UserApiIT extends AbstractIntegrationTest {

    private String adminToken;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        adminToken = adminAccessToken();
    }

    private String adminCreatesUser(String fullName, String email, String userType) throws Exception {
        String body = mockMvc.perform(bearer(post("/api/v1/users"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","email":"%s","phone":"+40712345678","userType":"%s",
                                 "password":"%s"}
                                """.formatted(fullName, email, userType, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminFullCrudLifecycle() throws Exception {
        String id = adminCreatesUser("Jane Doe", "Jane@Example.com", "MARKETER");

        mockMvc.perform(bearer(get("/api/v1/users/{id}", id), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.phone").value("+40712345678"))
                .andExpect(jsonPath("$.userType").value("MARKETER"))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(bearer(put("/api/v1/users/{id}", id), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Jane Smith","email":"jane.smith@example.com","userType":"CUSTOMER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Smith"))
                .andExpect(jsonPath("$.email").value("jane.smith@example.com"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.userType").value("CUSTOMER"));

        mockMvc.perform(bearer(delete("/api/v1/users/{id}", id), adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(bearer(get("/api/v1/users/{id}", id), adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCreatedUserCanLogIn() throws Exception {
        adminCreatesUser("Jane", "jane@example.com", "MARKETER");

        login("jane@example.com", PASSWORD);
    }

    @Test
    void nonAdminCannotCreateUsers() throws Exception {
        String token = accessToken(signup("Brand", "brand@example.com", "CUSTOMER"));

        mockMvc.perform(bearer(post("/api/v1/users"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"X","email":"x@example.com","userType":"ADMIN","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCanUpdateAndDeleteOnlyThemselves() throws Exception {
        String me = signup("Me", "me@example.com", "MARKETER");
        String other = signup("Other", "other@example.com", "MARKETER");
        String token = accessToken(me);

        mockMvc.perform(bearer(put("/api/v1/users/{id}", userId(me)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Me Renamed","email":"me@example.com","userType":"MARKETER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Me Renamed"));

        mockMvc.perform(bearer(put("/api/v1/users/{id}", userId(other)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Hacked","email":"other@example.com","userType":"MARKETER"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(bearer(delete("/api/v1/users/{id}", userId(other)), token))
                .andExpect(status().isForbidden());

        mockMvc.perform(bearer(delete("/api/v1/users/{id}", userId(me)), token))
                .andExpect(status().isNoContent());
    }

    @Test
    void userCannotPromoteThemselvesToAdmin() throws Exception {
        String me = signup("Me", "me@example.com", "CUSTOMER");

        mockMvc.perform(bearer(put("/api/v1/users/{id}", userId(me)), accessToken(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Me","email":"me@example.com","userType":"ADMIN"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(bearer(get("/api/v1/users/{id}", userId(me)), adminToken))
                .andExpect(jsonPath("$.userType").value("CUSTOMER"));
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        adminCreatesUser("First", "dup@example.com", "MARKETER");

        mockMvc.perform(bearer(post("/api/v1/users"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Second","email":"DUP@example.com","userType":"CUSTOMER",
                                 "password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void updatingToAnotherUsersEmailIsRejected() throws Exception {
        adminCreatesUser("First", "first@example.com", "MARKETER");
        String secondId = adminCreatesUser("Second", "second@example.com", "CUSTOMER");

        mockMvc.perform(bearer(put("/api/v1/users/{id}", secondId), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Second","email":"first@example.com","userType":"CUSTOMER"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void listFiltersByUserTypeAndPaginates() throws Exception {
        adminCreatesUser("Streamer One", "s1@example.com", "MARKETER");
        adminCreatesUser("Streamer Two", "s2@example.com", "MARKETER");
        adminCreatesUser("Influencer", "i1@example.com", "MARKETER");
        adminCreatesUser("Brand", "brand@example.com", "CUSTOMER");

        mockMvc.perform(bearer(get("/api/v1/users"), adminToken).param("userType", "MARKETER").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        mockMvc.perform(bearer(get("/api/v1/users"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(5));
    }

    @Test
    void invalidPayloadIsRejected() throws Exception {
        mockMvc.perform(bearer(post("/api/v1/users"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Jane","email":"bad","userType":"MARKETER","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email", notNullValue()));
    }

    @Test
    void readinessProbeIsPublic() throws Exception {
        mockMvc.perform(get("/readyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
