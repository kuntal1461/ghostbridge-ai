package com.ghostbridge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.dto.AiRequest;
import com.ghostbridge.dto.AiResponse;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import com.ghostbridge.service.AiClient;
import com.ghostbridge.service.AiProvider;
import com.ghostbridge.service.impl.AiLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withConfiguration(AutoConfigurations.of(AiAutoConfiguration.class));

    private static AiRequest request() {
        return new AiRequest("generic-structured", null, Map.of("text", "summarize me"), null, null, null, "u1");
    }

    private ApplicationContextRunner withProfiles(String... profiles) {
        return runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profiles));
    }

    private static void assertStartupFailureMentions(AssertableApplicationContext context, String text) {
        assertThat(context).hasFailed();
        assertThat(rootMessage(context.getStartupFailure())).contains(text);
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "dev", "test"})
    void mockProviderWorksDeterministicallyInApprovedProfiles(String profile) {
        withProfiles(profile).withPropertyValues("ghostbridge.ai.provider=mock").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AiClient.class).hasSingleBean(AiProvider.class);
            AiResponse first = context.getBean(AiClient.class).generate(request());
            AiResponse second = context.getBean(AiClient.class).generate(request());
            assertThat(first.provider()).isEqualTo("mock");
            assertThat(first.text()).isEqualTo(second.text());
            assertThat(first.structuredOutput()).isPresent();
        });
    }

    @Test
    void mockProviderIsRejectedInProd() {
        withProfiles("prod").withPropertyValues("ghostbridge.ai.provider=mock")
                .run(context -> assertStartupFailureMentions(context, "mock is allowed only"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"local,prod", "prod,dev", "test,prod", "dev,qa,prod"})
    void mixedProfilesContainingProdCannotBypassTheRestriction(String profiles) {
        withProfiles(profiles.split(",")).withPropertyValues("ghostbridge.ai.provider=mock")
                .run(context -> assertStartupFailureMentions(context, "mock is allowed only"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"qa", "uat", "production", "staging"})
    void mockProviderIsRejectedInUnapprovedProfiles(String profile) {
        withProfiles(profile).withPropertyValues("ghostbridge.ai.provider=mock")
                .run(context -> assertStartupFailureMentions(context, "mock is allowed only"));
    }

    @Test
    void mockProviderIsRejectedWhenNoProfileIsActive() {
        runner.withPropertyValues("ghostbridge.ai.provider=mock")
                .run(context -> assertStartupFailureMentions(context, "mock is allowed only"));
    }

    @Test
    void noProviderConfiguredMeansNoMockAndNoFallback() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(AiProvider.class);
            assertThatThrownBy(() -> context.getBean(AiClient.class).generate(request()))
                    .isInstanceOfSatisfying(AiException.class,
                            e -> assertThat(e.getReason()).isEqualTo(AiErrorReason.MISSING_CONFIGURATION));
        });
    }

    @Test
    void prodWithoutProviderStartsButAiStaysDisabled() {
        withProfiles("prod").run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(AiProvider.class));
    }

    @Test
    void invalidBedrockConfigurationFailsStartupWithTheReason() {
        runner.withPropertyValues("ghostbridge.ai.provider=bedrock")
                .run(context -> assertStartupFailureMentions(context, "model-id is required"));
        runner.withPropertyValues("ghostbridge.ai.provider=bedrock", "ghostbridge.ai.model-id=m")
                .run(context -> assertStartupFailureMentions(context, "aws.region is required"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ghostbridge.ai.max-output-tokens=0",
            "ghostbridge.ai.temperature=1.5",
            "ghostbridge.ai.retry.max-attempts=6",
            "ghostbridge.ai.rate-limit.requests-per-minute=0",
            "ghostbridge.ai.rate-limit.max-concurrent=1000",
            "ghostbridge.ai.max-input-length=0",
            "ghostbridge.ai.max-response-size=0",
            "ghostbridge.ai.budget.max-tokens=-1",
            "ghostbridge.ai.history-max-entries=0"})
    void outOfRangeSettingsFailStartupViaBeanValidation(String property) {
        runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ghostbridge.ai.timeout=0s",
            "ghostbridge.ai.timeout=-5s",
            "ghostbridge.ai.timeout=10m",
            "ghostbridge.ai.retry.initial-backoff=-1s",
            "ghostbridge.ai.retry.initial-backoff=0ms",
            "ghostbridge.ai.retry.max-backoff=0s",
            "ghostbridge.ai.budget.window=0s",
            "ghostbridge.ai.pricing.input-per1k-tokens=-0.1"})
    void nonPositiveOrOversizedDurationsAndNegativePricesFailStartup(String property) {
        runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void initialBackoffAboveMaxBackoffFailsStartup() {
        runner.withPropertyValues("ghostbridge.ai.retry.initial-backoff=5s", "ghostbridge.ai.retry.max-backoff=1s")
                .run(context -> assertStartupFailureMentions(context, "must not exceed max-backoff"));
    }

    @Test
    void applicationClientOverridesDefault() {
        AiClient custom = req -> null;
        runner.withBean("custom", AiClient.class, () -> custom)
                .run(context -> assertThat(context.getBean(AiClient.class)).isSameAs(custom));
    }

    @Test
    void limiterIsIndividuallyOverridable() {
        AiLimiter custom = new AiLimiter(new AiProperties.RateLimit(1, 1), java.time.Clock.systemUTC());
        runner.withBean("customLimiter", AiLimiter.class, () -> custom)
                .run(context -> assertThat(context.getBean(AiLimiter.class)).isSameAs(custom));
    }
}
