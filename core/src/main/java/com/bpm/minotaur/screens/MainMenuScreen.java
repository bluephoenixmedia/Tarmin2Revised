package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.save.SlotMetadata;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.managers.MusicManager;
import com.bpm.minotaur.managers.SaveManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Main Title Screen supporting both Modern and Retro INTV visuals.
 * Provides rich multi-slot save interactions: Continue, Load Game,
 * New Game, Settings, and Quit with unified keyboard and mouse navigation.
 */
public class MainMenuScreen extends BaseScreen implements InputProcessor {

    private Stage stage;
    private BitmapFont titleFont;
    private BitmapFont regularFont;
    private BitmapFont buttonFont;
    private ShapeRenderer shapeRenderer;
    private GlyphLayout layout;
    private float animationTimer;
    private boolean textBlink;
    private final DebugManager debugManager = DebugManager.getInstance();
    private Texture modernBackground;

    private Texture buttonBg;
    private Texture buttonBgOver;
    private Texture buttonBgDisabled;

    private TextButton.TextButtonStyle defaultStyle;
    private TextButton.TextButtonStyle focusedStyle;
    private TextButton.TextButtonStyle continueStyle;
    private TextButton.TextButtonStyle continueFocusedStyle;
    private TextButton.TextButtonStyle disabledStyle;

    private final List<TextButton> menuButtons = new ArrayList<>();
    private int selectedButtonIndex = 0;
    private boolean hasSave = false;

    // Cooldown timer to prevent accidental immediate screen transitions
    private float inputCooldown = 0.2f;

    // Game Mode Selection (toggleable via 'M' key)
    private GameMode selectedGameMode = GameMode.ADVANCED;

    // Exact color palette from the original Intellivision title screen
    private static final Color INTV_WHITE = new Color(253 / 255f, 253 / 255f, 253 / 255f, 1.0f);
    private static final Color INTV_YELLOW = new Color(249 / 255f, 234 / 255f, 79 / 255f, 1.0f);
    private static final Color INTV_GREEN = new Color(0 / 255f, 167 / 255f, 88 / 255f, 1.0f);
    private static final Color INTV_DARK_GREEN = new Color(56 / 255f, 107 / 255f, 64 / 255f, 1.0f);
    private static final Color INTV_TAN = new Color(200 / 255f, 207 / 255f, 172 / 255f, 1.0f);
    private static final Color INTV_RED = new Color(254 / 255f, 60 / 255f, 17 / 255f, 1.0f);
    private static final Color INTV_BLUE = new Color(0 / 255f, 44 / 255f, 254 / 255f, 1.0f);
    private static final Color INTV_BLACK = new Color(1 / 255f, 1 / 255f, 0 / 255f, 1.0f);
    private static final Color OLIVE_GREEN = new Color(85 / 255f, 110 / 255f, 0 / 255f, 1.0f);

    public MainMenuScreen(Tarmin2 game) {
        super(game);
    }

    @Override
    public void show() {
        float worldHeight = game.getViewport().getWorldHeight();

        // Generate Intellivision TTF fonts
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/intellivision.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = (int) (worldHeight * 0.07f);
        parameter.color = INTV_WHITE;
        parameter.minFilter = Texture.TextureFilter.Nearest;
        parameter.magFilter = Texture.TextureFilter.Nearest;
        titleFont = generator.generateFont(parameter);

        parameter.size = (int) (worldHeight * 0.045f);
        regularFont = generator.generateFont(parameter);

        parameter.size = (int) (worldHeight * 0.035f);
        buttonFont = generator.generateFont(parameter);
        generator.dispose();

        shapeRenderer = new ShapeRenderer();
        layout = new GlyphLayout();
        animationTimer = 0f;
        textBlink = false;
        inputCooldown = 0.25f;

        modernBackground = new Texture(Gdx.files.internal("images/tarmin_title.png"));

        // Generate procedural UI textures for sleek button backgrounds
        createButtonTextures();

        // Stage setup using the game's shared FitViewport
        stage = new Stage(game.getViewport(), game.getBatch());
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        buildMenuUI();

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        MusicManager.getInstance().playTrack("sounds/music/tarmin_ambient.ogg");
    }

    private void createButtonTextures() {
        // Normal Button: Dark slate with subtle blue-gray border
        Pixmap pix = new Pixmap(500, 54, Pixmap.Format.RGBA8888);
        pix.setColor(0.10f, 0.12f, 0.18f, 0.88f);
        pix.fill();
        pix.setColor(0.35f, 0.42f, 0.55f, 1f);
        pix.drawRectangle(0, 0, 500, 54);
        pix.drawRectangle(1, 1, 498, 52);
        buttonBg = new Texture(pix);
        pix.dispose();

        // Hover / Focused Button: Deep navy with glowing gold border
        Pixmap overPix = new Pixmap(500, 54, Pixmap.Format.RGBA8888);
        overPix.setColor(0.18f, 0.28f, 0.42f, 0.95f);
        overPix.fill();
        overPix.setColor(0.98f, 0.85f, 0.35f, 1f);
        overPix.drawRectangle(0, 0, 500, 54);
        overPix.drawRectangle(1, 1, 498, 52);
        buttonBgOver = new Texture(overPix);
        overPix.dispose();

        // Disabled Button: Faded dark background
        Pixmap disPix = new Pixmap(500, 54, Pixmap.Format.RGBA8888);
        disPix.setColor(0.08f, 0.09f, 0.12f, 0.45f);
        disPix.fill();
        disPix.setColor(0.22f, 0.24f, 0.30f, 0.5f);
        disPix.drawRectangle(0, 0, 500, 54);
        buttonBgDisabled = new Texture(disPix);
        disPix.dispose();
    }

    private void buildMenuUI() {
        stage.clear();
        menuButtons.clear();

        Table root = new Table();
        root.setFillParent(true);
        root.center();

        Table buttonTable = new Table();

        defaultStyle = new TextButton.TextButtonStyle();
        defaultStyle.font = buttonFont;
        defaultStyle.fontColor = INTV_WHITE;
        defaultStyle.overFontColor = INTV_YELLOW;
        defaultStyle.disabledFontColor = Color.GRAY;
        defaultStyle.up = new TextureRegionDrawable(buttonBg);
        defaultStyle.over = new TextureRegionDrawable(buttonBgOver);
        defaultStyle.disabled = new TextureRegionDrawable(buttonBgDisabled);

        focusedStyle = new TextButton.TextButtonStyle();
        focusedStyle.font = buttonFont;
        focusedStyle.fontColor = INTV_YELLOW;
        focusedStyle.overFontColor = INTV_YELLOW;
        focusedStyle.disabledFontColor = Color.GRAY;
        focusedStyle.up = new TextureRegionDrawable(buttonBgOver);
        focusedStyle.over = new TextureRegionDrawable(buttonBgOver);
        focusedStyle.disabled = new TextureRegionDrawable(buttonBgDisabled);

        continueStyle = new TextButton.TextButtonStyle(defaultStyle);
        continueStyle.fontColor = INTV_YELLOW;

        continueFocusedStyle = new TextButton.TextButtonStyle(focusedStyle);
        continueFocusedStyle.fontColor = INTV_YELLOW;

        disabledStyle = new TextButton.TextButtonStyle(defaultStyle);
        disabledStyle.up = new TextureRegionDrawable(buttonBgDisabled);

        // 1. CONTINUE BUTTON
        final int recentSlot = SaveManager.getInstance().getMostRecentOccupiedSlot();
        hasSave = recentSlot >= 1;

        String continueLabel = "CONTINUE (C)";
        if (hasSave) {
            SlotMetadata meta = SaveManager.getInstance().getSlotMetadata(recentSlot);
            continueLabel = "CONTINUE: SLOT " + recentSlot + " (LVL " + meta.level + " " + meta.characterClass + ")";
        }

        final TextButton continueBtn = new TextButton(continueLabel, hasSave ? continueStyle : defaultStyle);
        continueBtn.setDisabled(!hasSave);
        setupButton(continueBtn, 0);
        buttonTable.add(continueBtn).width(500).height(54).padBottom(14).row();
        menuButtons.add(continueBtn);

        // 2. LOAD GAME BUTTON
        final TextButton loadBtn = new TextButton("LOAD EXPEDITION (L)", defaultStyle);
        setupButton(loadBtn, 1);
        buttonTable.add(loadBtn).width(500).height(54).padBottom(14).row();
        menuButtons.add(loadBtn);

        // 3. NEW GAME BUTTON
        final TextButton newGameBtn = new TextButton("NEW EXPEDITION (N)", defaultStyle);
        setupButton(newGameBtn, 2);
        buttonTable.add(newGameBtn).width(500).height(54).padBottom(14).row();
        menuButtons.add(newGameBtn);

        // 4. SETTINGS BUTTON
        final TextButton settingsBtn = new TextButton("SETTINGS (S)", defaultStyle);
        setupButton(settingsBtn, 3);
        buttonTable.add(settingsBtn).width(500).height(54).padBottom(14).row();
        menuButtons.add(settingsBtn);

        // 5. QUIT BUTTON
        final TextButton exitBtn = new TextButton("QUIT TO DESKTOP (ESC)", defaultStyle);
        setupButton(exitBtn, 4);
        buttonTable.add(exitBtn).width(500).height(54).row();
        menuButtons.add(exitBtn);

        root.add(buttonTable).padTop(240).row();
        stage.addActor(root);

        // Default selection: Continue if save exists, else New Game
        selectedButtonIndex = hasSave ? 0 : 2;
        updateButtonStyles();
    }

    private void setupButton(final TextButton btn, final int index) {
        btn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!btn.isDisabled()) {
                    triggerButton(index);
                }
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                super.enter(event, x, y, pointer, fromActor);
                if (!btn.isDisabled() && pointer == -1) {
                    selectedButtonIndex = index;
                    updateButtonStyles();
                }
            }
        });
    }

    private void updateButtonStyles() {
        for (int i = 0; i < menuButtons.size(); i++) {
            TextButton btn = menuButtons.get(i);
            if (btn.isDisabled()) {
                btn.setStyle(disabledStyle);
                continue;
            }
            if (i == selectedButtonIndex) {
                btn.setStyle((i == 0 && hasSave) ? continueFocusedStyle : focusedStyle);
            } else {
                btn.setStyle((i == 0 && hasSave) ? continueStyle : defaultStyle);
            }
        }
    }

    private void selectPreviousButton() {
        if (menuButtons.isEmpty()) return;
        int next = selectedButtonIndex;
        for (int i = 0; i < menuButtons.size(); i++) {
            next = (next - 1 + menuButtons.size()) % menuButtons.size();
            if (!menuButtons.get(next).isDisabled()) {
                selectedButtonIndex = next;
                updateButtonStyles();
                break;
            }
        }
    }

    private void selectNextButton() {
        if (menuButtons.isEmpty()) return;
        int next = selectedButtonIndex;
        for (int i = 0; i < menuButtons.size(); i++) {
            next = (next + 1) % menuButtons.size();
            if (!menuButtons.get(next).isDisabled()) {
                selectedButtonIndex = next;
                updateButtonStyles();
                break;
            }
        }
    }

    private void triggerButton(int index) {
        if (inputCooldown > 0) return;
        switch (index) {
            case 0: // CONTINUE
                final int recentSlot = SaveManager.getInstance().getMostRecentOccupiedSlot();
                if (recentSlot >= 1) {
                    SaveSlotSelectScreen.launchResumeGame(game, recentSlot);
                }
                break;
            case 1: // LOAD
                game.setScreen(new SaveSlotSelectScreen(game, this, false));
                break;
            case 2: // NEW GAME
                game.setScreen(new SaveSlotSelectScreen(game, this, true));
                break;
            case 3: // SETTINGS
                game.setScreen(new SettingsScreen(game));
                break;
            case 4: // QUIT
                Gdx.app.exit();
                break;
        }
    }

    @Override
    public void render(float delta) {
        animationTimer += delta;
        if (inputCooldown > 0) {
            inputCooldown -= delta;
        }
        textBlink = animationTimer % 1.6f < 0.8f;
        float targetWidth = game.getViewport().getWorldWidth();
        float targetHeight = game.getViewport().getWorldHeight();

        // Ensure viewport is applied
        game.getViewport().apply();

        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            // Modern Visual Mode
            ScreenUtils.clear(Color.BLACK);
            game.getBatch().setProjectionMatrix(game.getViewport().getCamera().combined);
            game.getBatch().begin();
            game.getBatch().draw(modernBackground, 0, 0, targetWidth, targetHeight);
            game.getBatch().end();

            // Render Stage UI Buttons
            stage.act(delta);
            stage.draw();

            // Bottom status / hint bar
            game.getBatch().setProjectionMatrix(game.getViewport().getCamera().combined);
            game.getBatch().begin();
            regularFont.setColor(INTV_WHITE);
            String modeStr = "F2: RETRO VIEW  |  M: MODE [" + selectedGameMode.name() + "]  |  UP/DOWN/ENTER: NAVIGATE";
            drawCenteredText(regularFont, modeStr, targetHeight * 0.05f);
            game.getBatch().end();

        } else {
            // Classic 1982 Intellivision Visual Mode
            ScreenUtils.clear(OLIVE_GREEN);
            shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

            float rectWidth = targetWidth / 16f;
            float rectHeight = targetHeight / 20f;
            float bannerY = targetHeight * 0.88f;
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
            titleFont.setColor(INTV_WHITE);
            drawCenteredText(titleFont, "THE LEGEND OF GALOR", targetHeight * 0.80f);
            drawCenteredText(titleFont, "TARMIN RETURNS", targetHeight * 0.72f);
            drawCenteredText(titleFont, "CARTRIDGE", targetHeight * 0.64f);
            game.getBatch().end();

            // Render Stage UI Buttons
            stage.act(delta);
            stage.draw();

            // Bottom credits
            game.getBatch().setProjectionMatrix(game.getViewport().getCamera().combined);
            game.getBatch().begin();
            regularFont.setColor(INTV_WHITE);
            drawCenteredText(regularFont, "*TM OF TSR HOBBIES  |  F2: MODERN VIEW  |  UP/DOWN/ENTER: NAVIGATE", targetHeight * 0.05f);
            game.getBatch().end();
        }
    }

    private void drawCenteredText(BitmapFont font, String text, float y) {
        float targetWidth = game.getViewport().getWorldWidth();
        layout.setText(font, text);
        float x = (targetWidth - layout.width) / 2f;
        font.draw(game.getBatch(), text, x, y);
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
        }

        // Arrow and Vim navigation
        if (keycode == Input.Keys.UP || keycode == Input.Keys.W || keycode == Input.Keys.K) {
            selectPreviousButton();
            return true;
        } else if (keycode == Input.Keys.DOWN || keycode == Input.Keys.J) {
            selectNextButton();
            return true;
        } else if (keycode == Input.Keys.TAB) {
            if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                selectPreviousButton();
            } else {
                selectNextButton();
            }
            return true;
        }

        // Activation keys
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER || keycode == Input.Keys.SPACE) {
            if (selectedButtonIndex >= 0 && selectedButtonIndex < menuButtons.size()) {
                TextButton btn = menuButtons.get(selectedButtonIndex);
                if (!btn.isDisabled()) {
                    triggerButton(selectedButtonIndex);
                    return true;
                }
            }
            return true;
        }

        if (inputCooldown > 0) {
            return false;
        }

        // Direct shortcut keys
        if (keycode == Input.Keys.C) {
            if (hasSave) {
                triggerButton(0);
                return true;
            }
        } else if (keycode == Input.Keys.L) {
            triggerButton(1);
            return true;
        } else if (keycode == Input.Keys.N) {
            triggerButton(2);
            return true;
        } else if (keycode == Input.Keys.S) {
            triggerButton(3);
            return true;
        } else if (keycode == Input.Keys.ESCAPE) {
            triggerButton(4);
            return true;
        }

        return false;
    }

    @Override
    public void resize(int width, int height) {
        if (game.getViewport() != null) {
            game.getViewport().update(width, height, true);
        }
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (titleFont != null) titleFont.dispose();
        if (regularFont != null) regularFont.dispose();
        if (buttonFont != null) buttonFont.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (modernBackground != null) modernBackground.dispose();
        if (buttonBg != null) buttonBg.dispose();
        if (buttonBgOver != null) buttonBgOver.dispose();
        if (buttonBgDisabled != null) buttonBgDisabled.dispose();
    }

    // --- Unused InputProcessor methods ---
    @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }
}
