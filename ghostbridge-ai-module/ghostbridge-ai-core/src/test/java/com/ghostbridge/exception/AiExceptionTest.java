package com.ghostbridge.exception;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiExceptionTest {

    @Test
    void retryAfterIsEmptyUnlessTheSourceProvidedIt() {
        assertThat(new AiException(AiErrorReason.PROVIDER_UNAVAILABLE, "x").getRetryAfter()).isEmpty();
        assertThat(new AiException(AiErrorReason.PROVIDER_UNAVAILABLE, "x", new IllegalStateException()).getRetryAfter()).isEmpty();
        assertThat(new AiException(AiErrorReason.RATE_LIMITED, "x", Duration.ofSeconds(7)).getRetryAfter())
                .contains(Duration.ofSeconds(7));
    }

    @Test
    void nonPositiveRetryAfterIsTreatedAsUnknown() {
        assertThat(new AiException(AiErrorReason.RATE_LIMITED, "x", Duration.ZERO).getRetryAfter()).isEmpty();
        assertThat(new AiException(AiErrorReason.RATE_LIMITED, "x", Duration.ofSeconds(-1)).getRetryAfter()).isEmpty();
        assertThat(new AiException(AiErrorReason.RATE_LIMITED, "x", (Duration) null).getRetryAfter()).isEmpty();
    }
}
