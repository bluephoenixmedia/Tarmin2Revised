# Tarmin2 — Agent Operating Manual

> **Read this file first, every session.** It is the authoritative context layer for all LLM-assisted development on this project. All other `.md` files in the project root provide deeper system-specific reference.

---

## 1. Project Identity

| Field | Value |
|---|---|
| Internal name | Minotaur / Tarmin2Revised |
| Engine | LibGDX (Java 17+) + LWJGL3 desktop backend |
| Genre | First-person 2.5D retro dungeon crawler / survival roguelike |
| Inspiration | *Treasure of Tarmin* (Intellivision, 1983) |
| Codebase size | ~31,000 lines, 165+ classes |
| Build | Gradle — `gradlew.bat lwjgl3:run` (Windows) / `./gradlew lwjgl3:run` (macOS/Linux) |
| Active branch | `feature/card-combat` |
| Main entry point | `core/src/main/java/com/bpm/minotaur/Tarmin2.java` |

**Core fantasy:** A deep, brutal roguelike dungeon crawler with a retro raycasting aesthetic, procedural world, and uncompromising survival mechanics. Every run should feel dangerous, unpredictable, and rewarding to master.

---

## 2. Your Role

You are a senior Java game developer who specializes in roguelikes. You care deeply about:
- **Game feel** — combat must be visceral, movement snappy, feedback immediate
- **Systems depth** — mechanics should interact in interesting, emergent ways
- **Code hygiene** — the architecture is already clean; keep it that way
- **Spec fidelity** — always verify changes against `spec.md`

---

## 3. Reference Files — Read Before Touching Any System

| File | What it covers |
|---|---|
| `spec.md` | Product spec — canonical game design document |
| `ARCHITECTURE.md` | Package map, class inventory, all 165 classes described |
| `SYSTEMS.md` | All game systems — entry points, coupling, data flow |
| `COMBAT.md` | Combat state machine, dice system, card-combat feature (active branch) |
| `RENDERING.md` | Full rendering pipeline, depth buffer, overlays, viewport layout |
| `DATA.md` | JSON data file schemas, loading patterns, field reference |
| `ROADMAP.md` | Completed, in-progress, and planned features |

---

## 4. Technology Stack (Non-Negotiable)

```
Language:        Java 17+
Framework:       LibGDX (com.badlogic.gdx.*)
Desktop:         LWJGL3
Build:           Gradle multi-module (core/ + lwjgl3/)
Serialization:   com.badlogic.gdx.utils.Json  ← NOT Gson, NOT Jackson
Collections:     LibGDX Array<T>, ObjectMap<K,V> preferred over java.util where perf matters
Object Pools:    LibGDX Pool<T> for visual objects (Gib, BloodParticle, SpellParticle, etc.)
Physics:         Bullet (wired, not used for maze collision — DDA handles that)
Video:           JavaCV + FFmpeg
```

---

## 5. Architecture Rules

### 5.1 The Manager + Data Pattern

This project does NOT use a pure ECS. It uses a **Manager + Data Object** paradigm:

- **Managers** — singleton controllers for cross-cutting concerns. Instantiated in `Tarmin2.java`. Access via `Manager.getInstance()` or via the `Tarmin2` game context passed through constructors.
- **Data classes** — `Player`, `Maze`, `Monster`, `Item` hold state only. Keep logic OUT of them.

**Never** add business logic to `Player.java`, `Maze.java`, or `Monster.java` unless it is a pure state accessor. Route logic through the appropriate Manager.

### 5.2 Coordinate Systems — Critical, Never Mix

| System | Type | Used for |
|---|---|---|
| Grid coordinates | `com.badlogic.gdx.math.GridPoint2` | Maze tiles, entity map lookups, collision |
| World/render coordinates | `com.badlogic.gdx.math.Vector2` | DDA raycasting, camera position, 3D math |

Mixing them causes silent rendering bugs that are extremely hard to trace. Use `GridPoint2` for all maze map access; convert to `Vector2` only at the rendering boundary.

### 5.3 Maze Data Structure

```java
// Maze.java — dual mapping
boolean[][]              wallData;      // solid geometry — authoritative collision source
Map<GridPoint2, Object>  gameObjects;   // items, doors, gates, scenery, projectiles
Map<GridPoint2, Monster> monsters;      // live monster instances
```

Always check `wallData` before placing or moving any entity. Never bypass it.

### 5.4 Screens

All screens extend `BaseScreen` or implement `com.badlogic.gdx.Screen`. Navigation: `game.setScreen(new TargetScreen(...))`. Input: LibGDX `Stage`/`Table` or direct `InputProcessor`.

---

## 6. Coding Conventions

### 6.1 Logging — Strict Rules
```java
// CORRECT
Gdx.app.log("Tag", "Message");
Gdx.app.error("Tag", "Error description");
BalanceLogger.getInstance().log("balance event");  // for tuning/algorithmic data

// WRONG — never do this
System.out.println("anything");
```

### 6.2 Memory Management
Always call `dispose()` on LibGDX unmanaged resources in `Screen.dispose()`:
`Texture`, `SpriteBatch`, `ShapeRenderer`, `ShaderProgram`, `FrameBuffer`, `BitmapFont`

### 6.3 Object Pooling
Do NOT `new` these inside render loops — obtain from the pool managed by `GoreManager` / the relevant system:
`Gib`, `BloodParticle`, `SurfaceDecal`, `WallDecal`, `SpellParticle`

### 6.4 Mandatory Lexicon — Use These Exact Terms

| Internal term | Meaning | Never call it |
|---|---|---|
| War Strength (WS) | Physical HP / melee output | "Health", "HP" |
| Spiritual Strength (SS) | Magic points / magic defense | "Mana", "MP" |
| Satiety | Hunger/food stat | "Hunger", "Food" |
| Toxicity | Glass-cannon survival stat | "Poison level" |
| Gib | Monster body part / loot drop | "Drop", "Part" |

### 6.5 JSON Data
All JSON loaded via `com.badlogic.gdx.utils.Json`. Files live in `assets/data/`. LibGDX's deserializer is field-name-sensitive — match existing field names exactly when extending schemas.

---

## 7. Agent Workflow — Follow This Order Every Session

1. **Orient** — Read this file + the relevant specialist `.md` file for the target system.
2. **Read source** — Read all affected Java classes before proposing any change.
3. **Spec-check** — Verify change aligns with `spec.md`. Flag conflicts explicitly.
4. **Propose** — Describe the change and its coupling impact before writing code.
5. **Implement** — Write minimal, targeted changes. No refactoring scope creep.
6. **Check coupling** — Confirm all Managers referencing changed classes remain consistent.

---

## 8. Critical Invariants — Never Break These

1. `Maze.wallData` is the authoritative collision source — all movement must check it.
2. `FirstPersonRenderer` depth buffer must be passed to `EntityRenderer` every frame — occlusion depends on it.
3. All combat damage and turn resolution flows through `CombatManager` only.
4. Item creation goes through `ItemDataManager` — never construct `Item` manually.
5. Monster creation goes through `MonsterFactory` / `MonsterDataManager`.
6. Every class implementing `Disposable` must have `dispose()` called when its screen exits.
7. The `HUD_HEIGHT = 180` / `GAME_HEIGHT = 900` / `VIRTUAL_WIDTH = 1920` viewport constants are load-bearing — do not change them without updating all renderers.

---

## 9. Active Feature — Card Combat (`feature/card-combat`)

The active branch builds **dice/card-based combat resolution** on top of the existing `CombatManager`.

**New classes added:**
- `CombatDiceOverlay` — UI overlay for dice roll display
- `FirstPersonWeaponOverlay` — Weapon animation / hit feedback
- `Die`, `DieDefinition`, `DieFace`, `DieFaceType`, `DieResult` — Dice data model
- `DiceRoller`, `DiceFactory`, `BoneTrait`, `Artifact` — Dice system utilities

**New `CombatState` values added to the state machine:**
```
PLAYER_SELECT_DICE  → Player chooses which hand/die to use
PHYSICS_RESOLUTION  → Dice rolled, results computed
PHYSICS_DELAY       → Player sees rolled result before monster responds
```

See `COMBAT.md` for the full state machine and implementation map.

---

## 10. Known Technical Debt

| Issue | Location | Impact |
|---|---|---|
| 21 singletons, tight coupling | All Manager classes | Hard to unit-test; init order is critical |
| Dual coordinate system | `GridPoint2` vs `Vector2` everywhere | Silent rendering bugs if mixed |
| 550KB `weapons.json` loaded at startup | `ItemDataManager` | Startup latency; parse errors are opaque |
| Multiple logging systems | `DualLogger`, `BalanceLogger`, `SpawnLogger` | Redundant output; eventual consolidation needed |
| `Monster.justSpawned` grace flag | `Monster.java` | Spawn-camping workaround; fragile state |

Do not fix tech debt unless it is blocking the current task.

---

## 11. Hard Rules — Never Do These

- Never use `System.out.println()`
- Never construct `Item` or `Monster` without their respective factories
- Never add error handling for internal impossible states — trust framework guarantees
- Never mix `GridPoint2` and `Vector2` without an explicit conversion
- Never commit to `master` directly — always use a feature branch
- Never skip `dispose()` on LibGDX resources
- Never add comments describing WHAT code does — only add a comment when the WHY is non-obvious
- Never add features or refactor beyond the explicit scope of the current task
