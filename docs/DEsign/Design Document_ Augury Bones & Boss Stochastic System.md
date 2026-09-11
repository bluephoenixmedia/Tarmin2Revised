# Design Document: Augury Bones & Boss Climax Stochastic System

## 1. Overview & Vision
This document defines the refined role of the 3D physics dice engine in Tarmin2, resolving the pacing friction between fast corridor dungeon crawling and physical dice simulation.

### Core Pillars
1. **Uninterrupted Corridor Flow**: Standard melee bump-to-attack and ranged ballistics remain 100% instantaneous, responsive, and calculation-under-the-hood with floating damage and combat log notifications.
2. **Augury Bones (Shelter Hub Crafting)**: Dice are contextualized as **Augury Bones**—ritual talismans carved from monster remains (`GIB_BONE`, `GIB_SKULL`, monster sinew) harvested during delves. They provide passive traits and active traits.
3. **Climax-Only 3D Physics Simulation**: The full 3D Bullet physics dice tray (`StochasticManager.java`) is reserved for high-stakes, cinematic moments:
   - **Boss Climax Encounters**: Minotaur phase transitions and fatal contest rolls.
   - **Divine & Eldritch Altars**: Throwing bones before ancient idols to receive blessings or ward off curses.
   - **Camp Divination / Rest**: Rolling auguries at the Shelter Hub to preview upcoming strata modifiers or unlock expedition boons.

---

## 2. System Architecture & Gameplay Loop

### A. The Delve & Return Bone Crafting Loop
```
Exploration / Combat
    │  Defeat monsters, dismember via GoreManager
    ▼
Harvesting
    │  Collect GIB_BONE, GIB_SKULL, monster viscera
    ▼
Return to Shelter Hub
    │  Deposit in Shelter Chest / Bring to Carving Bench
    ▼
Carve Augury Bones
    │  Combine bone materials + traits → New Die in Player's Augury Set
    ▼
Next Expedition / Boss Climax
       Engage Minotaur or Altars with custom Bone Traits
```

### B. Bone Trait & Face Mechanics
Building on [BoneTrait.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/dice/BoneTrait.java) and [DiceFactory.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/dice/DiceFactory.java):
- **Rusty Bone Die**: Baseline scavenged bone die (`SWORD`, `GLANCING`, `SHIELD`, `BLANK`).
- **Warrior's Carved Bone**: Aggressive high-impact traits (`CLEAVE`, `RECKLESS`).
- **Guardian's Rib**: Defensive parry traits (`PARRY`, `TOWER_SHIELD`).
- **Eldritch Horn**: High variance magical traits (`INFERNO`, `ASH`, `SOUL_DRAIN`).

### C. Triggering the 3D Simulation Tray
`StochasticManager.java` is invoked selectively rather than on generic combat turns:
1. **Altar Interaction**: Facing a divine or profane altar, player chooses "Cast Augury Bones". The 3D tray renders as an overlay. Results apply persistent floor or expedition status effects (e.g. +WIS, torch brightness, monster fear).
2. **Minotaur / Boss Phase Shifts**: When the Minotaur reaches critical HP thresholds (e.g. 50% Blood Frenzy), a Boss Contest begins. The player rolls their Augury Hand to counter the Minotaur's onslaught.
3. **Key 7 Toggle**: Preserved for dev testing and optional manual divination.

---

## 3. Engineering & Codebase Integration

### Existing Classes Reused
- [StochasticManager.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/managers/StochasticManager.java): 3D Bullet physics simulation world (`btDiscreteDynamicsWorld`), rigid body dice rolling, settling detection, and face reading.
- [DiceFactory.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/dice/DiceFactory.java): Themed dice definition.
- [BoneTrait.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/dice/BoneTrait.java): Trait resolution.
- [GoreManager.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/gore/GoreManager.java): Source of monster bone drops (`GIB_BONE`, `GIB_SKULL`).

### Planned Enhancements
1. **Shelter Hub Bone Carving UI**: Add a tab/interaction card in the Shelter Hub allowing players to inspect collected bone materials and assemble customized Augury Bones.
2. **Altar Interaction Card**: When discovering an Altar tile in the dungeon, offer "Cast Auguries" which launches `StochasticManager` for instant ritual resolution.
3. **Boss Climax Hook**: Tie `CombatManager` boss states directly to an Augury duel phase.
