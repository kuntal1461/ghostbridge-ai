# Implementation — GB-AI-001 (AI Framework Setup)

Record of what was actually done. Updated as each milestone completes. Planned design: `implementation-plan.md`.

## Milestone 1: Discovery and planning
- **Changes:** Documentation only. No source, configuration or POM change.
- **Files created:** this task folder (`existing-implementation.md`, `requirements.md`, `implementation-plan.md`, `impact-analysis.md`, `test-cases.md`, `milestone.md`, `tasks.md`, `implementation.md`, `changelog.md`); `.kiro/specs/ghostbridge-ai/{requirements,design,tasks}.md`.
- **Decisions:**
  - Existing `templates/tasks/` and `.kiro/agents/` reused; nothing recreated. (The prompt's `ghostbridge-ai/templates/tasks/` path does not exist; the real path is `templates/tasks/`.)
  - Kiro spec lives at `.kiro/specs/ghostbridge-ai/` as already planned in `.kiro/README.md`.
  - OpenAPI/springdoc deferred (D11) per `architecture.md`.
- **Deviations from the original request:** `.kiro/steering/` and `.kiro/hooks/` do not exist yet (the prompt assumed existing steering); they are created in T1.5/T1.6 rather than "reused".
- **Honest evidence note:** these spec files were authored with Claude Code in Kiro's spec format (requirements → design → tasks). They have not yet been opened or executed in the Kiro IDE; Kiro-specific evidence (spec task execution, hook runs, agent runs) must be recorded only when it actually happens.

## Milestone 2: AI Core foundation (remediation done; round-3 delta review pending)
- **Built:** shared `ErrorCode`/`statusFor` change; ai-core DTOs, `AiProperties`, `AiException`/`AiErrorReason`, `AiClient`/`AiProvider`, mock provider, coordinator, classpath prompts, JSON-schema validation, limiter/retry/budget, telemetry, auto-configuration, mock-profile guard; `GhostBridgeAiLiterals` (`com.ghostbridge.constants`, normal class import, no static/wildcard imports; rule added to `rules/rules.md`); Kiro steering/hooks.
- **Remediation (round 1 findings):**
  - F-01: strict reader (`FAIL_ON_TRAILING_TOKENS`, `STRICT_DUPLICATE_DETECTION`), whole-output fence only, response text = canonical validated JSON. F-08: errors report schema evaluation paths only.
  - F-02: each variable sanitized and wrapped in the untrusted-data delimiters; generic prompt variable is now `text`.
  - F-03: delimiter look-alike neutralization on a canonical copy (NFKC, format characters and combining marks removed, U+2028/2029 stripped, tolerant separators and bracket variants). Defence in depth only; Cyrillic/other homoglyphs and a fixed public delimiter remain residual risks, documented in `AiTextSanitizer`.
  - CQ-01/F-14: `AiRequest` validates null names/values, variable count (32) and name length (64) → typed errors. CQ-02/F-09: unexpected runtime failures wrapped as `PROVIDER_FAILURE` with telemetry. CQ-03/F-11: duration, backoff, window, price and rate bounds. CQ-04: injectable resource patterns + 12 fail-fast tests. F-07/CQ-10/F-15: duplicate schema/provider names fail startup.
  - CQ-05: error contract (below). F-10: mock only when `local`/`dev`/`test` active and `prod` not (mixed profiles covered). CQ-07 idempotent permit; CQ-08 `INTERRUPTED`; CQ-12 separately overridable beans; CQ-13 exhaustive `statusFor` (no `default`); F-12 correlation/requester id validation; CQ-18 Javadoc on contracts.
- **Remediation (round 2 findings, verified by failing tests first):**
  - N-01 (Medium, introduced by the F-03 fix): delimiter regex made linear (possessive quantifiers); timing test (50k/200k-char hostile whitespace) now passes (before: minutes).
  - N-02: schemas may use only the standard dialect, local `$ref`/`$dynamicRef` and a scheme-less `$id`; remote id/dialect/ftp fixtures fail startup (before: remote fetch at startup).
  - N-03: `LazyInitializationExcludeFilter` keeps the mock guard eager; `SpringApplication` test with `prod` + `lazy-initialization=true` now fails startup (before: started and served mock output).
  - N-04: null prompt name → `INVALID_INPUT`, recorded as `unknown`. N-07 (quality N-01): telemetry is non-fatal. F-03 residuals: combining marks, U+2010, `.` separator, ‹ 〈 brackets now escaped. N-05: overclaiming test names corrected. N-08: redaction limitation documented.
- **Error contract (HTTP):** invalid input 400 (`INVALID_INPUT`); oversized input 413 (`PAYLOAD_TOO_LARGE`); rate limit/concurrency 429 (`RATE_LIMITED`); token-budget exhaustion 429 with distinct code `QUOTA_EXCEEDED` (documented policy status); invalid/oversized/failed upstream 502 (`UPSTREAM_ERROR`); unavailable/missing config/credentials/interrupted 503; timeout 504.
- **Deferred gates (mandatory, recorded in `tasks.md`/`milestone.md`):** G-M3-1 provider byte limits before any real provider; G-M4-1 per-requester limits from trusted authentication identity before any AI Web endpoint (global limits stay as secondary safeguard); G-M4-2 `Retry-After`, DTO bounds, `reason` exposure, and converting `AiRequest` construction errors to 400/413 in the web mapping.
- **Accepted / documented, not fixed:** retry amplification and per-attempt timeout (F-05), budget check-then-act overshoot and no-usage providers (F-06; revisit M3), ReDoS risk of operator redaction patterns (F-13), `classpath*:` scanning of all JARs (F-07 residual), mock accepted with e.g. `dev,uat` (N-06, per the approved rule), unqualified `Clock` bean (quality N-04), `DefaultAiCoordinator` size/7 constructor parameters (quality N-02, revisit in M3), partially open Low/Info quality items (CQ-16 only limiter has a concurrency test, CQ-18 some impl Javadoc, CQ-19/20/22/25).
- **Verification (2026-11-21, after final fixes):** `mvn -o clean verify` BUILD SUCCESS; tests springboot-core 31, springboot-web 55, ai-core 173. No AWS SDK and no ai-web/app dependency in ai-core. Coverage: 98.5% instruction / 89.9% branch / 98.7% line measured by the quality reviewer via ad-hoc JaCoCo (before the final fixes; not part of the build, not enforced). Sonar: not verified. Dependency vulnerability scan: not performed.
- **Reviews:** round 1 and round 2 by independent Claude Code subagents applying the Kiro reviewer prompts (not Kiro runs). Round 2 results: security 0 Critical/High, 1 new Medium (N-01, fixed), 3 Low (fixed), 4 Info; quality 0 Critical/High/Medium new, 6 Low, 6 Info. Round 3 (delta of the final fixes) status: see `milestone.md`.
- **Deviations:** single `AiException` + reason enum; no `AiCoordinator` interface; Bedrock adapter and AWS SDK deferred to M3; `AiRequest` validation happens at construction.

## Milestones 3–7
Not started.
