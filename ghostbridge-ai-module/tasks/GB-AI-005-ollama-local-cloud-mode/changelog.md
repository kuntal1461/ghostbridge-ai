# Changelog — GB-AI-005

| Date | Change | Files |
|---|---|---|
| 2026-11-23 | Task documentation created | `tasks/GB-AI-005-ollama-local-cloud-mode/*` |
| 2026-11-24 | `local-enabled` flag, local/cloud settings, endpoint validator, provider local mode, tests, `.env.example` | `ghostbridge-ai-core` config/service/constants/tests, `.env.example` |
| 2026-11-24 | Review round 1 and fixes (link-local, allow-list hygiene, no-proxy local) | same, `security-review.md`, `code-quality-review.md` |
| 2026-11-24 | Safe provider selection: no default mode, local-only inference, cloud only explicit; tests, `.env.example`; manual local live test and one real local inference (phi3:3.8b) | `AiProperties`, `AiAutoConfiguration`, tests, `.env.example` |
| 2026-11-24 | Round-2 reviews and fixes | `security-review.md`, `code-quality-review.md` |
