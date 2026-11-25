# Milestones — GB-AI-004

| # | Milestone | Acceptance criteria | Status | Verified by |
|---|---|---|---|---|
| M0 | Milestone 4 closure | Reviews read; CQ-01 verified; permissive-chain test added; token-budget risk reviewed; Maven green; reports updated | Complete (implementer-verified, no independent round 2) | `mvn clean verify`, new test 3/3 |
| M1 | Documentation | Nine required task files exist before code | Complete | files present |
| M2 | Tooling + API layer | scripts run; client/guards/auth/config tests pass | Complete | Test runs in test-cases.md |
| M3 | Shell + shared components | layout, routing, a11y basics | Complete | Test runs in test-cases.md |
| M4 | Read pages | Provider, Usage, Dashboard | Complete | Test runs in test-cases.md |
| M5 | Write pages | Playground, Structured | Complete | Test runs in test-cases.md |
| M6 | Dev mock + hardening | mock tests, secret scan, dist check | Complete | Test runs in test-cases.md |
| M7 | Reviews and closure | both reviews, fixes, final verification | Complete (round 1 only; fixes not independently re-reviewed) | reviews + logged runs |

Status values: Not started, In progress, Blocked, Complete (only after verification).

## Blockers
- No application authentication exists: real end-to-end calls return 401. Not a blocker for this milestone (mock mode + stubbed API tests); is a blocker for live use.

## Verification results
- 2026-11-23 final: frontend typecheck/lint clean, 152 tests passed, build + check-dist OK, `npm audit` 0 vulnerabilities; `mvn clean verify` BUILD SUCCESS (86 ai-web tests). Security review: 0 Critical/High/Medium, 7 Low. Code-quality review: 0 Critical/High, 5 Medium (fixed), 14 Low.
- Not verified: live authenticated end-to-end, real Ollama, coverage, Sonar, visual/browser review.
- 2026-11-23 Phase 1: `mvn -o -B clean verify` BUILD SUCCESS before changes; new test passes.
