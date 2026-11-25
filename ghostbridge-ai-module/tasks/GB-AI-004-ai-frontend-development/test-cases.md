# Test Cases — GB-AI-004

Types: unit, component, api (API integration against a stubbed `fetch`), security. No test uses the network, Ollama or credentials. Status is updated only from executed runs (see execution log).

| ID | Type | Scenario (brief topic #) | Expected result | Test file | Status |
|---|---|---|---|---|---|
| TC-1 | component | Dashboard renders provider, usage and rate limit from API (#1) | Values shown exactly as returned; no invented fields | `features/dashboard/DashboardPage.test.tsx` | Passed |
| TC-2 | component | Navigation (#2) | Five links, each routes to its page, current page marked `aria-current` | `App.test.tsx` | Passed |
| TC-3 | unit/component | Playground validation (#3) | Blank/invalid prompt name, bad variable name, oversize value, bad temperature block submit with field errors; no request sent | `features/requestFields.test.ts`, `features/playground/PlaygroundPage.test.tsx` | Passed |
| TC-4 | component | Successful generation (#4) | Request body matches `AiGenerateRequestVO`; text, model, usage, duration displayed; copy works | `PlaygroundPage.test.tsx` | Passed |
| TC-5 | component | Structured rendering (#5) | JSON formatted and highlighted; copy JSON; only approved schema chosen | `StructuredPage.test.tsx` | Passed |
| TC-6 | component | Provider status (#6) | `configured`, `status`, `provider`, `model`, `capabilities`; label says configuration not live check | `ProviderStatusPage.test.tsx` | Passed |
| TC-7 | component | Usage (#7) | Counts/tokens/rate limit; scope labelled retained history | `UsagePage.test.tsx` | Passed |
| TC-8 | component | Loading states (#8) | Skeleton with `aria-busy`; submit disabled and busy while in flight | all page tests | Passed |
| TC-9 | component | Empty states (#9) | Empty capabilities, zero requests, absent rate limit, empty model text and `{}` JSON show explicit empty text. A structured response with no `structured` field is deliberately an `invalid-response` error (the backend guarantees the field), not an empty state | page tests | Passed |
| TC-10 | api | HTTP 400 with `errors[]` (#10) | `ApiError(kind=validation)`; field messages shown as text | `api/client.test.ts`, `PlaygroundPage.test.tsx` | Passed |
| TC-11 | api/component | HTTP 401 (#11) | kind `unauthenticated`; "Authentication required" state, no retry | `client.test.ts`, `DashboardPage.test.tsx` | Passed |
| TC-12 | api/component | HTTP 403 (#12) | kind `forbidden`; "Access denied" state | same | Passed |
| TC-13 | api | HTTP 413 (#13) | kind `payload-too-large` message | `client.test.ts` | Passed |
| TC-14 | api/component | HTTP 429 with/without `Retry-After` (#14) | Parsed seconds shown only when valid; none invented; no auto retry (fetch called once) | `client.test.ts`, `PlaygroundPage.test.tsx` | Passed |
| TC-15 | api | HTTP 502 (#15) | kind `bad-gateway`, generic message | `client.test.ts` | Passed |
| TC-16 | api | HTTP 503 (#16) | kind `unavailable` | `client.test.ts` | Passed |
| TC-17 | api | HTTP 504 (#17) | kind `gateway-timeout` | `client.test.ts` | Passed |
| TC-18 | api | Network failure (#18) | kind `network`, no raw error text shown | `client.test.ts` | Passed |
| TC-19 | api/component | Cancellation (#19) | Caller abort → kind `aborted`, not shown as error; unmount aborts in-flight; client timeout → kind `timeout` | `client.test.ts`, `hooks/useApiMutation.test.tsx` | Passed |
| TC-20 | security | Unsafe model HTML (#20) | `<img onerror>`/`<script>`/`javascript:` strings appear as literal text; no element created; no `dangerouslySetInnerHTML` in source | `components/OutputViews.test.tsx`, `features/playground/PlaygroundPage.test.tsx`, `security/source.test.ts` | Passed |
| TC-21 | api | Missing/ill-typed response fields (#21) | kind `invalid-response`, UI error panel, no crash | `api/aiApi.test.ts` | Passed |
| TC-22 | unit | Dev mock mode (#22) | With DEV and `VITE_AI_MOCK=true` the mock answers all four endpoints, responses labelled, banner shown, simulated 429/401 | `api/mock/mockFetch.test.ts`, `api/mock/devMockMode.test.tsx` | Passed |
| TC-23 | unit | Production mock prevention (#23) | `readEnv` ignores the flag when not DEV; build output has no mock marker (`scripts/check-dist.mjs`); real failures never produce mock success | `config/env.test.ts`, `api/mock/devMockMode.test.tsx`, `scripts/check-dist.mjs` | Passed |
| TC-24 | security | Secret absence (#24) | No `VITE_OLLAMA`, `OLLAMA_API_KEY`, provider hostnames, `Bearer <literal>`, `sk-…` keys, `localStorage`/`sessionStorage` use in `src`, `.env*`, `dist` | `security/source.test.ts`, `check-dist` | Passed |
| TC-25 | api | Non-JSON error body (HTML 502 page) | No parse crash; generic message; body not rendered | `client.test.ts` | Passed |
| TC-26 | api/security | Auth provider hook | Header/credentials from provider applied; absent by default; CSRF header only on unsafe methods when configured | `api/auth.test.ts` | Passed |
| TC-27 | component | ErrorBoundary | Render error shows fallback without leaking message | `components/feedback/ErrorBoundary.test.tsx` | Passed |
| TC-29 | component | Route change moves focus to `<main>`, updates title; ErrorBoundary recovers on navigation; http base URL rejected in production | `App.test.tsx`, `ErrorBoundary.test.tsx`, `config/env.test.ts` | Passed |
| TC-28 | backend | Permissive `SecurityFilterChain` regression (M4 closure) | 401/403 still enforced | `AiWebPermissiveChainTest` | Passed (3/3) |

## Execution log
| Date | Command | Result |
|---|---|---|
| 2026-11-23 | `mvn -o -B clean verify` (root, JDK 21), baseline | BUILD SUCCESS, 83 ai-web tests |
| 2026-11-23 | `mvn -o -B -pl ghostbridge-ai-module/ghostbridge-ai-web test -Dtest=AiWebPermissiveChainTest` | 3 tests, 0 failures |
| 2026-11-23 | `npm run typecheck`, `npm run lint`, `npm test`, `npm run build`, `npm audit` (Node 24.19) after review fixes | typecheck and lint clean; 17 files, 152 tests passed; build OK and `check-dist` clean; `npm audit`: 0 vulnerabilities |
| 2026-11-23 | `mvn -o -B clean verify` (root, JDK 21) after all changes | BUILD SUCCESS, 86 ai-web tests (83 + 3 new) |

Coverage was not measured (no coverage tooling configured; CQ-18). Sonar is not configured. Statuses above mean "the named test file passed in the runs logged here", not a coverage claim. No browser/visual check was performed; layouts were not viewed on real devices (dev server start and asset serving were smoke-tested with curl only).
