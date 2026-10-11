# Specification: Enhanced 3D Main Menu Flyover (Attract Mode)

## Overview & Vision
Transform the Main Menu 3D Flyover (`AttractModeRenderer`) from a quiet architectural survey into an epic, cinematic showcase of the entire living game world. As the camera travels along the 90-second closed-loop Catmull-Rom spline, the player witnesses a world at war: full dynamic skies, biome weather transitions, warring faction columns and skirmishes, soaring arrow volleys, distant smoke plumes, wilderness predators, loot, and a climactic midnight castle siege under a thunderstorm.

---

## 1. Living War & Faction Architecture
- **Active War Selection**: Query `HistorySimulator` for the active war currently waged between two Houses in the latest historical era. If no save exists, fallback to a canonical rivalry (e.g. **Ashen Penitents** vs. **Chainwrights** or **Antlered Host** vs. **Ossuary Lords**).
- **Faction Presentation**:
  - Armies wear authentic doctrine heraldry and chest tabards (`assets/images/paperdoll/chest/tabard_*.png`).
  - **Marching Columns**: Infantry squads in close formation marching along roads and clearing corridors.
  - **War Camps**: Pitched faction tents, supply crates, banners, weapon racks, and burning campfires with rising vertical smoke pillars.
  - **Active Melee Skirmishes**: Opposing battle lines clashing with attack telegraphs, swinging weapons, hit flashes, shield blocks, and blood splatters.
  - **Fortress Defenders**: Defenders manning castle battlements and gatehouses.

---

## 2. Dynamic 3D Skybox, Lighting Arc & Weather
- **Skybox Restoration**: Re-enable `Skybox3DRenderer` with sun, moon, stars, dynamic clouds, and celestial day-night lighting.
- **Void Floor Seal**: Floor gaps in ruins are underlaid with an authentic dark abyss plane / liquid pools so no unrendered voids leak through the terrain.
- **24-Hour Cinematic Lighting & Weather Arc**:
  1. **Sector 1 (Lakelands, t = 0s - 18s)**: *Dawn / Morning*. Misty low water canal skimming, soft morning sunbeams, gentle water ripples.
  2. **Sector 2 (Ancient Forest, t = 18s - 36s)**: *High Noon*. Verdant canopy, sun shafts filtering through branches, mild woodland breeze.
  3. **Sector 3 (Desert Canyons, t = 36s - 54s)**: *Dusk / Golden Hour*. Fiery sunset over sandstone cliffs, warm heat mirage, blowing sand dust.
  4. **Sector 4 (Mountain Pass, t = 54s - 68s)**: *Nightfall / Purple Twilight*. Cold mountain gorge, moonlight on granite crags, glowing campfires in the ravines.
  5. **Sector 5 (Castle Tarmin & Grand Moat, t = 68s - 90s)**: *Midnight Thunderstorm*. Total overcast, driving 3D rain streaks, dramatic lightning flashes illuminating castle spires and arrow crossfire.

---

## 3. Projectile Ballistics & Distant VFX
- **Overhead Arrow Volleys**:
  - Archers and siege batteries release synchronized parabolic volleys.
  - Arrows fly overhead directly past the camera lens with directional 3D audio whooshes.
  - Arrows strike wooden barricades, shields, and ground flags with impact dust puffs and quivering stuck shafts.
- **Distant Environmental VFX**:
  - Multi-tier smoke columns rising from war camps, burning siege engines, and distant ruins.
  - Campfires and torches with dynamic flickering point lights.

---

## 4. Wilderness Beasts & Ground Dressing
- **Wild Predators & Megabeasts**:
  - A massive, legendary Megabeast lumbering or roaring in the high mountain gorge / desert canyon.
  - Native wilderness beasts (bears, spiders, dire wolves) prowling forest pathways.
  - Blighted horrors prowling the dark castle moat perimeter.
- **Narrative Ground Dressing**:
  - Discarded weapons, shields stuck in the dirt with house insignias, broken cartwheels, supply chests, and rare glowing artifacts on stone pedestals.

---

## 5. Audio & Expedition Dive Transition
- **Layered Audio Mix**:
  - Main soundtrack: `Crown_of_Molten_Steel.mp3` at full fidelity.
  - Diegetic Underlay: Low-pass filtered distant war drums, war horns, close-range arrow whooshes, thunderclaps during lightning, and spatialized monster roars.
- **Expedition War Dive**:
  - Clicking "New Game" or "Continue" accelerates the camera directly through the arrow crossfire over the moat, under the raised portcullis, and into the dark archway as battle audio peaks, cutting cleanly into the game screen.

---

## 6. Performance & Streaming Budget
- **Spline-Sector Cluster Streaming**:
  - 5 deterministic sector actor pools tied to spline knot progress.
  - Only actors within active camera frustum (~35 tiles) are ticked and batched via `DynamicQuadBatcher`.
  - Zero per-frame heap allocations; rock-solid 60 FPS maintained throughout.
