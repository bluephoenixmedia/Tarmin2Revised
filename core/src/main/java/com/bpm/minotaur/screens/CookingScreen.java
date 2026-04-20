package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
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
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CookingManager;
import com.bpm.minotaur.managers.CookingManager.CookingRecipe;

import java.util.ArrayList;
import java.util.List;

public class CookingScreen extends BaseScreen {

    private final GameScreen parentScreen;
    private final Player player;
    private final CookingManager cookingManager;

    private Stage stage;
    private Table rootTable;
    private Label resourcesLabel;
    private Label skillLabel;
    private Label statusLabel;
    private Label recipePreviewLabel;
    private Label synergyLabel;
    private Table ingredientsTable;
    private Table inventoryTable;

    private List<Item> selectedIngredients = new ArrayList<>();

    // UI Resources
    private BitmapFont font;
    private LabelStyle labelStyle;
    private TextButtonStyle buttonStyle;
    private TextButtonStyle accentButtonStyle;
    private Texture whitePixel;
    private Texture potTexture;

    public CookingScreen(Tarmin2 game, GameScreen parentScreen, Player player,
            com.bpm.minotaur.managers.WorldManager worldManager) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.cookingManager = worldManager.getCookingManager();
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        Gdx.input.setInputProcessor(stage);

        font = new BitmapFont();
        font.getData().setScale(2f);
        labelStyle = new LabelStyle(font, Color.WHITE);

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.DARK_GRAY);
        pixmap.fill();
        whitePixel = new Texture(pixmap);
        pixmap.dispose();

        buttonStyle = new TextButtonStyle();
        buttonStyle.font = font;
        buttonStyle.up = new TextureRegionDrawable(whitePixel);
        buttonStyle.down = new TextureRegionDrawable(whitePixel).tint(Color.LIGHT_GRAY);
        buttonStyle.over = new TextureRegionDrawable(whitePixel).tint(Color.GRAY);
        buttonStyle.fontColor = Color.WHITE;
        buttonStyle.disabledFontColor = Color.GRAY;

        // Accent style for important actions (orange tint on hover)
        Pixmap accentPix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        accentPix.setColor(new Color(0.3f, 0.15f, 0f, 1f));
        accentPix.fill();
        Texture accentTex = new Texture(accentPix);
        accentPix.dispose();
        accentButtonStyle = new TextButtonStyle();
        accentButtonStyle.font = font;
        accentButtonStyle.up = new TextureRegionDrawable(accentTex);
        accentButtonStyle.down = new TextureRegionDrawable(accentTex).tint(Color.LIGHT_GRAY);
        accentButtonStyle.over = new TextureRegionDrawable(accentTex).tint(new Color(1f, 0.6f, 0.1f, 1f));
        accentButtonStyle.fontColor = Color.ORANGE;
        accentButtonStyle.disabledFontColor = Color.GRAY;

        try {
            potTexture = new Texture(Gdx.files.internal("images/items/cookingpot.png"));
        } catch (Exception e) {
            Gdx.app.error("CookingScreen", "Could not load cookingpot.png");
            potTexture = whitePixel;
        }

        buildUI();
    }

    private void buildUI() {
        rootTable = new Table();
        rootTable.setFillParent(true);
        stage.addActor(rootTable);

        // Title row
        Label title = new Label("COOKING", labelStyle);
        title.setAlignment(Align.center);
        title.setFontScale(3f);
        title.setColor(Color.ORANGE);
        rootTable.add(title).padTop(20).colspan(3).row();

        // Resource + skill row
        updateResourcesLabel();

        // Main three-column layout: [cauldron | ingredient pot | inventory]
        Table leftTable  = new Table(); // Cauldron image + cook/rest buttons
        Table centerTable = new Table(); // Selected ingredients + labels
        Table rightTable = new Table(); // Inventory list

        // -------- LEFT: Pot image + primary buttons --------
        Image potImage = new Image(potTexture);
        leftTable.add(potImage).size(220, 220).padBottom(10).row();

        // Recipe preview label (shown when ingredients hint at a recipe)
        recipePreviewLabel = new Label("", labelStyle);
        recipePreviewLabel.setColor(Color.YELLOW);
        recipePreviewLabel.setAlignment(Align.center);
        recipePreviewLabel.setWrap(true);
        leftTable.add(recipePreviewLabel).width(320).padBottom(6).row();

        // Synergy label
        synergyLabel = new Label("", labelStyle);
        synergyLabel.setColor(new Color(0.4f, 1f, 0.4f, 1f));
        synergyLabel.setAlignment(Align.center);
        leftTable.add(synergyLabel).padBottom(10).row();

        TextButton cookBtn = new TextButton("COOK MEAL", accentButtonStyle);
        cookBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (selectedIngredients.isEmpty()) {
                    statusLabel.setText("Add at least 1 ingredient.");
                    return;
                }
                if (player.getStats().getKindlingCount() < 1 || player.getStats().getCookingWaterCount() < 1) {
                    statusLabel.setText("Not enough Kindling or Water!");
                    return;
                }
                performCooking();
            }
        });
        leftTable.add(cookBtn).width(300).height(70).padTop(10).row();

        // Rest button — costs 1 kindling, heals based on skill
        TextButton restBtn = new TextButton("REST (1 Kindling)", buttonStyle);
        restBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                performCampfireRest();
            }
        });
        leftTable.add(restBtn).width(300).height(60).padTop(6).row();

        // Gib Codex button
        TextButton codexBtn = new TextButton("GIB CODEX", buttonStyle);
        codexBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // Return selected ingredients to inventory before switching screens
                for (Item item : selectedIngredients) {
                    player.getInventory().pickupToBackpack(item);
                }
                selectedIngredients.clear();
                game.setScreen(new GibCodexScreen(game, CookingScreen.this, cookingManager));
            }
        });
        leftTable.add(codexBtn).width(300).height(60).padTop(6).row();

        // -------- CENTER: Selected ingredients --------
        Label potLabel = new Label("In the Pot (Max 3)", labelStyle);
        potLabel.setAlignment(Align.center);
        centerTable.add(potLabel).padBottom(8).row();

        ingredientsTable = new Table();
        centerTable.add(ingredientsTable).width(420).height(260).row();

        // -------- RIGHT: Inventory --------
        Label invLabel = new Label("Ingredients", labelStyle);
        rightTable.add(invLabel).padBottom(8).row();

        inventoryTable = new Table();
        ScrollPane scrollPane = new ScrollPane(inventoryTable);
        scrollPane.setFadeScrollBars(false);
        rightTable.add(scrollPane).width(560).height(560).row();

        rootTable.add(leftTable).expand().fill().pad(20);
        rootTable.add(centerTable).expand().fill().pad(20);
        rootTable.add(rightTable).expand().fill().pad(20).row();

        // Status bar
        statusLabel = new Label("Ready to cook.", labelStyle);
        statusLabel.setAlignment(Align.center);
        statusLabel.setWrap(true);
        rootTable.add(statusLabel).pad(14).colspan(3).width(1800).row();

        // Back button
        TextButton backBtn = new TextButton("Back to Game", buttonStyle);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                for (Item item : selectedIngredients) {
                    player.getInventory().pickupToBackpack(item);
                }
                game.setScreen(parentScreen);
                dispose();
            }
        });
        rootTable.add(backBtn).width(400).height(70).padBottom(40).colspan(3);

        refreshUI();
    }

    // -------------------------------------------------------------------------
    // Resource display
    // -------------------------------------------------------------------------

    private void updateResourcesLabel() {
        if (resourcesLabel == null) {
            resourcesLabel = new Label("", labelStyle);
            skillLabel = new Label("", labelStyle);
            Table resRow = new Table();
            resRow.add(resourcesLabel).padRight(40);
            resRow.add(skillLabel);
            rootTable.add(resRow).pad(12).colspan(3).row();
        }

        int kindling = player.getStats().getKindlingCount();
        int water    = player.getStats().getCookingWaterCount();
        int skill    = player.getStats().getCookingSkill();

        resourcesLabel.setText("Kindling: " + kindling + "  |  Water: " + water);
        resourcesLabel.setColor((kindling < 1 || water < 1) ? Color.RED : Color.WHITE);

        int baseDur = cookingManager.calculateEffectDuration(skill, false);
        skillLabel.setText("Cook Skill: " + skill + "  (base " + baseDur + " turns/effect)");
        skillLabel.setColor(Color.CYAN);
    }

    // -------------------------------------------------------------------------
    // UI refresh
    // -------------------------------------------------------------------------

    private void refreshUI() {
        // -- Ingredient pot --
        ingredientsTable.clear();
        for (final Item item : selectedIngredients) {
            String label = item.getDisplayName();
            MonsterType src = item.getCorpseSource();
            if (src != null && cookingManager.isGibIdentified(src)) {
                StatusEffectType eff = cookingManager.getEffectForMonster(src);
                if (eff != null) label += " [" + formatEffect(eff) + "]";
            }
            TextButton btn = new TextButton(label + "  \u2715", buttonStyle); // × to remove
            btn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectedIngredients.remove(item);
                    player.getInventory().pickupToBackpack(item);
                    refreshUI();
                }
            });
            ingredientsTable.add(btn).width(390).height(55).pad(4).row();
        }

        // -- Recipe / synergy indicators --
        CookingRecipe matched = cookingManager.findMatchingRecipe(selectedIngredients);
        boolean synergy = cookingManager.detectSynergy(selectedIngredients);
        int skill = player.getStats().getCookingSkill();

        if (matched != null) {
            int dur = cookingManager.calculateEffectDuration(skill, synergy);
            recipePreviewLabel.setText("Recipe: " + matched.name + "\n" + matched.description + "\n(" + dur + " turns)");
            recipePreviewLabel.setColor(Color.YELLOW);
        } else {
            String hint = cookingManager.getPartialRecipeHint(selectedIngredients);
            if (hint != null) {
                recipePreviewLabel.setText("Partial match: " + hint + "\nAdd more ingredients...");
                recipePreviewLabel.setColor(new Color(0.8f, 0.8f, 0.3f, 1f));
            } else if (!selectedIngredients.isEmpty()) {
                boolean hasGibs = selectedIngredients.stream()
                        .anyMatch(i -> i.getCorpseSource() != null || i.getType().name().startsWith("GIB_"));
                recipePreviewLabel.setText(hasGibs ? "Potent Monster Stew" : "Bland Stew");
                recipePreviewLabel.setColor(hasGibs ? Color.WHITE : Color.LIGHT_GRAY);
            } else {
                recipePreviewLabel.setText("");
            }
        }

        if (synergy) {
            int bonusDur = cookingManager.calculateEffectDuration(skill, true);
            synergyLabel.setText("SYNERGY! Effects last " + bonusDur + " turns");
        } else {
            synergyLabel.setText("");
        }

        // -- Inventory list --
        inventoryTable.clear();
        List<Item> pack = player.getInventory().getMainInventory();
        for (final Item item : pack) {
            if (isCookable(item)) {
                String label = item.getDisplayName();
                MonsterType src = item.getCorpseSource();
                if (src != null) {
                    if (cookingManager.isGibIdentified(src)) {
                        StatusEffectType eff = cookingManager.getEffectForMonster(src);
                        if (eff != null) label += " [" + formatEffect(eff) + "]";
                    } else {
                        label += " [?]";
                    }
                }
                TextButton btn = new TextButton(label, buttonStyle);
                btn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        if (selectedIngredients.size() >= 3) {
                            statusLabel.setText("Pot is full! Max 3 ingredients.");
                            return;
                        }
                        player.getInventory().getMainInventory().remove(item);
                        selectedIngredients.add(item);
                        refreshUI();
                    }
                });
                inventoryTable.add(btn).width(520).height(58).pad(4).row();
            }
        }

        updateResourcesLabel();
    }

    private boolean isCookable(Item item) {
        String typeName = item.getType().name();
        return item.isFood() || typeName.startsWith("GIB_")
                || typeName.equals("MEAT") || typeName.equals("MONSTER_EYE");
    }

    // -------------------------------------------------------------------------
    // Cooking logic
    // -------------------------------------------------------------------------

    private void performCooking() {
        player.getStats().modifyKindlingCount(-1);
        player.getStats().modifyCookingWaterCount(-1);

        int skill     = player.getStats().getCookingSkill();
        boolean synergy = cookingManager.detectSynergy(selectedIngredients);
        int duration  = cookingManager.calculateEffectDuration(skill, synergy);

        Item meal = game.getItemDataManager().createItem(Item.ItemType.MEAL,
                (int) player.getPosition().x, (int) player.getPosition().y,
                ItemColor.WHITE, game.getAssetManager());

        List<StatusEffectType> effects = new ArrayList<>();
        String mealName;

        // 1. Check for a matching recipe first
        CookingRecipe recipe = cookingManager.findMatchingRecipe(selectedIngredients);

        if (recipe != null) {
            // Recipe match — guaranteed effects
            mealName = recipe.name;
            effects.addAll(recipe.guaranteedEffects);
        } else {
            // No recipe — fall back to gib random effects or bland
            boolean hasGibIngredient = false;
            for (Item ingr : selectedIngredients) {
                MonsterType source = ingr.getCorpseSource();
                if (source != null) {
                    StatusEffectType eff = cookingManager.getEffectForMonster(source);
                    if (eff != null) {
                        effects.add(eff);
                        meal.addMealSource(source);
                        hasGibIngredient = true;
                    }
                } else if (ingr.getMealEffects() != null) {
                    effects.addAll(ingr.getMealEffects());
                    hasGibIngredient = true;
                }
            }

            if (hasGibIngredient) {
                mealName = "Potent Monster Stew";
            } else {
                // Bland stew — give a mild HEALTHY / RECOVERING buff scaled by skill
                mealName = "Bland Stew";
                effects.add(StatusEffectType.RECOVERING);
                if (skill >= 3) effects.add(StatusEffectType.HEALTHY);
            }
        }

        for (StatusEffectType effect : effects) {
            meal.addMealEffect(effect);
        }

        // Store the skill-adjusted duration on the meal for consumption
        meal.setName(mealName);
        meal.setMealEffectDuration(duration);

        selectedIngredients.clear();

        player.getStats().incrementCookingSkill();

        String synMsg = synergy ? " (SYNERGY! " + duration + " turns)" : " (" + duration + " turns)";
        if (player.getInventory().pickupToBackpack(meal)) {
            statusLabel.setText("Cooked " + mealName + "!" + synMsg);
        } else {
            parentScreen.getMaze().addItem(meal);
            statusLabel.setText("Inventory full — " + mealName + " dropped." + synMsg);
        }

        refreshUI();
    }

    // -------------------------------------------------------------------------
    // Campfire rest
    // -------------------------------------------------------------------------

    private void performCampfireRest() {
        if (player.getStats().getKindlingCount() < 1) {
            statusLabel.setText("You need Kindling to rest by a fire.");
            return;
        }
        player.getStats().modifyKindlingCount(-1);

        int skill   = player.getStats().getCookingSkill();
        int healAmt = 5 + (skill * 2);

        player.getStats().heal(healAmt);
        player.getStats().modifySatiety(10 + skill);

        // Warm the body temperature back toward normal
        float temp = player.getStats().getBodyTemperature();
        if (temp < com.bpm.minotaur.gamedata.player.PlayerStats.BODY_TEMP_NORMAL) {
            player.getStats().setBodyTemperature(
                Math.min(com.bpm.minotaur.gamedata.player.PlayerStats.BODY_TEMP_NORMAL, temp + 1.5f));
        }

        parentScreen.getEventManager().addEvent(
            new GameEvent("You rest by the fire and recover " + healAmt + " HP.", 2.5f));
        statusLabel.setText("Rested. Recovered " + healAmt + " HP and some satiety.");
        updateResourcesLabel();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String formatEffect(StatusEffectType eff) {
        return eff.name().replace('_', ' ');
    }

    // -------------------------------------------------------------------------
    // BaseScreen overrides
    // -------------------------------------------------------------------------

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.02f, 0.05f, 1f);
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
        if (potTexture != null && potTexture != whitePixel) potTexture.dispose();
    }
}
