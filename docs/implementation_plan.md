# Implementation Plan: Progression Reboot, NetHack Food/Intrinsics, Combat & UX Overhaul

This plan addresses all 14 issues from [test_notes_9_12_26.md](file:///c:/Users/denni/Desktop/Tarmin2/docs/test_notes_9_12_26.md) and forensic findings from `logs/game_log.txt`, incorporating the decisions settled during the `/grill-me` session.

---

## User Review Required

> [!IMPORTANT]
> - **Branching Strategy**: Per user guidelines, because this is a major architectural milestone, we will create a dedicated branch `feature/nethack-progression-reboot` branching from the clean, synchronized `feature/weapon-rendering-batch2`.
> - **Combat Feel**: Standard monster ACs will be bounded to 11–15, attack rolls missing by $\le 3$ will trigger *Glancing Blows* (35% damage + soft cyan float), and player accuracy scales with level and stats rather than remaining at +1.
> - **NetHack Food Paradigm**: Eating food grants **0 direct HP**. Food only alters nutrition along the NetHack Satiation Spectrum (`Starving` $\leftrightarrow$ `Hungry` $\leftrightarrow$ `Normal` $\leftrightarrow$ `Satiated` $\leftrightarrow$ `Choking`). Eating monster flesh or poisonous organs can inflict status debuffs but offers a permanent chance to unlock intrinsic resistances (`Poison Resist`, `Fire Resist`, `Cold Resist`, `Shock Resist`).

---

## Proposed Changes

### Component 1: Combat Resolution Math & Pacing

#### [MODIFY] [PlayerStats.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/player/PlayerStats.java)
- Replace legacy `level / 4 + getDexModifier()` with standard RPG bounded accuracy:
  ```java
  public int getToHitBonus() {
      int baseBonus = 2 + (this.level / 2);
      return baseBonus + getDexModifier();
  }
  ```
- Add constitution-scaled natural HP regeneration interval:
  ```java
  public int getRegenIntervalTurns() {
      int conMod = getConModifier();
      return Math.max(8, 20 - conMod * 2);
  }
  ```

#### [MODIFY] [CombatManager.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/managers/CombatManager.java)
- In `processPlayerInstantAttack()`, implement the **Glancing Blow** logic:
  ```java
  int delta = attackRoll - targetAC;
  boolean isHit = (delta >= 0) || isCrit;
  boolean isGlancing = !isHit && (delta >= -3);
  ```
- When `isGlancing`:
  - Calculate glancing damage: `int glanceDmg = Math.max(1, (int)(totalDamage * 0.35f));`
  - Apply damage to monster: `monster.takeDamage(glanceDmg, dmgType, false);`
  - Spawn floating combat text: `"Glancing -" + glanceDmg` in `HudSkin.COL_MAGIC_CYAN`.
  - Play glancing audio deflection sound (`soundManager.playWeaponImpact(false)`).
  - Update telemetry counters: `telemetryManager.recordAttack(HitType.GLANCING, glanceDmg);`

#### [MODIFY] [monsters.json](file:///c:/Users/denni/Desktop/Tarmin2/assets/data/monsters.json) & [MonsterTemplate.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/monster/MonsterTemplate.java)
- Re-tune high-level monster AC values that previously spiked to 18–20 down to the bounded range (12–15 for standard monsters; 16–17 for elite guardians and bosses).

---

### Component 2: NetHack-Style Food, Satiation, Poison & Intrinsics

#### [MODIFY] [PlayerStats.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/player/PlayerStats.java)
- Implement NetHack Satiation Spectrum:
  ```java
  public enum SatiationState {
      STARVING, // <= 0 nutrition: Cannot naturally heal, stamina recovery halved
      HUNGRY,   // 1 - 25 nutrition: Warning state
      NORMAL,   // 26 - 80 nutrition: Natural HP/MP regen active
      SATIATED, // 81 - 110 nutrition: Cannot eat normal meals; movement speed slightly reduced
      CHOKING   // > 110 nutrition: Takes suffocation damage each turn
  }
  ```
- Track continuous nutrition score (0–120), consuming 1 point per 12 turns.

#### [MODIFY] [Player.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/player/Player.java)
- In `feastOnMeal()` and `useItem()`:
  - **Remove instant HP healing**: `stats.heal(healAmt)` $\rightarrow$ removed.
  - Satiation check: If `SatiationState == SATIATED` or `CHOKING`, prevent eating or trigger choking penalty.
  - Apply **NetHack Intrinsics & Poisonous Flesh**:
    - When eating raw monster flesh or poisonous organs (e.g. `GIANT_SCORPION_FLESH`, `SNAKE_FLESH`, `GHOUL_FLESH`):
      - Inflict nausea / poison status effect (`StatusEffectType.POISON`, `StatusEffectType.NAUSEA`).
      - Roll intrinsic chance (e.g. 25% chance to permanently acquire `StatusEffectType.RESIST_POISON`).
      - When eating elemental beast flesh (e.g. Fire Beetle / Fire Giant): 20% chance to grant `RESIST_FIRE`.
  - Add throttled state logging: Alert only on state transitions (`"You are starting to feel hungry."`, `"You are satiated."`), completely suppressing per-step log spam.

---

### Component 3: Death Screen UX & Idempotency Enforcement

#### [MODIFY] [GameScreen.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/screens/GameScreen.java)
- Add run-level idempotency lock:
  ```java
  private String activeExpeditionRunId;
  private boolean isDeathTransitionTriggered = false;
  ```
- In `onPlayerDied()`:
  - Check `if (isDeathTransitionTriggered) return;`
  - Immediately set `isDeathTransitionTriggered = true;`
  - Clear the `GameEventManager` pending event queue so stray monster attacks or status ticks do not queue multiple death events.
  - Pass `activeExpeditionRunId` to `DoomManager.recordDeath(activeExpeditionRunId)`.

#### [MODIFY] [DoomManager.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/managers/DoomManager.java)
- Ensure `recordDeath(String runId)` records at most one demise per unique run ID.

#### [MODIFY] [PlayerDeathScreen.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/screens/PlayerDeathScreen.java)
- Redesign layout into a 3-Card horizontal widescreen layout:
  1. **Left Card: Tarmin's Doom** (Demise count 1/50, Doom clock %, Bridge Integrity, Apocalyptic Stakes).
  2. **Center Card: Run Epitaph & Milestones** (Exact cause of death: e.g., *"Fell to an Umber Hulk at Strata Depth 3 on turn 412 while parched"*; Monsters slain; Max depth reached; Milestone achievements unlocked this run).
  3. **Right Card: Casualties & Banked Wealth** (Unequipped items lost, Travel kits preserved, Divinities collected and banked for the Shelter Altar).

---

### Component 4: Shelter Altar & Meta-Progression System

#### [NEW] [ShelterAltar.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/progression/ShelterAltar.java)
- Model persistent Altar upgrades loaded from `save_slot_X/altar_progression.json`:
  - `provisionsTier` (Extra bread, waterskin, bandages at run start).
  - `repertoireTier` (Unseals advanced weapons: Composite Bows, Heavy Crossbows, Warhammers, Morningstars in early strata).
  - `monumentTier` (Increases statue event frequency from 15% up to 35%).

#### [NEW] [ShelterAltarScreen.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/screens/ShelterAltarScreen.java)
- Scene2D interface allowing the player to spend banked Divinities to purchase tiers for Trees 1–3.

#### [MODIFY] [GameScreen.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/screens/GameScreen.java)
- Spawn an interactive ancient stone Altar in Chunk (0, 0) opposite the Stash Chest. Clicking it opens `ShelterAltarScreen`.

---

### Component 5: Inventory UX, Spell Slots & Capacity

#### [MODIFY] [InventoryScreen.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/screens/InventoryScreen.java)
- Fix drag-and-drop between quickslots and inventory grid slots: Ensure item swap updates both internal inventory indices and visual slots.
- Add **Right-Click Drop**: Right-clicking an item in backpack or quickslots immediately drops it to the current tile floor with toast `"Dropped [Item Name]"`.
- Add **Shift+Click / Double-Click**: Quick-use or quick-equip item.
- Fix "Inventory Full" false report: Evaluate capacity strictly against empty discrete grid slots (`inventory.getCarriedCount() < 24`), allowing stackables to merge without false rejection.

#### [MODIFY] [Hud.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/rendering/Hud.java)
- Link quickslot hotbar rendering to `player.getUnlockedSpellSlots()`:
  - When tomes unlock slots 2, 3, 4, 5, dynamically expand the hotbar and allow dragging/equipping spells into newly unlocked slots.

---

### Component 6: Item Authenticity & Teleportation Reachability

#### [MODIFY] [ItemTemplate.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/item/ItemTemplate.java) & [items.json](file:///c:/Users/denni/Desktop/Tarmin2/assets/data/items.json)
- Switch butcher monster flesh items (`ItemType.FOOD` variants representing meat/flesh) from generic bread/meat texture to appropriate GIB gore textures (`item_flesh.png` / GIB sprites).

#### [MODIFY] [DiscoveryManager.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/discovery/DiscoveryManager.java) & [Player.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/player/Player.java)
- Ensure scroll names are completely bound to their single source of truth (`Item.getSpellId()`). Unidentified scrolls display randomized cryptic runes (`"Scroll labeled XEL'NAGA"`) until cast or identified.

#### [MODIFY] [Player.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/gamedata/player/Player.java) (Teleportation)
- In teleportation logic: Run a breadth-first search (BFS) flood-fill from candidate landing coordinates. Reject coordinates that cannot path to the level's open corridors or stairwells, preventing containment in closed wall pockets.

---

### Component 7: Telemetry System

#### [NEW] [TelemetryManager.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/telemetry/TelemetryManager.java)
- Collect run metrics in memory:
  - `runId`, `turnsLived`, `strataReached`, `causeOfDeath`, `killerMonster`
  - `attacksMade`, `attacksHit`, `attacksGlanced`, `attacksMissed` (calculating true accuracy %)
  - `damageDealt`, `damageTaken`, `damageMitigatedByArmor`
  - `foodConsumed`, `waterConsumed`, `potionsQuaffed`, `scrollsRead`
  - `monstersKilledByType`, `divinitiesEarned`
- On run completion (death or shelter return), serialize to `logs/telemetry/run_<timestamp>.json` and append a summary line to `logs/game_balance_session.log`.

---

### Component 8: Visual Polish, Shaders & Animations

#### [MODIFY] [World3DRenderer.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/rendering/World3DRenderer.java)
- In subterranean forest chunks (`FOREST_THEME` with subterranean flag), override clear/sky fog with deep cavern moss green / near-black twilight (`#0a180f`) with a 6–8 tile falloff, replacing the blinding white sky fog.

#### [MODIFY] [FirstPersonWeaponOverlay.java](file:///c:/Users/denni/Desktop/Tarmin2/core/src/main/java/com/bpm/minotaur/rendering/FirstPersonWeaponOverlay.java)
- Implement **Buff Aura Glow**: When the player has active strength, fire, speed, or holy buffs, apply a pulsating edge tint shader to the 3D first-person weapon model matching the active buff element.
- Implement **Tome Weapon Attack**: When a book/tome is held and an attack is triggered, display a page-flutter particle animation and launch an Arcane Spark (0 MP, 1d4 + INT Spiritual damage) instead of a physical weapon swing.

---

## Verification Plan

### Automated Tests
- Run Gradle test suite:
  ```powershell
  .\gradlew.bat :core:test
  ```
- Add unit tests:
  - `CombatAccuracyTest.java`: Verify attack roll formula, bounded AC math, and glancing blow damage calculation at $\le 3$ delta.
  - `NetHackSatiationTest.java`: Verify food grants 0 direct HP, verifies satiation state transitions, and tests intrinsic resistance grant chance.
  - `DeathIdempotencyTest.java`: Simulate lethal damage in an event queue and verify `DoomManager` and `PlayerDeathScreen` record exactly 1 death per run UUID.
  - `TelemetryExportTest.java`: Verify telemetry tracker exports clean valid JSON with hit/glance/miss stats upon run end.

### Manual Verification
1. Launch game via `.\run.bat`.
2. Engage Level 3+ monsters (Hobgoblins, Alligators): Observe floating "Glancing" hits and confirm combat miss rate is balanced (~25% misses instead of 80%).
3. Consume food: Verify HP does not instantly jump, nutrition increases, and eating monster flesh applies appropriate intrinsic/poison checks.
4. Allow character to die: Verify exactly 1 demise is registered on Tarmin's Doom clock, and inspect the new 3-Card death screen showing run epitaph, kills, and banked Divinities.
5. In Starting Shelter: Click the new Altar and test spending Divinities.
6. Verify subterranean cavern fog renders atmospheric dark emerald rather than bright white.
