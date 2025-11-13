package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.managers.MusicManager;


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

    // --- NEW: Game Mode Selection ---
    private GameMode selectedGameMode = GameMode.ADVANCED; // Default to Classic

    // Exact color palette from the original Intellivision title screen.
    // (Color definitions remain the same)
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
        // (Font loading remains the same)
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/intellivision.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = (int)(game.getViewport().getWorldHeight() * 0.07f);
        parameter.color = INTV_WHITE;
        parameter.minFilter = Texture.TextureFilter.Nearest;
        parameter.magFilter = Texture.TextureFilter.Nearest;
        titleFont = generator.generateFont(parameter);
        parameter.size = (int)(game.getViewport().getWorldHeight() * 0.05f);
        regularFont = generator.generateFont(parameter);
        generator.dispose();

        shapeRenderer = new ShapeRenderer();
        layout = new GlyphLayout();
        animationTimer = 0f;
        textBlink = false;
        modernBackground = new Texture(Gdx.files.internal("images/tarmin_title.png"));


        Gdx.input.setInputProcessor(this);

        // --- Start music (Removed isPlaying check) ---
        MusicManager.getInstance().playTrack("sounds/music/tarmin_ambient.ogg");
    }

    @Override
    public void render(float delta) {
        // (Rendering logic remains mostly the same, ensuring viewport is used)
        animationTimer += delta;
        if (inputCooldown > 0) {
            inputCooldown -= delta;
        }
        textBlink = animationTimer % 1.6f < 0.8f;
        float targetWidth = game.getViewport().getWorldWidth();
        float targetHeight = game.getViewport().getWorldHeight();

        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            ScreenUtils.clear(Color.BLACK);
            game.getBatch().setProjectionMatrix(game.getViewport().getCamera().combined);
            game.getBatch().begin();
            game.getBatch().draw(modernBackground, 0, 0, targetWidth, targetHeight);
            regularFont.setColor(INTV_WHITE);
            String modeText = "Mode: " + selectedGameMode.name() + " (Press M to change)";
            layout.setText(regularFont, modeText);
            float modeX = (targetWidth - layout.width) / 2f;
            regularFont.draw(game.getBatch(), modeText, modeX, targetHeight * 0.25f);
            if (textBlink) {
                drawCenteredText(regularFont, "PRESS ANY KEY TO START", targetHeight * 0.15f);
            }
            game.getBatch().end();
        } else {
            ScreenUtils.clear(OLIVE_GREEN);
            shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            // (Banner drawing logic remains the same)
            float rectWidth = targetWidth / 16f;
            float rectHeight = targetHeight / 20f;
            float bannerY = targetHeight * 0.85f;
            float totalBannerWidth = (rectWidth * 8);
            float startX = (targetWidth - totalBannerWidth) / 2f;
            Color[] leftColors = {INTV_WHITE, INTV_YELLOW, INTV_GREEN, INTV_DARK_GREEN};
            for (int i = 0; i < 4; i++) {
                shapeRenderer.setColor(leftColors[i]);
                shapeRenderer.rect(startX + (i * rectWidth), bannerY, rectWidth * 0.9f, rectHeight);
            }
            Color[] rightColors = {INTV_TAN, INTV_RED, INTV_BLUE, INTV_BLACK};
            float rightStartX = startX + (4 * rectWidth);
            for (int i = 0; i < 4; i++) {
                shapeRenderer.setColor(rightColors[i]);
                shapeRenderer.rect(rightStartX + (i * rectWidth), bannerY, rectWidth * 0.9f, rectHeight);
            }
            shapeRenderer.end();

            game.getBatch().setProjectionMatrix(game.getViewport().getCamera().combined);
            game.getBatch().begin();
            // (Title text drawing remains the same)
            titleFont.setColor(INTV_WHITE);
            drawCenteredText(titleFont, "THE LEGEND OF GALOR", targetHeight * 0.75f);
            drawCenteredText(titleFont, "TARMIN RETURNS", targetHeight * 0.65f);
            drawCenteredText(titleFont, "CARTRIDGE", targetHeight * 0.55f);

            regularFont.setColor(INTV_WHITE);
            String modeText = "Mode: " + selectedGameMode.name() + " (Press M)";
            layout.setText(regularFont, modeText);
            float modeX = (targetWidth - layout.width) / 2f;
            regularFont.draw(game.getBatch(), modeText, modeX, targetHeight * 0.45f);
            if (textBlink) {
                drawCenteredText(regularFont, "PRESS ANY KEY TO START", targetHeight * 0.35f);
            }
            drawCenteredText(regularFont, "*TM OF TSR HOBBIES", targetHeight * 0.20f);
            drawCenteredText(regularFont, "TSR", targetHeight * 0.13f);
            game.getBatch().end();
        }
    }

    private void drawCenteredText(BitmapFont font, String text, float y) {
        float targetWidth = game.getViewport().getWorldWidth();
        layout.setText(font, text);
        float x = (targetWidth - layout.width) / 2f;
        font.draw(game.getBatch(), text, x, y);
    }

    private void startGame() {
        if (inputCooldown <= 0) {
            Gdx.app.log("MainMenuScreen", "Starting game in mode: " + selectedGameMode);
            MusicManager.getInstance().stop(); // Stop menu music
            // TODO: Add sound effect for starting game if desired
            // enterSound.play(SettingsManager.getInstance().getSfxVolume());

            // --- FIXED: Call the CORRECT GameScreen constructor (4 args) ---
            // Pass the selected GameMode along with default difficulty
            game.setScreen(new GameScreen(game, 1, Difficulty.EASY, selectedGameMode));
        }
    }


    @Override
    public void dispose() {
        // (Dispose logic remains the same)
        if (titleFont != null) titleFont.dispose();
        if (regularFont != null) regularFont.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (modernBackground != null) modernBackground.dispose();
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.F2) {
            debugManager.toggleRenderMode();
            return true;
        } else if (keycode == Input.Keys.M) {
            selectedGameMode = (selectedGameMode == GameMode.CLASSIC) ? GameMode.ADVANCED : GameMode.CLASSIC;
            Gdx.app.log("MainMenuScreen", "GameMode toggled to: " + selectedGameMode);
            return true;
        } else if (inputCooldown <= 0) {
            game.setScreen(new SettingsScreen(game)); // <-- This is CORRECT
            startGame();
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        game.setScreen(new SettingsScreen(game)); // <-- This is the FIX
        startGame();
        // Return based on cooldown check inside startGame
        return inputCooldown <= 0;
    }

    // --- Unused InputProcessor methods ---
    @Override public void resize(int width, int height) { game.getViewport().update(width, height, true); }
    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }
}
