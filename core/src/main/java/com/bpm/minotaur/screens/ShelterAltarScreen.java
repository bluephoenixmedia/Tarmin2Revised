package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ShelterChest;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.managers.DivinityManager;
import com.bpm.minotaur.rendering.HudSkin;

import java.util.List;

/**
 * Scene2D interface for the Shelter Altar:
 * 1. Shelter Expansion: spend Divinities to build stations (Bed, Chest, Fire Pot, Crafting Bench, Lantern).
 * 2. Blessings: spend Divinities on the three legacy permanent upgrade trees (Provisions, Repertoire, Monument).
 * 3. Sacrifice Offerings: burn excess dungeon loot at the altar to harvest Divinities.
 * 4. Commune: restore 100% HP/MP and save the expedition without a bed (requires delve to rearm).
 */
public class ShelterAltarScreen extends BaseScreen {

    public enum Tab { EXPANSION, BLESSINGS, SACRIFICE }

    private final GameScreen parentScreen;
    private final Player player;
    private final HudSkin hudSkin;

    private Stage stage;
    private Label divinityLabel;
    private Label statusLabel;
    private TextButton communeBtn;

    private Tab activeTab = Tab.EXPANSION;
    private Table bodyContainer;
    private TextButton tabExpansionBtn;
    private TextButton tabBlessingsBtn;
    private TextButton tabSacrificeBtn;

    // Blessings controls
    private Label provisionsTierLabel;
    private Label repertoireTierLabel;
    private Label monumentTierLabel;
    private TextButton provisionsBtn;
    private TextButton repertoireBtn;
    private TextButton monumentBtn;

    public ShelterAltarScreen(Tarmin2 game, GameScreen parentScreen, Player player) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.hudSkin = new HudSkin();
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        buildUI();
    }

    private void buildUI() {
        stage.clear();

        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(40, 100, 40, 100);

        // --- HEADER ---
        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(16, 24, 16, 24);
        Label title = new Label("THE ANCIENT STONE ALTAR", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        title.setFontScale(1.3f);
        header.add(title).center().row();
        Label subtitle = new Label("Banked Divinities survive death -- commune for respite, expand the shelter, or sacrifice offerings",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        header.add(subtitle).center().padTop(4).row();

        Table topActions = new Table();
        topActions.padTop(10);
        divinityLabel = new Label("", new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT));
        topActions.add(divinityLabel).padRight(30);

        communeBtn = createActionButton("COMMUNE (REST & SAVE)");
        communeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                performCommune();
            }
        });
        topActions.add(communeBtn).width(360).height(44);
        header.add(topActions).center().row();

        root.add(header).fillX().padBottom(16).row();

        // --- TAB BAR ---
        Table tabBar = new Table();
        tabExpansionBtn = createTabButton("1. SHELTER EXPANSION", Tab.EXPANSION);
        tabBlessingsBtn = createTabButton("2. BLESSINGS & REPERTOIRE", Tab.BLESSINGS);
        tabSacrificeBtn = createTabButton("3. SACRIFICE OFFERINGS", Tab.SACRIFICE);

        tabBar.add(tabExpansionBtn).width(340).height(48).padRight(12);
        tabBar.add(tabBlessingsBtn).width(340).height(48).padRight(12);
        tabBar.add(tabSacrificeBtn).width(340).height(48);
        root.add(tabBar).left().padBottom(16).row();

        // --- BODY CONTAINER ---
        bodyContainer = new Table();
        root.add(bodyContainer).expand().fill().padBottom(16).row();

        // --- FOOTER ---
        Table footer = new Table();
        footer.setBackground(hudSkin.getPanelBg());
        footer.pad(12, 20, 12, 20);
        Label keyHints = new Label("[ESC] LEAVE ALTAR", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        footer.add(keyHints).left().expandX();
        statusLabel = new Label("Approach and commune with the ancient stone.",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_FOOD_GREEN));
        footer.add(statusLabel).right();
        root.add(footer).fillX();

        stage.addActor(root);

        refresh();
    }

    private TextButton createTabButton(String label, Tab tab) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = hudSkin.getFontHeader();
        TextButton btn = new TextButton(label, style);
        btn.getLabel().setFontScale(0.75f);
        btn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                activeTab = tab;
                refresh();
            }
        });
        return btn;
    }

    private void updateTabStyles() {
        setTabSelected(tabExpansionBtn, activeTab == Tab.EXPANSION);
        setTabSelected(tabBlessingsBtn, activeTab == Tab.BLESSINGS);
        setTabSelected(tabSacrificeBtn, activeTab == Tab.SACRIFICE);
    }

    private void setTabSelected(TextButton btn, boolean selected) {
        TextButton.TextButtonStyle style = btn.getStyle();
        if (selected) {
            style.up = hudSkin.getPrimaryButtonDown();
            style.down = hudSkin.getPrimaryButtonDown();
            style.over = hudSkin.getPrimaryButtonDown();
            style.fontColor = HudSkin.COL_GOLD_BRIGHT;
        } else {
            style.up = hudSkin.getPanelBg();
            style.down = hudSkin.getPanelBg();
            style.over = hudSkin.getSlotRecessed();
            style.fontColor = HudSkin.COL_GOLD_MUTED;
        }
    }

    private void refresh() {
        ShelterAltar altar = ShelterAltar.getInstance();
        int divinities = DivinityManager.getInstance().getCurrentDivinities();
        divinityLabel.setText(DivinityManager.DIVINITY_NAME + ": " + divinities);

        // Commune Button status
        if (altar.canCommune()) {
            communeBtn.setText("COMMUNE (RESTORE HP/MP & SAVE)");
            setButtonEnabled(communeBtn, true);
        } else {
            communeBtn.setText("DORMANT (REQUIRES 2-CHUNK DELVE)");
            setButtonEnabled(communeBtn, false);
        }

        updateTabStyles();

        bodyContainer.clear();
        switch (activeTab) {
            case EXPANSION:
                buildExpansionTab(altar, divinities);
                break;
            case BLESSINGS:
                buildBlessingsTab(altar, divinities);
                break;
            case SACRIFICE:
                buildSacrificeTab();
                break;
        }
    }

    // =========================================================================
    // TAB 1: SHELTER EXPANSION
    // =========================================================================

    private void buildExpansionTab(ShelterAltar altar, int divinities) {
        Table grid = new Table();
        grid.top().left();

        for (ShelterAltar.Station station : ShelterAltar.Station.values()) {
            Table card = new Table();
            card.setBackground(hudSkin.getDoubleBorderPanel());
            card.top().left().pad(16);

            Label nameLbl = new Label(station.getDisplayName().toUpperCase(),
                    new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
            card.add(nameLbl).left().padBottom(6).row();

            Label descLbl = new Label(station.getDescription(),
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            descLbl.setWrap(true);
            card.add(descLbl).width(480).left().padBottom(12).row();

            boolean unlocked = altar.hasStation(station);
            TextButton buildBtn;
            if (unlocked) {
                buildBtn = createActionButton("[ CONSTRUCTED ]");
                setButtonEnabled(buildBtn, false);
            } else {
                buildBtn = createActionButton("BUILD (" + station.getCost() + " Div)");
                setButtonEnabled(buildBtn, divinities >= station.getCost());
                buildBtn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        purchaseStation(station);
                    }
                });
            }
            card.add(buildBtn).width(260).height(44).left().row();

            grid.add(card).width(520).pad(10);
            if (grid.getChildren().size % 3 == 0) {
                grid.row();
            }
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFadeScrollBars(false);
        bodyContainer.add(scroll).expand().fill();
    }

    private void purchaseStation(ShelterAltar.Station station) {
        ShelterAltar altar = ShelterAltar.getInstance();
        boolean success = altar.unlockStation(station, parentScreen.getMaze(), game.getItemDataManager(), game.getAssetManager());
        if (success) {
            statusLabel.setText(station.getDisplayName() + " materialized into the shelter!");
            if (parentScreen.getSoundManager() != null) {
                parentScreen.getSoundManager().playDimensionalWarpSound();
            }
            refresh();
        } else {
            statusLabel.setText("Not enough Divinities to construct " + station.getDisplayName() + ".");
        }
    }

    // =========================================================================
    // TAB 2: BLESSINGS & REPERTOIRE
    // =========================================================================

    private void buildBlessingsTab(ShelterAltar altar, int divinities) {
        Table body = new Table();

        Table provisionsCard = buildTreeCard("PROVISIONS",
                "Extra bread, waterskins, and bandages granted at the start of every expedition.");
        provisionsTierLabel = new Label("", new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_ANTIQUE));
        provisionsCard.add(provisionsTierLabel).left().padTop(10).row();
        provisionsBtn = createActionButton("UPGRADE PROVISIONS");
        provisionsBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                purchaseTree(ShelterAltar.Tree.PROVISIONS);
            }
        });
        provisionsCard.add(provisionsBtn).width(320).height(52).padTop(16).row();
        body.add(provisionsCard).width(380).expandY().fillY().padRight(20);

        Table repertoireCard = buildTreeCard("REPERTOIRE",
                "Unseals advanced weaponry -- Composite Bows, Heavy Crossbows, Warhammers, and Morning Stars -- directly into the Stash Chest.");
        repertoireTierLabel = new Label("", new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_ANTIQUE));
        repertoireCard.add(repertoireTierLabel).left().padTop(10).row();
        repertoireBtn = createActionButton("UPGRADE REPERTOIRE");
        repertoireBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                purchaseTree(ShelterAltar.Tree.REPERTOIRE);
            }
        });
        repertoireCard.add(repertoireBtn).width(320).height(52).padTop(16).row();
        body.add(repertoireCard).width(380).expandY().fillY().padRight(20);

        Table monumentCard = buildTreeCard("MONUMENT",
                "Raises the frequency of statue encounter events from a 15% baseline up to 35% per chunk.");
        monumentTierLabel = new Label("", new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_ANTIQUE));
        monumentCard.add(monumentTierLabel).left().padTop(10).row();
        monumentBtn = createActionButton("UPGRADE MONUMENT");
        monumentBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                purchaseTree(ShelterAltar.Tree.MONUMENT);
            }
        });
        monumentCard.add(monumentBtn).width(320).height(52).padTop(16).row();
        body.add(monumentCard).width(380).expandY().fillY();

        refreshTree(altar, ShelterAltar.Tree.PROVISIONS, provisionsTierLabel, provisionsBtn, divinities);
        refreshTree(altar, ShelterAltar.Tree.REPERTOIRE, repertoireTierLabel, repertoireBtn, divinities);
        refreshTree(altar, ShelterAltar.Tree.MONUMENT, monumentTierLabel, monumentBtn, divinities);

        bodyContainer.add(body).expand().fill();
    }

    private Table buildTreeCard(String name, String description) {
        Table card = new Table();
        card.setBackground(hudSkin.getDoubleBorderPanel());
        card.top().left();
        card.pad(20);

        Label nameLbl = new Label(name, new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        card.add(nameLbl).left().padBottom(10).row();

        Label descLbl = new Label(description, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        descLbl.setWrap(true);
        card.add(descLbl).width(320).left().row();

        return card;
    }

    private void refreshTree(ShelterAltar altar, ShelterAltar.Tree tree, Label tierLabel, TextButton btn, int divinities) {
        int tier = altar.getTier(tree);
        if (altar.isMaxed(tree)) {
            tierLabel.setText(String.format("Tier %d / %d -- MAXED", tier, ShelterAltar.MAX_TIER));
            setButtonEnabled(btn, false);
        } else {
            int cost = altar.getNextUpgradeCost(tree);
            tierLabel.setText(String.format("Tier %d / %d -- Next: %d %s", tier, ShelterAltar.MAX_TIER, cost,
                    DivinityManager.DIVINITY_NAME));
            setButtonEnabled(btn, divinities >= cost);
        }
    }

    private void purchaseTree(ShelterAltar.Tree tree) {
        ShelterAltar altar = ShelterAltar.getInstance();
        if (!altar.purchaseUpgrade(tree)) {
            statusLabel.setText("Not enough Divinities for that upgrade.");
            refresh();
            return;
        }

        String message;
        switch (tree) {
            case PROVISIONS:
                message = "Provisions upgraded! Your next expedition begins with extra supplies.";
                break;
            case REPERTOIRE:
                message = grantRepertoireWeapon(altar.getRepertoireTier());
                break;
            case MONUMENT:
                message = "Monument upgraded! Statue encounters now appear "
                        + Math.round(altar.getStatueEventFrequency() * 100) + "% of the time per chunk.";
                break;
            default:
                message = "Upgrade purchased.";
        }
        statusLabel.setText(message);
        refresh();
    }

    private String grantRepertoireWeapon(int newTier) {
        Item.ItemType weaponType;
        String weaponName;
        switch (newTier) {
            case 1:
                weaponType = Item.ItemType.WARHAMMER;
                weaponName = "a Warhammer";
                break;
            case 2:
                weaponType = Item.ItemType.MORNING_STAR;
                weaponName = "a Morning Star";
                break;
            case 3:
            default:
                weaponType = Item.ItemType.BOW_COMPOSITE_LONG;
                weaponName = "a Composite Bow and a Heavy Crossbow";
                break;
        }

        ShelterChest chest = ShelterChest.getInstance();
        Item weapon = game.getItemDataManager().createItem(weaponType, 0, 0, ItemColor.TAN, game.getAssetManager());
        if (weapon != null) {
            chest.addItem(weapon);
        }
        if (newTier >= 3) {
            Item crossbow = game.getItemDataManager().createItem(Item.ItemType.CROSSBOW_HEAVY, 0, 0, ItemColor.TAN,
                    game.getAssetManager());
            if (crossbow != null) {
                chest.addItem(crossbow);
            }
        }
        chest.save();
        return "Repertoire upgraded! " + weaponName + " now awaits you in the Stash Chest.";
    }

    // =========================================================================
    // TAB 3: SACRIFICE OFFERINGS
    // =========================================================================

    private void buildSacrificeTab() {
        Table list = new Table();
        list.top().left();

        List<Item> backpack = (player.getInventory() != null) ? player.getInventory().getMainInventory() : java.util.Collections.emptyList();
        if (backpack.isEmpty()) {
            Label emptyLbl = new Label("Your pack contains no spoils to sacrifice. Delve deeper into the labyrinth and return with treasures.",
                    new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_MUTED));
            list.add(emptyLbl).pad(40);
        } else {
            for (Item item : backpack) {
                if (item == null) continue;
                int yield = ShelterAltar.getSacrificeValue(item);

                Table row = new Table();
                row.setBackground(hudSkin.getSlotRecessed());
                row.pad(10, 16, 10, 16);

                Label nameLbl = new Label(item.getFriendlyName(), new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT));
                row.add(nameLbl).width(400).left();

                String rarity = (item.getItemColor() != null) ? item.getItemColor().name() : "COMMON";
                Label rarityLbl = new Label("[" + rarity + "]", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE));
                row.add(rarityLbl).width(160).left();

                Label valLbl = new Label("+" + yield + " Divinities", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_FOOD_GREEN));
                row.add(valLbl).width(200).left();

                TextButton sacBtn = createActionButton("SACRIFICE");
                setButtonEnabled(sacBtn, true);
                sacBtn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        performSacrifice(item, yield);
                    }
                });
                row.add(sacBtn).width(160).height(40).right();

                list.add(row).fillX().padBottom(8).row();
            }
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFadeScrollBars(false);
        bodyContainer.add(scroll).expand().fill();
    }

    private void performSacrifice(Item item, int yield) {
        ShelterAltar altar = ShelterAltar.getInstance();
        String name = item.getFriendlyName();
        boolean ok = altar.sacrificeItem(player, item);
        if (ok) {
            statusLabel.setText("The altar consumes " + name + " in holy flame, releasing " + yield + " Divinities!");
            if (parentScreen.getSoundManager() != null) {
                parentScreen.getSoundManager().playWeaponImpact(true);
            }
            refresh();
        }
    }

    private void performCommune() {
        ShelterAltar altar = ShelterAltar.getInstance();
        if (altar.commune(player, parentScreen.getWorldManager())) {
            statusLabel.setText("You commune with the ancient stone. HP and MP fully restored. Expedition saved.");
            if (parentScreen.getSoundManager() != null) {
                parentScreen.getSoundManager().playDoorOpenSound();
            }
            refresh();
        } else {
            statusLabel.setText("The altar remains dormant. Delve at least 2 chunks away or into Strata 2 to rearm.");
        }
    }

    // =========================================================================
    // BUTTON HELPERS
    // =========================================================================

    private TextButton createActionButton(String text) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = hudSkin.getFontHeader();
        style.disabled = hudSkin.getSlotRecessed();
        TextButton btn = new TextButton(text, style);
        btn.getLabel().setFontScale(0.72f);
        setButtonEnabled(btn, false);
        return btn;
    }

    private void setButtonEnabled(TextButton btn, boolean enabled) {
        TextButton.TextButtonStyle style = btn.getStyle();
        if (enabled) {
            style.up = hudSkin.getPrimaryButtonUp();
            style.down = hudSkin.getPrimaryButtonDown();
            style.over = hudSkin.getPrimaryButtonDown();
            style.fontColor = HudSkin.COL_TEXT_ON_GOLD;
            style.overFontColor = HudSkin.COL_TEXT_ON_GOLD;
        } else {
            style.up = hudSkin.getSlotRecessed();
            style.down = hudSkin.getSlotRecessed();
            style.over = hudSkin.getSlotRecessed();
            style.fontColor = HudSkin.COL_GOLD_MUTED;
            style.overFontColor = HudSkin.COL_GOLD_MUTED;
        }
        btn.setDisabled(!enabled);
    }

    private void closeAltar() {
        game.setScreen(parentScreen);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            closeAltar();
            return true;
        }
        return false;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(10f / 255f, 8f / 255f, 6f / 255f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

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
        if (hudSkin != null) hudSkin.dispose();
    }
}
