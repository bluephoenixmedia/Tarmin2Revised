# Design Document: Procedural Maze Chunk Architecture & Generation

**Status**: Approved (via Grilling Process)  
**Date**: September 2026  
**Related Documents**: 
- `docs/Implementation Plan_ The Expedition Loop & Progression Reboot.md`
- `docs/DEsign/Roguelike Maze Chunk Design.md`
- `CONTEXT.md`

---

## 1. Executive Summary & Architectural Vision

*Tarmin 2* marries the authentic claustrophobic first-person grid crawl of Mattel's *AD&D Treasure of Tarmin* with modern emergent roguelike dungeon generation inspired by *Caves of Qud*, *Gunfire Reborn*, and *NetHack*. 

This document defines the architectural specification for the **Modular Maze Chunk & Procedural Generation System**, adapting the spatial and pacing principles of `docs/DEsign/Roguelike Maze Chunk Design.md` to our discrete tile grid.

### Core Architectural Invariants:
1. **Grid-Step Spatial Integrity**: Discrete tile movement, 90-degree turns, bump melee, and directional projectile ballistics are strictly preserved.
2. **Retro Mode Isolation**: Legacy `GameMode.CLASSIC` preserves the original 16-tile array, 2x2 layout, and retro presentation. The modern procedural chunk engine powers `GameMode.ADVANCED` and the Delve & Return Expedition loop.
3. **Data-Driven Prefabs**: Chunks are authored as modular JSON prefabs in `assets/data/chunks/*.json` with typed socket interfaces, decoupled from code.
4. **Macro-Mission Cyclic Grammars**: Strata are structured as lock-and-key loops (`Start -> Hub -> Champion/Key -> Gate -> Descent`).
5. **Dynamic Telemetry Director (EDPCG)**: Difficulty scales adaptively using real-time injury and death telemetry to prevent early-run frustration while challenging experienced players.

---

## 2. Spatial Metrics & Discrete Tile Translation

Rather than continuous multi-meter geometries, the 3D world is expressed in discrete tile units:

| Spatial Concept | Grid Tile Metric | Combat & Navigation Behavior |
| :--- | :--- | :--- |
| **Claustrophobic Choke** | 1-tile corridor width | Zero lateral evasion; piercing weapons penetrate entire line; bump combat front-to-front. |
| **Tactical Hallway / Arena** | 2–3 tiles wide | Enables column occlusion, lateral dodging, flanking, and two-front monster engagements. |
| **Colosseum / Panopticon** | 8x8 to 10x10 open vault | Radial engagement arena; requires low-cover trenches and central pillars to break sightlines. |
| **Low-Cover Obstacle** | 1 tile (half-height barrier) | **Impassable to movement**; **Permeable to ranged arrows, quarrels, and spells**; grants **+3 AC** cover bonus. |
| **Full Wall (`#`)** | 1 tile (full-height solid) | Blocks movement, sightlines, projectiles, and attenuates spatial audio. |
| **Floor (`.`)** | 1 tile (flat traversable) | Standard movement and combat surface. |

### Low-Cover Mechanical Specification:
- **Movement**: Pathfinding cost = $\infty$ (impassable for both player and monsters; cannot be stepped onto).
- **Ballistics**: Projectiles (arrows, quarrels, thrown daggers, breath weapons, targeted spells) travel freely over low-cover tiles.
- **Armor Class Bonus**: When an entity is attacked with a ranged attack and the line-of-sight ray passes through at least one low-cover obstacle adjacent to the target, the defender receives **+3 AC** against that attack.
- **Visual Presentation**: Rendered in `World3DRenderer` as 2D illustrated billboard scenery props anchored to the tile center (`stone_altar.png`, `wooden_barricade.png`, `rubble_pile.png`).

---

## 3. Curated Archetype Taxonomy (10 Prefab Archetypes)

Each 12x12 sub-chunk adheres to one of ten distinct functional archetypes:

1. **The Multi-Tiered Panopticon (Arena)**:
   - *Layout*: Central open platform surrounded by a perimeter trench with low-cover rubble piles and outer archer alcoves.
   - *Function*: High-intensity combat arena used for Champion and swarm encounters.

2. **The Claustrophobic Choke (Transition)**:
   - *Layout*: 1-tile wide, 10-tile long winding passage with tight 90-degree corners.
   - *Function*: Spikes anxiety; negates lateral dodging; rewards spears, polearms, and linear ray spells.

3. **The Suspended Chasm Walkway (Void Hazard)**:
   - *Layout*: 1-tile wide stone walkways spanning bottomless pit/chasm tiles.
   - *Function*: Tests positioning; knockback weapons (heavy warhammers, concussive blasts) can push monsters into the void.

4. **The Environmental Furnace (Trap Room)**:
   - *Layout*: Compact square room with floor spike grids, poison gas grates, or lava tiles.
   - *Function*: Environmental weaponization equally lethal to monsters and players; rewards crowd-control positioning.

5. **The Blind-Corner Vault (Risk / Reward)**:
   - *Layout*: High-occlusion L-shaped or Z-shaped inner walls blocking sight from the entrance.
   - *Function*: Optional dead-end branch harboring a rare chest/altar, guarded by a heavy ambush enemy.

6. **The "Breather" Sanctuary (Pacing Reset)**:
   - *Layout*: Open, circular room featuring a campfire, restorative fountain, or traveling merchant.
   - *Function*: Safe zone; no monsters may spawn here; provides recovery and inventory preparation.

7. **The Flooded Crossroads (Hub & Wayfinding)**:
   - *Layout*: Multi-socket central chamber with knee-deep water slowing entity movement.
   - *Function*: Central landmark connecting branches; distinct lighting cues mark branch exits.

8. **The Descent Shaft (Verticality)**:
   - *Layout*: Fortified room housing the downward stone stairs or descent rope to the next strata.
   - *Function*: Critical path terminus; guarded by stationed gate sentries.

9. **The Asymmetric Flank (Tactical Infiltration)**:
   - *Layout*: Dual-lane corridor with a primary open hallway and an adjacent pillared flanking lane.
   - *Function*: Allows stealthy players to bypass or isolate patrolling enemy groups.

10. **The Debris Field / Pillar Hall (Occlusion Arena)**:
    - *Layout*: Symmetrical grid of stone pillars and low rubble barriers.
    - *Function*: Dynamic cover arena where entities break line of sight, juke projectile fire, and reset aggro.

---

## 4. Prefab Schema & Modular Socket System

Prefabs are defined in `assets/data/chunks/*.json`.

### Schema Specification:
```json
{
  "id": "choke_passage_01",
  "archetype": "CHOKE",
  "minStrata": 1,
  "maxStrata": 5,
  "pacingWeight": 100,
  "sockets": {
    "NORTH": "CORRIDOR_1",
    "SOUTH": "CORRIDOR_1",
    "EAST": "WALL",
    "WEST": "WALL"
  },
  "layout": [
    "############",
    "#####..#####",
    "#####..#####",
    "#####..#####",
    "#####..#####",
    "....o..o....",
    "....o..o....",
    "#####..#####",
    "#####..#####",
    "#####..#####",
    "#####..#####",
    "############"
  ],
  "tileLegend": {
    "#": "WALL",
    ".": "FLOOR",
    "o": "LOW_COVER_RUBBLE",
    "A": "ALTAR",
    "C": "CHEST",
    "T": "TRAP_SPIKE"
  },
  "hazardSlots": 2,
  "decalSlots": 4
}
```

### Transformation & Matching Rules:
1. **4-Way Rotation**: The generator may rotate any prefab by 0°, 90°, 180°, or 270°. During rotation, socket definitions rotate identically:
   $$\text{Socket}_{\text{new}}(d) = \text{Socket}_{\text{old}}((d + \text{rot}) \pmod 4)$$
2. **Socket Compatibility**: An edge can only connect to an adjacent chunk if their touching sockets match:
   - `CORRIDOR_1` $\leftrightarrow$ `CORRIDOR_1`
   - `CORRIDOR_2` $\leftrightarrow$ `CORRIDOR_2`
   - `WALL` $\leftrightarrow$ `WALL`
   - `OPEN` $\leftrightarrow$ `OPEN`

---

## 5. Macro-Mission Cyclic Grammars & Champion Encounters

### The Strata Graph Grammar:
Each strata map (3x3 chunk grid in Advanced Mode) is generated according to a cyclic lock-and-key grammar:
$$\text{Start} \longrightarrow \text{Hub (Crossroads)} \longrightarrow \begin{cases} \text{Branch A: Champion Lair} \rightarrow \text{Runic Keystone} \\ \text{Branch B: Optional Vault / Sanctuary} \end{cases} \longrightarrow \text{Runic Gate} \longrightarrow \text{Descent Shaft}$$

### Champion Arena Lockdown:
1. **Arena Seal**: When the player steps over the threshold into the Champion's chamber, entrance sockets seal immediately (portcullises slam shut or glowing arcane wards block passage).
2. **Anti-Cheese Invariant**: Hallway kiting is prevented; the player must engage the Champion using the room's tactical cover and pillars.
3. **Key Dispensation**: Upon the Champion's death, the lockdown seals dispel and the **Runic Keystone** drops directly onto the central altar or into player inventory.

---

## 6. Dynamic Telemetry Director (EDPCG)

The Telemetry Director dynamically computes a **Strain Score ($S$)** during strata generation using metrics from `TelemetryManager`:

$$S = \text{CurrentHP\%} - (\text{Injuries} \times 15) - (\text{RecentDeaths} \times 25)$$

### Pacing States & Archetype Weight Modulation:
| Pacing State | Condition | Archetype Adjustments | Gameplay Modulators |
| :--- | :--- | :--- | :--- |
| **High Strain** | $S < 40$ | *Sanctuary* weight +200%<br>*Traps/Gauntlets* -50% | Bandage/ration drop rate boosted; merchant offers discounts. |
| **Neutral** | $40 \le S \le 75$ | Baseline distribution | Standard spawn tables and hazard densities. |
| **Flow / Dominance** | $S > 75$ | *Champion/Gauntlet* +50%<br>*Blind Vault* +30% | Champions gain elite affixes; higher probability of rare magical weapons/armor. |

---

## 7. Tactical AI: Role-Differentiated Cover Steering

When an entity initiates a combat turn:
- **Ranged AI (Skeleton Archers, Goblin Slingers, Spitting Spiders)**:
  1. Checks adjacent 4 orthogonal tiles for any tile marked `LOW_COVER_*`.
  2. If an adjacent low-cover tile exists between the monster and the player, the monster remains in place or steps behind it before firing, claiming the **+3 AC** cover bonus.
- **Melee AI**:
  1. Low-cover tiles have infinite movement cost; A* navigates around them.
  2. Melee units seek the shortest path to bump into the player's tile.

---

## 8. Acoustic Wall Occlusion & Audio Attenuation

Section 2.3 of the chunk design doc requires auditory wayfinding:
- **Calculation**: Bresenham raycast line between sound source $(x_s, y_s)$ and player $(x_p, y_p)$.
- **Attenuation Formula**:
  $$\text{Gain}_{\text{final}} = \text{Gain}_{\text{base}} \times \left( \frac{1}{1 + 0.15 \cdot d} \right) \times (0.70)^{N_{\text{walls}}}$$
  where $d$ is Euclidean distance and $N_{\text{walls}}$ is the count of `#` wall tiles intersected.
- **Perception**: A sound behind 1 wall is heard at 70% volume; behind 2 walls at 49%; behind 3+ walls it is muffled below 34%, alerting the player to nearby threats without revealing exact positions.

---

## 9. Scenery Decals & Visual Wear Decorator Pass

To avoid sterile repetition across identical chunk prefabs:
- Chunk definitions declare optional `decalSlots`.
- During generation, `MazeChunkGenerator` executes a decorator pass adding decals to `Maze.getScenery()`:
  - Combat / Trap rooms: `blood_spatter_1.png`, `scorch_mark.png`.
  - Damp / Cave rooms: `moss_floor.png`, `slime_residue.png`.
  - Abandoned ruins: `cobwebs_corner.png`, `cracked_pavement.png`.

---

## 10. Retro Mode Preservation Guarantee

> [!IMPORTANT]
> **Architectural Non-Negotiable**:  
> Whenever `gameMode == GameMode.CLASSIC`, all modern procedural chunk logic, WFC socket graph solvers, low-cover 3D billboards, and Telemetry Director modulations are completely bypassed. Classic mode strictly runs the original 16-tile array (`tile1` through `tile16`), generating authentic 2x2 layouts with retro wireframe styling. This invariant must be preserved across all future builds.
