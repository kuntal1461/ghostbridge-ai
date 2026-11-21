package com.ghostbridge.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiPropertiesTest {

    @Test
    void unsetProviderMeansDisabledWithRestrictiveDefaults() {
        AiProperties props = AiTestSupport.properties();
        assertThat(props.enabled()).isFalse();
        assertThat(props.modelId()).isNull();
        assertThat(props.aws().region()).isNull();
        assertThat(props.maxOutputTokens()).isEqualTo(1024);
        assertThat(props.temperature()).isEqualTo(0.2);
        assertThat(props.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(props.retry().maxAttempts()).isEqualTo(3);
        assertThat(props.budget().enabled()).isFalse();
        assertThat(props.pricing().configured()).isFalse();
    }

    @Test
    void mockWorksWithoutAnyAwsConfiguration() {
        AiProperties props = AiTestSupport.properties("provider", "mock");
        assertThat(props.enabled()).isTrue();
        assertThat(props.modelId()).isEqualTo(AiProperties.DEFAULT_MOCK_MODEL);
        assertThat(props.aws().region()).isNull();
    }

    @Test
    void providerNameIsNormalized() {
        assertThat(AiTestSupport.properties("provider", " MOCK ").provider()).isEqualTo("mock");
    }

    @Test
    void bedrockRequiresModelId() {
        assertThatThrownBy(() -> AiTestSupport.properties("provider", "bedrock", "aws.region", "eu-west-1"))
                .isInstanceOf(BindException.class).hasRootCauseMessage("ghostbridge.ai.model-id is required when provider=bedrock");
    }

    @Test
    void bedrockRequiresRegion() {
        assertThatThrownBy(() -> AiTestSupport.properties("provider", "bedrock", "model-id", "some-model"))
                .isInstanceOf(BindException.class).hasRootCauseMessage("ghostbridge.ai.aws.region is required when provider=bedrock");
    }

    @Test
    void bedrockWithModelAndRegionIsAccepted() {
        AiProperties props = AiTestSupport.properties("provider", "bedrock", "model-id", "m", "aws.region", "r");
        assertThat(props.modelId()).isEqualTo("m");
        assertThat(props.aws().region()).isEqualTo("r");
    }

    @Test
    void unknownProviderIsRejected() {
        assertThatThrownBy(() -> AiTestSupport.properties("provider", "other"))
                .isInstanceOf(BindException.class);
    }

    @Test
    void bindsAllRequiredKeys() {
        AiProperties props = AiTestSupport.properties("provider", "mock", "model-id", "x", "max-output-tokens", "256",
                "temperature", "0.5", "timeout", "5s", "aws.region", "eu-west-1");
        assertThat(props.maxOutputTokens()).isEqualTo(256);
        assertThat(props.temperature()).isEqualTo(0.5);
        assertThat(props.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(props.aws().region()).isEqualTo("eu-west-1");
    }

    @Test
    void durationAndPriceBoundsAreEnforcedWhenBinding() {
        assertThatThrownBy(() -> AiTestSupport.properties("timeout", "0s")).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> AiTestSupport.properties("timeout", "6m")).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> AiTestSupport.properties("retry.initial-backoff", "-1s")).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> AiTestSupport.properties("retry.initial-backoff", "3s", "retry.max-backoff", "1s"))
                .isInstanceOf(BindException.class);
        assertThatThrownBy(() -> AiTestSupport.properties("budget.window", "0s")).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> AiTestSupport.properties("pricing.output-per1k-tokens", "-1")).isInstanceOf(BindException.class);
    }
}
