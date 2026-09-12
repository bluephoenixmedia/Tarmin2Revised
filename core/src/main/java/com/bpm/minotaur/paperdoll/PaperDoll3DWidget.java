package com.bpm.minotaur.paperdoll;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.item.Item;

/**
 * Renders an interactive 3D character paper doll into an off-screen FrameBuffer
 * and draws the resulting TextureRegion inside the inventory portrait window.
 *
 * Supports:
 * - 360-degree mouse drag rotation (left click drag to rotate, right click to reset)
 * - 2D pixel-art texture swapping (nearest-neighbor point sampling)
 * - Weapon and shield prop attachment toggling
 * - Material color ramp tinting (Bronze, Iron, Steel, Gold, etc.)
 */
public class PaperDoll3DWidget extends Widget implements Disposable {

    private static final int FBO_W = 380;
    private static final int FBO_H = 500;

    private FrameBuffer fbo;
    private TextureRegion fboRegion;
    private ModelBatch modelBatch;
    private PerspectiveCamera camera;
    private Environment environment;

    // 3D Models
    private Model bodyModel;
    private ModelInstance bodyInstance;

    private Model swordModel;
    private ModelInstance swordInstance;

    private Model shieldModel;
    private ModelInstance shieldInstance;

    // State
    private float yawDegrees = 0f;
    private float idleTimer = 0f;
    private boolean showMainHand = false;
    private boolean showOffHand = false;
    private Color currentTint = new Color(1f, 1f, 1f, 1f);

    public PaperDoll3DWidget() {
        initViewport();
        initModels();
        initInteraction();
    }

    private void initViewport() {
        fbo = new FrameBuffer(Format.RGBA8888, FBO_W, FBO_H, true);
        // FBO textures in OpenGL are Y-flipped relative to LibGDX Scene2D coordinates
        fboRegion = new TextureRegion(fbo.getColorBufferTexture());
        fboRegion.flip(false, true);

        modelBatch = new ModelBatch();

        // Portrait aspect ratio camera
        camera = new PerspectiveCamera(38f, FBO_W, FBO_H);
        // Paladin model: height ~1.72m, centered around Y=0.86m
        camera.position.set(0f, 0.90f, 2.65f);
        camera.lookAt(0f, 0.88f, 0f);
        camera.near = 0.1f;
        camera.far = 20f;
        camera.update();

        // Warm ambient lighting matching the parchment book
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.65f, 0.60f, 0.55f, 1f));
        // Key light from front-upper-right
        environment.add(new DirectionalLight().set(0.95f, 0.90f, 0.80f, -0.35f, -0.65f, -0.90f));
        // Soft rim light from back-left
        environment.add(new DirectionalLight().set(0.30f, 0.35f, 0.45f, 0.50f, 0.25f, 0.80f));
    }

    private void initModels() {
        ObjLoader loader = new ObjLoader();

        try {
            if (Gdx.files.internal("models/player/paladin.obj").exists()) {
                bodyModel = loader.loadModel(Gdx.files.internal("models/player/paladin.obj"), true);
                bodyInstance = new ModelInstance(bodyModel);
                applyPixelFiltering(bodyModel);
            }
        } catch (Exception e) {
            Gdx.app.error("PaperDoll3D", "Could not load paladin.obj: " + e.getMessage());
        }

        try {
            if (Gdx.files.internal("models/player/paladin_sword.obj").exists()) {
                swordModel = loader.loadModel(Gdx.files.internal("models/player/paladin_sword.obj"), true);
                swordInstance = new ModelInstance(swordModel);
                applyPixelFiltering(swordModel);
            }
        } catch (Exception e) {
            Gdx.app.error("PaperDoll3D", "Could not load paladin_sword.obj: " + e.getMessage());
        }

        try {
            if (Gdx.files.internal("models/player/paladin_shield.obj").exists()) {
                shieldModel = loader.loadModel(Gdx.files.internal("models/player/paladin_shield.obj"), true);
                shieldInstance = new ModelInstance(shieldModel);
                applyPixelFiltering(shieldModel);
            }
        } catch (Exception e) {
            Gdx.app.error("PaperDoll3D", "Could not load paladin_shield.obj: " + e.getMessage());
        }
    }

    private void applyPixelFiltering(Model model) {
        if (model == null) return;
        for (Material mat : model.materials) {
            TextureAttribute texAttr = (TextureAttribute) mat.get(TextureAttribute.Diffuse);
            if (texAttr != null && texAttr.textureDescription != null && texAttr.textureDescription.texture != null) {
                texAttr.textureDescription.texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            }
        }
    }

    private void initInteraction() {
        addListener(new InputListener() {
            private float lastX;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button == Input.Buttons.RIGHT) {
                    yawDegrees = 0f; // Reset to front facing
                    return true;
                }
                lastX = x;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                float dx = x - lastX;
                yawDegrees -= dx * 0.75f;
                lastX = x;
            }
        });
    }

    public void setMainHandEquipped(boolean equipped) {
        this.showMainHand = equipped;
    }

    public void setOffHandEquipped(boolean equipped) {
        this.showOffHand = equipped;
    }

    public void setMaterialTint(Color color) {
        if (color == null) color = Color.WHITE;
        this.currentTint.set(color);
        if (bodyInstance != null) {
            for (Material mat : bodyInstance.materials) {
                mat.set(ColorAttribute.createDiffuse(currentTint));
            }
        }
    }

    public void syncEquipment(Item mainHand, Item offHand, Item chest, Item helmet) {
        showMainHand = (mainHand != null && mainHand.isWeapon());
        showOffHand = (offHand != null && (offHand.isShield() || offHand.isWeapon()));

        // Material tinting based on chest or armor type
        if (chest != null) {
            String name = chest.getTypeName().toUpperCase();
            if (name.contains("BRONZE") || name.contains("COPPER")) {
                setMaterialTint(new Color(0.90f, 0.65f, 0.40f, 1f));
            } else if (name.contains("GOLD")) {
                setMaterialTint(new Color(1.0f, 0.85f, 0.30f, 1f));
            } else if (name.contains("DARK") || name.contains("OBSIDIAN") || name.contains("SHADOW")) {
                setMaterialTint(new Color(0.35f, 0.35f, 0.40f, 1f));
            } else if (name.contains("LEATHER") || name.contains("PADDED") || name.contains("HIDE")) {
                setMaterialTint(new Color(0.70f, 0.50f, 0.35f, 1f));
            } else if (name.contains("MITHRIL") || name.contains("SILVER")) {
                setMaterialTint(new Color(0.85f, 0.95f, 1.0f, 1f));
            } else {
                setMaterialTint(Color.WHITE); // Standard polished steel / iron
            }
        } else {
            setMaterialTint(Color.WHITE);
        }
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        idleTimer += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();

        if (bodyInstance == null) return;

        // Apply rotation to all character parts
        bodyInstance.transform.setToRotation(Vector3.Y, yawDegrees);

        if (swordInstance != null) {
            swordInstance.transform.set(bodyInstance.transform);
        }
        if (shieldInstance != null) {
            shieldInstance.transform.set(bodyInstance.transform);
        }

        // Render off-screen 3D pass
        batch.end();

        fbo.begin();
        Gdx.gl.glViewport(0, 0, FBO_W, FBO_H);
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f); // Transparent background
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);
        modelBatch.render(bodyInstance, environment);

        if (showMainHand && swordInstance != null) {
            modelBatch.render(swordInstance, environment);
        }
        if (showOffHand && shieldInstance != null) {
            modelBatch.render(shieldInstance, environment);
        }
        modelBatch.end();

        fbo.end();

        // Restore Scene2D Stage viewport
        if (getStage() != null) {
            getStage().getViewport().apply();
        }

        batch.begin();

        // Draw FBO TextureRegion inside widget bounds
        batch.setColor(1f, 1f, 1f, parentAlpha);
        batch.draw(fboRegion, getX(), getY(), getWidth(), getHeight());
    }

    @Override
    public void dispose() {
        if (fbo != null) {
            fbo.dispose();
            fbo = null;
        }
        if (modelBatch != null) {
            modelBatch.dispose();
            modelBatch = null;
        }
        if (bodyModel != null) {
            bodyModel.dispose();
            bodyModel = null;
        }
        if (swordModel != null) {
            swordModel.dispose();
            swordModel = null;
        }
        if (shieldModel != null) {
            shieldModel.dispose();
            shieldModel = null;
        }
    }
}
