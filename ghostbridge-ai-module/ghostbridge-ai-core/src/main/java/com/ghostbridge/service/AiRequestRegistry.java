package com.ghostbridge.service;

import com.ghostbridge.dto.AiRequestMetadata;

import java.util.List;
import java.util.Optional;

/** Bounded store of request metadata (never content). */
public interface AiRequestRegistry {

    /** Stores metadata, evicting the oldest entry when the capacity is reached. */
    void record(AiRequestMetadata metadata);

    /** Looks up a request by id. */
    Optional<AiRequestMetadata> find(String requestId);

    /** Up to {@code limit} entries, most recent first; an empty list when {@code limit} is not positive. */
    List<AiRequestMetadata> recent(int limit);
}
