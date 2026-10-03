package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ShelterChest;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CookingManager;
import com.bpm.minotaur.managers.CookingManager.CookingRecipe;
import com.bpm.minotaur.managers.WorldManager;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.ui.KeyHintLegend;
import com.bpm.minotaur.ui.UiContexts;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiNames;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTabs;
import com.bpm.minotaur.ui.UiTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * Fullscreen Scene2D screen for the Shelter Cooking Hearth.
 * Implements Caves of Qud inspired cooking:
 * 1. Whip Up a Meal: Instant 1-click hearty campfire meal from available basic food/meat.
 * 2. Cauldron (Custom): Freeform 1-3 ingredient cauldron mixing with live recipe match & procedural nomenclature.
 * 3. Cookbook (Codex): Mastered recipes and monster gib lore with 1-click pot auto-fill.
 * 4. Rest by the Hearth: Spends 1 kindling to warm hypothermia, heal HP, and advance in-game time 45 min.
 */
public class CookingScreen extends BaseScreen {

    public static class PantryEntry {
        public final Item item;
        public final boolean fromChest;

        public PantryEntry(Item item, boolean fromChest) {
            this.item = item;
            this.fromChest = fromChest;
        }
    }

    private final GameScreen parentScreen;
    private final Player player;
    private final WorldManager worldManager;
    private final CookingManager cookingManager;
    private final HudSkin hudSkin;

    private Stage stage;
    private int currentTab = 0; // 0: Whip Up, 1: Cauldron, 2: Cookbook, 3: Rest

    // Cauldron selected ingredients (max 3)
    private final List<PantryEntry> cauldronIngredients = new ArrayList<>();

    // Dynamic UI widgets
    private Table leftPanelContent;
    private Table rightPanelContent;
    private Label resourceBarLabel;
    private Label feedbackLabel;
    private UiTabs tabs;

    /** This screen's entry on the input-context stack (SPEC 5.6). */
    private static final String CONTEXT = "HEARTH";

    private final boolean fieldMode;

    public CookingScreen(Tarmin2 game, GameScreen parentScreen, Player player, WorldManager worldManager) {
        this(game, parentScreen, player, worldManager, false);
    }

    public CookingScreen(Tarmin2 game, GameScreen parentScreen, Player player, WorldManager worldManager,
            boolean fieldMode) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.worldManager = worldManager;
        this.cookingManager = worldManager != null ? worldManager.getCookingManager() : new CookingManager();
        this.fieldMode = fieldMode;
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
        header.pad(16, 24, 16, 24);
        Label titleLabel = new Label(fieldMode ? "PORTABLE COOKWARE" : "THE SHELTER COOKING HEARTH",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        header.add(titleLabel).center().row();

        Label subLabel = new Label(fieldMode
                ? "Cooking from your pack alone. The Shelter Chest is out of reach."
                : "Simmer harvested beast gibs, prepare hearty expedition meals, and discover ancient culinary boons",
                new Label.LabelStyle(hudSkin.getFontSmall(), Color.LIGHT_GRAY));
        header.add(subLabel).center().padTop(4).row();

        root.add(header).fillX().padBottom(12).row();

        // --- 2. TAB SWITCHER BAR ---
        // HEARTH-1 (RC5): four labels of the shape "[ 2 : CAULDRON (CUSTOM) ]" in cells fixed
        // at 380, which the longest of them exceeds -- so the tab name and the next tab's
        // bracketed number were drawn over each other and it rendered as "MEAL [2".
        tabs = new UiTabs(hudSkin)
                .addTab(1, "Whip up a meal")
                .addTab(2, "Cauldron")
                .addTab(3, "Cookbook & codex")
                .addTab(4, "Rest by hearth");
        tabs.onSelect(this::switchTab);
        tabs.setSelectedSilently(currentTab);
        root.add(tabs).center().padBottom(12).row();

        // --- 3. RESOURCE STATUS BAR ---
        Table resBar = new Table();
        resBar.setBackground(hudSkin.getSlotRecessed());
        resourceBarLabel = new Label("", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_BRIGHT));
        resBar.add(resourceBarLabel).pad(6, 25, 6, 25);

        root.add(resBar).center().padBottom(15).row();

        // --- 4. MAIN SPLIT BODY (LEFT PANTRY + RIGHT HEARTH WORKBENCH) ---
        Table body = new Table();

        // Left Panel: Unified Pantry (Backpack + Shelter Chest)
        Table leftContainer = new Table();
        leftContainer.setBackground(hudSkin.getPanelBg());
        leftPanelContent = new Table();
        leftPanelContent.top().left();
        ScrollPane leftScroll = new ScrollPane(leftPanelContent, UiStyles.scrollPane(hudSkin));
        leftScroll.setFadeScrollBars(false);
        leftContainer.add(leftScroll).expand().fill().pad(15);

        body.add(leftContainer).width(720).height(640).padRight(25);

        // Right Panel: Cooking Station Tab Content
        Table rightContainer = new Table();
        rightContainer.setBackground(hudSkin.getPanelBg());
        rightPanelContent = new Table();
        rightPanelContent.top().left();
        ScrollPane rightScroll = new ScrollPane(rightPanelContent, UiStyles.scrollPane(hudSkin));
        rightScroll.setFadeScrollBars(false);
        rightContainer.add(rightScroll).expand().fill().pad(15);

        body.add(rightContainer).width(1100).height(640).row();

        root.add(body).expand().fill().padBottom(12).row();

        // --- 5. FOOTER FEEDBACK & BACK BUTTON ---
        Table footer = new Table();
        feedbackLabel = new Label("Ready to cook at the hearth.",
                new Label.LabelStyle(hudSkin.getFontSmall(), Color.WHITE));
        feedbackLabel.setAlignment(Align.left);
        footer.add(feedbackLabel).expandX().left().padLeft(20);

        // HEARTH-2: the label carried its own two shortcuts and a destination, at the header
        // font, in a 400-unit cell -- so it was clipped at both ends and read
        // "ESC / O : Back to Shelt...". The button says what it does; the keys are in the
        // legend beside it.
        TextButton backBtn = createActionButton("BACK TO SHELTER", true);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(parentScreen);
            }
        });
        footer.add(new KeyHintLegend(hudSkin)
                .hint("1-4", "Tab")
                .hint("O", "Close")
                .escapeHint("Back to shelter")).right().padRight(UiTheme.PAD_XL);
        footer.add(backBtn).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).right().padRight(20);

        root.add(footer).fillX();

        stage.addActor(root);
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
        btn.setDisabled(!enabled);
        return btn;
    }

    public void switchTab(int tabIndex) {
        this.currentTab = tabIndex;
        if (tabs != null) {
            tabs.setSelectedSilently(tabIndex);
        }
        refreshRightPanel();
    }

    private void refreshAll() {
        refreshResourceBar();
        refreshLeftPanelPantry();
        refreshRightPanel();
    }

    private void refreshResourceBar() {
        int kindling = player.getStats().getKindlingCount();
        int water = player.getStats().getCookingWaterCount();
        int skill = player.getStats().getCookingSkill();
        int baseDur = cookingManager.calculateEffectDuration(skill, false);

        String text = String.format("HEARTH FUEL (Kindling): %d  |  COOKING WATER: %d  |  COOKING SKILL: %d  (Base Boon: %d Turns)",
                kindling, water, skill, baseDur);
        resourceBarLabel.setText(text);
        resourceBarLabel.setColor((kindling < 1 || water < 1) ? Color.valueOf("E57373") : HudSkin.COL_GOLD_BRIGHT);
    }

    // -------------------------------------------------------------------------
    // LEFT PANEL: Unified Pantry (Backpack + Shelter Chest)
    // -------------------------------------------------------------------------

    private List<PantryEntry> getUnifiedPantryItems() {
        List<PantryEntry> list = new ArrayList<>();
        // 1. Player Pack
        for (Item item : player.getInventory().getMainInventory()) {
            if (isCookable(item)) {
                list.add(new PantryEntry(item, false));
            }
        }
        // 2. Shelter Chest (not reachable from a portable field kit)
        if (!fieldMode) {
            for (Item item : ShelterChest.getInstance().getItems()) {
                if (isCookable(item)) {
                    list.add(new PantryEntry(item, true));
                }
            }
        }
        return list;
    }

    private boolean isCookable(Item item) {
        if (item == null) return false;
        String typeName = item.getType().name();
        return item.isFood() || typeName.startsWith("GIB_") || typeName.equals("MEAT") || typeName.equals("MONSTER_EYE");
    }

    private void refreshLeftPanelPantry() {
        leftPanelContent.clear();

        Label title = new Label(fieldMode ? "TRAIL PANTRY" : "UNIFIED SHELTER PANTRY",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        leftPanelContent.add(title).left().padBottom(6).row();

        // HEARTH-4: this line had no width, so in a 720-unit panel it was cut at
        // "Click to ad...". It wraps now.
        Label sub = UiLabels.wrapping(fieldMode
                ? "Draws from your backpack only. Click an item to add it to the cauldron."
                : "Draws from your backpack and the shelter chest. Click an item to add it to the cauldron.",
                UiStyles.caption(hudSkin));
        leftPanelContent.add(sub).growX().left().padBottom(15).row();

        List<PantryEntry> pantry = getUnifiedPantryItems();
        if (pantry.isEmpty()) {
            Label empty = new Label("No meat, monster gibs, or forage found in pack or chest.",
                    new Label.LabelStyle(hudSkin.getFontMain(), Color.DARK_GRAY));
            leftPanelContent.add(empty).padTop(30).row();
            return;
        }

        for (final PantryEntry entry : pantry) {
            Table row = new Table();
            row.setBackground(hudSkin.getSlotRecessed());

            // HEARTH-5: the source was a bracketed word taking as much room as the item name.
            String sourceBadge = entry.fromChest ? "Chest" : "Pack";
            Color sourceCol = entry.fromChest ? HudSkin.COL_GOLD_MUTED : HudSkin.COL_WATER_CYAN;
            Label badgeLbl = new Label(sourceBadge, new Label.LabelStyle(hudSkin.getFontSmall(), sourceCol));
            row.add(badgeLbl).width(90f).padLeft(10).padRight(10);

            String name = entry.item.getDisplayName();
            MonsterType src = entry.item.getCorpseSource();
            if (src != null) {
                if (cookingManager.isGibIdentified(src)) {
                    StatusEffectType eff = cookingManager.getEffectForMonster(src);
                    if (eff != null) name += " (" + UiNames.of(eff) + ")";
                } else {
                    name += " [?]";
                }
            }
            Label nameLbl = UiLabels.ellipsized(name, UiStyles.body(hudSkin));
            row.add(nameLbl).growX().left();

            TextButton addBtn = createActionButton("+ Add", cauldronIngredients.size() < 3);
            addBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (cauldronIngredients.size() >= 3) {
                        feedbackLabel.setText("Cauldron is full! (Max 3 ingredients)");
                        feedbackLabel.setColor(Color.valueOf("FFB74D"));
                        return;
                    }
                    cauldronIngredients.add(entry);
                    if (currentTab != 1) {
                        currentTab = 1;
                    }
                    refreshAll();
                }
            });
            row.add(addBtn).size(90, 36).padRight(8);

            leftPanelContent.add(row).expandX().fillX().height(52).padBottom(6).row();
        }
    }

    // -------------------------------------------------------------------------
    // RIGHT PANEL: Tab Switching (Whip Up, Cauldron, Cookbook, Rest)
    // -------------------------------------------------------------------------

    private void refreshRightPanel() {
        rightPanelContent.clear();
        switch (currentTab) {
            case 0:
                buildTabWhipUp();
                break;
            case 1:
                buildTabCauldron();
                break;
            case 2:
                buildTabCookbook();
                break;
            case 3:
                buildTabRest();
                break;
        }
    }

    // --- TAB 0: WHIP UP A MEAL ---
    private void buildTabWhipUp() {
        Label tabTitle = new Label("WHIP UP A CAMPFIRE MEAL",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        rightPanelContent.add(tabTitle).left().padBottom(6).row();

        Label desc = new Label("Quickly roast and simmer available meats or forage without manual recipe tuning.\nCosts 1 Kindling and 1 Cooking Water.",
                new Label.LabelStyle(hudSkin.getFontMain(), Color.LIGHT_GRAY));
        desc.setWrap(true);
        rightPanelContent.add(desc).width(1000).left().padBottom(20).row();

        List<PantryEntry> pantry = getUnifiedPantryItems();
        List<Item> pantryItems = new ArrayList<>();
        for (PantryEntry pe : pantry) pantryItems.add(pe.item);

        final List<Item> quickIngredients = cookingManager.findQuickCookIngredients(pantryItems);

        Table previewBox = new Table();
        previewBox.setBackground(hudSkin.getParchmentCard());
        previewBox.pad(20);

        if (quickIngredients.isEmpty()) {
            Label noMeat = new Label("No basic meat, gibs, or forage available to whip up a meal.",
                    new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_PARCHMENT_DENY));
            previewBox.add(noMeat).center().row();
        } else {
            Label mealPreview = new Label("Dish: Campfire Hearty Stew",
                    new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_PARCHMENT_HEADING));
            previewBox.add(mealPreview).left().padBottom(8).row();

            StringBuilder sb = new StringBuilder("Ingredients: ");
            for (int i = 0; i < quickIngredients.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(quickIngredients.get(i).getDisplayName());
            }
            Label ingLbl = new Label(sb.toString(), new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_PARCHMENT_TEXT));
            previewBox.add(ingLbl).left().padBottom(8).row();

            int skill = player.getStats().getCookingSkill();
            int dur = cookingManager.calculateEffectDuration(skill, false);
            Label boonLbl = new Label("Guaranteed Boons: HEALTHY, RECOVERING (" + dur + " turns)\nRestores high Satiety (+45), Hydration, and HP.",
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_PARCHMENT_AFFORD));
            previewBox.add(boonLbl).left().row();
        }

        rightPanelContent.add(previewBox).expandX().fillX().padBottom(25).row();

        // Action Buttons
        boolean hasResources = player.getStats().getKindlingCount() >= 1 && player.getStats().getCookingWaterCount() >= 1;
        boolean canCook = hasResources && !quickIngredients.isEmpty();

        Table btnRow = new Table();
        addActionWithCaption(btnRow, "FEAST", "Eat it here and now", canCook,
                () -> cookMealAction(quickIngredients, true));
        addActionWithCaption(btnRow, "PACK RATIONS", "Carry it into the delve", canCook,
                () -> cookMealAction(quickIngredients, false));

        rightPanelContent.add(btnRow).left().row();
    }

    /**
     * An action button with its cost or consequence underneath (HEARTH-3).
     *
     * <p>The labels here used to carry both -- "FEAST AT HEARTH (EAT NOW)", "REST AND STOKE
     * FIRE (1 Kindling)" -- at the header font, in cells narrower than the text. A verb fits in
     * a button; a condition belongs in a caption.
     */
    private void addActionWithCaption(Table row, String verb, String caption, boolean enabled, Runnable action) {
        TextButton btn = createActionButton(verb, enabled);
        btn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (enabled) {
                    action.run();
                }
            }
        });
        Table col = new Table();
        col.add(btn).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).growX().row();
        col.add(UiLabels.ellipsized(caption,
                UiStyles.caption(hudSkin, enabled ? UiTheme.TEXT_DIM : UiTheme.DANGER)))
                .center().padTop(UiTheme.PAD_XS);
        row.add(col).padRight(UiTheme.PAD_XL).top();
    }

    // --- TAB 1: CAULDRON (CUSTOM MIXING) ---
    private void buildTabCauldron() {
        Label tabTitle = new Label("THE IRON CAULDRON (CUSTOM COMBINATION)",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        rightPanelContent.add(tabTitle).left().padBottom(6).row();

        Label desc = new Label("Drop 1 to 3 ingredients into the simmering pot. Classic recipes grant guaranteed effects;\nexperimental mixes unlock procedural monster dishes and discover new gib properties.",
                new Label.LabelStyle(hudSkin.getFontMain(), Color.LIGHT_GRAY));
        desc.setWrap(true);
        rightPanelContent.add(desc).width(1000).left().padBottom(15).row();

        // 3 Pot Ingredient Slots
        Table potRow = new Table();
        for (int i = 0; i < 3; i++) {
            Table slot = new Table();
            slot.setBackground(hudSkin.getSlotRecessed());
            slot.pad(10);

            if (i < cauldronIngredients.size()) {
                final PantryEntry pe = cauldronIngredients.get(i);
                Label numLbl = new Label("Slot " + (i + 1), new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
                slot.add(numLbl).left().row();

                Label nameLbl = new Label(pe.item.getDisplayName(), new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE));
                nameLbl.setWrap(true);
                slot.add(nameLbl).width(260).height(45).left().padTop(4).row();

                TextButton removeBtn = createActionButton("[ X Remove ]", true);
                removeBtn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        cauldronIngredients.remove(pe);
                        refreshAll();
                    }
                });
                slot.add(removeBtn).size(160, 32).left().padTop(6);
            } else {
                Label numLbl = new Label("Slot " + (i + 1), new Label.LabelStyle(hudSkin.getFontSmall(), Color.DARK_GRAY));
                slot.add(numLbl).center().row();
                Label emptyLbl = new Label("[ Empty ]", new Label.LabelStyle(hudSkin.getFontMain(), Color.DARK_GRAY));
                slot.add(emptyLbl).center().padTop(10).row();
            }
            potRow.add(slot).size(310, 130).padRight(15);
        }
        rightPanelContent.add(potRow).left().padBottom(20).row();

        // Live Preview Box
        List<Item> currentItems = new ArrayList<>();
        for (PantryEntry pe : cauldronIngredients) currentItems.add(pe.item);

        CookingRecipe matchedRecipe = cookingManager.findMatchingRecipe(currentItems);
        boolean synergy = cookingManager.detectSynergy(currentItems);
        int skill = player.getStats().getCookingSkill();
        int dur = cookingManager.calculateEffectDuration(skill, synergy);

        Table previewBox = new Table();
        previewBox.setBackground(hudSkin.getParchmentCard());
        previewBox.pad(15);

        if (currentItems.isEmpty()) {
            Label hint = new Label("Select ingredients from the pantry to preview your meal.",
                    new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_PARCHMENT_TEXT));
            previewBox.add(hint).center();
        } else {
            String dishName;
            String dishDesc;
            List<StatusEffectType> previewEffects = new ArrayList<>();

            if (matchedRecipe != null) {
                dishName = matchedRecipe.name + " (Mastered Recipe)";
                dishDesc = matchedRecipe.description;
                previewEffects.addAll(matchedRecipe.guaranteedEffects);
            } else {
                dishName = cookingManager.generateProceduralMealName(currentItems);
                dishDesc = "An experimental culinary brew steeped in monster essences.";
                for (Item ingr : currentItems) {
                    MonsterType src = ingr.getCorpseSource();
                    if (src != null) {
                        StatusEffectType eff = cookingManager.getEffectForMonster(src);
                        if (eff != null && !previewEffects.contains(eff)) previewEffects.add(eff);
                    }
                }
                if (previewEffects.isEmpty()) {
                    previewEffects.add(StatusEffectType.HEALTHY);
                    previewEffects.add(StatusEffectType.RECOVERING);
                }
            }

            Label nameLbl = new Label("Dish: " + dishName,
                    new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_PARCHMENT_HEADING));
            previewBox.add(nameLbl).left().row();

            Label descLbl = new Label(dishDesc, new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_PARCHMENT_TEXT));
            previewBox.add(descLbl).left().padTop(4).padBottom(8).row();

            StringBuilder boons = new StringBuilder("Boons: ");
            for (int i = 0; i < previewEffects.size(); i++) {
                if (i > 0) boons.append(", ");
                boons.append(UiNames.of(previewEffects.get(i)));
            }
            boons.append(" (").append(dur).append(" turns)");
            Label boonsLbl = new Label(boons.toString(), new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_PARCHMENT_AFFORD));
            previewBox.add(boonsLbl).left().row();

            if (synergy) {
                Label synLbl = new Label("ESSENCE SYNERGY! Duration doubled by harmonious donor monster traits.",
                        new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_PARCHMENT_HEADING));
                previewBox.add(synLbl).left().padTop(6).row();
            }
        }
        rightPanelContent.add(previewBox).expandX().fillX().padBottom(20).row();

        // Action Buttons
        boolean hasResources = player.getStats().getKindlingCount() >= 1 && player.getStats().getCookingWaterCount() >= 1;
        boolean canCook = hasResources && !currentItems.isEmpty();

        Table btnRow = new Table();
        addActionWithCaption(btnRow, "FEAST", "Eat it here and now", canCook,
                () -> cookMealAction(currentItems, true));
        addActionWithCaption(btnRow, "PACK RATIONS", "Carry it into the delve", canCook,
                () -> cookMealAction(currentItems, false));

        rightPanelContent.add(btnRow).left().row();
    }

    // --- TAB 2: COOKBOOK & GIB CODEX ---
    private void buildTabCookbook() {
        Label tabTitle = new Label("THE HEARTH COOKBOOK & GIB CODEX",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        rightPanelContent.add(tabTitle).left().padBottom(6).row();

        Label desc = UiLabels.wrapping(
                "Documented recipes and identified monster essences. Auto-Fill loads a recipe's "
                        + "ingredients into the cauldron.",
                UiStyles.body(hudSkin, UiTheme.TEXT_DIM));
        rightPanelContent.add(desc).growX().left().padBottom(15).row();

        List<PantryEntry> pantry = getUnifiedPantryItems();

        for (final CookingRecipe recipe : cookingManager.getAllRecipes()) {
            Table row = new Table();
            row.setBackground(hudSkin.getSlotRecessed());
            row.pad(10);

            boolean isDiscovered = cookingManager.isRecipeDiscovered(recipe.name);
            // HEARTH-4: none of these three had a width, so the cards grew to whatever the
            // longest ingredient list needed and ran off the right of the panel.
            Label nameLbl = UiLabels.ellipsized(isDiscovered ? recipe.name : "Unknown recipe",
                    new Label.LabelStyle(hudSkin.getFontHeader(), isDiscovered ? HudSkin.COL_GOLD_ANTIQUE : UiTheme.TEXT_OFF));
            row.add(nameLbl).growX().left().row();

            Label rDesc = UiLabels.wrapping(isDiscovered ? recipe.description
                            : "Experiment with monster gibs in the cauldron to discover this recipe.",
                    UiStyles.caption(hudSkin));
            row.add(rDesc).growX().left().padTop(2).padBottom(4).row();

            // HEARTH-6: this printed the ItemType constants, so the requirement line read
            // "GIB<-FLESH, MONSTER<-EYE".
            StringBuilder ingStr = new StringBuilder("Needs: ");
            for (int i = 0; i < recipe.ingredients.size(); i++) {
                if (i > 0) ingStr.append(", ");
                ingStr.append(UiNames.of(recipe.ingredients.get(i)));
            }
            Label reqLbl = UiLabels.wrapping(ingStr.toString(), UiStyles.caption(hudSkin, UiTheme.TEXT));
            row.add(reqLbl).growX().left().row();

            // Auto fill button
            if (isDiscovered) {
                final List<PantryEntry> matchingPantry = findPantryMatches(recipe, pantry);
                boolean canFill = matchingPantry.size() == recipe.ingredients.size();

                TextButton fillBtn = createActionButton("Auto-Fill Pot", canFill);
                fillBtn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        cauldronIngredients.clear();
                        cauldronIngredients.addAll(matchingPantry);
                        currentTab = 1;
                        refreshAll();
                    }
                });
                row.add(fillBtn).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).right().padTop(4).row();
            }

            rightPanelContent.add(row).expandX().fillX().padBottom(10).row();
        }
    }

    private List<PantryEntry> findPantryMatches(CookingRecipe recipe, List<PantryEntry> pantry) {
        List<PantryEntry> matches = new ArrayList<>();
        List<PantryEntry> available = new ArrayList<>(pantry);

        for (Item.ItemType reqType : recipe.ingredients) {
            PantryEntry found = null;
            for (PantryEntry pe : available) {
                if (pe.item.getType() == reqType) {
                    found = pe;
                    break;
                }
            }
            if (found != null) {
                matches.add(found);
                available.remove(found);
            }
        }
        return matches;
    }

    // --- TAB 3: REST BY THE HEARTH ---
    private void buildTabRest() {
        Label tabTitle = new Label("REST BY THE HEARTH FIRE",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        rightPanelContent.add(tabTitle).left().padBottom(6).row();

        Label desc = new Label("Stoke the hearth fire with dry kindling. Warming yourself restores bodily vigor,\nheals hypothermia, replenishes health, and advances the clock by 45 minutes.\nCosts 1 Kindling.",
                new Label.LabelStyle(hudSkin.getFontMain(), Color.LIGHT_GRAY));
        desc.setWrap(true);
        rightPanelContent.add(desc).width(1000).left().padBottom(25).row();

        int skill = player.getStats().getCookingSkill();
        int healAmt = 15 + (skill * 4);

        Table statsBox = new Table();
        statsBox.setBackground(hudSkin.getSlotRecessed());
        statsBox.pad(20);

        statsBox.add(new Label("- Health Restored: +" + healAmt + " HP",
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_HP_RED))).left().padBottom(6).row();
        statsBox.add(new Label("- Body Warmth: stabilised to a normal 37.0C",
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_TEMP_ORANGE))).left().padBottom(6).row();
        statsBox.add(new Label("- Satiety Restored: +20 Sustenance",
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_FOOD_GREEN))).left().padBottom(6).row();
        statsBox.add(new Label("- Time Advanced: +45 minutes",
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT))).left().row();

        rightPanelContent.add(statsBox).expandX().fillX().padBottom(25).row();

        boolean hasKindling = player.getStats().getKindlingCount() >= 1;
        Table restRow = new Table();
        addActionWithCaption(restRow, "REST AND STOKE THE FIRE",
                hasKindling ? "Costs 1 kindling" : "Needs 1 kindling - have none",
                hasKindling, this::performRestAction);
        rightPanelContent.add(restRow).left().row();
    }

    // -------------------------------------------------------------------------
    // COOKING EXECUTION
    // -------------------------------------------------------------------------

    private void cookMealAction(List<Item> ingredients, boolean feastImmediately) {
        if (player.getStats().getKindlingCount() < 1 || player.getStats().getCookingWaterCount() < 1) {
            feedbackLabel.setText("Lacking fuel or water at the hearth!");
            feedbackLabel.setColor(Color.valueOf("E57373"));
            return;
        }

        // 1. Deduct fuel & water
        player.getStats().modifyKindlingCount(-1);
        player.getStats().modifyCookingWaterCount(-1);

        // 2. Determine Recipe or Procedural Dish
        CookingRecipe recipe = cookingManager.findMatchingRecipe(ingredients);
        boolean synergy = cookingManager.detectSynergy(ingredients);
        int skill = player.getStats().getCookingSkill();
        int duration = cookingManager.calculateEffectDuration(skill, synergy);

        String mealName;
        List<StatusEffectType> mealEffects = new ArrayList<>();

        if (recipe != null) {
            mealName = recipe.name;
            mealEffects.addAll(recipe.guaranteedEffects);
            cookingManager.discoverRecipe(recipe.name);
        } else {
            mealName = cookingManager.generateProceduralMealName(ingredients);
            for (Item ingr : ingredients) {
                MonsterType src = ingr.getCorpseSource();
                if (src != null) {
                    cookingManager.identifyGib(src);
                    StatusEffectType eff = cookingManager.getEffectForMonster(src);
                    if (eff != null && !mealEffects.contains(eff)) mealEffects.add(eff);
                }
            }
            if (mealEffects.isEmpty()) {
                mealEffects.add(StatusEffectType.HEALTHY);
                mealEffects.add(StatusEffectType.RECOVERING);
            }
        }

        // 3. Create Item (Meal or Potion)
        Item cookedItem;
        if (recipe != null && recipe.resultItemType != null) {
            cookedItem = game.getItemDataManager().createItem(recipe.resultItemType,
                    (int) player.getPosition().x, (int) player.getPosition().y,
                    ItemColor.WHITE, game.getAssetManager());
        } else {
            cookedItem = game.getItemDataManager().createItem(Item.ItemType.MEAL,
                    (int) player.getPosition().x, (int) player.getPosition().y,
                    ItemColor.WHITE, game.getAssetManager());
            cookedItem.setName(mealName);
            cookedItem.setMealEffectDuration(duration);
            for (StatusEffectType eff : mealEffects) cookedItem.addMealEffect(eff);
        }

        // 4. Consume ingredients from pack first, then chest (never in field mode)
        // One ration per ingredient slot, never the whole stack.
        for (Item ingr : ingredients) {
            if (!player.getInventory().consumeOne(ingr) && !fieldMode) {
                ShelterChest.getInstance().consumeOne(ingr);
            }
        }
        ShelterChest.getInstance().save();

        cauldronIngredients.clear();
        player.getStats().incrementCookingSkill();

        // 5. Feast or Pack
        if (feastImmediately && cookedItem.getType() == Item.ItemType.MEAL) {
            player.feastOnMeal(cookedItem);
            feedbackLabel.setText("Feasted upon " + mealName + "! Metabolizing active boons.");
            feedbackLabel.setColor(HudSkin.COL_FOOD_GREEN);
        } else {
            if (player.getInventory().pickupToBackpack(cookedItem)) {
                feedbackLabel.setText("Prepared " + cookedItem.getDisplayName() + " into pack.");
            } else if (!fieldMode) {
                ShelterChest.getInstance().addItem(cookedItem);
                ShelterChest.getInstance().save();
                feedbackLabel.setText("Pack full! Stored " + cookedItem.getDisplayName() + " in Shelter Chest.");
            } else {
                feedbackLabel.setText("Pack full! " + cookedItem.getDisplayName() + " spoils on the ground.");
            }
            feedbackLabel.setColor(HudSkin.COL_GOLD_BRIGHT);
        }

        refreshAll();
    }

    private void performRestAction() {
        if (player.getStats().getKindlingCount() < 1) {
            feedbackLabel.setText("You need dry Kindling to stoke the hearth fire.");
            feedbackLabel.setColor(Color.valueOf("E57373"));
            return;
        }

        player.getStats().modifyKindlingCount(-1);

        int skill = player.getStats().getCookingSkill();
        int healAmt = 15 + (skill * 4);
        player.getStats().healWithTrait(healAmt);
        player.getStats().modifySatiety(20f);

        // Warm body temperature to 37.0°C
        player.getStats().setBodyTemperature(com.bpm.minotaur.gamedata.player.PlayerStats.BODY_TEMP_NORMAL);

        // Deep restorative rest at sanctuary hearth
        player.restAtSanctuary(parentScreen.getEventManager());

        // Advance Day/Night time by 45 minutes
        if (worldManager != null && worldManager.getDayNightManager() != null) {
            worldManager.getDayNightManager().advanceMinutes(45f);
        }

        parentScreen.getEventManager().addEvent(
                new GameEvent("You rest by the hearth fire. Embers crackle warmly as 45 minutes pass. (+" + healAmt + " HP)", 3.5f));
        feedbackLabel.setText("Rested by the hearth fire. Healed " + healAmt + " HP and warmed to 37.0C.");
        feedbackLabel.setColor(HudSkin.COL_FOOD_GREEN);

        refreshAll();
    }

    // -------------------------------------------------------------------------
    // BaseScreen Overrides & Input
    // -------------------------------------------------------------------------

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
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.O || keycode == Input.Keys.I) {
            game.setScreen(parentScreen);
            return true;
        }
        return tabs != null && tabs.handleKey(keycode);
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
