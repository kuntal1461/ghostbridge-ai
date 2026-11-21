package com.ghostbridge.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private String run(String incomingHeader, AtomicReference<String> mdcSeen, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (incomingHeader != null) {
            request.addHeader(CorrelationIdFilter.HEADER, incomingHeader);
        }
        FilterChain chain = (req, res) -> mdcSeen.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        filter.doFilter(request, response, chain);
        return response.getHeader(CorrelationIdFilter.HEADER);
    }

    @Test
    void reusesWellFormedIncomingId() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        String id = run("abc-12345.XYZ", seen, new MockHttpServletResponse());
        assertThat(id).isEqualTo("abc-12345.XYZ");
        assertThat(seen.get()).isEqualTo("abc-12345.XYZ");
    }

    @Test
    void generatesIdWhenMissing() throws Exception {
        String id = run(null, new AtomicReference<>(), new MockHttpServletResponse());
        assertThat(id).hasSize(36);
    }

    @Test
    void replacesMalformedOrOversizedIdToPreventLogInjection() throws Exception {
        assertThat(run("bad id\r\nforged=1", new AtomicReference<>(), new MockHttpServletResponse())).hasSize(36);
        assertThat(run("x".repeat(100), new AtomicReference<>(), new MockHttpServletResponse())).hasSize(36);
        assertThat(run("short", new AtomicReference<>(), new MockHttpServletResponse())).hasSize(36);
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        run("abc-12345", new AtomicReference<>(), new MockHttpServletResponse());
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
