package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.dice.BoneTrait;
import com.bpm.minotaur.gamedata.dice.Die;
import com.bpm.minotaur.gamedata.dice.DieFace;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.item.ShelterChest;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CraftingManager;
import com.bpm.minotaur.rendering.HudSkin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fullscreen Scene2D screen for the Shelter Artisan Workshop.
 * Implements the 3 pillars:
 * 1. Forge & Refinement: Hone weapons/armor (+1 to +3) and socket monster trophies.
 * 2. Salvage & Scrapping: Dismantle equipment/debris into materials, with batch quick-scrap.
 * 3. Ossuary & Relics: Carve custom 6-sided combat dice and forge equippable bone talismans.
 * Styled via HudSkin dark-fantasy stone, gold bevels, and crisp FreeType typography.
 */
public class CraftingScreen extends BaseScreen {

    private final GameScreen parentScreen;
    private final Player player;
    private final CraftingManager craftingManager;
    private final HudSkin hudSkin;

    private Stage stage;
    private int currentTab = 0; // 0: Forge, 1: Salvage, 2: Ossuary

    // Selected Items
    private Item selectedTargetItem; // Weapon/Armor to hone/infuse, or Item to salvage
    private Item selectedTrophy;     // Trophy for infusion or talisman
    private Item selectedBone;       // Base bone for talisman
    private Item boneStructure;      // For dice carving
    private Item boneEdge;
    private Item boneCore;

    // UI containers for live updates
    private Table leftPanelContent;
    private Table rightPanelContent;
    private Label resourceBarLabel;
    private Label feedbackLabel;
    private TextButton tabBtnForge;
    private TextButton tabBtnSalvage;
    private TextButton tabBtnOssuary;
    private TextButton tabBtnAlchemy;
    private CraftingManager.AlchemyRecipe selectedRecipe;

    private final boolean fieldMode;

    public CraftingScreen(Tarmin2 game, GameScreen parentScreen, Player player, CraftingManager craftingManager) {
        this(game, parentScreen, player, craftingManager, false);
    }

    public CraftingScreen(Tarmin2 game, GameScreen parentScreen, Player player, CraftingManager craftingManager,
            boolean fieldMode) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.craftingManager = craftingManager;
        this.fieldMode = fieldMode;
        this.craftingManager.setFieldMode(fieldMode);
        this.hudSkin = new HudSkin();
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        buildScreenLayout();
        refreshAll();
    }

    private void buildScreenLayout() {
        stage.clear();

        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        root.top().pad(25);

        // --- 1. HEADER ---
        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(18, 24, 18, 24);
        Label titleLabel = new Label(fieldMode ? "FIELD CRAFTING TOOLKIT" : "THE ARTISAN'S WORKSHOP",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        titleLabel.setFontScale(1.3f);
        header.add(titleLabel).center().row();

        Label subLabel = new Label(fieldMode
                ? "Working with what's in your pack. The Shelter Chest is out of reach."
                : "Shelter Hub Forge, Salvage Station & Bone Ossuary",
                new Label.LabelStyle(hudSkin.getFontSmall(), Color.LIGHT_GRAY));
        header.add(subLabel).center().padTop(4).row();

        root.add(header).fillX().padBottom(15).row();

        // --- 2. TAB SWITCHER BAR ---
        Table tabBar = new Table();
        tabBtnForge = createTabButton("[ 1 : FORGE & REFINE ]", 0);
        tabBtnSalvage = createTabButton("[ 2 : SALVAGE & SCRAP ]", 1);
        tabBtnOssuary = createTabButton("[ 3 : OSSUARY & RELICS ]", 2);
        tabBtnAlchemy = createTabButton("[ 4 : ALCHEMY CAULDRON ]", 3);

        tabBar.add(tabBtnForge).size(400, 50).padRight(20);
        tabBar.add(tabBtnSalvage).size(400, 50).padRight(20);
        tabBar.add(tabBtnOssuary).size(400, 50).padRight(20);
        tabBar.add(tabBtnAlchemy).size(400, 50);

        root.add(tabBar).center().padBottom(15).row();

        // --- 3. RESOURCE INVENTORY BAR ---
        Table resBar = new Table();
        resBar.setBackground(hudSkin.getSlotRecessed());
        resourceBarLabel = new Label("", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_BRIGHT));
        resBar.add(resourceBarLabel).pad(8, 25, 8, 25);

        root.add(resBar).center().padBottom(15).row();

        // --- 4. MAIN SPLIT BODY (LEFT BROWSER + RIGHT WORKBENCH) ---
        Table body = new Table();

        // Left Panel: Item Selector
        Table leftContainer = new Table();
        leftContainer.setBackground(hudSkin.getPanelBg());
        leftPanelContent = new Table();
        leftPanelContent.top().left();
        ScrollPane leftScroll = new ScrollPane(leftPanelContent);
        leftScroll.setFadeScrollBars(false);
        leftContainer.add(leftScroll).expand().fill().pad(15);

        body.add(leftContainer).width(620).height(740).padRight(25);

        // Right Panel: The Workbench Anvil
        Table rightContainer = new Table();
        rightContainer.setBackground(hudSkin.getPanelBg());
        rightPanelContent = new Table();
        rightPanelContent.top();
        ScrollPane rightScroll = new ScrollPane(rightPanelContent);
        rightScroll.setFadeScrollBars(false);
        rightContainer.add(rightScroll).expand().fill().pad(20);

        body.add(rightContainer).width(1200).height(740).row();

        root.add(body).expand().fill().row();

        // --- 5. BOTTOM FEEDBACK BANNER ---
        feedbackLabel = new Label("Select an item to begin.", new Label.LabelStyle(hudSkin.getFontLog(), Color.WHITE));
        feedbackLabel.setAlignment(Align.center);
        root.add(feedbackLabel).padTop(10).center();

        stage.addActor(root);
    }

    private TextButton createTabButton(String text, final int tabIndex) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = hudSkin.getFontHeader();
        style.fontColor = Color.LIGHT_GRAY;
        style.overFontColor = HudSkin.COL_GOLD_BRIGHT;
        style.up = hudSkin.getSlotRecessed();
        style.down = hudSkin.getSlotActive();
        style.over = hudSkin.getTooltipBg();

        final TextButton btn = new TextButton(text, style);
        btn.getLabel().setFontScale(0.78f);
        btn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                switchTab(tabIndex);
            }
        });
        return btn;
    }

    /** Applies the mockup's filled-gold active tab / dark inactive tab look. */
    private void applyTabButtonState(TextButton btn, boolean active) {
        TextButton.TextButtonStyle style = btn.getStyle();
        if (active) {
            style.up = hudSkin.getPrimaryButtonUp();
            style.down = hudSkin.getPrimaryButtonDown();
            style.over = hudSkin.getPrimaryButtonUp();
            btn.getLabel().setColor(HudSkin.COL_TEXT_ON_GOLD);
        } else {
            style.up = hudSkin.getSlotRecessed();
            style.down = hudSkin.getSlotActive();
            style.over = hudSkin.getTooltipBg();
            btn.getLabel().setColor(HudSkin.COL_GOLD_MUTED);
        }
    }

    private void switchTab(int index) {
        currentTab = index;
        selectedTargetItem = null;
        selectedTrophy = null;
        selectedBone = null;
        boneStructure = null;
        boneEdge = null;
        boneCore = null;
        selectedRecipe = null;
        feedbackLabel.setText("Tab switched.");
        refreshAll();
    }

    private void refreshAll() {
        updateTabButtonStyles();
        updateResourceBar();
        populateLeftBrowser();
        populateRightWorkbench();
    }

    private void updateTabButtonStyles() {
        applyTabButtonState(tabBtnForge, currentTab == 0);
        applyTabButtonState(tabBtnSalvage, currentTab == 1);
        applyTabButtonState(tabBtnOssuary, currentTab == 2);
        applyTabButtonState(tabBtnAlchemy, currentTab == 3);
    }

    private void updateResourceBar() {
        int scrap = craftingManager.countMaterial(player, ItemType.METAL_SCRAP);
        int leather = craftingManager.countMaterial(player, ItemType.LEATHER_SCRAP);
        int bone = craftingManager.countMaterial(player, ItemType.BONE) + craftingManager.countMaterial(player, ItemType.BONES);
        int strange = craftingManager.countMaterial(player, ItemType.STRANGE_METAL);
        int chitin = craftingManager.countMaterial(player, ItemType.CHITIN);

        resourceBarLabel.setText(String.format(
                "Unified Storage  ::  Metal Scrap: %d   |   Leather: %d   |   Bones: %d   |   Chitin: %d   |   Strange Metal: %d",
                scrap, leather, bone, chitin, strange
        ));
    }

    // =========================================================================
    // LEFT ITEM BROWSER
    // =========================================================================

    private void populateLeftBrowser() {
        leftPanelContent.clear();

        if (currentTab == 3) {
            populateAlchemyRecipeCodex();
            return;
        }

        String sectionTitle;
        List<Item> displayItems = new ArrayList<>();
        List<Item> allAvailable = new ArrayList<>(player.getInventory().getAllItems());
        if (!fieldMode) {
            ShelterChest chest = ShelterChest.getInstance();
            if (chest != null) {
                allAvailable.addAll(chest.getItems());
            }
        }

        if (currentTab == 0) { // Forge
            sectionTitle = "Equipment & Monster Trophies";
            for (Item it : allAvailable) {
                if (it == null) continue;
                if (it.isWeapon() || it.isArmor() || it.isShield() || craftingManager.isValidTrophy(it)) {
                    displayItems.add(it);
                }
            }
        } else if (currentTab == 1) { // Salvage
            sectionTitle = "Gear, Debris & Clutter";
            for (Item it : allAvailable) {
                if (it == null) continue;
                if (it.isWeapon() || it.isArmor() || it.isShield() || craftingManager.isJunkDebris(it)) {
                    displayItems.add(it);
                }
            }
        } else { // Ossuary
            sectionTitle = "Bones, Skulls & Trophies";
            for (Item it : allAvailable) {
                if (it == null) continue;
                ItemType t = it.getType();
                if (t == ItemType.BONE || t == ItemType.BONES || t == ItemType.RAT_SKULL || craftingManager.isValidTrophy(it)) {
                    displayItems.add(it);
                }
            }
        }

        Label sectionLabel = new Label(sectionTitle + " (" + displayItems.size() + ")", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        leftPanelContent.add(sectionLabel).left().padBottom(15).row();

        if (displayItems.isEmpty()) {
            String emptyMsg = fieldMode ? "No eligible items found in your pack." : "No eligible items found in pack or chest.";
            leftPanelContent.add(new Label(emptyMsg, new Label.LabelStyle(hudSkin.getFontSmall(), Color.GRAY))).padTop(20);
            return;
        }

        for (final Item item : displayItems) {
            Table itemRow = new Table();
            boolean isSelected = (item == selectedTargetItem || item == selectedTrophy || item == selectedBone || item == boneStructure || item == boneEdge || item == boneCore);
            itemRow.setBackground(isSelected ? hudSkin.getSlotActive() : hudSkin.getSlotRecessed());

            String nameText = item.getDisplayName();
            String detailText = "";
            if (item.isWeapon()) {
                detailText = " [" + (item.getDamageDice() != null ? item.getDamageDice() : "Weapon") + "]";
            } else if (item.isArmor() || item.isShield()) {
                detailText = " [AC +" + item.getArmorClassBonus() + "]";
            } else if (craftingManager.isValidTrophy(item)) {
                detailText = " [Trophy]";
            } else if (craftingManager.isJunkDebris(item)) {
                detailText = " [Debris]";
            }

            Label nameLabel = new Label(nameText + detailText, new Label.LabelStyle(hudSkin.getFontSmall(), isSelected ? HudSkin.COL_GOLD_BRIGHT : Color.WHITE));
            nameLabel.setWrap(true);
            itemRow.add(nameLabel).left().expandX().fillX().pad(8, 12, 8, 12);

            itemRow.setTouchable(Touchable.enabled);
            itemRow.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onLeftItemClicked(item);
                }
            });

            leftPanelContent.add(itemRow).expandX().fillX().padBottom(6).row();
        }
    }

    private void onLeftItemClicked(Item item) {
        if (currentTab == 0) { // Forge
            if (craftingManager.isValidTrophy(item)) {
                selectedTrophy = item;
                feedbackLabel.setText("Selected Trophy: " + item.getDisplayName());
            } else {
                selectedTargetItem = item;
                feedbackLabel.setText("Selected Equipment: " + item.getDisplayName());
            }
        } else if (currentTab == 1) { // Salvage
            selectedTargetItem = item;
            feedbackLabel.setText("Selected for Salvage: " + item.getDisplayName());
        } else { // Ossuary
            if (craftingManager.isValidTrophy(item)) {
                selectedTrophy = item;
                feedbackLabel.setText("Selected Talisman Trophy: " + item.getDisplayName());
            } else {
                // Fill bone slots
                if (boneStructure == null) {
                    boneStructure = item;
                } else if (boneEdge == null && item != boneStructure) {
                    boneEdge = item;
                } else if (boneCore == null && item != boneStructure && item != boneEdge) {
                    boneCore = item;
                } else {
                    selectedBone = item;
                }
                feedbackLabel.setText("Placed bone into workshop slot.");
            }
        }
        refreshAll();
    }

    // =========================================================================
    // RIGHT WORKBENCH ANVIL
    // =========================================================================

    private void populateRightWorkbench() {
        rightPanelContent.clear();

        if (currentTab == 0) {
            renderForgeWorkbench();
        } else if (currentTab == 1) {
            renderSalvageWorkbench();
        } else if (currentTab == 2) {
            renderOssuaryWorkbench();
        } else {
            renderAlchemyWorkbench();
        }
    }

    // =========================================================================
    // TAB 4: ALCHEMY CAULDRON
    // =========================================================================

    private void populateAlchemyRecipeCodex() {
        Label sectionLabel = new Label("Recipe Codex", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        leftPanelContent.add(sectionLabel).left().padBottom(15).row();

        CraftingManager.AlchemyCategory[] categories = CraftingManager.AlchemyCategory.values();
        for (CraftingManager.AlchemyCategory category : categories) {
            Label catLabel = new Label(category.name(), new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            leftPanelContent.add(catLabel).left().padTop(10).padBottom(4).row();

            for (final CraftingManager.AlchemyRecipe recipe : craftingManager.getAlchemyRecipes()) {
                if (recipe.category != category) continue;

                Table row = new Table();
                boolean isSelected = (recipe == selectedRecipe);
                row.setBackground(isSelected ? hudSkin.getSlotActive() : hudSkin.getSlotRecessed());

                boolean canBrew = craftingManager.canBrew(player, recipe);
                Label nameLabel = new Label(recipe.name,
                        new Label.LabelStyle(hudSkin.getFontSmall(), isSelected ? HudSkin.COL_GOLD_BRIGHT
                                : (canBrew ? Color.WHITE : Color.GRAY)));
                nameLabel.setWrap(true);
                row.add(nameLabel).left().expandX().fillX().pad(8, 12, 8, 12);

                row.setTouchable(Touchable.enabled);
                row.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        selectedRecipe = recipe;
                        feedbackLabel.setText("Selected recipe: " + recipe.name);
                        refreshAll();
                    }
                });

                leftPanelContent.add(row).expandX().fillX().padBottom(6).row();
            }
        }
    }

    private void renderAlchemyWorkbench() {
        Label title = new Label("THE BUBBLING CAULDRON", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        rightPanelContent.add(title).center().padBottom(20).row();

        // Reagent stock card: what's available from pack + Shelter Chest
        Table stockCard = new Table();
        stockCard.setBackground(hudSkin.getSlotRecessed());
        stockCard.pad(15);
        Label stockTitle = new Label("Reagents On Hand", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        stockCard.add(stockTitle).left().row();
        String stockText = String.format(
                "Bile: %d   |   Organ: %d   |   Flesh: %d   |   Bone: %d   |   Water: %d",
                craftingManager.countMaterial(player, ItemType.GIB_BILE),
                craftingManager.countMaterial(player, ItemType.GIB_ORGAN),
                craftingManager.countMaterial(player, ItemType.GIB_FLESH),
                craftingManager.countMaterial(player, ItemType.GIB_BONE),
                craftingManager.countMaterial(player, ItemType.PUDDLE_WATER));
        Label stockLabel = new Label(stockText, new Label.LabelStyle(hudSkin.getFontSmall(), Color.LIGHT_GRAY));
        stockCard.add(stockLabel).left().padTop(6);
        rightPanelContent.add(stockCard).expandX().fillX().padBottom(25).row();

        if (selectedRecipe == null) {
            Label hint = new Label("Select a recipe from the codex to begin brewing.", new Label.LabelStyle(hudSkin.getFontMain(), Color.GRAY));
            hint.setAlignment(Align.center);
            rightPanelContent.add(hint).padTop(60);
            return;
        }

        // Potion preview card
        Table previewCard = new Table();
        previewCard.setBackground(hudSkin.getTooltipBg());
        previewCard.pad(18);

        Label nameLabel = new Label(selectedRecipe.name, new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        nameLabel.setFontScale(1.1f);
        previewCard.add(nameLabel).left().row();

        Label catLabel = new Label("Category: " + selectedRecipe.category.name(), new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        previewCard.add(catLabel).left().padTop(4).row();

        Label descLabel = new Label(selectedRecipe.description, new Label.LabelStyle(hudSkin.getFontSmall(), Color.LIGHT_GRAY));
        descLabel.setWrap(true);
        previewCard.add(descLabel).left().width(1050).padTop(8).row();

        StringBuilder reagentText = new StringBuilder("Reagent Slots: ");
        int i = 0;
        for (Map.Entry<ItemType, Integer> entry : selectedRecipe.reagents.entrySet()) {
            if (i++ > 0) reagentText.append(",  ");
            int have = craftingManager.countMaterial(player, entry.getKey());
            reagentText.append(entry.getValue()).append("x ").append(entry.getKey().name())
                    .append(" (have ").append(have).append(")");
        }
        Label reagentLabel = new Label(reagentText.toString(), new Label.LabelStyle(hudSkin.getFontSmall(), Color.WHITE));
        reagentLabel.setWrap(true);
        previewCard.add(reagentLabel).left().width(1050).padTop(10).row();

        rightPanelContent.add(previewCard).expandX().fillX().padBottom(25).row();

        boolean canBrew = craftingManager.canBrew(player, selectedRecipe);
        TextButton brewBtn = createActionButton("[ BREW POTION ]", canBrew);
        brewBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (brewBtn.isDisabled()) return;
                Item potion = craftingManager.brewPotion(player, selectedRecipe);
                if (potion != null) {
                    feedbackLabel.setText("Brewed " + potion.getDisplayName() + "! Deposited into storage.");
                    feedbackLabel.setColor(Color.GREEN);
                } else {
                    feedbackLabel.setText("Failed to brew: missing reagents.");
                    feedbackLabel.setColor(Color.RED);
                }
                refreshAll();
            }
        });
        rightPanelContent.add(brewBtn).size(320, 50);
    }

    // --- TAB 0: FORGE & REFINE ---
    private void renderForgeWorkbench() {
        Label title = new Label("EQUIPMENT ANVIL & TROPHY FORGE", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        rightPanelContent.add(title).center().padBottom(20).row();

        if (selectedTargetItem == null) {
            Label hint = new Label("Select a Weapon, Shield, or Armor from the left to begin forging.", new Label.LabelStyle(hudSkin.getFontMain(), Color.GRAY));
            hint.setAlignment(Align.center);
            rightPanelContent.add(hint).padTop(100);
            return;
        }

        // Primary Target Card
        Table targetCard = new Table();
        targetCard.setBackground(hudSkin.getTooltipBg());
        targetCard.pad(15);

        Label itemNameLabel = new Label(selectedTargetItem.getDisplayName(), new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        itemNameLabel.setFontScale(1.1f);
        targetCard.add(itemNameLabel).left().row();

        String stats = (selectedTargetItem.isWeapon() ? "Damage: " + selectedTargetItem.getDamageDice() : "Armor Class: +" + selectedTargetItem.getArmorClassBonus())
                + "   |   Enhancement: +" + selectedTargetItem.getEnhancementLevel()
                + "   |   Sockets Used: " + selectedTargetItem.getTrophyInfusionCount() + " / 2";
        Label statsLabel = new Label(stats, new Label.LabelStyle(hudSkin.getFontSmall(), Color.WHITE));
        targetCard.add(statsLabel).left().padTop(6).row();

        // Modifiers list
        if (selectedTargetItem.isModified()) {
            StringBuilder sb = new StringBuilder("Current Properties: ");
            for (ItemModifier mod : selectedTargetItem.getModifiers()) {
                sb.append("• ").append(mod.displayName).append(" ");
            }
            Label modLabel = new Label(sb.toString(), new Label.LabelStyle(hudSkin.getFontSmall(), Color.LIGHT_GRAY));
            modLabel.setWrap(true);
            targetCard.add(modLabel).left().width(1100).padTop(6).row();
        }

        rightPanelContent.add(targetCard).expandX().fillX().padBottom(25).row();

        // ACTION 1: HONING
        Table honeSection = new Table();
        honeSection.setBackground(hudSkin.getSlotRecessed());
        honeSection.pad(18);

        Label honeTitle = new Label("1. HONE & REINFORCE (+1 to +3)", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        honeSection.add(honeTitle).left().row();

        int currentLvl = selectedTargetItem.getEnhancementLevel();
        if (currentLvl >= 3) {
            Label maxLabel = new Label("Maximum Enhancement Tier (+3) Reached for this item.", new Label.LabelStyle(hudSkin.getFontMain(), Color.GRAY));
            honeSection.add(maxLabel).left().padTop(8).row();
        } else {
            ItemType mat = craftingManager.getHoneMaterialType(selectedTargetItem);
            int cost = craftingManager.getHoneCostScrap(selectedTargetItem);
            boolean needsStrange = craftingManager.requiresStrangeMetal(selectedTargetItem);
            int availableMat = craftingManager.countMaterial(player, mat);
            int availableStrange = craftingManager.countMaterial(player, ItemType.STRANGE_METAL);

            boolean canAfford = craftingManager.canHone(selectedTargetItem, player);

            String costText = "Cost: " + cost + "x " + (mat == ItemType.LEATHER_SCRAP ? "Leather Scrap" : "Metal Scrap")
                    + " (Have: " + availableMat + ")" + (needsStrange ? " + 1x Strange Metal (Have: " + availableStrange + ")" : "");

            String previewText = selectedTargetItem.isWeapon()
                    ? "Preview: Increases base damage bonus by +1 (Target: +" + (currentLvl + 1) + ")"
                    : "Preview: Increases Armor Class by +1 (Target: +" + (currentLvl + 1) + " AC)";

            Table honeCard = new Table();
            honeCard.setBackground(hudSkin.getParchmentCard());
            honeCard.pad(16);
            Label honeCardHeading = new Label("HONE THE EDGE", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_PARCHMENT_HEADING));
            honeCard.add(honeCardHeading).left().row();
            Label previewLabel = new Label(previewText, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_PARCHMENT_TEXT));
            previewLabel.setWrap(true);
            honeCard.add(previewLabel).left().width(1050).padTop(8).row();
            Label costLabel = new Label(costText, new Label.LabelStyle(hudSkin.getFontSmall(), canAfford ? HudSkin.COL_PARCHMENT_AFFORD : HudSkin.COL_PARCHMENT_DENY));
            honeCard.add(costLabel).left().padTop(6).row();
            honeSection.add(honeCard).expandX().fillX().padTop(6).padBottom(14).row();

            TextButton honeBtn = createActionButton("Hone Equipment to +" + (currentLvl + 1), canAfford);
            honeBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (craftingManager.honeItem(player, selectedTargetItem)) {
                        feedbackLabel.setText("Successfully honed " + selectedTargetItem.getFriendlyName() + "!");
                        feedbackLabel.setColor(Color.GREEN);
                        refreshAll();
                    } else {
                        feedbackLabel.setText("Failed to hone: insufficient materials.");
                        feedbackLabel.setColor(Color.RED);
                    }
                }
            });
            honeSection.add(honeBtn).size(340, 45).left();
        }

        rightPanelContent.add(honeSection).expandX().fillX().padBottom(20).row();

        // ACTION 2: TROPHY INFUSION
        Table infuseSection = new Table();
        infuseSection.setBackground(hudSkin.getSlotRecessed());
        infuseSection.pad(18);

        Label infuseTitle = new Label("2. TROPHY SOCKET INFUSION (Max 2)", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        infuseSection.add(infuseTitle).left().row();

        int sockets = selectedTargetItem.getTrophyInfusionCount();
        if (sockets >= 2) {
            Label fullLabel = new Label("All Trophy Sockets (2/2) are filled for this item.", new Label.LabelStyle(hudSkin.getFontMain(), Color.GRAY));
            infuseSection.add(fullLabel).left().padTop(8).row();
        } else {
            String trophyPrompt = (selectedTrophy == null)
                    ? "Select a Monster Trophy (Claw, Tooth, Eye, Chitin, Bile, Lichen) from the left to infuse."
                    : "Selected Trophy: " + selectedTrophy.getDisplayName();
            Label trophyLabel = new Label(trophyPrompt, new Label.LabelStyle(hudSkin.getFontSmall(), selectedTrophy != null ? HudSkin.COL_GOLD_BRIGHT : Color.LIGHT_GRAY));
            infuseSection.add(trophyLabel).left().padTop(6).row();

            if (selectedTrophy != null) {
                ItemModifier mod = craftingManager.getInfusionModifier(selectedTargetItem, selectedTrophy);
                if (mod != null) {
                    Label modPreview = new Label("Enchantment Preview: Adds " + mod.displayName + " (" + mod.type.name() + " +" + mod.value + ")", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_FOOD_GREEN));
                    infuseSection.add(modPreview).left().padTop(4).padBottom(10).row();
                }
                boolean canInfuse = craftingManager.canInfuse(selectedTargetItem, selectedTrophy, player);
                TextButton infuseBtn = createActionButton("Infuse " + selectedTrophy.getDisplayName(), canInfuse);
                infuseBtn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        if (craftingManager.infuseTrophy(player, selectedTargetItem, selectedTrophy)) {
                            feedbackLabel.setText("Successfully infused " + selectedTrophy.getDisplayName() + " into " + selectedTargetItem.getFriendlyName() + "!");
                            feedbackLabel.setColor(Color.GREEN);
                            selectedTrophy = null;
                            refreshAll();
                        } else {
                            feedbackLabel.setText("Failed to infuse trophy.");
                            feedbackLabel.setColor(Color.RED);
                        }
                    }
                });
                infuseSection.add(infuseBtn).size(380, 45).left();
            }
        }

        rightPanelContent.add(infuseSection).expandX().fillX();
    }

    // --- TAB 1: SALVAGE & SCRAP ---
    private void renderSalvageWorkbench() {
        Label title = new Label("MATERIAL RECLAMATION & SALVAGE", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        rightPanelContent.add(title).center().padBottom(20).row();

        // Section A: Single Item Salvage
        Table singleSalvage = new Table();
        singleSalvage.setBackground(hudSkin.getSlotRecessed());
        singleSalvage.pad(20);

        Label sTitle = new Label("Dismantle Selected Item", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        singleSalvage.add(sTitle).left().row();

        if (selectedTargetItem == null) {
            Label noSelect = new Label("Select any unneeded weapon, armor, or debris from the left to dismantle.", new Label.LabelStyle(hudSkin.getFontSmall(), Color.GRAY));
            singleSalvage.add(noSelect).left().padTop(8).row();
        } else {
            Label itName = new Label("Target: " + selectedTargetItem.getDisplayName(), new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE));
            singleSalvage.add(itName).left().padTop(8).row();

            List<ItemType> yield = craftingManager.getSalvageYield(selectedTargetItem);
            StringBuilder sb = new SalvageYieldFormatter().format(yield);
            Label yieldLabel = new Label("Yield Breakdown: " + sb.toString(), new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_FOOD_GREEN));
            singleSalvage.add(yieldLabel).left().padTop(6).padBottom(15).row();

            TextButton scrapBtn = createActionButton("Dismantle " + selectedTargetItem.getFriendlyName(), true);
            scrapBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    List<Item> created = craftingManager.salvageItem(player, selectedTargetItem);
                    feedbackLabel.setText("Dismantled " + selectedTargetItem.getFriendlyName() + " into " + created.size() + " materials!");
                    feedbackLabel.setColor(Color.GREEN);
                    selectedTargetItem = null;
                    refreshAll();
                }
            });
            singleSalvage.add(scrapBtn).size(400, 48).left();
        }

        rightPanelContent.add(singleSalvage).expandX().fillX().padBottom(30).row();

        // Section B: Quick Scrap All Junk Debris
        Table batchScrap = new Table();
        batchScrap.setBackground(hudSkin.getTooltipBg());
        batchScrap.pad(20);

        Label bTitle = new Label("Quick-Scrap All Junk Debris", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        batchScrap.add(bTitle).left().row();

        Label bDesc = new Label("Automatically dismantle all loose clutter and debris (bent nails, chains, scraps, rat skulls, rags, bones) across both your backpack and the Shelter Chest in one click.", new Label.LabelStyle(hudSkin.getFontSmall(), Color.LIGHT_GRAY));
        bDesc.setWrap(true);
        batchScrap.add(bDesc).width(1100).left().padTop(6).padBottom(15).row();

        TextButton quickScrapBtn = createActionButton("Scrap All Junk Debris in Storage", true);
        quickScrapBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                int count = craftingManager.salvageAllJunk(player);
                feedbackLabel.setText("Dismantled " + count + " junk debris items into raw scrap!");
                feedbackLabel.setColor(Color.GREEN);
                selectedTargetItem = null;
                refreshAll();
            }
        });
        batchScrap.add(quickScrapBtn).size(480, 50).left();

        rightPanelContent.add(batchScrap).expandX().fillX();
    }

    // --- TAB 2: OSSUARY & RELICS ---
    private void renderOssuaryWorkbench() {
        Label title = new Label("OSSUARY CARVING & BONE RELICS", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        rightPanelContent.add(title).center().padBottom(15).row();

        // Section A: Custom Combat Die (3 Bones)
        Table diceSection = new Table();
        diceSection.setBackground(hudSkin.getSlotRecessed());
        diceSection.pad(16);

        Label dTitle = new Label("1. Carve Custom Combat Die (3 Bones)", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        diceSection.add(dTitle).left().row();

        String slot1 = boneStructure != null ? boneStructure.getDisplayName() : "[ Empty Structure Bone ]";
        String slot2 = boneEdge != null ? boneEdge.getDisplayName() : "[ Empty Edge Bone ]";
        String slot3 = boneCore != null ? boneCore.getDisplayName() : "[ Empty Core Bone ]";

        Label slotsLabel = new Label("Slots: 1. Structure: " + slot1 + "   |   2. Edge: " + slot2 + "   |   3. Core: " + slot3, new Label.LabelStyle(hudSkin.getFontSmall(), Color.LIGHT_GRAY));
        diceSection.add(slotsLabel).left().padTop(4).padBottom(8).row();

        // Fetch bone traits for previewing the 6-sided net
        BoneTrait traitStruct = (boneStructure != null) ? BoneTrait.get(boneStructure.getCorpseSource()) : null;
        BoneTrait traitEdge = (boneEdge != null) ? BoneTrait.get(boneEdge.getCorpseSource()) : null;
        BoneTrait traitCore = (boneCore != null) ? BoneTrait.get(boneCore.getCorpseSource()) : null;

        DieFace f1 = (traitStruct != null) ? traitStruct.faces[0] : null;
        DieFace f6 = (traitStruct != null) ? traitStruct.faces[1] : null;
        DieFace f2 = (traitEdge != null) ? traitEdge.faces[0] : null;
        DieFace f5 = (traitEdge != null) ? traitEdge.faces[1] : null;
        DieFace f3 = (traitCore != null) ? traitCore.faces[0] : null;
        DieFace f4 = (traitCore != null) ? traitCore.faces[1] : null;

        // Visual 6-Sided Unfolded Die Net Diagram (Cross Pattern)
        Table netTable = new Table();
        netTable.pad(6);
        // Row 1: Top Pole (F1)
        netTable.add().size(130, 48); // Left spacer
        netTable.add(createFaceBox("F1 (Top Pole)", f1, traitStruct != null ? traitStruct.boneColor : null)).size(150, 48).pad(2);
        netTable.add().size(130, 48); // Right spacer
        netTable.add().size(130, 48).row(); // Far right spacer

        // Row 2: Equator (F4 Core, F2 Edge, F3 Core, F5 Edge)
        netTable.add(createFaceBox("F4 (Core)", f4, traitCore != null ? traitCore.boneColor : null)).size(130, 48).pad(2);
        netTable.add(createFaceBox("F2 (Edge)", f2, traitEdge != null ? traitEdge.boneColor : null)).size(130, 48).pad(2);
        netTable.add(createFaceBox("F3 (Core)", f3, traitCore != null ? traitCore.boneColor : null)).size(130, 48).pad(2);
        netTable.add(createFaceBox("F5 (Edge)", f5, traitEdge != null ? traitEdge.boneColor : null)).size(130, 48).pad(2).row();

        // Row 3: Bottom Pole (F6)
        netTable.add().size(130, 48); // Left spacer
        netTable.add(createFaceBox("F6 (Bottom Pole)", f6, traitStruct != null ? traitStruct.boneColor : null)).size(150, 48).pad(2);
        netTable.add().size(130, 48); // Right spacer
        netTable.add().size(130, 48).row(); // Far right spacer

        diceSection.add(netTable).center().padTop(4).padBottom(6).row();

        // Anatomy Legend
        Label guideLbl = new Label("Structure sets Poles (Faces 1 & 6) | Edge sets Striking Facets (Faces 2 & 5) | Core sets Balance (Faces 3 & 4)",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        diceSection.add(guideLbl).center().padBottom(6).row();

        // Check Pure Resonance
        boolean isResonant = (traitStruct != null && traitEdge != null && traitCore != null
                && traitStruct.source == traitEdge.source && traitEdge.source == traitCore.source);
        if (isResonant) {
            String resTitle = "★ PURE " + traitStruct.source.name().replace('_', ' ') + " RESONANCE ACTIVE: "
                    + (traitStruct.resonanceName != null ? traitStruct.resonanceName : "+25% Potency") + " ★";
            Label resLabel = new Label(resTitle, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_BRIGHT));
            diceSection.add(resLabel).center().padBottom(8).row();
        }

        boolean canCarveDie = (boneStructure != null && boneEdge != null && boneCore != null);
        if (canCarveDie) {
            Die previewDie = craftingManager.craftBoneDie(boneStructure, boneEdge, boneCore);
            if (previewDie != null) {
                Label preview = new Label("Resulting Die: " + previewDie.getName(), new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_FOOD_GREEN));
                diceSection.add(preview).left().padBottom(8).row();
            }
        }

        Table diceBtnRow = new Table();
        TextButton carveBtn = createActionButton("Carve Die into Pool", canCarveDie);
        carveBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (carveBtn.isDisabled() || boneStructure == null || boneEdge == null || boneCore == null) {
                    return;
                }
                Die die = craftingManager.craftBoneDie(boneStructure, boneEdge, boneCore);
                if (die != null) {
                    player.getStats().getDicePool().add(die);
                    player.getInventory().removeItem(boneStructure);
                    player.getInventory().removeItem(boneEdge);
                    player.getInventory().removeItem(boneCore);
                    if (!fieldMode) {
                        ShelterChest.getInstance().removeItem(boneStructure);
                        ShelterChest.getInstance().removeItem(boneEdge);
                        ShelterChest.getInstance().removeItem(boneCore);
                    }
                    feedbackLabel.setText("Carved " + die.getName() + " and added to your Combat Dice Pool!");
                    feedbackLabel.setColor(Color.GREEN);
                    boneStructure = null;
                    boneEdge = null;
                    boneCore = null;
                    refreshAll();
                }
            }
        });
        diceBtnRow.add(carveBtn).size(320, 45).padRight(15);

        TextButton clearBonesBtn = createActionButton("Clear Bone Slots", true);
        clearBonesBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                boneStructure = null;
                boneEdge = null;
                boneCore = null;
                refreshAll();
            }
        });
        diceBtnRow.add(clearBonesBtn).size(260, 45);

        diceSection.add(diceBtnRow).left();
        rightPanelContent.add(diceSection).expandX().fillX().padBottom(25).row();

        // Section B: Forge Bone Talisman
        Table talismanSection = new Table();
        talismanSection.setBackground(hudSkin.getTooltipBg());
        talismanSection.pad(20);

        Label tTitle = new Label("2. Forge Equippable Bone Talisman", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        talismanSection.add(tTitle).left().row();

        String bText = selectedBone != null ? selectedBone.getDisplayName() : "[ Select Bone Base ]";
        String trText = selectedTrophy != null ? selectedTrophy.getDisplayName() : "[ Select Monster Trophy ]";
        Label reqLabel = new Label("Ingredients: Bone (" + bText + ") + Trophy (" + trText + ") + 1x Metal Scrap", new Label.LabelStyle(hudSkin.getFontSmall(), Color.LIGHT_GRAY));
        talismanSection.add(reqLabel).left().padTop(6).padBottom(10).row();

        boolean canForge = craftingManager.canForgeTalisman(player, selectedBone, selectedTrophy);
        TextButton forgeBtn = createActionButton("Forge Bone Talisman", canForge);
        forgeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (forgeBtn.isDisabled()) {
                    return;
                }
                Item tal = craftingManager.forgeBoneTalisman(player, selectedBone, selectedTrophy);
                if (tal != null) {
                    feedbackLabel.setText("Forged " + tal.getDisplayName() + "! Deposited into storage.");
                    feedbackLabel.setColor(Color.GREEN);
                    selectedBone = null;
                    selectedTrophy = null;
                    refreshAll();
                } else {
                    feedbackLabel.setText("Failed to forge talisman: missing materials.");
                    feedbackLabel.setColor(Color.RED);
                }
            }
        });
        talismanSection.add(forgeBtn).size(320, 45).left();

        rightPanelContent.add(talismanSection).expandX().fillX();
    }

    private Table createFaceBox(String roleTitle, DieFace face, Color boneColor) {
        Table box = new Table();
        box.setBackground(hudSkin.getDoubleBorderPanel());
        box.pad(4, 6, 4, 6);

        Label titleLbl = new Label(roleTitle, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        box.add(titleLbl).center().row();

        String traitText = (face != null) ? face.getLabel() + " (" + face.getType().name() + " " + face.getValue() + ")" : "[ Empty ]";
        Color textColor = (face != null && boneColor != null) ? boneColor : Color.GRAY;
        Label traitLbl = new Label(traitText, new Label.LabelStyle(hudSkin.getFontSmall(), textColor));
        box.add(traitLbl).center().padTop(2);

        return box;
    }

    private TextButton createActionButton(String text, boolean enabled) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = hudSkin.getFontHeader();
        if (enabled) {
            style.fontColor = HudSkin.COL_TEXT_ON_GOLD;
            style.overFontColor = HudSkin.COL_TEXT_ON_GOLD;
            style.up = hudSkin.getPrimaryButtonUp();
            style.down = hudSkin.getPrimaryButtonDown();
            style.over = hudSkin.getPrimaryButtonDown();
        } else {
            style.fontColor = HudSkin.COL_GOLD_MUTED;
            style.overFontColor = HudSkin.COL_GOLD_MUTED;
            style.up = hudSkin.getSlotRecessed();
            style.down = hudSkin.getSlotRecessed();
            style.over = hudSkin.getSlotRecessed();
        }
        style.disabled = hudSkin.getSlotRecessed();

        TextButton btn = new TextButton(text, style);
        btn.getLabel().setFontScale(0.8f);
        btn.getLabel().setWrap(false);
        btn.setDisabled(!enabled);
        btn.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
        return btn;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.08f, 0.07f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.I) {
            game.setScreen(parentScreen);
            return true;
        }
        if (keycode == Input.Keys.NUM_1 || keycode == Input.Keys.NUMPAD_1) {
            switchTab(0);
            return true;
        }
        if (keycode == Input.Keys.NUM_2 || keycode == Input.Keys.NUMPAD_2) {
            switchTab(1);
            return true;
        }
        if (keycode == Input.Keys.NUM_3 || keycode == Input.Keys.NUMPAD_3) {
            switchTab(2);
            return true;
        }
        if (keycode == Input.Keys.NUM_4 || keycode == Input.Keys.NUMPAD_4) {
            switchTab(3);
            return true;
        }
        return false;
    }

    @Override
    public void dispose() {
        stage.dispose();
        hudSkin.dispose();
    }

    private static class SalvageYieldFormatter {
        public StringBuilder format(List<ItemType> yield) {
            StringBuilder sb = new StringBuilder();
            java.util.Map<ItemType, Integer> counts = new java.util.HashMap<>();
            for (ItemType t : yield) {
                counts.put(t, counts.getOrDefault(t, 0) + 1);
            }
            int i = 0;
            for (java.util.Map.Entry<ItemType, Integer> entry : counts.entrySet()) {
                if (i++ > 0) sb.append(", ");
                sb.append(entry.getValue()).append("x ").append(entry.getKey().name());
            }
            return sb;
        }
    }
}
