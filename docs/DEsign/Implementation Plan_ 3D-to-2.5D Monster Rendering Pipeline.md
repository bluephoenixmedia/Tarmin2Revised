# Implementation Plan: AI-Generated 3D Parts → 2.5D Raycaster Sprites
## Corrected & Grounded in the Actual MODERN Renderer

**Branch:** `monster_overhaul`  
**Date:** 2026-05-07

---

## 1. Does the Rendering System Need to Change?

**Short answer: No. The MODERN renderer already handles everything needed.**

Here is what `EntityRenderer.drawMonsterTexture()` already does:

```java
// Column-by-column depth-culled sprite rendering (EntityRenderer.java:765)
for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
    if (transformY >= depthBuffer[stripe]) continue;  // depth cull
    float u = (float)(stripe - (screenX - spriteWidth/2 + pixelOffX)) / (float)spriteWidth;
    spriteBatch.draw(monsterTex, stripe, drawY, 1, spriteHeight, u, 1, u + (1.0f/spriteWidth), 0);
}
```

It already supports:
- `monster.getTexture()` — single flat texture (used by most monsters)
- `monster.hasDirectionalTextures()` + `monster.getTextureForPlayerAngle(angle)` — 4 directional angles (N/E/W + animated S frames), already used by **Giant Scorpion** in production
- `monster.updateAnimation(delta)` — advances the south animation frame timer
- `monster.scale.x/y` and `monster.getTemplate().offsetX/Y` — size and position tuning
- `monster.getTexture() != null` check — the `needsTexture` gate that switches to the textured path automatically

The **only two lines that need adding to `Monster.java`** are setter methods so `MonsterFactory` can inject baked textures programmatically (currently these fields are only set from `AssetManager` in the constructor). Everything else in the renderer is already correct.

---

## 2. The Existing Visual Aesthetic — What We Must Match

The existing MODERN sprites (`assets/images/monsters/*.png`) are **chunky pixel art** with a consistent game-wide palette:

| Tone | Color | Usage |
|---|---|---|
| Shadow / outline | `#0D0010` near-black | Hard edges, outlines |
| Dark primary | `#3D1A5C` dark purple | Main body shadow areas |
| Mid primary | `#6A2E96` mid purple | Main body lit areas |
| Highlight | `#9E7CC4` light purple/lavender | Specular highlights |
| Accent / bone | `#D4C49A` cream/tan | Claws, horns, teeth, belly |
| Deep shadow | `#2A2A35` dark grey | Recessed crevices |

Every existing sprite uses variations on this purple/cream scheme. The new baked monsters must use the **same tonal mapping** to feel like they belong — the stylization shader achieves this by replacing 3D luminance values with game-palette equivalents.

Key pixel art properties to replicate:
- ~4px block size (each "art pixel" is ~4 screen pixels at native sprite resolution)
- Hard edges with no anti-aliasing
- 4–5 discrete tonal steps maximum — no smooth gradients
- Dark 1-pixel outline ringing every silhouette edge

---

## 3. The Full Pipeline

```
AI Tool (Meshy.ai / Tripo3D)
    │  text prompt → .glb / .obj output
    ▼
Offline Conversion (gdx-tools)
    │  .glb → .g3db (consistent pivot, scale, socket convention)
    ▼
CreaturePartLibrary
    │  AssetManager.load() at startup; Model handles cached by part id
    ▼
CreatureStitcher  ← reads dna/*.json
    │  assembles ModelInstance tree (torso + limbs at bone sockets)
    ▼
CreatureBaker
    ├── Pass 1: ModelBatch + OrthographicCamera → FrameBuffer (512×512 RGBA)
    │           4 bakes: rotate torso 0°/90°/180°/270° for N/E/S/W
    └── Pass 2: SpriteBatch + creature_stylize.frag → styled FrameBuffer
                pixelate → posterize → palette map → Sobel outline → black bg
    ▼
BakedMonsterSprite  (holds Texture[4] for N/E/W/S, Texture[] for S animation frames)
    │
monster.setTexture() / monster.setBakedDirectionalTextures()
    │
EntityRenderer.drawMonsterTexture()   ← UNCHANGED
    │  reads monster.hasDirectionalTextures() → monster.getTextureForPlayerAngle()
    │  column-by-column depth-culled render — exactly as today
    ▼
Screen  (MODERN mode, identical to Giant Scorpion pipeline today)
```

---

## 4. AI Asset Generation

### 4.1 Recommended Tools

| Tool | Why | Output |
|---|---|---|
| **Meshy.ai** | Best creature part topology; text + image prompt | .glb + texture |
| **Tripo3D** | Fast turnaround; good limb silhouettes | .glb |
| **TripoSR (local)** | Image → 3D from concept sketch | .obj |

### 4.2 Prompt Strategy

Because the stylization shader discards almost all texture detail and remaps everything to the game palette, **geometry and silhouette matter far more than texture quality**. Focus prompts on clean shapes.

**Example Meshy prompt for a limb:**
```
"Fantasy monster claw arm, isolated subject, no background, no base plate,
low-poly game asset, clean closed mesh, pivot/origin at shoulder socket,
arm extending outward from origin, suitable for modular creature rigging"
```

**Non-negotiable constraints for every generated part:**
- **Pivot at the attachment socket** — the mesh root must be at `(0,0,0)` at the connecting joint
- **Consistent unit scale** — 1 unit = height of one grid tile; torso should be ~1.5u tall
- **Clean single mesh** — no ground planes, no extra objects, no open edges
- **Facing convention** — all parts face +Z (toward camera) at rest pose

### 4.3 Offline Conversion: .glb → .g3db

Add `gdx-tools` only to the `tools/` subproject — it is not a runtime dependency:

```gradle
// tools/build.gradle
dependencies {
    implementation "com.badlogicgames.gdx:gdx-tools:1.13.1"
}
```

```bash
# Run offline before committing assets
java -cp gdx-tools.jar com.badlogic.gdx.tools.g3d.G3DConverter input.glb output.g3db
```

**Validation before committing:**
1. Loads without error via `AssetManager`
2. Visible in a test `ModelBatch` render (not black on black)
3. Pivot is at `(0,0,0)` — check bounding box centre ≠ origin means wrong pivot
4. Scale: torso height within 1.2–1.8 world units

### 4.4 Asset File Layout

```
assets/
  models/creatures/
    torsos/   torso_hulk.g3db, torso_insectoid.g3db ...
    heads/    head_horned.g3db, head_skulled.g3db ...
    arms/     arm_claw.g3db, arm_tentacle.g3db ...
    legs/     leg_hoof.g3db, leg_plantigrade.g3db ...
    tails/    tail_spike.g3db, tail_barbed.g3db ...
  data/creatures/
    dna/      troll_forest.json, scorpion_desert.json ...
    parts/    parts_manifest.json
```

---

## 5. The Baking System

### 5.1 Why OrthographicCamera

The existing sprite renderer draws sprites as flat 2D projections. Baking with an `OrthographicCamera` produces a projection-free front view that matches how the existing pixel art sprites look. Perspective baking would cause edge distortion that looks wrong as a flat 2D sprite.

### 5.2 CreatureBaker

```java
// com.bpm.minotaur.rendering.CreatureBaker
public class CreatureBaker implements Disposable {

    private static final int BAKE_W = 512, BAKE_H = 512;

    private final FrameBuffer  sceneFbo;    // raw 3D render
    private final FrameBuffer  styleFbo;    // after stylization shader
    private final ModelBatch   modelBatch;
    private final SpriteBatch  spriteBatch;
    private final OrthographicCamera bakeCamera;
    private final ShaderProgram styleShader;
    private final Environment  env;

    public CreatureBaker() {
        sceneFbo    = new FrameBuffer(Pixmap.Format.RGBA8888, BAKE_W, BAKE_H, true);
        styleFbo    = new FrameBuffer(Pixmap.Format.RGBA8888, BAKE_W, BAKE_H, false);
        modelBatch  = new ModelBatch();
        spriteBatch = new SpriteBatch();

        bakeCamera = new OrthographicCamera(2.0f, 2.0f * BAKE_H / BAKE_W);
        bakeCamera.position.set(0, 0.75f, 5f);
        bakeCamera.lookAt(0, 0.75f, 0);
        bakeCamera.near = 0.1f;
        bakeCamera.far  = 20f;
        bakeCamera.update();

        env = new Environment();
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.35f, 0.35f, 0.35f, 1f));
        // Main light: front-above (matches the shading visible on existing sprites)
        env.add(new DirectionalLight().set(1f, 0.95f, 0.9f,  -0.2f, -0.6f, -1f));
        // Subtle rim light from below-behind for separation from background
        env.add(new DirectionalLight().set(0.15f, 0.1f, 0.2f, 0.2f, 0.5f, 0.8f));

        styleShader = new ShaderProgram(
            Gdx.files.internal("shaders/creature_stylize.vert"),
            Gdx.files.internal("shaders/creature_stylize.frag")
        );
        if (!styleShader.isCompiled())
            throw new GdxRuntimeException("creature_stylize shader: " + styleShader.getLog());
    }

    /**
     * Produces a BakedMonsterSprite with 4 facing textures.
     * Call once on spawn; result is stored on Monster.
     */
    public BakedMonsterSprite bakeAllFacings(StitchedCreature creature) {
        Texture north = bakeAtAngle(creature,   0f);  // facing camera
        Texture east  = bakeAtAngle(creature,  90f);
        Texture south = bakeAtAngle(creature, 180f);  // back to camera
        Texture west  = bakeAtAngle(creature, 270f);
        return new BakedMonsterSprite(north, east, south, west);
    }

    private Texture bakeAtAngle(StitchedCreature creature, float yDegrees) {
        creature.setFacing(yDegrees);  // rotates root transform around Y

        // --- Pass 1: render 3D model ---
        sceneFbo.begin();
        Gdx.gl.glViewport(0, 0, BAKE_W, BAKE_H);
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        modelBatch.begin(bakeCamera);
        creature.render(modelBatch, env);
        modelBatch.end();
        sceneFbo.end();

        // --- Pass 2: stylization ---
        styleFbo.begin();
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        spriteBatch.setShader(styleShader);
        spriteBatch.begin();
        // flip Y: libGDX FBO textures are upside-down when drawn with SpriteBatch
        spriteBatch.draw(sceneFbo.getColorBufferTexture(),
            0, BAKE_H, BAKE_W, -BAKE_H);   // negative height flips it
        spriteBatch.end();
        spriteBatch.setShader(null);
        styleFbo.end();

        return copyToTexture(styleFbo);
    }

    private Texture copyToTexture(FrameBuffer fbo) {
        Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, BAKE_W, BAKE_H);
        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); // no blurring — hard pixels
        pm.dispose();
        return t;
    }

    @Override public void dispose() {
        sceneFbo.dispose(); styleFbo.dispose();
        modelBatch.dispose(); spriteBatch.dispose();
        styleShader.dispose();
    }
}
```

**Critical detail — `Nearest` texture filter:** The existing pixel art sprites use `Nearest` filtering. The baked textures must also use `Nearest` so they render with hard pixel edges matching the existing art style, not blurred bilinear interpolation.

---

## 6. The Stylization Shader — Matching the Existing Pixel Art

The shader has one job: turn the smooth 3D render into output that is visually indistinguishable in style from the existing `assets/images/monsters/*.png` sprites.

Looking at those sprites, the recipe is:
1. **Pixelate** — snap to ~4px blocks so individual "art pixels" are visible
2. **Posterize to 5 tonal steps** — dark shadow, mid shadow, base tone, lit, highlight
3. **Map each step to the game's actual palette** — purple/cream tones, not the 3D model's original colours
4. **Hard black outline** via Sobel alpha-edge detection — 1px dark border rings every silhouette
5. **Black background** — transparent pixels become black (matching existing sprite backgrounds)

### `assets/shaders/creature_stylize.vert`
```glsl
attribute vec4 a_position;
attribute vec2 a_texCoord0;
uniform   mat4 u_projTrans;
varying   vec2 v_uv;

void main() {
    v_uv        = a_texCoord0;
    gl_Position = u_projTrans * a_position;
}
```

### `assets/shaders/creature_stylize.frag`
```glsl
#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_uv;

uniform sampler2D u_texture;
uniform vec2      u_resolution;    // (512.0, 512.0)
uniform float     u_blockSize;     // art-pixel block size in texels, e.g. 8.0 → 64×64 art-pixels

// Game palette (5 tones from dark to light, matching existing sprite colours)
// Tune these to taste against the existing sprites
const vec3 TONE_0 = vec3(0.05, 0.00, 0.06);   // near-black shadow / outline
const vec3 TONE_1 = vec3(0.24, 0.10, 0.36);   // dark purple
const vec3 TONE_2 = vec3(0.42, 0.18, 0.59);   // mid purple
const vec3 TONE_3 = vec3(0.62, 0.49, 0.77);   // light purple / lavender
const vec3 TONE_4 = vec3(0.83, 0.77, 0.60);   // cream / bone highlight

// Snap UV to nearest art-pixel block
vec2 pixelSnap(vec2 uv) {
    vec2 artPixels = u_resolution / u_blockSize;
    return (floor(uv * artPixels) + 0.5) / artPixels;
}

// Map a luminance value [0..1] to one of 5 palette tones (hard steps, no lerp)
vec3 paletteLookup(float lum) {
    if      (lum < 0.15) return TONE_0;
    else if (lum < 0.35) return TONE_1;
    else if (lum < 0.55) return TONE_2;
    else if (lum < 0.75) return TONE_3;
    else                 return TONE_4;
}

// Sobel edge detection on alpha channel — returns 0 (interior) or 1 (edge)
float alphaEdge(vec2 uv) {
    vec2 px = 1.0 / u_resolution;
    float tl = texture2D(u_texture, uv + vec2(-px.x,  px.y)).a;
    float tm = texture2D(u_texture, uv + vec2(   0.0, px.y)).a;
    float tr = texture2D(u_texture, uv + vec2( px.x,  px.y)).a;
    float ml = texture2D(u_texture, uv + vec2(-px.x,   0.0)).a;
    float mr = texture2D(u_texture, uv + vec2( px.x,   0.0)).a;
    float bl = texture2D(u_texture, uv + vec2(-px.x, -px.y)).a;
    float bm = texture2D(u_texture, uv + vec2(   0.0,-px.y)).a;
    float br = texture2D(u_texture, uv + vec2( px.x, -px.y)).a;
    float gx = -tl + tr - 2.0*ml + 2.0*mr - bl + br;
    float gy = -tl - 2.0*tm - tr + bl + 2.0*bm + br;
    return clamp(sqrt(gx*gx + gy*gy) * 2.5, 0.0, 1.0);
}

void main() {
    // 1. Snap to art-pixel grid
    vec2 snappedUV = pixelSnap(v_uv);
    vec4 raw = texture2D(u_texture, snappedUV);

    // 2. Transparent → solid black background (matches existing sprites)
    if (raw.a < 0.1) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // 3. Compute luminance of the 3D render
    float lum = dot(raw.rgb, vec3(0.299, 0.587, 0.114));

    // 4. Map to game palette via hard tonal steps
    vec3 paletteColor = paletteLookup(lum);

    // 5. Silhouette outline — run Sobel on un-snapped UV for pixel-accurate edges
    float edge = alphaEdge(v_uv);
    // Outline is the darkest tone (TONE_0), snapped hard
    vec3 finalColor = (edge > 0.5) ? TONE_0 : paletteColor;

    gl_FragColor = vec4(finalColor, 1.0);
}
```

**Tuning guide:**

| Uniform | Effect | Start Value |
|---|---|---|
| `u_blockSize` | Art-pixel block size in texels. 8.0 on a 512 FBO = 64×64 art pixels. Match to existing sprites (they look ~64-80 art pixels wide). | `8.0` |
| `TONE_0`–`TONE_4` | The 5 palette colors. Eyedropper these from the existing `giant_scorpion.png` or `minotaur.png` for exact match. | Values above |

**To handle monsters with non-purple colour schemes** (future expansion): pass the palette tones as uniforms rather than constants so per-monster colour themes can be applied.

---

## 7. The Two Lines That Touch `Monster.java`

The renderer already reads `monster.getTexture()`, `monster.hasDirectionalTextures()`, and `monster.getTextureForPlayerAngle()`. These fields are currently only populated from `AssetManager` in the constructor. We need setters so `MonsterFactory` can inject baked textures:

```java
// Add to Monster.java — two methods only, nothing else changes
public void setTexture(Texture t) {
    this.texture = t;
}

public void setBakedDirectionalTextures(Texture north, Texture east, Texture west, Texture[] southFrames) {
    this.texNorth    = north;
    this.texEast     = east;
    this.texWest     = west;
    this.southFrames = southFrames;
}
```

That is the complete change to existing code in the rendering path. Everything else — `hasDirectionalTextures()`, `getTextureForPlayerAngle()`, `updateAnimation()`, the column-by-column draw loop — works unchanged.

---

## 8. MonsterFactory Hook

```java
// In MonsterFactory.java — add CreatureBaker + CreatureStitcher fields (injected at construction)
public Monster createMonster(String monsterType, Vector2 position, BiomeFilterContext biome) {
    Monster monster = buildBaseMonster(monsterType, position);  // existing logic unchanged

    CreatureDna dna = dnaLibrary.get(monsterType);
    if (dna != null) {
        // Assemble 3D parts
        StitchedCreature stitched = stitcher.stitch(dna, biome, monster.getId());
        monster.setStitched(stitched);

        // Bake to pixel-art sprites
        BakedMonsterSprite baked = baker.bakeAllFacings(stitched);

        // Inject into Monster's existing texture fields
        // The renderer will automatically use the MODERN textured path
        monster.setBakedDirectionalTextures(
            baked.north,
            baked.east,
            baked.west,
            new Texture[]{ baked.south }  // extend to animated frames later
        );
    }
    // No DNA = existing PNG texture from AssetManager is used as-is (no regression)
    return monster;
}
```

---

## 9. Limb Severing

When a limb is severed the monster needs a new baked texture (missing the severed part) and the limb becomes a gore entity.

### 9.1 Re-bake after sever

```java
// Add to Monster.java
public void severLimb(String socket, CreatureBaker baker) {
    if (stitched == null) return;
    stitched.removeLimb(socket);

    // Re-bake all 4 angles without the removed limb
    BakedMonsterSprite updated = baker.bakeAllFacings(stitched);
    setBakedDirectionalTextures(updated.north, updated.east, updated.west,
                                 new Texture[]{updated.south});

    // Spawn the severed limb as a gore entity
    maze.getGoreManager().spawnSeveredLimb(socket, getPosition(), stitched.getLimbModel(socket), baker);
}
```

### 9.2 Severed limb as a Gib

The existing `renderTextureGibs()` in `EntityRenderer` already renders `Gib` objects that have a `textureRegion`. The severed limb is a `Gib` with a baked limb texture — it needs zero renderer changes:

```java
// In GoreManager.spawnSeveredLimb() — bake just the one limb part
public void spawnSeveredLimb(String socket, Vector2 origin, ModelInstance limbModel, CreatureBaker baker) {
    StitchedCreature single = StitchedCreature.fromSingleLimb(limbModel);
    Texture limbTex = baker.bakeAtAngle(single, 0f);   // front-facing only

    Gib gib = new Gib(origin);
    gib.textureRegion = new TextureRegion(limbTex);
    gib.color         = Color.WHITE;
    gib.velX = MathUtils.random(-2f, 2f);
    gib.velY = MathUtils.random(1f, 4f);
    gib.rotSpeed = MathUtils.random(-200f, 200f);
    addGib(gib);
}
```

`renderTextureGibs()` picks this up and renders it with rotation — already implemented at `EntityRenderer.java:531`.

---

## 10. New Classes Summary

| Class | Package | Description |
|---|---|---|
| `CreatureDna` | `gamedata.monster.stitcher` | POJO; loaded from `dna/*.json` |
| `AttachmentPoint` | `gamedata.monster.stitcher` | Socket name, bone name, scale, optional flag |
| `CreaturePartDef` | `gamedata.monster.stitcher` | Part id, model path, socket type |
| `CreaturePartLibrary` | `gamedata.monster.stitcher` | Loads/caches `.g3db` Model handles |
| `BiomeFilterContext` | `gamedata.monster.stitcher` | Value object: temp, humidity, gravity floats |
| `WeightedPartSelector` | `gamedata.monster.stitcher` | Stateless; biome-weighted part selection |
| `CreatureStitcher` | `gamedata.monster.stitcher` | Assembles `ModelInstance` tree from DNA |
| `StitchedCreature` | `gamedata.monster.stitcher` | Runtime: torso + limb map + sever support |
| `CreatureBaker` | `rendering` | FrameBuffer + ModelBatch + stylization pipeline |
| `BakedMonsterSprite` | `rendering` | Holds `Texture` N/E/W/S facing bakes |

---

## 11. File Change Summary

| File | Change Type | What Changes |
|---|---|---|
| `Monster.java` | **Modify — 2 methods** | Add `setTexture()`, `setBakedDirectionalTextures()` |
| `MonsterFactory.java` | **Modify** | Inject baker+stitcher; call on spawn when DNA exists |
| `EntityRenderer.java` | **No change** | Already handles everything |
| `assets/shaders/creature_stylize.vert` | **New** | |
| `assets/shaders/creature_stylize.frag` | **New** | Pixelate + palette map + Sobel outline |
| `gamedata/monster/stitcher/*.java` | **New** | 8 new classes |
| `rendering/CreatureBaker.java` | **New** | |
| `rendering/BakedMonsterSprite.java` | **New** | |
| `assets/models/creatures/**/*.g3db` | **New** | AI-generated parts, converted offline |
| `assets/data/creatures/dna/*.json` | **New** | Creature DNA descriptors |
| `assets/data/creatures/parts/parts_manifest.json` | **New** | |

---

## 12. Integration Milestones

### Milestone 1 — Shader Aesthetic Match (Days 1–2)
Write a standalone `BakerTestScreen`. Load any `.g3db` (a box if nothing else exists yet), bake it, render the output alongside `giant_scorpion.png` side-by-side on screen. Tune `u_blockSize` and the `TONE_*` constants until both look like they belong to the same game.

**Pass criteria:** A developer looking at both cannot immediately tell the baked output is 3D-sourced.

### Milestone 2 — Single Part In-World (Days 3–5)
- Install `gdx-tools` in `tools/` subproject
- Generate one part (e.g. a head) via Meshy.ai, convert to `.g3db`
- Implement `CreaturePartLibrary`, `StitchedCreature` (torso-only, no sockets yet)
- Bake to texture, inject via `monster.setTexture()` on one monster type
- Run the game in MODERN mode — verify the monster renders at the correct position and scale

### Milestone 3 — Full Assembly + 4 Directions (Days 6–12)
- Generate a full part set for one DNA (head, 2 arms, 2 legs, tail)
- Implement `CreatureStitcher` with socket attachment
- Bake all 4 facing angles — wire `setBakedDirectionalTextures()`
- Walk around the monster and confirm correct facing texture is shown at each angle

### Milestone 4 — Severing (Days 13–16)
- Implement `severLimb()` on `Monster`
- Implement `GoreManager.spawnSeveredLimb()`
- Trigger on a critical hit in combat
- Verify re-bake, monster looks correct without severed limb, limb gib arcs and lands

### Milestone 5 — Biome Variation (Days 17–20)
- Implement `WeightedPartSelector` with biome modifier JSON
- Verify two spawns of same monster type in different biomes produce visually different creatures

---

## 13. Risk Register

| Risk | Mitigation |
|---|---|
| Bake hitch on monster spawn causes frame stutter | Bake asynchronously during spawn-in animation (first frame shows a fade-in effect anyway); or pre-bake a pool of common assemblies at level load |
| Stylization palette looks wrong for cream/bone accent areas (claws, horns) | The palette mapping is luminance-only — very bright regions automatically map to `TONE_4` (cream). If the 3D model's horns/claws are modelled in a lighter material, this works naturally |
| `.g3db` pivot misaligned from AI tool | Enforce in Meshy prompt; write `CreatureValidator.validate(model)` that logs if bounding box centre is not within 0.05u of origin |
| Re-bake on sever causes GPU pipeline flush mid-frame | Cap one re-bake per frame; queue additional severs. A single 512×512 bake pass takes ~1ms on any modern GPU — acceptable |
| FBO texture Y-flip causes upside-down sprites | Already handled in `bakeAtAngle()` by drawing with negative height: `spriteBatch.draw(..., 0, BAKE_H, BAKE_W, -BAKE_H)` |

---

## 14. Live In-Game Integration: Strata 3+ Aberrations (Decision 2026-09-11)

### Gameplay Role
To maintain the iconic identity of classic Tarmin monsters (Skeletons, Giant Ants, Scorpions) while leveraging the procedural baking pipeline:
1. **Standard Delves (Strata 1–2)**: Corridors exclusively spawn handcrafted, iconic monsters loaded from `monsters.json`.
2. **Deep Strata Delves (Strata 3+)**: `SpawnManager.java` rolls a chance (e.g. 15–20% on Strata 3, 30% on Strata 4) to spawn a procedural **"Aberration / Chimera"** miniboss.
3. **Runtime Assembly**:
   - `SpawnManager` selects a DNA descriptor (or randomizes limb selection via `WeightedPartSelector`).
   - `CreatureBaker` bakes the 4 directional angles into a `BakedMonsterSprite` during chunk generation.
   - Monster is initialized with dynamic HP/Damage scaled to effective depth, and limb severing is active via `GoreManager`.

