# Research: the lore already written

> For map #265 (the World Log and the myth of the Eternal Champion), ticket #266.
> Read on `develop` at `d951e9f7`, 2026-10-20. Every claim cites the file that owns it.
> Paths under `core/` are shortened from `core/src/main/java/com/bpm/minotaur/`.

## Summary

The canon is split across two layers written at different times:

1. **The protagonist story** (`CONTEXT.md`, decided in #158 / #176): a broken man is taken in a raid and sentenced to the maze to feed Tarmin. He is an Eternal Champion, and his deaths feed Tarmin's Hunger.
2. **The Houses of the Maze** (the Houses plan, `doctrines.json`, `chronicle_grammar.json`, `gamedata/history/`): hell is the Maze. Tarmin-Zul broke out of it through the Obsidian Rift and holds Castle Tarmin. Three great houses hold gashes into the world, and every house hates and envies him.

The two layers meet in only three places: `SEEKER_FELL`, `TARMIN_ASCENDANT` (driven by Doom stages) and Tarmin's Knell. Nothing written says *why* Tarmin wants the condemned to die in the maze, how the soul comes back, or what the houses know of the Champion. Older residue disagrees with the current canon: a "sacrificial ritual" left over from the daughter story, "the Minotaur" written where Tarmin is meant, and a Bridge that is *built* by deaths in the code but *worn down* by them in `CONTEXT.md`.

---

## (a) What is established

### The protagonist and death

- **Story.** He is a broken man who rebuilt a simple life after a tragedy. Tarmin's forces took him in a raid and sentenced him to the maze to feed Tarmin. He starts unarmoured, with only his Father's Sword, and wants Tarmin and the maze dead. He is an Eternal Champion in Moorcock's sense: "when he dies, the maze draws his soul back into a new body". (`CONTEXT.md`, Overview; origin #158 resolution "Story change (user)".)
- **Incarnation.** One life, from rebirth to death: "a different condemned prisoner each time, with a name of their own". Level, stats, skill points, Mastery and carried gear die with it. (`CONTEXT.md`, glossary: Incarnation, Mastery.)
- **Eternal Champion.** The soul, "known by one title across them all". It keeps its Known Spells, its Combat Arts and the Father's Sword, "the one thing carried over from its first life". The world keeps the unlocks, the altar stations, the chronicle, the Shelter Chest and Tarmin's Hunger. (`CONTEXT.md`; `core/gamedata/player/IncarnationLedger.java` lists SOUL: Known Spells, Father's Sword, and WORLD: Tarmin's Hunger and others; `Player.beginNewIncarnation`, `Player.java:1136`.)
- **Father's Sword.** A rusty heirloom and the weakest weapon in the game. It is a Keepsake, returns with every Incarnation, and its perk deepens with the soul's milestones. (`CONTEXT.md`; #158.)
- **The death screen** reads "THE INCARNATION LOSES" and "THE SOUL AND THE WORLD KEEP", and ends with "AWAKEN IN THE SHELTER". (`core/screens/PlayerDeathScreen.java:199-203, 330`.)
- **Rebirth place.** The Shelter. The shelter chunk is the respawn tile. (`CONTEXT.md` §Death & Stakes; `core/gamedata/boss/BridgeBoss.java` javadoc on `chunkFor`.)

### Tarmin

- **Who he is.** A god-like being who rules from Castle Tarmin and feeds on the condemned. The Houses' chronicles call him Tarmin-Zul. "He is not a Minotaur." (`CONTEXT.md`, glossary: Tarmin.) The Expedition Loop plan agrees: "take your revenge on Tarmin himself, a god-like being, not a Minotaur" (`docs/Implementation Plan_ The Expedition Loop & Progression Reboot.md`, line 9).
- **Origin.** A lord of the Maze who escaped through the Obsidian Rift. His castle is a fourth seat, and the houses hate him and covet it. (Houses plan D6; `CONTEXT.md` "The Maze ... the realm ... Tarmin-Zul escaped".)
- **In the simulation.** Tarmin-Zul is born 900 years before he rises. He is ageless (never assassinated, never dies of age), Ambitious and Cruel. He rises once, between prehistory years 180 and 260 of 300, at "the Obsidian Rift", with his house **the Antlered Host** holding Castle Tarmin at strength 90. (`core/gamedata/history/HistorySimulator.java:210, 304-329`.)
- **Titles.** The Antlered Lord, the Antlered Usurper (to his enemies), "the beast in the castle". (`assets/data/chronicle_grammar.json`, `TARMIN_ZUL_RISES` / `TARMIN_ASCENDANT`.)
- **His house's creed.** "The rift is a door, and the door is ours. All Mazes kneel to the Antlered Lord." Its mottos are "The Door Is Ours" and "Kneel Or Be The Offering". Its roster is MINOTAUR, ORC, HOBGOBLIN, GARGOYLE and OGRE, and its boss base is MINOTAUR. It is unique, so no other house takes this doctrine. (`assets/data/doctrines.json`, `antlered_host`.)
- **Minotaurs** are Tarmin's generals, the soldiers of the Antlered Host. (`CONTEXT.md`, glossary: Minotaur.)
- **His voice** is Tarmin's Knell. A gong tolls, and Tarmin-Zul speaks in red in the seeker's head: "a telepathic gloat, part of the war he wages and the game he plays with it." He is "gloating, amused, bored; a game-master of his own war". Examples: "I could not have planned it better. I did plan it."; "The houses do my work for me."; "Another step closer to you, little seeker. I am almost there."; "You killed {beast} ... I will remember this, seeker." (Houses plan, Slice 5; `chronicle_grammar.json` `knell`, and its `_comment`.)
- **Look.** The unused sprites `assets/images/monsters/tarmin.png` and `tarmin_god.png` (identical) show a purple-robed reaper with a stag skull and great antlers, carrying a scythe. Neither is referenced by code.

### Tarmin's Hunger and the Bridge

- **Hunger** is the death count: "each death is a meal for Tarmin, and the maze grows more savage as he feeds. At 50, the Bridge falls and the game is lost." "Doom" is the internal name only. (`CONTEXT.md`; #176 resolution.)
- **Bridge** is "the barrier holding the Maze apart from the world. Tarmin's Hunger wears it down; the bridge boss guards the breach." (`CONTEXT.md`.)
- **Code.** Stages are 1 Quiescent, 2 Restless, 3 Corrupted and 4 Tarmin's Wrath. They come from expedition turns (400 / 800 / 1200) or from deaths (10 / 25 / 40), whichever stage is higher. They raise EDL, the spawn rate, speed and shadow affixes. (`core/managers/DoomManager.java:43-45`, `getDoomStage` javadoc.) "Bridge integrity" is deaths/50 as a percentage. At 50 deaths a **bridge boss** is summoned. Killing it resets deaths to 0, and each later summoning is harder. Dying while it is active **wipes the save slot** ("apocalypse"). (`DoomManager.java:86-100, 264-277`.)
- **The bridge boss** is an IRON_GOLEM, 2.5 tiles tall, that roams a random level-1 chunk on the surface, at the player's speed. (`core/gamedata/boss/BridgeBoss.java`; `WorldManager.placeBridgeBossIfDue`.)
- **Bridge of Souls** is a separate themed chunk: "An island in a chasm of molten fire, where the Bringer of Death waits for fallen souls on the far side of every bridge." Its rune is the "Demonic Scythe Mark (Doom Boss)" and its champion a BRINGER_OF_DEATH. It used to be the bridge boss's fixed chunk at (0,1). Now it is an ordinary random theme. (`core/generation/theme/ChunkTheme.java:75-81`; `assets/data/themes.json` `BRIDGE_OF_SOULS`; `WorldManager.java:2073-2075, 2104-2114`.)
- **In the history.** Each new Doom stage is chronicled once as `TARMIN_ASCENDANT`: every other house loses 3 x stage strength, and from stage 3 the weakest free lesser house is forced to kneel to him. History reads the Doom Clock and never writes to it. (`HistorySimulator.ascend`, `:1001-1028`; Houses plan D42, Invariant 7; `docs/DEsign/Houses of the Maze - How It Works.md` §11.)

### The Maze, the houses and the gashes

- **The Maze** is hell, the realm the houses come from (`CONTEXT.md`). It is original IP and "Generic rival factions of hell" (D1).
- **Gashes.** There are three gashes, tears from the Maze into the world at the three seal sites, each held by a great house. Beside the great houses are 4-8 lesser houses. Every house is hostile to the player, and no path makes one friendly. (D2, D5, D10, Invariant 4.) The seal boss of a gash is its current holder's lord, met in its court two strata down (D7, D30; How It Works §6).
- **Why the houses war.** "Internal politics only: a War-of-the-Roses epic" (D3). War is chosen by utility, with a recorded casus belli: a grudge, a disposition and an opportunity (D25). The wars are fought on the surface (D13). Houses also besiege Castle Tarmin (`SIEGE_LAID` and its kin; `HistorySimulator:117-190`).
- **What the houses know of Tarmin.** He is a usurper beast "squatting in its stolen castle", and a door-opener who now takes tithes: "the lesser houses learned what they were for", "The sacrifice draws near", "bled the houses white to feed one appetite". (`chronicle_grammar.json`, `TARMIN_ZUL_RISES` and `TARMIN_ASCENDANT`, FOR and AGAINST.)
- **What the houses know of the player.** He is a **seeker**: "a seeker from the mortal world", "a creature from the shelters", "mortal vermin". Every death is numbered: "the {ordinal} seeker". In their telling a seeker "came to the maze for vengeance" and "had come only to see Tarmin dead". "There will be another." "The bed in the shelter stayed warm for the next." "The Antlered Lord's feast was not delayed." (`SEEKER_FELL`, `SEEKER_FELL_UNKNOWN`, `SLAIN_BY_PLAYER`.) The houses treat each death as a *new* seeker, which matches "a different condemned prisoner each time".
- **Doctrines.** There are 8 creeds, each with a chronicler voice (`doctrines.json`; `voices` in the grammar):

  | Doctrine | Creed (abridged) | Voice |
  |---|---|---|
  | Flensed Choir | Pain is a hymn | liturgical |
  | Chainwrights | What is bound cannot be lost. Bind everything. | mercantile |
  | Hollow Court | The perfect body ... wants nothing | courtly |
  | Gilded Rot | Decay is the last and richest ornament | courtly |
  | Ashen Penitents | Burn the sin out | liturgical |
  | Ossuary Lords | The dead are the only loyal subjects | chronicler |
  | Glass Hunger | Appetite is the only honest prayer | feral |
  | Antlered Host | The rift is a door, and the door is ours | martial (Tarmin only) |

- **Megabeasts** are 3-6 per world: an archetype (Wyrm, Titan, Colossal Hive, Drowned Thing, Bone-Choir or Fungal Mother) with a rolled material, breath and weakness. They are part of the same history and older than any house ("Older than any house, and you killed it", knell). (`assets/data/megabeasts.json`; D33-D35.)
- **Mortals.** Towns answer to the Refugee Council, the Goblin Clans or the Outcast Covenant, and none of them is of the Maze (`core/gamedata/history/town/Allegiance.java`). Elders remember "We were farmers once, before the gashes" and having "buried more seekers than I have", so mortals know seekers come repeatedly. (`chronicle_grammar.json` `talk.ELDER`.)

### What the player learns, and where

| Channel | What it tells | Source |
|---|---|---|
| Arrival hint (once per save) | "You came to the maze for vengeance against Castle Tarmin." | `core/managers/HintManager.java:126` |
| Doom hint | "Tarmin's Hunger grows: each death feeds him. At 50 deaths, the Bridge falls." | `HintManager.java:209` |
| Death screen | death count, "BRIDGE" %, and one escalating line: "Something beneath the castle took note." / "The Minotaur stirs beneath the castle." / "The boundary is failing." / "The ritual is all but complete." | `PlayerDeathScreen.java:217-245` |
| Fragments (pages, proclamations, banners, Void glyphs) | unlock chronicle entries at the Archive Lectern ("Chronicle of Tarmin") | `core/gamedata/history/FragmentKind.java`; `CodexScreen.java:280-293` |
| Lectern header | "The Maze calls you {epithet}." The epithet goes the Seeker, then the Bloodied, then Lordsbane, then Gashbreaker, earned from kills across *all* lives. | `core/gamedata/history/text/Epithets.player` |
| Shelter rumours on waking | the loudest events of the season | `text/Headlines.java` |
| Tarmin's Knell | Tarmin gloating over great events and over the seeker's deeds | grammar `knell`; `text/Knell.java` |
| Void lore glyphs | seven "Story I-VII" runes (below); reading one also unlocks `TARMIN_ZUL_RISES` | `core/managers/DimensionalManager.java:36-44`; `GameScreen.java:256` |
| Bones / ghosts | a ghost of a past life carries the epithet; the name is "Fallen Hero" | `BonesManager`, `BonesData.java:13` |

**The Void glyphs** are the most mythic text in the game:

- I. "Castle Tarmin casts its crimson shadow upon the mortal realm. We rang the bells..."
- II. "...the scholar inscribed the name of the Antlered Lord, sealing the rites in flame."
- III. "From the obsidian rift strode the Minotaur, leading legions of titans and creeping carapaces."
- IV. "The village stones turned to embers..."
- V. "Chained within iron bars ... our kin were ferried to the Maw of Tarmin."
- VI. "The Antlered Nether Lord extracted the mortal spirit, tearing open the primordial Raycast Void."
- VII. "He who rests at the green hearth may yet take up the notched blade and reclaim the light."

The Void is also where a **Hollow Shade** goes. When a spiritual or undead foe "severs your mortal soul", the player wakes in the Void and must find a Rift Anchor to reclaim their mortal form. (`core/managers/CombatManager.java:1020-1022`.)

### The Shelter and the Altar

- The Shelter is "a safe 4x4 sanctuary" (`CONTEXT.md`). The Altar sells persistent upgrades for banked Divinity. They "survive death and world resets, and are only cleared by a full Apocalypse wipe" (`core/gamedata/progression/ShelterAltar.java:9-12`). The Altar's stations carry **no lore** about why the shelter is safe or who keeps it. The Archive Lectern holds "the Chronicle of Tarmin" (`ShelterAltar.java:56`). There is more than one shelter: "every shelter" (`ShelterAltar.java:262`).

### Events with story weight (`assets/data/events.json`)

- **The Corpse in Oakhaven Robes:** "a dried corpse in the grey robes of an Oakhaven archivist. Its face is uncomfortably like your own." This could be read as a past Incarnation.
- **The Drowned Library:** "Oakhaven's crest is stamped on the spines. Someone carried these a long way from home."
- **The Reflection of Elara:** "Elara's reflection ... She is weeping. 'I am forgetting,' she whispers. 'Do not let me go.'"
- `docs/complete/events.md:10` says "The Archivist, Oakhaven and Elara are canon." Nothing anywhere says who Elara or the Archivist are.

---

## (b) Contradictions between sources

1. **Is Tarmin a Minotaur?** `CONTEXT.md` and the Expedition Loop plan say no. The following say yes or blur it:
   - Houses plan D6: "**Tarmin-Zul** (the Minotaur) is a Maze lord".
   - Void glyph III: "From the obsidian rift strode the Minotaur". Glyph VI names the same figure the Antlered Nether Lord.
   - The death screen: "The Minotaur stirs beneath the castle." (`PlayerDeathScreen.java:235`).
   - The Antlered Host's boss base is MINOTAUR, though that may be his generals.
   - "Antlered" itself suggests a stag, as the `tarmin_god.png` sprite shows, not a bull.
2. **Does the Bridge fall or get built?** `CONTEXT.md`: Hunger "wears it down"; at 50 "the Bridge falls". The code counts "Bridge integrity" *up* from 0 to 100% with deaths, and `isApocalypse` is documented as "True if the bridge is complete" (`DoomManager.java`). The death screen shows "BRIDGE 98%" as the danger nears. The two metaphors are opposite: a barrier wearing down, or a crossing being completed.
3. **Is the game lost at 50 deaths?** `CONTEXT.md` and the hints say "At 50, the Bridge falls and the game is lost". The code says that at 50 a bridge boss comes; killing it resets Hunger to 0, and only a death *while it stands* wipes the slot (`DoomManager.java:86-100, 264-277`). `TormentManager.java:21` moves the cap to 30.
4. **Two "bridge" guardians.** The Doom bridge boss is a roaming IRON_GOLEM (`BridgeBoss.TYPE`). The Bridge of Souls theme has a BRINGER_OF_DEATH that "waits for fallen souls", and `DoomManager.java:89` still says "the Bringer of Death spawns!". The Bridge of Souls is now an ordinary random theme, not tied to the Hunger.
5. **Who feeds Tarmin?** `CONTEXT.md` says the *condemned* and the Champion's deaths. D42, `HistorySimulator.ascend` and the grammar say he "bleeds the houses to feed the sacrifice" and takes tithes from the houses. Both are true in code (a death raises Doom, Doom ascends Tarmin, the houses bleed), but no text links the prisoner's death to the houses' loss.
6. **A "sacrificial ritual" with no victim.** Residue of the daughter story (see (d)). It survives in the Expedition Loop plan line 19 ("shortening the time before the sacrificial ritual"), on the death screen ("The ritual is all but complete."), in the grammar ("The sacrifice draws near.") and in D42 ("feed the sacrifice"). No current canon says what the ritual is.
7. **Came, or was sent?** `CONTEXT.md` says he was taken in a raid and *sentenced*. The arrival hint says "You **came** to the maze for vengeance" (`HintManager.java:126`), and so does the grammar ("came to the maze for vengeance").
8. **One soul, or many seekers?** `CONTEXT.md` has one soul with one constant title. The chronicle numbers seekers ("the 7th seeker") as if each were a different person. `Epithets.player` gives a title that *changes* with kills (the Seeker, then the Bloodied, then Lordsbane, then Gashbreaker), which is not "one title across them all". The new "different prisoner each time" decision fits the numbering, so the two are reconcilable but not yet reconciled.
9. **Escaped how?** `CONTEXT.md` and D6 say Tarmin-Zul *escaped* the Maze through the Rift. Void glyph II says a mortal scholar "inscribed the name of the Antlered Lord, sealing the rites in flame", which suggests he was summoned or bound by mortals. The grammar calls the castle one that "had always been waiting" and "stolen". It is unclear whether the castle was mortal-built, his, or older.
10. **What the Bridge holds apart.** It "hold[s] the Maze apart from the world", yet three gashes already tear the Maze into the world, and Tarmin already walked out through the Rift. What remains on the far side of the Bridge is unsaid.
11. **Gods.** The Expedition Loop plan line 21 has "Shrines to Tarmin gods" (plural). Tarmin is otherwise a single god-like being. "Tome of Tarmin" and "Tear of Tarmin" (`Player.java:1929`) use the name as a place or adjective.
12. **"Tarmin Legion."** The faction text says "garrison soldiers and elite knights bound to Castle Tarmin" (`core/gamedata/monster/Faction.java:8`). ADR 0004 folds the Legion into the Antlered Host, whose roster is minotaurs, orcs, hobgoblins, gargoyles and ogres. The name and description were never updated.

---

## (c) What is left open

- **Why the condemned are sent into the maze.** Only "to feed Tarmin" (`CONTEXT.md`). Nothing says why it has to be *in the maze* rather than at the castle, who sentences them (glyph V: raids ferry "our kin ... to the Maw of Tarmin"), or what crime they were condemned for. The maze is also called "the Labyrinth" ("Fell in the Labyrinth", `PlayerDeathScreen.java:50`), and it is not clear whether it is the Maze, hell itself, or Tarmin's own pen in the mortal world.
- **What Tarmin gains from deaths.** Mechanically: Doom stages (a more savage maze), the Bridge's progress, and power over the houses (ascendancy, forced oaths). In fiction: "a meal", growth, "Another step closer to you". Nothing says what he becomes at 50, why the Bridge matters to a lord who is *already out*, or what the "ritual" or "sacrifice" is for.
- **How the soul is bound and reborn.** Only "the maze draws his soul back into a new body" (`CONTEXT.md`). Hooks already in the code:
  - glyph VI, "extracted the mortal spirit";
  - the Hollow Shade and the Rift Anchor;
  - the Bridge of Souls, where "the Bringer of Death waits for fallen souls";
  - glyph VII's prophecy of "the green hearth" and "the notched blade" (the Shelter and the Father's Sword?);
  - the Oakhaven corpse with "your own" face.

  Nothing settles whether Tarmin binds the soul on purpose (a game, a farm, an endless meal) or whether the Champion is a cosmic counterweight he did not choose. Why rebirth happens *at the Shelter*, and why the Shelter is safe, is also unsaid.
- **What the Gash hell-wars are for.** D3 says internal politics only: succession, grudges and ambition. The houses covet Tarmin's castle and his door out (D6, the sieges), and he bleeds them at each ascendancy. Nothing says whether the wars serve Tarmin, whether the Knell's "I did plan it" is literal, or what holding a gash *gains* a house beyond prestige.
- **What the houses know of Tarmin and the Champion.** They call him usurper and beast, and they know of the "sacrifice" and the tithe. They see seekers as numbered, interchangeable mortals out of the shelters, come for vengeance. No line shows a house knowing that the seekers are one soul, or that the seekers' deaths are what feeds Tarmin. Tarmin's Knell talks to the seeker as one continuous "little seeker" who is remembered, which is the only hint that someone knows.
- **What the player learns through chronicles and fragments.** The houses' politics, Tarmin's rise (only through a Void glyph), the ascendancies, and their own numbered deaths. Nothing reveals the myth of the Champion, the reason for the sentence, or the Bridge's nature. The seven Void glyphs are the only telling of the backstory, and they are seeded randomly in the Void.
- **Who Elara and Oakhaven are.** They are declared canon (`docs/complete/events.md:10`) but never defined. The Archivist was the protagonist of an older frame, *The Silence After Tarmin* (`docs/archive/Dungeon Crawler Event Generation.md`). In that frame Oakhaven's books were "thrown here by the Minotaur" and a "Silence" ate memories. Both are now loose ends.

---

## (d) Every trace of the daughter story

The daughter story was the original premise: rescue your daughter from Tarmin and the Minotaur. The Doom Clock tracked "how close Tarmin is to sacrificing your daughter" (`CONTEXT.md` as first committed in `0bad8a3b`). It was cut in #158 and #176 and scrubbed by #189 (commit `1436bf2b`). `grep -ri daughter` is now clean except for one test.

**Literal mentions**

| Where | What | Action |
|---|---|---|
| `core/src/test/java/com/bpm/minotaur/managers/HintManagerTest.java:69-86` | `arrivalHintUsesVengeanceStoryAndNeverDaughter`: a guard asserting the arrival hint never says "daughter" | Keep: it is the regression guard |
| Git history: `0bad8a3b`, `981cfdbf`, `b302409b`, `21d8a50c`, `1436bf2b`; issues #158, #176, #189 | the original text and its removal | History only |

**Residue of the daughter plot, still live.** The sacrifice that was hers now has no victim:

| Where | Text |
|---|---|
| `docs/Implementation Plan_ The Expedition Loop & Progression Reboot.md:19` | "shortening the time before the sacrificial ritual" |
| `core/screens/PlayerDeathScreen.java:229` | "The ritual is all but complete." (Its predecessor, removed in `916fb46e`: "Should 50 deaths occur, the sacrificial ritual will consume all memory of your journey.") |
| `core/screens/PlayerDeathScreen.java:235` | "The Minotaur stirs beneath the castle." (from the old Minotaur-as-villain story) |
| `assets/data/chronicle_grammar.json` `TARMIN_ASCENDANT` FOR | "The sacrifice draws near." |
| Houses plan D42 | "he bleeds the houses to feed the sacrifice" |
| `core/managers/DoomManager.java:89` | "the Bringer of Death spawns!" (comment, stale) |

**Possible traces (unconfirmed)**

- **Elara.** `assets/data/events.json` `EVENT_REFLECTION_OF_ELARA` and `assets/images/events/reflection_of_elara.jpg`: a weeping woman in a pool, "I am forgetting ... Do not let me go." It comes from the *Silence After Tarmin* doc (`docs/archive/Dungeon Crawler Event Generation.md:297-310`), which never says who she is. `docs/complete/events.md:10` calls her canon. A grieving woman tied to the protagonist reads as a lost wife or daughter, so she either needs a role in the new myth (perhaps the "tragedy" of `CONTEXT.md`) or a rewrite. Also listed in `docs/complete/event-art-prompts.md:95`.
- **Oakhaven and the Archivist.** `EVENT_OAKHAVEN_CORPSE` and `EVENT_DROWNED_LIBRARY` in `events.json`; `docs/complete/events.md`; `docs/complete/event-art-prompts.md:39`. These come from the pre-revenge Archivist frame, not the daughter story itself.

---

## (e) How names are made today

**`NameForge`** (`core/gamedata/history/NameForge.java`). It is deterministic from the history's own RNG and ASCII only.

- **Given names** come from `given(doctrine, rng)`, which joins a head from `doctrine.givenNames` and an ending from `doctrine.givenEndings`. If the head's last letter equals the ending's first, that letter is dropped. Each doctrine in `doctrines.json` has 10 heads and 8 endings, so names are flavoured by creed: Choir "Vaelath", Glass Hunger "Gnawax", Antlered Host "Tarmin", "Zulok" and so on. There are no surnames. A figure is identified by name, epithet and house.
- **House names:** "House " plus one of 5 `houseNames` per doctrine, unique per world. After 8 failed tries it falls back to "House " plus a given name. The Antlered Host is hard-coded as "the Antlered Host" (`HistorySimulator.java:307`).
- **Places:** "the " + one of 20 first words + one of 16 second words ("the Gallows Ford", "the Widow Causeway").
- **Gashes:** "the " + one of 8 first words + one of 7 second words ("the Weeping Wound"), unique per world.
- **Sigils:** a doctrine `charges` entry, "on", and one of 7 field colours.
- **Tarmin-Zul** is a fixed name (`HistorySimulator.java:315`).

**Other name sources**

| Source | Pattern | File |
|---|---|---|
| Mortals (town folk) | given name from the allegiance + family name from the allegiance. Refugee Council: Anglo-rustic ("Brenna Thatch"). Goblin Clans: comic-grim ("Skrag Ratbite"). Outcast Covenant: religious ("Sister Vey of the Ash") | `core/gamedata/history/town/Allegiance.java`; `MortalSimulator.java:45, 120` |
| Towns | compound word: one of 12 + one of 10 ("Lanternhold", "Dripdeep") | `town/Town.java:62-67, 123` |
| Megabeasts | 15 fixed given names ("Ulgrath", "Mother Silt", "Pale Agnes") + archetype and material ("the Obsidian Wyrm") | `assets/data/megabeasts.json`; `beast/MegabeastCatalog.java` |
| Epithets for figures | from deeds or the first trait, with a sympathetic and a hostile pair ("the Steadfast" / "the Usurper") | `text/Epithets.of` |
| The player's epithet | world-wide, from kills: the Seeker, the Bloodied, Lordsbane, Gashbreaker | `text/Epithets.player` |
| The player's name | fixed "Hero" at new game (`SaveSlotSelectScreen.java:391`, `SlotMetadata.java:10`); ghosts and bones show "Fallen Hero" (`BonesData.java:13`, `BonesManager.java:107`, `GhostPlayerMonster.java:31`, `BonesAwakenModal.java:123`, `Hud.java:2394`) | |

**What is missing for prisoners.** No pool fits a *mortal prisoner* from the world above. The nearest is the Refugee Council pool (a mortal, Anglo-rustic given name plus family name), which would also tie prisoners to the refugee towns. Doctrine pools are hellish and wrong for mortals. A prisoner name source, and a constant soul title separate from the changing `Epithets.player`, both need deciding.
