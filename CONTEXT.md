# Project Context: Tarmin2 (Minotaur Remake)

## Overview
Tarmin2 is a dark, atmospheric first-person procedural dungeon crawler combining the spirit of Mattel's *Treasure of Tarmin* (Intellivision), classic *Might and Magic II & III*, and modern systemic roguelikes (*Caves of Qud*, *NetHack*, *Dwarf Fortress*).

## High-Level Game Loop: The Expedition Loop
1. **The Shelter (Camp)**: A safe 4x4 sanctuary where the player rests, cooks monster parts, crafts and repairs gear, identifies unknown items, and spends XP/Divinity to level up stats and skills.
2. **The Expedition**: The player ventures into overland biomes (Forest, Plains, Ruins) or descends vertically into underground strata (Caves of Qud / Dwarf Fortress vertical strata descent available on any chunk).
3. **Tactical Combat**: Fast turn-based grid combat (bump-to-attack, archery/thrown weapons, tactical spells). Underlying damage calculation honors the classic War vs Spiritual duality.
4. **Death & Stakes**: Dying revives the player at the Shelter; backpack gear, bones, and lost divinities drop at the death tile (Corpse Run), but each death advances the Doom Clock (Tarmin's Hunger).
5. **The Final Run**: Gather ancient seals from deep strata bosses across the world to unlock the gates of Castle Tarmin, brave the final gauntlet, slay the Minotaur, and rescue your daughter.

## Domain Glossary
- **War Strength ($W$)**: Physical power, physical weapon attack, and health scaling.
- **Spiritual Strength ($S$)**: Magical power, spell potency, and mystical defenses.
- **Bad Monsters**: Monsters vulnerable only to Spiritual weapons/attacks (immune to physical).
- **Nasty Monsters**: Monsters vulnerable only to War weapons/attacks (immune to spiritual).
- **Horrible Monsters**: Monsters vulnerable to both War and Spiritual attacks.
- **Divinity**: Spiritual essence / experience points earned through exploration and slaying monsters; drops on death and can be reclaimed.
- **Doom Clock ("Tarmin's Hunger")**: Escalating threat meter tracking how close Tarmin is to sacrificing your daughter; advances on player death.
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
- **Casus Belli**: The recorded reason a house declared war.
- **Season**: One political tick of the history, which happens once per shelter sleep.
- **War Clock**: The turn-based clock that moves fronts and resolves battles. It is separate from the Doom Clock.
- **Front**: A moving zone of war on the overland map, between two warring houses.
- **Battle**: A front engaging the player's current chunk.
- **Rout**: The end of a battle, when one side's reserve and morale both collapse.
- **Melee Spill**: A soldier breaking off from the battle to attack the player.
- **Fragment**: A found piece of history (tome, banner, proclamation, rumour, trophy) that unlocks chronicle entries at the Archive Lectern.
- **Chronicler**: The biased in-world author of a fragment. Accounts of the same event can contradict each other.
- **Megabeast**: A named, unique creature of the history, with a lair in the strata.
- **Town**: A persistent underground settlement owing allegiance to a mortal power.
- **Suborned**: Said of a town secretly serving a Maze house.
- **Seat**: Where a house sits on the overland map: a great house at its gash's seal site, Tarmin-Zul at the castle, a lesser house at a holdfast placed from the world seed. Fronts run between seats.
- **War Band**: The soldiers of a battle. They march on when it ends, or when the player leaves; they are never saved with a chunk.
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

- **Encounter**: A scheduled piece of the surface war: a skirmish, a marching column, a raiding party, a war camp, an aftermath field or a siege. Scheduled from the seed, the war clock and the chunk, never saved; not history unless the player takes part.
- **Skirmish**: Two war-bands of 6-10 meeting in a chunk and fighting until one routs. Smaller than a battle, and far more common.
- **Column**: Soldiers of one house marching in file between its seat and a front, crossing chunks.
- **Raid**: A small party of one house burning another's ground; in peacetime too, between houses that bear a grudge.
- **War Camp**: A house's tents, fires and sentries near a front, for as long as its war lasts.
- **Aftermath**: A battlefield left behind: the dead, crows, burnt ground and broken banners, for three sleeps.
- **Earshot**: How many chunks off a fight is heard: steel and screams next door, horns and drums two or three chunks off, a lone horn out to six. Underground it is a rumble through the stone.
- **Favour**: A house's regard for the player, from Enemy to Friend.
- **Sworn**: Bound to a house by its oath: its colours, its contracts, its enemies.
- **Contract**: A task a sworn house gives the player: hold a chunk, kill a captain, escort a column, burn a camp.
- **Herald**: A house's courier on the roads between seats, carrying a proclamation.
- **Siege / Breach**: A house laying siege to Castle Tarmin, and the gap in its wall that stays open while the siege lasts.

## Key Architectural Invariants
- **Retro Mode Preservation**: `GameMode.CLASSIC` strictly preserves the original 16-tile array (`tile1` to `tile16`), 2x2 map layout, and retro wireframe presentation. Modern procedural chunk engines, cyclic mission graphs, and low-cover billboards operate solely in `GameMode.ADVANCED` and the Expedition Delve loop.
- **Consult Active Architecture Specs**:
  - `docs/DEsign/Design Document_ Procedural Maze Chunk Architecture & Generation.md` (Modular 12x12 chunk archetypes, cyclic lock-and-key, low-cover +3 AC, Telemetry Director)
  - `docs/Implementation Plan_ The Expedition Loop & Progression Reboot.md` (Delve & Return Expedition loop)
  - `docs/DEsign/2D RPG Paperdoll System Architecture.md` (Paperdoll layer rendering and weapon slotting)

