package com.ghostbridge.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.util.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AiRequestBodyLimitFilterTest {
    private final AiRequestBodyLimitFilter filter = new AiRequestBodyLimitFilter(1024, new ProblemResponseWriter(new ObjectMapper()));

    /** Simulates a chunked request: the container reports no Content-Length. */
    private static MockHttpServletRequest withBody(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ai/generate") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body);
        return request;
    }

    @Test
    void declaredOversizeIsRejectedWith413() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ai/generate");
        request.setContent(new byte[2000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("must not reach the chain"); });
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
    }

    @Test
    void chunkedOversizeIsRejectedOnActualBytes() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(withBody(new byte[1025]), response, (req, res) -> { throw new AssertionError("must not reach the chain"); });
        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void bodyAtTheLimitIsReplayedToTheChain() throws Exception {
        byte[] body = new byte[1024];
        java.util.Arrays.fill(body, (byte) 'a');
        AtomicReference<byte[]> seen = new AtomicReference<>();
        AtomicReference<String> seenText = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            seen.set(req.getInputStream().readAllBytes());
            seenText.set(new String(seen.get(), StandardCharsets.UTF_8));
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(withBody(body), response, chain);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seen.get()).isEqualTo(body);
    }

    @Test
    void replayedRequestSupportsReaderAndStreamState() throws Exception {
        AtomicReference<String> text = new AtomicReference<>();
        AtomicReference<Boolean> finished = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            text.set(req.getReader().readLine());
            finished.set(req.getInputStream().isFinished());
        };
        filter.doFilter(withBody("{\"a\":1}".getBytes(StandardCharsets.UTF_8)), new MockHttpServletResponse(), chain);
        assertThat(text.get()).isEqualTo("{\"a\":1}");
        assertThat(finished.get()).isTrue();
    }

    @Test
    void unknownCharsetNameFallsBackToUtf8() throws Exception {
        MockHttpServletRequest request = withBody("héllo".getBytes(StandardCharsets.UTF_8));
        request.setCharacterEncoding("StandardCharsets");
        AtomicReference<String> text = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> text.set(req.getReader().readLine()));
        assertThat(text.get()).isEqualTo("héllo");
    }
}
