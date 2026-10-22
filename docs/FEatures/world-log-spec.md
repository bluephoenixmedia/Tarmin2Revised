# Spec: the World Log and the myth of the Eternal Champion

The spec the map **Wayfinder: the World Log and the myth of the Eternal Champion** ([#265](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/265)) was charting toward. Each section names the ticket that decided it; that ticket wins if this page disagrees. Vocabulary is in `CONTEXT.md`: Incarnation, Eternal Champion, soul's name, origin, World Log, Nemesis, Telling, Era, Bridge, Age, Reckoning, Shifting, Enduring, Tarmin's Hunger, Bridge of Souls, Old gods, Kindred, Treasure of Tarmin.

Sources: `docs/research/existing-lore.md` (branch `research/existing-lore`), `docs/research/original-treasure-of-tarmin.md`, `docs/research/qud-df-history-generation.md`, `docs/research/tarmin-unreleased-sequel.md` (each on its `research/*` branch), and the screen prototype `docs/prototypes/PROTOTYPE_world_log.html` on `prototype/world-log`.

## Already built

- **The history** (`gamedata/history`): Houses, Figures, Megabeasts, towns, wars, the Knell, the Chronicler and its JSON grammar, Epithets, fragments read at the Archive Lectern, and `ChronicleDump`.
- **The history is a replay** ([ADR 0004](../adr/0004-dynamic-house-factions.md)): its own seed, the seasons elapsed and the ordered `PlayerDeed` log. Death re-rolls the world seed but never the history seed. `PlayerDeed.Kind.SEEKER_FELL` already records a death.
- **The Shifting already happens:** `GameScreen` calls `WorldManager.wipeExploredWorldOnDeath()`, which deletes the explored chunks and rolls a new world seed. The Altar, Divinity, the chronicle and road progress survive it.
- **Tarmin's Hunger** (`DoomManager`): deaths to 50, then the Warden (`gamedata/boss/BridgeBoss`, the Iron Golem). Its defeat resets the count, and each summoning is harder. A death while it stands calls `SaveManager.wipeActiveSlotOnApocalypse()`; this spec removes that.
- **The soul's name today** is `SlotMetadata.characterName`, defaulting to "Hero". Graves and ghosts read it through `BonesManager`.

## 1. The myth ([#267](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/267), [#272](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/272), [#276](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/276))

- **Tarmin** is no Minotaur, and the Minotaurs are his generals. He is a prisoner of hell building the **Bridge of Souls** from the maze's dead, to cross into the mortal world. Each Champion death is a stone.
- **The condemned** are raided by the Antlered Host, or tithed by mortal kingdoms that pay convicts to keep the raids away.
- **The old gods** keep the Champion's soul and send it back against him. There are thirteen of them: the original game's monsters, woken from the world's forgetting.
  - The Shelter, the Altar, Divinity and the Statues are theirs.
  - The Champion's first life was the original adventurer. It slew their warden, the Minotaur King, and took the **Treasure** (the seal on the Rift), which let Tarmin-Zul in.
- **The Houses** fight over who rules hell once Tarmin crosses.
- **The seeker's curse:** the soul is never allowed to stay dead.
- **Lore-text rewrites** are already an issue: "Lore text: the ritual becomes the Bridge, and Tarmin is no Minotaur" ([#273](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/273)). #276 supersedes its "the game is lost": a crossing no longer ends the save.

## 2. A life ([#268](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/268), [#274](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/274))

- **The soul's name** is typed at a new game, or produced by a name generator on the same screen. It stays the same across every life and Age.
- **Each Incarnation gets a generated identity:**
  - a name;
  - a **home settlement**, taken from the history's real mortal towns;
  - an **origin**: *raided* (with a raid on that settlement invented as its cause) or *tithed* (with a crime and the tithing kingdom).

  The identity is seeded, so it can be reproduced. It is shown at rebirth with **one reroll** of the whole identity. Generate it the Caves of Qud way: invent the event first and its causes afterwards, from the state that exists.
- **A life's record:**
  - It holds up to about 8 notable lines, most notable first:
    - every `PlayerDeed` the life made;
    - a new deepest stratum for the soul;
    - the life's first Heavy, and any boss;
    - Adept or higher in a Family;
    - a Combat Art the soul learns for the first time;
    - seals won;
    - a Nemesis slain;
    - the Warden destroyed.
  - Then the death: epitaph, killer, place, turn.
  - Each line is stored as a **fact**, with a plain text and a mythic text rendered from it.
- **Storage:** life records are a list of their own in the slot. They are read together with the history but are not replayed into it. A death still enters the history as `SEEKER_FELL`, which now carries the killer: that is what makes the killer a Nemesis (section 4).
- **Older saves:** the lives before this feature become one entry, "N lives unremembered", where N is the Hunger at the time of the upgrade.

## 3. The World Log screen ([#270](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/270), [#269](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/269), #276)

- **The Ledger:**
  - the **Bridge bar** across the top: the Hunger shown as stones out of 50;
  - the lives listed newest first;
  - the open life's page: name, origin, "the Champion <soul's name> reborn", deeds, death, and "As the chronicles tell it" (the Tellings found so far).
- **Divisions:** **Age** (one Tarmin's reign) > **Bridge** (one Bridge of Souls, "the Second Bridge") > **Era** (Hunger stages 10, 25 and 40). Each division has a generated name, like a Dwarf Fortress Age, and a **Reckoning** at its break.
- **Where it opens:** a new Shelter station, and a "Life N ends" line on `GameOverScreen` that links to it. It is not on the Pause menu.
- **The Chronicle's continuous prose** stays at the Archive Lectern.
- **AGENTS.md UI rules apply:** UiTheme and HudSkin tokens, Scene2D tables, `UiNames`, `UiGlyphs.sanitize`. Coordinate the layout with "Wayfinder: Complete UI/UX Overhaul" (#209).

## 4. The Nemesis (#269)

- **Every killer of an Incarnation becomes a Nemesis,** including a common beast. It becomes a history Figure with a generated name ("Gorvak the Gnawer") and an epithet that rises with each life it takes ("Slayer of Brannoc", then "Bane of Three Lives").
- **It is Enduring:** it stays in its region through every Shifting, and grows one level per life taken.
- **It remembers:** it speaks to a later life it meets ("I remember Brannoc. He screamed too.").
- **Slaying it:**
  - is a notable deed (`SLEW_FIGURE`);
  - yields the gear of the lives it took;
  - is a record line.

## 5. Graves and ghosts (#269)

A grave reads "Here lies Brannoc of Velmere, who fell to Gorvak the Gnawer". A ghost is "the shade of Brannoc". Neither says "Fallen Hero". Named graves and ghosts are Enduring: rare ancient tombs, placed anew after a Shifting.

## 6. Tellings (#269, #274)

- **Fixed facts never change:** the name, when the life was lived, and that it died.
- **What bends:** the killer, motive and crime bend to the teller's interest. A House may claim a kill for prestige.
- **Who tells:**
  - a House tells a life only if it was involved: an oath, its figure slain, or its soldier the killer;
  - Tarmin's **Knell** tells a life whenever the Hunger reaches a new stage.
- **Where they are found:** as a new `FragmentKind` dropped in the teller's territory, read at the Archive Lectern. Once found, a Telling is shown on that life's page in the World Log.

## 7. The old gods at their shrines (#272, [#271](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/271), [#275](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/275))

- **Thirteen gods, one per statue in `assets/images/events/statue`.** The table in #272's resolution gives each shrine's god, domain and Kindred.
  - The statue "Colossus of Minotaur Tarmin" is renamed "Colossus of the Minotaur King".
- **Kindreds:** Spirit-born, War-born and Dread-born, from the original's monster classes. Each tells which weapons their sleeping kin fear.
- **The rites keep their mechanics,** retitled to their gods. The Altar's MONUMENT tree wakes more shrines.
- **Revenge of the Master:** the gods' prophecy that the old order returns. The Ghost (the Unremembered) and the Old Dwarf (Grandfather Ash) came with it.
- **The layered reveal** (#267 and #272):
  - at arrival, only revenge and "old shrines";
  - mid-game, fragments and Void glyphs show the Bridge and the tithe, and name the gods;
  - as the Hunger rises, the first life, the Treasure and the Rift;
  - at the last stand, the whole truth.

## 8. The endings (#276)

- **Nothing ends the world.** Every death is a **Shifting**. The **Enduring** are re-placed in the remade maze:
  - Nemeses;
  - Minotaur generals;
  - Gashes and their holders;
  - Houses and their lords;
  - Megabeasts;
  - the old gods' shrines;
  - named graves and ghosts.

  Everything else is generated anew. Progression carries forward. Each save slot is its own world.
- **The Warden falls:** the Bridge crumbles, the Hunger goes to 0, and a Reckoning closes the Bridge. Each summoning is harder, as today.
- **Tarmin crosses** (a death while the Warden stands): he lays waste to the mortal world, and the old gods drag him back.
  - One god is **spent**: its shrine goes dark for good.
  - The soul returns, the Hunger goes to 0, and the maze shifts.
  - The log gets the life's death, a Reckoning of the crossing, and the involved Houses' Tellings of the scramble for the throne.
  - The apocalypse slot wipe is removed.
  - What happens when the last god is spent is an open ticket on the map: "Grilling: when the last old god goes dark".
- **The Champion slays Tarmin** (the final run). This is a real choice:
  - **Return the Treasure:** the Rift is sealed and the soul rests, until the curse wakes it. The House that wins hell's war takes the throne and the name. The closing line is in the soul's name ("…returned the Treasure to the Rift, and was allowed to die").
  - **Take the throne:** that Incarnation becomes the new Tarmin, an Enduring figure, and the next life is a prisoner sent to hunt it.

  Either way an Age ends, a new Age and Bridge begin, and the Hunger goes to 0. The Tarmin fight itself is not built and is not part of this spec: build against a "Tarmin slain" hook that a playtest scenario can fire.
- **A monster slays Tarmin:** off-screen and very rare, rolled per Era in the history. The likeliest way is a House's Siege of Castle Tarmin succeeding; a top-rank Nemesis or a Megabeast is also possible. The slayer takes the name ("Tarmin-Thrax, who was the Gutter-King's Bane"), a Reckoning is told, and a new Age begins at the next Shifting.
- **Reckonings** are the Knell's accounts of a Warden's fall, a crossing or a usurpation. They are generated afterwards from the history's facts, in plain and mythic text, with the Qud and Dwarf Fortress grammar techniques. They are separate from Tellings.

## 9. The coherence check (#269)

Extend `ChronicleDump` (or a sibling tool) to generate dozens of lives, Bridges and at least one Age change from a seed, and check:
- fixed facts never contradict across the record and every Telling;
- a Nemesis's tally matches the deaths attributed to it;
- names don't repeat;
- every life falls inside exactly one Era, Bridge and Age.

It runs as a test, and it is part of the acceptance criteria of every slice that adds generated text.

## Build slices

Each is a `ready-for-agent` issue on milestone 0.0.4 and a sub-issue of the map, with its blocking edges wired natively.

| Slice | Blocked by |
|---|---|
| [The soul's name and each life's prisoner](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/279) (§2) | none |
| [A life's record, and the lives unremembered](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/280) (§2) | identity |
| [The Ledger screen, its Shelter station and the death-screen link](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/281) (§3) | record |
| [Graves and ghosts carry the life's name](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/282) (§5) | identity |
| [The Nemesis is born and endures](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/283) (§4) | record |
| [The Nemesis remembers, and its fall pays](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/284) (§4) | Nemesis born |
| [The Houses' Tellings, found as fragments](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/285) (§6) | record, Nemesis born, screen |
| [The coherence check for many lives](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/286) (§9) | Tellings |
| [Old gods: the thirteen at their shrines](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/287) (§7) | none |
| [Old gods: the layered reveal](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/288) (§7) | shrines, lore text (#273) |
| [The Enduring survive the Shifting](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/289) (§8) | Nemesis born, graves, shrines |
| [Bridges and Reckonings: the Warden's fall and Tarmin's crossing](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/290) (§8) | screen, shrines, Tellings |
| [Ages: Tarmin slain and the throne](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/291) (§8) | Bridges |
| [A monster takes Tarmin's throne](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/292) (§8) | Ages |

Still a decision: [Grilling: when the last old god goes dark](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/278). The Bridges slice has an interim rule until it resolves.

## Out of scope

- Choosing appearance or class for each life.
- Descendants and genealogy.
- The Tarmin fight and Castle Tarmin's final gauntlet.
