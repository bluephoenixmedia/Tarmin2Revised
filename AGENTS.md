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
- **Retro Mode Preservation**: Absolute non-negotiable invariant. When retro mode (`GameMode.CLASSIC`) is active, the game MUST strictly preserve the authentic Mattel Treasure of Tarmin look and feel: legacy 16-tile array (`tile1` to `tile16`), 2x2 map layout, retro wireframe vector/bitmap walls, original color palette, classic soundscape, and legacy viewport. Advanced modern features (modular 12x12 chunk archetypes, low-cover 3D billboards, macro mission graphs, and modern telemetry pacing) MUST remain completely isolated to `GameMode.ADVANCED` and never leak into Classic mode. Always consult `docs/DEsign/Retro_Mode_Preservation_Charter.md`.
- **Git Commits**: Commit when completing logical units of work; follow conventional commits (`feat:`, `fix:`, `refactor:`, `test:`, `chore:`).
- **Core Loop**: Maintain the integrity of the Delve & Return Expedition loop: Shelter Hub -> Overland/Strata Delve -> Return to Camp -> Prepare for Castle Tarmin.
- **Combat**: Keep grid combat fast and responsive (bump-to-attack in melee, directional projectile ballistics for ranged, spellcast overlays). Calculations run under the hood with instant floating damage and combat log feedback.
