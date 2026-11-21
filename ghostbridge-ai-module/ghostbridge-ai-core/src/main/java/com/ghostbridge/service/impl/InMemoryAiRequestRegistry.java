package com.ghostbridge.service.impl;

import com.ghostbridge.dto.AiRequestMetadata;
import com.ghostbridge.service.AiRequestRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Process-local registry bounded to {@code capacity} entries; the oldest entry is evicted first. */
public class InMemoryAiRequestRegistry implements AiRequestRegistry {
    private final Map<String, AiRequestMetadata> entries;

    public InMemoryAiRequestRegistry(int capacity) {
        this.entries = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, AiRequestMetadata> eldest) {
                return size() > capacity;
            }
        };
    }

    @Override
    public synchronized void record(AiRequestMetadata metadata) {
        entries.put(metadata.requestId(), metadata);
    }

    @Override
    public synchronized Optional<AiRequestMetadata> find(String requestId) {
        return Optional.ofNullable(entries.get(requestId));
    }

    @Override
    public synchronized List<AiRequestMetadata> recent(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<AiRequestMetadata> newestFirst = new ArrayList<>(entries.values());
        Collections.reverse(newestFirst);
        return List.copyOf(newestFirst.subList(0, Math.min(limit, newestFirst.size())));
    }
}
