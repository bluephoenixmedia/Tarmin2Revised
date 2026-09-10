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
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
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
 * New Game, Settings, and Quit.
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

    private final List<TextButton> menuButtons = new ArrayList<>();
    private int selectedButtonIndex = 0;

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

        // Stage setup
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        buildMenuUI();

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        MusicManager.getInstance().playTrack("sounds/music/tarmin_ambient.ogg");
    }

    private void createButtonTextures() {
        // Normal Button: Dark slate with subtle blue-gray border
        Pixmap pix = new Pixmap(460, 56, Pixmap.Format.RGBA8888);
        pix.setColor(0.10f, 0.12f, 0.18f, 0.88f);
        pix.fill();
        pix.setColor(0.35f, 0.42f, 0.55f, 1f);
        pix.drawRectangle(0, 0, 460, 56);
        pix.drawRectangle(1, 1, 458, 54);
        buttonBg = new Texture(pix);
        pix.dispose();

        // Hover / Focused Button: Deep navy with glowing gold border
        Pixmap overPix = new Pixmap(460, 56, Pixmap.Format.RGBA8888);
        overPix.setColor(0.18f, 0.28f, 0.42f, 0.95f);
        overPix.fill();
        overPix.setColor(0.98f, 0.85f, 0.35f, 1f);
        overPix.drawRectangle(0, 0, 460, 56);
        overPix.drawRectangle(1, 1, 458, 54);
        buttonBgOver = new Texture(overPix);
        overPix.dispose();

        // Disabled Button: Faded dark background
        Pixmap disPix = new Pixmap(460, 56, Pixmap.Format.RGBA8888);
        disPix.setColor(0.08f, 0.09f, 0.12f, 0.45f);
        disPix.fill();
        disPix.setColor(0.22f, 0.24f, 0.30f, 0.5f);
        disPix.drawRectangle(0, 0, 460, 56);
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

        TextButton.TextButtonStyle defaultStyle = new TextButton.TextButtonStyle();
        defaultStyle.font = buttonFont;
        defaultStyle.fontColor = INTV_WHITE;
        defaultStyle.overFontColor = INTV_YELLOW;
        defaultStyle.disabledFontColor = Color.GRAY;
        defaultStyle.up = new TextureRegionDrawable(buttonBg);
        defaultStyle.over = new TextureRegionDrawable(buttonBgOver);
        defaultStyle.disabled = new TextureRegionDrawable(buttonBgDisabled);

        // 1. CONTINUE BUTTON
        final int recentSlot = SaveManager.getInstance().getMostRecentOccupiedSlot();
        boolean hasSave = recentSlot >= 1;

        String continueLabel = "CONTINUE (C)";
        if (hasSave) {
            SlotMetadata meta = SaveManager.getInstance().getSlotMetadata(recentSlot);
            continueLabel = "CONTINUE: SLOT " + recentSlot + " (LVL " + meta.level + " " + meta.characterClass + ")";
        }

        TextButton.TextButtonStyle continueStyle = new TextButton.TextButtonStyle(defaultStyle);
        if (hasSave) {
            continueStyle.fontColor = INTV_YELLOW;
        }

        TextButton continueBtn = new TextButton(continueLabel, continueStyle);
        continueBtn.setDisabled(!hasSave);
        if (hasSave) {
            continueBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (inputCooldown <= 0) {
                        SaveSlotSelectScreen.launchResumeGame(game, recentSlot);
                    }
                }
            });
        }
        buttonTable.add(continueBtn).width(500).height(54).padBottom(14).row();
        menuButtons.add(continueBtn);

        // 2. LOAD GAME BUTTON
        TextButton loadBtn = new TextButton("LOAD EXPEDITION (L)", defaultStyle);
        loadBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (inputCooldown <= 0) {
                    game.setScreen(new SaveSlotSelectScreen(game, MainMenuScreen.this, false));
                }
            }
        });
        buttonTable.add(loadBtn).width(500).height(54).padBottom(14).row();
        menuButtons.add(loadBtn);

        // 3. NEW GAME BUTTON
        TextButton newGameBtn = new TextButton("NEW EXPEDITION (N)", defaultStyle);
        newGameBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (inputCooldown <= 0) {
                    game.setScreen(new SaveSlotSelectScreen(game, MainMenuScreen.this, true));
                }
            }
        });
        buttonTable.add(newGameBtn).width(500).height(54).padBottom(14).row();
        menuButtons.add(newGameBtn);

        // 4. SETTINGS BUTTON
        TextButton settingsBtn = new TextButton("SETTINGS (S)", defaultStyle);
        settingsBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (inputCooldown <= 0) {
                    game.setScreen(new SettingsScreen(game));
                }
            }
        });
        buttonTable.add(settingsBtn).width(500).height(54).padBottom(14).row();
        menuButtons.add(settingsBtn);

        // 5. QUIT BUTTON
        TextButton exitBtn = new TextButton("QUIT TO DESKTOP (ESC)", defaultStyle);
        exitBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (inputCooldown <= 0) {
                    Gdx.app.exit();
                }
            }
        });
        buttonTable.add(exitBtn).width(500).height(54).row();
        menuButtons.add(exitBtn);

        // In Modern mode, pad down so it sits below title logo; in INTV mode below retro title
        root.add(buttonTable).padTop(240).row();
        stage.addActor(root);

        // Default keyboard selection
        selectedButtonIndex = hasSave ? 0 : 1;
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
            game.getBatch().begin();
            regularFont.setColor(INTV_WHITE);
            String modeStr = "F2: RETRO VIEW  |  M: MODE [" + selectedGameMode.name() + "]";
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
            game.getBatch().begin();
            regularFont.setColor(INTV_WHITE);
            drawCenteredText(regularFont, "*TM OF TSR HOBBIES  |  F2: MODERN VIEW", targetHeight * 0.05f);
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

        if (inputCooldown > 0) {
            return false;
        }

        // Direct shortcut keys
        if (keycode == Input.Keys.C) {
            int recentSlot = SaveManager.getInstance().getMostRecentOccupiedSlot();
            if (recentSlot >= 1) {
                SaveSlotSelectScreen.launchResumeGame(game, recentSlot);
                return true;
            }
        } else if (keycode == Input.Keys.L) {
            game.setScreen(new SaveSlotSelectScreen(game, this, false));
            return true;
        } else if (keycode == Input.Keys.N) {
            game.setScreen(new SaveSlotSelectScreen(game, this, true));
            return true;
        } else if (keycode == Input.Keys.S) {
            game.setScreen(new SettingsScreen(game));
            return true;
        } else if (keycode == Input.Keys.ESCAPE) {
            Gdx.app.exit();
            return true;
        }

        return false;
    }

    @Override
    public void resize(int width, int height) {
        game.getViewport().update(width, height, true);
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
