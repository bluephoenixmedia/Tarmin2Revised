package com.bpm.minotaur.debug;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterIdleAnimationRegistry;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;

/**
 * In-game monster sprite and animation debugger. Toggle with F12.
 * Lets you inspect any monster's idle spritesheet (3x3), base texture, or directional
 * frames, scrub frame-by-frame, step forward/back, tweak animation speed, scale,
 * offset, and export tuned parameters to JSON.
 */
public class MonsterDebugOverlay {

    public enum TextureSource {
        IDLE_ANIMATION("Idle Spritesheet"),
        BASE_TEXTURE("Base Static"),
        DIR_NORTH("Facing North"),
        DIR_EAST("Facing East"),
        DIR_WEST("Facing West"),
        DIR_SOUTH("Facing South");

        public final String label;
        TextureSource(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum ViewMode {
        ACTIVE_FRAME("Active Frame (Game Preview)"),
        SIDE_BY_SIDE("Side-by-Side (Frame + Sheet)"),
        FULL_SHEET("Full Spritesheet Grid");

        public final String label;
        ViewMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private static final int PANEL_WIDTH = 340;

    private boolean visible = false;

    private final Stage stage;
    private final Skin skin;
    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final MonsterDataManager monsterDataManager;
    private final AssetManager assetManager;
    private BitmapFont font;

    // Current monster state
    private Monster.MonsterType currentType;
    private MonsterTemplate currentTemplate;
    private Texture currentTexture;
    private String currentTexturePath;
    private TextureRegion[][] frames;   // [row][col]

    // Source & view selection
    private TextureSource currentSource = TextureSource.IDLE_ANIMATION;
    private ViewMode currentViewMode = ViewMode.SIDE_BY_SIDE;
    private SelectBox<Monster.MonsterType> monsterPicker;
    private SelectBox<TextureSource> sourcePicker;
    private SelectBox<ViewMode> viewModePicker;

    // Live-tweak params
    private int tweakCols = 1;
    private int tweakRows = 1;
    private float tweakFrameDuration = 0.15f;
    private float tweakScaleX = 1.0f;
    private float tweakScaleY = 1.0f;
    private float tweakOffsetX = 0.0f;
    private float tweakOffsetY = 0.0f;
    private float tweakZoom = 1.5f;
    private boolean showGrid = true;
    private boolean animPaused = false;

    // Animation state
    private float animTimer = 0f;
    private int currentFrame = 0;

    // UI Controls
    private TextButton pauseBtn;
    private Slider frameSlider;
    private Slider colsSlider, rowsSlider, durSlider;
    private Slider sxSlider, sySlider, oxSlider, oySlider, zoomSlider;
    private Label frameVal, colsVal, rowsVal, durVal, sxVal, syVal, oxVal, oyVal, zoomVal;
    private Label statusLabel, texInfoLabel, pathInfoLabel;

    public MonsterDebugOverlay(MonsterDataManager monsterDataManager, AssetManager assetManager) {
        this.monsterDataManager = monsterDataManager;
        this.assetManager = assetManager;
        this.batch = new SpriteBatch();
        this.shapes = new ShapeRenderer();
        this.stage = new Stage(new ScreenViewport());
        this.skin = new Skin();

        buildSkin();
        buildUI();
        setupInputShortcuts();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public boolean isVisible() { return visible; }

    public void toggle() { visible = !visible; }

    public Stage getStage() { return stage; }

    public void render(float delta) {
        if (!visible) return;

        // Advance animation
        int totalFrames = Math.max(1, tweakCols * tweakRows);
        if (!animPaused && totalFrames > 1) {
            animTimer += delta;
            while (animTimer >= tweakFrameDuration && tweakFrameDuration > 0.001f) {
                animTimer -= tweakFrameDuration;
                currentFrame = (currentFrame + 1) % totalFrames;
                if (frameSlider != null && !frameSlider.isDragging()) {
                    frameSlider.setValue(currentFrame);
                }
            }
        }

        batch.setProjectionMatrix(stage.getCamera().combined);
        shapes.setProjectionMatrix(stage.getCamera().combined);

        float previewX = PANEL_WIDTH;
        float previewW = Gdx.graphics.getWidth() - PANEL_WIDTH;
        float previewH = Gdx.graphics.getHeight();

        // Dim background
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.75f);
        shapes.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        drawPanelBg(0, 0, PANEL_WIDTH, previewH);
        drawCheckerboard(previewX, 0, previewW, previewH);
        drawPreview(previewX, 0, previewW, previewH);

        updateStatus();

        stage.act(delta);
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    public void dispose() {
        batch.dispose();
        shapes.dispose();
        stage.dispose();
        skin.dispose();
    }

    // =========================================================================
    // UI Construction
    // =========================================================================

    private void buildUI() {
        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        Table panel = new Table(skin);
        panel.top().left().pad(8);

        // Title
        panel.add(label("Monster Animation Debug  [F12]", "title")).colspan(2).left().padBottom(4).row();
        panel.add(label("Space: Pause/Play  |  Left/Right: Step Frame", "dim")).colspan(2).left().padBottom(8).row();

        // Monster dropdown
        panel.add(label("Monster:", "default")).left().padRight(4);
        monsterPicker = new SelectBox<>(skin);
        monsterPicker.setItems(Monster.MonsterType.values());
        monsterPicker.setSelected(Monster.MonsterType.AGIS);
        monsterPicker.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                loadMonster(monsterPicker.getSelected());
            }
        });
        panel.add(monsterPicker).fillX().padBottom(6).row();

        // Texture Source dropdown
        panel.add(label("Source:", "default")).left().padRight(4);
        sourcePicker = new SelectBox<>(skin);
        sourcePicker.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                loadCurrentSource();
            }
        });
        panel.add(sourcePicker).fillX().padBottom(6).row();

        // View Mode dropdown
        panel.add(label("View Mode:", "default")).left().padRight(4);
        viewModePicker = new SelectBox<>(skin);
        viewModePicker.setItems(ViewMode.values());
        viewModePicker.setSelected(ViewMode.SIDE_BY_SIDE);
        viewModePicker.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                currentViewMode = viewModePicker.getSelected();
            }
        });
        panel.add(viewModePicker).fillX().padBottom(10).row();

        // ─ Animation & Frame Stepping ─
        panel.add(label("─ Animation & Playback ─", "section")).colspan(2).left().padBottom(4).row();

        Table playbackBtns = new Table(skin);
        pauseBtn = new TextButton("Pause", skin);
        pauseBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                animPaused = !animPaused;
                updatePauseButtonText();
            }
        });

        TextButton prevBtn = new TextButton(" < Step ", skin);
        prevBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { prevFrame(); }
        });

        TextButton nextBtn = new TextButton(" Step > ", skin);
        nextBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { nextFrame(); }
        });

        playbackBtns.add(prevBtn).padRight(4);
        playbackBtns.add(pauseBtn).fillX().expandX().padRight(4);
        playbackBtns.add(nextBtn);
        panel.add(playbackBtns).colspan(2).fillX().padBottom(6).row();

        // Frame Scrubber
        panel.add(label("Frame:", "default")).left().padRight(4);
        frameSlider = new Slider(0, 8, 1, false, skin);
        frameVal = label("0 / 0", "val");
        frameSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (frameSlider.isDragging()) {
                    animPaused = true;
                    updatePauseButtonText();
                    currentFrame = (int) frameSlider.getValue();
                    updateStatus();
                }
            }
        });
        panel.add(sliderRow(frameSlider, frameVal)).left().padBottom(4).row();

        // Duration / Speed
        panel.add(label("Speed (s):", "default")).left().padRight(4);
        durSlider = new Slider(0.02f, 1.0f, 0.01f, false, skin);
        durSlider.setValue(0.15f);
        durVal = label("0.15", "val");
        durSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                tweakFrameDuration = durSlider.getValue();
                durVal.setText(String.format("%.2f", tweakFrameDuration));
            }
        });
        panel.add(sliderRow(durSlider, durVal)).left().padBottom(8).row();

        // ─ Spritesheet Grid ─
        panel.add(label("─ Spritesheet Grid ─", "section")).colspan(2).left().padBottom(4).row();

        panel.add(label("Cols:", "default")).left().padRight(4);
        colsSlider = new Slider(1, 16, 1, false, skin);
        colsVal = label("3", "val");
        colsSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                tweakCols = (int) colsSlider.getValue();
                colsVal.setText(String.valueOf(tweakCols));
                rebuildFrames();
            }
        });
        panel.add(sliderRow(colsSlider, colsVal)).left().padBottom(3).row();

        panel.add(label("Rows:", "default")).left().padRight(4);
        rowsSlider = new Slider(1, 16, 1, false, skin);
        rowsVal = label("3", "val");
        rowsSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                tweakRows = (int) rowsSlider.getValue();
                rowsVal.setText(String.valueOf(tweakRows));
                rebuildFrames();
            }
        });
        panel.add(sliderRow(rowsSlider, rowsVal)).left().padBottom(6).row();

        // ─ Scale & Offset ─
        panel.add(label("─ In-Game Scale & Offset ─", "section")).colspan(2).left().padBottom(4).row();

        panel.add(label("Scale X:", "default")).left().padRight(4);
        sxSlider = new Slider(0.1f, 5.0f, 0.05f, false, skin);
        sxSlider.setValue(1.0f);
        sxVal = label("1.00", "val");
        sxSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                tweakScaleX = sxSlider.getValue();
                sxVal.setText(String.format("%.2f", tweakScaleX));
            }
        });
        panel.add(sliderRow(sxSlider, sxVal)).left().padBottom(3).row();

        panel.add(label("Scale Y:", "default")).left().padRight(4);
        sySlider = new Slider(0.1f, 5.0f, 0.05f, false, skin);
        sySlider.setValue(1.0f);
        syVal = label("1.00", "val");
        sySlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                tweakScaleY = sySlider.getValue();
                syVal.setText(String.format("%.2f", tweakScaleY));
            }
        });
        panel.add(sliderRow(sySlider, syVal)).left().padBottom(3).row();

        panel.add(label("Offset X:", "default")).left().padRight(4);
        oxSlider = new Slider(-1.0f, 1.0f, 0.01f, false, skin);
        oxSlider.setValue(0.0f);
        oxVal = label("0.00", "val");
        oxSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                tweakOffsetX = oxSlider.getValue();
                oxVal.setText(String.format("%.2f", tweakOffsetX));
            }
        });
        panel.add(sliderRow(oxSlider, oxVal)).left().padBottom(3).row();

        panel.add(label("Offset Y:", "default")).left().padRight(4);
        oySlider = new Slider(-1.0f, 1.0f, 0.01f, false, skin);
        oySlider.setValue(0.0f);
        oyVal = label("0.00", "val");
        oySlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                tweakOffsetY = oySlider.getValue();
                oyVal.setText(String.format("%.2f", tweakOffsetY));
            }
        });
        panel.add(sliderRow(oySlider, oyVal)).left().padBottom(6).row();

        // ─ View Options ─
        panel.add(label("─ View Options ─", "section")).colspan(2).left().padBottom(4).row();

        panel.add(label("Zoom:", "default")).left().padRight(4);
        zoomSlider = new Slider(0.25f, 5.0f, 0.1f, false, skin);
        zoomSlider.setValue(1.5f);
        zoomVal = label("1.50", "val");
        zoomSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                tweakZoom = zoomSlider.getValue();
                zoomVal.setText(String.format("%.2f", tweakZoom));
            }
        });
        panel.add(sliderRow(zoomSlider, zoomVal)).left().padBottom(3).row();

        panel.add(label("Grid:", "default")).left();
        final CheckBox gridBox = new CheckBox(" Show Sheet Grid & Numbers", skin);
        gridBox.setChecked(true);
        gridBox.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { showGrid = gridBox.isChecked(); }
        });
        panel.add(gridBox).left().padBottom(8).row();

        // ─ Info & Status ─
        panel.add(label("─ Frame Inspector Info ─", "section")).colspan(2).left().padBottom(4).row();
        statusLabel = label("Frame: -/-", "default");
        texInfoLabel = label("Dimensions: -", "val");
        pathInfoLabel = label("Path: -", "dim");
        panel.add(statusLabel).colspan(2).left().row();
        panel.add(texInfoLabel).colspan(2).left().row();
        panel.add(pathInfoLabel).colspan(2).left().padBottom(8).row();

        // Action Buttons
        TextButton jsonBtn = new TextButton("Copy Tuning JSON to Console", skin);
        jsonBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { printJson(); }
        });
        panel.add(jsonBtn).colspan(2).fillX().padBottom(4).row();

        TextButton resetBtn = new TextButton("Reset Parameters", skin);
        resetBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { loadMonster(currentType); }
        });
        panel.add(resetBtn).colspan(2).fillX().row();

        ScrollPane scroll = new ScrollPane(panel, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);

        root.add(scroll).width(PANEL_WIDTH).fillY().top().left();
        root.add().expandX().fillY();

        // Initial monster load
        loadMonster(Monster.MonsterType.ALLIGATOR);
    }

    private void setupInputShortcuts() {
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (!visible) return false;
                if (keycode == Input.Keys.SPACE) {
                    animPaused = !animPaused;
                    updatePauseButtonText();
                    return true;
                } else if (keycode == Input.Keys.LEFT || keycode == Input.Keys.LEFT_BRACKET) {
                    prevFrame();
                    return true;
                } else if (keycode == Input.Keys.RIGHT || keycode == Input.Keys.RIGHT_BRACKET) {
                    nextFrame();
                    return true;
                }
                return false;
            }
        });
    }

    // =========================================================================
    // Monster & Source Loading
    // =========================================================================

    private void loadMonster(Monster.MonsterType type) {
        currentType = type;
        currentTemplate = monsterDataManager.getTemplate(type);

        Array<TextureSource> sources = new Array<>();
        if (MonsterIdleAnimationRegistry.hasIdleAnimation(type)
                || (currentTemplate != null && currentTemplate.idleAnimationPath != null)) {
            sources.add(TextureSource.IDLE_ANIMATION);
        }
        if (currentTemplate != null && currentTemplate.texturePath != null) {
            sources.add(TextureSource.BASE_TEXTURE);
        }
        if (currentTemplate != null && currentTemplate.directionTextures != null) {
            if (currentTemplate.directionTextures.north != null) sources.add(TextureSource.DIR_NORTH);
            if (currentTemplate.directionTextures.east != null) sources.add(TextureSource.DIR_EAST);
            if (currentTemplate.directionTextures.west != null) sources.add(TextureSource.DIR_WEST);
            if (currentTemplate.directionTextures.southFrames != null && currentTemplate.directionTextures.southFrames.length > 0) {
                sources.add(TextureSource.DIR_SOUTH);
            }
        }
        if (sources.size == 0) {
            sources.add(TextureSource.BASE_TEXTURE);
        }

        sourcePicker.setItems(sources);
        if (sources.contains(TextureSource.IDLE_ANIMATION, true)) {
            sourcePicker.setSelected(TextureSource.IDLE_ANIMATION);
        } else {
            sourcePicker.setSelected(sources.first());
        }

        loadCurrentSource();
    }

    private void loadCurrentSource() {
        TextureSource src = sourcePicker.getSelected();
        if (src == null) return;
        currentSource = src;

        String path = null;
        int cols = 1;
        int rows = 1;
        float dur = 0.15f;

        switch (src) {
            case IDLE_ANIMATION:
                MonsterIdleAnimationRegistry.IdleConfig cfg = MonsterIdleAnimationRegistry.getConfig(currentType);
                if (cfg != null) {
                    path = cfg.texturePath;
                    cols = cfg.cols;
                    rows = cfg.rows;
                    dur = cfg.frameDuration;
                } else if (currentTemplate != null && currentTemplate.idleAnimationPath != null) {
                    path = currentTemplate.idleAnimationPath;
                    cols = Math.max(1, currentTemplate.spriteCols);
                    rows = Math.max(1, currentTemplate.spriteRows);
                    dur = Math.max(0.02f, currentTemplate.spriteFrameDuration);
                }
                break;
            case BASE_TEXTURE:
                if (currentTemplate != null) {
                    path = currentTemplate.texturePath;
                    cols = currentTemplate.isSpriteSheet ? Math.max(1, currentTemplate.spriteCols) : 1;
                    rows = currentTemplate.isSpriteSheet ? Math.max(1, currentTemplate.spriteRows) : 1;
                    dur = Math.max(0.02f, currentTemplate.spriteFrameDuration);
                }
                break;
            case DIR_NORTH:
                if (currentTemplate != null && currentTemplate.directionTextures != null) {
                    path = currentTemplate.directionTextures.north;
                }
                break;
            case DIR_EAST:
                if (currentTemplate != null && currentTemplate.directionTextures != null) {
                    path = currentTemplate.directionTextures.east;
                }
                break;
            case DIR_WEST:
                if (currentTemplate != null && currentTemplate.directionTextures != null) {
                    path = currentTemplate.directionTextures.west;
                }
                break;
            case DIR_SOUTH:
                if (currentTemplate != null && currentTemplate.directionTextures != null
                        && currentTemplate.directionTextures.southFrames != null
                        && currentTemplate.directionTextures.southFrames.length > 0) {
                    path = currentTemplate.directionTextures.southFrames[0];
                }
                break;
        }

        currentTexture = fetchTexture(path);
        currentTexturePath = path;

        tweakCols = cols;
        tweakRows = rows;
        tweakFrameDuration = dur;
        tweakScaleX = (currentTemplate != null && currentTemplate.scale != null) ? currentTemplate.scale.x : 1.0f;
        tweakScaleY = (currentTemplate != null && currentTemplate.scale != null) ? currentTemplate.scale.y : 1.0f;
        tweakOffsetX = (currentTemplate != null) ? currentTemplate.offsetX : 0.0f;
        tweakOffsetY = (currentTemplate != null) ? currentTemplate.offsetY : 0.0f;

        rebuildFrames();
        syncSliders();
        updateStatus();
    }

    private Texture fetchTexture(String path) {
        if (path == null || path.isEmpty()) return null;
        if (assetManager.isLoaded(path, Texture.class)) {
            return assetManager.get(path, Texture.class);
        }
        if (Gdx.files.internal(path).exists()) {
            try {
                assetManager.load(path, Texture.class);
                assetManager.finishLoadingAsset(path);
                if (assetManager.isLoaded(path, Texture.class)) {
                    return assetManager.get(path, Texture.class);
                }
            } catch (Exception e) {
                Gdx.app.error("MonsterDebugOverlay", "Could not load texture: " + path, e);
            }
        }
        return null;
    }

    private void rebuildFrames() {
        frames = null;
        currentFrame = 0;
        animTimer = 0f;
        if (currentTexture == null) return;
        int fw = currentTexture.getWidth() / Math.max(1, tweakCols);
        int fh = currentTexture.getHeight() / Math.max(1, tweakRows);
        if (fw <= 0 || fh <= 0) return;
        frames = TextureRegion.split(currentTexture, fw, fh);
        updateFrameSliderBounds();
    }

    private void updateFrameSliderBounds() {
        if (frameSlider == null) return;
        int total = Math.max(1, tweakCols * tweakRows);
        frameSlider.setRange(0, total - 1);
        frameSlider.setValue(currentFrame);
    }

    private void prevFrame() {
        int total = Math.max(1, tweakCols * tweakRows);
        if (total <= 1) return;
        animPaused = true;
        updatePauseButtonText();
        currentFrame = (currentFrame - 1 + total) % total;
        if (frameSlider != null) frameSlider.setValue(currentFrame);
        updateStatus();
    }

    private void nextFrame() {
        int total = Math.max(1, tweakCols * tweakRows);
        if (total <= 1) return;
        animPaused = true;
        updatePauseButtonText();
        currentFrame = (currentFrame + 1) % total;
        if (frameSlider != null) frameSlider.setValue(currentFrame);
        updateStatus();
    }

    private void updatePauseButtonText() {
        if (pauseBtn != null) {
            pauseBtn.setText(animPaused ? "Resume" : "Pause");
        }
    }

    private void syncSliders() {
        if (colsSlider == null) return;
        colsSlider.setValue(tweakCols);      colsVal.setText(String.valueOf(tweakCols));
        rowsSlider.setValue(tweakRows);      rowsVal.setText(String.valueOf(tweakRows));
        durSlider.setValue(tweakFrameDuration);  durVal.setText(String.format("%.2f", tweakFrameDuration));
        sxSlider.setValue(tweakScaleX);      sxVal.setText(String.format("%.2f", tweakScaleX));
        sySlider.setValue(tweakScaleY);      syVal.setText(String.format("%.2f", tweakScaleY));
        oxSlider.setValue(tweakOffsetX);     oxVal.setText(String.format("%.2f", tweakOffsetX));
        oySlider.setValue(tweakOffsetY);     oyVal.setText(String.format("%.2f", tweakOffsetY));
        zoomSlider.setValue(tweakZoom);      zoomVal.setText(String.format("%.2f", tweakZoom));
        updateFrameSliderBounds();
    }

    private void updateStatus() {
        if (statusLabel == null) return;
        int total = Math.max(1, tweakCols * tweakRows);
        int fr = currentFrame / Math.max(1, tweakCols);
        int fc = currentFrame % Math.max(1, tweakCols);

        String pauseTag = animPaused ? " [PAUSED]" : " [PLAYING]";
        statusLabel.setText("Frame: " + (currentFrame + 1) + " / " + total
                + " (R" + fr + ", C" + fc + ")" + pauseTag);
        if (frameVal != null) {
            frameVal.setText((currentFrame + 1) + "/" + total);
        }

        if (currentTexture != null) {
            int fw = currentTexture.getWidth() / Math.max(1, tweakCols);
            int fh = currentTexture.getHeight() / Math.max(1, tweakRows);
            texInfoLabel.setText("Sheet: " + currentTexture.getWidth() + "x" + currentTexture.getHeight()
                    + "  |  Cell: " + fw + "x" + fh + " px");
            pathInfoLabel.setText(currentTexturePath != null ? currentTexturePath : "Loaded");
        } else if (currentTemplate != null && currentTemplate.spriteData != null) {
            texInfoLabel.setText("ASCII Sprite Data (24x24)");
            pathInfoLabel.setText("Vector / Procedural");
        } else {
            texInfoLabel.setText("No texture loaded");
            pathInfoLabel.setText("Path: none");
        }
    }

    // =========================================================================
    // Drawing
    // =========================================================================

    private void drawPanelBg(float x, float y, float w, float h) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.10f, 0.10f, 0.13f, 1f);
        shapes.rect(x, y, w, h);
        shapes.end();
    }

    private void drawCheckerboard(float px, float py, float pw, float ph) {
        final int cs = 16;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int cy = 0; cy < (int) ph; cy += cs) {
            for (int cx = 0; cx < (int) pw; cx += cs) {
                boolean even = ((cx / cs) + (cy / cs)) % 2 == 0;
                if (even) shapes.setColor(0.18f, 0.18f, 0.20f, 1f);
                else       shapes.setColor(0.24f, 0.24f, 0.26f, 1f);
                shapes.rect(px + cx, py + cy, Math.min(cs, pw - cx), Math.min(cs, ph - cy));
            }
        }
        shapes.end();
    }

    private void drawPreview(float px, float py, float pw, float ph) {
        if (currentTexture != null) {
            switch (currentViewMode) {
                case ACTIVE_FRAME:
                    drawActiveFrameOnly(px, py, pw, ph);
                    break;
                case SIDE_BY_SIDE:
                    drawSideBySide(px, py, pw, ph);
                    break;
                case FULL_SHEET:
                    drawFullSheet(px, py, pw, ph);
                    break;
            }
        } else if (currentTemplate != null && currentTemplate.spriteData != null) {
            drawAsciiPreview(px, py, pw, ph);
        }
    }

    private void drawActiveFrameOnly(float px, float py, float pw, float ph) {
        int fw = currentTexture.getWidth() / Math.max(1, tweakCols);
        int fh = currentTexture.getHeight() / Math.max(1, tweakRows);

        float fitScale = Math.min((pw * 0.75f) / fw, (ph * 0.75f) / fh);
        float drawW = fw * fitScale * tweakZoom * tweakScaleX;
        float drawH = fh * fitScale * tweakZoom * tweakScaleY;

        float centerX = px + pw / 2f;
        float centerY = py + ph / 2f;
        float drawX = centerX - drawW / 2f + tweakOffsetX * drawW;
        float drawY = centerY - drawH / 2f + tweakOffsetY * drawH;

        TextureRegion region = getActiveRegion();
        if (region != null) {
            batch.begin();
            batch.setColor(Color.WHITE);
            batch.draw(region, drawX, drawY, drawW, drawH);
            batch.end();
        }

        // Draw bounding box & center crosshair
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0f, 1f, 1f, 0.5f);
        shapes.rect(drawX, drawY, drawW, drawH);
        shapes.setColor(1f, 1f, 0f, 0.3f);
        shapes.line(centerX - 20, centerY, centerX + 20, centerY);
        shapes.line(centerX, centerY - 20, centerX, centerY + 20);
        shapes.end();
    }

    private void drawSideBySide(float px, float py, float pw, float ph) {
        float splitW = pw * 0.52f;
        float sheetAreaX = px + splitW;
        float sheetAreaW = pw - splitW;

        // Left: Clean active frame
        drawActiveFrameOnly(px, py, splitW, ph);

        // Divider line
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.35f, 0.35f, 0.40f, 1f);
        shapes.line(sheetAreaX, 0, sheetAreaX, ph);
        shapes.end();

        // Right: Spritesheet with grid & active cell highlight
        drawSheetAndGrid(sheetAreaX, py, sheetAreaW, ph);
    }

    private void drawFullSheet(float px, float py, float pw, float ph) {
        drawSheetAndGrid(px, py, pw, ph);
    }

    private void drawSheetAndGrid(float px, float py, float pw, float ph) {
        float texW = currentTexture.getWidth();
        float texH = currentTexture.getHeight();

        float fitScale = Math.min((pw * 0.85f) / texW, (ph * 0.85f) / texH);
        float sheetW = texW * fitScale * tweakZoom;
        float sheetH = texH * fitScale * tweakZoom;

        float drawX = px + pw / 2f - sheetW / 2f;
        float drawY = py + ph / 2f - sheetH / 2f;

        float frameW = sheetW / tweakCols;
        float frameH = sheetH / tweakRows;

        // Draw entire spritesheet
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(currentTexture, drawX, drawY, sheetW, sheetH,
                0, 0, (int) texW, (int) texH, false, true);
        batch.end();

        if (showGrid) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            // Highlight active frame cell
            int fr = currentFrame / Math.max(1, tweakCols);
            int fc = currentFrame % Math.max(1, tweakCols);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(1f, 0.55f, 0.1f, 0.45f);
            shapes.rect(drawX + fc * frameW, drawY + (tweakRows - 1 - fr) * frameH, frameW, frameH);
            shapes.end();

            // Grid lines
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(1f, 1f, 0.2f, 0.9f);
            for (int c = 0; c <= tweakCols; c++) {
                float lx = drawX + c * frameW;
                shapes.line(lx, drawY, lx, drawY + sheetH);
            }
            for (int r = 0; r <= tweakRows; r++) {
                float ly = drawY + r * frameH;
                shapes.line(drawX, ly, drawX + sheetW, ly);
            }
            // Active cell border
            shapes.setColor(1f, 0.3f, 0.1f, 1f);
            shapes.rect(drawX + fc * frameW, drawY + (tweakRows - 1 - fr) * frameH, frameW, frameH);
            shapes.end();

            Gdx.gl.glDisable(GL20.GL_BLEND);

            // Draw frame indices (0, 1, 2...)
            batch.begin();
            font.setColor(Color.YELLOW);
            for (int r = 0; r < tweakRows; r++) {
                for (int c = 0; c < tweakCols; c++) {
                    int idx = r * tweakCols + c;
                    float numX = drawX + c * frameW + 6;
                    float numY = drawY + (tweakRows - 1 - r) * frameH + frameH - 6;
                    font.draw(batch, "#" + idx, numX, numY);
                }
            }
            batch.end();
        }
    }

    private TextureRegion getActiveRegion() {
        if (frames == null || tweakCols <= 0 || tweakRows <= 0) {
            return currentTexture != null ? new TextureRegion(currentTexture) : null;
        }
        int fr = currentFrame / tweakCols;
        int fc = currentFrame % tweakCols;
        if (fr < frames.length && fc < frames[fr].length) {
            return frames[fr][fc];
        }
        return null;
    }

    private void drawAsciiPreview(float px, float py, float pw, float ph) {
        String[] data = currentTemplate.spriteData;
        int cols = data[0].length();
        int rows = data.length;
        int ps = Math.max(1, (int) (Math.min(pw * 0.7f, ph * 0.7f) * tweakZoom / Math.max(cols, rows)));
        float startX = px + pw / 2f - (cols * ps) / 2f;
        float startY = py + ph / 2f - (rows * ps) / 2f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < data[row].length(); col++) {
                char c = data[row].charAt(col);
                if (c != '.') {
                    shapes.setColor(asciiColor(c));
                    shapes.rect(startX + col * ps, startY + (rows - 1 - row) * ps, ps, ps);
                }
            }
        }
        shapes.end();
    }

    // =========================================================================
    // JSON Output
    // =========================================================================

    private void printJson() {
        String s = "\n// ── " + currentType + " (" + currentSource + ") ── paste into monsters.json ──\n"
                + "\"isSpriteSheet\": " + (tweakCols * tweakRows > 1) + ",\n"
                + "\"spriteCols\": " + tweakCols + ",\n"
                + "\"spriteRows\": " + tweakRows + ",\n"
                + "\"spriteFrameDuration\": " + String.format("%.2f", tweakFrameDuration) + ",\n"
                + "\"scale\": { \"x\": " + String.format("%.2f", tweakScaleX)
                + ", \"y\": " + String.format("%.2f", tweakScaleY) + " },\n"
                + "\"offsetX\": " + String.format("%.2f", tweakOffsetX) + ",\n"
                + "\"offsetY\": " + String.format("%.2f", tweakOffsetY) + "\n"
                + "// ─────────────────────────────────────────────────────────";
        Gdx.app.log("MonsterDebug", s);
        System.out.println(s);
    }

    // =========================================================================
    // Skin & Styles
    // =========================================================================

    private void buildSkin() {
        font = new BitmapFont();

        skin.add("default", new Label.LabelStyle(font, Color.WHITE));
        skin.add("title",   new Label.LabelStyle(font, new Color(1f, 0.85f, 0.2f, 1f)));
        skin.add("section", new Label.LabelStyle(font, new Color(0.45f, 0.85f, 1f, 1f)));
        skin.add("dim",     new Label.LabelStyle(font, new Color(0.60f, 0.60f, 0.60f, 1f)));
        skin.add("val",     new Label.LabelStyle(font, new Color(0.95f, 0.95f, 0.4f, 1f)));

        // SelectBox
        com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle listStyle =
                new com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle();
        listStyle.font = font;
        listStyle.fontColorSelected = Color.WHITE;
        listStyle.fontColorUnselected = new Color(0.75f, 0.75f, 0.75f, 1f);
        listStyle.selection = dr(new Color(0.2f, 0.45f, 0.7f, 1f));
        listStyle.background = dr(new Color(0.08f, 0.08f, 0.12f, 1f));

        SelectBox.SelectBoxStyle sbStyle = new SelectBox.SelectBoxStyle();
        sbStyle.font = font;
        sbStyle.fontColor = Color.WHITE;
        sbStyle.background = dr(new Color(0.18f, 0.18f, 0.24f, 1f));
        sbStyle.listStyle = listStyle;
        sbStyle.scrollStyle = new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle();
        skin.add("default", sbStyle);

        // Slider
        Pixmap trackPix = new Pixmap(1, 8, Pixmap.Format.RGBA8888);
        trackPix.setColor(new Color(0.28f, 0.28f, 0.33f, 1f));
        trackPix.fill();
        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = new TextureRegionDrawable(new TextureRegion(new Texture(trackPix)));
        trackPix.dispose();
        sliderStyle.knob = dr(new Color(0.3f, 0.7f, 1.0f, 1f), 12, 12);
        skin.add("default-horizontal", sliderStyle);

        // TextButton
        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = font;
        btnStyle.fontColor = Color.WHITE;
        btnStyle.up   = dr(new Color(0.16f, 0.38f, 0.22f, 1f));
        btnStyle.over = dr(new Color(0.22f, 0.52f, 0.30f, 1f));
        btnStyle.down = dr(new Color(0.12f, 0.28f, 0.16f, 1f));
        skin.add("default", btnStyle);

        // CheckBox
        CheckBox.CheckBoxStyle cbStyle = new CheckBox.CheckBoxStyle();
        cbStyle.font = font;
        cbStyle.fontColor = Color.WHITE;
        cbStyle.checkboxOff = dr(new Color(0.28f, 0.28f, 0.33f, 1f), 13, 13);
        cbStyle.checkboxOn  = dr(new Color(0.3f, 0.7f, 1.0f, 1f), 13, 13);
        skin.add("default", cbStyle);

        skin.add("default", new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle());
    }

    private Table sliderRow(Slider slider, Label val) {
        Table t = new Table();
        t.add(slider).width(190);
        t.add(val).width(45).padLeft(4);
        return t;
    }

    private Label label(String text, String style) { return new Label(text, skin, style); }

    private TextureRegionDrawable dr(Color c) { return dr(c, 1, 1); }

    private TextureRegionDrawable dr(Color c, int w, int h) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(c); p.fill();
        TextureRegionDrawable d = new TextureRegionDrawable(new TextureRegion(new Texture(p)));
        p.dispose();
        return d;
    }

    private Color asciiColor(char c) {
        switch (c) {
            case 'R': return Color.RED;    case 'G': return Color.GREEN;  case 'L': return Color.LIME;
            case 'B': return Color.BLUE;   case 'Y': return Color.YELLOW; case 'W': return Color.WHITE;
            case 'K': return Color.BLACK;  case 'O': return Color.ORANGE; case 'P': return Color.PURPLE;
            case 'M': return Color.MAGENTA;case 'C': return Color.CYAN;   case 'S': return Color.GRAY;
            case 'D': return Color.GOLD;   case 'T': return Color.TEAL;   default:  return Color.GRAY;
        }
    }
}
