# AGENTS.md

## Agent skills

### Issue tracker

Issues and specs live as GitHub issues on `bluephoenixmedia/Tarmin2Revised`. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical triage roles (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout with root `CONTEXT.md` and `docs/`. See `docs/agents/domain.md`.

---

## Repository Guidelines

- **Architecture**: Always consult `docs/Implementation Plan_ The Expedition Loop & Progression Reboot.md` before making architectural modifications.
- **Git Commits**: Commit when completing logical units of work; follow conventional commits (`feat:`, `fix:`, `refactor:`, `test:`, `chore:`).
- **Core Loop**: Maintain the integrity of the Delve & Return Expedition loop: Shelter Hub -> Overland/Strata Delve -> Return to Camp -> Prepare for Castle Tarmin.
- **Combat**: Keep grid combat fast and responsive (bump-to-attack in melee, directional projectile ballistics for ranged, spellcast overlays). Calculations run under the hood with instant floating damage and combat log feedback.
