# Implementation Plan — GB-AI-002 (Ollama Cloud Provider Integration)

Flow: `AiClient (DefaultAiCoordinator) → AiProvider SPI → OllamaAiProvider → Ollama Cloud` (backend only).
Frontend → GhostBridge App/API (M4+) → AI Core → Ollama Cloud. The frontend never sees the key and never calls the provider.

## Credential architecture (no real credentials appear in this document)
```
runtime environment / secrets manager        committed configuration
  OLLAMA_API_KEY  ───────────────┐             ghostbridge.ai.ollama.api-key: ${OLLAMA_API_KEY}   (placeholder only)
                                 ▼
  Spring Environment → AiProperties.Ollama.apiKey : AiSecret   (masked toString/JSON, value only via reveal())
                                 ▼
  AiAutoConfiguration: key present? (boolean check only) → OllamaAiProvider(validated endpoint URI, AiSecret)
                                 ▼
  per request: HttpRequest header "Authorization: Bearer <reveal()>" → https://<allow-listed host>/v1/chat/completions
```
- Library module: no committed `application.yml` with the key. The property can be supplied as `ghostbridge.ai.ollama.api-key` (e.g. `${OLLAMA_API_KEY}` in the *consuming app's* YAML) or, if blank, `AiAutoConfiguration` falls back to the `OLLAMA_API_KEY` environment variable. Missing → `IllegalStateException` naming the property, never a value.
- The value is never converted to `String` outside `reveal()`; no logging of the provider request/headers; HTTP errors are mapped from status codes only (response bodies of error statuses are not read into messages).

## Decisions
| # | Decision | Rationale / deviation |
|---|---|---|
| D1 | The adapter implements the provider SPI **`AiProvider`** (`OllamaAiProvider`), not `AiClient`. | In this codebase `AiClient` is the coordinator-facing contract implemented by `DefaultAiCoordinator`; implementing it in a provider would bypass validation, limits, retry, budget and structured validation. The brief's chain `AiCoordinator → AiClient → OllamaAiClient` maps to `DefaultAiCoordinator(AiClient) → AiProvider → OllamaAiProvider`. **Deviation, flagged for the user.** |
| D2 | Provider wire contracts are package-private `OllamaChatRequestVO`, `OllamaChatMessageVO`, `OllamaChatResponseVO` records in `service.impl`; existing public `AiRequest`/`AiResponse` records are **not** renamed. | `RequestVO`/`ResponseVO` naming is not an existing repository standard (no rule, no usage). Applying it to the provider's HTTP payloads honors the request without a breaking public rename. **Flagged.** |
| D3 | JDK `java.net.http.HttpClient` (HTTP/1.1, `Redirect.NEVER`, default TLS validation, connect timeout from config). | No new dependency; `ghostbridge-ai-core` has no `spring-web`. Provider/HTTP types never appear in public contracts. |
| D4 | `AiSecret` (in `com.ghostbridge.config`): holds the value, `toString()` and Jackson output are `****`, `equals/hashCode` do not leak, value only via `reveal()`. | A `String` in a record leaks through `toString`, bind errors, debug dumps and metadata endpoints. |
| D5 | New `ghostbridge.ai.ollama.*`: `mode` (only `cloud` accepted), `base-url` (default `https://ollama.com`), `allowed-hosts` (default `ollama.com`), `api-key`, `connect-timeout` (5s), `max-request-bytes` (256 KiB), `max-response-bytes` (1 MiB). Reused unchanged: `provider`, `model-id` (required for ollama, **no default**), `max-output-tokens`, `temperature`, `timeout` (response timeout per attempt), `retry.*`. | No duplicate settings; defaults restrictive; bounds validated. |
| D6 | Destination validation in `AiProperties.Ollama`: scheme `https`, host in allow-list, no user-info/query/fragment, no explicit non-443 port unless the host is allow-listed with it (default: port must be absent or 443). Redirects never followed; a 3xx is a `PROVIDER_FAILURE`. | SSRF/credential-forwarding protection (SR-4). |
| D7 | Request bytes: serialize with Jackson to `byte[]`, reject `length > max-request-bytes` with `INPUT_TOO_LARGE` before any network I/O (multibyte-safe). Response: check `Content-Length`, then read from the body stream in bounded chunks and abort at `max-response-bytes` with `RESPONSE_TOO_LARGE`. `max_tokens` always sent. | G-M3-1. |
| D8 | Status mapping per FR-5; error bodies are never read into messages or logs. Retries only via the existing coordinator `AiRetryExecutor` (retryable reasons), no retry inside the provider. | One bounded retry policy. |
| D9 | `status()` returns `ready=true` with the safe detail `"Ollama Cloud (credential configured)"`; the provider cannot be constructed without a header-safe key, so a missing key is a startup failure rather than a not-ready status. No host, no key. | Operator visibility without leakage. |
| D10 | Fixed protocol constants (path `/v1/chat/completions`, role names, header names, JSON field names, `Bearer ` prefix, `ollama` provider id, `cloud` mode, default host) go in `GhostBridgeAiLiterals` (normal class import). Credentials, runtime config values and client-facing messages do not. | Literals convention. |
| D11 | Optional live test `OllamaLiveProviderIT`-style class `OllamaLiveProviderTest`: `@Tag("live-provider")`, enabled only when `-Dlive.provider=true` **and** `OLLAMA_API_KEY` + `GHOSTBRIDGE_AI_MODEL_ID` env vars are set; never prints the key; not run by default. | No paid call by accident, no Surefire change needed. |
| D12 | Tests use the JDK `com.sun.net.httpserver.HttpServer` on `127.0.0.1` via a package-private constructor that takes an explicit endpoint `URI`; the public constructor only accepts already-validated properties. Synthetic dummy keys only. | Deterministic, no network, no new dependency; config validation tested separately. |
| D13 | Bedrock stays unimplemented (accepted by config validation only); not removed in this task. | Scope control; noted for the user. |
| D14 | `.gitignore` secret patterns and `.env.example` (empty placeholders) added; secret scanning is run via Gitleaks if installable/available, otherwise the regex fallback is run and reported as the weaker substitute. | SR-7. |

## Files (planned)
`ghostbridge-ai-core`: `config/AiSecret`, `config/AiProperties` (+`Ollama`), `config/AiAutoConfiguration` (provider bean, key resolution), `constants/GhostBridgeAiLiterals`, `service/impl/OllamaAiProvider`, `OllamaChatRequestVO`, `OllamaChatMessageVO`, `OllamaChatResponseVO`, tests under `src/test`. Repository: `.gitignore`, `.env.example`, `PROJECT_STRUCTURE.md` (class list note if needed), `.kiro/specs/ghostbridge-ai/*`, this task folder, M2 `milestone.md` pointer (M3 provider changed from Bedrock to Ollama).

## Dependency changes
None planned. (`spring-boot-configuration-processor` already generates metadata; the key has no default value.)

## Milestones
| # | Goal | Exit |
|---|---|---|
| A | Docs + secret hygiene (`.gitignore`, `.env.example`, scans) | Docs complete; scans recorded; no secret found |
| B | Config: `AiSecret`, `AiProperties.Ollama`, literals, validation, auto-config + startup checks | Config tests green |
| C | `OllamaAiProvider`, VOs, byte limits, error mapping | Stub-server tests green |
| D | Coordinator integration, regression, log/error/`toString`/metadata redaction tests | Full build green |
| E | Independent security + quality reviews, fixes, secret scan, final build | No Critical/High; reports written |

## Risks and rollback
| Risk | Mitigation |
|---|---|
| Key leakage via logs/toString/bind errors | `AiSecret`; tests that capture logs and exceptions; reviewer focus; scan |
| Endpoint/model facts unverifiable offline | Config-only; stub-tested; reported "not live-verified" |
| Stub-server seam weakens production guarantees | Package-private constructor only; public path validated; reviewer asked to check |
| JDK HttpClient proxy defaults | Documented residual: system proxy settings may apply; no custom proxy configured |
| Rollback | Revert the milestone commit; no data or schema change; config keys are additive |

## Deliberately out of scope
AI Web controllers, frontend, per-requester limits (G-M4-1), `Retry-After` (G-M4-2), Bedrock adapter, streaming, Docker/CI secret tooling, history rewriting.
