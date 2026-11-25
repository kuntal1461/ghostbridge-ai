# Security Review — GB-AI-004

> Provenance: executed by a Claude Code subagent following the Kiro role prompt in `.kiro/agents/prompts/`; it was not run by the Kiro agent runtime.

**Reviewer:** ghostbridge-security-reviewer  **Date:** 2026-11-23  **Round:** 1

## Scope and checks performed
Reviewed the final source of `ghostbridge-ai-module/ghostbridge-ai-frontend` (src/**, `scripts/check-dist.mjs`, `vite.config.ts`, `index.html`, `.env.example`, `package.json`) against `requirements.md` (SEC-1..SEC-8), `implementation-plan.md`, `impact-analysis.md` and the backend contract classes in `ghostbridge-ai-web` (`AiRestController`, `AiExceptionAdvice`). I did not modify the backend and did not review it beyond the contract.

Checks performed, with evidence:
- **XSS / AI output rendering.** Read `TextOutput.tsx`, `JsonViewer.tsx`, `lib/jsonTokens.ts`, `ErrorPanel.tsx`, `StatusBadge.tsx`, `ErrorBoundary.tsx` and every feature page. Model text, `structured` JSON, server `detail`, field errors, `requestId`/`correlationId`, `status`, `provider` and `model` are all React text children. Grep over non-test `src` finds no `dangerouslySetInnerHTML`, `innerHTML`, `eval`, `new Function`, `document.write`, `href=` built from data, `target=`, `localStorage`/`sessionStorage`/`document.cookie` or `console.*`. The only `href` is the static skip link `#main`. Class names built from data (`error-panel--${error.kind}`, `json-${token.kind}`, `badge--${tone}`) come from closed enums, not server strings. The JSON tokenizer regex is linear (no nested quantifiers on overlapping classes), and tokens are never interpreted as markup.
- **Secrets / `VITE_` variables.** Only `VITE_API_BASE_URL`, `VITE_API_TIMEOUT_MS`, `VITE_AI_STRUCTURED_SCHEMAS` and `VITE_AI_MOCK` exist (non-secret). `.env.example` has placeholders only. No provider host, Ollama key or token literal in source or `dist` (`check-dist.mjs` plus the `source.test.ts` scans).
- **Auth.** Read `api/auth.ts` and `api/client.ts`. The default strategy sends no credentials (`same-origin`, no headers). The bearer strategy uses `credentials: 'omit'` and takes the token from memory per request. The cookie strategy adds the CSRF header on unsafe methods only and never an `Authorization` header. The two are separate objects. 401 and 403 map to fixed copy; there is no redirect, no auto-retry and nothing persisted. A CR/LF token would make `fetch` throw and surface as a generic `network` error (no header-injection path). Request headers and bodies are never logged.
- **Request control.** `useApiMutation` ignores a second `run` while one is in flight, aborts on unmount, cancel and reset, and never retries. `useApiResource` loads once on mount and again only on a user-initiated refresh. There is no polling. The client has no retry logic. `Retry-After` is accepted only as `^\d{1,6}$` and at most 86,400 s.
- **Data leakage.** 5xx bodies are never rendered (`client.ts:60-62`). Non-JSON error bodies are discarded. The error body is truncated to 64 KiB, `detail` to 300 characters, field errors to 20 entries, and `code`/`reason`/`correlationId` to 64 characters. `cache: 'no-store'` and `referrerPolicy: 'no-referrer'` are set on every request, and `index.html` has `<meta name="referrer" content="no-referrer">`. The `ErrorBoundary` renders neither message nor stack.
- **Mock-mode safeguards, verified by me.** `transport.ts:12` uses the literal `import.meta.env.DEV && config.mockEnabled`, and `env.ts:70` gates the banner the same way. I ran `npm run build` and grepped `dist/assets/*.js` for `synthetic`, `mockFetch`, `mock-model`, `GhostBridge-Mock` and `Development mock`: 0 hits. I then built into the scratchpad with `VITE_AI_MOCK=true` set (`vite build --outDir <scratch>`) and grepped again: 0 hits, with the same 3-file output (single JS chunk, no mock chunk). So a hostile or accidental `VITE_AI_MOCK=true` cannot enable the mock in production. No scratch files were left in the repo (`dist/` is git-ignored build output).
- **Dev proxy / base URL.** `vite.config.ts` proxies only `/api`; the target comes from the non-embedded `DEV_PROXY_TARGET` and no credentials are configured. `sourcemap: false`. `parseBaseUrl` requires an absolute http(s) URL with no userinfo, so `javascript:`, `data:` and `user:pass@` are rejected.
- **Response guards.** `guards.ts` validates the types of every contract field and rejects mismatches as `invalid-response`. Variable names matching `^[A-Za-z]...` exclude `__proto__`. JSON display is capped at 200,000 characters.
- **Dependencies.** `npm audit` and `npm audit --omit=dev`: found 0 vulnerabilities (npm advisory database, run today). No other scanner (SCA, SAST, secret scanner) was run.
- **Commands run (in `ghostbridge-ai-frontend`):** `npm run typecheck` pass; `npm run lint` pass (no warnings printed); `npm test` 17 files / 147 tests passed; `npm run build` pass, including `check-dist: 3 files scanned, no mock code or secret patterns found`.

## Findings
| ID | Severity (Critical/High/Medium/Low) | File:line | Issue | Impact | Remediation | Status |
|---|---|---|---|---|---|---|
| S-1 | Low | `src/config/env.ts:37`; `src/api/client.ts:98` | A production build accepts an `http:` `VITE_API_BASE_URL` for any host. With the bearer strategy, `Authorization` and prompts would travel in cleartext. | Operator misconfiguration only (build-time value, not attacker-controlled). Credentials/prompts exposed on the network if used. | Reject or loudly warn on non-`https:` base URLs unless the host is loopback (or `DEV`). Prefer the documented empty/same-origin setting. | Open |
| S-2 | Low | `src/components/display/TextOutput.tsx:19`; `src/api/guards.ts:57`; `src/api/client.ts:109` | Success bodies are not size-bounded. `JsonViewer` truncates at 200,000 characters, but `TextOutput` renders the full `text`, and `response.json()` buffers the entire body. | A compromised, misbehaving or misconfigured backend/proxy could freeze the tab (client-side DoS). The backend bounds output tokens, so this is not exploitable by an ordinary caller. | Apply a display cap to `text` (like `MAX_RENDERED_JSON_CHARS`) with a "truncated" notice; optionally reject oversized responses via `Content-Length`. | Open |
| S-3 | Low | `src/hooks/useApiMutation.ts:43-53` | Cancel/Reset aborts only the browser request; the server may keep generating, and the UI immediately re-enables Submit. Repeated cancel-then-resubmit can leave several server-side generations running. | Cost/rate-limit consumption relies entirely on backend limits (rate limit, max concurrent). I did not verify the backend enforces them in this review. | Rely on, and keep tests for, backend concurrency/rate limits; optionally add a short client cool-down after cancel. | Open (accepted risk candidate) |
| S-4 | Low | `scripts/check-dist.mjs:9,27`; `package.json:9` | Mock detection is by four hard-coded text markers, and the script runs only as part of `npm run build`, not after a bare `vite build`. Changing the mock's strings silently weakens the check. | A future refactor could ship mock code without failing the guard. (Today the bundle is clean, verified above.) | Also assert structurally (for example, no chunk containing the `mock/mockFetch` module id) or add a build-output test; keep the markers in sync with `mockFetch.ts`. | Open |
| S-5 | Low | `src/api/mock/mockFetch.ts:2`; `src/config/env.ts:70`; `src/api/transport.ts:12` | The mock header comment says it is gated by `MOCK_ACTIVE`, but the actual gate that removes it is the literal `import.meta.env.DEV` in `transport.ts`. The dev condition is expressed in two places (`MOCK_ACTIVE`, `transport.ts`). | Maintenance hazard: someone "fixing" the transport to use `MOCK_ACTIVE` (an indirection `transport.ts:6-8` already warns is not folded) could re-introduce the mock into production. | Correct the comment; keep the literal gate and the dist check; add a test that fails if `transport.ts` stops containing the literal. | Open |
| S-6 | Low | `src/components/feedback/ErrorPanel.tsx:19,24`; `src/api/client.ts:61-62` | 4xx `detail` and field-error text from the server is shown to the user (escaped, 300-character cap). It is not an XSS vector, but any backend text that reflects caller input could be displayed as authoritative "Server message". | Low social-engineering / misleading-content risk only; no code execution. Backend advice is described as not reflecting provider detail. | Keep as is; optionally restrict to a known-code allowlist or label it as untrusted. | Accepted risk |
| S-7 | Low | `package.json:15-19`; `package-lock.json` (untracked in git) | `react-dom` and `react-router-dom` are peer dependencies with caret ranges, and `package-lock.json` is untracked (`git status`: `??`). A host build or a clean checkout may resolve different versions than the audited tree. | Supply-chain reproducibility: `npm audit` result (0 vulnerabilities) applies only to the currently locked tree. | Commit the lockfile and install with `npm ci` in CI; run `npm audit` in the pipeline. | Open |

No Critical, High or Medium findings.

## Missing tests
- Base-URL scheme policy: no test for an `http:` non-loopback URL (see S-1); `env.test.ts` covers rejection of unsafe schemes and credentials but not cleartext.
- A very large `text` response through `PlaygroundResult` (S-2).
- A test that fails if the literal `import.meta.env.DEV` gate in `transport.ts` is changed, and a test that runs the dist check against a build with `VITE_AI_MOCK=true` (I verified this manually only).
- Cancel then immediate resubmit behaviour (S-3).
- An end-to-end header/credential test through the real `client.ts` using the cookie strategy with `credentials: 'include'` against a cross-origin base URL (the auth tests cover headers and modes, not this combination).

## Limitations and remaining risks
- Backend authentication is not implemented yet; the UI returns 401 "authentication required" against a real backend. Authorization, CSRF and CORS correctness depend on the future GhostBridge App integration. A mock-free end-to-end test against a live backend was not performed.
- Rate limiting, output size caps and cost control are enforced only on the backend. This frontend cannot enforce them.
- No browser-level testing (CSP, real browsers, clipboard) was done; `jsdom` only. No CSP or other HTTP security headers are set by this frontend; they must come from the hosting layer (this review did not check them).
- Vite preview/production serving behaviour of the `/api` proxy was not tested; the proxy is documented as development-only.
- `npm audit` covers known advisories only. No SAST, secret-scanner or license scan was run.
- Residual: prompt injection and model-output trust are handled by treating all output as inert text; there is no execution or link generation, but a human can still be misled by model content.
- This review does not claim the application is free of vulnerabilities.

## Gate
Critical/High unresolved: 0. Result: pass (7 Low findings open or accepted; no Critical, High or Medium)

## Disposition by the implementer (2026-11-23, not independently re-reviewed)
| ID | Disposition |
|---|---|
| S-1 | Fixed: `http:` base URL rejected outside DEV unless localhost (`env.test.ts`). |
| S-2 | Fixed: `TextOutput` display cap (same limit as JSON) with notice and test. |
| S-3 | Accepted residual: browser cancel cannot stop server generation; backend per-requester concurrency (2) and rate limit bound the effect (SEC-M4-01 context). |
| S-4 | Partly fixed: marker regex generalised; the guard still runs only in `npm run build` (documented). |
| S-5 | Fixed (comment). |
| S-6 | Accepted: escaped, capped 4xx text is intentional so users see why a request was rejected. |
| S-7 | Fixed: dependencies/peer ranges; the lockfile is untracked until the user commits. |
