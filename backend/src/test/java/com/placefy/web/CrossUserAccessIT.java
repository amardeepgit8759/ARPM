package com.placefy.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.placefy.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Proves that one user cannot reach another user's data, on every endpoint that exists.
 *
 * <p>Placefy has no tenants, so the isolation boundary is user-to-user (ADR-003). That makes this
 * the suite the product's privacy actually rests on, rather than a formality that passes because
 * there is only ever one tenant.
 *
 * <p>The last test is the one that keeps this honest over time: it enumerates every mapped
 * endpoint from Spring's own handler mapping and fails if one appears that is neither declared
 * public nor covered here. A cross-user suite that has to be remembered is a cross-user suite
 * that will eventually be forgotten.
 */
@AutoConfigureMockMvc
class CrossUserAccessIT extends PostgresIntegrationTest {

    private static final String ADA_PASSWORD = "correct-horse-battery";
    private static final String GRACE_PASSWORD = "another-good-password";

    /** Endpoints that are meant to be reachable without a token. */
    private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
            "POST /api/v1/auth/register",
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/refresh",
            "POST /api/v1/auth/logout",
            "GET /actuator/health",
            "GET /actuator/health/**");

    /** Endpoints this suite exercises for cross-user isolation below. */
    private static final Set<String> COVERED_ENDPOINTS =
            Set.of(
                    "GET /api/v1/students/me",
                    "PUT /api/v1/students/me",
                    "POST /api/v1/students/me/password",
                    "GET /api/v1/students/me/export",
                    "DELETE /api/v1/students/me",
                    "GET /api/v1/admin/overview");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    // Actuator registers its own RequestMappingHandlerMapping, so this has to be qualified by
    // name or the context has two candidates and injects neither.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private JdbcTemplate jdbc;

    private String adaToken;
    private String graceToken;
    private String adaId;
    private String graceId;
    private Cookie adaCookie;

    @BeforeEach
    void registerTwoUsers() throws Exception {
        jdbc.execute("TRUNCATE TABLE refresh_tokens, users CASCADE");

        MvcResult ada = register("Ada Lovelace", "ada@example.com", ADA_PASSWORD);
        MvcResult grace = register("Grace Hopper", "grace@example.com", GRACE_PASSWORD);

        adaToken = tokenFrom(ada);
        graceToken = tokenFrom(grace);
        adaId = userIdFrom(ada);
        graceId = userIdFrom(grace);
        adaCookie = ada.getResponse().getCookie("placefy_refresh");
    }

    @Test
    @DisplayName("GET /me returns only the token holder")
    void meIsScopedToTheTokenSubject() throws Exception {
        mvc.perform(get("/api/v1/students/me").header(HttpHeaders.AUTHORIZATION, bearer(adaToken)))
                .andExpect(jsonPath("$.id").value(adaId))
                .andExpect(jsonPath("$.email").value("ada@example.com"));

        mvc.perform(get("/api/v1/students/me").header(HttpHeaders.AUTHORIZATION, bearer(graceToken)))
                .andExpect(jsonPath("$.id").value(graceId));
    }

    @Test
    @DisplayName("an export contains the caller's data and nothing of anyone else's")
    void exportIsScopedToTheTokenSubject() throws Exception {
        String body = mvc.perform(get("/api/v1/students/me/export").header(HttpHeaders.AUTHORIZATION, bearer(adaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(adaId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("ada@example.com");
        assertThat(body)
                .as("Grace's identifiers must not appear anywhere in Ada's export")
                .doesNotContain("grace@example.com")
                .doesNotContain(graceId);
    }

    @Test
    @DisplayName("an export carries no credential material")
    void exportContainsNoSecrets() throws Exception {
        String body = mvc.perform(get("/api/v1/students/me/export").header(HttpHeaders.AUTHORIZATION, bearer(adaToken)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String storedHash =
                jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'ada@example.com'", String.class);
        String storedTokenHash =
                jdbc.queryForObject("SELECT token_hash FROM refresh_tokens LIMIT 1", String.class);

        assertThat(body).doesNotContain(storedHash).doesNotContain(storedTokenHash);
        assertThat(body).doesNotContain(adaCookie.getValue());
        assertThat(body).doesNotContain("passwordHash").doesNotContain("tokenHash");
    }

    @Test
    @DisplayName("deleting an account removes only that account")
    void deletionIsScopedToTheTokenSubject() throws Exception {
        mvc.perform(delete("/api/v1/students/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adaToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", ADA_PASSWORD))))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE email = 'ada@example.com'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE email = 'grace@example.com'", Integer.class))
                .isEqualTo(1);

        // Grace's session is untouched.
        mvc.perform(get("/api/v1/students/me").header(HttpHeaders.AUTHORIZATION, bearer(graceToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("deletion takes the account's sessions with it and leaves other users' alone")
    void deletionCascadesOnlyToItsOwnSessions() throws Exception {
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Integer.class);
        assertThat(before).isEqualTo(2);

        mvc.perform(delete("/api/v1/students/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adaToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", ADA_PASSWORD))))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Ada's password does not authorise deleting Grace's account")
    void deletionRejectsAnotherUsersPassword() throws Exception {
        mvc.perform(delete("/api/v1/students/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(graceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", ADA_PASSWORD))))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("a valid token with the wrong password cannot delete")
    void deletionRequiresTheCurrentPassword() throws Exception {
        mvc.perform(delete("/api/v1/students/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adaToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", "not-the-right-password"))))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("a deleted account's token stops working immediately")
    void aDeletedUsersTokenIsDead() throws Exception {
        mvc.perform(delete("/api/v1/students/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adaToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", ADA_PASSWORD))))
                .andExpect(status().isNoContent());

        // The signature still verifies; the account behind it does not exist. That has to be a
        // rejection, not a 500 and not an empty profile.
        mvc.perform(get("/api/v1/students/me").header(HttpHeaders.AUTHORIZATION, bearer(adaToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:unauthenticated"));
    }

    @Test
    @DisplayName("renaming yourself renames nobody else")
    void profileUpdateIsScopedToTheTokenSubject() throws Exception {
        mvc.perform(put("/api/v1/students/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adaToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Ada King"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(adaId))
                .andExpect(jsonPath("$.name").value("Ada King"));

        assertThat(jdbc.queryForObject(
                        "SELECT name FROM users WHERE email = 'grace@example.com'", String.class))
                .isEqualTo("Grace Hopper");
    }

    @Test
    @DisplayName("a password change revokes the caller's sessions and nobody else's")
    void passwordChangeIsScopedToTheTokenSubject() throws Exception {
        mvc.perform(post("/api/v1/students/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adaToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "currentPassword", ADA_PASSWORD, "newPassword", "a-brand-new-passphrase"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(adaId));

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM refresh_tokens t JOIN users u ON u.id = t.user_id "
                                + "WHERE u.email = 'grace@example.com' AND t.revoked_at IS NULL",
                        Integer.class))
                .isEqualTo(1);
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", "grace@example.com", "password", GRACE_PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ada's password cannot change Grace's")
    void passwordChangeRejectsAnotherUsersPassword() throws Exception {
        mvc.perform(post("/api/v1/students/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(graceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "currentPassword", ADA_PASSWORD, "newPassword", "a-brand-new-passphrase"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:invalid-credentials"));
    }

    @Test
    @DisplayName("a student is refused the admin overview with a 403 problem")
    void studentsCannotReachAdminEndpoints() throws Exception {
        mvc.perform(get("/api/v1/admin/overview").header(HttpHeaders.AUTHORIZATION, bearer(adaToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:forbidden"));
    }

    @Test
    @DisplayName("the admin overview is an aggregate and names no student")
    void adminOverviewExposesNoIndividualData() throws Exception {
        String adminToken = promoteAndLogIn("admin@example.com", "an-admin-passphrase");

        String body = mvc.perform(get("/api/v1/admin/overview").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentCount").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .doesNotContain("ada@example.com")
                .doesNotContain("grace@example.com")
                .doesNotContain(adaId)
                .doesNotContain(graceId);
    }

    @Test
    @DisplayName("demoting an admin in the database takes effect before their token expires")
    void adminAccessFollowsTheStoredRole() throws Exception {
        String adminToken = promoteAndLogIn("admin@example.com", "an-admin-passphrase");
        jdbc.update("UPDATE users SET role = 'STUDENT' WHERE email = 'admin@example.com'");

        // The token still says ADMIN, so the URL rule lets it through; the use case refuses.
        mvc.perform(get("/api/v1/admin/overview").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:forbidden"));
    }

    @Test
    @DisplayName("every authenticated endpoint is either covered here or declared public")
    void noEndpointEscapesThisSuite() {
        Set<String> unaccounted = new TreeSet<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
                handlerMapping.getHandlerMethods().entrySet()) {

            RequestMappingInfo info = entry.getKey();
            Set<String> patterns = new LinkedHashSet<>();
            if (info.getPathPatternsCondition() != null) {
                info.getPathPatternsCondition()
                        .getPatterns()
                        .forEach(pattern -> patterns.add(pattern.getPatternString()));
            }

            for (String pattern : patterns) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                for (var method : info.getMethodsCondition().getMethods()) {
                    String endpoint = method.name() + " " + pattern;
                    if (!PUBLIC_ENDPOINTS.contains(endpoint) && !COVERED_ENDPOINTS.contains(endpoint)) {
                        unaccounted.add(endpoint);
                    }
                }
            }
        }

        assertThat(unaccounted)
                .as("New endpoints with no cross-user test. Add a test here and list them in "
                        + "COVERED_ENDPOINTS, or add them to PUBLIC_ENDPOINTS if they are meant to be "
                        + "reachable without a token.")
                .isEmpty();
    }

    private MvcResult register(String name, String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("name", name, "email", email, "password", password))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    /** Registers, promotes the way an operator would (a database statement), then logs in fresh. */
    private String promoteAndLogIn(String email, String password) throws Exception {
        register("Site Admin", email, password);
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email);
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn();
        return tokenFrom(login);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String tokenFrom(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    private String userIdFrom(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString())
                .get("user")
                .get("id")
                .asText();
    }
}
