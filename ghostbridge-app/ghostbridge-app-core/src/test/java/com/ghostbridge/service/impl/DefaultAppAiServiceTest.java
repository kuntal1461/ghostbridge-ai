package com.ghostbridge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.config.AiProperties;
import com.ghostbridge.config.AppAiProperties;
import com.ghostbridge.dto.AiGenerationOptions;
import com.ghostbridge.dto.AiProviderCall;
import com.ghostbridge.dto.AiProviderResult;
import com.ghostbridge.dto.AiProviderStatus;
import com.ghostbridge.dto.AiRequest;
import com.ghostbridge.dto.AiRequestMetadata;
import com.ghostbridge.dto.AiRequestStatus;
import com.ghostbridge.dto.AiResponse;
import com.ghostbridge.dto.AiUsage;
import com.ghostbridge.dto.AppAiGenerateCommand;
import com.ghostbridge.dto.AiProviderOverview;
import com.ghostbridge.dto.AppAiStructuredCommand;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import com.ghostbridge.service.AiClient;
import com.ghostbridge.service.AiProvider;
import com.ghostbridge.service.AiRequestRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class DefaultAppAiServiceTest {
    private final List<AiRequest> seen = new ArrayList<>();
    private AiResponse next = new AiResponse("req-1", "mock", "mock-model", "text", new ObjectMapper().createObjectNode().put("summary", "s"),
            new AiUsage(5, 3, null), 12);
    private final List<AiRequestMetadata> history = new ArrayList<>();

    private final AiClient client = request -> {
        seen.add(request);
        return next;
    };
    private final AiRequestRegistry registry = new AiRequestRegistry() {
        @Override
        public void record(AiRequestMetadata metadata) {
            history.add(metadata);
        }

        @Override
        public Optional<AiRequestMetadata> find(String requestId) {
            return Optional.empty();
        }

        @Override
        public List<AiRequestMetadata> recent(int limit) {
            return history;
        }
    };

    private static AiProperties properties(String... pairs) {
        Map<String, String> source = new java.util.HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            source.put("ghostbridge.ai." + pairs[i], pairs[i + 1]);
        }
        return new Binder(new MapConfigurationPropertySource(source)).bindOrCreate("ghostbridge.ai", AiProperties.class);
    }

    private static AiProvider provider(String name, boolean ready) {
        return new AiProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public AiProviderResult invoke(AiProviderCall call) {
                throw new UnsupportedOperationException();
            }

            @Override
            public AiProviderStatus status() {
                return new AiProviderStatus(ready, "detail that must not be exposed");
            }
        };
    }

    private DefaultAppAiService service(AiProperties ai, AiProvider... providers) {
        return new DefaultAppAiService(client, ai, new AppAiProperties(null), registry, List.of(providers));
    }

    @Test
    void generateTranslatesTheCommandToTheCoreRequestWithTheTrustedRequesterKey() {
        AiResponse response = service(properties("provider", "mock")).generate(new AppAiGenerateCommand("key-1", "generic-structured", 2,
                Map.of("text", "hi"), "doc", "mock-model", new AiGenerationOptions(100, 0.5)));
        assertThat(response).isSameAs(next);
        AiRequest request = seen.get(0);
        assertThat(request.promptName()).isEqualTo("generic-structured");
        assertThat(request.promptVersion()).isEqualTo(2);
        assertThat(request.variables()).containsEntry("text", "hi");
        assertThat(request.untrustedContent()).isEqualTo("doc");
        assertThat(request.modelId()).isEqualTo("mock-model");
        assertThat(request.options().maxOutputTokens()).isEqualTo(100);
        assertThat(request.requesterId()).isEqualTo("key-1");
    }

    @Test
    void structuredUsesTheApprovedSchemasPromptAndNeverAClientChosenPrompt() {
        service(properties("provider", "mock")).generateStructured(new AppAiStructuredCommand("key-1", "generic-summary",
                Map.of("text", "hi"), null, AiGenerationOptions.defaults()));
        assertThat(seen.get(0).promptName()).isEqualTo("generic-structured");
        assertThat(seen.get(0).modelId()).isNull();
    }

    @Test
    void anUnapprovedSchemaIsRejectedBeforeAnyModelCall() {
        AiException failure = catchThrowableOfType(AiException.class, () -> service(properties("provider", "mock"))
                .generateStructured(new AppAiStructuredCommand("key-1", "other-schema", Map.of(), null, null)));
        assertThat(failure.getReason()).isEqualTo(AiErrorReason.INVALID_INPUT);
        assertThat(failure.getMessage()).isEqualTo("Schema is not approved");
        assertThat(seen).isEmpty();
    }

    @Test
    void aStructuredCallWithoutStructuredOutputFailsValidation() {
        next = new AiResponse("req-2", "mock", "mock-model", "text", null, new AiUsage(1, 1, null), 1);
        AiException failure = catchThrowableOfType(AiException.class, () -> service(properties("provider", "mock"))
                .generateStructured(new AppAiStructuredCommand("key-1", "generic-summary", Map.of(), null, null)));
        assertThat(failure.getReason()).isEqualTo(AiErrorReason.OUTPUT_VALIDATION_FAILED);
    }

    @Test
    void providerStatusReportsConfigurationAndReadinessWithoutDetail() {
        assertThat(service(properties()).providerStatus()).extracting(AiProviderOverview::configured, AiProviderOverview::state)
                .containsExactly(false, "NOT_CONFIGURED");
        AiProviderOverview ready = service(properties("provider", "mock"), provider("mock", true)).providerStatus();
        assertThat(ready.state()).isEqualTo("AVAILABLE");
        assertThat(ready.provider()).isEqualTo("mock");
        assertThat(ready.model()).isEqualTo("mock-model");
        assertThat(ready.capabilities()).containsExactly("TEXT_GENERATION", "STRUCTURED_OUTPUT");
        assertThat(ready.toString()).doesNotContain("detail");
        assertThat(service(properties("provider", "mock"), provider("mock", false)).providerStatus().state()).isEqualTo("UNAVAILABLE");
        assertThat(service(properties("provider", "mock")).providerStatus().state()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void usageCountsOnlyTheRequestersOwnHistory() {
        history.add(new AiRequestMetadata("a", null, "me", "mock", "m", "p", 1, AiRequestStatus.SUCCESS, null, 1, new AiUsage(7, 2, null), Instant.EPOCH));
        history.add(new AiRequestMetadata("b", null, "other", "mock", "m", "p", 1, AiRequestStatus.SUCCESS, null, 1, new AiUsage(70, 20, null), Instant.EPOCH));
        assertThat(service(properties("provider", "mock")).usage("me").totalTokens()).isEqualTo(9);
        assertThat(service(properties("provider", "mock")).usage("nobody").requests()).isZero();
    }
}
