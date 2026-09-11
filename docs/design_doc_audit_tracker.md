# Design Document Audit Tracker

Last Updated: 2026-09-11
Audit Status: In Progress (Session 1 - Round 2)
Current Branch: `audit/design-docs-review`

## Overview & Methodology
This tracker maintains the multi-session audit of legacy design documents located in [docs/DEsign](file:///c:/Users/denni/Desktop/Tarmin2/docs/DEsign).
For each document:
1. **Fact-Finding Scan**: Inspect codebase classes, systems, and assets to verify if proposed architectural items exist.
2. **Gap Analysis**: Detail what was implemented vs what is missing or changed.
3. **Grill & Decision**: Review with the user via `/grill-me` whether missing mechanics should be built, adapted to the current expedition loop architecture, or marked complete/superseded.
4. **Relocation**: Move fully implemented documents to [docs/complete](file:///c:/Users/denni/Desktop/Tarmin2/docs/complete). Move obsolete/superseded documents to [docs/archive](file:///c:/Users/denni/Desktop/Tarmin2/docs/archive).

---

## Master Inventory (21 Documents)

| # | Document | Domain | Status | Location | Notes |
|---|---|---|---|---|---|
| 1 | `Design Document_ Combat Damage Indicators.pdf` | Combat / UI | **COMPLETE** | `docs/complete/` | Unified into `AnimationManager` & `CombatManager.showDamageText` |
| 2 | `Design Document_ Biome Fog of War & Seamless Chunk Transitions.pdf` | World / Render | **COMPLETE** | `docs/complete/` | Built in `Biome`, `WorldManager`, `World3DRenderer` |
| 3 | `Design Document_ Dynamic Weather System.pdf` | Weather / Ambience | **COMPLETE** | `docs/complete/` | Built in `weather/` package, storm shaders, skybox |
| 4 | `Design Document_ Loot Modifier System.pdf` | Items / Economy | **COMPLETE** | `docs/complete/` | Built in `LootTable`, `ItemModifier`, `ModifierType` |
| 5 | `Design Document_ Monster AI System (Phase 1).pdf` | AI / Monsters | **COMPLETE** | `docs/complete/` | Built and surpassed in `MonsterAiManager` state machine |
| 6 | `Design Document_ NetHack-Style Player Attributes.pdf` | Player / RPG | **COMPLETE** | `docs/complete/` | Built in `PlayerStats`, `Player`, modifier bonuses |
| 7 | `Design Document_ Player Home Integration.pdf` | Shelter / World | **COMPLETE** | `docs/complete/` | Built via `homeTiles`, shelter exclusion radius, `ShelterChest` |
| 8 | `Design Document_ Randomized Thematic Levels.pdf` | World / Generation | **COMPLETE** | `docs/complete/` | Built in `RetroTheme.java` and `WorldManager` |
| 9 | `Design Document_ Ranged Combat & Ballistics Overhaul.pdf` | Combat / Projectiles | **COMPLETE** | `docs/complete/` | Built via `HitResult`, `raycastProjectile`, `AnimationManager` |
| 10 | `Design Document_ Spawn Logic & Balancing Overhaul (1).pdf` | Spawning / Balance | **COMPLETE** | `docs/complete/` | Built via `spawntables.json`, `SpawnTableData`, `MonsterVariant` |
| 11 | `Design Document_ Status Effect System.docx` | Combat / Effects | **COMPLETE** | `docs/complete/` | Built in `effects/` package, `StatusManager` |
| 12 | `Design Document_ _Project Viscera_ (Retro Gore System).pdf` | Gore / VFX | **COMPLETE** | `docs/complete/` | Built in `gore/` package, `GoreManager` |
| 13 | `Forest Biome Implementation (Revised).pdf` | Biomes / Render | **COMPLETE** | `docs/complete/` | Built via `Scenery`, `ForestChunkGenerator` |
| 14 | `Design Document_ 3D Chunk & Dungeon Level Integration.pdf` | World / Engine | **SUPERSEDED** | `docs/archive/` | Superseded by Open World Strata Descent architecture |
| 15 | `Game Design Analysis and Enhancement (1).pdf` | Meta / Research | **ARCHIVE / REFERENCE** | `docs/archive/` | Theoretical research paper on asymptotic dungeon |
| 16 | `Minotaur spec.pdf` | Specification | **ARCHIVE / REFERENCE** | `docs/archive/` | Original founding remake specification |
| 17 | `Implementation Plan_ The Expedition Loop & Progression Reboot.md` | Architecture Core | **ACTIVE FOUNDATION** | `docs/` root | Living canonical blueprint per `AGENTS.md` |
| 18 | `Procedural Creature Assembly_ Gap-Free Stitching.pdf` | Monster / Mesh | **COMPLETE** | Pending Round 2 | `GapBridgeBuilder`, `MeshSurfaceIntersector`, etc. implemented |
| 19 | `Implementation Plan_ Modular Creature System & Weather Engine.md` | Monsters / Weather | **SUPERSEDED / PARTIAL** | Pending Round 2 | Weather is complete; Decal/physics graph superseded by 2.5D pipeline |
| 20 | `Implementation Plan_ 3D-to-2.5D Monster Rendering Pipeline.md` | Monster / Render | **PARTIAL (DEV ONLY)** | Pending Round 2 | `CreatureBaker` & `CreatureDevScreen` live; live dungeon spawning missing |
| 21 | `Math-Infused Dice Combat System Design (2).pdf` | Combat / Dice | **COMPLETE / ADAPTED** | Pending Round 2 | Bullet physics 3D dice implemented with themed RPG dice & Bone traits |

---

## Detailed Reviews & Status Breakdown

### 18. `Procedural Creature Assembly_ Gap-Free Stitching.pdf`
- **Specification**: Outlines algorithmic requirements for assembling modular 3D creature components and stitching vertices across boundaries without visual gaps. Specifies mesh vertex extraction, surface intersection, and bridge geometry generation.
- **Codebase Findings**:
  - [MeshVertexExtractor.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/monster/stitcher/MeshVertexExtractor.java) exists and is fully implemented.
  - [MeshSurfaceIntersector.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/monster/stitcher/MeshSurfaceIntersector.java) exists and is fully implemented.
  - [GapBridgeBuilder.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/monster/stitcher/GapBridgeBuilder.java) exists and is fully implemented.
  - Integrated into `CreatureStitcher` and `StitchedCreature`.
- **Status**: **COMPLETE**. The algorithmic implementation matches the paper.

### 19. `Implementation Plan_ Modular Creature System & Weather Engine.md`
- **Specification**:
  - Part 1: Modular Stitcher (JSON DNA, parts manifest, socket attachment).
  - Part 2: Weather Engine GPU fog/shader upgrade.
  - Part 3: `CreatureBillboard` (DecalBatch) and `CreaturePhysicsGraph` (Bullet physics joint constraints per limb).
- **Codebase Findings**:
  - Part 1 is fully built in `com.bpm.minotaur.gamedata.monster.stitcher`.
  - Part 2 is fully built in `com.bpm.minotaur.weather`.
  - Part 3 (`CreatureBillboard` / `CreaturePhysicsGraph`) was evaluated and deliberately superseded by `Implementation Plan_ 3D-to-2.5D Monster Rendering Pipeline.md`, which proved that the engine's column raycaster (`EntityRenderer`) already handles 2.5D directional monster billboarding without needing DecalBatch or multi-body physics joint overhead.
- **Status**: **SUPERSEDED / COMPLETED IN SUCCESSOR**.

### 20. `Implementation Plan_ 3D-to-2.5D Monster Rendering Pipeline.md`
- **Specification**:
  - Two-pass baking pipeline: ModelBatch 3D render to scene FBO, then SpriteBatch + `creature_stylize.frag` pixelation/palette mapping to style FBO.
  - Directional baking (4 facings: N, E, S, W) into `BakedMonsterSprite`.
  - Integration with `EntityRenderer.drawMonsterTexture()`.
  - Dynamic runtime procedural creature generation during dungeon play.
- **Codebase Findings**:
  - [CreatureBaker.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/rendering/CreatureBaker.java) and [BakedMonsterSprite.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/rendering/BakedMonsterSprite.java) are completely built and functional.
  - [creature_stylize.frag](file:///c:/Users/denni/Desktop/Tarmin2/assets/shaders/creature_stylize.frag) is implemented.
  - [CreatureDevScreen.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/screens/CreatureDevScreen.java) is fully operational (accessed via F9) to assemble, bake, view, and test limb severing.
  - **Gap**: Procedurally stitched chimeras are NOT hooked into runtime level generation (`SpawnManager.spawnMonsters()`). The live game currently spawns standard monsters defined in `assets/data/monsters.json`.
- **Status**: **PARTIAL (CORE ENGINE BUILT, LIVE IN-GAME SPAWNING UNWIRED)**.

### 21. `Math-Infused Dice Combat System Design (2).pdf`
- **Specification**: "The Axiomatic Engine" - integrating non-Euclidean stochastics, mathematical metaphysics (axioms $\pi, e, 1, 0$), topology, and Bullet physics 3D dice simulation.
- **Codebase Findings**:
  - Bullet physics 3D dice simulation is completely implemented in [StochasticManager.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/managers/StochasticManager.java) (`btDiscreteDynamicsWorld`, rigid bodies, dice settling, face detection).
  - Wired directly into [CombatManager.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/managers/CombatManager.java) (`PHYSICS_RESOLUTION` and `PHYSICS_DELAY`).
  - Adapted into rich gameplay themed dice ([DiceFactory.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/dice/DiceFactory.java)) and [BoneTrait.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/dice/BoneTrait.java), replacing the hyper-abstract mathematical axioms with RPG-grounded traits.
- **Status**: **COMPLETE / ADAPTED**.
