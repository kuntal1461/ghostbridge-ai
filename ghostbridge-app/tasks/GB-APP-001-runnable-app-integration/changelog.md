# Changelog — GB-APP-001 (Milestone 6)

| Date | Change | Files |
|---|---|---|
| 2026-11-24 | Task documentation, design and impact analysis (before code) | `ghostbridge-app/tasks/GB-APP-001-*` |
| 2026-11-24 | AI Core helpers; AI Web switch and advice marker; refactor of `DefaultAiApiService` | `ghostbridge-ai-core`, `ghostbridge-ai-web` |
| 2026-11-24 | App core service and App web controller, authentication, configuration | `ghostbridge-app-core`, `ghostbridge-app-web` |
| 2026-11-24 | npm workspace; App frontend; AI frontend host hooks; mock aligned to the real schema | root `package.json`, `ghostbridge-app-frontend`, `ghostbridge-ai-frontend` |
| 2026-11-24 | Deployment artifacts (first written unverified; built and run on 2026-11-25, see the later entry) | `deploy/` |
| 2026-11-24 | PROJECT_STRUCTURE.md updated | `doc-architecture/PROJECT_STRUCTURE.md` |
| 2026-11-24 | Independent reviews round 1 recorded; fixes: native forwarded headers in prod (S-1), user lock scoped to address (S-2), short anonymous session (S-3), prod insecure-cookie guard (S-5), fresh CSRF before login and safe redirect target (S-6/S-7), docs (CQ-04/13). Duplication items CQ-01..03 deferred | `ghostbridge-app-web`, `ghostbridge-app-core`, `ghostbridge-app-frontend`, `doc-architecture/PROJECT_STRUCTURE.md` |
| 2026-11-24 | Round 2: CQ-01..03 resolved (shared AI Core `AiStructuredOutputs`, `AiProviderReadiness.overview`, `AiDurations`; `RequesterLimitedAppAiService`; mapper reuse); login handler uses the configured session timeout; address-scoped lockout / session timeout / session-id-change tests; `AppRealHttpTest` hardened and investigated | `ghostbridge-ai-core`, `ghostbridge-ai-web`, `ghostbridge-app-core`, `ghostbridge-app-web` |
| 2026-11-24 | Round-2 reviews recorded; R2-1 fixed (lazy CSRF token read before the anonymous session timeout is applied); decorator and utility tests strengthened; PROJECT_STRUCTURE app-web row | `AuthRestController`, tests, `doc-architecture/PROJECT_STRUCTURE.md` |
| 2026-11-25 | Final acceptance work: Docker deployment built and run; Caddy edge rate limits, network split, pinned images, Ollama cloud disabled; real-browser E2E (24/24); low findings fixed (CQ-06/07/11/14/15/19/23/26/27/33/34/35/38); runbook allow-list and public-host conditions; evidence folder; round-3 reviews | `deploy/`, `evidence/`, app-web/app-core/ai-core/frontend, docs |
