# Requirements: Spell System Overhaul

**Status**: Specified, not started.
**Date**: 2026-09-27
**Branch to work on**: branch from `develop` (see `AGENTS.md` → Branching and releases)

This document is a handoff spec. Every design decision below was settled with
the project owner; where a decision overrides an earlier recommendation, that is
stated so it is not silently re-litigated.

---

## 1. The ask

> "Let's overhaul the spell system entirely. Right now I don't even know how to
> access the spell book, it's too hard to unlock spell slots via the Tome. I want
> to be able to unlock slots via leveling and the Tomes are just bonus unlocks.
> Once we stabilize this then it's time to go one by one for the spells in the
> game and spend time on their animations and effects in great detail."

Two phases. **Phase 1 stabilises access and progression. Phase 2 is visual
work and must not begin until Phase 1 ships.**

---

## 2. What is actually true today

Verified against the code on 2026-09-27. Do not trust prose elsewhere in the
repo: `SYSTEMS.md` §6 and `ROADMAP.md:31` are stale (they describe a
`Player.knownSpells` / `SpellType` system that no longer exists, and "6 spells
implemented" when there are 370).

### 2.1 Accessing the spellbook

| Fact | Detail |
| :--- | :--- |
| Key | `Q`, rebindable, `SettingsManager.java:194` |
| Handler | `GameScreen.java:2956-2962` |
| Listed in Controls screen | Yes, `SettingsManager.java:51` |
| Hotbar affordance | `Hud.java:564-641` shows `[Z][X][V][B][N]` and **never mentions `Q`** |
| Only always-visible hint | `screens/inventory/SpellbookPanel.java:53-56`, MODERN inventory only |
| Level-up teaches | `[K]` for the skill tree (`Player.java:3042`), never `[Q]` |

**It is not gated — it is unannounced.** Two real bugs sit alongside:

1. The `Q` branch is placed **after** the modal-forwarding guards
   (`GameScreen.java:2300-2322`), so it silently does nothing while any modal is
   open, and during `ENEMY_TURN` / `PLAYER_MENU` combat states.
2. `Q` is also "save and quit" on the Pause screen (`PauseScreen.java:235`).

### 2.2 Spell slots

There is no Slot class. A slot is an index into a fixed array plus a counter —
`Player.java:207-213`:

```java
private final String[] preparedSpells = new String[5];
private int unlockedSpellSlots = 1;
```

Clamped to 1..5 at `Player.java:228-230`. **The only code that ever raises it is
the Tome path** (`Player.java:784-792`). Nothing in levelling, skills or the
Altar touches it.

### 2.3 Why Tomes are "too hard"

Not difficulty — **scarcity and depth-gating**. `SpawnManager.java:227-246`
places exactly one Tome per milestone depth, only ever in chunk (0,0):

| Tome | Slot | Depth required | Study turns |
| :--- | :--- | :--- | :--- |
| Initiate | 2 | 2 | 10 |
| Elements | 3 | 4 | 15 |
| Arcane | 4 | 6 | 20 |
| Tarmin | 5 | 8 | 25 |

So **the fifth slot requires surviving to depth 8.** Study is channelled
(`GameScreen.java:1991-1993`, 0.25s real time per world turn) and is cancelled by
*any* keypress, any damage, or any hostile entering view
(`TomeStudy.java:36-43`, `GameScreen.java:1966-1989`).

### 2.4 Levelling

`PlayerStats.performLevelUp()` (`PlayerStats.java:207-229`) is the hook. XP curve
is `BASE_XP_REQUIRED = 120`, `LOG_BASE = 1.6` (`PlayerStats.java:42-45`).
A skill tree already exists (`SkillId.java`, opened with `K`), with six Arcana
skills — **none grants a slot**.

Note `unlockedSpellSlots` lives on `Player`, not `PlayerStats`, so the new rule
belongs in `Player.addExperience` / `Player.performLevelUp`
(`Player.java:3026-3079`), not in `PlayerStats`.

### 2.5 Spell inventory

370 spells in `assets/data/spells.json`, keyed by id. Schema in
`SpellTemplate.java:7-24`.

- By level: L0 30, L1 60, L2 65, L3 48, L4 34, L5 39, L6 36, L7 23, L8 19, L9 16
- By school: transmutation 70, evocation 65, conjuration 58, abjuration 42,
  divination 38, enchantment 35, necromancy 33, illusion 29
- Learn-level gate: `(spell.level - 1) * 2 + 1` (`Player.java:487-496`)
- Known = `knownSpellIds`, split into `permanentSpellIds` (survive death) and
  `runSpellIds` (wiped at `Player.java:276-286`)

### 2.6 Visuals

12 archetypes in `VisualArchetype.java:10-130`, each carrying colour, sound,
explosion type, decal colour and particle glyph. Authored distribution:

```
132 FORCE_MISSILE   60 HOLY_RADIANCE   43 ARCANE_WARD   31 NECROTIC_DRAIN
 22 EXPLOSIVE_BURST 20 TOXIC_CLOUD     20 SPATIAL_WARP  15 FLAME_BOLT
 10 PSYCHIC_SHOCK    9 FROST_RAY        4 LIGHTNING_ARC  4 THUNDER_CONCUSSION
```

> [!IMPORTANT]
> The 132 `FORCE_MISSILE` entries are **authored that way in the JSON**, not
> falling back. An earlier reading of this code claimed a silent fallback; that
> was wrong. Every one of the 370 has an authored `visualArchetype`. This means
> "a third of the book looks identical" is a **data problem fixable by editing
> JSON**, not an art problem.

Only 6 bespoke handlers exist (`SpellExecutionEngine.java:98-119`), covering 13
spells. The other 357 are rendered generically from their archetype.

`isHealing` is detected by **name substring** (`SpellExecutionEngine.java:369-372`
— `contains("cure")||contains("heal")||...`). Fragile; replace with a data field.

Dead code: `spells/effects/{MagicArrow,Heal,ForcePush,Drain,IronSkin,Teleport}Effect.java`
are never instantiated anywhere.

### 2.7 MP — probably already fixed

An earlier backlog note said MP regenerates almost instantly and should be
attribute-gated. `TurnManager.java:424-440` shows it **already is**: Wisdom-paced,
1 MP per 24 turns at WIS 10, down to 1 per 8 turns at WIS 20, and gated on
hydration > 0. **Re-check in play before spending time here.** The likely real
refill channel is the +25% of max MP granted on every level-up
(`PlayerStats.java:225`).

---

## 3. Settled decisions

| # | Decision | Chosen |
| :--- | :--- | :--- |
| Q1 | Phase 2 scope | Per-archetype effects first, then bespoke passes on the spells actually cast |
| Q2 | Slot progression shape | Front-loaded |
| Q3 | What Tomes become | Source of specific, permanent, otherwise-unobtainable spells |
| Q4 | Spellbook discoverability | Label hotbar + teach at level-up + fix handler order. **Do not rebind `Q`** |
| Q5 | Slot ceiling | **5** — unchanged |
| Q6 | Arcane Attunement | Becomes a Tome-choice *quality* axis only; supersede ADR 0001 |
| Q7 | Effect axis | **Archetype (12), not school (8)** |
| Q8 | Slot schedule | 1 at start, +1 at levels **2, 5, 8, 11** |

---

## 4. Phase 1 — Access and progression

### 4.1 Slots from levelling

Start at 1 slot. Grant +1 at character levels **2, 5, 8, 11**, reaching the cap
of 5 at level 11.

Implement in `Player` (not `PlayerStats`), as a pure function so it is testable
without a game instance:

```java
/** Slots unlocked by reaching a given character level. */
public static int slotsForLevel(int level) { ... }   // 1,2,2,2,3,3,3,4,4,4,5...
```

Requirements:

- `unlockedSpellSlots` must be `max(slotsForLevel(level), slotsFromTomes)` so a
  loaded save never *loses* a slot it already had.
- The level-up modal (`LevelUpModal.java:28-115`) must announce a newly unlocked
  slot and offer to open the spellbook.
- Cap stays 5. Do not widen `preparedSpells`; the HUD hotbar and the save format
  both assume five.

### 4.2 Tomes stop granting slots

`Player.unlockTomeSlot` (`Player.java:779-792`) no longer raises the counter.
A Tome instead grants a **specific, permanent spell** the player cannot obtain
otherwise.

- Tome-granted spells go to `permanentSpellIds` — they survive death. This
  matches a separate logged requirement that Tome and level-up spells are
  permanent while field-learned ones are run-only.
- Keep the Tome Choice UI; it now chooses a spell, never a slot.
- Curate a Tome-exclusive pool. Only 51 of 370 spells currently carry
  `tomePools`, and four TARMIN-pool spells are unreachable without Arcane
  Attunement tier 3. Fix that reachability as part of this.
- **Relax the study channel.** With slots no longer behind it, the 10–25 turn
  cancel-on-any-keypress channel is pure friction. Recommend: keep the turn cost,
  but only cancel on *damage* or a hostile entering view — not on any keypress.

### 4.3 Make the spellbook discoverable

1. Caption the HUD hotbar `SPELLS [Q]` (`Hud.java:564-641`), using the live
   binding so a rebind is reflected.
2. Teach `[Q]` in the level-up modal beside the existing `[K]`, and again the
   first time a slot unlocks.
3. **Fix the handler order** — move the `SPELLBOOK` check ahead of the
   modal-forwarding guards at `GameScreen.java:2300-2322` so it works during
   combat and is not swallowed by modals. This is a bug, not polish: a key that
   silently does nothing reads as broken.
4. Do **not** rebind `Q`. It breaks existing muscle memory and the key is not the
   problem.

### 4.4 Retire the third unlock axis

`ShelterAltar.isSpellSealed` (`ShelterAltar.java:408-420`) hard-gates exactly 9
named spells out of 370 — too small a fraction for a player to perceive a rule.

- Remove the 9-spell sealing.
- Keep `getTomeChoicePerks` (`ShelterAltar.java:375-386`): Arcane Attunement now
  governs only Tome-choice richness (options 3→4→5, rerolls, level cap).
- **Write a new ADR superseding `docs/adr/0001-tomes-grant-slots-and-a-spell-choice.md`.**
  That ADR records Tomes as the slot source; leaving it in place would make the
  documented architecture silently wrong.
- Update `CONTEXT.md` glossary: slots, Tome grants, and the Altar's new role.

### 4.5 Tests that will constrain this

These exist and will need updating — read them before editing, they encode the
current intent:

```
gamedata/player/SpellSlotAssignmentTest.java
gamedata/player/SpellbookLearningTest.java
gamedata/player/TomeStudyTest.java
gamedata/player/ExpeditionProgressionBatch2Test.java
gamedata/save/PlayerSpellSaveTest.java
gamedata/spells/SpellSystemTest.java
```

New tests required: `slotsForLevel` boundaries (levels 1, 2, 5, 8, 11, 20); that
a save never loses a slot; that a Tome grants a permanent spell and not a slot.

---

## 5. Phase 2 — Visuals

**Do not start until Phase 1 ships.**

### 5.1 Step one is free: re-author the data

Editing the 132 placeholder `FORCE_MISSILE` entries so each spell's
`visualArchetype` matches what it actually does will fix most of "they all look
the same" **before any art is made**. This is a JSON pass, not an art pass, and
it is the highest value-per-hour work in the whole overhaul.

Also replace the `isHealing` name-substring check with a real data field while in
the file.

### 5.2 Step two: one strong effect per archetype

Twelve effects, not 370 and not 8. Archetype is already wired end-to-end
(colour, sound, explosion type, decal, particle), so this slots into the existing
pipeline.

Art is available in-repo and licensed (Humble Bundle purchases — see
`docs/asset-licenses.md`). The most directly usable:

| Pack | Contents |
| :--- | :--- |
| `docs/game_assets/Free Pixel Effects Pack` | 20 named spritesheets: `magicspell`, `casting`, `protectioncircle`, `freezing`, `felspell`, `flamelash`, `firespin`, `vortex`, `phantom`, `nebula`, `sunburn`, `midnight`, `magicbubbles`, `bluefire`, `brightfire`, `magickahit`, `weaponhit`, `magic8` |
| `docs/game_assets/Super Pixel Effects Gigapack (Free Version) v2.0.0` | Explosion sets in several colourways (epic / stylized / symmetrical) |
| `docs/game_assets/BearFX Explosions`, `explosion pack 1` | Additional explosions |
| `docs/game_assets/POLYGON_Particle_FX_SourceFiles_v2` | Particle source files |

Suggested mapping (adjust on sight): `FLAME_BOLT`→flamelash/firespin,
`FROST_RAY`→freezing, `ARCANE_WARD`→protectioncircle, `NECROTIC_DRAIN`→felspell,
`SPATIAL_WARP`→vortex, `PSYCHIC_SHOCK`→nebula, `HOLY_RADIANCE`→sunburn/brightfire,
`FORCE_MISSILE`→magicspell/magickahit, `TOXIC_CLOUD`→midnight,
`EXPLOSIVE_BURST`/`THUNDER_CONCUSSION`→explosion packs.

Baking to billboards: `tools/blender/bake_theme_props.py` is the existing
precedent for turning source art into game-ready sprites.

### 5.3 Step three: bespoke passes

Only after the twelve read well. Extend the 6 existing bespoke handlers
(`SpellExecutionEngine.java:98-119`) to the spells a player actually casts —
realistically the 40–60 across levels 0–3 reachable in a normal run. Let play
decide which earn the attention; most of the 370 are unreachable at normal levels
anyway.

---

## 6. Definition of done — Phase 1

- [ ] Slots unlock at levels 2, 5, 8, 11; cap 5; a loaded save never loses one.
- [ ] Tomes grant a permanent spell, never a slot.
- [ ] Tome study no longer cancels on an arbitrary keypress.
- [ ] Hotbar is captioned with the live spellbook binding.
- [ ] Level-up teaches `[Q]` and announces new slots.
- [ ] `Q` works during combat and is not swallowed by modals.
- [ ] Arcane Attunement no longer seals spells; it shapes Tome choices only.
- [ ] New ADR supersedes 0001; `CONTEXT.md` glossary updated.
- [ ] Full suite passes and the game boots (`AGENTS.md` rule: a clean merge is not
      proof of a working one).

## 7. Risks

**Save compatibility.** `unlockedSpellSlots` is persisted
(`PlayerSaveData.java:89,201,319`). Taking the max of level-derived and stored
values is what prevents an existing character losing slots on load. Get this
wrong and players lose progression silently.

**Balance shift.** Slots currently require depth 8; they will require level 11.
Whether level 11 arrives sooner or later than depth 8 in practice is unmeasured —
check run telemetry before tuning further.

**Scope.** Phase 2 at 370 spells is open-ended by nature. The ordering in §5 is
deliberate: the free data fix first, then twelve effects, then bespoke work only
where play shows it matters.
