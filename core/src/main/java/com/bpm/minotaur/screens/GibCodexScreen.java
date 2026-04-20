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
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import com.bpm.minotaur.managers.CookingManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Displays all discovered gib-to-effect mappings as a field journal.
 * Opened from the CookingScreen.
 */
public class GibCodexScreen extends BaseScreen {

    private final CookingScreen parentCookingScreen;
    private final CookingManager cookingManager;

    private Stage stage;
    private BitmapFont font;
    private Texture whitePixel;

    // Effects that are unambiguously positive — used for colour coding rows.
    private static final java.util.Set<StatusEffectType> BUFF_EFFECTS = new java.util.HashSet<>(java.util.Arrays.asList(
        StatusEffectType.FOCUSED, StatusEffectType.ADRENALINE_BOOST,
        StatusEffectType.HEALTHY, StatusEffectType.IMMUNE_BOOSTED,
        StatusEffectType.TEMP_STRENGTH, StatusEffectType.TEMP_SPEED,
        StatusEffectType.TEMP_HEALTH, StatusEffectType.PSYCHIC,
        StatusEffectType.FLOATING, StatusEffectType.FLYING,
        StatusEffectType.SUPER_SPEED, StatusEffectType.HARDENED,
        StatusEffectType.SUPER_INTELLIGENT, StatusEffectType.OMNISCIENT,
        StatusEffectType.RESIST_FIRE, StatusEffectType.RESIST_COLD,
        StatusEffectType.RESIST_LIGHTNING, StatusEffectType.RESIST_POISON,
        StatusEffectType.TELEPATHY, StatusEffectType.INVISIBLE,
        StatusEffectType.SEE_INVISIBLE, StatusEffectType.RECOVERING
    ));

    public GibCodexScreen(Tarmin2 game, CookingScreen parentCookingScreen, CookingManager cookingManager) {
        super(game);
        this.parentCookingScreen = parentCookingScreen;
        this.cookingManager = cookingManager;
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        Gdx.input.setInputProcessor(stage);

        font = new BitmapFont();
        font.getData().setScale(1.8f);

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.DARK_GRAY);
        pixmap.fill();
        whitePixel = new Texture(pixmap);
        pixmap.dispose();

        buildUI();
    }

    private void buildUI() {
        LabelStyle style = new LabelStyle(font, Color.WHITE);
        LabelStyle headerStyle = new LabelStyle(font, Color.ORANGE);

        TextButtonStyle btnStyle = new TextButtonStyle();
        btnStyle.font = font;
        btnStyle.up = new TextureRegionDrawable(whitePixel);
        btnStyle.down = new TextureRegionDrawable(whitePixel).tint(Color.LIGHT_GRAY);
        btnStyle.over = new TextureRegionDrawable(whitePixel).tint(Color.GRAY);
        btnStyle.fontColor = Color.WHITE;

        Table rootTable = new Table();
        rootTable.setFillParent(true);
        stage.addActor(rootTable);

        // Title
        Label title = new Label("GIB CODEX", headerStyle);
        title.setFontScale(2.5f);
        title.setAlignment(Align.center);
        rootTable.add(title).padTop(20).colspan(3).row();

        Label subtitle = new Label("Eat to discover. Knowledge is permanent.", style);
        subtitle.setAlignment(Align.center);
        subtitle.setColor(Color.LIGHT_GRAY);
        rootTable.add(subtitle).padBottom(20).colspan(3).row();

        // Stats bar
        Map<MonsterType, StatusEffectType> allEffects = cookingManager.getAllGibEffects();
        Map<MonsterType, Boolean> discovered = cookingManager.getDiscoveredGibs();
        long discoveredCount = discovered.values().stream().filter(v -> v).count();
        long totalCount = discovered.size();

        Label statsLabel = new Label("Discovered: " + discoveredCount + " / " + totalCount, style);
        statsLabel.setColor(Color.YELLOW);
        statsLabel.setAlignment(Align.center);
        rootTable.add(statsLabel).padBottom(10).colspan(3).row();

        // Column headers
        addColumnHeaders(rootTable, style);

        // Scrollable body — identified entries first, then unknowns
        Table bodyTable = new Table();
        bodyTable.top();

        List<MonsterType> identified = new ArrayList<>();
        List<MonsterType> unknown = new ArrayList<>();
        for (MonsterType mt : MonsterType.values()) {
            if (Boolean.TRUE.equals(discovered.get(mt))) {
                identified.add(mt);
            } else {
                unknown.add(mt);
            }
        }

        for (MonsterType mt : identified) {
            addCodexRow(bodyTable, style, mt, allEffects.get(mt), true);
        }
        for (MonsterType mt : unknown) {
            addCodexRow(bodyTable, style, mt, allEffects.get(mt), false);
        }

        ScrollPane scroll = new ScrollPane(bodyTable);
        scroll.setFadeScrollBars(false);
        rootTable.add(scroll).colspan(3).width(1600).height(700).pad(10).row();

        // Close button
        TextButton closeBtn = new TextButton("Back to Cooking", btnStyle);
        closeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(parentCookingScreen);
                dispose();
            }
        });
        rootTable.add(closeBtn).width(400).height(70).padBottom(30).colspan(3);
    }

    private void addColumnHeaders(Table root, LabelStyle style) {
        LabelStyle hs = new LabelStyle(font, Color.ORANGE);
        Label col1 = new Label("MONSTER", hs);
        Label col2 = new Label("GIB EFFECT", hs);
        Label col3 = new Label("STATUS", hs);
        root.add(col1).width(600).padLeft(20);
        root.add(col2).width(700);
        root.add(col3).width(300).row();

        // Divider
        Label divider = new Label("-----------------------------------------------------------------------"
                + "-------------------------------------", style);
        divider.setColor(Color.DARK_GRAY);
        root.add(divider).colspan(3).row();
    }

    private void addCodexRow(Table body, LabelStyle style, MonsterType mt, StatusEffectType effect, boolean isIdentified) {
        String monsterName = formatName(mt.name());
        String effectName;
        Color effectColor;

        if (isIdentified && effect != null) {
            effectName = formatName(effect.name());
            effectColor = BUFF_EFFECTS.contains(effect) ? Color.GREEN : Color.RED;
        } else {
            effectName = "???";
            effectColor = Color.GRAY;
        }

        Color rowColor = isIdentified ? Color.WHITE : new Color(0.5f, 0.5f, 0.5f, 1f);

        LabelStyle rowStyle = new LabelStyle(font, rowColor);
        LabelStyle effectStyle = new LabelStyle(font, effectColor);

        Label nameLabel = new Label(monsterName, rowStyle);
        Label effectLabel = new Label(effectName, effectStyle);
        Label statusLabel = new Label(isIdentified ? "KNOWN" : "Unknown", new LabelStyle(font, isIdentified ? Color.YELLOW : Color.DARK_GRAY));

        body.add(nameLabel).width(600).padLeft(20).padBottom(6).left();
        body.add(effectLabel).width(700).padBottom(6).left();
        body.add(statusLabel).width(300).padBottom(6).left().row();
    }

    private String formatName(String rawName) {
        if (rawName == null || rawName.isEmpty()) return rawName;
        String[] words = rawName.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.03f, 0.02f, 0.08f, 1f);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (font != null) font.dispose();
        if (whitePixel != null) whitePixel.dispose();
    }
}
