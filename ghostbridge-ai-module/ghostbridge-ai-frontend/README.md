# ghostbridge-ai-frontend

React 19 + TypeScript + Vite console for the GhostBridge AI Web API (`/api/v1/ai/**`). It is not a Maven module.
Task record: `ghostbridge-ai-module/tasks/GB-AI-004-ai-frontend-development/`.

## Run

```bash
npm install
npm run dev          # http://localhost:5173, /api is proxied to DEV_PROXY_TARGET (default http://localhost:8080)
npm run typecheck && npm run lint && npm test
npm run build        # typecheck + bundle + scripts/check-dist.mjs (no mock code or secret patterns in dist)
```

Node 20+ is required. Copy `.env.example` to `.env.local` for overrides (git-ignored). Only the non-secret variables in
`.env.example` exist; **never** add an API key, token or any `VITE_*` secret: every `VITE_*` value is public.

## Authentication

The backend has no application login yet, so real calls answer 401 and the UI shows "Authentication required".
The host app plugs in its mechanism with `setAuthStrategy` (`bearerAuth` or `sessionCookieAuth`, see `src/api/auth.ts`).
Credentials live in memory only. Session cookies additionally need the CSRF header (`csrfHeaderName` is required).

## Development mock

`VITE_AI_MOCK=true npm run dev` serves labelled synthetic responses (no backend needed). Put `!mock-401`, `!mock-403`,
`!mock-413`, `!mock-429`, `!mock-502`, `!mock-503`, `!mock-504` or `!mock-slow` in a variable value to simulate errors.
The mock is compiled out of production builds and is never a fallback for failed real calls.

## Layout

`src/api` (typed client, guards, auth, mock) · `src/config` · `src/hooks` · `src/components` · `src/features/<page>` · `src/styles`.
Only `src/api/client.ts` (via `transport.ts`) performs HTTP.
