# Implementation — GB-AI-004

Record of what was actually done. Updated as each milestone completes. Work performed by Claude Code (not Kiro).

## Milestone 0: Milestone 4 closure
- **Changes:** Added `AiWebPermissiveChainTest` (permit-all `SecurityFilterChain`, CSRF off: anonymous → 401, non-role → 403, role → 200). Appended closure verification sections to GB-AI-003 `security-review.md` and `code-quality-review.md`. CQ-01 verified fixed (no `ghostbridge-ai-module/src`). SEC-M4-01 re-reviewed: remains accepted; production must enable `ghostbridge.ai.budget`.
- **Decisions:** closure is implementer-verified; no independent round-2 review was run, and the reports say so.

## Milestone 1-6: Frontend (F1-F7)
- **Stack and versions (as installed):** React 19.3, react-dom 19.3, react-router-dom 7.18, Vite 8.3, Vitest 5.1, jsdom 31, Testing Library (react 16.3, jest-dom 8, user-event 14.6), ESLint 9.39 + typescript-eslint 8.73 + react-hooks 7.1 + jsx-a11y 6.10, TypeScript 5.x, `@types/node` 22. `npm audit`: 0 vulnerabilities.
- **Scripts:** `dev`, `build` (tsc + vite build + `scripts/check-dist.mjs`), `preview`, `typecheck`, `lint`, `test`.
- **Pages:** Dashboard `/`, AI Playground `/playground`, Structured Output `/structured`, Provider Status `/provider`, Usage & Limits `/usage`.
- **API services (`src/api`):** `client.ts` (single HTTP entry: timeout, abort, status mapping, Retry-After, safe non-JSON handling, no retries), `transport.ts` (mock/real fetch selection), `aiApi.ts` (4 typed endpoints), `guards.ts` (runtime response validation), `errors.ts` (`ApiError` with fixed safe messages), `auth.ts` (in-memory `AuthStrategy`: none by default, `bearerAuth`, `sessionCookieAuth` with required CSRF header name), `types.ts` (contracts copied from the Java VOs), `mock/mockFetch.ts` (dev only).
- **Hooks:** `useApiResource` (GET, refresh on demand, abort on unmount), `useApiMutation` (single in-flight POST, cancel/reset/unmount abort), `useCopy`.
- **Components:** layout (`AppShell`, `navigation`), feedback (`Skeleton`, `ErrorPanel`, `errorCopy`, `EmptyState`, `MockBanner`, `ErrorBoundary`, `PageHeader`, `ResourceView`, `MutationResult`), display (`Card`, `StatCard`, `StatusBadge`, `CopyButton`, `JsonViewer`, `TextOutput`, `ResultMeta`), form (`TextField`, `SelectField`, `RequestFields`, `FormActions`). Feature code: `features/<page>/…`, shared form validation in `features/requestFields.ts`.
- **Decisions:**
  - The backend has no free-form prompt and no discovery endpoint, so the Playground takes a template name plus variables, and the structured schema list comes from `VITE_AI_STRUCTURED_SCHEMAS` (default `generic-summary`).
  - Mock gating must be the literal `import.meta.env.DEV && config.mockEnabled` at the dynamic import. A first version gated through an exported constant and the post-build check showed the mock chunk was still emitted; fixed and the check now fails the build if it recurs.
  - JSON highlighting is a tokenizer rendering React text nodes; no HTML string is ever produced.
  - 5xx server text is dropped; only 4xx `detail` and field messages are shown, escaped and length-capped.
  - Added a `--border-control` token after a measured 1.5:1 border contrast on form controls (now 4.1-4.9:1). Text contrast ratios computed 7.3:1 to 16.3:1. Contrast was computed from the tokens, not audited with a tool.
- **Not done / limits:** no backend change; no live Ollama or authenticated end-to-end test; no coverage or Sonar; no visual browser review; `package-lock.json` must be committed with the change.

## Review fixes (after round 1)
Security: S-1 (https required outside local development), S-2 (text display cap), S-4 (marker regex generalised to `!mock-\d{3}`), S-5 (comment corrected), S-7 (peer ranges aligned, react/react-dom/react-router-dom added to devDependencies; lockfile to be committed). Code quality: CQ-1 (boundary keyed by route), CQ-2 (extracted `FormActions`, `MutationResult`), CQ-3, CQ-4 (focus + title on route change), CQ-5 (test-cases corrected), CQ-13, CQ-17, CQ-19.
