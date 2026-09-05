# Implementation Plan: Modular Creature "Stitcher" System & Atmospheric Weather Engine

**Branch:** `monster_overhaul`  
**Date:** 2026-05-07  
**Author:** Senior Game Architect (Claude Sonnet 4.6)

---

## Executive Summary

This plan extends the existing Tarmin2 architecture with two interrelated systems:

1. **Modular Stitcher Architecture** — a JSON-driven "DNA" system that procedurally assembles 3D creature bodies from pre-authored `.g3db` limb assets, influenced by biome environmental variables.
2. **Atmospheric Weather Engine** — an upgrade to the existing `WeatherManager` / `WeatherRenderer` pipeline to drive GPU-side fog, wetness, and performant particle layers via uniform variables passed to world shaders.

Both systems plug into the existing `FirstPersonRenderer`, `MonsterRenderer`, `MonsterFactory`, and the `weather/` package. No existing classes are removed; all work is additive.

---

## Required libGDX Extensions

| Extension | Artifact | Purpose | Status |
|---|---|---|---|
| gdx-bullet | `com.badlogicgames.gdx:gdx-bullet:1.13.1` | Rigid-body physics, constraints | **Already present** |
| gdx-tools | `com.badlogicgames.gdx:gdx-tools` | FBX→G3DB pipeline (offline) | Add to `tools/` subproject |
| gdx-freetype | `com.badlogicgames.gdx:gdx-freetype:1.13.1` | Font rendering | **Already present** |
| gdx core (3D) | `com.badlogicgames.gdx:gdx:1.13.1` | FrameBuffer, Decal, ModelBatch | **Already present** |

> **Note:** `gdx-3d` is part of the core jar since libGDX 1.9.x. No separate artifact is needed for `ModelBatch`, `FrameBuffer`, or `Decal`.

---

## Phase 1 — The Modular "Stitcher" Architecture

### 1.1 JSON Descriptor ("DNA") Structure

Every creature is described by a **Creature DNA** file. The torso defines named `AttachmentPoints` (sockets); the part library fills those sockets.

**`assets/data/creatures/dna/troll_forest.json` (example)**

```json
{
  "id": "troll_forest",
  "family": "GIANT",
  "torso": {
    "model": "models/creatures/torso_hulk.g3db",
    "attachmentPoints": [
      { "socket": "HEAD",        "bone": "socket_head",   "scale": 1.0 },
      { "socket": "ARM_L",       "bone": "socket_arm_l",  "scale": 0.9 },
      { "socket": "ARM_R",       "bone": "socket_arm_r",  "scale": 0.9 },
      { "socket": "LEG_L",       "bone": "socket_leg_l",  "scale": 1.0 },
      { "socket": "LEG_R",       "bone": "socket_leg_r",  "scale": 1.0 },
      { "socket": "TAIL",        "bone": "socket_tail",   "scale": 0.8, "optional": true }
    ]
  },
  "partWeights": {
    "HEAD":  ["head_troll_A:60", "head_troll_horned:40"],
    "ARM_L": ["arm_claw_heavy:50", "arm_club:30", "arm_tentacle:20"],
    "ARM_R": ["arm_claw_heavy:50", "arm_club:30", "arm_tentacle:20"],
    "LEG_L": ["leg_hoof:70", "leg_plantigrade:30"],
    "LEG_R": ["leg_hoof:70", "leg_plantigrade:30"],
    "TAIL":  ["tail_spike:100"]
  },
  "biomeModifiers": {
    "temperature": { "cold": { "TAIL": ["tail_frost:80", "tail_spike:20"] } },
    "humidity":    { "wet":  { "ARM_L": ["arm_tentacle:90", "arm_claw_heavy:10"] } }
  }
}
```

**`assets/data/creatures/parts/parts_manifest.json`**

```json
{
  "parts": [
    { "id": "head_troll_A",      "model": "models/creatures/parts/head_troll_a.g3db",      "socket": "HEAD" },
    { "id": "head_troll_horned", "model": "models/creatures/parts/head_troll_horned.g3db", "socket": "HEAD" },
    { "id": "arm_claw_heavy",    "model": "models/creatures/parts/arm_claw_heavy.g3db",    "socket": "ARM_L" },
    { "id": "arm_tentacle",      "model": "models/creatures/parts/arm_tentacle.g3db",      "socket": "ARM_L" }
  ]
}
```

---

### 1.2 New Java Class Outline

**Package:** `com.bpm.minotaur.gamedata.monster.stitcher`

```
CreatureDna.java            — POJO; loaded from dna/*.json via Gson
AttachmentPoint.java        — POJO; socket name, bone name, scale, optional flag
CreaturePartDef.java        — POJO; id, model path, socket type
CreaturePartLibrary.java    — Singleton; loads parts_manifest.json; caches Model handles
BiomeFilterContext.java     — Value object; holds temp, humidity, gravity floats
WeightedPartSelector.java   — Stateless; applies biome modifiers, returns part id via rng
CreatureStitcher.java       — Assembles ModelInstance from Dna + context + part lib
StitchedCreature.java       — Runtime creature; holds ModelInstance[], one per limb node
```

**`CreatureStitcher.java` — core method skeleton**

```java
package com.bpm.minotaur.gamedata.monster.stitcher;

public class CreatureStitcher {

    private final CreaturePartLibrary library;
    private final WeightedPartSelector selector;

    public StitchedCreature stitch(CreatureDna dna, BiomeFilterContext biome, long seed) {
        ModelInstance torso = new ModelInstance(library.get(dna.torso.model));
        Map<String, ModelInstance> limbs = new LinkedHashMap<>();

        for (AttachmentPoint socket : dna.torso.attachmentPoints) {
            String partId = selector.select(dna, socket.socket, biome, seed);
            if (partId == null && socket.optional) continue;

            ModelInstance limb = new ModelInstance(library.get(partId));
            Node boneNode = torso.getNode(socket.bone);
            // align limb origin to bone world transform
            limb.transform.set(boneNode.globalTransform);
            limbs.put(socket.socket, limb);
        }
        return new StitchedCreature(torso, limbs, dna);
    }
}
```

**`WeightedPartSelector.java` — biome filtering**

```java
public String select(CreatureDna dna, String socket, BiomeFilterContext biome, long seed) {
    // 1. start with base weights from dna.partWeights
    Map<String, Integer> weights = parseWeights(dna.partWeights.get(socket));

    // 2. overlay biome modifier entries (replace weight map slice)
    applyBiomeModifiers(dna.biomeModifiers, biome, socket, weights);

    // 3. weighted random draw
    return weightedDraw(weights, new Random(seed));
}
```

**Integration point:** `MonsterFactory.java` — replace direct `Monster` construction with a call to `CreatureStitcher.stitch()`, storing the resulting `StitchedCreature` on the `Monster` instance.

---

## Phase 2 — The 2.5D Rendering Pipeline

### 2.1 Off-Screen Baking with FrameBuffer

Each `StitchedCreature` is rendered once per pose-change (not every frame) to a `FrameBufferObject`. The resulting texture becomes the billboard sprite.

**`CreatureBaker.java`** — `com.bpm.minotaur.rendering`

```java
public class CreatureBaker implements Disposable {

    private static final int BAKE_SIZE = 128; // base resolution before pixelation
    private final FrameBuffer fbo;
    private final ModelBatch modelBatch;
    private final OrthographicCamera bakeCamera;

    public CreatureBaker() {
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, BAKE_SIZE, BAKE_SIZE, true);
        modelBatch = new ModelBatch();
        bakeCamera = new OrthographicCamera(2f, 2f);
        bakeCamera.near = 0.1f;
        bakeCamera.far  = 100f;
        bakeCamera.position.set(0, 1, 3);
        bakeCamera.lookAt(0, 1, 0);
        bakeCamera.update();
    }

    public TextureRegion bake(StitchedCreature creature, Environment env) {
        fbo.begin();
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        modelBatch.begin(bakeCamera);
        modelBatch.render(creature.torso, env);
        for (ModelInstance limb : creature.limbs.values()) modelBatch.render(limb, env);
        modelBatch.end();
        fbo.end();
        return new TextureRegion(fbo.getColorBufferTexture());
    }
}
```

**Baking triggers:** initial spawn, on limb detachment (re-bake without the lost limb), on palette-swap events.

---

### 2.2 Pixelation Shader

Add `assets/shaders/creature_pixelate.vert` / `creature_pixelate.frag`.

**`creature_pixelate.frag`**

```glsl
#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform vec2 u_resolution;   // full bake resolution (e.g. 128x128)
uniform float u_pixelSize;   // target pixel block size (e.g. 4.0 = 32px effective)

varying vec2 v_texCoords;

void main() {
    // snap UV to nearest pixel block
    vec2 blocks = u_resolution / u_pixelSize;
    vec2 snapped = (floor(v_texCoords * blocks) + 0.5) / blocks;
    gl_FragColor = texture2D(u_texture, snapped);
}
```

**`creature_pixelate.vert`** — standard pass-through (reuse `floor.vert` or write a minimal one).

The `CreatureBaker` runs the FBO texture through a second `SpriteBatch` pass with this shader before storing the final `TextureRegion`.

---

### 2.3 Billboard System

The pixelated `TextureRegion` is wrapped in a libGDX `Decal` that auto-faces the `PerspectiveCamera`.

**`CreatureBillboard.java`** — `com.bpm.minotaur.rendering`

```java
public class CreatureBillboard {

    private final Decal decal;
    private final Monster monster;

    public CreatureBillboard(TextureRegion region, Monster monster) {
        this.monster  = monster;
        this.decal    = Decal.newDecal(region, true); // true = blending
        decal.setScale(monster.getWorldScale());
    }

    public void update(PerspectiveCamera cam) {
        Vector3 pos = monster.getWorldPosition();
        decal.setPosition(pos.x, pos.y + 0.5f, pos.z);
        decal.lookAt(cam.position, cam.up);  // billboard facing
    }

    public Decal getDecal() { return decal; }
}
```

**Integration into `MonsterRenderer.java`:** replace or supplement the existing 2D sprite path with `CreatureBillboard.update(cam)` followed by a `DecalBatch.add(billboard.getDecal())` call.

---

## Phase 3 — "Exploding" Physics Logic

### 3.1 Physics Graph on StitchedCreature

When a creature spawns, each limb `ModelInstance` gets a corresponding `btRigidBody`. Limbs are held to the torso via `btFixedConstraint`.

**`CreaturePhysicsGraph.java`** — `com.bpm.minotaur.paperdoll.physics`

```java
public class CreaturePhysicsGraph implements Disposable {

    private final btRigidBody torsoBody;
    private final Map<String, btRigidBody> limbBodies;     // socket → body
    private final Map<String, btFixedConstraint> joints;   // socket → constraint

    public CreaturePhysicsGraph(StitchedCreature creature, btDynamicsWorld world) {
        torsoBody = buildBody(creature.torso, 0f); // mass=0 (kinematic anchor)
        world.addRigidBody(torsoBody);

        limbBodies = new LinkedHashMap<>();
        joints     = new LinkedHashMap<>();

        for (Map.Entry<String, ModelInstance> e : creature.limbs.entrySet()) {
            btRigidBody limb = buildBody(e.getValue(), 1.0f);
            world.addRigidBody(limb);

            btTransform frameInA = new btTransform(Matrix4.idt);
            btTransform frameInB = new btTransform(Matrix4.idt);
            btFixedConstraint joint = new btFixedConstraint(torsoBody, limb, frameInA, frameInB);
            joint.setBreakingImpulseThreshold(Float.MAX_VALUE); // never auto-break
            world.addConstraint(joint, true);

            limbBodies.put(e.getKey(), limb);
            joints.put(e.getKey(), joint);
        }
    }

    private btRigidBody buildBody(ModelInstance mi, float mass) {
        btBoxShape shape = new btBoxShape(computeHalfExtents(mi));
        btVector3 inertia = new btVector3(0, 0, 0);
        if (mass > 0) shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, shape, inertia);
        return new btRigidBody(info);
    }
}
```

---

### 3.2 Detachment Event

**`CreaturePhysicsGraph.java` — `detachLimb(socket)`**

```java
public void detachLimb(String socket, btDynamicsWorld world, Vector3 impulseDir) {
    btFixedConstraint joint = joints.remove(socket);
    if (joint == null) return;

    world.removeConstraint(joint);
    joint.dispose();

    btRigidBody limb = limbBodies.get(socket);
    limb.setActivationState(Collision.ACTIVE_TAG);

    // convert direction to bullet vector
    btVector3 impulse = new btVector3(
        impulseDir.x * DETACH_FORCE,
        impulseDir.y * DETACH_FORCE + DETACH_UPWARD_BIAS,
        impulseDir.z * DETACH_FORCE
    );
    limb.applyCentralImpulse(impulse);
    impulse.dispose();
}

private static final float DETACH_FORCE       = 8.0f;
private static final float DETACH_UPWARD_BIAS = 3.0f;
```

**Defeat / Explode sequence in `Monster.java`:**

```java
public void onDefeated(Vector3 hitDir) {
    for (String socket : physicsGraph.getAllSockets()) {
        Vector3 spread = hitDir.cpy().add(MathUtils.random(-0.3f, 0.3f),
                                          MathUtils.random(0f, 0.5f),
                                          MathUtils.random(-0.3f, 0.3f));
        physicsGraph.detachLimb(socket, world, spread);
    }
    // trigger re-bake with empty limb map for death sprite
    baker.bake(stitchedCreature, env);
    setState(MonsterState.DEAD);
}
```

---

## Phase 4 — Atmospheric Weather Engine

### 4.1 WeatherManager Uniform Bridge

The existing `WeatherManager.java` is extended to maintain a `WeatherUniforms` value object that is passed to all world shaders each frame.

**`WeatherUniforms.java`** — `com.bpm.minotaur.weather`

```java
public class WeatherUniforms {
    public float fogDensity;       // 0..1
    public float fogHeightFalloff; // world-units; fog fades above this Y
    public float fogColorR, fogColorG, fogColorB;
    public float wetness;          // 0..1; drives specular multiplier in shaders
    public float rainIntensity;    // 0..1; particle density
    public float snowIntensity;    // 0..1
}
```

**Additions to `WeatherManager.java`:**

```java
// existing fields preserved; new additions:
private WeatherUniforms uniforms = new WeatherUniforms();

public void update(float delta) {
    // existing transition logic...
    uniforms.fogDensity       = lerp(uniforms.fogDensity, targetFogDensity, delta * FOG_LERP_SPEED);
    uniforms.wetness          = lerp(uniforms.wetness, targetWetness, delta * WET_LERP_SPEED);
    uniforms.fogHeightFalloff = currentBiome.getFogHeightFalloff();
    // push to renderer
    weatherRenderer.applyUniforms(uniforms);
}

public WeatherUniforms getUniforms() { return uniforms; }
```

---

### 4.2 Shader Features

#### Height Fog — `floor.frag` additions

```glsl
// --- Weather uniforms (add to existing floor.frag) ---
uniform float u_fogDensity;
uniform float u_fogHeightFalloff;
uniform vec3  u_fogColor;

// At end of main(), blend fog over final color:
float heightFog = clamp(1.0 - v_worldPos.y / u_fogHeightFalloff, 0.0, 1.0);
float fog = u_fogDensity * heightFog * heightFog; // quadratic falloff
gl_FragColor.rgb = mix(gl_FragColor.rgb, u_fogColor, fog);
```

`v_worldPos` must be passed as a `varying` from the vertex shader (add to `floor.vert`).

#### Specular Wetness — `floor.frag` additions

```glsl
uniform float u_wetness;

// Modulate specular term (assumes Phong or Blinn-Phong exists in shader):
float wetSpec = mix(u_shininess, u_shininess * 4.0, u_wetness);
vec3 specular = pow(max(dot(reflectDir, viewDir), 0.0), wetSpec) * u_specularColor;
gl_FragColor.rgb += specular * u_wetness;
```

#### `WeatherRenderer.java` — uniform push method

```java
public void applyUniforms(WeatherUniforms u) {
    floorShader.begin();
    floorShader.setUniformf("u_fogDensity",       u.fogDensity);
    floorShader.setUniformf("u_fogHeightFalloff",  u.fogHeightFalloff);
    floorShader.setUniformf("u_fogColor",          u.fogColorR, u.fogColorG, u.fogColorB);
    floorShader.setUniformf("u_wetness",           u.wetness);
    floorShader.end();
}
```

---

### 4.3 Performant Particle Strategy

The grid-based first-person view constrains the visible volume to a narrow frustum — this is a hard performance win.

**Strategy: Layered Billboard Pool**

- Maintain a fixed `ParticlePool` of ~200 `Decal` instances reused across frames (object pool, no GC pressure).
- Emit particles only in the 3×3 tile area directly ahead of the player — outside that area particles are invisible anyway.
- Use a single `DecalBatch` per weather type (rain, snow), submitted after the monster `DecalBatch`.

**`WeatherParticleSystem.java`** — `com.bpm.minotaur.weather`

```java
public class WeatherParticleSystem implements Disposable {

    private static final int POOL_SIZE = 200;
    private final Decal[] pool = new Decal[POOL_SIZE];
    private final DecalBatch batch;

    public WeatherParticleSystem(Texture rainTex, CameraGroupStrategy strategy) {
        batch = new DecalBatch(strategy);
        for (int i = 0; i < POOL_SIZE; i++) {
            pool[i] = Decal.newDecal(0.05f, 0.3f, new TextureRegion(rainTex), true);
        }
    }

    public void update(float delta, WeatherUniforms u, Vector3 playerPos, PerspectiveCamera cam) {
        int active = (int)(u.rainIntensity * POOL_SIZE);
        for (int i = 0; i < active; i++) {
            Decal d = pool[i];
            // drift downward; reset at top when below floor
            d.getPosition().y -= 4.0f * delta;
            if (d.getPosition().y < 0) resetParticle(d, playerPos);
            d.lookAt(cam.position, cam.up);
            batch.add(d);
        }
        batch.flush();
    }

    private void resetParticle(Decal d, Vector3 origin) {
        d.setPosition(
            origin.x + MathUtils.random(-3f, 3f),
            3.5f,
            origin.z + MathUtils.random(-3f, 3f)
        );
    }
}
```

---

## Integration Roadmap

### Step 1 — Part Library & DNA Loader (Week 1)
1. Create `assets/data/creatures/dna/` and `assets/data/creatures/parts/` directories.
2. Implement `CreaturePartLibrary`, `CreatureDna`, `AttachmentPoint`, `CreaturePartDef` POJOs.
3. Write unit tests: load a DNA file, verify part list resolves without models loaded.

### Step 2 — Stitcher Core (Week 1–2)
1. Implement `WeightedPartSelector` with biome modifier logic.
2. Implement `CreatureStitcher.stitch()` using placeholder cube meshes (no `.g3db` files yet).
3. Hook into `MonsterFactory`: add a `StitchedCreature` field to `Monster`.

### Step 3 — Baker + Shader (Week 2)
1. Write `CreatureBaker` with `FrameBuffer` pipeline.
2. Add `creature_pixelate.frag` / `creature_pixelate.vert` to `assets/shaders/`.
3. Wire `CreatureBillboard` into `MonsterRenderer` alongside (not replacing) the existing 2D sprite path — gated by a `MonsterSpriteData.useStitcher` flag.

### Step 4 — Physics Graph (Week 3)
1. Implement `CreaturePhysicsGraph` using the already-loaded `gdx-bullet`.
2. Test detach on a single-limb prototype monster; verify impulse direction is correct.
3. Hook `Monster.onDefeated()` to trigger full detach sequence.

### Step 5 — Weather Shader Uniforms (Week 3–4)
1. Add `WeatherUniforms` value object.
2. Extend `WeatherManager.update()` to populate uniforms from `WeatherType` data.
3. Add height fog uniforms to `floor.vert` / `floor.frag` (pass `v_worldPos`).
4. Add wetness specular to `floor.frag`.
5. Push uniforms in `WeatherRenderer.applyUniforms()`.

### Step 6 — Particle System (Week 4)
1. Implement `WeatherParticleSystem` with object pool.
2. Create rain and snow `Texture` assets (single-pixel streak and snowflake sprites).
3. Integrate into `WeatherRenderer.render()`.

### Step 7 — Asset Pipeline (Ongoing, parallel)
1. Author torso `.g3db` files with named bone sockets (Blender → FBX → `gdx-tools` converter).
2. Author limb `.g3db` files with consistent pivot points at the attach origin.
3. Validate in `CreatureBaker` against each DNA file.

---

## File Change Summary

| File | Action | Notes |
|---|---|---|
| `gamedata/monster/Monster.java` | Modify | Add `StitchedCreature`, `CreaturePhysicsGraph` fields; `onDefeated()` |
| `generation/MonsterFactory.java` | Modify | Call `CreatureStitcher.stitch()` on spawn |
| `rendering/MonsterRenderer.java` | Modify | Add `DecalBatch` path; `CreatureBillboard` updates |
| `weather/WeatherManager.java` | Modify | Add `WeatherUniforms` population and lerp |
| `weather/WeatherRenderer.java` | Modify | Add `applyUniforms()`, host `WeatherParticleSystem` |
| `assets/shaders/floor.vert` | Modify | Pass `v_worldPos` varying |
| `assets/shaders/floor.frag` | Modify | Height fog + specular wetness |
| `assets/shaders/creature_pixelate.vert` | **New** | Pass-through vertex shader |
| `assets/shaders/creature_pixelate.frag` | **New** | Pixel-block downsampling shader |
| `gamedata/monster/stitcher/*.java` | **New** | Full stitcher package (7 classes) |
| `rendering/CreatureBaker.java` | **New** | FrameBuffer bake pipeline |
| `rendering/CreatureBillboard.java` | **New** | Decal billboard wrapper |
| `paperdoll/physics/CreaturePhysicsGraph.java` | **New** | Bullet constraint graph |
| `weather/WeatherUniforms.java` | **New** | Shader uniform value object |
| `weather/WeatherParticleSystem.java` | **New** | Pooled billboard particle system |
| `assets/data/creatures/dna/*.json` | **New** | Per-creature DNA files |
| `assets/data/creatures/parts/parts_manifest.json` | **New** | Part library manifest |

---

## Risk Register

| Risk | Mitigation |
|---|---|
| `.g3db` bone naming mismatches between torso and limb files | Enforce socket naming convention in asset pipeline; add a `CreatureValidator` tool that logs mismatches at load time |
| FrameBuffer bake cost on initial spawn causing hitch | Bake asynchronously on a second render pass; defer first-frame billboard to a "hatching" animation frame |
| `btFixedConstraint` drift on kinematic torso | Set torso body as `KINEMATIC_CHARACTER_OBJECT`; sync its world transform from the `Monster` position each tick |
| Height fog `v_worldPos` varying breaks existing floor shader logic | Add `#ifdef USE_WEATHER_FOG` preprocessor guard; existing path unchanged when flag absent |
| Particle GC on mobile/low-end desktop | Object pool (already in plan) eliminates allocation; cap `POOL_SIZE` to 100 for low-spec profile |
