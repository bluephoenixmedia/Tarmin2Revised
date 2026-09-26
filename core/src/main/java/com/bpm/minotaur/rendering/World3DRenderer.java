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
import com.bpm.minotaur.gamedata.ShopkeeperNpc;
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
import com.bpm.minotaur.rendering.mesh.WallTextureProvider;
import com.bpm.minotaur.rendering.mesh.WorldMeshCache;
import com.bpm.minotaur.rendering.MonsterDecalCompositor;
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
    private final WallTextureProvider wallVariantProvider;
    private final Texture forestWallTexture;
    private final Texture doorTexture;
    private final Texture gateTexture;
    private final Texture floorTexture;
    private final Texture forestFloorTexture;
    private final Texture ceilingTexture;
    private final Texture blankTexture;

    /**
     * Authored sigils for the rune above a themed gate, keyed by theme.
     *
     * <p>Before this the rune billboard drew {@code blankTexture} tinted by the
     * theme colour: a glowing square, never the glyph. The sigils are authored
     * white-on-transparent so the existing colour pulse still tints them.
     */
    private final java.util.EnumMap<com.bpm.minotaur.generation.theme.ChunkTheme, Texture> runeTextures =
            new java.util.EnumMap<>(com.bpm.minotaur.generation.theme.ChunkTheme.class);

    /** Frames of the biome portal vortex loop, sliced from an 8x8 sheet. */
    private TextureRegion[] portalVortexFrames;
    /** Neutral-white arcane circle laid flat under a portal, tinted per biome. */
    private Texture portalRuneCircle;
    private TextureRegion portalRuneRegion;
    /** Reused every portal every frame; the render loop must not allocate. */
    private final Color portalScratchColor = new Color();

    private static final int PORTAL_VORTEX_COLS = 8;
    private static final int PORTAL_VORTEX_ROWS = 8;
    /** The sheet's last three cells are blank, so only 61 of 64 are played. */
    private static final int PORTAL_VORTEX_FRAMES = 61;
    private static final float PORTAL_VORTEX_FPS = 18f;
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

    // Strata darkness scaling: each dungeon level below the surface dims ambient
    // light and closes in fog further, down to a floor so it's never pitch black.
    private static final float DEPTH_DARKNESS_PER_LEVEL = 0.05f;
    private static final float DEPTH_DARKNESS_FLOOR = 0.35f;

    /** @return a 0..1 darkening multiplier that shrinks with every strata level descended. */
    private static float getDepthDarknessFactor(int level) {
        return MathUtils.clamp(1.0f - (level - 1) * DEPTH_DARKNESS_PER_LEVEL, DEPTH_DARKNESS_FLOOR, 1.0f);
    }

    // Smooth eye adaptation and celestial lighting state
    private final Color currentAmbientColor = new Color(LightingManager.COLOR_SHELTER_AMBIENT);
    private final Color targetAmbientColor = new Color();
    private final Color displayAmbient = new Color();
    private final Vector3 currentDirLightDir = new Vector3(0.3f, 0.8f, 0.4f).nor();
    private final Vector3 targetDirLightDir = new Vector3(0.3f, 0.8f, 0.4f).nor();
    private final Color currentDirLightColor = new Color(0f, 0f, 0f, 1f);
    private final Color targetDirLightColor = new Color(0f, 0f, 0f, 1f);
    private final Color scratchColor = new Color();
    // Separate from scratchColor: both are live within the same uniform-upload block.
    private final Color rimScratchColor = new Color();
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
        this.wallVariantProvider = new WallTextureProvider(this.wallTexture);
        this.wallTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        if (Gdx.files.internal("images/forest_cliff.png").exists()) {
            this.forestWallTexture = new Texture(Gdx.files.internal("images/forest_cliff.png"));
            this.forestWallTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        } else {
            this.forestWallTexture = this.wallTexture;
        }

        this.doorTexture = new Texture(Gdx.files.internal("images/door.png"));
        this.gateTexture = new Texture(Gdx.files.internal("images/gate.png"));

        this.floorTexture = new Texture(Gdx.files.internal("images/floor.png"));
        this.floorTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        if (Gdx.files.internal("images/floor_forest.png").exists()) {
            this.forestFloorTexture = new Texture(Gdx.files.internal("images/floor_forest.png"));
            this.forestFloorTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        } else {
            this.forestFloorTexture = this.floorTexture;
        }

        this.ceilingTexture = new Texture(Gdx.files.internal("images/floor.png"));
        this.ceilingTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pix.setColor(Color.WHITE);
        pix.fill();
        this.blankTexture = new Texture(pix);
        pix.dispose();

        this.ladderDownTexture = new Texture(Gdx.files.internal("images/items/ladder.png"));
        this.ladderUpTexture = new Texture(Gdx.files.internal("images/items/ladder_up.png"));

        loadRuneTextures();
        loadPortalTextures();

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
            Gdx.app.error(TAG, "Failed to load 3D assets", e);
        }

        try {
            this.skybox3DRenderer = new Skybox3DRenderer();
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to initialize Skybox3DRenderer: " + e.getMessage());
        }
    }

    /**
     * The death cinematic, when one is playing. Null the rest of the time.
     *
     * <p>Held rather than passed per frame because {@code updateCamera} is deep in the render
     * path and already takes four arguments.
     */
    private DeathSequence deathSequence;

    /**
     * The animation list holding in-flight projectiles.
     *
     * <p>Projectiles were only ever drawn by {@code AnimationManager.render}, which GameScreen
     * calls exclusively in the raycaster branch -- so in the default 3D engine every arrow, bolt
     * and thrown weapon was completely invisible. The flight existed in simulation and nowhere on
     * screen.
     */
    /**
     * Only skipped when effectively inside the camera. An earlier 1.1 clip was far too greedy:
     * most shots in a grid dungeon hit something one or two tiles away, so the whole flight sat
     * inside the clip and nothing was ever drawn.
     */
    private static final float PROJECTILE_NEAR_CLIP = 0.30f;

    /**
     * Caps how much of the view a projectile may span, in world units per unit of distance.
     * Keeps a shot readable far away without letting it become a slab as it leaves the muzzle.
     */
    private static final float PROJECTILE_ANGULAR_CAP = 0.34f;

    /**
     * Projectile art, resolved from textures ItemDataManager has already loaded.
     *
     * <p>Reused rather than loaded again: every one of these is an item template texture, so the
     * AssetManager is holding them regardless.
     *
     * <p>Names in this folder are not to be trusted -- "flightarrow" is a quiver of arrows and
     * "big_bolt" is a lightning burst, neither of which is a bolt in flight.
     */
    private static final String SPRITE_ARROW = "images/weapons/arrow_war.png";
    private static final String SPRITE_BOLT = "images/weapons/quarrel_light.png";
    private static final String SPRITE_SPELL = "images/weapons/big_bolt.png";

    private com.badlogic.gdx.assets.AssetManager projectileAssets;

    public void setProjectileAssets(com.badlogic.gdx.assets.AssetManager assets) {
        this.projectileAssets = assets;
    }

    /** The sprite for a shot, or null to fall back to a plain quad. */
    private TextureRegion projectileRegion(com.bpm.minotaur.rendering.Animation a) {
        if (projectileAssets == null) return null;

        String path;
        if (a.getType() == com.bpm.minotaur.rendering.Animation.AnimationType.PROJECTILE_SPELL) {
            path = SPRITE_SPELL;
        } else if ("bolt".equals(a.getProjectileSprite())) {
            path = SPRITE_BOLT;
        } else {
            path = SPRITE_ARROW;
        }

        if (!projectileAssets.isLoaded(path, com.badlogic.gdx.graphics.Texture.class)) {
            return null;
        }
        return new TextureRegion(projectileAssets.get(path, com.badlogic.gdx.graphics.Texture.class));
    }

    private com.bpm.minotaur.rendering.AnimationManager animationManager;

    public void setAnimationManager(com.bpm.minotaur.rendering.AnimationManager manager) {
        this.animationManager = manager;
    }

    /**
     * Draws in-flight projectiles as camera-facing billboards.
     *
     * <p>Runs inside the entity pass so projectiles are depth-tested against walls and monsters --
     * a bolt passing behind a pillar should be hidden by it.
     */
    private void renderProjectiles() {
        if (animationManager == null || blankTexture == null) return;

        for (com.bpm.minotaur.rendering.Animation a : animationManager.getAnimations()) {
            com.bpm.minotaur.rendering.Animation.AnimationType t = a.getType();
            if (t != com.bpm.minotaur.rendering.Animation.AnimationType.PROJECTILE_PLAYER
                    && t != com.bpm.minotaur.rendering.Animation.AnimationType.PROJECTILE_MONSTER
                    && t != com.bpm.minotaur.rendering.Animation.AnimationType.PROJECTILE_SPELL) {
                continue;
            }
            Vector2 from = a.getStartPosition();
            Vector2 to = a.getEndPosition();
            if (from == null || to == null) continue;

            float progress = a.getProgress();
            float mx = from.x + (to.x - from.x) * progress;
            float my = from.y + (to.y - from.y) * progress;

            // Maze Y maps to world -Z, matching updateCamera and the entity pass.
            float wx = mx;
            float wz = -my;
            // Chest height, so a shot reads as travelling through the room rather than along
            // the floor.
            float wy = 0.45f;

            // A shot leaves the muzzle ~0.6 units from the eye. At that range even a small quad
            // subtends most of the view, so the first frames of every shot would flash a
            // screen-filling slab. Hold the sprite back until it has cleared the weapon.
            float dx = wx - camera.position.x;
            float dy = wy - camera.position.y;
            float dz = wz - camera.position.z;
            float distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < PROJECTILE_NEAR_CLIP * PROJECTILE_NEAR_CLIP) {
                continue;
            }

            TextureRegion sprite = projectileRegion(a);
            boolean spell = (t == com.bpm.minotaur.rendering.Animation.AnimationType.PROJECTILE_SPELL);

            // The arrow occupies a thin diagonal of a mostly transparent square, so the quad has
            // to be generous or the arrow itself is a few pixels at range.
            float baseSize = (sprite != null) ? (spell ? 1.10f : 0.90f) : (spell ? 0.22f : 0.12f);
            // Shrink rather than hide when close, so a point-blank shot is still drawn.
            float dist = (float) Math.sqrt(distSq);
            float size = Math.min(baseSize, dist * PROJECTILE_ANGULAR_CAP);
            Color tint = (sprite != null)
                    ? Color.WHITE
                    : ((a.getColor() != null) ? a.getColor() : Color.WHITE);

            TextureRegion region = (sprite != null) ? sprite : new TextureRegion(blankTexture);
            Texture bound = (sprite != null) ? sprite.getTexture() : blankTexture;

            // Flush per texture: the batcher draws one texture at a time, and arrows, bolts and
            // spell bursts are three different ones.
            dynamicBatcher.addBillboard(wx, wy, wz, size, size,
                    region, tint, camRight, camUp, camDir);
            dynamicBatcher.flush(shader, bound);
        }
    }

    public void setDeathSequence(DeathSequence sequence) {
        this.deathSequence = sequence;
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

        if (wm != null && currentLevel == 1 && !isInsideHome) {
            fogEnabled = true;
            fogDistance = wm.getFogDistance();
            fogColor.set(wm.getFogColor());
            // Atmospheric fog harmonization with Day/Night cycle
            if (dnm != null) {
                fogColor.mul(dnm.getSkyTint());
            }
        } else if (isInsideHome) {
            fogEnabled = false;
        } else if (gameMode == GameMode.ADVANCED && biome != null && biome.hasFogOfWar()) {
            fogEnabled = true;
            // Strata deepen the dark: fog closes in the farther underground you go,
            // matching the difficulty curve (deeper == harder == blinder).
            fogDistance = biome.getFogDistance() * getDepthDarknessFactor(currentLevel);
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
            skybox3DRenderer.setInsideHome(isInsideHome);
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

        // Sky bounce: only outdoors on the surface, under open sky. The shelter has a roof, so
        // it gets no bounce even though the skybox still draws through its windows.
        boolean skyOverhead = (currentLevel == 1) && !isIndoors;
        // Damped like the ambient: the rim tints surfaces, it does not repaint them.
        Color rimTint = (dnm != null) ? dnm.getWorldTint(rimScratchColor) : Color.WHITE;
        shader.setUniformf("u_skyRimColor", rimTint.r, rimTint.g, rimTint.b);
        shader.setUniformf("u_skyRimStrength", skyOverhead ? 0.35f : 0f);

        // --- AMBIENT & CELESTIAL LIGHT TARGET COMPUTATION ---
        if (isInsideHome) {
            // Shelter Haven: warm hearth/lamp sanctuary ambient
            targetAmbientColor.set(LightingManager.COLOR_SHELTER_AMBIENT);
            targetDirLightColor.set(0f, 0f, 0f, 1f); // Roof blocks direct sun/moon
        } else if (currentLevel > 1 || (isIndoors && !isInsideHome)) {
            // Dungeon / Underground Void: darkens further with every strata descended,
            // mirroring the difficulty curve so deeper levels are also blinder.
            targetAmbientColor.set(LightingManager.COLOR_COLD_VOID).mul(getDepthDarknessFactor(currentLevel));
            targetDirLightColor.set(0f, 0f, 0f, 1f);
        } else {
            // Level 1 Outdoors: dynamically calibrated from Day/Night cycle and Weather
            float dayAmbient = (dnm != null) ? dnm.getAmbientLight() : 0.60f;
            // Damped, not the full sky palette: at full strength the volcanic tint eats world
            // materials, turning hedge greens to olive-red mud (issue #104, Q22).
            Color skyTint = (dnm != null) ? dnm.getWorldTint(scratchColor) : Color.WHITE;
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
                forestWallTexture,
                forestFloorTexture,
                ceilingTexture,
                worldManager,
                wallVariantProvider
        );

        for (ChunkSubMesh subMesh : subMeshes) {
            if (isRetro) {
                shader.setUniformf("u_retroBorder", 1.0f);
                switch (subMesh.getSurface()) {
                    case WALL:
                        shader.setUniformf("u_retroColor", theme.wall);
                        break;
                    case FLOOR:
                        shader.setUniformf("u_retroColor", theme.floor);
                        break;
                    default:
                        shader.setUniformf("u_retroColor", theme.ceiling);
                        break;
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
        renderLiquids(maze, player, isRetro);
        renderBiomePortals(maze, player);
        renderGore(maze, worldManager, isRetro, theme);

        // B. Entities: Monsters, Items, Ladders, Scenery
        renderEntities(maze, player, combatManager, isRetro, theme);
        renderProjectiles();

        // --- PASS 3: 3D PRECIPITATION & WEATHER PARTICLES ---
        // Weather particles and splashes spawn strictly on outdoor tiles (never under indoor roofs/shelters).
        // OpenGL depth testing against shelter wall/ceiling/doorframe geometry occludes outdoor particles
        // behind solid surfaces while allowing them to animate through open doorways and barred windows.
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

        boolean dying = deathSequence != null && deathSequence.isActive();
        float pz = dying ? deathSequence.getEyeHeight() + deathSequence.getImpactJolt() : 0.5f;

        camera.position.set(px, pz, -py);

        // Direction: maze +X -> world +X (East), maze +Y -> world -Z (North)
        Vector2 dir = player.getDirectionVector();
        camDir.set(dir.x, 0f, -dir.y).nor();

        if (dying) {
            // Pitch the view forward into the floor. Pitch has been structurally zero everywhere
            // else in the game, so this is the one place the horizon is allowed to leave level.
            float pitch = deathSequence.getPitchDegrees();
            camRight.set(camDir).crs(0f, 1f, 0f).nor();
            camDir.rotate(camRight, -pitch).nor();
        }

        camera.direction.set(camDir);

        camera.up.set(0f, 1f, 0f);

        if (dying) {
            // Roll supplies the variety between deaths; the blood covers it before it settles.
            camera.up.rotate(camera.direction, deathSequence.getRollDegrees());
        }

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
                } else if (gate.getTheme() != null) {
                    float dist2 = player.getPosition().dst2(gate.getPosition());
                    if (dist2 < 144f) {
                        int idx = count * 3;
                        lightPosArray[idx]     = gate.getPosition().x;
                        lightPosArray[idx + 1] = 1.10f; // Floating rune level
                        lightPosArray[idx + 2] = -gate.getPosition().y;

                        Color runeCol = gate.getTheme().getRuneColor();
                        float pulse = (float) Math.sin(totalTime * 3.5f + gate.getPosition().x) * 0.25f + 0.75f;
                        lightColorArray[idx]     = runeCol.r * pulse;
                        lightColorArray[idx + 1] = runeCol.g * pulse;
                        lightColorArray[idx + 2] = runeCol.b * pulse;

                        lightRadiusArray[count]     = 4.5f;
                        lightIntensityArray[count] = 1.6f * pulse;
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

    private void loadRuneTextures() {
        for (com.bpm.minotaur.generation.theme.ChunkTheme t
                : com.bpm.minotaur.generation.theme.ChunkTheme.values()) {
            com.bpm.minotaur.generation.theme.ThemeDefinition def =
                    com.bpm.minotaur.generation.theme.ThemeDataManager.getInstance().get(t);
            String path = def != null ? def.getRuneTexture() : null;
            if (path == null) continue;
            try {
                if (Gdx.files.internal(path).exists()) {
                    runeTextures.put(t, new Texture(Gdx.files.internal(path)));
                }
            } catch (Exception e) {
                Gdx.app.log("World3DRenderer", "Failed to load rune texture " + path + ": " + e.getMessage());
            }
        }
    }

    private void loadPortalTextures() {
        try {
            String vortexPath = "images/portals/portal_vortex.png";
            if (Gdx.files.internal(vortexPath).exists()) {
                Texture sheet = new Texture(Gdx.files.internal(vortexPath));
                int fw = sheet.getWidth() / PORTAL_VORTEX_COLS;
                int fh = sheet.getHeight() / PORTAL_VORTEX_ROWS;

                portalVortexFrames = new TextureRegion[PORTAL_VORTEX_FRAMES];
                for (int i = 0; i < PORTAL_VORTEX_FRAMES; i++) {
                    int cx = i % PORTAL_VORTEX_COLS;
                    int cy = i / PORTAL_VORTEX_COLS;
                    portalVortexFrames[i] = new TextureRegion(sheet, cx * fw, cy * fh, fw, fh);
                }
            }

            String circlePath = "images/portals/portal_rune_circle.png";
            if (Gdx.files.internal(circlePath).exists()) {
                portalRuneCircle = new Texture(Gdx.files.internal(circlePath));
            }
        } catch (Exception e) {
            Gdx.app.log("World3DRenderer", "Failed to load portal textures: " + e.getMessage());
        }
    }

    /** The authored sigil for a theme, or the blank quad when art is missing. */
    private Texture runeTextureFor(com.bpm.minotaur.generation.theme.ChunkTheme theme) {
        Texture tex = theme != null ? runeTextures.get(theme) : null;
        if (tex != null) return tex;
        return (blankTexture != null) ? blankTexture : gateTexture;
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
                    dynamicBatcher.addSlidingDoor(x, y, ewFacing, door.getAnimationProgress(), col,
                            ChunkMeshBuilder.ceilingHeightFor(maze, x, y));
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
                    dynamicBatcher.addSlidingDoor(tileX, tileY, ewFacing, gate.getAnimationProgress(), col,
                            ChunkMeshBuilder.ceilingHeightFor(maze, tileX, tileY));
                    dynamicBatcher.flush(shader, gateTexture);
                    if (gate.getTheme() != null) {
                        float runeBob = (float) Math.sin(totalTime * 3.0f + gx * 1.5f) * 0.04f;
                        float runeY = 1.05f + runeBob;
                        float runeSize = 0.55f; // An authored sigil needs more room than a glowing dot
                        Color runeCol = gate.getTheme().getRuneColor();
                        float pulse = (float) Math.sin(totalTime * 4.0f + gx) * 0.15f + 0.85f;
                        Color emissiveColor = new Color(runeCol.r * pulse, runeCol.g * pulse, runeCol.b * pulse, 0.95f);
                        Texture runeTex = runeTextureFor(gate.getTheme());
                        dynamicBatcher.addBillboard(
                                tileX + 0.5f, runeY, -(tileY + 0.5f),
                                runeSize, runeSize,
                                new TextureRegion(runeTex),
                                emissiveColor,
                                camRight, camUp, camDir
                        );
                        dynamicBatcher.flush(shader, runeTex);
                    }
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

                // --- D. Floating Runic Sigil Billboard ---
                if (gate.getTheme() != null) {
                    shader.setUniformMatrix("u_worldTrans", identityMatrix);
                    shader.setUniformf("u_retroBorder", 0.0f);

                    float runeBob = (float) Math.sin(totalTime * 3.0f + gateX * 1.5f) * 0.04f;
                    float runeY = 1.05f + runeBob;
                    float runeSize = 0.55f; // An authored sigil needs more room than a glowing dot
                    Color runeCol = gate.getTheme().getRuneColor();
                    float pulse = (float) Math.sin(totalTime * 4.0f + gateX) * 0.15f + 0.85f;
                    Color emissiveColor = new Color(runeCol.r * pulse, runeCol.g * pulse, runeCol.b * pulse, 0.95f);

                    Texture runeTex = runeTextureFor(gate.getTheme());
                    dynamicBatcher.addBillboard(
                            gateX, runeY, gateZ,
                            runeSize, runeSize,
                            new TextureRegion(runeTex),
                            emissiveColor,
                            camRight, camUp, camDir
                    );
                    dynamicBatcher.flush(shader, runeTex);
                }
            }

            // Restore identity world transform for subsequent render passes
            shader.setUniformMatrix("u_worldTrans", identityMatrix);
            shader.setUniformf("u_retroBorder", 0.0f);
        }
    }

    /**
     * Draws the liquid surface as translucent quads just above the floor.
     *
     * <p>Flooded Caverns set liquid data that nothing ever drew, so the theme
     * was invisible at runtime. Wading costs the player a turn, so the water
     * has to be visible before they step in: an unseen hazard is a trap, not a
     * decision.
     */
    /**
     * Draws the shelter's biome portals: a rotating rune circle underfoot and a
     * looping vortex standing in the niche, both tinted to the destination.
     *
     * <p>The portal item's own billboard is a still frame; this is what makes it
     * read as a live rift. Colour comes from tinting neutral art rather than one
     * recoloured sheet per biome, so adding a destination stays a data row.
     */
    private void renderBiomePortals(Maze maze, Player player) {
        if (maze == null || maze.getItems().isEmpty()) return;
        if (portalVortexFrames == null || portalRuneCircle == null) return;

        int px = (int) player.getPosition().x;
        int py = (int) player.getPosition().y;

        for (java.util.Map.Entry<GridPoint2, com.bpm.minotaur.gamedata.item.Item> entry
                : maze.getItems().entrySet()) {
            com.bpm.minotaur.gamedata.item.Item item = entry.getValue();
            if (item == null) continue;

            com.bpm.minotaur.gamedata.progression.BiomePortal portal =
                    com.bpm.minotaur.gamedata.progression.BiomePortal.forItem(item.getType());
            boolean isReturn = item.getType()
                    == com.bpm.minotaur.gamedata.item.Item.ItemType.BIOME_RETURN_PORTAL;
            if (portal == null && !isReturn) continue;

            GridPoint2 pos = entry.getKey();
            if (Math.abs(pos.x - px) > 16 || Math.abs(pos.y - py) > 16) continue;

            Color tint = portal != null
                    ? portal.getTint()
                    : com.bpm.minotaur.gamedata.progression.BiomePortal.RETURN_PORTAL_TINT;

            // Rune circle: flat on the floor, counter-rotating, tinted.
            float spin = (totalTime * 18f) % 360f;
            portalScratchColor.set(tint.r, tint.g, tint.b, 0.85f);
            dynamicBatcher.addRotatedFloorQuad(
                    pos.x + 0.5f, 0.03f, -(pos.y + 0.5f),
                    0.5f, 0.5f,
                    portalRuneRegion, portalScratchColor, spin);
            dynamicBatcher.flush(shader, portalRuneCircle);

            // Vortex: upright in the niche, looping, tinted, gently pulsing.
            int frame = ((int) (totalTime * PORTAL_VORTEX_FPS)) % portalVortexFrames.length;
            float pulse = (float) Math.sin(totalTime * 2.4f + pos.x) * 0.12f + 0.88f;
            portalScratchColor.set(tint.r * pulse, tint.g * pulse, tint.b * pulse, 0.95f);

            dynamicBatcher.addBillboard(
                    pos.x + 0.5f, 0.55f, -(pos.y + 0.5f),
                    0.9f, 0.9f,
                    portalVortexFrames[frame], portalScratchColor,
                    camRight, camUp, camDir);
            dynamicBatcher.flush(shader, portalVortexFrames[frame].getTexture());
        }
    }

    private void renderLiquids(Maze maze, Player player, boolean isRetro) {
        if (maze == null || maze.getLiquidManager() == null || blankTexture == null) return;

        int px = (int) player.getPosition().x;
        int py = (int) player.getPosition().y;
        int radius = 14;

        int minX = Math.max(0, px - radius);
        int maxX = Math.min(maze.getWidth() - 1, px + radius);
        int minY = Math.max(0, py - radius);
        int maxY = Math.min(maze.getHeight() - 1, py + radius);

        // A gentle swell so the surface reads as liquid rather than a stain.
        float swell = (float) Math.sin(totalTime * 1.6f) * 0.004f;
        TextureRegion region = new TextureRegion(blankTexture);
        boolean any = false;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                com.bpm.minotaur.gamedata.liquid.LiquidType liquid = maze.getLiquidAt(x, y);
                if (liquid == com.bpm.minotaur.gamedata.liquid.LiquidType.NONE) continue;
                if (maze.isWall(x, y)) continue;

                Color base = liquid.getColor();
                float ripple = (float) Math.sin(totalTime * 2.2f + x * 0.7f + y * 0.5f) * 0.06f + 0.94f;
                Color tint = new Color(base.r * ripple, base.g * ripple, base.b * ripple, base.a);

                dynamicBatcher.addFloorQuad(
                        x + 0.5f, 0.021f + swell, -(y + 0.5f),
                        0.5f, 0.5f,
                        region, tint);
                any = true;
            }
        }

        if (any) {
            dynamicBatcher.flush(shader, blankTexture);
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
                Texture tex = (g.customTexture != null)
                        ? g.customTexture
                        : ((region != null) ? region.getTexture() : blankTex);
                if (tex == null) continue;

                if (currentTex != null && currentTex != tex) {
                    dynamicBatcher.flush(shader, currentTex);
                }
                currentTex = tex;

                float localX = g.position.x - chunkOriginX;
                float localZ = g.position.z - chunkOriginZ;
                float gibSize = 0.22f;
                Color col = isRetro ? retroTint : g.color;

                if (g.polygonVertices != null && g.polygonUVs != null) {
                    if (g.onGround) {
                        // Render flat against the maze floor in true floor perspective
                        dynamicBatcher.addFloorPolygon(
                                localX, 0.004f, -localZ,
                                g.polygonVertices, g.polygonUVs, col,
                                g.rotation
                        );
                    } else {
                        // Render airborne tumbling polygon billboard
                        dynamicBatcher.addPolygonBillboard(
                                localX, Math.max(0.02f, g.position.y), -localZ,
                                g.polygonVertices, g.polygonUVs, col,
                                camRight, camUp, camDir,
                                g.rotation
                        );
                    }
                } else if (g.onGround) {
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

    /**
     * Free-running clock for the mimic idle bob and reveal shudder. Driven by wall time
     * rather than a per-item timer so chests breathe out of phase with one another.
     */
    private float mimicIdlePhase = 0f;

    private void renderEntities(Maze maze, Player player, CombatManager combatManager, boolean isRetro, RetroTheme.Theme theme) {
        mimicIdlePhase = MimicBob.advance(mimicIdlePhase, com.badlogic.gdx.Gdx.graphics.getDeltaTime());

        List<Renderable> entities = new ArrayList<>();
        entities.addAll(maze.getItems().values());

        entities.addAll(maze.getMonsters().values());
        if (combatManager != null && combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE && combatManager.getMonster() != null) {
            if (!entities.contains(combatManager.getMonster())) {
                entities.add(combatManager.getMonster());
            }
        }
        entities.addAll(maze.getLadders().values());
        entities.addAll(maze.getScenery().values());
        if (maze.getShopkeeper() != null && maze.getShopkeeper().isAlive()) {
            entities.add(maze.getShopkeeper());
        }

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
                TextureRegion region = null;
                if (!isRetro) {
                    region = MonsterDecalCompositor.getInstance().getCompositeRegion(m);
                }
                if (region == null) {
                    region = m.getTextureRegion();
                }
                Texture tex = (region != null) ? region.getTexture() : m.getTexture();

                if (tex != null) {
                    if (region == null) {
                        region = new TextureRegion(tex);
                    }
                    float w = m.getScale().x;
                    float h = m.getScale().y;
                    float maxMonsterW = 0.82f;
                    if (w > maxMonsterW) {
                        float scale = maxMonsterW / w;
                        w = maxMonsterW;
                        h *= scale;
                    }

                    // Hit recoil: spring backward away from the player, fading over ~0.12s
                    float mex = ex, mwz = wz;
                    float hitFlash = m.getHitFlashProgress();
                    if (hitFlash < 1f) {
                        float recoil = m.getHitRecoilOffset();
                        float dx = ex - player.getPosition().x;
                        float dy = ey - player.getPosition().y;
                        float len = (float) Math.sqrt(dx * dx + dy * dy);
                        if (len > 0.0001f) {
                            mex += (dx / len) * recoil;
                            mwz += -(dy / len) * recoil;
                        }
                    }
                    Color tint = (hitFlash < 1f)
                            ? new Color(1f, 1f - (1f - hitFlash) * 0.4f, 1f - (1f - hitFlash) * 0.4f, 1f)
                            : Color.WHITE;

                    // Twitchy monster attack telegraph (Eye Flare + Lunge / Retro Threat Aura)
                    if (combatManager != null && combatManager.getAttackIndicatorMonster() == m) {
                        float progress = combatManager.getAttackIndicatorProgress();
                        CombatManager.AttackIndicatorVariant variant = combatManager.getAttackIndicatorVariant();
                        if (progress < 1f) {
                            float pulse = 1f - progress;
                            if (variant == CombatManager.AttackIndicatorVariant.EYE_FLARE_LUNGE) {
                                float surge = 1f + 0.35f * pulse;
                                w *= surge;
                                h *= surge;
                                Color eyeColor = getMonsterEyeFlareColor(m);
                                tint = new Color(eyeColor).lerp(tint, 1f - pulse);
                            } else if (variant == CombatManager.AttackIndicatorVariant.RETRO_AURA) {
                                tint = new Color(1f, 0.15f * (1f - pulse), 0.15f * (1f - pulse), 1f);
                            }
                        }
                    }

                    float monsterY = 0.0f;
                    if (m.getType() == Monster.MonsterType.PLAYER_GHOST) {
                        float floatOffset = (float) Math.sin(System.currentTimeMillis() / 350.0) * 0.05f + 0.08f;
                        monsterY += floatOffset;
                        tint = new Color(0.45f, 0.92f, 0.95f, 0.88f);
                    }

                    dynamicBatcher.addBillboard(mex, monsterY, mwz, w, h, region, tint, camRight, camUp, camDir);
                    dynamicBatcher.flush(shader, tex);

                    // Overhead Health Bar
                    float dist = player.getPosition().dst(m.getPosition());
                    if (dist <= 25f && blankTexture != null && m.getCurrentHP() > 0) {
                        float topX = mex + camUp.x * h;
                        float topY = monsterY + camUp.y * h;
                        float topZ = mwz + camUp.z * h;
                        float toTopX = topX - camera.position.x;
                        float toTopY = topY - camera.position.y;
                        float toTopZ = topZ - camera.position.z;
                        float zCam = toTopX * camera.direction.x + toTopY * camera.direction.y + toTopZ * camera.direction.z;
                        if (zCam < 0.1f) zCam = 0.1f;

                        float pixelWorldHeight = (2.0f * zCam * MathUtils.tanDeg(camera.fieldOfView / 2.0f)) / camera.viewportHeight;
                        float gapWorld = 10.0f * pixelWorldHeight;

                        float barFeetX = mex + camUp.x * (h + gapWorld) - camDir.x * 0.002f;
                        float barFeetY = monsterY + camUp.y * (h + gapWorld) - camDir.y * 0.002f;
                        float barFeetZ = mwz + camUp.z * (h + gapWorld) - camDir.z * 0.002f;

                        float barW = Math.max(0.35f, w * 0.75f);
                        float barH = Math.max(0.04f, 6.0f * pixelWorldHeight);
                        float hpRatio = (m.getMaxHP() > 0) ? Math.max(0f, Math.min(1f, (float) m.getCurrentHP() / m.getMaxHP())) : 0f;

                        // Dark background
                        dynamicBatcher.addBillboard(barFeetX, barFeetY, barFeetZ, barW, barH, new TextureRegion(blankTexture), new Color(0.25f, 0f, 0f, 0.85f), camRight, camUp, camDir);

                        // Health foreground
                        if (hpRatio > 0f) {
                            Color fgColor = (hpRatio > 0.5f) ? Color.GREEN : (hpRatio > 0.25f) ? Color.YELLOW : Color.RED;
                            float fgW = barW * hpRatio;
                            float offset = -(barW - fgW) / 2.0f;
                            float fgX = barFeetX + camRight.x * offset - camDir.x * 0.001f;
                            float fgY = barFeetY + camRight.y * offset - camDir.y * 0.001f;
                            float fgZ = barFeetZ + camRight.z * offset - camDir.z * 0.001f;
                            dynamicBatcher.addBillboard(fgX, fgY, fgZ, fgW, barH, new TextureRegion(blankTexture), fgColor, camRight, camUp, camDir);
                        }
                        dynamicBatcher.flush(shader, blankTexture);
                    }
                }
            } else if (r instanceof ShopkeeperNpc) {
                ShopkeeperNpc sk = (ShopkeeperNpc) r;
                Texture tex = sk.getTexture();
                if (tex != null) {
                    TextureRegion region = new TextureRegion(tex);
                    float w = sk.scale.x;
                    float h = sk.scale.y;
                    dynamicBatcher.addBillboard(ex, 0.0f, wz, w, h, region, Color.WHITE, camRight, camUp, camDir);
                    dynamicBatcher.flush(shader, tex);

                    // Overhead Health Bar (mirrors Monster HP bar presentation)
                    float dist = player.getPosition().dst(sk.getPosition());
                    if (dist <= 25f && blankTexture != null && sk.getCurrentHP() > 0) {
                        float topX = ex + camUp.x * h;
                        float topY = camUp.y * h;
                        float topZ = wz + camUp.z * h;
                        float toTopX = topX - camera.position.x;
                        float toTopY = topY - camera.position.y;
                        float toTopZ = topZ - camera.position.z;
                        float zCam = toTopX * camera.direction.x + toTopY * camera.direction.y + toTopZ * camera.direction.z;
                        if (zCam < 0.1f) zCam = 0.1f;

                        float pixelWorldHeight = (2.0f * zCam * MathUtils.tanDeg(camera.fieldOfView / 2.0f)) / camera.viewportHeight;
                        float gapWorld = 10.0f * pixelWorldHeight;

                        float barFeetX = ex + camUp.x * (h + gapWorld) - camDir.x * 0.002f;
                        float barFeetY = camUp.y * (h + gapWorld) - camDir.y * 0.002f;
                        float barFeetZ = wz + camUp.z * (h + gapWorld) - camDir.z * 0.002f;

                        float barW = Math.max(0.35f, w * 0.75f);
                        float barH = Math.max(0.04f, 6.0f * pixelWorldHeight);
                        float hpRatio = (sk.getMaxHP() > 0)
                                ? Math.max(0f, Math.min(1f, (float) sk.getCurrentHP() / sk.getMaxHP())) : 0f;

                        dynamicBatcher.addBillboard(barFeetX, barFeetY, barFeetZ, barW, barH, new TextureRegion(blankTexture), new Color(0.25f, 0f, 0f, 0.85f), camRight, camUp, camDir);
                        if (hpRatio > 0f) {
                            Color fgColor = (hpRatio > 0.5f) ? Color.GREEN : (hpRatio > 0.25f) ? Color.YELLOW : Color.RED;
                            float fgW = barW * hpRatio;
                            float offset = -(barW - fgW) / 2.0f;
                            float fgX = barFeetX + camRight.x * offset - camDir.x * 0.001f;
                            float fgY = barFeetY + camRight.y * offset - camDir.y * 0.001f;
                            float fgZ = barFeetZ + camRight.z * offset - camDir.z * 0.001f;
                            dynamicBatcher.addBillboard(fgX, fgY, fgZ, fgW, barH, new TextureRegion(blankTexture), fgColor, camRight, camUp, camDir);
                        }
                        dynamicBatcher.flush(shader, blankTexture);
                    }
                }
            } else if (r instanceof Item) {
                Item it = (Item) r;
                ItemTemplate t = it.getTemplate();

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

                    // --- MIMIC TELLS ---
                    // A disguised mimic breathes. This is the always-on tell every
                    // player gets regardless of Wisdom: too small to notice in passing,
                    // learnable once you know to look for it.
                    // TODO: replace with the dedicated mimic_chest.png silhouette variant
                    // once the asset exists -- swap it in at spawn and delete this bob.
                    if (it.isMimic()) {
                        float bob = MimicBob.worldOffset(mimicIdlePhase, ex, ey);
                        renderFeetY += bob;
                        h *= 1.0f + bob * 0.5f;
                    }

                    // Once the player has reached for it, the lid thrashes: a beat of
                    // warning before the burst covers the swap.
                    GridPoint2 shudderTile = (combatManager != null) ? combatManager.getMimicRevealTile() : null;
                    if (shudderTile != null && shudderTile.x == (int) ex && shudderTile.y == (int) ey) {
                        float progress = combatManager.getMimicShudderProgress();
                        float violence = 0.02f + progress * 0.05f;
                        renderX += (float) Math.sin(mimicIdlePhase * 11f) * violence;
                        renderFeetY += Math.abs((float) Math.sin(mimicIdlePhase * 17f)) * violence;
                        w *= 1.0f + progress * 0.22f;
                        h *= 1.0f + progress * 0.22f;
                    }

                    dynamicBatcher.addBillboard(renderX, renderFeetY, renderZ, w, h, region, it.getColor(), camRight, camUp, camDir);
                    dynamicBatcher.flush(shader, tex);
                }
            } else if (r instanceof Scenery) {
                Scenery sc = (Scenery) r;
                Texture tex = sc.getTexture();
                if (tex != null) {
                    TextureRegion reg = new TextureRegion(tex);
                    if (sc.isFlippedX()) {
                        reg.flip(true, false);
                    }
                    Vector2 sceneryScale = sc.getScale();
                    float sw = (sceneryScale != null && sceneryScale.x > 0) ? sceneryScale.x : 1.0f;
                    float sh = (sceneryScale != null && sceneryScale.y > 0) ? sceneryScale.y : 1.0f;

                    Color tint = Color.WHITE;
                    if (sc.getType() == Scenery.SceneryType.STATUE && maze != null
                            && maze.getEventAt((int) sc.getPosition().x, (int) sc.getPosition().y) == null) {
                        // Depleted statue: encounter already resolved, dim to convey dormancy
                        tint = Color.GRAY;
                    } else if (sc.getType() == Scenery.SceneryType.DECOMPOSING_CORPSE) {
                        if (sc.isDefeated()) {
                            tint = new Color(0.85f, 0.95f, 0.90f, 1.0f); // Peaceful soft glow
                        } else if (sc.isAwakened()) {
                            tint = new Color(1.0f, 0.6f, 0.6f, 0.85f);  // Ominous red glow
                        } else {
                            tint = new Color(0.7f, 0.9f, 0.95f, 0.95f);  // Dormant ethereal cyan
                        }
                    }

                    float feetY = 0.0f;
                    if (sc.getType() == Scenery.SceneryType.DECOMPOSING_CORPSE) {
                        feetY = -0.095f; // Project ~50 pixels lower in 3D viewport at 1-tile interaction distance
                    }

                    dynamicBatcher.addBillboard(ex, feetY, wz, sw, sh, reg, tint, camRight, camUp, camDir);
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

        // ── Render 3D Sprite Explosions (BearFX) ───────────────────────────
        if (combatManager != null && combatManager.getAnimationManager() != null) {
            java.util.List<Animation> anims = combatManager.getAnimationManager().getAnimations();
            com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry registry = com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.getInstance();
            for (int i = 0; i < anims.size(); i++) {
                Animation anim = anims.get(i);
                if (anim.getType() == Animation.AnimationType.SPRITE_EXPLOSION_3D && anim.getExplosionType() != null) {
                    TextureRegion frame = registry.getFrame(anim.getExplosionType(), anim.getProgress());
                    if (frame != null && frame.getTexture() != null) {
                        Vector3 p3d = anim.getPosition3D();
                        float scale = anim.getScale3D();
                        float feetY = Math.max(0.01f, p3d.y - (scale * 0.5f));
                        float ex = p3d.x;
                        float ez = -p3d.z;

                        if (anim.isAdditiveBlend()) {
                            Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
                            Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE);
                        }

                        dynamicBatcher.addBillboard(ex, feetY, ez, scale, scale, frame, Color.WHITE, camRight, camUp, camDir);
                        dynamicBatcher.flush(shader, frame.getTexture());

                        if (anim.isAdditiveBlend()) {
                            Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
                        }
                    }
                }
            }
        }
    }

    /** Signature eye-flare color by monster family, mirroring EntityRenderer's palette. */
    private static Color getMonsterEyeFlareColor(Monster monster) {
        if (monster == null || monster.getType() == null) return Color.RED;
        String name = monster.getType().name();
        if (name.contains("SKELETON") || name.contains("WRAITH") || name.contains("GHOST") || name.contains("UNDEAD") || name.contains("SPIRIT")) {
            return new Color(0.75f, 0.30f, 1.0f, 1.0f); // Ethereal Violet
        } else if (name.contains("TROGLODYTE") || name.contains("SPIDER") || name.contains("SNAKE") || name.contains("SLIME")) {
            return new Color(0.15f, 1.0f, 0.25f, 1.0f); // Venom Green
        }
        return new Color(1.0f, 0.15f, 0.15f, 1.0f); // Ruby Red
    }

    public void invalidateMeshCache() {
        meshCache.invalidate();
    }

    @Override
    public void dispose() {
        shader.dispose();
        meshCache.dispose();
        dynamicBatcher.dispose();

        if (wallVariantProvider != null) wallVariantProvider.dispose();
        wallTexture.dispose();
        if (forestWallTexture != null && forestWallTexture != wallTexture) forestWallTexture.dispose();
        doorTexture.dispose();
        gateTexture.dispose();
        floorTexture.dispose();
        if (forestFloorTexture != null && forestFloorTexture != floorTexture) forestFloorTexture.dispose();
        ceilingTexture.dispose();
        blankTexture.dispose();
        ladderDownTexture.dispose();
        ladderUpTexture.dispose();

        for (Texture runeTexture : runeTextures.values()) {
            if (runeTexture != null) runeTexture.dispose();
        }
        runeTextures.clear();

        // Every frame shares one backing sheet, so disposing the first region's
        // texture releases the whole animation.
        if (portalVortexFrames != null && portalVortexFrames.length > 0
                && portalVortexFrames[0] != null) {
            portalVortexFrames[0].getTexture().dispose();
        }
        if (portalRuneCircle != null) portalRuneCircle.dispose();

        if (gateFrameModel != null) gateFrameModel.dispose();
        if (gateLeftDoorModel != null) gateLeftDoorModel.dispose();
        if (gateRightDoorModel != null) gateRightDoorModel.dispose();
        if (gateDiffuseTexture != null) gateDiffuseTexture.dispose();


        if (skybox3DRenderer != null) {
            skybox3DRenderer.dispose();
        }
    }
}
