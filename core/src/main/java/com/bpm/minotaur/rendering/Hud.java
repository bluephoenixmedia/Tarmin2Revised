package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Item;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Player;

public class Hud implements Disposable {

    public Stage stage;
    private final Viewport viewport;
    private final Player player;
    private final Maze maze; // Added maze reference
    private final BitmapFont font;
    private final BitmapFont directionFont;
    private final SpriteBatch spriteBatch;
    private final ShapeRenderer shapeRenderer;

    private final Texture hudBackground;

    private final Label warStrengthValueLabel;
    private final Label spiritualStrengthValueLabel;
    private final Label foodValueLabel;
    private final Label arrowsValueLabel;
    private final Label directionLabel;
    private final Label dungeonLevelLabel; // Added this line

    private final Actor[] inventorySlots = new Actor[8];

    public Hud(SpriteBatch sb, Player player, Maze maze) { // Added maze parameter
        this.player = player;
        this.maze = maze; // Store maze reference
        this.spriteBatch = sb;
        this.shapeRenderer = new ShapeRenderer();
        viewport = new FitViewport(1920, 1080, new OrthographicCamera());
        stage = new Stage(viewport, sb);

        hudBackground = new Texture(Gdx.files.internal("images/hud_background.png"));

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/intellivision.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = 24;
        parameter.color = Color.WHITE;
        parameter.minFilter = Texture.TextureFilter.Nearest;
        parameter.magFilter = Texture.TextureFilter.Nearest;
        font = generator.generateFont(parameter);

        parameter.size = 48;
        directionFont = generator.generateFont(parameter);
        generator.dispose();

        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        Label.LabelStyle directionLabelStyle = new Label.LabelStyle(directionFont, Color.GOLD);

        // This table will contain the left and right sides of the HUD
        Table mainContentTable = new Table();

        // --- Left Side of HUD ---
        Table leftTable = new Table();
        directionLabel = new Label("", directionLabelStyle);
        for(int i = 0; i < 8; i++) { inventorySlots[i] = new Actor(); }
        float slotSize = 60f;
        leftTable.add(inventorySlots[0]).width(slotSize).height(slotSize);
        leftTable.add(inventorySlots[1]).width(slotSize).height(slotSize);
        leftTable.add(inventorySlots[2]).width(slotSize).height(slotSize);
        leftTable.row();
        leftTable.add(inventorySlots[3]).width(slotSize).height(slotSize);
        leftTable.add(directionLabel).width(slotSize).height(slotSize);
        leftTable.add(inventorySlots[4]).width(slotSize).height(slotSize);
        leftTable.row();
        leftTable.add(inventorySlots[5]).width(slotSize).height(slotSize);
        leftTable.add(inventorySlots[6]).width(slotSize).height(slotSize);
        leftTable.add(inventorySlots[7]).width(slotSize).height(slotSize);

        // --- Right Side of HUD ---
        Table rightTable = new Table();
        warStrengthValueLabel = new Label("", labelStyle);
        Table warTable = new Table();
        warTable.add(new Label("WS", labelStyle)).padRight(10);
        warTable.add(new Label("[S]", labelStyle)).padRight(10);
        warTable.add(warStrengthValueLabel);
        spiritualStrengthValueLabel = new Label("", labelStyle);
        Table spiritualTable = new Table();
        spiritualTable.add(new Label("SS", labelStyle)).padRight(10);
        spiritualTable.add(new Label("[+]", labelStyle)).padRight(10);
        spiritualTable.add(spiritualStrengthValueLabel);
        foodValueLabel = new Label("", labelStyle);
        Table foodTable = new Table();
        foodTable.add(new Label("FOOD", labelStyle)).padRight(10);
        foodTable.add(new Label("[F]", labelStyle)).padRight(10);
        foodTable.add(foodValueLabel);
        arrowsValueLabel = new Label("", labelStyle);
        Table arrowsTable = new Table();
        arrowsTable.add(new Label("ARROWS", labelStyle)).padRight(10);
        arrowsTable.add(new Label("[>]", labelStyle)).padRight(10);
        arrowsTable.add(arrowsValueLabel);
        rightTable.add(warTable).left().padBottom(20);
        rightTable.add(spiritualTable).right().expandX().padBottom(20);
        rightTable.row();
        rightTable.add(foodTable).left().padRight(100f);
        rightTable.add(arrowsTable).right().expandX();

        // Add left and right tables to the main content table
        mainContentTable.add(leftTable).width(500).padLeft(50);
        mainContentTable.add(rightTable).expandX().right().padRight(50);

        // --- Dungeon Level Table (Bottom Center) ---
        dungeonLevelLabel = new Label("", labelStyle);
        Table levelTable = new Table();
        levelTable.add(dungeonLevelLabel);

        // --- Main container table ---
        Table mainContainer = new Table();
        mainContainer.bottom();
        mainContainer.setFillParent(true);
        mainContainer.add(mainContentTable).expandX().fillX();
        mainContainer.row();
        mainContainer.add(levelTable).expandX().padBottom(10);

        stage.addActor(mainContainer);
    }

    public void update(float dt) {
        warStrengthValueLabel.setText(String.format("%d", player.getWarStrength()));
        spiritualStrengthValueLabel.setText(String.format("%d", player.getSpiritualStrength()));
        foodValueLabel.setText(String.format("%d", player.getFood()));
        arrowsValueLabel.setText(String.format("%d", player.getArrows()));
        directionLabel.setText(player.getFacing().name().substring(0,1));
        dungeonLevelLabel.setText("DUNGEON LEVEL " + maze.getLevel()); // Update level text
    }

    // ... (render, drawInventory, resize, and dispose methods are unchanged) ...
    public void render() {
        viewport.apply();

        spriteBatch.setProjectionMatrix(stage.getCamera().combined);
        spriteBatch.begin();
        spriteBatch.draw(hudBackground, 0, 0, 1920, 1080);
        spriteBatch.end();

        drawInventory();

        stage.draw();
    }

    private void drawInventory() {
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        Item[] backpack = player.getInventory().getBackpack();
        Item leftHand = player.getInventory().getLeftHand();
        Item rightHand = player.getInventory().getRightHand();

        Item[] itemsToDraw = new Item[] {
            backpack[0], backpack[1], backpack[2],
            leftHand, rightHand,
            backpack[3], backpack[4], backpack[5]
        };

        for (int i = 0; i < itemsToDraw.length; i++) {
            Item item = itemsToDraw[i];
            Actor slot = inventorySlots[i];

            if (item != null) {
                Vector2 pos = slot.localToStageCoordinates(new Vector2(0, 0));
                shapeRenderer.setColor(item.getColor());
                shapeRenderer.rect(pos.x, pos.y, slot.getWidth(), slot.getHeight());
            }
        }

        shapeRenderer.end();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
        font.dispose();
        directionFont.dispose();
        hudBackground.dispose();
        shapeRenderer.dispose();
    }
}
