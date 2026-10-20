# The survival meters after the thirst change

Research for [#259](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/259), part of map
[#253](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/253). Written 2026-10-20 against
`develop`. The question is how food, water and cold actually run over an Incarnation now that thirst
burns 1.0 a turn ([#235](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/235), commit
`0caa75d9`, 10-19 17:50).

Sources: the constants in `core/src/main/java/com/bpm/minotaur/gamedata/player/{Hunger,Thirst,Exposure}.java`,
the burn in `managers/TurnManager.java#updateMetabolism`, the starting kit in `Player` (constructor)
and `GameScreen` (respawn), `generation/WaterSupply.java`, `generation/StratumMap.java`,
`assets/data/spawntables.json`, and all 16 expedition telemetry files in `logs/telemetry/` (main
checkout, 10-14 to 10-19).

## Summary

- **No meter bites inside a typical expedition.** On a fresh Incarnation, Hungry and Thirsty both
  arrive on **turn 751**, even if the player eats and drinks nothing at all. The median expedition
  is **247 turns**, the 75th percentile is 640, and only **2 of 16** expeditions passed 750.
- **Food and water now run on the same clock.** With the starting kit, food lasts the longer of
  the two: Hungry at turn **2,351**, against Thirsty at **1,751** if the waterskin is never refilled.
  Water is still the easier one to restore, so neither meter is scarce. Water is the more trivial
  of the two on the surface.
- **The 10-19 run used water about six times over.** It took 27 drinks (5,400 hydration) in 835
  turns. At most about 6 of those could register under the 1,200 cap, so at least **21 drinks
  (about 4,300 hydration) were wasted**.
- **Cold has never fired.** Across all 16 files (6,801 expedition turns) `turnsCold` is 0. Telemetry
  doesn't record biome or stratum, so it can't say whether the player avoided the cold or never
  went anywhere cold.

## 1. When each meter bites

### Hunger and thirst

Both stores start at 900 (`Hunger.START`, `Thirst.START`) and burn `BURN_PER_TURN = 1.0` per
standard turn. The stage thresholds are the same on both scales: warning below 150, penalties below
50, passing out below 0, and death below `-(100 + 10 x CON)`, which is -200 at the starting CON of 10
(`PlayerStats` line 185). The table assumes a fresh Incarnation, unencumbered, CON 10, with nothing
eaten or drunk:

| Turn | Hunger stage | Thirst stage | Effect |
|---|---|---|---|
| 751 | Hungry | Thirsty | Warning only |
| 851 | Weak | Dehydrated | Hunger: -2 STR, -1 to-hit. Thirst: -2 DEX, -20% speed |
| 901 | Fainting | Collapse | A per-turn chance to pass out for 1-5 turns, starting near 1 in 20 and rising |
| 1,101 | Starved | Dead | Death |

Under the old 1.5 thirst burn the thirst column was Thirsty at turn 501 and dead at turn 734, so
thirst always bit first. The Javadoc on `Thirst.THIRSTY_BELOW` still says "turn 501" and is stale.

**The starting kit pushes both clocks out.** It is 2 rations at 800 each (`Hunger.RATION`,
`STARTING_RATIONS`) and a full waterskin of 5 drinks at 200 each (`Item#fillNewWaterskin`). A
respawn gets both again if it carries none (`GameScreen` around line 2002).

| | Store plus kit | First warning | Death (CON 10) |
|---|---|---|---|
| Food | 900 + 1,600 = 2,500 | Hungry at turn 2,351 | Turn 2,701 |
| Water (skin never refilled) | 900 + 1,000 = 1,900 | Thirsty at turn 1,751 | Turn 2,101 |

The meters reset only at death (`GameScreen` around line 1949). Returning to the Shelter does not
refill them, so the clock that matters is the whole Incarnation, not one expedition.

**These multipliers stack on the burn** (`TurnManager#updateMetabolism`):

- **Encumbrance:** Stressed is 1.5x per action and Overloaded is 2x, for both food and water
  (`Encumbrance.Tier.drainFactor`). Stressed brings Hungry and Thirsty forward to about turn 500.
- **Heatstroke:** a body temperature above 38 C makes thirst burn 2x.
- **Acute Fever:** makes thirst burn 1.5x (`Thirst.FEVER_THIRST_MULT`). Drinking blood brings it
  on 70% of the time.
- **The Fasting Monk trait:** 0.5x on both (`assets/data/traits.json`).
- **Slower actions:** time per action is `12 / speed`, so the Cold and Dehydrated slows also burn
  more per action.

### Cold (Exposure)

Body temperature starts at 37 C and is lost at `(15 - feels-like) x 0.0008` C per turn. That rate
doubles when WET and triples when SOAKED. "Feels like" is the air plus the rags (+2) plus a lit
lantern (+3, which the protagonist starts with). Telemetry counts a turn as cold when the body is
below 35 C, which is the Cold band. Chilly turns are not counted.

The table gives turns until the body reaches Cold, Freezing and Hypothermic, wearing rags only:

| Place | Air | Dry | WET | SOAKED |
|---|---|---|---|---|
| Maze, Fungal Forest (strata) | 18 | never | never | never |
| Ossuary | 12 | 2,500 / 3,750 / 5,625 | 1,250 / 1,875 / 2,812 | 833 / 1,250 / 1,875 |
| Flooded Halls | 6 | 357 / 536 / 804 | 179 / 268 / 402 | 119 / 179 / 268 |
| Forest, night, rain | 9 | 625 / 938 / 1,406 | 312 / 469 / 703 | 208 / 312 / 469 |
| Mountains, day | 4 | 278 / 417 / 625 | 139 / 208 / 312 | 93 / 139 / 208 |
| Tundra, day | -12 | 100 / 150 / 225 | 50 / 75 / 112 | 33 / 50 / 75 |

These numbers match the measured pacing in `docs/balance/0.0.2-targets.md`: Tundra day about 110,
wet Forest night about 310, wet Flooded Halls about 180.

- **A lit lantern:** roughly doubles the Flooded Halls times (Cold at 625 dry, 312 wet). It removes
  the cold entirely in the Ossuary and on a dry Lakelands night.
- **Where cold can reach the player:** levels 2-3 draw from `StratumMap.SHALLOW` (Maze, Maze, Fungal
  Forest, Flooded Halls). The Flooded Halls are therefore the only cold place in reach of the depths
  telemetry has seen. On the surface, cold comes from the Tundra, the Mountains and wet nights.
- **What cold does:** it never kills directly except through Hypothermic, at 1 HP every 10 turns.
  The real cost is the 10-20% slow and the loss of regen at Freezing.

## 2. Is water trivial and food scarce, or the reverse?

Neither is scarce. **Water is the more trivial of the two, except deep in the strata.**

**Water:**

- Every tile of water is an unlimited drink worth 200.
- Every surface chunk outside the Desert and the Tundra is given at least 4 water tiles
  (`WaterSupply.SURFACE_MIN_WATER`).
- Rain adds one drink to the skin every 20 turns.
- In the strata only 25% of water stays water, with a 10% chance of illness per draught. The rest
  becomes blood (70% fever), black muck (100% illness) or freezing slush (a 1 C chill).
- Even so, the starting skin alone covers about 1,000 turns.
- `Player#drinkLiquid` has **no fullness check.** Drinking at the 1,200 cap silently wastes the
  draught, still rolls the strata illness, and gives no "you're not thirsty" refusal. The only
  "hard time getting it down" message is keyed to the *hunger* stage.

**Food:**

- Rations and flour sacks are 50 of 617 item-spawn weight at level 1 (8.1%). Rations are worth 800;
  flour sacks are worth 150-250 and are eaten on pickup.
- Containers hold food at a weight of 2 in 85.
- Every kill leaves cookable parts, worth 25-300 each and scaled by size.
- The Altar can add provisions.
- Across 16 expeditions the player picked up 12 food items and ate 10, and was **never Hungry for
  a single turn**.

**Since #235 the binding meter over a long life is food, but only in principle.** Thirst no longer
outpaces hunger, and water refills for free. Nobody has yet played long enough for either to bite:
the longest life on record is about 1,640 turns.

**The 1.5-burn runs had a different story.** Both long runs died "while parched":

- **1792342213370:** 1,641 turns, 7 drinks, 320 turns thirsty.
- **1792378868620:** 650 turns, 0 drinks, 159 turns thirsty. That matches Thirsty at turn 501 almost
  exactly.

Thirst was the meter that bit. At 1.0 the one run since #235 shows the opposite failure: water used
as a free action with no limit.

## 3. What the telemetry cannot yet show

1. **n = 1.** Only `expedition_1792462865267.json` (10-19 22:21) was recorded after #235.
2. **No meter values.** Satiety, hydration, body temperature and wetness are never sampled.
   Telemetry records only turn counts past the first warning (Hungry, Thirsty, below 35 C). It can't
   say how close a run came to a threshold, or split Hungry from Weak or Thirsty from Dehydrated.
3. **No record of drinks:** not the liquid, not the source (skin or tile), and not whether the drink
   was wasted at the cap. Illness from drinking is mixed into `injuriesSustained`.
4. **Flour sacks are invisible.** They are eaten on pickup (`Player` around line 1466) before
   `recordFoodConsumed` or pickup counting, so `foodConsumed` undercounts.
5. **Cooked meals count twice.** A meal counts as both food and water (`feastOnMeal`), so
   `waterConsumed` mixes drinks and meals.
6. **No location.** Biome, stratum, weather, wetness and fire use aren't recorded, so `turnsCold = 0`
   can't be attributed. The same goes for encumbrance tier, heatstroke and fever time.
7. **`turnsLived` is not per life.** It is `TurnManager.turnCounter`, which never resets on death.
   For example, 1792378868620 reports `turnsLived` 1,236 for a life that began around turn 586.
   Grouping by life needs `lifeId`, which only schema 3 has (one file).
8. **Expedition files miss part of the clock.** The meters carry across expeditions within a life,
   and anything consumed in the Shelter lands in the next file. Per-expedition files therefore
   understate the hunger and thirst clock.

**Telemetry that would close these gaps:** per-life stage-entry turns for each meter, the minimum
satiety, hydration and body temperature, drinks broken down by liquid, source and wasted, flour
sacks counted as food, and turns spent per biome or stratum.
