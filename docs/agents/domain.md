# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root
- **`docs/`**: read design documents and architectural specs relevant to the system you are touching.

## File structure

Single-context repo:

```
/
├── CONTEXT.md
├── docs/
│   ├── Implementation Plan_ The Expedition Loop & Progression Reboot.md
│   └── ...
└── core/
    └── src/main/java/com/bpm/minotaur/
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the terms as defined in `CONTEXT.md`.

## Flag ADR conflicts

If your output contradicts an established architectural decision, surface it explicitly.
