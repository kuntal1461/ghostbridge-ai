package com.ghostbridge.responsevo;

/** The caller's own per-requester limit status (window is one minute). */
public record AiRateLimitResponseVO(int limitPerMinute, int remaining, long resetInSeconds, int maxConcurrent) {
}
