# Fog Cloud — design spec

Agreed with DG, 2026-09-28, as 24 decisions. Recorded here because the implementation is
spread across the spell engine, monster AI, combat targeting and two renderers, and none of
those files can carry the reasoning on their own.

## The problem

`FOG_CLOUD` in `assets/data/spells.json` was generator output: 2d6 FORCE damage on a `BURST` at
range 24, `statusEffect: null`, `bespokeEffect: null`, wearing the `TOXIC_CLOUD` archetype.
5e's Fog Cloud does no damage at all — it is terrain you make.

## The spell

| Decision | Choice |
|---|---|
| Damage | None. Strip the 2d6 FORCE entirely. |
| Level / cost | Level 1, 3 MP, unchanged. Fleeing should be affordable. |
| Placement | Raycast down the player's facing, capped at 6 tiles; the cloud centres on the impact tile. Covers the escape case too — cast at short range and you are inside it. |
| Spread | Breadth-first flood fill, radius 2, through tiles a creature could walk through. Fills a room, turns a corner, never occupies stone. |
| Duration | 10 world turns in still air. |
| Recasting | Refreshes the overlap to whichever duration is longer and unions the footprints. Durations never add. |
| Dispersal | Wind magnitude >= 4 (storms and worse, on the surface) halves what is left each turn, so a cloud is gone in about three. Underground it holds. |

## What obscurement does

| Decision | Choice |
|---|---|
| Direction | Both ways. Monsters lose you; your own view closes in. |
| Sight vs hearing | Sight only. Monster hearing (`5 + intelligence` tiles, intelligence-weighted roll) routes straight around fog. This is the spell's counter: a rat loses you, a lich walks through and finds you. |
| Ranged attacks | Blocked in both directions — bow, thrown, spell, firearm. Melee is unaffected. |
| Projectiles | Consumed at the fog boundary rather than flying on and missing, so the player sees why the shot failed. Firearms are hitscan: they still flash and still wake the level. |
| Adjacency | Exempt. A monster one tile away has not lost you, and a swing that can land needs a target to land on. |
| Losing the target | The monster walks to the tile it last saw you on, then wanders. |
| Giving up | After `3 + intelligence` turns, floor 3, ceiling 20 — replacing a flat 5 for every creature. |

## Presentation

| Decision | Choice |
|---|---|
| Archetype | A new `OBSCURING_MIST`, not a tint on `TOXIC_CLOUD`. The archetype drives the cast overlay and the post-process flash, so borrowing the poison one flashed the screen green. |
| Modern renderer | Both: billboard puffs per tile seen from outside, and the distance fog collapsing to about two tiles of grey from inside. |
| Visibility inside | Monsters still render within about a tile. Obscured, not blindfolded. |
| Retro renderer | Mechanics identical; a flat desaturating wash instead of volumetrics. Not invisible — a cloud that blinds you without showing itself is a bug report. |
| Feedback | A chronicle line entering and leaving, plus a persistent `StatusPillBar` pill while inside. |

## Architecture

| Decision | Choice |
|---|---|
| Generality | A typed `AreaEffectManager`, not a Fog-Cloud-only bespoke. Five sibling cloud spells exist (Cloudkill, Stinking Cloud, Incendiary Cloud, Sleet Storm, Darkness); only the `OBSCURING` type is implemented. |
| Ownership | On the `Maze`, per chunk, like `LiquidManager`. |
| Persistence | None. `WorldSaveData` is a seed and a dozen scalars — the world is regenerated, not stored. **Consequence: saving and reloading inside a cloud loses it, and so does leaving the chunk.** Accepted for a ten-turn effect. |
| Tests | Unit tests for the flood fill, the duration tick, wind dispersal and the line-of-sight predicate. |
| Branch | Its own, off `develop`. |

## Corrections made during design

Two facts I asserted while grilling were wrong, and the design was priced against them before
being re-priced:

- "Monsters never consult line of sight." They do — `MonsterAiManager:135` calls a private
  `checkLineOfSight`, and there is a separate hearing check beside it.
- "`lastKnownTargetPos` is read by nothing and `turnsSinceLastSeen` is never incremented." Both
  are used: `MonsterAiManager:339` paths to the last known tile, and the counter is incremented
  with a give-up at 5 turns. Pursuit decay already existed; this work only made the threshold
  scale with intelligence.

## Raised by review, not decided

**Should fog hide monsters from the "is it safe?" check?** `HostileSight.anyInView` gates the
calm-only actions — changing spell slots and studying a Tome. Making fog block it was
implemented, then reverted: it is not among the decisions above, and it is not cosmetic. It
would let you re-slot spells and read a Tome with something two tiles away that has lost you.
Arguably right, but it is its own decision.

## Not built

Cloudkill, Stinking Cloud, Incendiary Cloud, Sleet Storm and Darkness still resolve as generic
burst damage. Each is now a new `AreaEffectType` constant plus a hook in the turn tick.
