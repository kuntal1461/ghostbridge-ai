package com.ghostbridge.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.config.AiProperties;
import com.ghostbridge.config.AiSecret;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.dto.AiProviderCall;
import com.ghostbridge.dto.AiProviderResult;
import com.ghostbridge.dto.AiProviderStatus;
import com.ghostbridge.dto.AiUsage;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import com.ghostbridge.service.AiProvider;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Ollama adapter (OpenAI-compatible chat completions) for Ollama Cloud or a self-hosted Ollama, as selected by
 * {@code ghostbridge.ai.ollama.local-enabled}. In local mode no credential is required, read or sent. Security properties
 * of the credentialed (cloud) mode:
 * <ul>
 *   <li>The credential is an {@link AiSecret}, read only to build the {@code Authorization} header of a request to the
 *       validated endpoint; it is never logged, put in a URL, a message, a cause or telemetry. A credential that is not
 *       plain printable ASCII is refused at construction, because the JDK would otherwise quote the whole header value
 *       in its own error message.</li>
 *   <li>Redirects are never followed, so the credential cannot be forwarded to another host; TLS validation is the
 *       JDK default (no custom trust manager or hostname verifier).</li>
 *   <li>The serialized request is limited in UTF-8 bytes and the response is read incrementally with a hard byte limit
 *       and an overall deadline; error-status bodies are discarded unread.</li>
 *   <li>Failures become {@link AiException}s with generic messages and deliberately <em>no cause</em>: JDK transport
 *       and request-building exceptions can carry request details, so they are not attached (an intentional, documented
 *       exception to the general "keep the cause" convention for credential-bearing adapters).</li>
 *   <li>Startup is refused while a JVM debug switch that prints request headers is set at launch
 *       ({@code jdk.httpclient.HttpClient.log} with {@code headers}, {@code content}, {@code trace} or {@code all};
 *       {@code javax.net.debug} with {@code plaintext} or {@code all}). This is best effort; see the check's Javadoc.</li>
 *   <li>Retrying is left to the coordinator's bounded retry policy. Note that a retried generation request can be billed
 *       more than once by the provider.</li>
 * </ul>
 * No provider or HTTP type is exposed through a public contract. The client is released by {@link #close()} (Spring
 * calls it when the context closes).
 */
public class OllamaAiProvider implements AiProvider, AutoCloseable {
    private static final String STATUS_DETAIL_CLOUD = "Ollama Cloud (credential configured)";
    private static final String STATUS_DETAIL_LOCAL = "Ollama Local (no credential)";
    private static final String MSG_TIMEOUT = "AI provider did not respond in time";
    private static final String MSG_UNEXPECTED_RESPONSE = "AI provider returned an unexpected response";
    private static final Set<String> LOOPBACK_HOSTS = Set.of(GhostBridgeAiLiterals.LOOPBACK_ADDRESS,
            GhostBridgeAiLiterals.LOOPBACK_ADDRESS_V6, GhostBridgeAiLiterals.LOCALHOST);

    private final URI endpoint;
    private final AiSecret apiKey;
    private final boolean sendCredential;
    private final int maxRequestBytes;
    private final int maxResponseBytes;
    private final ObjectMapper mapper;
    private final HttpClient client;

    /** Production constructor: the endpoint comes from validated configuration only (cloud: https and allow-listed host; local: loopback, private or allow-listed host). */
    public OllamaAiProvider(AiProperties.Ollama settings, ObjectMapper mapper) {
        this(settings.chatCompletionsUri(), settings.credentialRequired() ? settings.apiKey() : AiSecret.empty(),
                settings.credentialRequired(), settings.connectTimeout(), settings.maxRequestBytes(), settings.maxResponseBytes(),
                mapper, false);
    }

    /**
     * Test seam. The package is shared by several modules, so package-private visibility is not a barrier on its own:
     * this constructor therefore accepts loopback endpoints only, so it cannot be used to send the credential to a
     * remote host while skipping the https and allow-list validation.
     */
    OllamaAiProvider(URI endpoint, AiSecret apiKey, Duration connectTimeout, int maxRequestBytes, int maxResponseBytes,
                     ObjectMapper mapper) {
        this(endpoint, apiKey, true, connectTimeout, maxRequestBytes, maxResponseBytes, mapper, true);
    }

    private OllamaAiProvider(URI endpoint, AiSecret apiKey, boolean sendCredential, Duration connectTimeout,
                             int maxRequestBytes, int maxResponseBytes, ObjectMapper mapper, boolean loopbackOnly) {
        if (sendCredential) {
            requireUsableCredential(apiKey);
            requireNoHeaderLogging();
        }
        if (loopbackOnly && !LOOPBACK_HOSTS.contains(String.valueOf(endpoint.getHost()).toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("The test constructor accepts loopback endpoints only");
        }
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.sendCredential = sendCredential;
        this.maxRequestBytes = maxRequestBytes;
        this.maxResponseBytes = maxResponseBytes;
        this.mapper = mapper;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1);
        if (!sendCredential) {
            builder.proxy(ProxySelector.of(null)); // local prompts must not be routed through a JVM-wide proxy
        }
        this.client = builder.build();
    }

    private static void requireUsableCredential(AiSecret apiKey) {
        if (apiKey == null || !apiKey.isPresent()) {
            throw new IllegalStateException("ghostbridge.ai.ollama.cloud.api-key is required for Ollama Cloud");
        }
        if (!apiKey.isHeaderSafe()) {
            throw new IllegalStateException("ghostbridge.ai.ollama.cloud.api-key must be plain printable ASCII without spaces or line breaks");
        }
    }

    /**
     * Best effort, launch-time only: the JDK reads these settings once, and {@code $JAVA_HOME/conf/net.properties} is not
     * visible here. Operators must keep both debug switches unset wherever the credential is configured.
     */
    private static void requireNoHeaderLogging() {
        requireModeAbsent(GhostBridgeAiLiterals.JDK_HTTP_CLIENT_LOG_PROPERTY, GhostBridgeAiLiterals.JDK_HTTP_LOG_HEADER_MODES);
        requireModeAbsent(GhostBridgeAiLiterals.JAVAX_NET_DEBUG_PROPERTY, GhostBridgeAiLiterals.JAVAX_NET_DEBUG_PLAINTEXT_MODES);
    }

    private static void requireModeAbsent(String property, Pattern unsafeModes) {
        String mode = System.getProperty(property);
        if (mode != null && unsafeModes.matcher(mode).matches()) {
            throw new IllegalStateException("The JVM debug setting '" + property + "' would print request headers "
                    + "(including the Authorization header); remove it where the Ollama credential is configured");
        }
    }

    @Override
    public String name() {
        return GhostBridgeAiLiterals.PROVIDER_OLLAMA;
    }

    @Override
    public AiProviderStatus status() {
        return new AiProviderStatus(true, sendCredential ? STATUS_DETAIL_CLOUD : STATUS_DETAIL_LOCAL);
    }

    @Override
    public AiProviderResult invoke(AiProviderCall call) {
        byte[] body = serialize(call);
        HttpResponse<byte[]> response = send(buildRequest(body, call.timeout()), call.timeout());
        requireSuccess(response.statusCode());
        OllamaChatResponseVO parsed = OllamaChatResponseVO.from(parse(response.body()));
        return new AiProviderResult(parsed.text(), new AiUsage(parsed.inputTokens(), parsed.outputTokens(), null));
    }

    /** Releases the HTTP client's connections and threads. */
    @Override
    public void close() {
        client.shutdownNow();
    }

    private byte[] serialize(AiProviderCall call) {
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(OllamaChatRequestVO.from(call));
        } catch (JsonProcessingException e) {
            throw new AiException(AiErrorReason.PROVIDER_FAILURE, "AI request could not be prepared");
        }
        if (body.length > maxRequestBytes) {
            throw new AiException(AiErrorReason.INPUT_TOO_LARGE, "AI request exceeds the configured maximum size");
        }
        return body;
    }

    /** A failure here is mapped without its cause: the JDK message of a rejected header quotes the header value. */
    private HttpRequest buildRequest(byte[] body, Duration timeout) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header(GhostBridgeAiLiterals.HEADER_CONTENT_TYPE, GhostBridgeAiLiterals.CONTENT_TYPE_JSON)
                    .header(GhostBridgeAiLiterals.HEADER_ACCEPT, GhostBridgeAiLiterals.CONTENT_TYPE_JSON)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            if (sendCredential) {
                builder.header(GhostBridgeAiLiterals.HEADER_AUTHORIZATION, GhostBridgeAiLiterals.BEARER_PREFIX + apiKey.reveal());
            }
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new AiException(AiErrorReason.PROVIDER_FAILURE, "AI request could not be prepared");
        }
    }

    private HttpResponse<byte[]> send(HttpRequest request, Duration deadline) {
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request, this::bodyHandler);
        try {
            return future.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new AiException(AiErrorReason.PROVIDER_TIMEOUT, MSG_TIMEOUT);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AiException(AiErrorReason.INTERRUPTED, "AI request was interrupted");
        } catch (ExecutionException e) {
            throw translate(e);
        }
    }

    /** Successful responses are read under the byte limit; every other body is discarded unread. */
    private HttpResponse.BodySubscriber<byte[]> bodyHandler(HttpResponse.ResponseInfo info) {
        if (isSuccess(info.statusCode())) {
            long declared = info.headers().firstValueAsLong(GhostBridgeAiLiterals.HEADER_CONTENT_LENGTH).orElse(-1L);
            return new OllamaBoundedBodySubscriber(maxResponseBytes, declared);
        }
        return HttpResponse.BodySubscribers.replacing(new byte[0]);
    }

    /** Messages are fixed; JDK exception text and causes are intentionally not propagated. */
    static AiException translate(ExecutionException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OllamaBoundedBodySubscriber.BodyTooLargeException) {
                return new AiException(AiErrorReason.RESPONSE_TOO_LARGE, "AI provider response exceeds the configured maximum size");
            }
            if (cause instanceof HttpTimeoutException) { // includes HttpConnectTimeoutException
                return new AiException(AiErrorReason.PROVIDER_TIMEOUT, MSG_TIMEOUT);
            }
            if (cause instanceof IOException) {
                break;
            }
        }
        return new AiException(AiErrorReason.PROVIDER_UNAVAILABLE, "AI provider is unreachable");
    }

    private static boolean isSuccess(int status) {
        return status >= GhostBridgeAiLiterals.HTTP_STATUS_OK_MIN && status <= GhostBridgeAiLiterals.HTTP_STATUS_OK_MAX;
    }

    /** Status mapping only; response bodies of error statuses are never read, logged or quoted. */
    private static void requireSuccess(int status) {
        if (isSuccess(status)) {
            return;
        }
        if (status == GhostBridgeAiLiterals.HTTP_STATUS_UNAUTHORIZED || status == GhostBridgeAiLiterals.HTTP_STATUS_FORBIDDEN) {
            throw new AiException(AiErrorReason.MISSING_CREDENTIALS, "AI provider rejected the configured credentials");
        }
        if (status == GhostBridgeAiLiterals.HTTP_STATUS_TOO_MANY_REQUESTS) {
            throw new AiException(AiErrorReason.PROVIDER_THROTTLED, "AI provider is rate limiting requests");
        }
        if (status == GhostBridgeAiLiterals.HTTP_STATUS_REQUEST_TIMEOUT) {
            throw new AiException(AiErrorReason.PROVIDER_TIMEOUT, MSG_TIMEOUT);
        }
        if (status >= GhostBridgeAiLiterals.HTTP_STATUS_SERVER_ERROR_MIN && status <= GhostBridgeAiLiterals.HTTP_STATUS_SERVER_ERROR_MAX) {
            throw new AiException(AiErrorReason.PROVIDER_UNAVAILABLE, "AI provider is temporarily unavailable");
        }
        throw new AiException(AiErrorReason.PROVIDER_FAILURE, "AI provider could not process the request");
    }

    private JsonNode parse(byte[] body) {
        try {
            return mapper.readTree(body);
        } catch (IOException e) {
            throw new AiException(AiErrorReason.INVALID_MODEL_RESPONSE, MSG_UNEXPECTED_RESPONSE);
        }
    }
}
