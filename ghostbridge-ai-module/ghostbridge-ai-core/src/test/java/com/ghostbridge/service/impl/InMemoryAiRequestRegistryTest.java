package com.ghostbridge.service.impl;

import com.ghostbridge.dto.AiRequestMetadata;
import com.ghostbridge.dto.AiRequestStatus;
import com.ghostbridge.dto.AiUsage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAiRequestRegistryTest {

    private static AiRequestMetadata meta(String id) {
        return new AiRequestMetadata(id, "c", "u", "mock", "m", "p", 1, AiRequestStatus.SUCCESS, null, 1, AiUsage.unknown(), Instant.EPOCH);
    }

    @Test
    void evictsOldestBeyondCapacityAndListsNewestFirst() {
        InMemoryAiRequestRegistry registry = new InMemoryAiRequestRegistry(2);
        registry.record(meta("1"));
        registry.record(meta("2"));
        registry.record(meta("3"));
        assertThat(registry.find("1")).isEmpty();
        assertThat(registry.find("3")).isPresent();
        assertThat(registry.recent(10)).extracting(AiRequestMetadata::requestId).containsExactly("3", "2");
        assertThat(registry.recent(1)).hasSize(1);
        assertThat(registry.recent(0)).isEmpty();
    }

    @Test
    void metadataHasNoContentFields() {
        assertThat(Arrays.stream(AiRequestMetadata.class.getRecordComponents()).map(RecordComponent::getName))
                .noneMatch(n -> n.toLowerCase().contains("prompt") && !n.equals("promptName") && !n.equals("promptVersion"))
                .doesNotContain("text", "response", "variables", "content", "user", "system");
    }
}
