# How often an Incarnation lands qualifying hits

Research for [#223](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/223), part of the map "Wayfinder: Combat Arts & Weapon Mastery" ([#221](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/221)). Terms are defined in `CONTEXT.md`: Incarnation (Life), Expedition, Mastery, Weapon Family, Fodder / Soldier / Heavy.

**Question.** How many hits on non-Trivial monsters does an Incarnation land, per expedition and per life, by depth and by attack mode? What scale does that set for the Mastery thresholds?

**Short answer.**
- A median life today lands **5-12 qualifying hits**. That is 6 in the 0.0.2-era files and 12 in the larger pre-0.0.2 archive. A good life (p75) lands 24-28, and only about 1 life in 10 passes 60.
- The Trivial rule has **never fired** in the recorded data: no player got past level 2.
- Nearly every hit is melee. Ranged hits appear in one life, at about 30% of its hits.
- Proposed per-Family scale, counting full hits only: **Novice 15, Adept 50, Expert 150, Master 400**. Under this scale a median life reaches Novice or nothing. A life that reaches depth 3 reaches Adept. Expert matches the on-pace level-5, depth 3-4 milestone.

## Sources

| Source | What it is | Used for |
|---|---|---|
| `C:/workspace/Tarmin2/logs/telemetry/expedition_*.json` (git-ignored, main checkout; copied before reading) | Schema 2, one file per expedition, with `fights`, `attacksByMode`, `levelUpTurns` and `lifeId` | 0.0.2-era rates, by depth and mode |
| `.../telemetry/archive-bot/` | 1 file, `cheatsUsed: true` | Excluded |
| `.../telemetry/archive-pre-0.0.2/run_*.json` | 301 files, old per-**life** schema: `attacksHit`, `attacksGlanced`, `strataReached`, `turnsLived` and kills. No fights, no modes, no difficulty, no cheat flag | Per-life totals by deepest stratum |
| `core/.../gamedata/player/Vengeance.java` | `roleOf` / `roleByLevel`: a monster is TRIVIAL when `monsterLevel - playerLevel <= -3` (`TRIVIAL_LEVELS_BELOW = 3`) | Definition of "qualifying" |
| `core/.../telemetry/TelemetryManager.java` | How fields are recorded | Caveats (below) |
| `lwjgl3/.../DuelSimulationLauncher.java` (`./gradlew :lwjgl3:runDuelSimulation`) | Real combat code; Father's Sword (RUSTY_SWORD), MEDIUM, unarmoured | Hits per fight at matched level, depths 1, 3 and 5 |
| `docs/balance/0.0.2-targets.md` | Swings-to-kill, hit rate, kills per level, level milestones | On-pace projection |

**Which files were used.** The 0.0.2-era set is the 12 schema-2 files in the telemetry root with `cheatsUsed: false`, all on MEDIUM. Nine are `build: 0.0.2-SNAPSHOT` and three are `0.0.3-SNAPSHOT`. The older set is the 301 pre-0.0.2 life files; I used the 270 that made at least one attack.

## How "qualifying" was computed

A hit qualifies if its target is not Trivial: `monster.level > playerLevel - 3`, as in `Vengeance.roleOf`. Fights carry the monster's `level`. Player level is not recorded, so I estimated it as `1 + (level-ups in levelUpTurns at or before the fight's openTurn)`. Every lifeId appears in only one file, so no earlier expedition of the same life could be counted.

**Result:** 0 of 50 fights were Trivial. Player level was 1 everywhere except one life, which reached level 2 at turn 1333. Monster levels ranged from 1 to 5. Trivial needs a player of level 4 or higher meeting level-1 monsters, and no recorded life got there. So for today's data, **qualifying hits = all hits**. The filter will start to matter once Mastery lives go deeper, or when a strong body goes back to depth 1.

**Hit vs glancing.** CONTEXT.md and the balance targets both say a Glancing Blow is not a hit. Figures below give full hits first and hits+glancing second. Note that `fights[].playerHits` counts glancing blows as hits (`TelemetryManager.recordPlayerAttack`: `type != MISS`), while `attacksByMode` keeps them apart.

## Per expedition and per life

0.0.2-era, 12 expeditions. Each file is a different life, so per-expedition and per-life are the same here. No file is a `returned` expedition: 10 died and 2 were abandoned.

| Set | Full hits: median [p25-p75] | Hits+glancing | Kills | Turns |
|---|---|---|---|---|
| all 12 | **6** [4.75-9], max 61 | 6.5 [5-10.5], max 80 | 2 [2-2.5] | 228 [149-511] |
| 0.0.2-SNAPSHOT (9) | 5 [4-7], max 28 | 6 [5-7] | 2 [2-2] | 215 [162-253] |
| 0.0.3-SNAPSHOT (3) | 9 [7-35], max 61 | 10 [7.5-45] | 2 [2-8.5] | 610 |

Pre-0.0.2 archive, 270 lives with an attack, per life:

| Deepest stratum | n | Full hits: median [p25-p75] | Hits+glancing |
|---|---|---|---|
| all | 270 | **12** [6-23.75], mean 23, max 404 | 14 [7-28] |
| 1 | 199 | 9 [4.5-15] | 11 [5-19] |
| 2 | 49 | 23 [15-39] | 28 [19-48] |
| 3 | 16 | 47.5 [39-77] | 62 [45-95] |
| 4 | 3 | 85 [74.5-166] | 106 |
| 5 | 1 | 121 | 146 |
| 6 | 2 | 171, 191 | 213, 245 |

Percentiles of hits+glancing per life: p50 14, p75 28, p90 62, p95 96.

## By depth (0.0.2-era fights, n = 50)

| Depth | Fights | Monster levels | Hits per fight: median [p25-p75] | Attacks per fight | Total hits | Outcomes |
|---|---|---|---|---|---|---|
| 1 | 31 | 1 (30), 5 (1) | 2 [2-3] | 3 [2-4] | ~74 | 23 killed, 7 player died, 1 disengaged |
| 2 | 11 | 1-2 | 3 [2-5] | 4 [2.5-5] | ~47 | 9 killed, 2 died |
| 3 | 8 | 3-5 | 6 [5-7] | 7 [6.5-7.75] | ~51 | 8 killed |

In fights the player won, the median is 3 hits per kill [2-5]. (Fight hits include glancing blows.)

Duel simulator, matched level (`--minLevel=--maxLevel=--edl=--playerLevel=L`), Father's Sword, MEDIUM:

| Band | Win % | Swings to kill | Full-hit % | Glancing % | Full hits per kill (swings x hit%) |
|---|---|---|---|---|---|
| L1 / EDL 1 (1000 duels each) | 95.2% | 4 | 70.2% | 12.0% | ~2.8 |
| L3 / EDL 3 (300 each) | 76.5% | 6 | 76.2% | 10.8% | ~4.6 |
| L5 / EDL 5 (300 each) | 27.8% | 8 | 72.5% | 12.0% | ~5.8 |

The depth-3 and depth-5 win rates are far below the 90% target. With the Father's Sword alone, deep fights are lost, which matches the map's "Mastery pays for the deeper-depth bands". The depth-1 and depth-3 runs used v0.0.2 code; the depth-5 run recompiled against `develop` at `fcb01be6`.

## By attack mode

Totals over the 12 files, full hit / glancing / miss:

| Mode | Hit | Glancing | Miss | Share of hits+glancing |
|---|---|---|---|---|
| melee | 128 | 21 | 34 | 86% |
| ranged | 17 | 7 | 3 | 14% |
| thrown, firearm, spell, book | 0 | 0 | 0 | 0% |

All the ranged hits come from one life, `56d8f6ab` (0.0.3, depth 3, 80 hits). In that life ranged was about 30% of hits, used alongside melee in 6 of its 8 depth-3 fights. The archive has no mode split.

**Approximating Weapon Family.** No weapon is recorded, so the most the data allows is melee ~ {Blade, Dagger, Axe, Blunt, Polearm, Unarmed} and ranged ~ {Bow, Crossbow}. Every 0.0.2-era life starts with the Father's Sword (Blade), and most never change weapon, so in practice nearly all recorded melee hits are probably Blade. Treat that as an assumption.

## What this implies for thresholds

Mastery belongs to the Incarnation, so **per-life** numbers set the scale. Within a life, hits are split across the Families used.

On-pace projection from `0.0.2-targets.md`: 8-10 on-level kills per level, level 5 around depth 3-4. Hits per kill come from the simulator: ~2.8 at L1, ~3.5 at L2, ~4.6 at L3, ~5.2 at L4.

| Point in a life | Cumulative full hits (all Families) |
|---|---|
| Level 2 (first expedition, ~9 kills at depth 1) | ~25 |
| Level 3 (depth ~2) | ~57 |
| Level 4 (depth ~3) | ~100 |
| Level 5 (depth 3-4) | ~145 |

Lives actually played run below this pace. The archive's stratum-3 lives median 48 hits and its stratum-4 lives 85, because they die before they clear levels.

**Proposed per-Family scale (full hits on non-Trivial monsters):**

| Rank | Hits | Who gets there |
|---|---|---|
| Novice | 15 | A good first expedition: 41% of archived lives get there (60% reach 10). The 0.0.2 median life (6) does not. |
| Adept (first Art) | 50 | A life that reaches depth 3. The archive's stratum-3 median is 48; on-pace this is about level 3. 10% of archived lives. |
| Expert | 150 | The on-pace level-5, depth 3-4 milestone. Only the archive's deepest lives (2%). |
| Master | 400 | Endgame for a one-Family life. Only one archived life (404) ever landed that many hits in total. |

Each rank costs about three times the last, which follows how hits per kill and kills per level both grow with depth.

**By design under this scale:** the median life reaches Novice at best, and never an Art. So a fresh body fights at today's numbers, as the map requires, and Adept is the reward for a life that survives into the depth-3 band, where the simulator shows the Father's Sword failing. If the user would rather a median life always gain *something*, lower Novice to about 8-10 (the 0.0.2 p75 / archive median). Moving Novice alone does not upset the Art ranks.

**Things to settle:**
1. Do glancing blows train Mastery? CONTEXT says Mastery is raised by "landing blows", and glancing adds about 15-20% more blows. Counting them would lower the effective thresholds by the same amount.
2. Splitting Families: a melee+bow life spends about 30% of its hits on the bow, so hybrid builds reach Adept later in both. That may be intended, as specialisation pressure.
3. Hits from a Thrown weapon are logged as THROWN mode. Whether that trains the Thrown Family or the weapon's own Family is a question for the Family spec.

## Telemetry to add (per Family)

At `CombatManager.modeOf(weapon)` (around line 3635) the weapon is already in hand, so the Family can be recorded beside the mode at little cost.

1. **Expedition `hitsByFamily`**: `{family: {hit, glancing, miss, qualifying}}`, like `attacksByMode`. Here `qualifying` counts hits on non-Trivial monsters.
2. **Fight fields**: `families` (a set, like `modes`), `playerLevel` at open, `role` (TRIVIAL / FODDER / SOLDIER / HEAVY from `Vengeance.roleOf`), and `playerGlancing` kept apart from `playerHits`, which today includes glancing.
3. **Expedition `playerLevelStart`**, so the Trivial check does not depend on reconstructing the level from `levelUpTurns`.
4. **Life-cumulative Mastery**: `masteryByFamily` at expedition start and end (qualifying hits and rank). Then a life's progress survives a missing or unlogged earlier expedition. Several of today's files have `turnsLived` well above `turns` and no earlier file for that lifeId.
5. **`masteryRankUps`**: `[{family, rank, turn, depth, playerLevel}]`, to measure "who reaches Adept" directly.
6. **Arts**: `artUses` (Active, by Art) and `artProcs` (Passive, by Art). Damage from Arts goes separately in `damageByArt`.
7. **`aggregate.py`**: report qualifying hits per life by Family and rank-up depth, grouping files by `lifeId`.

## Caveats

- **Thin 0.0.2 data.** There are 12 lives, all dead or abandoned, almost all at depths 1-2. The proposed thresholds lean on the pre-0.0.2 archive. That archive predates the 0.0.2 combat retune, has no difficulty or cheat flag (some debug lives may be in it, such as the 404-hit outlier), and counts every hit with no level data, so "qualifying" there is assumed rather than checked.
- **Trivial is untested.** Since no life passed level 2, these figures say nothing about how much the Trivial rule will cut once Mastery carries players deeper. Re-measure after the telemetry above lands.
- **The simulator is Father's-Sword-only.** Better weapons kill faster, so they produce fewer hits per kill. Per-kill hit counts are an upper end for a well-equipped body.
- Thrown hits record only HIT/MISS, never glancing (`CombatManager` ~line 1508).
