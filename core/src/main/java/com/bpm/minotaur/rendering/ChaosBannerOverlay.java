package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.managers.ChaosManager;
import com.bpm.minotaur.managers.ChaosSurgeEvent;
import com.bpm.minotaur.ui.UiGlyphs;
import com.bpm.minotaur.ui.UiTheme;

/**
 * On-screen herald announcement banner and persistent HUD counter for Chaos Surges
 * triggered by The Laughing Void.
 *
 * <p>Renders a 1920x1080 integer-scaled banner in {@code intellivision.ttf} with a
 * LibGDX chromatic oscillating shader (#00FFFF -> #BF00FF -> #FF007F), framed by
 * decorative carved jester accents.
 */
public final class ChaosBannerOverlay extends Table implements Disposable, ChaosManager.ChaosSurgeListener {

    private static final String TAG = "ChaosBannerOverlay";
    private static final float FADE_IN = 0.5f;
    private static final float HOLD_TIME = 4.0f;
    private static final float FADE_OUT = 0.8f;

    // Thematic Colors
    public static final Color COL_CYAN = Color.valueOf("00FFFF");
    public static final Color COL_VIOLET = Color.valueOf("BF00FF");
    public static final Color COL_MAGENTA = Color.valueOf("FF007F");
    public static final Color COL_VOID_BG = new Color(0.04f, 0.02f, 0.08f, 0.90f);

    private final Table heraldBanner;
    private final Label titleLabel;
    private final Label eventNameLabel;
    private final Label descLabel;
    private final Label durationLabel;

    private final Table hudPill;
    private final Label hudStatusLabel;

    private ShaderProgram chromaticShader;
    private float shaderTime = 0f;
    private Texture frameTexture;

    private static BitmapFont defaultFont() {
        try {
            com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData data = new com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData();
            com.badlogic.gdx.utils.Array<com.badlogic.gdx.graphics.g2d.TextureRegion> regions = new com.badlogic.gdx.utils.Array<>(1);
            regions.add(new com.badlogic.gdx.graphics.g2d.TextureRegion());
            return new BitmapFont(data, regions, false);
        } catch (Throwable t) {
            return null;
        }
    }

    public ChaosBannerOverlay(HudSkin skin) {
        setFillParent(true);
        setTouchable(Touchable.disabled);

        initShader();

        // -------------------------------------------------------------
        // 1. Herald Banner (Center / Upper-Center Announcement)
        // -------------------------------------------------------------
        heraldBanner = new Table();
        heraldBanner.getColor().a = 0f;

        BitmapFont fontDisplay = skin != null ? skin.getFontDisplay() : defaultFont();
        BitmapFont fontHeader = skin != null ? skin.getFontHeader() : defaultFont();
        BitmapFont fontMain = skin != null ? skin.getFontMain() : defaultFont();
        BitmapFont fontSmall = skin != null ? skin.getFontSmall() : defaultFont();

        titleLabel = new Label(UiGlyphs.sanitize("~ THE LAUGHING VOID ~"), new Label.LabelStyle(fontHeader, COL_CYAN));
        titleLabel.setAlignment(Align.center);

        eventNameLabel = new Label("", new Label.LabelStyle(fontDisplay, COL_MAGENTA));
        eventNameLabel.setAlignment(Align.center);

        descLabel = new Label("", new Label.LabelStyle(fontMain, Color.WHITE));
        descLabel.setWrap(true);
        descLabel.setAlignment(Align.center);

        durationLabel = new Label("", new Label.LabelStyle(fontSmall, COL_VIOLET));
        durationLabel.setAlignment(Align.center);

        // Decorative background frame
        frameTexture = createFrameTexture(900, 220);
        Image frameBg = frameTexture != null ? new Image(frameTexture) : new Image();

        Table contentTable = new Table();
        contentTable.add(titleLabel).padTop(12).row();
        contentTable.add(eventNameLabel).padTop(6).padBottom(4).row();
        contentTable.add(descLabel).width(820).padLeft(20).padRight(20).row();
        contentTable.add(durationLabel).padTop(8).padBottom(12).row();

        Stack bannerStack = new Stack();
        bannerStack.add(frameBg);
        bannerStack.add(contentTable);

        heraldBanner.add(bannerStack).width(900).height(220);

        // -------------------------------------------------------------
        // 2. Compact HUD Indicator (Persistent Upper-Right Status Pill)
        // -------------------------------------------------------------
        hudPill = new Table();
        hudPill.getColor().a = 0f;
        hudStatusLabel = new Label("", new Label.LabelStyle(fontSmall, COL_CYAN));
        hudStatusLabel.setAlignment(Align.right);
        hudPill.add(hudStatusLabel).pad(6, 12, 6, 12);

        // Layout overall container: HUD Pill top-right, Herald Banner center-top
        Table topRow = new Table();
        topRow.right();
        topRow.add(hudPill).padTop(20).padRight(30);

        top().left();
        add(topRow).fillX().row();
        add(heraldBanner).expandX().center().padTop(90).row();

        ChaosManager.getInstance().addListener(this);
    }

    private void initShader() {
        try {
            if (Gdx.files != null && Gdx.files.internal("shaders/chaos_banner.vert").exists()) {
                chromaticShader = new ShaderProgram(
                        Gdx.files.internal("shaders/chaos_banner.vert"),
                        Gdx.files.internal("shaders/chaos_banner.frag")
                );
                if (!chromaticShader.isCompiled()) {
                    if (Gdx.app != null) {
                        Gdx.app.error(TAG, "Chaos chromatic shader compilation failed: " + chromaticShader.getLog());
                    }
                    chromaticShader = null;
                }
            }
        } catch (Throwable t) {
            if (Gdx.app != null) {
                Gdx.app.log(TAG, "Shader unavailable (headless or test env): " + t.getMessage());
            }
            chromaticShader = null;
        }
    }

    private Texture createFrameTexture(int width, int height) {
        if (Gdx.gl == null) return null;
        try {
            Pixmap pix = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            pix.setColor(COL_VOID_BG);
            pix.fill();

            // Dual carved border (Cyan outer, Magenta inner)
            pix.setColor(COL_CYAN);
            pix.drawRectangle(0, 0, width, height);
            pix.drawRectangle(1, 1, width - 2, height - 2);

            pix.setColor(COL_MAGENTA);
            pix.drawRectangle(4, 4, width - 8, height - 8);

            // Corner accents (Jester motley diamond motif)
            drawCornerMotif(pix, 8, 8);
            drawCornerMotif(pix, width - 24, 8);
            drawCornerMotif(pix, 8, height - 24);
            drawCornerMotif(pix, width - 24, height - 24);

            Texture tex = new Texture(pix);
            pix.dispose();
            return tex;
        } catch (Throwable t) {
            return null;
        }
    }

    private void drawCornerMotif(Pixmap pix, int x, int y) {
        pix.setColor(COL_VIOLET);
        pix.fillRectangle(x, y, 16, 16);
        pix.setColor(COL_CYAN);
        pix.drawRectangle(x, y, 16, 16);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        shaderTime += delta;

        // Sync persistent HUD Pill
        ChaosManager chaos = ChaosManager.getInstance();
        if (chaos.isSurgeActive()) {
            hudPill.getColor().a = 1.0f;
            String name = chaos.getActiveEvent() != null ? chaos.getActiveEvent().getName().toUpperCase() : "CHAOS SURGE";
            int rem = chaos.getRemainingTurns();
            hudStatusLabel.setText(UiGlyphs.sanitize("[CHAOS] " + name + " (" + rem + " TURNS)"));
        } else {
            hudPill.getColor().a = 0.0f;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (Gdx.gl == null) return;
        boolean usingShader = chromaticShader != null && heraldBanner.getColor().a > 0.01f;
        if (usingShader) {
            batch.setShader(chromaticShader);
            chromaticShader.setUniformf("u_time", shaderTime);
            chromaticShader.setUniformf("u_chromaIntensity", 0.85f);
        }

        super.draw(batch, parentAlpha);

        if (usingShader) {
            batch.setShader(null);
        }
    }

    @Override
    public void onSurgeTriggered(ChaosSurgeEvent event, int duration) {
        if (event == null) return;

        eventNameLabel.setText(UiGlyphs.sanitize(event.getName().toUpperCase()));
        descLabel.setText(UiGlyphs.sanitize(event.getDescription()));
        durationLabel.setText(UiGlyphs.sanitize("CHAOS SURGE DURATION: " + duration + " TURNS"));

        heraldBanner.clearActions();
        heraldBanner.getColor().a = 0f;
        heraldBanner.addAction(Actions.sequence(
                Actions.fadeIn(FADE_IN),
                Actions.delay(HOLD_TIME),
                Actions.fadeOut(FADE_OUT)
        ));
    }

    @Override
    public void onSurgeExpired() {
        heraldBanner.clearActions();
        heraldBanner.addAction(Actions.fadeOut(0.4f));
    }

    public boolean isBannerVisible() {
        return heraldBanner.getColor().a > 0.01f;
    }

    public boolean isHudPillVisible() {
        return hudPill.getColor().a > 0.01f;
    }

    public String getCurrentBannerTitle() {
        return eventNameLabel.getText().toString();
    }

    @Override
    public void dispose() {
        ChaosManager.getInstance().removeListener(this);
        if (chromaticShader != null) {
            chromaticShader.dispose();
            chromaticShader = null;
        }
        if (frameTexture != null) {
            frameTexture.dispose();
            frameTexture = null;
        }
    }
}
