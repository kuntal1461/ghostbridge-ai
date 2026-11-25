# Requirements — GB-AI-005

## Objective
One Spring Boot flag, `ghostbridge.ai.ollama.local-enabled`, selects locally hosted Ollama (`true`) or Ollama Cloud (`false`) for every AI service, via the existing `ghostbridge.ai.*` configuration framework. Nothing is stored in a database; no separate JSON config.

## Functional requirements
| ID | Requirement |
|---|---|
| FR-1 | New properties: `ollama.local-enabled` (no default: explicit `true` = local, `false` = Ollama Cloud), `ollama.local.base-url`, `ollama.local.model`, `ollama.local.allowed-hosts`, `ollama.cloud.base-url`, `ollama.cloud.model`, `ollama.cloud.api-key`. |
| FR-2 | Compatibility: existing `ollama.base-url`, `ollama.api-key`, `ollama.allowed-hosts`, `ollama.mode` and `ghostbridge.ai.model-id` keep working as aliases for the cloud settings / selected model. A conflicting pair (both set, different values) fails startup. |
| FR-3 | The selected model (`local.model` or `cloud.model`, else legacy `model-id`) becomes `AiProperties.modelId()`, so the coordinator, approved-model check and all services use it. |
| FR-4 | Provider selection happens once, in `AiAutoConfiguration` / `OllamaAiProvider` construction from `AiProperties.Ollama`; controllers, coordinator and `AiProvider` interface are unchanged. |
| FR-5 | Local mode: no API key required, no `Authorization` header sent, `OLLAMA_API_KEY` ignored; the local endpoint and model are used. |
| FR-6 | Cloud mode: `OLLAMA_API_KEY` (or `cloud.api-key`) required; https, allow-listed host, TLS validation; startup fails clearly without a key. |
| FR-7 | No silent fallback in either direction; a local failure is reported, never retried against cloud. |
| FR-8 | Mock provider unaffected (the mode requirement applies only when `provider=ollama`). |
| FR-9 | **Explicit selection (added after review):** when `provider=ollama`, the mode comes from `local-enabled`, else the legacy `mode`; failing both it is inferred only as local (local settings present, no cloud setting). A missing, blank, misspelled or ambiguous setting never selects the paid cloud: startup fails with a message naming `local-enabled`. Cloud always needs `local-enabled=false` (or `mode=cloud`). |

## Security requirements
| ID | Requirement |
|---|---|
| SEC-1 | Local endpoint: `http` or `https` plain origin (no user-info, path, query, fragment); host must be loopback (`localhost`, `127.0.0.0/8`, `::1`), a private IP literal (10/8, 172.16/12, 192.168/16, 169.254/16, fc00::/7) or listed in `local.allowed-hosts` (container/VPN names). Public IP literals, unspecified addresses and `ollama.com` hosts are rejected. |
| SEC-2 | The key never appears in logs, REST responses, `toString`, property metadata values, or the frontend; local mode never transmits it. |
| SEC-3 | Redirects not followed; JDK TLS defaults; byte and time limits unchanged. |
| SEC-4 | Model identifiers validated against a safe pattern (`[A-Za-z0-9][A-Za-z0-9._:/@-]{0,127}`), so no control characters or whitespace reach the request. |
| SEC-5 | Documentation states Ollama's port 11434 must stay on loopback/private networks; the application cannot enforce server binding. |

## Non-functional
Java 21, constructor injection, centralized literals, no wildcard/static literal imports, cognitive complexity ≤ 15, no new dependencies or modules, configuration is read at startup (restart required).

## Out of scope
Live inference (cloud account has no credits), runtime reconfiguration/refresh, creating an `application.yml` for a non-existent runnable app, frontend changes, VPS provisioning, dynamic model discovery.

## Acceptance criteria
- [ ] Local/cloud selection tests pass (endpoint, model, header presence).
- [ ] Startup validation tests for all rows of the brief pass.
- [ ] Legacy keys still bind; conflicts fail.
- [ ] `mvn clean verify` passes; no paid or cloud call in tests.
- [ ] Security and code-quality reviews done, findings resolved or documented.
