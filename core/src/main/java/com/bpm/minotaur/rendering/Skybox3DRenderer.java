package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.DayNightManager;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.managers.DoomManager;
import com.bpm.minotaur.managers.WorldManager;
import com.bpm.minotaur.weather.WeatherManager;
import com.bpm.minotaur.weather.WeatherType;

/**
 * 3D Dynamic Skybox & Horizon Landmark Renderer.
 *
 * <p>Renders real 3D models of Castle Tarmin (North), the South Spire (South),
 * the Horizon Mountain Ring, and celestial bodies (Sun/Moon) against a dynamic
 * procedural 3D storm sky dome running multi-octave FBM fluid clouds and lightning scattering.
 */
public class Skybox3DRenderer {

    private static final String TAG = "Skybox3DRenderer";

    // Horizon Distances
    private static final float LANDMARK_DISTANCE = 140f;
    private static final float PARALLAX_SCALE = 0.05f;

    // Volcanic smoke ceiling (issue #104). The floor is always present regardless of weather;
    // doom thickens it until the sky closes over entirely.
    private static final float SMOKE_FLOOR_BASE = 0.70f;
    private static final float SMOKE_FLOOR_DOOM = 0.92f;

    private final Color zenithTint = new Color(Color.BLACK);
    private float smokeFloor = SMOKE_FLOOR_BASE;
    private float doom01 = 0f;
    private final SkyState worldSkyState = new SkyState();

    private final PerspectiveCamera camera;
    private final ModelBatch modelBatch;
    private final Environment environment;

    private final DirectionalLight keyLight;
    private final DirectionalLight fillLight;
    private final ColorAttribute ambientAttr;

    // Loaded 3D Landmark Models
    private Model castleModel;
    private Model spireModel;
    private Model mountainModel;
    private Model domeModel;

    // Procedural Sky Dome Shader & Transformation
    private ShaderProgram stormShader;
    private final Matrix4 domeTransform = new Matrix4();

    // 3D Model Instances
    private ModelInstance castleInstance;
    private ModelInstance spireInstance;
    private ModelInstance mountainInstance;

    // Shelter beacons: one unit column, stood up once per visible beacon at its real bearing.
    private Model beaconModel;
    private final com.badlogic.gdx.utils.Array<ModelInstance> beaconPool = new com.badlogic.gdx.utils.Array<>();
    private int beaconsShown;

    private WeatherType currentWeather = WeatherType.CLEAR;

    // Dynamic Atmosphere & Weather Tracking
    private float totalTime = 0f;
    private boolean isStormy = false;
    private float currentFlash = 0f;
    /** Seconds since Tarmin's Knell struck, or negative while the sky is quiet (plan K5). */
    private float knellAge = -1f;
    /** How long the knell's lightning crosses and lingers in the sky. */
    static final float KNELL_SECONDS = 3.5f;
    private float knellDirX = 0f;
    private float knellDirZ = -1f;
    private float currentCloudCover = 0.2f;
    private final Vector3 sunDir = new Vector3(0.3f, 0.8f, 0.4f).nor();
    private final Vector3 moonDir = new Vector3(-0.3f, -0.8f, -0.4f).nor();
    private final Color skyTint = new Color(0.12f, 0.14f, 0.22f, 1f);
    private final Color horizonFogColor = new Color(0.15f, 0.15f, 0.20f, 1f);
    private final Vector3 tempVec = new Vector3();
    private final Color tempColor = new Color();

    private boolean isInitialized = false;
    private boolean isInsideHome = false;

    public void setInsideHome(boolean isInsideHome) {
        this.isInsideHome = isInsideHome;
    }

    public Skybox3DRenderer() {
        // Perspective Camera matching game resolution & 70 deg FOV
        camera = new PerspectiveCamera(70f, 1920f, 1080f);
        camera.near = 0.5f;
        camera.far = 800f;

        modelBatch = new ModelBatch();

        environment = new Environment();
        ambientAttr = new ColorAttribute(ColorAttribute.AmbientLight, 0.35f, 0.35f, 0.42f, 1f);
        environment.set(ambientAttr);

        keyLight = new DirectionalLight().set(1f, 0.95f, 0.85f, -0.4f, -0.8f, -0.4f);
        fillLight = new DirectionalLight().set(0.25f, 0.28f, 0.38f, 0.4f, 0.5f, 0.4f);
        environment.add(keyLight);
        environment.add(fillLight);

        initSkyShader();
        loadModels();
    }

    private void initSkyShader() {
        try {
            ShaderProgram.pedantic = false;
            stormShader = new ShaderProgram(
                    Gdx.files.internal("shaders/storm_skydome.vert"),
                    Gdx.files.internal("shaders/storm_skydome.frag")
            );
            if (!stormShader.isCompiled()) {
                Gdx.app.error(TAG, "Storm skydome shader compilation failed:\n" + stormShader.getLog());
            } else {
                Gdx.app.log(TAG, "Procedural storm sky dome shader successfully compiled.");
            }
        } catch (Throwable t) {
            Gdx.app.error(TAG, "Error initializing storm sky shader: " + t.getMessage(), t);
        }
    }

    private void loadModels() {
        ObjLoader loader = new ObjLoader();
        try {
            // Castle Citadel / Tarmin (North: World -Z)
            // castle_tarmin has the better material split (four materials, including MatSlits) but
            // far cruder geometry -- it reads as a stepped black ziggurat, not a castle. The
            // citadel's silhouette is worth more than the slits, so we keep the citadel mesh and
            // blacken it programmatically instead of re-authoring its baked diffuse. See #104.
            String citadelPath = "models/skybox/castle_citadel.obj";
            String castlePath = Gdx.files.internal(citadelPath).exists() ? citadelPath : "models/skybox/castle_tarmin.obj";
            if (Gdx.files.internal(castlePath).exists()) {
                castleModel = loader.loadModel(Gdx.files.internal(castlePath));
                castleInstance = new ModelInstance(castleModel);
                blackenToSilhouette(castleInstance);
                // Position North at Z = -140 with grounded base
                castleInstance.transform.setToTranslation(0f, -6f, -LANDMARK_DISTANCE);
                castleInstance.transform.scale(2.025f, 2.025f, 2.025f);
            }

            // South Spire (South: World +Z) - Scaled +50% (1.25 * 1.5 = 1.875f)
            if (Gdx.files.internal("models/skybox/south_spire.obj").exists()) {
                spireModel = loader.loadModel(Gdx.files.internal("models/skybox/south_spire.obj"));
                spireInstance = new ModelInstance(spireModel);
                blackenToSilhouette(spireInstance);
                // Position South at Z = +140 with grounded base
                spireInstance.transform.setToTranslation(0f, -6f, LANDMARK_DISTANCE);
                spireInstance.transform.scale(1.875f, 1.875f, 1.875f);
            }

            // Mountain Ring (Perimeter)
            if (Gdx.files.internal("models/skybox/mountain_ring.obj").exists()) {
                mountainModel = loader.loadModel(Gdx.files.internal("models/skybox/mountain_ring.obj"));
                mountainInstance = new ModelInstance(mountainModel);
                mountainInstance.transform.setToTranslation(0f, -8f, 0f);
            }

            // The reclaimed storm-cloud meshes are not used as eruption plumes: they are modelled
            // as a horizontal overcast slab, so at any usable scale they read as a lumpy ceiling
            // directly overhead rather than a column rising from the northern ridge. The plumes
            // are procedural instead (see storm_skydome.frag, section 1D).

            // Sun and moon disc meshes are gone: under a permanent smoke ceiling they never
            // resolve into discs, so the dome shader renders them as a diffuse smear instead.

            // Celestial Sky Dome (Hemisphere for Procedural FBM Cloud & Lightning Shader)
            if (Gdx.files.internal("models/skybox/celestial_dome.obj").exists()) {
                domeModel = loader.loadModel(Gdx.files.internal("models/skybox/celestial_dome.obj"));
            }

            buildBeaconModel();

            isInitialized = (castleInstance != null && spireInstance != null && domeModel != null);
            Gdx.app.log(TAG, "3D Skybox models successfully loaded. Initialized: " + isInitialized);
        } catch (Throwable t) {
            Gdx.app.error(TAG, "Error loading 3D skybox models: " + t.getMessage(), t);
        }
    }

    /**
     * How high, in degrees above the horizon, each beacon climbs. Tall enough to clear the
     * maze walls and stand above the top of the view when looked at straight on, so a
     * shelter's fire can be found from deep in the maze.
     */
    private static final float BEACON_SMOKE_ELEVATION = 48f;
    private static final float BEACON_GLOW_ELEVATION = 44f;
    private static final float BEACON_PILLAR_ELEVATION = 62f;

    /** A unit column, base at the origin, for the shelter beacons. Coloured per instance. */
    private void buildBeaconModel() {
        com.badlogic.gdx.graphics.g3d.utils.ModelBuilder mb = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
        mb.begin();
        com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder part = mb.part("beacon", GL20.GL_TRIANGLES,
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                new Material());
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(part, 0f, 0.5f, 0f, 1f, 1f, 1f);
        beaconModel = mb.end();
    }

    private ModelInstance beaconInstance(int i) {
        while (beaconPool.size <= i) {
            ModelInstance inst = new ModelInstance(beaconModel);
            Material m = inst.materials.first();
            m.set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 0.8f));
            m.set(new com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute(GL20.GL_LEQUAL, false));
            beaconPool.add(inst);
        }
        return beaconPool.get(i);
    }

    /**
     * Stands each beacon on its bearing: nearer beacons stand closer and taller.
     *
     * <p>By day a road shelter's beacon is a smoke column in its road's colour; by night the
     * same column burns. A claimed shelter keeps a steady, narrower glow; a seal site is a tall
     * pillar. A road whose seal is won burns dimmed. See docs/DEsign/Requirements_ Shelter Roads.md.
     */
    private void placeBeacons(SkyState state, float camX, float camZ, float brightness) {
        beaconsShown = 0;
        if (beaconModel == null || state.beacons == null) return;
        boolean night = brightness < 0.45f;
        float range = com.bpm.minotaur.generation.WorldConstants.BEACON_RANGE_CHUNKS;
        for (com.bpm.minotaur.gamedata.shelter.BeaconPlanner.Beacon b : state.beacons) {
            float dx = b.getDx();
            float dz = -b.getDy();
            float chunks = (float) Math.sqrt(dx * dx + dz * dz);
            if (chunks < 0.01f) continue;
            float near = 1f - MathUtils.clamp(chunks / range, 0f, 1f);
            float dist = LANDMARK_DISTANCE * (0.45f + 0.5f * (1f - near));
            // Height is set by the angle a column climbs above the horizon, not in units, so a
            // distant beacon still rises clear of the maze walls rather than shrinking into them.
            float width, elevationDeg;
            switch (b.getKind()) {
                case PILLAR: width = 0.022f * dist; elevationDeg = BEACON_PILLAR_ELEVATION; break;
                case GLOW:   width = 0.010f * dist; elevationDeg = BEACON_GLOW_ELEVATION; break;
                default:     width = 0.016f * dist; elevationDeg = BEACON_SMOKE_ELEVATION + 10f * near; break;
            }
            float height = dist * (float) Math.tan(Math.toRadians(elevationDeg));
            Color c = b.getColor();
            // Smoke burns by night and drifts by day; a claimed shelter's glow and a pillar hold steady.
            boolean steady = b.getKind() != com.bpm.minotaur.gamedata.shelter.BeaconPlanner.Kind.SMOKE;
            float glow = night || steady ? 1f : 0.55f;
            if (b.isSpent()) glow *= com.bpm.minotaur.gamedata.shelter.BeaconPalette.SPENT;
            if (!night && b.getKind() == com.bpm.minotaur.gamedata.shelter.BeaconPlanner.Kind.SMOKE) {
                // Daylight smoke: the road's colour through grey.
                c.lerp(com.bpm.minotaur.gamedata.shelter.BeaconPalette.SMOKE, 0.5f);
            }
            ModelInstance inst = beaconInstance(beaconsShown++);
            Material m = inst.materials.first();
            m.set(ColorAttribute.createDiffuse(c.r * 0.2f, c.g * 0.2f, c.b * 0.2f, 1f));
            m.set(ColorAttribute.createEmissive(c.r * glow, c.g * glow, c.b * glow, 1f));
            ((com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute)
                    m.get(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type)).opacity =
                    (night || steady ? 0.85f : 0.6f) * (b.isSpent() ? 0.6f : 1f);
            float dirX = dx / chunks;
            float dirZ = dz / chunks;
            inst.transform.idt()
                    .setToTranslation(camX + dirX * dist, -6f, camZ + dirZ * dist)
                    .scale(width, height, width);
        }
    }

    /**
     * Updates celestial positions, weather dynamics, and camera alignment.
     */
    /** Pins the knell's age for {@link SkyCaptureHarness}, which renders one moment at a time. */
    void setKnellAgeForCapture(float age) {
        knellAge = age;
    }

    /** Tarmin's Knell strikes: lightning ripples out across the sky from the castle (plan K5). */
    public void tollKnell() {
        knellAge = 0f;
    }

    public void update(float delta, Player player, WorldManager worldManager) {
        if (!isInitialized || player == null) return;

        WeatherManager weather = (worldManager != null) ? worldManager.getWeatherManager() : null;
        com.badlogic.gdx.math.GridPoint2 chunk = (worldManager != null)
                ? worldManager.getCurrentPlayerChunkId()
                : new com.badlogic.gdx.math.GridPoint2(0, 0);

        if (viewAligned) {
            // The world camera looks where the player does not always: up at a window, down in
            // a fall. The sky must turn with it, or the castle rides along on the screen.
            worldSkyState.camX = viewPosition.x * PARALLAX_SCALE;
            worldSkyState.camZ = viewPosition.z * PARALLAX_SCALE;
            worldSkyState.forwardX = viewDirection.x;
            worldSkyState.forwardY = viewDirection.y;
            worldSkyState.forwardZ = viewDirection.z;
            worldSkyState.up.set(viewUp);
            viewAligned = false;
        } else {
            worldSkyState.camX = player.getPosition().x * PARALLAX_SCALE;
            worldSkyState.camZ = -player.getPosition().y * PARALLAX_SCALE;
            worldSkyState.forwardX = player.getDirectionVector().x;
            worldSkyState.forwardY = 0f;
            worldSkyState.forwardZ = -player.getDirectionVector().y; // Maze Y -> World -Z
            worldSkyState.up.set(Vector3.Y);
        }
        worldSkyState.dayNight = (worldManager != null) ? worldManager.getDayNightManager() : null;
        worldSkyState.weather = (weather != null) ? weather.getCurrentWeather() : WeatherType.CLEAR;
        worldSkyState.cloudCover = (weather != null) ? weather.getCloudCover() : 0f;
        worldSkyState.flash = (weather != null) ? weather.getFlashIntensity() : 0f;
        worldSkyState.stormy = weather != null && weather.isStormy();
        worldSkyState.doom = MathUtils.clamp(
                DoomManager.getInstance().getBridgeIntegrity() / 100f, 0f, 1f);
        worldSkyState.chunkYProgress = chunk.y + (player.getPosition().y / 16f);
        worldSkyState.chunkX = chunk.x;

        com.badlogic.gdx.math.GridPoint2 site = (worldManager != null && worldManager.getBiomeManager() != null)
                ? worldManager.getBiomeManager().getCastleSite() : null;
        worldSkyState.hasCastleSite = site != null;
        if (site != null) {
            com.bpm.minotaur.gamedata.Maze here = worldManager.getCurrentMaze();
            float w = (here != null && here.getWidth() > 0) ? here.getWidth() : 1f;
            float h = (here != null && here.getHeight() > 0) ? here.getHeight() : 1f;
            // Chunk-space position, so the bearing turns as the player walks across a chunk.
            float px = chunk.x + player.getPosition().x / w - 0.5f;
            float py = chunk.y + player.getPosition().y / h - 0.5f;
            worldSkyState.castleDX = site.x - px;
            worldSkyState.castleDY = site.y - py;
            worldSkyState.inCastleChunk = site.equals(chunk);
        }

        // Shelter beacons, by the same chunk-space position the castle bearing uses.
        worldSkyState.beacons = null;
        if (worldManager != null && worldManager.getBiomeManager() != null
                && worldManager.getBiomeManager().getRoads() != null && worldManager.getCurrentLevel() == 1) {
            com.bpm.minotaur.gamedata.Maze here = worldManager.getCurrentMaze();
            float w = (here != null && here.getWidth() > 0) ? here.getWidth() : 1f;
            float h = (here != null && here.getHeight() > 0) ? here.getHeight() : 1f;
            worldSkyState.beacons = com.bpm.minotaur.gamedata.shelter.BeaconPlanner.visible(
                    worldManager.getBiomeManager(), com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance(),
                    chunk.x + player.getPosition().x / w - 0.5f, chunk.y + player.getPosition().y / h - 0.5f,
                    com.bpm.minotaur.generation.WorldConstants.BEACON_RANGE_CHUNKS);
        }

        updateSky(delta, worldSkyState);
    }

    /**
     * Everything the sky needs to know about the world for one frame.
     *
     * <p>Bundled rather than passed as a dozen loose parameters so the production path and the
     * capture harness cannot drift apart on argument order, and so doom arrives as state rather
     * than being read from a singleton deep inside the update -- which previously made doom
     * impossible to vary from a capture.
     *
     * <p>Mutable and reused per frame: this is filled every frame on the render path.
     */
    public static final class SkyState {
        public float camX;
        public float camZ;
        public float forwardX;
        /** Up or down component of the view; 0 keeps the horizon level. */
        public float forwardY;
        public float forwardZ;
        /** The view's up vector, so the sky rolls with the world camera. */
        public final Vector3 up = new Vector3(Vector3.Y);
        public DayNightManager dayNight;
        public WeatherType weather = WeatherType.CLEAR;
        public float cloudCover;
        public float flash;
        public boolean stormy;
        /** 0 = expedition start, 1 = fully doomed. Drives smoke density and wind speed. */
        public float doom;
        /** Progress north in chunks; the castle closes and grows across the first 25. */
        public float chunkYProgress;
        public int chunkX;
        /**
         * True when the world has a Castle Tarmin site (every non-legacy world). The castle
         * then stands on the bearing to it rather than due north, and the South Spire takes
         * the opposite bearing so the two never overlap.
         */
        public boolean hasCastleSite;
        /** From the player to the castle site, in chunks (x east, y north). */
        public float castleDX;
        public float castleDY;
        /** The player stands in the castle chunk, where the world billboard is drawn. */
        public boolean inCastleChunk;
        /** Shelter and seal-site beacons in range, or null for none. */
        public java.util.List<com.bpm.minotaur.gamedata.shelter.BeaconPlanner.Beacon> beacons;
        /**
         * Pins animation time instead of accumulating it. NaN (the default) means "run normally".
         * Captures set this so cloud drift, ember flicker and heat-lightning land identically on
         * every run -- without it the contact sheet differs every time and is useless as a
         * regression check.
         */
        public float timeOverride = Float.NaN;
    }

    /**
     * Strips a landmark's baked colour and replaces it with blackened volcanic stone.
     *
     * <p>Horizon landmarks are read as shapes against the fire, never as lit surfaces. Any
     * diffuse texture or specular highlight on them only muddies that silhouette.
     */
    private void blackenToSilhouette(ModelInstance instance) {
        for (Material material : instance.materials) {
            material.remove(TextureAttribute.Diffuse);
            material.set(ColorAttribute.createDiffuse(0.050f, 0.042f, 0.055f, 1f));
            material.set(ColorAttribute.createSpecular(0f, 0f, 0f, 1f));

            // Apertures that are meant to glow keep their authored emissive. Blanket-setting it
            // here would erase exactly the lava slits and chevrons the materials exist to carry.
            ColorAttribute emissive = (ColorAttribute) material.get(ColorAttribute.Emissive);
            boolean alreadyGlows = emissive != null
                    && (emissive.color.r + emissive.color.g + emissive.color.b) > 0.45f;
            if (!alreadyGlows) {
                // A trace of emissive keeps stone from going flat black against a dark zenith.
                material.set(ColorAttribute.createEmissive(0.10f, 0.030f, 0.010f, 1f));
            }
        }
    }

    /**
     * Drives the sky from explicit state rather than from a live world.
     *
     * <p>Separating this from {@link #update(float, Player, WorldManager)} lets the sky be
     * rendered without a running game -- which is what the capture harness uses to produce
     * regression screenshots across time-of-day, weather and doom states.
     */
    public void updateSky(float delta, SkyState state) {
        if (!isInitialized) return;

        if (Float.isNaN(state.timeOverride)) {
            totalTime += delta;
        } else {
            totalTime = state.timeOverride;
        }

        float camX = state.camX;
        float camZ = state.camZ;
        float fwdX = state.forwardX;
        float fwdZ = state.forwardZ;
        DayNightManager dayNight = state.dayNight;

        // 1. Camera Alignment (Direction tracks player view continuous vector)
        camera.position.set(camX, 0.5f, camZ);
        camera.direction.set(fwdX, state.forwardY, fwdZ).nor();
        camera.up.set(state.up);
        camera.update();

        // Dome tracks camera position so player is always at center of celestial hemisphere
        domeTransform.idt().setToTranslation(camX, 0.5f, camZ);

        // 2. Weather Dynamics
        isStormy = state.stormy;
        currentWeather = state.weather;
        currentFlash = state.flash;
        if (knellAge >= 0f) {
            knellAge += delta;
            if (knellAge > KNELL_SECONDS) knellAge = -1f;
        }
        if (state.hasCastleSite) {
            // Maze Y runs to world -Z, as the camera's does.
            float len = (float) Math.sqrt(state.castleDX * state.castleDX + state.castleDY * state.castleDY);
            if (len > 1e-3f) {
                knellDirX = state.castleDX / len;
                knellDirZ = -state.castleDY / len;
            }
        }
        currentCloudCover = state.cloudCover;

        // 2B. Doom drives the sky harder than weather does. At zero doom the sky already matches
        // the reference art; at full doom the smoke closes over completely.
        doom01 = MathUtils.clamp(state.doom, 0f, 1f);
        smokeFloor = MathUtils.lerp(SMOKE_FLOOR_BASE, SMOKE_FLOOR_DOOM, doom01);

        // 3. Day/Night Lighting & Celestial Disk Positions
        if (dayNight != null) {
            Color currentSky = dayNight.getSkyTint();
            skyTint.set(currentSky);
            zenithTint.set(dayNight.getZenithTint());
            // The horizon is the hottest part of the sky, so it keeps far more of the tint than
            // the old cool-shifted fog did.
            horizonFogColor.set(currentSky.r * 0.95f, currentSky.g * 0.55f, currentSky.b * 0.45f, 1f);

            if (currentWeather == WeatherType.TORNADO) {
                // Distinct sickly greenish-dark supercell atmosphere
                skyTint.lerp(new Color(0.18f, 0.25f, 0.16f, 1f), 0.70f);
                horizonFogColor.set(0.24f, 0.30f, 0.20f, 1f);
            }

            float brightness = dayNight.getBrightness();

            // Key light color & direction
            dayNight.getSunDirection(sunDir);
            dayNight.getMoonDirection(moonDir);
            keyLight.direction.set(sunDir.x, -sunDir.y, sunDir.z).nor();

            Color keyColor = dayNight.getDirectionalLightColor(tempColor);

            // Storm Dimming & Lightning Flash
            if (currentFlash > 0.05f) {
                // Lightning Flash: brilliant white sky burst silhouetting towers
                ambientAttr.color.set(0.95f, 0.95f, 1.0f, 1f);
                keyLight.color.set(1.5f, 1.5f, 1.8f, 1f);
            } else {
                float stormDim = isStormy ? 0.35f : 1.0f;
                ambientAttr.color.set(
                        skyTint.r * 0.4f * stormDim,
                        skyTint.g * 0.4f * stormDim,
                        skyTint.b * 0.5f * stormDim,
                        1f
                );
                keyLight.color.set(
                        keyColor.r * brightness * stormDim,
                        keyColor.g * brightness * stormDim,
                        keyColor.b * brightness * stormDim,
                        1f
                );
            }

        }

        placeBeacons(state, camX, camZ, dayNight != null ? dayNight.getBrightness() : 1f);

        castleHidden = false;
        if (castleInstance != null && state.hasCastleSite) {
            placeOnCastleBearing(state, camX, camZ);
        } else if (castleInstance != null) {
            // Legacy worlds: the castle has no site, so it stays due north.
            float chunkY = state.chunkYProgress;
            // Reduced progress rate towards Castle Tarmin by 80% (paced over 25 chunks north)
            float northProgress = Math.min(Math.max(chunkY / 25.0f, 0f), 1.0f);
            float currentDist = LANDMARK_DISTANCE - (northProgress * 55f); // 140f down to 85f
            float currentScale = 2.025f * (1.0f + (northProgress * 0.85f));
            float castleX = (state.chunkX * 2.5f);
            castleInstance.transform.idt()
                    .setToTranslation(camX + castleX, -6f, -currentDist)
                    .scale(currentScale, currentScale, currentScale);
        }
    }

    /** Set when the player stands in the castle chunk, where the world billboard takes over. */
    private boolean castleHidden = false;

    /** Chunks over which the castle closes and grows; beyond this it sits at the horizon. */
    private static final float CASTLE_APPROACH_CHUNKS = 50f;

    /**
     * Stands the castle on the bearing to its site, nearer and larger as the player
     * closes in, facing them; and puts the South Spire on the opposite bearing.
     * Maze y is world -Z, so the site's +y (north) is -Z here.
     */
    private void placeOnCastleBearing(SkyState state, float camX, float camZ) {
        float dx = state.castleDX;
        float dz = -state.castleDY;
        float chunksAway = (float) Math.sqrt(dx * dx + dz * dz);

        // Inside the castle chunk the billboard in the world is the castle. Only there:
        // the billboard is not drawn from neighbouring chunks, so the sky keeps it until then.
        if (state.inCastleChunk) {
            castleHidden = true;
            return;
        }
        if (chunksAway < 0.01f) {
            return;
        }
        float dirX = dx / chunksAway;
        float dirZ = dz / chunksAway;

        float progress = MathUtils.clamp(1f - (chunksAway - 1f) / CASTLE_APPROACH_CHUNKS, 0f, 1f);
        float dist = LANDMARK_DISTANCE - progress * 55f;
        float scale = 2.025f * (1.0f + progress * 0.85f);
        // The model was authored facing +Z (seen from the south); turn that face toward the camera.
        float yaw = MathUtils.atan2(-dirX, -dirZ) * MathUtils.radiansToDegrees;

        castleInstance.transform.idt()
                .setToTranslation(camX + dirX * dist, -6f, camZ + dirZ * dist)
                .rotate(0f, 1f, 0f, yaw)
                .scale(scale, scale, scale);

        if (spireInstance != null) {
            spireInstance.transform.idt()
                    .setToTranslation(camX - dirX * LANDMARK_DISTANCE, -6f, camZ - dirZ * LANDMARK_DISTANCE)
                    .rotate(0f, 1f, 0f, yaw + 180f)
                    .scale(1.875f, 1.875f, 1.875f);
        }
    }

    private boolean viewAligned;
    private final Vector3 viewPosition = new Vector3();
    private final Vector3 viewDirection = new Vector3();
    private final Vector3 viewUp = new Vector3();

    /**
     * Points the sky's camera the way the world camera points, for the next render. Without
     * it the sky faces the player's heading with a level horizon, which is wrong whenever the
     * world camera looks elsewhere.
     */
    public void alignTo(Vector3 position, Vector3 direction, Vector3 up) {
        viewPosition.set(position);
        viewDirection.set(direction);
        viewUp.set(up);
        viewAligned = true;
    }

    /**
     * Renders the 3D horizon scene.
     */
    public void render(SpriteBatch spriteBatch, Player player, Viewport viewport,
                       WorldManager worldManager, DebugManager.RenderMode renderMode) {
        if (!isInitialized) return;

        update(Gdx.graphics.getDeltaTime(), player, worldManager);

        DayNightManager dayNight = (worldManager != null) ? worldManager.getDayNightManager() : null;
        Color skyColor = (dayNight != null) ? dayNight.getSkyTint() : Color.NAVY;

        renderPass(viewport, skyColor);
    }

    /**
     * Renders the sky from explicit state, for capture and regression screenshots.
     *
     */
    public void renderDirect(Viewport viewport, SkyState state, float delta) {
        if (!isInitialized) return;
        updateSky(delta, state);
        renderPass(viewport, (state.dayNight != null) ? state.dayNight.getSkyTint() : Color.NAVY);
    }

    private void renderPass(Viewport viewport, Color skyColor) {
        // Clear color to sky tint & clear depth for 3D horizon pass
        // Clear to the zenith colour: anything the dome fails to cover should read as choked sky,
        // never as a pale wash.
        Gdx.gl.glClearColor(zenithTint.r, zenithTint.g, zenithTint.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        viewport.apply();

        camera.fieldOfView = DebugManager.getInstance().getFov3d();
        camera.viewportWidth = viewport.getWorldWidth();
        camera.viewportHeight = viewport.getWorldHeight();
        camera.update();

        // --- PASS 1: PROCEDURAL STORM SKY DOME ---
        // Rendered with depth testing disabled so it acts as an infinite background
        if (domeModel != null && stormShader != null && stormShader.isCompiled()) {
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDepthMask(false);

            stormShader.bind();
            stormShader.setUniformMatrix("u_projTrans", camera.combined);
            stormShader.setUniformMatrix("u_worldTrans", domeTransform);
            stormShader.setUniformf("u_cameraPos", camera.position.x, camera.position.y, camera.position.z);
            stormShader.setUniformf("u_time", totalTime);
            stormShader.setUniformf("u_sunDir", sunDir.x, sunDir.y, sunDir.z);
            stormShader.setUniformf("u_moonDir", moonDir.x, moonDir.y, moonDir.z);
            stormShader.setUniformf("u_skyTint", skyTint.r, skyTint.g, skyTint.b);
            stormShader.setUniformf("u_horizonColor", horizonFogColor.r, horizonFogColor.g, horizonFogColor.b);
            stormShader.setUniformf("u_stormIntensity", isStormy ? 1.0f : 0.2f);
            stormShader.setUniformf("u_cloudCover", currentCloudCover);
            stormShader.setUniformf("u_smokeFloor", smokeFloor);
            stormShader.setUniformf("u_doom", doom01);
            stormShader.setUniformf("u_knellAge", knellAge);
            stormShader.setUniformf("u_knellDir", knellDirX, knellDirZ);
            stormShader.setUniformf("u_zenithColor", zenithTint.r, zenithTint.g, zenithTint.b);
            stormShader.setUniformf("u_flashIntensity", currentFlash);
            // Doom drives the whole sky faster, not just darker.
            stormShader.setUniformf("u_windSpeed", (isStormy ? 2.5f : 0.8f) * (1f + doom01));

            for (int i = 0; i < domeModel.meshes.size; i++) {
                domeModel.meshes.get(i).render(stormShader, GL20.GL_TRIANGLES);
            }
        }

        // --- PASS 2: 3D LANDMARK TOWERS & HORIZON MOUNTAINS ---
        // Rendered with depth test enabled so landmarks stand in silhouette against the sky dome
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);

        if (mountainInstance != null) modelBatch.render(mountainInstance, environment);
        if (castleInstance   != null && !castleHidden) modelBatch.render(castleInstance, environment);
        if (spireInstance    != null) modelBatch.render(spireInstance, environment);
        for (int i = 0; i < beaconsShown; i++) {
            modelBatch.render(beaconPool.get(i), environment);
        }

        modelBatch.end();

        // Clear depth buffer so subsequent scene passes (World3D mesh or 2D raycaster)
        // always render cleanly OVER the skybox and landmarks.
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void dispose() {
        modelBatch.dispose();
        if (castleModel   != null) castleModel.dispose();
        if (spireModel    != null) spireModel.dispose();
        if (mountainModel != null) mountainModel.dispose();
        if (domeModel     != null) domeModel.dispose();
        if (beaconModel   != null) beaconModel.dispose();
        if (stormShader   != null) stormShader.dispose();
    }
}
