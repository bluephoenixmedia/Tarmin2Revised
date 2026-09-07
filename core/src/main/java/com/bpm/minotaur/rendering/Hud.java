package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.*;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.managers.*;

import java.util.List;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.screens.GameScreen;

public class Hud implements Disposable {

    private final Tarmin2 game;
    private final DebugManager debugManager;
    public Stage stage;
    private final Viewport viewport;
    private final Player player;
    private final Maze maze;
    private final CombatManager combatManager;
    private final GameEventManager eventManager;
    private final BitmapFont font;
    private final BitmapFont logFont; // Separate font for game log
    private BitmapFont debugFont; // Default font for debug overlay
    private final BitmapFont directionFont;
    private final SpriteBatch spriteBatch;
    private final ShapeRenderer shapeRenderer;
    private final Texture bottomBarBg;
    private final Texture whiteTexture; // Generic white texture for tinting
    private final TextureRegionDrawable bottomBarDrawable;

    // --- Modern Theme & Component Actors ---
    private final HudSkin hudSkin;
    private final ModernStatBar hpBar;
    private final ModernStatBar mpBar;
    private final ModernStatBar expBar;
    private final ModernStatBar foodBar;
    private final ModernStatBar waterBar;
    private final ModernStatBar tempBar;
    private final ModernStatBar monsterHpBar;

    private final CompassMedallion compassMedallion;
    private final HudTooltip hudTooltip;
    private final WorldInteractionCard worldInteractionCard;
    private GameScreen gameScreen;

    public void setGameScreen(GameScreen gameScreen) {
        this.gameScreen = gameScreen;
    }

    private final Label warStrengthValueLabel;
    private final Label spiritualStrengthValueLabel;
    private final Label arrowsValueLabel;
    private final Label directionLabel;
    private final Label dungeonLevelLabel;
    private final Label monsterStrengthLabel;
    private final Label combatStatusLabel;
    private final Label logLabel; // Replaces messageLabel for backward compatibility
    private final Label treasureValueLabel;
    private final Label levelLabel, xpLabel;
    private final Label levelBadgeLabel;
    private final Label divinitiesLabel;
    private final Label doomLabel;
    private final Label equippedWeaponLabel;
    private Label heldItemLabel;
    private Label rightHandStatsLabel;

    private final Table[] backpackSlots = new Table[6];
    private final Table leftHandSlot;
    private final Table rightHandSlot;

    // --- Action Chronicle Labels (5 lines) ---
    private final Label[] chronicleLabels = new Label[5];

    // Combat Menu
    public CombatMenu combatMenu;

    private DiscoveryManager discoveryManager;
    private Item toastItem = null;
    private float toastTimer = 0f;

    public void setDiscoveryManager(DiscoveryManager discoveryManager) {
        this.discoveryManager = discoveryManager;
    }

    public void showPickupToast(Item item) {
        if (item == null) return;
        this.toastItem = item;
        this.toastTimer = 2.5f;
    }

    // Portrait
    private TextureAtlas portraitAtlas;
    private Image portraitImage;
    private String currentPortraitName = "";

    // --- Layout Tables ---
    private final Table mainContainer;
    private final Table bottomBarTable;

    private final Table vitalsZone;
    private final Table beltZone;
    private final Table delveZone;
    private final Table delveInfoTable;
    private final Table combatMonsterTable;
    private final Table chronicleZone;

    private final Table statsTable;
    private final Table survivalTable; // New table for suvival stats
    private final Table logTable;
    private final Table inventoryTable;

    private final WorldManager worldManager;
    private final EncounterWindow encounterWindow;

    private String equippedWeapon = "NOTHING";
    private String damage = "0";
    private String range = "0";
    private String isRanged = "N/A";
    private String weaponColor = "NONE";
    private String weaponType = "NULL";

    private final GameMode gameMode;

    private static final Color GLOW_COLOR_UI = new Color(1.0f, 0.9f, 0.2f, 0.7f);
    private static final Color BG_COLOR_UI = new Color(0f, 0f, 0f, 0.8f);

    // --- Attack Indicators ---
    private static class AttackIndicator {
        Direction direction;
        float duration;

        public AttackIndicator(Direction direction) {
            this.direction = direction;
            this.duration = 1.0f; // 1 second fade
        }
    }

    private java.util.List<AttackIndicator> attackIndicators = new java.util.ArrayList<>();

    public void showAttackIndicator(Direction dir) {
        if (dir == null)
            return;
        attackIndicators.add(new AttackIndicator(dir));
    }

    private final GlyphLayout glyphLayout = new GlyphLayout();

    public Hud(SpriteBatch sb, Player player, Maze maze, CombatManager combatManager, GameEventManager eventManager,
            WorldManager worldManager, Tarmin2 game, DebugManager debugManager, GameMode gameMode) {
        this.game = game;
        this.debugManager = debugManager;
        this.gameMode = gameMode;
        this.player = player;
        this.maze = maze;
        this.combatManager = combatManager;
        this.eventManager = eventManager;
        this.spriteBatch = sb;
        this.worldManager = worldManager;
        this.shapeRenderer = new ShapeRenderer();

        // --- Viewport and Stage Setup ---
        viewport = new FitViewport(1920, 1080, new OrthographicCamera());
        stage = new Stage(viewport, sb);

        // --- Initialize HudSkin Theme & Assets ---
        this.hudSkin = new HudSkin();
        this.font = hudSkin.getFontMain();
        this.directionFont = hudSkin.getFontCompass();
        this.logFont = hudSkin.getFontLog();

        // White Texture for legacy tinting
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        whiteTexture = new Texture(pixmap);
        pixmap.dispose();

        // Background texture
        bottomBarBg = hudSkin.getWhitePixel();
        bottomBarDrawable = new TextureRegionDrawable(new TextureRegion(bottomBarBg));

        // Floating Tooltip Card
        hudTooltip = new HudTooltip(hudSkin);
        worldInteractionCard = new WorldInteractionCard(hudSkin);

        // --- Label Styles ---
        Label.LabelStyle labelStyle = new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE);
        Label.LabelStyle headerStyle = new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT);
        Label.LabelStyle smallStyle = new Label.LabelStyle(hudSkin.getFontSmall(), Color.WHITE);
        Label.LabelStyle smallGoldStyle = new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_BRIGHT);
        Label.LabelStyle directionLabelStyle = new Label.LabelStyle(directionFont, HudSkin.COL_GOLD_BRIGHT);
        Label.LabelStyle logLabelStyle = new Label.LabelStyle(hudSkin.getFontLog(), Color.WHITE);
        Label.LabelStyle weaponHeaderStyle = new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE);

        // --- Legacy Tables for compatibility ---
        bottomBarTable = new Table();
        statsTable = new Table();
        survivalTable = new Table();
        logTable = new Table();
        inventoryTable = new Table();

        // --- 4 Functional Zone Panels ---
        vitalsZone = new Table();
        beltZone = new Table();
        delveZone = new Table();
        delveInfoTable = new Table();
        combatMonsterTable = new Table();
        chronicleZone = new Table();

        vitalsZone.setBackground(hudSkin.getPanelBg());
        vitalsZone.pad(8f);

        beltZone.setBackground(hudSkin.getPanelBg());
        beltZone.pad(6f);

        delveZone.setBackground(hudSkin.getPanelBg());
        delveZone.pad(8f);

        chronicleZone.setBackground(hudSkin.getPanelBg());
        chronicleZone.pad(8f);

        // ══════════════════════════════════════════════════════════════════
        // ZONE 1: Character & Vitals (~450px)
        // ══════════════════════════════════════════════════════════════════
        warStrengthValueLabel = new Label("", labelStyle);
        spiritualStrengthValueLabel = new Label("", labelStyle);
        levelLabel = new Label("", labelStyle);
        xpLabel = new Label("", labelStyle);
        divinitiesLabel = new Label("DIV: 0", smallGoldStyle);
        doomLabel = new Label("DOOM: 0 [0%]", smallStyle);
        levelBadgeLabel = new Label("LVL 1", smallGoldStyle);
        arrowsValueLabel = new Label("", smallStyle);
        treasureValueLabel = new Label("", smallStyle);
        directionLabel = new Label("", directionLabelStyle);

        hpBar = new ModernStatBar("HP", HudSkin.COL_HP_RED, hudSkin);
        mpBar = new ModernStatBar("MP", HudSkin.COL_MP_BLUE, hudSkin);
        expBar = new ModernStatBar("EXP", HudSkin.COL_EXP_AMBER, hudSkin);
        compassMedallion = new CompassMedallion(hudSkin, player);

        // Portrait
        try {
            if (game.getAssetManager().isLoaded("packed/portrait.atlas")) {
                this.portraitAtlas = game.getAssetManager().get("packed/portrait.atlas", TextureAtlas.class);
            }
        } catch (Exception e) {
            Gdx.app.error("Hud", "Failed to load portrait atlas", e);
        }

        Stack portraitStack = new Stack();
        Image cameoFrame = new Image(hudSkin.getPortraitCameo());
        portraitStack.add(cameoFrame);

        if (portraitAtlas != null) {
            TextureRegion region = portraitAtlas.findRegion("portrait", 100);
            if (region != null) {
                portraitImage = new Image(region);
                Table portWrap = new Table();
                portWrap.add(portraitImage).size(86, 86);
                portraitStack.add(portWrap);
            }
        }

        Table portraitCol = new Table();
        portraitCol.add(portraitStack).size(96, 96).row();
        Table levelBadgeTable = new Table();
        levelBadgeTable.setBackground(hudSkin.getGaugeTrack());
        levelBadgeTable.add(levelBadgeLabel).pad(2, 8, 2, 8);
        portraitCol.add(levelBadgeTable).padTop(4).row();

        Table barsCol = new Table();
        barsCol.add(hpBar).width(235).height(22).padBottom(4).row();
        barsCol.add(mpBar).width(235).height(22).padBottom(4).row();
        barsCol.add(expBar).width(235).height(14).padBottom(6).row();

        Table vitalsSubRow = new Table();
        vitalsSubRow.add(compassMedallion).size(42, 42).padRight(12);
        Table divDoomCol = new Table();
        divDoomCol.add(divinitiesLabel).left().row();
        divDoomCol.add(doomLabel).left().padTop(2).row();
        vitalsSubRow.add(divDoomCol).left();

        barsCol.add(vitalsSubRow).left().row();

        vitalsZone.add(portraitCol).padRight(12);
        vitalsZone.add(barsCol).expand().fill();

        // ══════════════════════════════════════════════════════════════════
        // ZONE 2: Equipment Hands & 2x3 Belt (~420px)
        // ══════════════════════════════════════════════════════════════════
        equippedWeaponLabel = new Label("Hands Empty", weaponHeaderStyle);
        beltZone.add(equippedWeaponLabel).colspan(3).center().padBottom(4).row();

        float slotSize = 50f;
        Table backpackTable = new Table();

        for (int i = 0; i < 6; i++) {
            final int slotIdx = i;
            backpackSlots[i] = new Table();
            backpackSlots[i].setBackground(hudSkin.getSlotRecessed());
            backpackSlots[i].top().left();

            Label badge = new Label(String.valueOf(i + 1), new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            backpackSlots[i].add(badge).padLeft(3).padTop(1).row();

            backpackSlots[i].addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (combatManager != null && (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_MENU
                            || combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN)) {
                        combatManager.playerUseItem(slotIdx, discoveryManager);
                    } else {
                        player.useQuickSlot(slotIdx, eventManager, discoveryManager, maze);
                    }
                }
            });

            backpackSlots[i].addListener(new InputListener() {
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                    Item item = player.getInventory().getQuickSlots()[slotIdx];
                    Vector2 pos = backpackSlots[slotIdx].localToStageCoordinates(new Vector2(0, 0));
                    hudTooltip.show(item, pos.x + slotSize / 2f, pos.y, "Hotkey " + (slotIdx + 1));
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                    hudTooltip.hide();
                }
            });
        }

        backpackTable.add(backpackSlots[0]).size(slotSize).pad(3);
        backpackTable.add(backpackSlots[1]).size(slotSize).pad(3);
        backpackTable.add(backpackSlots[2]).size(slotSize).pad(3);
        backpackTable.row();
        backpackTable.add(backpackSlots[3]).size(slotSize).pad(3);
        backpackTable.add(backpackSlots[4]).size(slotSize).pad(3);
        backpackTable.add(backpackSlots[5]).size(slotSize).pad(3);

        leftHandSlot = new Table();
        leftHandSlot.setBackground(hudSkin.getSlotHand());
        leftHandSlot.top().left();
        Label lBadge = new Label("L", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_BRIGHT));
        leftHandSlot.add(lBadge).padLeft(3).padTop(1).row();

        leftHandSlot.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                player.getInventory().swapHands();
                if (eventManager != null) eventManager.addEvent(new GameEvent("Swapped hands.", 1.5f));
            }
        });
        leftHandSlot.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                Item item = player.getInventory().getLeftHand();
                Vector2 pos = leftHandSlot.localToStageCoordinates(new Vector2(0, 0));
                hudTooltip.show(item, pos.x + 28f, pos.y, "Click to Swap / [S]");
            }
            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                hudTooltip.hide();
            }
        });

        rightHandSlot = new Table();
        rightHandSlot.setBackground(hudSkin.getSlotHand());
        rightHandSlot.top().left();
        Label rBadge = new Label("R", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_BRIGHT));
        rightHandSlot.add(rBadge).padLeft(3).padTop(1).row();

        rightHandSlot.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                player.getInventory().swapHands();
                if (eventManager != null) eventManager.addEvent(new GameEvent("Swapped hands.", 1.5f));
            }
        });
        rightHandSlot.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                Item item = player.getInventory().getRightHand();
                Vector2 pos = rightHandSlot.localToStageCoordinates(new Vector2(0, 0));
                hudTooltip.show(item, pos.x + 28f, pos.y, "Click to Swap / [S]");
            }
            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                hudTooltip.hide();
            }
        });

        beltZone.add(leftHandSlot).size(56).padRight(8);
        beltZone.add(backpackTable).padRight(8);
        beltZone.add(rightHandSlot).size(56);

        // ══════════════════════════════════════════════════════════════════
        // ZONE 3: Condition, Delve & Dynamic Combat Card (~330px)
        // ══════════════════════════════════════════════════════════════════
        dungeonLevelLabel = new Label("DUNGEON LVL 1", headerStyle);
        foodBar = new ModernStatBar("FOOD", HudSkin.COL_FOOD_GREEN, hudSkin);
        waterBar = new ModernStatBar("H2O", HudSkin.COL_WATER_CYAN, hudSkin);
        tempBar = new ModernStatBar("TEMP", HudSkin.COL_TEMP_ORANGE, hudSkin);
        tempBar.setTemperatureMode(true);

        monsterStrengthLabel = new Label("", headerStyle);
        combatStatusLabel = new Label("", labelStyle);
        monsterHpBar = new ModernStatBar("HP", HudSkin.COL_HP_RED, hudSkin);

        // Sub-table 1: Delve exploration info
        delveInfoTable.top().left();
        delveInfoTable.add(dungeonLevelLabel).left().padBottom(4).row();
        delveInfoTable.add(foodBar).width(210).height(20).padBottom(4).row();
        delveInfoTable.add(waterBar).width(210).height(20).padBottom(4).row();
        delveInfoTable.add(tempBar).width(210).height(20).row();

        // Sub-table 2: Combat monster card
        combatMonsterTable.top().left();
        combatMonsterTable.add(monsterStrengthLabel).left().padBottom(2).row();
        combatMonsterTable.add(monsterHpBar).width(210).height(20).padBottom(4).row();
        combatMonsterTable.add(combatStatusLabel).left().padBottom(6).row();
        combatMonsterTable.setVisible(false);

        Stack delveStack = new Stack();
        delveStack.add(delveInfoTable);
        delveStack.add(combatMonsterTable);
        delveZone.add(delveStack).grow();

        // ══════════════════════════════════════════════════════════════════
        // ZONE 4: Chronicle Action Log (~650px)
        // ══════════════════════════════════════════════════════════════════
        chronicleZone.top().left();
        Label chronicleHeader = new Label("[ CHRONICLE ]", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        chronicleZone.add(chronicleHeader).left().padBottom(2).row();

        for (int i = 0; i < 5; i++) {
            chronicleLabels[i] = new Label("", logLabelStyle);
            chronicleLabels[i].setEllipsis(true);
            chronicleZone.add(chronicleLabels[i]).left().growX().padBottom(1).row();
        }
        logLabel = chronicleLabels[4]; // Alias for backward compatibility

        // --- Assemble Bottom Bar Table ---
        bottomBarTable.setBackground(hudSkin.getDashboardBg());
        bottomBarTable.pad(10f, 15f, 10f, 15f);

        bottomBarTable.add(vitalsZone).width(450).fillY();
        bottomBarTable.add(new Image(hudSkin.getDividerIron())).width(6).fillY().padLeft(4).padRight(4);
        bottomBarTable.add(beltZone).width(420).fillY();
        bottomBarTable.add(new Image(hudSkin.getDividerIron())).width(6).fillY().padLeft(4).padRight(4);
        bottomBarTable.add(delveZone).width(330).fillY();
        bottomBarTable.add(new Image(hudSkin.getDividerIron())).width(6).fillY().padLeft(4).padRight(4);
        bottomBarTable.add(chronicleZone).expandX().fill();

        // --- Main Container (200px Height) ---
        mainContainer = new Table();
        mainContainer.setFillParent(true);
        mainContainer.bottom();
        mainContainer.add(bottomBarTable).growX().height(200);

        stage.addActor(mainContainer);
        stage.addActor(hudTooltip);
        stage.addActor(worldInteractionCard);

        // Initialize Combat Menu
        combatMenu = new CombatMenu(font);
        combatMenu.setVisible(false);
        stage.addActor(combatMenu);

        // Encounter Window
        com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle btnStyle = new com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle();
        btnStyle.font = font;
        btnStyle.fontColor = Color.WHITE;
        btnStyle.downFontColor = Color.GRAY;

        encounterWindow = new EncounterWindow(
                new com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle(font, Color.WHITE),
                new com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle(font, Color.WHITE),
                btnStyle);
        encounterWindow.setSize(1000, 800);
        encounterWindow.setPosition((viewport.getWorldWidth() - 1000) / 2f, (viewport.getWorldHeight() - 800) / 2f);
        stage.addActor(encounterWindow);

        // --- Global Input Listener for EncounterWindow ---
        stage.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override
            public boolean keyDown(com.badlogic.gdx.scenes.scene2d.InputEvent event, int keycode) {
                if (encounterWindow.isVisible()) {
                    return encounterWindow.handleInput(keycode);
                }
                return false;
            }
        });
    }

    private void updatePortrait() {
        if (portraitAtlas == null)
            return;

        if (player == null)
            return;

        float hpPercent = (float) player.getCurrentHP() / (float) player.getMaxHP();
        String suffix = "100";

        if (hpPercent >= 0.8f)
            suffix = "100";
        else if (hpPercent >= 0.7f)
            suffix = "80";
        else if (hpPercent >= 0.6f)
            suffix = "60"; // Mapped to closest if missing, assuming 60 exists
        else if (hpPercent >= 0.5f)
            suffix = "50";
        else if (hpPercent >= 0.4f)
            suffix = "40";
        else if (hpPercent >= 0.3f)
            suffix = "30";
        else if (hpPercent >= 0.2f)
            suffix = "20";
        else
            suffix = "10";

        if (!suffix.equals(currentPortraitName)) {
            // Use findRegion("portrait", index) because packer splits name_index
            TextureRegion region = portraitAtlas.findRegion("portrait", Integer.parseInt(suffix));
            if (region != null) {
                if (portraitImage != null) {
                    portraitImage.setDrawable(new TextureRegionDrawable(region));
                }
                currentPortraitName = suffix;
            }
        }
    }

    public void update(float dt) {
        stage.act(dt);

        updatePortrait();

        // --- 1. Update Vitals Gauges ---
        hpBar.setValue(player.getCurrentHP(), player.getMaxHP());
        if (player.getCurrentHP() <= player.getMaxHP() * 0.25f) {
            hpBar.setBarColor(HudSkin.COL_HP_CRITICAL);
        } else {
            hpBar.setBarColor(HudSkin.COL_HP_RED);
        }

        mpBar.setValue(player.getCurrentMP(), player.getMaxMP());
        expBar.setValue(player.getExperience(), player.getStats().getExperienceToNextLevel());
        levelBadgeLabel.setText("LVL " + player.getLevel());

        warStrengthValueLabel.setText(checkScramble(String.format("%d / %d", player.getCurrentHP(), player.getMaxHP())));
        spiritualStrengthValueLabel.setText(checkScramble(String.format("%d / %d", player.getCurrentMP(), player.getMaxMP())));
        xpLabel.setText(String.format("%d", player.getExperience()));
        levelLabel.setText(String.format("%d", player.getLevel()));

        DivinityManager dm = DivinityManager.getInstance();
        divinitiesLabel.setText("DIV: " + dm.getCurrentDivinities());
        divinitiesLabel.setColor(dm.hasLostDivinities() ? HudSkin.COL_GOLD_BRIGHT : Color.WHITE);

        DoomManager doom = DoomManager.getInstance();
        int deaths = doom.getDeathCount();
        float bridge = doom.getBridgeIntegrity();
        doomLabel.setText(String.format("DOOM: %d (%.0f%%)", deaths, bridge));
        if (bridge >= 75f) {
            doomLabel.setColor(HudSkin.COL_HP_CRITICAL);
        } else if (bridge >= 40f) {
            doomLabel.setColor(HudSkin.COL_TEMP_ORANGE);
        } else {
            doomLabel.setColor(Color.LIGHT_GRAY);
        }

        arrowsValueLabel.setText(String.format("%d", player.getArrows()));
        directionLabel.setText(checkScramble(player.getFacing().name().substring(0, 1)));
        treasureValueLabel.setText(checkScramble(String.format("%d", player.getTreasureScore())));

        // --- 2. Update Equipment Subtitle & Active Slot ---
        Item rItem = player.getInventory().getRightHand();
        Item lItem = player.getInventory().getLeftHand();
        if (rItem != null) {
            String mods = returnModsString(rItem);
            String statStr = "";
            if (rItem.getDamageDice() != null && !rItem.getDamageDice().isEmpty()) {
                statStr = " [" + rItem.getDamageDice() + "]";
            } else if (rItem.getArmorClassBonus() > 0) {
                statStr = " [+" + rItem.getArmorClassBonus() + " AC]";
            }
            equippedWeaponLabel.setText(checkScramble(rItem.getDisplayName() + statStr + mods));
        } else if (lItem != null) {
            equippedWeaponLabel.setText(checkScramble(lItem.getDisplayName()));
        } else {
            equippedWeaponLabel.setText(checkScramble("Hands Empty"));
        }

        for (int i = 0; i < 6; i++) {
            if (i == 0) {
                backpackSlots[i].setBackground(hudSkin.getSlotActive());
            } else {
                backpackSlots[i].setBackground(hudSkin.getSlotRecessed());
            }
        }

        // --- 3. Update Condition & Delve / Combat Card ---
        foodBar.setValue(player.getFood(), PlayerStats.MAX_SATIETY);
        foodBar.setBarColor(player.getFood() < 20 ? HudSkin.COL_HP_CRITICAL : HudSkin.COL_FOOD_GREEN);

        waterBar.setValue(player.getStats().getHydrationFloat(), PlayerStats.MAX_HYDRATION);
        waterBar.setBarColor(player.getStats().getHydrationFloat() < 20 ? HudSkin.COL_HP_CRITICAL : HudSkin.COL_WATER_CYAN);

        float temp = player.getStats().getBodyTemperature();
        tempBar.setValue(temp, 50f);
        if (temp < 35f) {
            tempBar.setBarColor(HudSkin.COL_WATER_CYAN);
        } else if (temp > 38.5f) {
            tempBar.setBarColor(HudSkin.COL_HP_CRITICAL);
        } else {
            tempBar.setBarColor(HudSkin.COL_TEMP_ORANGE);
        }

        String biomeName = "MAZE";
        if (worldManager != null && worldManager.getBiomeManager() != null) {
            GridPoint2 chunkId = worldManager.getCurrentPlayerChunkId();
            Biome b = worldManager.getBiomeManager().getBiome(chunkId);
            if (b != null) biomeName = b.name();
        }
        dungeonLevelLabel.setText(checkScramble("DUNGEON LVL " + maze.getLevel() + " [" + biomeName + "]"));

        if (combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE
                && combatManager.getMonster() != null) {
            delveInfoTable.setVisible(false);
            combatMonsterTable.setVisible(true);

            Monster monster = combatManager.getMonster();
            String catBadge = "";
            if (monster.getType() != null && monster.getType().getCategory() != null) {
                switch (monster.getType().getCategory()) {
                    case BAD:
                        catBadge = " [BAD: Holy/Spiritual]";
                        break;
                    case NASTY:
                        catBadge = " [NASTY: Weak to War]";
                        break;
                    case HORRIBLE:
                        catBadge = " [HORRIBLE: Balanced]";
                        break;
                }
            }
            monsterStrengthLabel.setText(checkScramble(monster.getMonsterType() + catBadge));
            monsterHpBar.setValue(monster.getCurrentHP(), monster.getMaxHP());

            switch (combatManager.getCurrentState()) {
                case PLAYER_TURN:
                    combatStatusLabel.setText(checkScramble(">>> YOUR TURN <<<"));
                    combatStatusLabel.setColor(HudSkin.COL_FOOD_GREEN);
                    break;
                case MONSTER_TURN:
                    combatStatusLabel.setText(checkScramble(">>> MONSTER ATTACKS! <<<"));
                    combatStatusLabel.setColor(HudSkin.COL_HP_CRITICAL);
                    break;
                default:
                    combatStatusLabel.setText(checkScramble(combatManager.getCurrentState().toString()));
                    combatStatusLabel.setColor(HudSkin.COL_GOLD_BRIGHT);
                    break;
            }
        } else {
            delveInfoTable.setVisible(true);
            combatMonsterTable.setVisible(false);
        }

        // --- 4. Update Chronicle Action Log ---
        updateChronicleLog();

        // --- 5. Update World Interaction Modal Card ---
        updateWorldInteractionCard();
    }

    private static class ParsedLogLine {
        String text;
        Color color;
        int count = 1;
    }

    private void updateChronicleLog() {
        List<String> rawHistory = eventManager.getMessageHistory();
        if (rawHistory == null || rawHistory.isEmpty()) {
            for (Label l : chronicleLabels) {
                l.setText("");
            }
            return;
        }

        // Deduplicate consecutive identical messages from the newest entries
        java.util.List<ParsedLogLine> deduplicated = new java.util.ArrayList<>();
        for (int i = 0; i < rawHistory.size() && deduplicated.size() < 5; i++) {
            String raw = rawHistory.get(i);
            if (raw == null || raw.trim().isEmpty()) continue;
            raw = raw.trim();

            if (!deduplicated.isEmpty() && deduplicated.get(deduplicated.size() - 1).text.equals(raw)) {
                deduplicated.get(deduplicated.size() - 1).count++;
            } else {
                ParsedLogLine line = new ParsedLogLine();
                line.text = raw;
                line.color = getSemanticColor(raw);
                deduplicated.add(line);
            }
        }

        // Alphas: index 4 (newest, bottom) = 1.0f, then 0.85f, 0.70f, 0.55f, 0.40f
        float[] alphas = { 0.40f, 0.55f, 0.70f, 0.85f, 1.0f };

        for (int i = 0; i < 5; i++) {
            chronicleLabels[i].setText("");
        }

        int numLines = Math.min(5, deduplicated.size());
        for (int i = 0; i < numLines; i++) {
            int labelIndex = 4 - i;
            ParsedLogLine item = deduplicated.get(i);
            String display = item.count > 1 ? item.text + " (x" + item.count + ")" : item.text;
            chronicleLabels[labelIndex].setText(checkScramble(display));
            Color c = item.color;
            float alpha = alphas[labelIndex];
            chronicleLabels[labelIndex].setColor(c.r, c.g, c.b, alpha);
        }
    }

    private Color getSemanticColor(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("experience") || lower.contains("gained") || lower.contains("level")
                || lower.contains("heal") || lower.contains("restored") || lower.contains("grace")) {
            return Color.valueOf("55FF55"); // Green
        }
        if (lower.contains("divinit") || lower.contains("treasure") || lower.contains("gold")
                || lower.contains("trophy") || lower.contains("holy") || lower.contains("blessed")) {
            return HudSkin.COL_GOLD_BRIGHT; // Gold
        }
        if (lower.contains("damage") || lower.contains("hit") || lower.contains("attacks")
                || lower.contains("dies") || lower.contains("dead") || lower.contains("starv")
                || lower.contains("thirst") || lower.contains("bleed") || lower.contains("poison")
                || lower.contains("wound")) {
            return Color.valueOf("FF5555"); // Red
        }
        if (lower.contains("can't") || lower.contains("cannot") || lower.contains("locked")
                || lower.contains("blocked") || lower.contains("fail")) {
            return HudSkin.COL_TEMP_ORANGE; // Orange/Warning
        }
        return Color.valueOf("D0D8E0"); // Slate/Light Gray
    }

    public void render() {
        viewport.apply(); // Apply the viewport settings

        // Removed background drawing

        // --- FIX: Only draw standard automap if debug overlay is NOT visible ---
        if (!debugManager.isDebugOverlayVisible()) {
            drawAutomap();
            drawBridgeIntegrityBar(); // NEW: Tarmin's Hunger UI
        }
        // Toggle debug lines based on global DebugManager state
        boolean isDebug = debugManager.isDebugOverlayVisible();

        // Apply the debug status to all tables
        mainContainer.setDebug(isDebug);
        bottomBarTable.setDebug(isDebug);
        statsTable.setDebug(isDebug);
        logTable.setDebug(isDebug);
        inventoryTable.setDebug(isDebug);

        // This renders all actors (labels, etc.) *and* the debug lines (if enabled)
        stage.draw();

        // Draw the 2D inventory items AFTER stage to appear on top
        drawInventory();

        drawPickupToast();

        if (isDebug) {

            // --- 1. PREPARE DEBUG FONT (Default) ---
            spriteBatch.setProjectionMatrix(stage.getCamera().combined);
            shapeRenderer.setProjectionMatrix(stage.getCamera().combined);

            // Lazy-load the default font if needed
            if (debugFont == null) {
                debugFont = new BitmapFont(); // Uses default LibGDX Arial font
                debugFont.getData().setScale(1.0f); // Make it smaller and cleaner
                debugFont.setColor(Color.WHITE);
            }

            // --- 2. Draw Backgrounds for Debug Text ---
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0f, 0f, 0f, 0.6f); // Darker semi-transparent black

            // Left Column Background (Controls)
            shapeRenderer.rect(10, 400, 340, 650);

            // Middle Column Background (Player Info)
            shapeRenderer.rect(360, 400, 340, 650);

            // New Column Background (Entity List)
            shapeRenderer.rect(720, 400, 260, 650);

            // Right Column Background (World/Items/Minimap)
            // Width spans to x=1520 to cover the minimap at x=1450
            shapeRenderer.rect(1000, 400, 520, 650);
            shapeRenderer.end();

            // --- 3. Draw all debug text ---
            spriteBatch.begin();

            BitmapFont defaultFont = debugFont;

            // --- COLUMN 1: CONTROLS & SYSTEM ---
            float leftColX = 20;
            float yPos = 1030;
            float lineGap = 25;

            defaultFont.setColor(Color.YELLOW);
            defaultFont.draw(spriteBatch, "SYSTEM & CONTROLS", leftColX, yPos);
            yPos -= lineGap;
            defaultFont.setColor(Color.WHITE);
            defaultFont.draw(spriteBatch, "DEBUG MODE (F1)", leftColX, yPos);
            yPos -= lineGap;
            defaultFont.draw(spriteBatch, "RENDER MODE: " + debugManager.getRenderMode() + " (F2)", leftColX, yPos);
            yPos -= lineGap;
            defaultFont.draw(spriteBatch, "FORCE MODIFIERS: " + SpawnManager.DEBUG_FORCE_MODIFIERS + " (F3)", leftColX,
                    yPos);
            yPos -= lineGap;
            defaultFont.draw(spriteBatch, "FPS: " + Gdx.graphics.getFramesPerSecond(), leftColX, yPos);
            yPos -= lineGap;

            yPos -= 10;
            defaultFont.setColor(Color.CYAN);
            defaultFont.draw(spriteBatch, "KEYBINDS", leftColX, yPos);
            yPos -= lineGap;
            defaultFont.setColor(Color.WHITE);

            String[] keyMappings = {
                    "UP/DOWN : Move",
                    "LEFT/RIGHT: Turn",
                    "O : Interact / Craft / Butcher",
                    "P : Pickup Item",
                    "U : Use Item",
                    "D : Climb Ladder",
                    "R : Rest",
                    "S : Swap Hands",
                    "E : Swap with Pack",
                    "T : Rotate Pack",
                    "A/SPACE : Instant Attack",
                    "NUM 7 : Dice Attack",
                    "M : Castle Map"
            };

            for (String mapping : keyMappings) {
                defaultFont.draw(spriteBatch, mapping, leftColX, yPos);
                yPos -= lineGap;
            }

            // --- COLUMN 2: PLAYER & EQUIPMENT ---
            float midColX = 370;
            float midY = 1030;

            if (player != null) {
                defaultFont.setColor(Color.YELLOW);
                defaultFont.draw(spriteBatch, "PLAYER STATS", midColX, midY);
                midY -= lineGap;
                defaultFont.setColor(Color.WHITE);

                int playerGridX = (int) player.getPosition().x;
                int playerGridY = (int) player.getPosition().y;
                defaultFont.draw(spriteBatch, "Grid Pos: (" + playerGridX + ", " + playerGridY + ")", midColX, midY);
                midY -= lineGap;
                defaultFont.draw(spriteBatch, "Facing: " + player.getFacing().name(), midColX, midY);
                midY -= lineGap;
                defaultFont.draw(spriteBatch, "AC: " + player.getArmorClass(), midColX, midY);
                midY -= lineGap;
                defaultFont.draw(spriteBatch,
                        "HP: " + player.getCurrentHP() + "/" + player.getMaxHP(), midColX,
                        midY);
                midY -= lineGap;
                defaultFont.draw(spriteBatch,
                        "MP: " + player.getCurrentMP() + "/" + player.getMaxMP(),
                        midColX, midY);
                midY -= lineGap;

                midY -= 20;

                // --- Equipped Item ---
                defaultFont.setColor(Color.YELLOW);
                defaultFont.draw(spriteBatch, "RIGHT HAND ITEM", midColX, midY);
                midY -= lineGap;
                defaultFont.setColor(Color.WHITE);

                Item rightHandItem = player.getInventory().getRightHand();
                if (rightHandItem != null) {
                    defaultFont.draw(spriteBatch, "Name: " + rightHandItem.getDisplayName(), midColX, midY);
                    midY -= lineGap;
                    defaultFont.draw(spriteBatch, "Category: " + rightHandItem.getCategory(), midColX, midY);
                    midY -= lineGap;

                    if (rightHandItem.isWeapon()) {
                        defaultFont.draw(spriteBatch, "Damage: " + rightHandItem.getDamageDice(), midColX, midY);
                        midY -= lineGap;
                        defaultFont.draw(spriteBatch, "Range: " + rightHandItem.getRange(), midColX, midY);
                        midY -= lineGap;
                    }
                    defaultFont.draw(spriteBatch, "Is Ranged: " + rightHandItem.isRanged(), midColX, midY);
                    midY -= lineGap;
                } else {
                    defaultFont.draw(spriteBatch, "Empty", midColX, midY);
                    midY -= lineGap;
                }
            }

            // --- COLUMN 2.5: ENTITY LIST ---
            float entityColX = 730;
            float entityY = 1030;

            if (maze != null) {
                // 1. Check for Portal
                boolean hasPortal = false;
                for (Item item : maze.getItems().values()) {
                    if (item.getType() == Item.ItemType.MYSTERIOUS_PORTAL) {
                        hasPortal = true;
                        break;
                    }
                }

                if (hasPortal) {
                    defaultFont.setColor(Color.LIME);
                    defaultFont.draw(spriteBatch, "PORTAL DETECTED!", entityColX, entityY);
                    entityY -= lineGap;
                } else {
                    defaultFont.setColor(Color.GRAY);
                    defaultFont.draw(spriteBatch, "No Portal", entityColX, entityY);
                    entityY -= lineGap;
                }

                entityY -= 10;

                // 2. List Monsters
                defaultFont.setColor(Color.RED);
                defaultFont.draw(spriteBatch, "MONSTERS", entityColX, entityY);
                entityY -= lineGap;
                defaultFont.setColor(Color.WHITE);

                java.util.Map<String, Integer> monsterCounts = new java.util.HashMap<>();
                for (com.bpm.minotaur.gamedata.monster.Monster m : maze.getMonsters().values()) {
                    String name = m.getType().name();
                    monsterCounts.put(name, monsterCounts.getOrDefault(name, 0) + 1);
                }
                for (java.util.Map.Entry<String, Integer> entry : monsterCounts.entrySet()) {
                    if (entityY < 420)
                        break;
                    defaultFont.draw(spriteBatch, entry.getValue() + "x " + entry.getKey(), entityColX, entityY);
                    entityY -= lineGap;
                }

                entityY -= 10;

                // 3. List Items
                defaultFont.setColor(Color.YELLOW);
                defaultFont.draw(spriteBatch, "ITEMS (REVEALED)", entityColX, entityY);
                entityY -= lineGap;

                defaultFont.setColor(Color.CYAN);
                java.util.Map<String, Integer> itemCounts = new java.util.HashMap<>();
                java.util.Map<String, Item> sampleItems = new java.util.HashMap<>();

                for (Item item : maze.getItems().values()) {
                    // Skip Portal in this list as it has its own dedicated status line above
                    if (item.getType() == Item.ItemType.MYSTERIOUS_PORTAL)
                        continue;

                    String name = item.getFriendlyName();
                    if (name == null || name.isEmpty())
                        name = item.getType().name();

                    if (item.getType().name().contains("SCROLL")) {
                        if (item.getScrollEffect() != null) {
                            name = "Scroll: " + item.getScrollEffect().getBaseName();
                        }
                    } else if (item.isPotion() && item.getTrueEffect() != null) {
                        name = "Potion: " + item.getTrueEffect().getBaseName();
                    }

                    itemCounts.put(name, itemCounts.getOrDefault(name, 0) + 1);
                    if (!sampleItems.containsKey(name)) {
                        sampleItems.put(name, item);
                    }
                }
                java.util.List<String> sortedKeys = new java.util.ArrayList<>(itemCounts.keySet());
                java.util.Collections.sort(sortedKeys);

                for (String key : sortedKeys) {
                    if (entityY < 420)
                        break;

                    Item sample = sampleItems.get(key);

                    // --- Draw Icon ---
                    spriteBatch.end();
                    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

                    float iconSize = 10f;
                    float iconX = entityColX - 15;
                    float iconY = entityY - 10; // Adjust for baseline

                    if (sample.getCategory() == ItemCategory.WAR_WEAPON
                            || sample.getCategory() == ItemCategory.SPIRITUAL_WEAPON) {
                        shapeRenderer.setColor(Color.ORANGE);
                        shapeRenderer.circle(iconX + iconSize / 2, iconY + iconSize / 2, iconSize / 2);
                    } else if (sample.isPotion()) {
                        shapeRenderer.setColor(Color.PINK);
                        shapeRenderer.circle(iconX + iconSize / 2, iconY + iconSize / 2, iconSize / 2);
                    } else if (sample.getType().toString().contains("SCROLL")) {
                        shapeRenderer.setColor(Color.CYAN);
                        shapeRenderer.rect(iconX, iconY, iconSize, iconSize);
                    } else {
                        shapeRenderer.setColor(Color.YELLOW);
                        shapeRenderer.circle(iconX + iconSize / 2, iconY + iconSize / 2, iconSize / 2);
                    }

                    shapeRenderer.end();
                    spriteBatch.begin();
                    // -----------------

                    // Truncate if too long
                    String dispKey = key.length() > 22 ? key.substring(0, 22) + "..." : key;
                    defaultFont.setColor(Color.LIGHT_GRAY);
                    defaultFont.draw(spriteBatch, itemCounts.get(key) + "x " + dispKey, entityColX, entityY);
                    entityY -= lineGap;
                }
            }

            // --- COLUMN 3: WORLD & ITEM MODS ---
            float rightColX = 1000;
            float rightY = 800; // Below minimap

            // World Info
            if (worldManager != null) {
                GridPoint2 chunkId = worldManager.getCurrentPlayerChunkId();
                Biome biome = worldManager.getBiomeManager().getBiome(chunkId);
                String themeName = (maze != null && maze.getTheme() != null) ? maze.getTheme().name : "Unknown";
                int chunkCount = worldManager.getLoadedChunkIds().size();

                defaultFont.setColor(Color.YELLOW);
                defaultFont.draw(spriteBatch, "WORLD DEBUG", rightColX, rightY);
                rightY -= lineGap;
                defaultFont.setColor(Color.WHITE);
                defaultFont.draw(spriteBatch, "Chunk ID: (" + chunkId.x + ", " + chunkId.y + ")", rightColX, rightY);
                rightY -= lineGap;
                defaultFont.draw(spriteBatch, "Biome: " + biome.name(), rightColX, rightY);
                rightY -= lineGap;
                defaultFont.draw(spriteBatch, "Theme: " + themeName, rightColX, rightY);
                rightY -= lineGap;
                defaultFont.draw(spriteBatch, "Loaded Chunks: " + chunkCount, rightColX, rightY);
                rightY -= lineGap;
            }

            rightY -= 20;

            // Item Modifiers
            if (player != null) {
                defaultFont.setColor(Color.LIME);
                defaultFont.draw(spriteBatch, "ITEM MODIFIERS", rightColX, rightY);
                rightY -= lineGap;

                rightY = drawItemModsDebug(spriteBatch, defaultFont, "Right Hand", player.getInventory().getRightHand(),
                        rightColX, rightY);
                rightY = drawItemModsDebug(spriteBatch, defaultFont, "Left Hand", player.getInventory().getLeftHand(),
                        rightColX, rightY);

                // Only show first 3 backpack slots to save space
                Item[] backpack = player.getInventory().getBackpack();
                for (int i = 0; i < 3; i++) {
                    Item item = (i < backpack.length) ? backpack[i] : null;
                    rightY = drawItemModsDebug(spriteBatch, defaultFont, "Pack " + i,
                            item, rightColX, rightY);
                }
            }

            spriteBatch.end(); // End batch for text drawing

            // --- 4. Draw the new minimap (Moved to end to be on top) ---
            // Positioned in the top right area
            drawWorldMinimap(1450, 900, debugFont, spriteBatch);

            // Note: We do NOT dispose the debugFont here anymore,
            // as we want to reuse the same instance.
        }

    }

    private String returnModsString(Item item) {
        String modText = "";
        if (item != null && item.isModified()) {
            for (ItemModifier mod : item.getModifiers()) {
                if (mod.type.name().equals("BONUS_DAMAGE")) {
                    modText += "+" + mod.value;
                } else {
                    modText += " " + mod.displayName + " ";
                }
            }
        }
        return modText;
    }

    private float drawItemModsDebug(SpriteBatch batch, BitmapFont font, String slotName, Item item, float x, float y) {
        if (item != null && item.isModified()) {
            font.setColor(Color.WHITE);
            font.draw(batch, slotName + ": " + item.getDisplayName(), x, y);
            y -= 20;

            font.setColor(Color.LIGHT_GRAY);
            for (ItemModifier mod : item.getModifiers()) {
                String modText = "  - " + mod.type.name() + " (" + mod.value + ")";
                font.draw(batch, modText, x, y);
                y -= 20;
            }
        }
        return y;
    }

    /**
     * Gets a single-character representation for a biome.
     */
    private String getBiomeLetter(Biome biome) {
        if (biome == null)
            return "?";
        switch (biome) {
            case MAZE:
                return "M";
            case FOREST:
                return "F";
            case PLAINS:
                return "P";
            case DESERT:
                return "D";
            case MOUNTAINS:
                return "A";
            case LAKELANDS:
                return "L";
            case OCEAN:
                return "O";
            default:
                return "?";
        }
    }

    /**
     * Draws the player's inventory (backpack and hands) using ShapeRenderer for
     * RETRO
     * mode
     * or SpriteBatch for MODERN mode.
     */
    private void drawInventory() {
        if (debugManager.getRenderMode() == DebugManager.RenderMode.RETRO) {
            renderRetroInventory();
        } else {
            renderModernInventory();
        }
    }

    private void renderRetroInventory() {
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Draw quick slots items (HUD belt)
        Item[] quickSlots = player.getInventory().getQuickSlots();
        for (int i = 0; i < quickSlots.length; i++) {
            Item item = quickSlots[i];
            Actor slot = backpackSlots[i];
            if (item != null) {
                Vector2 pos = slot.localToStageCoordinates(new Vector2(0, 0));
                ItemTemplate template = item.getTemplate();
                if (template != null && template.spriteData != null) {
                    drawItemSprite(shapeRenderer, item, template.spriteData, pos.x + 4, pos.y + 4,
                            slot.getWidth() - 8, slot.getHeight() - 8, item.getColor());
                }
            }
        }

        // Draw left hand item
        Item leftHand = player.getInventory().getLeftHand();
        if (leftHand != null) {
            Vector2 pos = leftHandSlot.localToStageCoordinates(new Vector2(0, 0));
            ItemTemplate template = leftHand.getTemplate();
            if (template != null && template.spriteData != null) {
                drawItemSprite(shapeRenderer, leftHand, template.spriteData, pos.x + 4, pos.y + 4,
                        leftHandSlot.getWidth() - 8, leftHandSlot.getHeight() - 8, leftHand.getColor());
            }
        }

        // Draw right hand item
        Item rightHand = player.getInventory().getRightHand();
        if (rightHand != null) {
            Vector2 pos = rightHandSlot.localToStageCoordinates(new Vector2(0, 0));
            ItemTemplate template = rightHand.getTemplate();
            if (template != null && template.spriteData != null) {
                drawItemSprite(shapeRenderer, rightHand, template.spriteData, pos.x + 4, pos.y + 4,
                        rightHandSlot.getWidth() - 8, rightHandSlot.getHeight() - 8, rightHand.getColor());
            }
        }

        shapeRenderer.end();
    }

    private void renderModernInventory() {
        spriteBatch.setProjectionMatrix(stage.getCamera().combined);
        spriteBatch.begin();

        // Draw quick slots items (HUD belt)
        Item[] quickSlots = player.getInventory().getQuickSlots();
        for (int i = 0; i < quickSlots.length; i++) {
            Item item = quickSlots[i];
            Actor slot = backpackSlots[i];
            Vector2 pos = slot.localToStageCoordinates(new Vector2(0, 0));
            if (item != null) {
                drawModernItem(item, pos.x + 4, pos.y + 4, slot.getWidth() - 8, slot.getHeight() - 8);
            }
        }

        // Draw left hand item
        Item leftHand = player.getInventory().getLeftHand();
        if (leftHand != null) {
            Vector2 pos = leftHandSlot.localToStageCoordinates(new Vector2(0, 0));
            drawModernItem(leftHand, pos.x + 4, pos.y + 4, leftHandSlot.getWidth() - 8, leftHandSlot.getHeight() - 8);
        }

        // Draw right hand item
        Item rightHand = player.getInventory().getRightHand();
        if (rightHand != null) {
            Vector2 pos = rightHandSlot.localToStageCoordinates(new Vector2(0, 0));
            drawModernItem(rightHand, pos.x + 4, pos.y + 4, rightHandSlot.getWidth() - 8, rightHandSlot.getHeight() - 8);
        }

        spriteBatch.end();

        renderModernItemOverlays();
    }

    private void renderModernItemOverlays() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Define common slots checks to avoid code duplication if Refactoring,
        // but for now we follow the pattern:

        // Quick Slots
        Item[] quickSlots = player.getInventory().getQuickSlots();
        for (int i = 0; i < quickSlots.length; i++) {
            Item item = quickSlots[i];
            Actor slot = backpackSlots[i];
            if (item != null && item.isModified()) {
                Vector2 pos = slot.localToStageCoordinates(new Vector2(0, 0));
                drawItemGlow(pos.x, pos.y, slot.getWidth(), slot.getHeight());
            }
        }

        // Left Hand
        Item leftHand = player.getInventory().getLeftHand();
        if (leftHand != null && leftHand.isModified()) {
            Vector2 pos = leftHandSlot.localToStageCoordinates(new Vector2(0, 0));
            drawItemGlow(pos.x, pos.y, leftHandSlot.getWidth(), leftHandSlot.getHeight());
        }

        // Right Hand
        Item rightHand = player.getInventory().getRightHand();
        if (rightHand != null && rightHand.isModified()) {
            Vector2 pos = rightHandSlot.localToStageCoordinates(new Vector2(0, 0));
            drawItemGlow(pos.x, pos.y, rightHandSlot.getWidth(), rightHandSlot.getHeight());
        }

        shapeRenderer.end();
    }

    private void drawItemGlow(float x, float y, float width, float height) {
        shapeRenderer.setColor(GLOW_COLOR_UI);
        shapeRenderer.rect(x - 2, y - 2, width + 4, height + 4);
    }

    private void drawModernItem(Item item, float x, float y, float width, float height) {
        TextureRegion region = item.getTextureRegion();
        Texture texture = item.getTexture();

        if (region != null) {
            spriteBatch.draw(region, x, y, width, height);
        } else if (texture != null) {
            spriteBatch.draw(texture, x, y, width, height);
        } else {
            // Fallback if no texture found?
            // Could render a placeholder or just skip.
            // For now, let's render a small colored rect using a 1x1 white pixel if
            // possible,
            // or just ignore it to avoid breaking the batch.
        }
    }

    private void updateWorldInteractionCard() {
        if (worldInteractionCard == null) return;

        if (debugManager.isDebugOverlayVisible() ||
                (combatManager != null && combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE)) {
            worldInteractionCard.hide();
            return;
        }

        Vector2 dir = player.getFacing().getVector();
        int frontX = (int) (player.getPosition().x + dir.x);
        int frontY = (int) (player.getPosition().y + dir.y);
        GridPoint2 frontTile = new GridPoint2(frontX, frontY);
        GridPoint2 feetTile = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);

        Item frontItem = maze.getItems().get(frontTile);

        // 1. Check Shelter Objects
        if (frontItem != null) {
            if (frontItem.getType() == Item.ItemType.HOME_CHEST) {
                worldInteractionCard.show(
                        "[SHELTER HUB]",
                        "[STORAGE STASH]",
                        "Shelter Storage Chest",
                        "Secure repository for equipment, relics, and treasures. Stored items persist between delve runs.",
                        "[ O ]",
                        "Open Storage Chest",
                        () -> { if (gameScreen != null) gameScreen.interactWithWorldObject(); }
                );
                return;
            }
            if (frontItem.getType() == Item.ItemType.HOME_SLEEPING_BAG) {
                worldInteractionCard.show(
                        "[SHELTER HUB]",
                        "[REST & RECOVERY]",
                        "Shelter Bedroll",
                        "Rest to fully replenish Health & Mana, cure status ailments, and save your delve progress.",
                        "[ O ]",
                        "Rest & Save Game",
                        () -> { if (gameScreen != null) gameScreen.interactWithWorldObject(); }
                );
                return;
            }
            if (frontItem.getType() == Item.ItemType.HOME_CRAFTING_BENCH) {
                worldInteractionCard.show(
                        "[SHELTER HUB]",
                        "[ARTISAN WORKBENCH]",
                        "Artisan's Crafting Bench & Forge",
                        "Hone weapons and armor, salvage dungeon scrap, infuse monster trophies, and carve bone relics. Draws from pack and chest.",
                        "[ O ]",
                        "Open Crafting Bench",
                        () -> { if (gameScreen != null) gameScreen.interactWithWorldObject(); }
                );
                return;
            }
            if (frontItem.getType() == Item.ItemType.HOME_FIRE_POT) {
                worldInteractionCard.show(
                        "[SHELTER HUB]",
                        "[CAMPFIRE & COOKING]",
                        "Cooking Fire Pot",
                        "Combine harvested meats and forage into hearty meals granting lasting survival buffs.",
                        "[ O ]",
                        "Cook Meals",
                        () -> { if (gameScreen != null) gameScreen.interactWithWorldObject(); }
                );
                return;
            }
        }

        // 2. Check Doors in front
        Object objInFront = maze.getGameObjectAt(frontX, frontY);
        if (objInFront instanceof Door) {
            Door door = (Door) objInFront;
            boolean isOpen = (door.getState() == Door.DoorState.OPEN || door.getState() == Door.DoorState.OPENING);
            worldInteractionCard.show(
                    "[DOORWAY]",
                    isOpen ? "[OPEN]" : "[CLOSED]",
                    "Heavy Timber Door",
                    isOpen ? "The doorway is open." : "Sturdy reinforced door keeping dungeon horrors out.",
                    "[ O ]",
                    isOpen ? "Close Door" : "Open Door",
                    () -> { if (gameScreen != null) gameScreen.interactWithWorldObject(); }
            );
            return;
        }

        // 3. Check Gates in front
        Gate gate = maze.getGates().get(frontTile);
        if (gate != null) {
            boolean isChunk = gate.isChunkTransitionGate();
            worldInteractionCard.show(
                    isChunk ? "[EXPEDITION GATEWAY]" : "[DUNGEON PORTCULLIS]",
                    "[GATE]",
                    isChunk ? "Sector Passage Gate" : "Iron Portcullis",
                    isChunk ? "Gateway leading into an adjacent sector of Castle Tarmin." : "Massive iron portcullis barring the corridor.",
                    "[ O ]",
                    "Pass Through Gate",
                    () -> { if (gameScreen != null) gameScreen.interactWithWorldObject(); }
            );
            return;
        }

        // 4. Check Containers in front
        if (frontItem != null && frontItem.getCategory() == ItemCategory.CONTAINER) {
            boolean locked = frontItem.isLocked();
            worldInteractionCard.show(
                    locked ? "[LOCKED CONTAINER]" : "[TREASURE CONTAINER]",
                    "[CONTAINER]",
                    frontItem.getDisplayName(),
                    locked ? "Secured ancient chest. Requires a matching key to unlock." : "Open to search for relics and treasures inside.",
                    "[ O ]",
                    locked ? "Unlock Container" : "Open Container",
                    () -> { if (gameScreen != null) gameScreen.interactWithWorldObject(); }
            );
            return;
        }

        // 5. Check Corpses in front
        if (frontItem != null && frontItem.getType() == Item.ItemType.CORPSE) {
            boolean hasStored = frontItem.getContents() != null && !frontItem.getContents().isEmpty();
            worldInteractionCard.show(
                    hasStored ? "[FALLEN REMAINS]" : "[CREATURE CARCASS]",
                    "[CORPSE]",
                    hasStored ? "Lost Adventurer Remains" : frontItem.getDisplayName(),
                    hasStored ? "Recover lost equipment and backpack from your previous demise." : "Fallen creature. Can be harvested for meat, bones, and alchemical viscera.",
                    "[ O ]",
                    hasStored ? "Recover Equipment" : "Harvest / Butcher",
                    () -> { if (gameScreen != null) gameScreen.interactWithWorldObject(); }
            );
            return;
        }

        // 6. Check Ladders in front or at feet
        Ladder ladder = maze.getLadders().get(frontTile);
        if (ladder == null) {
            ladder = maze.getLadders().get(feetTile);
        }
        if (ladder != null) {
            boolean isDown = (ladder.getType() == Ladder.LadderType.DOWN);
            worldInteractionCard.show(
                    isDown ? "[STRATA DESCENT]" : "[STRATA ASCENT]",
                    "[LADDER]",
                    isDown ? "Ladder Down" : "Ladder Up",
                    isDown ? "Descends deeper into Castle Tarmin." : "Ascends toward upper sanctums and camp.",
                    "[ D ]",
                    isDown ? "Descend Ladder" : "Ascend Ladder",
                    () -> { if (gameScreen != null) gameScreen.ascendOrDescendLadder(); }
            );
            return;
        }

        // 7. Check Ground Items (in front first, then at feet)
        Item groundItem = (frontItem != null && !frontItem.isImpassable()) ? frontItem : null;
        boolean atFeet = false;
        if (groundItem == null) {
            Item feetItem = maze.getItems().get(feetTile);
            if (feetItem != null && !feetItem.isImpassable()) {
                groundItem = feetItem;
                atFeet = true;
            }
        }
        if (groundItem != null) {
            String loc = atFeet ? "[GROUND (FEET)]" : "[GROUND (AHEAD)]";
            String cat = groundItem.getCategory() != null ? "[" + groundItem.getCategory().name().replace('_', ' ') + "]" : "[ITEM]";
            String title = groundItem.getDisplayName();
            String statDesc = formatItemStatDescription(groundItem);
            String actionText = "Pick Up [P]";
            if (groundItem.isWeapon() || groundItem.isArmor() || groundItem.isShield()) {
                actionText += "  |  Quick Equip [E]";
            } else if (groundItem.isConsumableOrTool()) {
                actionText += "  |  Consume [E/U]";
            }
            worldInteractionCard.show(
                    loc,
                    cat,
                    title,
                    statDesc,
                    "[ P / E ]",
                    actionText,
                    () -> { if (gameScreen != null) gameScreen.pickupWorldItem(); }
            );
            return;
        }

        // 8. Nothing interactive found
        worldInteractionCard.hide();
    }

    private String formatItemStatDescription(Item item) {
        if (item == null) return "";
        StringBuilder sb = new StringBuilder();
        if (item.getDamageDice() != null && !item.getDamageDice().isEmpty()) {
            sb.append("Damage: ").append(item.getDamageDice()).append("   ");
        }
        if (item.getArmorClassBonus() > 0) {
            sb.append("Armor: +").append(item.getArmorClassBonus()).append(" AC   ");
        }
        if (item.getNutrition() > 0) {
            sb.append("Food: +").append(item.getNutrition()).append("   ");
        }
        if (item.getHydrationValue() > 0) {
            sb.append("Water: +").append(item.getHydrationValue()).append("   ");
        }
        if (item.isPotion()) {
            if (item.isIdentified() && item.getTrueEffect() != null) {
                sb.append("Effect: ").append(item.getTrueEffect().getBaseName()).append("   ");
            } else {
                sb.append("Unidentified Potion   ");
            }
        }
        if (item.getGrantedDie() != null) {
            sb.append("Die: ").append(item.getGrantedDie().getName()).append("   ");
        }
        if (item.getBaseValue() > 0 && sb.length() == 0) {
            sb.append("Value: ").append(item.getBaseValue()).append(" Gold");
        }
        return sb.length() > 0 ? sb.toString().trim() : "Usable world item.";
    }

    private void drawPickupToast() {
        if (toastTimer <= 0f || toastItem == null) return;
        toastTimer -= Gdx.graphics.getDeltaTime();

        float alpha = Math.min(1.0f, toastTimer * 2f);
        String name = toastItem.getDisplayName();
        String cat = toastItem.getCategory() != null ? "[" + toastItem.getCategory().name().replace('_', ' ') + "]" : "";
        String detail = "";
        if (toastItem.isWeapon()) {
            detail = " (" + (toastItem.getDamageDice() != null ? toastItem.getDamageDice() : "1d6") + " Dmg)";
        } else if (toastItem.isArmor()) {
            detail = " (+" + toastItem.getArmorClassBonus() + " AC)";
        } else if (toastItem.isFood()) {
            detail = " (+" + (toastItem.getNutrition() > 0 ? toastItem.getNutrition() : 5) + " Food)";
        } else if (toastItem.isPotion()) {
            detail = toastItem.isIdentified() && toastItem.getTrueEffect() != null
                    ? " (" + toastItem.getTrueEffect().getBaseName() + ")"
                    : " (Unknown Potion)";
        }

        String text = "Acquired: " + name + " " + cat + detail;
        GlyphLayout layout = new GlyphLayout(font, text);
        float boxW = layout.width + 50;
        float boxH = 48;
        float boxX = (viewport.getWorldWidth() - boxW) / 2f;
        float boxY = 1000f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.06f, 0.08f, 0.12f, 0.90f * alpha);
        shapeRenderer.rect(boxX, boxY, boxW, boxH);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.2f, 0.85f, 0.45f, alpha);
        shapeRenderer.rect(boxX, boxY, boxW, boxH);
        shapeRenderer.end();

        spriteBatch.setProjectionMatrix(stage.getCamera().combined);
        spriteBatch.begin();
        font.setColor(1f, 1f, 1f, alpha);
        font.draw(spriteBatch, text, boxX + 25, boxY + boxH - 14);
        spriteBatch.end();
    }

    private void drawItemSprite(ShapeRenderer shapeRenderer, Item item, String[] spriteData, float x, float y,
            float width, float height, Color color) {
        if (spriteData == null || spriteData.length == 0)
            return;

        if (item.isModified()) {
            shapeRenderer.setColor(GLOW_COLOR_UI);
            shapeRenderer.rect(x - 2, y - 2, width + 4, height + 4);
        }

        shapeRenderer.setColor(color);
        int numRows = spriteData.length;
        float pixelHeight = height / (float) numRows;

        for (int row = 0; row < numRows; row++) {
            String line = spriteData[row];
            if (line == null)
                continue;
            int numCols = line.length();
            float pixelWidth = width / (float) Math.max(1, numCols);
            for (int col = 0; col < numCols; col++) {
                if (line.charAt(col) == '#') {
                    shapeRenderer.rect(x + col * pixelWidth, y + (numRows - 1 - row) * pixelHeight, pixelWidth, pixelHeight);
                }
            }
        }
    }

    /**
     * Draws the new world minimap in the debug (F1) view.
     */
    private void drawWorldMinimap(float centerX, float centerY, BitmapFont debugFont, SpriteBatch spriteBatch) {
        if (worldManager == null)
            return;

        java.util.Set<GridPoint2> loadedChunks = worldManager.getLoadedChunkIds();
        GridPoint2 currentChunk = worldManager.getCurrentPlayerChunkId();
        BiomeManager biomeManager = worldManager.getBiomeManager();

        if (loadedChunks == null || currentChunk == null || biomeManager == null)
            return;

        float chunkSize = 20; // Size of each chunk square
        float gap = 4; // Gap between chunks

        // --- 1. Draw Squares ---
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (GridPoint2 id : loadedChunks) {
            int relX = id.x - currentChunk.x;
            int relY = id.y - currentChunk.y;
            float rectX = centerX + (relX * (chunkSize + gap));
            float rectY = centerY + (relY * (chunkSize + gap));

            // Set color: Green for current, Gray for others
            if (relX == 0 && relY == 0) {
                shapeRenderer.setColor(Color.GREEN);
            } else {
                shapeRenderer.setColor(Color.GRAY);
            }

            shapeRenderer.rect(rectX, rectY, chunkSize, chunkSize);
        }
        shapeRenderer.end();

        // --- 2. Draw Letters ---
        spriteBatch.begin();
        debugFont.setColor(Color.BLACK); // Use black for high contrast

        for (GridPoint2 id : loadedChunks) {
            int relX = id.x - currentChunk.x;
            int relY = id.y - currentChunk.y;
            float rectX = centerX + (relX * (chunkSize + gap));
            float rectY = centerY + (relY * (chunkSize + gap));

            // Get the biome and its letter
            Biome biome = biomeManager.getBiome(id);
            String letter = getBiomeLetter(biome);
            glyphLayout.setText(debugFont, letter);

            // Calculate centered position
            float textX = rectX + (chunkSize - glyphLayout.width) / 2;
            float textY = rectY + (chunkSize + glyphLayout.height) / 2;

            debugFont.draw(spriteBatch, glyphLayout, textX, textY);
        }
        spriteBatch.end();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    private void drawAutomap() {
        if (maze == null || player == null || worldManager == null)
            return;

        // Configuration
        float maxMapSize = 300f; // Maximum dimension (width or height)
        float mapRightMargin = 20f;
        float mapTopMargin = 20f;

        // Fetch Biome Data for Fog
        GridPoint2 chunkId = worldManager.getCurrentPlayerChunkId();
        Biome biome = worldManager.getBiomeManager().getBiome(chunkId);
        boolean hasFog = biome != null && biome.hasFogOfWar();
        float fogDistance = (biome != null) ? biome.getFogDistance() : 100f;

        // Fix: Clamp logic removed as per user request (Standard Minimap)
        // if (biome == Biome.FOREST) {
        // fogDistance = 2.0f;
        // }

        int mazeW = maze.getWidth();
        int mazeH = maze.getHeight();

        // Prevent division by zero
        if (mazeW == 0 || mazeH == 0)
            return;

        // Calculate cell size to fit within the maxMapSize box
        float cellSize = maxMapSize / Math.max(mazeW, mazeH);

        // Calculate the ACTUAL size of the map on screen
        float actualMapWidth = mazeW * cellSize;
        float actualMapHeight = mazeH * cellSize;

        // Position: Top Right (Anchored)
        float startX = 1920 - actualMapWidth - mapRightMargin;
        float startY = 1080 - actualMapHeight - mapTopMargin;

        // --- 1. Draw Background (Fitted) ---
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.5f); // Semi-transparent black

        // Draw rect with small padding (5px) around the ACTUAL size
        shapeRenderer.rect(startX - 5, startY - 5, actualMapWidth + 10, actualMapHeight + 10);

        // --- 2. Draw Visited Tiles (Walls/Floor) ---
        shapeRenderer.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Wall bitmasks
        int WALL_NORTH = 0b01000000;
        int WALL_EAST = 0b00000100;
        int WALL_SOUTH = 0b00010000;
        int WALL_WEST = 0b00000001;
        int DOOR_NORTH = 0b10000000;
        int DOOR_EAST = 0b00001000;
        int DOOR_SOUTH = 0b00100000;
        int DOOR_WEST = 0b00000010;

        for (int y = 0; y < mazeH; y++) {
            for (int x = 0; x < mazeW; x++) {
                // VISIBILITY CHECK
                boolean isOmniscient = player.getStatusManager().hasEffect(StatusEffectType.OMNISCIENT);
                if (!maze.isVisited(x, y) && !isOmniscient) {
                    continue;
                }

                // Fog of War Check
                if (hasFog && !isOmniscient) {
                    float dist = Vector2.dst(player.getPosition().x, player.getPosition().y, x, y);
                    if (dist > fogDistance) {
                        continue;
                    }
                }

                int mask = maze.getWallDataAt(x, y);
                float cx = startX + (x * cellSize);
                float cy = startY + (y * cellSize);

                // Draw Walls (White)
                shapeRenderer.setColor(Color.WHITE);
                if ((mask & WALL_NORTH) != 0)
                    shapeRenderer.line(cx, cy + cellSize, cx + cellSize, cy + cellSize);
                if ((mask & WALL_EAST) != 0)
                    shapeRenderer.line(cx + cellSize, cy, cx + cellSize, cy + cellSize);
                if ((mask & WALL_SOUTH) != 0)
                    shapeRenderer.line(cx, cy, cx + cellSize, cy);
                if ((mask & WALL_WEST) != 0)
                    shapeRenderer.line(cx, cy, cx, cy + cellSize);

                // Draw Doors (Gold/Yellow)
                shapeRenderer.setColor(Color.GOLD);
                if ((mask & DOOR_NORTH) != 0)
                    shapeRenderer.line(cx, cy + cellSize, cx + cellSize, cy + cellSize);
                if ((mask & DOOR_EAST) != 0)
                    shapeRenderer.line(cx + cellSize, cy, cx + cellSize, cy + cellSize);
                if ((mask & DOOR_SOUTH) != 0)
                    shapeRenderer.line(cx, cy, cx + cellSize, cy);
                if ((mask & DOOR_WEST) != 0)
                    shapeRenderer.line(cx, cy, cx, cy + cellSize);
            }
        }
        shapeRenderer.end();

        // --- 3. Draw Objects (Gates, Ladders, Player) ---
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int y = 0; y < mazeH; y++) {
            for (int x = 0; x < mazeW; x++) {
                boolean isOmniscient = player.getStatusManager().hasEffect(StatusEffectType.OMNISCIENT);
                if (!maze.isVisited(x, y) && !isOmniscient)
                    continue;

                // Fog of War Check
                if (hasFog && !isOmniscient) {
                    float dist = Vector2.dst(player.getPosition().x, player.getPosition().y, x, y);
                    if (dist > fogDistance) {
                        continue;
                    }
                }

                Object obj = maze.getGameObjectAt(x, y);
                float cx = startX + (x * cellSize);
                float cy = startY + (y * cellSize);

                if (obj instanceof Gate) {
                    shapeRenderer.setColor(Color.CYAN);
                    shapeRenderer.rect(cx + cellSize * 0.25f, cy + cellSize * 0.25f, cellSize * 0.5f, cellSize * 0.5f);
                } else if (maze.getLadders().containsKey(new GridPoint2(x, y))) {
                    shapeRenderer.setColor(Color.BROWN);
                    shapeRenderer.rect(cx + cellSize * 0.3f, cy + cellSize * 0.3f, cellSize * 0.4f, cellSize * 0.4f);
                }
            }
        }

        // --- 3.5 Draw Scenery (Trees, Rocks) ---
        shapeRenderer.setColor(Color.FOREST);
        for (java.util.Map.Entry<GridPoint2, com.bpm.minotaur.gamedata.Scenery> entry : maze.getScenery().entrySet()) {
            GridPoint2 pos = entry.getKey();
            com.bpm.minotaur.gamedata.Scenery scenery = entry.getValue();

            int x = pos.x;
            int y = pos.y;

            boolean isOmniscient = player.getStatusManager().hasEffect(StatusEffectType.OMNISCIENT);
            if (!maze.isVisited(x, y) && !isOmniscient)
                continue;

            // Fog Check
            if (hasFog && !isOmniscient) {
                float dist = Vector2.dst(player.getPosition().x, player.getPosition().y, x, y);
                if (dist > fogDistance)
                    continue;
            }

            float cx = startX + (x * cellSize);
            float cy = startY + (y * cellSize);

            if (scenery.getType() == com.bpm.minotaur.gamedata.Scenery.SceneryType.TREE) {
                shapeRenderer.setColor(Color.FOREST);
                shapeRenderer.circle(cx + cellSize / 2, cy + cellSize / 2, cellSize * 0.4f);
            } else if (scenery.getType() == com.bpm.minotaur.gamedata.Scenery.SceneryType.ROCK) {
                shapeRenderer.setColor(Color.GRAY);
                shapeRenderer.rect(cx + cellSize * 0.2f, cy + cellSize * 0.2f, cellSize * 0.6f, cellSize * 0.6f);
            } else {
                shapeRenderer.setColor(Color.OLIVE);
                shapeRenderer.circle(cx + cellSize / 2, cy + cellSize / 2, cellSize * 0.2f);
            }
        }

        // Player (Green Arrow/Dot)
        if (player != null) {
            float px = startX + (player.getPosition().x * cellSize);
            float py = startY + (player.getPosition().y * cellSize);

            shapeRenderer.setColor(Color.LIME);
            shapeRenderer.circle(px, py, cellSize * 0.3f, 8);

            // Direction Indicator
            Vector2 dir = player.getDirectionVector();
            shapeRenderer.setColor(Color.LIME);
            shapeRenderer.rectLine(px, py, px + (dir.x * cellSize * 0.6f), py + (dir.y * cellSize * 0.6f), 2f);
        }

        // --- 3.7 Draw Lost Divinities Tile (Gold Diamond) ---
        GridPoint2 lostTile = DivinityManager.getInstance().getLostDivinityTile();
        if (lostTile != null) {
            float ltx = startX + (lostTile.x * cellSize) + cellSize * 0.5f;
            float lty = startY + (lostTile.y * cellSize) + cellSize * 0.5f;
            float r = cellSize * 0.4f;
            shapeRenderer.setColor(Color.GOLD);
            shapeRenderer.triangle(ltx, lty + r, ltx + r, lty, ltx, lty - r);
            shapeRenderer.triangle(ltx, lty + r, ltx - r, lty, ltx, lty - r);
        }

        shapeRenderer.end();

        // --- 4. Draw Monsters (Red Dots) ---
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.RED);

        GridPoint2 playerPosVal = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);

        for (java.util.Map.Entry<GridPoint2, Monster> entry : maze.getMonsters().entrySet()) {
            GridPoint2 mPos = entry.getKey();
            Monster monster = entry.getValue(); // Use monster object if needed later?

            // Distance Check (Hearing) - Radius 4
            float dist = Vector2.dst(playerPosVal.x, playerPosVal.y, mPos.x, mPos.y);
            boolean isAudible = dist <= 4.0f;

            // Visibility Check (Sight)
            // Only strictly needed if outside audible range, but requirements say "Seen OR
            // Audible"
            boolean isVisible = false;

            boolean isOmniscient = player.getStatusManager().hasEffect(StatusEffectType.OMNISCIENT);

            // Fog Check
            // if (hasFog && !isOmniscient) {
            // if (dist > fogDistance) {
            // continue; // Skip rendering if in fog
            // }
            // }

            // Optimization: Only check LOS if NOT audible (since audible is sufficient to
            // show)
            // UPDATED LOGIC: User said "As long as they are in view".
            // If they are strictly visual range (e.g. 8 tiles away) but usually seen?
            // "Once a monster is 'seen'... as long as they are in view" implies immediate
            // update.
            // If audible is true, we show. If audible is false, check LOS.

            if (isAudible) {
                isVisible = true;
            } else if (monster.isTagged()) {
                isVisible = true; // Always visible if tagged
            } else if (player != null && player.getStatusManager().hasEffect(StatusEffectType.OMNISCIENT)) {
                isVisible = true; // Super Vision
            } else if (dist <= 15) { // Check for new tags
                if (checkhudLineOfSight(playerPosVal, mPos)) {
                    monster.setTagged(true); // Tag it!
                    isVisible = true;
                }
            }

            if (isVisible) {
                float mx = startX + (mPos.x * cellSize);
                float my = startY + (mPos.y * cellSize);
                // Draw slightly larger than walls? Or same size.
                // "Red square".
                shapeRenderer.rect(mx, my, cellSize, cellSize);
            }
        }
        shapeRenderer.end();

        // --- 5. Game Log (New Feature) ---
        // drawGameLog(startX, startY); // Merged into bottom bar
    }

    /**
     * Simple Bresenham LOS check for the HUD.
     */
    private boolean checkhudLineOfSight(GridPoint2 start, GridPoint2 end) {
        int x0 = start.x;
        int y0 = start.y;
        int x1 = end.x;
        int y1 = end.y;

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);

        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;

        int err = dx - dy;

        int cx = x0;
        int cy = y0;

        while (true) {
            if (cx == x1 && cy == y1)
                return true;

            if (!(cx == start.x && cy == start.y)) {
                // Check walls
                int walls = maze.getWallDataAt(cx, cy);
                // Standard walls have bits set. 0 is empty?
                // In MonsterAiManager, checks were loose.
                // Let's use isPassable for simplicity BUT isPassable checks monsters/items too.
                // We ONLY care about OPAQUE walls/doors.
                // WallDataAt returns 255 if OOB.

                // Inspecting Maze.java: "walls" is raw int.
                // "if ((currentCellData & wallMask) != 0)" -> Blocks.
                // If cell has ANY wall, does it block LOS?
                // No, a cell can have a North wall but allow E-W passage.

                // ACCURATE LOS needs to check crossing edges.
                // BUT for simple minimap, cell-based occlusion (is the cell solid?) is often
                // used.
                // Let's assume non-zero wall data implies *some* structure.
                // However, "isWallBlocking" logic suggests complex bitmasking.

                // Simpler check: Doors.
                // If it's a door and closed, it blocks.
                Object obj = maze.getGameObjectAt(cx, cy);
                if (obj instanceof Door && ((Door) obj).getState() != Door.DoorState.OPEN)
                    return false;

                // Wall check:
                // If it has ALL walls (solid block)?
                // Or simplified: Just check if we can see through.
                // Let's copy the simpler approach: if wallData implies high density or specific
                // blocking?
                // Actually, let's treat any wallData != 0 as potential occlusion?
                // No, floor tiles might be 0.
                // Let's assume if it blocks MOVEMENT it blocks SIGHT?
                // No, Gates block movement but not sight.

                // Best Approximation:
                // If it is a CLOSED DOOR, block.
                // If it is a SOLID WALL (all bits?), block.

                // Re-reading MonsterAiManager:
                // "if (walls != 0) return false;" <- It assumed 0 is open.
                // I will use that for consistency.
                if (walls != 0)
                    return false;
            }

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                cx += sx;
            }
            if (e2 < dx) {
                err += dx;
                cy += sy;
            }
        }
    }

    // drawGameLog removed as it is now integrated into the bottom bar log table.

    // --- NEW: Combat Menu UI ---
    public class CombatMenu extends Table {
        private final Label attackLabel;
        private final Label castLabel;
        private final Label rollLabel;
        private final Label useLabel;
        private final Label blockLabel;
        private final Label cookLabel;
        private final Label[] options;
        private int selectedIndex = 0;

        public CombatMenu(BitmapFont font) {
            this.setBackground(bottomBarDrawable); // Use bottom bar bg
            this.setSize(300, 210);
            this.setPosition(20, 205); // Bottom Left above 200px HUD

            Label.LabelStyle style = new Label.LabelStyle(font, Color.WHITE);
            Label.LabelStyle selectedStyle = new Label.LabelStyle(font, Color.YELLOW);

            attackLabel = new Label("ATTACK", style);
            castLabel = new Label("CAST", style);
            rollLabel = new Label("ROLL", style);
            useLabel = new Label("USE", style);
            blockLabel = new Label("BLOCK", style);
            cookLabel = new Label("COOK", style);

            options = new Label[] { attackLabel, castLabel, rollLabel, useLabel, blockLabel, cookLabel };

            this.add(attackLabel).pad(5).left().row();
            this.add(castLabel).pad(5).left().row();
            this.add(rollLabel).pad(5).left().row();
            this.add(useLabel).pad(5).left().row();
            this.add(blockLabel).pad(5).left().row();
            this.add(cookLabel).pad(5).left().row();

            updateSelection();
        }

        public void navigateUp() {
            selectedIndex--;
            if (selectedIndex < 0)
                selectedIndex = options.length - 1;
            updateSelection();
        }

        public void navigateDown() {
            selectedIndex++;
            if (selectedIndex >= options.length)
                selectedIndex = 0;
            updateSelection();
        }

        private void updateSelection() {
            for (int i = 0; i < options.length; i++) {
                options[i].setColor(i == selectedIndex ? Color.YELLOW : Color.WHITE);
                options[i].setText((i == selectedIndex ? "> " : "  ") + getOptionName(i));
            }
        }

        private String getOptionName(int index) {
            switch (index) {
                case 0:
                    return "ATTACK";
                case 1:
                    return "CAST";
                case 2:
                    return "ROLL";
                case 3:
                    Item active = player.getInventory().getQuickSlots()[0];
                    String activeName = active != null ? active.getDisplayName() : "Empty";
                    return "USE [1-6] (" + activeName + ")";
                case 4:
                    return "BLOCK";
                case 5:
                    return "COOK";
                default:
                    return "???";
            }
        }

        public int getSelectedIndex() {
            return selectedIndex;
        }
    }

    // --- NEW: Helper method to support GameScreen calls ---
    public void addMessage(String message) {
        if (eventManager != null) {
            eventManager.addEvent(new GameEvent(message, 2f));
        }
    }

    @Override
    public void dispose() {
        stage.dispose();
        font.dispose();
        directionFont.dispose();
        bottomBarBg.dispose();
        if (whiteTexture != null)
            whiteTexture.dispose();
        shapeRenderer.dispose();
        // messageBackgroundTexture.dispose(); // Removed, as we use bottomBarBg now or
        // separate logic?
        // We removed messageBackgroundTexture in constructor, so no need to dispose it.
        if (debugFont != null)
            debugFont.dispose(); // Clean up debug font
    }

    // --- NEW: Tarmin's Hunger UI ---
    private void drawBridgeIntegrityBar() {
        if (com.bpm.minotaur.managers.DoomManager.getInstance().getBridgeIntegrity() <= 0)
            return;

        float integrity = com.bpm.minotaur.managers.DoomManager.getInstance().getBridgeIntegrity();
        float maxW = 400;
        float h = 20;
        float x = (viewport.getWorldWidth() - maxW) / 2;
        float y = viewport.getWorldHeight() - 40;

        // 1. Draw Bar
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Background
        shapeRenderer.setColor(Color.DARK_GRAY);
        shapeRenderer.rect(x, y, maxW, h);

        // Foreground (Purple/Magical)
        shapeRenderer.setColor(Color.PURPLE);
        shapeRenderer.rect(x, y, maxW * (integrity / 100f), h);

        shapeRenderer.end();

        // Border
        Gdx.gl.glLineWidth(2);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.rect(x, y, maxW, h);
        shapeRenderer.end();
        Gdx.gl.glLineWidth(1);

        // 2. Draw Text
        spriteBatch.setProjectionMatrix(stage.getCamera().combined);
        spriteBatch.begin();
        String text = String.format("Bridge Integrity: %.0f%%", integrity);
        glyphLayout.setText(font, text);

        // Center text on bar
        float textX = x + (maxW - glyphLayout.width) / 2;
        float textY = y + (h + glyphLayout.height) / 2 - 2; // -2 for visual alignment

        font.setColor(Color.WHITE);
        font.draw(spriteBatch, text, textX, textY);
        spriteBatch.end();
        // --- Draw Attack Indicators ---
        if (!attackIndicators.isEmpty()) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

            java.util.Iterator<AttackIndicator> it = attackIndicators.iterator();
            while (it.hasNext()) {
                AttackIndicator ind = it.next();
                ind.duration -= Gdx.graphics.getDeltaTime();

                if (ind.duration <= 0) {
                    it.remove();
                    continue;
                }

                // Fade out
                float alpha = MathUtils.clamp(ind.duration, 0f, 1f);
                shapeRenderer.setColor(1f, 0f, 0f, alpha);

                // Draw Arrow based on Screen Edges
                float cx = 1920f / 2f;
                float cy = 1080f / 2f;
                float offset = 300f; // Distance from center
                float size = 50f;

                // Transform world direction to screen relative direction based on player facing
                // Only if we want relative (Left/Right) or absolute (North/South)?
                // Request says "from a direction they are not facing", implying relative.
                // But Hud is 2D.
                // Let's assume Screen Top = Direction player is facing?
                // Or Screen Top = North?

                // If 3D view: Top is "Forward".
                // We need relative direction.

                Direction playerFacing = player.getFacing();
                // We need to find rotation Difference.

                // Map Directions to angles (North=0, East=90...)
                // N=0, E=270, S=180, W=90 (GDX rotation? No, let's just use indices)
                // Let N=0, E=1, S=2, W=3
                int pIndex = getDirIndex(playerFacing);
                int aIndex = getDirIndex(ind.direction);

                // Diff: Forward (0), Right (1), Back (2), Left (3)
                int diff = (aIndex - pIndex + 4) % 4;

                // 0=Front (Shouldn't happen for flank usually, but maybe), 1=Right, 2=Back,
                // 3=Left

                float drawX = cx;
                float drawY = cy;

                // Coordinates for Triangle
                float x1 = 0, y1 = 0, x2 = 0, y2 = 0, x3 = 0, y3 = 0;

                switch (diff) {
                    case 0: // Front (Top)
                        drawY += offset;
                        x1 = drawX;
                        y1 = drawY;
                        x2 = drawX - size / 2;
                        y2 = drawY + size;
                        x3 = drawX + size / 2;
                        y3 = drawY + size;
                        break;
                    case 1: // Right
                        drawX += offset;
                        x1 = drawX;
                        y1 = drawY;
                        x2 = drawX + size;
                        y2 = drawY + size / 2;
                        x3 = drawX + size;
                        y3 = drawY - size / 2;
                        break;
                    case 2: // Back (Bottom)
                        drawY -= offset;
                        x1 = drawX;
                        y1 = drawY;
                        x2 = drawX - size / 2;
                        y2 = drawY - size;
                        x3 = drawX + size / 2;
                        y3 = drawY - size;
                        break;
                    case 3: // Left
                        drawX -= offset;
                        x1 = drawX;
                        y1 = drawY;
                        x2 = drawX - size;
                        y2 = drawY + size / 2;
                        x3 = drawX - size;
                        y3 = drawY - size / 2;
                        break;
                }

                shapeRenderer.triangle(x1, y1, x2, y2, x3, y3);
            }
            shapeRenderer.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    private int getDirIndex(Direction d) {
        switch (d) {
            case NORTH:
                return 0;
            case EAST:
                return 1;
            case SOUTH:
                return 2;
            case WEST:
                return 3;
        }
        return 0;
    }

    public EncounterWindow getEncounterWindow() {
        return encounterWindow;
    }

    // --- NEW: Confusion Scrambling Helper ---
    private String checkScramble(String text) {
        if (text == null)
            return null;
        if (player != null && player.getStatusManager().hasEffect(StatusEffectType.CONFUSED)) {
            char[] chars = text.toCharArray();
            if (chars.length > 1) {
                // Swap random characters occasionally
                if (MathUtils.randomBoolean(0.5f)) {
                    int i = MathUtils.random(chars.length - 1);
                    int j = MathUtils.random(chars.length - 1);
                    char temp = chars[i];
                    chars[i] = chars[j];
                    chars[j] = temp;
                }
                // Randomly offset a character code
                if (MathUtils.randomBoolean(0.3f)) {
                    int k = MathUtils.random(chars.length - 1);
                    chars[k] = (char) (chars[k] + MathUtils.random(-2, 2));
                }
            }
            return new String(chars);
        }
        return text;
    }
}
