package com.ghostbridge.security;

import com.ghostbridge.config.AiWebProperties;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import com.ghostbridge.service.impl.AiLimiter;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-requester rate (fixed one-minute window) and concurrency limiter (gate G-M4-1). The key is the trusted requester
 * key from {@link AiRequesterResolver}. The global {@code AiLimiter} remains an additional safeguard behind it.
 *
 * <p>Memory is bounded: at most {@code maxTrackedRequesters} entries exist. An entry is removed once its window has
 * ended and it has no request in flight (a sweep runs at most once per window, and immediately when the table is full).
 * When the table is full of live entries a new requester is rejected (fail closed) instead of evicting another
 * requester's counters, because eviction would reset an abuser's quota.
 *
 * <p>One lock guards all state: every operation is O(1) (sweeps are O(n) but rare), which is negligible next to a model
 * call, and it makes the counters trivially consistent. <strong>State is local to this JVM</strong>: with several
 * application instances the effective per-requester limit is multiplied by the instance count; a shared (distributed)
 * limiter is required there and is not provided.
 */
public class AiRequesterLimiter {

    /** A caller's own limit status; never describes anybody else. */
    public record Status(int limit, int remaining, Duration resetIn, int maxConcurrent) {
    }

    private static final class Entry {
        private long windowStart;
        private int used;
        private int inFlight;

        private Entry(long windowStart) {
            this.windowStart = windowStart;
        }
    }

    private final Object lock = new Object();
    private final Map<String, Entry> entries = new HashMap<>();
    private final AiWebProperties.RequesterLimit limits;
    private final long windowMillis = GhostBridgeAiLiterals.RATE_LIMIT_WINDOW.toMillis();
    private final Clock clock;
    private long lastSweep;

    public AiRequesterLimiter(AiWebProperties.RequesterLimit limits, Clock clock) {
        this.limits = limits;
        this.clock = clock;
        this.lastSweep = clock.millis();
    }

    /**
     * Admits one request for the requester and returns a permit that must be closed when the request ends.
     *
     * @throws AiException RATE_LIMITED when the requester's rate or concurrency limit, or the tracking bound, is reached
     */
    public AiLimiter.Permit acquire(String requesterKey) {
        synchronized (lock) {
            long now = clock.millis();
            sweepIfDue(now);
            Entry entry = entries.get(requesterKey);
            if (entry == null) {
                entry = newEntry(requesterKey, now);
            }
            roll(entry, now);
            if (entry.inFlight >= limits.maxConcurrent()) {
                throw new AiException(AiErrorReason.RATE_LIMITED, "Too many concurrent AI requests for this caller");
            }
            if (entry.used >= limits.requestsPerMinute()) {
                throw new AiException(AiErrorReason.RATE_LIMITED, "AI request rate limit exceeded for this caller",
                        Duration.ofMillis(Math.max(1, entry.windowStart + windowMillis - now)));
            }
            entry.used++;
            entry.inFlight++;
            return permit(entry);
        }
    }

    /** The caller's current status. Reading it never creates tracking state. */
    public Status status(String requesterKey) {
        synchronized (lock) {
            long now = clock.millis();
            Entry entry = entries.get(requesterKey);
            if (entry == null || now - entry.windowStart >= windowMillis) {
                return new Status(limits.requestsPerMinute(), limits.requestsPerMinute(), Duration.ZERO, limits.maxConcurrent());
            }
            int remaining = Math.max(0, limits.requestsPerMinute() - entry.used);
            return new Status(limits.requestsPerMinute(), remaining,
                    Duration.ofMillis(entry.windowStart + windowMillis - now), limits.maxConcurrent());
        }
    }

    /** Number of tracked requesters (for diagnostics and tests). */
    public int trackedRequesters() {
        synchronized (lock) {
            return entries.size();
        }
    }

    private Entry newEntry(String key, long now) {
        if (entries.size() >= limits.maxTrackedRequesters()) {
            sweep(now);
        }
        if (entries.size() >= limits.maxTrackedRequesters()) {
            throw new AiException(AiErrorReason.RATE_LIMITED, "AI service is busy, try again later", earliestExpiry(now));
        }
        Entry entry = new Entry(now);
        entries.put(key, entry);
        return entry;
    }

    /** When the first idle entry frees a slot; null when nothing is known to expire (callers must not invent a value). */
    private Duration earliestExpiry(long now) {
        long soonest = Long.MAX_VALUE;
        for (Entry entry : entries.values()) {
            if (entry.inFlight == 0) {
                soonest = Math.min(soonest, entry.windowStart + windowMillis - now);
            }
        }
        return soonest == Long.MAX_VALUE ? null : Duration.ofMillis(soonest);
    }

    private void roll(Entry entry, long now) {
        if (now - entry.windowStart >= windowMillis) {
            entry.windowStart = now;
            entry.used = 0;
        }
    }

    private void sweepIfDue(long now) {
        if (now - lastSweep >= windowMillis) {
            sweep(now);
        }
    }

    private void sweep(long now) {
        lastSweep = now;
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.inFlight == 0 && now - entry.windowStart >= windowMillis) {
                iterator.remove();
            }
        }
    }

    private AiLimiter.Permit permit(Entry entry) {
        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            if (released.compareAndSet(false, true)) {
                synchronized (lock) {
                    entry.inFlight--;
                }
            }
        };
    }
}
