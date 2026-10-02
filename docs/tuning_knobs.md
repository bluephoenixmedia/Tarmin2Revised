# Tuning Knobs

The values that change how the game plays when you move them. Compiled 2026-10-01 from the code as it stands on `develop`.

- **[JSON]** = edit the file under `assets/data/`, restart, no compile. Everything else is a Java constant: edit, then rebuild.
- Paths: `J/` = `core/src/main/java/com/bpm/minotaur/`, `D/` = `assets/data/`. Line numbers drift; search for the constant name.
- Before you tune, read **Dead and disabled knobs** below. Several things that look like knobs do nothing.

## Dead and disabled knobs (changing these has no effect)

| Where | What |
|---|---|
| `D/spawntables.json` `monsterSpawnTable` (~41 rows) | Loaded but never read. Monster choice really comes from `monsters.json` `frequency` and `baseLevel` plus the `MonsterSpawner` rules (section 6). |
| `D/spawntables.json` `itemSpawnTable` (399 rows) | Loaded but never read. World items come from `ItemSpawner` category weights times each template's `probability` (section 7). |
| `D/spawntables.json` `levelBudgets[].monsterBudget` | Not used for monster count. `SpawnManager` computes `round(14 + 3.5 x (depth-1))` instead. (`itemBudget`, `containerBudget`, `debrisBudget`, `mimicBudget`, `defaultBudget` ARE live.) |
| `J/managers/SpawnManager.java` `BASE_MODIFIER_CHANCE` 0.15, `COLOR_MULTIPLIER_BONUS` 0.1, `SECOND_MODIFIER_CHANCE` 0.25, `THIRD_MODIFIER_CHANCE` 0.10 | Declared, unused. |
| `J/managers/DoomManager.java` `LOOT_DECAY_RATE` 0.02 | Disabled: `getLootChanceMultiplier()` returns 1.0. |
| `J/managers/TurnManager.java` `TEMP_ADJUST_RATE` 0.05 | Appears unused; the inline temperature numbers below are the real ones. |
| `D/monsters.json` `baseAC` | Legacy; combat reads `armorClass`. |

Other things to know before tuning:
- Most level 1-2 spells in `spells.json` carry placeholder `2d6` / `3d6` FORCE dice even when they are not damage spells. Check a spell really reads `damageDice` first.
- `weapons.json` has `probability` 0 on 250 of 266 entries and `armor.json` on 108 of 118, so almost nothing spawns from the world roll for those. Raise `probability` to make an item findable.
- Many later monsters have no `frequency` key (default 0, so rare but not excluded, plus alignment +5 and level +1 bonuses).
- Dragon `baseExperience` is 50, low next to its stats; likely a data-entry slip.
- `startArrows` on EASIEST is 99, an outlier next to 8/7/6.

## 1. Difficulty and player start

| Where | Knob | Value | Effect |
|---|---|---|---|
| `J/gamedata/Difficulty.java` | `startWarStrength` | EASIEST 18, EASY 16, MEDIUM 14, HARD 12 | Starting HP and max HP |
| same | `startSpiritualStrength` | 9 / 8 / 7 / 6 | Starting MP and max MP |
| same | `startArrows` | 99 / 8 / 7 / 6 | Starting arrows |
| same | `vulnerabilityMultiplier` | 0.25 / 0.50 / 0.75 / 1.0 | Scales damage the player takes |
| `J/gamedata/player/PlayerStats.java` | base attributes | all 10 | Starting STR, DEX, CON, INT, WIS, AGI, CHA. Modifier is (stat-10)/2 everywhere |
| same | `stamina` | 3 | Starting dice-selection stamina |
| same | `maxToxicity` | 100 | Toxicity cap |
| same | `getBaseCritChance()` | 0.05 | Base crit chance |
| `J/gamedata/player/Player.java` | `STARTING_RATIONS` | 30 | FOOD items in the starting pack |
| same | starting kit | lantern (left hand), 1 waterskin, no armour | |
| `J/gamedata/player/PlayerStats.java` | `STARTING_SATIETY` | 80 | Food meter at the start of every expedition |
| `J/screens/GameScreen.java` | provisions bonus | +1 FOOD per Provisions tier | Altar bonus rations |

## 2. Progression: XP, levelling, regeneration

| Where | Knob | Value | Effect |
|---|---|---|---|
| `PlayerStats.java` | `BASE_XP_REQUIRED`, `LOG_BASE` | 120, 1.6 | XP for a level = 120 x 1.6^(level-1) (L2 192, L3 307, ...) |
| same | HP per level | `max(1, 2 + CONmod + rnd(0..2))` | The `2` and `nextInt(3)` are inline |
| same | MP per level | `max(1, 2 + INTmod + rnd(0..2))` | Same pattern |
| same | level-up heal | 25% of new max | |
| same | points per level | 2 attribute, 1 skill | |
| same | `getToHitBonus` | `max(1, 2 + level/2 + DEXmod)` | Player to-hit |
| same | `getRegenIntervalTurns` | `max(8, 20 - 2 x CONmod)` | Turns per 1 HP regenerated |
| same | `getDamageBonus` | `max(0, (effSTR-10)/2)` | Strength damage bonus |
| `J/managers/TurnManager.java` | MP regen interval | `max(8, 24 - 3 x WISmod)` | Turns per 1 MP; needs hydration above 0 |
| same | ring recharge | every 120 turns | +1 charge |
| same | Ring of Regeneration | heal per ring each turn | |
| `J/managers/CombatManager.java` | kill XP scalar | `1 + 0.1 x dungeonLevel` | XP = baseExperience x colour multiplier x this |
| `J/gamedata/monster/Monster.java` | `scaleStats` | HP `1 + 0.15 x (level-1)`, AC `+level/5`, XP `1 + 0.25 x (level-1)` | How monsters scale with depth |
| `J/managers/DivinityManager.java` | kill Divinities | `max(1, (monsterBaseLevel + dungeonLevel)/2)` | Meta-currency per kill |
| same | chunk Divinities | `max(1, dungeonLevel)` per new chunk per run | |

## 3. Survival: hunger, thirst, temperature, encumbrance

| Where | Knob | Value | Effect |
|---|---|---|---|
| `TurnManager.java` | `SATIETY_DECAY` | 0.04 per time unit | Hunger rate (about 3000 turns from 120) |
| same | `HYDRATION_DECAY` | 0.04 | Thirst rate; x2 when body temperature above 38 |
| same | starvation damage | 1 true damage every 25 turns at satiety 0 | |
| same | dehydration damage | 1 true damage every 20 turns at hydration 0 | |
| same | cold stress | `-(5 - ambient - warmth) x 0.003` when ambient below 5C | |
| same | heat stress | `(ambient - 35) x 0.005` when ambient above 35C | |
| same | comfort recovery | +/-0.02 per time unit toward 37C | |
| same | desert shade | -12C ambient | |
| same | ring of WARMTH | +20 warmth | |
| same | body temperature clamp | 30.0 to 41.0 | |
| same | `HEAT_SOURCE_RADIUS`, `HEAT_SOURCE_WARM_RATE` | 3.5, 0.4 | Campfire, pot, lantern warming |
| `PlayerStats.java` | `MAX_SATIETY`, `MAX_HYDRATION` | 120, 100 | Meter sizes |
| same | satiation thresholds | STARVING <=0, HUNGRY <=25, NORMAL <=80, SATIATED <=110, CHOKING >110 | |
| same | `BODY_TEMP_FREEZING`, `BODY_TEMP_OVERHEAT` | 32.0, 41.0 | |
| `J/managers/AlertMonitor.java` | `THIRSTY_BELOW` | 25 | Thirst alert threshold |
| `Player.java` | ration default nutrition | 8 | When the item has no `nutrition` |
| same | meal effect | +45 satiety, +25 food, +15 hydration | |
| same | potion default hydration | 5 | |
| same | flour sack, meat pickup | 6-9, 10-15 food | |
| `J/screens/CookingScreen.java` | campfire cook | heal `15 + 4 x cookingSkill`, +20 satiety | |
| `D/items.json` | `nutrition`, `hydrationValue` [JSON] | per item (e.g. MEAT nutrition 10) | Per-item food and water |

Eating while too full deals 3 true damage (`Player.java`, near the meal code).

**Encumbrance** (`J/gamedata/player/Encumbrance.java`)

| Knob | Value |
|---|---|
| `BASE_CAPACITY` | 25 |
| `CAPACITY_PER_STRENGTH` | 5 |
| `COINS_PER_WEIGHT_UNIT` | 100 |
| Tier thresholds (share of capacity) | Burdened 1.0, `STRESSED_AT` 1.5, `OVERLOADED_AT` 2.0 |

| Tier | speedFactor | drainFactor |
|---|---|---|
| Unencumbered | 1.00 | 1.0 |
| Burdened | 0.75 | 1.0 |
| Stressed | 0.50 | 1.5 |
| Overloaded | 0.25 | 2.0 |

**Item weights** (`J/gamedata/item/ItemWeights.java`; used only when the template `weight` is the default 1.0, and `weapons.json` / `armor.json` are 1 on almost every entry, so these decide):
- Container 2, shield 8. Armour = slot weight x category factor.
  - Slot weights: torso 15, legs 8, arms 5, helmet and boots 4, gauntlets and cloak 3, other 5.
  - Category factors: HEAVY 1.5, MEDIUM 1.0, LIGHT 0.6.
- Ring 0.2, amulet 0.5, scroll 0.2, key 0.3, gem 0.3, treasure 0.5, spellbook 3, other 1.
- Weapons: thrown 1, crossbow 7, other ranged 3, two-handed or reach 10, finesse 2, other 5.
- `Player.java` `AMMO_WEIGHT` 0.05. A JSON `weight` other than 1.0 overrides all of this.

**Movement speed** (`Player.java`): base 12 + (AGI-10)/2. SLOWED halves it, SUPER_SPEED doubles it, CHILLED x0.8. Leg fractures use the injury speed table (section 4).

## 4. Combat

| Where | Knob | Value | Effect |
|---|---|---|---|
| `Player.java` | player AC | 10 + DEXmod (capped by chest `maxDexBonus`) + equipment AC | |
| same | HARDENED status | +4 AC | |
| `J/gamedata/player/PlayerEquipment.java` | `MAX_EQUIPMENT_AC_BONUS` | 15 | Cap on equipment AC |
| `D/armor.json` [JSON] | `armorClassBonus`, `maxDexBonus`, `armorCategory`, `weight` | AC 0-5; DEX cap 99/2/0; HEAVY 29, MEDIUM 19, LIGHT 62, SHIELD 8 | Per-armour stats |
| `D/weapons.json` [JSON] | `damageDice`, `accuracyModifier`, `baseValue`, `critChanceBonus` | mostly 1d4 / 1d6; accuracy 0 on all but 2 | Per-weapon stats |
| `D/items.json` [JSON] | BRASS_LANTERN `critChanceBonus` | 0.10 | Lantern crit bonus |
| `CombatManager.java` | monster to-hit | `d20 + monsterAtkBonus >= targetAC` | |
| same | `calculateMonsterAttackBonus` | `2 + level/3` plus stat modifier | |
| same | `MONSTER_STAT_MOD_FLOOR` | 1 | Monster stat modifier = max(floor, maxHP/14) |
| same | player crit | natural 20, or `random < getCritChance()` | |
| `Player.java` | `getCritChance` | 0.05 base + 0.02 per DEXmod + 0.005 per Luck + gear + Ring of Critical Edge 0.10 + lantern in left hand 0.10; cap 0.95 | |
| same | `LANTERN_STATION_CRIT_BONUS` | 0.05 | With the Shelter Lantern station |
| same | `getCritMultiplier` | 2.0 + 0.1 per `BONUS_CRIT_MULTIPLIER` | |
| `CombatManager.java` | `GLANCING_BLOW_MAX_DELTA` | 3 | A miss within 3 of AC still glances |
| same | `GLANCING_BLOW_DAMAGE_MULTIPLIER` | 0.35 | Glancing damage |
| same | `PERFECT_PARRY_WINDOW_MS` | 200 | Timing window for a full negate |
| same | guard damage reduction | 4 with shield, 2 without | |
| same | HEAVY_ARMOR_MASTERY | -3 damage | Skill effect |
| same | AGI flurry | AGI>=18: 0.35, AGI>=14: 0.20 | Chance of a bonus instant attack |
| same | shield bash | 1d4 + shield AC bonus | |
| same | confusion | 50% fail or 50% wild miss | |
| same | toxicity damage boost | x2 at toxicity >= 76 | |
| same | venom on hit (snake, spider) | 30% | Applies POISONED |
| same | BLOOD_SURGE trigger | HP <= 35%, 15 turns | |
| same | weapon bleed chance | crit 1.0, finisher 0.85, normal 0.40 | Bleed on monster |
| same | monster bleed | 3 ticks x `max(1, dmg/3)` | |
| same | stun chance | crit 0.80, finisher 0.55, normal 0.25 | Stun on monster |
| `J/managers/DimensionalManager.java` | Void multipliers | physical x0.40, spiritual x2.50 | |
| `J/gamedata/MagicResistance.java` | `PLAYER_CAP`, `MONSTER_CAP` | 75, 90 | Resistance cuts spell damage by X% and is also the resist chance |
| `D/monsters.json` [JSON] | `magicResistance` | 0-60 (Dragon 30, Lich and Iron Golem 50, Bringer 60) | Per-monster resistance |
| `J/gamedata/monster/MonsterColor.java` | (strength, XP) per colour | BLUE 1.0/1.2, PINK 1.2/1.3, PURPLE 1.4/1.5, WHITE 1.0/1.2, GRAY 1.2/1.3, ORANGE 1.4/1.5, YELLOW 1.0/1.4, TAN 1.2/1.5, BROWN 1.1/1.2, RED 1.3/1.4, GREEN 1.1/1.2, BLACK 1.6/1.8 | Variant HP and XP multipliers |
| `J/gamedata/effects/PoisonDose.java` | `MAX_TICKS`, `MAX_POTENCY` | 18, 4 | Poison caps |
| same | monster-level dose | ticks min `3+(L-1)`, max `6+2(L-1)`; potency `1 + L/5` | |
| `J/gamedata/monster/MimicDetection.java` | `BASE_CHANCE`, `PER_POINT`, `MIN_CHANCE`, `MAX_CHANCE` | 25, 5, 5, 95 (percent, by WIS) | Spotting a mimic |
| `J/gamedata/monster/MonsterTactics.java` | `ELITE_INTELLIGENCE`, `ELITE_LEVEL`, `MELEE_CLOSING_DISTANCE`, `ELITE_STANDOFF_DISTANCE` | 14, 10, 2, 3 | Ranged-monster behaviour |
| `J/gamedata/monster/HostileSight.java` | `SIGHT_RANGE_TILES` | 8 | Monster sight |
| `J/gamedata/monster/PursuitMemory.java` | `MIN_PATIENCE_TURNS`, `MAX_PATIENCE_TURNS` | 3, 20 | Pursuit duration |
| `J/gamedata/spells/MonsterSpellSight.java` | `EXTREME_INTELLIGENCE` | 22 | |

**Injury, bleeding and illness** (`J/gamedata/injury/InjuryManager.java`)

| Knob | Value |
|---|---|
| `HEAVY_HIT_HP_FRACTION` | 0.35 |
| `HEAVY_HIT_MIN_DAMAGE` | 5 |
| `HEAVY_HIT_INJURY_CHANCE` | 0.30 |
| `CRIT_INJURY_CHANCE` | 0.55 |
| `BLEED_INTERVAL_BASE` | 6 (steps between ticks: 5, 4, 3 by severity) |
| `BLEED_DAMAGE_PER_TICK` | 1 |
| `ILLNESS_TICK_STEPS` | 120 |
| `FESTER_STEPS` | 150 |
| Severity bands | damage fraction >= 0.75 is 3, >= 0.55 is 2, otherwise 1 |
| `FAILED_PRESSURE_HP_COST`, `FAILED_PRESSURE_TURN_COST` | 2, 5 |
| Bare-hand pressure success | 0.60 (25 turns) |
| Dirty-rag infection | 0.35 |
| Leg-fracture speed | 0.85 / 0.75 / 0.60 untreated; 0.90 splinted; septic delirium 0.75 |
| `InjuryRecord` `BLEED_TICKS_BASE`, `BLEED_TICKS_PER_SEVERITY` | 4, 2 |

**Firearms** (`J/gamedata/firearm/`)
- `FirearmProfile`: `SIDEARM_RELOAD_TURNS` 2, `LONG_GUN_RELOAD_TURNS` 3, `MIN_AUDIBLE_RADIUS` 9.
- Per weapon (noise radius, minimum depth): Starwheel pistol 9 / 2, Blunderbuss 16 / 2, Tufenk 10 / 6, Arquebus 12 / 8, Musket 12 / 10.
- `PowderDampness`: `MAX_MISFIRE_CHANCE` 0.25, `TURNS_TO_DRY` 15, `SOAKING_WEATHER_THRESHOLD` 0.5.

## 5. Spells and magic

`D/spells.json` [JSON], 370 spells. Per spell: `level` (0-9), `mpCost`, `damageDice`, `damageType`, `range`, `duration`, `statusEffect`.

| Level | mpCost | Common damageDice |
|---|---|---|
| 0 | 0-2 (30 cantrips) | 1d8 |
| 1 | 3 | 2d6 (many placeholders) |
| 2 | 5 | 3d6 |
| 3 | 8-15 | 6d6 |
| 4 | 12 | 8d6 |
| 5 | 16 | 10d6 |
| 6 | 22 | 12d6 |
| 7 | 28 | 14d6 |
| 8 | 36 | 16d6 |
| 9 | 45 | 18d6 |

| Where | Knob | Value |
|---|---|---|
| `J/gamedata/spells/SpellExecutionEngine.java` | skill cost reduction | -1 MP, minimum 1 |
| same | `RUNIC_CONSERVATION` free-cast chance | 0.25 |
| same | Fireball range | clamp(spell range, 4, 12) |
| same | Magic Missile min range, dart spread | 3, 0.15 per dart |
| same | `FOG_MAX_RANGE`, `FOG_RADIUS_TILES`, `FOG_DURATION_TURNS` | 6, 2, 10 |
| `J/gamedata/spells/effects/ForcePushEffect.java` | `GORE_WALL_THRESHOLD` | 3 |
| `J/gamedata/spells/SpellbookStudy.java` | `MAX_DELAY_TURNS` | 6 |
| `J/gamedata/progression/ShelterAltar.java` | `ARCANE_COSTS` | {15, 25, 40}. Tier 1: 4 Tome choices; tier 2: adds a reroll; tier 3: 5 choices and level-8 Tarmin spells |
| `J/managers/CombatManager.java` | `ARCANE_SPARK_DICE` | "1d4" |

## 6. Monster spawning

**Per-level budgets** `D/spawntables.json` `levelBudgets` [JSON] (`monsterBudget` is dead, see top):

| Level | item | container | debris | mimic |
|---|---|---|---|---|
| 1 | 12 | 4 | 15 | 0 |
| 2 | 12 | 4 | 6 | 0 |
| 3-5 | 15 | 6 | 6-7 | 0 |
| 6-9 | 18 | 8 | 9-10 | 0 (6-7), 1 (8-9) |
| 10-15 | 20 | 10 | 11-15 | 1 (10), 2 (11-15) |

`defaultBudget` (past level 15): item 5, container 4, debris 7, mimic 2.

**Counts** (`J/managers/SpawnManager.java`)
- Monsters: starter chunk (0,0) on depth 1 is 8; otherwise `round(14 + 3.5 x (depth-1))`.
- Items: `itemBudget x 1.2`. Containers: `containerBudget x 0.5`. Debris: `debrisBudget x 0.5`.
- Mimics: from level 8 (`MimicSpawnRule.MIN_LEVEL`), uniform 0 to `mimicBudget`.
- Container key tiers: depth >= 6 skeleton 10% / gold 55% / silver 35%; depth >= 3 skeleton 3% / silver 57% / iron 40%; shallower silver 25% / iron 75%.
- Item affixes: cursed below 0.20, blessed above 0.90. Enchant chance 0.15 (magnitude 1-3; at depth > 5, 70% are forced positive). Affix counts by colour: ORANGE 1, BLUE 1-2, WHITE 2, PINK 2-3, PURPLE 3-4, TAN 0.
- Spawn exclusion: 8 tiles from the player for periodic spawns; 6 from the door and hearth.

**Which monster** (`J/generation/MonsterSpawner.java`)
- Weight = `monsters.json` `frequency`, +5 if alignment matches, +1 if `baseLevel <= 2 x playerLevel`.
- Level window: `baseLevel >= max(1, EDL/4)` and `<= EDL`. Below the surface, a 15% out-of-depth chance raises the cap to `EDL + 1 + rn2(3)`.
- Near the starter shelter (depth <= 1, chunk within 2 of 0,0): `baseLevel <= 2` and MCR <= 100.
- `calculateMCR` = maxHP + maxMP + 3 x AC + DEX + 15 if ranged.
- Gehennom starts at level 25.
- **EDL** (`J/managers/WorldManager.java`): `el = depth + (|chunkX|+|chunkY|)/2`; `edl = (el + playerLevel)/2`; capped at 2 on the starter ring; plus `difficultyOffset`, which grows by `currentLevel` on each world reset.
- Monster inventory (`J/generation/MonsterFactory.java`): weapon users get armour on a 1-in-2 roll; INT >= 8 get a potion at 1-in-6 and a wand at 1-in-10; INT > 5 get gold at 1-in-3.

**Per-monster** `D/monsters.json` [JSON], 60 monsters. Keys: `baseLevel`, `frequency`, `maxHP`, `maxMP`, `armorClass`, `magicResistance`, `moveSpeed` (default 12), `baseExperience`, `damageDice`, `dexterity`, `intelligence`, `attackRange`, `rangedDamageDice`, `onHitEffects[{type,duration,potency,chance}]`, `variants[{color,minLevel,maxLevel,weight}]`, `spellChance`, `isSpellcaster`.
- Frequency: ant, spider, skeleton 10; scorpion, snake 8; orc 6; dwarf 4; dragon 1.

| Monster | Level | HP | AC | XP | Damage |
|---|---|---|---|---|---|
| KOBOLD | 1 | 8 | 12 | 5 | 1d4+1 |
| GOBLIN | 1 | 8 | 12 | 8 | 1d6 |
| GIANT_ANT | 1 | 12 | 12 | 10 | 1d6 |
| ORC | 3 | 22 | 13 | 20 | 1d10+3 |
| OGRE | 5 | 75 | 11 | 140 | 3d8+5 |
| TROLL | 6 | 70 | 15 | 130 | 2d10+5 |
| DRAGON | 10 | 80 | 16 | 50 | 4d10+8 |
| LICH | 15 | 50 | 17 | 300 | 3d10+8 |
| IRON_GOLEM | 15 | 120 | 16 | 500 | 4d10+10 |
| PURPLE_WORM | 18 | 140 | 17 | 600 | 5d10+10 |
| BRINGER_OF_DEATH | 18 | 140 | 16 | 500 | 4d12+10 |

**Periodic spawn**: none at level <= 1; the interval comes from the doom stage (section 8), and EDL gets the doom bonus.

**Bridge boss** (`J/gamedata/boss/BridgeBoss.java`): `SCALE_X` 0.82, `SCALE_Y` 2.5, `MOVE_SPEED` 12, `BASE_HP_BONUS` 120; each re-summon adds 50% of the base bonus.

**Themed chunks** `D/themes.json` [JSON], 9 themes. Keys: `crestAward`, `objectiveCount`, `championHpBonus`, `propDensity`, `fogDistance`, `championType`, `monsters`, `props`, `hazard`.
- `crestAward`: Blood Colosseum 2, Ruined Castle 2, Buried Necropolis 2, Bridge of Souls 3, the rest 1.
- `championHpBonus`: Castle 80, Battalion 50, Necropolis 70, Bridge 120.
- `objectiveCount`: Makeshift Graveyard 4, the rest 1.

## 7. Loot and item spawning

**`J/generation/ItemSpawner.java`** picks a category by weight, then an item by its template `probability`.

| Context | Category weights |
|---|---|
| Default | FOOD 30, POTION 20, SCROLL 25, WAR_WEAPON 15, SPIRITUAL_WEAPON 15, ARMOR 20, WAND 15, TOOL 8, GEM 8, BOOK 8, RING 6, AMULET 2, AMMUNITION 15 |
| Container | POTION 18, SCROLL 18, GEM 18, FOOD 5, TOOL 8, WAND 6, RING 6, BOOK 6, AMULET 2, GOLD 9, AMMO 12 (no weapons or armour) |
| Gehennom | WAR_WEAPON 10, SPIRITUAL_WEAPON 10, ARMOR 20, POTION 1, SCROLL 1, FOOD 5, GEM 15, TOOL 10, WAND 10, RING 8, BOOK 5, AMULET 5, AMMO 10 |

- From EDL 6, food weight is x0.20 (floor 1): the "subsistence shift".
- `unlockGated` items get x3 weight.
- `FirearmSpawnRule` gates firearms by depth (section 4).

**`D/spawntables.json`** [JSON]
- `containerSpawnTable`: SMALL_BAG levels 1-5 weight 12; BOX 1-10 weight 10; MEDIUM_PACK 4-14 weight 8; LARGE_BAG 6-18 weight 6; REGULAR_CHEST 8+ weight 4.
- `containerLoot.default` (minLevel, weight): FOOD 1/25, COINS 1/20, SCROLL 1/8, WAND 2/6, CHALICE 3/10, LAMP 1/8, NECKLACE 5/6, INGOT 8/4, CROWN 12/2, QUIVER 1/16, QUARREL_LIGHT 1/12, ARROW_FLIGHT 1/12, SHOT_POUCH 2/8.
- `debrisSpawnTable` (41 rows): MYSTERIOUS_PORTAL weight 5 at levels 4-6, 15 at 7-9, 30 at 10+ (the only debris gated by level in code); other debris draws by weight from 20 (STICK, SMALL_ROCK) down to 1 (ANCIENT_FOSSIL).
- `J/gamedata/LootTable.java` `MODIFIER_POOL`: affixes with `minLevel`, `maxLevel`, `minBonus`, `maxBonus`.

## 8. Doom clock, deaths, bones

| Where | Knob | Value | Effect |
|---|---|---|---|
| `J/managers/DoomManager.java` | `MAX_DEATHS_ALLOWED` | 50 | Death cap: boss, then apocalypse |
| same | `DAMAGE_SCALE_PER_DEATH` | 0.025 | +2.5% per death, scaled by depth |
| same | depth weight | `(level-1)/4`, capped; multiplier max 1.5 | Enemy HP and damage multiplier |
| same | stage thresholds | deaths 1/2/3 = stage 2/3/4; turns >400/800/1200 | |
| same | spawn interval | stage 1: 300, 2: 200, 3: 120, 4: 80 turns | |
| same | EDL bonus | 0, +1, +2, +3 | |
| same | speed multiplier | 1.05 at stage >= 2 | |
| same | shadow affixes | stage >= 3 | |
| same | re-summon HP | +50% of base bonus | Bridge boss |
| `J/managers/BonesManager.java` | `MAX_BONES_PER_STRATA` | 5 | |
| same | `DEFAULT_SPAWN_CHANCE` | 0.30 | Bones chance per floor |
| same | strata | `(floor-1)/3 + 1` | Three floors per strata |

## 9. Shop, crafting, cooking

| Where | Knob | Value |
|---|---|---|
| `J/gamedata/ShopInventory.java` | `MARKUP` | 2.0 (buy = baseValue x 2) |
| same | `BUYBACK` | 0.5 (sell price) |
| same | `DEFAULT_BASE_VALUE` | 50 |
| same | stock counts | weapons 3-5, armour 2-3, potions 3-5, special 1-2, ammo 1-2 |
| `J/gamedata/ShopkeeperNpc.java` | `BASE_SPEED`, `LASER_COOLDOWN_TURNS`, `RESTITUTION_DISCOUNT` | 8, 2, 0.15 |
| `J/managers/ShopkeeperAiManager.java` | `WANDER_CHANCE`, `TRADING_COOLDOWN` | 0.30, 5 |
| `J/generation/MazeChunkGenerator.java` | merchants | one per level, at least 8 tiles from the player spawn |
| `D/items.json`, `weapons.json`, `armor.json` [JSON] | `baseValue` | per item (potions 20-800; healing 60/120/250/500). Also feeds the unlock score |
| `J/managers/CookingManager.java` | `BASE_EFFECT_DURATION`, `SKILL_DURATION_BONUS`, `SYNERGY_DURATION_MULTIPLIER` | 120, 20 per skill level, 2.0 |
| `J/managers/CraftingManager.java` | hone scrap cost | tier +1: 3, +2: 6, +3: 12 (plus 1 STRANGE_METAL at the third) |
| same | recipes (`legacyRecipes`, alchemy `reagentMap`) | hard-coded ingredients and outputs |
| same | salvage yield | 2-3 scrap, +1 if baseValue > 50 (weapons) or > 60 (armour) |

## 10. Shelter altar, divinities, crests, unlocks

| Where | Knob | Value | Effect |
|---|---|---|---|
| `J/gamedata/progression/ShelterAltar.java` | `MAX_ASCENSION_TIER`, `ASCENSION_COSTS` | 5, {1, 2, 4, 7, 10} Crests of Valor | Cost per tier; each tier gives +1 to the stat |
| same | `MAX_TIER`, `BASE_COST` | 3, 30 | Provisions, Repertoire, Monument cost 30 / 60 / 90 |
| same | `STATUE_FREQUENCY_BASE`, `_MAX` | 0.15, 0.35 | Statue event chance by Monument tier |
| same | station costs (Divinities) | Bed 15, Stash 20, Fire Pot 20, Crafting Bench 30, Lantern 10, Training Grounds 25, Archive Lectern 15, Verdant Gate 25 (depth 3), Dune Gate 35 (depth 5), Mist Gate 45 (depth 7) | |
| `J/managers/DivinityManager.java` | `MAX_LOOT_RETENTION_LEVEL`, `LOOT_RETENTION_ITEMS_PER_LEVEL`, `LOOT_RETENTION_BASE_COST` | 5, 2, 20 | Retention upgrade costs 20 x (level+1) |
| `J/managers/UnlockManager.java` | `UNLOCK_SCORE_THRESHOLD` | 50 | Items scoring above this start locked |
| same | `calculateItemScore` | `baseValue + AC x 100 + dice n x sides x 50` | |
| same | unlock eligibility | `maxEligibleScore = 50 + strata x 250` | Score ceiling by depth |
| same | milestones | 100 kills, 150 divinities earned, 1000 turns lived | Each adds one unlock |
| same | `unlocksToGrant` | `min(2, 1 + milestones)` | Unlocks per delve |
| `J/managers/UnlockRotation.java` | `HISTORY_LENGTH`, `MAX_STALENESS` | 6, 4 | Category weight = 1 + min(stale, 4) |
| `J/managers/SaveManager.java` | `MAX_SLOTS` | 3 | |

## 11. Resting and alerts

| Where | Knob | Value |
|---|---|---|
| `Player.java` | `FIELD_REST_HEAL_AMOUNT` | 5 HP per field rest |
| same | field rest cost and cooldown | 1 satiety; cooldown = `getRegenIntervalTurns()` (8-20), not its own constant |
| `J/screens/GameScreen.java` | `FIELD_REST_TICKS_PER_PRESS` | 5 |
| Bed station | rest at the shelter | 100% HP and MP, no cooldown |
| `J/managers/AlertMonitor.java` | `COOLDOWN_SECONDS` | 3 |
| same | `LOW_HEALTH_SHARE`, `REARM_HEALTH_SHARE` | 0.25, 0.40 |

## 12. Lighting, day and night, weather, world

**Lighting** (`J/lighting/LightingManager.java`)

| Knob | Value |
|---|---|
| `TORCH_RADIUS` / `TORCH_INTENSITY` | 3.5 / 1.40 |
| `LANTERN_RADIUS` / `LANTERN_INTENSITY` | 5.5 / 1.61 |
| `MOUNTED_LANTERN_INTENSITY` | 1.40 |
| `UNDERGROUND_LANTERN_BOOST` | 1.5 (radius and intensity, depth > 1) |
| `STATION_LANTERN_RADIUS_BONUS` / `STATION_LANTERN_INTENSITY_FACTOR` | +1.0 / 1.10 |
| Mote of Light | radius 8.5, intensity 1.35 |
| Shopkeeper light | lantern x 0.9 radius, x 0.95 intensity |

Underground, a carried lantern lights 5.5 x 1.5 = 8.25 tiles against a torch's 3.5.
Sprite brightness falloff (`J/generation/WorldConstants.java`): `TORCH_FULL_BRIGHTNESS_RADIUS` 1.8, `TORCH_FADE_START` 3.5, `TORCH_FADE_END` 5.5, `TORCH_MIN_BRIGHTNESS` 0.04.

**Day and night** (`J/managers/DayNightManager.java`): `CYCLE_DURATION` 1500; `DAWN_START` 0.22, `DAWN_SUNRISE` 0.26, `DAY_START` 0.30, `DUSK_START` 0.68, `NIGHT_START` 0.78; `MIN_BRIGHTNESS` 0.18; `WORLD_TINT_STRENGTH` 0.45.

**Weather** (`J/weather/WeatherManager.java`)
- `MIN_WEATHER_DURATION` 300, `MAX_WEATHER_DURATION` 6000, `FOG_LERP_SPEED` 0.5.
- Weather odds by biome (`pickWeatherForBiome`):
  - Maze and Forest: clear 0.30, fog 0.15, rain 0.20, storm 0.15, snow 0.13, blizzard 0.05, tornado 0.02.
  - Lakelands: clear 0.20, fog 0.25, rain 0.25, storm 0.15, snow 0.11, blizzard 0.04.
  - Mountains: clear 0.25, fog 0.20, snow 0.30, blizzard 0.25.
  - Ocean: clear 0.10, rain 0.20, storm 0.35, snow 0.25, blizzard 0.10.
  - Desert: clear 0.70, tornado 0.15, snow 0.12, blizzard 0.03.
- Intensity odds: LIGHT 0.10, MEDIUM 0.20, HEAVY 0.30, EXTREME 0.40 (clear is always LIGHT).
- Ambient temperature: Maze 20C fixed, Lakelands 18, Forest 20, Mountains 15, Ocean 19, Desert 14-42C by sun.

**World generation** (`J/generation/WorldConstants.java`): `WORLD_SEED` 12345, `CENTRAL_MAZE_RADIUS` 10, `FOREST_BORDER_SIZE` 5, `OCEAN_THRESHOLD` -0.3, `MOUNTAIN_THRESHOLD` 0.6, `DESERT_HUMIDITY_THRESHOLD` -0.30, `LAKELANDS_HUMIDITY_THRESHOLD` 0.30, `BIOME_NOISE_FREQUENCY` 0.02, `HUMIDITY_NOISE_FREQUENCY` 0.08.

## 13. Blood and gore (mostly visual)

- Tile blood per event (`CombatManager.java`, `maze.addBlood`): 0.03 player hit, 0.04 monster-vs-monster, 0.05 various, 0.10 on kill, 0.10 / 0.30 by damage ratio. Tile blood caps at 1.0.
- Damage-ratio bands 0.15 and 0.35 pick the hit effect size; `HitFx.HEAVY_HIT_SHARE` 0.30.
- Overkill gib tiers: heavy kill at >= 40% of max HP; tiers at overkill 25% and 50%.
- `GoreManager`: `MAX_ACTIVE_PARTICLES` 250, `MAX_ACTIVE_GIBS` 40, `MAX_ACTIVE_SURFACE_DECALS` 200, `MAX_ACTIVE_WALL_DECALS` 100.
- Lifetimes: gib 30s (fade 3), surface decal 45s (expand 0.4, fade 5), wall decal 45s (fade 5).
- `BloodCoat`: `MAX_STAINS` 300, `SOAK_PER_AREA` 2.0, `OVERFLOW_SOAK` 0.001, `MAX_SOAK_PER_STAIN` 0.02. `PlayerBlood`: `MAX_PENDING` 800, `PENDING_OVERFLOW_SOAK` 0.002.
- Blood frenzy (beasts smell a gore-soaked player): `LiquidManager.MAX_EXPOSURE` 20 and `TurnManager.applyBloodFrenzy`.
- Screen shake trauma 0.1 / 0.3 / 0.5 and hit pause 0.05 / 0.12 / 0.15 by hit size.

## Not covered

Rendering and UI constants (`UiTheme`, `World3DRenderer`, paper doll, creature stitcher), and the visual JSON (`paperdoll_*.json`, `weapon_view_calibration.json`, `gate_uv.json`, `fx.json`, `spellfx.json`, `soundbank.json`, `props.json`, `segments.json`, `skeleton.json`). `encounters.json` holds event text and choices; its outcome numbers were not reviewed.
