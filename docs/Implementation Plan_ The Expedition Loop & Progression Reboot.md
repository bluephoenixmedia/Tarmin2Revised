# Tarmin2 Architecture Blueprint & Implementation Plan: The Expedition Loop

## Vision & Core Identity

Tarmin2 is a dark, atmospheric first-person procedural dungeon crawler blending:
1. **Treasure of Tarmin (Intellivision)**: Grid-based claustrophobic exploration, the War vs Spiritual duality (stats, weapons, and monsters), colored tiers of gear, and the menacing Minotaur awaiting at the climax.
2. **Might and Magic II & III**: Step-by-step first-person overland biomes and dungeons, landmark navigation (Castle Tarmin looming in the distance), town/camp services, and deep character development.
3. **Advanced Procedural Roguelikes (Caves of Qud, NetHack, Dwarf Fortress)**: Systemic emergent interactions, open-ended vertical strata descent on any chunk, sensory monster AI, organic harvesting (flesh, bones, debris), and unidentified item experimentation (without punishing curses).
4. **The Narrative Win Condition**: Prepare across expeditions, forge gear, unlock divine thresholds, and launch the **Final Run** through the sealed gates of Castle Tarmin to vanquish the Minotaur and rescue your daughter.

---

## Decisions Resolved During Architecture Review

- **Core Macro-Loop**: **Delve & Return (Expedition Loop)** based out of a safe starting Shelter (4x4 sanctuary with bed, hearth, chest, and workbench).
- **World Topography**: Explorable surface wilderness with biomes (Forest, Plains, Swamps, Ruins) and Castle Tarmin visible on the horizon. **Every chunk permits vertical strata descent (Caves of Qud / Dwarf Fortress style)** into dangerous subterranean depths harboring rare loot and optional bosses.
- **Combat Flow**: **Fast-Paced Grid Tactics**. Bump-to-attack, archery/thrown ballistics, and tactical spells directly on the grid. Dice rolls and trait modifiers run under the hood with instant floating damage feedback and combat log output (no modal physics delays interrupting movement).
- **Character Progression**: **Camp Progression & Skill Mastery (M&M + Qud)**. Spend accumulated XP and Divinity at camp to raise Attributes and unlock skills; read Tarmin tomes to raise War/Spiritual power thresholds.
- **Death & Stakes**: **Corpse Run + Doom Clock**. Dying revives you at the Shelter without carried pack items; your gear, bones, and lost divinities wait at the death tile. Dying advances the Doom Clock (Tarmin's Hunger), corrupting the world and shortening the time before the sacrificial ritual.
- **Loot & Identification**: Full paper doll equipment, NetHack-style unidentified descriptors ("fizzy blue potion") testable in the field or safely analyzed at camp, weapon durability and ammo, **no beatitude/curses**.
- **Dungeon Interactivity**: Shrines to Tarmin gods for permanent blessings, mysterious fountains, harvestable debris (bones/wood/stone), secret walls, destructible doors, and elemental reactions (fire/water/lightning).
- **Endgame Gauntlet**: Breaching Castle Tarmin requires collecting ancient seals from deep strata bosses, launching an unretreatable, multi-level final siege.
- **Visual Presentation**: Crisp 2.5D retro pixel sprites with color palette variants (tan, orange, blue, white, pink, purple) and modular dismemberment overlays.

---

## Staged Implementation Roadmap

### Phase 1: The Core Expedition Loop & Shelter Hub *(Immediate Execution Target)*
Establish the physical foundation of the Delve-Return loop:
1. **The Starting Shelter Hub (Chunk 0,0, Level 0)**:
   - Configure a dedicated safe 4x4 starting chunk.
   - Implement interactive furniture:
     - **Bed**: Rest, advance time, heal, and save progress.
     - **Fire Pot / Hearth**: Cook harvested meat and prepare field rations.
     - **Stash Chest**: Persistent multi-slot storage container that survives death.
     - **Workbench**: Alchemical identification and bone carving.
     - **Threshold Gate**: Leads out to the wilderness.
2. **Streamlined Fast-Paced Grid Combat**:
   - Make bump-to-attack instantaneous without modal menu or dice physics interruptions.
   - Wire dice and damage equations directly into real-time calculations.
   - Enforce War vs Spiritual damage resistances and vulnerabilities.
   - Fix the zero-player-damage and enemy balance bugs.
3. **Corpse Run & Death Loop Wiring**:
   - Player death spawns a death marker containing backpack items, bones, and lost divinities on that tile.
   - Player respawns in the Shelter bed.
   - Advance the Doom Clock on death.

### Phase 2: Open-World Strata & Biome Navigation
- Implement overland chunk transitions with visible Castle Tarmin landmark in the skybox.
- Enable vertical descent (ladders/sinkholes) on surface chunks into procedural strata levels.
- Deep strata difficulty and loot scaling (Dwarf Fortress cavern depth model).

### Phase 3: Camp Progression & Skill Tree
- Spend XP and Divinity at camp to train Attributes (STR, DEX, CON, INT, WIS, AGI, CHA).
- Implement perk/skill trees (Butchery, Archery, Arcana, Field Dressing, Foraging).
- Tarmin Tomes integration to expand War/Spiritual maximum power.

### Phase 4: Systemic Labyrinth Features & Ecosystem
- Shrines, fountains, and harvestable debris.
- Sensory monster AI (hearing player footsteps, scent, sleeping states).
- Destructible doors and secret wall searching.

### Phase 5: Castle Tarmin & The Final Run Gauntlet
- Boss encounters in deep strata guarding the ancient Seals of Tarmin.
- The Point-of-No-Return castle gauntlet, the Minotaur showdown, and the daughter rescue sequence.

---

## Proposed Changes (Phase 1 Focus)

### Core Systems & World Management

#### [MODIFY] [WorldManager.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/managers/WorldManager.java)
- Explicitly define Chunk (0,0) at Level 0 as the persistent Starting Shelter.
- Prevent hostile monster spawns within the Shelter.
- Spawn the initial interactable stations: Bed, Fire Pot, Stash Chest, Workbench, and Exit Doorway.
- Support chunk persistence across returns to base.

#### [MODIFY] [GameScreen.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/screens/GameScreen.java)
- Streamline input handling:
  - Moving forward into a monster immediately triggers a fast melee attack without opening a mandatory menu or waiting for physics settles.
  - Retain Spacebar for tactical menu actions (Cast, Use Item, Wait) if needed.
- Connect Shelter furniture interaction (Bed -> Rest/Save; Chest -> Stash UI; Hearth -> Cook; Bench -> Alchemy/Ossuary).
- Implement corpse drop and Shelter respawn handling in `PLAYER_DIED` event handler.

#### [MODIFY] [CombatManager.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/managers/CombatManager.java)
- Make `playerMeleeStrike` and `playerAttackInstant` the primary, rapid combat resolution path.
- Resolve weapon damage, modifiers, and dice pools purely mathematical-in-code, rendering floating combat text and audio effects immediately.
- Enforce Tarmin damage rules:
  - `BAD` monsters: immune to War damage, vulnerable to Spiritual.
  - `NASTY` monsters: immune to Spiritual damage, vulnerable to War.
  - `HORRIBLE` monsters: vulnerable to both.
- Ensure monsters accurately deal damage to the player based on armor mitigation.

#### [MODIFY] [DoomManager.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/managers/DoomManager.java)
- Hook player death and extended expedition time into the Doom Clock ("Tarmin's Hunger").
- Expose current Doom level to the HUD and display escalation warnings.

#### [NEW] [ShelterChest.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/item/ShelterChest.java)
- Data model for persistent home storage (storing items, gold, materials) that survives expeditions.

---

## Verification Plan

### Automated Tests
- Run `./gradlew.bat test` to verify all existing unit tests pass.
- Add unit tests for `CombatManager` War vs Spiritual damage resolution and monster vulnerability checks.
- Add unit test for `ShelterChest` serialization and persistence across runs.

### Manual Verification
- Launch the game via `./gradlew.bat lwjgl3:run`.
- Verify the player spawns inside the 4x4 Shelter with Bed, Fire Pot, Chest, and Workbench.
- Interact with Chest to store and retrieve items.
- Step outside the doorway into the wilderness/dungeon.
- Engage a monster in melee: verify bump-to-attack is instant, floating damage numbers appear, and combat log updates smoothly.
- Test death: allow a monster to defeat the player, verify respawn in the Shelter bed, and confirm a corpse/divinity marker is left at the death tile.
