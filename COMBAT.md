# Combat System Reference

> Part of the Tarmin2 agent context system. See `AGENT.md` for the full file map.
> This is the primary reference for the active `feature/card-combat` branch.

---

## Overview

Combat in Tarmin2 is **turn-based**, **dice-driven**, and managed entirely by `CombatManager`. No damage or turn resolution may happen outside of it.

Combat is initiated when the player walks into a monster's grid tile (or vice versa). The combat loop runs within `GameScreen.render()` via `combatManager.update(delta)`.

---

## CombatState State Machine

```
INACTIVE
   │
   ▼ (player/monster collision)
PLAYER_MENU          ← Waiting for player to choose an action (attack, spell, flee, item)
   │
   ▼ (action chosen)
PLAYER_SELECT_DICE   ← NEW: Player selects which hand/die to use
   │
   ▼ (die selected)
PHYSICS_RESOLUTION   ← Dice rolled, damage/effects computed
   │
   ▼ (results ready)
PHYSICS_DELAY        ← Brief pause so player reads the result
   │
   ├─► MONSTER_TURN  ← Monster executes its AI-driven action
   │       │
   │       ▼
   │   PHYSICS_RESOLUTION (monster roll)
   │       │
   │       ▼
   │   PHYSICS_DELAY
   │       │
   │       ├─► PLAYER_MENU  (loop continues if both alive)
   │       ├─► VICTORY      (monster WS ≤ 0)
   │       └─► DEFEAT       (player WS ≤ 0)
   │
   ├─► VICTORY        ← Monster dead; trigger loot, gore, XP
   └─► DEFEAT         ← Player dead; trigger GameOverScreen
```

**State access:**
```java
CombatState state = combatManager.getCurrentState();
combatManager.setState(CombatState.PLAYER_MENU);
```

---

## Dice System (Card Combat)

### Data Model

```
DieDefinition
  └── List<DieFace>
        └── DieFaceType  (ATTACK, DEFEND, SPECIAL, BLANK, ...)
            int value

Die
  └── DieDefinition definition
  └── List<BoneTrait> traits   ← modifiers applied to this die

DieResult
  └── DieFaceType rolledType
  └── int rolledValue
  └── Die sourcedie
```

### DieFaceType Values

| Type | Effect |
|---|---|
| `ATTACK` | Deal physical damage equal to value |
| `DEFEND` | Block incoming damage equal to value |
| `SPECIAL` | Trigger a special effect (type-specific) |
| `BLANK` | No effect — wasted roll |

### Rolling

```java
// Single die
DieResult result = DiceRoller.roll(die);

// Pool of dice
List<DieResult> results = DiceRoller.rollAll(diceList);
```

`DiceFactory` constructs `Die` instances from `DieDefinition`. `BoneTrait` modifiers can: reroll blanks, add face values, convert face types, or grant special effects.

### Combat Resolution Flow

1. Player selects action in `PLAYER_MENU`
2. `PLAYER_SELECT_DICE` — player chooses right or left hand die
3. `PHYSICS_RESOLUTION` — `DiceRoller.roll(selectedDie)` called; result applied:
   - `ATTACK` face → `monster.warStrength -= (value - monster.effectiveAC)`
   - `DEFEND` face → stored for damage mitigation on monster turn
   - `SPECIAL` face → context-specific effect
4. `CombatDiceOverlay` displays the `DieResult` visually
5. `PHYSICS_DELAY` timer expires → advance state
6. `MONSTER_TURN` — monster rolls its `AttackDefinition` dice pool
7. Apply monster damage: `player.stats.warStrength -= (monsterDmg - player.effectiveAC)`
8. Check win/loss conditions → route to `VICTORY` or `DEFEAT` or loop back to `PLAYER_MENU`

---

## Key Classes

### `CombatManager`
- `CombatState getCurrentState()`
- `void startCombat(Monster monster)` — transitions from `INACTIVE` → `PLAYER_MENU`
- `void update(float delta)` — called every frame from `GameScreen`
- `HitResult resolveProjectile(GridPoint2 origin, Direction dir, int range)` — raycasts for ranged attacks/spells
- `void setHud(Hud hud)` — injected after HUD construction

### `HitResult`
```java
public enum HitType { NOTHING, WALL, MONSTER, PLAYER, OUT_OF_BOUNDS }
public final GridPoint2 collisionPoint;
public final HitType type;
public final Monster hitMonster;   // null unless type == MONSTER
```

### `TurnManager`
Tracks which entity acts next. Handles turn order based on speed stats (`Player.getEffectiveSpeed()`, `Monster.moveSpeed`). Speed is modified by `StatusEffectType.SLOWED`, `HASTE`, `TEMP_SPEED`, `SUPER_SPEED`.

### `CombatDiceOverlay` (NEW)
Renders the dice roll result UI during `PHYSICS_RESOLUTION` and `PHYSICS_DELAY` states. Injected into `GameScreen`. Receives `DieResult` to display. Does not perform any game logic — display only.

### `FirstPersonWeaponOverlay` (NEW)
Renders the weapon animation in the foreground during `PLAYER_TURN` / `PHYSICS_RESOLUTION`. Plays attack animation on swing, flinch animation on player hit. Reads current equipped weapon from `PlayerEquipment.rightHand`.

---

## Ranged Combat

Ranged attacks (bows, wands, thrown items) call:
```java
HitResult hit = combatManager.resolveProjectile(player.getGridPos(), player.getFacing(), range);
```

Result routes to:
- `WALL` → projectile stops, impact effect
- `MONSTER` → `hit.hitMonster` receives damage
- `PLAYER` → (monster-fired projectile) player takes damage
- `NOTHING` / `OUT_OF_BOUNDS` → miss

---

## Spell Combat

Spells are cast during `PLAYER_MENU` via action selection. The selected `SpellType` resolves its `SpellEffect.apply(player, target, maze)`. SS is deducted before resolution. If SS is insufficient, cast fails — no state transition.

Projectile spells (`MagicArrowEffect`) use the same `resolveProjectile` pipeline as ranged weapons.

---

## Combat-Adjacent Systems

| System | Interaction |
|---|---|
| `GoreManager` | Called on `VICTORY` — spawns gibs, blood at monster position |
| `StatusManager` | Combat can apply status effects (POISONED, SLOW, etc.) via `EffectApplicationData` |
| `AnimationManager` | Receives animation triggers on hit, kill, and player damage |
| `SoundManager` | Plays SFX on attack, hit, death, spell cast |
| `GameEventManager` | Posts combat text events (miss, critical, status applied) |
| `BalanceLogger` | Logs every combat resolution for tuning data |

---

## Planned Combat Features (from `spec.md`)

- **Trap templates** — grid-trigger combat damage/effect events
- **Fleeing AI** — monsters retreat at low WS
- **Ambush AI** — monsters wait at chokepoints
- **Hallucinogenic rendering** — high-toxicity visual distortion during combat
