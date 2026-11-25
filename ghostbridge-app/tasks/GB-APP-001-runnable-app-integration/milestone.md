# Milestones — GB-APP-001 (Milestone 6)

| # | Milestone | Acceptance criteria | Status | Verified by |
|---|---|---|---|---|
| M6.0 | Design and impact analysis | documents created before code | Complete | files present |
| M6.1 | Runnable skeleton | single executable jar, centralized yml, health UP | Complete | jar started; `AppAuthenticationTest` |
| M6.2 | App service and controller | App controller → App service → AI Core; no HTTP to AI Web; no duplicate endpoints | Complete | `AppLocalOllamaFlowTest`, `AiRestDisabledTest`, manual run |
| M6.3 | Authentication and authorization | sign-in, CSRF, roles, throttling, logout | Complete (automated + manual) | `AppAuthenticationTest`, manual run |
| M6.4 | Frontend integration | AI pages inside the App frontend behind sign-in; one HTTP client; one React | Complete: jsdom tests plus **24 real-browser checks** through Caddy/HTTPS | `App.test.tsx`, `evidence/browser-e2e.mjs` |
| M6.5 | Local Ollama end to end | real local inference through the complete path | Complete (one manual request, phi3:3.8b) | manual run |
| M6.6 | Deployment artifacts | Dockerfiles, compose, Caddy, runbook | Built and run locally with Docker Desktop; smoke, proxy security and browser checks pass; **public host not verified** | `test-cases.md` deployment sections, `evidence/` |
| M6.7 | Reviews and closure | independent security and code-quality reviews, fixes | see dispositions in the review files | rounds 1 and 2 done; no Critical/High; CQ-05 open (not reproduced); `deploy/` unapproved until run with Docker |

## Blockers and open decisions
- Open decisions from implementation-plan section 10 were answered with defaults, awaiting the user's confirmation: cookie-session authentication, configured users, same-origin proxy, no Playwright.
- Remaining (external to this environment): a public HTTPS host with a real certificate, a firewall check from outside and a two-address client test (hackathon deadline December 4, 2026); the user's confirmation of the default decisions above.

## Verification results
See test-cases.md execution log.
