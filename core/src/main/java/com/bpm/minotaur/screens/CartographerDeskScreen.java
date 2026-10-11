package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.Megabeast;
import com.bpm.minotaur.gamedata.history.beast.BeastTracks;
import com.bpm.minotaur.gamedata.history.war.Front;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.gamedata.shelter.ShelterNetwork;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.DivinityManager;
import com.bpm.minotaur.managers.WorldManager;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.screens.map.ExpeditionMapScreen;
import com.bpm.minotaur.ui.UiContexts;
import com.bpm.minotaur.ui.UiGlyphs;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiNames;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Interface for the Shelter Cartographer's Desk:
 * Displays surveyed world chunks around lit shelters, regional reconnaissance,
 * and allows upgrading the survey apparatus to reveal deeper cartographic layers.
 */
public class CartographerDeskScreen extends BaseScreen {

    private static final String CONTEXT = "CARTOGRAPHER_DESK";

    private final GameScreen parentScreen;
    private final Player player;
    private final Maze maze;
    private final WorldManager worldManager;
    private final HudSkin hudSkin;
    private Stage stage;

    public CartographerDeskScreen(Tarmin2 game, GameScreen parentScreen, Player player, Maze maze, WorldManager worldManager) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.maze = maze;
        this.worldManager = worldManager;
        this.hudSkin = new HudSkin();
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    returnToShelter();
                    return true;
                }
                if (keycode == Input.Keys.M) {
                    openFullExpeditionMap();
                    return true;
                }
                return false;
            }
        });
        Gdx.input.setInputProcessor(multiplexer);
        UiContexts.push(CONTEXT, UiContexts.Kind.PANEL);
        build();
    }

    private void returnToShelter() {
        game.setScreen(parentScreen);
        dispose();
    }

    private void openFullExpeditionMap() {
        game.setScreen(new ExpeditionMapScreen(game, player, maze, parentScreen));
        dispose();
    }

    private Label.LabelStyle style(com.badlogic.gdx.graphics.g2d.BitmapFont font, com.badlogic.gdx.graphics.Color color) {
        return new Label.LabelStyle(font, color);
    }

    private TextButton.TextButtonStyle buttonStyle() {
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
        s.font = hudSkin.getFontMain();
        s.up = hudSkin.getPanelBg();
        s.down = hudSkin.getPrimaryButtonDown();
        s.over = hudSkin.getSlotRecessed();
        s.fontColor = HudSkin.COL_GOLD_BRIGHT;
        return s;
    }

    private void build() {
        stage.clear();

        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(30, 80, 30, 80);

        // Header Banner
        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(16, 24, 16, 24);
        header.add(UiLabels.of("THE CARTOGRAPHER'S DESK", style(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT))).center().row();
        header.add(UiLabels.of("Drafting surveys of the seeded world, charting roads, battle fronts, and regional perils",
                style(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED))).center().padTop(4).row();
        root.add(header).fillX().padBottom(16).row();

        // Main 2-Column Layout
        Table body = new Table();
        root.add(body).grow().row();

        // Left Column: Surveyed Regional Intel Log
        Table leftColumn = new Table();
        leftColumn.setBackground(hudSkin.getDoubleBorderPanel());
        leftColumn.pad(18);
        buildSurveyIntelPanel(leftColumn);
        body.add(leftColumn).grow().padRight(16);

        // Right Column: Apparatus Upgrades & Actions
        Table rightColumn = new Table();
        rightColumn.setBackground(hudSkin.getDoubleBorderPanel());
        rightColumn.pad(18);
        buildUpgradesPanel(rightColumn);
        body.add(rightColumn).width(580f).fillY();

        stage.addActor(root);
    }

    private void buildSurveyIntelPanel(Table panel) {
        panel.add(UiLabels.of("REGIONAL RECONNAISSANCE REPORT", style(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT)))
                .left().padBottom(4).row();

        ShelterAltar altar = ShelterAltar.getInstance();
        int tier = altar.getCartographerDeskTier();
        int radius = altar.getCartographerSurveyRadius();

        panel.add(UiLabels.of("Active survey radiance: " + radius + " chunks from each lit sanctuary",
                style(hudSkin.getFontSmall(), HudSkin.COL_TEXT_MUTED))).left().padBottom(12).row();

        Table listTable = new Table();
        listTable.top().left();

        BiomeManager biomes = worldManager != null ? worldManager.getBiomeManager() : null;
        ShelterNetwork network = ShelterNetwork.getInstance();
        Set<GridPoint2> anchors = new HashSet<>();
        anchors.add(new GridPoint2(0, 0));
        if (network != null) {
            anchors.addAll(network.getClaimed());
        }

        Set<GridPoint2> surveyed = new HashSet<>();
        for (GridPoint2 anchor : anchors) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    surveyed.add(new GridPoint2(anchor.x + dx, anchor.y + dy));
                }
            }
        }

        List<GridPoint2> sortedChunks = new ArrayList<>(surveyed);
        sortedChunks.sort((a, b) -> {
            int distA = Math.max(Math.abs(a.x), Math.abs(b.y));
            int distB = Math.max(Math.abs(b.x), Math.abs(b.y));
            if (distA != distB) return Integer.compare(distA, distB);
            if (a.y != b.y) return Integer.compare(a.y, b.y);
            return Integer.compare(a.x, b.x);
        });

        for (GridPoint2 c : sortedChunks) {
            Table row = new Table();
            row.setBackground(hudSkin.getPanelBg());
            row.pad(8, 12, 8, 12);

            Biome b = biomes == null ? null : biomes.getBiome(c);
            String biomeName = b == null ? "Wilds" : UiNames.of(b);
            String shelterLabel = (c.x == 0 && c.y == 0) ? " [HOME HEARTH]" : (network != null && network.isClaimed(c)) ? " [LIT SHELTER]" : "";

            row.add(UiLabels.of("Chunk (" + c.x + ", " + c.y + "): " + biomeName + shelterLabel,
                    style(hudSkin.getFontMain(), shelterLabel.isEmpty() ? HudSkin.COL_TEXT_ON_DARK : HudSkin.COL_GOLD_BRIGHT)))
                    .left().growX().row();

            // Tier 2: Warfronts
            if (tier >= 2 && worldManager != null) {
                boolean hasConflict = false;
                for (Front front : worldManager.currentFronts()) {
                    if (front != null && front.covers(c)) {
                        hasConflict = true;
                        row.add(UiLabels.of("  War: Contested frontline between hostile houses",
                                style(hudSkin.getFontSmall(), HudSkin.COL_HP_CRITICAL))).left().padTop(2).row();
                        break;
                    }
                }
                if (!hasConflict) {
                    row.add(UiLabels.of("  War: Neutral territory / no active clashes",
                            style(hudSkin.getFontSmall(), HudSkin.COL_TEXT_MUTED))).left().padTop(2).row();
                }
            }

            // Tier 3: Monsters & Threats
            if (tier >= 3 && worldManager != null) {
                int danger = worldManager.calculateEffectiveDifficulty(c, 1);
                String beastInfo = "";
                HistoryWorld hw = worldManager.getHistory() != null ? worldManager.getHistory().world() : null;
                if (hw != null) {
                    for (Megabeast beast : hw.megabeasts()) {
                        if (beast != null && beast.isAlive()) {
                            GridPoint2 lair = BeastTracks.lairChunk(hw.seed, beast);
                            if (c.equals(lair)) {
                                beastInfo = " - DEN OF " + UiGlyphs.sanitize(beast.name).toUpperCase();
                                break;
                            }
                        }
                    }
                }
                row.add(UiLabels.of("  Recon: Threat Level " + danger + beastInfo,
                        style(hudSkin.getFontSmall(), beastInfo.isEmpty() ? HudSkin.COL_GOLD_ANTIQUE : HudSkin.COL_HP_CRITICAL)))
                        .left().padTop(2).row();
            }

            listTable.add(row).growX().padBottom(6).row();
        }

        ScrollPane scroll = new ScrollPane(listTable, UiStyles.scrollPane(hudSkin));
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        panel.add(scroll).grow().row();
    }

    private void buildUpgradesPanel(Table panel) {
        panel.top().left();
        panel.add(UiLabels.of("SURVEY APPARATUS", style(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT))).left().row();

        ShelterAltar altar = ShelterAltar.getInstance();
        int tier = altar.getCartographerDeskTier();

        String tierTitle;
        switch (tier) {
            case 1: tierTitle = "TIER 1 - LOCAL CHART"; break;
            case 2: tierTitle = "TIER 2 - TACTICAL TRIANGULATION"; break;
            case 3: default: tierTitle = "TIER 3 - MASTER CARTOGRAPHY"; break;
        }

        panel.add(UiLabels.of(tierTitle, style(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT)))
                .left().padTop(6).padBottom(4).row();
        panel.add(UiLabels.of("Current Radius: " + tier + " chunk" + (tier > 1 ? "s" : "") + " around each lit sanctuary",
                style(hudSkin.getFontSmall(), HudSkin.COL_TEXT_MUTED))).left().padBottom(14).row();

        // Features list
        Table features = new Table();
        features.setBackground(hudSkin.getPanelBg());
        features.pad(12);

        features.add(UiLabels.of("[X] Tier 1: Biome classification & road networks",
                style(hudSkin.getFontSmall(), HudSkin.COL_TEXT_ON_DARK))).left().row();
        features.add(UiLabels.of(tier >= 2 ? "[X] Tier 2: Living War frontline borders & clashes" : "[ ] Tier 2: Living War frontline borders & clashes",
                style(hudSkin.getFontSmall(), tier >= 2 ? HudSkin.COL_TEXT_ON_DARK : HudSkin.COL_TEXT_MUTED))).left().padTop(6).row();
        features.add(UiLabels.of(tier >= 3 ? "[X] Tier 3: Megabeast dens, monster danger & cache sites" : "[ ] Tier 3: Megabeast dens, monster danger & cache sites",
                style(hudSkin.getFontSmall(), tier >= 3 ? HudSkin.COL_TEXT_ON_DARK : HudSkin.COL_TEXT_MUTED))).left().padTop(6).row();

        panel.add(features).growX().padBottom(20).row();

        // Upgrade Actions
        if (tier < ShelterAltar.MAX_CARTOGRAPHER_TIER) {
            int crestCost = altar.getCartographerUpgradeCrestCost();
            int divCost = altar.getCartographerUpgradeDivinityCost();
            int playerCrests = altar.getCrestsOfValor();
            int playerDivs = DivinityManager.getInstance().getDivinities();

            Table costTable = new Table();
            costTable.setBackground(hudSkin.getPanelBg());
            costTable.pad(10);
            costTable.add(UiLabels.of("Upgrade Cost to Tier " + (tier + 1) + ":", style(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED))).left().row();
            costTable.add(UiLabels.of("- Crests of Valor: " + crestCost + "  (Owned: " + playerCrests + ")",
                    style(hudSkin.getFontSmall(), playerCrests >= crestCost ? HudSkin.COL_TEXT_ON_DARK : HudSkin.COL_HP_CRITICAL))).left().padTop(4).row();
            costTable.add(UiLabels.of("- Divinities: " + divCost + "  (Owned: " + playerDivs + ")",
                    style(hudSkin.getFontSmall(), playerDivs >= divCost ? HudSkin.COL_TEXT_ON_DARK : HudSkin.COL_HP_CRITICAL))).left().padTop(2).row();
            panel.add(costTable).growX().padBottom(14).row();

            TextButton upgradeBtn = new TextButton("UPGRADE APPARATUS", buttonStyle());
            upgradeBtn.setDisabled(!altar.canUpgradeCartographerDesk());
            upgradeBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (altar.upgradeCartographerDesk()) {
                        if (parentScreen.getSoundManager() != null) {
                            parentScreen.getSoundManager().playDoorOpenSound();
                        }
                        build();
                    }
                }
            });
            panel.add(upgradeBtn).growX().height(48f).padBottom(14).row();
        } else {
            Table maxTable = new Table();
            maxTable.setBackground(hudSkin.getPanelBg());
            maxTable.pad(12);
            maxTable.add(UiLabels.of("SURVEY APPARATUS FULLY MAXIMIZED", style(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT))).center().row();
            maxTable.add(UiLabels.of("All regional cartography layers and deep reconnaissance active.",
                    style(hudSkin.getFontSmall(), HudSkin.COL_TEXT_ON_DARK))).center().padTop(4).row();
            panel.add(maxTable).growX().padBottom(16).row();
        }

        // Full map shortcut button
        TextButton openMapBtn = new TextButton("OPEN EXPEDITION MAP [M]", buttonStyle());
        openMapBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                openFullExpeditionMap();
            }
        });
        panel.add(openMapBtn).growX().height(44f).padBottom(10).row();

        // Return button
        TextButton backBtn = new TextButton("BACK TO SHELTER [ESC]", buttonStyle());
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                returnToShelter();
            }
        });
        panel.add(backBtn).growX().height(44f).padBottom(10).row();
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0, 0, 0, 1);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        UiContexts.pop(CONTEXT);
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (hudSkin != null) hudSkin.dispose();
    }
}
