# Impact Analysis — GB-APP-001 (Milestone 6)

## Affected projects and consumers
| Project / module | Impact |
|---|---|
| `ghostbridge-ai-core` | Additive: `AiUsageSummary`, `AiUsageSummarizer`, `AiProviderReadiness` (logic moved out of AI Web). No contract change. |
| `ghostbridge-ai-web` | Additive property `ghostbridge.ai.web.rest-enabled` (default true: standalone behaviour unchanged); `AiExceptionAdvice` scoped by a marker annotation instead of one class; `DefaultAiApiService` refactored onto the shared helpers. Existing tests unchanged and green (88). |
| `ghostbridge-app-core` | First real content: application service, commands, properties. |
| `ghostbridge-app-web` | The executable application; repackaging enabled; auth; `AppAiController`. |
| `ghostbridge-ai-frontend` | Backward-compatible additions (optional `headerActions`, `form`, 204, unauthenticated hook, exports); `package-lock.json` moves to the workspace root. |
| `ghostbridge-app-frontend` | New app. |
| Repository | Root `package.json` + `package-lock.json`, `deploy/`, docs. |

## Runtime behaviour changes
- With AI Web's controller disabled the only AI controller is `AppAiController`; same paths, same VOs, same status codes.
- Cookie session + CSRF replace "no authentication": a browser must GET `/auth/csrf` first and send the token on every POST.
- Dev server binds to loopback only.

## Authentication dependencies
Users are configuration; no external IdP. Roles: `AI_USER` required for AI. `AiRequesterResolver` keys requesters by the user name (unique per configuration, enforced).

## Compatibility risks
- Anything that called AI Web with bearer/no auth now needs a session (there was no working authentication before).
- A per-requester limit applies per user across all browser tabs; local models are slow so `max-concurrent=1` per user can surprise a user with two tabs (429).
- In-memory sessions and limiters are lost on restart and are per instance (single instance by design).
- The `Boolean`-based Ollama mode selection (GB-AI-005) requires `local-enabled` to be set; the application yml provides it.

## Potential breaking changes
For library consumers constructing `DefaultAiApiService` or relying on `UsageTotals` (removed private class): none outside this repository. Developers must run `npm install` at the repository root now (per-project installs are replaced by the workspace).

## Mitigations
Tests listed in test-cases.md; both independent reviews; fail-fast configuration validation; deployment artifacts built and run locally.

## Round-2 impact (2026-11-24)
Modules touched: `ghostbridge-ai-core` (new shared classes, literals), `ghostbridge-ai-web` (`AiApiMapper`, `DefaultAiApiService`, `AiExceptionAdvice` use the shared helpers; public REST contract, status codes and JSON unchanged, covered by the existing 88 AI Web tests), `ghostbridge-app-core` (`DefaultAppAiService`, `AppAiService.providerStatus` return type `AiProviderOverview`), `ghostbridge-app-web` (decorator, controller, mapper, login handler, config). Dependency direction unchanged: app-web → app-core → ai-core; app-web → ai-web; ai-web → ai-core; no cycle. `AppAiService` is now implemented by two beans (`DefaultAppAiService`, `@Primary RequesterLimitedAppAiService`): injecting the interface yields the limited one; only the decorator injects the default by class. No HTTP or configuration contract changed.

## Round-3 impact (2026-11-25)
`deploy/` only plus small App Web/App Core/front end changes listed in implementation.md; no public API, configuration contract or Maven dependency changed. Deployment now depends on a Caddy build that includes a third-party module (`caddy-ratelimit`, pinned) and on pinned image digests (rebuild deliberately). Published ports are IPv4 only. The App trusts forwarded headers only from private proxy addresses in the `prod` profile.
