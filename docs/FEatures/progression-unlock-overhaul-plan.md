# Implementation plan: Progression / Unlock overhaul

**Status:** designed, not implemented
**Origin:** `docs/polish/polish_9_17_26.md` — *"We need to overhaul and re-implement the Progression /
Unlock system. Ideally every run unlocks at least 1 new item which can spawn in the dungeon in future
runs... It should feed into the roguelike nature of the game to encourage future runs even if they end
early."*

---

## 0. Read this first: there is a live data-loss bug

`UnlockManager` writes to `saves/profile.json` (`UnlockManager.java:11`). So does `SaveManager`
(`SaveManager.java:31`, `PROFILE_FILE`). Two managers serialise two unrelated classes to the same path.

**UnlockManager currently wins.** The file on disk holds `UnlockData`, not `ProfileData`, so every one of
these is silently reset to defaults on each unlock save:

- `classicModeUnlocked` — earned by slaying the Minotaur, and therefore **unearnable in practice**
- `lastPlayedSlot`, `totalRunsAttempted`, `totalRunsWon`, `totalMinotaursSlain`, `highestTormentCleared`

This is independent of any redesign. **Fix it first, in its own commit** (decision Q5), before any of the
work below. It is a path constant plus a migration.

---

## 1. What exists today

The system runs, persists, and reaches nothing.

**It tracks and evaluates.** `UnlockManager` accumulates four counters — `totalSteps`,
`totalDoorsOpened`, `deepestLevelReached`, and per-type `monsterKills` — generates an "unlock plan" at
runtime, and evaluates thresholds. The live save shows 643 steps, 24 doors, depth 3, 26 kills, and
`WAND` + `LARGE_LIGHTNING` already "unlocked".

**But nothing is gated.** Both enforcement sites are dead:

| Site | Why it's dead |
|---|---|
| `ItemSpawner.java:209-218` | The `t.probability > 0` guard runs **before** the `t.locked` check, and all four locked weapons have `probability: 0`. The branch is unreachable. |
| `ItemDataManager.java:381-389` | Gates on `template.unlockId`, and **every `unlockId` in every data file is null**. |

`WAND` additionally spawns through `spawntables.json`, which never consults `UnlockManager` at all.

**The two schemes don't even match.** The plan and `unlockedContent` are keyed on `ItemType` names;
`ItemDataManager` gates on `unlockId`. They are incompatible as written.

**The criteria are random and unseeded.** `generateUnlockPlan` picks
`GateType.values()[rng.nextInt(4)]` from a bare `new Random()`. Nothing is authored anywhere.

**Content inventory:** 266 weapons, 118 armour, 153 items (537 in the categories in scope), plus 370
spells, 48 monsters, 15 encounters. Exactly 4 entries carry `locked: true` — `SWORD`,
`TWO_HANDED_SWORD`, `WAND`, `LARGE_LIGHTNING` — and all 4 gates are non-functional. **Effectively 0
gated, 905 free.**

**The death screen lies.** `PlayerDeathScreen.java:155` shows a hardcoded "Progression Note" claiming
unlocks happen. It has no data behind it. The only real unlock UI is `GameOverScreen.java:274-296`,
reachable **only after 50 deaths**.

---

## 2. Decisions

| # | Decision |
|---|---|
| **Q1/Q9** | Tiered lock: items scoring **≥ 310** start locked — 135 of 537. Score is `baseValue + armorClassBonus×100 + (numDice × dieSize × 50)`, already implemented as `UnlockManager.calculateItemScore` (`:168`). **`AXE` is exempt** (see §5). |
| **Q2/Q10** | Hybrid earning: **1 guaranteed** unlock per run, plus milestones — new deepest strata, 25+ kills, 50+ divinities, 300+ turns survived. **Cap 3 per run.** |
| **Q3/Q11** | The unlock is **drawn at random**, weighted by depth: eligible pool is everything locked scoring `≤ 310 + (deepestStrata × 250)`. |
| **Q4** | **Weapons, armour and items only.** Spells, encounters, statues and events deferred (see §6). |
| **Q5** | The `profile.json` collision is fixed **first, in a separate commit**. |
| **Q6** | Unlocks stay **global** — not slot-scoped, never wiped by death, new game, slot deletion or Apocalypse. |
| **Q7** | **Per-item criteria are deleted.** No `unlockPlan`, no `GateType`, no runtime RNG. Run-level earning replaces them entirely. |
| **Q8** | Migrate the **unlocked set only**. The counters existed to feed the thresholds Q7 deletes. |
| **Q12** | Show the real unlock on `PlayerDeathScreen`, replacing the hardcoded claim. A codex is the follow-up. |
| **Q13** | Death ends a run. The "return alive" path is **deferred and documented** (see §6). |
| **Q14** | All numbers ship as **explicit placeholders**, expected to move after play. |
| **Q15** | The merchant respects the gate, with a floor so pools never empty. |

### Why a tiered lock rather than a deep ladder

Every item locked is content taken away from a *new* player to give a *returning* one something to earn.
Today all 905 item-entries are free and the game plays fine. A score threshold of 310 locks 135 — roughly
four months at one guaranteed unlock per run — while leaving 402 available, so the first hours are
untouched. The distribution across all 537 in-scope entries, for retuning:

| Percentile | Score | Items locked above |
|---|---|---|
| p25 | 100 | 403 |
| p50 | 210 | 269 |
| **p75** | **310** | **135** ← proposed |
| p90 | 510 | 54 |

---

## 3. The work

### 3.1 Commit one — the collision (Q5)

- Move unlock data off `saves/profile.json` to its own global path, e.g. `saves/unlocks.json`.
  It must **not** live in a slot folder (Q6).
- Switch `UnlockManager.save()` to `SaveManager.atomicWriteJson`, matching every other manager.
  It currently uses a bare `writeString`.
- **Migration (Q8):** on load, if the old `profile.json` parses as `UnlockData`, carry `unlockedContent`
  across and discard the counters and plan. Then let `SaveManager` rewrite a clean `ProfileData`.
- `UnlockManager.incrementStat` never calls `save()` — counters only reach disk as a side effect of other
  methods. Moot once Q7 deletes the counters, but do not preserve the bug in passing.

### 3.2 Commit two — the system

**Delete** (Q7): `UnlockData.unlockPlan`, `UnlockRequirement`, `GateType`, `generateUnlockPlan`,
`checkUnlocks`, the four counters, `incrementStat`, `updateDeepestLevel`, and `UnlockManager`'s
`recordKill`. Also `UnlockData.sessionUnlocks` (`:24`), which is dead — `UnlockManager` keeps its own at
`:18`. Remove the now-orphaned `incrementStat` call sites in `Player.java` (`:2276`, `:2333`, `:2434`).

**Locking** (Q1/Q9): derive locked-ness from the score at load. **Do not reuse `ItemTemplate.locked`** —
see §5. Add a distinct concept, e.g. `ItemTemplate.unlockGated`, computed once when templates load.

**Enforcement** (one place, Q4): `ItemSpawner.selectSpecificItem` (`:206-222`) is the single chokepoint —
it serves floor loot, `MimicHoard`, and `MonsterFactory` inventories. Two fixes needed:
- Check the gate **before** the `probability > 0` guard, or the branch stays unreachable exactly as it is
  today.
- Key the check on `ItemType` name, matching `unlockedContent`. The `unlockId` scheme in
  `ItemDataManager.java:381` is a second, incompatible mechanism — delete it.

**Shop** (Q15): `ShopInventory` uses hardcoded pools and never consults `UnlockManager`. Six currently
stocked items would be locked at threshold 310 — `AXE` (312), `SPEAR_ONE_HANDED` (310), `CROSSBOW` (315),
`LARGE_SHIELD` (330), `MORNING_STAR` (410), `SWORD` (525). Filter the pools, but keep a floor so a pool
can never empty.

**Earning** (Q2/Q3/Q10/Q11): at the death block (`GameScreen.java:1120-1213`), after
`telemetry.exportRun` (`:1204`) and before `PlayerDeathScreen` is shown, roll the unlocks. Telemetry
already records everything the milestones need — `strataReached`, `getTotalMonstersKilled()`,
`divinitiesEarned`, `turnsLived`.

**UI** (Q12): replace the hardcoded "Progression Note" at `PlayerDeathScreen.java:155` with what was
actually unlocked. Also fix `GameOverScreen.java:285`, which strips `"item_"` / `"monster_"` prefixes
that have never existed.

### 3.3 Commit three — the codex (follow-up)

A browsable list of unlocked and still-locked content. Deliberately deferred: it can't be designed well
until the ladder exists and has been played.

---

## 4. Suggested tests

The firearm work showed that unit tests over the pieces all pass while the seam between them is broken.
Cover the seam:

- An item scoring below the threshold is never gated; one above it is, until unlocked.
- **`AXE` is never gated**, whatever its score.
- A locked item cannot be drawn from `ItemSpawner` — and specifically, that the gate is checked *before*
  the probability guard, which is the exact bug that made the current system dead.
- The depth weighting bounds the pool: a strata-1 death cannot draw a 4500-score Full Plate.
- A run always yields at least one unlock, and never more than three.
- Unlocks survive a simulated death, new game and slot deletion (Q6).
- Migration carries `unlockedContent` across and leaves `ProfileData` intact (Q5/Q8).
- The shop never offers a locked item, and never presents an empty pool.

---

## 5. Hazards

**`ItemTemplate.locked` is overloaded — do not reuse it.** `Item.java:458` and `:514` do
`item.isLocked = template.locked`, and `Item.isLocked()` means *"this container is physically locked and
needs a key"* — consumed at `Player.java:2465`, `SpawnManager.java:549`, `Hud.java:1938`. One JSON flag
already drives two unrelated concepts. Adding unlock-gating to it would make chests unopenable.

**`AXE` is load-bearing.** `ItemDataManager.java:386` substitutes `AXE` whenever an item is gated, with a
comment reading *"Ensure we don't infinitely recurse if AXE is also locked (it shouldn't be)."* At score
312 it would be locked by default. Exempt it explicitly.

**The four stray `locked: true` flags** on `SWORD`, `TWO_HANDED_SWORD`, `WAND` and `LARGE_LIGHTNING`
(`weapons.json`) should be removed as part of this work. They are the same species as the
`"TESTING: Locked BOW"` leftover already deleted from `ItemDataManager`.

**`UnlockManager.isUnlocked` returns `true` for a null or empty id** (`:232`). Fine today because nothing
reaches it; a hazard once the gate is live and something passes a missing key.

---

## 6. Deferred, with reasons

**The "return alive" path (Q13).** A run can currently only end by dying: `respawnInShelter` and
`TelemetryManager.startNewRun` each have exactly one caller, `PlayerDeathScreen`. There is no code path
where returning to the Shelter alive ends a run.

So *"every run unlocks something"* means **"every death unlocks something"**, and the careful player who
survives and returns to camp — the loop `CONTEXT.md` is built on — earns nothing. **This also puts two
systems in direct opposition: the Doom Clock punishes death (50 ends the world) while unlocks now reward
it.** Survivable at one unlock per death, but it is the strongest argument for building the return path,
and it should be built before the unlock ladder gets much deeper.

**Spells, encounters, statues, events (Q4).** Spells are the largest pool (370, with a genuine 10-level
ladder) but mostly inert SRD data, and several are actively mis-wired — `TRUE_SEEING` deals 12d6 damage
instead of revealing anything. Unlocking a broken spell as a reward would make the whole system feel
broken. Encounters number only 15 in total. Both want their own pass.

**A `tier` field.** No `tier`, `rarity` or `unlockOrder` field exists in any schema; `calculateItemScore`
is a proxy. Authoring real tiers across 537 entries is the better long-term answer and can replace the
score without changing anything around it.

---

## 7. Tuning placeholders (Q14)

| Knob | Value |
|---|---|
| Lock threshold | score **≥ 310** (p75) → 135 locked, 402 free |
| Guaranteed unlocks per run | 1 |
| Milestones | new deepest strata · 25+ kills · 50+ divinities · 300+ turns survived |
| Max unlocks per run | 3 |
| Depth weighting | eligible pool is `score ≤ 310 + (deepestStrata × 250)` |

The threshold is the one most likely to move. 135 locked items is roughly four months of play at one per
run — plausible on paper, but only play will show whether the early game feels thinner for it.
