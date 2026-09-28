# Main Menu 3D Flyover and Attract Mode Architecture

## Context

Previously, the title screen (`MainMenuScreen`) displayed a static 2D image (`images/tarmin_title.png`) in Modern mode, or a classic block banner in Retro mode. The game lacked an iconic attract mode to introduce the player to the scale of the world, castle, and biomes before starting an expedition (reminiscent of the classic *Unreal* 1998 castle flyover).

Generating an entire 100-chunk world (129,600 tiles) synchronously at boot would cause long freezes and high VRAM overhead. Furthermore, menu buttons placed in the center of the screen would obstruct the 3D cinematic vista.

## Decision

We implemented a **streaming 3D Flyover Attract Mode** for the Main Menu:

1. **Concentric Kingdom Macro-World (`AttractWorld`)**:
   - Represents a 10×10 chunk grid (100 chunks total) with deterministic procedural features.
   - Central citadel (chunks 4,4 to 5,5) houses the Grand Castle Tarmin stone labyrinth: a dense network of authentic stone masonry corridors, royal pillared chambers, grand ceremonial avenues, royal courtyards, and perimeter water moat.
   - Surrounding quadrants encompass all biomes as continuous, interconnected labyrinths: ancient mossy cliff corridors in `FOREST` (North), sunken stone canals in `LAKELANDS` (East), winding sandstone slot canyons in `DESERT` (South), craggy rock gorges in `MOUNTAINS` (West), and outer `OCEAN` perimeter rim.

2. **Looping 3D Flight Spline & Aerodynamic Banking (`AttractSplinePath`)**:
   - A 90-second closed-loop Catmull-Rom spline hovering right over and through the maze corridors: low corridor skimming (*y* = 1.5–1.8) through stone halls and mossy woodland paths, low-altitude water gliding (*y* = 1.6–1.8) over misty Lakelands canals, sandstone canyon navigation (*y* = 1.8–2.4), and scenic mountain pass vistas (*y* = 3.5–4.2) before banking back into the castle gates.
   - Features aerodynamic banking roll (±4° to 8°) calculated from trajectory curvature.

3. **Active Trajectory Chunk Streaming (`AttractModeRenderer`)**:
   - Pre-caches and renders a 5×5 chunk window (radius 2) around the camera along the flight path, evicting distant chunks to maintain a constant 60 FPS and low memory footprint.
   - Renders authentic game geometry (chunk sub-meshes for walls and floors, dynamic liquid quad batcher for moat and canal water) under the 3D sky dome (`Skybox3DRenderer.renderDirect`), omitting external landmark meshes so the player directly surveys the real generated labyrinth.

4. **Attract Mode State Machine & UI Framing (`AttractController` & `MainMenuScreen`)**:
   - Modern UI is framed on the left with sleek translucent glassmorphic button cards and an overhead "TARMIN II" crest, leaving the center and right screen open for the 3D vista.
   - 15 seconds of user inactivity smoothly fades the UI out over 1.2 seconds, revealing the unobstructed cinematic flyover with a soft pulsing prompt (*"Press any key to enter"*).
   - Any keyboard or mouse input instantly restores the UI to 100% opacity without consuming the input.
   - Clicking an expedition button (Continue, New Game, Load Game) triggers an acceleration dive toward the fortress gates over 0.8s with ambient rumble before transitioning screens.
   - Retro mode (`F2`) preserves the authentic 1982 Intellivision 2D title screen layout and colors.

## Consequences

- Delivers a breathtaking, modern first impression upon launching the game without delaying startup time.
- World streaming avoids loading 100 full chunks simultaneously, keeping memory consumption low.
- Fully compatible with both Modern 3D mode and Retro 1982 Intellivision mode.
