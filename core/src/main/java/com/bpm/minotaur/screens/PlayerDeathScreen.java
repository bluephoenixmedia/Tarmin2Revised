package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.managers.MusicManager;
import com.bpm.minotaur.rendering.HudSkin;

/**
 * Atmospheric transition screen displayed when the player perishes during an expedition.
 * Presents the casualties of the fall, loot loss/retention accounting, the growing
 * Doom Clock ("Tarmin's Hunger"), and guides the player seamlessly into awakening
 * back at the Starting Shelter to plan their next delve.
 */
public class PlayerDeathScreen extends BaseScreen {

    private final GameScreen parentScreen;
    private final int deathCount;
    private final int maxDeaths;
    private final float bridgeIntegrity;
    private final int lostItems;
    private final int retainedItems;
    private final String defeatLore;
    private final String epitaphCause;
    private final int depthReached;
    private final int monstersSlain;
    private final int divinitiesEarned;
    private final java.util.List<String> newUnlocks;
    private final HudSkin hudSkin;

    private Stage stage;
    private boolean transitionTriggered = false;

    public PlayerDeathScreen(Tarmin2 game, GameScreen parentScreen, int deathCount, int maxDeaths,
                             float bridgeIntegrity, int lostItems, int retainedItems, String defeatLore) {
        this(game, parentScreen, deathCount, maxDeaths, bridgeIntegrity, lostItems, retainedItems, defeatLore,
             "Fell in the Labyrinth", 1, 0, 0, null);
    }

    public PlayerDeathScreen(Tarmin2 game, GameScreen parentScreen, int deathCount, int maxDeaths,
                             float bridgeIntegrity, int lostItems, int retainedItems, String defeatLore,
                             String epitaphCause, int depthReached, int monstersSlain, int divinitiesEarned) {
        this(game, parentScreen, deathCount, maxDeaths, bridgeIntegrity, lostItems, retainedItems, defeatLore,
             epitaphCause, depthReached, monstersSlain, divinitiesEarned, null);
    }

    public PlayerDeathScreen(Tarmin2 game, GameScreen parentScreen, int deathCount, int maxDeaths,
                             float bridgeIntegrity, int lostItems, int retainedItems, String defeatLore,
                             String epitaphCause, int depthReached, int monstersSlain, int divinitiesEarned,
                             java.util.List<String> newUnlocks) {
        super(game);
        this.parentScreen = parentScreen;
        this.deathCount = deathCount;
        this.maxDeaths = maxDeaths;
        this.bridgeIntegrity = bridgeIntegrity;
        this.lostItems = lostItems;
        this.retainedItems = retainedItems;
        this.defeatLore = (defeatLore != null && !defeatLore.trim().isEmpty())
                ? defeatLore : "Your physical vessel collapsed in the labyrinth...";
        this.epitaphCause = (epitaphCause != null && !epitaphCause.trim().isEmpty())
                ? epitaphCause : "Slain in the Labyrinth";
        this.depthReached = Math.max(1, depthReached);
        this.monstersSlain = monstersSlain;
        this.divinitiesEarned = divinitiesEarned;
        this.newUnlocks = newUnlocks;
        this.hudSkin = new HudSkin();
    }

    @Override
    public void show() {
        MusicManager.getInstance().stop();
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        buildUI();
    }

    private void buildUI() {
        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(40, 50, 40, 50);

        // --- HEADER BANNER ---
        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(20, 40, 20, 40);

        Label perishedLabel = new Label("YOU HAVE FALLEN", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_HP_CRITICAL));
        header.add(perishedLabel).center().row();

        Label loreLabel = new Label(defeatLore, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        header.add(loreLabel).center().padTop(8).row();

        root.add(header).fillX().padBottom(24).row();

        // --- BODY: Three Column Overview Cards ---
        Table body = new Table();

        // 1. Left Card: Tarmin's Hunger / Doom Clock
        Table doomCard = new Table();
        doomCard.setBackground(hudSkin.getDoubleBorderPanel());
        doomCard.pad(22);

        Label doomTitle = new Label("TARMIN'S HUNGER", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        doomCard.add(doomTitle).left().padBottom(14).row();

        Label deathStat = new Label(String.format("EXPEDITION DEMISE:  %d  /  %d", deathCount, maxDeaths),
                new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE));
        doomCard.add(deathStat).left().padBottom(8).row();

        Label bridgeStat = new Label(String.format("BRIDGE INTEGRITY:  %d%%", (int) bridgeIntegrity),
                new Label.LabelStyle(hudSkin.getFontMain(), bridgeIntegrity <= 30f ? HudSkin.COL_HP_CRITICAL : HudSkin.COL_GOLD_ANTIQUE));
        doomCard.add(bridgeStat).left().padBottom(16).row();

        Label doomDesc = new Label("The Minotaur stirs beneath Castle Tarmin. With every demise, the boundary holding back the apocalyptic tide weakens. Should 50 deaths occur, the sacrificial ritual will consume all memory of your journey.",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        doomDesc.setWrap(true);
        doomCard.add(doomDesc).width(500).left().expandY().top().row();

        body.add(doomCard).width(560).expandY().fillY().padRight(20);

        // 2. Center Card: Expedition Epitaph & Milestones
        Table epitaphCard = new Table();
        epitaphCard.setBackground(hudSkin.getDoubleBorderPanel());
        epitaphCard.pad(22);

        Label epitaphTitle = new Label("EXPEDITION EPITAPH", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        epitaphCard.add(epitaphTitle).left().padBottom(14).row();

        Label causeLbl = new Label("CAUSE: " + epitaphCause,
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_HP_CRITICAL));
        causeLbl.setWrap(true);
        epitaphCard.add(causeLbl).width(530).left().padBottom(10).row();

        Label depthLbl = new Label(String.format("STRATA REACHED:  Depth %d", depthReached),
                new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE));
        epitaphCard.add(depthLbl).left().padBottom(6).row();

        Label killsLbl = new Label(String.format("FOES VANQUISHED:  %d", monstersSlain),
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_ANTIQUE));
        epitaphCard.add(killsLbl).left().padBottom(6).row();

        Label divEarnedLbl = new Label(String.format("DIVINITIES COLLECTED:  +%d", divinitiesEarned),
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_FOOD_GREEN));
        epitaphCard.add(divEarnedLbl).left().padBottom(14).row();

        if (newUnlocks != null && !newUnlocks.isEmpty()) {
            Label unlockTitle = new Label("DISCOVERIES UNLOCKED FOR FUTURE EXPEDITIONS:",
                    new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_FOOD_GREEN));
            epitaphCard.add(unlockTitle).width(530).left().padTop(6).padBottom(4).row();

            StringBuilder sb = new StringBuilder();
            for (String unlock : newUnlocks) {
                sb.append("- ").append(unlock).append("\n");
            }
            Label unlockItems = new Label(sb.toString().trim(),
                    new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT));
            unlockItems.setWrap(true);
            epitaphCard.add(unlockItems).width(530).left().padBottom(6).row();

            Label unlockNote = new Label("These discoveries will now appear in future dungeon loot and merchant inventories.",
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            unlockNote.setWrap(true);
            epitaphCard.add(unlockNote).width(530).left().expandY().top().row();
        } else {
            Label unlockDesc = new Label("Progression Note: Every expedition attunes the procedural generation of future mazes. Deeper delves and greater triumphs will yield further discoveries for future runs.",
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            unlockDesc.setWrap(true);
            epitaphCard.add(unlockDesc).width(530).left().expandY().top().row();
        }

        body.add(epitaphCard).width(590).expandY().fillY().padRight(20);

        // 3. Right Card: Casualties & Stash Status
        Table lootCard = new Table();
        lootCard.setBackground(hudSkin.getDoubleBorderPanel());
        lootCard.pad(22);

        Label lootTitle = new Label("EXPEDITION CASUALTIES", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        lootCard.add(lootTitle).left().padBottom(14).row();

        Label lostLbl = new Label(String.format("UNEQUIPPED ITEMS LOST:  %d", lostItems),
                new Label.LabelStyle(hudSkin.getFontMain(), lostItems > 0 ? HudSkin.COL_HP_RED : HudSkin.COL_FOOD_GREEN));
        lootCard.add(lostLbl).left().padBottom(6).row();

        Label keptLbl = new Label(String.format("ITEMS SECURED (Retention):  %d", retainedItems),
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_FOOD_GREEN));
        lootCard.add(keptLbl).left().padBottom(6).row();

        Label kitLbl = new Label("TRAVEL CRAFTING KITS:  PRESERVED",
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_FOOD_GREEN));
        lootCard.add(kitLbl).left().padBottom(6).row();

        Label eqLbl = new Label("EQUIPPED WEAPONS & ARMOR:  PRESERVED",
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_ANTIQUE));
        lootCard.add(eqLbl).left().padBottom(14).row();

        Label stashDesc = new Label("Your Stash Chest inside the Starting Shelter remains safe. The ruins and wilderness beyond will reconfigure from fresh Ley-lines, rolling new terrain, monsters, and treasures for your next delve.",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        stashDesc.setWrap(true);
        lootCard.add(stashDesc).width(500).left().expandY().top().row();

        body.add(lootCard).width(560).expandY().fillY();

        root.add(body).expand().fill().padBottom(24).row();

        // --- FOOTER: AWAKEN CTA BUTTON ---
        Table footer = new Table();
        footer.setBackground(hudSkin.getPanelBg());
        footer.pad(18, 30, 18, 30);

        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = hudSkin.getFontHeader();
        btnStyle.up = hudSkin.getPrimaryButtonUp();
        btnStyle.down = hudSkin.getPrimaryButtonDown();
        btnStyle.over = hudSkin.getPrimaryButtonDown();
        btnStyle.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        btnStyle.overFontColor = HudSkin.COL_TEXT_ON_GOLD;

        TextButton awakenBtn = new TextButton("AWAKEN IN THE SHELTER  [ENTER / SPACE]", btnStyle);
        awakenBtn.getLabel().setFontScale(1.0f);
        awakenBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                awakenInShelter();
            }
        });

        footer.add(awakenBtn).minWidth(680).height(64).center();
        root.add(footer).fillX();

        stage.addActor(root);
    }

    private void awakenInShelter() {
        if (transitionTriggered) return;
        transitionTriggered = true;
        parentScreen.respawnInShelter(lostItems, retainedItems, deathCount, bridgeIntegrity);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE || keycode == Input.Keys.ESCAPE) {
            awakenInShelter();
            return true;
        }
        return false;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(14f / 255f, 4f / 255f, 4f / 255f, 1f);
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
