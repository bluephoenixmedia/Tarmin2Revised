package com.bpm.minotaur.rendering.attract;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.rendering.Skybox3DRenderer;
import com.bpm.minotaur.rendering.mesh.ChunkMeshBuilder;
import com.bpm.minotaur.rendering.mesh.ChunkSubMesh;
import com.bpm.minotaur.rendering.mesh.DynamicQuadBatcher;

import java.util.*;

/**
 * High-fidelity 3D renderer for the Main Menu Attract Mode flyover.
 * Manages active chunk trajectory streaming over the 100-chunk world,
 * 3D skybox dome, multi-tier citadel & high spire models, water bodies,
 * and cinematic camera choreography.
 */
public class AttractModeRenderer implements Disposable {

    private static final String TAG = "AttractModeRenderer";

    private final AttractSplinePath splinePath;
    private final AttractWorld world;
    private final AttractController controller;
    private final AttractSoundManager soundManager;

    private final PerspectiveCamera camera;
    private float flightTime = 0f;

    // Dive Transition Vectors
    private final Vector3 diveStartPos = new Vector3();
    private final Vector3 currentPos = new Vector3();
    private final Vector3 currentLookAt = new Vector3();

    // 3D Shader & Uniforms
    private ShaderProgram worldShader;
    private final Matrix4 identityMatrix = new Matrix4();
    private final Color currentFogColor = new Color(0.2f, 0.2f, 0.3f, 1f);
    private final float[] currentFogDist = new float[]{70f};

    // Terrain Textures
    private Texture wallTexture;
    private Texture forestWallTexture;
    private Texture floorTexture;
    private Texture forestFloorTexture;
    private Texture desertFloorTexture;
    private Texture blankTexture;

    // Liquid Batching
    private DynamicQuadBatcher liquidBatcher;

    // Active Chunk Mesh Cache
    private final Map<String, List<ChunkSubMesh>> cachedMeshes = new HashMap<>();
    private final Set<String> activeKeys = new HashSet<>();

    // 3D Skybox
    private Skybox3DRenderer skyboxRenderer;
    private final Skybox3DRenderer.SkyState skyState = new Skybox3DRenderer.SkyState();

    public AttractModeRenderer() {
        this.splinePath = new AttractSplinePath();
        this.world = new AttractWorld();
        this.controller = new AttractController();
        this.soundManager = new AttractSoundManager();

        this.camera = new PerspectiveCamera(65f, 1920f, 1080f);
        this.camera.near = 0.1f;
        this.camera.far = 400f;

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
                Gdx.app.error(TAG, "Shader compile failed: " + worldShader.getLog());
            }

            // Load and configure repeating textures
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

            skyboxRenderer = new Skybox3DRenderer();
        } catch (Throwable t) {
            Gdx.app.error(TAG, "Initialization error in AttractModeRenderer: " + t.getMessage(), t);
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

        Biome currentBiome = splinePath.getBiomeAt(flightTime);
        splinePath.getAtmosphere(flightTime, currentFogColor, currentFogDist);
        soundManager.update(currentPos, currentBiome);

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

        // 1. Render Skybox Dome Pass
        if (skyboxRenderer != null && skyboxRenderer.isInitialized()) {
            skyState.camX = camera.position.x * 0.05f;
            skyState.camZ = camera.position.z * 0.05f;
            skyState.forwardX = camera.direction.x;
            skyState.forwardZ = camera.direction.z;
            skyState.cloudCover = 0.35f;
            skyboxRenderer.renderDirect(viewport, skyState, Gdx.graphics.getDeltaTime());
            Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        } else {
            Gdx.gl.glClearColor(currentFogColor.r, currentFogColor.g, currentFogColor.b, 1f);
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
        worldShader.setUniformf("u_doomFactor", 0.0f);
        worldShader.setUniformf("u_fogEnabled", 1.0f);
        worldShader.setUniformf("u_fogDistance", currentFogDist[0]);
        worldShader.setUniformf("u_fogColor", currentFogColor.r, currentFogColor.g, currentFogColor.b);
        worldShader.setUniformf("u_ambientColor", 0.45f, 0.42f, 0.48f);
        worldShader.setUniformf("u_dirLightDir", -0.4f, -0.8f, -0.4f);
        worldShader.setUniformf("u_dirLightColor", 0.65f, 0.62f, 0.58f);
        worldShader.setUniformi("u_numLights", 0);

        // Render Active Terrain Chunk Submeshes
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

    /**
     * Triggers the dramatic 0.8s camera acceleration dive towards the fortress gates
     * before executing the expedition launch callback.
     */
    public void startExpeditionDive(Runnable onComplete) {
        diveStartPos.set(camera.position);
        soundManager.playGateDive();
        controller.startDive(onComplete);
    }

    public AttractController getController() {
        return controller;
    }

    public PerspectiveCamera getCamera() {
        return camera;
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
        if (soundManager != null) {
            soundManager.dispose();
        }

        for (List<ChunkSubMesh> list : cachedMeshes.values()) {
            for (ChunkSubMesh subMesh : list) {
                subMesh.dispose();
            }
        }
        cachedMeshes.clear();
    }
}
