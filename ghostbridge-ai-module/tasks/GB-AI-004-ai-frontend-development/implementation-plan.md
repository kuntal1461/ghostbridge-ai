# Implementation Plan — GB-AI-004

## Phases (verified one at a time)
| Phase | Goal | Deliverables |
|---|---|---|
| F1 | Tooling | Vite, TS project refs, ESLint, Vitest, `index.html`, scripts. `typecheck`/`lint`/`build`/`test` run on a stub. |
| F2 | API layer | `api/types.ts`, `errors.ts`, `client.ts`, `aiApi.ts`, response guards, auth hook, config, tests (all status codes, cancel, timeout, non-JSON). |
| F3 | Shell | Layout, routing, nav, theme CSS, ErrorBoundary, shared components (Skeleton, ErrorPanel, EmptyState, StatusBadge, CopyButton, JsonViewer). |
| F4 | Read pages | Provider Status, Usage & Limits, Dashboard. |
| F5 | Write pages | Playground and Structured Output with variable editor, validation, abort. |
| F6 | Dev mock | Mock transport (DEV only), banner, production-absence check. |
| F7 | Hardening | Secret scan test, a11y pass, bundle check, `mvn clean verify`. |
| F8 | Reviews | `ghostbridge-security-reviewer`, `ghostbridge-code-quality-reviewer`, fixes, re-verification. |

## UI architecture
```
src/
  config/env.ts              parse and validate VITE_* (pure, testable); mock only when DEV
  api/                       types.ts, errors.ts, client.ts, aiApi.ts, guards.ts, auth.ts, mock/ (DEV only)
  hooks/                     useApiResource (GET + refresh + abort), useApiMutation (POST + in-flight guard + abort), useCopy
  components/                layout (AppShell, NavBar), feedback (Skeleton, ErrorPanel, EmptyState, MockBanner, ErrorBoundary),
                             display (StatusBadge, KeyValue, StatCard, JsonViewer, TextOutput, UsageSummary, CopyButton),
                             form (VariableEditor, OptionsFields, Field)
  features/                  dashboard/, playground/, structured/, provider/, usage/  (page + feature hooks + validation)
  styles/                    tokens.css (design tokens), app.css
  index.ts                   module exports for embedding in GhostBridge App
```
Rules: pages compose components; components never call `fetch`; only `api/client.ts` does. Features own validation logic (pure functions in `validation.ts`) so presentation stays thin.

## API service boundaries
`client.ts` exposes `request<T>(spec)` handling base URL, auth headers (from the auth hook), JSON body, timeout (`AbortController` combined with the caller's signal), status → `ApiError` mapping, `Retry-After`, problem-detail parsing with safe fallback for non-JSON bodies. `aiApi.ts` exposes four typed functions (`generate`, `generateStructured`, `getProviderStatus`, `getUsage`) and runs a runtime guard on each response. No retries in the client.

## Technical decisions
| Decision | Alternatives considered | Reason |
|---|---|---|
| Plain CSS with custom properties | Tailwind, MUI | No design system exists; zero extra dependency; easy theming for the host app. |
| `react-router-dom` | State-based tabs | Real URLs, deep links, a11y semantics for navigation; single justified runtime dependency. |
| Hand-written JSON tokenizer rendering React spans | `highlight.js`/`prism` | No HTML string generation → no XSS surface; no dependency. |
| Hand-written response guards | `zod` | Four small shapes; avoids a dependency. |
| `vi.stubGlobal('fetch')` + a small fake | MSW | MSW adds a service-worker/node interceptor stack; one fetch stub suffices. |
| Mock selected by compile-time `import.meta.env.DEV` + dynamic import | Runtime flag only | Dead-code elimination guarantees no mock in production; verified by scanning `dist`. |
| Auth hook (`setAuthProvider`) returning request headers/credentials mode, in memory | Token in `localStorage`, invented login | Backend auth mechanism is undecided; keeps cookies (credentials + CSRF) and bearer strictly separate. |
| Schema list from `VITE_AI_STRUCTURED_SCHEMAS` (default `generic-summary`) | Free-text schema name, new backend discovery endpoint | Backend only accepts approved ids and offers no listing; backend contract is frozen. Selection from a configured list prevents arbitrary schema entry. |
| TypeScript 5.x | TypeScript 7 | Existing pin `^5.6`; typescript-eslint compatibility. |
| ESLint 9 flat config | ESLint 10 | Plugin peer-dependency compatibility (jsx-a11y). |

## Dependencies
Runtime: `react-dom`, `react-router-dom`. Dev: `vite`, `@vitejs/plugin-react`, `vitest`, `jsdom`, `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`, `@types/react-dom`, `eslint`, `@eslint/js`, `typescript-eslint`, `eslint-plugin-react-hooks`, `eslint-plugin-jsx-a11y`, `globals`. Versions are recorded in implementation.md after install. `npm audit` is run and reported honestly.

## Frontend testing strategy
Vitest + RTL + jsdom. Layers: unit (env, guards, validation, error mapping, JSON tokenizer), API integration against a stubbed `fetch` (every status, Retry-After, abort, timeout, non-JSON, missing fields), component tests per page (loading, success, empty, errors, 401/403/429), security tests (hostile HTML renders as text, no `innerHTML`, secret-pattern scan of `src`, no `localStorage` writes), mock-mode tests (DEV on/off, production off), and a post-build script asserting `dist` has no mock marker. No test needs Ollama or network.

## Rollback considerations
Frontend-only: delete the added files under `ghostbridge-ai-frontend` and revert `package.json`/`tsconfig.json`. The only non-frontend source change is the added Java test `AiWebPermissiveChainTest` (test-only) and review/task markdown.
