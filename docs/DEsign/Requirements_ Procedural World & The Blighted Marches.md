# Requirements: Procedural World & The Blighted Marches

**Status**: In progress on `feat/procedural-world-blight` (target: 0.0.2).
**Date**: October 2026
**Origin**: design grilling session, 2026-10-06. Every decision below was put to and answered by the designer.
**Related Documents**:
- `docs/DEsign/Requirements_ Reviving the Desert and Lakelands Biomes.md` (the format this follows)
- `docs/DEsign/Themed Chunk Contract.md`
- `docs/Implementation Plan_ The Expedition Loop & Progression Reboot.md` (Phase 5, Castle Tarmin & The Final Run)
- `docs/tuning_knobs.md`

---

## 1. Why this document exists

The shelter's portal gallery has five niches (`PPPPP` in `MazeChunkGenerator.homeTile`) and four portals. The fifth niche is a bare archway. This document fills it, and in doing so fixes the world it opens onto.

What the code did before this work:

| Concern | Before |
| :--- | :--- |
| Biome noise seed | `WorldConstants.WORLD_SEED = 12345`, a constant. Every save got the same biome map, even though `WorldManager.worldSeed` (`masterSeed` in the save) was already random per world. |
| Ring around the maze | A fixed FOREST ring at Chebyshev radius 11-15. |
| Tundra | A hard band: every land chunk with `y >= 16` is TUNDRA. |
| Desert / Lakelands / Forest | Humidity noise. |
| Castle Tarmin | A skybox model that is always due north, and a map marker at chunk (0, 5), inside the maze. No place in the world. |

Requirement from the designer: **all biome generation is procedural**. Direction must not predict biome. The one fixed relationship is that **the biome surrounding Castle Tarmin is the Blight**.

---

## 2. Scope

**In scope (all 0.0.2)**
1. Biome placement driven by the per-world seed (`masterSeed`), with a versioned world-gen algorithm so legacy saves keep their old layout.
2. A distance-band biome gradient that mirrors the portal ladder. Retire the forest ring and the Tundra hard band.
3. A procedurally placed Castle Tarmin site, with a guaranteed overland route from the maze.
4. The castle exterior: a large billboard in impassable terrain with a sealed front door. The skybox castle and the map marker point at the real site.
5. The Blighted Marches biome: generator, atmosphere, and ash weather.
6. Taint, the ward charm and Ashwater.
7. The Blight roster.
8. The Crimson Gate, the fifth portal.

**Out of scope**
- The castle interior (the "castle biome"). The front door is a defined hook only (§6.4). Phase 5 fills it.
- Placing the nine low-level unused monsters in other biomes. That is a separate issue.
- A Blight-themed underground. Strata under the Blight use the existing generator (§9).
- The dreamsmoke / mutation system. Taint is designed to feed it later, but not now.

---

## 3. World seed and world-gen versions

- `WorldManager.worldSeed` already rolls per world, is saved as `WorldSaveData.masterSeed`, and is rerolled when the explored world is wiped on death. Biome placement now reads it.
- The algorithm is **versioned**. `WorldSaveData.worldGenVersion`:
  - `1` = **legacy**: the exact pre-change `BiomeManager` (fixed seed 12345, forest ring, Tundra band, humidity). A save that has no `worldGenVersion` field reads as `1`.
  - `2` = **current**: everything in §4-§5.
- New games use the current version.
- **A legacy world upgrades to the current version whenever every chunk file is deleted.** That happens on death (`wipeExploredWorldOnDeath`) and on `resetWorldKeepDifficulty`. Nothing generated under one version ever meets a chunk generated under another, so there are no seams.
- `BiomeManager` is rebuilt whenever the seed or the version changes. Callers already fetch it through `WorldManager.getBiomeManager()` each time.

**Acceptance**
- A legacy `BiomeManager` returns the same biome as the old code for every chunk in a 121x121 window.
- Saving and loading round-trips `worldGenVersion`. A world-data JSON without the field loads as legacy.

---

## 4. Distance-band biome selection (version 2)

Let `d` be a chunk's Chebyshev distance beyond the maze edge: `max(|x|, |y|) - CENTRAL_MAZE_RADIUS`.

1. `d <= 0` is MAZE.
2. The castle region (§5) is BLIGHT.
3. `d` is **warped** by low-frequency noise (about ±4 chunks), so band edges are ragged and not square rings.
4. Each land biome has a **preferred band**. The values are tunables in `WorldConstants` and are listed in `docs/tuning_knobs.md`:

   | Biome | Band (warped `d`) | Portal tier |
   | :--- | :--- | :--- |
   | FOREST | 1-10 | Verdant Gate, depth 3 |
   | DESERT | 6-18 | Dune Gate, depth 5 |
   | LAKELANDS | 12-24 | Mist Gate, depth 7 |
   | TUNDRA | 18-32 | Frost Gate, depth 9 |
   | BLIGHT | 28+ | Crimson Gate, depth 11 |

5. Where bands overlap, a smooth **selector noise** picks among the eligible biomes, so regions are contiguous patches and not salt-and-pepper.
6. Elevation noise still places OCEAN and MOUNTAINS (impassable). It is **suppressed** within 6 chunks of the maze edge and along the castle corridor (§5).

**Acceptance**
- Across a seed sweep, the average distance at which each biome first appears follows the portal ladder: Forest < Desert < Lakelands < Tundra < Blight.
- Across a seed sweep, Tundra appears in every compass quadrant. Direction does not predict biome.
- Every portal destination is generated within the portal scan range (60 chunks) for every seed in the sweep.

---

## 5. Castle Tarmin site

- **Placement**: a direction chosen from the seed, at a Euclidean distance of 40-60 chunks from the origin. The site chunk is the castle chunk.
- **Blight guarantee**: every chunk within about 8 chunks of the site (with a noise-ragged edge) is BLIGHT, whatever its band.
- **Overland route**: every chunk within 1 chunk of the straight line from the maze to the site has OCEAN and MOUNTAINS suppressed. A band that wide always contains a 4-connected path, so the castle can always be reached on foot. The portal is the shortcut, not the only way.
- The site is exposed through `BiomeManager.getCastleSite()`, which returns null for legacy worlds.

**Acceptance**
- Over a seed sweep: the site is 40-60 chunks out, sits in BLIGHT, and a 4-connected path of passable chunks runs from the maze edge to the site.

---

## 6. The castle exterior

### 6.1 Castle chunk
The site chunk is generated by the Blight generator in **castle mode**:
- The castle billboard stands in a small courtyard at the chunk centre, inside a 15x15 block of impassable ash ridge (`CASTLE_HALF` 7). The ridges stand low (`BLIGHT_RIDGE_Y` 1.6) so the castle rises over them.
- A ring road runs around the block, and each edge gate's avenue meets the ring, so every gate reaches every other.
- One **front door** (the `castle_gate` prop) is set into the block on the side facing the maze, along the dominant axis. Braziers flank the doorstep, and a flask of Ashwater lies beside it. A player arriving in this chunk stands on the doorstep.
- The design discussion first proposed a 3x3-chunk citadel. One chunk was chosen instead, because the renderer draws only the current chunk: a castle spread across nine chunks would be invisible from eight of them.

### 6.2 Billboard art
`assets/images/blight/castle_tarmin_billboard.png` is rendered from `models/skybox/castle_citadel.obj` by `tools/blender/render_castle_billboard.py`, so the close-up matches the skybox silhouette. The first pass is generated. An art-polish pass is a `ready-for-human` follow-up.

### 6.3 Skybox hand-off
- The skybox castle points along the bearing from the player to the site, and grows as the player closes in. The South Spire moves to the opposite bearing.
- Within 1 chunk of the site, the skybox castle is hidden and the world billboard takes over.
- Legacy worlds keep the old due-north behaviour.
- `CastleMapScreen` marks the real site. Legacy worlds keep (0, 5).

### 6.4 The sealed door (hook for Phase 5)
Bumping the door reports that the gates are sealed and need the ancient seals. `CastleGate` owns the check, and today it always refuses. Phase 5 replaces that refusal with a transition into the castle zone.

---

## 7. The Blighted Marches (`Biome.BLIGHT`)

- **Display name**: "The Blighted Marches".
- **Generator**: `BlightChunkGenerator`, a 36x36 chunk with the same contract as Tundra (four edge gates, connectivity flood-fill, maze-border handling). Content:
  - Low ruined ridges.
  - **Rot pools**: the existing `LiquidType.BLACK_MUCK` (necrotic sludge). They keep their toxicity, and they add Taint while you stand in them (§8).
  - Dead and blackened trees.
  - Props from `props.json`: gibbet cage, head spike, skull pile, bone pile, gravestones, war banner, ruined pillar, rubble, twisted root, bramble.
  - A wrecked Legion camp.
- **Atmosphere**: crimson-ochre fog at roughly Tundra's distance, permanently overcast (no CLEAR weather), and the castle visible as the landmark.
- **Weather**: a new `WeatherType.ASHFALL`, rendered as grey flakes with the odd ember. The Blight allows FOG, ASHFALL and, keeping the Maelstrom rule that every biome can snow, a rare SNOW. It never gets CLEAR weather.
- **Floor and cliff textures**: `images/floor_blight.png` and `images/blight_cliff.png`, ash-recoloured from the existing biome textures by `tools/make_blight_textures.py`.

---

## 8. Taint

A 0-100 meter on `PlayerStats`, saved with the player.

| Rule | Value (tunable) |
| :--- | :--- |
| Rises | Only on Blight **surface** chunks (depth 1). Never underground. |
| Base rate | 0.06 per turn |
| Night / dusk | x2 |
| Standing in a rot pool | +0.5 per turn |
| Carrying a ward charm | Gain x0.5 |
| Hit by a Blighted monster | +3 |
| 25+ | Natural regeneration takes twice as long |
| 50+ | Max HP -15% |
| 75+ | Max HP -30% |
| 100 | Tarmin Legion in the chunk are woken and hunt the player. A Legion patrol musters at intervals. |
| Cleared by | Resting in the shelter bed. Death (a new expedition). |
| Ashwater | Cleanses 40 |

- **Persistence**: Taint survives between expeditions until the player rests in the bed.
- **HUD**: a pill in `StatusPillBar` that appears only while Taint > 0, shows the threshold tier, and explains the cure. Its colour comes from `HudSkin`, and its text survives `UiGlyphs.sanitize`.
- **Items**: `WARD_CHARM` works passively when carried. `ASHWATER` is a usable consumable. Both drop in the Blight. Shelter stock comes later.

---

## 9. Roster and strata

| Group | Monsters | Faction |
| :--- | :--- | :--- |
| Legion patrols | ORC, HOBGOBLIN, GARGOYLE (the `RUINED_CASTLE` roster without the golem) | TARMIN_LEGION |
| Blight denizens (common) | SPECTER, SKELETAL_WIZARD, DEMON_SLIME | UNDEAD |
| Rare elite | FALL_ANGEL (about 1 in 12 chunks) | UNDEAD |
| Blighted fauna | Existing beasts (werewolf, owlbear, ghoul, zombie, giant scorpion) with a Blighted tint, +25% HP, and Taint on hit | BEASTS_AND_VERMIN |

- BRINGER_OF_DEATH is **not** in the Blight. It is kept for the castle and the Final Run.
- `blighted` and `faction` are saved in `ChunkData.MonsterData`, so they survive a chunk reload.
- The Blight brings its own roster. `SpawnManager.spawnSuppliesOnly()` adds the usual items, containers and debris, but no generic monsters.
- **Strata** under the Blight use the existing generator. Effective difficulty already rises with distance, so the far band gives a higher baseline. There is no Taint underground.

---

## 10. The Crimson Gate

- `BiomePortal.BLIGHT`, `ShelterAltar.Station.PORTAL_BLIGHT`, and `Item.ItemType.BIOME_PORTAL_BLIGHT`.
- **Cost**: 65 Crests. **Required depth**: 11. It goes in the fifth niche.
- **Arrival**: the first BLIGHT chunk on the castle corridor, walking out from the maze (`BiomeManager.findCorridorBlightEntry`). That is the Blight edge facing the maze, and it is guaranteed to connect overland to the castle. A merely nearest Blight chunk could be a pocket cut off by sea.

---

## 11. Issue slices

1. World seed drives biomes, plus world-gen versioning (§3)
2. Distance-band selection, retiring the forest ring and the Tundra band (§4). Blocked by 1.
3. Castle site and overland route (§5). Blocked by 2.
4. Castle exterior, billboard, sealed door, skybox and map (§6). Blocked by 3 and 5.
5. Blight generator, atmosphere and ash weather (§7). Blocked by 2.
6. Taint, ward charm and Ashwater (§8). Blocked by 5.
7. Blight roster and Blighted variants (§9). Blocked by 5.
8. Crimson Gate (§10). Blocked by 5.
9. Separate: place the nine unused low-level monsters in existing biomes.
