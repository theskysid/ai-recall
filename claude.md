# CLAUDE.md

## Purpose

This repository contains the Recall project.

The project documentation lives in the `ai/` directory.

Always use those documents before making implementation decisions.

---

# Startup Rules

At the beginning of every new task:

1. Read `ai/PROJECT.md`
2. Read `ai/CURRENT_TASK.md`
3. Read only the documentation relevant to the current task.
4. Read only the source files required for the task.

Do not scan the repository unless explicitly requested.

---

# Development Workflow

Follow this order:

1. Understand the task.
2. Explain the implementation plan.
3. Wait if the task is unclear.
4. Implement only the approved plan.
5. Summarize changes made.

Never skip directly to implementation when requirements are ambiguous.



---

# Scope Control

Do not modify unrelated files.

Do not perform repository-wide refactors.

Do not rename files unless requested.

Do not introduce new frameworks, libraries, or services without approval.

Do not change architecture without approval.

Keep changes as small as possible.

---

# Documentation

Whenever architecture, APIs, database schema, or major project decisions change:

Update the appropriate document inside the `ai/` folder.

Do not duplicate documentation across multiple files.

---

# Coding Principles

Prefer existing project conventions over introducing new patterns.

Prefer modifying existing code instead of creating unnecessary abstractions.

Keep controllers thin.

Keep business logic inside services.

Avoid duplicate logic.

---

# Safety Rules

Before editing more than five files, explain why.

Before changing database schema, explain the migration.

Before deleting code, explain why it is no longer needed.

Never generate placeholder implementations without stating they are placeholders.

Never silently remove existing functionality.

---

# Research

The research component is not the current priority unless explicitly requested.

Do not implement research-specific algorithms unless asked.

Focus on building a stable, working product first.

---

# Communication

Be concise.

Explain reasoning before major architectural decisions.

If multiple approaches exist, recommend one and briefly explain the trade-offs.

If requirements conflict with the existing architecture or documentation, raise the conflict before writing code.

---

# Reporting

When a task is finished, report in this format and nothing more:

```
STATUS   done | blocked | needs-decision
BUILD    pass | fail (+ the error if fail)
FILES    paths only
BLOCKS   what another terminal or the DoD is now blocked on — else "none"
DECIDE   the one question needing an answer — else "none"
```

Rules:

Do not restate the task, the spec, or the endpoints you were given.

Do not justify conventions you followed. Report only where you deviated, in one line.

Do not include tables, per-file summaries, or code you already wrote to disk.

"BUILD pass" means you ran it. Never infer it.

Report a problem you found but did not fix under BLOCKS in one line, not as prose.