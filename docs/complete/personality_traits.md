# Personality Traits

Design for item 29 of `new_items_9_28_26.md`. All 18 traits are built and offered (`assets/data/traits.json`); every number is a starting value to tune. Where the shipped card differs from this draft, the shipped wording is noted in the row.

## How traits work (agreed)

- On a new game and on every 5th respawn the player is offered **3 traits** at random from the pool (never the one they already have) and picks **one**. They may also keep their current trait instead.
- One trait at a time. It lasts until the next choice, through the deaths in between. The design leaves room for more than one later.
- Each trait is **a small stat change plus one rule-changing quirk**, with a **good side and a bad side**, both shown on the card.
- The choice is a three-card modal over the shelter. If the player quits before choosing, the choice is still waiting when they return.
- Inspirations: Caves of Qud mutations, Darkest Dungeon quirks, Fallout 4 traits.

**Hook** column: *existing* means the game already has a place to apply it (crit chance, luck, XP, hunger and thirst rates, light radius, shop prices, spell cost, dodge, Magic Resistance, regeneration, max HP/MP, status effects). *new* means a small new hook has to be added.

## The three you named

| Trait | Good | Bad | Hooks |
|---|---|---|---|
| **Silent Lunatic** | Monsters notice you from about half as far away (sight and hearing range x0.5). | Every turn a hostile monster is in view there is a 10% chance you go **Berserk** for a few turns. Spells cost 50% more MP. | Notice range: **new** (the monster AI has no player-side hook). Berserk: existing status. Spell cost: existing. |
| **Angry Genius** | +3 Intelligence. +25% experience. Spells cost 1 less MP (minimum 1). | You take 20% more damage. 10% of your attacks fail outright. | Existing: stats, XP, spell cost. Damage taken and attack failure: **new**, small. |
| **Stoic Clown** | HP regenerates 30% faster. You resist fear and mind effects (confusion, sleep, charm). | Shop prices are 25% worse. Crit chance -5%. | Existing: regen, shops, crit. Mind-effect resistance: **new**, small. |

## Fifteen more

| # | Trait | Good | Bad | Hooks |
|---|---|---|---|---|
| 1 | **Gentle Brute** | +3 Strength. Carry capacity +30%. | -2 Charisma. Shop prices 20% worse. | Existing |
| 2 | **Paranoid Scout** | Carried light radius +1.5. Mimic detection +25 points. | Resting at the bed restores only half your missing HP and MP. | Light: existing. Mimic detection: existing. Bed rest: **new**, small. |
| 3 | **Lucky Fool** | +4 Luck (which also nudges crit chance up). | Maximum HP -20%. | Existing |
| 4 | **Greedy Scholar** | +50% Divinities from kills and new chunks. Sells items for 25% more. | Buys cost 25% more. -1 Constitution. | Existing |
| 5 | **Fasting Monk** | Hunger and thirst fall 50% slower. | Maximum HP -25%. Equipment AC -2. | Existing |
| 6 | **Reckless Duelist** | Crit chance +15%. Crit damage +0.5x. | AC -2. Dodge chance halved. | Existing |
| 7 | **Cowardly Alchemist** | Potions and food heal 50% more. | Melee damage -25%. | Healing amounts: **new**, small. Melee damage: existing. |
| 8 | **Night Owl** | +2 light radius underground. +10% dodge below the surface (shipped as "underground"). | Torch and lantern light is 1 tile shorter on the surface. -1 Wisdom. | Light: existing. Dodge in dark: **new**, small. |
| 9 | **Iron Stomach** | Immune to poison and sickness. Raw monster flesh cannot hurt you. | You choke on food 10 fullness points sooner than others. | Poison: existing. Raw meat and the overfeed limits: **new**, small. |
| 10 | **Ghost Whisperer** | +20% Magic Resistance. Spells that hit the undead do +2 damage per die. | Living monsters notice you from 25% farther away. | Magic Resistance: existing. Notice range: **new**. Undead damage: **new**, small. |
| 11 | **Hardy Cynic** | Maximum HP +25%. Poison and bleed damage halved. | -25% experience. Monster kills give 1 less Divinity. | Existing |
| 12 | **Born Coward** | Move speed +25%. Dodge +10%. | Weapon damage -2 (minimum 1) against a monster that is hunting you at point-blank range. | Speed and dodge: existing. Damage rule: **new**, small. |
| 13 | **Lucky Pariah** | Crit chance +10%. Found gold and gems +30%. | Shopkeepers will not trade with you; the shelter stash is the only place to sell. | Crit: existing. Gold bonus and trade refusal: **new**. |
| 14 | **Hollow Prophet** | Learns spells from books one level early. Spell cost -25%. | Maximum HP -30%. Regeneration 50% slower. | Spell cost and regeneration: existing. Early learning: **new**, small. |
| 15 | **Bloodsoaked Saint** | Heals 1 HP for every 4 damage you deal in melee. | You are 25% more likely to suffer a lasting wound (slower-closing wounds were not built). | Lifesteal: **new**, small. Bleed chance: existing. |

## Open design points

- **Balance:** the good and bad sides are meant to roughly cancel, but a few (Lucky Pariah, Hollow Prophet, Bloodsoaked Saint) are strong enough to need playtesting before they stay.
- **Trait pool size:** 18 traits give three offers per choice with little repetition across several respawns.
- **Notice range** is used by Silent Lunatic and Ghost Whisperer, so it should be built once and shared.
- **Data-driven:** traits should live in a JSON file (name, description, good and bad text, numeric modifiers, optional quirk id) so they can be tuned and extended without recompiling.
- **Later:** more than one trait at a time, traits unlocked by progress, and a "mutation" layer that changes a trait over a run.
