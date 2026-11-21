# GhostBridge AI — Project Structure

Three Maven projects under a root aggregator `pom.xml` (`ghostbridge-parent`) that only lists them so they build and load in the IDE together. `ghostbridge-springboot` is the head for configuration: it inherits `spring-boot-starter-parent` and owns the Spring Boot version, dependency management and plugins; the AI module and app inherit from it. Build everything with `mvn clean install` from the repository root; Maven orders the projects automatically.

```
ghostbridge-ai/                      <- repository root
├── pom.xml                          <- aggregator only (ghostbridge-parent)
├── rules/
│   ├── rules.md                     <- mandatory development rules
│   └── claude/CLAUDE.md             <- instructions for Claude Code
├── templates/tasks/                 <- task document templates (mandatory for every task)
├── .kiro/agents/                    <- ghostbridge-security-reviewer, ghostbridge-code-quality-reviewer
├── doc-architecture/
│   ├── architecture.md              <- HLD
│   └── PROJECT_STRUCTURE.md         <- this file
│
├── ghostbridge-springboot/          <- (1) common backend infrastructure (parent POM)
│   ├── ghostbridge-springboot-core/     config, exception, util today; entity, repository,
│   │                                    service (+ impl), dto added when needed
│   ├── ghostbridge-springboot-web/      advice, config, filter, security, util today;
│   │                                    controller/restcontroller/dto added when needed; depends on core
│   ├── ghostbridge-springboot-frontend/ React/TypeScript (not a Maven module)
│   └── tasks/                           task folders (e.g. GB-SPRINGBOOT-001-centralized-framework-setup)
│
├── ghostbridge-ai-module/           <- (2) isolated AI subsystem (parent POM)
│   ├── ghostbridge-ai-core/             provider-neutral AI interfaces, provider adapters, prompts,
│   │                                    structured-output parsing, analysis services, models/DTOs,
│   │                                    AI exceptions, token usage and inference metadata
│   ├── ghostbridge-ai-web/              AI controllers, request/response DTOs, delegates to AI core
│   └── ghostbridge-ai-frontend/         React/TypeScript (not a Maven module): AI components,
│                                        analysis and prompt-inspection views, API client
│
└── ghostbridge-app/                 <- (3) integrator + SOAP-to-REST workflow (parent POM)
    ├── ghostbridge-app-core/            entity, repository, service (+ service/impl), dto, util,
    │                                    exception; main business logic
    ├── ghostbridge-app-web/             runnable Spring Boot app (main class
    │                                    to be added), controller; depends on app core, springboot-web, ai-web
    └── ghostbridge-app-frontend/        React/TypeScript (not a Maven module)
```

A `frontend` module (React + TypeScript) is not created yet. It is added only when needed.

## Module responsibilities

| Project | Module | Responsibility |
|---|---|---|
| springboot | ghostbridge-springboot-core | Entities, repositories, service interfaces and `impl`, DTOs, business logic, utilities, exceptions, shared configuration |
| springboot | ghostbridge-springboot-web | Controllers and REST endpoints, global exception handlers, API DTOs, CORS config; depends on core |
| springboot | ghostbridge-springboot-frontend | React frontend, not a Maven module |
| ai-module | ghostbridge-ai-core | Provider-neutral AI interfaces, LLM provider adapters, prompt management, structured-output parsing, AI analysis services, models/DTOs, AI exceptions, token usage and inference metadata |
| ai-module | ghostbridge-ai-web | AI controllers and REST endpoints, request/response DTOs, delegation to AI core; no LLM logic |
| ai-module | ghostbridge-ai-frontend | Reusable AI components, analysis and prompt-inspection views, API clients for AI web |
| app | ghostbridge-app-core | Main business logic of the SOAP-to-REST workflow: entities, repositories, services and `impl`, DTOs, utilities, exceptions |
| app | ghostbridge-app-web | Runnable application, controllers and REST endpoints; depends on app core, springboot-web and ai-web |
| app | ghostbridge-app-frontend | React frontend, not a Maven module |

## Dependency rules

```
ghostbridge-springboot-core  <-  ghostbridge-springboot-web
ghostbridge-springboot-core  <-  ghostbridge-ai-core  <-  ghostbridge-ai-web (also uses ghostbridge-springboot-web)
ghostbridge-springboot-core + ghostbridge-ai-core  <-  ghostbridge-app-core
ghostbridge-app-core  <-  ghostbridge-app-web (also uses ghostbridge-springboot-web, ghostbridge-ai-web)
```

1. Dependencies flow one way: springboot → ai → app. Never upward.
2. Only `ghostbridge-ai-core` knows about LLM providers.
3. Business logic lives only in `ghostbridge-app`.
4. Versions, plugins and dependency management are declared once, in `ghostbridge-springboot/pom.xml`.

## Java package convention

`com.ghostbridge` is the single base package for every module. Functional packages sit directly under it; the module decides the layer.

| Module | Packages under `com.ghostbridge` |
|---|---|
| `ghostbridge-springboot-core` | `config`, `exception`, `util` (add `entity`, `repository`, `service`, `service.impl`, `dto` only when code needs them) |
| `ghostbridge-springboot-web` | `advice`, `config`, `filter`, `security`, `util` (add `controller`, `restcontroller`, `dto` only when needed) |
| `ghostbridge-ai-core` | `config`, `constants`, `dto`, `service`, `service.impl`, `exception`, `util`; resources `prompts/`, `ai-schemas/` |
| `ghostbridge-ai-web` | `restcontroller` (more as needed) |
| `ghostbridge-app-core` | `entity`, `repository`, `service`, `service.impl`, `dto`, `util`, `exception` |
| `ghostbridge-app-web` | `controller`, `restcontroller` |

Modules share package names, so class names must be unique repository-wide. See [rules.md](../rules/rules.md).

## Design principles

- The root parent POM manages all modules, dependency versions and build configuration.
- A project parent (for example `ghostbridge-springboot`) contains `core` and `web`; `web` depends on `core`.
- The frontend is a separate React application, not a Maven module.
- Other projects reuse the Spring Boot core or web libraries through Maven dependencies.
- Avoid unnecessary nested package names and duplicate directory structures.

## Target module layout

| Project (parent POM) | Module | Contents |
|---|---|---|
| `ghostbridge-springboot` | `ghostbridge-springboot-core` | Entities, repositories, services, implementations, business logic |
| | `ghostbridge-springboot-web` | Controllers, REST controllers, exception advice |
| | `ghostbridge-springboot-frontend` | React frontend, not a Maven module |
| `ghostbridge-ai-module` | `ghostbridge-ai-core` / `-web` / `-frontend` | Isolated AI subsystem with its own core, web and frontend |
| `ghostbridge-app` | `ghostbridge-app-core` / `-web` / `-frontend` | Integrates the other projects and implements the SOAP-to-REST modernization workflow |

- **GhostBridge Spring Boot** provides the common backend infrastructure and configuration.
- **GhostBridge AI Module** is an isolated AI subsystem, with its own core, web and frontend.
- **GhostBridge App** integrates these modules and implements the actual SOAP-to-REST workflow.

> Status: `ghostbridge-springboot` now contains the centralized infrastructure (GB-SPRINGBOOT-001). `ghostbridge-ai-core` now holds the provider-neutral AI foundation (GB-AI-001 M2; mock provider only, Bedrock adapter planned for M3). `ghostbridge-ai-web`, the app modules and all frontends remain structure only; `ghostbridge-app-web` has no main class yet.

## Rules

All development rules are in [rules.md](../rules/rules.md). In short:

- **Business logic** goes in `core`.
- **REST API** goes in `web`.
- **Frontend code** goes in `frontend`.
- Code must comply with Sonar, Java, project-structure and folder-naming rules.
