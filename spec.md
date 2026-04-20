# Tarmin2 (Minotaur) Product Specification

## 1. Overview
**Project Name:** Tarmin2Revised (Internal Name: Minotaur)
**Engine/Framework:** LibGDX (Java, Desktop via LWJGL3)
**Genre:** First-Person 3D Dungeon Crawler / Survival RPG
**Perspective:** First-Person (Retro Raycasting 3D) & 2D Top-Down maps
**Core Hook:** A modern tribute to the classic Intellivision game *Treasure of Tarmin*, blending procedurally generated mazes, deep procedural tiered itemization, and intense resource survival mechanics. The game utilizes a hybrid retro 3D aesthetic featuring a raycasted environment populated with 2D bilboarded entities.

## 2. Core Gameplay Loop
1. **Exploration:** Navigate procedural tile-based mazes in 3D. Open dynamic doors, uncover dead-ends, and locate the ladder to descend deeper into the dungeon.
2. **Survival Mechanics:** Balance a holistic set of survivability traits: Hit Points (WS), Magic Points (SS), Satiety (Food), Hydration, Body Temperature, and Toxicity.
3. **Combat:** Engage entities via class turn-based combat governed by a Player/Monster turn state machine. Monsters also feature independent combat AI allowing for inter-monster combat.
4. **Looting & Progression:** Defeat enemies (yielding XP, loot, and "gibs"). Specific colored keys unlock their respective colored chests for better tiered gear based on current depth.
5. **Crafting (Cooking):** Harvest monster gibs, kindling, and water. Utilize the Cooking Screen to prepare meals offering specialized status buffs, balancing the risks associated with high toxicity.

## 3. Key Mechanic Specifications

### 3.1 Procedural Maze Architecture
- Dungeons are stitched together dynamically.
- System utilizes massive grids (e.g. 2x2 map configuration built from 12x12 pre-designed room "tiles").
- Tiles are randomized, rotated, and interconnected using specifically designed corridors to avoid unwinnable bounds, maintaining complete topological navigability.
- Preventative rules handle "Off-Map Spawning" guaranteeing all interactables and paths exist within validated walkable tiles.

### 3.2 Immersive Survival & Statistics
- **Player Stats:** 
  - `War Strength` (Maximum Health and Melee output).
  - `Spiritual Strength` (Magic Points / Defence).
  - Dexterity, Strength, and Luck manipulate combat variance.
- **Bodily Status:** 
  - `Satiety & Hydration`: Decays over time, recovered by food/water, penalizes when low.
  - `Body Temperature`: Simulates hypo/hyperthermia (32c to 41c).
  - `Toxicity`: Gained through dubious food or combat; creates a Glass-Cannon paradigm (e.g., +20% Strength and -20% Defense).

### 3.3 Dynamic Combat & Entities
- **AI Tiers:** Enemies scale according to Dungeon Level by color (e.g. Tan = low, Orange = med, Blue = high).
- **Independent Ecosystem:** Monsters execute A-star or greedy paths not only toward the player but toward other monster factions, offering dynamic dungeon ecosystems.
- **Weapon System:** Features standard Melee, Ranged (Arrows with cap limits), and Spiritual Weapons with modifiers.

### 3.4 Monster Cooking System
- Entities drop specific body parts based on their archetype (e.g., `GIB_FLESH`) applying randomized procedural gore texturing.
- The `CookingScreen` allows players to mix 1 Gib + 1 Kindling + 1 Water.
- Meal ingestion applies temporary and permanent stat manipulations and alters player Toxicity.

### 3.5 Game Event System
- Implemented via `GameEventManager`, supporting text, flags, and triggers.
- Environmental occurrences (e.g. "Statue Events") conditionally alter the dungeon's state. Statues pop into rendering gracefully based on distance vectors and event execution to avoid invisible interactions.

## 4. Technical Architecture

### 4.1 Rendering (2.5D Raycasting)
- **FirstPersonRenderer:** Generates the maze geometry in real-time utilizing a DDA raycasting algorithm. Extrapolates a `depthBuffer`.
- **EntityRenderer:** Evaluates all `Renderable` interfaces mapped on the grid. Sorts objects by Z-distance and masks against the `depthBuffer` to allow entities to properly occlude behind structural walls.
- Players can hot-swap rendering contexts between a textured MODERN view and an untextured CLASSIC perspective.

### 4.2 Code Structure
- **Game/Controller:** `Tarmin2.java` handles context configuration. Screens (`GameScreen`, `MainMenuScreen`, `CastleMapScreen`, etc.) isolate views.
- **Entity Component (ECS-lite):** All interactable map data is anchored in `Maze.java` under a dual-mapping paradigm: map masks for solid objects and `Map<GridPoint2, Object>` for entities.
- **Singleton Managers:** Control cross-cutting concerns: `SpawnManager` (populates dungeon on initialization budget), `CombatManager` (manages turn-resolution states), `SoundManager` / `MusicManager` (asset pipelines).

## 5. Development Scope & Roadmap
- Implement comprehensive Trap templates via Event system.
- Build upon existing Toxicity logic for hallucinogenic rendering outcomes.
- Enhance AI behavioral scripting (Fleeing, ambushing).
- Increase graphical fidelity options in the `FirstPersonRenderer` (Dynamic Lighting point-sources).
