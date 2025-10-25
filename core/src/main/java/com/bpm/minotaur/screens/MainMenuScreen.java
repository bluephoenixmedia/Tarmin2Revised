package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.managers.DebugManager;

/**
 * An authentic recreation of the original game's title screen,
 * based on analysis of game.asm and the provided reference implementation.
 */
public class MainMenuScreen extends BaseScreen implements InputProcessor {

    private BitmapFont titleFont;
    private BitmapFont regularFont;
    private ShapeRenderer shapeRenderer;
    private GlyphLayout layout;
    private float animationTimer;
    private boolean textBlink;
    private final DebugManager debugManager = DebugManager.getInstance();
    private Texture modernBackground;

    // Cooldown timer to prevent immediate screen transition on first input.
    private float inputCooldown = 0.2f;

    // The virtual width and height are now managed by the viewport in Tarmin2.java
    private static final float TARGET_WIDTH = 1920f;
    private static final float TARGET_HEIGHT = 1080f;

    // Exact color palette from the original Intellivision title screen.
    private static final Color INTV_WHITE = new Color(253/255f, 253/255f, 253/255f, 1.0f);
    private static final Color INTV_YELLOW = new Color(249/255f, 234/255f, 79/255f, 1.0f);
    private static final Color INTV_GREEN = new Color(0/255f, 167/255f, 88/255f, 1.0f);
    private static final Color INTV_DARK_GREEN = new Color(56/255f, 107/255f, 64/255f, 1.0f);
    private static final Color INTV_TAN = new Color(200/255f, 207/255f, 172/255f, 1.0f);
    private static final Color INTV_RED = new Color(254/255f, 60/255f, 17/255f, 1.0f);
    private static final Color INTV_BLUE = new Color(0/255f, 44/255f, 254/255f, 1.0f);
    private static final Color INTV_BLACK = new Color(1/255f, 1/255f, 0/255f, 1.0f);
    private static final Color OLIVE_GREEN = new Color(85/255f, 110/255f, 0/255f, 1.0f);

    public MainMenuScreen(Tarmin2 game) {
        super(game);
    }

    @Override
    public void show() {
        // Load the custom TTF font.
        // This requires "intellivision.ttf" in the assets/fonts folder.
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/intellivision.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();

        parameter.size = 72;
        parameter.color = INTV_WHITE;
        parameter.minFilter = Texture.TextureFilter.Nearest;
        parameter.magFilter = Texture.TextureFilter.Nearest;
        titleFont = generator.generateFont(parameter);

        parameter.size = 54;
        regularFont = generator.generateFont(parameter);

        generator.dispose();

        shapeRenderer = new ShapeRenderer();
        layout = new GlyphLayout();
        animationTimer = 0f;
        textBlink = false;
        modernBackground = new Texture(Gdx.files.internal("images/tarmin_title.png"));


        Gdx.input.setInputProcessor(this);
    }

    @Override
    public void render(float delta) {
        animationTimer += delta;
        if (inputCooldown > 0) {
            inputCooldown -= delta;
        }

        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            ScreenUtils.clear(Color.BLACK);
            game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
            game.batch.begin();
            game.batch.draw(modernBackground, 0, 0, TARGET_WIDTH, TARGET_HEIGHT);
            game.batch.end();
        } else {
            // Controls the blinking "PRESS ANY KEY" text.
            textBlink = animationTimer % 1.6f < 0.8f;

            // Set the background color to the authentic olive green.
            ScreenUtils.clear(OLIVE_GREEN);

            // Update the ShapeRenderer's projection matrix with the viewport's camera.
            shapeRenderer.setProjectionMatrix(game.viewport.getCamera().combined);

            // Draw the colored banner rectangles at the top of the screen.
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            float rectWidth = TARGET_WIDTH / 16f;
            float rectHeight = TARGET_HEIGHT / 20f;
            float bannerY = TARGET_HEIGHT * 0.85f;
            float totalBannerWidth = (rectWidth * 8) + (rectWidth * 2);
            float startX = (TARGET_WIDTH - totalBannerWidth) / 2f;

            Color[] leftColors = {INTV_WHITE, INTV_YELLOW, INTV_GREEN, INTV_DARK_GREEN};
            for (int i = 0; i < 4; i++) {
                shapeRenderer.setColor(leftColors[i]);
                shapeRenderer.rect(startX + (i * rectWidth), bannerY, rectWidth - 6, rectHeight);
            }

            Color[] rightColors = {INTV_TAN, INTV_RED, INTV_BLUE, INTV_BLACK};
            float rightStartX = startX + (4 * rectWidth) + (rectWidth * 2);
            for (int i = 0; i < 4; i++) {
                shapeRenderer.setColor(rightColors[i]);
                shapeRenderer.rect(rightStartX + (i * rectWidth), bannerY, rectWidth - 6, rectHeight);
            }
            shapeRenderer.end();

            // The SpriteBatch projection matrix is updated in Tarmin2.java's resize method.
            game.batch.begin();
            titleFont.setColor(INTV_WHITE);
            drawCenteredText(titleFont, "THE LEGEND OF GALOR", TARGET_HEIGHT * 0.75f);
            drawCenteredText(titleFont, "TARMIN RETURNS", TARGET_HEIGHT * 0.65f);
            drawCenteredText(titleFont, "CARTRIDGE", TARGET_HEIGHT * 0.55f);

            if (textBlink) {
                drawCenteredText(regularFont, "PRESS ANY KEY TO START", TARGET_HEIGHT * 0.35f);
            }

            drawCenteredText(regularFont, "*TM OF TSR HOBBIES", TARGET_HEIGHT * 0.20f);
            drawCenteredText(regularFont, "TSR", TARGET_HEIGHT * 0.13f);

            game.batch.end();
        }
    }

    private void drawCenteredText(BitmapFont font, String text, float y) {
        layout.setText(font, text);
        float x = (TARGET_WIDTH - layout.width) / 2f;
        font.draw(game.batch, text, x, y);
    }

    @Override
    public void dispose() {
        if (titleFont != null) titleFont.dispose();
        if (regularFont != null) regularFont.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (modernBackground != null) modernBackground.dispose();
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.F2) {
            debugManager.toggleRenderMode();
        } else
        if (inputCooldown <= 0) {
            Gdx.app.log("MainMenuScreen", "Key pressed. Starting game...");
            game.setScreen(new SettingsScreen(game));
            //game.setScreen(new GameScreen(game, 1, Difficulty.EASIEST));
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (inputCooldown <= 0) {
            Gdx.app.log("MainMenuScreen", "Screen touched. Starting game...");
            game.setScreen(new GameScreen(game,1,  Difficulty.EASIEST));
            return true;
        }
        return false;
    }

    // --- Unused InputProcessor methods ---
    // The resize method is now handled globally by Tarmin2.java
    @Override public void resize(int width, int height) { }
    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }
}
