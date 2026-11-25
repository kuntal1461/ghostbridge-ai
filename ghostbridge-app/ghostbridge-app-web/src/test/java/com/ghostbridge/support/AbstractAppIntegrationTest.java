package com.ghostbridge.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full application context (real security chain, real AI core) with two synthetic accounts: {@code alice} (role AI_USER)
 * and {@code bob} (role VIEWER). Passwords are synthetic test values and exist only as bcrypt hashes in the context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractAppIntegrationTest {
    public static final String PASSWORD = "test-pw-12345";
    private static final String HASH = "{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);

    @Autowired
    protected MockMvc mvc;
    protected final ObjectMapper json = new ObjectMapper();

    /** A signed-in (or anonymous) browser: its session plus the CSRF header name and token to send. */
    public record Browser(MockHttpSession session, String csrfHeader, String csrfToken) {
    }

    @DynamicPropertySource
    static void users(DynamicPropertyRegistry registry) {
        registry.add("ghostbridge.app.security.users[0].username", () -> "alice");
        registry.add("ghostbridge.app.security.users[0].password-hash", () -> HASH);
        registry.add("ghostbridge.app.security.users[0].roles[0]", () -> "AI_USER");
        registry.add("ghostbridge.app.security.users[1].username", () -> "bob");
        registry.add("ghostbridge.app.security.users[1].password-hash", () -> HASH);
        registry.add("ghostbridge.app.security.users[1].roles[0]", () -> "VIEWER");
    }

    /** Starts a session and reads the CSRF token, exactly as the frontend does. */
    protected Browser anonymousBrowser() throws Exception {
        MockHttpSession session = new MockHttpSession();
        return refreshed(session);
    }

    protected Browser refreshed(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return new Browser(session, body.get("headerName").asText(), body.get("token").asText());
    }

    private static final AtomicInteger ADDRESSES = new AtomicInteger();

    /** A distinct client address per call, so the per-address throttle of one test never affects another. */
    protected static RequestPostProcessor freshAddress() {
        String address = "10.20." + (ADDRESSES.incrementAndGet() / 250) + "." + (ADDRESSES.get() % 250 + 1);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    /** Signs in and returns the browser with the CSRF token refreshed after login, as the frontend does. */
    protected Browser signIn(String username) throws Exception {
        Browser anonymous = anonymousBrowser();
        mvc.perform(post("/api/v1/auth/login").with(freshAddress()).session(anonymous.session()).header(anonymous.csrfHeader(), anonymous.csrfToken())
                .contentType("application/x-www-form-urlencoded").param("username", username).param("password", PASSWORD))
                .andExpect(status().isOk());
        return refreshed(anonymous.session());
    }
}
