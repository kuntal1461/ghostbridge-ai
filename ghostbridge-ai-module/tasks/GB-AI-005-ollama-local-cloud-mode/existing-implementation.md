# Existing Implementation — GB-AI-005

## Scope analysed
`rules/rules.md`, `.kiro/steering/ghostbridge-ai-module.md`, `ghostbridge-ai-core`: `AiProperties`, `AiAutoConfiguration`, `AiProductionValidator`, `AiSecret`, `OllamaAiProvider`, `GhostBridgeAiLiterals`, existing tests (`AiOllamaPropertiesTest`, `AiOllamaAutoConfigurationTest`, `AiSecretTest`, `OllamaAiProviderTest`, `OllamaCoordinatorIntegrationTest`, `StubOllamaServer`, `OllamaLiveProviderTest`); `ghostbridge-ai-web` test properties; repository search for `application*.yml|properties`.

## Current behaviour
- Provider selection: `ghostbridge.ai.provider` (`mock` | `bedrock` | `ollama`). `AiAutoConfiguration` creates exactly one provider bean, conditional on that property. `DefaultAiCoordinator` receives `ObjectProvider<AiProvider>` and is provider-neutral. Controllers contain no provider logic.
- Model: a single `ghostbridge.ai.model-id` (required for ollama/bedrock; never defaulted), plus `approved-model-ids`.
- Ollama settings (`ghostbridge.ai.ollama.*`): `mode` (must equal `cloud`), `base-url` (default `https://ollama.com`), `allowed-hosts` (default `ollama.com`), `api-key` (`AiSecret`, masked), `connect-timeout`, `max-request-bytes`, `max-response-bytes`. Destination must be https, allow-listed host, plain origin, no custom port.
- Credential: `ollama.api-key` or, if blank, the `OLLAMA_API_KEY` OS environment variable (OS source only). Startup fails if absent. `OllamaAiProvider` requires a header-safe key and always sends `Authorization: Bearer`. No redirects, JDK TLS defaults, bounded bodies, generic error messages without causes, refuses JVM header-logging switches.
- Property metadata flags `api-key` as sensitive (checked by `AiOllamaAutoConfigurationTest`).
- There is **no** runnable application configuration in the repository: `ghostbridge-app-web` has no main class and no `application.yml`; the only properties file is `ghostbridge-ai-web/src/test/resources/application.properties` (`ghostbridge.ai.provider=mock`).
- Live verification (GB-AI-002): the cloud request returned HTTP 402 (account billing), not a code defect.

## Functionality that must be preserved
- Mock provider for tests and `local|dev|test` profiles; no silent fallback between providers.
- Existing cloud keys: `ghostbridge.ai.provider=ollama`, `model-id`, `ollama.base-url`, `ollama.api-key`, `ollama.allowed-hosts`, byte limits, timeouts.
- Public AI Core contracts (`AiClient`, `AiProvider`, `AiProviderCall`, `AiProviderResult`, VOs), the AI Web API, and the secret masking.
- Core never depends on web; no new Maven modules or dependencies.

## Gaps and risks found
- No local (self-hosted) Ollama support; the destination validator rejects http and loopback/private hosts.
- Cloud-only `mode` check blocks any local value.
- Model and endpoint are not selectable per mode; `model-id` is one global value.
- `OllamaAiProvider` hard-requires and sends a credential, and its status text says "Ollama Cloud".
- No validation of the model identifier format.

## Evidence
Source reads on 2026-11-23; `mvn -o -B clean verify` BUILD SUCCESS (ai-core 274 tests incl. 1 skipped live test, ai-web 86).
