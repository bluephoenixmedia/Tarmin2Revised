# Requirements: Shelter Roads

**Status**: Implemented on branch `feat/shelter-roads`, cut from `develop` after the Blight merge, because it builds on the castle corridor and world-gen versioning (target: 0.0.2).
**Date**: October 2026
**Origin**: design grilling session, 2026-10-07. The designer was asked and answered every decision below.
**Related Documents**:
- `docs/DEsign/Requirements_ Procedural World & The Blighted Marches.md` (castle site, corridor, world-gen versions, Taint)
- `docs/Implementation Plan_ The Expedition Loop & Progression Reboot.md` (Phase 5, the Seals of Tarmin)
- `docs/tuning_knobs.md`

---

## 1. Why this document exists

The game should be about travelling from shelter to shelter, in the spirit of *The Long Journey*. Today there is one shelter. It is hard-coded to chunk (0, 0) on level 1, and every expedition starts and ends there. The overland journey only goes out and comes back again; nothing pulls the player forward. The portals were added partly so the designer could reach biomes quickly for testing.

What the code does today:

| Concern | Today |
| :--- | :--- |
| Shelters | One, at (0, 0). `WorldManager` checks `chunkId.x == 0 && chunkId.y == 0` in several places. |
| Unlocked stations | A global set on `ShelterAltar`, saved once, and shown in that one shelter. |
| Stash | `shelter_chest.json`, global. |
| Respawn | Death wipes every chunk, rerolls `masterSeed`, and wakes the player at (0, 0). |
| Direction | Only the castle has a landmark (skybox model + billboard). Nothing else on the horizon tells you where to go. |
| Seals | `CastleGate` always refuses. Seals do not exist yet. |

---

## 2. Scope

**In scope (0.0.2)**
1. Shelters are equal places. Any claimed shelter is a full shelter.
2. Four roads leave home: one castle road and three seal roads, with shelters along each.
3. Off-road shelters scattered through the world.
4. Claiming a cold shelter by lighting its hearth with a Tinder Bundle.
5. Beacons on the horizon, colour-coded by road.
6. Seal sites as hooks (an entrance that is sealed, plus a pillar beacon). `CastleGate` counts seals.
7. Remembered road progress across a death.
8. Portals arrive at castle-road shelters.
9. World-gen v3, plus a one-time upgrade wipe for v1/v2 saves.
10. Debug cheats for warping to and claiming shelters.
11. Bug fix: the debug key legend was drawn under the minimap (done, §12).

**Out of scope**
- Shelter lore. The hooks for it are the shared stash, the stations appearing in every shelter, and lighting the hearth. The lore itself comes later.
- Seal bosses and their strata. The seal site is a hook only (§7).
- Fast travel between shelters (§9).
- Shelters underground.

---

## 3. Shelters are equal places

- A **claimed** shelter is a full sanctuary under the home shelter's rules:
  - No monsters spawn in it and none walk in.
  - The Shelter Altar is present. Unlocking a station at any shelter unlocks it everywhere at once.
  - Every station in `ShelterAltar`'s global unlocked set appears in every claimed shelter.
  - Resting in its Bed (once unlocked) heals, clears ailments, saves, and **clears Taint**. The Blight shelters on the castle road are the Taint reset points that make the final push possible.
- **The stash is one shared inventory** that every shelter's Stash Chest opens.
- **Biome portals stand only in the home shelter.**
- **The last shelter rested in is the respawn shelter** (§8).
- An **unclaimed (cold)** shelter is an ordinary building: monsters can wander in, it has no stations, and it has an unlit hearth.
- **Form**: a small sanctuary building (the 4x4 room) set into an ordinary biome chunk, with a biome-themed exterior: log cabin (Forest), adobe (Desert), stilt hut (Lakelands), snowed-in lodge (Tundra), fortified ruin (Blight). The home shelter keeps its current layout.
- Shelters exist only on the surface, never in strata.

**Acceptance**
- Unlock a station in shelter A, walk to claimed shelter B, and the station is there.
- An item stored in A's chest can be taken out of B's chest.
- Resting in a claimed Blight shelter's bed clears Taint.

---

## 4. Roads

- There are **four roads**, spaced about 90° apart, starting from the castle's seeded bearing (the existing §5 castle site). Direction stays unpredictable from save to save. The roads end up as N/E/S/W rotated by a random angle.
  - The **castle road** ends at the Castle Tarmin site (40-60 chunks out, as now).
  - Each of the **three seal roads** ends at a seal site (§7).
- **Seal-site distances are staggered**, giving a difficulty ladder that lines up with the portal tiers. Defaults are **20, 30 and 40 chunks**. The seed decides which road gets which distance. All three are tunables in `WorldConstants` and listed in `docs/tuning_knobs.md`.
- Every road gets the castle corridor's walkability guarantee: no OCEAN or MOUNTAINS within 1 chunk of the line, so every road can always be walked overland.
- **Road shelters** sit every **8-10 chunks** (tunable) along each road, the first 3 chunks past the maze edge, none within 3 chunks of the road's end. That gives about 5 on the castle road. A road too short for that (a 20-chunk seal road on a diagonal leaves the maze 14 chunks out) still gets one shelter, halfway from the maze edge to the seal site.
- **Off-road shelters**: about 1 per 150 surface chunks outside the roads, at least 8 chunks from any other shelter (both tunable).
- Shelter positions are deterministic for a given seed. They are worked out from the seed alone and never depend on which chunks have been generated.

**Acceptance (seed sweep)**
- Four roads, with neighbouring bearings within 90° ± 15° of each other.
- Every road shelter and road end can be reached overland from the maze edge.
- The gap between consecutive shelters on a road is always within the spacing band, so the next shelter is always within beacon range of the last (§6).
- No two shelters are closer than the minimum spacing.

---

## 5. Claiming a shelter: Tinder Bundle

- New item `TINDER_BUNDLE`. A new character starts with 2, and a respawn tops the pack up to 1.
- Crafted at the Crafting Bench (and the field toolkit): 2 Sticks make 1 bundle, 1 Firewood makes 2. Logged in `docs/tuning_knobs.md`.
- It also drops in normal container loot (weight 8), and half of all outposts keep a bundle by the door, so it turns up more often along the roads.
- **Lighting the hearth**: bump or use the cold hearth while carrying tinder. It is a channelled action of about **5 turns** (tunable), interrupted by taking damage, and it uses up one bundle. When it finishes, the shelter is claimed, its stations appear, and it becomes a sanctuary.
- The home shelter is always claimed.
- Tinder must never become a hard lock: it can always be crafted from common materials.

**Acceptance**
- With no tinder, the hearth explains what is needed.
- Damage during lighting interrupts it and keeps the bundle.
- A claimed shelter stays claimed across a save and load.

---

## 6. Beacons

Beacons are drawn the way the castle is: on the skybox, at the real bearing to the shelter. The renderer draws only the current chunk, so a beacon cannot be world geometry.

| Source | Look | Shown |
| :--- | :--- | :--- |
| Unclaimed road shelter | Smoke column by day, fire glow at night, **in the road's colour** | Within **14 chunks** (tunable) |
| Claimed shelter | Steady glow in its colour, plus a map marker | Within range |
| Castle road | **Purple**, for every shelter on that road in every biome, not only inside the Blight | |
| Seal roads | One distinct colour each (for example amber, teal, white). Colours come from `HudSkin`/`UiTheme`-style constants, never literals in screen code. | |
| Off-road shelter | Plain grey hearth smoke | Within range |
| Seal site | A tall **pillar of light** in the road's colour | Within 14 chunks |
| Road whose seal you've already won | The road's colour, **dimmed** | |

- Beacons show through Blight fog.
- The ranges and spacings are first guesses; the designer expects to adjust them in playtesting.

**Acceptance**
- From any road shelter, the next shelter on the same road shows a beacon at the correct bearing.
- A beacon is hidden once the player is in the shelter's chunk.

---

## 7. Seal sites (hook)

- Each seal road ends at a **seal site**: a sealed strata entrance, marked by the pillar beacon.
- Bumping it says the way is sealed. Like the castle door, it is a defined hook. A later feature replaces the refusal with the descent to the deep boss who holds the seal (Phase 5, "Boss encounters in deep strata guarding the ancient Seals of Tarmin").
- **Seals are permanent progress**, kept like station unlocks. They survive death and world rerolls.
- `CastleGate` counts seals: **3 of 3** opens the gate (the castle interior is still Phase 5). For now it reports how many seals the player holds.

**Acceptance**
- A debug-granted seal survives a death and a world reroll.
- `CastleGate` refuses with 0-2 seals and passes with 3.

---

## 8. Death and remembered progress

Death still **wipes the explored world and rerolls the seed**. The new world remembers how far the player got along each road:

- Each road keeps its **claimed places**: the new world generates its roads and pre-claims the same positions on each (the first, the fourth, ...), so a player who skipped ahead keeps their place. Places past the end of a shorter new road are lost.
- **The road of the last shelter rested in loses one**: its furthest-out claim. If that shelter was home or an off-road shelter, no road loses anything.
- A road whose seal has been won never loses a shelter.
- **Off-road shelters are always forgotten.**
- **Respawn**: the player wakes at the shelter in the same position on the same road as the last rest. If that position was lost to the penalty, they wake at the nearest remembered shelter behind it on that road, or at home if none is left. If they last rested at home or off-road, they wake at home.
- The Corpse Run and the Doom Clock are unchanged.

**Acceptance**
- Claim 3 shelters on road A and 2 on road B, rest at A#3, and die. The new world has A at 2 and B at 2, and the player wakes at A#2.
- Rest at an off-road shelter and die. Every road keeps its count, and the player wakes at home.

---

## 9. Portals and travel

- Each biome portal now arrives **next to the first castle-road shelter inside its biome band**. That shelter is still cold, and the player lights it. The return portal works as before. This replaces the current arrival rules, including the Crimson Gate's `findCorridorBlightEntry`. The designer treats this as an experiment; the portals started as a testing aid.
- **No fast travel between shelters.** Walking the road is the point. Fast travel may come later.

---

## 10. World-gen v3

- Roads and shelters exist only in `worldGenVersion = 3`. New games use v3.
- A v1 or v2 save that loads gets a **one-time upgrade wipe** (the same path as a death wipe, with no penalty) and a combat-log message saying the world has shifted. This is 0.0.2-SNAPSHOT, so there are no players' worlds to protect.

---

## 11. Debug

Add to `DebugCheats`, listed in `DebugKeys` so the legend matches:
- `;` warps to the next castle-road shelter (cycles *n*).
- `'` warps to the next seal site (cycles *k*).
- `\` claims every shelter on the road last warped to (castle road by default).
- `/` grants the seal of the last seal road warped to (else the next seal not held).

---

## 12. Bug: debug key legend hidden by the minimap (done)

`GameScreen.renderDebugLegend` anchored itself to the stage's top-right corner, which `Hud` reserves for the minimap, so the map was drawn over it. The legend now measures from `Hud.MINIMAP_*` and sits immediately left of the minimap box.

---

## 13. Issue slices

1. Shelter as a place: generalise the (0, 0) checks into "is a claimed shelter here". Stations, sanctuary rules and the shared stash work in any claimed shelter. Last-rested shelter is saved.
2. World-gen v3: four road bearings, staggered seal distances, road corridors, shelter positions (road and off-road), plus the upgrade wipe. Blocked by 1.
3. Shelter building and biome-themed exteriors in each surface generator. Blocked by 2.
4. Tinder Bundle and lighting the hearth. Blocked by 3.
5. Beacons (skybox columns, glows, seal pillars, colours, ranges) and map markers. Blocked by 2.
6. Seal sites (hook), permanent seals, and `CastleGate` seal count. Blocked by 2.
7. Remembered road progress and respawn across death. Blocked by 1 and 2.
8. Portals arrive at castle-road shelters. Blocked by 2.
9. Debug cheats. Blocked by 2.
