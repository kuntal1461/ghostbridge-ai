package com.ghostbridge.security;

import com.ghostbridge.config.AppSecurityProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded, in-memory brute-force defence for the login endpoint. Failures are counted per key (the caller uses the
 * lower-cased user name and the client address as separate keys); once the caller's threshold is reached the key is blocked
 * until its window ends. Local to one JVM, like the other limiters of the platform. When the table is full, expired keys
 * are dropped first and then the oldest, so memory stays bounded even under a flood of distinct keys.
 */
public class LoginAttemptLimiter {
    private static final int MAX_TRACKED_KEYS = 10_000;

    private record Entry(int failures, Instant windowStart) {
    }

    private final AppSecurityProperties.LoginAttempts settings;
    private final Clock clock;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public LoginAttemptLimiter(AppSecurityProperties.LoginAttempts settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    /** How long the key stays blocked, when it is blocked now. */
    public synchronized Optional<Duration> blockedFor(String key, int threshold) {
        Entry entry = entries.get(key);
        if (entry == null || entry.failures() < threshold) {
            return Optional.empty();
        }
        Duration remaining = Duration.between(clock.instant(), entry.windowStart().plus(settings.window()));
        if (remaining.isNegative() || remaining.isZero()) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(remaining);
    }

    public synchronized void recordFailure(String key) {
        Instant now = clock.instant();
        Entry entry = entries.remove(key);
        if (entry == null || !now.isBefore(entry.windowStart().plus(settings.window()))) {
            makeRoom(now);
            entries.put(key, new Entry(1, now));
        } else {
            entries.put(key, new Entry(entry.failures() + 1, entry.windowStart()));
        }
    }

    public synchronized void reset(String key) {
        entries.remove(key);
    }

    private void makeRoom(Instant now) {
        if (entries.size() < MAX_TRACKED_KEYS) {
            return;
        }
        entries.values().removeIf(entry -> !now.isBefore(entry.windowStart().plus(settings.window())));
        Iterator<String> oldest = entries.keySet().iterator();
        while (entries.size() >= MAX_TRACKED_KEYS && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }
}
