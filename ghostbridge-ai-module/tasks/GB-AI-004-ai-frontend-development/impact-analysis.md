# Impact Analysis — GB-AI-004

## Affected projects and consumers
| Project / module | Impact |
|---|---|
| `ghostbridge-ai-frontend` | New application code, tooling and dependencies. |
| `ghostbridge-ai-web` | **No behaviour change.** One new test class (`AiWebPermissiveChainTest`) from Milestone 4 closure. |
| `ghostbridge-ai-core`, `ghostbridge-springboot` | None. |
| `ghostbridge-app` (future) | Will embed or link the AI frontend (`src/index.ts` exports) and must supply authentication and a same-origin or CORS-configured deployment. |
| Maven build | Frontend is not a Maven module; `mvn clean verify` unaffected. `node_modules/`, `dist/` are git-ignored (`dist/` added to `.gitignore` if missing). |

## Runtime behaviour changes
- None server-side. Browser sends only the four documented requests under `/api/v1/ai`.

## Authentication dependencies
- Backend requires an authenticated principal with role `AI_USER`; none can authenticate today, so a real backend returns 401 to the UI (rendered as "authentication required"). Real use needs the GhostBridge App to (1) provide a `GhostBridgeHttpSecurityCustomizer`/auth mechanism, (2) register the matching browser strategy through `setAiAuthProvider`.
- Stateless header auth: provide the header from memory. Session cookies: set `credentials: 'include'` and send the CSRF token header (Spring CSRF is on when `ghostbridge.security.stateless=false`); the hook supports both explicitly.

## Compatibility risks
- Request/response types are hand-copied from Java records; a backend rename breaks the UI silently. Mitigation: runtime response guards, tests pinned to the contract in `existing-implementation.md`, compile-time types in one file.
- No prompt/schema discovery: the UI's default `generic-structured` and `generic-summary` assume the shipped core resources; operators with other schemas must set `VITE_AI_STRUCTURED_SCHEMAS`.
- `Retry-After` unreadable cross-origin unless exposed (SEC-M4-10); UI degrades to generic retry guidance.
- Error `detail` text is server-supplied; rendered as escaped text only.
- `/usage` scope is retained history of all callers filtered to the caller; UI labels it as such.

## Potential breaking changes
None for existing code. The `package.json` `main`/peer dependency shape stays; `react-dom` and `react-router-dom` become peers/devs.

## Mitigations
Tests listed in test-cases.md; reviews by both Kiro-defined reviewer roles; mock mode only in dev.
