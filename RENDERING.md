# Rendering Pipeline Reference

> Part of the Tarmin2 agent context system. See `AGENT.md` for the full file map.

---

## Viewport Layout

The game uses a **split viewport** on a fixed 1920×1080 virtual canvas:

```
┌─────────────────────────────────────────────┐
│                                             │
│         GAME_HEIGHT = 900px                 │  ← FirstPersonRenderer + EntityRenderer
│       (FirstPerson 3D View)                 │
│                                             │
├─────────────────────────────────────────────┤
│         HUD_HEIGHT = 180px                  │  ← Hud (stats, minimap, quick-access)
└─────────────────────────────────────────────┘
  VIRTUAL_WIDTH = 1920px
```

**Constants (in `GameScreen.java`) — load-bearing, do not change without updating all renderers:**
```java
static final int VIRTUAL_WIDTH  = 1920;
static final int VIRTUAL_HEIGHT = 1080;
static final int HUD_HEIGHT     = 180;
static final int GAME_HEIGHT    = 900;   // VIRTUAL_HEIGHT - HUD_HEIGHT
```

The `FitViewport` scales the virtual canvas to the physical display. A `FrameBuffer` (FBO) captures the full frame for CRT post-processing.

---

## Frame Render Order

Every frame, `GameScreen.render(delta)` calls renderers in this exact order:

```
1. FirstPersonRenderer.render(maze, player, depthBuffer)
      └── DDA raycasting for walls, doors, gates, windows
      └── Draws floor, ceiling, skybox
      └── Produces depthBuffer[VIRTUAL_WIDTH]   ← critical output

2. EntityRenderer.render(maze, player, depthBuffer)
      └── Collect all Renderable objects from maze.gameObjects + monsters
      └── Sort by distance (farthest first)
      └── For each: mask sprite columns against depthBuffer
      └── Renders: monsters, items, gibs, projectiles, scenery, events

3. FirstPersonWeaponOverlay.render(batch, equipment)  ← NEW (card-combat branch)
      └── Draws equipped weapon in foreground
      └── Plays attack / hit animations

4. Hud.render(batch, player, maze)
      └── WS/SS bars, Satiety/Hydration/Toxicity indicators
      └── Minimap (top-down tile rendering)
      └── Active event text queue
      └── Quick-access inventory row

5. CombatDiceOverlay.render(batch, dieResult)         ← NEW (card-combat branch)
      └── Only visible during PHYSICS_RESOLUTION + PHYSICS_DELAY
      └── Shows rolled die face, value, and effect label

6. DebugRenderer.render(maze, player)                 ← Only when DebugManager active (F1)
      └── Top-down orthographic view
      └── Draws wallData grid, monster positions, player position

7. Post-processing (CRT shader)
      └── FBO texture passed through ShaderProgram
      └── Applies scanline and barrel distortion
      └── Screen shake applied via trauma parameter → camera offset
```

---

## FirstPersonRenderer — DDA Raycasting

**Algorithm:** Digital Differential Analyzer (DDA). For each screen column (0 to VIRTUAL_WIDTH):
1. Cast a ray from `player.position` in the direction of that column
2. Step through the maze grid until hitting a `wallData` solid cell
3. Compute the perpendicular wall distance
4. Calculate the wall slice height for that column
5. Sample the wall texture (MODERN mode) or fill solid color (CLASSIC mode)
6. Write the hit distance to `depthBuffer[column]`

**Key outputs:**
- Wall geometry drawn to the SpriteBatch
- `float[] depthBuffer` — passed to `EntityRenderer` for occlusion

**Rendering modes (hot-swappable at runtime):**
- `MODERN` — textured walls, sprites loaded from texture atlas
- `CLASSIC` — flat colors, retro Tarmin aesthetic

**Special geometry handled:**
- Doors (`Door`) — rendered as partial-width wall when open
- Gates (`Gate`) — color-tinted wall slice
- Windows (`Window`) — transparent slice with background bleed-through
- Skybox — rendered behind walls as background panorama
- Floor and ceiling — rendered as distance-based gradient or texture

---

## EntityRenderer — Depth-Buffered Sprite Rendering

All objects in `Maze.gameObjects` and `Maze.monsters` that implement `Renderable` are passed to `EntityRenderer`.

**Render process:**
1. Build list of all `Renderable` objects visible in the player's FOV
2. Sort by distance (farthest → nearest for correct transparency)
3. For each entity:
   - Project entity position onto screen using player camera vectors
   - For each screen column the sprite covers: check `depthBuffer[column]`
   - Only draw columns where `spriteDistance < depthBuffer[column]` (entity is in front of wall)
   - Scale sprite height based on distance
4. Render

**`Renderable` interface:** Any class implementing `Renderable` can be rendered by `EntityRenderer`. Implement `getPosition()`, `getTexture()`, and `getSpriteHeight()`.

---

## Camera System

The player's view is defined by three `Vector2` values on `Player`:
```java
Vector2 position;       // world position (not grid)
Vector2 directionVector; // facing direction unit vector
Vector2 cameraPlane;     // perpendicular FOV plane (controls field of view width)
```

When the player turns, `directionVector` and `cameraPlane` rotate together. The DDA algorithm uses these directly — do not cache derived values across frames.

**Screen shake:** `GameScreen.trauma` accumulates on hits. Each frame, a random offset proportional to `trauma²` is applied to `directionVector` and `cameraPlane`, then restored from `originalDir` / `originalPlane` after rendering.

---

## Post-Processing

A `FrameBuffer` captures the full rendered frame. A `ShaderProgram` (`crtShader`) applies:
- Scanline overlay
- Barrel/fisheye distortion
- Vignette

Controlled by `useCrtFilter` boolean in `GameScreen`. Time uniform (`time`) passed to shader for animated scanlines.

---

## Animation System

`AnimationManager` tracks per-entity animation state machines. On combat events:
- Hit → play hit animation frames on the monster sprite
- Death → play death animation, then remove from `Maze.monsters`
- Player attack → `FirstPersonWeaponOverlay` triggers swing animation

Animations are defined as frame sequences in the texture atlas. `AnimationManager` tracks current frame and elapsed time per entity.

---

## Debug Renderer

`DebugRenderer` renders an orthographic top-down view of the maze. Active when `DebugManager.isDebugMode()` returns true (toggle with F1). Draws:
- `wallData` grid as colored tiles
- Monster positions as colored dots
- Player position and facing direction vector
- Entity spawn overlay (when `DebugSpawnOverlay` is enabled)

Does not interfere with the main rendering pipeline — both can render in the same frame.
