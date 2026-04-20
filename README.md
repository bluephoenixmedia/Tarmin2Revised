# Tarmin2 (Minotaur)

A first-person 2.5D retro dungeon crawler built with LibGDX — a modern tribute to the classic Intellivision game *Treasure of Tarmin*. Procedurally generated mazes, deep survival mechanics, color-tiered loot, and brutal turn-based combat.

---

## Getting Started

**Requirements:** JDK 17+

```bash
# Run
gradlew.bat lwjgl3:run          # Windows
./gradlew lwjgl3:run            # macOS / Linux

# Build JAR
gradlew lwjgl3:jar              # Output: lwjgl3/build/libs/
```

---

## Key Features

- **First-Person Raycasting** — DDA-based 2.5D renderer with depth-buffered entity occlusion, textured (MODERN) and flat-color (CLASSIC) modes
- **Procedural Dungeons** — 12×12 room templates randomly selected, rotated, and stitched into fully navigable layouts each run
- **Card/Dice Combat** — Turn-based combat resolved through a dice system with per-hand die selection and visual roll feedback *(active development)*
- **Dual-Strength System** — War Strength (WS) for physical health/melee, Spiritual Strength (SS) for magic/defense
- **Full Survival Model** — Satiety, Hydration, Body Temperature (32–41°C), and Toxicity all decay and interact
- **Toxicity Glass-Cannon** — High toxicity grants +20% damage / −20% defense — a deliberate high-risk playstyle
- **Deep Itemization** — 450+ item types across weapons, armor, rings, scrolls, wands, and consumables; color-coded by power tier
- **Monster Ecosystem** — 37+ monster types with faction AI; monsters fight each other independent of the player
- **Cooking & Alchemy** — Harvest monster gibs, combine with kindling and water to craft meals; brew potions from ingredients
- **Gore & Gibs** — Pooled blood particles, persistent wall decals, and body-part drops per monster archetype
- **Multiple Biomes** — Dungeon, Forest, Desert, and Castle environments with biome-specific generators
- **Paperdoll System** — Verlet-physics cloth simulation for character appearance
- **CRT Post-Processing** — Scanline shader with screen shake on hit

---

## Runtime Keybindings

| Key | Action |
|---|---|
| `F1` | Toggle debug overlay (top-down map view) |
| `F2` | Toggle MODERN / CLASSIC renderer |

---

## Project Structure

```
core/                           # Platform-independent game logic (~31,000 lines, 165+ classes)
│   └── src/main/java/com/bpm/minotaur/
│       ├── Tarmin2.java        # Main game class; bootstraps all managers
│       ├── screens/            # All UI screens (GameScreen, InventoryScreen, etc.)
│       ├── gamedata/           # Pure data objects (Player, Maze, Monster, Item)
│       ├── managers/           # 21 singleton managers (Combat, AI, Spawn, Sound, etc.)
│       ├── rendering/          # Raycasting renderer, HUD, overlays
│       └── generation/         # Procedural dungeon generators, spawners
lwjgl3/                         # Desktop launcher (LWJGL3 backend)
assets/
│   ├── data/                   # JSON config files (weapons, armor, monsters, items, etc.)
│   ├── images/                 # Sprites, skyboxes, gore textures
│   ├── sounds/                 # SFX and music
│   └── packed/                 # Generated texture atlases (do not edit manually)
```

---

## Architecture Overview

The game uses a **Manager + Data Object** pattern (ECS-lite):

- **Data classes** (`Player`, `Maze`, `Monster`, `Item`) hold state only
- **Manager singletons** own all logic and cross-cutting concerns
- **Screens** compose managers and renderers for each view

Two coordinate systems are in use — **`GridPoint2`** for maze tile lookups, **`Vector2`** for world/rendering math. The `Maze` stores entities in a dual map: `wallData[][]` for collision geometry and `Map<GridPoint2, Object>` for interactive objects.

Full architecture, system, and API documentation is in the agent context files below.

---

## Agent & Developer Documentation

This project uses spec-driven agentic development. Read these files before touching any system:

| File | Contents |
|---|---|
| [`AGENT.md`](AGENT.md) | **Start here** — role, rules, architecture invariants, conventions |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Full package map and all 165+ classes described |
| [`SYSTEMS.md`](SYSTEMS.md) | All game systems — entry points, data flow, coupling |
| [`COMBAT.md`](COMBAT.md) | Combat state machine, dice system, card-combat feature |
| [`RENDERING.md`](RENDERING.md) | Rendering pipeline, viewport layout, depth buffer |
| [`DATA.md`](DATA.md) | JSON data file schemas and loading patterns |
| [`ROADMAP.md`](ROADMAP.md) | Feature status — done, in progress, planned |
| [`spec.md`](spec.md) | Canonical product specification |

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 17+ |
| Framework | LibGDX |
| Desktop backend | LWJGL3 |
| Build | Gradle (multi-module) |
| JSON | `com.badlogic.gdx.utils.Json` |
| Physics | Bullet |
| Video | JavaCV + FFmpeg |
