# Stat System Overhaul — Implementation Tracker

Branch: `feature/card-combat`

---

## Context

This document tracks the full stat system overhaul planned during the session of **2026-05-02**. The goal is a six-attribute primary stat system (STR, DEX, CON, INT, WIS, AGI) where every stat is modifiable via equipment, items, and intrinsics, and the system supports meaningful build differentiation (Berserker, Arcane Scholar, Shadow, Paladin, Toxic Communion).

---

## Phase 1 — Core Stat Foundation ✅ COMPLETE

### Files changed
- `core/.../gamedata/player/PlayerStats.java`
- `core/.../gamedata/player/Player.java`
- `core/.../gamedata/ModifierType.java`
- `core/.../gamedata/item/RingEffectType.java`
- `core/.../managers/CombatManager.java`
- `core/.../screens/CombatDiceOverlay.java`

### What was implemented

**Bug fixes**
- [x] Fixed STR double-dip in dice combat (`resolveDiceHand`): replaced `getAttackModifier() + unhalved(STR-10)` with `player.getDamageBonus()`, unifying dice and instant-combat formulas
- [x] Fixed DEX dead-code: `getToHitBonus()` now uses `level/4 + getDexModifier()` instead of pure `level/2`
- [x] Fixed crit DEX double-count in `getCritChance()`: raw stat was added in `getBaseCritChance()` AND effective stat added again in Player; reduced `getBaseCritChance()` to the 5% constant only
- [x] Fixed toxicity DoT and double-damage checks hardcoded at `>= 76`; now use `>= 76 + player.getToxicityThresholdShift()`
- [x] Fixed `CombatDiceOverlay` stamina gate: `player.getStats().getStamina()` → `player.getEffectiveStamina()` (also the display label)

**New primary attributes (PlayerStats.java)**
- [x] `constitution` (base 10) — getters/setters/modifyX
- [x] `intelligence` (base 10) — getters/setters/modifyX
- [x] `wisdom` (base 10) — getters/setters/modifyX
- [x] `agility` (base 10) — getters/setters/modifyX
- [x] Six modifier helpers: `getStrModifier()`, `getDexModifier()`, `getConModifier()`, `getIntModifier()`, `getWisModifier()`, `getAgiModifier()` — all `(stat-10)/2`

**Level-up scaling (PlayerStats.performLevelUp)**
- [x] HP per level: `max(1, 2 + CON_modifier + rand(3))` — CON-driven, replaces flat random
- [x] MP per level: `max(1, 2 + INT_modifier + rand(3))` — INT-driven, replaces flat random

**Toxicity threshold system (PlayerStats)**
- [x] `getEffectiveStrength(int thresholdShift)` and `getDefenseMultiplier(int thresholdShift)` overloads — tier boundaries (26/75/76) shift upward by the given amount
- [x] `Player.getToxicityThresholdShift()` — sums `BONUS_TOXICITY_THRESHOLD` from equipment + Ring of Fortitude (+15)
- [x] `Player.getEffectiveStrength()` passes the shift through

**New ModifierType entries (14 total)**
- [x] `BONUS_STRENGTH`, `BONUS_DEXTERITY`, `BONUS_CONSTITUTION`, `BONUS_INTELLIGENCE`, `BONUS_WISDOM`, `BONUS_AGILITY`
- [x] `BONUS_ABSORB` — flat damage mitigation on connected hits (separate from AC)
- [x] `BONUS_SPELL_POWER` — flat addition to all offensive spells
- [x] `BONUS_STAMINA` — extra dice selectable per combat round
- [x] `BONUS_LUCK` — stacks, clamped to ±13 at Player.getLuck()
- [x] `BONUS_CRIT_CHANCE` — integer percentage points (5 = +5%)
- [x] `BONUS_CRIT_MULTIPLIER` — tenths of a multiplier (5 = +0.5×)
- [x] `BONUS_DODGE` — integer percentage points
- [x] `BONUS_TOXICITY_THRESHOLD` — shifts tier boundaries upward

**Player.java — effective stat wrappers**
- [x] `getEffectiveStrength()` — toxicity-adjusted + `BONUS_STRENGTH` from equipment
- [x] `getEffectiveDexterity()` — base + `BONUS_DEXTERITY` + Ring of Dexterity (+5)
- [x] `getEffectiveConstitution()` — base + `BONUS_CONSTITUTION` + Ring of Constitution (+5)
- [x] `getEffectiveIntelligence()` — base + `BONUS_INTELLIGENCE` + Ring of Intelligence (+5)
- [x] `getEffectiveWisdom()` — base + `BONUS_WISDOM` + Ring of Wisdom (+5)
- [x] `getEffectiveAgility()` — base + `BONUS_AGILITY` + Ring of Agility (+5)
- [x] `getDamageBonus()` — flows through `getEffectiveStrength()` so `BONUS_STRENGTH` items work
- [x] `getSpellPower()` — INT modifier + `BONUS_SPELL_POWER` + Ring of Spell Mastery (+6)
- [x] `getCritChance()` — 5% base + effective DEX + effective luck + `BONUS_CRIT_CHANCE` + Ring of Critical Edge (+10%)
- [x] `getCritMultiplier()` — 2.0× base + `BONUS_CRIT_MULTIPLIER` × 0.1
- [x] `getEffectiveStamina()` — base 3 + positive CON modifier + `BONUS_STAMINA`
- [x] `getLuck()` — raw stat + `BONUS_LUCK` from equipment, clamped ±13
- [x] `getToxicityThresholdShift()` — `BONUS_TOXICITY_THRESHOLD` + Ring of Fortitude (+15)

**Player.java — combat wiring**
- [x] `takeDamage()` — AGI+EVASION dodge roll first (returns 0 on dodge); `BONUS_ABSORB` as flat mitigation; AC no longer incorrectly used as damage absorber
- [x] `takeSpiritualDamage()` — WIS modifier now reduces spiritual damage taken
- [x] `getEffectiveSpeed()` — AGI modifier shifts base movement speed

**CombatManager.java — formula updates**
- [x] Dice combat damage: `getDamageBonus()` (unified with instant combat)
- [x] Magic Arrow: `5 + level + player.getSpellPower()`
- [x] Crit roll: `(d20 == 20) || (random < player.getCritChance())`
- [x] Crit application: `totalDamage * player.getCritMultiplier()`
- [x] Toxicity DoT threshold: respects `getToxicityThresholdShift()`
- [x] Toxicity double-damage threshold: respects `getToxicityThresholdShift()`

**RingEffectType.java — new ring effects (9 added)**
- [x] `DEXTERITY`, `CONSTITUTION`, `INTELLIGENCE`, `WISDOM`, `AGILITY` (+5 to stat each)
- [x] `CRITICAL_EDGE` (+10% crit chance)
- [x] `EVASION` (+12% dodge chance)
- [x] `SPELL_MASTERY` (+6 flat spell power)
- [x] `FORTITUDE` (+15 toxicity threshold shift)

---

## Phase 2 — Item Data Population ✅ COMPLETE

Modifiers are applied procedurally at spawn time via `SpawnManager` pulling from `LootTable.MODIFIER_POOL`. All new modifier types have been added to the pool. Ring appearances were expanded to cover all 15 `RingEffectType` values.

### Tasks
- [x] Add `BONUS_STRENGTH` to `LootTable.MODIFIER_POOL` — 3 tiers on ARMOR ("Strong" / "of Giants" / "of Titans")
- [x] Add `BONUS_DEXTERITY` — 3 tiers on ARMOR ("Nimble" / "of Precision" / "of the Blade")
- [x] Add `BONUS_CONSTITUTION` — 3 tiers on ARMOR ("Hardy" / "of the Ox" / "of Endurance")
- [x] Add `BONUS_INTELLIGENCE` — 3 tiers on RING ("Brilliant" / "of Insight" / "of Mastery")
- [x] Add `BONUS_WISDOM` — 3 tiers on RING ("Sage" / "of the Seer" / "of the Oracle")
- [x] Add `BONUS_AGILITY` — 3 tiers on ARMOR ("Swift" / "of Shadows" / "of Swiftness")
- [x] Add `BONUS_LUCK` — 3 tiers on RING ("Lucky" / "Charmed" / "Blessed")
- [x] Add `BONUS_ABSORB` — 3 tiers on ARMOR ("Iron" / "Steel" / "Titan")
- [x] Add `BONUS_CRIT_CHANCE` — 2 tiers on WAR_WEAPON ("Precise" / "Lethal")
- [x] Add `BONUS_CRIT_MULTIPLIER` — 2 tiers on WAR_WEAPON ("Keen" / "Savage")
- [x] Add `BONUS_DODGE` — 2 tiers on ARMOR ("Evasive" / "Ghostly")
- [x] Add `BONUS_SPELL_POWER` — 3 tiers on SPIRITUAL_WEAPON and RING ("Arcane" / "Potent" / "Supreme")
- [x] Add `BONUS_STAMINA` — 2 tiers on ARMOR ("Vigorous" / "Tireless")
- [x] Add `BONUS_TOXICITY_THRESHOLD` — 2 tiers on ARMOR and RING ("Alchemical" / "Venomwoven")
- [x] Add 8 new ring appearance `ItemType` values: `RING_RED, RING_YELLOW, RING_WHITE, RING_BLACK, RING_ORANGE, RING_SILVER, RING_BRONZE, RING_IVORY`
- [x] Add JSON templates for all 8 new ring appearances in `items.json`
- [x] Register all 15 ring appearances in `ItemDataManager` (also fixed: `RING_GREEN` and `RING_GOLD` were missing from the list)
- [x] `DiscoveryManager.initializeRings()` already uses `RingEffectType.values()` — all 15 ring effects now covered

---

## Phase 3 — Inventory Screen Overhaul ✅ COMPLETE

The character sheet is a scrollable middle panel in `InventoryScreen` between the paper doll and the backpack grid. It is rebuilt by `refreshCharacterSheet()` whenever equipment changes.

### Implementation tasks
- [x] Design the layout (middle panel in the `mainSplit` three-column layout)
- [x] Create `refreshCharacterSheet()` / `buildCharSheetScroll()` reading all effective stats from `Player`
- [x] Wire in equipment delta display — `csAttr(name, base, effective)` shows delta in cyan (buff) or red (debuff)
- [x] Display all resistances (eight damage types via `player.getElementalResistance(DamageType)`)
- [x] Display survival stats with color-coded warning thresholds (satiety, hydration, temperature)
- [x] Display toxicity tier label (CLEAN / MEDIUM / CRITICAL) respecting `getToxicityThresholdShift()`
- [x] Display active status effects with duration — debuffs orange, buffs/intrinsics cyan; "perm" for infinite
- [x] Update display whenever equipment changes (equip/unequip/drop calls `refreshSlots()` → `refreshCharacterSheet()`)

---

## Phase 4 — Build Differentiation Polish ✅ COMPLETE

Make the five build archetypes feel distinct in practice.

### Build archetypes
| Build | Primary Stats | Core Mechanic |
|---|---|---|
| **Berserker** | STR, CON | High HP, max stamina dice, toxicity embrace |
| **Arcane Scholar** | INT, WIS | Spell burst, MP regen, spiritual defense |
| **Shadow** | DEX, AGI | Crit fishing, dodge, movement speed |
| **Divine Paladin** | STR, WIS | Spiritual damage, BANE weapons, healing |
| **Toxic Communion** | CON, TOX | Stack threshold items, poison immunity |

### Tasks
- [x] AGI flurry: at AGI 14+, 20% chance bonus instant attack per turn; 35% at AGI 18+ — `CombatManager.playerAttackInstant()`, stateless resolveAttack so MONSTER_TURN is not re-triggered
- [x] WIS MP regeneration: passive MP recovery per turn — `TurnManager` ring-effects block; WIS mod × 10% chance per turn to restore 1 MP
- [x] Luck integration in loot quality: `SpawnManager.attemptToModifyItem()` — `playerLuck * 0.01f` shifts base modifier chance, second, and third modifier roll thresholds (±13 luck = ±13% chance)
- [ ] Luck in trap trigger probability (`WorldManager`) — no trap system found; defer until traps are implemented
- [x] Berserker build items: `BELT_OF_GIANT_STRENGTH` (amulet slot, STR+4, MaxHP+5), `BERSERKERS_GAUNTLETS` (STR+2, Absorb+2, Stamina+1)
- [x] Shadow build items: `BOOTS_OF_SWIFTNESS` (AGI+4, Dodge+5%), `CLOAK_OF_SHADOWS` (AGI+2, DEX+2, Dodge+3%)
- [x] Scholar build items: `HOOD_OF_CLARITY` (INT+4, MaxMP+5), `AMULET_OF_INSIGHT` (INT+2, WIS+2, SpellPower+2)
- [x] Paladin build items: `AMULET_OF_DIVINE_FAVOR` (WIS+4, MaxMP+3), `HOLY_GAUNTLETS` (WIS+2, STR+2, AC+2)
- [x] Toxic build items: `ALCHEMISTS_BELT` (ToxThreshold+20, CON+4), `TOXIC_VEIL` (ToxThreshold+10, PoisonResist+8, CON+2)
- Implementation: ItemType enum + armor.json templates + guaranteed modifiers in `ItemDataManager.applyNamedItemModifiers()` + spawntables.json entries (weight 2, minLevel 5-7)

---

## Phase 5 — Stat Point Allocation (Optional / Future) ❌ NOT STARTED

Currently stats only change via equipment and items (NetHack model). If we want explicit build choices at level-up or character creation, this phase adds that.

### Tasks
- [ ] Character creation: choose a starting archetype that sets initial stat distribution (e.g. Warrior: STR 14, CON 12, DEX 10, INT 8, WIS 8, AGI 10)
- [ ] Level-up: grant 1-2 stat points to allocate (player picks which primary stat increases)
- [ ] UI for stat allocation (simple +/- per stat with a confirm button)
- [ ] Balance: decide whether items or allocation points are the primary progression driver

---

## Key formulas reference (as implemented)

```
To-Hit Bonus        = max(1, level/4 + (DEX-10)/2)
Damage Bonus        = max(0, (effectiveSTR-10)/2) + equipment.BONUS_DAMAGE
Effective STR       = base + toxicity_boost + equipment.BONUS_STRENGTH
Toxicity med tier   = toxicity >= 26+shift  (+10% STR, -10% defense)
Toxicity crit tier  = toxicity >= 76+shift  (+20% STR, -20% defense, double damage, DoT)
Threshold shift     = equipment.BONUS_TOXICITY_THRESHOLD + (Fortitude ring ? 15 : 0)
Crit Chance         = 5% + max(0,DEX_mod)*2% + luck*0.5% + BONUS_CRIT_CHANCE% + (Critical Edge ? 10% : 0)
Crit Multiplier     = 2.0 + BONUS_CRIT_MULTIPLIER*0.1
Dodge Chance        = max(0,AGI_mod)*4% + BONUS_DODGE% + (Evasion ? 12% : 0)
Absorb              = equipment.BONUS_ABSORB  (flat reduction on connected physical hits)
Spiritual Defense   = ring defense + resistance + max(0,WIS_mod)
Spell Power         = max(0,(INT-10)/2) + BONUS_SPELL_POWER + (Spell Mastery ? 6 : 0)
Magic Arrow damage  = 5 + level + spellPower
Stamina             = base(3) + max(0,CON_mod) + BONUS_STAMINA
Effective Luck      = clamp(stats.luck + BONUS_LUCK, -13, 13)
HP per level        = max(1, 2 + CON_mod + rand(3))
MP per level        = max(1, 2 + INT_mod + rand(3))
```

---

## Next session starting point

**Phases 1–4 are complete**, plus the full inventory screen overhaul (2026-05-02). The stat system is fully wired: six primary attributes + Charisma, 15 modifier types, build-specific named items, and a live two-column stats panel in the inventory screen that shows all stats, deltas, resistances, survival, and status effects.

Layout: `[Paper Doll + Spells/Alchemy below] | [Backpack + Quick Slots + Stats panel (bottom)]` — both MODERN and RETRO modes.

**Phase 5 (stat point allocation)** is the only remaining work if explicit build choices at level-up are desired.

Key files if continuing:
- `core/.../screens/InventoryScreen.java` — `refreshStatsTable()` for any display tweaks
- `core/.../gamedata/player/PlayerStats.java` — add stat allocation fields here
- `core/.../screens/GameScreen.java` — wire level-up UI prompt
