# Tasks — GB-APP-001 (Milestone 6)

## Pending
- [ ] User confirmation of the default decisions (implementation-plan section 10)
- [ ] Real browser check of the integrated UI (Playwright or manual) and a screenshot review
- [ ] Run and harden `deploy/` on a machine with Docker; first HTTPS deployment; demo credentials in the submission
- [ ] Resolve the unexplained one-time `AppRealHttpTest` failure or make the test deterministic
- [ ] Remaining Low review findings (see the review files)
- [ ] Commit and push (needs user approval; nothing committed)

## In progress

## Completed
- [x] Task documentation, architecture and impact analysis before code
- [x] AI Core shared helpers; AI Web `rest-enabled` switch and advice marker
- [x] `AppAiService`, `AppAiController`, mapper, properties (app-core / app-web)
- [x] Authentication: session + CSRF + login throttling + validation
- [x] Centralized `application.yml` (+ dev, prod)
- [x] Frontend: npm workspace, App frontend, AI console hosted behind sign-in
- [x] Tests: backend integration, unit, real-HTTP; frontend component tests
- [x] Root `mvn clean verify`; both frontends typecheck, lint, test, build
- [x] One manual end-to-end request through the jar to the local model
- [x] Independent security and code-quality reviews (round 1)
