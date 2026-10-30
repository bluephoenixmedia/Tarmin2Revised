# Project Context: Tarmin2 (Minotaur Remake)

## Overview
Tarmin2 is a dark, atmospheric first-person procedural dungeon crawler combining the spirit of Mattel's *Treasure of Tarmin* (Intellivision), classic *Might and Magic II & III*, and modern systemic roguelikes (*Caves of Qud*, *NetHack*, *Dwarf Fortress*).

**The story is revenge.** Each Incarnation is a condemned prisoner, taken in a raid by Tarmin's Antlered Host or tithed to the maze by a mortal kingdom, in whom the soul of an **Eternal Champion** wakes, in Michael Moorcock's sense. Tarmin is building the **Bridge of Souls** out of those who die in the maze, to cross into the mortal world. The **old gods** keep the Champion's soul whole and send it back into a new prisoner after every death, against him, yet every death still pays his Bridge. Unarmoured and carrying only the Father's Sword, the soul wants Tarmin and his maze dead, at any cost.

## High-Level Game Loop: The Expedition Loop
1. **The Shelter**: A safe 4x4 sanctuary where the player rests, cooks monster parts, crafts and repairs gear, identifies unknown items, and spends XP/Divinity to level up stats and skills.
2. **The Expedition**: The player ventures into overland biomes (Forest, Plains, Ruins) or descends vertically into underground strata (Caves of Qud / Dwarf Fortress vertical strata descent available on any chunk).
3. **Tactical Combat**: Fast turn-based grid combat (bump-to-attack, archery/thrown weapons, tactical spells). Underlying damage calculation honors the classic War vs Spiritual duality.
4. **Death & Stakes**: Death is permanent for the Incarnation, as in NetHack: its level, stats and carried gear are lost, and the gear and bones stay at the death tile. The Eternal Champion is reborn at the Shelter as a new Incarnation, keeping only what the soul and the world remember. Every death feeds Tarmin's Hunger.
5. **The Final Run**: Gather ancient seals from deep strata bosses across the world to unlock the gates of Castle Tarmin, cut through Tarmin's Minotaur generals in the final gauntlet, and destroy Tarmin.

## Domain Glossary
- **War Strength ($W$)**: Physical power, physical weapon attack, and health scaling.
- **Spiritual Strength ($S$)**: Magical power, spell potency, and mystical defenses.
- **Bad Monsters**: Monsters Resistant to War damage and Weak to Spiritual.
- **Nasty Monsters**: Monsters Resistant to Spiritual damage and Weak to War.
- **Horrible Monsters**: Monsters that take War and Spiritual damage alike.
- **Resistant / Weak**: A monster's affinity to a damage type. Resistant takes reduced damage, Weak takes increased damage. A critical hit pierces resistance. _Avoid_: "immune"; no monster is immune to a damage type.
- **Father's Sword**: The protagonist's starting weapon, a rusty heirloom and the weakest weapon in the game. Every other weapon out-hits it, but it is a Keepsake: it grants its perk while carried, wielded or not. It returns with every Incarnation, and its perk deepens with the soul's milestones.
- **Keepsake**: An item whose perk applies while it is carried anywhere on the player, not only when equipped.
- **Glancing Blow**: An attack that just misses and lands for reduced damage. It does not count as a hit.
- **Vengeance**: The HP the protagonist takes back with each kill. With the slow natural regen, salves and the shelter bed, it is how he heals; there is no resting in the field.
- **Exposure**: How cold the protagonist is, driven by environment, wetness, clothing warmth and nearby fire. Shown as a band: Warm, Comfortable, Chilly, Cold, Freezing, Hypothermic. Cold slows; Freezing also stops natural regen; Hypothermic does damage. WET and SOAKED make heat go two and three times as fast.
- **Divinity**: The old gods' favour, earned through exploration and slaying monsters and spent at the Shelter Altar on permanent upgrades. Banked as it is earned and never lost, not even on death: only a new game resets it.
- **Expedition**: One trip out from the Shelter, ending in a return to the Shelter or in death. Many expeditions can make up one Life.
- **Incarnation**: One life of the Eternal Champion, from rebirth (or a new game) to death: a different condemned prisoner each time, with a name of their own and an **origin**: *raided* from a mortal settlement by Tarmin's Host, or *tithed* to the maze by a mortal kingdom for a crime. Level, stats, skill points and carried gear belong to the Incarnation and die with it. _Avoid_: "run", which is used loosely. Also called a **Life**.
- **Eternal Champion**: The protagonist's soul, kept whole by the old gods and returned by them into each Incarnation, and known by one name across them all, the **soul's name**, typed or generated at a new game. It cannot stay dead, even after it has earned its rest: that is the seeker's curse. It remembers its Known Spells and its Father's Sword, the one thing carried over from its first life; the world remembers unlocks, altar stations, the chronicle, the Shelter Chest and Tarmin's Hunger.
- **World Log**: The plain record of every Incarnation in order: who they were, a few of their most notable deeds, and how they died. Lives lived before it existed appear as one entry of lives unremembered. It is divided into **Ages**, each Age into **Bridges**, each Bridge into **Eras**, with a **Reckoning** at every break. It is read alongside the chronicle, which tells the same lives in the Houses' biased voices. _Avoid_: "legends", "graveyard".
- **Nemesis**: Whatever kills an Incarnation becomes a named figure, even a common beast. It stays in its part of the world, grows with each life it takes, its epithet rising ("Slayer of Brannoc", "Bane of Three Lives"), and remembers the lives it took when it meets the next. Slaying one is a notable deed. _Avoid_: "boss", "rival".
- **Era**: A span of a Bridge between stages of Tarmin's Hunger (at 10, 25 and 40 deaths), named like a Dwarf Fortress Age.
- **Bridge**: One Bridge of Souls from its first stone to its end, and the World Log's division for it ("the Second Bridge"). It ends when its Warden is destroyed or when Tarmin crosses.
- **Age**: One Tarmin's reign: the World Log's largest division. It ends when Tarmin is slain, by the Champion or, rarely, by a House's Siege, a Nemesis or a Megabeast, and the slayer may take the throne and the name.
- **Last Crossing**: A crossing with no old god left to spend. Tarmin stays in the mortal world, the Knell writes the final Reckoning, and the world ends: it can still be read, never played. The only true ending of a world.
- **Reckoning**: The Knell's account of an event the player did not see or that ends a division: a Warden's fall, a crossing, a usurpation. Generated afterwards from the history's facts, in plain and mythic text, and read in the World Log. _Avoid_: "report"; a Telling is one House's account of one life.
- **Shifting**: The maze's remaking at every death of a seeker, part of Tarmin's game. The layout, items and common monsters are new; the **Enduring** remain. Nothing ends the world: the endings of a Bridge and of an Age are shiftings too.
- **Enduring**: Whatever survives a Shifting and is placed anew in the remade maze: Nemeses, Minotaur generals, Gashes and their holders, Houses and their lords, Megabeasts, the old gods' shrines, and the graves and ghosts of named lives.
- **Telling**: A House's account of one life. Fixed facts (the name, when it lived, that it died) never change; the killer, motive and crime bend to the teller's interest. Found in the maze as a Fragment, then shown in the World Log.
- **Tarmin's Hunger**: How many times the Eternal Champion has died. Each death is a stone in the Bridge of Souls, and the maze grows more savage as it grows. At 50 the Bridge reaches the far shore and its **Warden** guards the last stone: destroy the Warden and the Bridge crumbles back to nothing; die while it stands and Tarmin crosses, wastes the mortal world, and is dragged back by the old gods at the cost of one of them, whose shrine goes dark for good. Within an Expedition, lingering also wakes the maze. _Avoid_ in player-facing text: "Doom", which is the internal name ("Doom Clock").
- **Tarmin**: The god-like being the protagonist wants dead, a prisoner of hell ruling from Castle Tarmin and building the Bridge of Souls to cross into the mortal world (Tarmin-Zul in the chronicles of the Houses). Tarmin is a name and a throne as much as a being: whoever slays him may take both, even the Champion. He is not a Minotaur; the Minotaurs are his generals.
- **Bridge of Souls**: Tarmin's way out of hell, built of the souls that die in the maze; the Champion's deaths pay it most. The Houses tithe souls to it and fight over who will rule hell once he crosses. _Avoid_: "the ritual", "the sacrifice"; and naming any other place a Bridge.
- **Guardian**: The monster that holds a themed chunk as its set piece (the Bringer of Death on the Bringer's Isle, the Minotaur in the ruined castle), carrying a title the player reads ("Keeper of the Isle"). However great it is elsewhere, at a shallow depth it is met only a little out of its depth: hard, never a certain death. _Avoid_: "champion", which is the Eternal Champion; "boss", which is the Bridge's Warden and the like.
- **Old gods**: Thirteen ancient powers of the maze, older than Tarmin: the creatures of the original *Treasure of Tarmin*, once monsters themselves, who changed while banished from the world's memory and woke to find a world worse than theirs. Each has a Statue for its shrine; most of their kind still sleep in Tarmin's hunger and attack the living. They keep the Champion's soul whole and send it back against him. The Shelter is their last sanctuary, the Altar is theirs, and Divinity is their favour. After each crossing one god, chosen by the Champion, is **spent** holding Tarmin back: its shrine goes dark until the Treasure is returned. Asterion is always spent last.
- **Kindred**: One of the old gods' three family lines, from the original game's monster classes: the Spirit-born, whose sleeping kin fear Spiritual weapons; the War-born, who fear War weapons; and the Dread-born, who fear both.
- **Treasure of Tarmin**: The seal on the Obsidian Rift, kept by the Minotaur King, the old gods' warden of the island of Tarmin. The Champion's first life, the adventurer of the original game, slew the warden and carried it off; with the island unguarded, Tarmin-Zul rose through the Rift and took the island's name as his own.
- **Revenge of the Master**: The old gods' prophecy of the old order's return, borrowed from the lost sequel's working title; the Ghost and the Old Dwarf came with that lost age.
- **Minotaur**: A high-end enemy of Tarmin's army, mostly its generals (the soldiers of his house, the Antlered Host). Met deep in the strata and in the final gauntlet. _Avoid_: treating the Minotaur as the final enemy; that is Tarmin.
- **Effective Difficulty Level (EDL)**: How dangerous a place is, set by its depth and its distance from the starting shelter. It does not follow the player's level: a stronger character simply finds the same place easier.
- **Fodder / Soldier / Heavy**: The roles a monster plays in an encounter. Fodder die in a swing or two and come in packs; Soldiers are on-level fights; Heavies are rare, telegraphed threats worth fighting or avoiding.
- **Strata**: Vertical subterranean dungeon layers beneath any overland chunk, increasing in hazard, loot quality, and depth.
- **Paper Doll**: Equipment configuration covering armor slots, rings, cloak, boots, gauntlets, and two active hands.
- **Discovery**: Identification status of potions, scrolls, and magical items (no cursed item locks).
- **Known Spells**: Every spell the player has learned, by `spells.json` id. Persistent progression: survives death and is saved.
- **Spell Slot**: One of five prepared quick-cast positions (Shift+1..5 / Z,X,V,B,N). Slot 1 is open from the start; character leveling unlocks slots 2–5 (at levels 2, 5, 8, 11). A slot holds one Known Spell.
- **Spellbook**: The screen listing Known Spells, their details, and the Spell Slots they are assigned to. Empty slots can be filled in combat; full slots are locked against swapping until combat ends.
- **Tome**: A heavy milestone book found in the strata (Initiate, Elements, Arcane, Tarmin). Studying it grants a permanent spell from a curated pool via Tome Choice.
- **Tome Choice**: Picking one not-yet-known spell from a Tome's curated, level-banded pool (falling back to general unlearned spells of that tier). Arcane Attunement at the Altar widens it (options 3→4→5, rerolls, and Level 9 reach for Tarmin).
- **Study**: Reading a Tome. Instant in the Shelter; in the field a channelled action over 10–25 turns, interrupted by damage or a hostile coming into view, or aborted via ESCAPE. Progress is kept on the Tome.
- **Inscribe**: Permanently learning a spell scroll into Known Spells for the spell's full MP cost (as opposed to reading it for one free cast).
- **Weapon Family**: The kind of weapon something is, for Mastery: Blade, Dagger, Axe, Blunt, Polearm, Bow, Crossbow, Thrown, Firearm or Unarmed. Every weapon has exactly one home Family; ammunition and magic have none. A weapon that can be thrown trains its home Family when swung and Thrown when thrown. _Avoid_: "weapon type", "damage type" (Slashing / Piercing / Bludgeoning is how a blow wounds, not what trained it).
- **Mastery**: The protagonist's rank in one Weapon Family (Untrained, Novice, Adept, Expert, Master), raised by landing blows with that Family on anything that is not Trivial. Belongs to the Incarnation: a new body starts Untrained. Unarmed Mastery also hardens the fist itself: each rank steps up the bare-hand damage die. _Avoid_: "skill", which means a skill-tree node; "martial arts", which is Unarmed Mastery.
- **Combat Art**: A named attack a Weapon Family teaches, one each at Adept, Expert and Master. Kept by the soul across deaths like Known Spells, but weaker until the new body retrains the Mastery rank that taught it. An **Active Art** is used on purpose from the Art bar and then cools down; a **Passive Art** fires by itself during ordinary attacks. A Family's Adept Art is a Passive, its Expert Art an Active, and its Master Art a **capstone** that makes the Adept Passive certain for a few turns.
- **Art bar**: The HUD strip of four slots beside the Spell Slots, showing the Active Arts of the Weapon Families in hand. It follows the hands; nothing is assigned to it. _Avoid_: "hotbar", "skill bar". _Avoid_: "skill", "technique", "move".
- **The Laughing Void**: The cosmic, unaligned primordial deity of pure entropy that looms over the world as a towering mountain-sized entity, heedless of Tarmin, the Old Gods, or the Houses. _Avoid_: "chaos jester", "trickster god".
- **Chaos Surge**: A temporary distortion of reality invoked when the Laughing Void manifests, imposing a wild magic rule or phenomenon on all entities for a random duration. _Avoid_: "chaos mode", "wild magic event".
- **Motley Reckoning**: A distinct entry recorded in the World Log when an Incarnation perishes during a Chaos Surge, chronicling the presiding surge and its toll.

### Houses of the Maze
See `docs/DEsign/Implementation Plan_ The Houses of the Maze.md`.
- **The Maze**: Hell. The realm the houses come from and Tarmin-Zul escaped.
- **House**: A political power of the Maze. A **great house** holds a gash; a **lesser house** is sworn to one, or to no one. Every house is hostile to the player.
- **Gash**: A tear from the Maze into the world, at a seal site. Its **holder** is the house that owns it.
- **Lord**: The character who heads a house. **Heir**, **vassal** and **sworn sword** are characters too.
- **Seal Lord**: The lord holding a seal road's gash, met as that road's seal boss two strata beneath its seal site.
- **Court**: A seal lord and its **retainers** (the house's sworn swords) in the strata beneath a seal site. A court never leaves its gash.
- **Epithet**: A name the chronicle gives a figure, which depends on who tells it ("the Steadfast" to their own house, "the Usurper" to others). The player earns one too.
- **Doctrine**: A house's authored creed. It sets the house's soldiers, palette, gash interior and chronicler voice.
- **Sigil**: A house's blazon in words: a charge from its doctrine, on its field colour, then a second colour of its own and how the field is divided ("an open mouth singing on bone white and tarnished gold, per pale"). Chronicles quote it.
- **Arms**: A house's sigil drawn as a shield, its border in the doctrine's colour. Every house has its own, great or lesser; a gash has none. Shown over each of the house's soldiers and as its armies' mark on the map; an army's captain carries it as a banner.
- **Army**: A house's host in the field, with a place on the overland map, a strength and an objective. It marches, camps, raids and fights other armies whether or not the player is near, and what it wins or loses is history. _Avoid_: "war band" for a whole army; a war band is the part of an army fighting in one chunk.
- **Strength**: A house's whole force, counted in soldiers: its armies in the field plus its garrison at home. Only the dead lower it; mustering and disbanding only move soldiers between field and home.
- **Siege**: An army camped around an enemy seat: the garrison starves, the walls wear down and the besiegers build their works (ladders, a ram) until they assault, a relief drives them off, or they leave. Castle Tarmin can be besieged but never falls to a house.
- **Sally**: A garrison striking out from a besieged seat against the camp around it.
- **Garrison**: The part of a house's strength left at its seat. It defends the seat against an army that marches on it, and refills the house's armies.
- **Captain**: The named figure leading an army: the lord, the heir or a sworn sword. When an army routs, its captain is slain, taken hostage or escapes.
- **Levy**: The army a vassal musters for its liege's war. It follows the liege's army under the vassal's own arms, and the liege's enemy is its enemy.
- **Casus Belli**: The recorded reason a house declared war.
- **Season**: One political tick of the history. It passes with every sleep, at the Shelter or a Bivouac, and every so often while the seeker is still awake in the field.
- **War Clock**: The turn-based clock that moves armies and resolves battles. It runs whether or not the player is near, and a sleep advances it. It is separate from the Doom Clock.
- **Front**: Where two warring houses' armies are meeting on the overland map.
- **Battle**: Armies fighting in one chunk. It goes on whether or not the player is there; one who walks in finds it as far along as it got.
- **Rout**: The end of a battle, when one side's reserve and morale both collapse.
- **Melee Spill**: A soldier breaking off from the battle to attack the player.
- **Fragment**: A found piece of history (tome, banner, proclamation, rumour, trophy) that unlocks chronicle entries at the Archive Lectern.
- **Chronicler**: The biased in-world author of a fragment. Accounts of the same event can contradict each other.
- **Megabeast**: A named, unique creature of the history, with a lair in the strata.
- **Town**: A persistent underground settlement owing allegiance to a mortal power.
- **Suborned**: Said of a town secretly serving a Maze house.
- **Seat**: Where a house sits on the overland map: a great house at its gash's seal site, Tarmin-Zul at the castle, a lesser house at a holdfast placed from the world seed, always on open land. Armies march on seats, and fronts run between them.
- **War Band**: The soldiers of a battle the player can see: each side's line, refilled from its reserve. When the player leaves, the battle goes on as numbers; a war band is never saved with a chunk.
- **Volley / Charge**: A battle's hazards, marked on the ground a turn before they land.
- **Spoils**: What a broken army leaves for a player who stayed: arms of the fallen, a signet ring (a trophy, kept and sold), a torn banner, and a fallen lord's own blade.
- **Lair / Hunt**: A megabeast's home chunk and level, and its pursuit of the player through gates and ladders. A megabeast is never saved with a chunk; only its wounds and its hunt are kept.
- **Stratum**: What a strata chunk is made of: the Maze, a Fungal Forest, Flooded Halls, an Ossuary, the Magma Deeps. Depth bands choose them; regional noise lays them out.
- **Allegiance**: The mortal power a town answers to: the Refugee Council, the Goblin Clans or the Outcast Covenant.
- **Folk**: A town's named people: merchant, smith, innkeeper, reeve (who gives the player work), elder and townsfolk.
- **Settlement**: A mortal community the history keeps, with a seat for each of its folk. A town on the map is bound to one the first time the game asks for it (ADR 0005).
- **Mortal**: A named keeper of a settlement's seat, born, seated and dead in the history's seasons; succeeded in the seat by family. Not a figure of the houses.
- **Exile**: A figure of the houses living in a town: a sworn sword who outlived their house, or the loser of a succession who survived it.
- **Standing**: How a town regards the player. Kept per town, heard at half weight by its sister towns and reversed by its rivals; a hostile town sets its guards on the player.

### The Living War

- **Encounter**: What the player meets of the surface war: an army marching (a column), halted (a war camp), raiding, fighting (a skirmish or battle), besieging, or the aftermath it left. Never placed for the player's sake; it is wherever the armies are.
- **Skirmish**: Two war-bands of 6-10 meeting in a chunk and fighting until one routs. Smaller than a battle, and far more common.
- **Column**: Soldiers of one house marching in file between its seat and a front, crossing chunks.
- **Supply Column**: Soldiers marching from a house's seat to refill one of its armies in the field. Foragers are the reverse: a few of an army's soldiers sent a chunk or two out and back for food. Both are real, and can be met, fought or cut off.
- **Raid**: A small party detached from an army to burn an enemy holdfast's lands or waylay the enemy's road, taking nothing; in peacetime too, between houses that bear a grudge. Raiders may camp outside a road shelter, never inside it.
- **Hunting Party**: A raid sent after the player by a house that counts them an Enemy, toward where its armies last saw them. The only part of the war that aims at the player.
- **War Camp**: A house's tents, fires and sentries near a front, for as long as its war lasts.
- **Aftermath**: A battlefield left behind, watched or not: the dead, crows, burnt ground and broken banners, for three sleeps.
- **Bivouac**: A portable camp the player pitches outside the Shelter to sleep in the field. The world moves as far as it does for a Shelter sleep, but none of the Shelter's services or safety come with it: armies and roaming monsters can find a sleeper, and strike before they wake. _Avoid_: "camp", which is ambiguous with the War Camp.
- **Ward**: A protection laid over a Bivouac for one night, crafted or cast: a Shield soaks the first blows, an Alarm wakes the sleeper before anything is close, and Concealment hides them from all but clever or magical eyes. Wards stack.
- **Earshot**: How many chunks off a fight is heard: steel and screams next door, horns and drums two or three chunks off, a lone horn out to six. Underground it is a rumble through the stone.
- **Favour**: A house's regard for the player, from Enemy to Friend.
- **Sworn**: Bound to a house by its oath: its colours, its contracts, its enemies.
- **Following**: The player marching with a host of their liege, at its pace and in its line, never steering it. _Avoid_: "joining", which is joining a battle.
- **Contract**: A task a sworn house sets against a named thing of the war: a host, a captain, a column, a camp or a seat. Void if the thing is gone before the player gets to it.
- **Herald**: A house's courier on the roads between seats, carrying a proclamation.
- **Siege / Breach**: A house laying siege to Castle Tarmin, and the gap in its wall that stays open while the siege lasts.

### The Laughing Void & Chaos Surges
See Issue #304.
- **The Laughing Void**: An unaligned, primordial chaotic god that exists beyond the factions of the Maze and mortal powers. Appears looming on the horizon as a colossal mountain-scale entity fading in and out of existence.
- **Chaos Surge**: A temporary wild magic reality fracture triggered when the Laughing Void gazes upon the world (15 + 2d12 turns). Inverts or distorts game rules, impacting the player and all creatures with cosmic indifference.
- **Motley Sovereign**: The archaic epithet by which the Laughing Void is recorded in ancient mortality ledgers and world chronicles.
- **Motley Reckoning**: An epitaph and World Log event recorded when an Incarnation perishes while a Chaos Surge is active ("Perished under the grinning gaze of the Motley Sovereign during [Surge Name]").

## Key Architectural Invariants
- **Retro Mode Preservation**: `GameMode.CLASSIC` strictly preserves the original 16-tile array (`tile1` to `tile16`), 2x2 map layout, and retro wireframe presentation. Modern procedural chunk engines, cyclic mission graphs, and low-cover billboards operate solely in `GameMode.ADVANCED` and the Expedition Delve loop.
- **Consult Active Architecture Specs**:
  - `docs/DEsign/Design Document_ Procedural Maze Chunk Architecture & Generation.md` (Modular 12x12 chunk archetypes, cyclic lock-and-key, low-cover +3 AC, Telemetry Director)
  - `docs/Implementation Plan_ The Expedition Loop & Progression Reboot.md` (Delve & Return Expedition loop)
  - `docs/DEsign/2D RPG Paperdoll System Architecture.md` (Paperdoll layer rendering and weapon slotting)

