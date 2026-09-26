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
- New Feature, when bridge integrity reaches 100%, a boss should spawn on the first chunk that the player must kill in order to reset the integrity level. This will require the player to prepare carefully for this scenario by storing items in their shelter chest (meaning they also need to unlock the chest and as many other shelter amenities as possible)  **[SPEC WRITTEN, NOT BUILT - see Requirements_ Bridge Integrity Boss.md]**
- When the player applies crude pressure to a wound and it DOESN'T resolve the wound, they need to lose health, otherwise they can just keeep retrying until it works.  **[DONE 2026-09-25]**
- I encountered strange red and blue squares on the floor on dungeon level 2 and I don't know why (screenshot C:\workspace\Tarmin2\docs\DEsign\screenshots\blue_red.png)  **[DONE 2026-09-26]**
- We need a spell that can be unlocked which warps the player back to the shelter
- I found an arcane war book that stated it does 0 dmg  **[DONE 2026-09-26]**
- The merchants available inventory should only pull from the pool of available unlocked items
- After casting a spell, the player recovers 1 MP amost immediately, we need to slow the MP recovery rate and gate it to one of the player's attributes so it can improve over time with leveling
- I encounted the 'undead graveyard' themed chunk but the desecrated graves were invisible despite providing collision to the player's movement, they also show up on the mini-map but nothing renders in the field of view.  **[DONE 2026-09-26]**
- Now thatwe are providing an easy way for the player to return to the shelter (via the spell), let's change the penalty for death so that the player loses their equipped armor and weapons upon death. They always start with the rusty sword (1d3)
- Currently I see special moves in toast text during combat (titan's cleave etc). The list of special moves is far too limited and needs to be dramatically expanded to accommodate the various weapon archtypes and combat animations.
- The level curve is a bit too punishing for early leveling. Review recent run telemetry and adjust to make it slightly easier to reach level one but ensure the curve is still balanced.
- When a wound festers into illness, what actually happens? How do you treat illness right now?
- We need a better way to represent water in the 'water logged' themed chunks.  **[DONE 2026-09-26]**
- When the player dies and revives in the shelter, their starting food, hunger and thirst need to reset to original values.
- What happened to the level up system where the player can unlock skills from a tree and assign attributes increase to their stats?
- I have purchased new assets here C:\workspace\Tarmin2\docs\game_assets\bt25_ultimatesfxforgames_softwarebundle, we need to perform a detailed analysis and catalogue of all of the new effects and determine what we want to use in the game. This needs to be documented in a new markdown file that stores the research for future analysis
- I have purchased new assets here C:\workspace\Tarmin2\docs\game_assets\, we need to perform a detailed analysis and catalogue of all of the new effects, sounds and images and determine what we want to use in the game. This needs to be documented in a new markdown file that stores the research for future analysis
- We need to introduce variety in the types of Key's the player finds and only certain key types should unlock certain locked containers, otherwise a single key can unlock all containers for the rest of the game.
- We need to overhaul ALL weapons and their damage, damage types, combat animation and special aspects (bleed, bludgeon / stun, sever, etc) use open5e website to source the info
- When viewing items on the ground in the notification window OR in investory, we should indicate clearly whether or not the item is better then what is currently equipped by the player in the equivalent slot
- Monsters should ALWAYS leave a corpse when killed that persists
- Spells learned by reading random spell books in the field should only apply to that run. Spells learned via unlocking via the leveling system or TOMES should be permanent and remain with the player after death.
- I drank a potion of heroism and the listed effect did not actually happen, what other effects attached to consumables are not implemented??  **[DONE 2026-09-26]**
- A was able to engage in combat with a Zombie through a wall.  **[DONE 2026-09-26]**
- The HUD UX will sometimes get out of alignment (see badUX.png) in screenshots. We need to polish the HUD UX to be stable, responsive and READABLE  **[DONE 2026-09-26]**
- I have included new ceiling texture files in assets/images. These should be used whn the player is underground and randomized along with the standard wall texture which we'll now use for the ceiling (instead of just re-using the floor texture for the ceiling like we do today.)  **[DONE 2026-09-26]**
