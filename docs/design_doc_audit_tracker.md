# Design Document Audit Tracker

Last Updated: 2026-09-11
Audit Status: Complete (Session 1)
Current Branch: `audit/design-docs-review`

## Overview & Executive Summary
This document tracks the comprehensive codebase audit of legacy design documents originally located in [docs/DEsign](file:///c:/Users/denni/Desktop/Tarmin2/docs/DEsign).
Every document was evaluated against production code, verified with file links, and categorized via interactive `/grill-me` alignment.

### Summary Metrics
- **Total Documents Audited**: 21
- **Moved to `docs/complete/`**: 14 (verified fully implemented in production code)
- **Moved to `docs/archive/`**: 5 (superseded proposals and historical references)
- **Established as Canonical Foundation**: 1 (living blueprint at `docs/`)
- **Active in `docs/DEsign/`**: 2 (refined, actionable feature specifications)

---

## Master Inventory & Disposition Matrix

| # | Original Document | Category | Final Status | Current Location | Codebase Implementation & Decision Notes |
|---|---|---|---|---|---|
| 1 | `Design Document_ Combat Damage Indicators.pdf` | Combat / UI | **COMPLETE** | [docs/complete/](file:///c:/Users/denni/Desktop/Tarmin2/docs/complete) | Implemented via `AnimationManager.renderDamageText` and `CombatManager.showDamageText`. |
| 2 | `Design Document_ Biome Fog of War & Seamless Chunk Transitions.pdf` | World / Render | **COMPLETE** | [docs/complete/](file:///c:/Users/denni/Desktop/Tarmin2/docs/complete) | Implemented in `Biome`, `WorldManager`, `World3DRenderer`. |
| 3 | `Design Document_ Dynamic Weather System.pdf` | Weather / Ambience | **COMPLETE** | [docs/complete/](file:///c:/Users/denni/Desktop/Tarmin2/docs/complete) | Implemented in `com.bpm.minotaur.weather`, storm shaders, 3D skybox. |
| 4 | `Design Document_ Loot Modifier System.pdf` | Items / Economy | **COMPLETE** | [docs/complete/](file:///c:/Users/denni/Desktop/Tarmin2/docs/complete) | Implemented in `LootTable`, `ItemModifier`, `ModifierType`. |
| 5 | `Design Document_ Monster AI System (Phase 1).pdf` | AI / Monsters | **COMPLETE** | [docs/complete/](file:///c:/Users/denni/Desktop/Tarmin2/docs/complete) | Implemented and surpassed in `MonsterAiManager` state machine. |
| 6 | `Design Document_ NetHack-Style Player Attributes.pdf` | Player / RPG | **COMPLETE** | [docs/complete/](file:///c:/Users/denni/Desktop/Tarmin2/docs/complete) | Implemented in `PlayerStats`, `Player`, modifier bonuses. |
| 7 | `Design Document_ Player Home Integration.pdf` | Shelter / World | **COMPLETE** | [docs/complete/](file:///c:/Users/denni/Desktop/Tarmin2/docs/complete) | Implemented via `homeTiles`, shelter exclusion radius, `ShelterChest`. |
| 8 | `Design Document_ Randomized Thematic Levels.pdf` | World / Generation | **COMPLETE** | [docs/complete/](file:///c:/Users/denni/Desktop/Tarmin2/docs/complete) | Implemented in `RetroTheme.java` and `WorldManager`. |
| 9 | `Design Document_ Ranged Combat & Ballistics Overhaul.pdf` | Combat / Projectiles | **COMPLETE** | [docs/complete/](file:///c:/Users/denni/Desktop/Tarmin2/docs/complete) | Implemented via `HitResult`, `raycastProjectile`, `AnimationManager`. |
| 10 | `Design Document_ Spawn Logic & Balancing Overhaul (1).pdf` | Spawning / Balance | **COMPLETE** | [docs/complete/](file:///c:/Users/denni/Desktop/Tarmin2/docs/complete) | Implemented via `spawntables.json`, `SpawnTableData`, `MonsterVariant`. |
| 11 | `Design Document_ Status Effect System.docx` | Combat / Effects | **COMPLETE** | [docs/complete/](file:///c:/Users/denni/Desktop/Tarmin2/docs/complete) | Implemented in `com.bpm.minotaur.gamedata.effects`, `StatusManager`. |
| 12 | `Design Document_ _Project Viscera_ (Retro Gore System).pdf` | Gore / VFX | **COMPLETE** | [docs/complete/](file:///c:/Users/denni/Desktop/Tarmin2/docs/complete) | Implemented in `com.bpm.minotaur.gamedata.gore`, `GoreManager`. |
| 13 | `Forest Biome Implementation (Revised).pdf` | Biomes / Render | **COMPLETE** | [docs/complete/](file:///c:/Users/denni/Desktop/Tarmin2/docs/complete) | Implemented via `Scenery`, `ForestChunkGenerator`. |
| 14 | `Procedural Creature Assembly_ Gap-Free Stitching.pdf` | Monster / Mesh | **COMPLETE** | [docs/complete/](file:///c:/Users/denni/Desktop/Tarmin2/docs/complete) | Mesh extraction, intersector, bridge builder implemented in `stitcher/`. |
| 15 | `Design Document_ 3D Chunk & Dungeon Level Integration.pdf` | World / Engine | **SUPERSEDED** | [docs/archive/](file:///c:/Users/denni/Desktop/Tarmin2/docs/archive) | Superseded by Open World Strata Descent architecture. |
| 16 | `Implementation Plan_ Modular Creature System & Weather Engine.md` | Monsters / Weather | **SUPERSEDED** | [docs/archive/](file:///c:/Users/denni/Desktop/Tarmin2/docs/archive) | Decal/physics graph replaced by 2.5D pipeline; weather is completed. |
| 17 | `Math-Infused Dice Combat System Design (2).pdf` | Combat / Design | **ARCHIVED / REPLACED** | [docs/archive/](file:///c:/Users/denni/Desktop/Tarmin2/docs/archive) | Legacy paper archived; replaced by grounded Augury Bones spec. |
| 18 | `Game Design Analysis and Enhancement (1).pdf` | Meta / Research | **REFERENCE** | [docs/archive/](file:///c:/Users/denni/Desktop/Tarmin2/docs/archive) | Theoretical research paper on asymptotic dungeon emergence. |
| 19 | `Minotaur spec.pdf` | Specification | **REFERENCE** | [docs/archive/](file:///c:/Users/denni/Desktop/Tarmin2/docs/archive) | Original founding remake specification. |
| 20 | `Implementation Plan_ The Expedition Loop & Progression Reboot.md` | Architecture Core | **ACTIVE FOUNDATION** | [docs/](file:///c:/Users/denni/Desktop/Tarmin2/docs) root | Canonical living blueprint mandated by `AGENTS.md`. |
| 21 | `Implementation Plan_ 3D-to-2.5D Monster Rendering Pipeline.md` | Monster / Render | **ACTIVE SPEC (WIP)** | [docs/DEsign/](file:///c:/Users/denni/Desktop/Tarmin2/docs/DEsign) | Engine functional in F9 dev screen; Section 14 added for Strata 3+ Aberrations. |
| 22 | `Design Document_ Augury Bones & Boss Stochastic System.md` | Combat / Design | **ACTIVE SPEC (NEW)** | [docs/DEsign/](file:///c:/Users/denni/Desktop/Tarmin2/docs/DEsign) | Fresh pragmatic spec: Shelter Hub bone crafting & Altar/Boss 3D dice rolls. |

---

## Active Workstreams in `docs/DEsign/`

### 1. Deep Strata Aberrations & Procedural Spawning
- **Spec**: [Implementation Plan_ 3D-to-2.5D Monster Rendering Pipeline.md](file:///c:/Users/denni/Desktop/Tarmin2/docs/DEsign/Implementation%20Plan_%203D-to-2.5D%20Monster%20Rendering%20Pipeline.md)
- **Status**: Rendering pipeline and dev viewer (`CreatureDevScreen`) complete.
- **Next Task**: Hook `SpawnManager.java` to roll and bake procedural Chimeras on Strata 3+ as rare minibosses.

### 2. Augury Bones & Boss Climax Stochastic System
- **Spec**: [Design Document_ Augury Bones & Boss Stochastic System.md](file:///c:/Users/denni/Desktop/Tarmin2/docs/DEsign/Design%20Document_%20Augury%20Bones%20&%20Boss%20Stochastic%20System.md)
- **Status**: 3D Bullet physics engine (`StochasticManager`) and dice definitions exist.
- **Next Task**: Connect bone drops (`GIB_BONE`, `GIB_SKULL`) to a Shelter Hub Carving Bench and wire the 3D rolling tray to Divine Altars and Minotaur Boss phases.
