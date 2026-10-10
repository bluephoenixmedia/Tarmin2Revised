package com.bpm.minotaur.rendering.attract;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterIdleAnimationRegistry;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.managers.DayNightManager;
import com.bpm.minotaur.rendering.Skybox3DRenderer;
import com.bpm.minotaur.rendering.mesh.ChunkMeshBuilder;
import com.bpm.minotaur.rendering.mesh.ChunkSubMesh;
import com.bpm.minotaur.rendering.mesh.DynamicQuadBatcher;
import com.bpm.minotaur.weather.WeatherType;

import java.util.*;

/**
 * High-fidelity 3D renderer for the Main Menu Attract Mode flyover.
 * Manages active chunk trajectory streaming over the 100-chunk world,
 * dynamic 3D skybox dome, warring faction armies, projectile volleys,
 * atmospheric weather arc, and cinematic camera choreography.
 */
public class AttractModeRenderer implements Disposable {

    private static final String TAG = "AttractModeRenderer";

    private final AttractSplinePath splinePath;
    private final AttractWorld world;
    private final AttractController controller;
    private final AttractSoundManager soundManager;
    private final AttractWarDirector warDirector;
    private final AttractProjectileManager projectileManager;
    private final AttractAtmosphereManager atmosphereManager;
    private final DayNightManager dayNightManager;

    private final PerspectiveCamera camera;
    private float flightTime = 0f;

    // Dive Transition Vectors
    private final Vector3 diveStartPos = new Vector3();
    private final Vector3 currentPos = new Vector3();
    private final Vector3 currentLookAt = new Vector3();

    // 3D Shader & Uniforms
    private ShaderProgram worldShader;
    private final Matrix4 identityMatrix = new Matrix4();

    // Skybox Renderer & State
    private Skybox3DRenderer skyboxRenderer;
    private final Skybox3DRenderer.SkyState skyState = new Skybox3DRenderer.SkyState();

    // Terrain Textures
    private Texture wallTexture;
    private Texture forestWallTexture;
    private Texture floorTexture;
    private Texture forestFloorTexture;
    private Texture desertFloorTexture;
    private Texture blankTexture;

    // Batchers
    private DynamicQuadBatcher liquidBatcher;
    private DynamicQuadBatcher actorBatcher;
    private DynamicQuadBatcher vfxBatcher;
    private DynamicQuadBatcher floorSealBatcher;

    // Scenic Props & VFX Textures
    private Texture campfireTexture;
    private Texture tentTexture;
    private Texture chestTexture;
    private Texture barricadeTexture;
    private Texture shieldTexture;
    private Texture arrowTexture;
    private Texture smokeTexture;
    private final Map<String, Texture> tabardTextures = new HashMap<>();
    private final Map<String, TextureRegion[]> spriteFrames = new HashMap<>();
    private final Map<String, Texture> loadedTextures = new HashMap<>();

    // Billboard Basis Vectors (0 heap allocations per frame)
    private final Vector3 camRight = new Vector3();
    private final Vector3 camUp = new Vector3();
    private final Vector3 camDir = new Vector3();
    private final Color scratchColor = new Color();

    // Active Chunk Mesh Cache
    private final Map<String, List<ChunkSubMesh>> cachedMeshes = new HashMap<>();
    private final Set<String> activeKeys = new HashSet<>();

    /**
     * The dark behind the maze, and the embers in it.
     */
    private VoidBackdrop voidBackdrop;

    public AttractModeRenderer() {
        this.splinePath = new AttractSplinePath();
        this.world = new AttractWorld();
        this.controller = new AttractController();
        this.soundManager = new AttractSoundManager();
        this.warDirector = new AttractWarDirector();
        this.projectileManager = new AttractProjectileManager();
        this.atmosphereManager = new AttractAtmosphereManager();
        this.dayNightManager = new DayNightManager(DayNightManager.DAWN_SUNRISE);

        this.camera = new PerspectiveCamera(65f, 1920f, 1080f);
        this.camera.near = 0.1f;
        this.camera.far = 400f;

        this.projectileManager.setWhistleListener(() -> {
            soundManager.playArrowWhistle();
        });

        initGLResources();
    }

    private void initGLResources() {
        try {
            // Compile world 3D shader
            ShaderProgram.pedantic = false;
            worldShader = new ShaderProgram(
                    Gdx.files.internal("shaders/world3d.vert"),
                    Gdx.files.internal("shaders/world3d.frag")
            );
            if (!worldShader.isCompiled()) {
                if (Gdx.app != null) {
                    Gdx.app.error(TAG, "Shader compile failed: " + worldShader.getLog());
                }
            }

            // Load terrain textures
            wallTexture = new Texture(Gdx.files.internal("images/wall.png"));
            wallTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

            if (Gdx.files.internal("images/forest_cliff.png").exists()) {
                forestWallTexture = new Texture(Gdx.files.internal("images/forest_cliff.png"));
                forestWallTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            } else {
                forestWallTexture = wallTexture;
            }

            floorTexture = new Texture(Gdx.files.internal("images/floor.png"));
            floorTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

            if (Gdx.files.internal("images/floor_forest.png").exists()) {
                forestFloorTexture = new Texture(Gdx.files.internal("images/floor_forest.png"));
                forestFloorTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            } else {
                forestFloorTexture = floorTexture;
            }

            if (Gdx.files.internal("images/floor_desert.png").exists()) {
                desertFloorTexture = new Texture(Gdx.files.internal("images/floor_desert.png"));
                desertFloorTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            } else {
                desertFloorTexture = floorTexture;
            }

            Pixmap pix = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
            pix.setColor(Color.WHITE);
            pix.fill();
            blankTexture = new Texture(pix);
            pix.dispose();

            liquidBatcher = new DynamicQuadBatcher();
            actorBatcher = new DynamicQuadBatcher();
            vfxBatcher = new DynamicQuadBatcher();
            floorSealBatcher = new DynamicQuadBatcher();

            voidBackdrop = new VoidBackdrop();

            // 3D Skybox Dome & Horizon Landmarks
            try {
                skyboxRenderer = new Skybox3DRenderer();
            } catch (Throwable t) {
                if (Gdx.app != null) {
                    Gdx.app.error(TAG, "Skybox3DRenderer initialization failed: " + t.getMessage(), t);
                }
                skyboxRenderer = null;
            }

            // Generate soft circular radial puff for volumetric smoke pillars
            Pixmap smokePix = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
            for (int py = 0; py < 64; py++) {
                for (int px = 0; px < 64; px++) {
                    float dx = (px - 31.5f) / 32f;
                    float dy = (py - 31.5f) / 32f;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    float a = Math.max(0f, 1f - dist);
                    a = a * a; // quadratic soft falloff
                    smokePix.setColor(1f, 1f, 1f, a);
                    smokePix.drawPixel(px, py);
                }
            }
            smokeTexture = new Texture(smokePix);
            smokePix.dispose();

            // Scenic Props
            campfireTexture = loadTextureWithFallback("images/props/campfire.png", "images/desert/campfire.png");
            tentTexture = loadTextureWithFallback("images/props/camp_tent.png", "images/desert/camp_tent.png");
            chestTexture = loadTextureWithFallback("images/items/chest.png", null);
            barricadeTexture = loadTextureWithFallback("images/props/shield_wall.png", null);
            shieldTexture = loadTextureWithFallback("images/armor/large_shield.png", null);
            arrowTexture = loadTextureWithFallback("images/weapons/arrow_war.png", null);

            // Tabards
            loadTabard(warDirector.getDefendingHouse().getDoctrineId());
            loadTabard(warDirector.getInvadingHouse().getDoctrineId());

            // Monster & Soldier Idle Animation Spritesheets
            loadMonsterSprite("GIANT_CENTIPEDE", Monster.MonsterType.GIANT_CENTIPEDE);
            loadMonsterSprite("HOBGOBLIN", Monster.MonsterType.HOBGOBLIN);
            loadMonsterSprite("LIZARD", Monster.MonsterType.LIZARD_WARRIOR);
            loadMonsterSprite("OGRE", Monster.MonsterType.OGRE);
            loadMonsterSprite("IRON_GOLEM", Monster.MonsterType.IRON_GOLEM);
            loadMonsterSprite("BEAR", Monster.MonsterType.OWLBEAR);
            loadMonsterSprite("GHOUL", Monster.MonsterType.GHOUL);
            loadMonsterSprite("WRAITH", Monster.MonsterType.WRAITH);
            loadMonsterSprite("MINOTAUR", Monster.MonsterType.MINOTAUR);
            loadMonsterSprite("GARGOYLE", Monster.MonsterType.GARGOYLE);
            loadMonsterSprite("GHAST", Monster.MonsterType.GHAST);
            loadMonsterSprite("MIND_FLAYER", Monster.MonsterType.MIND_FLAYER);
            loadMonsterSprite("SPECTER", Monster.MonsterType.SPECTER);

        } catch (Throwable t) {
            if (Gdx.app != null) {
                Gdx.app.error(TAG, "Initialization error in AttractModeRenderer: " + t.getMessage(), t);
            }
        }
    }

    private Texture loadTextureWithFallback(String primary, String fallback) {
        if (primary != null && Gdx.files.internal(primary).exists()) {
            Texture t = new Texture(Gdx.files.internal(primary));
            loadedTextures.put(primary, t);
            return t;
        } else if (fallback != null && Gdx.files.internal(fallback).exists()) {
            Texture t = new Texture(Gdx.files.internal(fallback));
            loadedTextures.put(fallback, t);
            return t;
        }
        return blankTexture;
    }

    private void loadTabard(String doctrineId) {
        String path = "images/paperdoll/chest/tabard_" + doctrineId + ".png";
        if (Gdx.files.internal(path).exists()) {
            Texture t = new Texture(Gdx.files.internal(path));
            loadedTextures.put(path, t);
            tabardTextures.put(doctrineId, t);
        }
    }

    private void loadMonsterSprite(String key, Monster.MonsterType monsterType) {
        try {
            MonsterIdleAnimationRegistry.IdleConfig cfg = MonsterIdleAnimationRegistry.getConfig(monsterType);
            if (cfg != null && Gdx.files.internal(cfg.texturePath).exists()) {
                Texture tex = new Texture(Gdx.files.internal(cfg.texturePath));
                loadedTextures.put(cfg.texturePath, tex);
                TextureRegion[][] split = TextureRegion.split(tex, tex.getWidth() / cfg.cols, tex.getHeight() / cfg.rows);
                TextureRegion[] frames = new TextureRegion[cfg.cols * cfg.rows];
                int idx = 0;
                for (int r = 0; r < cfg.rows; r++) {
                    for (int c = 0; c < cfg.cols; c++) {
                        frames[idx++] = split[r][c];
                    }
                }
                spriteFrames.put(key, frames);
                return;
            }

            // Fallback to static monster texture
            String staticPath = "images/monsters/" + monsterType.name().toLowerCase() + ".png";
            if (Gdx.files.internal(staticPath).exists()) {
                Texture tex = new Texture(Gdx.files.internal(staticPath));
                loadedTextures.put(staticPath, tex);
                spriteFrames.put(key, new TextureRegion[]{ new TextureRegion(tex) });
            }
        } catch (Throwable t) {
            if (Gdx.app != null) {
                Gdx.app.log(TAG, "Could not load monster sprite for " + key + ": " + t.getMessage());
            }
        }
    }

    public void update(float delta) {
        controller.update(delta);

        if (controller.isDiving()) {
            float progress = controller.getDiveProgress01();
            splinePath.evaluateDive(diveStartPos, progress, currentPos);
            currentLookAt.set(AttractSplinePath.CASTLE_ENTRANCE);
            camera.position.set(currentPos);
            camera.direction.set(currentLookAt).sub(currentPos).nor();
            camera.up.set(0f, 1f, 0f);
        } else {
            flightTime += delta;
            splinePath.getPosition(flightTime, currentPos);
            splinePath.getLookAt(flightTime, currentLookAt);
            float roll = splinePath.getRollDegrees(flightTime);

            camera.position.set(currentPos);
            camera.direction.set(currentLookAt).sub(currentPos).nor();
            camera.up.set(0f, 1f, 0f);
            camera.up.rotate(camera.direction, roll);
        }

        camera.update();

        // Billboard basis vectors
        camRight.set(camera.direction).crs(camera.up).nor();
        camUp.set(camRight).crs(camera.direction).nor();
        camDir.set(camera.direction);

        // Update War Director, Projectiles, and Atmosphere
        warDirector.update(delta, flightTime);
        projectileManager.update(delta, flightTime, camera.position, soundManager);
        atmosphereManager.update(delta, flightTime, camera.position, soundManager);

        // Evaluate Day/Night time of day for Skybox celestial arc
        float loopTime = flightTime % 90.0f;
        float timeOfDay;
        if (loopTime < 18f) {
            timeOfDay = MathUtils.lerp(0.25f, 0.45f, loopTime / 18f);
        } else if (loopTime < 36f) {
            timeOfDay = MathUtils.lerp(0.45f, 0.72f, (loopTime - 18f) / 18f);
        } else if (loopTime < 54f) {
            timeOfDay = MathUtils.lerp(0.72f, 0.85f, (loopTime - 36f) / 18f);
        } else if (loopTime < 68f) {
            timeOfDay = MathUtils.lerp(0.85f, 1.0f, (loopTime - 54f) / 14f);
        } else {
            timeOfDay = MathUtils.lerp(0.0f, 0.25f, (loopTime - 68f) / 22f);
        }
        dayNightManager.setTimeOfDay(timeOfDay % 1.0f);

        // Sync Skybox SkyState
        skyState.camX = camera.position.x;
        skyState.camZ = camera.position.z;
        skyState.forwardX = camera.direction.x;
        skyState.forwardY = camera.direction.y;
        skyState.forwardZ = camera.direction.z;
        skyState.up.set(camera.up);
        skyState.dayNight = dayNightManager;
        skyState.weather = atmosphereManager.isPrecipitationActive() ? WeatherType.STORM : WeatherType.CLEAR;
        skyState.stormy = atmosphereManager.isPrecipitationActive();
        skyState.cloudCover = atmosphereManager.isPrecipitationActive() ? 0.95f : 0.25f;
        skyState.flash = atmosphereManager.isLightningActive() ? 1.0f : 0.0f;
        skyState.doom = (loopTime >= 68f) ? 0.85f : 0.15f;
        skyState.chunkX = MathUtils.clamp((int) (camera.position.x / AttractWorld.CHUNK_TILES), 0, AttractWorld.GRID_SIZE - 1);
        skyState.chunkYProgress = MathUtils.clamp((-camera.position.z / AttractWorld.CHUNK_TILES), 0f, (float) AttractWorld.GRID_SIZE);

        Biome currentBiome = splinePath.getBiomeAt(flightTime);
        soundManager.update(currentPos, currentBiome);

        if (voidBackdrop != null) {
            voidBackdrop.applyFog(atmosphereManager.getFogColor());
            voidBackdrop.update(delta);
        }

        // Update active chunk streaming around camera
        updateActiveChunks();
    }

    private void updateActiveChunks() {
        int camChunkX = MathUtils.clamp((int) (camera.position.x / AttractWorld.CHUNK_TILES), 0, AttractWorld.GRID_SIZE - 1);
        int camChunkY = MathUtils.clamp((int) (-camera.position.z / AttractWorld.CHUNK_TILES), 0, AttractWorld.GRID_SIZE - 1);

        activeKeys.clear();
        int radius = 2; // Stream 5x5 chunk window around camera (25 chunks active)
        int minCX = Math.max(0, camChunkX - radius);
        int maxCX = Math.min(AttractWorld.GRID_SIZE - 1, camChunkX + radius);
        int minCY = Math.max(0, camChunkY - radius);
        int maxCY = Math.min(AttractWorld.GRID_SIZE - 1, camChunkY + radius);

        for (int cy = minCY; cy <= maxCY; cy++) {
            for (int cx = minCX; cx <= maxCX; cx++) {
                String key = "CHUNK_" + cx + "_" + cy;
                activeKeys.add(key);

                if (!cachedMeshes.containsKey(key)) {
                    buildChunkMesh(cx, cy, key);
                }
            }
        }

        // Evict meshes for chunks farther away than radius + 1
        Iterator<Map.Entry<String, List<ChunkSubMesh>>> it = cachedMeshes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, List<ChunkSubMesh>> entry = it.next();
            if (!activeKeys.contains(entry.getKey())) {
                for (ChunkSubMesh subMesh : entry.getValue()) {
                    subMesh.dispose();
                }
                it.remove();
            }
        }
    }

    private void buildChunkMesh(int cx, int cy, String key) {
        Maze maze = world.getChunk(cx, cy);
        if (maze == null) return;

        Texture wall = (maze.getBiome() == Biome.FOREST) ? forestWallTexture : wallTexture;
        Texture floor = floorTexture;
        if (maze.getBiome() == Biome.FOREST) floor = forestFloorTexture;
        else if (maze.getBiome() == Biome.DESERT) floor = desertFloorTexture;

        float offsetX = cx * AttractWorld.CHUNK_TILES;
        float offsetZ = -cy * AttractWorld.CHUNK_TILES;

        List<ChunkSubMesh> meshes = ChunkMeshBuilder.buildChunk(
                maze,
                0, 0, AttractWorld.CHUNK_TILES, AttractWorld.CHUNK_TILES,
                wall, floor, floor,
                false, offsetX, offsetZ
        );

        cachedMeshes.put(key, meshes);
    }

    public void render(Viewport viewport) {
        if (worldShader == null) return;

        // Apply viewport to camera
        camera.viewportWidth = viewport.getWorldWidth();
        camera.viewportHeight = viewport.getWorldHeight();
        camera.update();

        // 1. Dynamic 3D Skybox Pass (Horizon mountains, clouds, sun/moon, celestial dome)
        if (skyboxRenderer != null) {
            skyboxRenderer.renderDirect(viewport, skyState, Gdx.graphics.getDeltaTime());
        } else if (voidBackdrop != null) {
            voidBackdrop.clear();
        } else {
            Gdx.gl.glClearColor(VoidBackdrop.VOID_COLOR.r, VoidBackdrop.VOID_COLOR.g,
                    VoidBackdrop.VOID_COLOR.b, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        }

        // 2. Enable 3D Depth Test & Bind Terrain Shader
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);

        worldShader.bind();
        worldShader.setUniformMatrix("u_projViewTrans", camera.combined);
        worldShader.setUniformMatrix("u_worldTrans", identityMatrix);
        worldShader.setUniformf("u_cameraPos", camera.position);
        worldShader.setUniformi("u_retroMode", 0);
        worldShader.setUniformf("u_doomFactor", atmosphereManager.getDoomFactor());
        worldShader.setUniformf("u_fogEnabled", 1.0f);
        worldShader.setUniformf("u_fogDistance", atmosphereManager.getFogDistance());
        Color fogCol = atmosphereManager.getFogColor();
        worldShader.setUniformf("u_fogColor", fogCol.r, fogCol.g, fogCol.b);
        Color ambCol = atmosphereManager.getAmbientColor();
        worldShader.setUniformf("u_ambientColor", ambCol.r, ambCol.g, ambCol.b);
        Vector3 dirDir = atmosphereManager.getDirLightDir();
        worldShader.setUniformf("u_dirLightDir", dirDir.x, dirDir.y, dirDir.z);
        Color dirCol = atmosphereManager.getDirectionalLightColor();
        worldShader.setUniformf("u_dirLightColor", dirCol.r, dirCol.g, dirCol.b);
        worldShader.setUniformi("u_numLights", 0);

        // 2B. Void Floor Seal: Underlay dark abyss plane beneath active chunks to prevent untextured void gaps
        renderFloorSeal();

        // 2C. Render Active Terrain Chunk Submeshes
        for (String key : activeKeys) {
            List<ChunkSubMesh> list = cachedMeshes.get(key);
            if (list != null) {
                for (ChunkSubMesh subMesh : list) {
                    subMesh.render(worldShader);
                }
            }
        }

        // 3. Render Translucent Water & Liquid Quads
        renderLiquids();

        // 4. Render Scenic Living War Actors (Soldiers, monsters, camps, smoke, barricades, loot)
        renderActors();

        // 5. Render Projectile Volleys & Parabolic Arrows
        renderProjectiles();

        // 6. Render Midnight Thunderstorm Rain Streaks
        renderRain();

        // 7. Embers in the dark, additive and occluded by the maze
        if (voidBackdrop != null) {
            voidBackdrop.render(camera, worldShader);
        }

        // Restore identity world matrix
        worldShader.setUniformMatrix("u_worldTrans", identityMatrix);
    }

    private void renderFloorSeal() {
        if (floorSealBatcher == null || blankTexture == null) return;

        int camChunkX = MathUtils.clamp((int) (camera.position.x / AttractWorld.CHUNK_TILES), 0, AttractWorld.GRID_SIZE - 1);
        int camChunkY = MathUtils.clamp((int) (-camera.position.z / AttractWorld.CHUNK_TILES), 0, AttractWorld.GRID_SIZE - 1);

        int radius = 2;
        int minCX = Math.max(0, camChunkX - radius);
        int maxCX = Math.min(AttractWorld.GRID_SIZE - 1, camChunkX + radius);
        int minCY = Math.max(0, camChunkY - radius);
        int maxCY = Math.min(AttractWorld.GRID_SIZE - 1, camChunkY + radius);

        for (int cy = minCY; cy <= maxCY; cy++) {
            for (int cx = minCX; cx <= maxCX; cx++) {
                float chunkMinX = cx * AttractWorld.CHUNK_TILES;
                float chunkCenterZ = -cy * AttractWorld.CHUNK_TILES - AttractWorld.CHUNK_TILES * 0.5f;
                float chunkCenterX = chunkMinX + AttractWorld.CHUNK_TILES * 0.5f;
                floorSealBatcher.addFloorQuad(
                        chunkCenterX, -0.15f, chunkCenterZ,
                        AttractWorld.CHUNK_TILES * 0.5f, AttractWorld.CHUNK_TILES * 0.5f,
                        0f, 0f, 1f, 1f,
                        VoidBackdrop.VOID_COLOR
                );
            }
        }
        floorSealBatcher.flush(worldShader, blankTexture);
    }

    private void renderLiquids() {
        if (liquidBatcher == null || blankTexture == null) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        int camChunkX = MathUtils.clamp((int) (camera.position.x / AttractWorld.CHUNK_TILES), 0, AttractWorld.GRID_SIZE - 1);
        int camChunkY = MathUtils.clamp((int) (-camera.position.z / AttractWorld.CHUNK_TILES), 0, AttractWorld.GRID_SIZE - 1);

        int radius = 1;
        int minCX = Math.max(0, camChunkX - radius);
        int maxCX = Math.min(AttractWorld.GRID_SIZE - 1, camChunkX + radius);
        int minCY = Math.max(0, camChunkY - radius);
        int maxCY = Math.min(AttractWorld.GRID_SIZE - 1, camChunkY + radius);

        Color waterColor = new Color(0.25f, 0.45f, 0.70f, 0.75f);
        boolean any = false;

        for (int cy = minCY; cy <= maxCY; cy++) {
            for (int cx = minCX; cx <= maxCX; cx++) {
                Maze maze = world.getChunk(cx, cy);
                if (maze == null || maze.getLiquidManager() == null) continue;

                float offsetX = cx * AttractWorld.CHUNK_TILES;
                float offsetZ = -cy * AttractWorld.CHUNK_TILES;

                for (int y = 0; y < AttractWorld.CHUNK_TILES; y++) {
                    for (int x = 0; x < AttractWorld.CHUNK_TILES; x++) {
                        if (maze.getLiquidAt(x, y) == LiquidType.WATER) {
                            float wx = offsetX + x + 0.5f;
                            float wz = offsetZ - (y + 0.5f);
                            liquidBatcher.addFloorQuad(wx, 0.05f, wz, 0.5f, 0.5f, 0f, 0f, 1f, 1f, waterColor);
                            any = true;
                        }
                    }
                }
            }
        }

        if (any) {
            liquidBatcher.flush(worldShader, blankTexture);
        }
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void renderActors() {
        if (actorBatcher == null || warDirector == null) return;

        List<AttractActor> visible = warDirector.getActorsNear(camera.position, 45f);
        if (visible.isEmpty()) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // 1. Campfires & Torches
        if (campfireTexture != null) {
            TextureRegion campRegion = new TextureRegion(campfireTexture);
            for (AttractActor a : visible) {
                if (a.getType() == AttractActor.ActorType.CAMPFIRE) {
                    actorBatcher.addBillboard(
                            a.getPosition().x, a.getPosition().y, a.getPosition().z,
                            a.getScale().x, a.getScale().y,
                            campRegion, Color.WHITE, camRight, camUp, camDir
                    );
                    // Flickering fire core
                    float flk = 0.85f + (float) Math.sin(flightTime * 14f + a.getId()) * 0.15f;
                    scratchColor.set(1f, 0.65f * flk, 0.2f * flk, 0.9f);
                    actorBatcher.addBillboard(
                            a.getPosition().x, a.getPosition().y + a.getScale().y * 0.45f, a.getPosition().z,
                            a.getScale().x * 0.65f * flk, a.getScale().y * 0.75f * flk,
                            campRegion, scratchColor, camRight, camUp, camDir
                    );
                } else if (a.getType() == AttractActor.ActorType.TORCH) {
                    float flk = 0.85f + (float) Math.sin(flightTime * 12f + a.getId()) * 0.15f;
                    scratchColor.set(1f, 0.7f * flk, 0.25f * flk, 0.95f);
                    actorBatcher.addBillboard(
                            a.getPosition().x, a.getPosition().y, a.getPosition().z,
                            a.getScale().x, a.getScale().y,
                            campRegion, scratchColor, camRight, camUp, camDir
                    );
                }
            }
            actorBatcher.flush(worldShader, campfireTexture);
        }

        // 2. War Camp Tents
        if (tentTexture != null) {
            TextureRegion tentRegion = new TextureRegion(tentTexture);
            for (AttractActor a : visible) {
                if (a.getType() == AttractActor.ActorType.TENT) {
                    actorBatcher.addBillboard(
                            a.getPosition().x, a.getPosition().y, a.getPosition().z,
                            a.getScale().x, a.getScale().y,
                            tentRegion, a.getTint(), camRight, camUp, camDir
                    );
                }
            }
            actorBatcher.flush(worldShader, tentTexture);
        }

        // 3. Loot Chests
        if (chestTexture != null) {
            TextureRegion chestRegion = new TextureRegion(chestTexture);
            for (AttractActor a : visible) {
                if (a.getType() == AttractActor.ActorType.LOOT_CHEST) {
                    actorBatcher.addBillboard(
                            a.getPosition().x, a.getPosition().y, a.getPosition().z,
                            a.getScale().x, a.getScale().y,
                            chestRegion, Color.WHITE, camRight, camUp, camDir
                    );
                }
            }
            actorBatcher.flush(worldShader, chestTexture);
        }

        // 4. Barricades & Shield Walls
        if (barricadeTexture != null) {
            TextureRegion barRegion = new TextureRegion(barricadeTexture);
            for (AttractActor a : visible) {
                if (a.getType() == AttractActor.ActorType.BARRICADE) {
                    actorBatcher.addBillboard(
                            a.getPosition().x, a.getPosition().y, a.getPosition().z,
                            a.getScale().x, a.getScale().y,
                            barRegion, a.getTint(), camRight, camUp, camDir
                    );
                }
            }
            actorBatcher.flush(worldShader, barricadeTexture);
        }

        // 5. Discarded Ground Shields
        if (shieldTexture != null) {
            TextureRegion shRegion = new TextureRegion(shieldTexture);
            for (AttractActor a : visible) {
                if (a.getType() == AttractActor.ActorType.DISCARDED_SHIELD) {
                    actorBatcher.addFloorQuad(
                            a.getPosition().x, a.getPosition().y + 0.04f, a.getPosition().z,
                            a.getScale().x * 0.5f, a.getScale().y * 0.5f,
                            shRegion, a.getTint()
                    );
                }
            }
            actorBatcher.flush(worldShader, shieldTexture);
        }

        // 6. Volumetric Vertical Smoke Pillars
        if (smokeTexture != null) {
            TextureRegion smRegion = new TextureRegion(smokeTexture);
            for (AttractActor a : visible) {
                if (a.getType() == AttractActor.ActorType.SMOKE_PLUME) {
                    // 5 vertically stacked puffs drifting with wind
                    for (int p = 0; p < 5; p++) {
                        float h = (p + 1) * (a.getScale().y / 5f);
                        float puffW = a.getScale().x * (1f + p * 0.45f);
                        float driftX = (float) Math.sin(flightTime * 0.8f + p * 0.6f + a.getId()) * 0.25f * (p + 1);
                        float driftZ = (float) Math.cos(flightTime * 0.6f + p * 0.5f + a.getId()) * 0.15f * (p + 1);
                        float alpha = Math.max(0.04f, 0.50f - (p * 0.08f));
                        scratchColor.set(a.getTint().r, a.getTint().g, a.getTint().b, alpha);
                        actorBatcher.addBillboard(
                                a.getPosition().x + driftX, a.getPosition().y + h, a.getPosition().z + driftZ,
                                puffW, puffW, smRegion, scratchColor, camRight, camUp, camDir
                        );
                    }
                }
            }
            actorBatcher.flush(worldShader, smokeTexture);
        }

        // 7. Living Soldiers, Megabeasts, and Wilderness Predators
        for (AttractActor a : visible) {
            if (a.getSpriteId() != null && spriteFrames.containsKey(a.getSpriteId())) {
                TextureRegion[] frames = spriteFrames.get(a.getSpriteId());
                if (frames != null && frames.length > 0) {
                    TextureRegion frame = frames[a.getAnimFrame() % frames.length];
                    if (a.getHitFlash() < 1f) {
                        scratchColor.set(1f, a.getHitFlash(), a.getHitFlash(), 1f);
                    } else {
                        scratchColor.set(a.getTint());
                    }

                    actorBatcher.addBillboard(
                            a.getPosition().x, a.getPosition().y, a.getPosition().z,
                            a.getScale().x, a.getScale().y,
                            frame, scratchColor, camRight, camUp, camDir
                    );
                    actorBatcher.flush(worldShader, frame.getTexture());

                    // Faction Tabards Overlay on Torso
                    if (a.getHouseId() != null && tabardTextures.containsKey(a.getHouseId())) {
                        Texture tabTex = tabardTextures.get(a.getHouseId());
                        actorBatcher.addBillboard(
                                a.getPosition().x, a.getPosition().y + a.getScale().y * 0.35f, a.getPosition().z,
                                a.getScale().x * 0.55f, a.getScale().y * 0.55f,
                                new TextureRegion(tabTex), Color.WHITE, camRight, camUp, camDir
                        );
                        actorBatcher.flush(worldShader, tabTex);
                    }

                    // Soldier Shield in Off-Hand
                    if (a.getType() == AttractActor.ActorType.SOLDIER_SHIELD && shieldTexture != null) {
                        actorBatcher.addBillboard(
                                a.getPosition().x + 0.28f, a.getPosition().y + a.getScale().y * 0.30f, a.getPosition().z,
                                0.45f, 0.60f,
                                new TextureRegion(shieldTexture), Color.WHITE, camRight, camUp, camDir
                        );
                        actorBatcher.flush(worldShader, shieldTexture);
                    }
                }
            }
        }

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void renderProjectiles() {
        if (vfxBatcher == null || projectileManager == null || blankTexture == null) return;

        List<AttractProjectileManager.AttractArrow> active = projectileManager.getActiveArrows();
        List<AttractProjectileManager.AttractArrow> stuck = projectileManager.getStuckArrows();
        if (active.isEmpty() && stuck.isEmpty()) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Active Airborne Arrows
        for (int i = 0; i < active.size(); i++) {
            AttractProjectileManager.AttractArrow a = active.get(i);
            Vector3 cur = a.getCurrentPos();
            Vector3 dir = a.getDir();

            float hx = cur.x + dir.x * 0.45f;
            float hy = cur.y + dir.y * 0.45f;
            float hz = cur.z + dir.z * 0.45f;
            float tx = cur.x - dir.x * 0.45f;
            float ty = cur.y - dir.y * 0.45f;
            float tz = cur.z - dir.z * 0.45f;

            Color col = a.isFlaming() ? Color.ORANGE : new Color(0.85f, 0.75f, 0.55f, 0.95f);
            vfxBatcher.addRainStreak(hx, hy, hz, tx, ty, tz, 0.045f, camera.position, camRight, col);
        }

        // Quivering Stuck Arrows
        for (int i = 0; i < stuck.size(); i++) {
            AttractProjectileManager.AttractArrow a = stuck.get(i);
            Vector3 cur = a.getCurrentPos();
            Vector3 dir = a.getDir();

            float hx = cur.x + dir.x * 0.45f;
            float hy = cur.y + Math.abs(dir.y) * 0.45f + 0.1f;
            float hz = cur.z + dir.z * 0.45f;

            Color col = a.isFlaming() ? Color.GOLD : new Color(0.6f, 0.5f, 0.4f, 0.85f);
            vfxBatcher.addRainStreak(hx, hy, hz, cur.x, cur.y, cur.z, 0.035f, camera.position, camRight, col);
        }

        vfxBatcher.flush(worldShader, blankTexture);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void renderRain() {
        if (vfxBatcher == null || atmosphereManager == null || blankTexture == null) return;
        if (!atmosphereManager.isPrecipitationActive()) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        float[] rain = atmosphereManager.getRainParticles();
        Color rainColor = new Color(0.65f, 0.75f, 0.90f, 0.35f);
        float fallSpeed = 0.55f;
        float windX = -0.06f;
        float windZ = -0.04f;

        for (int i = 0; i < AttractAtmosphereManager.NUM_RAIN_STREAKS; i++) {
            float rx = camera.position.x + rain[i * 4];
            float ry = camera.position.y + rain[i * 4 + 1];
            float rz = camera.position.z + rain[i * 4 + 2];
            float len = rain[i * 4 + 3];

            vfxBatcher.addRainStreak(
                    rx, ry, rz,
                    rx - windX * len, ry + fallSpeed * len, rz - windZ * len,
                    0.018f, camera.position, camRight, rainColor
            );
        }

        vfxBatcher.flush(worldShader, blankTexture);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Triggers the dramatic 0.8s camera acceleration dive towards the fortress gates
     * before executing the expedition launch callback.
     */
    public void startExpeditionDive(Runnable onComplete) {
        diveStartPos.set(camera.position);
        soundManager.playGateDive();
        soundManager.playWarHorn();
        controller.startDive(onComplete);
    }

    public AttractController getController() {
        return controller;
    }

    public PerspectiveCamera getCamera() {
        return camera;
    }

    public AttractWarDirector getWarDirector() {
        return warDirector;
    }

    public AttractProjectileManager getProjectileManager() {
        return projectileManager;
    }

    public AttractAtmosphereManager getAtmosphereManager() {
        return atmosphereManager;
    }

    public Skybox3DRenderer getSkyboxRenderer() {
        return skyboxRenderer;
    }

    public DayNightManager getDayNightManager() {
        return dayNightManager;
    }

    @Override
    public void dispose() {
        if (worldShader != null) {
            worldShader.dispose();
            worldShader = null;
        }
        if (wallTexture != null) {
            wallTexture.dispose();
            wallTexture = null;
        }
        if (forestWallTexture != null && forestWallTexture != wallTexture) {
            forestWallTexture.dispose();
            forestWallTexture = null;
        }
        if (floorTexture != null) {
            floorTexture.dispose();
            floorTexture = null;
        }
        if (forestFloorTexture != null && forestFloorTexture != floorTexture) {
            forestFloorTexture.dispose();
            forestFloorTexture = null;
        }
        if (desertFloorTexture != null && desertFloorTexture != floorTexture) {
            desertFloorTexture.dispose();
            desertFloorTexture = null;
        }
        if (blankTexture != null) {
            blankTexture.dispose();
            blankTexture = null;
        }
        if (liquidBatcher != null) {
            liquidBatcher.dispose();
            liquidBatcher = null;
        }
        if (actorBatcher != null) {
            actorBatcher.dispose();
            actorBatcher = null;
        }
        if (vfxBatcher != null) {
            vfxBatcher.dispose();
            vfxBatcher = null;
        }
        if (floorSealBatcher != null) {
            floorSealBatcher.dispose();
            floorSealBatcher = null;
        }
        if (smokeTexture != null) {
            smokeTexture.dispose();
            smokeTexture = null;
        }
        if (soundManager != null) {
            soundManager.dispose();
        }
        if (voidBackdrop != null) {
            voidBackdrop.dispose();
            voidBackdrop = null;
        }
        if (skyboxRenderer != null) {
            skyboxRenderer.dispose();
            skyboxRenderer = null;
        }

        for (Texture t : loadedTextures.values()) {
            if (t != null) t.dispose();
        }
        loadedTextures.clear();
        tabardTextures.clear();
        spriteFrames.clear();

        for (List<ChunkSubMesh> list : cachedMeshes.values()) {
            for (ChunkSubMesh subMesh : list) {
                subMesh.dispose();
            }
        }
        cachedMeshes.clear();
    }
}

