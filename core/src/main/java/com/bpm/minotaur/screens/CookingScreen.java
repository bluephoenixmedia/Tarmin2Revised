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
    private TextButton tabBtnWhipUp;
    private TextButton tabBtnCauldron;
    private TextButton tabBtnCookbook;
    private TextButton tabBtnRest;

    public CookingScreen(Tarmin2 game, GameScreen parentScreen, Player player, WorldManager worldManager) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.worldManager = worldManager;
        this.cookingManager = worldManager != null ? worldManager.getCookingManager() : new CookingManager();
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

        Table root = new Table();
        root.setFillParent(true);
        root.top().pad(25);

        // --- 1. HEADER ---
        Table header = new Table();
        Label titleLabel = new Label("THE SHELTER COOKING HEARTH",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        titleLabel.setFontScale(1.3f);
        header.add(titleLabel).center().row();

        Label subLabel = new Label("Simmer harvested beast gibs, prepare hearty expedition meals, and discover ancient culinary boons",
                new Label.LabelStyle(hudSkin.getFontSmall(), Color.LIGHT_GRAY));
        header.add(subLabel).center().padTop(4).row();

        root.add(header).fillX().padBottom(12).row();

        // --- 2. TAB SWITCHER BAR ---
        Table tabBar = new Table();
        tabBtnWhipUp = createTabButton("[ 1 : WHIP UP A MEAL ]", 0);
        tabBtnCauldron = createTabButton("[ 2 : CAULDRON (CUSTOM) ]", 1);
        tabBtnCookbook = createTabButton("[ 3 : COOKBOOK & CODEX ]", 2);
        tabBtnRest = createTabButton("[ 4 : REST BY HEARTH ]", 3);

        tabBar.add(tabBtnWhipUp).size(340, 48).padRight(15);
        tabBar.add(tabBtnCauldron).size(340, 48).padRight(15);
        tabBar.add(tabBtnCookbook).size(340, 48).padRight(15);
        tabBar.add(tabBtnRest).size(340, 48);

        root.add(tabBar).center().padBottom(12).row();

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
        ScrollPane leftScroll = new ScrollPane(leftPanelContent);
        leftScroll.setFadeScrollBars(false);
        leftContainer.add(leftScroll).expand().fill().pad(15);

        body.add(leftContainer).width(720).height(640).padRight(25);

        // Right Panel: Cooking Station Tab Content
        Table rightContainer = new Table();
        rightContainer.setBackground(hudSkin.getPanelBg());
        rightPanelContent = new Table();
        rightPanelContent.top().left();
        ScrollPane rightScroll = new ScrollPane(rightPanelContent);
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

        TextButton backBtn = createActionButton("[ ESC / O : Back to Shelter ]", true);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(parentScreen);
            }
        });
        footer.add(backBtn).size(320, 48).right().padRight(20);

        root.add(footer).fillX();

        stage.addActor(root);
    }

    private TextButton createTabButton(String text, final int tabIndex) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = hudSkin.getFontHeader();
        style.fontColor = Color.LIGHT_GRAY;
        style.overFontColor = HudSkin.COL_GOLD_BRIGHT;
        style.up = hudSkin.getSlotRecessed();
        style.down = hudSkin.getSlotActive();
        style.over = hudSkin.getSlotActive();

        TextButton btn = new TextButton(text, style);
        btn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                switchTab(tabIndex);
            }
        });
        return btn;
    }

    private TextButton createActionButton(String text, boolean enabled) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = hudSkin.getFontHeader();
        style.fontColor = enabled ? Color.WHITE : Color.DARK_GRAY;
        style.overFontColor = enabled ? HudSkin.COL_GOLD_BRIGHT : Color.DARK_GRAY;
        style.up = hudSkin.getSlotRecessed();
        style.down = hudSkin.getSlotActive();
        style.over = enabled ? hudSkin.getTooltipBg() : hudSkin.getSlotRecessed();
        style.disabled = hudSkin.getSlotRecessed();

        TextButton btn = new TextButton(text, style);
        btn.setDisabled(!enabled);
        return btn;
    }

    private void switchTab(int tabIndex) {
        this.currentTab = tabIndex;
        updateTabButtonStyles();
        refreshRightPanel();
    }

    private void updateTabButtonStyles() {
        tabBtnWhipUp.getLabel().setColor(currentTab == 0 ? HudSkin.COL_GOLD_BRIGHT : Color.LIGHT_GRAY);
        tabBtnCauldron.getLabel().setColor(currentTab == 1 ? HudSkin.COL_GOLD_BRIGHT : Color.LIGHT_GRAY);
        tabBtnCookbook.getLabel().setColor(currentTab == 2 ? HudSkin.COL_GOLD_BRIGHT : Color.LIGHT_GRAY);
        tabBtnRest.getLabel().setColor(currentTab == 3 ? HudSkin.COL_GOLD_BRIGHT : Color.LIGHT_GRAY);
    }

    private void refreshAll() {
        updateTabButtonStyles();
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
        // 2. Shelter Chest
        for (Item item : ShelterChest.getInstance().getItems()) {
            if (isCookable(item)) {
                list.add(new PantryEntry(item, true));
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

        Label title = new Label("UNIFIED SHELTER PANTRY",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        leftPanelContent.add(title).left().padBottom(6).row();

        Label sub = new Label("Draws from Backpack & Shelter Chest. Click to add to Cauldron.",
                new Label.LabelStyle(hudSkin.getFontSmall(), Color.GRAY));
        leftPanelContent.add(sub).left().padBottom(15).row();

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

            String sourceBadge = entry.fromChest ? "[CHEST]" : "[PACK]";
            Color sourceCol = entry.fromChest ? HudSkin.COL_GOLD_MUTED : HudSkin.COL_WATER_CYAN;
            Label badgeLbl = new Label(sourceBadge, new Label.LabelStyle(hudSkin.getFontSmall(), sourceCol));
            row.add(badgeLbl).padLeft(10).padRight(10);

            String name = entry.item.getDisplayName();
            MonsterType src = entry.item.getCorpseSource();
            if (src != null) {
                if (cookingManager.isGibIdentified(src)) {
                    StatusEffectType eff = cookingManager.getEffectForMonster(src);
                    if (eff != null) name += " [" + eff.name().replace('_', ' ') + "]";
                } else {
                    name += " [?]";
                }
            }
            Label nameLbl = new Label(name, new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE));
            row.add(nameLbl).expandX().left();

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
        previewBox.setBackground(hudSkin.getSlotRecessed());
        previewBox.pad(20);

        if (quickIngredients.isEmpty()) {
            Label noMeat = new Label("No basic meat, gibs, or forage available to whip up a meal.",
                    new Label.LabelStyle(hudSkin.getFontMain(), Color.valueOf("E57373")));
            previewBox.add(noMeat).center().row();
        } else {
            Label mealPreview = new Label("Dish: Campfire Hearty Stew",
                    new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_ANTIQUE));
            previewBox.add(mealPreview).left().padBottom(8).row();

            StringBuilder sb = new StringBuilder("Ingredients: ");
            for (int i = 0; i < quickIngredients.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(quickIngredients.get(i).getDisplayName());
            }
            Label ingLbl = new Label(sb.toString(), new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE));
            previewBox.add(ingLbl).left().padBottom(8).row();

            int skill = player.getStats().getCookingSkill();
            int dur = cookingManager.calculateEffectDuration(skill, false);
            Label boonLbl = new Label("Guaranteed Boons: HEALTHY, RECOVERING (" + dur + " turns)\nRestores high Satiety (+45), Hydration, and HP.",
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_FOOD_GREEN));
            previewBox.add(boonLbl).left().row();
        }

        rightPanelContent.add(previewBox).expandX().fillX().padBottom(25).row();

        // Action Buttons
        boolean hasResources = player.getStats().getKindlingCount() >= 1 && player.getStats().getCookingWaterCount() >= 1;
        boolean canCook = hasResources && !quickIngredients.isEmpty();

        Table btnRow = new Table();
        TextButton feastBtn = createActionButton("FEAST AT HEARTH (EAT NOW)", canCook);
        feastBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                cookMealAction(quickIngredients, true);
            }
        });
        btnRow.add(feastBtn).size(420, 56).padRight(25);

        TextButton packBtn = createActionButton("PACK FIELD RATIONS", canCook);
        packBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                cookMealAction(quickIngredients, false);
            }
        });
        btnRow.add(packBtn).size(420, 56);

        rightPanelContent.add(btnRow).left().row();
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
        previewBox.setBackground(hudSkin.getSlotRecessed());
        previewBox.pad(15);

        if (currentItems.isEmpty()) {
            Label hint = new Label("Select ingredients from the pantry to preview your meal.",
                    new Label.LabelStyle(hudSkin.getFontMain(), Color.GRAY));
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
                    new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
            previewBox.add(nameLbl).left().row();

            Label descLbl = new Label(dishDesc, new Label.LabelStyle(hudSkin.getFontMain(), Color.LIGHT_GRAY));
            previewBox.add(descLbl).left().padTop(4).padBottom(8).row();

            StringBuilder boons = new StringBuilder("Boons: ");
            for (int i = 0; i < previewEffects.size(); i++) {
                if (i > 0) boons.append(", ");
                boons.append(previewEffects.get(i).name().replace('_', ' '));
            }
            boons.append(" (").append(dur).append(" turns)");
            Label boonsLbl = new Label(boons.toString(), new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_FOOD_GREEN));
            previewBox.add(boonsLbl).left().row();

            if (synergy) {
                Label synLbl = new Label("ESSENCE SYNERGY! Duration doubled by harmonious donor monster traits.",
                        new Label.LabelStyle(hudSkin.getFontSmall(), Color.valueOf("FFD54F")));
                previewBox.add(synLbl).left().padTop(6).row();
            }
        }
        rightPanelContent.add(previewBox).expandX().fillX().padBottom(20).row();

        // Action Buttons
        boolean hasResources = player.getStats().getKindlingCount() >= 1 && player.getStats().getCookingWaterCount() >= 1;
        boolean canCook = hasResources && !currentItems.isEmpty();

        Table btnRow = new Table();
        TextButton feastBtn = createActionButton("FEAST AT HEARTH (EAT NOW)", canCook);
        feastBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                cookMealAction(currentItems, true);
            }
        });
        btnRow.add(feastBtn).size(420, 56).padRight(25);

        TextButton packBtn = createActionButton("PACK FIELD RATIONS", canCook);
        packBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                cookMealAction(currentItems, false);
            }
        });
        btnRow.add(packBtn).size(420, 56);

        rightPanelContent.add(btnRow).left().row();
    }

    // --- TAB 2: COOKBOOK & GIB CODEX ---
    private void buildTabCookbook() {
        Label tabTitle = new Label("THE HEARTH COOKBOOK & GIB CODEX",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        rightPanelContent.add(tabTitle).left().padBottom(6).row();

        Label desc = new Label("Documented recipes and identified monster essences. Click [Auto-Fill] to load ingredients into Cauldron.",
                new Label.LabelStyle(hudSkin.getFontMain(), Color.LIGHT_GRAY));
        rightPanelContent.add(desc).left().padBottom(15).row();

        List<PantryEntry> pantry = getUnifiedPantryItems();

        for (final CookingRecipe recipe : cookingManager.getAllRecipes()) {
            Table row = new Table();
            row.setBackground(hudSkin.getSlotRecessed());
            row.pad(10);

            boolean isDiscovered = cookingManager.isRecipeDiscovered(recipe.name);
            Label nameLbl = new Label(isDiscovered ? recipe.name : "??? Unknown Recipe",
                    new Label.LabelStyle(hudSkin.getFontHeader(), isDiscovered ? HudSkin.COL_GOLD_ANTIQUE : Color.GRAY));
            row.add(nameLbl).expandX().left().row();

            Label rDesc = new Label(isDiscovered ? recipe.description : "Experiment with monster gibs in the Cauldron to discover this recipe.",
                    new Label.LabelStyle(hudSkin.getFontSmall(), Color.LIGHT_GRAY));
            row.add(rDesc).left().padTop(2).padBottom(4).row();

            StringBuilder ingStr = new StringBuilder("Required: ");
            for (int i = 0; i < recipe.ingredients.size(); i++) {
                if (i > 0) ingStr.append(", ");
                ingStr.append(recipe.ingredients.get(i).name());
            }
            Label reqLbl = new Label(ingStr.toString(), new Label.LabelStyle(hudSkin.getFontSmall(), Color.WHITE));
            row.add(reqLbl).left().row();

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
                row.add(fillBtn).size(180, 36).right().padTop(4).row();
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

        statsBox.add(new Label("• Health Restored: +" + healAmt + " HP",
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_HP_RED))).left().padBottom(6).row();
        statsBox.add(new Label("• Body Warmth: Stabilized to cozy normal 37.0°C",
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_TEMP_ORANGE))).left().padBottom(6).row();
        statsBox.add(new Label("• Satiety Restored: +20 Sustenance",
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_FOOD_GREEN))).left().padBottom(6).row();
        statsBox.add(new Label("• Time Advanced: +45 Minutes (Day/Night Progression)",
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT))).left().row();

        rightPanelContent.add(statsBox).expandX().fillX().padBottom(25).row();

        boolean hasKindling = player.getStats().getKindlingCount() >= 1;
        TextButton restBtn = createActionButton("REST AND STOKE FIRE (1 Kindling)", hasKindling);
        restBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                performRestAction();
            }
        });
        rightPanelContent.add(restBtn).size(480, 56).left().row();
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

        // 3. Create Meal Item
        Item meal = game.getItemDataManager().createItem(Item.ItemType.MEAL,
                (int) player.getPosition().x, (int) player.getPosition().y,
                ItemColor.WHITE, game.getAssetManager());
        meal.setName(mealName);
        meal.setMealEffectDuration(duration);
        for (StatusEffectType eff : mealEffects) meal.addMealEffect(eff);

        // 4. Consume ingredients from pack first, then chest
        for (Item ingr : ingredients) {
            if (!player.getInventory().removeItem(ingr)) {
                ShelterChest.getInstance().removeItem(ingr);
            }
        }
        ShelterChest.getInstance().save();

        cauldronIngredients.clear();
        player.getStats().incrementCookingSkill();

        // 5. Feast or Pack
        if (feastImmediately) {
            player.feastOnMeal(meal);
            feedbackLabel.setText("Feasted upon " + mealName + "! Metabolizing active boons.");
            feedbackLabel.setColor(HudSkin.COL_FOOD_GREEN);
        } else {
            if (player.getInventory().pickupToBackpack(meal)) {
                feedbackLabel.setText("Packed " + mealName + " into your trail rations.");
            } else {
                ShelterChest.getInstance().addItem(meal);
                ShelterChest.getInstance().save();
                feedbackLabel.setText("Pack full! Packed " + mealName + " into Shelter Chest.");
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
        player.getStats().heal(healAmt);
        player.getStats().modifySatiety(20f);

        // Warm body temperature to 37.0°C
        player.getStats().setBodyTemperature(com.bpm.minotaur.gamedata.player.PlayerStats.BODY_TEMP_NORMAL);

        // Advance Day/Night time by 45 minutes
        if (worldManager != null && worldManager.getDayNightManager() != null) {
            worldManager.getDayNightManager().advanceMinutes(45f);
        }

        parentScreen.getEventManager().addEvent(
                new GameEvent("You rest by the hearth fire. Embers crackle warmly as 45 minutes pass. (+" + healAmt + " HP)", 3.5f));
        feedbackLabel.setText("Rested by the hearth fire. Healed " + healAmt + " HP and warmed to 37.0°C.");
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
        if (stage != null) stage.dispose();
        if (hudSkin != null) hudSkin.dispose();
    }
}
