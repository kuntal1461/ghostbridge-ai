package com.ghostbridge.service.impl;

import com.ghostbridge.dto.AiRequestMetadata;
import com.ghostbridge.dto.AiRequestStatus;
import com.ghostbridge.dto.AiUsage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AiRequestTelemetryTest {

    private static AiRequestMetadata meta(String provider, AiRequestStatus status, AiUsage usage) {
        return new AiRequestMetadata("id", "corr", "u", provider, "m", "p", 1, status, null, 12, usage, Instant.EPOCH);
    }

    @Test
    void publishesMetricsWhenRegistryExists() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        beans.addBean("meters", meters);
        InMemoryAiRequestRegistry registry = new InMemoryAiRequestRegistry(5);
        new AiRequestTelemetry(registry, beans.getBeanProvider(MeterRegistry.class))
                .record(meta("mock", AiRequestStatus.SUCCESS, new AiUsage(10, 5, null)));
        assertThat(registry.find("id")).isPresent();
        assertThat(meters.get("ghostbridge.ai.requests").tag("status", "SUCCESS").counter().count()).isEqualTo(1.0);
        assertThat(meters.get("ghostbridge.ai.request.duration").timer().count()).isEqualTo(1);
        assertThat(meters.get("ghostbridge.ai.tokens").tag("type", "input").counter().count()).isEqualTo(10.0);
        assertThat(meters.get("ghostbridge.ai.tokens").tag("type", "output").counter().count()).isEqualTo(5.0);
    }

    @Test
    void worksWithoutMeterRegistryAndUnknownProvider() {
        InMemoryAiRequestRegistry registry = new InMemoryAiRequestRegistry(5);
        new AiRequestTelemetry(registry, new StaticListableBeanFactory().getBeanProvider(MeterRegistry.class))
                .record(meta(null, AiRequestStatus.FAILURE, AiUsage.unknown()));
        assertThat(registry.find("id")).isPresent();
    }

    @Test
    void unknownProviderIsTaggedNone() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        beans.addBean("meters", meters);
        new AiRequestTelemetry(new InMemoryAiRequestRegistry(5), beans.getBeanProvider(MeterRegistry.class))
                .record(meta(null, AiRequestStatus.FAILURE, AiUsage.unknown()));
        assertThat(meters.get("ghostbridge.ai.requests").tag("provider", "none").counter().count()).isEqualTo(1.0);
    }

    @Test
    void failingRegistryNeverBreaksTheCaller() {
        com.ghostbridge.service.AiRequestRegistry broken = new com.ghostbridge.service.AiRequestRegistry() {
            @Override
            public void record(AiRequestMetadata metadata) {
                throw new IllegalStateException("registry down");
            }

            @Override
            public java.util.Optional<AiRequestMetadata> find(String requestId) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<AiRequestMetadata> recent(int limit) {
                return java.util.List.of();
            }
        };
        AiRequestTelemetry telemetry = new AiRequestTelemetry(broken, new StaticListableBeanFactory().getBeanProvider(MeterRegistry.class));
        assertThatCode(() -> telemetry.record(meta("mock", AiRequestStatus.SUCCESS, AiUsage.unknown()))).doesNotThrowAnyException();
    }
}
