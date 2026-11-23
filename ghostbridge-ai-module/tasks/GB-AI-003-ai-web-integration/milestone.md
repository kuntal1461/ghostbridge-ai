# Milestones — GB-AI-003 (Milestone 4: Secure AI Web & REST API)

| # | Milestone | Acceptance criteria | Status | Verified by |
|---|---|---|---|---|
| A | Task docs | All required docs exist before code | Complete | files in this folder |
| B | Core retry metadata | `AiException.retryAfter`, limiter/budget values, tests | Complete | tests (see test-cases.md) |
| C | G-M4-1 and security building blocks | Properties, resolver, per-requester limiter, access interceptor, body-limit filter; tests green; **before endpoints** | Complete | tests (see test-cases.md) |
| D | Contracts, advice, service, controller, auto-config | VOs, mapper, advice, endpoints, imports file; G-M4-2 table verified | Complete | tests (see test-cases.md) |
| E | Integration tests | TC-1…TC-25 green with mock provider | Complete | tests (see test-cases.md) |
| F | Reviews and closure | Independent security + quality reviews; no Critical/High; `mvn clean verify`; docs | Complete (with caveats) | |

Status values: Not started, In progress, Blocked, Complete (only after verification).

## Blockers
- Authentication mechanism absent in the platform (endpoints are 401 for everyone until an app plugs one in). Not a blocker for library completion; blocks real use.

## Verification results
- 2026-11-23: `mvn -o clean verify` root BUILD SUCCESS (31/55/274+1 skipped/83). Gates G-M4-1 and G-M4-2 verified by unit and MockMvc tests with the mock provider.
- Reviews: security 0 Critical/High (1 Medium accepted, Lows documented); quality 1 High (stray directory, fixed) + Mediums fixed/documented.

## Milestone 4 completion record
**Status: complete as a library; not usable until an application supplies authentication.** Caveats: reviews were Claude Code subagents applying the Kiro prompts (not Kiro runs); post-review fixes were not re-reviewed; Sonar, vulnerability and secret scanners were not run; no Ollama request made; limiter is per-JVM; no per-requester token budget; open Low quality items remain; nothing committed.
