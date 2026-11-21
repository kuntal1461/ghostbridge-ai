# Milestones — GB-AI-001 (AI Framework Setup)

| # | Milestone | Acceptance criteria | Status | Verified by |
|---|---|---|---|---|
| M1 | Discovery and planning | Existing implementation documented; task folder complete (8 mandatory docs + changelog); Kiro spec (requirements/design/tasks) created and cross-referenced; steering and hooks plan recorded; open questions listed; no code written | In progress — documents written, awaiting user review of plan and open questions | Pending user review |
| M2 | AI Core foundation | See "M2 acceptance criteria" and "M2 remediation criteria" below | **Complete** (2026-11-21), with the caveats listed under "M2 completion record" | `mvn -o clean verify` BUILD SUCCESS (31/55/174); independent review rounds 1-3 |
| M3 | Real provider integration | `BedrockAiProvider` with credentials from the default chain, timeouts, bounded retry, error mapping; mocked-SDK tests I-04 pass; live test tagged and disabled | Not started | |
| M4 | AI Web | Endpoints, DTOs, advice, authorization; tests I-05…I-13 pass; no competing security/CORS/advice | Not started | |
| M5 | AI Frontend | Overview, Playground, Viewer, History; `build`, `lint`, `test`, `typecheck` pass; F-01…F-08 pass | Not started | |
| M6 | Review and hardening | Separate security and quality reviews written; Critical/High resolved or listed as blockers; re-review of fixes; `mvn clean verify` green from root | Not started | |
| M7 | GhostBridge App readiness | Public contract documented; structured request demonstrated through `AiClient` in a test; integration examples; no SOAP logic in AI | Not started | |

Status values: Not started, In progress, Blocked, Complete (only after verification).

## M2 acceptance criteria
1. `ErrorCode` has the four new codes mapped to 429/503/504/502; existing mappings unchanged; regression tests (existing + new) pass.
2. `AiClient`, `AiProvider`, DTO records, `AiProperties` (all six required keys), `AiException`/`AiErrorReason` exist in `ghostbridge-ai-core` under `com.ghostbridge.*`; core has no dependency on web/app; no AWS dependency.
3. Provider-specific validation: `bedrock` without `model-id`/`aws.region` fails binding; `mock` works with no AWS config; unset provider → calls fail with missing-configuration, no fallback; `prod` + `mock` fails startup.
4. Mock provider is deterministic; coordinator pipeline (validate → resolve → prompt → options → limit/retry → invoke → size check → structured validation → usage/metrics/registry) covered by unit tests U-01…U-24 (live/Bedrock items excluded).
5. Prompts load from classpath, fail fast when malformed; untrusted content delimited as data (defence in depth, not a complete prompt-injection control); errors/logs contain no prompt or response text.
6. `mvn clean verify` green from the repository root; `mvn dependency:tree` shows no AWS SDK and no ai-core → web dependency; coverage reported as measured.
7. Security and code-quality review reports written by the separate agents (or limitation stated), findings fixed and re-reviewed.

## M2 remediation criteria (approved 2026-11-21)
M2 is complete only if: all 9 Medium findings (F-01, F-02, F-03, F-04→gate, CQ-01…CQ-05) are resolved or, for F-04, converted into the recorded mandatory gate G-M4-1; no Critical/High findings; both independent re-reviews cover the current code; `mvn -o clean verify` green; error mapping matches the table in `implementation-plan.md`; deferred gates G-M3-1, G-M4-1, G-M4-2 documented in `tasks.md`.
**Note:** F-04 (per-requester limits) cannot be implemented in core without a trusted identity source; the user approved deferring it as a mandatory M4 gate, with global limits as the secondary safeguard.

## Mandatory deferred gates
G-M3-1 provider byte limits (before a real provider is enabled); G-M4-1 per-requester limits from trusted authentication context (before exposing AI Web endpoints); G-M4-2 Retry-After / DTO bounds / reason exposure.

## Completed deliverables (M1)
- `existing-implementation.md`, `requirements.md`, `implementation-plan.md`, `impact-analysis.md`, `test-cases.md`, `milestone.md`, `tasks.md`, `implementation.md` (stub), `changelog.md`.
- Kiro spec: `.kiro/specs/ghostbridge-ai/{requirements,design,tasks}.md`.

## Blockers
- None blocking M1. Decisions needed before M2/M3 (see `implementation-plan.md` → Open questions): approval of the additive shared-module change (D5), Bedrock access/region/model for live verification.

## Verification results
- 2026-11-21: Repository inspected (see `existing-implementation.md` → Evidence). No build was run for M1 because no code or POM was changed.

## M2 completion record (2026-11-21)
- **Criteria check:** (1) All 9 Medium findings: F-01, F-02, F-03, CQ-01..CQ-05 resolved; F-04 (per-requester limits) converted, as the user approved, into the recorded mandatory gate **G-M4-1** with global limits kept as the secondary safeguard — it is NOT implemented and stays an open Medium until M4. The round-2 Medium N-01 (quadratic regex) was fixed and confirmed by round 3. (2) No Critical/High in either report. (3) Independent reviews: round 1, round 2 and round 3 (delta) by separate Claude Code subagents applying the Kiro reviewer prompts — **not Kiro runs**. (4) `mvn -o clean verify`: BUILD SUCCESS; tests springboot-core 31, springboot-web 55, ai-core 174. (5) Deferred gates G-M3-1, G-M4-1, G-M4-2 recorded in `tasks.md` and `implementation-plan.md`. (6) `dependency:tree`: ai-core has no AWS SDK and no ai-web/app dependency.
- **Caveats (stated, not hidden):** (a) after round 3 the following small changes were made and are covered by tests but were **not independently re-reviewed**: NFKC-expansion cap in `AiTextSanitizer` (security R3-01), `$id` now rejected outright in schemas, stronger schema fixtures and telemetry-failure assertion (quality R3-01/R3-02). (b) Sonar and dependency-vulnerability scanning were not run; coverage (98.5%/90.4% branch, ai-core) was measured ad hoc by a reviewer before the last changes and is not enforced by the build. (c) Residual Low/Info items are accepted and listed in `implementation.md` and the review reports (homoglyph look-alikes, retry amplification, budget overshoot, `DefaultAiCoordinator` size, partly open Javadoc/test-style items). (d) No Bedrock adapter, web, frontend or app work exists; **M3 and M4 are not started and not complete.**
