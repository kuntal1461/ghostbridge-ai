# Impact Analysis — GB-AI-005

## Affected projects and consumers
| Project / module | Impact |
|---|---|
| `ghostbridge-ai-core` | Config record, provider construction, auto-configuration, literals, tests. |
| `ghostbridge-ai-web` | None in code. Its tests keep using the mock provider. |
| `ghostbridge-springboot`, frontend, app | None. The frontend never receives provider settings (`/provider/status` exposes only provider name/model/capabilities; model now shows the selected one). |

## Runtime behaviour changes
- Default behaviour unchanged (`local-enabled=false`, cloud). With `local-enabled=true` the provider calls the local endpoint without credentials.
- `AiProviderStatus.detail` text differs per mode (not exposed by the API).
- The configuration is read at startup; changing the flag needs a restart (no refresh scope is used).

## Compatibility risks
- `ollama.mode` previously defaulted to `cloud` and rejected anything else; it now accepts `local`/`cloud` (a blank value counts as unset), is an explicit selection on its own, and must agree with `local-enabled`. A config with `mode=local` that formerly failed will fail unless `local-enabled=true`.
- `ghostbridge.ai.ollama.base-url` no longer has a bound default (default applied in resolution); behaviour identical.
- Bean/record constructor signature of `AiProperties.Ollama` changes (public record); only tests and `AiAutoConfiguration` use it in this repository. External consumers constructing it directly would break (none known).
- A configured `model-id` that differs from the selected `local.model`/`cloud.model` now fails startup.

## Potential breaking changes
Record canonical constructor of `AiProperties.Ollama` (internal config type). Documented.

## Mitigations
Aliases + conflict checks, tests for legacy keys. **Breaking (deliberate):** with `provider=ollama`, a configuration that selects cloud only implicitly (for example `model-id` plus `OLLAMA_API_KEY` in the environment, or only `ollama.api-key`/`cloud.model`) now fails startup until `ghostbridge.ai.ollama.local-enabled=false` is set. Migration: add that one setting.
