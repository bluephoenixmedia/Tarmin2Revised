package com.bpm.minotaur.screens;

import com.bpm.minotaur.ui.UiStyles;
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
import com.bpm.minotaur.ui.HoldButton;
import com.bpm.minotaur.ui.KeyHintLegend;
import com.bpm.minotaur.ui.UiContexts;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiNames;
import com.bpm.minotaur.ui.UiTabs;
import com.bpm.minotaur.ui.UiTheme;

import java.util.List;

/**
 * Scene2D interface for the Shelter Altar:
 * 1. Shelter Expansion: spend Divinities to build stations (Bed, Chest, Fire Pot, Crafting Bench, Lantern).
 * 2. Blessings: spend Divinities on the three legacy permanent upgrade trees (Provisions, Repertoire, Monument).
 * 3. Sacrifice Offerings: burn excess dungeon loot at the altar to harvest Divinities.
 * 4. Commune: restore 100% HP/MP and save the expedition without a bed (requires delve to rearm).
 */
public class ShelterAltarScreen extends BaseScreen {

    public enum Tab { EXPANSION, BLESSINGS, SACRIFICE, ASCENSION }

    /** One column of the three-up station and ascension grids, inside the screen's padding. */
    private static final float CARD_W = 520f;

    /** This screen's entry on the input-context stack (SPEC 5.6). */
    private static final String CONTEXT = "ALTAR";

    private final GameScreen parentScreen;
    private final Player player;
    private final HudSkin hudSkin;

    private Stage stage;
    private Label divinityLabel;
    private Label statusLabel;
    private TextButton communeBtn;

    private Tab activeTab = Tab.EXPANSION;
    private Table bodyContainer;
    private UiTabs tabs;

    // Blessings controls
    private Label provisionsTierLabel;
    private Label repertoireTierLabel;
    private Label monumentTierLabel;
    private Label arcaneTierLabel;
    private TextButton provisionsBtn;
    private TextButton repertoireBtn;
    private TextButton monumentBtn;
    private TextButton arcaneBtn;

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
        UiContexts.push(CONTEXT, UiContexts.Kind.PANEL);

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
        header.add(title).center().row();
        Label subtitle = new Label("Banked Divinities survive death -- commune for respite, expand the shelter, or sacrifice offerings",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        header.add(subtitle).center().padTop(4).row();

        // ALTAR-1: the balance and the Commune button shared a row in which the button was
        // pinned to 360 units. Its label needs more than that, and a TextButton does not clip,
        // so the label ran out of both ends of the button -- over the balance on its left, and
        // past its own border on its right. The button now sizes to its label and the two cells
        // are separated by real padding instead of by luck.
        Table topActions = new Table();
        topActions.padTop(10);
        divinityLabel = UiLabels.ellipsized("", new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT));
        topActions.add(divinityLabel).left().padRight(UiTheme.PAD_XXL);

        communeBtn = createActionButton("COMMUNE (REST & SAVE)");
        communeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                performCommune();
            }
        });
        topActions.add(communeBtn).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).right();
        header.add(topActions).center().row();

        root.add(header).fillX().padBottom(16).row();

        // --- TAB BAR (ALTAR-2 / RC5) ---
        // Four labels, four hard-coded widths, and the widest label was wider than its cell --
        // so "SHELTER EXPANSION" overflowed into the next tab and rendered as
        // "SHELTER EXPANSI|2.", eating its own name and picking up the neighbour's number.
        // UiTabs gives the number its own keycap cell and sizes each tab to its label.
        tabs = new UiTabs(hudSkin)
                .addTab(1, "Shelter Expansion")
                .addTab(2, "Blessings")
                .addTab(3, "Sacrifice")
                .addTab(4, "Altar of Ascension");
        tabs.onSelect(index -> {
            activeTab = Tab.values()[index];
            refresh();
        });
        tabs.setSelectedSilently(activeTab.ordinal());
        root.add(tabs).left().padBottom(16).row();

        // --- BODY CONTAINER ---
        bodyContainer = new Table();
        root.add(bodyContainer).expand().fill().padBottom(16).row();

        // --- FOOTER ---
        Table footer = new Table();
        footer.setBackground(hudSkin.getPanelBg());
        footer.pad(12, 20, 12, 20);
        statusLabel = UiLabels.ellipsized("Approach and commune with the ancient stone.",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_FOOD_GREEN));
        footer.add(statusLabel).left().expandX();
        footer.add(new KeyHintLegend(hudSkin)
                .hint("1-4", "Tab")
                .escapeHint("Leave altar")).right();
        root.add(footer).fillX();

        stage.addActor(root);

        refresh();
    }

    private void refresh() {
        ShelterAltar altar = ShelterAltar.getInstance();
        int divinities = DivinityManager.getInstance().getCurrentDivinities();
        // ALTAR-8: Crests of Valor were buried in a subheading on one tab, and the game said
        // "Div", "Divinities" and "+1 Divinities" in three places. Both balances belong in the
        // header, on every tab, and neither is wrong at one.
        divinityLabel.setText(UiNames.plural(divinities, "Divinity", "Divinities")
                + "   |   " + UiNames.plural(ShelterAltar.getInstance().getCrestsOfValor(), "Crest of Valor", "Crests of Valor"));

        // Commune Button status
        if (altar.canCommune()) {
            communeBtn.setText("COMMUNE (RESTORE HP/MP & SAVE)");
            setButtonEnabled(communeBtn, true);
        } else {
            communeBtn.setText("DORMANT (REQUIRES 2-CHUNK DELVE)");
            setButtonEnabled(communeBtn, false);
        }


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
            case ASCENSION:
                buildAscensionTab(altar);
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
            // A station the player has not yet reached the depth for stays out
            // of the list entirely, so what the Altar shows is always something
            // they could actually buy rather than a row of locked teases.
            if (!station.isRevealed()) continue;

            Table card = new Table();
            card.setBackground(hudSkin.getDoubleBorderPanel());
            card.top().left().pad(16);

            Label nameLbl = new Label(station.getDisplayName().toUpperCase(),
                    new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
            card.add(nameLbl).left().padBottom(6).row();

            Label descLbl = UiLabels.wrapping(station.getDescription(),
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            card.add(descLbl).width(CARD_W - 32f).left().padBottom(12).row();

            boolean unlocked = altar.hasStation(station);
            if (unlocked) {
                // ALTAR-11: "[ CONSTRUCTED ]" was a disabled button, which reads as something
                // you failed to press. A built station is a state, so it gets a badge.
                card.add(UiLabels.of("+ Constructed",
                        new Label.LabelStyle(hudSkin.getFontMain(), UiTheme.SUCCESS))).left().row();
            } else {
                int cost = station.getCost();
                boolean afford = divinities >= cost;
                TextButton buildBtn = createActionButton("BUILD");
                setButtonEnabled(buildBtn, afford);
                buildBtn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        purchaseStation(station);
                    }
                });
                card.add(buildBtn).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).left().row();
                // ALTAR-5: every Build button used to sit live at zero Divinities, so pressing
                // one taught the player nothing. The cost line says what it takes and, when
                // they cannot pay, what they are short.
                card.add(UiLabels.of(afford
                                ? UiNames.plural(cost, "Divinity", "Divinities")
                                : "Need " + cost + " - have " + divinities,
                        new Label.LabelStyle(hudSkin.getFontSmall(), afford ? UiTheme.TEXT_DIM : UiTheme.DANGER)))
                        .left().padTop(UiTheme.PAD_XS).row();
            }

            grid.add(card).width(CARD_W).top().pad(10);
            if (grid.getChildren().size % 3 == 0) {
                grid.row();
            }
        }

        ScrollPane scroll = new ScrollPane(grid, UiStyles.scrollPane(hudSkin));
        scroll.setFadeScrollBars(false);
        bodyContainer.add(scroll).expand().fill();
    }

    private void purchaseStation(ShelterAltar.Station station) {
        ShelterAltar altar = ShelterAltar.getInstance();
        boolean success = altar.unlockStation(station, parentScreen.getMaze(), game.getItemDataManager(), game.getAssetManager());
        if (success) {
            StringBuilder message = new StringBuilder(station.getDisplayName() + " materialized into the shelter!");

            // The portable counterpart arrives with the station, not on the player's next
            // death -- otherwise buying the bench would mean having to die to collect the
            // field toolkit it teaches.
            com.bpm.minotaur.gamedata.progression.FieldKitGrant.Result kits =
                    com.bpm.minotaur.gamedata.progression.FieldKitGrant.grantOwed(
                            altar.getUnlockedStations(),
                            parentScreen.getPlayer().getInventory(),
                            game.getItemDataManager(),
                            game.getAssetManager());

            for (com.bpm.minotaur.gamedata.item.Item kit : kits.getGranted()) {
                message.append(" ").append(kit.getDisplayName()).append(" packed for the field.");
            }
            // The divinities are already spent, so a pack too full to take the kit has to
            // be said here -- the player is looking at this label, not the world HUD.
            for (com.bpm.minotaur.gamedata.item.Item kit : kits.getNoRoom()) {
                message.append(" Your pack is too full for the ").append(kit.getDisplayName())
                        .append(" -- make room and it will be issued on your next expedition.");
            }

            statusLabel.setText(message.toString());
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
        provisionsCard.add(provisionsBtn).growX().height(UiTheme.BUTTON_H).padTop(16).row();
        body.add(provisionsCard).grow().padRight(20);

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
        repertoireCard.add(repertoireBtn).growX().height(UiTheme.BUTTON_H).padTop(16).row();
        body.add(repertoireCard).grow().padRight(20);

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
        monumentCard.add(monumentBtn).growX().height(UiTheme.BUTTON_H).padTop(16).row();
        body.add(monumentCard).grow().padRight(20);

        Table arcaneCard = buildTreeCard("ARCANE ATTUNEMENT",
                "Unseals higher spell circles into scroll loot and widens every Tome Choice: "
                        + "4 spells to choose from, then a reroll, then 5 spells and level 8 magic from the Tome of Tarmin.");
        arcaneTierLabel = new Label("", new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_ANTIQUE));
        arcaneCard.add(arcaneTierLabel).left().padTop(10).row();
        arcaneBtn = createActionButton("UPGRADE ATTUNEMENT");
        arcaneBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                purchaseTree(ShelterAltar.Tree.ARCANE_ATTUNEMENT);
            }
        });
        arcaneCard.add(arcaneBtn).growX().height(UiTheme.BUTTON_H).padTop(16).row();
        body.add(arcaneCard).grow();

        refreshTree(altar, ShelterAltar.Tree.PROVISIONS, provisionsTierLabel, provisionsBtn, divinities);
        refreshTree(altar, ShelterAltar.Tree.REPERTOIRE, repertoireTierLabel, repertoireBtn, divinities);
        refreshTree(altar, ShelterAltar.Tree.MONUMENT, monumentTierLabel, monumentBtn, divinities);
        refreshTree(altar, ShelterAltar.Tree.ARCANE_ATTUNEMENT, arcaneTierLabel, arcaneBtn, divinities);

        bodyContainer.add(body).expand().fill();
    }

    private Table buildTreeCard(String name, String description) {
        Table card = new Table();
        card.setBackground(hudSkin.getDoubleBorderPanel());
        card.top().left();
        card.pad(20);

        Label nameLbl = new Label(name, new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        card.add(nameLbl).left().padBottom(10).row();

        // ALTAR-3: the description was pinned to 320 inside a card the caller sized to 380,
        // and the upgrade button below it was pinned to 320 while its label needed more. Both
        // now take the width the card actually has.
        Label descLbl = UiLabels.wrapping(description, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        card.add(descLbl).growX().left().row();

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
            case ARCANE_ATTUNEMENT:
                com.bpm.minotaur.gamedata.spells.TomeChoice.Perks perks = altar.getTomeChoicePerks();
                message = "Attunement deepened! Tome Choices now show " + perks.options() + " spells"
                        + (perks.rerolls() > 0 ? " with " + perks.rerolls() + " reroll" : "")
                        + (perks.tarminMaxLevel() > com.bpm.minotaur.gamedata.spells.Tome.TARMIN.getMaxSpellLevel() ? ", and the Tome of Tarmin reaches Level " + perks.tarminMaxLevel() : "")
                        + "!";
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

                Label nameLbl = UiLabels.ellipsized(
                        com.bpm.minotaur.gamedata.item.ItemName.natural(item.getFriendlyName()),
                        new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT));
                row.add(nameLbl).width(480).left();

                // ALTAR-7/ALTAR-9: this column printed the ItemColor constant in brackets --
                // "[TAN]" -- which is the colour of the sprite, not a rarity, and told the
                // player nothing about what they were about to burn. ItemColor already carries
                // the power tier as a word; that is the thing worth showing.
                ItemColor color = item.getItemColor();
                String rarity = (color != null) ? color.getPowerLevel() : "Regular";
                Label rarityLbl = UiLabels.ellipsized(rarity,
                        new Label.LabelStyle(hudSkin.getFontSmall(),
                                color != null ? color.getColor() : HudSkin.COL_GOLD_ANTIQUE));
                row.add(rarityLbl).width(200).left();

                Label valLbl = UiLabels.ellipsized("+" + UiNames.plural(yield, "Divinity", "Divinities"),
                        new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_FOOD_GREEN));
                row.add(valLbl).width(260).left();

                // ALTAR-7: burning real gear used to take one click, alongside ten other gold
                // buttons that burned junk. Gear is held; junk is clicked.
                if (isGear(item)) {
                    HoldButton sacBtn = new HoldButton("HOLD TO SACRIFICE", hudSkin,
                            () -> performSacrifice(item, yield));
                    row.add(sacBtn).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).right().expandX();
                } else {
                    TextButton sacBtn = createActionButton("SACRIFICE");
                    setButtonEnabled(sacBtn, true);
                    sacBtn.addListener(new ClickListener() {
                        @Override
                        public void clicked(InputEvent event, float x, float y) {
                            performSacrifice(item, yield);
                        }
                    });
                    row.add(sacBtn).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).right().expandX();
                }

                list.add(row).fillX().padBottom(8).row();
            }
        }

        ScrollPane scroll = new ScrollPane(list, UiStyles.scrollPane(hudSkin));
        scroll.setFadeScrollBars(false);
        bodyContainer.add(scroll).expand().fill();
    }

    /**
     * Whether an item is equipment rather than spoil.
     *
     * <p>Only the answer to "does destroying this hurt" -- it decides between a click and a
     * hold, nothing else.
     */
    private static boolean isGear(Item item) {
        if (item == null || item.getCategory() == null) {
            return false;
        }
        switch (item.getCategory()) {
            case WAR_WEAPON:
            case SPIRITUAL_WEAPON:
            case ARMOR:
            case RING:
            case AMULET:
                return true;
            default:
                return false;
        }
    }

    private void performSacrifice(Item item, int yield) {
        ShelterAltar altar = ShelterAltar.getInstance();
        String name = com.bpm.minotaur.gamedata.item.ItemName.natural(item.getFriendlyName());
        boolean ok = altar.sacrificeItem(player, item);
        if (ok) {
            statusLabel.setText("The altar consumes " + name + " in holy flame, releasing "
                    + UiNames.plural(yield, "Divinity", "Divinities") + "!");
            if (parentScreen.getSoundManager() != null) {
                parentScreen.getSoundManager().playCoins();
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
        style.font = hudSkin.getFontMain();
        style.disabled = hudSkin.getSlotRecessed();
        TextButton btn = new TextButton(text, style);
        setButtonEnabled(btn, false);
        return btn;
    }

    // =========================================================================
    // TAB 4: ALTAR OF ASCENSION (STAT PROGRESSION)
    // =========================================================================

    private void buildAscensionTab(ShelterAltar altar) {
        Table root = new Table();
        root.top().left();

        // Banner
        Table banner = new Table();
        banner.setBackground(hudSkin.getDoubleBorderPanel());
        banner.pad(12, 20, 12, 20);
        Label bannerTitle = new Label("ALTAR OF ASCENSION -- PERMANENT ATTRIBUTE EMPOWERMENT",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        banner.add(bannerTitle).left().row();

        Label bannerSub = new Label("Banked Crests of Valor: " + altar.getCrestsOfValor() + " | Slay colosseum combatants to harvest Crests. Tiers survive death permanently.",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_FOOD_GREEN));
        banner.add(bannerSub).left().padTop(4).row();
        root.add(banner).fillX().padBottom(16).row();

        // 2x3 Grid for the 6 stats
        Table grid = new Table();
        grid.top().left();
        int col = 0;

        for (ShelterAltar.StatType stat : ShelterAltar.StatType.values()) {
            Table card = new Table();
            card.setBackground(hudSkin.getDoubleBorderPanel());
            card.top().left().pad(14);

            Label nameLbl = new Label(stat.getDisplayName().toUpperCase(),
                    new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
            card.add(nameLbl).left().padBottom(4).row();

            Label descLbl = new Label(stat.getDescription(),
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            descLbl.setWrap(true);
            card.add(descLbl).width(CARD_W - 28f).left().padBottom(8).row();

            int tier = altar.getAscensionTier(stat);
            Label tierLbl = new Label("Tier: " + tier + " / " + ShelterAltar.MAX_ASCENSION_TIER + " (+" + tier + " Permanent " + stat.getDisplayName() + ")",
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_BRIGHT));
            card.add(tierLbl).left().padBottom(10).row();

            int cost = altar.getAscensionCost(stat);
            TextButton buyBtn;
            if (tier >= ShelterAltar.MAX_ASCENSION_TIER) {
                buyBtn = createActionButton("FULLY ASCENDED");
                setButtonEnabled(buyBtn, false);
            } else {
                // ALTAR-4: this label is long and the cell it sat in was 360, so the text
                // started outside the button and crossed both of its borders.
                buyBtn = createActionButton("ASCEND +1  (" + UiNames.plural(cost, "Crest") + ")");
                setButtonEnabled(buyBtn, altar.canAscend(stat));
                buyBtn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        if (altar.purchaseAscension(stat)) {
                            statusLabel.setText("ASCENSION! Your " + stat.getDisplayName() + " has permanently increased by +1!");
                            refresh();
                        }
                    }
                });
            }
            card.add(buyBtn).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).left().row();

            grid.add(card).width(CARD_W).top().pad(8);
            col++;
            if (col % 2 == 0) {
                grid.row();
            }
        }

        root.add(grid).expand().fill().row();
        bodyContainer.add(root).expand().fill();
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
        // The tabs advertise 1-4 on their keycaps, so the keys have to work.
        return tabs != null && tabs.handleKey(keycode);
    }

    @Override
    public void hide() {
        UiContexts.pop(CONTEXT);
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
