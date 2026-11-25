# Implementation Plan — GB-AI-005

## Phases
| Phase | Goal | Deliverables |
|---|---|---|
| 1 | Properties | Extend `AiProperties.Ollama` (`localEnabled`, `Local`, `Cloud`, legacy aliases, destination validators), model-id resolution/validation in `AiProperties`, literals. |
| 2 | Provider | `OllamaAiProvider(settings, mapper)` honours mode: no key/no header in local mode; status text per mode. `AiAutoConfiguration`: env key fallback only in cloud mode. |
| 3 | Tests | Properties tests (selection, validation matrix, aliases, conflicts, profiles), provider tests against loopback stub (no `Authorization` in local mode), auto-config tests (missing key cloud/local), no-cloud-call guarantee. |
| 4 | Docs/config | `.env.example`, YAML contract documented, PROJECT_STRUCTURE status unaffected. |
| 5 | Reviews | security + code-quality review, fixes, `mvn clean verify`. |

## Affected files (planned)
`GhostBridgeAiLiterals`, `AiProperties`, `AiAutoConfiguration`, `OllamaAiProvider`, new `OllamaEndpointValidator` (package `config`, name unique repo-wide) to keep `AiProperties` small, tests under `ghostbridge-ai-core/src/test`, `.env.example`.

## Technical decisions
| Decision | Alternatives | Reason |
|---|---|---|
| Keep `ghostbridge.ai.provider=ollama` as the provider switch; `local-enabled` only selects the Ollama deployment | New `provider=ollama-local` | The brief asks for one flag under `ollama`; no new provider names; coordinator unchanged. |
| Default `local-enabled=false` | Default true | Preserves current behaviour; local must be opted into. |
| Selected model overrides into `AiProperties.modelId()` | Separate model lookups in coordinator | One source of truth; zero core-contract change; conflicting `model-id` fails startup. |
| Legacy flat keys remain as cloud aliases | Remove/rename | Backward compatibility; conflicts detected. |
| `allowed-hosts` per mode (`ollama.allowed-hosts` stays cloud; `local.allowed-hosts` new) | One shared list | A cloud allow-list must not approve plain-http local hosts. |
| Literal IP parsing only, no DNS in validation | Resolve hostnames | Avoids DNS at config time and rebinding-style surprises; names need explicit allow-listing. |
| No `application.yml` created | Add one under ai-web/app-web | There is no runnable application; creating configs in libraries would duplicate config (rules §6). YAML contract is documented. |

## Dependencies
None added.

## Rollback
Revert the listed files; `local-enabled` absent behaves exactly as before (cloud).
