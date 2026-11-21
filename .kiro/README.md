# GhostBridge AI Kiro Evidence

This directory documents how GhostBridge AI is developed with Kiro. It is intentionally committed to the repository and must not be ignored.

## Evidence log (accurate record of what was actually done)

| Artifact | Created with | Run/validated in Kiro? |
|---|---|---|
| `specs/ghostbridge-ai/{requirements,design,tasks}.md` | Claude Code (Kiro spec format) | **No** — not opened in the Kiro IDE |
| `steering/*.md` (`inclusion: always` and `fileMatch`) | Claude Code (Kiro steering format) | **No** — not loaded by Kiro yet |
| `hooks/*.kiro.hook` (3 `fileEdited` + 1 `postTaskExecution`) | Claude Code, written from the documented hook schema | **No** — not loaded in Kiro; schema unverified until opened in Kiro |
| `agents/*` reviewer prompts extended with AI-module scope and steering/spec resources | Claude Code | **No** — Kiro CLI/IDE not installed on this machine |
| M2 security/quality reviews (`tasks/GB-AI-001-…/*-review.md`) | Independent Claude Code subagents applying the Kiro reviewer prompts | **No** — these are not Kiro agent runs |

Update this table only with real Kiro runs (spec task execution, hook triggers, agent runs) and their results.

## Planned Evidence

- `specs/ghostbridge-ai/requirements.md` - Functional and non-functional requirements.
- `specs/ghostbridge-ai/design.md` - Architecture and implementation decisions.
- `specs/ghostbridge-ai/tasks.md` - Tracked implementation work.
- `steering/` - Repository conventions and domain rules for SOAP, REST, Spring Boot, React, testing, and submission compliance.
- `hooks/` - Workflow hooks for validation and generated-artifact checks.
- `agents/` - Custom-agent definitions or documented purpose and usage.

## Required Kiro Write-Up Topics

Document which Kiro surfaces were used and provide examples of their impact:

- IDE, CLI, Web, or Crew
- Custom agents
- Agent hooks
- Spec-driven development
- Steering documents
- MCP integrations
- Property-based testing
- Kiro powers

The final evidence should describe real workflows and artifacts, not imply features that were not used.
