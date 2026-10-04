# Choice Events

Short scenes in the style of Slay the Spire and Monster Train, set on hidden tiles in the world.
A scene shows an image and a few lines of text, then offers a handful of choices. Each choice
can cost something, can be gated, can roll against an attribute, and resolves into outcomes:
healing, harm, items, gold, status effects, injuries, or a fight.

This replaces `docs/DEsign/Dungeon Crawler Event Generation.md`, which was written before the
current stat model and biome set existed. That document is now a pool of ideas only. Every
*Death Gate Cycle* name is gone. The Archivist, Oakhaven and Elara are canon. The doc's
"Silence" meter is out of scope.

## Choice events are not statues

The statue encounters (`assets/data/encounters.json`, `EncounterManager`, `EncounterWindow`,
`Scenery.STATUE`) are a separate feature, and choice events leave them alone. The two scenes that
predate the statues, The Broken Mirror and The Weeping Statue, move out of `encounters.json` and
into `events.json`. Nothing else in the statue path changes.

| | Statues | Choice events |
|---|---|---|
| Data | `data/encounters.json` | `data/events.json` |
| In the world | a visible statue you bump or use | an invisible tile you step on |
| Window | `EncounterWindow` | `EventWindow` (Carved Frame) |
| Placement | 1 to 3 per chunk, uniform pick | at most 1 per chunk, filtered by biome and depth |

## Triggering

- An event is an invisible floor trigger. There is no sprite, no sound and no minimap mark.
  It fires when the player steps onto the tile.
- It fires only when the coast is clear: no living, non-allied monster within 4 tiles, and none
  within 8 tiles that has line of sight to the player. If the tile is not clear, nothing happens
  and the tile stays armed for the next time the player steps on it.
- An event fires once. The tile is cleared the moment it fires.
- While the window is open the world does not advance (the game is turn-based and the window
  holds the input context).

## Placement

- At most one event per freshly generated chunk.
- Chance per chunk: 25% for wilderness (Forest, Desert, Lakelands) and 15% for Maze.
- Never on the starting Shelter chunk (level 1, chunk 0,0).
- Never on a chunk's outer ring of tiles. Crossing into a wilderness chunk moves the player
  without a step, so an event on an edge tile would be walked over without firing.
- Classic mode gets no events: it returns the generator's chunk directly, before placement runs.
- The pool is filtered by each event's `biomes` and `minDepth`/`maxDepth`, then picked by
  `weight`, the same min/max-level pattern `spawntables.json` uses.
- No repeats within a run. An event id is marked seen when it is placed. The seen set lives on
  `WorldManager`, is saved in `world.json`, and is cleared when the world is wiped on death or
  reset.
- Events are saved per chunk in `ChunkData.choiceEvents`, apart from the statues' `events`.

## Choices

Every event has at least one choice with no gate and no unaffordable cost, so a player who
stumbles into a scene can always get out.

A choice has, in order of resolution:

1. **Gates** (`requires`): conditions that grey the button out when unmet. Kinds:
   `ATTRIBUTE_MIN` (`attribute`, `value`), `HAS_ITEM` (`itemKind` or `itemId`, `count`) and
   `GOLD_MIN` (`value`). A gate carries the `label` shown on the button when unmet.
   `itemKind` is one of `ANY FOOD SCROLL POTION RING WEAPON RANGED_WEAPON ARMOR SHIELD CURSED
   UNBLESSED`, asked of the item itself; `ItemCategory` cannot be used because it reports scrolls
   and potions as USEFUL.
2. **Costs** (`costs`): outcomes paid up front, before any roll. A cost the player cannot pay greys
   the button out exactly like a gate. Affordability is derived from the cost itself, so it does
   not need a matching gate.
3. **Check** (`check`): optional. If present, the choice rolls for success.
4. **Outcomes**: `success` on a passed check (or when there is no check), `failure` otherwise.

### Odds

```
chance = base + (attribute - 10) * 5% + luck * 2%,  clamped to [5%, 95%]
```

`attribute` is one of `STR DEX CON INT WIS AGI CHA`, read through the player's effective
getters. A check with no attribute is a luck roll: `base + luck * 2%`. The percentage is printed
on the button, for example `[ WIS 55% ]`.

### Outcomes

All outcomes last for the current run only. Meta-progression stays with Divinities and the
Shelter Altar.

| Type | Fields | Effect |
|---|---|---|
| `HEAL` / `DAMAGE` | `amount` | HP up or down. Damage is physical. |
| `RESTORE_MP` / `DRAIN_MP` | `amount` | MP up or down. |
| `MAX_HP` / `MAX_MP` | `amount` (signed) | Max pool up or down, current clamped. |
| `ATTRIBUTE` | `attribute`, `amount` (signed) | Base attribute up or down. |
| `LUCK` | `amount` (signed) | Base luck. |
| `ADD_STATUS` / `CURE_STATUS` | `status`, `duration`, `potency` | Through `StatusManager`. |
| `INJURY` | `bodyPart`, `injury`, `severity` | Through `InjuryManager.inflictInjury`. |
| `GIVE_ITEM` | `itemId` or `itemPool`, `count` | Into the pack, or onto the floor if full. |
| `TAKE_ITEM` | `itemKind` or `itemId`, `count` | Removes carried items, one from a stack at a time. |
| `GOLD` | `amount` (signed) | Gold (`treasureScore`). |
| `SATIETY` / `HYDRATION` | `amount` (signed) | Food and water. |
| `XP` | `amount` | Through the normal level-up path. |
| `CURSE_ITEM` / `BLESS_ITEM` | `itemKind` | A random matching carried item's beatitude. |
| `IDENTIFY_ALL` | | Identifies every carried item. |
| `SPAWN_MONSTER` | `monsterId` | A level-scaled monster on an open tile next to the player. |
| `REVEAL_MAP` | | Marks every tile of the current chunk seen. |
| `CHAIN_EVENT` | `eventId` | Opens another event when this one closes. |

Every outcome may carry `text`, posted to the message log and shown in the window after the
choice. An outcome with only `text` and no `type` is narration, used for walking away.

## Window

`EventWindow` is built to the UI overhaul rules: Scene2D tables, colours and sizes from
`UiTheme`/`HudSkin`, `UiLabels` for wrapping, every string passed through `UiGlyphs`, and an
input context pushed on `UiContexts`. It shows the event's image, title and text, and one button
per choice with its odds or its unmet gate. Number keys pick a choice; arrows and Enter work too.
There is no Escape out of a scene: the ungated choice is the way out. After the player chooses,
the window shows the result text and a Continue button.

Behind the panel sits the event's `backgroundPath`, or the biome default
`images/events/backgrounds/<biome>.png`, dimmed. If neither exists it falls back to the scrim.
If an event's `imagePath` is missing, the window shows the biome placeholder
`images/events/placeholders/<biome>.png`.

Art prompts for every image are in `docs/DEsign/event-art-prompts.md`.

## Debug and tests

- Debug mode (F5) adds **PgDn**: fire a random event that is eligible for the current biome and
  depth, ignoring the seen set.
- `EventDataValidationTest` checks that every event parses, has an ungated choice, references real
  item, monster, status, attribute, injury and body-part names, chains only to real event ids,
  survives `UiGlyphs`, and points at an image that exists or a biome placeholder that does.
- Unit tests cover the odds formula, the pool filter, the trigger rule, costs and gates, and the
  chunk save round trip.

## First batch

| # | Id | Biomes | Depth |
|---|---|---|---|
| 1 | `EVENT_BROKEN_MIRROR` | Maze | 1+ |
| 2 | `EVENT_WEEPING_STATUE` | Forest, Maze | 1-3 |
| 3 | `EVENT_OAKHAVEN_CORPSE` | Maze, Forest | 1+ |
| 4 | `EVENT_CHITINOUS_MOUND` | Forest | 1 |
| 5 | `EVENT_TRADER_OF_ROOTS` | Forest | 1 |
| 6 | `EVENT_SNARED_SCORPION` | Forest | 1 |
| 7 | `EVENT_MIRAGE_VENDOR` | Desert | 1 |
| 8 | `EVENT_SMOKY_BOTTLE` | Desert | 1 |
| 9 | `EVENT_LIGHTNING_SPIRE` | Desert | 1 |
| 10 | `EVENT_REFLECTION_OF_ELARA` | Lakelands | 1 |
| 11 | `EVENT_CORAL_ALTAR` | Lakelands | 1 |
| 12 | `EVENT_DROWNED_LIBRARY` | Lakelands, Maze | 1+ |
| 13 | `EVENT_DROWNING_POOL` | Lakelands | 1 |
| 14 | `EVENT_BLOOD_CIRCLE` | Maze | 4+ |

The full text, choices and numbers live in `assets/data/events.json`, which is the source of
truth. Deep maze (4+) is thin: two more deep-maze events belong in the next batch.
