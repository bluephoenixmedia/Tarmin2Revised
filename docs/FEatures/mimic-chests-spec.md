# Spec: Mimic Chests

**Status:** implemented — see §10 for where the build diverged from this design
**Origin:** `docs/polish/polish_9_17_26.md` — "make mimics more like their inspiration"

Mimics stop being wandering monsters and become what they are in the source material: a chest
that bites. A proportion of chests in the strata are mimics in disguise. Opening one wakes it,
and the disguise is now the mimic's *only* spawn path.

---

## 1. Settled decisions

| # | Decision | Chosen |
|---|---|---|
| 1 | Reveal trigger | On the player's **interact** with the chest, not on proximity or sight |
| 2 | Eligible container | `REGULAR_CHEST` only; raise `MIMIC` `minLevel` 5 → 8 to match the chest band |
| 3 | Wandering mimics | **Removed** from the monster spawn table; disguise is the only spawn path |
| 4 | Rate | See #12 — superseded |
| 5 | Counterplay | Both a passive Wisdom check **and** an always-on sprite tell |
| 6 | Loot on death | Drops container-tier loot plus normal monster loot |
| 7 | Render engines | `PLANAR_3D` gets the full effect; retro raycaster gets sprite swap + retro gibs |
| 8 | Reveal control | Blocking — a new `CombatManager` state, with a one-frame hit-pause jolt and trauma |
| 9 | Morph shape | Shudder → burst → mimic |
| 10 | Reveal duration | ~0.5 s (0.15 s shudder + 0.35 s burst); input **dropped**, not buffered |
| 11 | Audio | Distinct pitched-down cue on the shudder frame; also wire `playChestOpen()` for all world containers |
| 12 | Rate (final) | A **dedicated mimic budget**, 0–2 per eligible level, independent of the container pool |
| 13 | Locking | Reveal fires on the **open attempt**, lock or no lock, consuming no key |
| 14 | Sprite tell | A dedicated `mimic_chest.png` texture variant (see §6) |
| 15 | Loot carrier | Contents are **not** pre-rolled; loot is rolled from `containerLootPool` at death |
| 16 | Free attack | The mimic gets **one free attack** on reveal |
| 17 | Perception stat | **Wisdom**, via `getWisdom()`; `chance = 25 + (wisdom - 10) * 5` |
| 18 | Pre-emptive attack | A **detected** mimic can be bump-attacked, denying it the free swing; an undetected chest just opens |
| 19 | Detection feedback | Event line **and** a permanent `WorldInteractionCard` change; `detected` is persisted |
| 20 | Ranged attacks | A **detected** mimic can be targeted at range; any damage triggers the reveal. Undetected mimics are untargetable; AoE does not flush them out |

### Why the rate needed its own budget

At the current tables, `REGULAR_CHEST` carries weight 4 against `BOX`(10)/`MEDIUM_PACK`(8)/`LARGE_BAG`(6),
and only `containerBudget × 0.5` containers spawn per level. Expected chests per level runs 0.57 (L8)
to 2.0 (L19+). A naive "20% of chests are mimics" yields **0.11–0.40 mimics per level** — roughly one
every nine levels early on. Since decision #3 removes the wandering spawn, that rate would make the
creature effectively absent. A dedicated budget decouples mimic frequency from the loot economy.

Note: `levelBudgets` in `spawntables.json` stops at level 15; levels 16+ fall through to `defaultBudget`,
which is *smaller* than level 15's. Pre-existing gap, out of scope here, but it is why chest counts dip
at L16–18.

---

## 2. Data and spawning

**`assets/data/spawntables.json`**
- Remove the `MIMIC` entry from the monster spawn table (`spawntables.json:303`).
- Add a per-level mimic budget alongside `containerBudget` in `levelBudgets` (and `defaultBudget`), or a
  dedicated `mimicSpawnTable` with `minLevel: 8`. Target ~0.5–1.0 mimics per eligible level.

**`assets/data/monsters.json`**
- `MIMIC` template already exists (`baseLevel 3`, 50 HP, AC 12, 150 XP, `images/monsters/mimic.png`).
- `Monster.MonsterType.MIMIC` already exists (`Monster.java:54`, `Category.HORRIBLE`).

**`SpawnManager`**
- New `spawnMimics(int budget)` sibling to `spawnContainers()` (`SpawnManager.java:499`), called from the
  same place as the other budget spawns (`SpawnManager.java:217`).
- Each mimic spawns as a `REGULAR_CHEST` `Item` with the disguise flag set, placed via `getEmptySpawnPoint()`.
- **Do not** call `addLootToContainer()` on it (decision #15) and **do not** call `spawnKey()` for it.

---

## 3. Disguise representation and persistence

The disguised mimic is an **`Item`** (type `REGULAR_CHEST`) carrying two new booleans, not a `Monster`.
It only becomes a `Monster` at reveal.

```
Item.isMimic     — this chest is a disguised mimic
Item.mimicSeen   — the player has passed the perception check on it
```

**Persistence is a hard requirement, not an optimization.** `ChunkData.ItemData` is a hand-written
whitelist of six fields (`ChunkData.java:301-328`), and the rebuild path (`ChunkData.java:148-171`)
reconstructs items from scratch. Both flags must be added to **both** sides or they evaporate on chunk
unload/reload — and `mimicSeen` failing to persist would let a player leave and re-enter a chunk to
re-roll the perception check.

`ItemSaveData` (`ItemSaveData.java:17-23`) covers inventory/equipment items only; world chests round-trip
through `ChunkData`, so no change is needed there for this feature.

---

## 4. The reveal

### Trigger

In `Player.interact()`, container branch (`Player.java:2428-2465`). The mimic check must run **before**
the `isLocked()` early-return at `Player.java:2431`, per decision #13 — every `REGULAR_CHEST` is
auto-locked at construction (see §7), so a check placed after it would only fire for key-carrying players.

Guard: a mimic chest must never be locked in a way that blocks the reveal. Simplest correct form is to
test `isMimic` first and branch out of the container path entirely.

### Sequence

1. Frame 0: `GameScreen.triggerHitPause(~0.1f)` + `addTrauma(...)` for the jolt
   (`GameScreen.java:2647-2653`). Note `hitPauseTimer` freezes the **entire** update block including
   `animationManager.update` (`GameScreen.java:484-490`) — the pause can only bookend the effect, never
   host it.
2. Enter a new `CombatManager.CombatState.MONSTER_REVEAL`, ticked on a wall-clock timer in
   `CombatManager.update()` alongside the existing `PHYSICS_DELAY` / `monsterAttackDelay` pattern.
   Player input is **dropped** (flushed), not buffered.
3. 0.00–0.15 s — shudder. Drive the chest billboard from the reveal timer.
4. 0.15–0.50 s — burst masks the swap: remove the `Item` (`Maze.removeItem`), create the mimic via
   `MonsterFactory.createMonster(MonsterType.MIMIC, level, ctx)`, insert with `Maze.addMonster`.
5. The mimic takes **one free attack** (decision #16), then `startCombat()` hands control back.

### Free attack

`CombatManager.startCombat()` unconditionally sets `PLAYER_MENU` (`CombatManager.java:301`) — the player
always acts first, and no initiative or surprise concept exists in the codebase. The `justSpawned` flag
(`Monster.java:183`) guards an auto-adjacent-attack that no longer exists and is currently only cleared,
never read meaningfully (`CombatManager.java:1653-1669`) — it is the natural hook. Resolve the free
attack through the existing `monsterMeleeStrike(monster)` path rather than inventing a second damage route.

Skip the free attack entirely when the mimic was attacked pre-emptively (decisions #18/#20).

---

## 5. Rendering and audio

### `PLANAR_3D` (`World3DRenderer`) — full effect

- Item billboards are drawn at `World3DRenderer.java:1075-1142`, with per-item scale, vertical offset and
  vertex-color tint available at the draw site.
- **Shudder**: the closest existing precedent is the attack telegraph at `World3DRenderer.java:991-1007`
  — a manager-owned timer driving `scale × (1 + k·pulse)` plus a color lerp. `DynamicQuadBatcher.addRotatedBillboard`
  already exists (it drives tumbling gibs) if rotation is wanted.
- **Burst**: `AnimationManager.spawnExplosion(ExplosionType, Vector3, scale, duration)`. `STANDARD` or
  `CONCUSSIVE`. Rendered in the same 3D pass as entities at `World3DRenderer.java:1214-1243`.
- Headroom note: item billboards clamp at h ≤ 0.6 / w ≤ 0.8 and `maxMonsterW` is 0.82, so a dramatic
  "it grows" beat is not available. This is why the burst masks the swap rather than cross-fading it.

### Retro raycaster (`EntityRenderer`) — cheap stand-in

`SPRITE_EXPLOSION_3D` is **not** rendered by the raycaster's animation pass. Use a sprite swap plus a few
frames of `GoreManager.spawnRetroGibs(origin, spriteData, color)` (`GoreManager.java:234`), which does work
in this engine. Engine selection is at `GameScreen.java:645-664`.

Also note: the raycaster tints items by lighting only (`spriteBatch.setColor(itemLight)`) — **a tint-based
tell will not show in retro mode**. This is why decision #14 chose a texture variant over a tint.

### Audio

- Distinct cue on the shudder frame: `monster_roar_heavy` pitched down, following the pitch-randomization
  pattern in `SoundManager.playMonsterReaction()`.
- The normal combat roar then fires for free from `startCombat` → `playCombatStartSound()`
  (`SoundManager.java:331-337`).
- **Also wire `playChestOpen()` into `Player.interact`'s container branch for all world chests**
  (`SoundManager.java:404-409`). It is currently only used by the shelter-chest and grave-loot UIs, so world
  containers open silently. The mimic's missing lid-creak is the cheapest tell in the feature, and it only
  works if ordinary chests creak.

---

## 6. Detection

**This is the codebase's first perception check.** Nothing comparable exists: `"PERCEPTION"` in
`EncounterManager` is a hardcoded `return true` stub commented *"Player doesn't have perception stat yet"*,
`RingEffectType.SEARCHING` is an empty placeholder, and no secret-door concept exists.

**Stat:** Wisdom. `ShelterAltar.java:23` already describes it as *"Spiritual energy, divine boons, and
secret perception"*, and it has an ascension tree where Luck does not.

**Formula:** `chance = 25 + (getWisdom() - 10) * 5`, rolled once per mimic chest when it first enters view.
~25% at base 10, ~75% at Wisdom 20.

> **Use `getWisdom()`, not `getWisModifier()`.** The `getXxx()` accessors apply the altar ascension bonus;
> the `getXxxModifier()` methods read the raw private field and bypass it. A modifier-based roll would give
> an altar-invested player nothing, which defeats the reason Wisdom was chosen over Luck.

**On success** (decision #19): an event line (*"Something about that chest is wrong."*), a permanent
`WorldInteractionCard` change (the card currently shows container name and `[LOCKED]` state at
`Hud.java:1855`), and `mimicSeen = true` persisted.

**The always-on tell** is the `mimic_chest.png` variant — available to every player regardless of Wisdom,
and the thing a player can learn to hunt for.

### Asset dependency

| | |
|---|---|
| Path | `assets/images/items/mimic_chest.png` |
| Dimensions | 2048×2048 (matching `chest.png`), transparent background |
| Atlas region | `mimic_chest` — derived from the filename by `Item.java:532-537` |
| Pack step | `lwjgl3/src/main/java/com/bpm/minotaur/lwjgl3/PackTextures.java` regenerates `items.atlas` |
| Data | new `MIMIC_CHEST` entry in `items.json` cloning `REGULAR_CHEST` with `texturePath` swapped |

Item `spriteData` ASCII arrays exist in `items.json` but are **dead for rendering** — their only consumer
is the debug `SpritePreviewer`. The game renders items from PNGs via `packed/items.atlas`, so the variant
must be a real image.

Zero-art fallback if the asset is not available: a slow shallow idle bob, which works in both render
engines with no new assets. Everything downstream (the perception roll, the card change, pre-emptive
attacks) is unaffected by which tell is used.

---

## 7. Combat, targeting, and loot

**Pre-emptive attack** (#18): bump-to-attack works on a mimic chest **only** when `mimicSeen` is true.
Bumping an undetected chest opens it normally. Without this restriction a paranoid player could swing at
every chest and bypass the system.

**Ranged and spells** (#20): a detected mimic is targetable at range; any damage triggers the reveal.
Undetected mimics are untargetable — they are `Item`s and are not in `maze.getMonsters()`. AoE spells
covering the tile do **not** flush out an undetected mimic.

**Loot** (#15): the disguise carries no contents. On death, roll from `containerLootPool` and drop the
result alongside the mimic's normal loot. Hook: `CombatManager.handleMonsterDeath()` →
`spawnCorpseEffects(monster, overkillTier)`, using the existing `dropSingleItem(Item, GridPoint2, Monster)`
placement helper.

This avoids a real persistence hole: `ChunkData.MonsterData` serializes six fields and **does not persist
`Monster.getInventory()`**, so loot transferred onto a live mimic would vanish if the chunk unloaded
mid-fight.

> `dropSingleItem` silently loses an item when all nine candidate tiles are occupied — no else-branch.
> Pre-existing; worth knowing when a mimic dies in a cluttered room.

---

## 8. Pre-existing bugs this feature sits next to

Flagged because implementation will touch or trip over them. **None are in scope; do not fix silently.**

1. **Every `BOX` and `REGULAR_CHEST` is hard-locked at construction** (`Item.java:596-602`), overriding the
   template. `unlocks()` accepts any key (`key.isKey()`), so any key opens any chest. Decision #13 routes
   around this rather than fixing it.
2. **`ChunkData.ItemData` does not serialize `isLocked`**, so an unlocked chest re-locks itself on chunk
   reload. Same root cause as the whitelist problem in §3.
3. **`ChunkData.ItemData` only rebuilds contents one level deep** — the write path recurses fully
   (`ChunkData.java:320-326`) but `buildMaze` never applies `insideData.contents`, so a chest inside a chest
   loses its innermost payload.
4. **`ItemSaveData` has no `contents` field at all** — a container carried in the backpack loses everything
   inside it on save/quit/reload.
5. **`ChunkData.MonsterData` does not persist monster inventory** — any monster carrying loot loses it across
   a chunk round trip.
6. **Divination spells are inert, mis-wired SRD data.** `TRUE_SEEING` has `targetType: TOUCH` and
   `damageDice: 12d6`, so it deals 12d6 spiritual damage instead of revealing anything. `SEE_INVISIBILITY`
   carries `statusEffect: INVISIBLE` and makes *the caster* invisible. `DETECT_MAGIC` and `DETECT_EVIL_AND_GOOD`
   fall through to a default `HARDENED` +AC buff. Players may reasonably expect these to counter mimics.
7. **`Item.trapped` / `setTrapped()`** (`Item.java:264`) are declared and never called anywhere.
8. **`levelBudgets` stops at level 15**; 16+ falls through to a smaller `defaultBudget`.

---

## 9. Open items

- **The `mimic_chest.png` asset does not exist yet** and must be authored externally (§6). The build ships
  the idle-bob fallback; swapping the texture in is a small, isolated change (see §10).

---

## 10. As built

Where implementation diverged from the design above, and why.

### Added: a third flag, `mimicRollSpent`

The design persisted `detected` to stop a player re-rolling the perception check by leaving and
re-entering a chunk. It missed a nearer version of the same hole: the roll fires once per *turn* while the
chest is in front of the player, so without a spent-flag, standing still converts a 25% chance into a
certainty in a handful of turns. `Item.mimicRollSpent` records that the roll happened at all, pass or fail,
and persists alongside the other two.

### Changed: the roll fires on the tile in front, not "on entering view"

A view-entry trigger needs a per-turn LOS sweep over every item in the chunk. The roll instead fires in
`GameScreen.playerTurnTakesAction()` against the tile the player is facing — which is also where the
interaction card lives, so detection and its feedback share one place. The practical difference is that a
mimic spotted across a room is now spotted when you walk up to it instead.

### Changed: the sprite tell is a bob, not a silhouette variant

Decision #14 chose a dedicated `mimic_chest.png`. The asset does not exist and cannot be authored from
here, so the shipped tell is the zero-art fallback: a slow shallow idle bob, implemented in both renderers
(`World3DRenderer` and `EntityRenderer`). It is marked with a TODO at the `World3DRenderer` site. Swapping
in the texture means setting the variant at spawn and deleting the bob.

### Changed: the ambush does not consume a turn

`interactWithWorldObject` returns without calling `playerTurnTakesAction()` on the mimic path. Advancing
the turn would run the rest of the level's AI while the blocking reveal state is live, which invites the
two state machines to interleave. The mimic's free blow is the cost of reaching for the chest.

### Clarified: the pre-emptive path is non-blocking and grants no free attack

Decisions #16 and #18 together imply, but do not state, what the pre-emptive reveal looks like. As built:
striking a detected mimic reveals it immediately with no `MONSTER_REVEAL` state and no free blow — the
player owns the initiative, which is what spotting it bought them. The blocking two-phase reveal is
exclusively the ambush.

### Added: death by the free blow routes through `DEFEAT`

The free blow can kill an already-wounded player. Raising `PLAYER_DIED` directly would skip death
inversion and combat logging, so the reveal hands off to `CombatState.DEFEAT` and lets the existing branch
run.

### Detail: detection chance is clamped to [5, 95]

`25 + (wisdom - 10) * 5` goes negative at low Wisdom and past 100 at high. Clamping keeps noticing
possible for a dullard and never certain for a sage. The designed 25%/75% band at Wisdom 10/20 is
unaffected.

### Detail: mimic budget values

`mimicBudget` is 0 below level 8, 1 at levels 8–10, and 2 from level 11 (including `defaultBudget`, which
covers 16+). The count is rolled uniformly in `[0, budget]`, giving a mean of 0.5 mimics per level early
and 1.0 deeper — against 0.11–0.40 under the per-chest approach this replaced.

### Changed: interacting with a *detected* mimic attacks it

§4 has one interact path. As built there are two, chosen on `mimicSeen`: an unspotted mimic ambushes
(blocking reveal + free blow); a spotted one is attacked pre-emptively, with the player striking first.
Without this split the interaction card's own button would have handed the mimic its free blow — the
opposite of decision #18 — since the card's action routes through `interactWithWorldObject`.

The interact path also now returns unconditionally once it sees a mimic, in *any* combat state. `Input.Keys.O`
is not state-gated, so without that return, pressing interact during the shudder (or during any fight) fell
through to `Player.interact`'s container branch and its `maze.getItems().remove(targetTile)` — deleting the
creature mid-reveal and stranding the encounter.

### Changed: the ranged reveal is opt-in per call site

`raycastProjectile` gained a `revealDisguises` flag, default false. Revealing inside a shared hit-test meant
any trace — including a speculative "is anything in range?" probe — could spawn a monster, play a sound and
shake the camera. Genuine attacks (ranged fire, thrown weapons, offensive spell targeting) pass true.

Note this reveals on being *targeted*, not on damage landing, which decision #20 nominally asked for
("any damage triggers the reveal"). Targeting requires a `Monster` to exist, and `HitResult` carries one —
so the disguise must drop before the to-hit roll. A missed shot therefore still reveals.

### Changed: the container sound fires before the lock check

`playChestOpen()` sits ahead of the `isLocked()` early-return. Since every chest spawns locked, placing it
after would have left the most common case silent and the audio tell would never have formed.

### Not used: `MonsterFactory` and `justSpawned`

§4 named both. `createMimicMonster` builds the monster directly instead, because `MonsterFactory` rolls an
inventory and decision #15 exists precisely so a mimic carries nothing. The free blow resolves through
`monsterMeleeStrike` rather than the `justSpawned` hook, which would have meant repurposing a flag that
several unrelated paths read. `createMimicMonster` also falls back to the stat-only `Monster` constructor
when no data manager is available, so a reveal degrades instead of throwing.

### Detail: loot comes from `ItemSpawner`, not `containerLootPool`

`containerLootPool` is private to `SpawnManager` and not reachable from combat. `MimicHoard` builds an
`ItemSpawner` with `SpawnContext.isContainer = true`, which is the same container-tier weighting
(potions/scrolls/gems) by a different route. A mimic drops 1–3 such items, deliberately more than the
single roll an ordinary chest gets.
