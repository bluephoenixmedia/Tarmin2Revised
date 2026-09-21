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
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.AlchemyManager;
import com.bpm.minotaur.managers.AlchemyManager.Recipe;
import com.bpm.minotaur.managers.BalanceLogger;
import com.bpm.minotaur.rendering.HudSkin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Screen for Field/Shelter Alchemy and Potion Transmutation.
 * Styled via HudSkin dark-fantasy gold & onyx tokens, recipe cards,
 * reagent requirement counters, and live systemic toxicity meter.
 */
public class AlchemyScreen extends BaseScreen {

    private final GameScreen parentScreen;
    private final Player player;
    private final HudSkin hudSkin;

    private Stage stage;
    private Recipe selectedRecipe;
    private int selectedRecipeIndex = 0;

    private Table recipeListTable;
    private Table workbenchPanel;
    private Label toxicityValueLabel;
    private Label feedbackLabel;
    private TextButton brewBtn;

    public AlchemyScreen(Tarmin2 game, GameScreen parentScreen, Player player) {
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

        List<Recipe> recipes = AlchemyManager.getInstance().getRecipes();
        if (!recipes.isEmpty()) {
            selectedRecipeIndex = Math.min(selectedRecipeIndex, recipes.size() - 1);
            selectedRecipe = recipes.get(selectedRecipeIndex);
        }

        buildUI();
    }

    private void buildUI() {
        stage.clear();

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(hudSkin.getScreenBackdrop());
        root.pad(30f);
        stage.addActor(root);

        // --- 1. HEADER ---
        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(12f, 24f, 12f, 24f);

        Label title = new Label("ALCHEMICAL LABORATORY & TRANSMUTATION",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        Label subtitle = new Label("Distill monster organs, flora, and catalysts into potent combat draughts. Beware rising toxicity.",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));

        header.add(title).center().row();
        header.add(subtitle).center().padTop(4f);
        root.add(header).fillX().padBottom(14f).row();

        // --- 2. VITALS & TOXICITY STRIP ---
        Table vitalsStrip = new Table();
        vitalsStrip.setBackground(hudSkin.getSlotRecessed());
        vitalsStrip.pad(8f, 18f, 8f, 18f);

        int tox = (player != null && player.getStats() != null) ? player.getStats().getToxicity() : 0;
        Color toxColor = tox >= 76 ? HudSkin.COL_HP_CRITICAL : tox >= 26 ? HudSkin.COL_TEMP_ORANGE : HudSkin.COL_FOOD_GREEN;
        String toxStatus = tox >= 76 ? "LETHAL SEPSIS RISK" : tox >= 26 ? "MODERATE TOXICITY" : "CLEAN BLOODSTREAM";

        Label toxTitle = new Label("SYSTEMIC TOXICITY: ", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE));
        toxicityValueLabel = new Label(tox + "% (" + toxStatus + ")", new Label.LabelStyle(hudSkin.getFontMain(), toxColor));

        int packUsed = (player != null && player.getInventory() != null) ? player.getInventory().getMainInventory().size() : 0;
        Label packLabel = new Label("Backpack Capacity: " + packUsed + "/42 Slots",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));

        vitalsStrip.add(toxTitle).left();
        vitalsStrip.add(toxicityValueLabel).left().padLeft(6f);
        vitalsStrip.add().expandX();
        vitalsStrip.add(packLabel).right();

        root.add(vitalsStrip).fillX().padBottom(16f).row();

        // --- 3. MAIN 2-COLUMN CONTENT ---
        Table contentTable = new Table();

        // Left: Recipe List (680px)
        Table leftPanel = buildRecipeListPanel();
        contentTable.add(leftPanel).width(680f).top().fillY().padRight(18f);

        // Right: Workbench & Cauldron (1160px)
        Table rightPanel = buildWorkbenchPanel();
        contentTable.add(rightPanel).width(1160f).top().fillY().row();

        root.add(contentTable).expand().fill().padBottom(14f).row();

        // --- 4. FOOTER ---
        Table footer = buildFooterBar();
        root.add(footer).fillX();

        refreshRecipeList();
        refreshWorkbench();
    }

    private Table buildRecipeListPanel() {
        Table panel = new Table();
        panel.setBackground(hudSkin.getPanelBg());
        panel.pad(16f);
        panel.top();

        Label header = new Label("RECIPE CODEX",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_ANTIQUE));
        panel.add(header).left().padBottom(12f).row();

        recipeListTable = new Table();
        recipeListTable.top().left();

        ScrollPane scroll = new ScrollPane(recipeListTable);
        scroll.setFadeScrollBars(false);
        panel.add(scroll).expand().fill();

        return panel;
    }

    private Table buildWorkbenchPanel() {
        workbenchPanel = new Table();
        workbenchPanel.setBackground(hudSkin.getDoubleBorderPanel());
        workbenchPanel.pad(20f);
        workbenchPanel.top().left();
        return workbenchPanel;
    }

    private void refreshRecipeList() {
        recipeListTable.clear();
        List<Recipe> recipes = AlchemyManager.getInstance().getRecipes();

        for (int i = 0; i < recipes.size(); i++) {
            final int index = i;
            final Recipe recipe = recipes.get(i);
            final boolean isSelected = (recipe == selectedRecipe);
            final boolean canCraft = AlchemyManager.getInstance().canCraft(player, recipe);

            Table card = new Table();
            card.setBackground(isSelected ? hudSkin.getSlotActive() : hudSkin.getSlotRecessed());
            card.pad(10f, 14f, 10f, 14f);

            Table textCol = new Table();
            Label nameLabel = new Label("[" + (i + 1) + "] " + recipe.name,
                    new Label.LabelStyle(hudSkin.getFontMain(), isSelected ? HudSkin.COL_GOLD_BRIGHT : Color.WHITE));
            nameLabel.setEllipsis(true);

            String statusStr = canCraft ? "[ READY TO BREW ]" : "[ MISSING REAGENTS ]";
            // The selected row is filled gold, so the status has to flip to dark ink --
            // muted gold on gold was effectively invisible on exactly the row the player
            // is looking at.
            Color statusCol;
            if (isSelected) {
                statusCol = HudSkin.COL_TEXT_ON_GOLD;
            } else {
                statusCol = canCraft ? HudSkin.COL_FOOD_ON_DARK : HudSkin.COL_TEXT_MUTED;
            }
            Label statLabel = new Label(statusStr, new Label.LabelStyle(hudSkin.getFontSmall(), statusCol));

            textCol.add(nameLabel).left().row();
            textCol.add(statLabel).left().padTop(3f);

            card.add(textCol).left().expandX();

            card.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectedRecipeIndex = index;
                    selectedRecipe = recipe;
                    refreshRecipeList();
                    refreshWorkbench();
                }
            });

            recipeListTable.add(card).expandX().fillX().padBottom(8f).row();
        }
    }

    private void refreshWorkbench() {
        workbenchPanel.clear();

        if (selectedRecipe == null) {
            Label empty = new Label("Select an alchemical formula from the codex to inspect ingredients.",
                    new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_MUTED));
            workbenchPanel.add(empty).center().padTop(100f);
            return;
        }

        final boolean canCraft = AlchemyManager.getInstance().canCraft(player, selectedRecipe);

        Label header = new Label("BREWING STATION: " + selectedRecipe.name.toUpperCase(),
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        workbenchPanel.add(header).left().padBottom(14f).row();

        // Concoction Card
        Table potionCard = new Table();
        potionCard.setBackground(hudSkin.getSlotRecessed());
        potionCard.pad(14f);

        Label targetName = new Label("Yields: " + formatItemName(selectedRecipe.outputType),
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_ANTIQUE));
        Label desc = new Label(getPotionDescription(selectedRecipe.outputType),
                new Label.LabelStyle(hudSkin.getFontSmall(), Color.WHITE));
        desc.setWrap(true);

        potionCard.add(targetName).left().row();
        potionCard.add(desc).left().expandX().fillX().padTop(4f).row();
        workbenchPanel.add(potionCard).expandX().fillX().padBottom(18f).row();

        // Reagents List
        Label reqTitle = new Label("REQUIRED ALCHEMICAL REAGENTS:",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        workbenchPanel.add(reqTitle).left().padBottom(8f).row();

        Table reagentsTable = new Table();
        reagentsTable.left();

        Map<ItemType, Integer> playerCounts = countPlayerItems();
        for (Map.Entry<ItemType, Integer> req : selectedRecipe.inputs.entrySet()) {
            ItemType reqType = req.getKey();
            int needed = req.getValue();
            int owned = playerCounts.getOrDefault(reqType, 0);
            boolean hasEnough = owned >= needed;

            Table row = new Table();
            row.setBackground(hudSkin.getSlotRecessed());
            row.pad(8f, 14f, 8f, 14f);

            Label name = new Label(formatItemName(reqType),
                    new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE));
            String countStr = owned + " / " + needed + " (In Backpack)";
            Color countCol = hasEnough ? HudSkin.COL_FOOD_ON_DARK : HudSkin.COL_HP_ON_DARK;
            Label count = new Label(countStr, new Label.LabelStyle(hudSkin.getFontSmall(), countCol));

            row.add(name).left().expandX();
            row.add(count).right();

            reagentsTable.add(row).expandX().fillX().padBottom(6f).row();
        }
        workbenchPanel.add(reagentsTable).expandX().fillX().padBottom(18f).row();

        // Toxicity Warning
        Table toxWarning = new Table();
        toxWarning.setBackground(hudSkin.getSlotRecessed());
        toxWarning.pad(10f, 14f, 10f, 14f);

        Label toxWarnText = new Label("Biohazard Notice: Ingesting concoctions raises systemic toxicity by +15%. High toxicity causes delirium.",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_TEMP_ORANGE));
        toxWarnText.setWrap(true);
        toxWarning.add(toxWarnText).expandX().fillX();
        workbenchPanel.add(toxWarning).expandX().fillX().padBottom(20f).row();

        // Brew Button & Feedback
        Table actionRow = new Table();
        TextButton.TextButtonStyle brewStyle = new TextButton.TextButtonStyle();
        brewStyle.font = hudSkin.getFontHeader();
        brewStyle.up = hudSkin.getPrimaryButtonUp();
        brewStyle.down = hudSkin.getPrimaryButtonDown();
        brewStyle.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        brewStyle.disabledFontColor = HudSkin.COL_GOLD_MUTED;
        brewStyle.disabled = hudSkin.getSlotRecessed();

        brewBtn = new TextButton("[ENTER] Brew Concoction", brewStyle);
        brewBtn.pad(10f, 24f, 10f, 24f);
        brewBtn.setDisabled(!canCraft);
        brewBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (brewBtn.isDisabled()) return;
                performCraft(selectedRecipe);
            }
        });
        actionRow.add(brewBtn).left().padRight(16f);

        feedbackLabel = new Label("", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_BRIGHT));
        actionRow.add(feedbackLabel).left().expandX();

        workbenchPanel.add(actionRow).expandX().fillX().padTop(6f).row();
    }

    private Map<ItemType, Integer> countPlayerItems() {
        Map<ItemType, Integer> counts = new HashMap<>();
        if (player != null && player.getInventory() != null) {
            for (Item item : player.getInventory().getAllItems()) {
                if (item != null && item.getType() != null) {
                    counts.put(item.getType(), counts.getOrDefault(item.getType(), 0) + 1);
                }
            }
        }
        return counts;
    }

    private String formatItemName(ItemType type) {
        if (type == null) return "Unknown";
        String s = type.name().replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : s.toCharArray()) {
            if (c == ' ') {
                capitalize = true;
                sb.append(' ');
            } else if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String getPotionDescription(ItemType type) {
        if (type == null) return "Mysterious distillate.";
        switch (type) {
            case POTION_OF_HEALING:
                return "Curative draft that rapidly knits ruptured flesh and closes deep lacerations (+30 HP).";
            case POTION_VITALITY:
                return "Purifying tonic that restores stamina, breaks physical fever, and cures blood poisoning.";
            case POTION_FERAL_DRAUGHT:
                return "Violent stimulant distilled from beast flesh. Grants +4 Strength and ferocity, but strains heart.";
            case POTION_TITAN_SLUDGE:
                return "Dense, acrid extract that hardens the epidermal layer, granting temporary Armor Class and stagger resist.";
            default:
                return "Alchemical brew with restorative or augmenting properties.";
        }
    }

    private Table buildFooterBar() {
        Table footer = new Table();
        footer.setBackground(hudSkin.getPanelBg());
        footer.pad(12f, 20f, 12f, 20f);

        Label legend = new Label("[1-4] Quick Formula    [UP/DOWN] Select Recipe    [ENTER] Brew    [ESC] Return",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        footer.add(legend).left().expandX();

        TextButton.TextButtonStyle closeStyle = new TextButton.TextButtonStyle();
        closeStyle.font = hudSkin.getFontMain();
        closeStyle.up = hudSkin.getSlotRecessed();
        closeStyle.down = hudSkin.getSlotActive();
        closeStyle.fontColor = Color.WHITE;

        TextButton closeBtn = new TextButton("[ESC] Return", closeStyle);
        closeBtn.pad(8f, 20f, 8f, 20f);
        closeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                closeScreen();
            }
        });
        footer.add(closeBtn).right();

        return footer;
    }

    private void performCraft(Recipe recipe) {
        if (recipe == null) return;

        Item result = AlchemyManager.getInstance().craft(
                player,
                recipe,
                game.getItemDataManager(),
                game.getAssetManager());

        if (result != null) {
            if (parentScreen != null && parentScreen.getSoundManager() != null) {
                parentScreen.getSoundManager().playSalveApplySound();
            }
            BalanceLogger.getInstance().log("CRAFTING", "Crafted " + recipe.name);

            // Update UI state in place without jarring screen reload
            buildUI();
            if (feedbackLabel != null) {
                feedbackLabel.setText("Successfully brewed " + result.getDisplayName() + "!");
                feedbackLabel.setColor(HudSkin.COL_FOOD_GREEN);
            }
        } else {
            if (feedbackLabel != null) {
                feedbackLabel.setText("Brewing failed: Insufficient backpack space or missing reagents.");
                feedbackLabel.setColor(HudSkin.COL_HP_CRITICAL);
            }
        }
    }

    private void closeScreen() {
        if (parentScreen != null) {
            game.setScreen(parentScreen);
        } else {
            game.setScreen(new MainMenuScreen(game));
        }
        dispose();
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            closeScreen();
            return true;
        }
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            if (selectedRecipe != null && AlchemyManager.getInstance().canCraft(player, selectedRecipe)) {
                performCraft(selectedRecipe);
                return true;
            }
        }
        List<Recipe> recipes = AlchemyManager.getInstance().getRecipes();
        if (keycode == Input.Keys.UP || keycode == Input.Keys.W) {
            if (!recipes.isEmpty()) {
                selectedRecipeIndex = (selectedRecipeIndex - 1 + recipes.size()) % recipes.size();
                selectedRecipe = recipes.get(selectedRecipeIndex);
                refreshRecipeList();
                refreshWorkbench();
                return true;
            }
        }
        if (keycode == Input.Keys.DOWN || keycode == Input.Keys.S) {
            if (!recipes.isEmpty()) {
                selectedRecipeIndex = (selectedRecipeIndex + 1) % recipes.size();
                selectedRecipe = recipes.get(selectedRecipeIndex);
                refreshRecipeList();
                refreshWorkbench();
                return true;
            }
        }
        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
            int num = keycode - Input.Keys.NUM_1;
            if (num < recipes.size()) {
                selectedRecipeIndex = num;
                selectedRecipe = recipes.get(num);
                refreshRecipeList();
                refreshWorkbench();
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.04f, 0.03f, 0.02f, 1f);
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
