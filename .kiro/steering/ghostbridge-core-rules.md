---
inclusion: always
---

# GhostBridge AI — Core Steering

The repository rules are authoritative. Do not restate or contradict them; follow them:

#[[file:rules/rules.md]]
#[[file:doc-architecture/PROJECT_STRUCTURE.md]]

## Workflow gate (applies before any change)
1. Every change belongs to a task folder `<project>/tasks/<TASK-ID>-<slug>/` created from `templates/tasks/`. No folder, no change.
2. `existing-implementation.md`, `requirements.md`, `implementation-plan.md`, `impact-analysis.md`, `test-cases.md`, `milestone.md`, `tasks.md` must exist before code, POM or configuration changes.
3. Work one milestone at a time. A milestone is complete only after its acceptance criteria are verified; record results in `milestone.md`, `implementation.md`, `changelog.md`.
4. Write tests with the code (test-backed development). Deterministic tests only; live paid-provider tests are tagged and disabled by default.
5. Reviews by `ghostbridge-security-reviewer` and `ghostbridge-code-quality-reviewer` are independent of the implementer; never self-approve.

## Ownership reminders
- Versions only in `ghostbridge-springboot/pom.xml`. Root `pom.xml` is an aggregator.
- Java base package is `com.ghostbridge` with functional sub-packages only; class names unique repo-wide (`Ai` prefix in the AI module).
- CORS, Spring Security, exception handling and logging config live only in `ghostbridge-springboot`.
- Sonar: cognitive complexity ≤ 15 (target ≤ 10). Report Sonar as "not verified" unless it actually ran.
- Do not claim Kiro features were used unless they were (see `.kiro/README.md` evidence log).
