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
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.GameEventManager;

public class Hud implements Disposable {

    public Stage stage;
    private final Viewport viewport;
    private final Player player;
    private final Maze maze;
    private final CombatManager combatManager;
    private final GameEventManager eventManager;
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
    private final Label dungeonLevelLabel;
    private final Label monsterStrengthLabel;
    private final Label combatStatusLabel;
    private final Label messageLabel;
    private final Label treasureValueLabel;

    private final Actor[] backpackSlots = new Actor[6];
    private Actor leftHandSlot;
    private Actor rightHandSlot;


    public Hud(SpriteBatch sb, Player player, Maze maze, CombatManager combatManager, GameEventManager eventManager) {
        this.player = player;
        this.maze = maze;
        this.combatManager = combatManager;
        this.eventManager = eventManager;
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

        Table mainContentTable = new Table();
        Table leftTable = new Table();
        float slotSize = 60f;
        for(int i = 0; i < 6; i++) { backpackSlots[i] = new Actor(); }
        leftTable.add(backpackSlots[0]).width(slotSize).height(slotSize);
        leftTable.add(backpackSlots[1]).width(slotSize).height(slotSize);
        leftTable.add(backpackSlots[2]).width(slotSize).height(slotSize);
        leftTable.row();
        leftTable.add(backpackSlots[3]).width(slotSize).height(slotSize);
        leftTable.add(backpackSlots[4]).width(slotSize).height(slotSize);
        leftTable.add(backpackSlots[5]).width(slotSize).height(slotSize);


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
        treasureValueLabel = new Label("", labelStyle);
        Table treasureTable = new Table();
        treasureTable.add(new Label("TREASURE", labelStyle)).padRight(10);
        treasureTable.add(treasureValueLabel);


        rightTable.add(warTable).left().padBottom(20);
        rightTable.add(spiritualTable).right().expandX().padBottom(20);
        rightTable.row();
        rightTable.add(foodTable).left().padRight(100f);
        rightTable.add(arrowsTable).right().expandX();
        rightTable.row();
        rightTable.add(treasureTable).colspan(2).center().padTop(10);

        monsterStrengthLabel = new Label("", labelStyle);
        combatStatusLabel = new Label("", labelStyle);
        rightTable.row();
        rightTable.add(new Label("MONSTER", labelStyle)).left().padTop(20);
        rightTable.add(monsterStrengthLabel).right().expandX().padTop(20);
        rightTable.row();
        rightTable.add(combatStatusLabel).colspan(2).center().padTop(10);

        mainContentTable.add(leftTable).width(500).padLeft(50);
        mainContentTable.add(rightTable).expandX().right().padRight(50);

        Table handsAndCompassTable = new Table();
        leftHandSlot = new Actor();
        rightHandSlot = new Actor();
        directionLabel = new Label("", directionLabelStyle);
        handsAndCompassTable.add(leftHandSlot).width(slotSize).height(slotSize).padRight(20);
        handsAndCompassTable.add(directionLabel).width(slotSize).height(slotSize);
        handsAndCompassTable.add(rightHandSlot).width(slotSize).height(slotSize).padLeft(20);

        dungeonLevelLabel = new Label("", labelStyle);
        Table levelTable = new Table();
        levelTable.add(dungeonLevelLabel);

        messageLabel = new Label("", labelStyle);
        Table messageTable = new Table();
        messageTable.add(messageLabel).center();

        Table mainContainer = new Table();
        mainContainer.bottom();
        mainContainer.setFillParent(true);
        mainContainer.add(messageTable).colspan(3).expandX().padBottom(10);
        mainContainer.row();
        mainContainer.add(mainContentTable).colspan(3).expandX().fillX();
        mainContainer.row();
        mainContainer.add(new Actor()).expandX();
        mainContainer.add(handsAndCompassTable).padBottom(5);
        mainContainer.add(new Actor()).expandX();
        mainContainer.row();
        mainContainer.add(levelTable).colspan(3).expandX().padBottom(10);


        stage.addActor(mainContainer);
    }

    public void update(float dt) {
        warStrengthValueLabel.setText(String.format("%d", player.getWarStrength()));
        spiritualStrengthValueLabel.setText(String.format("%d", player.getSpiritualStrength()));
        foodValueLabel.setText(String.format("%d", player.getFood()));
        arrowsValueLabel.setText(String.format("%d", player.getArrows()));
        directionLabel.setText(player.getFacing().name().substring(0,1));
        dungeonLevelLabel.setText("DUNGEON LEVEL " + maze.getLevel());
        treasureValueLabel.setText(String.format("%d", player.getTreasureScore()));

        if(combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE && combatManager.getMonster() != null) {
            Monster monster = combatManager.getMonster();
            monsterStrengthLabel.setText("WS:" + monster.getWarStrength() + " SS:" + monster.getSpiritualStrength());
            monsterStrengthLabel.setVisible(true);
            combatStatusLabel.setVisible(true);

            switch(combatManager.getCurrentState()){
                case PLAYER_TURN:
                    combatStatusLabel.setText("YOUR TURN. PRESS 'A' TO ATTACK.");
                    break;
                case MONSTER_TURN:
                    combatStatusLabel.setText(monster.getType() + " ATTACKS!");
                    break;
                default:
                    combatStatusLabel.setText("");
                    break;
            }
        } else {
            monsterStrengthLabel.setVisible(false);
            combatStatusLabel.setVisible(false);
        }

        if (!eventManager.getEvents().isEmpty()) {
            messageLabel.setText(eventManager.getEvents().get(0).message);
        } else {
            messageLabel.setText("");
        }
    }

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
        for (int i = 0; i < backpack.length; i++) {
            Item item = backpack[i];
            Actor slot = backpackSlots[i];
            if (item != null) {
                Vector2 pos = slot.localToStageCoordinates(new Vector2(0, 0));
                String[] spriteData = ItemSpriteData.getSpriteByType(item.getType().name());
                if (spriteData != null) {
                    drawItemSprite(shapeRenderer, spriteData, pos.x, pos.y, slot.getWidth(), slot.getHeight(), item.getColor());
                }
            }
        }

        Item leftHand = player.getInventory().getLeftHand();
        if (leftHand != null) {
            Vector2 pos = leftHandSlot.localToStageCoordinates(new Vector2(0, 0));
            String[] spriteData = ItemSpriteData.getSpriteByType(leftHand.getType().name());
            if (spriteData != null) {
                drawItemSprite(shapeRenderer, spriteData, pos.x, pos.y, leftHandSlot.getWidth(), leftHandSlot.getHeight(), leftHand.getColor());
            }
        }

        Item rightHand = player.getInventory().getRightHand();
        if (rightHand != null) {
            Vector2 pos = rightHandSlot.localToStageCoordinates(new Vector2(0, 0));
            String[] spriteData = ItemSpriteData.getSpriteByType(rightHand.getType().name());
            if (spriteData != null) {
                drawItemSprite(shapeRenderer, spriteData, pos.x, pos.y, rightHandSlot.getWidth(), rightHandSlot.getHeight(), rightHand.getColor());
            }
        }

        shapeRenderer.end();
    }

    private void drawItemSprite(ShapeRenderer shapeRenderer, String[] spriteData, float x, float y, float width, float height, Color color) {
        shapeRenderer.setColor(color);
        float pixelWidth = width / 24.0f;
        float pixelHeight = height / 24.0f;

        for (int row = 0; row < 24; row++) {
            for (int col = 0; col < 24; col++) {
                if (spriteData[row].charAt(col) == '#') {
                    shapeRenderer.rect(x + col * pixelWidth, y + (23 - row) * pixelHeight, pixelWidth, pixelHeight);
                }
            }
        }
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
