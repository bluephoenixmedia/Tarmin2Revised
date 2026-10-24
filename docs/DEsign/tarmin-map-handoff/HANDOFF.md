# Tarmin Expedition Map: isometric diorama, port to libGDX

You are porting an approved visual prototype into the existing Java/libGDX game.
**The prototype is the spec. Match it exactly**: palette, proportions, fog behaviour,
glyphs, layout. Where this brief and the prototype disagree, the prototype wins.

## 0. Before writing any code: inspect the existing game

1. Find the current Expedition Map screen. Look at its class, how it gets
   chunk knowledge (UNKNOWN / GLIMPSED / VISITED), shelters, roads, seals, graves,
   portals, strata, pins, waypoint and suggestion, and how input is routed.
2. Find the "Carved Frame" UI skin, the `intellivision.ttf` font setup and any
   existing FrameBuffer or shader utilities.
3. Report what exists and propose where the new renderer plugs in **before**
   implementing. Extend the existing map screen and data classes. Do not
   restructure them. All current map functions stay. Only the presentation changes.

## 1. Files in this bundle

| File | What it is |
|---|---|
| `prototype/Main.dc.html` + `prototype/support.js` | The running prototype. Serve the folder (`npx serve prototype` or `python -m http.server`) and open `Main.dc.html`. `support.js` is only the prototype's UI runtime. **All map logic is the `class Component` script at the bottom of `Main.dc.html`. Port that.** |
| `screenshots/1-world.png` | World zoom, fog at full strength |
| `screenshots/2-region.png` | Region zoom: pins, graves, portal, depth badge |
| `screenshots/3-chunk.png` | Chunk zoom: maze walls, player figure and facing, items/events |
| `screenshots/4-stratum.png` | Floor −1: surface ghosted away, cavern section revealed |

Screenshots were taken without web fonts, so the UI text shows a fallback
monospace. The real font is the game's pixel font (the prototype uses Press Start 2P / VT323 as stand-ins).

## 2. Architecture of the prototype (map these 1:1)

**Low-res render, integer upscale.** Everything map-side is drawn at **720×448**
and upscaled ×2 with nearest filtering to 1440×896. In libGDX: render into a
720×448 `FrameBuffer`, set the texture filter to `Nearest`, and draw it at 2×. This is
what gives the painterly pixel look. Keep it.

**Projection.** World unit = one chunk. Chunks are 8×8 tiles.
`hw = 28*s`, `hh = 14*s` (s = zoom scale), rotation θ in 90° steps (animated):
```
rx = dx*cosθ − dy*sinθ ; ry = dx*sinθ + dy*cosθ      (dx,dy = world − camera)
screenX = W/2 + (rx − ry)*hw ;  screenY = H/2 + (rx + ry)*hh − z*s
```
Depth sort key = `rx + ry` (painter's algorithm: chunks back→front, then each
chunk's walls and props back→front). See `P()`, `dep()`, `unproj()`.

**Zoom levels** (continuous, eased): world `s=1.0`, region `2.6`, chunk `6.4`.
Thresholds: world < 1.7 ≤ region < 4.2 ≤ chunk. See `LV`, `levelOf()`.

**Heights (z units, ×s px):** board top 0 (board slab down to −5); visited
chunk top 6 (ocean 2); glimpsed 2.5 (ocean 1.2); maze walls +4.2 above the top;
cavern slabs 0→5 with three strata bands, rock pillars 5→~8.

**Layers per frame** (`draw()`):
1. Table background + warm radial glow (`drawTable`)
2. Static scene, cached and re-rendered only when the camera changes (`renderSurface` / `renderCave`)
3. Additive lights: campfires, beacons, portal swirl, seal glow, blight pulse, cave fungi (`lights`)
4. **Fog** (`fog`, see §3)
5. Vignette
6. Overlay, drawn over the fog: ember road, rumoured beacon beam, castle silhouette and edge arrow,
   glyph plates, waypoint and suggestion rings, player marker, selection outline (`overlay`)

**Floor switching** (`floorT` eases 0↔1): cavern scene + cavern fog drawn
first; the fogged surface is drawn on top, lifted by `min(64, 22*s)*ease(t)` px,
with alpha `1 − 0.84*ease(t)`. Ghost floor at 0.16 alpha when fully down.

## 3. Fog of war: highest-risk part, do this first

The prototype builds the fog each frame on a 720×448 buffer:
1. Fill `rgba(60,70,82,0.6)` (cave: `rgba(28,32,38,0.84)`).
2. Three tiling fBm value-noise textures (128², see `noise()` / `tex()`), drawn as
   scrolling patterns at different scales and speeds, partly anchored to the world
   (offset by the projected world origin × 0.85–0.95):
   body (86,98,112), shade (32,40,50), highlight (198,210,220).
3. **Clear holes with a world-space knowledge mask**: one pixel per tile,
   `(N + 2*PAD)*8` square, values: unknown 0, glimpsed 0.42, visited seen tile 1,
   visited unseen tile 0.5. Box-blurred (r=2 then r=1). Drawn with the iso
   affine transform (`maskTf()`, offset by z=5) using `destination-out`.
4. Frontier wisps: a 4th noise texture, masked by `4.4*c*(1−c)` of the same
   mask (peaks at the frontier and over glimpsed chunks), composited at 0.8.
5. Composite with alpha `0.3 + 0.7*fogStrength` (default 1 = strongest).

**libGDX approach:** one fragment shader on a fullscreen quad. Sample the noise
textures (`GL_REPEAT`) with scrolling UVs, and sample the mask texture through the
inverse iso transform (pass the 2×3 affine as uniforms). Output
`fogRGBA * (1 − mask)` + wisps × frontier. Rebuild the mask texture only when
knowledge changes or the floor changes.

## 4. Implementation order (by risk)

1. **Fog shader + mask texture** over a flat placeholder diamond grid. Get the
   look matching `1-world.png` before anything else.
2. **Low-res FBO pipeline + iso projection + camera** (pan, eased zoom toward
   cursor, 90° animated rotation, centre on player). Picking via `unproj()`,
   testing z = 6, 2.5, 2, 1.2, 0 in that order.
3. **Chunk blocks + tiles** (box sides shaded by face normal; the left-facing
   side uses the lit colour). Maze walls as boxes. Generation is seeded in the
   prototype (`genChunk`), but use the game's real tile and seen data.
4. **Biome props**: pine, crag, dune, cactus, hoodoo, arch, bones, reeds, boat,
   mound, ice, dead tree, crack, pustule, shelter (+ pyramid roof, lit windows,
   home banner), campfire, grave, portal, seal ring, ladder, chest, player figure.
   Glimpsed chunks use silhouette colours `#2d343c` / `#20262c`. All prop shapes
   are in `prop()` as rect, triangle and ellipse primitives scaled by `k = s*0.8*size`.
5. **Lights, overlay glyphs, castle arrow and silhouette, road.** Glyph plates are
   7×7 bitmaps (`initGlyphs` → `G`) on 11×11 parchment plates with a pointer
   (`plate()`), depth badge (`depthBadge()`). Draw them pixel-exact.
6. **Strata view** (`renderCave`, `floorT` ghost transition).
7. **UI chrome**: header, floor stack, compass, legend, side panel, footer keys,
   built in the Carved Frame skin. Match the screenshots' layout at 1920×1080.

## 5. Palette and constants

- UI: bg `#0B0907 / #15100B / #1F170F`; lines `#3A2A18 / #6E4D25 / #B8862F`;
  text `#EFE3CC`; dim `#B39C78`; gold `#E9B44C`; focus `#FFD77A`.
- Player (the only cool colour): `#6CCFE0`. Castle/castle road: `#8E5BC4`.
  Seal road: ember `#D0683F` (fades to 35% alpha once the seal is won).
  Portal: lilac `#C9A6E8`.
- Biome tops / sides: see `BIOME` in `initWorld()`. Muted washes, never saturated.
- Pin plate colours: `PINC`. Pin cycle order: none, danger, loot, return, trader, locked, unknown.

## 6. Controls (unchanged from the current map)

Drag / arrows pan · wheel / +− zoom · `[` `]` or PgDn/PgUp floor · Q/E rotate ·
W waypoint · G accept suggestion · P cycle pin · C centre · Enter (or clicking
the selected chunk again) zooms in · Esc backs out · M closes.

## 7. Done means

Side-by-side with each screenshot at 1920×1080, the libGDX build matches the fog
density and motion, block heights, prop silhouettes, glyph pixels, colours and
UI layout. All existing map functions still work.
