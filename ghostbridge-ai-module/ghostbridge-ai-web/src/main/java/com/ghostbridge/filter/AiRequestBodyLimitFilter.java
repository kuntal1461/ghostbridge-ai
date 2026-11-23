package com.ghostbridge.filter;

import com.ghostbridge.exception.ErrorCode;
import com.ghostbridge.util.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Enforces the AI API body limit on the bytes actually received (the shared filter only checks a declared
 * Content-Length, so chunked bodies would pass). The body is read up to {@code maxBytes + 1}; anything larger is
 * rejected with 413 before parsing. Accepted bodies are replayed to the rest of the chain from memory.
 */
public class AiRequestBodyLimitFilter extends OncePerRequestFilter {
    private final int maxBytes;
    private final ProblemResponseWriter writer;

    public AiRequestBodyLimitFilter(int maxBytes, ProblemResponseWriter writer) {
        this.maxBytes = maxBytes;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            reject(response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            reject(response);
            return;
        }
        chain.doFilter(new ReplayableRequest(request, body), response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        writer.write(response, HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE, "Request body is too large");
    }

    /** Serves the already-read, size-checked body again. */
    private static final class ReplayableRequest extends HttpServletRequestWrapper {
        private final ServletInputStream stream;

        private ReplayableRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.stream = streamOf(new ByteArrayInputStream(body));
        }

        @Override
        public ServletInputStream getInputStream() {
            return stream;
        }

        private static ServletInputStream streamOf(ByteArrayInputStream source) {
            return new ServletInputStream() {
                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return source.read(buffer, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Asynchronous reads are not supported");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(stream, requestCharset()));
        }

        private Charset requestCharset() {
            String encoding = getCharacterEncoding();
            try {
                return encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            } catch (IllegalArgumentException e) { // unknown or illegal charset name sent by the client: fall back to UTF-8
                return StandardCharsets.UTF_8;
            }
        }
    }
}
