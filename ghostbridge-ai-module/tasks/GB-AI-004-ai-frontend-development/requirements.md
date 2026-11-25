# Requirements — GB-AI-004

## Objective
Build `ghostbridge-ai-frontend` (React 19, TypeScript, Vite) into a polished dark-mode AI management and testing console that talks only to the GhostBridge AI Web REST API: Dashboard, AI Playground, Structured Output, Provider Status, Usage & Limits. It is authentication-ready but does not invent a login. Live authenticated inference is a later GhostBridge App integration milestone.

## Functional requirements
| ID | Requirement |
|---|---|
| FR-1 | Navigation with exactly five working entries: Dashboard, AI Playground, Structured Output, Provider Status, Usage & Limits. No decorative links. |
| FR-2 | Dashboard shows provider status (`configured`, `status`, `provider`, `model`, `capabilities`), usage summary and rate limit, using only returned fields; loading skeletons, empty, 401/403/unavailable states, manual refresh. No invented figures; "recent requests" is not offered because the API has none. |
| FR-3 | Playground calls `POST /generate` with `AiGenerateRequestVO` fields: prompt template name, optional version, variables, optional untrusted content, optional model id, optional `maxOutputTokens` and `temperature`. Shows `text`, optional `structured`, model, request id, usage and duration. Copy, reset, loading indicator, error panel. |
| FR-4 | Structured page calls `POST /generate/structured` with `schemaName` chosen from a configured list of approved identifiers (no arbitrary schema authoring), variables, untrusted content, options. Shows formatted, syntax-highlighted JSON, copy JSON, validation errors, empty/malformed handling. |
| FR-5 | Provider Status page calls `GET /provider/status`. Labels `configured` as "configuration status, not a live availability check". |
| FR-6 | Usage page calls `GET /usage`. Shows request/success/failure counts, input/output/total tokens, `requestsWithTokenUsage`, rate-limit values, and labels the scope as "retained history (last N requests of all callers), not lifetime usage". |
| FR-7 | 429 shows a rate-limit message and the `Retry-After` value only when the header is present and parseable; no invented timing; no automatic retry; submit disabled while a request is active. |
| FR-8 | One central API client: configurable base URL and timeout, typed requests/responses, JSON parsing, safe non-JSON error handling, cancellation, mapping for 400, 401, 403, 413, 429, 502, 503, 504, network and timeout errors. No `fetch` outside it. |
| FR-9 | Pluggable authentication hook (in-memory only). 401 → "authentication required" state; 403 → "access denied" state. |
| FR-10 | Development-only mock API, enabled explicitly, absent from production builds, clearly labelled, never a fallback for failed real calls. |

## Non-functional requirements
| ID | Requirement |
|---|---|
| NFR-1 | Responsive at ≥320 px (mobile drawer/stacked nav), tablet and desktop. |
| NFR-2 | Accessibility: keyboard operable, visible focus, labelled controls, `aria-live` status/error announcements, WCAG AA contrast for text, skip link, `prefers-reduced-motion` honoured. |
| NFR-3 | Strict TypeScript, no `any`, focused components (< ~150 lines), business logic in hooks/services. |
| NFR-4 | Minimal dependencies: react-dom, react-router-dom (runtime); vite, plugin-react, vitest, Testing Library, jsdom, eslint stack (dev). No UI kit, no state library, no syntax-highlighting library, no MSW. |
| NFR-5 | Scripts: `dev`, `build`, `preview`, `typecheck`, `lint`, `test`. |

## Security requirements
| ID | Requirement |
|---|---|
| SEC-1 | Never call Ollama or any provider; never ask for, store or embed API keys; no `VITE_*` secrets. Only non-secret `VITE_API_BASE_URL` (plus non-secret `VITE_API_TIMEOUT_MS`, `VITE_AI_STRUCTURED_SCHEMAS`, `VITE_AI_MOCK`). |
| SEC-2 | Model output is rendered as plain text through React escaping or tokenised spans; no `dangerouslySetInnerHTML`, no `innerHTML`, no `eval`, no model-controlled links. |
| SEC-3 | No credentials in `localStorage`/`sessionStorage`; auth material lives only in memory behind the auth hook. No hardcoded tokens. |
| SEC-4 | Server error text rendered as escaped text and length-limited; no stack traces; correlation id shown for support. |
| SEC-5 | Prompts and responses are not logged and not persisted by the frontend. |
| SEC-6 | Mock mode is impossible in production builds (compile-time `import.meta.env.DEV` gate, code absent from `dist`). |
| SEC-7 | If cookie sessions are adopted later, credentials mode and the CSRF header are explicit in the auth hook; cookies and bearer tokens are separate strategies. |
| SEC-8 | External links use `rel="noopener noreferrer"`; none are generated from model output. |

## Out of scope
- Real login/session UI, GhostBridge App workflows, backend changes, live Ollama verification, per-requester token budget (backend), prompt/schema discovery endpoints, persistence of prompts, internationalisation, light theme.

## Acceptance criteria
- [ ] Five pages implemented and reachable; nothing decorative.
- [ ] `npm run typecheck`, `lint`, `test`, `build` all pass.
- [ ] All 24 required test topics from the milestone brief are covered by automated tests (see test-cases.md).
- [ ] Production bundle contains no mock code and no secret patterns (scripted check).
- [ ] 401/403/413/429/502/503/504/network/timeout/cancel states verified.
- [ ] Security and code-quality reviews run on the final source; no open Critical/High.
- [ ] `mvn clean verify` still passes.
