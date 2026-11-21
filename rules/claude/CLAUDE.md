# GhostBridge AI — Instructions for Claude

Read these before changing anything:

1. [rules/rules.md](../rules.md) — mandatory development rules.
2. [doc-architecture/PROJECT_STRUCTURE.md](../../doc-architecture/PROJECT_STRUCTURE.md) — modules, folders, dependency rules.
3. [doc-architecture/architecture.md](../../doc-architecture/architecture.md) — high-level design.

## Task gate (strict, applies before any work)

- Every change (feature, fix, refactor, framework change) belongs to a task folder: `<project>/tasks/<TASK-ID>-<slug>/`, created from `templates/tasks/` (see its README).
- **If the task folder is not present, do not change anything.** Create it first, or stop and ask.
- **Without `existing-implementation.md` (the past) and `implementation-plan.md` (the plan) — plus requirements, impact analysis, milestone and tasks — do not start work.** No code, configuration or POM change may start until these exist.
- Work one milestone at a time; update `milestone.md`, `tasks.md`, `implementation.md`, `changelog.md` as you go. A milestone is complete only after its acceptance criteria are verified.
- Run `ghostbridge-security-reviewer` and `ghostbridge-code-quality-reviewer` (`.kiro/agents/`) before declaring a task done.

## How to work

- **Write code only when asked.** Do not add dummy, sample or placeholder code. If the user asks for structure, create folders, poms and docs only. The AI project (`ghostbridge-ai-module`) is structure only until the user says where to write code.
- **Follow the layers.** Business logic in `*-core`, REST controllers and exception advice in `*-web`, UI in `*-frontend`. Core never depends on web.
- **Follow the structure.** Do not create modules, folders or packages that are not in PROJECT_STRUCTURE.md. If the structure changes, update that file in the same change.
- **Naming.** Lowercase hyphenated module and folder names: `ghostbridge-<project>-<layer>`. Java base package is only `com.ghostbridge` in every project, with functional packages directly under it (`config`, `entity`, `repository`, `service`, `service.impl`, `controller`, `restcontroller`, `advice`, `dto`, `util`, `exception`). No `.springboot`, `.ai`, `.app`, `.core` or `.web` levels. Class names must be unique across modules.
- **Versions** live only in `ghostbridge-springboot/pom.xml`. The root `pom.xml` is an aggregator only.
- **Standards.** Java 21, Spring Boot 3.5, SOLID, clean code, Sonar-compliant.
- **Build before finishing.** Run `mvn clean install` from the repository root and report failures honestly.
- **Shared infrastructure.** Never duplicate CORS, security, exception-handling or logging config outside `ghostbridge-springboot`. See sections 6-9 of rules.md.
- **Credits.** Record any hackathon credits in the Kiro Credits section of architecture.md.
