package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
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
    // The war's smoke (Living War W10): soft, unlit billboards stacked into a plume per fight.
    private com.badlogic.gdx.graphics.g3d.decals.DecalBatch smokeBatch;
    private Texture smokeTexture;
    private final com.badlogic.gdx.utils.Array<com.badlogic.gdx.graphics.g3d.decals.Decal> smokePool = new com.badlogic.gdx.utils.Array<>();
    private int smokeShown;

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

    /** Fronts this far off still raise smoke over the horizon (W10). */
    private static final int WAR_SMOKE_RANGE = 24;
    private static final int WAR_SMOKE_MAX = 12;
    private static final float WAR_SMOKE_ELEVATION = 30f;
    private float smokeTime;

    /** Every fight under way that the player could see the smoke of: fronts, skirmishes, raids, far battles. */
    private void gatherWarSmoke(com.bpm.minotaur.managers.WorldManager worldManager, com.badlogic.gdx.math.GridPoint2 chunk,
            com.bpm.minotaur.gamedata.player.Player player) {
        worldSkyState.warSmokeCount = 0;
        if (worldManager == null || worldManager.getCurrentLevel() != 1 || chunk == null) return;
        if (worldSkyState.warSmoke == null) worldSkyState.warSmoke = new float[WAR_SMOKE_MAX * 2];
        com.bpm.minotaur.gamedata.Maze here = worldManager.getCurrentMaze();
        float w = (here != null && here.getWidth() > 0) ? here.getWidth() : 1f;
        float h = (here != null && here.getHeight() > 0) ? here.getHeight() : 1f;
        float px = chunk.x + player.getPosition().x / w - 0.5f;
        float py = chunk.y + player.getPosition().y / h - 0.5f;
        long clock = worldManager.getHistory().warClock();
        worldSkyState.volleyCount = 0;
        worldSkyState.beaconLit = 0;
        if (worldSkyState.volleys == null) worldSkyState.volleys = new float[WAR_SMOKE_MAX * 2];
        if (worldSkyState.beacons3 == null) worldSkyState.beacons3 = new float[BEACON_CHAIN * 2];
        com.badlogic.gdx.math.GridPoint2 me = new com.badlogic.gdx.math.GridPoint2(chunk);
        for (com.bpm.minotaur.gamedata.history.war.Front f : worldManager.currentFronts()) {
            addSmoke(f.center, px, py, WAR_SMOKE_RANGE);
            if (Math.max(0, com.bpm.minotaur.gamedata.history.war.EncounterScheduler.distance(me, f.center)
                    - com.bpm.minotaur.gamedata.history.war.Front.RADIUS) <= 1) addVolley(f.center, px, py);
        }
        for (com.bpm.minotaur.gamedata.history.war.Encounter e : worldManager.currentEncounters()) {
            if (!e.fighting()) continue;
            com.badlogic.gdx.math.GridPoint2 at = e.chunkAt(clock);
            addSmoke(at, px, py, com.bpm.minotaur.gamedata.history.war.EncounterScheduler.EARSHOT);
            if (e.kind != com.bpm.minotaur.gamedata.history.war.Encounter.Kind.DISTANT
                    && com.bpm.minotaur.gamedata.history.war.EncounterScheduler.distance(me, at) <= 1) addVolley(at, px, py);
            if (e.kind == com.bpm.minotaur.gamedata.history.war.Encounter.Kind.BATTLE) lightBeacons(worldManager, e, clock, px, py);
        }
    }

    /** Beacons in a chain, one lit every few turns, from the mustering house's seat to its battle (W10.3). */
    private static final int BEACON_CHAIN = 4;
    private static final int BEACON_TURNS = 3;

    private void lightBeacons(com.bpm.minotaur.managers.WorldManager worldManager, com.bpm.minotaur.gamedata.history.war.Encounter e,
            long clock, float px, float py) {
        com.bpm.minotaur.gamedata.history.war.SeatMap seats = worldManager.houseSeats();
        com.badlogic.gdx.math.GridPoint2 seat = seats != null ? seats.seat(e.houseA) : null;
        if (seat == null) return;
        com.badlogic.gdx.math.GridPoint2 to = e.chunkAt(clock);
        int lit = (int) Math.min(BEACON_CHAIN, (clock - e.start) / BEACON_TURNS + 1);
        for (int i = 0; i < lit && worldSkyState.beaconLit < BEACON_CHAIN; i++) {
            float t = i / (float) BEACON_CHAIN;
            int k = worldSkyState.beaconLit++;
            worldSkyState.beacons3[2 * k] = seat.x + (to.x - seat.x) * t - px;
            worldSkyState.beacons3[2 * k + 1] = seat.y + (to.y - seat.y) * t - py;
        }
    }

    private void addVolley(com.badlogic.gdx.math.GridPoint2 at, float px, float py) {
        if (worldSkyState.volleyCount >= WAR_SMOKE_MAX) return;
        int i = worldSkyState.volleyCount++;
        worldSkyState.volleys[2 * i] = at.x - px;
        worldSkyState.volleys[2 * i + 1] = at.y - py;
    }

    private void addSmoke(com.badlogic.gdx.math.GridPoint2 at, float px, float py, int range) {
        if (worldSkyState.warSmokeCount >= WAR_SMOKE_MAX) return;
        float dx = at.x - px;
        float dy = at.y - py;
        // The fight in the player's own chunk is on the ground, not the horizon.
        if (Math.max(Math.abs(dx), Math.abs(dy)) < 0.75f || Math.hypot(dx, dy) > range) return;
        int i = worldSkyState.warSmokeCount++;
        worldSkyState.warSmoke[2 * i] = dx;
        worldSkyState.warSmoke[2 * i + 1] = dy;
    }

    /** Puffs per plume, bottom to top: each wider, fainter and further downwind than the last. */
    private static final int PLUME_PUFFS = 11;

    /** A soft puff of smoke, white, for tinting: dense in the middle, ragged at the edges. */
    private Texture smokeTexture() {
        if (smokeTexture != null) return smokeTexture;
        int w = 64, h = 64;
        com.badlogic.gdx.graphics.Pixmap px = new com.badlogic.gdx.graphics.Pixmap(w, h, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        px.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None);
        java.util.Random r = new java.util.Random(0x5E0CEL);
        float[] lumps = new float[12 * 3];
        for (int i = 0; i < 12; i++) {
            lumps[3 * i] = 0.3f + 0.4f * r.nextFloat();
            lumps[3 * i + 1] = 0.3f + 0.4f * r.nextFloat();
            lumps[3 * i + 2] = 0.12f + 0.14f * r.nextFloat();
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float u = (x + 0.5f) / w, v = (y + 0.5f) / h;
                float a = 0f;
                for (int i = 0; i < 12; i++) {
                    float dx = u - lumps[3 * i], dy = v - lumps[3 * i + 1], rr = lumps[3 * i + 2];
                    a += (float) Math.exp(-(dx * dx + dy * dy) / (rr * rr));
                }
                float edge = (float) Math.exp(-((u - 0.5f) * (u - 0.5f) + (v - 0.5f) * (v - 0.5f)) / 0.06f);
                a = MathUtils.clamp(a * 0.35f * edge * 1.6f, 0f, 1f);
                px.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, a));
            }
        }
        smokeTexture = new Texture(px);
        smokeTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        px.dispose();
        return smokeTexture;
    }

    private com.badlogic.gdx.graphics.g3d.decals.Decal smokePuff(int i) {
        while (smokePool.size <= i) {
            com.badlogic.gdx.graphics.g3d.decals.Decal d = com.badlogic.gdx.graphics.g3d.decals.Decal.newDecal(1f, 1f,
                    new com.badlogic.gdx.graphics.g2d.TextureRegion(smokeTexture()), true);
            smokePool.add(d);
        }
        return smokePool.get(i);
    }

    /**
     * Stands a plume on the bearing of each fight (W10): soot-black by day, lit orange from below by
     * night, leaning downwind as it climbs. Nearer fights stand closer and wider.
     */
    private void placeWarSmoke(SkyState state, float camX, float camZ, float brightness, float delta) {
        smokeShown = 0;
        smokeTime = Float.isNaN(state.timeOverride) ? smokeTime + delta : state.timeOverride;
        if (state.warSmoke == null) return;
        boolean night = brightness < 0.45f;
        for (int k = 0; k < state.warSmokeCount; k++) {
            float dx = state.warSmoke[2 * k];
            float dz = -state.warSmoke[2 * k + 1];
            float chunks = (float) Math.sqrt(dx * dx + dz * dz);
            if (chunks < 0.01f) continue;
            float near = 1f - MathUtils.clamp(chunks / WAR_SMOKE_RANGE, 0f, 1f);
            float dist = LANDMARK_DISTANCE * (0.42f + 0.5f * (1f - near));
            float base = (0.075f + 0.06f * near) * dist;
            float height = dist * (float) Math.tan(Math.toRadians(WAR_SMOKE_ELEVATION + 12f * near));
            float dirX = dx / chunks;
            float dirZ = dz / chunks;
            float sideX = -dirZ;
            float sideZ = dirX;
            for (int p = 0; p < PLUME_PUFFS; p++) {
                float t = p / (float) (PLUME_PUFFS - 1);
                // Each puff rises and widens on its own clock, so the column churns rather than sits.
                float churn = (smokeTime * 0.08f + k * 0.37f + p * 0.21f) % 1f;
                float rise = MathUtils.clamp(t + churn * (1f / PLUME_PUFFS), 0f, 1f);
                float size = base * (1f + 1.8f * rise);
                float lean = base * 3.5f * rise * rise * (0.8f + 0.2f * MathUtils.sin(smokeTime * 0.3f + k));
                com.badlogic.gdx.graphics.g3d.decals.Decal d = smokePuff(smokeShown++);
                d.setDimensions(size, size * 1.1f);
                d.setPosition(camX + dirX * dist + sideX * lean, -6f + size * 0.4f + rise * height, camZ + dirZ * dist + sideZ * lean);
                float alpha = (0.8f - 0.55f * rise) * (0.65f + 0.35f * near);
                if (night) {
                    float fire = MathUtils.clamp(1f - rise * 2.2f, 0f, 1f);
                    d.setColor(0.10f + 0.85f * fire, 0.06f + 0.33f * fire, 0.05f + 0.08f * fire, alpha);
                } else {
                    d.setColor(0.07f, 0.06f, 0.055f, alpha);
                }
                d.lookAt(camera.position, camera.up);
            }
            if (night) {
                // The fire under the smoke lights the horizon (W10.2).
                com.badlogic.gdx.graphics.g3d.decals.Decal glow = smokePuff(smokeShown++);
                glow.setDimensions(base * 5f, base * 1.4f);
                glow.setPosition(camX + dirX * dist, -6f + base * 0.3f, camZ + dirZ * dist);
                glow.setColor(1f, 0.42f, 0.12f, 0.45f * (0.6f + 0.4f * near));
                glow.lookAt(camera.position, camera.up);
            }
        }
        placeVolleys(state, camX, camZ);
        placeBeaconChain(state, camX, camZ, night);
    }

    /** Arrows per flight, and seconds a flight takes to cross. */
    private static final int VOLLEY_ARROWS = 14;
    private static final float VOLLEY_SECONDS = 2.6f;
    private static final float VOLLEY_EVERY = 4.5f;

    /** Dark flights of arrows arcing over a fight next door (W10.4), one after another. */
    private void placeVolleys(SkyState state, float camX, float camZ) {
        if (state.volleys == null) return;
        for (int k = 0; k < state.volleyCount; k++) {
            float dx = state.volleys[2 * k];
            float dz = -state.volleys[2 * k + 1];
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            float dirX = len < 0.01f ? 0f : dx / len;
            float dirZ = len < 0.01f ? -1f : dz / len;
            float phase = (smokeTime + k * 1.3f) % VOLLEY_EVERY;
            if (phase > VOLLEY_SECONDS) continue;
            float t = phase / VOLLEY_SECONDS;
            float dist = LANDMARK_DISTANCE * 0.35f;
            float sideX = -dirZ, sideZ = dirX;
            float span = dist * 0.5f;
            float apex = dist * 0.55f;
            for (int a = 0; a < VOLLEY_ARROWS; a++) {
                // Each arrow its own moment and lane in the flight, so it reads as a volley, not a streak.
                float jitter = ((a * 37) % 11) / 11f - 0.5f;
                float lane = ((a * 53) % 13) / 13f - 0.5f;
                float ta = MathUtils.clamp(t + jitter * 0.3f, 0f, 1f);
                float along = (ta - 0.5f) * span + lane * span * 0.25f;
                float height = 4f * apex * ta * (1f - ta) * (0.85f + 0.3f * (lane + 0.5f));
                com.badlogic.gdx.graphics.g3d.decals.Decal d = smokePuff(smokeShown++);
                float size = dist * 0.011f;
                d.setDimensions(size * 2.4f, size * 0.7f);
                d.setPosition(camX + dirX * (dist + lane * dist * 0.3f) + sideX * along,
                        -6f + height, camZ + dirZ * (dist + lane * dist * 0.3f) + sideZ * along);
                d.setColor(0.03f, 0.025f, 0.02f, 0.95f);
                d.lookAt(camera.position, camera.up);
            }
        }
    }

    /** A chain of beacon fires toward a mustering battle (W10.3), the newest the brightest. */
    private void placeBeaconChain(SkyState state, float camX, float camZ, boolean night) {
        if (state.beacons3 == null) return;
        for (int k = 0; k < state.beaconLit; k++) {
            float dx = state.beacons3[2 * k];
            float dz = -state.beacons3[2 * k + 1];
            float chunks = (float) Math.sqrt(dx * dx + dz * dz);
            if (chunks < 0.5f) continue;
            float near = 1f - MathUtils.clamp(chunks / WAR_SMOKE_RANGE, 0f, 1f);
            float dist = LANDMARK_DISTANCE * (0.42f + 0.5f * (1f - near));
            float flicker = 0.85f + 0.15f * MathUtils.sin(smokeTime * 9f + k * 2.1f);
            float size = dist * (0.045f + 0.025f * near);
            com.badlogic.gdx.graphics.g3d.decals.Decal d = smokePuff(smokeShown++);
            d.setDimensions(size, size * 1.6f);
            d.setPosition(camX + dx / chunks * dist, -6f + size * 0.9f, camZ + dz / chunks * dist);
            d.setColor(1f, 0.6f + 0.2f * flicker, 0.2f, (night ? 0.95f : 0.7f) * flicker);
            d.lookAt(camera.position, camera.up);
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
        gatherWarSmoke(worldManager, chunk, player);

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
        /**
         * Where the war is burning (Living War W10): chunk offsets from the player as x,y pairs
         * (x east, y north), {@link #warSmokeCount} of them. Null for none.
         */
        public float[] warSmoke;
        public int warSmokeCount;
        /** Fights close enough to see their arrows over the walls (W10.4): offsets as x,y pairs. */
        public float[] volleys;
        public int volleyCount;
        /** A house's beacons lit toward the battle it musters for (W10.3): offsets as x,y pairs, the lit ones. */
        public float[] beacons3;
        public int beaconLit;
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
        placeWarSmoke(state, camX, camZ, dayNight != null ? dayNight.getBrightness() : 1f, delta);

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
        if (smokeShown > 0) {
            if (smokeBatch == null) {
                smokeBatch = new com.badlogic.gdx.graphics.g3d.decals.DecalBatch(
                        new com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy(camera));
            }
            // Colour only: the smoke must not thin the frame's alpha, or a capture shows sky through it.
            Gdx.gl.glDepthMask(false);
            Gdx.gl.glColorMask(true, true, true, false);
            for (int i = 0; i < smokeShown; i++) smokeBatch.add(smokePool.get(i));
            smokeBatch.flush();
            Gdx.gl.glColorMask(true, true, true, true);
            Gdx.gl.glDepthMask(true);
        }

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
        if (smokeBatch != null) smokeBatch.dispose();
        if (smokeTexture != null) smokeTexture.dispose();
        if (stormShader   != null) stormShader.dispose();
    }
}
