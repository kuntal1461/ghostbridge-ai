# Milestones — GB-SPRINGBOOT-001

| # | Milestone | Acceptance criteria | Status | Verified by |
|---|---|---|---|---|
| M1 | Templates, task docs, workflow gate | Templates dir exists; task docs exist; rules/CLAUDE.md enforce docs-first | Complete | Files present; gate in `rules/rules.md` section 5 and `rules/claude/CLAUDE.md` |
| M2 | Core infrastructure | Core builds; TC-12..14, 18 pass | Complete | `mvn clean verify`: 31 core tests pass |
| M3 | Web infrastructure | Web builds; TC-1..11, 15..17, 19, 20 pass | Complete | `mvn clean verify`: 40 web tests pass |
| M4 | Tests complete | All TC rows Pass | Complete | test-cases.md |
| M5 | Reviews | Agent definitions exist; both reports exist; no unresolved Critical/High; re-review done | **Partially complete** | Reports exist and 2 rounds done, **but by the implementing session, not the Kiro agents** (not executed). Independent Kiro run pending. |
| M6 | Docs and final verification | rules/architecture updated; `mvn clean verify` passes from root | Complete | Root build SUCCESS; docs updated |

## Blockers
- No Kiro runtime here, so the review agents have not been run independently (M5).
- Sonar is not configured, so Sonar compliance is unverified.
- No dependency vulnerability scanner is configured (S-4).

## Verification results
See test-cases.md execution log.
