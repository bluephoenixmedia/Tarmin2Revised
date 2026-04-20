package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.AlchemyManager;
import com.bpm.minotaur.managers.AlchemyManager.Recipe;
import com.bpm.minotaur.managers.BalanceLogger;

public class AlchemyScreen extends BaseScreen {

    private final GameScreen parentScreen;
    private final Player player;

    private Stage stage;
    private Table rootTable;
    private Label toxicityLabel;
    private Label statusLabel;

    // UI Resources
    private BitmapFont font;
    private LabelStyle labelStyle;
    private TextButtonStyle buttonStyle;
    private Texture whitePixel;

    public AlchemyScreen(Tarmin2 game, GameScreen parentScreen, Player player) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
    }

    @Override
    public void show() {
        // Setup Stage
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        Gdx.input.setInputProcessor(stage);

        // Setup Styles
        font = new BitmapFont();
        font.getData().setScale(2f);

        labelStyle = new LabelStyle(font, Color.WHITE);

        // Simple pixel texture for buttons
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.DARK_GRAY);
        pixmap.fill();
        whitePixel = new Texture(pixmap);
        pixmap.dispose();

        buttonStyle = new TextButtonStyle();
        buttonStyle.font = font;
        buttonStyle.up = new TextureRegionDrawable(whitePixel);
        buttonStyle.down = new TextureRegionDrawable(whitePixel).tint(Color.LIGHT_GRAY);
        buttonStyle.over = new TextureRegionDrawable(whitePixel).tint(Color.GRAY);
        buttonStyle.fontColor = Color.WHITE;
        buttonStyle.disabledFontColor = Color.GRAY;

        rootTable = new Table();
        rootTable.setFillParent(true);
        stage.addActor(rootTable);

        // Title
        Label title = new Label("TOXIC ALCHEMY", labelStyle);
        title.setAlignment(Align.center);
        title.setFontScale(3f);
        title.setColor(Color.PURPLE);
        rootTable.add(title).padTop(20).colspan(2).row();

        // Toxicity Display
        toxicityLabel = new Label("Toxicity: " + player.getStats().getToxicity() + "%", labelStyle);
        rootTable.add(toxicityLabel).pad(20).colspan(2).row();

        // Content Table (Recipe List)
        Table recipeTable = new Table();
        recipeTable.top();

        Label recipeHeader = new Label("Recipes (Click to Craft)", labelStyle);
        recipeTable.add(recipeHeader).pad(10).row();

        for (final Recipe recipe : AlchemyManager.getInstance().getRecipes()) {
            boolean canCraft = AlchemyManager.getInstance().canCraft(player, recipe);
            String status = canCraft ? "[Available]" : "[Need Ingredients]";

            TextButton craftBtn = new TextButton(recipe.name + " " + status, buttonStyle);
            if (!canCraft) {
                craftBtn.setDisabled(true);
            }

            craftBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (craftBtn.isDisabled())
                        return;
                    performCraft(recipe);
                }
            });

            recipeTable.add(craftBtn).width(800).height(60).pad(5).row();
        }

        rootTable.add(recipeTable).expand().fill().row();

        // Status / Result area
        statusLabel = new Label("", labelStyle);
        rootTable.add(statusLabel).pad(20).colspan(2).row();

        // Back Button
        TextButton backBtn = new TextButton("Back to Inventory", buttonStyle);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // Return to Inventory
                // We need to pass the maze. Assuming parentScreen has it or we can get it from
                // somewhere.
                // Assuming GameScreen has getMaze() or we just passed it in constructor but
                // didn't store it separate from Player.
                // Wait, InventoryScreen needs 'Maze'.
                // If GameScreen doesn't expose it, we are in trouble.
                // However, usually GameScreen exposes it. I will assume getMaze() exists.
                game.setScreen(new InventoryScreen(game, parentScreen, player, parentScreen.getMaze()));
                dispose();
            }
        });
        rootTable.add(backBtn).width(400).height(80).padBottom(50).colspan(2);
    }

    private void performCraft(Recipe recipe) {
        Item result = AlchemyManager.getInstance().craft(
                player,
                recipe,
                game.getItemDataManager(),
                game.getAssetManager());

        if (result != null) {
            statusLabel.setText("Crafted " + result.getDisplayName() + "!");
            BalanceLogger.getInstance().log("CRAFTING", "Crafted " + recipe.name);
            // Refresh screen to update buttons
            game.setScreen(new AlchemyScreen(game, parentScreen, player));
            dispose();
        } else {
            statusLabel.setText("Crafting Failed.");
        }
    }

    @Override
    public void render(float delta) {
        // Clear screen
        com.badlogic.gdx.utils.ScreenUtils.clear(0.1f, 0.05f, 0.1f, 1f); // Dark purple bg

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (stage != null)
            stage.dispose();
        if (font != null)
            font.dispose();
        if (whitePixel != null)
            whitePixel.dispose();
    }
}
