# Test Cases — GB-AI-005

| ID | Type | Scenario | Expected | Test class | Status |
|---|---|---|---|---|---|
| TC-1 | negative | provider=ollama with no mode setting (model-id only; also with `OLLAMA_API_KEY` in the environment) | startup fails: `local-enabled must be set explicitly`; no cloud selection | `AiOllamaLocalModeTest`, `AiOllamaAutoConfigurationTest` | Passed |
| TC-2 | positive | local-enabled=true, valid local URL+model, no key | local URI `…/v1/chat/completions`, modelId = local model, no key required | `AiOllamaLocalModeTest` | Passed |
| TC-3 | positive | local-enabled=false with cloud.model | modelId = cloud model, cloud URI | same | Passed |
| TC-4 | negative | local mode without local.base-url / model | startup (binding) failure naming the property | same | Passed |
| TC-5 | negative | Local base URLs: public IP, ollama.com, user-info, path, query, fragment, ftp, garbage | rejected; message never echoes URL | same | Passed |
| TC-6 | positive | Local hosts: localhost, 127.0.0.1, [::1], 10.x, 172.16-31.x, 192.168.x, fc00::/7, allow-listed name | accepted (http and https) | same | Passed |
| TC-7 | negative | 172.32.0.1, 0.0.0.0, unlisted hostname | rejected | same | Passed |
| TC-8 | negative | Invalid model identifiers (blank handled as missing, space, control char, too long) | rejected | same | Passed |
| TC-9 | negative | Conflicts: `model-id` ≠ selected model; `mode` ≠ flag; `base-url` ≠ `cloud.base-url`; `api-key` ≠ `cloud.api-key` | rejected | same | Passed |
| TC-10 | positive | Legacy keys alone (`ollama.base-url`, `ollama.api-key`) | still bind as cloud | `AiOllamaPropertiesTest` | Passed |
| TC-11 | positive | Profile-specific override (`application-<profile>` style via second property source) | later source wins; resulting mode consistent | `AiOllamaLocalModeTest` | Passed |
| TC-12 | integration | Cloud mode without key (context runner) | startup fails, message names settings, no value | `AiOllamaAutoConfigurationTest` | Passed |
| TC-13 | integration | Local mode without key, with `OLLAMA_API_KEY` irrelevant | context starts; provider bean present | same | Passed |
| TC-14 | integration | Local mode: request to loopback stub has **no** `Authorization` header; response mapped | passes, 1 request | `OllamaLocalProviderTest` | Passed |
| TC-15 | integration | Local failure (stub 500/connection refused) does not contact any other host | AiException, only stub hit | same | Passed |
| TC-16 | security | Key never in `toString`, property metadata value, status; local mode sends no Authorization header | masked/absent | existing + `OllamaLocalProviderTest` | Passed (log-line content not asserted) |
| TC-17 | regression | Mock provider and full suites | pass | whole build | Passed |

## Execution log
| Date | Command | Result |
|---|---|---|
| 2026-11-24 | `mvn -o -B -pl ghostbridge-ai-module/ghostbridge-ai-core clean test` | 366 tests, 0 failures, 1 skipped (live test) |
| 2026-11-24 | `mvn -o -B clean verify` (root, JDK 21), after review fixes | BUILD SUCCESS: ai-core 366 (1 skipped live test), ai-web 86, springboot modules green; no POM changes |
| 2026-11-24 | key-shape scan of repository files | 0 matches |

Added after reviews: link-local/trailing-dot/IPv4-mapped/leading-zero/port cases, allow-list hygiene, both-destinations validation, malformed flag, blank `.env.example` placeholders, redirect not followed. No live or paid request was made; no local Ollama server was started, so real local inference is unverified.

### Round 2 (safe provider selection) — 2026-11-24
| ID | Type | Scenario | Test | Status |
|---|---|---|---|---|
| TC-18 | negative | Missing, blank, both-sides, none-configured, misspelled-with-cloud-settings, cloud-only legacy settings, malformed flag (`maybe`, `yes please`, `ture`, `2`) | `AiOllamaLocalModeTest` | Passed |
| TC-19 | positive | Misspelled flag with only `local.*` settings selects local; explicit flag beats inference; `mode=cloud` and `mode=local` explicit; blank mode = unset; mock/other providers unaffected | `AiOllamaLocalModeTest`, `AiOllamaAutoConfigurationTest` | Passed |
| TC-20 | integration | Relaxed environment variable names (`GHOSTBRIDGE_AI_OLLAMA_LOCAL_ENABLED=TRUE`…) start local without a credential; env-only cloud setup without the flag fails | `AiOllamaAutoConfigurationTest` | Passed |
| TC-21 | live (manual, local only) | One real request to local Ollama `127.0.0.1:11434`, model `phi3:3.8b`, through `OllamaAiProvider`, no key | `OllamaLocalLiveProviderTest` | Passed (65 chars, 3.7-4.7 s, usage 31/20) |

| Date | Command | Result |
|---|---|---|
| 2026-11-24 | `mvn -o -B clean verify` (root, JDK 21), final code | BUILD SUCCESS: springboot 31 + 55, ai-core 376 tests (2 skipped: the two manual live tests), ai-web 86; no POM changes |
| 2026-11-24 | `OllamaLocalLiveProviderTest -Dlive.local.provider=true`, `GHOSTBRIDGE_AI_OLLAMA_LOCAL_MODEL=phi3:3.8b` (twice) | passed; local only; `OLLAMA_API_KEY` unset |
