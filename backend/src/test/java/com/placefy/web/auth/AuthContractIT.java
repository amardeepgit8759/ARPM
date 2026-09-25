package com.placefy.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placefy.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
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
 * The committed contract for /api/v1/auth and /api/v1/students/me, exercised end to end against a real
 * PostgreSQL. What this file asserts and what docs/api/openapi.yaml documents must agree.
 */
@AutoConfigureMockMvc
class AuthContractIT extends PostgresIntegrationTest {

    private static final String COOKIE_NAME = "placefy_refresh";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearTables() {
        jdbc.execute("TRUNCATE TABLE refresh_tokens, users CASCADE");
    }

    // ---------------------------------------------------------------- register

    @Test
    void registerReturns201WithASessionAndAProfile() throws Exception {
        mvc.perform(registration("Ada Lovelace", "ada@example.com", "correct-horse-battery"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.accessTokenExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.id").isNotEmpty())
                .andExpect(jsonPath("$.user.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.user.email").value("ada@example.com"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.user.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("the refresh token is only ever in the cookie, never in the response body")
    void registerNeverPutsTheRefreshTokenInTheBody() throws Exception {
        MvcResult result = mvc.perform(registration("Ada Lovelace", "ada@example.com", "correct-horse-battery"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String cookieValue = result.getResponse().getCookie(COOKIE_NAME).getValue();
        assertThat(cookieValue).isNotBlank();
        assertThat(body).doesNotContain(cookieValue);
    }

    @Test
    @DisplayName("the cookie is httpOnly, Secure, SameSite=Strict and scoped to the auth endpoints")
    void registerSetsAHardenedCookie() throws Exception {
        mvc.perform(registration("Ada Lovelace", "ada@example.com", "correct-horse-battery"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString(COOKIE_NAME + "=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("SameSite=Strict")))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE, Matchers.containsString("Path=/api/v1/auth;")));
    }

    @Test
    void registerRejectsADuplicateEmailWith409Problem() throws Exception {
        mvc.perform(registration("Ada Lovelace", "ada@example.com", "correct-horse-battery"));

        mvc.perform(registration("Ada Byron", "ADA@example.com", "another-good-password"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:email-already-registered"))
                .andExpect(jsonPath("$.title").value("Email already registered"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/register"));
    }

    @Test
    @DisplayName("a validation failure names the offending field")
    void registerRejectsAShortPasswordWith400Problem() throws Exception {
        mvc.perform(registration("Ada Lovelace", "ada@example.com", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:validation-failed"))
                .andExpect(jsonPath("$.field").value("password"));
    }

    @Test
    void registerRejectsAMalformedEmailWith400Problem() throws Exception {
        mvc.perform(registration("Ada Lovelace", "not-an-email", "correct-horse-battery"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    void registerRejectsUnreadableJsonWith400Problem() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:malformed-request"));
    }

    // ---------------------------------------------------------------- login

    @Test
    void loginReturns200ForCorrectCredentials() throws Exception {
        registerAda();

        mvc.perform(login("ada@example.com", "correct-horse-battery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("ada@example.com"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("a wrong password and an unknown address are indistinguishable in the response")
    void loginFailuresDoNotRevealWhetherAnAccountExists() throws Exception {
        registerAda();

        String wrongPassword = mvc.perform(login("ada@example.com", "wrong-password-entirely"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String unknownAccount = mvc.perform(login("nobody@example.com", "wrong-password-entirely"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode a = json.readTree(wrongPassword);
        JsonNode b = json.readTree(unknownAccount);
        assertThat(a.get("type")).isEqualTo(b.get("type"));
        assertThat(a.get("title")).isEqualTo(b.get("title"));
        assertThat(a.get("detail")).isEqualTo(b.get("detail"));
        assertThat(a.get("type").asText()).isEqualTo("urn:placefy:problem:invalid-credentials");
    }

    // ---------------------------------------------------------------- me

    @Test
    void meRequiresAToken() throws Exception {
        mvc.perform(get("/api/v1/students/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:unauthenticated"));
    }

    @Test
    void meRejectsATamperedToken() throws Exception {
        String token = accessTokenFrom(registerAda());
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "B" : "A");

        mvc.perform(get("/api/v1/students/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:unauthenticated"));
    }

    @Test
    void meReturnsTheAuthenticatedProfile() throws Exception {
        String token = accessTokenFrom(registerAda());

        mvc.perform(get("/api/v1/students/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    @DisplayName("a token returns its own user's data and never another user's")
    void meIsScopedToTheTokenSubject() throws Exception {
        MvcResult ada = registerAda();
        MvcResult grace = mvc.perform(registration("Grace Hopper", "grace@example.com", "another-good-password"))
                .andReturn();

        mvc.perform(get("/api/v1/students/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenFrom(ada)))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.id").value(userIdFrom(ada)));

        mvc.perform(get("/api/v1/students/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenFrom(grace)))
                .andExpect(jsonPath("$.email").value("grace@example.com"))
                .andExpect(jsonPath("$.id").value(userIdFrom(grace)));

        assertThat(userIdFrom(ada)).isNotEqualTo(userIdFrom(grace));
    }

    @Test
    @DisplayName("there is no endpoint that accepts a user id, so one cannot be substituted")
    void thereIsNoWayToAskForAnotherUserById() throws Exception {
        MvcResult ada = registerAda();
        MvcResult grace = mvc.perform(registration("Grace Hopper", "grace@example.com", "another-good-password"))
                .andReturn();

        mvc.perform(get("/api/v1/students/" + userIdFrom(grace))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenFrom(ada)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/users/" + userIdFrom(grace))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenFrom(ada)))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- refresh

    @Test
    void refreshRotatesTheCookieAndReturnsANewAccessToken() throws Exception {
        MvcResult registered = registerAda();
        Cookie original = registered.getResponse().getCookie(COOKIE_NAME);

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(original))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("ada@example.com"))
                .andReturn();

        Cookie rotated = refreshed.getResponse().getCookie(COOKIE_NAME);
        assertThat(rotated.getValue()).isNotBlank().isNotEqualTo(original.getValue());
    }

    @Test
    void refreshWithoutACookieIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:invalid-refresh-token"));
    }

    @Test
    @DisplayName("replaying a rotated cookie is rejected and ends the whole session chain")
    void refreshDetectsReplayAndRevokesTheFamily() throws Exception {
        MvcResult registered = registerAda();
        Cookie original = registered.getResponse().getCookie(COOKIE_NAME);

        MvcResult firstRotation = mvc.perform(post("/api/v1/auth/refresh").cookie(original))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotated = firstRotation.getResponse().getCookie(COOKIE_NAME);

        mvc.perform(post("/api/v1/auth/refresh").cookie(original))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:invalid-refresh-token"));

        // The replacement the honest client was holding is dead too: that is the point.
        mvc.perform(post("/api/v1/auth/refresh").cookie(rotated)).andExpect(status().isUnauthorized());

        Integer live = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class);
        assertThat(live).isZero();
    }

    @Test
    @DisplayName("a rejected refresh clears the dead cookie from the browser")
    void refreshFailureDoesNotLeaveAStaleCookieBehind() throws Exception {
        MvcResult registered = registerAda();
        Cookie original = registered.getResponse().getCookie(COOKIE_NAME);
        mvc.perform(post("/api/v1/auth/refresh").cookie(original));

        mvc.perform(post("/api/v1/auth/refresh").cookie(original))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString(COOKIE_NAME + "=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("Max-Age=0")));
    }

    @Test
    @DisplayName("one user's cookie never refreshes into another user's session")
    void refreshIsScopedToTheCookiesOwner() throws Exception {
        MvcResult ada = registerAda();
        MvcResult grace = mvc.perform(registration("Grace Hopper", "grace@example.com", "another-good-password"))
                .andReturn();

        mvc.perform(post("/api/v1/auth/refresh").cookie(ada.getResponse().getCookie(COOKIE_NAME)))
                .andExpect(jsonPath("$.user.email").value("ada@example.com"));

        mvc.perform(post("/api/v1/auth/refresh").cookie(grace.getResponse().getCookie(COOKIE_NAME)))
                .andExpect(jsonPath("$.user.email").value("grace@example.com"));
    }

    // ---------------------------------------------------------------- logout

    @Test
    @DisplayName("logout answers 204, clears the cookie, and the cookie can no longer refresh")
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        Cookie cookie = registerAda().getResponse().getCookie(COOKIE_NAME);

        mvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString(COOKIE_NAME + "=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("Max-Age=0")));

        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:placefy:problem:invalid-refresh-token"));

        Integer live = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class);
        assertThat(live).isZero();
    }

    @Test
    @DisplayName("logout needs no access token, so an expired one cannot trap a user signed in")
    void logoutWorksWithoutAnAccessToken() throws Exception {
        Cookie cookie = registerAda().getResponse().getCookie(COOKIE_NAME);

        mvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("logout with no cookie, or twice, is still a quiet 204")
    void logoutIsIdempotent() throws Exception {
        Cookie cookie = registerAda().getResponse().getCookie(COOKIE_NAME);

        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE_NAME, "never-issued")))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("one user's logout leaves another user's session alive")
    void logoutIsScopedToTheCookiesOwner() throws Exception {
        MvcResult ada = registerAda();
        MvcResult grace = mvc.perform(registration("Grace Hopper", "grace@example.com", "another-good-password"))
                .andReturn();

        mvc.perform(post("/api/v1/auth/logout").cookie(ada.getResponse().getCookie(COOKIE_NAME)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh").cookie(grace.getResponse().getCookie(COOKIE_NAME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("grace@example.com"));
    }

    // ---------------------------------------------------------------- helpers

    private MvcResult registerAda() throws Exception {
        return mvc.perform(registration("Ada Lovelace", "ada@example.com", "correct-horse-battery"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private org.springframework.test.web.servlet.RequestBuilder registration(
            String name, String email, String password) throws Exception {
        return post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthRequests.RegisterRequest(name, email, password)));
    }

    private org.springframework.test.web.servlet.RequestBuilder login(String email, String password)
            throws Exception {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthRequests.LoginRequest(email, password)));
    }

    private String accessTokenFrom(MvcResult result) throws Exception {
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
