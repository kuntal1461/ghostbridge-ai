# GhostBridge AI

**Tagline:** Resurrect Legacy Systems. Bridge the Future.

GhostBridge AI modernizes legacy SOAP APIs into modern REST services using AI-assisted analysis and generated compatibility artifacts.

- **Category:** Resurrection
- **Backend:** Spring Boot
- **Frontend:** React + TypeScript
- **Submission documentation:** [hackathon-requirements/HACKATHON_REQUIREMENTS.md](hackathon-requirements/HACKATHON_REQUIREMENTS.md)
- **Repository structure:** [doc-architecture/PROJECT_STRUCTURE.md](doc-architecture/PROJECT_STRUCTURE.md)
- **Kiro evidence:** [.kiro/README.md](.kiro/README.md)

## Status

Early development for the Kiroween 2026 hackathon. The multi-module Spring Boot structure and the centralized Spring Boot infrastructure (`ghostbridge-springboot`: configuration, CORS, security, error handling, validation) are implemented and tested. The AI module, the app modules and the frontends are structure only; the SOAP-to-REST workflow is not built yet.

## Requirements

- Java 21 (build verified with JDK 25 targeting 21)
- Maven 3.9+

## Build and test

```bash
mvn clean verify
```

There is no runnable application yet (`ghostbridge-app-web` has no main class), so there is nothing to start. Setup and run instructions will be added here when the application exists.

## Project rules and documentation

- [rules/rules.md](rules/rules.md): mandatory development rules
- [rules/claude/CLAUDE.md](rules/claude/CLAUDE.md): instructions for Claude Code
- [templates/tasks/](templates/tasks/README.md): task document templates; every task needs a task folder first
- [.kiro/agents/](.kiro/agents): security and code-quality review agents

## License

[MIT](LICENSE)
