package com.advertisingmarketplace.user;

import static com.advertisingmarketplace.support.WithJwt.as;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.security.SecurityConfig;
import com.advertisingmarketplace.user.dto.CreateUserRequest;
import com.advertisingmarketplace.user.dto.UpdateUserRequest;
import com.advertisingmarketplace.user.dto.UserResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final String VALID_CREATE = """
            {"fullName":"Jane Doe","email":"jane@example.com","phone":"+40712345678",
             "userType":"MARKETER","password":"Sup3r-secret"}
            """;
    private static final String VALID_UPDATE = """
            {"fullName":"Jane Doe","email":"jane@example.com","userType":"MARKETER"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static UserResponse sampleUser(UUID id) {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new UserResponse(id, "Jane Doe", "jane@example.com", "+40712345678", UserType.MARKETER, now, now);
    }

    @Nested
    class Authentication {

        @Test
        void anonymousRequestIsRejectedWith401ProblemDetail() throws Exception {
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource"));

            verifyNoInteractions(userService);
        }

        @Test
        void invalidBearerTokenIsRejectedWith401() throws Exception {
            mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer not-a-jwt"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")));
        }

        @Test
        void responsesCarrySecurityHeaders() throws Exception {
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().exists("Content-Security-Policy"))
                    .andExpect(header().exists("Cache-Control"));
        }

        @Test
        void corsPreflightFromAllowedOriginSucceeds() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .options("/api/v1/users")
                            .header("Origin", "http://localhost:3000")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        }

        @Test
        void corsPreflightFromUnknownOriginIsRejected() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .options("/api/v1/users")
                            .header("Origin", "https://evil.example")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Create {

        @Test
        void adminCreateReturns201WithLocationAndBody() throws Exception {
            UUID id = UUID.randomUUID();
            when(userService.create(any(CreateUserRequest.class))).thenReturn(sampleUser(id));

            mockMvc.perform(post("/api/v1/users").with(as(ADMIN_ID, UserType.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith("/api/v1/users/" + id)))
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.userType").value("MARKETER"))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        @Test
        void nonAdminCannotCreateUsers() throws Exception {
            mockMvc.perform(post("/api/v1/users").with(as(UUID.randomUUID(), UserType.MARKETER))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));

            verifyNoInteractions(userService);
        }

        @Test
        void invalidBodyReturns400WithFieldErrors() throws Exception {
            mockMvc.perform(post("/api/v1/users").with(as(ADMIN_ID, UserType.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fullName":"","email":"not-an-email","phone":"abc","password":"short"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", hasKey("fullName")))
                    .andExpect(jsonPath("$.errors", hasKey("email")))
                    .andExpect(jsonPath("$.errors", hasKey("phone")))
                    .andExpect(jsonPath("$.errors", hasKey("userType")))
                    .andExpect(jsonPath("$.errors", hasKey("password")));

            verifyNoInteractions(userService);
        }

        @Test
        void unknownUserTypeReturns400() throws Exception {
            mockMvc.perform(post("/api/v1/users").with(as(ADMIN_ID, UserType.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fullName":"Jane","email":"jane@example.com","userType":"WIZARD",
                                     "password":"Sup3r-secret"}
                                    """))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(userService);
        }

        @Test
        void duplicateEmailReturns409() throws Exception {
            when(userService.create(any())).thenThrow(new EmailAlreadyInUseException("jane@example.com"));

            mockMvc.perform(post("/api/v1/users").with(as(ADMIN_ID, UserType.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Email jane@example.com is already in use"));
        }
    }

    @Nested
    class Read {

        @Test
        void anyAuthenticatedUserCanGetAUser() throws Exception {
            UUID id = UUID.randomUUID();
            when(userService.getById(id)).thenReturn(sampleUser(id));

            mockMvc.perform(get("/api/v1/users/{id}", id).with(as(UUID.randomUUID(), UserType.CUSTOMER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("jane@example.com"));
        }

        @Test
        void getByIdReturns404WhenMissing() throws Exception {
            UUID id = UUID.randomUUID();
            when(userService.getById(id)).thenThrow(new UserNotFoundException(id));

            mockMvc.perform(get("/api/v1/users/{id}", id).with(as(ADMIN_ID, UserType.ADMIN)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        void malformedIdReturns400() throws Exception {
            mockMvc.perform(get("/api/v1/users/{id}", "not-a-uuid").with(as(ADMIN_ID, UserType.ADMIN)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void listPassesTypeFilterAndPaging() throws Exception {
            UUID id = UUID.randomUUID();
            when(userService.list(eq(UserType.MARKETER), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(sampleUser(id)), PageRequest.of(0, 5), 1));

            mockMvc.perform(get("/api/v1/users").with(as(UUID.randomUUID(), UserType.CUSTOMER))
                            .param("userType", "MARKETER").param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                    .andExpect(jsonPath("$.page.size").value(5))
                    .andExpect(jsonPath("$.page.totalElements").value(1));
        }
    }

    @Nested
    class Update {

        @Test
        void userCanUpdateThemselvesWithoutTypeChangeRights() throws Exception {
            UUID id = UUID.randomUUID();
            when(userService.update(eq(id), any(UpdateUserRequest.class), eq(false))).thenReturn(sampleUser(id));

            mockMvc.perform(put("/api/v1/users/{id}", id).with(as(id, UserType.MARKETER))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()));
        }

        @Test
        void adminCanUpdateAnyoneWithTypeChangeRights() throws Exception {
            UUID id = UUID.randomUUID();
            when(userService.update(eq(id), any(UpdateUserRequest.class), eq(true))).thenReturn(sampleUser(id));

            mockMvc.perform(put("/api/v1/users/{id}", id).with(as(ADMIN_ID, UserType.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                    .andExpect(status().isOk());
        }

        @Test
        void userCannotUpdateSomeoneElse() throws Exception {
            mockMvc.perform(put("/api/v1/users/{id}", UUID.randomUUID()).with(as(UUID.randomUUID(), UserType.MARKETER))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(userService);
        }

        @Test
        void serviceAccessDeniedMapsTo403() throws Exception {
            UUID id = UUID.randomUUID();
            when(userService.update(eq(id), any(), anyBoolean()))
                    .thenThrow(new AccessDeniedException("Only administrators can change a user's type"));

            mockMvc.perform(put("/api/v1/users/{id}", id).with(as(id, UserType.CUSTOMER))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Delete {

        @Test
        void userCanDeleteThemselves() throws Exception {
            UUID id = UUID.randomUUID();

            mockMvc.perform(delete("/api/v1/users/{id}", id).with(as(id, UserType.CUSTOMER)))
                    .andExpect(status().isNoContent());

            verify(userService).delete(id);
        }

        @Test
        void userCannotDeleteSomeoneElse() throws Exception {
            mockMvc.perform(delete("/api/v1/users/{id}", UUID.randomUUID()).with(as(UUID.randomUUID(), UserType.CUSTOMER)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(userService);
        }

        @Test
        void adminDeleteOfMissingUserReturns404() throws Exception {
            UUID id = UUID.randomUUID();
            doThrow(new UserNotFoundException(id)).when(userService).delete(id);

            mockMvc.perform(delete("/api/v1/users/{id}", id).with(as(ADMIN_ID, UserType.ADMIN)))
                    .andExpect(status().isNotFound());
        }
    }
}
