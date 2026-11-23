# Milestones — GB-AI-002 (Ollama Cloud Provider Integration)

This task is Milestone 3 of the AI framework (provider changed from Bedrock to Ollama Cloud by the user). Parent record: `../GB-AI-001-ai-framework-setup/milestone.md`.

| # | Milestone | Acceptance criteria | Status | Verified by |
|---|---|---|---|---|
| A | Docs and secret hygiene | Task docs complete; `.gitignore` + `.env.example`; scans recorded; no secret found | Complete | `git check-ignore`, fallback scans (2026-11-22) |
| B | Configuration and credential type | `AiSecret`, `AiProperties.Ollama`, validation, literals, safe startup failure; config tests green | Complete | `AiSecretTest`, `AiOllamaPropertiesTest`, `AiOllamaAutoConfigurationTest` |
| C | Ollama provider | `OllamaAiProvider`, VOs, G-M3-1 byte limits, error mapping; stub-server tests green | Complete (stub-tested, **not live-verified**) | `OllamaAiProviderTest` |
| D | Integration and redaction | Coordinator integration, regression, log/error/toString/metadata redaction tests; `mvn clean verify` green | Complete | `mvn -o clean verify` BUILD SUCCESS (31/55/255, 1 skipped live test) |
| E | Reviews and closure | Independent security + quality reviews; Critical/High fixed; secret scan; docs/Kiro spec updated | Complete (with caveats below) | Review rounds 1-2 (security + quality); fallback secret scans; `mvn -o clean verify` on JDK 21 and 25 |

Status values: Not started, In progress, Blocked, Complete (only after verification).

## Completion criteria (from the brief)
Provider implemented; credential handling backend-only; G-M3-1 satisfied; provider error handling works; tests pass; Git secret checks pass; independent reviews complete; no unresolved Critical/High; documentation updated. Without a live request the provider is reported as **stub-server-tested, not live-verified**.

## Blockers
- None. (A secret scanner is not installed; the regex fallback is used and reported as weaker. The compromised key must be revoked by its owner at the provider; this repository never contained it.)

## Verification results
- 2026-11-22: Phase 1 inspection and fallback secret scans completed. `mvn -o clean verify` BUILD SUCCESS: springboot-core 31, springboot-web 55, ai-core 255 (1 skipped manual live test). `dependency:tree`: no AWS SDK, no ai-web/app dependency; only pre-existing transitive `spring-web` via starter-json. Live Ollama request: **not made**.

## Milestone 3 completion record (2026-11-22)
**Status: complete, stub-tested, NOT live-verified.** Criteria: Ollama Cloud provider implemented behind `AiProvider` (yes); credential handling backend-only (masked secret type, header-safe check, OS-env fallback, no frontend/`VITE_` use); G-M3-1 satisfied (UTF-8 request byte limit before I/O, incremental response limit incl. chunked and declared length, `max_tokens` forwarded, overall deadline, redirects refused) and independently reviewed; provider error handling works (status/timeout/JSON mapping, retries via coordinator); tests pass (JDK 21: 31/55/269, 1 skipped live test); Git secret checks pass (tree, history, tracked and staged files; pre-commit hook enabled locally); independent reviews complete (security 1 Medium fixed + 6 Low; quality 3 Medium fixed + Lows; round 2 0 Critical/High, new Medium R2-01 fixed); no unresolved Critical/High; documentation and Kiro spec updated.

**Caveats (stated, not hidden):**
1. No real Ollama Cloud request has been made. Endpoint path, model id and OpenAI-compatible behavior come from the brief and are unverified against the live service.
2. Fixes made after review round 2 (hook rewrite incl. gitleaks combination, `javax.net.debug` guard, extra tests) were verified by tests and a throwaway-repo hook matrix but **not independently re-reviewed**.
3. Reviews were Claude Code subagents applying the Kiro reviewer prompts, **not Kiro runs**. Kiro steering/hooks have not been loaded in Kiro.
4. Secret scanning used a regex fallback (Gitleaks/trufflehog not installed). Sonar and dependency-vulnerability scans were not run. The pre-commit hook is advisory (`--no-verify`); a CI/server-side scan is still recommended.
5. The previously shared Ollama key must be revoked by its owner; it never appeared in this repository or its history.
6. Remaining Low/Info items are listed in the review reports (JDK debug-switch limits, retry double-billing, proxy behavior, Actuator exposure in consuming apps, minor test/Javadoc items).
7. **M4 is not started:** G-M4-1 (per-requester limits from trusted identity) and G-M4-2 remain mandatory before any AI Web endpoint.
