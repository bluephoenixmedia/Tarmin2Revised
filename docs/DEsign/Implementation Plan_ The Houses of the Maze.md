# Implementation Plan: The Houses of the Maze

> **Status:** Approved design, not started. Ships **after 0.0.2**, in four slices.
> **Source:** design grilling session, 2026-10-08 (decisions Q1-Q45, recorded below).
> **Branching:** every task branches from `develop` and merges back to `develop`. Never `master`.
> **Coordinator:** `.claude/agents/facilitator.md` owns ticket state, defect triage, review gates and the MVP cut line for this plan.

Hell is **the Maze**. Its great houses are rival powers, all hostile to the player, which tear
**gashes** into the mortal world and feud among themselves. Their wars, successions and betrayals
are procedurally generated history, written in a grim, specific, George R. R. Martin voice and
simulated the way Dwarf Fortress and Caves of Qud simulate theirs. The player crosses those wars on
the surface, or flees beneath them into strata haunted by named megabeasts and home to the game's
only friendly towns.

---

## 1. Decisions (single source of truth)

Every task below traces to these. A task that contradicts one is a defect in the task.

| # | Decision |
| :-- | :-- |
| D1 | **Original IP.** Generic rival factions of hell ("the Maze"). No Hellraiser names, sigils or likenesses. |
| D2 | A **gash** is a tear in the world into a house's domain. The land around it is tainted on the overland map. |
| D3 | Wars are caused by **internal politics only**: a War-of-the-Roses epic, procedurally generated. |
| D4 | **Content ceiling:** political cruelty and gore, written vividly; sprites show aftermath (corpses, impalements, banners). **No sexual content of any kind.** Enforced by test (see T1.9). |
| D5 | **Three great houses** hold the three gashes at the seal sites, plus **4-8 lesser houses** that swear, rebel and usurp. Seats can change hands; there are always three gashes. |
| D6 | **Tarmin-Zul** (the Minotaur) is a Maze lord who broke out through the obsidian rift. His castle is a fourth seat. The houses hate him and covet it. |
| D7 | The **seal bosses** (unbuilt Phase 5) are the lords currently holding each gash: generated characters with names, histories and grudges. |
| D8 | History is **pre-generated at world creation and simulated live**. |
| D9 | The simulation tracks **houses and named characters**: lords, heirs, vassals, traits, relationships, titles, seats. |
| D10 | The player changes history **through violence only**. Kills are chronicled; the chronicle gives the player an epithet. No diplomacy with the Maze. |
| D11 | Prose comes from a **handwritten grammar**: deterministic per seed, offline. No runtime LLM. |
| D12 | History is **found in the world** (fragments) and **compiled in the chronicle**. |
| D13 | Wars are fought **on the surface**. The **underground** is the alternative route, with its own terrors (megabeasts) and its own life (towns). |
| D14 | A battle is mandatory **only if the player stays in the chunk**. Surviving pays significantly. |
| D15 | **Fronts are visible on the overland map** and move over time. When a front reaches the player's chunk, they get a warning window (horns, drums, scouts) to leave by an edge or by descending. |
| D16 | Once the battle is joined, the player can still flee, but **the chunk edges are the battle lines**. No themed-chunk gate sealing. |
| D17 | A battle ends when **one side routs**; the player can tip it. A turn cap is the fallback. |
| D18 | Rewards: **the field** (gear of the fallen), **trophies** (sigils, signet rings, banners; also chronicle fragments and a currency), and **any fallen lord's gear**. |
| D19 | **Megabeasts live in the same history** as the houses: generated in prehistory, lairs in the strata, bargained with and feared. |
| D20 | **Underground towns** each have their own allegiances; some are hostile. |
| D21 | Ships **after 0.0.2**, as slices 1 to 4 in order. |
| D22 | History cadence: **political events tick once per shelter sleep (a "season")**. **Fronts and battles tick on a war clock** that runs on the surface and underground, separate from the Doom Clock. |
| D23 | Prehistory: **~300 years, 10-12 generations**, under **2 s** at world creation, deterministic per seed, headless-testable. |
| D24 | v1 events: succession (clean, disputed, usurped), assassination, marriage pact or alliance, betrayal, vassal oath, vassal rebellion, declaration of war, battle, seat seized, hostage taken, hostage executed, lord slain by the player. Megabeast events are stubbed until slice 3. |
| D25 | Houses decide on war by **utility with a recorded casus belli**: a grudge, a disposition (ambitious, wrathful) and an opportunity. The "why" is stored on the event. |
| D26 | **Factions are generalised to dynamic houses.** Each house is its own faction id; relations between houses come from the history; every house is hostile to the player. `TARMIN_LEGION` becomes Tarmin-Zul's house. Needs an ADR. |
| D27 | House soldiers in v1 **reuse the existing monster roster**, grouped by the house's doctrine, with palette-swapped heraldry and banners. |
| D28 | The chronicle **merges into the Archive Lectern** (the existing "Chronicle of Tarmin"). Unlocks become one section. |
| D29 | History is told by **biased in-world chroniclers**. Accounts of one event can contradict each other, and the Lectern shows the versions side by side. |
| D30 | A lord as boss is **composed**: a doctrine archetype, plus trait modifiers, plus a retinue of named sworn swords from the history. |
| D31 | A battle is **30-40 live combatants** in a 36x36 chunk. Each side has a **reserve** spent to march reinforcements in from its edge. Off-screen volleys and charges are telegraphed hazard tiles. Soldiers target enemy soldiers first; they turn on the player when adjacent, when struck by the player's side, or in a random **melee spill**. |
| D32 | A war **can change who holds a gash** during a run. The change is announced in the chronicle and on the map; the seal still exists. |
| D33 | Megabeasts are a **hybrid**: about 6 authored archetypes crossed with generated modifiers (material, breath or affliction, weakness). |
| D34 | A megabeast has a lair at a fixed depth and a roaming radius. It raids or is bargained with at history ticks. It can hunt across chunks once it has noticed the player. Its death is permanent. |
| D35 | **3-6 megabeasts per world**; lair depth scales power; at least one lair is reachable from strata 1-2. |
| D36 | **Underground biomes** come in depth bands with regional noise (fungal forest, flooded halls, ossuary, magma deeps, plus the existing maze). Towns appear only in certain biomes. |
| D37 | A **town** is a persistent chunk template with a generated layout, holding 4-8 named NPCs from the history: merchant, smith, innkeeper, quest-giver, elder. About one town per 10-15 underground chunks, at strata 2-4. |
| D38 | NPC talk is **Qud-light**: Talk, Trade, Rumours, Quest, Leave, with generated lines. |
| D39 | Quests are **generated from the history** by templates, and completing one is a history event. |
| D40 | Towns owe allegiance to **mortal powers** (refugee councils, Goblin Clans, Outcast cults). A town can be **secretly suborned** by a Maze house, which a betrayal event later reveals. |
| D41 | **Standing is per town**, with allegiance-wide spillover. Hostile towns turn their guards on the player. |
| D42 | Tarmin-Zul's house acts in the history. Each Doom Clock stage becomes a chronicled **ascendancy event** (he bleeds the houses to feed the sacrifice), with **no change to doom numbers**. |
| D43 | A **gash interior** is 3-5 Maze strata themed by the holding house's doctrine, ending in the lord's court and its seal. It re-themes when the holder changes. |
| D44 | **6-8 authored doctrines.** Each sets a roster, palette, interior theme, boss archetype and chronicler grammar flavour. Houses add generated names, sigils, words and mottos. |
| D45 | **The player's deaths enter the history.** Each dead expedition is chronicled. A ghost player carries its epithet. The house that killed it gains prestige. |

---

## 2. Glossary

Add these terms to `CONTEXT.md` in T1.1. Use them in code, tickets and tests.

- **The Maze**: hell. The realm the houses come from and Tarmin-Zul escaped.
- **House**: a political power of the Maze. A **great house** holds a gash; a **lesser house** is sworn to one, or to no one.
- **Gash**: a tear from the Maze into the world, at a seal site. Its **holder** is the house that owns it.
- **Lord**: the character who heads a house. **Heir**, **vassal** and **sworn sword** are also characters.
- **Doctrine**: a house's authored creed. It sets that house's soldiers, palette, interior and voice.
- **Casus belli**: the recorded reason a house declared war.
- **Season**: one political tick of the history, which happens once per shelter sleep.
- **War clock**: the turn-based clock that moves fronts and resolves battles.
- **Front**: a moving zone of war on the overland map, between two warring houses.
- **Battle**: a front engaging the player's current chunk.
- **Rout**: the end of a battle, when one side's reserve and morale both collapse.
- **Melee spill**: a soldier breaking off to attack the player mid-battle.
- **Fragment**: a found piece of history (tome, banner, proclamation, rumour, trophy) that unlocks chronicle entries.
- **Chronicler**: the biased in-world author of a fragment.
- **Megabeast**: a named, unique creature of the history with a lair in the strata.
- **Town**: a persistent underground settlement owing allegiance to a mortal power.
- **Suborned**: a town secretly serving a Maze house.

---

## 3. Architecture

Read `docs/Implementation Plan_ The Expedition Loop & Progression Reboot.md` first, as AGENTS.md
requires. This system adds a layer **above** chunk generation. It never replaces it.

```
gamedata/history/        pure model + simulation, NO libGDX imports (headless, unit-testable)
  HistoryWorld           root aggregate: houses, characters, events, megabeasts, towns, fronts
  House, Character, Doctrine, Trait, Relationship, Title, Seat
  HistoryEvent (+ EventType, CasusBelli, Witness list for chroniclers)
  HistorySimulator       prehistory(seed) and tickSeason(world, playerDeeds)
  WarClock, Front, FrontPlanner
  Megabeast, MegabeastArchetype, MegabeastModifier      (slice 3)
  Town, Allegiance, Standing                            (slice 4)
gamedata/history/text/   ChronicleGrammar, Chronicler, Fragment, EpithetGenerator
managers/HistoryManager  owns the HistoryWorld at runtime; saves and loads; feeds other managers
managers/BattleDirector  runs a battle in the current chunk                    (slice 2)
```

### Invariants every task keeps

1. **Determinism.** History uses its own seeded RNG stream, derived from the world seed. It never
   draws from the world-generation or combat streams, and never the reverse. The same seed and
   the same player deeds produce the same history, byte for byte.
2. **Headless.** `gamedata/history/**` makes no `Gdx.*` runtime calls (`com.badlogic.gdx.utils` such as `JsonReader` is fine). Each slice's simulation is
   covered by plain JUnit tests under `./gradlew :core:test`.
3. **Save compatibility.** History lives in the world save. A pre-history save loads, generates
   a history on first load, and never crashes.
4. **The Maze is hostile.** No path makes a Maze house friendly to the player (D10).
5. **Content ceiling (D4).** No grammar can emit sexual content. A lexicon test enforces it.
6. **UI text.** Every generated string passes `UiGlyphs.sanitize` (no `_`, no degree sign, no
   box drawing) and reaches the screen through `UiNames` and `UiTheme`, per the UI overhaul rules.
7. **Doom numbers unchanged (D42).** History reads the Doom Clock. It never writes to it.
8. **Seal gate unchanged.** `CastleGate.SEALS_REQUIRED` stays 3. A seal's holder can change; the
   seal itself never disappears.

---

## 4. Slices and tasks

Tasks are sized for a single agent session. Every task:
- carries an **MVP** or **STRETCH** tag (the facilitator cuts STRETCH first, Section 6);
- names its dependencies;
- is done when **every** acceptance criterion holds, `./gradlew :core:test` is green, and the game
  boots to a world that uses the feature.

### Slice 1: History, chronicle and lords

The root. Everything else hangs on it. Exit criterion: a fresh world has a 300-year history, the
Lectern shows it, and the three seal bosses are generated lords.

**T1.1: Glossary and ADR** · MVP · deps: none
- Add the Section 2 terms to `CONTEXT.md`.
- Write `docs/adr/0004-dynamic-house-factions.md` recording D26: why `Faction` grows dynamic
  house ids, what happens to `FactionMatrix` seeding, and how saves migrate.
- AC: both files merged; the ADR names the migration path for `ChunkData.MonsterData.faction`.

**T1.2: History model** · MVP · deps: T1.1
- Add `HistoryWorld`, `House`, `Character`, `Doctrine`, `Trait`, `Relationship`, `Title`, `Seat`,
  `HistoryEvent`, `CasusBelli`: plain data with ids, no behaviour beyond invariants.
- AC: the history saves as a replay (seasons elapsed, ordered player deeds, unlocked fragments;
  ADR 0004). A unit test proves that saving and then loading reproduces an identical world.

**T1.3: Doctrines** · MVP · deps: T1.2
- Author 6-8 doctrines in `assets/data/doctrines.json` (D44). Each one has: id, display name,
  roster selection drawn from existing `monsters.json` entries (D27), palette, interior theme key,
  boss archetype base, trait weights, and grammar flavour key.
- AC: loader test proves every roster entry exists in `monsters.json`; every doctrine has every
  field.

**T1.4: Prehistory generator** · MVP · deps: T1.2, T1.3
- `HistorySimulator.prehistory(seed)`: found 3 great and 4-8 lesser houses, then run 10-12
  generations (D23). Use the D24 event set, and utility-based war with a casus belli (D25).
  Tarmin-Zul's house breaks out through the obsidian rift partway through history (D6).
- AC:
  - same seed, same history (test);
  - under 2 s on a dev machine (test with a generous CI bound);
  - exactly 3 gashes held at the end;
  - every war event has a non-null casus belli;
  - at least one disputed succession or usurpation in 95% of 100 seeds (test).

**T1.5: Dynamic house factions** · MVP · deps: T1.1, T1.4
- Implement ADR 0004. House relations in `FactionMatrix` derive from the `HistoryWorld`; every
  house is hostile to the player; `TARMIN_LEGION` maps to Tarmin-Zul's house. The existing
  infighting in `MonsterAiManager` keeps working unchanged.
- AC:
  - existing faction tests pass;
  - a new test shows two houses at war infight and two allied houses do not;
  - old saves load.

**T1.6: Live season tick** · MVP · deps: T1.4
- `HistorySimulator.tickSeason` runs on shelter sleep (D22). The events it produces are queued
  as "news" for the wake-up.
- AC: sleeping advances exactly one season; news shows on waking; deterministic for the same
  inputs.

**T1.7: Player deeds** · MVP · deps: T1.6
- Killing a lord, heir or sworn sword is recorded as a history event. Each dead expedition is
  chronicled, and the killing house gains prestige (D10, D45). The player gets a generated
  epithet.
- AC: killing a named character triggers its consequence (succession) at the next season; a test
  covers the succession crisis.

**T1.8: Chronicle grammar** · MVP · deps: T1.4
- `ChronicleGrammar` renders events through a `Chronicler`'s bias (D11, D29): the same event
  renders differently for each side ("the Usurper" against "the Restorer"). Authored templates
  live in `assets/data/chronicle_grammar.json`. Also generate epithets, house words and mottos.
- AC:
  - every `EventType` has at least 3 templates for each of at least 2 biases;
  - a golden-file test renders a fixed seed's chronicle;
  - `./gradlew :core:dumpChronicle -Pseed=N` (or an equivalent test task) writes a full chronicle
    to a file for human prose review.

**T1.9: Content ceiling and glyph guard** · MVP · deps: T1.8
- A test renders 200 seeds' chronicles and asserts:
  - no term from a sexual-content blocklist appears (D4);
  - every string survives `UiGlyphs.sanitize` unchanged.
- AC: the test exists and is green.

**T1.10: Fragments** · MVP · deps: T1.8
- Place fragments in the world (tomes, banners, impaled heralds with proclamations, shelter
  rumours, existing Void lore glyphs). Picking one up unlocks its chronicle entries (D12).
- AC: each kind of fragment spawns in a test world; pickup unlocks the matching entry; unlocks
  persist across save and load.

**T1.11: Lectern chronicle** · MVP · deps: T1.10
- Merge the history into the Archive Lectern (D28). Unlocks become one section. History entries
  are grouped by house and era, and contradicting accounts show side by side (D29). Use UI
  overhaul components and `UiTheme`, wrap or ellipsise every label, and keep it Table-only.
- AC: a screenshot check at 1920x1080; existing unlock browsing still works; tick any SPEC
  findings this touches.

**T1.12: Lords as seal bosses** · MVP · deps: T1.5, T1.4
- Fill the Phase 5 seal-boss slot (`SealedGates`, `CastleGate`): the boss is the gash holder's
  lord, composed from doctrine archetype, traits and a named retinue (D7, D30).
- AC:
  - two seeds with different lords produce bosses that fight measurably differently (test on
    composed stats and behaviours);
  - killing the boss yields the seal and records the death.

**T1.13: Gash interior** · STRETCH · deps: T1.12
- 3-5 strata themed by doctrine, ending in the lord's court (D43); re-themes when the holder
  changes. Until this lands, the existing seal-site strata stand in.
- AC: entering a seal site produces the doctrine's theme; a holder change re-themes it on next
  entry.

**T1.14: Ascendancy events** · STRETCH · deps: T1.6
- Each Doom Clock stage emits a chronicled Tarmin-Zul ascendancy event (D42). Doom numbers are
  untouched (Invariant 7).
- AC: a test advances doom stages and finds one event per stage; `DoomManager` values match a
  baseline.

### Slice 2: The surface war

Exit criterion: a war front crosses the map; the player gets the warning; staying produces a
battle that routs, and the field pays out.

**T2.1: War clock and fronts** · MVP · deps: T1.6
- `WarClock` ticks on player turns on the surface and underground, separately from the Doom
  Clock (D22). Declared wars spawn `Front`s that move between the warring houses' territories
  along the overland chunk graph.
- AC: fronts move deterministically; a front can cross a road; a war's end removes its front.

**T2.2: Fronts on the map** · MVP · deps: T2.1
- The expedition map and minimap show fronts with house colours (D15).
- AC: fronts are visible; they update per war-clock tick; colours come from `UiTheme`/`HudSkin`.

**T2.3: Warning window** · MVP · deps: T2.1
- When a front reaches the player's chunk: horns and drums, scouts streaming past, a chronicle
  line, and N turns (a tuning knob, default 12) to leave by an edge or descend (D15).
- AC: leaving within the window means no battle; staying starts one.

**T2.4: Battle director** · MVP · deps: T2.3, T1.5
- `BattleDirector` runs D31:
  - 30-40 live combatants;
  - per-side reserve and morale;
  - edge reinforcements;
  - telegraphed volley and charge hazard tiles;
  - targeting order: enemy soldier, then the player if adjacent or struck, then a melee spill
    with a tunable chance.
- AC:
  - a headless test runs 50 battles to completion, all routing or hitting the turn cap;
  - frame time holds at 40 combatants on the dev machine (record the number);
  - soldiers prefer rivals over the player.
- Recorded 2026-10-09 (`--playtest=surface-wars-megabeasts`, dev machine): avg 6.4 ms a frame
  over 398 frames with 30-32 combatants on the field, worst single frame 254 ms. That battle
  rolled 32, so 40 is not yet measured; the scenario asserts a 30 fps average whenever 30 or more fight.

**T2.5: Battle lines** · MVP · deps: T2.4
- Once joined, the chunk edges hold ranks of soldiers. Fleeing means cutting through a line (D16).
  No themed-chunk gate sealing.
- AC: the player can exit through any edge after fighting through; never soft-locked (test with
  a scripted flee).

**T2.6: Rout and the player's hand** · MVP · deps: T2.4
- A side routs when reserve and morale collapse. Killing a war-captain drops that side's morale
  sharply (D17). The outcome is written back to the history.
- AC: killing the captain routs that side within N turns in the test; the outcome appears in the
  chronicle.

**T2.7: The spoils** · MVP · deps: T2.6
- Survival drops the field (arms of the fallen), trophies (sigils, signet rings, banners as items
  that also unlock fragments and act as a currency), and any fallen lord's gear (D18).
- AC: surviving a test battle yields all three kinds; trophies unlock chronicle entries.

**T2.8: Gash holder change** · MVP · deps: T2.6, T1.12
- A war that takes a great house's seat transfers the gash. The seal boss becomes the new lord
  (D32), announced on the map and in the chronicle.
- AC: a forced conquest swaps the boss; `SEALS_REQUIRED` and seal state are unaffected.

**T2.9: War audio** · STRETCH · deps: T2.3
- Horns, war drums, a crowd-of-battle ambience and steel clash. Sources are in Section 5.
- AC: cues fire on warning, joining and rout.

### Slice 3: Megabeasts

Exit criterion: each world has 3-6 named megabeasts in the chronicle, at least one reachable from
strata 1-2, hunting a player who disturbs it.

**T3.1: Archetypes and modifiers** · MVP · deps: T1.4
- Author about 6 archetypes (wyrm, titan, colossal hive, drowned thing, bone-choir, fungal mother)
  and a modifier table (material, breath or affliction, weakness) in
  `assets/data/megabeasts.json` (D33). Each archetype reuses a sprite from the existing roster or
  `docs/game_assets/old` until art lands.
- AC: the loader test passes; every archetype has a sprite key that resolves.

**T3.2: Megabeasts in prehistory** · MVP · deps: T3.1
- Generate 3-6 per world with lairs by depth, and at least one at strata 1-2 (D35). Un-stub the
  megabeast events: raids, bargains, and a house setting a beast on a rival (D19).
- AC: a 100-seed test passes the count and shallow-lair rules; megabeast events appear in
  chronicles.

**T3.3: Lair and hunt** · MVP · deps: T3.2
- Spawn at the lair; roam within a radius; hunt across chunks after noticing the player via the
  `MonsterPursuitManager` pattern; death is permanent and chronicled (D34). Set `G_UNIQ` so the
  same beast never spawns twice.
- AC: pursuit crosses a ladder in a test; a dead beast never respawns after save and load.

### Slice 4: The underground

Exit criterion: strata 2-4 contain biomes and towns; the player can talk, trade, take a
history-generated quest, and be betrayed by a suborned town.

**T4.1: Underground biomes** · MVP · deps: none (can run in parallel with slice 2)
- Depth bands with regional noise: fungal forest, flooded halls, ossuary, magma deeps, plus the
  maze (D36). Replace the "everything below level 1 is `Biome.MAZE`" path in `WorldManager.loadChunk`.
- AC: each biome generates at its band; the maze still appears; existing strata tests pass.

**T4.2: Town generation** · MVP · deps: T4.1, T1.4
- A persistent town chunk template with a generated layout, about one per 10-15 underground chunks
  at strata 2-4, in the permitted biomes only (D37). NPCs are named characters in the
  `HistoryWorld`. The existing `ShopkeeperNpc` becomes the town merchant.
- AC: towns persist across save and load; NPC names match history characters.
- Done as ADR 0005: the history keeps 24 settlements of mortals who age, die and are succeeded by
  family; a map town binds to one on first sight (saved). Towns past the 24th are made up, without
  history folk. Exiles of the houses are taken in besides.

**T4.3: Qud-light talk** · MVP · deps: T4.2, T1.8
- A Talk, Trade, Rumours, Quest, Leave menu (D38). Lines come from the chronicle grammar.
  Rumours draw on recent seasons, including wars on the surface. It is a UI overhaul component.
- AC: every menu path works; rumours reference real recent events.

**T4.4: Allegiance and standing** · MVP · deps: T4.2
- Mortal allegiances (refugee councils, Goblin Clans, Outcasts) with feuds between them (D40).
  Standing is per town with allegiance spillover (D41). Hostile towns turn their guards on the
  player.
- AC: a crime drops standing and guards turn hostile; spillover moves sister towns.

**T4.5: Generated quests** · MVP · deps: T4.3, T4.4
- Quest templates driven by the history: recover a sigil, unmask a house agent, slay or pacify a
  megabeast, carry a message through a front (D39). Completion is a history event.
- AC: each template generates from a test world and completes; completion appears in the
  chronicle.

**T4.6: Suborned towns** · STRETCH · deps: T4.4, T1.6
- A town can be secretly suborned by a house; a betrayal event at a season reveals it (D40).
- AC: forced subornation reveals itself through fragments before the betrayal; the betrayal
  flips the town hostile.

---

## 5. Assets

Surveyed in `docs/game_assets` on 2026-10-08. Licensing is recorded in `docs/asset-licenses.md`.
Per the usual workflow: use what's here, and for each gap write an AI-generation prompt in
`docs/DEsign/event-art-prompts.md` style and ship a stand-in.

| Need | Source on hand | Gap |
| :-- | :-- | :-- |
| House banners, heraldry | `POLYGON_Viking_Realm/.../Textures/Banners` (Banner_01-07, Deer, Dragon); Kenney Fantasy Town Kit banners; camp `FLAG BANNER.stl` | Sigil glyph set for generated heraldry |
| War aftermath (gibbets, cages, bones, skulls, hooks) | `POLYGON_Goblin_War_Camp` gibbets, bone building parts, wagon cage; Generic `SM_Gen_Prop_Hook_01`, `SM_Gen_Prop_Skull_01`; DungeonProps skull | Impalement stake |
| Lord's court | Viking Realm `SM_Prop_Throne_01` | Doctrine-specific court dressing |
| House soldiers | Existing roster (D27) | Demonic Maze roster (later upgrade) |
| Megabeasts | `old/`: dragon, purple_worm, hydra, beholder, giant, u_hulk, flayer, `tarmin_god` | Hive, drowned thing, fungal mother |
| Towns | `POLYGON_Adventure_Pack` village buildings 01-07, huts, stalls, market; Adventure peasant character; Kenney Fantasy Town Kit; Kenney medieval blacksmith tile | Underground-adapted facades |
| Underground biomes | DungeonProps cave tiles and `LavaTexture`; Adventure stalagmites; Alpine stalactites and ice; Kenney mushrooms (cartoon; tint or replace) | Fungal forest, flooded halls, ossuary tilesets |
| War audio | Retro bundle darkwave drum loops; `FL_DW_150_Synth_Pad_Horn_Am` | **Medieval Fighting** and **Horror Screamers** folders exist only as `__MACOSX` stubs in the SFX bundle. The real audio was never extracted. Re-extract the bundle before T2.9. |

---

## 6. Agentic process

### Roles
- **Facilitator** (`.claude/agents/facilitator.md`): owns the board, triage, review gates and the
  MVP cut line. It dispatches work and does not write feature code.
- **Implementer agents**: one task each, on a branch `feat/hm-<task-id>-<slug>` from `develop`,
  test-first where a test is named in the AC (use the `tdd` skill).
- **Human (Dennis)**: reviews prose dumps (T1.8), screenshots (T1.11, T2.2, T4.3), art prompts,
  and every merge to `master`.

### Tickets
Each task becomes one GitHub issue on `bluephoenixmedia/Tarmin2Revised` (see
`docs/agents/issue-tracker.md`):
- **Title:** `[HM T1.4] Prehistory generator`.
- **Body:** the task text plus its decisions.
- **Labels:** `houses-of-the-maze`, `slice-<n>`, `mvp` or `stretch`, and a triage label from
  `docs/agents/triage-labels.md`.
- **Dependencies:** GitHub native issue dependencies.

A task is `ready-for-agent` when its dependencies are closed. Tasks whose AC needs a human
judgement call (prose review, art) are `ready-for-human` for that step.

### Definition of done
1. Every AC holds, each one ticked in the PR body with evidence (test name, screenshot or number).
2. `./gradlew :core:test` is green. If the run fails with a test-JVM exit and no failing test,
   rerun it once before treating it as real.
3. The game boots and the feature is reachable (use the `run` skill).
4. `/code-review` against `develop` is clean on both axes. Standards findings are fixed; spec
   findings are fixed or answered in the PR.
5. Merged to `develop` with a conventional commit; issue closed with a link.

### Defects
A defect is any failing AC, broken invariant (Section 3), regression in an existing test, or
contradiction with Section 1. It gets an issue labelled `bug`, `houses-of-the-maze` and
`needs-triage`, and links the task it broke. Severity:
- **S1:** blocks boot, corrupts saves, or breaks an invariant. Stop the slice.
- **S2:** breaks an MVP AC.
- **S3:** everything else.

### MVP cut line
Per slice: all MVP tasks, in dependency order. STRETCH tasks start only when every MVP task in
the slice is merged, and are the first to be cut when a slice runs long. The facilitator may move
a task between MVP and STRETCH only by recording the reason in the slice's tracking issue. It must
never cut T1.9, the content-ceiling test.

---

## 7. Deferred (not in this plan)

- Diplomacy with the Maze (excluded by D10).
- Battles inside the Maze (D13). Revisit after slice 2.
- A demonic art roster to replace D27's reuse.
- Blight underground (still out of scope, as in the 0.0.2 requirements).
