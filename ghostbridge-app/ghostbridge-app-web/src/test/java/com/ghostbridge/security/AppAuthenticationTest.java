package com.ghostbridge.security;

import com.ghostbridge.config.AppSecurityProperties;
import com.ghostbridge.constants.GhostBridgeAppLiterals;
import com.ghostbridge.restcontroller.AppAiController;
import com.ghostbridge.restcontroller.AiRestController;
import com.ghostbridge.support.AbstractAppIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-in, session, CSRF, authorization and brute-force defence through the real security chain. */
@SpringBootTest(properties = {"ghostbridge.ai.provider=mock", "ghostbridge.app.security.login-attempts.max-failures=3",
        "ghostbridge.app.security.login-attempts.max-failures-per-address=1000"})
class AppAuthenticationTest extends AbstractAppIntegrationTest {
    private static final String VALID_GENERATE = "{\"promptName\":\"generic-structured\",\"variables\":{\"text\":\"hello world\"}}";
    @Autowired
    private ApplicationContext context;

    private org.springframework.test.web.servlet.ResultActions login(Browser browser, String user, String password) throws Exception {
        return login(browser, user, password, "198.51.100.1");
    }

    private org.springframework.test.web.servlet.ResultActions login(Browser browser, String user, String password, String address) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                }).session(browser.session()).header(browser.csrfHeader(), browser.csrfToken())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("username", user).param("password", password));
    }

    @Test
    void theAppControllerOwnsTheAiPathsAndAiWebsOwnControllerIsAbsent() {
        assertThat(context.getBeansOfType(AppAiController.class)).hasSize(1);
        assertThat(context.getBeansOfType(AiRestController.class)).isEmpty();
    }

    @Test
    void healthIsPublicButNothingElseOperationalIs() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void everyAiEndpointRejectsAnonymousCallers() throws Exception {
        Browser anonymous = anonymousBrowser(); // a valid CSRF token, so the refusal is about authentication, not CSRF
        mvc.perform(post("/api/v1/ai/generate").session(anonymous.session()).header(anonymous.csrfHeader(), anonymous.csrfToken())
                .contentType("application/json").content(VALID_GENERATE)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/ai/generate/structured").session(anonymous.session()).header(anonymous.csrfHeader(), anonymous.csrfToken())
                .contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/ai/generate").contentType("application/json").content(VALID_GENERATE)).andExpect(status().isForbidden()); // no CSRF
        mvc.perform(get("/api/v1/ai/provider/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ai/usage")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void theFullSessionFlowSignsInCallsTheAiApiAndSignsOut() throws Exception {
        Browser browser = signIn("alice");
        mvc.perform(get("/api/v1/auth/me").session(browser.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("AI_USER")))
                .andExpect(content().string(not(containsString("bcrypt")))).andExpect(header().string("Cache-Control", containsString("no-store")));
        mvc.perform(post("/api/v1/ai/generate").session(browser.session()).header(browser.csrfHeader(), browser.csrfToken())
                        .contentType("application/json").content(VALID_GENERATE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.model").value("mock-model")).andExpect(jsonPath("$.requestId").isNotEmpty());
        mvc.perform(get("/api/v1/ai/usage").session(browser.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestCount").value(1)).andExpect(jsonPath("$.successCount").value(1));
        mvc.perform(get("/api/v1/ai/provider/status").session(browser.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.provider").value("mock")).andExpect(jsonPath("$.status").value("AVAILABLE"));

        mvc.perform(post("/api/v1/auth/logout").session(browser.session()).header(browser.csrfHeader(), browser.csrfToken()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").session(browser.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void loginRequiresTheCsrfTokenAndAStateChangingCallWithoutItIsRefusedEvenWhenSignedIn() throws Exception {
        Browser anonymous = anonymousBrowser();
        mvc.perform(post("/api/v1/auth/login").session(anonymous.session()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "alice").param("password", PASSWORD)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/login").session(anonymous.session()).header(anonymous.csrfHeader(), "forged-token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("username", "alice").param("password", PASSWORD))
                .andExpect(status().isForbidden());

        Browser browser = signIn("alice");
        mvc.perform(post("/api/v1/ai/generate").session(browser.session()).contentType("application/json").content(VALID_GENERATE))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").session(browser.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me").session(browser.session())).andExpect(status().isOk());
    }

    @Test
    void aSignedInUserWithoutTheAiRoleIsForbiddenFromEveryAiEndpoint() throws Exception {
        Browser browser = signIn("bob");
        mvc.perform(get("/api/v1/auth/me").session(browser.session())).andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("VIEWER"));
        mvc.perform(post("/api/v1/ai/generate").session(browser.session()).header(browser.csrfHeader(), browser.csrfToken())
                .contentType("application/json").content(VALID_GENERATE)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/ai/usage").session(browser.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ai/provider/status").session(browser.session())).andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameGenericAnswer() throws Exception {
        Browser one = anonymousBrowser();
        String wrongPassword = login(one, "alice", "not-the-password").andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        Browser two = anonymousBrowser();
        String unknownUser = login(two, "nobody-here", "not-the-password").andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(wrongPassword).get("detail").asText()).isEqualTo("Invalid credentials")
                .isEqualTo(json.readTree(unknownUser).get("detail").asText());
        assertThat(wrongPassword).doesNotContain("alice").doesNotContain("password");
    }

    @Test
    void repeatedFailuresLockTheUserNameOutEvenForTheCorrectPasswordUntilTheWindowEnds() throws Exception {
        Browser browser = anonymousBrowser();
        for (int i = 0; i < 3; i++) {
            login(browser, "locked-user", "wrong-" + i).andExpect(status().isUnauthorized());
        }
        login(browser, "locked-user", PASSWORD).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void aLockoutBelongsToTheClientAddressSoAStrangerCannotLockAKnownAccountForEveryone() throws Exception {
        Browser attacker = anonymousBrowser();
        for (int i = 0; i < 3; i++) {
            login(attacker, "alice", "wrong-" + i, "203.0.113.7").andExpect(status().isUnauthorized());
        }
        login(attacker, "alice", PASSWORD, "203.0.113.7").andExpect(status().isTooManyRequests());
        Browser owner = anonymousBrowser();
        login(owner, "alice", PASSWORD, "203.0.113.99").andExpect(status().isOk());
    }

    @Test
    void aSignedOutSessionExpiresQuicklyAndASignedInOneGetsTheNormalLifetime() throws Exception {
        Browser anonymous = anonymousBrowser();
        assertThat(anonymous.session().getMaxInactiveInterval()).isEqualTo(GhostBridgeAppLiterals.ANONYMOUS_SESSION_SECONDS);
        Browser signedIn = signIn("alice");
        assertThat(signedIn.session().getMaxInactiveInterval()).isGreaterThan(GhostBridgeAppLiterals.ANONYMOUS_SESSION_SECONDS);
    }

    @Test
    void aCookielessCsrfRequestCreatesASessionWithTheShortAnonymousLifetime() throws Exception {
        jakarta.servlet.http.HttpSession created = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn()
                .getRequest().getSession(false);
        assertThat(created).isNotNull();
        assertThat(created.getMaxInactiveInterval()).isEqualTo(GhostBridgeAppLiterals.ANONYMOUS_SESSION_SECONDS);
    }

    @Test
    void signingInChangesTheSessionIdentifier() throws Exception {
        Browser anonymous = anonymousBrowser();
        String before = anonymous.session().getId();
        login(anonymous, "alice", PASSWORD, "198.51.100.77").andExpect(status().isOk());
        assertThat(anonymous.session().getId()).isNotEqualTo(before);
    }

    @Test
    void unknownFieldsAndBadBodiesAreStillRejectedByTheAiContract() throws Exception {
        Browser browser = signIn("alice");
        mvc.perform(post("/api/v1/ai/generate").session(browser.session()).header(browser.csrfHeader(), browser.csrfToken())
                .contentType("application/json").content("{\"promptName\":\"generic-structured\",\"requesterId\":\"someone-else\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/ai/generate/structured").session(browser.session()).header(browser.csrfHeader(), browser.csrfToken())
                .contentType("application/json").content("{\"schemaName\":\"not-approved\",\"variables\":{\"text\":\"x\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theConfiguredUsersNeverAppearInTheirOwnToString() {
        AppSecurityProperties.User user = context.getBean(AppSecurityProperties.class).users().get(0);
        assertThat(user.toString()).contains("alice").doesNotContain("bcrypt").doesNotContain(user.passwordHash());
    }
}
