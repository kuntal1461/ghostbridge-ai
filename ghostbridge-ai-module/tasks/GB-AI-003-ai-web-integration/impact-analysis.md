# Impact Analysis — GB-AI-003

## Affected projects and consumers
| Project / module | Impact |
|---|---|
| ghostbridge-ai-web | New endpoints, auto-configuration, properties `ghostbridge.ai.web.*`. |
| ghostbridge-app-web | Already depends on ai-web: endpoints are registered there but return 401 until the app plugs authentication in. No code change. |
| ghostbridge-ai-core | Additive `AiException.retryAfter`; limiter/budget set it. Existing constructors unchanged. |
| ghostbridge-springboot-web | `GlobalExceptionHandler.statusFor` becomes `public static` (no behaviour change). |
| Frontend | None (out of scope). |

## Runtime behaviour changes
- `/api/v1/ai/**` exists (when security enabled): authentication + role required, per-requester limits, 64 KiB body cap for these paths.
- 429 responses from the global limiter/budget now carry `Retry-After` when the window end is known (previously none) — only visible through the new advice for AI endpoints.

## Compatibility risks
- Apps with a custom `SecurityFilterChain` must still authenticate; the interceptor rejects anonymous callers regardless.
- Per-requester defaults (20 req/min, 2 concurrent) are lower than the global default (60/4); operators tune `ghostbridge.ai.web.requester-limit.*`.
- Limiter state is per JVM: N instances multiply the effective per-user limit by N.

## Potential breaking changes
None for existing consumers.

## Security impact
New attack surface: authenticated model invocation (cost/abuse). Mitigated by role check, per-requester + global limits, bounded input, registered prompts only, approved models only, output validation, no secret exposure. Remaining: no per-requester token budget; no distributed limiter; response text is model output and must be encoded by any future UI (XSS handled in frontend milestone).

## Mitigations
Defaults restrictive; deny-by-default; tests for spoofing, isolation, concurrency, expiry, headers; independent reviews before closure.
