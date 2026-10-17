# Design Document Audit Tracker

Last Updated: 2026-10-11
Audit Status: Complete (Session 2 Audit & Triage)
Current Branch: `develop`

## Overview & Executive Summary
This document tracks the comprehensive codebase audits of design specifications originally located in [docs/DEsign](file:///c:/workspace/Tarmin2/docs/DEsign).
Every document was evaluated against production code, verified with file links, and categorized.

### Summary Metrics (Cumulative)
- **Total Documents Audited Across Sessions**: 35
- **Moved to `docs/complete/`**: 25 (verified fully implemented in production code)
- **Moved to `docs/archive/`**: 6 (superseded proposals and historical references)
- **Established as Canonical Foundation / Contract**: 2 (`Implementation Plan_ The Expedition Loop & Progression Reboot.md`, `Themed Chunk Contract.md`)
- **Active Specs in `docs/DEsign/`**: 2 (refined, actionable feature specifications with pending phases)

---

## Master Inventory & Disposition Matrix

| # | Original Document | Category | Final Status | Current Location | Codebase Implementation & Decision Notes |
|---|---|---|---|---|---|
| 1 | `Design Document_ Combat Damage Indicators.pdf` | Combat / UI | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented via `AnimationManager.renderDamageText` and `CombatManager.showDamageText`. |
| 2 | `Design Document_ Biome Fog of War & Seamless Chunk Transitions.pdf` | World / Render | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented in `Biome`, `WorldManager`, `World3DRenderer`. |
| 3 | `Design Document_ Dynamic Weather System.pdf` | Weather / Ambience | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented in `com.bpm.minotaur.weather`, storm shaders, 3D skybox. |
| 4 | `Design Document_ Loot Modifier System.pdf` | Items / Economy | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented in `LootTable`, `ItemModifier`, `ModifierType`. |
| 5 | `Design Document_ Monster AI System (Phase 1).pdf` | AI / Monsters | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented and surpassed in `MonsterAiManager` state machine. |
| 6 | `Design Document_ NetHack-Style Player Attributes.pdf` | Player / RPG | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented in `PlayerStats`, `Player`, modifier bonuses. |
| 7 | `Design Document_ Player Home Integration.pdf` | Shelter / World | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented via `homeTiles`, shelter exclusion radius, `ShelterChest`. |
| 8 | `Design Document_ Randomized Thematic Levels.pdf` | World / Generation | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented in `RetroTheme.java` and `WorldManager`. |
| 9 | `Design Document_ Ranged Combat & Ballistics Overhaul.pdf` | Combat / Projectiles | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented via `HitResult`, `raycastProjectile`, `AnimationManager`. |
| 10 | `Design Document_ Spawn Logic & Balancing Overhaul (1).pdf` | Spawning / Balance | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented via `spawntables.json`, `SpawnTableData`, `MonsterVariant`. |
| 11 | `Design Document_ Status Effect System.docx` | Combat / Effects | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented in `com.bpm.minotaur.gamedata.effects`, `StatusManager`. |
| 12 | `Design Document_ _Project Viscera_ (Retro Gore System).pdf` | Gore / VFX | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented in `com.bpm.minotaur.gamedata.gore`, `GoreManager`. |
| 13 | `Forest Biome Implementation (Revised).pdf` | Biomes / Render | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented via `Scenery`, `ForestChunkGenerator`. |
| 14 | `Procedural Creature Assembly_ Gap-Free Stitching.pdf` | Monster / Mesh | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Mesh extraction, intersector, bridge builder implemented in `stitcher/`. |
| 15 | `Design Document_ 3D Chunk & Dungeon Level Integration.pdf` | World / Engine | **SUPERSEDED** | [docs/archive/](file:///c:/workspace/Tarmin2/docs/archive) | Superseded by Open World Strata Descent architecture. |
| 16 | `Implementation Plan_ Modular Creature System & Weather Engine.md` | Monsters / Weather | **SUPERSEDED** | [docs/archive/](file:///c:/workspace/Tarmin2/docs/archive) | Decal/physics graph replaced by 2.5D pipeline; weather is completed. |
| 17 | `Math-Infused Dice Combat System Design (2).pdf` | Combat / Design | **ARCHIVED / REPLACED** | [docs/archive/](file:///c:/workspace/Tarmin2/docs/archive) | Legacy paper archived; replaced by grounded Augury Bones spec. |
| 18 | `Game Design Analysis and Enhancement (1).pdf` | Meta / Research | **REFERENCE** | [docs/archive/](file:///c:/workspace/Tarmin2/docs/archive) | Theoretical research paper on asymptotic dungeon emergence. |
| 19 | `Minotaur spec.pdf` | Specification | **REFERENCE** | [docs/archive/](file:///c:/workspace/Tarmin2/docs/archive) | Original founding remake specification. |
| 20 | `Implementation Plan_ The Expedition Loop & Progression Reboot.md` | Architecture Core | **ACTIVE FOUNDATION** | [docs/](file:///c:/workspace/Tarmin2/docs) root | Canonical living blueprint mandated by `AGENTS.md`. |
| 21 | `Requirements_ Bridge Integrity Boss.md` | Combat / Progression | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented in `BridgeBoss.java`, `DoomManager.java`, and tested in `BridgeBossProgressionTest.java`. |
| 22 | `Requirements_ Reviving the Desert and Lakelands Biomes.md` | World / Biomes | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented in `DesertChunkGenerator`, `LakelandsChunkGenerator`, `BiomePortal`, and connectivity tests. |
| 23 | `Requirements_ Shelter Roads.md` | World / Progression | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented in `ShelterNetwork`, `BeaconPlanner`, `CastleGate`, and `ShelterNetworkTest`. |
| 24 | `Requirements_ Procedural World & The Blighted Marches.md` | World / Biomes | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented in `BlightChunkGenerator`, `BlightAtmosphere`, `WeatherType.ASHFALL`, and `PlayerStats` (Taint). |
| 25 | `Requirements_ Expedition Map.md` | UI / Map System | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented in `ExpeditionMapScreen`, `MapKnowledge`, `ClassicMapScreen`, and `MapKnowledgeTest`. |
| 26 | `Enhanced_Flyover_Attract_Mode_Spec.md` | Rendering / Title | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented in `AttractModeRenderer`, `AttractWarDirector`, and `AttractWarDirectorTest`. |
| 27 | `events.md` & `event-art-prompts.md` | Narrative / Events | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented in `EventWindow`, `events.json`, floor trigger placement, and integrated pixel art. |
| 28 | `personality_traits.md` | Character / RPG | **COMPLETE** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Implemented in `TraitSelectionModal`, `traits.json` (all 18 traits), and `PlayerStats`. |
| 29 | `living-war-remaining-work.md` & `living-war-asset-prompts.md` | War / Audio / Art | **COMPLETE (0.0.2 MVP)** | [docs/complete/](file:///c:/workspace/Tarmin2/docs/complete) | Verified Natural Siege lifecycle, Commissary, Campfire rest, audio/art integration; passes `SiegeTest`. |
| 30 | `Dungeon Crawler Event Generation.md` | Narrative / Events | **SUPERSEDED** | [docs/archive/](file:///c:/workspace/Tarmin2/docs/archive) | Replaced by `events.md`. |
| 31 | `Themed Chunk Contract.md` | Architecture Core | **ACTIVE CONTRACT** | [docs/DEsign/](file:///c:/workspace/Tarmin2/docs/DEsign) | All 6 themes pass `ThemeContractTest.java`; canonical standard for chunk authoring. |
| 32 | `Requirements_ Spell System Overhaul.md` | Spells / Progression | **ACTIVE SPEC (PHASE 1 & 2 COMPLETE)** | [docs/DEsign/](file:///c:/workspace/Tarmin2/docs/DEsign) | Phase 1 (slots, Tomes, hotbar) and Phase 2 (132 spell re-authoring, archetype VFX/audio unification, Spellbook UX overhauled) complete. |
| 33 | `Implementation Plan_ The Houses of the Maze.md` / `The Living War` | Living War / History | **ACTIVE SPEC (0.0.2 MVP COMPLETE)** | [docs/DEsign/](file:///c:/workspace/Tarmin2/docs/DEsign) | Slice 1 complete in `com.bpm.minotaur.gamedata.history`; Lord duel (W39) & Deep Towns deferred to post-0.0.2. |
| 34 | `Implementation Plan_ 3D-to-2.5D Monster Rendering Pipeline.md` | Monster / Render | **ACTIVE SPEC (WIP)** | [docs/DEsign/](file:///c:/workspace/Tarmin2/docs/DEsign) | Rendering pipeline, stitcher, and dev viewer (`CreatureDevScreen`) complete; Strata 3+ spawning pending. |
| 35 | `Design Document_ Augury Bones & Boss Stochastic System.md` | Combat / Design | **ACTIVE SPEC (WIP)** | [docs/DEsign/](file:///c:/workspace/Tarmin2/docs/DEsign) | 3D Bullet physics engine (`StochasticManager`) & bone crafting live; Altar card & Minotaur boss duel pending. |

---

## Active Workstreams in `docs/DEsign/`

### 1. Spell System Overhaul (Phase 2 Visuals)
- **Spec**: [Requirements_ Spell System Overhaul.md](file:///c:/workspace/Tarmin2/docs/DEsign/Requirements_%20Spell%20System%20Overhaul.md)
- **Status**: Phase 1 (slots, Tomes, progression, bindings, tests) is 100% complete.
- **Next Task**: Phase 2 visual data authoring (clean up 132 `FORCE_MISSILE` defaults, replace `isHealing` substring with data flag, per-archetype effects).

### 2. Deep Strata Aberrations & Procedural Spawning
- **Spec**: [Implementation Plan_ 3D-to-2.5D Monster Rendering Pipeline.md](file:///c:/workspace/Tarmin2/docs/DEsign/Implementation%20Plan_%203D-to-2.5D%20Monster%20Rendering%20Pipeline.md)
- **Status**: Rendering pipeline and dev viewer (`CreatureDevScreen`) complete.
- **Next Task**: Hook `SpawnManager.java` to roll and bake procedural Chimeras on Strata 3+ as rare minibosses.

### 3. Augury Bones & Boss Climax Stochastic System
- **Spec**: [Design Document_ Augury Bones & Boss Stochastic System.md](file:///c:/workspace/Tarmin2/docs/DEsign/Design%20Document_%20Augury%20Bones%20&%20Boss%20Stochastic%20System.md)
- **Status**: 3D Bullet physics engine (`StochasticManager`) and bone crafting (`OssuaryManager`, `BoneTrait`) exist.
- **Next Task**: Connect 3D rolling tray to Divine Altars and Minotaur Boss phases.
