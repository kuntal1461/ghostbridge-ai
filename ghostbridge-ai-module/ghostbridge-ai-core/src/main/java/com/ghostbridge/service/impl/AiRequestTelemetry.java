package com.ghostbridge.service.impl;

import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.dto.AiRequestMetadata;
import com.ghostbridge.service.AiRequestRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

/**
 * Records one finished request: bounded registry entry, Micrometer metrics (when a registry exists) and a single
 * safe log line. Only ids, provider, model, status, duration and token counts are emitted — never prompts or output.
 */
public class AiRequestTelemetry {
    private static final Logger LOG = LoggerFactory.getLogger(AiRequestTelemetry.class);

    private final AiRequestRegistry registry;
    private final ObjectProvider<MeterRegistry> meters;

    public AiRequestTelemetry(AiRequestRegistry registry, ObjectProvider<MeterRegistry> meters) {
        this.registry = registry;
        this.meters = meters;
    }

    /** Never throws: a failing registry or metrics backend must not turn a finished request into a failure. */
    public void record(AiRequestMetadata metadata) {
        try {
            doRecord(metadata);
        } catch (RuntimeException e) {
            LOG.warn("ai.telemetry.failed type={}", e.getClass().getSimpleName());
        }
    }

    private void doRecord(AiRequestMetadata metadata) {
        registry.record(metadata);
        MeterRegistry meterRegistry = meters.getIfAvailable();
        if (meterRegistry != null) {
            publish(meterRegistry, metadata);
        }
        LOG.info("ai.request id={} correlationId={} provider={} model={} prompt={} status={} reason={} durationMs={} "
                        + "inputTokens={} outputTokens={}",
                metadata.requestId(), metadata.correlationId(), metadata.provider(), metadata.model(),
                metadata.promptName(), metadata.status(), metadata.errorReason(), metadata.durationMs(),
                metadata.usage().inputTokens(), metadata.usage().outputTokens());
    }

    private static void publish(MeterRegistry registry, AiRequestMetadata m) {
        String provider = m.provider() == null ? GhostBridgeAiLiterals.NONE : m.provider();
        String model = m.model() == null ? GhostBridgeAiLiterals.NONE : m.model();
        String status = m.status().name();
        registry.counter(GhostBridgeAiLiterals.METRIC_REQUESTS, GhostBridgeAiLiterals.TAG_PROVIDER, provider, GhostBridgeAiLiterals.TAG_MODEL, model, GhostBridgeAiLiterals.TAG_STATUS, status).increment();
        Timer.builder(GhostBridgeAiLiterals.METRIC_DURATION).tags(GhostBridgeAiLiterals.TAG_PROVIDER, provider, GhostBridgeAiLiterals.TAG_MODEL, model, GhostBridgeAiLiterals.TAG_STATUS, status)
                .register(registry).record(Duration.ofMillis(m.durationMs()));
        if (m.usage().inputTokens() != null) {
            registry.counter(GhostBridgeAiLiterals.METRIC_TOKENS, GhostBridgeAiLiterals.TAG_PROVIDER, provider, GhostBridgeAiLiterals.TAG_TYPE, GhostBridgeAiLiterals.TOKEN_TYPE_INPUT)
                    .increment(m.usage().inputTokens());
        }
        if (m.usage().outputTokens() != null) {
            registry.counter(GhostBridgeAiLiterals.METRIC_TOKENS, GhostBridgeAiLiterals.TAG_PROVIDER, provider, GhostBridgeAiLiterals.TAG_TYPE, GhostBridgeAiLiterals.TOKEN_TYPE_OUTPUT)
                    .increment(m.usage().outputTokens());
        }
    }
}
