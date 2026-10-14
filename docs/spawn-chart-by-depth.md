# What can actually spawn at each depth (develop @ dae38d64)

Research note for issue #157, part of map #149. It records facts only and makes no
balance recommendations. File and line references are to `develop` at `dae38d64`.
`J/` is `core/src/main/java/com/bpm/minotaur/` and `D/` is `assets/data/`.

## The short answer

On a new save, the world floor roll is the same narrow pool at every depth. It has
no body armour, no shields, no swords or bows, no rings, wands, gems or amulets, and
no gold. Every item that scores 50 or more is unlock-gated (the gate is 50 on
develop, not 310). Depth only changes the following:

- how many things spawn,
- the spell level of scrolls and books,
- the colour and affix tier of each piece,
- which containers appear and what treasure they hold,
- the monster level window.

All of these are keyed on the **Effective Difficulty Level (EDL)**, not on raw depth.

## Method

- Read the spawn path end to end: `WorldManager` -> chunk generator -> `SpawnManager`
  -> `ItemSpawner` / `MonsterSpawner` / `MonsterFactory`, then `UnlockManager`,
  `ItemDataManager`, `DivinityManager` and the JSON in `D/`.
- Ran a throwaway JUnit probe that was not committed. It loaded the real
  `ItemDataManager` (`load`, `loadWeapons` and `loadArmor`, in the same order as
  `J/Tarmin2.java:88-91`), the real `MonsterDataManager` and a fresh `UnlockManager`,
  then sampled the real code:
  - `ItemSpawner.spawnItem` 200,000 times at each of EDL 1, 2, 3, 4, 5, 6, 8, 10, 12, 15,
    20 and 25. Each sample went through the same post-filters as
    `SpawnManager.spawnItems`: shot pouch, firearm depth and the variant lookup.
  - `MonsterSpawner.spawnRandomMonster` 100,000 times for each depth / EDL / chunk
    scenario.
- The percentages below come from that sampling. Counts and budgets are read
  straight from code and JSON.
- Probe caveat: in about 2 of roughly 4 million `MonsterSpawner` calls, a
  `ClassCastException` came out of `TreeMap.put` (`J/generation/MonsterSpawner.java:159`).
  The failing iteration changed between runs that used identical seeds, and the
  bytecode at that call is a plain `put(Long, Entry)`. I treated it as a probe/JVM
  artefact, not a game behaviour, and did not chase it further.

## 1. Spawning is keyed on EDL, not depth

- Every chunk generator passes `spawnDifficulty` into `SpawnManager` as both `level`
  and `playerLevel`, for example `J/generation/MazeChunkGenerator.java:197,341-342`.
  `spawnDifficulty` is the EDL from `WorldManager.loadChunk`
  (`J/managers/WorldManager.java:560,676`).
- Inside `SpawnManager`, therefore, `level` is the EDL, and so is
  `SpawnContext.playerLevel`. `SpawnContext.depth` is the real depth, from
  `maze.getLevel()` (`J/managers/SpawnManager.java:316-333`).
- The EDL formula (`J/managers/WorldManager.java:441-458`):
  - `EL = depth + floor((|chunkX|+|chunkY|)/2)`
  - `EDL = max(1, floor((EL + playerLevel)/2) + difficultyOffset)`
  - Within `|x|,|y| <= 1` of the origin column, EDL is capped at 2 for depth <= 2
    and at 3 for depth 3.
- `difficultyOffset` is always 0. `resetWorldKeepDifficulty()`
  (`WorldManager.java:460-469`) is its only writer and has no callers.
- Periodic (doom) spawns use EDL plus the doom bonus of 0 to 3, every 300 / 200 / 120 /
  80 turns, and never at depth 1 (`WorldManager.java:1383-1397`,
  `J/managers/DoomManager.java:156-175`).

These things follow EDL: monster count, item / container / debris / mimic budgets, the
container type and loot windows, the monster level window, item colour variants,
affixes, spell level, the food shift, the firearm depth gate, the mimic gate and
Gehennom (`level >= 25`, `SpawnManager.java:317`).

These things follow real depth: the out-of-depth caps, the near-origin threat caps,
the key tier, milestone tomes, chronicle fragments, the merchant, and the Divinity
award amounts.

### Depth to EDL

| Depth | EDL, fresh character (P1) at origin column | EDL if player level = depth, at origin | EDL range for P1-6 and chunk distance 0-6 |
|---|---|---|---|
| 1 | 1 | 1 | 1-5 (2 inside the starter ring) |
| 2 | 1 | 2 | 1-5 (2 inside the ring) |
| 3 | 2 | 3 | 2-6 (3 inside the ring) |
| 4 | 2 | 4 | 2-6 |
| 5 | 3 | 5 | 3-7 |
| 6 | 3 | 6 | 3-7 |
| n | floor((n+1)/2) | n | floor((n+1)/2) .. floor((n+9)/2) |

## 2. Counts and budgets per EDL

Sources: `SpawnManager.java:201-227` and `D/spawntables.json:2-130`. The budget row is
looked up by EDL, and anything above EDL 15 falls back to `defaultBudget`
(`SpawnManager.java:134-137`).

| EDL | Monsters | Item attempts (`itemBudget x 1.2`) | Items placed, fresh save (~) | Containers (`x 0.5`) | Debris (`x 0.5`) | Mimic chests |
|---|---|---|---|---|---|---|
| 1 | 14 (**8** in chunk (0,0)) | 14 | 9.5 | 2 | 7 | 0 |
| 2 | 18 | 14 | 9.5 | 2 | 3 | 0 |
| 3 | 21 | 18 | 12.2 | 3 | 3 | 0 |
| 4 | 25 | 18 | 12.2 | 3 | 3 | 0 |
| 5 | 28 | 18 | 12.2 | 3 | 3 | 0 |
| 6 | 32 | 21 | 14.5 | 4 | 4 | 0 |
| 7 | 35 | 21 | 14.5 | 4 | 4 | 0 |
| 8 | 39 | 21 | 14.5 | 4 | 5 | 0-1 |
| 9 | 42 | 21 | 14.5 | 4 | 5 | 0-1 |
| 10 | 46 | 24 | 16.5 | 5 | 5 | 0-1 |
| 11-15 | 49-63 | 24 | 16.5 | 5 | 5-7 | 0-2 |
| 16-24 | 67-95 | **6** | 4.1 | **2** | 3 | 0-2 |
| 25+ (Gehennom) | 98+ | 6 | 3.0 | 2 | 3 | 0-2 |

How the columns are derived:

- **Monsters:** `round(14 + 3.5 x (EDL-1))`, capped by free floor tiles
  (`SpawnManager.java:204-211`). The 8-monster cap applies to chunk (0,0) whenever
  EDL <= 1, at any depth, because the check reads `this.level`, which is the EDL
  (`:206`).
- **`monsterBudget` in the JSON is dead.** It is not read for the count.
- **Items placed:** one attempt in three produces nothing on a fresh save, as section 3
  explains. About 68% of attempts place an item at EDL 1-24 and about 50% at EDL 25+,
  per the probe.
- **The loot-decay roll is disabled.** `DoomManager.getLootChanceMultiplier()` returns
  1.0 (`DoomManager.java:228-231`).
- **Mimic chests:** these exist only at EDL >= 8 (`J/generation/MimicSpawnRule.java:22,39-45`).
  The count is uniform over 0..`mimicBudget`, and they are separate from the MIMIC
  monster in section 4.
- **Above EDL 15:** `defaultBudget` sets item 5, container 4, debris 7 and mimic 2
  (`spawntables.json:124`), so item attempts drop from 24 to 6 there.

Extras that do not come from the budgets:

- **Milestone tomes**, only in chunk (0,0): depth 2 Tome of the Initiate, depth 4 Tome of
  Elements, depth 6 Tome of the Arcane, depth 8 Tome of Tarmin (`SpawnManager.java:267-288`).
- **Chronicle fragment**, 30% per chunk. At depth 1 it is a torn banner or herald
  proclamation. Deeper, it is a chronicle page 3 times in 4 and a proclamation 1 time in 4
  (`SpawnManager.java:259-265`).
- **Merchant:** one per level, on depths 1-2 only
  (`J/generation/MazeChunkGenerator.java:230-241`). The shop stocks fixed pools that
  include gated items such as SWORD, BOW and LEATHER_ARMOR
  (`J/gamedata/ShopInventory.java:23-53`).
- **Surface biome fauna:**
  - Desert adds 2-4 creatures from `monsterSpawnTable`, keyed by EDL
    (`J/generation/DesertChunkGenerator.java:231,741-800`).
  - Lakelands does the same, keyed by layout depth
    (`J/generation/LakelandsChunkGenerator.java:227,833-847`).
  - So `monsterSpawnTable` is not wholly dead, which corrects `docs/tuning_knobs.md`.
- **Blight** spawns supplies only. It brings its own roster and gets no generic monsters
  (`SpawnManager.java:234-239`, `J/generation/BlightChunkGenerator.java:555-558`).

## 3. Items: what the world roll can produce

### The pipeline

`ItemSpawner.spawnItem` (`J/generation/ItemSpawner.java:31-54`) works in two steps:

1. It picks a category by weight (`:163-228`). The default weights are FOOD 30,
   POTION 20, SCROLL 25, WAR_WEAPON 15, SPIRITUAL_WEAPON 15, ARMOR 20, WAND 15, TOOL 8,
   GEM 8, BOOK 8, RING 6, AMULET 2 and AMMUNITION 15. From EDL 6 the FOOD weight drops
   to x0.2, giving 6 (`:221-225`).
2. It picks a template within that category, weighted by `probability`, after skipping
   locked unlock-gated templates. Unlocked gated templates get x3 weight (`:249-293`).

Then `SpawnManager.spawnItems` applies its own filters:

- It drops SHOT_POUCH (`SpawnManager.java:512`).
- It applies the firearm depth gate, passing EDL (`:521`).
- It drops any template with no colour variant (`:532-534`).

### Gating facts that decide the pool

- **`probability: 0` in the JSON does not mean "never".** The loaders rewrite 0 to 10
  for every items, weapons and armor entry (`J/gamedata/item/ItemDataManager.java:80-81,
  222-223, 279-280`). Ammunition gets at least 15 and QUIVER at least 20. In the JSON,
  `probability` is absent or 0 on 251 of 266 weapons, 119 of 119 armour and 166 of 168
  items. **`docs/tuning_knobs.md` is wrong to say this keeps them from spawning.**
- **The unlock gate is a score >= 50**, not 310 (`J/managers/UnlockManager.java:36`;
  applied at `ItemDataManager.java:331-342`).
  - Score = `baseValue + 100 x AC (armour) + 50 x n x sides (weapon dice)`
    (`UnlockManager.java:148-165`).
  - AXE is exempt (`ItemDataManager.java:336`).
  - The comment at `J/gamedata/item/ItemTemplate.java:41` still says 310.
- **A gated item enters the world roll only once this save has unlocked it**
  (`UnlockManager.java:195-214`). It unlocks when:
  - it is picked up, added to inventory, or looted from a corpse, grave or container,
    or looked at on the ground (`J/gamedata/Inventory.java:28,79,237,260`,
    `J/rendering/Hud.java:2592`, `J/gamedata/player/Player.java:3288`, and the
    corpse/grave loot screens);
  - or it is one of the 1-2 random unlocks rolled at death
    (`J/screens/GameScreen.java:1480`, `UnlockManager.java:299-390`). That roll is
    limited to score <= `50 + 250 x strata`.
- **Categories that can never produce an item.** `isCategoryMatch` has no case for TOOL,
  AMULET or GOLD (`ItemSpawner.java:295-329`). On a fresh save RING, WAND, GEM and the
  non-dynamic SCROLL/BOOK have no ungated template either:
  - every ring scores >= 50;
  - the generated WAND_A..H / SCROLL_A..H templates keep probability 0
    (`ItemDataManager.java:98-138`).

  The result is that **about 21% of world item attempts return nothing (24% from EDL 6)**.
- **SCROLL and BOOK rolls ignore gating.** They become dynamic spell scrolls or books
  drawn from `spells.json` (`ItemSpawner.java:36-50, 77-150`):
  - spell level 1 at EDL <= 3, 2 at <= 6, 3 at <= 9, 4 at <= 12, 5 above that;
  - no spell is sealed, because `ShelterAltar.isSpellSealed` returns false
    (`J/gamedata/progression/ShelterAltar.java:542-544`).

### The fresh-save floor pool (probability > 0 and not gated)

This pool is identical at every depth. It amounts to 12 weapons, 29 armour pieces,
6 potions, 3 foods and 3 ammo types. The tables list what is in each group and how often
each group appears.

| Group | Members that can be placed | Share of placed items, EDL 1 | EDL 6-24 | EDL 25+ |
|---|---|---|---|---|
| Spell scroll (dynamic) | any unsealed spell of the EDL's spell level | 19.7% | 22.3% | 1.8% |
| Food | FOOD, MEAL, FLOUR_SACK | 14.2% | 3.2% | 1.1% |
| Armour piece | 25 placeable pieces, listed below | 13.7% | 15.4% | 32.3% |
| "Disk, Crossbow" | DISK_CROSSBOW, as SPIRITUAL_WEAPON (see slips) | 13.0% | 14.5% | 20.4% |
| Potion | POTION_BLUE / PINK / GREEN / GOLD / SWIRLY / BUBBLY | 11.8% | 13.3% | 1.5% |
| Other weapon | AXE plus 8 more, listed below | 10.7% | 12.0% | 16.7% |
| Ammo | QUIVER, ARROW_WAR, QUARREL_HEAVY | 10.5% | 12.0% | 16.8% |
| Spellbook (dynamic) | as for scrolls | 6.3% | 7.2% | 9.3% |

The 25 placeable armour pieces:

- **Generic:** GAUNTLETS, HELMET, BOOTS, LEGS, ARMS, EYES.
- **Helms:** BASINET, GREAT_HELM, LEATHER_HELM, HIDE_HELMET, PADDED_HELMET,
  WOOD_BONE_HELMET, STUDDED_LEATHER_HELMET.
- **Boots:** LEATHER_BOOTS, WOOD_BONE_BOOTS, STUDDED_LEATHER_BOOTS, HIDE_BOOTS,
  LIGHT_SCALE_BOOTS.
- **Leggings:** HIDE_LEGGINGS, LEATHER_LEGGINGS, STUDDED_LEATHER_LEGGINGS,
  PADDED_LEGGINGS, WOOD_BONE_LEGGINGS.
- **Gauntlets:** STUDDED_LEATHER_GAUNTLETS, LIGHT_SCALE_GAUNTLETS.
- Four `*_ARMS` pieces are ungated but have no variants, so they are never placed.
- None of the 25 is a body piece or a shield.

The 8 other weapons alongside AXE are FLAIL_GRAIN, HATCHET_HAWK, LANCE_FLIGHT,
LANCE_HEAVY_HORSE1, LANCE_MEDIUM_HORSE1, MACE_BLADEBACK, MORNING_STAR_DOUBLE_BALL and
POLEARM_SPETUM3.

What is never on the floor of a fresh save:

- every body armour and every shield;
- 241 of 253 weapons, including every sword, bow, dagger and firearm;
- every ring, wand, gem and amulet;
- gold.

Firearms are gated by score (510-810) before `FirearmSpawnRule` is reached. Once one is
unlocked, its minimum (2 / 2 / 6 / 8 / 10, `J/gamedata/firearm/FirearmProfile.java:56-60`)
is compared against the **EDL**, not the depth.

Item colour and affixes come from each template's `variants` window at the EDL, where
any variant with a multiplier above 1.4 is skipped below EDL 5
(`ItemDataManager.java:922-960`), and from `attemptToModifyItem`
(`SpawnManager.java:710-770`).

### Containers and their contents

**Container type** comes from `containerSpawnTable`, filtered by EDL
(`SpawnManager.java:140-145`, `spawntables.json:2775`):

| EDL | Container types |
|---|---|
| 1-3 | SMALL_BAG 12, BOX 10 |
| 4-5 | adds MEDIUM_PACK 8 |
| 6-7 | BOX, MEDIUM_PACK, LARGE_BAG 6 |
| 8-10 | adds REGULAR_CHEST 4 |
| 11-14 | MEDIUM_PACK, LARGE_BAG, REGULAR_CHEST |
| 15-18 | LARGE_BAG, REGULAR_CHEST |
| 19+ | REGULAR_CHEST only |

**Contents:** exactly one item, from `containerLoot.default` filtered by EDL
(`SpawnManager.java:646-672`, `spawntables.json:3060`). This path ignores unlock gating
and does not use `ItemSpawner`. The `ItemSpawner` container weights at
`ItemSpawner.java:166-183` are dead, because `isContainer` is always false
(`SpawnManager.java:318`).

| EDL | Possible contents (weight) | Pool total | Empty chance | Gold per container (expected) |
|---|---|---|---|---|
| 1 | FOOD 25, COINS 20, QUIVER 16, QUARREL_LIGHT 12, ARROW_FLIGHT 12, SCROLL 8, LAMP 8, TINDER_BUNDLE 8 (yields nothing) | 109 | 7.3% | 5.5 |
| 2 | adds WAND 6, SHOT_POUCH 8 | 123 | 6.5% | 4.9 |
| 3-4 | adds CHALICE 10 | 133 | 6.0% | 8.3 |
| 5-7 | adds NECKLACE 6 | 139 | 5.8% | 11.2 |
| 8-11 | adds INGOT 4 | 143 | 5.6% | 13.6 |
| 12+ | adds CROWN 2 | 145 | 5.5% | 15.5 |

**Keys:** a locked container also spawns a key. The key tier follows real depth
(`SpawnManager.java:674-708`):

| Depth | Key odds |
|---|---|
| < 3 | silver 25%, iron 75% |
| 3-5 | skeleton 3%, silver 57%, iron 40% |
| >= 6 | skeleton 10%, gold 55%, silver 35% |

## 4. Monsters

### The pool

- **Choice:** weight = `frequency` + 5 if the alignment matches + 1 if
  `baseLevel <= 2 x playerLevel` (`MonsterSpawner.java:138-149`).
  - The level alignment is hard-coded NEUTRAL (`SpawnManager.java:316`), so every
    neutral monster gets +5.
  - `SpawnContext.playerLevel` is the EDL, so the +1 nearly always applies.
  - The 30 monsters with no `frequency` key (KOBOLD, GOBLIN, OGRE, TROLL, LICH ...)
    therefore weigh 6. A frequency-10 monster (ant, spider, skeleton) weighs 16.
  - CHAOTIC and LAWFUL monsters (AGIS, ORC, DWARF, DEMON_SLIME, FALL_ANGEL,
    BRINGER_OF_DEATH) miss the +5.
- **Window:** `baseLevel` runs from `min(14, max(1, EDL/4))` to EDL
  (`MonsterSpawner.java:84-91`).
- **Out of depth (OOD):** below the surface, there is a 15% chance per spawn of raising
  the cap, and an OOD monster's weight is divided by 3 (`:51-64, 153-155`).

| Depth | OOD cap |
|---|---|
| 1 (surface) | no OOD |
| 2 | `min(3, EDL+1)` |
| 3 | `min(4, EDL+1)` |
| >= 4 | `EDL + 1..3` |

- **Near-origin caps**, applied within `|x|,|y| <= 2` (`MonsterSpawner.java:95-123`):

| Depth | Cap |
|---|---|
| 1 | baseLevel <= 2 and MCR <= 100 |
| 2 | baseLevel <= 3 and MCR <= 110 |
| 3 | baseLevel <= 4 and MCR <= 160 |

- **Flags:** none of the 60 monsters in `D/monsters.json` sets `generationFlags`. That
  means no uniques, no hell-only monsters and no excluded monsters. The MIMIC monster
  (baseLevel 3, weight 6) is in the ordinary random pool from EDL 3.

### Per-depth pool (sampled)

"Depth", "EDL" and "chunk" give the scenario; the remaining columns describe the pool.

| Depth | EDL | Chunk | Species | baseLevel seen (>=0.05%) | Mean baseLevel | Level-1 share | Divinity per kill (mean) |
|---|---|---|---|---|---|---|---|
| 1 | 1 | any | 7 | 1 | 1.0 | 100% | 1.0 |
| 1 | 2 | any | 13 | 1-2 | 1.37 | 63% | 1.0 |
| 1 | 3 | far | 23 | 1-3 | 1.98 | 39% | 1.38 |
| 1 | 4 | far | 28 | 1-4 | 2.29 | 34% | 1.47 |
| 2 | 1 | origin | 13 | 1-2 (OOD) | 1.02 | 98% | 1.02 |
| 2 | 2 | any | 23 | 1-3 (OOD) | 1.41 | 62% | 1.38 |
| 3 | 2 | origin | 23 | 1-3 | 1.40 | 62% | 2.02 |
| 3 | 3 | any | 28 | 1-4 | 2.00 | 39% | 2.38 |
| 4 | 2 | origin | 34 | 1-5 | 1.43 | 61% | 2.40 |
| 4 | 4 | origin | 42 | 1-6 | 2.33 | 33% | 2.83 |
| 4 | 6 | far | 48 | 1-8 | 3.14 | 25% | 3.26 |
| 5 | 3 | origin | 40 | 1-6 | 2.03 | 39% | 3.40 |
| 5 | 5 | origin | 45 | 1-7 | 2.73 | 28% | 3.72 |
| 6 | 3 | origin | 40 | 1-6 | 2.03 | 39% | 3.63 |
| 6 | 6 | origin | 48 | 1-8 | 3.15 | 25% | 4.27 |
| 6 | 8 | far | 45 | 2-10 | 4.30 | 0% | 4.91 |
| 8 | 8 | origin | 45 | 2-10 | 4.30 | 0% | 5.91 |
| 10 | 10 | origin | 47 | 2-12 | 4.85 | 0% | 7.18 |
| 15 | 15 | origin | 47 | 3-15 | 6.31 | 0% | 10.45 |
| 20 | 20 | origin | 32 | 5-18 | 8.49 | 0% | 13.99 |
| 25 | 25 | origin | 26 | 6-18 | 9.46 | 0% | 16.91 |

Notes on the sampled pool:

- **Species by level:**
  - Level 1: BAT, GIANT_ANT, GIANT_SNAKE, GOBLIN, KOBOLD, SKELETON, SPIDER.
  - Level 2 adds GHOUL, GIANT_BEE, GIANT_SNAIL, HOBGOBLIN, TROGLODYTE, ZOMBIE.
  - Level 3 adds AGIS, CLOAKED_SKELETON, GELATINOUS_CUBE, GHAST, GIANT_CENTIPEDE,
    GIANT_SCORPION, MIMIC, ORC, RUST_MONSTER, WERERAT.
- **OOD monsters are rare:** 0.4-3% of spawns, each OOD species about 0.1%.
- **The pool is wide and flat:** at EDL 6, 48 species are possible and the most common
  is 4.7%.
- **Level-1 monsters vanish at EDL 8,** because the lower bound becomes 2.
- **How it extrapolates:**
  - The window is `[EDL/4, EDL]`.
  - The count grows by 3.5 per EDL.
  - The mean baseLevel climbs slowly (about 0.35 per EDL), because low-level, high-weight
    species stay in the window until EDL reaches 4 x their level.
- **Monster scaling:** monster stats scale with EDL through `MonsterFactory` /
  `Monster.scaleStats` (see `docs/tuning_knobs.md` section 2).

### Monster inventory

Source: `J/generation/MonsterFactory.java:62-101`.

- **Weapon users** (ORC, GOBLIN, KOBOLD, DWARF, SKELETON, ZOMBIE, GIANT ...) draw a
  WAR_WEAPON through the same gated `ItemSpawner`, and armour 1 time in 2. On a fresh save
  that means one of the 10 ungated war weapons and one of the 29 ungated armour pieces.
- **INT >= 8:** a potion 1 time in 6 (one of the 6 ungated colours), and a wand 1 time in
  10. The wand roll always comes back empty, because the WAND category has no
  ungated template.
- **INT > 5:** `goldAmount = d(EDL,10) + EDL` is computed and never used. The GOLD
  category it then asks for returns null (`:92-100`), so **monsters never carry gold**.

## 5. Gold sources

Gold is `PlayerStats.treasureScore`.

| Source | Where | Magnitude |
|---|---|---|
| Picking up TREASURE items | `J/gamedata/player/Player.java:1431-1433, 1476-1478` (`baseValue`, x trait `foundTreasureMult`) | COINS 10, LAMP 50, CHALICE 50, NECKLACE 75, INGOT 100, CROWN 150 |
| Containers, the only spawn source of treasure | section 3 | about 11 / 25 / 34 / 45 / 55 / 68 / 78 per level at EDL 1-2 / 3-4 / 5 / 6-7 / 8-9 / 10-11 / 12-15; about 31 at EDL 16+ |
| World floor roll | none | 0. There is no GOLD or TREASURE category in the default roll |
| Monster drops | none | 0 (section 4) |
| Selling to the merchant (depths 1-2) | `J/rendering/ShopkeeperWindow.java:344`, `BUYBACK` 0.5 (`ShopInventory.java:56`) | half of `baseValue` |
| Gem exchange | `ShopkeeperWindow.java:373` | gem values |
| Encounter events | `J/gamedata/events/EventResolver.java:240-241` | per event |
| House contracts | `J/screens/GameScreen.java:2461-2464` | `contract.coin()` |
| GOLD die face in combat | `J/managers/CombatManager.java:1958-1959` | the face value |

## 6. Divinity sources

Divinity is meta-currency that is never lost on death.

| Source | Where | Amount |
|---|---|---|
| Kill | `DivinityManager.java:73-79`, called from `CombatManager.java:1162, 3641` with real depth | `max(1, (baseLevel + depth)/2)`, x trait `divinityMult` plus `divinityKillAdd` |
| New chunk, once per chunk per world | `DivinityManager.java:61-68`, `GameScreen.java:545-552` | `max(1, depth)` |
| Altar sacrifice | `ShelterAltar.java:419-446` | GIB_* 1; by colour TAN 1, GRAY 2, GREEN 4 + 2 per modifier, BLUE 8, GOLD 15, others 1 |

Magnitudes per chunk if every generated monster is killed (count x mean per kill):

| Depth | EDL | Chunk | Divinity |
|---|---|---|---|
| 1 | 1 | starter chunk | 8 |
| 1 | 1 | other chunk | 14 |
| 2 | 2 | | 25 |
| 3 | 3 | | 50 |
| 4 | 4 | | 71 |
| 5 | 5 | | 104 |
| 6 | 6 | | 137 |
| 8 | 8 | | 230 |
| 10 | 10 | | 330 |

Add `depth` for entering a new chunk. Periodic doom spawns add more kills at depth > 1.

## 7. Data slips and code quirks found

1. **Dragon `baseExperience` 50** (`D/monsters.json:632`) at baseLevel 10. Other
   baseExperience values that look out of line with their level:

   | Monster | baseLevel | baseExperience |
   |---|---|---|
   | WYVERN | 9 | 220 |
   | CHIMERA | 9 | 300 |
   | MINOTAUR | 15 | 100 |
   | LICH | 15 | 300 |
   | GHAST | 3 | 200 |
   | MIMIC | 3 | 150 |
   | GELATINOUS_CUBE | 3 | 120 |
   | ORC | 3 | 20 |
2. **The unlock gate is 50, not 310.** The ticket premise and the
   `ItemTemplate.java:41` comment are stale (`UnlockManager.java:29-36`).
3. **`probability: 0` is rewritten to 10 at load** (`ItemDataManager.java:80-81,
   222-223, 279-280`). `docs/tuning_knobs.md` ("raise probability to make an item
   findable") describes a knob that does not gate anything.
4. **Weapon dice with a modifier break the score parse.** `"1d6+1".split("d")[1]` is
   `"6+1"`, so `parseInt` fails and only `baseValue` counts (`UnlockManager.java:154-161`).
   That is why 11 weapons are ungated with a score of 10: ARROW_WAR, DISK_CROSSBOW,
   FLAIL_GRAIN, HATCHET_HAWK, the 3 LANCE_*, MACE_BLADEBACK, MORNING_STAR_DOUBLE_BALL,
   POLEARM_SPETUM3 and QUARREL_HEAVY.
5. **DISK_CROSSBOW is the only SPIRITUAL_WEAPON on a fresh save.** The name match
   `friendlyName.contains("Cross")` (`ItemSpawner.java:310-312`) catches "Disk,
   Crossbow", so it takes the whole SPIRITUAL weight of 15. That makes it about 13-15% of
   all floor items, the single most common item after spell scrolls.
6. **TOOL, AMULET and GOLD have no case in `isCategoryMatch`** (`ItemSpawner.java:295-329`),
   so those category rolls always return null, from the world roll and from
   `MonsterFactory` alike.
7. **The `ItemSpawner` container weights are dead.** `createSpawnContext()` always passes
   `isContainer=false` (`SpawnManager.java:318`).
8. **TINDER_BUNDLE has no `variants`** (`D/items.json:6446`), so its 8-weight
   container-loot roll leaves the container empty (`SpawnManager.java:664-666`). Other
   ungated templates with no variants, which are never placed: HIDE_ARMS, LEATHER_ARMS,
   STUDDED_LEATHER_ARMS, WOOD_BONE_ARMS, MEAT, COOKED_MEAT and POTION_CLIMBING.
9. **COOKED_MEAT is flagged `isPotion`,** so it is a POTION-category candidate (with no
   variants, so it is never placed).
10. **`D/weapons.json:16825` defines a `"SCROLL"` entry.** It overrides `D/items.json:104`,
    because weapons load after items: baseValue goes from 50 to 20 and colour variants
    are added.
11. **16 `weapons.json` keys use an `_S` apostrophe form that has no `ItemType`**
    (BARD_S_FRIEND vs BARDS_FRIEND, MACE_FOOTMAN_S vs MACE_FOOTMANS ...), so they are
    never loaded. The ItemTypes of the same names get a stub template with probability 0.
    MACE_FOOTMANS and FLAIL_FOOTMANS are in the shop `WEAPON_POOL`.
12. **`defaultBudget.itemBudget` is 5,** against 20 at EDL 15, so item attempts fall from
    24 to 6 past EDL 15 (`spawntables.json:124`).
13. **`difficultyOffset` is always 0.** `resetWorldKeepDifficulty` has no callers.
14. **`FirearmSpawnRule` receives EDL, not depth** (`SpawnManager.java:521`), and
    `MimicSpawnRule` does the same (`:615`).
15. **`monsterSpawnTable` is read** by the Desert and Lakelands fauna. `docs/tuning_knobs.md`
    lists it as dead. `itemSpawnTable` is still unread.
16. **No monster sets `generationFlags`,** so the G_UNIQ / G_HELL / G_NOHELL / G_NOGEN logic
    in `MonsterSpawner` never fires, and MIMIC spawns as an ordinary monster.
17. **`MonsterFactory.isUserOfWeapons` matches by substring** (`MonsterFactory.java:105-114`).
    `"GIANT"` also matches GIANT_ANT, GIANT_BEE, GIANT_SNAKE, GIANT_SNAIL, GIANT_SCORPION
    and GIANT_CENTIPEDE, so those are rolled a weapon, and armour 1 time in 2.

## Sources

- Code: `J/generation/ItemSpawner.java`, `J/generation/MonsterSpawner.java`,
  `J/generation/SpawnContext.java`, `J/generation/MonsterFactory.java`,
  `J/generation/FirearmSpawnRule.java`, `J/generation/MimicSpawnRule.java`,
  `J/managers/SpawnManager.java`, `J/managers/WorldManager.java`,
  `J/managers/UnlockManager.java`, `J/managers/DivinityManager.java`,
  `J/managers/DoomManager.java`, `J/gamedata/item/ItemDataManager.java`,
  `J/gamedata/progression/ShelterAltar.java`, `J/gamedata/ShopInventory.java`,
  `J/generation/MazeChunkGenerator.java`, `J/generation/DesertChunkGenerator.java`,
  `J/generation/LakelandsChunkGenerator.java`.
- Data: `D/spawntables.json`, `D/items.json`, `D/weapons.json`, `D/armor.json`,
  `D/monsters.json`.
- Secondary source, checked against the code above: `docs/tuning_knobs.md`.
