# Implementation Plan: The Living War

> **Status:** Approved design, not started. Builds on the shipped Houses of the Maze
> (`Houses of the Maze - How It Works.md`, `Implementation Plan_ The Houses of the Maze.md`).
> **Source:** design grilling session, 2026-10-10 (decisions Q1-Q25, recorded below as W1-W40).
> **Branching:** one branch per phase, `feat/living-war-<n>-<slug>` from `develop`, merged back to
> `develop` after the full suite passes and a play-test. Never `master`.

The war of the houses is no longer a story the player hears about after the fact. It is happening
**all around them, all the time**. Horns carry across the overland, smoke stands on the horizon,
columns of soldiers march past with banners and torches, and the stone underfoot shakes when a gash's
house is fighting. The player is a small cog in a gigantic war. They can **join it**, earning a
house's favour, swearing to it, and fighting its contracts, or **use it**, slipping through its battles
as a rogue and sneaking into Castle Tarmin while a siege has the defenders busy.

---

## 1. Decisions (single source of truth)

Every task traces to these. A task that contradicts one is a defect in the task.

### The two layers

| # | Decision |
| :-- | :-- |
| W1 | **Two layers of war.** The history (`HistorySimulator`) decides who is at war, who wins each season, and who holds what. On the surface, a second layer of **encounters** (skirmishes, columns, raids, camps, aftermath, sieges) makes the war visible and audible. Encounters write no history unless the player takes part (W12). |
| W2 | **Encounter kinds:** skirmish (two war-bands of 6-10 clash until one routs, ~30-60 turns); marching column (8-15 soldiers in file with banners and torches, crossing chunks toward a front, ~20 turns to pass); raiding party (4-6 soldiers burning a holdfast, shelter road or town entrance); war camp (tents, fires, sentries and a captain near a front, for as long as the war lasts); pitched battle (the existing `BattleDirector`); aftermath (bodies, crows, burnt ground, broken banners and loot, lasting 3 sleeps); siege (W33). |
| W3 | **Encounters are scheduled, not simulated.** Like fronts, they are a pure function of the world seed, the war clock and the chunk. They are never saved; a reload sees the same war. Only what the player changes is saved: aftermath fields, favour, oath, contracts, destroyed columns and camps, and siege state. |
| W4 | **Frequency targets (surface, tuning constants):** while any war is active, some war is always within earshot (3-6 chunks); a **visible encounter** (skirmish, column or raid) about every **80-120 overland turns**; about **one pitched battle per expedition**. |
| W5 | **Bias toward the player.** The scheduler picks *which chunk* the next encounter crosses so it falls near the player. That is how frequency targets are met without moving the history. |
| W6 | **A war at the start.** If prehistory ends with no active war, the most aggrieved house declares one when live play begins. This is a deterministic deed, so it replays. |
| W7 | **Border raids in peacetime.** Houses that bear a grudge but are not at war still send occasional raiding parties, so peace is never total silence. |
| W8 | **The first skirmish is guaranteed.** On the first expedition, within about **150 turns** of leaving the shelter, a skirmish crosses a chunk next to the player: horns first, then it comes into view. It teaches the player that they can fight or slip past. |

### Hearing and seeing the war

| # | Decision |
| :-- | :-- |
| W9 | **Distant battle sound** by chunk distance, panned toward the fight (left/right): **adjacent**: clash of steel, screams, arrow volleys, beast roars, shouted orders (surface); dull thuds and dust falling from the ceiling (underground). **2-3 chunks**: horns and drums, a low roar of many voices (surface); a rumble through the stone and torches flickering (underground). **4-6 chunks**: a lone distant horn now and then, drums at the edge of hearing (surface); underground, silence, *unless the gash's own house is at war*, in which case its interior shakes. |
| W10 | **Seeing the war (surface), all seven, built in this order:** (1) smoke columns on the skybox horizon toward each active front, black and lit orange from below at night; (6) aftermath fields that stay scarred for 3 sleeps; (5) marching columns; (7) war camps from the Goblin War Camp and camp-scatter packs; (4) arrow volleys arcing in the sky over an adjacent chunk; (3) signal beacons lit in a chain toward the player when a house musters; (2) fire glow on the horizon at night. |
| W11 | **Sound sourcing.** The battle bed is built from existing pieces (`war_horn`, `war_drums`, `arrow_1-4`, metal hits, roars, thunder, the ABYSS loops for underground rumble), overlapped and randomised, then filtered and attenuated by distance. Gaps (a many-voices battle roar loop, a distant clash loop, crows, marching feet and armour jingle, camp ambience) get stand-ins and an AI-generation prompt each. The user is also sourcing new audio. |

### The player in the war

| # | Decision |
| :-- | :-- |
| W12 | **A skirmish the player joins** nudges the history: winner +2 strength, loser -2, plus favour for the player. It enters the chronicle **only when a named captain dies** in it. A pitched battle the player stays in is still a full `BATTLE_WITNESSED` battle. |
| W13 | **Noise masking.** In a battle's chunk and its neighbours, monsters' hearing range is cut sharply: the din covers the player. Sight is unchanged. |
| W14 | **Busy soldiers.** Soldiers in combat target enemy soldiers first, and turn on the player only if struck or if the player comes within 1 tile. |
| W15 | **Slipping the lines.** A player who crosses a front's chunk during a battle and never attacks does not trigger the "lines close" lock. |
| W16 | **The polymorph disguise.** If the player's random polymorph form is a monster type on a house's doctrine roster, soldiers of **every house fielding that type** take them for one of their own. They ignore the player unless struck, or until the player has been adjacent for 3 turns. **Those houses' enemies see an enemy soldier** and may attack. The disguise ends with the form. The Antlered Host's roster (Minotaur, Orc, Hobgoblin, Gargoyle, Ogre) makes castle defenders take the player for their own. |
| W17 | **Tabard disguise is deferred** until the tabard exists (W24). The polymorph disguise (W16) is the disguise for now. |

### Favour and the oath

| # | Decision |
| :-- | :-- |
| W18 | **Favour per house, -100 to +100.** Enemy ≤ -50: its soldiers hunt the player, and bounty hunters come. Distrusted, -49 to -10: hostile. Unknown, -9 to +24: hostile on sight, as today. Tolerated, +25 to +59: its soldiers ignore the player unless struck. Friend, ≥ +60: soldiers salute, its camps let the player rest and trade, and an oath is offered. |
| W19 | **Favour is earned** by killing a house's enemies near its soldiers, winning skirmishes on its side, and returning its signets. **It is lost** by killing its soldiers, aiding its enemies, and killing its heralds. Favour with one house costs a little with that house's enemies (half, reversed, the same spillover rule as town standing). |
| W20 | **The oath** is offered by a war-camp captain at Friend. Taking it gives: the house's tabard (W24); contracts (W25); its camps as safe rest points; its soldiers fighting beside the player. Its enemies treat the player as Enemy on sight. |
| W21 | **Breaking the oath** (attacking the sworn house, or swearing to another) is a chronicled **BETRAYAL** naming the player. Every house's favour drops, and the former house sends hunters. |
| W22 | **The sworn house's gash court still fights for its seal**, unless the player completes the **"the lord grants you the seal"** contract chain (W26). |
| W23 | **This plan supersedes Houses D10 and invariant 4** ("violence only; the Maze is hostile"). Favour and the oath are a path to an allied house. Every house still starts hostile (Unknown). |
| W24 | **The tabard** is a new worn chest item: one tabard sprite tinted with the doctrine's primary and secondary colours, plus a small sigil charge. It is drawn through the existing paper-doll overlay. **The doll and its overlay offsets are never edited** (UI overhaul rule). Check `docs/game_assets` first; if nothing fits, ship a stand-in and give the user a generation prompt. |
| W25 | **Contracts:** hold a chunk; kill an enemy captain; escort a column; burn an enemy camp. Each one gives +10 to +20 favour and gold. **Every third contract** gives a **doctrine relic**: a weapon or armour in the house's style, rolled from the existing rare-variant/modifier pool as a fixed rare. |
| W26 | **The seal-grant chain:** three hard contracts at Friend or better while sworn, ending with the house lord handing the player its gash's seal. |

### Keeping up with the war

| # | Decision |
| :-- | :-- |
| W27 | **The War Table**: a new shelter prop beside the Archive Lectern, opening its own full-screen war map. It shows wars, fronts, each season's score, sieges, seats at risk, the player's favour with each house, and active contracts. |
| W28 | **Battle reports on the expedition map:** fronts drawn as clashing banners, fresh battlefields marked, columns as arrows. |
| W29 | **Heralds and couriers** walk the shelter roads between seats. Reading a herald's proclamation (or taking it from the body) gives the news early: it unlocks a fresh event. Killing a herald costs that house favour. |
| W30 | **The HUD war tally**: while the player is in a skirmish or battle chunk, two house banners with a morale tug-of-war bar. |
| W31 | **The Knell is not widened.** It stays rare. A siege beginning and ending tolls it (W33); nothing else new does. |

### Underground

| # | Decision |
| :-- | :-- |
| W32 | **This plan supersedes Houses D13** for two cases only. (1) **Gash interiors** (strata 2-3 under a seal site): while the holding house is at war, enemy **raiding parties** come down into the gash, and its court is **thinner** because sworn swords leave to fight. (2) **Towns** (strata 2-4): occasional **raids on the town's gate** by a house, which the guards and the player can drive off for standing and favour. **No columns or skirmishes underground**: the strata stay quieter and deeper, where the war is heard as rumble (W9). |

### The siege of Castle Tarmin

| # | Decision |
| :-- | :-- |
| W33 | **SIEGE_LAID** is a new history event. A house at war with the Antlered Host, with strength ≥ 70, may besiege the castle instead of fighting at a front. The siege lasts **3-6 seasons**, or until the attacker's strength breaks (**SIEGE_BROKEN** / **SIEGE_LIFTED**). The Knell tolls when it begins and ends. |
| W34 | **The look and sound of a siege:** siege camps ring the castle chunk; fires and smoke columns rise around the castle silhouette; siege fire (flaming projectiles) arcs onto the walls; red lightning when Tarmin answers; horns and drums without end. |
| W35 | **The breach.** While a siege lasts, a breach opens in the castle wall. Entering by the breach needs **1 seal** if the player is Friend or sworn with the besieging house, and **2 seals** otherwise. This plan supersedes Houses invariant 8 for the breach only; the gate still needs 3. |
| W36 | **Beyond the breach** (castle interior not yet built): a **stub**, "the castle's halls": one themed chunk where both sides fight in the halls and the defenders are thinned, ending at a sealed inner door ("the throne lies beyond"). The gate leads to the same place once it opens. The real interior gets its own plan. |
| W37 | **Sworn players can join the siege** as an assault contract, which also needs 1 seal. |

### Process

| # | Decision |
| :-- | :-- |
| W38 | **Build it all, in five phases** (Section 4), each playable on its own. Phase 1 alone delivers "the war is all around me". |
| W39 | **Rising through the ranks** of a house is a separate plan, written after its own grill: the player may abandon the main story and climb a house's ranks, ending in a duel with the current lord to take the house. It builds on W18-W26. |
| W40 | **Polymorph remains numbers-only** apart from W16. The richer dreamsmoke-style system stays deferred. |

---

## 2. Glossary

Add to `CONTEXT.md` in L1.1.

- **Encounter**: a scheduled piece of the surface war (skirmish, column, raid, camp, aftermath, siege).
  Not history, unless the player takes part.
- **War-band**: one side of a skirmish.
- **Column**: soldiers marching between a seat and a front.
- **Earshot**: the chunk radius within which a battle is heard (W9).
- **Favour**: a house's regard for the player (W18).
- **Sworn**: bound to a house by the oath (W20).
- **Contract**: a task a sworn house gives (W25).
- **Herald**: a house courier on the roads, carrying a proclamation (W29).
- **Siege, breach**: W33-W36.

---

## 3. Architecture

```
gamedata/history/war/
  EncounterScheduler   pure: (seed, warClock, history, playerChunk, saved overrides) -> encounters
                       in and around a chunk. Bias toward the player (W5), first skirmish (W8)
  Encounter            kind, houses, chunk(s), start/end clock, route (columns)
  WarSoundscape        pure: encounters + player chunk/level -> layered cues with volume and pan
  SiegePlanner         pure: siege state from the history
gamedata/history/favour/
  Favour               per-house favour, spillover, tiers
  Oath, Contract       sworn house, contracts, relic counter, seal-grant chain
managers/
  EncounterDirector    puts an encounter's monsters, props and aftermath into the live chunk
  WarAudioManager      plays WarSoundscape cues through SoundManager, ducks under the Knell
rendering/
  Skybox3DRenderer     smoke columns, fire glow, arrow arcs, beacons, siege fire
screens/WarTableScreen the War Table
```

### Invariants

1. **Determinism** (Houses invariant 1). The scheduler draws only from streams keyed on the seed,
   the war clock and the chunk. A reload sees the same encounters.
2. **Headless.** `EncounterScheduler`, `WarSoundscape`, `SiegePlanner`, `Favour`, `Oath` and
   `Contract` make no `Gdx.*` calls and are covered by JUnit.
3. **History changes only through deeds.** Every effect the player has on the history (W6, W12,
   W21, W26, W33) is a `PlayerDeed`, so the save replays it.
4. **Save compatibility.** A save without favour, oath or siege data loads as Unknown with every
   house, unsworn, no siege.
5. **The Knell stays rare** (W31).
6. **UI text** passes `UiGlyphs.sanitize`; colours and sizes come from `UiTheme` and `HudSkin`.
7. **The paper doll is protected** (W24).
8. **Performance.** Distant encounters are sound and sky only. Monsters exist only for encounters
   in the player's chunk.

---

## 4. Phases and tasks

Each task: AC, test first where a test is named. Play-test scenarios use the fresh-snapshot save
procedure.

### Phase 1: The war you hear and see

Exit criterion: on a new game, the player hears war within the first minutes, sees smoke on the
horizon, meets a skirmish within ~150 turns, and comes across columns, raids, camps and aftermath
regularly on the surface.

**L1.1: Glossary** · deps: none. Add the Section 2 terms to `CONTEXT.md`.

**L1.2: A war at the start (W6)** · deps: none
- If no war is active when live play begins, a deterministic deed makes the most aggrieved house
  declare war.
- AC: 100 seeds, each starts with ≥ 1 active war (test); a save round-trip keeps the same history.

**L1.3: EncounterScheduler (W2-W5, W7, W8)** · deps: L1.2
- AC: same seed and clock give the same encounters (test). Over a simulated 2,000-turn overland
  walk, a visible encounter every 80-120 turns, and some war always within 6 chunks while a war is
  active (test). The first skirmish is adjacent within 150 turns on the first expedition (test).
  Border raids happen in peacetime between grudge-holding houses (test).

**L1.4: EncounterDirector: skirmish, column, raid** · deps: L1.3
- Spawns war-bands and columns from doctrine rosters; columns walk their route across chunks;
  skirmishes fight to a rout (reuse `BattleModel`); raids burn props.
- Joining a skirmish applies W12 as a deed.
- AC: a skirmish routs within 30-60 turns with no player; joining it nudges strength (test);
  a captain killed in it is chronicled (test).

**L1.5: War camps and aftermath (W2, W10.6-7)** · deps: L1.4
- Camps from the Goblin War Camp and camp-scatter packs; aftermath fields saved for 3 sleeps.
- AC: an aftermath field persists across save/load and is gone after 3 sleeps (test); screenshot review.

**L1.6: WarSoundscape and WarAudioManager (W9, W11)** · deps: L1.3
- Re-extract the SFX bundle's Medieval Fighting, Monsters, Foley, Ambience & SFX and Horror
  Screamers folders (currently only `__MACOSX` stubs); use what fits; stand-ins plus prompts for gaps.
- AC: the cue table from W9 per distance and level (test); panning follows the bearing (test);
  ducks under the Knell; play-test by ear.

**L1.7: Sky: smoke columns (W10.1)** · deps: L1.3. Smoke on the horizon toward each active front, lit from below at night. AC: screenshots by day and night.

**L1.8: Play-test scenario `living-war`**: new game, walk out, assert the first skirmish, screenshots
of smoke, a column, a camp and an aftermath field.

### Phase 2: The rogue

**L2.1: Noise masking (W13)**. AC: a monster's hearing range drops in a battle chunk and its neighbours (test).
**L2.2: Busy soldiers (W14)**. AC: soldiers in combat prefer enemy soldiers; they turn on the player only when struck or adjacent (test).
**L2.3: Slipping the lines (W15)**. AC: crossing a front during a battle without attacking does not lock the lines (test).
**L2.4: Polymorph disguise (W16)**. AC: a roster form is ignored by every house fielding it, attacked by their enemies, and noticed after 3 adjacent turns (tests); a Minotaur form passes Antlered Host defenders.
**L2.5: Underground raids (W32)**. Gash raiders and a thinner court while the holder is at war; raids on town gates. AC: tests for both; town standing and favour from driving a raid off.

### Phase 3: Keeping up

**L3.1: Heralds (W29)**. AC: heralds walk roads between seats; a proclamation unlocks a fresh event; killing one costs favour (tests).
**L3.2: The War Table (W27)**. A prop beside the Lectern, plus a `WarTableScreen`. AC: shows wars, fronts, season scores, sieges, seats at risk, favour, contracts; screenshot review.
**L3.3: Map battle reports (W28)**. AC: fronts as clashing banners, battlefields marked, columns as arrows; screenshot review.
**L3.4: HUD war tally (W30)**. AC: banners and a morale bar in a battle chunk, hidden elsewhere.
**L3.5: Sky: arrow volleys, beacons, fire glow (W10.2-4)**. AC: screenshots.

### Phase 4: The sworn

**L4.1: Favour (W18, W19)**. AC: the tiers drive soldier hostility (test); spillover to enemies (test); saved.
**L4.2: The oath (W20, W21, W23)**. AC: offered at Friend by a camp captain; sworn effects (tests); breaking it is a chronicled BETRAYAL naming the player, drops every house's favour, and sends hunters (test).
**L4.3: The tabard (W24)**. AC: a tinted tabard per doctrine on the doll overlay; the doll's coordinates unchanged (existing doll tests stay green).
**L4.4: Contracts and relics (W25)**. AC: each contract kind completes (tests); every third contract gives a doctrine relic.
**L4.5: The seal-grant chain (W22, W26)**. AC: three contracts, then the lord grants the seal; `ShelterNetwork` counts it (test).

### Phase 5: The siege

**L5.1: SIEGE_LAID / SIEGE_LIFTED / SIEGE_BROKEN (W33)**. History events, grammar for every bias and for the Knell. AC: sieges occur under the trigger (test); deterministic; tolled.
**L5.2: The siege's look and sound (W34)**. AC: screenshots; play-test by ear.
**L5.3: The breach (W35)**. AC: 1 seal when Friend or sworn with the besieger, 2 otherwise (tests); the gate still needs 3.
**L5.4: The castle's halls stub (W36)**. AC: entering the breach leads to the halls chunk; both sides fight; the inner door is sealed.
**L5.5: Siege assault contract (W37)**. AC: sworn players get it; it needs 1 seal.

---

## 5. Assets

| Need | On hand | Gap |
| :-- | :-- | :-- |
| Battle sounds (earlier) | `sounds/war/war_horn.ogg`, `war_drums.ogg`; `sfx/arrow_1-4`; `metal_hit*`, `meat_hit`, `monster_roar*`; `thunder_1-5`; ABYSS dark-cinematic loops | Many-voices battle roar loop, distant clash loop, crows, marching feet and armour jingle, camp ambience. **Re-extract** the SFX bundle's Medieval Fighting, Monsters, Foley, Ambience & SFX and Horror Screamers folders (only `__MACOSX` stubs today). The user is sourcing more. |
| Battle sounds, sourced 2026-10-10 (`assets/sounds/war/`, Freesound) | **Adjacent:** `376646` vikings in battle (208 s bed), `349382` medieval battle 1500 AD (141 s bed), `782984` wading into zombies, `547600` sword attack, `614068` demon pain (soldier screams), `222608` archers shooting + `866990` dart/arrow (volleys). **2-3 chunks:** `188815` battle horn, `274223` war drum loop, `342465` tribe drum loop, plus the adjacent beds low-passed. **4-6 chunks and underground rumble:** `823852` distant rumbling battle (143 s bed), `149966` muffled distant explosion, `372086` distant explosion. **Siege (W34):** `486086` artillery barrage (siege fire), `682401` explosion echo, `783023` fireworks (trim to bursts), `244796` war-of-the-worlds horn (the siege's horn, or Tarmin answering). **Bells:** `703236`, `867756` (spare knell/siege tolls) | Crows, marching feet and armour jingle, camp ambience. **Before shipping:** convert every file to 16-bit OGG (libGDX `Sound` can't load 24-bit/float WAV or FLAC); trim and loop the beds and stream them as `Music`; keep the 230 MB and 55 MB originals out of `assets/`; record each file's Freesound licence and attribution in `docs/asset-licenses.md`, and drop any CC BY-NC file. |
| Camps | `POLYGON_Goblin_War_Camp` (tents, palisades, gibbets, banners); `fantasy-camp-scatter-terrain` (tents, campfires, supplies); `assets/models/camp` | none |
| Banners | Viking Realm banners; Kenney flags and banners | none |
| Smoke, fire, siege fire | `POLYGON_Particle_FX`, `BearFX Explosions` | a sky smoke-column texture if particles don't read at distance |
| Tabard | none found yet | a tintable tabard overlay sprite: stand-in plus prompt |
| Castle siege | `assets/models/castle_citadel.glb` | a breach in the wall; siege engines |

---

## 6. Deferred

- **Rising through the ranks** of a house to duel its lord (W39): its own plan after its own grill.
- **The Castle Tarmin interior** beyond the halls stub (W36): its own plan.
- **The tabard disguise** (W17).
- **Dreamsmoke-style polymorph** (W40).
