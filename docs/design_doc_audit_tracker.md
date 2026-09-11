# Design Document Audit Tracker

Last Updated: 2026-09-11
Audit Status: In Progress (Session 1 - Round 3)
Current Branch: `audit/design-docs-review`

## Overview & Methodology
This tracker maintains the multi-session audit of legacy design documents originally located in [docs/DEsign](file:///c:/Users/denni/Desktop/Tarmin2/docs/DEsign).
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
| 14 | `Procedural Creature Assembly_ Gap-Free Stitching.pdf` | Monster / Mesh | **COMPLETE** | `docs/complete/` | Mesh extraction, intersector, bridge builder built in `stitcher/` |
| 15 | `Design Document_ 3D Chunk & Dungeon Level Integration.pdf` | World / Engine | **SUPERSEDED** | `docs/archive/` | Superseded by Open World Strata Descent architecture |
| 16 | `Game Design Analysis and Enhancement (1).pdf` | Meta / Research | **ARCHIVE / REFERENCE** | `docs/archive/` | Theoretical research paper on asymptotic dungeon |
| 17 | `Minotaur spec.pdf` | Specification | **ARCHIVE / REFERENCE** | `docs/archive/` | Original founding remake specification |
| 18 | `Implementation Plan_ Modular Creature System & Weather Engine.md` | Monsters / Weather | **SUPERSEDED** | `docs/archive/` | Weather & DNA built; Decal/physics graph replaced by 2.5D pipeline |
| 19 | `Implementation Plan_ The Expedition Loop & Progression Reboot.md` | Architecture Core | **ACTIVE FOUNDATION** | `docs/` root | Living canonical blueprint per `AGENTS.md` |
| 20 | `Implementation Plan_ 3D-to-2.5D Monster Rendering Pipeline.md` | Monster / Render | **ACTIVE SPEC (WIP)** | `docs/DEsign/` | Core rendering engine live in F9 dev screen; needs runtime dungeon spawning |
| 21 | `Math-Infused Dice Combat System Design (2).pdf` | Combat / Design | **UNDER REVIEW** | `docs/DEsign/` | Revisit from scratch from a gameplay and design perspective |

---

## Active Workstreams in `docs/DEsign/`

### Workstream 1: Procedural Monster Spawning in Live Strata (`Implementation Plan_ 3D-to-2.5D Monster Rendering Pipeline.md`)
- **Status**: Engine built (`CreatureBaker`, `BakedMonsterSprite`, `creature_stylize.frag`, `CreatureDevScreen`), pending live dungeon generation hook.
- **Goal**: Allow `SpawnManager.spawnMonsters()` to optionally roll DNA, assemble parts, bake 2.5D sprites, and spawn procedural chimeras in deeper strata alongside handcrafted monsters.

### Workstream 2: Gameplay Review of Dice Combat (`Math-Infused Dice Combat System Design (2).pdf`)
- **Status**: Under gameplay design review. The 3D Bullet physics rolling engine exists in `StochasticManager` and can be triggered via Key 7 / `playerAttackWithDice()`.
- **Goal**: Re-evaluate from scratch whether physical 3D dice rolling, dice collection, and bone traits fit the core "Delve & Return" expedition loop, or whether it creates pacing friction against fast bump-to-attack grid combat.
