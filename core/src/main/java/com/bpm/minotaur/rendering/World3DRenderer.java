package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.Ladder;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Renderable;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.gore.BloodParticle;
import com.bpm.minotaur.gamedata.gore.Gib;
import com.bpm.minotaur.gamedata.gore.GoreManager;
import com.bpm.minotaur.gamedata.gore.SurfaceDecal;
import com.bpm.minotaur.gamedata.gore.WallDecal;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.lighting.LightSource;
import com.bpm.minotaur.lighting.LightingManager;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.DayNightManager;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.managers.DoomManager;
import com.bpm.minotaur.managers.WorldManager;
import com.bpm.minotaur.weather.WeatherManager;
import com.bpm.minotaur.rendering.mesh.ChunkMeshBuilder;
import com.bpm.minotaur.rendering.mesh.ChunkSubMesh;
import com.bpm.minotaur.rendering.mesh.DynamicQuadBatcher;
import com.bpm.minotaur.rendering.mesh.WorldMeshCache;
import com.bpm.minotaur.weather.WeatherManager;
import com.bpm.minotaur.weather.WeatherRenderer;
import com.bpm.minotaur.weather.WeatherType;

import java.util.ArrayList;
import java.util.List;

/**
 * Full 3D Planar World Rendering Pipeline.
 * Renders the maze world using true 3D planar geometry, LibGDX PerspectiveCamera,
 * OpenGL hardware depth buffer (GL_DEPTH_TEST), chunk sub-meshes, 3D billboards,
 * and a 16-point-light GLSL shader.
 */
public class World3DRenderer implements Disposable {

    private static final String TAG = "World3DRenderer";
    private static final int MAX_LIGHTS = 16;
    private static final float GATE_DOOR_HINGE_X = 0.28f;

    private final PerspectiveCamera camera;
    private final ShaderProgram shader;
    private final WorldMeshCache meshCache;
    private final DynamicQuadBatcher dynamicBatcher;

    // Textures
    private final Texture wallTexture;
    private final Texture doorTexture;
    private final Texture gateTexture;
    private final Texture floorTexture;
    private final Texture ceilingTexture;
    private final Texture blankTexture;
    private final Texture ladderDownTexture;
    private final Texture ladderUpTexture;

    // 3D Skullgate Models & Assets
    private Model gateFrameModel;
    private Model gateLeftDoorModel;
    private Model gateRightDoorModel;
    private Texture gateDiffuseTexture;
    private final Matrix4 gateTransform = new Matrix4();

    // Optional 3D skybox integration
    private Skybox3DRenderer skybox3DRenderer;

    // 3D Weather & Precipitation Renderer
    private WeatherRenderer weatherRenderer;

    // Reusable scratch vectors & arrays for camera math and lights
    private final Vector3 camRight = new Vector3();
    private final Vector3 camUp = new Vector3();
    private final Vector3 camDir = new Vector3();
    private final Matrix4 identityMatrix = new Matrix4();

    private final float[] lightPosArray = new float[MAX_LIGHTS * 3];
    private final float[] lightColorArray = new float[MAX_LIGHTS * 3];
    private final float[] lightRadiusArray = new float[MAX_LIGHTS];
    private final float[] lightIntensityArray = new float[MAX_LIGHTS];

    public static final float DEFAULT_FOV = DebugManager.DEFAULT_FOV_3D;
    private float totalTime = 0f;

    // Smooth eye adaptation and celestial lighting state
    private final Color currentAmbientColor = new Color(LightingManager.COLOR_SHELTER_AMBIENT);
    private final Color targetAmbientColor = new Color();
    private final Color displayAmbient = new Color();
    private final Vector3 currentDirLightDir = new Vector3(0.3f, 0.8f, 0.4f).nor();
    private final Vector3 targetDirLightDir = new Vector3(0.3f, 0.8f, 0.4f).nor();
    private final Color currentDirLightColor = new Color(0f, 0f, 0f, 1f);
    private final Color targetDirLightColor = new Color(0f, 0f, 0f, 1f);
    private final Color scratchColor = new Color();
    private final Color overcastTint = new Color(0.68f, 0.74f, 0.84f, 1.0f);

    public World3DRenderer() {
        this.camera = new PerspectiveCamera(DebugManager.getInstance().getFov3d(), 1920f, 1080f);
        this.camera.near = 0.05f;
        this.camera.far = 300f;

        // Compile 3D Shader
        ShaderProgram.pedantic = false;
        this.shader = new ShaderProgram(
                Gdx.files.internal("shaders/world3d.vert"),
                Gdx.files.internal("shaders/world3d.frag")
        );
        if (!shader.isCompiled()) {
            Gdx.app.error(TAG, "Failed to compile world3d shader: " + shader.getLog());
        }

        // Initialize Textures with hardware repeat
        this.wallTexture = new Texture(Gdx.files.internal("images/wall.png"));
        this.wallTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        this.doorTexture = new Texture(Gdx.files.internal("images/door.png"));
        this.gateTexture = new Texture(Gdx.files.internal("images/gate.png"));

        this.floorTexture = new Texture(Gdx.files.internal("images/floor.png"));
        this.floorTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        this.ceilingTexture = new Texture(Gdx.files.internal("images/floor.png"));
        this.ceilingTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pix.setColor(Color.WHITE);
        pix.fill();
        this.blankTexture = new Texture(pix);
        pix.dispose();

        this.ladderDownTexture = new Texture(Gdx.files.internal("images/items/ladder.png"));
        this.ladderUpTexture = new Texture(Gdx.files.internal("images/items/ladder_up.png"));

        this.meshCache = new WorldMeshCache();
        this.dynamicBatcher = new DynamicQuadBatcher();

        // Load 3D Skullgate Assets
        ObjLoader objLoader = new ObjLoader();
        try {
            if (Gdx.files.internal("models/gate/gate_frame.obj").exists()) {
                this.gateFrameModel = objLoader.loadModel(Gdx.files.internal("models/gate/gate_frame.obj"));
            }
            if (Gdx.files.internal("models/gate/gate_door_left.obj").exists()) {
                this.gateLeftDoorModel = objLoader.loadModel(Gdx.files.internal("models/gate/gate_door_left.obj"));
            }
            if (Gdx.files.internal("models/gate/gate_door_right.obj").exists()) {
                this.gateRightDoorModel = objLoader.loadModel(Gdx.files.internal("models/gate/gate_door_right.obj"));
            }
            if (Gdx.files.internal("models/gate/gate_diffuse.png").exists()) {
                this.gateDiffuseTexture = new Texture(Gdx.files.internal("models/gate/gate_diffuse.png"));
            }
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to load 3D Skullgate assets", e);
        }

        try {
            this.skybox3DRenderer = new Skybox3DRenderer();
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to initialize Skybox3DRenderer: " + e.getMessage());
        }
    }

    public PerspectiveCamera getCamera() {
        return camera;
    }

    /**
     * Main 3D render method called from GameScreen.
     */
    public void render(
            Player player,
            Maze maze,
            Viewport viewport,
            WorldManager worldManager,
            int currentLevel,
            GameMode gameMode,
            CombatManager combatManager
    ) {
        if (player == null || maze == null || !shader.isCompiled()) return;

        float delta = Gdx.graphics.getDeltaTime();
        totalTime += delta;

        // Synchronize player lighting
        LightingManager lm = (worldManager != null) ? worldManager.getLightingManager() : null;
        if (lm != null) {
            lm.update(delta, player, maze);
        }

        // Determine indoor / shelter state
        boolean isInsideHome = maze.isHomeTile((int) player.getPosition().x, (int) player.getPosition().y);
        boolean isIndoors = (currentLevel > 1) || isInsideHome || maze.isIndoors((int) player.getPosition().x, (int) player.getPosition().y);

        DayNightManager dnm = (worldManager != null) ? worldManager.getDayNightManager() : null;
        com.bpm.minotaur.weather.WeatherManager wm = (worldManager != null) ? worldManager.getWeatherManager() : null;

        // Determine Atmosphere, Fog, and Doom
        Biome biome = (worldManager != null && worldManager.getBiomeManager() != null)
                ? worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId())
                : Biome.FOREST;

        float fogDistance = 1000f;
        Color fogColor = new Color(Color.WHITE);
        boolean fogEnabled = false;

        if (wm != null && currentLevel == 1) {
            fogEnabled = true;
            fogDistance = wm.getFogDistance();
            fogColor.set(wm.getFogColor());
            // Atmospheric fog harmonization with Day/Night cycle
            if (dnm != null) {
                fogColor.mul(dnm.getSkyTint());
            }
        } else if (gameMode == GameMode.ADVANCED && biome != null && biome.hasFogOfWar()) {
            fogEnabled = true;
            fogDistance = (biome == Biome.FOREST) ? 2.0f : biome.getFogDistance();
            fogColor.set(biome.getFogColor());
        }

        float bridgeIntegrity = DoomManager.getInstance().getBridgeIntegrity();
        float doomFactor = 1.0f - ((bridgeIntegrity / 100f) * 0.6f);
        if (bridgeIntegrity > 50) {
            fogColor.lerp(Color.RED, ((bridgeIntegrity - 50) / 50f) * 0.3f);
        }

        // --- UPDATE 3D CAMERA ---
        updateCamera(player, viewport, wm, isIndoors);

        // --- PASS 0: 3D SKYBOX & HORIZON LANDMARKS (Outdoors Level 1) ---
        boolean canRender3DSky = (currentLevel == 1) && (!isIndoors || isInsideHome);
        if (canRender3DSky && skybox3DRenderer != null && skybox3DRenderer.isInitialized()) {
            skybox3DRenderer.render(null, player, viewport, worldManager, DebugManager.getInstance().getRenderMode());
            // Clear depth buffer so the skybox & horizon landmarks remain purely background
            // and all maze geometry (walls, floors, ceilings, doors) renders OVER the skybox.
            Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        } else {
            // In dungeons or indoors without skybox, clear color & depth cleanly
            Color clearCol = isIndoors ? new Color(0.04f, 0.04f, 0.06f, 1f) : fogColor;
            Gdx.gl.glClearColor(clearCol.r, clearCol.g, clearCol.b, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        }

        // --- BIND 3D SHADER & GLOBAL UNIFORMS ---
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);

        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);
        shader.setUniformMatrix("u_worldTrans", identityMatrix);
        shader.setUniformf("u_cameraPos", camera.position);

        boolean isRetro = (DebugManager.getInstance().getRenderMode() == DebugManager.RenderMode.RETRO);
        shader.setUniformi("u_retroMode", isRetro ? 1 : 0);
        shader.setUniformf("u_doomFactor", doomFactor);

        // --- AMBIENT & CELESTIAL LIGHT TARGET COMPUTATION ---
        if (isInsideHome) {
            // Shelter Haven: warm hearth/lamp sanctuary ambient
            targetAmbientColor.set(LightingManager.COLOR_SHELTER_AMBIENT);
            targetDirLightColor.set(0f, 0f, 0f, 1f); // Roof blocks direct sun/moon
        } else if (currentLevel > 1 || (isIndoors && !isInsideHome)) {
            // Dungeon / Underground Void
            targetAmbientColor.set(LightingManager.COLOR_COLD_VOID);
            targetDirLightColor.set(0f, 0f, 0f, 1f);
        } else {
            // Level 1 Outdoors: dynamically calibrated from Day/Night cycle and Weather
            float dayAmbient = (dnm != null) ? dnm.getAmbientLight() : 0.60f;
            Color skyTint = (dnm != null) ? dnm.getSkyTint() : Color.WHITE;
            float weatherDim = (wm != null) ? wm.getGlobalLightDimmer() : 1.0f;

            // During overcast storms/rain, ambient light takes on a cool slate-blue tint
            float overcastFactor = (wm != null && wm.isPrecipitation()) ? 0.65f : 0.0f;
            targetAmbientColor.set(skyTint).lerp(overcastTint, overcastFactor);

            // Calibrated outdoor ambient intensity (soft, moody, never bleached):
            // - Night storm: ~0.10
            // - Morning storm (08:00): ~0.26 - 0.30
            // - Midday storm: ~0.35 - 0.38
            // - Clear midday: ~0.65 - 0.75
            float outdoorAmbientIntensity = MathUtils.clamp(dayAmbient * weatherDim * 0.72f, 0.08f, 0.75f);
            targetAmbientColor.mul(outdoorAmbientIntensity);

            // Directional Celestial Light (Sun in daytime, Moon at night)
            if (dnm != null) {
                float sunElevation = MathUtils.sin((dnm.getTimeOfDay() - 0.25f) * 2.0f * MathUtils.PI);
                if (sunElevation > 0.0f) {
                    dnm.getSunDirection(targetDirLightDir);
                    Color sunColor = dnm.getDirectionalLightColor(scratchColor);
                    // Clouds diffuse sunlight during storm, keeping directional light soft
                    float sunIntensity = MathUtils.clamp(sunElevation, 0.15f, 1.0f) * (wm != null && wm.isStormy() ? 0.18f : 0.50f);
                    targetDirLightColor.set(sunColor).mul(sunIntensity);
                } else {
                    dnm.getMoonDirection(targetDirLightDir);
                    float moonIntensity = (wm != null && wm.isStormy() ? 0.06f : 0.18f);
                    targetDirLightColor.set(0.35f, 0.45f, 0.65f, 1.0f).mul(moonIntensity);
                }
            } else {
                targetDirLightDir.set(0.3f, 0.8f, 0.4f).nor();
                targetDirLightColor.set(0.2f, 0.2f, 0.2f, 1.0f);
            }
        }

        // --- SMOOTH EYE ADAPTATION ---
        float adaptSpeed = 3.5f; // ~0.5s smooth transition between environments
        currentAmbientColor.lerp(targetAmbientColor, Math.min(1.0f, delta * adaptSpeed));
        currentDirLightColor.lerp(targetDirLightColor, Math.min(1.0f, delta * adaptSpeed));
        currentDirLightDir.lerp(targetDirLightDir, Math.min(1.0f, delta * adaptSpeed)).nor();

        // Lightning flash surge
        displayAmbient.set(currentAmbientColor);
        if (currentLevel == 1 && wm != null) {
            float flash = wm.getFlashIntensity();
            if (flash > 0.05f) {
                if (isInsideHome) {
                    displayAmbient.lerp(Color.WHITE, flash * 0.45f);
                } else if (!isIndoors) {
                    displayAmbient.lerp(Color.WHITE, flash * 0.90f);
                }
            }
        }

        shader.setUniformf("u_ambientColor", displayAmbient.r, displayAmbient.g, displayAmbient.b);
        shader.setUniformf("u_dirLightDir", currentDirLightDir);
        shader.setUniformf("u_dirLightColor", currentDirLightColor.r, currentDirLightColor.g, currentDirLightColor.b);

        // Fog
        shader.setUniformf("u_fogEnabled", fogEnabled ? 1.0f : 0.0f);
        shader.setUniformf("u_fogDistance", fogDistance);
        shader.setUniformf("u_fogColor", fogColor.r, fogColor.g, fogColor.b);

        // Dynamic surface weather modulation
        float wetness = (wm != null && currentLevel == 1) ? wm.getWetness() : 0.0f;
        float snowAccum = (wm != null && currentLevel == 1) ? wm.getSnowAccumulation() : 0.0f;
        shader.setUniformf("u_wetness", wetness);
        shader.setUniformf("u_snowAccumulation", snowAccum);

        // Setup dynamic point lights (including opening gate cyan emissive pulses)
        setupDynamicLights(lm, player, maze);

        // --- PASS 1: OPAQUE CHUNK SUB-MESHES & DYNAMIC SLIDING DOORS ---
        shader.setUniformf("u_alphaCutoff", 0.0f);

        RetroTheme.Theme theme = maze.getTheme();
        if (theme == null) theme = RetroTheme.STANDARD_THEME;

        List<ChunkSubMesh> subMeshes = meshCache.getVisibleSubMeshes(
                maze,
                player.getPosition().x,
                player.getPosition().y,
                currentLevel,
                isIndoors,
                wallTexture,
                floorTexture,
                ceilingTexture,
                worldManager
        );

        for (ChunkSubMesh subMesh : subMeshes) {
            if (isRetro) {
                shader.setUniformf("u_retroBorder", 1.0f);
                if (subMesh.getTexture() == wallTexture) {
                    shader.setUniformf("u_retroColor", theme.wall);
                } else if (subMesh.getTexture() == floorTexture) {
                    shader.setUniformf("u_retroColor", theme.floor);
                } else {
                    shader.setUniformf("u_retroColor", theme.ceiling);
                }
            } else {
                shader.setUniformf("u_retroBorder", 0.0f);
            }
            subMesh.render(shader);
        }

        // Dynamic Doors & Gates
        renderDynamicDoors(maze, player, isRetro, theme);

        // --- PASS 2: ALPHA CUTOUT & BLENDING (Entities, Decals, Floor Corpses) ---
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shader.setUniformf("u_alphaCutoff", 0.05f);
        shader.setUniformf("u_retroBorder", 0.0f);
        if (isRetro) {
            shader.setUniformf("u_retroColor", Color.WHITE);
        }

        // A. Gore System: Coplanar Wall Decals, Floor Decals, Particles, Gibs
        renderGore(maze, worldManager, isRetro, theme);

        // B. Entities: Monsters, Items, Ladders, Scenery
        renderEntities(maze, player, combatManager, isRetro, theme);

        // --- PASS 3: 3D PRECIPITATION & WEATHER PARTICLES ---
        if (currentLevel == 1 && wm != null && wm.isPrecipitation()) {
            if (this.weatherRenderer == null) {
                this.weatherRenderer = new WeatherRenderer(wm);
            }
            this.weatherRenderer.update(Gdx.graphics.getDeltaTime(), player, maze);

            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
            Gdx.gl.glDepthMask(false); // Depth-test against world geometry without writing to depth buffer

            shader.setUniformf("u_alphaCutoff", 0.0f);
            shader.setUniformf("u_retroBorder", 0.0f);
            shader.setUniformf("u_fogEnabled", 0.0f);
            shader.setUniformf("u_retroColor", 1.0f, 1.0f, 1.0f, 1.0f);

            this.weatherRenderer.render3D(dynamicBatcher, blankTexture, shader, camera, player, maze, wm, isRetro);

            Gdx.gl.glDepthMask(true);
        }

        // --- RESTORE OPENGL STATE ---
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    private void updateCamera(Player player, Viewport viewport, WeatherManager wm, boolean isIndoors) {
        float px = player.getPosition().x;
        float py = player.getPosition().y;
        float pz = 0.5f; // Eye height

        camera.position.set(px, pz, -py);

        // Direction: maze +X -> world +X (East), maze +Y -> world -Z (North)
        Vector2 dir = player.getDirectionVector();
        camDir.set(dir.x, 0f, -dir.y).nor();
        camera.direction.set(camDir);

        camera.up.set(0f, 1f, 0f);

        // Confused / Dizzy Effect Camera Roll
        if (player.getStatusManager().hasEffect(StatusEffectType.CONFUSED)) {
            float dizzyAngle = 2.0f * (float) Math.sin(totalTime * 2.0f);
            camera.up.rotate(camera.direction, dizzyAngle);
        }

        // Outdoor Tornado Gale-force Wind Vibration / Trauma
        if (wm != null && wm.getCurrentWeather() == WeatherType.TORNADO && !isIndoors) {
            float traumaAngle = (float) (Math.sin(totalTime * 18.0f) * 0.70f + Math.cos(totalTime * 27.0f) * 0.40f);
            camera.up.rotate(camera.direction, traumaAngle);
            camera.position.add(
                    (float) Math.sin(totalTime * 22.0f) * 0.015f,
                    (float) Math.cos(totalTime * 19.0f) * 0.010f,
                    (float) Math.sin(totalTime * 25.0f) * 0.015f
            );
        }

        camera.fieldOfView = DebugManager.getInstance().getFov3d();
        camera.viewportWidth = viewport.getWorldWidth();
        camera.viewportHeight = viewport.getWorldHeight();
        camera.update();

        // Calculate Right and Up unit vectors for billboards
        camRight.set(camera.direction).crs(camera.up).nor();
        camUp.set(camRight).crs(camera.direction).nor();
    }

    private void setupDynamicLights(LightingManager lm, Player player, Maze maze) {
        int count = 0;
        if (lm != null) {
            // Player Light at index 0
            LightSource pl = lm.getPlayerLight();
            if (pl != null) {
                lightPosArray[0] = pl.getPosition().x;
                lightPosArray[1] = 0.5f;
                lightPosArray[2] = -pl.getPosition().y;

                Color c = pl.getCurrentColor();
                lightColorArray[0] = c.r;
                lightColorArray[1] = c.g;
                lightColorArray[2] = c.b;

                lightRadiusArray[0] = pl.getCurrentRadius();
                lightIntensityArray[0] = pl.getCurrentIntensity();
                count = 1;
            }

            // Up to 15 nearby world lights
            Array<LightSource> worldLights = lm.getWorldLights();
            if (worldLights != null && worldLights.size > 0) {
                // Sort by distance to player
                worldLights.sort((a, b) -> Float.compare(
                        player.getPosition().dst2(a.getPosition()),
                        player.getPosition().dst2(b.getPosition())
                ));

                for (int i = 0; i < worldLights.size && count < MAX_LIGHTS; i++) {
                    LightSource wl = worldLights.get(i);
                    int idx = count * 3;
                    lightPosArray[idx]     = wl.getPosition().x;
                    lightPosArray[idx + 1] = 0.5f;
                    lightPosArray[idx + 2] = -wl.getPosition().y;

                    Color c = wl.getCurrentColor();
                    lightColorArray[idx]     = c.r;
                    lightColorArray[idx + 1] = c.g;
                    lightColorArray[idx + 2] = c.b;

                    lightRadiusArray[count]     = wl.getCurrentRadius();
                    lightIntensityArray[count] = wl.getCurrentIntensity();
                    count++;
                }
            }
        }

        // Also inject dynamic gate emissive lights if any gate nearby is OPEN or OPENING
        if (maze != null && maze.getGates() != null) {
            for (Gate gate : maze.getGates().values()) {
                if (count >= MAX_LIGHTS) break;
                if (gate.getState() == Gate.GateState.OPENING || gate.getState() == Gate.GateState.OPEN) {
                    float dist2 = player.getPosition().dst2(gate.getPosition());
                    if (dist2 < 144f) { // Within 12 tiles
                        int idx = count * 3;
                        lightPosArray[idx]     = gate.getPosition().x;
                        lightPosArray[idx + 1] = 0.70f; // Eye level of gargoyles / demon skull
                        lightPosArray[idx + 2] = -gate.getPosition().y;

                        float pulse = (float) Math.sin(totalTime * 8.0f) * 0.20f + 0.80f;
                        lightColorArray[idx]     = 0.15f * pulse; // Cyan R
                        lightColorArray[idx + 1] = 0.85f * pulse; // Cyan G
                        lightColorArray[idx + 2] = 1.00f * pulse; // Cyan B

                        lightRadiusArray[count]     = 4.0f;
                        lightIntensityArray[count] = 1.25f * pulse;
                        count++;
                    }
                }
            }
        }

        shader.setUniform3fv("u_lightPos", lightPosArray, 0, count * 3);
        shader.setUniform3fv("u_lightColor", lightColorArray, 0, count * 3);
        shader.setUniform1fv("u_lightRadius", lightRadiusArray, 0, count);
        shader.setUniform1fv("u_lightIntensity", lightIntensityArray, 0, count);
        shader.setUniformi("u_numLights", count);
    }

    private void renderDynamicDoors(Maze maze, Player player, boolean isRetro, RetroTheme.Theme theme) {
        int px = (int) player.getPosition().x;
        int py = (int) player.getPosition().y;
        int radius = 16;

        int minX = Math.max(0, px - radius);
        int maxX = Math.min(maze.getWidth() - 1, px + radius);
        int minY = Math.max(0, py - radius);
        int maxY = Math.min(maze.getHeight() - 1, py + radius);

        // 1. Standard sliding wooden doors
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                Object obj = maze.getGameObjectAt(x, y);
                if (obj instanceof Door) {
                    Door door = (Door) obj;
                    boolean ewFacing = door.getOrientation() == Door.Orientation.EAST_WEST;

                    Color col = isRetro ? theme.door : Color.WHITE;
                    if (isRetro) {
                        shader.setUniformf("u_retroColor", theme.door);
                        shader.setUniformf("u_retroBorder", 1.0f);
                    }
                    dynamicBatcher.addSlidingDoor(x, y, ewFacing, door.getAnimationProgress(), col);
                    dynamicBatcher.flush(shader, doorTexture);
                }
            }
        }

        // 2. 3D Skullgate Portals
        if (maze.getGates() != null && !maze.getGates().isEmpty()) {
            boolean has3dModels = (gateFrameModel != null && gateLeftDoorModel != null && gateRightDoorModel != null);

            int colorLoc = shader.getAttributeLocation("a_color");
            if (colorLoc >= 0) {
                Gdx.gl.glVertexAttrib4f(colorLoc, 1f, 1f, 1f, 1f);
            }

            for (Gate gate : maze.getGates().values()) {
                float gx = gate.getPosition().x;
                float gy = gate.getPosition().y;
                int tileX = (int) gx;
                int tileY = (int) gy;

                float distSq = (gx - player.getPosition().x) * (gx - player.getPosition().x) +
                               (gy - player.getPosition().y) * (gy - player.getPosition().y);
                if (distSq > (radius + 2) * (radius + 2)) continue;

                if (!has3dModels) {
                    // Fallback to sliding door quad if 3D model missing
                    boolean ewFacing = gate.getOrientation() == Door.Orientation.EAST_WEST;
                    Color col = isRetro ? theme.doorDark : Color.WHITE;
                    if (isRetro) {
                        shader.setUniformf("u_retroColor", theme.doorDark);
                        shader.setUniformf("u_retroBorder", 1.0f);
                    }
                    dynamicBatcher.addSlidingDoor(tileX, tileY, ewFacing, gate.getAnimationProgress(), col);
                    dynamicBatcher.flush(shader, gateTexture);
                    continue;
                }

                float progress = gate.getAnimationProgress();
                float rumble = 0.0f;
                if (gate.getState() == Gate.GateState.OPENING) {
                    rumble = (float) Math.sin(progress * Math.PI * 18.0) * 1.8f;
                }

                // Determine base gate rotation so portal faces toward approaching player
                float baseRotation = 0f;
                if (tileY == maze.getHeight() - 1) {
                    baseRotation = 0f;   // North boundary gate faces South into chunk
                } else if (tileY == 0) {
                    baseRotation = 180f; // South boundary gate faces North into chunk
                } else if (tileX == maze.getWidth() - 1) {
                    baseRotation = 270f; // East boundary gate faces West into chunk
                } else if (tileX == 0) {
                    baseRotation = 90f;  // West boundary gate faces East into chunk
                } else {
                    baseRotation = (gate.getOrientation() == Door.Orientation.EAST_WEST) ? 90f : 0f;
                }

                float gateX = tileX + 0.5f;
                float gateZ = -(tileY + 0.5f);

                // --- A. Gate Frame ---
                gateTransform.idt();
                gateTransform.translate(gateX, 0f, gateZ);
                gateTransform.rotate(0f, 1f, 0f, baseRotation);

                if (isRetro) {
                    shader.setUniformf("u_retroColor", theme.wall);
                    shader.setUniformf("u_retroBorder", 1.0f);
                } else {
                    shader.setUniformf("u_retroBorder", 0.0f);
                    if (gateDiffuseTexture != null) {
                        gateDiffuseTexture.bind(0);
                        shader.setUniformi("u_diffuseTexture", 0);
                    }
                }

                shader.setUniformMatrix("u_worldTrans", gateTransform);
                for (Mesh mesh : gateFrameModel.meshes) {
                    mesh.render(shader, GL20.GL_TRIANGLES);
                }

                // --- B. Left Door Leaf ---
                // Hinge at local X = -GATE_DOOR_HINGE_X, swings +90 deg away into portal
                float angleLeft = 90.0f * progress + rumble;
                gateTransform.idt();
                gateTransform.translate(gateX, 0f, gateZ);
                gateTransform.rotate(0f, 1f, 0f, baseRotation);
                gateTransform.translate(-GATE_DOOR_HINGE_X, 0f, 0f);
                gateTransform.rotate(0f, 1f, 0f, angleLeft);

                if (isRetro) {
                    shader.setUniformf("u_retroColor", theme.doorDark);
                    shader.setUniformf("u_retroBorder", 1.0f);
                }
                shader.setUniformMatrix("u_worldTrans", gateTransform);
                for (Mesh mesh : gateLeftDoorModel.meshes) {
                    mesh.render(shader, GL20.GL_TRIANGLES);
                }

                // --- C. Right Door Leaf ---
                // Hinge at local X = +GATE_DOOR_HINGE_X, swings -90 deg away into portal
                float angleRight = -90.0f * progress - rumble;
                gateTransform.idt();
                gateTransform.translate(gateX, 0f, gateZ);
                gateTransform.rotate(0f, 1f, 0f, baseRotation);
                gateTransform.translate(GATE_DOOR_HINGE_X, 0f, 0f);
                gateTransform.rotate(0f, 1f, 0f, angleRight);

                if (isRetro) {
                    shader.setUniformf("u_retroColor", theme.doorDark);
                    shader.setUniformf("u_retroBorder", 1.0f);
                }
                shader.setUniformMatrix("u_worldTrans", gateTransform);
                for (Mesh mesh : gateRightDoorModel.meshes) {
                    mesh.render(shader, GL20.GL_TRIANGLES);
                }
            }

            // Restore identity world transform for subsequent render passes
            shader.setUniformMatrix("u_worldTrans", identityMatrix);
            shader.setUniformf("u_retroBorder", 0.0f);
        }
    }

    private void renderGore(Maze maze, WorldManager worldManager, boolean isRetro, RetroTheme.Theme theme) {
        GoreManager gore = (worldManager != null) ? worldManager.getGoreManager() : (maze != null ? maze.getGoreManager() : null);
        if (gore == null) return;

        GridPoint2 currentChunkId = (worldManager != null) ? worldManager.getCurrentPlayerChunkId() : new GridPoint2(0, 0);
        float chunkOriginX = currentChunkId.x * 36.0f;
        float chunkOriginZ = currentChunkId.y * 36.0f;

        Texture currentTex = null;
        Texture blankTex = blankTexture;
        Color retroTint = (theme != null && theme.wall != null) ? theme.wall : Color.WHITE;

        // 1. Coplanar Wall Decals
        Array<WallDecal> wallDecals = gore.getActiveWallDecals();
        if (wallDecals != null && wallDecals.size > 0) {
            for (int i = 0; i < wallDecals.size; i++) {
                WallDecal decal = wallDecals.get(i);
                TextureRegion region = isRetro ? null : decal.textureRegion;
                Texture tex = (region != null) ? region.getTexture() : blankTex;
                if (tex == null) continue;

                if (currentTex != null && currentTex != tex) {
                    dynamicBatcher.flush(shader, currentTex);
                }
                currentTex = tex;

                float localGridX = decal.gridX - chunkOriginX;
                float localGridY = decal.gridY - chunkOriginZ;
                Color col = isRetro ? retroTint : decal.color;
                dynamicBatcher.addWallDecal(decal, localGridX, localGridY, col);
            }
        }

        // 2. Floor Surface Decals (Blood Pools)
        Array<SurfaceDecal> surfaceDecals = gore.getActiveDecals();
        if (surfaceDecals != null && surfaceDecals.size > 0) {
            for (int i = 0; i < surfaceDecals.size; i++) {
                SurfaceDecal d = surfaceDecals.get(i);
                TextureRegion region = isRetro ? null : d.textureRegion;
                Texture tex = (region != null) ? region.getTexture() : blankTex;
                if (tex == null) continue;

                if (currentTex != null && currentTex != tex) {
                    dynamicBatcher.flush(shader, currentTex);
                }
                currentTex = tex;

                float localX = d.position.x - chunkOriginX;
                float localZ = d.position.z - chunkOriginZ;
                float splatSize = Math.max(0.08f, d.size);
                Color col = isRetro ? retroTint : d.color;
                dynamicBatcher.addFloorQuad(
                        localX, 0.002f, -localZ,
                        splatSize, splatSize,
                        region != null ? region : new TextureRegion(blankTex), col
                );
            }
        }

        // 3. Active Flying Blood Droplets
        Array<BloodParticle> particles = gore.getActiveParticles();
        if (particles != null && particles.size > 0) {
            for (int i = 0; i < particles.size; i++) {
                BloodParticle p = particles.get(i);
                if (p.onGround) continue;

                TextureRegion region = isRetro ? null : p.textureRegion;
                Texture tex = (region != null) ? region.getTexture() : blankTex;
                if (tex == null) continue;

                if (currentTex != null && currentTex != tex) {
                    dynamicBatcher.flush(shader, currentTex);
                }
                currentTex = tex;

                float localX = p.position.x - chunkOriginX;
                float localZ = p.position.z - chunkOriginZ;
                float pSize = Math.max(0.035f, p.size * 2.0f);
                Color col = isRetro ? retroTint : p.color;

                dynamicBatcher.addBillboard(
                        localX, p.position.y, -localZ,
                        pSize, pSize,
                        region != null ? region : new TextureRegion(blankTex), col,
                        camRight, camUp, camDir
                );
            }
        }

        // 4. Active Gib Chunks (Airborne Tumbling Billboards and Resting Floor Quads)
        Array<Gib> gibs = gore.getActiveGibs();
        if (gibs != null && gibs.size > 0) {
            for (int i = 0; i < gibs.size; i++) {
                Gib g = gibs.get(i);
                TextureRegion region = isRetro ? null : g.textureRegion;
                Texture tex = (region != null) ? region.getTexture() : blankTex;
                if (tex == null) continue;

                if (currentTex != null && currentTex != tex) {
                    dynamicBatcher.flush(shader, currentTex);
                }
                currentTex = tex;

                float localX = g.position.x - chunkOriginX;
                float localZ = g.position.z - chunkOriginZ;
                float gibSize = 0.22f;
                Color col = isRetro ? retroTint : g.color;

                if (g.onGround) {
                    dynamicBatcher.addFloorQuad(
                            localX, 0.003f, -localZ,
                            gibSize * 0.5f, gibSize * 0.5f,
                            region != null ? region : new TextureRegion(blankTex), col
                    );
                } else {
                    dynamicBatcher.addRotatedBillboard(
                            localX, Math.max(0.02f, g.position.y), -localZ,
                            gibSize, gibSize,
                            region != null ? region : new TextureRegion(blankTex), col,
                            camRight, camUp, camDir,
                            g.rotation
                    );
                }
            }
        }

        if (currentTex != null) {
            dynamicBatcher.flush(shader, currentTex);
        }
    }

    private void renderEntities(Maze maze, Player player, CombatManager combatManager, boolean isRetro, RetroTheme.Theme theme) {
        List<Renderable> entities = new ArrayList<>();
        entities.addAll(maze.getItems().values());

        if (combatManager != null && combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE && combatManager.getMonster() != null) {
            entities.add(combatManager.getMonster());
        } else {
            entities.addAll(maze.getMonsters().values());
        }
        entities.addAll(maze.getLadders().values());
        entities.addAll(maze.getScenery().values());

        // Sort entities back-to-front by distance to player
        entities.sort((a, b) -> Float.compare(
                player.getPosition().dst2(b.getPosition()),
                player.getPosition().dst2(a.getPosition())
        ));

        for (Renderable r : entities) {
            float ex = r.getPosition().x;
            float ey = r.getPosition().y;
            float wz = -ey;

            if (r instanceof Monster) {
                Monster m = (Monster) r;
                TextureRegion region = m.getTextureRegion();
                Texture tex = (region != null) ? region.getTexture() : m.getTexture();

                if (tex != null) {
                    if (region == null) {
                        region = new TextureRegion(tex);
                    }
                    float w = m.getScale().x;
                    float h = m.getScale().y;
                    dynamicBatcher.addBillboard(ex, 0.0f, wz, w, h, region, Color.WHITE, camRight, camUp, camDir);
                    dynamicBatcher.flush(shader, tex);
                }
            } else if (r instanceof Item) {
                Item it = (Item) r;
                TextureRegion region = it.getTextureRegion();
                Texture tex = (region != null) ? region.getTexture() : it.getTexture();

                if (tex != null) {
                    if (region == null) {
                        region = new TextureRegion(tex);
                    }
                    float baseHeight = 0.45f;
                    Vector2 scale = it.getScale();
                    float sx = (scale != null && scale.x > 0) ? scale.x : 0.5f;
                    float sy = (scale != null && scale.y > 0) ? scale.y : 0.5f;

                    float regW = region.getRegionWidth();
                    float regH = region.getRegionHeight();
                    float aspect = (regH > 0) ? (regW / regH) : 1.0f;

                    float h = baseHeight * sy;
                    float w = baseHeight * sx * aspect;

                    ItemTemplate t = it.getTemplate();
                    if (t != null && t.modelScale > 0 && t.modelScale != 1.0f) {
                        w *= t.modelScale;
                        h *= t.modelScale;
                    }

                    // Bounds safety for dungeon floor items
                    h = Math.max(0.06f, Math.min(0.6f, h));
                    w = Math.max(0.06f, Math.min(0.8f, w));

                    // Check if item is at player's feet (same tile as player)
                    float px = player.getPosition().x;
                    float py = player.getPosition().y;
                    float distSq = (ex - px) * (ex - px) + (ey - py) * (ey - py);
                    boolean atFeet = distSq < 0.15f;

                    float renderX = ex;
                    float renderZ = wz;
                    float renderFeetY = 0.01f;

                    if (atFeet) {
                        Vector2 dir = player.getDirectionVector();
                        // Anchor within the player's tile, avoiding wall clipping
                        int playerTileX = (int) px;
                        int playerTileY = (int) py;
                        boolean wallInFront = maze != null && maze.isWallBlocking(playerTileX, playerTileY, player.getFacing());
                        float zOffset = wallInFront ? 0.35f : 0.42f;

                        // Position vertically so the item sits comfortably in the lower view above the HUD banner
                        float halfFovRad = (float) Math.toRadians(camera.fieldOfView * 0.5f);
                        float targetAngle = halfFovRad * 0.76f;
                        float deltaY = zOffset * (float) Math.tan(targetAngle);
                        float targetCenterY = camera.position.y - deltaY;

                        renderFeetY = Math.max(0.01f, targetCenterY - h * 0.5f);
                        renderX = px + dir.x * zOffset;
                        renderZ = -py - dir.y * zOffset;

                        // Scale slightly for close-up presentation at player's feet
                        h = Math.min(0.32f, h * 0.85f);
                        w = Math.min(0.40f, w * 0.85f);
                    }

                    dynamicBatcher.addBillboard(renderX, renderFeetY, renderZ, w, h, region, it.getColor(), camRight, camUp, camDir);
                    dynamicBatcher.flush(shader, tex);
                }
            } else if (r instanceof Scenery) {
                Scenery sc = (Scenery) r;
                Texture tex = sc.getTexture();
                if (tex != null) {
                    TextureRegion reg = new TextureRegion(tex);
                    dynamicBatcher.addBillboard(ex, 0.0f, wz, 1.0f, 1.0f, reg, Color.WHITE, camRight, camUp, camDir);
                    dynamicBatcher.flush(shader, tex);
                }
            } else if (r instanceof Ladder) {
                Ladder ld = (Ladder) r;
                boolean isUp = (ld.getType() == Ladder.LadderType.UP);
                Texture tex = isUp ? ladderUpTexture : ladderDownTexture;
                TextureRegion region = new TextureRegion(tex);

                float w = isUp ? 0.8f : 0.85f;
                float h = isUp ? 1.0f : 0.45f;

                // Check if ladder is at player's feet (same tile as player)
                float px = player.getPosition().x;
                float py = player.getPosition().y;
                float distSq = (ex - px) * (ex - px) + (ey - py) * (ey - py);
                boolean atFeet = distSq < 0.15f;

                float renderX = ex;
                float renderZ = wz;
                float renderFeetY = 0.01f;

                if (atFeet) {
                    Vector2 dir = player.getDirectionVector();
                    int playerTileX = (int) px;
                    int playerTileY = (int) py;
                    boolean wallInFront = maze != null && maze.isWallBlocking(playerTileX, playerTileY, player.getFacing());
                    float zOffset = wallInFront ? 0.35f : 0.42f;

                    float halfFovRad = (float) Math.toRadians(camera.fieldOfView * 0.5f);
                    float targetAngle = halfFovRad * 0.76f;
                    float deltaY = zOffset * (float) Math.tan(targetAngle);
                    float targetCenterY = camera.position.y - deltaY;

                    renderFeetY = Math.max(0.01f, targetCenterY - h * 0.5f);
                    renderX = px + dir.x * zOffset;
                    renderZ = -py - dir.y * zOffset;

                    h = isUp ? 0.6f : 0.32f;
                    w = isUp ? 0.5f : 0.60f;
                }

                dynamicBatcher.addBillboard(renderX, renderFeetY, renderZ, w, h, region, Color.WHITE, camRight, camUp, camDir);
                dynamicBatcher.flush(shader, tex);
            }
        }
    }

    public void invalidateMeshCache() {
        meshCache.invalidate();
    }

    @Override
    public void dispose() {
        shader.dispose();
        meshCache.dispose();
        dynamicBatcher.dispose();

        wallTexture.dispose();
        doorTexture.dispose();
        gateTexture.dispose();
        floorTexture.dispose();
        ceilingTexture.dispose();
        blankTexture.dispose();
        ladderDownTexture.dispose();
        ladderUpTexture.dispose();

        if (gateFrameModel != null) gateFrameModel.dispose();
        if (gateLeftDoorModel != null) gateLeftDoorModel.dispose();
        if (gateRightDoorModel != null) gateRightDoorModel.dispose();
        if (gateDiffuseTexture != null) gateDiffuseTexture.dispose();

        if (skybox3DRenderer != null) {
            skybox3DRenderer.dispose();
        }
    }
}
