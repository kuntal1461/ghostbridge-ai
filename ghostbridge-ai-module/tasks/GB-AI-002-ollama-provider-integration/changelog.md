# Changelog — GB-AI-002 (Ollama Cloud Provider Integration)

| Date | Change | Files |
|---|---|---|
| 2026-11-22 | Task folder and documentation created; secret review of tree and history (no credentials found; scanner not installed) | `ghostbridge-ai-module/tasks/GB-AI-002-ollama-provider-integration/*` |
| 2026-11-22 | Secret hygiene: `.gitignore` patterns, `.env.example` | `.gitignore`, `.env.example` |
| 2026-11-22 | Ollama provider: `AiSecret`, `AiProperties.Ollama`, auto-config, `OllamaAiProvider`, wire VOs, bounded body subscriber, literals; tests and optional live test; Kiro spec updated | `ghostbridge-ai-core/**`, `.kiro/specs/**`, `ghostbridge-ai-module/tasks/GB-AI-001-*/milestone.md` |
| 2026-11-22 | Review round 1 fixes: header-safe credential check, loopback-only test constructor, JDK debug-switch guard, AutoCloseable client, OS-env-only fallback, URL trimming, test hardening; `.gitignore` additions; pre-commit hook and allowlist | `ghostbridge-ai-core/**`, `.gitignore`, `.githooks/**` |
| 2026-11-22 | Review round 2 fixes: hook regex (case-insensitive, `.env.example`, non-ASCII, gitleaks combined, fail closed), `javax.net.debug` guard, close/destroy tests, doc corrections; Milestone 3 marked complete with caveats | `.githooks/**`, `ghostbridge-ai-core/**`, task docs |
