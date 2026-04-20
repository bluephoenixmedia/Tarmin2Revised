package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.screens.GameScreen;

public class DebugSpawnOverlay extends Table {

    private final GameScreen gameScreen;
    private final SelectBox<String> itemSelectBox;
    private final SelectBox<String> monsterSelectBox;
    private final TextButton spawnItemButton;
    private final TextButton spawnMonsterButton;

    public DebugSpawnOverlay(GameScreen gameScreen, Skin skin) {
        this.gameScreen = gameScreen;

        // Visual setup
        setBackground(createBackground());
        pad(20);

        // --- Styles ---
        // Ideally these should come from a loaded Skin, but for a debug tool,
        // programmatic styles are fine if no global skin exists.
        // Assuming 'skin' is passed or we create a basic one.
        // For robustness, I'll create a minimal style set if skin is null or simple.

        Label.LabelStyle labelStyle = new Label.LabelStyle(new BitmapFont(), Color.WHITE);

        // --- Header ---
        Label titleLabel = new Label("Debug Spawner", labelStyle);
        titleLabel.setFontScale(1.5f);
        add(titleLabel).colspan(2).padBottom(20).center();
        row();

        // --- Item Spawner Section ---
        add(new Label("Spawn Item:", labelStyle)).left();
        row();

        itemSelectBox = new SelectBox<>(createSelectBoxStyle());
        Array<String> itemNames = new Array<>();
        for (Item.ItemType type : Item.ItemType.values()) {
            itemNames.add(type.name());
        }
        itemNames.sort();
        itemSelectBox.setItems(itemNames);
        add(itemSelectBox).width(200).padBottom(10).left();

        spawnItemButton = new TextButton("Spawn", createTextButtonStyle());
        spawnItemButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String selected = itemSelectBox.getSelected();
                if (selected != null) {
                    try {
                        Item.ItemType type = Item.ItemType.valueOf(selected);
                        gameScreen.spawnDebugItem(type);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        add(spawnItemButton).padLeft(10).padBottom(10).left();
        row();

        // --- Monster Spawner Section ---
        add(new Label("Spawn Monster:", labelStyle)).left();
        row();

        monsterSelectBox = new SelectBox<>(createSelectBoxStyle());
        Array<String> monsterNames = new Array<>();
        for (Monster.MonsterType type : Monster.MonsterType.values()) {
            monsterNames.add(type.name());
        }
        monsterNames.sort();
        monsterSelectBox.setItems(monsterNames);
        add(monsterSelectBox).width(200).padBottom(10).left();

        spawnMonsterButton = new TextButton("Spawn", createTextButtonStyle());
        spawnMonsterButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String selected = monsterSelectBox.getSelected();
                if (selected != null) {
                    try {
                        Monster.MonsterType type = Monster.MonsterType.valueOf(selected);
                        gameScreen.spawnDebugMonster(type);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        add(spawnMonsterButton).padLeft(10).padBottom(10).left();
        row();

        // --- Instructions ---
        Label helpLabel = new Label("Spawns 1 tile in front of player.", labelStyle);
        helpLabel.setColor(Color.LIGHT_GRAY);
        helpLabel.setFontScale(0.8f);
        add(helpLabel).colspan(2).padTop(20).center();

        pack(); // Size to fit content
    }

    private TextureRegionDrawable createBackground() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(0, 0, 0, 0.8f);
        pixmap.fill();
        return new TextureRegionDrawable(new TextureRegion(new Texture(pixmap)));
    }

    // --- Helper Styles for independent rendering ---
    private SelectBox.SelectBoxStyle createSelectBoxStyle() {
        BitmapFont font = new BitmapFont();
        // Background
        Pixmap bgPix = new Pixmap(1, 1, Pixmap.Format.RGB888);
        bgPix.setColor(Color.DARK_GRAY);
        bgPix.fill();
        TextureRegionDrawable bg = new TextureRegionDrawable(new TextureRegion(new Texture(bgPix)));

        // Scroll Background
        Pixmap scrollPix = new Pixmap(1, 1, Pixmap.Format.RGB888);
        scrollPix.setColor(Color.BLACK);
        scrollPix.fill();
        TextureRegionDrawable scrollBg = new TextureRegionDrawable(new TextureRegion(new Texture(scrollPix)));

        // Selection
        Pixmap selPix = new Pixmap(1, 1, Pixmap.Format.RGB888);
        selPix.setColor(Color.BLUE);
        selPix.fill();
        TextureRegionDrawable selection = new TextureRegionDrawable(new TextureRegion(new Texture(selPix)));

        List.ListStyle listStyle = new List.ListStyle(font, Color.WHITE, Color.GRAY, selection);
        listStyle.background = scrollBg;

        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        scrollStyle.background = scrollBg;

        return new SelectBox.SelectBoxStyle(font, Color.WHITE, bg, scrollStyle, listStyle);
    }

    private TextButton.TextButtonStyle createTextButtonStyle() {
        BitmapFont font = new BitmapFont();

        Pixmap upPix = new Pixmap(1, 1, Pixmap.Format.RGB888);
        upPix.setColor(Color.DARK_GRAY);
        upPix.fill();
        TextureRegionDrawable up = new TextureRegionDrawable(new TextureRegion(new Texture(upPix)));

        Pixmap downPix = new Pixmap(1, 1, Pixmap.Format.RGB888);
        downPix.setColor(Color.GRAY);
        downPix.fill();
        TextureRegionDrawable down = new TextureRegionDrawable(new TextureRegion(new Texture(downPix)));

        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = font;
        style.up = up;
        style.down = down;
        style.fontColor = Color.WHITE;
        return style;
    }
}
