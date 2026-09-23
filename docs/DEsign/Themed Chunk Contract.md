# Design Document: Themed Chunk Contract

**Status**: Approved (via Grilling Process)
**Date**: September 2026
**Related Documents**:
- `docs/DEsign/Design Document_ Procedural Maze Chunk Architecture & Generation.md`
- `docs/Implementation Plan_ The Expedition Loop & Progression Reboot.md`
- `CONTEXT.md`

---

## 1. Purpose

Themed chunks are the procedurally-flavoured set pieces of the Delve & Return
Expedition loop. Exactly one chunk in every 3x3 cluster is themed. A themed
chunk announces itself from outside via a glowing rune above the gate that
leads *into* it, so the player can choose to enter or walk past.

Before this document, themes were prose descriptions attached to a switch
statement. Two of six were invisible at runtime. This document defines the
contract that makes "the theme is implemented" a testable claim instead of a
judgement call.

---

## 2. The Seven-Slot Contract

> [!IMPORTANT]
> Every value of `ChunkTheme` MUST fill all seven slots. A theme that cannot
> fill a slot does not ship. This is enforced by `ThemeContractTest`.

| Slot | Name | Requirement |
| :--- | :--- | :--- |
| **(a)** | Layout mutation | The theme reshapes the tile grid. Carving is bounded by the chunk's real dimensions and never destroys protected tiles. |
| **(b)** | Prop dressing | The theme declares a non-empty weighted prop table. Every `propId` resolves against the prop catalogue, and every catalogue asset exists on disk. |
| **(c)** | Hazard | One environmental mechanic that changes how the player moves or fights. Cosmetic-only themes are non-conforming. |
| **(d)** | Encounter table | A theme-specific, non-empty monster table. Reskinned generics are non-conforming. |
| **(e)** | Objective | A completable goal, with a defined completion predicate. |
| **(f)** | Reward | Crests of Valor, banked to `ShelterAltar`, surviving the expedition. |
| **(g)** | Sensory signature | Fog tint, rune colour, and an entry stinger, so the theme is identifiable without reading text. |

### Slot (c) is the one that gets skipped

A hazard must change a decision. Tinted floor tiles are not a hazard. The test
asserts each theme declares a hazard block; reviewers must assert it *matters*.

---

## 3. Seal & Surrender

**Every themed chunk seals its gates on entry** and unseals when its objective
completes. The rune above the gate is the forewarning that makes this fair: the
player is told what is ahead and chooses to walk in.

Because sealing is universal, an uncompletable objective is an unrecoverable
save. Two defences are mandatory and belong together:

1. **Per-theme guarantees.** Each theme guarantees its own objective is
   completable at generation time (a fire source always spawns in the Thicket;
   the Gravedigger is invulnerable; the drowned cache is always reachable).
2. **The Rune of Surrender.** Every sealed gate carries one. Channelling it for
   3 turns opens the gates, forfeits the Crest, and marks the chunk failed for
   the remainder of the run.

Defence 1 handles the failures we can enumerate. Defence 2 handles the ones we
cannot. Neither is sufficient alone.

There is a third rule that makes the first two safe: **an objective that could
not be placed never seals the chunk and never pays out.** Generation records
whether the champion, the graves or the cache were actually placed. Without
this, "no champion is alive" reads as "the champion is dead" and awards a Crest
for a champion that never existed, and an arena that spawned nothing completes
on turn one.

---

## 4. The Six Themes

| Theme | Lvl | Crests | Objective | Hazard |
| :--- | :--- | :--- | :--- | :--- |
| Blood Colosseum | 3 | 2 | Last combatant standing | Blood pools cost 2 ticks to cross |
| Ruined Castle | 3 | 2 | Slay the Castle Warden in the inner keep | Rubble blocks courtyard lanes |
| Wandering Battalion | 2 | 1 | Kill the Commander; the legion routs | Brazier and campfire tiles burn on entry |
| Makeshift Graveyard | 1 | 1 | Reconsecrate 4 graves; each raises its dead | Grave mounds block movement; fog shortens sight |
| Flooded Caverns | 1 | 1 | Open the drowned cache | Wading costs 2 ticks; muck poisons; water rusts metal |
| Overgrown Thicket | 1 | 1 | Burn the heart-bloom | Brambles regrow each turn and re-block paths |

### Design principle: one verb each

Each objective exercises a different verb - attrition, boss, trigger-and-survive,
decapitation, navigation-under-hazard, race-against-regrowth. Six kill-the-boss
rooms would be six identical rooms wearing different textures, which is the
failure mode themed chunks exist to avoid.

---

## 5. Data Layout

Tunable data lives in JSON. Layout carving stays in Java, because carving an
arena is genuinely an algorithm.

### `assets/data/props.json` - flat catalogue

```json
{
  "props": [
    {
      "id": "gibbet_cage",
      "asset": "images/props/gibbet_cage.png",
      "renderMode": "SPRITE",
      "scaleX": 1.0,
      "scaleY": 1.6,
      "passable": false,
      "emissive": false
    }
  ]
}
```

Props are catalogued once and referenced by many themes. A brazier belongs to
both Wandering Battalion and Ruined Castle; it is defined once.

### `assets/data/themes.json` - per-theme tables

```json
{
  "themes": [
    {
      "id": "MAKESHIFT_GRAVEYARD",
      "crestAward": 1,
      "fogTint": { "r": 0.62, "g": 0.66, "b": 0.72 },
      "stinger": "sounds/door_creak.ogg",
      "props": [ { "propId": "gravestone", "weight": 40 } ],
      "monsters": [ { "type": "SKELETON", "weight": 30 } ],
      "hazard": { "kind": "BLOCKING_PROPS", "density": 0.12 }
    }
  ]
}
```

---

## 6. Prop Representation

`SceneryType.PROP` is a single new enum value carrying a `propId` resolved
against the catalogue. This keeps every existing save loading untouched -
`SceneryData.type` is serialised as an enum, and adding one value is additive
while replacing the enum would not be.

Each prop declares its own `passable` flag, and an impassable one blocks
movement for player and monsters alike. Standing props (gibbets, tents, pillars,
brambles, gravestones) block; ground clutter and overhead dressing (skull piles,
bone piles, hanging vines, chandeliers) do not, because a mushroom that stops a
minotaur reads as a bug rather than a hazard.

Props do **not** implement the `LOW_COVER` specification from the chunk
architecture document (`+3 AC`, projectile permeability) - that remains a
follow-up, because it touches pathfinding, ballistics and AC calculation, three
systems with independent regression surfaces.

A prop may also carry `burnDamage`, which makes it walkable **and** harmful:
braziers and campfires are enterable precisely so they can hurt. An impassable
fire would be a wall wearing a flame, and the Wandering Battalion's declared
BURNING_TILES hazard would burn nothing.

---

## 7. Liquids

Liquids render as translucent animated quads above the floor, and carry real
mechanics:

- **Entering a liquid tile costs 2 turn ticks.** Monsters get a free action
  while the player wades. This is deterministic and visible, so the player can
  decide whether to fight on stone or in the water. A random chance to lose the
  turn was rejected: it is a punish with no counterplay.
- **`BLACK_MUCK`** applies poison stacks.
- **`WATER`** rusts unprotected metal.
- **`BLOOD`** frenzies beasts.

---

## 8. Generation Invariants

> [!IMPORTANT]
> `ChunkThemeDecorator` runs as a post-pass over an already-generated chunk.
> It MUST NOT destroy what the generator placed.

1. Carve rectangles are derived from `maze.getWidth()`/`getHeight()`, never
   hardcoded tile indices.
2. Ladder, gate, item, event and home tiles are protected and never overwritten.
3. If a carve would bury the down-ladder, the ladder is **relocated** into the
   carved area, not deleted.
4. The decorator never runs on the Level 1 shelter chunk or its buffer.

The architecturally cleaner alternative - passing `ChunkTheme` into
`generateChunk()` so generators lay themes out natively - is deferred. It
requires rewriting three generators totalling ~2,200 lines. Its consequence is
that a themed chunk in a FOREST or DESERT biome still reads as stamped in.

---

## 9. Rune Semantics

The rune above a gate always advertises the **destination** chunk, never the
chunk the player is standing in. A signpost that sometimes names where you are
standing is noise.

This makes the existing Wisdom/Intelligence identification check meaningful: it
is a *scouting* decision about whether to walk in. Players below the threshold
see the cryptic omen instead of the true name.

---

## 10. Save Compatibility

All new state is additive. `propId`, objective progress and the reward flag are
nullable or defaulted, so saves written before this work load unchanged.

> [!IMPORTANT]
> Gate lock state persists. A themed chunk that reloads **without** an objective
> state is therefore sealed with nothing able to open it: the completion check
> bails on a null state, and so does the Rune of Surrender. `ChunkData` must
> always hand a themed chunk an objective back - restored from the save, or
> freshly seeded from the theme definition when the save predates objectives.
> `ThemeSaveRoundTripTest` guards this.

Previously-generated themed chunks are **not** regenerated on load. Regenerating
a chunk the player has already looted would duplicate treasure and resurrect
monsters - an exploit and a continuity break in a permadeath game. The cost is
that one stale chunk may persist in an in-flight run.

---

## 11. Test Matrix

`ThemeContractTest` asserts, for every `ChunkTheme` value:

- **Data**: all seven slots declared; every `propId` resolves; every catalogue
  asset exists on disk; fog tint and stinger resolve.
- **Output**: decorating a `Maze` produces props, places the objective entity,
  and leaves the down-ladder reachable.

Data assertions alone would pass a theme whose decorator silently places
nothing. Output assertions alone would pass a theme pointing at missing PNGs.
Both are required.
