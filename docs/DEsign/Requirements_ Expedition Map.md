# Requirements: Expedition Map

**Status**: In progress on branch `feat/map`, stacked on `feat/shelter-roads` (the map reads the road layout). Rebase onto `develop` once shelter roads merges.
**Date**: October 2026
**Origin**: design grilling session, 2026-10-08. The designer was asked and answered every decision below.
**Related Documents**:
- `docs/DEsign/Requirements_ Shelter Roads.md` (roads, beacons, seals, claimed shelters)
- `docs/ui-overhaul/SPEC.md` (HUD-7, tokens, the 2B Carved Frame)

---

## 1. Why this document exists

The map was built for one shelter and a handful of chunks. `CastleMapScreen` shows a fixed 7x7 window of visited chunks, so it cannot show a world whose seal sites stand 20, 30 and 40 chunks out. None of the shelter-roads work appears on it: no roads, no beacons, no lit or cold hearths, no seals. Strata are uniform grey boxes. Every colour is a literal and every string is hand-placed, against the UI overhaul's standing rules.

## 2. Scope

- A new full-screen **Expedition Map** for `GameMode.ADVANCED`.
- The HUD minimap (SPEC HUD-7) built on the same knowledge.
- `GameMode.CLASSIC` keeps the old screen, renamed `ClassicMapScreen` and frozen.

The map answers three questions, in this order:
1. **Where do I go next?** Roads, the next shelter, seal sites, the castle.
2. **What is my logistics?** Lit shelters, return portals, how far from safety.
3. **Where have I been?** Explored chunks, loot left behind, graves.

## 3. What the map knows

Knowledge, not omniscience. Exploring fills the map in.

| Thing | Shown when |
| :--- | :--- |
| A chunk, in full | The player has entered it (it has a chunk save). |
| A chunk, as a faint biome tint ("glimpsed") | It borders a surface chunk the player stood in, and that chunk's biome is seamless (not the maze). |
| A shelter, as "rumoured" (dashed, unlit) | Its beacon has been on the horizon from a chunk the player entered. |
| A shelter, as lit | The player has claimed it (`ShelterNetwork`). |
| A road segment | Both its ends are known (home, a known shelter, or a known road end). |
| A seal site | The last shelter on its road is lit, or the player has entered it. |
| The castle | Its direction always, as an arrow at the World view's edge; its chunk once entered. |
| A hero's grave | The player has seen the tile holding a fallen hero's bones. |
| A return portal | The player has seen its tile. |

There is no corpse-run marker. Death does not leave the player's remains where they fell; the Bones system places them at random on a later floor.

Glimpsed chunks, sighted shelters, pins and the waypoint are new state in `MapKnowledge`, scoped to the save slot like `ShelterNetwork`. A death lays out a new world, so the map forgets the old one's glimpses, sightings, pins and waypoint at the same moment.

## 4. Look

- The 2B Carved Frame chrome, from `UiTheme` / `HudSkin`.
- The map surface is dark vellum (`assets/images/map/dark_vellum.png`, made from the designer's `dark_vellum.jpg` by `tools/make_map_assets.py`).
- Biomes are muted ink washes: `UiTheme.MAP_*` tokens, not saturated fills.
- **Roads take their beacon colour** (`BeaconPalette`): the castle road purple, each seal road its own colour, faded once its seal is won. *Amended from the session's "castle road red, seal roads gold": the sky already colours each road's beacons, so the map matches the sky rather than introducing a second code.*
- The player is the one cool accent (`UiTheme.INFO`).
- Icons are stand-ins tinted from Kenney's *Game Icons* until the generated sheet arrives (prompt in section 9).

## 5. Views

Three zoom steps, each an integer scale, each adding detail rather than repeating it. The view pans freely.

| View | Cell | Shows |
| :--- | :--- | :--- |
| **World** | 12 px per chunk | Biome tints (glimpsed fainter), roads, home, shelters, castle, seal sites, waypoint, suggestion, the player. No text. |
| **Region** | 96 px per chunk | World, plus depth badges, pins, graves, return portals, ladder pips on strata. |
| **Chunk** | Tiles | The cursor chunk's explored tiles: walls in ink, fog in vellum, ladders, items, events, the player and their facing. |

- **Strata** are separate floor views. A surface chunk carries a depth badge (the deepest stratum explored beneath it). A stratum view draws the surface biome above as a faint underlay.
- The map reopens where the player left it this session. The first time, it opens at Region, centred on the player.

## 6. Panels

- **Header**: the floor, seals won, the Doom stage, the waypoint and its distance.
- **Side panel**, for the chunk under the cursor: biome and danger level; shelter state (lit, cold or rumoured, its road, stations installed); strata below; distance from the player and to the nearest lit shelter; the pin; loot left on seen tiles; graves and their epitaphs.

## 7. Actions

- **Waypoint**: one at a time, set on any known chunk; cleared on arrival. The HUD minimap points at it.
- **Suggested next step**: a ghost marker the player can accept with one key. It targets the next cold shelter on the road to the nearest unwon seal site ("nearest" by the site's distance from home), then that road's seal site once its shelters are lit. With all three seals won it walks the castle road instead.
- **Pins**: six fixed icons (danger, loot, return here, trader, locked, unknown), no text.

| Key | Action |
| :--- | :--- |
| Arrows / drag | Pan (moves the cursor) |
| Wheel, `+` / `-` | Zoom step |
| `[` / `]`, PgUp / PgDn | Floor |
| Enter / click | Zoom into the cursor chunk |
| `W` | Set or clear the waypoint |
| `G` | Accept the suggestion |
| `P` | Cycle the cursor chunk's pin |
| `C` | Recentre on the player |
| Esc | Back out one zoom step; close at World |
| `M` | Close |

The map's keys are bindings in `SettingsManager` (`MAP_WAYPOINT`, `MAP_SUGGEST`, `MAP_PIN`, `MAP_RECENTER`).

## 8. HUD minimap

North-up. The current chunk's explored tiles, plus a strip of each loaded neighbouring chunk. Compass letters on the rim, and an arrow on the rim toward the waypoint (or, without one, the suggestion).

## 9. Art

Stand-ins ship now. The final set is AI-generated by the designer from these prompts.

**Icon sheet.** Pixel-art map icon sheet, 24x24 px per icon, one sheet as a 6-column grid on a transparent background, 1 px transparent gutter. Style: hand-inked cartographer's glyphs from a dark-fantasy world map, bold readable silhouettes, 1 px dark outline (#0B0907), at most 3 tones per icon, no anti-aliasing, no gradients, no text, no drop shadows. Palette limited to: parchment #E9D8B4, ink #3B2614, gold #E9B44C, ember #E0533D, pale steel #B39C78. Icons, in order: 1 home hearth (small house with chimney and smoke curl); 2 shelter, lit (outpost hut with a flame above it); 3 shelter, cold (same hut, no flame, darker); 4 Castle Tarmin (jagged three-tower citadel); 5 seal site, unwon (round wax seal with a rune); 6 seal site, won (same seal, cracked open); 7 ladder going up (ladder with an up chevron); 8 ladder going down (ladder with a down chevron); 9 player marker (arrowhead pointing up); 10 waypoint (banner on a pole); 11 hero grave (skull over a cairn); 12 return portal (oval rune-gate); 13 pin: danger (skull); 14 pin: loot (open chest); 15 pin: return here (looped arrow); 16 pin: trader (coin purse); 17 pin: locked (padlock); 18 pin: unknown (question-mark rune stone). Each icon must stay readable at 24 px and doubled to 48 px with nearest-neighbour scaling.

**Compass rose.** Pixel-art compass rose, 64x64 px, transparent background. Dark-fantasy cartographer style, 8 points with the N point long and in gold #E9B44C, the others in ink #3B2614 with parchment #E9D8B4 highlights, 1 px outline #0B0907. No letters (the game font draws N/E/S/W), no anti-aliasing, at most 4 tones.

## 10. Delivery

1. `MapKnowledge` model and tests
2. Screen split at today's feature parity
3. World view, roads and objectives
4. Header and side panel
5. Waypoint and suggestion
6. Pins
7. Strata badges and underlay
8. Graves and portals
9. HUD-7 minimap
10. Art swap (when the generated art arrives)

Follow-up, not in this work: a findable **Cartographer's Chart** that reveals one road and its end.
