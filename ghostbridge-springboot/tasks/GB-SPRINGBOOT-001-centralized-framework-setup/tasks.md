# Tasks — GB-SPRINGBOOT-001

## Pending
- [ ] Run `ghostbridge-security-reviewer` and `ghostbridge-code-quality-reviewer` in Kiro for an independent review (M5)
- [ ] Configure Sonar (SonarCloud/SonarQube) and a dependency scanner (S-4, Q-6)
- [ ] Decide handling of chunked oversized bodies (S-2) and an outbound SSRF allowlist (S-5) before the first user-supplied-URL feature

## In progress
(none)

## Completed
- [x] M1 templates, task docs, workflow gate in rules/CLAUDE.md
- [x] M2 core: properties, production validator, exceptions, masker, secure XML parser, upload validator, auto-config, defaults file
- [x] M3 web: correlation filter, request-size filter, exception handler, CORS, security chain + headers, RestClient customizer, auto-config imports
- [x] M4 tests (31 core, 40 web)
- [x] M5 review agent definitions (`.kiro/agents/`), security and code-quality reports, fixes S-1 and Q-1, re-review
- [x] M6 rules.md, architecture and structure docs, `mvn clean verify`, `mvn dependency:tree`
