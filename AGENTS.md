# AGENTS.md

## Agent skills

### Issue tracker

Issues and specs live as GitHub issues on `bluephoenixmedia/Tarmin2Revised`. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical triage roles (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout with root `CONTEXT.md` and `docs/`. See `docs/agents/domain.md`.

---

## Branching and releases

`master` holds releases only. `develop` is the integration branch and is where
work goes.

- **Branch from `develop`, never from `master`.** Merge back into `develop`.
- `master` is currently **v0.0.1** (tag `v0.0.1`). `develop` is working toward
  **0.0.2**, and carries `projectVersion=0.0.2-SNAPSHOT` in `gradle.properties`.
- When 0.0.2 is MVP-complete: everything lands on `develop`, `develop` is tested
  as a whole, and only then does `develop` merge to `master`, which is tagged as
  the new release.
- Nothing is committed directly to `master` except a release merge and its tag.
- On release, set `projectVersion` to the release number on `master` and bump
  `develop` to the next `-SNAPSHOT`.

Before merging to `master`, the full suite must pass (`./gradlew :core:test`)
and the game must boot. Note that `./gradlew :core:test` occasionally fails with
a test-JVM exit and no failing test; rerun before treating it as real.

A clean textual merge is not proof of a working merge. The 0.0.1 release merge
applied without conflict and did not compile, because one side deleted a field
the other side had started reading. Compile and test every merge.

---

## Repository Guidelines

- **Architecture**: Always consult `docs/Implementation Plan_ The Expedition Loop & Progression Reboot.md` before making architectural modifications.
- **Git Commits**: Commit when completing logical units of work; follow conventional commits (`feat:`, `fix:`, `refactor:`, `test:`, `chore:`).
- **Core Loop**: Maintain the integrity of the Delve & Return Expedition loop: Shelter Hub -> Overland/Strata Delve -> Return to Camp -> Prepare for Castle Tarmin.
- **Combat**: Keep grid combat fast and responsive (bump-to-attack in melee, directional projectile ballistics for bows, crossbows and thrown weapons, spellcast overlays). Calculations run under the hood with instant floating damage and combat log feedback.
- **Firearms are the exception to ballistics**: muskets and pistols resolve hitscan, carried by a muzzle flash and powder smoke rather than a travelling sprite. Suddenness is the only thing a gun has over a bow, and a ball you can watch cross the room takes it away. Their cost is paid elsewhere -- a multi-turn reload, a noise pulse that wakes the level, and ammunition that never litters the floor. See `core/src/main/java/com/bpm/minotaur/gamedata/firearm/`.
