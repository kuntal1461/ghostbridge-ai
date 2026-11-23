# Test Cases — GB-AI-003

Status column is updated after execution. Mock provider only; no Ollama credentials, no network.

| ID | Type | Scenario | Expected | Test class | Status |
|---|---|---|---|---|---|
| TC-1 | positive | Authorized generate with mock provider | 200, `AiGenerateResponseVO` JSON, `Cache-Control: no-store`, no secrets | AiRestControllerTest | Pass |
| TC-2 | positive | Structured generate with approved schema | 200, `structured` object present | AiRestControllerTest | Pass |
| TC-3 | security | No authentication on all four endpoints | 401 AUTHENTICATION_REQUIRED | AiWebSecurityTest | Pass |
| TC-4 | security | Authenticated without AI role | 403 ACCESS_DENIED; invalid body still 403 (not 400) | AiWebSecurityTest | Pass |
| TC-5 | negative | Blank/missing required fields, bad variable key | 400 with field errors | AiRestControllerTest | Pass |
| TC-6 | negative | Oversized body (declared and chunked) / oversized field bytes | 413 PAYLOAD_TOO_LARGE | AiRequestBodyLimitFilterTest, AiRestControllerTest | Pass |
| TC-7 | negative | Malformed JSON, unknown field | 400 MALFORMED_REQUEST | AiRestControllerTest | Pass |
| TC-8 | negative | Unknown schema / unknown prompt | 400 | AiRestControllerTest | Pass |
| TC-9 | positive | Provider status | only provider, model, status, capabilities; no `detail`, no key | AiRestControllerTest | Pass |
| TC-10 | security | Usage shows only own data; other caller's requests invisible | counts per caller | AiRestControllerTest | Pass |
| TC-11 | positive | ResponseVO serialization (field names, null omission) | exact keys | AiRestControllerTest | Pass |
| TC-12 | security | Per-requester limit exceeded | 429 RATE_LIMITED + Retry-After in [1,60] | AiRequesterLimiterTest, AiWebSecurityTest | Pass |
| TC-13 | security | Global limit still enforced across requesters | 429 | AiGlobalLimitIntegrationTest | Pass |
| TC-14 | security | Requester A exhausted, B unaffected | B 200 | AiRequesterLimiterTest | Pass |
| TC-15 | security | Spoofed `requesterId` body field / `X-Requester-Id`, `X-User` headers | body → 400; headers ignored, limit keyed by principal | AiWebSecurityTest | Pass |
| TC-16 | security | Concurrent requests (N threads) never exceed limit/in-flight cap | exactly limit admitted | AiRequesterLimiterTest | Pass |
| TC-17 | security | State expiry and max-tracked bound; fail closed when full | map size ≤ max; new requester 429 when full | AiRequesterLimiterTest | Pass |
| TC-18 | security | Bypass attempts: case/whitespace variants, anonymous token | distinct principals distinct keys; anonymous 401 | AiRequesterResolverTest | Pass |
| TC-19 | security | Retry-After: rate window yes; concurrency no; budget yes; provider 503 none | exact | AiExceptionAdviceTest | Pass |
| TC-20 | security | Credential non-disclosure (error bodies, status, usage) | no key/Authorization text | AiWebSecurityTest | Pass |
| TC-21 | security | CORS preflight allowed/denied; no wildcard credentials | shared config headers | AiWebSecurityTest | Pass |
| TC-22 | security | CSRF: stateless POST ok; `stateless=false` POST without token | 200 / 403 | AiWebStatefulCsrfTest | Pass |
| TC-23 | negative | Error mapping 400,401,403,413,429,502,503,504,500 with `code`, `reason` | table in requirements | AiExceptionAdviceTest, AiErrorMappingIntegrationTest | Pass |
| TC-24 | negative | Config/credential reasons collapse to PROVIDER_UNAVAILABLE | no leak | AiExceptionAdviceTest | Pass |
| TC-25 | integration | `security.enabled=false` → AI endpoints not registered | 404 | AiWebDisabledSecurityTest | Pass |
| TC-26 | regression | Core: `AiException.retryAfter`, limiter/budget retry values | exact seconds | AiLimiterTest, AiTokenBudgetTest, AiExceptionTest | Pass |
| TC-27 | regression | Existing module test suites | unchanged green | all | Pass |

## Execution log
| Date | Command | Result |
|---|---|---|
| 2026-11-23 | `mvn -o clean verify` (root, JDK 21) | BUILD SUCCESS: springboot-core 31, springboot-web 55, ai-core 274 (1 skipped live), ai-web 83 |
| 2026-11-23 | `mvn dependency:tree` (ai-core, ai-web) | no AWS, no cycle, no ai-web→Ollama coupling |
| 2026-11-23 | Sonar / dependency-vulnerability / secret scanners | **not run** (not installed) |

Note: TC-3/4/12/15/21/22 are covered by AiWebSecurityTest, AiWebStatefulCsrfTest; a permissive custom SecurityFilterChain test is still missing.
