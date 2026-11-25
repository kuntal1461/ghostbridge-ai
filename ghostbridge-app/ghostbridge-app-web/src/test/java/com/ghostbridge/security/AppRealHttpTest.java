package com.ghostbridge.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Over a real servlet container: session cookie attributes and security headers, which MockMvc does not exercise. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ghostbridge.ai.provider=mock")
@ActiveProfiles("test")
class AppRealHttpTest {
    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @BeforeEach
    void theApplicationsOwnSecurityChainIsInPlaceAndTheServerIsUp() throws Exception {
        // Preconditions: without them a failure below would be ambiguous (no filter chain vs. a wrong response).
        assertThat(context.getBeansOfType(SecurityFilterChain.class)).as("security filter chains").isNotEmpty();
        assertThat(context.containsBean("appAuthenticationCustomizer")).isTrue();
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
    }

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void theSessionCookieIsHttpOnlySecureAndSameSiteStrict() throws Exception {
        HttpResponse<String> response = get("/api/v1/auth/csrf");
        assertThat(response.statusCode()).as("%s %s", response.headers().map(), response.body()).isEqualTo(200);
        List<String> cookies = response.headers().allValues("Set-Cookie");
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie).startsWith("JSESSIONID=").containsIgnoringCase("HttpOnly")
                .containsIgnoringCase("Secure").containsIgnoringCase("SameSite=Strict"));
        assertThat(response.headers().firstValue("Cache-Control")).hasValueSatisfying(value -> assertThat(value).contains("no-store"));
    }

    @Test
    void errorAndApiResponsesCarryTheSharedSecurityHeadersAndNoInternals() throws Exception {
        HttpResponse<String> response = get("/api/v1/ai/usage");
        assertThat(response.statusCode()).as("%s %s", response.headers().map(), response.body()).isEqualTo(401);
        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(response.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(csp -> assertThat(csp).contains("frame-ancestors 'none'"));
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).contains("application/problem+json"));
        assertThat(response.body()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"").doesNotContain("Exception").doesNotContain("at com.");
    }
}
