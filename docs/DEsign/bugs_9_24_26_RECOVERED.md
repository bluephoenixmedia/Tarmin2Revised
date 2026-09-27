# RECOVERED: bugs_9_24_26.md, full original list

**This file is a recovery, not a working list.** It is the fullest version of
`bugs_9_24_26.md` that could be reconstructed, containing **34 items**.

Recovered from the Claude Code session transcript
`b822401f-20d3-44d7-8c57-844336727129.jsonl`, line 3854, on 2026-09-26.
It was not recoverable from git: the file was never committed with this
content, so no blob of it exists in the object database. Every `.jsonl`
transcript for this project was searched; 34 items is the largest version
found anywhere.

What happened: the list was read at 11 items, the work was done against that
snapshot, and the file was then rewritten wholesale. The additional 23 items
had been added in the meantime and were overwritten on disk.

The 11 items that were actually completed are marked below. Everything else is
untouched and still outstanding. Verbatim, in original order, with no edits.

---

- PLayer should start with no default armor, just their clothes  **[DONE 2026-09-25]**
- Let's increase the height of the starting shelter's ceiling by 50%  **[DONE 2026-09-25]**
- Decrease the spawn rate of the merchant by 50%  **[DONE 2026-09-25]**
- Blood splatter should never appear in an open doorway as it looks like the blood is floating in the air  **[DONE 2026-09-25]**
- While play testing, I heard the 'death' sound when I traveled down a ladder, this was not supposed to happen.  **[DONE 2026-09-25]**
- After dying and restarting in the shelter, sometimes the flaming red sky is no longer visible and the sky is completely dark despite the time of day.  **[DONE 2026-09-25]**
- Opening a bag container makes the noies of opening a chest, we need to ensure the sounds we are using are for the correct items.  **[DONE 2026-09-25]**
- The unlock rate for items is far too aggressive, let's reduce the curve  **[DONE 2026-09-25]**
- The amount of items available to a new game must be VERY limited, making unlocks and replays more enjoyable.  **[DONE 2026-09-25]**
- New Feature, when bridge integrity reaches 100%, a boss should spawn on the first chunk that the player must kill in order to reset the integrity level. This will require the player to prepare carefully for this scenario by storing items in their shelter chest (meaning they also need to unlock the chest and as many other shelter amenities as possible)  **[DONE 2026-09-26]**
- When the player applies crude pressure to a wound and it DOESN'T resolve the wound, they need to lose health, otherwise they can just keeep retrying until it works.  **[DONE 2026-09-25]**
- I encountered strange red and blue squares on the floor on dungeon level 2 and I don't know why (screenshot C:\workspace\Tarmin2\docs\DEsign\screenshots\blue_red.png)  **[DONE 2026-09-26]**
- We need a spell that can be unlocked which warps the player back to the shelter  **[DONE 2026-09-26]**
- I found an arcane war book that stated it does 0 dmg  **[DONE 2026-09-26]**
- The merchants available inventory should only pull from the pool of available unlocked items  **[DONE 2026-09-26]**
- After casting a spell, the player recovers 1 MP amost immediately, we need to slow the MP recovery rate and gate it to one of the player's attributes so it can improve over time with leveling  **[DONE 2026-09-26]**
- I encounted the 'undead graveyard' themed chunk but the desecrated graves were invisible despite providing collision to the player's movement, they also show up on the mini-map but nothing renders in the field of view.  **[DONE 2026-09-26]**
- Now thatwe are providing an easy way for the player to return to the shelter (via the spell), let's change the penalty for death so that the player loses their equipped armor and weapons upon death. They always start with the rusty sword (1d3)  **[DONE 2026-09-26]**
- Currently I see special moves in toast text during combat (titan's cleave etc). The list of special moves is far too limited and needs to be dramatically expanded to accommodate the various weapon archtypes and combat animations.  **[DONE 2026-09-26]**
- The level curve is a bit too punishing for early leveling. Review recent run telemetry and adjust to make it slightly easier to reach level one but ensure the curve is still balanced.  **[DONE 2026-09-26]**
- When a wound festers into illness, what actually happens? How do you treat illness right now?  **[DONE 2026-09-26]**
- We need a better way to represent water in the 'water logged' themed chunks.  **[DONE 2026-09-26]**
- When the player dies and revives in the shelter, their starting food, hunger and thirst need to reset to original values.  **[DONE 2026-09-26]**
- What happened to the level up system where the player can unlock skills from a tree and assign attributes increase to their stats?  **[DONE 2026-09-26]**
- I have purchased new assets here C:\workspace\Tarmin2\docs\game_assets\bt25_ultimatesfxforgames_softwarebundle, we need to perform a detailed analysis and catalogue of all of the new effects and determine what we want to use in the game. This needs to be documented in a new markdown file that stores the research for future analysis  **[DONE 2026-09-26]**
- I have purchased new assets here C:\workspace\Tarmin2\docs\game_assets\, we need to perform a detailed analysis and catalogue of all of the new effects, sounds and images and determine what we want to use in the game. This needs to be documented in a new markdown file that stores the research for future analysis  **[DONE 2026-09-26]**
- We need to introduce variety in the types of Key's the player finds and only certain key types should unlock certain locked containers, otherwise a single key can unlock all containers for the rest of the game.  **[DONE 2026-09-26]**
- We need to overhaul ALL weapons and their damage, damage types, combat animation and special aspects (bleed, bludgeon / stun, sever, etc) use open5e website to source the info  **[DONE 2026-09-26]**
- When viewing items on the ground in the notification window OR in investory, we should indicate clearly whether or not the item is better then what is currently equipped by the player in the equivalent slot  **[DONE 2026-09-26]**
- Monsters should ALWAYS leave a corpse when killed that persists  **[DONE 2026-09-26]**
- Spells learned by reading random spell books in the field should only apply to that run. Spells learned via unlocking via the leveling system or TOMES should be permanent and remain with the player after death.  **[DONE 2026-09-26]**
- I drank a potion of heroism and the listed effect did not actually happen, what other effects attached to consumables are not implemented??  **[DONE 2026-09-26]**
- A was able to engage in combat with a Zombie through a wall.  **[DONE 2026-09-26]**
- The HUD UX will sometimes get out of alignment (see badUX.png) in screenshots. We need to polish the HUD UX to be stable, responsive and READABLE  **[DONE 2026-09-26]**
- I have included new ceiling texture files in assets/images. These should be used whn the player is underground and randomized along with the standard wall texture which we'll now use for the ceiling (instead of just re-using the floor texture for the ceiling like we do today.)  **[DONE 2026-09-26]**
- We need a better solution to represent corpses of monsters. Right now we are showing the player characters bones corpse for every monster.  **[DONE 2026-09-27]** — monster remains split into their own scenery types. A clean kill leaves the monster's own sprite, rotated, darkened, flattened and over a blood pool; a dismembered or obliterated kill leaves a gore pile instead. Butchering now costs 3 turns and usually yields nothing, since instant guaranteed meat+bone made the cooking chain unlimited. Two things found alongside: monster corpses were also inheriting the ethereal cyan tint meant for sleeping hero bones, and `SceneryData` never persisted `bonesData` — so a dead hero's ghost, epitaph and grave loot were lost on chunk reload and the remains became butcherable meat. **Known gap: the retro raycaster shows none of this — remains appear there as upright, fully-lit living monsters.**
- For the random, varied wall textures in the maze, we should use the default wall 75% of the time and intersperse the others 25% of the time.   **[DONE 2026-09-27]** — per-face roll, 75% default and the remaining quarter split evenly across the other five (~5% each). Grey chunks default to `grey_wall.png`, not the green wall. MAZE biome only.
- There are now multiple variations of the floor texture. let's randomly intersperse the new versions about 25% of the time, but default to floor.png for the remaining 75%.  **[DONE 2026-09-27]** — same 75/25 rule, rolled **per tile**. Floors were previously one texture for a whole chunk, so the eight variants could only ever have said "this room is different". MAZE biome only, so forest, desert and lakelands keep their own authored floors.
- The ceiling textures need to follow the same logic, but with the default ceiling texture being ceiling.png  **[DONE 2026-09-27]** — per-tile, underground only, 75% default with `ceiling_1..8.jpg` as the 25%. The default is **`wall.png`**, per the original note ("randomized along with the standard wall texture which we'll now use for the ceiling") — a dungeon ceiling is the same masonry seen from below. Two wrong defaults shipped first: `floor.png` (the original complaint), then `ceiling_1.jpg`, promoted silently because the assumed `ceiling.png` does not exist. A test now fails if a `ceiling.png` is ever added, so that choice has to be made deliberately rather than by filename.
- Let's overhaul the spell system entirely. Right now I don't even know how to access the spell book, it's too hard to unlock spell slots via the Tome. I want to be able to unlock slots via leveling and the Tomes are just bonus unlocks. Once we stabilize this then it's time to go one by one for the spells in the game and spend time on their animations and effects in great detail.
