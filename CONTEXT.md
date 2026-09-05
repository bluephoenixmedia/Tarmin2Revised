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
