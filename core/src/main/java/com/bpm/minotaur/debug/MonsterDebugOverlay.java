package com.bpm.minotaur.debug;

import com.badlogic.gdx.Gdx;
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
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;

/**
 * In-game monster sprite debugger. Toggle with F12.
 * Lets you inspect any monster's spritesheet, tweak cols/rows, animation speed,
 * scale, and offset — then print a JSON snippet ready to paste into monsters.json.
 */
public class MonsterDebugOverlay {

    private static final int PANEL_WIDTH = 310;

    private boolean visible = false;

    private final Stage stage;
    private final Skin skin;
    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final MonsterDataManager monsterDataManager;
    private final AssetManager assetManager;

    // Current monster state
    private Monster.MonsterType currentType;
    private MonsterTemplate currentTemplate;
    private Texture currentTexture;     // obtained from AssetManager — not disposed by us
    private TextureRegion[][] frames;   // [row][col]

    // Live-tweak params
    private int tweakCols = 1;
    private int tweakRows = 1;
    private float tweakFrameDuration = 0.15f;
    private float tweakScaleX = 1.0f;
    private float tweakScaleY = 1.0f;
    private float tweakOffsetX = 0.0f;
    private float tweakOffsetY = 0.0f;
    private float tweakZoom = 2.0f;
    private boolean showGrid = true;
    private boolean animPaused = false;

    // Animation state
    private float animTimer = 0f;
    private int currentFrame = 0;

    // Status labels
    private Label statusLabel;
    private Label texInfoLabel;

    // Slider refs for syncing on monster load
    private Slider colsSlider, rowsSlider, durSlider;
    private Slider sxSlider, sySlider, oxSlider, oySlider, zoomSlider;
    private Label colsVal, rowsVal, durVal, sxVal, syVal, oxVal, oyVal, zoomVal;

    // -------------------------------------------------------------------------

    public MonsterDebugOverlay(MonsterDataManager monsterDataManager, AssetManager assetManager) {
        this.monsterDataManager = monsterDataManager;
        this.assetManager = assetManager;
        this.batch = new SpriteBatch();
        this.shapes = new ShapeRenderer();
        this.stage = new Stage(new ScreenViewport());
        this.skin = new Skin();
        buildSkin();
        buildUI();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public boolean isVisible() { return visible; }

    public void toggle() { visible = !visible; }

    /** Returns the Stage so GameScreen can add it to the InputMultiplexer. */
    public Stage getStage() { return stage; }

    public void render(float delta) {
        if (!visible) return;

        // Advance animation
        int totalFrames = tweakCols * tweakRows;
        if (!animPaused && totalFrames > 1) {
            animTimer += delta;
            if (animTimer >= tweakFrameDuration) {
                animTimer -= tweakFrameDuration;
                currentFrame = (currentFrame + 1) % totalFrames;
            }
        }

        batch.setProjectionMatrix(stage.getCamera().combined);
        shapes.setProjectionMatrix(stage.getCamera().combined);

        float previewX = PANEL_WIDTH;
        float previewW = Gdx.graphics.getWidth() - PANEL_WIDTH;
        float previewH = Gdx.graphics.getHeight();

        // Dim the game behind the overlay
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
        // currentTexture is owned by AssetManager — do not dispose it here
    }

    // =========================================================================
    // UI
    // =========================================================================

    private void buildUI() {
        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        Table panel = new Table(skin);
        panel.top().left().pad(8);

        // Title + close hint
        panel.add(label("Monster Debug  [F12 to close]", "title")).colspan(2).left().padBottom(8).row();

        // Monster dropdown
        panel.add(label("Monster:", "default")).left().padRight(4);
        final SelectBox<Monster.MonsterType> picker = new SelectBox<Monster.MonsterType>(skin);
        picker.setItems(Monster.MonsterType.values());
        picker.setSelected(Monster.MonsterType.AGIS);
        picker.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { loadMonster(picker.getSelected()); }
        });
        panel.add(picker).fillX().padBottom(10).row();

        // ─ Spritesheet ─
        panel.add(label("─ Spritesheet ─", "section")).colspan(2).left().padBottom(2).row();

        panel.add(label("Cols:", "default")).left().padRight(4);
        colsSlider = new Slider(1, 32, 1, false, skin);
        colsVal = label("1", "val");
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
        rowsVal = label("1", "val");
        rowsSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                tweakRows = (int) rowsSlider.getValue();
                rowsVal.setText(String.valueOf(tweakRows));
                rebuildFrames();
            }
        });
        panel.add(sliderRow(rowsSlider, rowsVal)).left().padBottom(6).row();

        // ─ Animation ─
        panel.add(label("─ Animation ─", "section")).colspan(2).left().padBottom(2).row();

        panel.add(label("Frame (s):", "default")).left().padRight(4);
        durSlider = new Slider(0.02f, 2.0f, 0.01f, false, skin);
        durSlider.setValue(0.15f);
        durVal = label("0.15", "val");
        durSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                tweakFrameDuration = durSlider.getValue();
                durVal.setText(String.format("%.2f", tweakFrameDuration));
            }
        });
        panel.add(sliderRow(durSlider, durVal)).left().padBottom(3).row();

        panel.add(label("", "default"));
        final TextButton pauseBtn = new TextButton("Pause", skin);
        pauseBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                animPaused = !animPaused;
                pauseBtn.setText(animPaused ? "Resume" : "Pause");
            }
        });
        panel.add(pauseBtn).left().padBottom(6).row();

        // ─ Scale ─
        panel.add(label("─ Scale ─", "section")).colspan(2).left().padBottom(2).row();

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
        panel.add(sliderRow(sySlider, syVal)).left().padBottom(6).row();

        // ─ Offset ─
        panel.add(label("─ Offset ─", "section")).colspan(2).left().padBottom(2).row();

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

        // ─ View ─
        panel.add(label("─ View ─", "section")).colspan(2).left().padBottom(2).row();

        panel.add(label("Zoom:", "default")).left().padRight(4);
        zoomSlider = new Slider(0.25f, 8.0f, 0.25f, false, skin);
        zoomSlider.setValue(2.0f);
        zoomVal = label("2.00", "val");
        zoomSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                tweakZoom = zoomSlider.getValue();
                zoomVal.setText(String.format("%.2f", tweakZoom));
            }
        });
        panel.add(sliderRow(zoomSlider, zoomVal)).left().padBottom(3).row();

        panel.add(label("Grid:", "default")).left();
        CheckBox gridBox = new CheckBox(" Show", skin);
        gridBox.setChecked(true);
        gridBox.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { showGrid = gridBox.isChecked(); }
        });
        panel.add(gridBox).left().padBottom(8).row();

        // Status
        panel.add(label("─ Info ─", "section")).colspan(2).left().padBottom(2).row();
        statusLabel = label("Frame: -/-", "default");
        texInfoLabel = label("Tex: none", "dim");
        panel.add(statusLabel).colspan(2).left().row();
        panel.add(texInfoLabel).colspan(2).left().padBottom(8).row();

        // Buttons
        TextButton jsonBtn = new TextButton("Copy JSON to Console", skin);
        jsonBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { printJson(); }
        });
        panel.add(jsonBtn).colspan(2).fillX().padBottom(4).row();

        TextButton resetBtn = new TextButton("Reset to Template Values", skin);
        resetBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { loadMonster(currentType); }
        });
        panel.add(resetBtn).colspan(2).fillX().row();

        ScrollPane scroll = new ScrollPane(panel, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);

        root.add(scroll).width(PANEL_WIDTH).fillY().top().left();
        root.add().expandX().fillY();

        // Default to AGIS
        loadMonster(Monster.MonsterType.AGIS);
    }

    // =========================================================================
    // Monster Loading
    // =========================================================================

    private void loadMonster(Monster.MonsterType type) {
        currentType = type;
        currentTemplate = monsterDataManager.getTemplate(type);
        frames = null;
        currentFrame = 0;
        animTimer = 0;
        currentTexture = null;

        if (currentTemplate == null) { updateStatus(); return; }

        if (currentTemplate.texturePath != null
                && assetManager.isLoaded(currentTemplate.texturePath, Texture.class)) {
            currentTexture = assetManager.get(currentTemplate.texturePath, Texture.class);
        }

        tweakCols = currentTemplate.isSpriteSheet ? Math.max(1, currentTemplate.spriteCols) : 1;
        tweakRows = currentTemplate.isSpriteSheet ? Math.max(1, currentTemplate.spriteRows) : 1;
        tweakFrameDuration = Math.max(0.02f, currentTemplate.spriteFrameDuration);
        tweakScaleX = (currentTemplate.scale != null) ? currentTemplate.scale.x : 1.0f;
        tweakScaleY = (currentTemplate.scale != null) ? currentTemplate.scale.y : 1.0f;
        tweakOffsetX = currentTemplate.offsetX;
        tweakOffsetY = currentTemplate.offsetY;

        rebuildFrames();
        syncSliders();
        updateStatus();
    }

    private void rebuildFrames() {
        frames = null;
        if (currentTexture == null) return;
        int fw = currentTexture.getWidth() / Math.max(1, tweakCols);
        int fh = currentTexture.getHeight() / Math.max(1, tweakRows);
        if (fw <= 0 || fh <= 0) return;
        frames = TextureRegion.split(currentTexture, fw, fh);
        currentFrame = 0;
        animTimer = 0;
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
    }

    private void updateStatus() {
        if (statusLabel == null) return;
        int total = tweakCols * tweakRows;
        statusLabel.setText("Frame: " + (currentFrame + 1) + "/" + total
                + (animPaused ? " [PAUSED]" : "")
                + "  " + Gdx.graphics.getFramesPerSecond() + " fps");

        if (currentTexture != null) {
            int fw = currentTexture.getWidth() / Math.max(1, tweakCols);
            int fh = currentTexture.getHeight() / Math.max(1, tweakRows);
            texInfoLabel.setText("Sheet: " + currentTexture.getWidth() + "x" + currentTexture.getHeight()
                    + "  Frame: " + fw + "x" + fh + " px");
        } else if (currentTemplate != null && currentTemplate.spriteData != null) {
            texInfoLabel.setText("ASCII sprite (24x24)");
        } else {
            texInfoLabel.setText("No texture loaded");
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
                if (even) shapes.setColor(0.20f, 0.20f, 0.20f, 1f);
                else       shapes.setColor(0.25f, 0.25f, 0.25f, 1f);
                shapes.rect(px + cx, py + cy,
                        Math.min(cs, pw - cx), Math.min(cs, ph - cy));
            }
        }
        shapes.end();
    }

    private void drawPreview(float px, float py, float pw, float ph) {
        if (currentTexture != null) {
            drawTexturePreview(px, py, pw, ph);
        } else if (currentTemplate != null && currentTemplate.spriteData != null) {
            drawAsciiPreview(px, py, pw, ph);
        }
    }

    private void drawTexturePreview(float px, float py, float pw, float ph) {
        float texW = currentTexture.getWidth();
        float texH = currentTexture.getHeight();

        float fitScale = Math.min((pw * 0.8f) / texW, (ph * 0.8f) / texH);
        float sheetW = texW * fitScale * tweakZoom * tweakScaleX;
        float sheetH = texH * fitScale * tweakZoom * tweakScaleY;

        float centerX = px + pw / 2f;
        float centerY = py + ph / 2f;
        float drawX = centerX - sheetW / 2f + tweakOffsetX * sheetW;
        float drawY = centerY - sheetH / 2f + tweakOffsetY * sheetH;

        float frameW = sheetW / tweakCols;
        float frameH = sheetH / tweakRows;
        boolean isAnimated = tweakCols * tweakRows > 1;

        // Full sheet (faint when animated)
        batch.begin();
        batch.setColor(1f, 1f, 1f, isAnimated ? 0.25f : 1f);
        batch.draw(currentTexture, drawX, drawY, sheetW, sheetH,
                0, 0, (int) texW, (int) texH, false, true);

        // Current frame bright and centered
        if (frames != null && isAnimated) {
            int fr = currentFrame / tweakCols;
            int fc = currentFrame % tweakCols;
            if (fr < frames.length && fc < frames[fr].length) {
                batch.setColor(Color.WHITE);
                batch.draw(frames[fr][fc],
                        centerX - frameW / 2f + tweakOffsetX * sheetW,
                        centerY - frameH / 2f + tweakOffsetY * sheetH,
                        frameW, frameH);
            }
        }
        batch.end();

        if (showGrid) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            // Highlight active cell
            if (frames != null && isAnimated) {
                int fr = currentFrame / tweakCols;
                int fc = currentFrame % tweakCols;
                shapes.begin(ShapeRenderer.ShapeType.Filled);
                shapes.setColor(1f, 0.45f, 0.1f, 0.4f);
                shapes.rect(drawX + fc * frameW, drawY + (tweakRows - 1 - fr) * frameH, frameW, frameH);
                shapes.end();
            }

            // Grid lines
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(1f, 1f, 0f, 0.9f);
            for (int c = 0; c <= tweakCols; c++) {
                float lx = drawX + c * frameW;
                shapes.line(lx, drawY, lx, drawY + sheetH);
            }
            for (int r = 0; r <= tweakRows; r++) {
                float ly = drawY + r * frameH;
                shapes.line(drawX, ly, drawX + sheetW, ly);
            }
            shapes.setColor(0f, 1f, 1f, 0.7f);
            shapes.rect(drawX, drawY, sheetW, sheetH);
            shapes.end();

            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
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
        String s = "\n// ── " + currentType + " ── paste into monsters.json ──\n"
                + "\"isSpriteSheet\": " + (tweakCols * tweakRows > 1) + ",\n"
                + "\"spriteCols\": " + tweakCols + ",\n"
                + "\"spriteRows\": " + tweakRows + ",\n"
                + "\"spriteFrameDuration\": " + String.format("%.2f", tweakFrameDuration) + ",\n"
                + "\"scale\": { \"x\": " + String.format("%.2f", tweakScaleX)
                + ", \"y\": " + String.format("%.2f", tweakScaleY) + " },\n"
                + "\"offsetX\": " + String.format("%.2f", tweakOffsetX) + ",\n"
                + "\"offsetY\": " + String.format("%.2f", tweakOffsetY) + "\n"
                + "// ─────────────────────────────────────────────────";
        Gdx.app.log("MonsterDebug", s);
        System.out.println(s);
    }

    // =========================================================================
    // Skin / Style
    // =========================================================================

    private void buildSkin() {
        BitmapFont font = new BitmapFont();

        skin.add("default", new Label.LabelStyle(font, Color.WHITE));
        skin.add("title",   new Label.LabelStyle(font, new Color(1f, 0.8f, 0.2f, 1f)));
        skin.add("section", new Label.LabelStyle(font, new Color(0.45f, 0.8f, 1f, 1f)));
        skin.add("dim",     new Label.LabelStyle(font, new Color(0.55f, 0.55f, 0.55f, 1f)));
        skin.add("val",     new Label.LabelStyle(font, new Color(0.9f, 0.95f, 0.4f, 1f)));

        // SelectBox
        com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle listStyle =
                new com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle();
        listStyle.font = font;
        listStyle.fontColorSelected = Color.WHITE;
        listStyle.fontColorUnselected = new Color(0.75f, 0.75f, 0.75f, 1f);
        listStyle.selection = dr(new Color(0.2f, 0.4f, 0.65f, 1f));
        listStyle.background = dr(new Color(0.08f, 0.08f, 0.12f, 1f));

        SelectBox.SelectBoxStyle sbStyle = new SelectBox.SelectBoxStyle();
        sbStyle.font = font;
        sbStyle.fontColor = Color.WHITE;
        sbStyle.background = dr(new Color(0.18f, 0.18f, 0.22f, 1f));
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
        btnStyle.up   = dr(new Color(0.16f, 0.38f, 0.20f, 1f));
        btnStyle.over = dr(new Color(0.22f, 0.52f, 0.28f, 1f));
        btnStyle.down = dr(new Color(0.12f, 0.28f, 0.15f, 1f));
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

    // =========================================================================
    // Helpers
    // =========================================================================

    private Table sliderRow(Slider slider, Label val) {
        Table t = new Table();
        t.add(slider).width(175);
        t.add(val).width(38).padLeft(4);
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
