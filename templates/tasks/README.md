# Task Document Templates

Every task (feature, fix, framework change) must have a task folder **before any code or configuration changes**:

```
<project>/tasks/<TASK-ID>-<short-slug>/
```

Copy every file from this directory into the new folder, drop the `.template` suffix, and fill it in.

| Template | Purpose | Required before coding |
|---|---|---|
| `existing-implementation.md.template` | What exists today and must be preserved | Yes |
| `requirements.md.template` | Requirements and acceptance criteria | Yes |
| `implementation-plan.md.template` | Phases, affected files, decisions, rollback | Yes |
| `impact-analysis.md.template` | Affected consumers, risks, breaking changes | Yes |
| `milestone.md.template` | Phase tracking and verification | Yes |
| `tasks.md.template` | Checklist of work items | Yes |
| `test-cases.md.template` | Test scenarios and results | Yes (planned cases) |
| `implementation.md.template` | Record of actual changes | Filled during work |
| `changelog.md.template` | Chronological change log | Filled during work |
| `security-review.md.template` | Output of `ghostbridge-security-reviewer` | Created by review |
| `code-quality-review.md.template` | Output of `ghostbridge-code-quality-reviewer` | Created by review |

Task ID format: `GB-<PROJECT>-<NNN>`, for example `GB-SPRINGBOOT-001`.
