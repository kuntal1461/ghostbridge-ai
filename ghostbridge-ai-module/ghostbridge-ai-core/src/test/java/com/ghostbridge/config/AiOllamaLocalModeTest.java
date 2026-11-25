package com.ghostbridge.config;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Local/cloud selection, destination validation, model resolution and legacy-key compatibility of the Ollama settings. */
class AiOllamaLocalModeTest {
    /** Synthetic dummy credential: not a real key. */
    private static final String DUMMY = "test-dummy-key-0123456789";
    private static final String LOCAL_URL = "http://127.0.0.1:11434";

    private static AiProperties props(String... extra) {
        List<String> all = new ArrayList<>(List.of("provider", "ollama"));
        all.addAll(List.of(extra));
        return AiTestSupport.properties(all.toArray(String[]::new));
    }

    private static AiProperties local(String... extra) {
        List<String> all = new ArrayList<>(List.of("ollama.local-enabled", "true", "ollama.local.base-url", LOCAL_URL,
                "ollama.local.model", "qwen2.5-coder:3b"));
        all.addAll(List.of(extra));
        return props(all.toArray(String[]::new));
    }

    private static void assertRejected(Throwable failure, String... messageParts) {
        assertThat(failure).isInstanceOf(BindException.class);
        String text = rootMessage(failure);
        for (String part : messageParts) {
            assertThat(text).contains(part);
        }
        assertThat(text).doesNotContain(DUMMY);
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }

    @Test
    void explicitCloudSelectionKeepsTheLegacyCloudBehaviour() {
        AiProperties.Ollama ollama = props("ollama.local-enabled", "false", "model-id", "legacy-model").ollama();
        assertThat(ollama.localEnabled()).isFalse();
        assertThat(ollama.mode()).isEqualTo("cloud");
        assertThat(ollama.credentialRequired()).isTrue();
        assertThat(ollama.chatCompletionsUri()).hasToString("https://ollama.com/v1/chat/completions");
        assertThat(props("ollama.local-enabled", "false", "model-id", "legacy-model").modelId()).isEqualTo("legacy-model");
    }

    @Test
    void localModeSelectsTheLocalEndpointAndModelWithoutAnApiKey() {
        AiProperties properties = local();
        assertThat(properties.ollama().mode()).isEqualTo("local");
        assertThat(properties.ollama().credentialRequired()).isFalse();
        assertThat(properties.ollama().apiKey().isPresent()).isFalse();
        assertThat(properties.ollama().chatCompletionsUri()).hasToString("http://127.0.0.1:11434/v1/chat/completions");
        assertThat(properties.modelId()).isEqualTo("qwen2.5-coder:3b");
    }

    @Test
    void cloudModeSelectsTheCloudEndpointAndModelEvenWhenLocalSettingsExist() {
        AiProperties properties = props("ollama.local-enabled", "false", "ollama.local.base-url", LOCAL_URL,
                "ollama.local.model", "local-model", "ollama.cloud.model", "deepseek-v4.1-flash", "ollama.cloud.api-key", DUMMY);
        assertThat(properties.ollama().chatCompletionsUri()).hasToString("https://ollama.com/v1/chat/completions");
        assertThat(properties.modelId()).isEqualTo("deepseek-v4.1-flash");
        assertThat(properties.ollama().apiKey().reveal()).isEqualTo(DUMMY);
        assertThat(properties.ollama().toString()).doesNotContain(DUMMY);
    }

    @Test
    void localModeRequiresItsOwnEndpointAndModel() {
        assertRejected(catchBind(() -> props("ollama.local-enabled", "true", "ollama.local.model", "m")),
                "ghostbridge.ai.ollama.local.base-url");
        assertRejected(catchBind(() -> props("ollama.local-enabled", "true", "ollama.local.base-url", LOCAL_URL)),
                "ghostbridge.ai.ollama.local.model");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:11434", "http://127.0.0.1:11434", "http://127.9.9.9", "http://[::1]:11434",
            "http://10.1.2.3:11434", "http://172.16.0.5:11434", "http://172.31.255.255:11434", "http://192.168.1.20:11434",
            "http://[fd12:3456::1]:11434", "https://192.168.1.20:11434", "http://127.0.0.1:11434/", "http://LOCALHOST:11434",
            "http://[::ffff:127.0.0.1]:11434", "http://[::ffff:10.0.0.1]:11434"})
    void loopbackAndPrivateEndpointsAreAccepted(String url) {
        assertThat(local("ollama.local.base-url", url).ollama().chatCompletionsUri().getPath()).isEqualTo("/v1/chat/completions");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://8.8.8.8:11434", "http://172.32.0.1:11434", "http://172.15.0.1:11434", "http://0.0.0.0:11434",
            "http://300.1.1.1:11434", "http://ollama.internal:11434", "http://169.254.169.254", "http://169.254.10.10:11434",
            "http://[fe80::1]:11434", "http://[::]:11434", "http://[::ffff:8.8.8.8]:11434", "http://010.0.0.1:11434", "http://127.0.0.1:",
            "http://127.0.0.1:0", "http://localhost.:11434", "http://ollama.com.:11434", "http://OLLAMA.COM", "   ", "http://example.com:11434", "http://ollama.com:11434",
            "https://api.ollama.com", "http://[2001:db8::1]:11434", "ftp://127.0.0.1", "127.0.0.1:11434", "http://user:secret-pass@127.0.0.1",
            "http://127.0.0.1/api", "http://127.0.0.1?x=1", "http://127.0.0.1#f", "http://127.0.0.1:99999", "http://127 .0.0.1", "not a url"})
    void publicOrUnsafeEndpointsAreRejectedWithoutEchoingTheUrl(String url) {
        Throwable failure = catchBind(() -> local("ollama.local.base-url", url));
        assertRejected(failure, "ghostbridge.ai.ollama.local.base-url");
        assertThat(rootMessage(failure)).doesNotContain("secret-pass").doesNotContain("8.8.8.8").doesNotContain("example.com");
    }

    @Test
    void anOperatorAllowListedHostNameIsAcceptedForContainerOrVpnDeployments() {
        AiProperties properties = local("ollama.local.base-url", "http://ollama:11434", "ollama.local.allowed-hosts", "Ollama,gpu-box.internal");
        assertThat(properties.ollama().chatCompletionsUri()).hasToString("http://ollama:11434/v1/chat/completions");
        assertRejected(catchBind(() -> local("ollama.local.base-url", "http://other:11434", "ollama.local.allowed-hosts", "ollama")),
                "local.allowed-hosts");
    }

    @Test
    void linkLocalAddressesAreAcceptedOnlyWhenTheOperatorAllowListsThem() {
        assertThat(local("ollama.local.base-url", "http://169.254.10.10:11434", "ollama.local.allowed-hosts", "169.254.10.10")
                .ollama().chatCompletionsUri().getHost()).isEqualTo("169.254.10.10");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ollama.com.", "*.internal", "host:11434", "host/path", "-bad", "bad-", "has space", "ü.example"})
    void malformedAllowListEntriesAreRejected(String entry) {
        assertRejected(catchBind(() -> local("ollama.local.allowed-hosts", entry)), "allowed-hosts entries");
    }

    @Test
    void anAllowListedCloudNameWithATrailingDotStillCannotBeALocalEndpoint() {
        assertRejected(catchBind(() -> local("ollama.local.base-url", "http://ollama.com.:11434", "ollama.local.allowed-hosts", "ollama.com")),
                "must not point at Ollama Cloud");
    }

    @Test
    void bothDestinationsAreValidatedEvenWhenOnlyOneIsSelected() {
        assertRejected(catchBind(() -> props("model-id", "m", "ollama.local-enabled", "false", "ollama.local.base-url", "http://8.8.8.8")),
                "ghostbridge.ai.ollama.local.base-url");
        assertRejected(catchBind(() -> local("ollama.allowed-hosts", "proxy.example.org")), "ghostbridge.ai.ollama.cloud.base-url");
    }

    @Test
    void anUndeterminedModeIsRejectedInsteadOfDefaultingToThePaidCloud() {
        String message = "ghostbridge.ai.ollama.local-enabled must be set explicitly";
        assertRejected(catchBind(() -> props("model-id", "m")), message);                                  // nothing says local or cloud
        assertRejected(catchBind(() -> props("model-id", "m", "ollama.local-enabled", "")), message);     // blank placeholder
        assertRejected(catchBind(() -> props("model-id", "m", "ollama.local.model", "x", "ollama.cloud.model", "y")), message); // both sides
        assertRejected(catchBind(() -> props("ollama.local-enabled", "false")), "model-id");               // explicit, but incomplete
    }

    @Test
    void aMisspelledFlagNeverSelectsTheCloud() {
        // "local-enable" is ignored by the binder, so the mode is inferred from what is configured.
        AiProperties onlyLocal = props("ollama.local-enable", "true", "ollama.local.base-url", LOCAL_URL, "ollama.local.model", "m");
        assertThat(onlyLocal.ollama().mode()).isEqualTo("local");
        assertThat(onlyLocal.ollama().chatCompletionsUri().getHost()).isEqualTo("127.0.0.1");
        assertRejected(catchBind(() -> props("ollama.local-enable", "true", "ollama.local.base-url", LOCAL_URL, "ollama.local.model", "m",
                "ollama.cloud.model", "c")), "must be set explicitly");
        assertRejected(catchBind(() -> props("ollama.local-enable", "false", "model-id", "m")), "must be set explicitly");
    }

    @Test
    void cloudIsNeverInferredFromCloudSettingsAlone() {
        String message = "must be set explicitly";
        assertRejected(catchBind(() -> props("model-id", "m", "ollama.api-key", DUMMY)), message);
        assertRejected(catchBind(() -> props("model-id", "m", "ollama.base-url", "https://ollama.com")), message);
        assertRejected(catchBind(() -> props("ollama.cloud.model", "deepseek-v4.1-flash")), message);
        assertRejected(catchBind(() -> props("ollama.cloud.model", "x", "ollama.cloud.api-key", DUMMY)), message);
        // a typo'd local key plus cloud settings must not end up in the paid cloud either
        assertRejected(catchBind(() -> props("ollama.local.url", "http://127.0.0.1:11434", "ollama.cloud.model", "x")), message);
    }

    @Test
    void cloudNeedsAnExplicitChoiceEitherByTheFlagOrByTheLegacyMode() {
        assertThat(props("model-id", "m", "ollama.local-enabled", "false", "ollama.api-key", DUMMY).ollama().mode()).isEqualTo("cloud");
        assertThat(props("model-id", "m", "ollama.mode", "cloud").ollama().mode()).isEqualTo("cloud");
        assertThat(props("model-id", "m", "ollama.mode", " CLOUD ").ollama().credentialRequired()).isTrue();
    }

    @Test
    void anExplicitFlagAlwaysWinsOverInference() {
        AiProperties cloud = props("ollama.local-enabled", "false", "ollama.local.base-url", LOCAL_URL, "ollama.local.model", "m",
                "ollama.cloud.model", "c", "ollama.cloud.api-key", DUMMY);
        assertThat(cloud.ollama().mode()).isEqualTo("cloud");
        assertThat(cloud.modelId()).isEqualTo("c");
        AiProperties local = props("ollama.local-enabled", "true", "ollama.local.base-url", LOCAL_URL, "ollama.local.model", "m",
                "ollama.cloud.model", "c", "ollama.cloud.api-key", DUMMY);
        assertThat(local.ollama().mode()).isEqualTo("local");
        assertThat(local.modelId()).isEqualTo("m");
    }

    @Test
    void aMalformedFlagValueFailsInsteadOfSilentlySelectingAMode() {
        for (String bad : List.of("maybe", "yes please", "ture", "2")) {
            assertThat(catchBind(() -> props("model-id", "m", "ollama.local-enabled", bad)))
                    .isInstanceOf(BindException.class).hasMessageContaining("local-enabled");
        }
        assertThat(props("model-id", "m", "ollama.local-enabled", " TRUE ", "ollama.local.base-url", LOCAL_URL, "ollama.local.model", "m")
                .ollama().mode()).isEqualTo("local");
    }

    @Test
    void theModeRequirementAppliesOnlyToTheOllamaProvider() {
        assertThat(AiTestSupport.properties("provider", "mock").ollama().isSelected()).isFalse();
        assertThat(AiTestSupport.properties().ollama().isSelected()).isFalse();
        assertThat(AiTestSupport.properties("provider", "mock").modelId()).isEqualTo("mock-model");
    }

    @Test
    void theCloudHostCanNeverBeUsedAsALocalEndpointEvenWhenAllowListed() {
        assertRejected(catchBind(() -> local("ollama.local.base-url", "https://ollama.com", "ollama.local.allowed-hosts", "ollama.com")),
                "must not point at Ollama Cloud");
    }

    @ParameterizedTest
    @ValueSource(strings = {"has space", "bad\tmodel", "-leading-dash", "ünïcode", "a;rm -rf"})
    void invalidModelIdentifiersAreRejected(String model) {
        assertRejected(catchBind(() -> local("ollama.local.model", model)), "model identifier");
        assertRejected(catchBind(() -> props("ollama.local-enabled", "false", "model-id", model)), "model identifier");
        assertRejected(catchBind(() -> props("ollama.cloud.model", model)), "ollama.cloud.model");
    }

    @Test
    void aModelIdentifierLongerThan128CharactersIsRejected() {
        assertRejected(catchBind(() -> local("ollama.local.model", "x".repeat(129))), "model identifier");
        assertThat(local("ollama.local.model", "x".repeat(128)).modelId()).hasSize(128);
    }

    @ParameterizedTest
    @ValueSource(strings = {"qwen2.5-coder:3b", "deepseek-v4.1-flash", "library/llama3:8b-instruct", "m", "gpt-oss:20b@sha"})
    void realisticModelIdentifiersAreAccepted(String model) {
        assertThat(local("ollama.local.model", model).modelId()).isEqualTo(model);
    }

    @Test
    void aLegacyModelIdThatContradictsTheSelectedModelFailsButAnEqualOneIsAllowed() {
        assertRejected(catchBind(() -> local("model-id", "other-model")), "ghostbridge.ai.model-id conflicts with ghostbridge.ai.ollama.local.model");
        assertThat(local("model-id", "qwen2.5-coder:3b").modelId()).isEqualTo("qwen2.5-coder:3b");
        assertRejected(catchBind(() -> props("ollama.local-enabled", "false", "ollama.cloud.model", "a", "model-id", "b")), "ghostbridge.ai.ollama.cloud.model");
    }

    @Test
    void legacyFlatKeysStillBindAsCloudAliasesWhenCloudIsSelectedExplicitlyAndConflictsFail() {
        AiProperties legacy = props("model-id", "m", "ollama.local-enabled", "false", "ollama.api-key", DUMMY, "ollama.base-url", "https://ollama.com");
        assertThat(legacy.ollama().apiKey().reveal()).isEqualTo(DUMMY);
        assertThat(legacy.ollama().cloud().apiKey().reveal()).isEqualTo(DUMMY);
        assertThat(props("model-id", "m", "ollama.local-enabled", "false", "ollama.api-key", DUMMY, "ollama.cloud.api-key", DUMMY).ollama().apiKey().reveal()).isEqualTo(DUMMY);
        assertRejected(catchBind(() -> props("model-id", "m", "ollama.local-enabled", "false", "ollama.api-key", DUMMY, "ollama.cloud.api-key", "different-key-000000")),
                "api-key conflicts with");
        assertRejected(catchBind(() -> props("model-id", "m", "ollama.local-enabled", "false", "ollama.base-url", "https://ollama.com", "ollama.cloud.base-url", "https://proxy.example.org",
                "ollama.allowed-hosts", "ollama.com,proxy.example.org")), "base-url conflicts with");
    }

    @Test
    void theLegacyModeMustAgreeWithTheFlag() {
        assertThat(props("model-id", "m", "ollama.mode", "CLOUD").ollama().mode()).isEqualTo("cloud");
        assertThat(local("ollama.mode", "local").ollama().mode()).isEqualTo("local");
        assertRejected(catchBind(() -> local("ollama.mode", "cloud")), "ollama.mode must match");
        assertRejected(catchBind(() -> props("model-id", "m", "ollama.local-enabled", "false", "ollama.mode", "local")), "ollama.mode must match");
        assertRejected(catchBind(() -> props("model-id", "m", "ollama.mode", "banana")), "mode must be local or cloud");
        assertThat(props("model-id", "m", "ollama.local-enabled", "false", "ollama.mode", "").ollama().mode()).isEqualTo("cloud"); // blank = unset
        assertRejected(catchBind(() -> props("model-id", "m", "ollama.mode", "")), "must be set explicitly");
    }

    @Test
    void cloudDestinationRulesStillApplyAndDoNotApproveLocalHosts() {
        assertRejected(catchBind(() -> props("model-id", "m", "ollama.local-enabled", "false", "ollama.cloud.base-url", "http://ollama.com")), "must use https");
        assertRejected(catchBind(() -> props("model-id", "m", "ollama.local-enabled", "false", "ollama.cloud.base-url", "https://127.0.0.1")), "allowed-hosts");
        assertRejected(catchBind(() -> props("model-id", "m", "ollama.local-enabled", "false", "ollama.allowed-hosts", "ollama.com", "ollama.base-url", "https://localhost")), "allowed-hosts");
    }

    @Test
    void aHigherPrecedencePropertySourceOverridesLowerOnes() {
        Map<String, String> base = Map.of("ghostbridge.ai.provider", "ollama",
                "ghostbridge.ai.ollama.local-enabled", "false", "ghostbridge.ai.ollama.cloud.model", "cloud-model",
                "ghostbridge.ai.ollama.local.base-url", LOCAL_URL, "ghostbridge.ai.ollama.local.model", "local-model");
        Map<String, String> profile = Map.of("ghostbridge.ai.ollama.local-enabled", "true");
        AiProperties merged = new Binder(new MapConfigurationPropertySource(profile), new MapConfigurationPropertySource(base))
                .bindOrCreate("ghostbridge.ai", AiProperties.class);
        assertThat(merged.ollama().mode()).isEqualTo("local");
        assertThat(merged.modelId()).isEqualTo("local-model");
    }

    @Test
    void blankPlaceholdersCopiedFromTheExampleEnvironmentBehaveAsUnset() {
        String[] blanks = {"ollama.local-enabled", "", "ollama.local.base-url", "", "ollama.local.model", "",
                "ollama.local.allowed-hosts", "", "ollama.cloud.model", "", "ollama.cloud.base-url", "", "ollama.cloud.api-key", ""};
        assertRejected(catchBind(() -> props(prepend(blanks, "model-id", "m"))), "must be set explicitly");
        AiProperties mock = AiTestSupport.properties(prepend(blanks, "provider", "mock"));
        assertThat(mock.ollama().isSelected()).isFalse();
    }

    private static String[] prepend(String[] values, String... head) {
        List<String> all = new ArrayList<>(List.of(head));
        all.addAll(List.of(values));
        return all.toArray(String[]::new);
    }

    @Test
    void theMockProviderIsUnaffectedByOllamaSettings() {
        AiProperties properties = AiTestSupport.properties("provider", "mock");
        assertThat(properties.modelId()).isEqualTo("mock-model");
        assertThat(properties.ollama().isSelected()).isFalse();
    }

    private static Throwable catchBind(ThrowableAssert.ThrowingCallable callable) {
        return catchThrowable(callable);
    }
}
