# Test Cases — GB-AI-002 (Ollama Cloud Provider Integration)

Results are "Not run" until executed. All tests use a local stub HTTP server (`127.0.0.1`, ephemeral port) and synthetic dummy credentials; no real key and no network access to Ollama is required. Live tests are separate, tagged `live-provider`, and disabled by default.

| ID | Scenario | Expected | Result |
|---|---|---|---|
| P-01 | Request goes to `POST /v1/chat/completions` | Method/path match | Not run |
| P-02 | Request body format | JSON with `model`, `messages` [system,user], `temperature`, `max_tokens`, `stream=false`; `response_format` only for JSON mode | Not run |
| P-03 | `Authorization: Bearer <dummy>` sent to the approved endpoint; `Content-Type: application/json`; key not in URL/query | Header present, URL clean | Not run |
| P-04 | Missing/blank key with `provider=ollama` | Startup fails with a message naming the property, never a value | Not run |
| P-05 | Env fallback `OLLAMA_API_KEY` used when the property is blank | Provider created | Not run |
| P-06 | Provider 401 (invalid key) | `MISSING_CREDENTIALS`, generic message, not retried | Not run |
| P-07 | Provider 403 | `MISSING_CREDENTIALS` | Not run |
| P-08 | Provider 429 | `PROVIDER_THROTTLED` (retryable) | Not run |
| P-09 | Provider 500 / 502 / 503 / 504 | `PROVIDER_UNAVAILABLE` (retryable) | Not run |
| P-10 | Other 4xx (400/404/422) | `PROVIDER_FAILURE`, no body in message | Not run |
| P-11 | Connection timeout (unroutable/blocked) | `PROVIDER_TIMEOUT` | Not run |
| P-12 | Response timeout (server stalls) | `PROVIDER_TIMEOUT` | Not run |
| P-13 | Connection refused / reset | `PROVIDER_UNAVAILABLE` | Not run |
| P-14 | Invalid JSON, wrong shape, empty `choices`, non-string content | `INVALID_MODEL_RESPONSE` | Not run |
| P-15 | Successful response mapping | text + usage tokens mapped; negative/missing usage → unknown | Not run |
| P-16 | Structured output through the coordinator (valid, schema violation, trailing JSON) | Validated JSON / typed errors | Not run |
| P-17 | Max request bytes (ASCII) | `INPUT_TOO_LARGE` before any request is sent | Not run |
| P-18 | Multibyte Unicode (emoji, CJK) within char limit but over byte limit | `INPUT_TOO_LARGE`; within byte limit → sent intact | Not run |
| P-19 | Max response bytes (large body, chunked, no `Content-Length`) | `RESPONSE_TOO_LARGE`, reading stops | Not run |
| P-20 | `Content-Length` above limit | Rejected without reading the body | Not run |
| P-21 | Retry limits through the coordinator (503 ×N, 429, then success; max attempts) | Attempts == configured max; non-retryable not retried | Not run |
| P-22 | HTTP 3xx redirect to a second stub server | Not followed; second server receives **no** request and no `Authorization` header; `PROVIDER_FAILURE` | Not run |
| S-01 | Log capture on success and every failure path | No key, no `Authorization` value, no prompt/response text in any log line | Not run |
| S-02 | Exception messages and causes' messages surfaced by `AiException` | No key, no response body | Not run |
| S-03 | `AiSecret.toString()`, Jackson serialization, `AiProperties.toString()`, bind-failure messages | Masked; key absent | Not run |
| S-04 | Configuration metadata (`spring-configuration-metadata.json`) and defaults | No key value or default | Not run |
| S-05 | `AiProperties.Ollama` validation: http scheme, non-allow-listed host, user-info, query/fragment, bad port, unsupported mode, non-positive timeouts/limits | Startup/bind failure | Not run |
| S-06 | Missing model id with `provider=ollama` | Startup failure | Not run |
| S-07 | Status/readiness text | No key, no URL | Not run |
| R-01 | Mock provider regression (all M2 tests) | Pass | Not run |
| R-02 | Coordinator integration: `DefaultAiCoordinator` → `OllamaAiProvider` against the stub, telemetry recorded, usage returned | Pass | Not run |
| R-03 | `provider=ollama` is not subject to the mock-profile guard; `provider=mock` still is | Pass | Not run |
| L-01 | Optional live round trip | Disabled by default; requires `-Dlive.provider=true` + env key + model; never prints the key | **Not run** (no live request is made automatically) |
| G-01 | Secret scan of working tree, staged files and history | No credential; tool and limits recorded | Not run |
| G-02 | `.gitignore` patterns ignore `.env`, `.env.local`, `secrets/x`, `a.pem`, `a.key` and do not ignore `.env.example` (`git check-ignore`) | Pass | Not run |


## Results (2026-11-22, `mvn -o clean verify`, BUILD SUCCESS)
Counts: springboot-core 31, springboot-web 55, ai-core 255 (1 skipped = the manual live test, intentionally). No real key and no network access to Ollama was used; stub server on `127.0.0.1` with synthetic dummy credentials.

| Case | Result | Evidence |
|---|---|---|
| P-01..P-03 endpoint, body format, Authorization only to the approved endpoint | Pass | `OllamaAiProviderTest` |
| P-04, P-05 missing/blank key fails safely; `OLLAMA_API_KEY` fallback | Pass | `AiOllamaAutoConfigurationTest` |
| P-06..P-10 401/403/429/408/5xx/other 4xx, body never echoed, no cause | Pass | `OllamaAiProviderTest` |
| P-11 connect timeout | Pass in this environment (test skips itself if 10.255.255.1 is not black-holed) | `OllamaAiProviderTest` |
| P-12 response timeout, stalled body | Pass | `OllamaAiProviderTest` |
| P-13 connection refused | Pass | `OllamaAiProviderTest` |
| P-14, P-15 invalid JSON/shape, successful mapping, usage not fabricated | Pass | `OllamaAiProviderTest` |
| P-16 structured output via coordinator (valid, schema violation, trailing JSON, non-JSON) | Pass | `OllamaCoordinatorIntegrationTest` |
| P-17..P-20 request/response byte limits, UTF-8 bytes (emoji/CJK), chunked and declared-length responses, exact-limit accepted | Pass | `OllamaAiProviderTest`, `OllamaCoordinatorIntegrationTest` |
| P-21 retry limits through the coordinator (3x503→200, bounded 503/429, 401 not retried) | Pass | `OllamaCoordinatorIntegrationTest` |
| P-22 redirect not followed, second server receives nothing, credential not forwarded | Pass; **mutation check:** allowing redirects makes the test fail | `OllamaAiProviderTest` |
| S-01, S-02 no key/prompt in logs or error messages (all failure paths) | Pass | `OllamaAiProviderTest`, `OllamaCoordinatorIntegrationTest` |
| S-03 masked `toString`/JSON/bind errors | Pass | `AiSecretTest` |
| S-04 configuration metadata has no key default | Pass | `AiOllamaAutoConfigurationTest` |
| S-05, S-06 destination/mode/timeouts validation, model id required, messages never echo URL | Pass | `AiOllamaPropertiesTest` |
| S-07 status text | Pass | `OllamaAiProviderTest`, `AiOllamaAutoConfigurationTest` |
| R-01 mock regression (all GB-AI-001 tests) | Pass | full build |
| R-02 coordinator integration, telemetry | Pass | `OllamaCoordinatorIntegrationTest` |
| R-03 ollama not subject to the mock guard; mock still guarded | Pass | `AiOllamaAutoConfigurationTest` |
| Lazy initialization with missing key still fails at startup | Pass | `AiOllamaAutoConfigurationTest` |
| L-01 live round trip | **Not run** (manual only; no live request has been made) | `OllamaLiveProviderTest` skipped |
| G-01 secret scan | Fallback regex scan only: tree, staged (none), tracked files, history → only the pre-existing synthetic masker fixture. Gitleaks not available | see `implementation.md` |
| G-02 ignore rules | Pass (`.env`, `.env.local`, `.env.production`, `secrets/x.txt`, `a.pem`, `a.key`, `config.local` ignored; `.env.example` not) | `git check-ignore` |


## Results after review fixes (2026-11-22, final)
`mvn -o clean verify`: **JDK 21.0.8: BUILD SUCCESS** — springboot-core 31, springboot-web 55, ai-core 269 (1 skipped: the manual live test). **JDK 25 (machine default): BUILD SUCCESS** — same counts, 2 skipped (live test + the metadata check, because annotation processing does not run by default on JDK 23+). The earlier "255" figure and the network-dependent connect-timeout test are superseded: connect/request timeout mapping is now tested deterministically via `OllamaAiProvider.translate(...)`.

Added after review: header-unsafe credentials refused (newline, space, tab, zero-width, non-ASCII, control) at provider construction and at startup without quoting the value; loopback-only test constructor (remote/userinfo/look-alike hosts refused); JDK header-logging and `javax.net.debug` plaintext modes refused; `close()` behavior and Spring destroy-method inference; interrupt and serialization-failure branches; environment isolation (also verified with a synthetic `OLLAMA_API_KEY` exported); OS-environment-only fallback (plain properties and `-D` ignored); `base-url` trimming; byte-limit floor; hook cases (12, in a throwaway repo with synthetic values; not part of the Maven build).

Coverage (ad-hoc JaCoCo run by the quality reviewer, before the last small fixes, not enforced by the build): ai-core 98.4% lines, new/changed classes 98.1%. Sonar: **not verified**. Dependency vulnerability scan: **not performed**.

Still **Not run**: L-01 live round trip (manual only; no request to Ollama has ever been made); real Gitleaks scan (not installed).
