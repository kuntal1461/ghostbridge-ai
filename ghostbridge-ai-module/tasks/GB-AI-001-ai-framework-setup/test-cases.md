# Test Cases — GB-AI-001 (AI Framework Setup)

All results are **Not run** until executed; "Actual" is filled only from real runs.
Default build uses the mock provider and mocked SDK clients only. Live-provider tests carry `@Tag("live-provider")` and are disabled unless `-Dlive.provider=true`.

## Unit tests — ai-core
| ID | Scenario | Expected | Actual |
|---|---|---|---|
| U-01 | Valid request via `DefaultAiCoordinator` + mock provider | `AiResponse` with provider `mock`, requestId, duration ≥ 0, deterministic text | Not run |
| U-02 | Same request twice | Identical text (deterministic) | Not run |
| U-03 | Blank prompt name / null variables / oversize input | `AiInvalidInputException` / `AiLimitExceededException` | Not run |
| U-04 | Provider selected by `ghostbridge.ai.provider` | Matching adapter used; unknown name → `AiConfigurationException` | Not run |
| U-05 | Model not on allowlist | `AiInvalidInputException`, provider not called | Not run |
| U-06 | Prompt render with all variables | Rendered system/user parts; user text never inside system part | Not run |
| U-07 | Missing / unknown template variable | `AiInvalidInputException` naming the variable, not its value | Not run |
| U-08 | Unknown prompt name or version | Typed error | Not run |
| U-09 | Structured output valid JSON + schema | Parsed `JsonNode` returned | Not run |
| U-10 | Malformed JSON | `AiInvalidResponseException`, message free of prompt text | Not run |
| U-11 | Valid JSON, schema violation / missing required field | `AiOutputValidationException` listing paths only | Not run |
| U-12 | Response larger than `max-response-size` | `AiLimitExceededException` | Not run |
| U-13 | `maxOutputTokens` above ceiling | Rejected or clamped per config (documented) | Not run |
| U-14 | Provider timeout | `AiProviderTimeoutException`, retried up to max-attempts only if retryable | Not run |
| U-15 | Provider 5xx / unavailable | Retry then `AiProviderUnavailableException`; attempts == configured max | Not run |
| U-16 | Non-retryable failure (invalid input to provider, credentials) | No retry | Not run |
| U-17 | Rate limit exceeded | `AiRateLimitException`, provider not called | Not run |
| U-18 | Concurrency limit exhausted | Fail-fast rate-limit error, permit always released after failure | Not run |
| U-19 | Token budget exceeded | `AiLimitExceededException` after reported usage crosses budget; no fabricated usage when provider reports none | Not run |
| U-20 | Missing configuration (no provider, no model) | `AiConfigurationException`; properties validation fails on invalid values | Not run |
| U-21 | Usage/cost | `estimatedCost` empty unless pricing configured | Not run |
| U-22 | Metrics recorder | Counter/timer recorded with provider/model/status tags; works with no `MeterRegistry` | Not run |
| U-23 | Registry bound | Oldest evicted after capacity; stores no prompt/response content | Not run |
| U-24 | Retry executor backoff and interruption handling | Interrupt flag preserved | Not run |

## Integration tests — ai-core / ai-web (Spring context)
| ID | Scenario | Expected | Actual |
|---|---|---|---|
| I-01 | Auto-configuration with `provider=mock`, profile `local` | Context starts; `AiClient` bean present; overridable via `@ConditionalOnMissingBean` | Not run |
| I-02 | No provider configured | AI disabled, readiness "not configured", no mock fallback | Not run |
| I-03 | Profile `prod` + provider `mock` | Startup fails | Not run |
| I-04 | `BedrockAiProvider` with mocked `BedrockRuntimeClient`: success, throttling, timeout, access denied, missing credentials | Mapped typed errors; usage mapped from SDK response; no raw provider message exposed | Not run |
| I-05 | Web: authorized `POST /api/v1/ai/generate` | 200, DTO shape, `X-Correlation-Id` | Not run |
| I-06 | Web: no authentication | 401 ProblemDetail | Not run |
| I-07 | Web: authenticated without `AI_USER` | 403 | Not run |
| I-08 | Web: invalid payload (blank prompt, bad temperature) | 400 `VALIDATION_ERROR` with field errors only | Not run |
| I-09 | Web: oversized payload | 413/400 via shared filter or semantic limit | Not run |
| I-10 | Web: provider failures mapped | 429 / 503 / 504 / 502 with stable `code` and `reason`, no provider text | Not run |
| I-11 | Web: `GET /requests` returns only caller's entries; admin sees all; no content fields | Verified | Not run |
| I-12 | Web: `/mock/test` requires `AI_ADMIN`, absent when mock inactive or `prod` | 403 / 404 | Not run |
| I-13 | Shared advice coexistence | Non-AI exceptions still handled by `GlobalExceptionHandler`; exactly one `SecurityFilterChain`, no extra CORS bean | Not run |
| I-14 | Live Bedrock round trip | `@Tag("live-provider")`, disabled by default | Not run (no credentials) |

## Security tests
| ID | Scenario | Expected | Actual |
|---|---|---|---|
| S-01 | Untrusted content containing "ignore previous instructions…" | Appears only in delimited data block; system part unchanged; output still schema-validated | Not run |
| S-02 | Model output containing code, shell commands, config/JSON-instruction text | Returned as inert data; nothing executed, no config mutated (no execution path exists — asserted structurally) | Not run |
| S-03 | Log capture during success and failure | No prompt text, response text, API key, `Authorization` value or AWS credential in logs | Not run |
| S-04 | API error bodies | No prompt text, raw provider message, stack trace, ARN/account id | Not run |
| S-05 | Invalid/expired provider credentials | `AiCredentialsException` → safe 5xx/503 body; no secret echoed | Not run |
| S-06 | `prod` + mock | Fails startup; no silent fallback when Bedrock is unreachable | Not run |
| S-07 | Input-size, output-size, token, rate and concurrency enforcement | Each rejected with specific error | Not run |
| S-08 | Redaction | Configured patterns masked before provider call | Not run |
| S-09 | Dependency check | `mvn dependency:tree` reviewed; vulnerability scan reported as run/not run honestly | Not run |

## Frontend tests (Vitest + Testing Library)
| ID | Scenario | Expected | Actual |
|---|---|---|---|
| F-01 | API client builds URL from env, sends `Authorization` from provided token getter, never stores token | Pass | Not run |
| F-02 | Playground form validation (empty prompt, length, temperature range) | Inline errors, no request | Not run |
| F-03 | Loading, success, failure states | Rendered | Not run |
| F-04 | ProblemDetail error display | Human-readable message + correlation id, no raw payload | Not run |
| F-05 | Structured viewer: parsed JSON, missing fields, invalid data | Rendered | Not run |
| F-06 | Session history | Newest first, cleared on reload, no prompt text stored | Not run |
| F-07 | Bundle/UI scan | No credential-like strings; no provider config shown | Not run |
| F-08 | `npm run build`, `npm run lint`, `npm run typecheck` | Exit 0 | Not run |

## Failure scenarios
Provider unreachable; DNS/TLS failure; slow provider beyond timeout; throttling storm (retry bound); empty model output; model output truncated at max tokens; schema file missing at startup (fail fast); prompt file with undeclared variable (fail fast at startup); interrupted thread during backoff; permit leak on exception; registry capacity overflow.


## M2 results (2026-11-21, `mvn -o clean verify`, BUILD SUCCESS)
Counts: springboot-core 31, springboot-web 55 (incl. `ErrorCodeStatusMappingTest` 15), ai-core 173, all passing. Only mock provider and fixtures were used; no live provider test exists yet.

| Planned case | Result | Evidence (test class) |
|---|---|---|
| U-01/02 valid + deterministic | Pass | `DefaultAiCoordinatorTest`, `MockAiProviderTest`, `AiAutoConfigurationTest` |
| U-03/05 invalid input, option bounds, model allow-list | Pass | `DefaultAiCoordinatorTest`, `AiDtoTest` (null/too many/long name/requester id) |
| U-04/20 provider selection, missing config | Pass | `AiAutoConfigurationTest`, `AiPropertiesTest` |
| U-06/07/08 prompt render, missing/unknown variable, unknown prompt | Pass | `ClasspathAiPromptServiceTest` |
| U-09..U-12 structured output (strict JSON, trailing content, duplicate keys, fences, schema, no echo, size) | Pass | `JsonSchemaAiStructuredOutputServiceTest`, `DefaultAiCoordinatorTest` |
| U-14..U-16 timeout/unavailable/non-retryable/retry bounds | Pass | `DefaultAiCoordinatorTest`, `AiRetryExecutorTest` |
| U-17/18 rate limit, concurrency, permit release/idempotence, contention | Pass | `AiLimiterTest`, `DefaultAiCoordinatorTest` |
| U-19/21 token budget, cost only from configured pricing | Pass | `AiTokenBudgetTest`, `DefaultAiCoordinatorTest` |
| U-22 metrics with/without registry; telemetry failure is non-fatal | Pass | `AiRequestTelemetryTest` |
| U-23 registry bound, no content | Pass | `InMemoryAiRequestRegistryTest` |
| I-01..I-03 auto-config, no provider, mock restrictions (local/dev/test ok; prod, mixed with prod, qa/uat/production/staging, none rejected; lazy-init) | Pass | `AiAutoConfigurationTest`, `AiProductionValidatorLazyInitTest` |
| Config bounds + bean validation startup failures | Pass | `AiAutoConfigurationTest`, `AiPropertiesTest` |
| Fail-fast prompts/schemas (duplicate, unreadable, unknown field, placeholders, mismatch, unknown schema, remote id/dialect/ftp refs) | Pass | `StartupFailFastTest` |
| S-01 injection: variables/content delimited, system text untouched, 18 look-alike variants, linear-time hostile input | Pass | `AiTextSanitizerTest`, `ClasspathAiPromptServiceTest`, `DefaultAiCoordinatorTest` |
| S-02 model output returned as inert data, never executed | Pass (no execution path exists) | `DefaultAiCoordinatorTest` |
| S-03/04 no prompt/response/raw provider text in logs or error messages | Pass | `DefaultAiCoordinatorTest`, `JsonSchemaAiStructuredOutputServiceTest` |
| Error-code → HTTP status table (all 14 codes) | Pass | `ErrorCodeStatusMappingTest`, `AiErrorReasonTest` |
| I-04 Bedrock mocked SDK, I-05..I-13 web, I-14 live, F-01..F-08 frontend | Not run — M3/M4/M5 not started | — |
| S-09 dependency vulnerability scan | Not performed (no scanner configured) | — |
