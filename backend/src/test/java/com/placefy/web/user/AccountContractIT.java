package com.placefy.web.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.placefy.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The committed contract for PUT /students/me, POST /students/me/password and
 * GET /admin/overview, against a real PostgreSQL. Must agree with docs/api/openapi.yaml.
 */
@AutoConfigureMockMvc
class AccountContractIT extends PostgresIntegrationTest {

    private static final String COOKIE_NAME = "placefy_refresh";
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;
    private Cookie cookie;

    @BeforeEach
    void registerAda() throws Exception {
        jdbc.execute("TRUNCATE TABLE refresh_tokens, users CASCADE");
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("name", "Ada Lovelace", "email", "ada@example.com", "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        token = json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        cookie = result.getResponse().getCookie(COOKIE_NAME);
    }

    // ---------------------------------------------------------------- profile

    @Test
    void updateReturnsTheNormalisedProfile() throws Exception {
        mvc.perform(put("/api/v1/students/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Ada   King \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada King"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.role").value("STUDENT"));

        mvc.perform(get("/api/v1/students/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.name").value("Ada King"));
    }

    @Test
    void updateRejectsAnInvalidNameWithTheFieldNamed() throws Exception {
        mvc.perform(put("/api/v1/students/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:validation-failed"))
                .andExpect(jsonPath("$.field").value("name"));
    }

    @Test
    @DisplayName("the email is not editable: an email in the body is ignored")
    void updateIgnoresAnEmailInTheBody() throws Exception {
        mvc.perform(put("/api/v1/students/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada King\",\"email\":\"attacker@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"));
    }

    @Test
    void updateRequiresAToken() throws Exception {
        mvc.perform(put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X Y\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:unauthenticated"));
    }

    // ---------------------------------------------------------------- password

    @Test
    @DisplayName("a password change returns a new session and kills the old refresh cookie")
    void passwordChangeRotatesTheSession() throws Exception {
        MvcResult changed = mvc.perform(changePassword(PASSWORD, "a-brand-new-passphrase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("ada@example.com"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("Path=/api/v1/auth;")))
                .andReturn();

        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(changed.getResponse().getCookie(COOKIE_NAME)))
                .andExpect(status().isOk());

        mvc.perform(login(PASSWORD)).andExpect(status().isUnauthorized());
        mvc.perform(login("a-brand-new-passphrase")).andExpect(status().isOk());
    }

    @Test
    void passwordChangeRejectsTheWrongCurrentPassword() throws Exception {
        mvc.perform(changePassword("not-my-password-at-all", "a-brand-new-passphrase"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:invalid-credentials"));

        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    void passwordChangeEnforcesThePolicyOnTheNewPassword() throws Exception {
        mvc.perform(changePassword(PASSWORD, "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("password"));
    }

    @Test
    void passwordChangeRequiresAToken() throws Exception {
        mvc.perform(post("/api/v1/students/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("currentPassword", PASSWORD, "newPassword", "a-brand-new-passphrase"))))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- admin

    @Test
    void adminOverviewRequiresAToken() throws Exception {
        mvc.perform(get("/api/v1/admin/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:unauthenticated"));
    }

    @Test
    void adminOverviewRefusesAStudentWith403Problem() throws Exception {
        mvc.perform(get("/api/v1/admin/overview").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("an admin sees the count of students, excluding administrators")
    void adminOverviewCountsStudents() throws Exception {
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = 'ada@example.com'");
        String adminToken = json.readTree(mvc.perform(login(PASSWORD))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("accessToken")
                .asText();

        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "name", "Grace Hopper", "email", "grace@example.com", "password", "another-good-password"))));

        String body = mvc.perform(get("/api/v1/admin/overview").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentCount").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(json.readTree(body).size()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- helpers

    private org.springframework.test.web.servlet.RequestBuilder changePassword(String current, String next)
            throws Exception {
        return post("/api/v1/students/me/password")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", current, "newPassword", next)));
    }

    private org.springframework.test.web.servlet.RequestBuilder login(String password) throws Exception {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "ada@example.com", "password", password)));
    }

    private static String bearer(String value) {
        return "Bearer " + value;
    }
}
