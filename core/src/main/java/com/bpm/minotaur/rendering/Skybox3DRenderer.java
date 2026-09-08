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
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.DayNightManager;
import com.bpm.minotaur.managers.DebugManager;
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
    private Model sunModel;
    private Model moonModel;
    private Model domeModel;

    // Procedural Sky Dome Shader & Transformation
    private ShaderProgram stormShader;
    private final Matrix4 domeTransform = new Matrix4();

    // 3D Model Instances
    private ModelInstance castleInstance;
    private ModelInstance spireInstance;
    private ModelInstance mountainInstance;
    private ModelInstance sunInstance;
    private ModelInstance moonInstance;

    // 3D Horizon Tornado Renderer
    private final ShapeRenderer tornadoRenderer;
    private WeatherType currentWeather = WeatherType.CLEAR;

    // Dynamic Atmosphere & Weather Tracking
    private float totalTime = 0f;
    private boolean isStormy = false;
    private float currentFlash = 0f;
    private final Vector3 sunDir = new Vector3(0.3f, 0.8f, 0.4f).nor();
    private final Vector3 moonDir = new Vector3(-0.3f, -0.8f, -0.4f).nor();
    private final Color skyTint = new Color(0.12f, 0.14f, 0.22f, 1f);
    private final Color horizonFogColor = new Color(0.15f, 0.15f, 0.20f, 1f);
    private final Vector3 tempVec = new Vector3();
    private final Color tempColor = new Color();

    private boolean isInitialized = false;

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

        tornadoRenderer = new ShapeRenderer();

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
            // Castle Tarmin (North: World -Z) - Scaled +50% (1.35 * 1.5 = 2.025f)
            if (Gdx.files.internal("models/skybox/castle_tarmin.obj").exists()) {
                castleModel = loader.loadModel(Gdx.files.internal("models/skybox/castle_tarmin.obj"));
                castleInstance = new ModelInstance(castleModel);
                // Position North at Z = -140 with grounded base
                castleInstance.transform.setToTranslation(0f, -6f, -LANDMARK_DISTANCE);
                castleInstance.transform.scale(2.025f, 2.025f, 2.025f);
            }

            // South Spire (South: World +Z) - Scaled +50% (1.25 * 1.5 = 1.875f)
            if (Gdx.files.internal("models/skybox/south_spire.obj").exists()) {
                spireModel = loader.loadModel(Gdx.files.internal("models/skybox/south_spire.obj"));
                spireInstance = new ModelInstance(spireModel);
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

            // Celestial Sun & Moon
            if (Gdx.files.internal("models/skybox/celestial_sun.obj").exists()) {
                sunModel = loader.loadModel(Gdx.files.internal("models/skybox/celestial_sun.obj"));
                sunInstance = new ModelInstance(sunModel);
            }
            if (Gdx.files.internal("models/skybox/celestial_moon.obj").exists()) {
                moonModel = loader.loadModel(Gdx.files.internal("models/skybox/celestial_moon.obj"));
                moonInstance = new ModelInstance(moonModel);
            }

            // Celestial Sky Dome (Hemisphere for Procedural FBM Cloud & Lightning Shader)
            if (Gdx.files.internal("models/skybox/celestial_dome.obj").exists()) {
                domeModel = loader.loadModel(Gdx.files.internal("models/skybox/celestial_dome.obj"));
            }

            isInitialized = (castleInstance != null && spireInstance != null && domeModel != null);
            Gdx.app.log(TAG, "3D Skybox models successfully loaded. Initialized: " + isInitialized);
        } catch (Throwable t) {
            Gdx.app.error(TAG, "Error loading 3D skybox models: " + t.getMessage(), t);
        }
    }

    /**
     * Updates celestial positions, weather dynamics, and camera alignment.
     */
    public void update(float delta, Player player, WorldManager worldManager) {
        if (!isInitialized || player == null) return;

        totalTime += delta;

        DayNightManager dayNight = (worldManager != null) ? worldManager.getDayNightManager() : null;
        WeatherManager weather   = (worldManager != null) ? worldManager.getWeatherManager() : null;

        // 1. Camera Alignment (Direction tracks player view continuous vector)
        float fwdX = player.getDirectionVector().x;
        float fwdZ = -player.getDirectionVector().y; // Maze Y -> World -Z

        // Micro-parallax translation based on player coordinates
        float camX = player.getPosition().x * PARALLAX_SCALE;
        float camZ = -player.getPosition().y * PARALLAX_SCALE;
        camera.position.set(camX, 0.5f, camZ);
        camera.direction.set(fwdX, 0f, fwdZ).nor();
        camera.up.set(Vector3.Y);
        camera.update();

        // Dome tracks camera position so player is always at center of celestial hemisphere
        domeTransform.idt().setToTranslation(camX, 0.5f, camZ);

        // 2. Weather Dynamics
        isStormy = (weather != null && weather.isStormy());
        currentWeather = (weather != null) ? weather.getCurrentWeather() : WeatherType.CLEAR;
        currentFlash = (weather != null) ? weather.getFlashIntensity() : 0f;

        // 3. Day/Night Lighting & Celestial Disk Positions
        if (dayNight != null) {
            Color currentSky = dayNight.getSkyTint();
            skyTint.set(currentSky);
            horizonFogColor.set(currentSky.r * 0.45f, currentSky.g * 0.45f, currentSky.b * 0.55f, 1f);

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

            // Position Sun Disk
            if (sunInstance != null) {
                sunInstance.transform.setToTranslation(
                        camX + sunDir.x * 200f,
                        sunDir.y * 200f,
                        camZ + sunDir.z * 200f
                );
                sunInstance.transform.scale(2.5f, 2.5f, 2.5f);
            }

            // Position Moon Disk
            if (moonInstance != null) {
                moonInstance.transform.setToTranslation(
                        camX + moonDir.x * 200f,
                        moonDir.y * 200f,
                        camZ + moonDir.z * 200f
                );
                moonInstance.transform.scale(2.0f, 2.0f, 2.0f);
            }
        }
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

    private void renderPass(Viewport viewport, Color skyColor) {
        // Clear color to sky tint & clear depth for 3D horizon pass
        Gdx.gl.glClearColor(skyColor.r * 0.25f, skyColor.g * 0.25f, skyColor.b * 0.35f, 1f);
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
            stormShader.setUniformf("u_flashIntensity", currentFlash);
            stormShader.setUniformf("u_windSpeed", isStormy ? 2.5f : 0.8f);

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
        if (castleInstance   != null) modelBatch.render(castleInstance, environment);
        if (spireInstance    != null) modelBatch.render(spireInstance, environment);

        // Sun & Moon are visible during clear/partly-cloudy skies; occluded during heavy storms
        if (!isStormy) {
            if (sunInstance  != null) modelBatch.render(sunInstance, environment);
            if (moonInstance != null) modelBatch.render(moonInstance, environment);
        }

        modelBatch.end();

        // 3D Horizon Tornado Vortex: rendered in horizon pass behind foreground raycast walls
        if (currentWeather == WeatherType.TORNADO) {
            render3DTornado();
        }

        // Disable depth writing so subsequent 2D raycaster passes draw over the sky
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    private void render3DTornado() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);

        tornadoRenderer.setProjectionMatrix(camera.combined);
        tornadoRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Sweeping orbit in distant horizon (~135 units out)
        float orbitAngle = totalTime * 0.05f;
        float tx = camera.position.x + MathUtils.cos(orbitAngle) * 135f;
        float tz = camera.position.z + MathUtils.sin(orbitAngle) * 135f;

        Color c = (currentFlash > 0.05f) ? Color.WHITE : tempColor.set(0.18f, 0.16f, 0.22f, 0.85f);
        tornadoRenderer.setColor(c);

        int tiers = 28;
        int particlesPerTier = 10;
        float totalH = 92f;

        for (int t = 0; t < tiers; t++) {
            float hRatio = t / (float) tiers;
            float y = -8f + hRatio * totalH;
            float r = 7f + 32f * (float) Math.pow(hRatio, 1.35);
            float spinSpeed = (6f - 3f * hRatio);
            float tierAngle = totalTime * spinSpeed + t * 0.45f;

            for (int p = 0; p < particlesPerTier; p++) {
                float a = tierAngle + (p / (float) particlesPerTier) * MathUtils.PI2;
                float px = tx + MathUtils.cos(a) * r;
                float pz = tz + MathUtils.sin(a) * r;

                // Tangent velocity streak + vertical updraft
                float tangentX = -MathUtils.sin(a) * 3.5f;
                float tangentZ = MathUtils.cos(a) * 3.5f;
                float updraft  = 1.8f;

                tornadoRenderer.line(px, y, pz, px + tangentX, y + updraft, pz + tangentZ);
            }
        }

        tornadoRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void dispose() {
        modelBatch.dispose();
        if (tornadoRenderer != null) tornadoRenderer.dispose();
        if (castleModel   != null) castleModel.dispose();
        if (spireModel    != null) spireModel.dispose();
        if (mountainModel != null) mountainModel.dispose();
        if (sunModel      != null) sunModel.dispose();
        if (moonModel     != null) moonModel.dispose();
        if (domeModel     != null) domeModel.dispose();
        if (stormShader   != null) stormShader.dispose();
    }
}
