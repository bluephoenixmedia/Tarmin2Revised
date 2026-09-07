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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.DayNightManager;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.managers.WorldManager;
import com.bpm.minotaur.weather.WeatherManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 3D Dynamic Skybox & Horizon Landmark Renderer.
 *
 * <p>Renders real 3D models of Castle Tarmin (North), the South Spire (South),
 * the Horizon Mountain Ring, West Cumulus Anvil Cloud, and celestial bodies (Sun/Moon).
 *
 * <p>Features:
 * <ul>
 *   <li>Subtle micro-parallax based on player grid coordinates.</li>
 *   <li>24-hour day/night celestial orbit with orbital lighting via {@link DayNightManager}.</li>
 *   <li>Dynamic storm cloud acceleration and lightning silhouetting via {@link WeatherManager}.</li>
 *   <li>Direct perspective horizon pass supporting both Modern and Retro CRT pipelines.</li>
 * </ul>
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

    // Loaded 3D Models
    private Model castleModel;
    private Model spireModel;
    private Model mountainModel;
    private Model cumulusModel;
    private Model sunModel;
    private Model moonModel;
    private Model stormCloudsUpperModel;
    private Model stormCloudsLowerModel;

    // 3D Model Instances
    private ModelInstance castleInstance;
    private ModelInstance spireInstance;
    private ModelInstance mountainInstance;
    private ModelInstance cumulusInstance;
    private ModelInstance sunInstance;
    private ModelInstance moonInstance;
    private ModelInstance stormCloudsUpperInstance;
    private ModelInstance stormCloudsLowerInstance;

    // Dynamic Cloud Drift & Rotations
    private float cloudOffset = 0f;
    private float upperCloudRotation = 0f;
    private float lowerCloudRotation = 0f;
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

        loadModels();
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

            // West Cumulus Anvil Cloud (West: World -X)
            if (Gdx.files.internal("models/skybox/west_cumulus.obj").exists()) {
                cumulusModel = loader.loadModel(Gdx.files.internal("models/skybox/west_cumulus.obj"));
                cumulusInstance = new ModelInstance(cumulusModel);
                cumulusInstance.transform.setToTranslation(-LANDMARK_DISTANCE * 0.95f, 2f, 0f);
                cumulusInstance.transform.rotate(Vector3.Y, 90f);
                cumulusInstance.transform.scale(1.2f, 1.2f, 1.2f);
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

            // Upper Overhead Storm Cloud Canopy (360-degree overcast ceiling)
            if (Gdx.files.internal("models/skybox/storm_clouds_upper.obj").exists()) {
                stormCloudsUpperModel = loader.loadModel(Gdx.files.internal("models/skybox/storm_clouds_upper.obj"));
                stormCloudsUpperInstance = new ModelInstance(stormCloudsUpperModel);
            }

            // Lower Horizon Storm Cloud Deck & Scud
            if (Gdx.files.internal("models/skybox/storm_clouds_lower.obj").exists()) {
                stormCloudsLowerModel = loader.loadModel(Gdx.files.internal("models/skybox/storm_clouds_lower.obj"));
                stormCloudsLowerInstance = new ModelInstance(stormCloudsLowerModel);
            }

            isInitialized = (castleInstance != null && spireInstance != null);
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

        DayNightManager dayNight = worldManager.getDayNightManager();
        WeatherManager weather   = worldManager.getWeatherManager();

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

        // 2. Cloud Drift Velocity & Storm Acceleration
        float driftSpeed = 0.6f;
        if (weather != null && weather.isStormy()) {
            driftSpeed = 2.8f; // Gale force surge during storm
        }
        cloudOffset += driftSpeed * delta;
        upperCloudRotation += driftSpeed * 0.35f * delta;
        lowerCloudRotation += driftSpeed * 0.85f * delta;

        // Dynamic multi-layer cloud positioning - elevated high into the sky dome
        if (stormCloudsUpperInstance != null) {
            stormCloudsUpperInstance.transform.setToTranslation(camX, 22f, camZ);
            stormCloudsUpperInstance.transform.rotate(Vector3.Y, upperCloudRotation);
        }

        if (stormCloudsLowerInstance != null) {
            stormCloudsLowerInstance.transform.setToTranslation(camX, 10f, camZ);
            stormCloudsLowerInstance.transform.rotate(Vector3.Y, lowerCloudRotation);
        }

        if (cumulusInstance != null) {
            // Subtle bobbing & wind drift for western anvil cloud
            float wobble = MathUtils.sin(cloudOffset * 0.4f) * 1.5f;
            cumulusInstance.transform.setToTranslation(-LANDMARK_DISTANCE * 0.95f, 16f + wobble, 0f);
            cumulusInstance.transform.rotate(Vector3.Y, 90f);
            cumulusInstance.transform.scale(1.2f, 1.2f, 1.2f);
        }

        // 3. Day/Night Lighting & Celestial Disk Positions
        if (dayNight != null) {
            Color skyTint = dayNight.getSkyTint();
            float brightness = dayNight.getBrightness();

            // Key light color & direction
            dayNight.getSunDirection(tempVec);
            keyLight.direction.set(tempVec.x, -tempVec.y, tempVec.z).nor();

            Color keyColor = dayNight.getDirectionalLightColor(tempColor);

            // Storm Dimming & Lightning Flash
            float flash = (weather != null) ? weather.getFlashIntensity() : 0f;
            if (flash > 0.05f) {
                // Lightning Flash: brilliant white sky burst silhouetting towers
                ambientAttr.color.set(0.95f, 0.95f, 1.0f, 1f);
                keyLight.color.set(1.5f, 1.5f, 1.8f, 1f);
            } else {
                float stormDim = (weather != null && weather.isStormy()) ? 0.45f : 1.0f;
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

            // Position Sun Disk (opposite direction of light)
            if (sunInstance != null) {
                dayNight.getSunDirection(tempVec);
                sunInstance.transform.setToTranslation(
                        camX + tempVec.x * 200f,
                        tempVec.y * 200f,
                        camZ + tempVec.z * 200f
                );
                sunInstance.transform.scale(2.5f, 2.5f, 2.5f);
            }

            // Position Moon Disk
            if (moonInstance != null) {
                dayNight.getMoonDirection(tempVec);
                moonInstance.transform.setToTranslation(
                        camX + tempVec.x * 200f,
                        tempVec.y * 200f,
                        camZ + tempVec.z * 200f
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
        Gdx.gl.glClearColor(skyColor.r * 0.35f, skyColor.g * 0.35f, skyColor.b * 0.45f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);

        viewport.apply();

        camera.viewportWidth = viewport.getWorldWidth();
        camera.viewportHeight = viewport.getWorldHeight();
        camera.update();

        modelBatch.begin(camera);

        if (mountainInstance        != null) modelBatch.render(mountainInstance, environment);
        if (castleInstance          != null) modelBatch.render(castleInstance, environment);
        if (spireInstance           != null) modelBatch.render(spireInstance, environment);
        if (stormCloudsLowerInstance != null) modelBatch.render(stormCloudsLowerInstance, environment);
        if (stormCloudsUpperInstance != null) modelBatch.render(stormCloudsUpperInstance, environment);
        if (cumulusInstance         != null) modelBatch.render(cumulusInstance, environment);
        if (sunInstance             != null) modelBatch.render(sunInstance, environment);
        if (moonInstance            != null) modelBatch.render(moonInstance, environment);

        modelBatch.end();

        // Disable depth writing so subsequent 2D raycaster passes draw over the sky
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void dispose() {
        modelBatch.dispose();
        if (castleModel           != null) castleModel.dispose();
        if (spireModel            != null) spireModel.dispose();
        if (mountainModel         != null) mountainModel.dispose();
        if (cumulusModel          != null) cumulusModel.dispose();
        if (sunModel              != null) sunModel.dispose();
        if (moonModel             != null) moonModel.dispose();
        if (stormCloudsUpperModel != null) stormCloudsUpperModel.dispose();
        if (stormCloudsLowerModel != null) stormCloudsLowerModel.dispose();
    }
}
