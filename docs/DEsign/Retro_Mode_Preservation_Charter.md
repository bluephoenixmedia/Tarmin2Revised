# Retro Mode Preservation Charter

**Status**: Active / Non-Negotiable Architectural Invariant  
**Target Systems**: Rendering, Generation, Combat, Audio, UI, AI  
**Primary References**: [AGENTS.md](file:///c:/Users/denni/Desktop/Tarmin2/AGENTS.md), [CONTEXT.md](file:///c:/Users/denni/Desktop/Tarmin2/CONTEXT.md)

---

## 1. Executive Summary & Core Principle

Tarmin2 honors its roots as a spiritual remake of Mattel's 1982 Intellivision classic *Treasure of Tarmin*. While the engine supports modern roguelike systems—including modular 12x12 chunk archetypes, cyclic lock-and-key macro mission graphs, 3D low-cover tactical combat, and dynamic telemetry pacing—**Retro Mode (`GameMode.CLASSIC`) must remain an authentic, uncompromised sanctuary for the classic presentation**.

> [!IMPORTANT]
> **The Retro Mode Preservation Invariant**:
> When `GameMode.CLASSIC` is active, the game MUST preserve the authentic visual style, 16-tile topological layout, retro color palette, classic soundscape, and legacy viewport of the original game. Modern procedural generation, environmental props, cover mechanics, and dynamic pacing engines MUST NOT alter, pollute, or leak into Classic mode under any circumstances.

---

## 2. The Six Non-Negotiable Invariants

### Invariant 1: Topographical Map Layout
- **Classic**: Strictly constrained to the original 16-tile array (`tile1` through `tile16`) arranged in a 4x4 grid of sectors, navigated via the classic 2x2 minimap overlay.
- **Advanced**: Employs `MacroMissionGraphGenerator` to generate a 3x3 strata network of 12x12 modular chunk archetypes (`ModularChunkLoader`).
- **Enforcement**: In `MazeChunkGenerator.generateChunk()`, if `gameMode == GameMode.CLASSIC`, modern chunk solvers and cyclic mission graphs are skipped entirely in favor of legacy maze array population.

### Invariant 2: Visual Presentation & Rendering
- **Classic**: Original wireframe / bitmap raycast projection, authentic Intellivision color palette, classic monster billboards, and classic 2D HUD overlays.
- **Low-Cover Props**: Modern tactical cover objects (`LOW_COVER_RUBBLE`, `LOW_COVER_ALTAR`, `LOW_COVER_BARRICADE`) must **never** spawn or render in Classic mode.
- **Lighting & Post-Processing**: Classic mode uses flat ambient brightness without dynamic shadow occluders or modern post-processing shaders.

### Invariant 3: Combat Mechanics & Tactical Subsystems
- **Classic**: Original bump-to-attack melee, classic inventory weapon swap, and pure War vs. Spiritual damage duality.
- **Low-Cover AC Bonus**: The +3 AC cover bonus and projectile line-of-sight over low obstacles only apply in `GameMode.ADVANCED`.
- **Enforcement**: `CombatManager.hasLowCoverBetween()` returns `false` or is gated such that cover calculations are inactive during Classic mode combat.

### Invariant 4: Audio Experience & Soundscape
- **Classic**: Authentic synthesizer bleeps, classic step sounds, and original monster breathing noises.
- **Acoustic Occlusion**: Modern Bresenham raycasted wall muffling (-4dB / 30% reduction per stone wall) must not distort classic sound effects in Retro Mode.

### Invariant 5: Safe Architecture & Gating Pattern
Every subsystem touching generation, rendering, combat, audio, or UI must follow this explicit guard pattern:

```java
if (gameMode == GameMode.CLASSIC) {
    // Preserve legacy behavior without modification
    return legacyBehavior();
}
// Modern Advanced features execute here
```

No modern feature may be added without verifying that `GameMode.CLASSIC` remains completely untouched.

### Invariant 6: Continuous Automated Verification
Every test suite evaluating generation or core mechanics must include explicit tests asserting the preservation of Classic mode:
- `ProceduralMazeChunkTest.testRetroModeClassicIsolation()`: Confirms that generating in `GameMode.CLASSIC` populates the legacy 16-tile array and does not invoke modular chunk archetypes or low-cover generation.
- All regression tests must run against both modes to ensure backward compatibility.

---

## 3. Subsystem Comparison Matrix

| Feature Domain | `GameMode.CLASSIC` (Retro Mode) | `GameMode.ADVANCED` (Modern Expedition) |
| :--- | :--- | :--- |
| **Map Structure** | 16-tile grid array (`tile1`–`tile16`) | 3x3 Strata Graph with 12x12 Modular Chunks |
| **Room Prefabs** | Legacy procedural cellular rooms | 10 Hand-crafted Archetypes (`assets/data/chunks/`) |
| **Tactical Cover** | None (walls are binary impassable) | Low-cover props (+3 AC, permeable to ranged) |
| **Pacing Engine** | Classic static monster encounter tables | Telemetry Director (`HIGH_STRAIN`, `NEUTRAL`, `FLOW`) |
| **Minimap** | 2x2 sector HUD overlay | Multi-strata mission graph automap |
| **Audio Filtering** | Raw retro synth SFX, flat distance | Bresenham acoustic wall occlusion (-4dB/wall) |
| **Visual Aesthetics** | Retro wireframe / pixel art palette | Atmospheric lighting, layered decals, 3D props |

---

## 4. Agent Guidelines

When any AI coding agent modifies the Tarmin2 codebase:
1. **Always Review This Charter**: Before implementing new gameplay features, confirm where and how the feature is gated behind `GameMode.ADVANCED`.
2. **Never Refactor Away Legacy Classic Fields**: Fields like `tile1` through `tile16` in `MazeChunk` are sacred for retro fidelity.
3. **Run Regression Tests**: Execute `.\gradlew.bat test` and verify that `testRetroModeClassicIsolation` passes alongside modern feature tests.
