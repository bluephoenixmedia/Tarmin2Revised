# Main Menu 3D Flyover and Attract Mode Architecture

## Context

Previously, the title screen (`MainMenuScreen`) displayed a static 2D image (`images/tarmin_title.png`) in Modern mode, or a classic block banner in Retro mode. The game lacked an iconic attract mode to introduce the player to the scale of the world, castle, and biomes before starting an expedition (reminiscent of the classic *Unreal* 1998 castle flyover).

Generating an entire 100-chunk world (129,600 tiles) synchronously at boot would cause long freezes and high VRAM overhead. Furthermore, menu buttons placed in the center of the screen would obstruct the 3D cinematic vista.

## Decision

We implemented a **streaming 3D Flyover Attract Mode** for the Main Menu:

1. **Concentric Kingdom Macro-World (`AttractWorld`)**:
   - Represents a 10×10 chunk grid (100 chunks total) with deterministic procedural features.
   - Central citadel (chunks 4,4 to 5,5) houses the Castle Tarmin stone fortress walls, moat liquid, and the soaring High Spire.
   - Surrounding quadrants encompass all biomes: ancient `FOREST` (North), misty `LAKELANDS` (East), canyon `DESERT` (South), craggy `MOUNTAINS` (West), and outer `OCEAN` abyss rim.

2. **Looping 3D Flight Spline & Aerodynamic Banking (`AttractSplinePath`)**:
   - A 90-second closed-loop Catmull-Rom spline with dramatic altitude shifts: low skimming (*y* = 1.4–2.5) over misty Lakelands water, weaving through dark forest canopies (*y* = 1.8–3.2), sweeping across desert dunes (*y* = 2.4–4.5), ascending castle battlements (*y* = 6.0–8.0), and orbiting the High Spire summit (*y* = 18.0–20.0) with a 360° panorama before diving back down to loop seamlessly.
   - Features aerodynamic banking roll (±4° to 8°) calculated from trajectory curvature.

3. **Active Trajectory Chunk Streaming (`AttractModeRenderer`)**:
   - Pre-caches and renders a 5×5 chunk window (radius 2) around the camera along the flight path, evicting distant chunks to maintain a constant 60 FPS and low memory footprint.
   - Embeds 3D landmarks (`castle_citadel.obj` and `south_spire.obj`), procedural liquid quads (moat and lake surfaces), and the 3D sky dome (`Skybox3DRenderer.renderDirect`).

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
