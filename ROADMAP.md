# Feature Roadmap

> Part of the Tarmin2 agent context system. See `AGENT.md` for the full file map.
> Last updated: 2026-04-19

---

## Status Key
- `DONE` — Implemented and functional
- `IN PROGRESS` — Active development (current branch or recent commits)
- `PLANNED` — Specified in `spec.md`, not yet started
- `PARTIAL` — Foundation exists, incomplete

---

## Core Gameplay Loop

| Feature | Status | Notes |
|---|---|---|
| Procedural maze generation | `DONE` | Room templates, corridor stitching, connectivity validation |
| First-person raycasting renderer | `DONE` | DDA algorithm, depth buffer, MODERN + CLASSIC modes |
| Player movement & collision | `DONE` | Grid-based, checks `Maze.wallData` |
| Level transitions (ladders) | `DONE` | Descend to next depth |
| Turn-based combat | `DONE` | State machine in `CombatManager` |
| Dice-based combat resolution | `IN PROGRESS` | `feature/card-combat` branch |
| Combat dice UI overlay | `IN PROGRESS` | `CombatDiceOverlay` |
| Weapon animation overlay | `IN PROGRESS` | `FirstPersonWeaponOverlay` |
| Monster AI pathfinding | `DONE` | A* + greedy fallback via `Pathfinder` |
| Inter-monster faction combat | `DONE` | Monsters attack rival `MonsterFamily` factions |
| Ranged combat (bows, wands) | `DONE` | `HitResult` projectile raycasting |
| Spell system | `DONE` | 6 spells implemented |

---

## Survival Systems

| Feature | Status | Notes |
|---|---|---|
| War Strength (WS / HP) | `DONE` | Core health stat |
| Spiritual Strength (SS / MP) | `DONE` | Mana for spells |
| Satiety (hunger) | `DONE` | Decays over time, penalizes when low |
| Hydration (thirst) | `DONE` | Decays over time, penalizes when low |
| Body temperature | `DONE` | 32°C–41°C range, hypo/hyperthermia debuffs |
| Toxicity / glass-cannon | `DONE` | +20% damage, -20% defense at high toxicity |
| Status effects (30+ types) | `DONE` | `StatusManager` per entity |
| XP and leveling | `DONE` | WS/SS scaling on level up |

---

## Items & Equipment

| Feature | Status | Notes |
|---|---|---|
| Item system (450+ types) | `DONE` | JSON-driven, color-tiered |
| Melee weapons | `DONE` | 200+ weapon templates |
| Ranged weapons (bows) | `DONE` | Arrow capacity system |
| Spiritual weapons | `DONE` | SS-driven weapons |
| Armor system | `IN PROGRESS` | `armor.json` being restructured on current branch |
| Item modifiers / enchantments | `DONE` | `ItemModifier` with `displayName` field (just added) |
| Rings | `DONE` | `RingEffectType` effects |
| Scrolls | `DONE` | `ScrollEffectType` effects |
| Wands | `DONE` | `WandEffectType` effects, charge system |
| Consumables / potions | `DONE` | 8+ `PotionEffectType` values |
| Item identification (roguelike) | `DONE` | `DiscoveryManager` tracks identified types |
| Beatitude (cursed/blessed) | `DONE` | Affects item behavior and unequip |
| Colored chests / keys | `DONE` | `Gate` locked by `ItemColor` matching key |
| Shop / NPC trading | `PARTIAL` | `ShopkeeperNpc`, `ShopkeeperWindow` exist; not fully integrated |
| Artifacts / unique items | `PARTIAL` | `Artifact` class exists as dice upgrade; broader unique item system not started |

---

## World & Generation

| Feature | Status | Notes |
|---|---|---|
| Dungeon biome | `DONE` | `MazeChunkGenerator` |
| Forest biome | `DONE` | `ForestChunkGenerator`, seamless transitions |
| Desert biome | `DONE` | `DesertChunkGenerator` |
| Castle biome | `PARTIAL` | `CastleMapScreen` exists; generator in progress |
| Dynamic doors | `DONE` | Open/close, affects `wallData` |
| Colored gates | `DONE` | Require matching key item |
| Weather system | `DONE` | `WeatherManager`, `WeatherRenderer`, dynamic skybox |
| Day/night cycle | `DONE` | `DayNightManager` |
| Encounter statues | `DONE` | Grid-triggered events from `encounters.json` |
| Trap system | `PLANNED` | Specified in `spec.md` — not started |
| Dynamic lighting point sources | `PLANNED` | Specified in `spec.md` — not started |

---

## Crafting & Economy

| Feature | Status | Notes |
|---|---|---|
| Cooking system | `DONE` | Gib + Kindling + Water → Meal |
| Alchemy | `DONE` | Potion brewing |
| Crafting (general) | `PARTIAL` | `CraftingManager` exists; limited recipes |
| Gold / currency economy | `PARTIAL` | Item values set; no gold item yet |

---

## Visual & Audio

| Feature | Status | Notes |
|---|---|---|
| Textured (MODERN) renderer | `DONE` | |
| Classic (untextured) renderer | `DONE` | Toggle at runtime |
| CRT post-processing shader | `DONE` | Scanlines, barrel distortion |
| Screen shake (trauma) | `DONE` | Accumulated hit trauma |
| Gore system (gibs, blood, decals) | `DONE` | Pooled, persistent wall decals |
| Paperdoll character system | `DONE` | Verlet physics cloth simulation |
| Directional monster sprites | `DONE` | Recent commit: 4f27a76 |
| Animated monster sprites | `DONE` | Recent commit: 4f27a76 |
| Sound effects | `DONE` | `SoundManager` |
| Music system | `DONE` | `MusicManager` |
| Video cutscenes | `DONE` | `JavaCVVideoPlayer` |
| Hallucinogenic rendering (toxicity) | `PLANNED` | Specified in `spec.md` — not started |

---

## AI & Difficulty

| Feature | Status | Notes |
|---|---|---|
| Color-tiered monster scaling | `DONE` | TAN→ORANGE→RED→BLUE by dungeon depth |
| A* pathfinding | `DONE` | |
| Faction inter-monster combat | `DONE` | |
| Fleeing behavior | `PLANNED` | Specified in `spec.md` |
| Ambush behavior | `PLANNED` | Specified in `spec.md` |
| Difficulty settings | `DONE` | `Difficulty` enum, `SettingsManager` |

---

## Technical & Infrastructure

| Feature | Status | Notes |
|---|---|---|
| Debug overlay (F1) | `DONE` | Top-down map, spawn overlay |
| Balance logging | `DONE` | `BalanceLogger` per session |
| Headless stubs | `DONE` | `HeadlessAnimationManager`, `HeadlessSoundManager` |
| Retro sound chip emulator | `DONE` | `AY38914` |
| Unlock system | `DONE` | `UnlockManager`, `UnlockData` |
| Death / Ossuary tracker | `DONE` | `OssuaryScreen` |

---

## Current Sprint — `feature/card-combat`

Active work (see `COMBAT.md` for full details):

1. **Dice system data model** (`Die`, `DieDefinition`, `DieFace`, `DieFaceType`, `DieResult`) — `DONE`
2. **`CombatDiceOverlay`** — dice roll UI display — `IN PROGRESS`
3. **`FirstPersonWeaponOverlay`** — weapon swing animation — `IN PROGRESS`
4. **`armor.json` restructure** — new stat fields — `IN PROGRESS`
5. **`ItemModifier.displayName`** — display name field added — `DONE`
6. **`DebugManager` overhaul** — enhanced debug features — `IN PROGRESS`
7. **`PLAYER_SELECT_DICE` state** — player chooses which die — `IN PROGRESS`
