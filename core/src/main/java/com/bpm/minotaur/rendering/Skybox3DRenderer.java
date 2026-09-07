package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
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
 *   <li>Modern Mode direct pass and Retro Mode 320x180 FBO pass with 4x4 Bayer dithering.</li>
 * </ul>
 */
public class Skybox3DRenderer {

    private static final String TAG = "Skybox3DRenderer";

    // Horizon Distances
    private static final float LANDMARK_DISTANCE = 140f;
    private static final float PARALLAX_SCALE = 0.05f;

    // Retro FBO Resolution
    public static final int RETRO_FBO_WIDTH = 320;
    public static final int RETRO_FBO_HEIGHT = 180;

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

    // 3D Model Instances
    private ModelInstance castleInstance;
    private ModelInstance spireInstance;
    private ModelInstance mountainInstance;
    private ModelInstance cumulusInstance;
    private ModelInstance sunInstance;
    private ModelInstance moonInstance;

    // Dynamic Cloud Drift
    private float cloudOffset = 0f;
    private final Vector3 tempVec = new Vector3();
    private final Color tempColor = new Color();

    // Retro Mode FrameBuffer & Shader
    private FrameBuffer retroFbo;
    private SpriteBatch retroBatch;
    private ShaderProgram retroDitherShader;

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

        initRetroResources();
        loadModels();
    }

    private void initRetroResources() {
        try {
            retroFbo = new FrameBuffer(Pixmap.Format.RGB888, RETRO_FBO_WIDTH, RETRO_FBO_HEIGHT, true);
            retroBatch = new SpriteBatch();

            ShaderProgram.pedantic = false;
            retroDitherShader = new ShaderProgram(
                    Gdx.files.internal("shaders/retro_skybox.vert"),
                    Gdx.files.internal("shaders/retro_skybox.frag")
            );
            if (!retroDitherShader.isCompiled()) {
                Gdx.app.error(TAG, "Retro skybox shader compilation failed:\n" + retroDitherShader.getLog());
            }
        } catch (Throwable t) {
            Gdx.app.error(TAG, "Failed to initialize retro FBO resources: " + t.getMessage());
        }
    }

    private void loadModels() {
        ObjLoader loader = new ObjLoader();
        try {
            // Castle Tarmin (North: World -Z)
            if (Gdx.files.internal("models/skybox/castle_tarmin.obj").exists()) {
                castleModel = loader.loadModel(Gdx.files.internal("models/skybox/castle_tarmin.obj"));
                castleInstance = new ModelInstance(castleModel);
                // Position North at Z = -140
                castleInstance.transform.setToTranslation(0f, -4f, -LANDMARK_DISTANCE);
                castleInstance.transform.scale(1.35f, 1.35f, 1.35f);
            }

            // South Spire (South: World +Z)
            if (Gdx.files.internal("models/skybox/south_spire.obj").exists()) {
                spireModel = loader.loadModel(Gdx.files.internal("models/skybox/south_spire.obj"));
                spireInstance = new ModelInstance(spireModel);
                // Position South at Z = +140
                spireInstance.transform.setToTranslation(0f, -4f, LANDMARK_DISTANCE);
                spireInstance.transform.scale(1.25f, 1.25f, 1.25f);
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
            driftSpeed = 2.4f; // Surge during storm
        }
        cloudOffset += driftSpeed * delta;
        if (cumulusInstance != null) {
            // Subtle bobbing & wind drift for western anvil cloud
            float wobble = MathUtils.sin(cloudOffset * 0.4f) * 1.5f;
            cumulusInstance.transform.setToTranslation(-LANDMARK_DISTANCE * 0.95f, 2f + wobble, 0f);
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

        if (renderMode == DebugManager.RenderMode.RETRO && retroFbo != null && retroDitherShader != null) {
            renderRetroPass(spriteBatch, viewport, skyColor);
        } else {
            renderModernPass(viewport, skyColor);
        }
    }

    private void renderModernPass(Viewport viewport, Color skyColor) {
        // Clear color to sky tint & clear depth for 3D horizon pass
        Gdx.gl.glClearColor(skyColor.r * 0.35f, skyColor.g * 0.35f, skyColor.b * 0.45f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);

        camera.viewportWidth = viewport.getWorldWidth();
        camera.viewportHeight = viewport.getWorldHeight();
        camera.update();

        modelBatch.begin(camera);

        if (mountainInstance != null) modelBatch.render(mountainInstance, environment);
        if (castleInstance   != null) modelBatch.render(castleInstance, environment);
        if (spireInstance    != null) modelBatch.render(spireInstance, environment);
        if (cumulusInstance  != null) modelBatch.render(cumulusInstance, environment);
        if (sunInstance      != null) modelBatch.render(sunInstance, environment);
        if (moonInstance     != null) modelBatch.render(moonInstance, environment);

        modelBatch.end();

        // Disable depth writing so subsequent 2D raycaster passes draw over the sky
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    private void renderRetroPass(SpriteBatch spriteBatch, Viewport viewport, Color skyColor) {
        // 1. Render 3D scene to low-res FBO (320x180)
        retroFbo.begin();
        Gdx.gl.glClearColor(skyColor.r * 0.35f, skyColor.g * 0.35f, skyColor.b * 0.45f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        camera.viewportWidth = RETRO_FBO_WIDTH;
        camera.viewportHeight = RETRO_FBO_HEIGHT;
        camera.update();

        modelBatch.begin(camera);
        if (mountainInstance != null) modelBatch.render(mountainInstance, environment);
        if (castleInstance   != null) modelBatch.render(castleInstance, environment);
        if (spireInstance    != null) modelBatch.render(spireInstance, environment);
        if (cumulusInstance  != null) modelBatch.render(cumulusInstance, environment);
        if (sunInstance      != null) modelBatch.render(sunInstance, environment);
        if (moonInstance     != null) modelBatch.render(moonInstance, environment);
        modelBatch.end();

        retroFbo.end();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        // 2. Blit upscaled FBO texture to screen using 4x4 Bayer dithering shader
        Texture fboTexture = retroFbo.getColorBufferTexture();
        fboTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.setShader(retroDitherShader);
        spriteBatch.begin();

        retroDitherShader.setUniformf("u_resolution", RETRO_FBO_WIDTH, RETRO_FBO_HEIGHT);

        // FBO texture coordinates are inverted on Y in OpenGL
        spriteBatch.draw(
                fboTexture,
                0, 0,
                viewport.getWorldWidth(), viewport.getWorldHeight(),
                0, 0, 1, 1
        );

        spriteBatch.end();
        spriteBatch.setShader(null);
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void dispose() {
        modelBatch.dispose();
        if (castleModel   != null) castleModel.dispose();
        if (spireModel    != null) spireModel.dispose();
        if (mountainModel != null) mountainModel.dispose();
        if (cumulusModel  != null) cumulusModel.dispose();
        if (sunModel      != null) sunModel.dispose();
        if (moonModel     != null) moonModel.dispose();

        if (retroFbo != null) retroFbo.dispose();
        if (retroBatch != null) retroBatch.dispose();
        if (retroDitherShader != null) retroDitherShader.dispose();
    }
}
