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
import com.bpm.minotaur.rendering.DiscoveryCard;
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
    private final String epitaphCause;
    private final int depthReached;
    private final int monstersSlain;
    private final int divinitiesEarned;
    private final java.util.List<String> newUnlocks;
    /**
     * The unlocked item types, when the caller has them. Cards need the type rather than the
     * display name, because icons are keyed on it in the packed atlases.
     */
    private final java.util.List<com.bpm.minotaur.gamedata.item.Item.ItemType> newUnlockTypes;
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
        this(game, parentScreen, deathCount, maxDeaths, bridgeIntegrity, lostItems, retainedItems,
                defeatLore, epitaphCause, depthReached, monstersSlain, divinitiesEarned, newUnlocks, null);
    }

    public PlayerDeathScreen(Tarmin2 game, GameScreen parentScreen, int deathCount, int maxDeaths,
                             float bridgeIntegrity, int lostItems, int retainedItems, String defeatLore,
                             String epitaphCause, int depthReached, int monstersSlain, int divinitiesEarned,
                             java.util.List<String> newUnlocks,
                             java.util.List<com.bpm.minotaur.gamedata.item.Item.ItemType> newUnlockTypes) {
        super(game);
        this.newUnlockTypes = newUnlockTypes;
        this.parentScreen = parentScreen;
        this.deathCount = deathCount;
        this.maxDeaths = maxDeaths;
        this.bridgeIntegrity = bridgeIntegrity;
        this.lostItems = lostItems;
        this.retainedItems = retainedItems;
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

        // Full-bleed shape per docs/UX/ux-standard.md section 2: header bar, body panels, footer
        // hint bar, filling the frame rather than floating as a centred block.
        Table root = new Table();
        root.setFillParent(true);
        root.top().pad(28, 50, 28, 50);

        root.add(buildHeader()).growX().row();
        root.add(buildTrophyRow()).growX().padTop(26f).row();
        root.add(buildStatRow()).growX().padTop(26f).row();
        root.add().expandY().row();
        root.add(buildFooter()).growX().padTop(26f).row();

        stage.addActor(root);
    }

    private Table buildHeader() {
        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(14f);

        Label title = new Label("YOU HAVE FALLEN",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_HP_CRITICAL));
        header.add(title).row();

        Label cause = new Label(epitaphCause,
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_TEXT_ON_DARK));
        cause.setWrap(true);
        cause.setAlignment(com.badlogic.gdx.utils.Align.center);
        header.add(cause).growX().padTop(6f).row();
        return header;
    }

    /**
     * The discoveries, as the centrepiece.
     *
     * <p>Most deaths unlock nothing, so the empty case is designed rather than left to collapse
     * into a hole where the best thing on the screen should be.
     */
    private Table buildTrophyRow() {
        Table row = new Table();

        java.util.List<com.bpm.minotaur.gamedata.item.Item.ItemType> types = newUnlockTypes;
        int count = (types != null) ? types.size() : (newUnlocks != null ? newUnlocks.size() : 0);

        if (count == 0) {
            Label none = new Label("THE DEPTHS YIELDED NOTHING",
                    new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_TEXT_MUTED));
            row.add(none).padTop(28f).padBottom(28f).row();
            return row;
        }

        com.bpm.minotaur.gamedata.item.ItemDataManager itemData =
                com.bpm.minotaur.managers.UnlockManager.getInstance().getItemDataManager();
        com.badlogic.gdx.assets.AssetManager assets = game.getAssetManager();

        Table cards = new Table();
        for (int i = 0; i < count; i++) {
            com.bpm.minotaur.gamedata.item.Item.ItemType type = (types != null) ? types.get(i) : null;
            String friendly;
            if (type != null) {
                friendly = com.bpm.minotaur.managers.UnlockManager.getInstance().displayNameFor(type);
            } else {
                friendly = newUnlocks.get(i);
            }
            DiscoveryCard card = new DiscoveryCard(hudSkin, type, friendly, itemData, assets, i * 0.25f);
            cards.add(card).size(DiscoveryCard.CARD_WIDTH, DiscoveryCard.CARD_HEIGHT).padLeft(14f).padRight(14f);
        }
        row.add(cards).row();

        Label caption = new Label("WRESTED FROM THE DEPTHS",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        row.add(caption).padTop(10f).row();
        return row;
    }

    private Table buildStatRow() {
        Table row = new Table();
        row.add(buildHungerPanel()).growX().uniformX().padRight(18f);
        row.add(buildEpitaphPanel()).growX().uniformX().padRight(18f);
        row.add(buildCasualtiesPanel()).growX().uniformX();
        return row;
    }

    private Table buildHungerPanel() {
        Table panel = newPanel("TARMIN'S HUNGER");
        addStat(panel, "DEMISE", deathCount + " / " + maxDeaths);
        addStat(panel, "BRIDGE", ((int) bridgeIntegrity) + "%");
        panel.add(buildBridgeBar()).growX().height(14f).padTop(4f).colspan(2).row();

        // One escalating line instead of a fixed lore paragraph. The paragraph was identical on
        // every death, so by the tenth it was furniture; this makes the panel read as a meter.
        float pressure = (maxDeaths > 0) ? (deathCount / (float) maxDeaths) : 0f;
        String line;
        Color lineColor;
        if (pressure >= 0.9f) {
            line = "The ritual is all but complete.";
            lineColor = HudSkin.COL_HP_CRITICAL;
        } else if (pressure >= 0.6f) {
            line = "The boundary is failing.";
            lineColor = HudSkin.COL_HP_ON_DARK;
        } else if (pressure >= 0.3f) {
            line = "The Minotaur stirs beneath the castle.";
            lineColor = HudSkin.COL_TEMP_ON_DARK;
        } else {
            line = "Something beneath the castle took note.";
            lineColor = HudSkin.COL_TEXT_MUTED;
        }
        Label lore = new Label(line, new Label.LabelStyle(hudSkin.getFontSmall(), lineColor));
        lore.setWrap(true);
        panel.add(lore).growX().colspan(2).padTop(10f).row();
        return panel;
    }

    private Table buildEpitaphPanel() {
        Table panel = newPanel("EXPEDITION EPITAPH");
        addStat(panel, "DEPTH", String.valueOf(depthReached));
        addStat(panel, "FOES", String.valueOf(monstersSlain));
        addStat(panel, "DIVINITY", "+" + divinitiesEarned);
        return panel;
    }

    private Table buildCasualtiesPanel() {
        Table panel = newPanel("EXPEDITION CASUALTIES");
        addStat(panel, "LOST", String.valueOf(lostItems));
        addStat(panel, "SECURED", String.valueOf(retainedItems));
        addStat(panel, "KITS", "PRESERVED");
        addStat(panel, "GEAR", "PRESERVED");
        return panel;
    }

    private Table newPanel(String title) {
        Table panel = new Table();
        panel.setBackground(hudSkin.getDoubleBorderPanel());
        panel.pad(14f);
        panel.top();
        Label heading = new Label(title,
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT));
        heading.setWrap(true);
        panel.add(heading).growX().colspan(2).padBottom(8f).row();
        return panel;
    }

    /**
     * Adds a label/value pair.
     *
     * <p>Every label wraps and every cell grows rather than being pinned to a pixel width. Pinning
     * the cells while leaving the labels unwrapped is what made the old panels overflow into each
     * other, which docs/UX/ux-standard.md already warned against.
     */
    private void addStat(Table panel, String label, String value) {
        Label l = new Label(label, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_TEXT_MUTED));
        Label v = new Label(value, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_TEXT_ON_DARK));
        // Only the label column absorbs slack. Growing both pushed the value past the panel edge,
        // and a single unbreakable token like PRESERVED cannot wrap its way back inside.
        panel.add(l).left().expandX().fillX().padBottom(3f);
        panel.add(v).right().padBottom(3f).row();
    }

    /** Bridge integrity as a bar: 2% as bare text carries no weight. */
    private Table buildBridgeBar() {
        float fraction = com.badlogic.gdx.math.MathUtils.clamp(bridgeIntegrity / 100f, 0f, 1f);

        com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable pixel =
                new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(
                        new com.badlogic.gdx.graphics.g2d.TextureRegion(hudSkin.getWhitePixel()));

        Table bar = new Table();
        Image track = new Image(pixel);
        track.setColor(HudSkin.COL_STONE_DARK);
        Image fill = new Image(pixel);
        fill.setColor(fraction >= 0.9f ? HudSkin.COL_HP_CRITICAL : HudSkin.COL_HP_RED);

        com.badlogic.gdx.scenes.scene2d.ui.Stack stack = new com.badlogic.gdx.scenes.scene2d.ui.Stack();
        Table trackWrap = new Table();
        trackWrap.add(track).grow();
        Table fillWrap = new Table();
        fillWrap.add(fill).growY().width(Math.max(2f, fraction * 220f)).left();
        fillWrap.left();
        stack.add(trackWrap);
        stack.add(fillWrap);

        bar.add(stack).grow();
        return bar;
    }

    private Table buildFooter() {
        Table footer = new Table();
        footer.setBackground(hudSkin.getPanelBg());
        footer.pad(12f, 24f, 12f, 24f);

        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = hudSkin.getFontHeader();
        btnStyle.up = hudSkin.getPrimaryButtonUp();
        btnStyle.down = hudSkin.getPrimaryButtonDown();
        btnStyle.over = hudSkin.getPrimaryButtonDown();
        btnStyle.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        btnStyle.overFontColor = HudSkin.COL_TEXT_ON_GOLD;

        TextButton awaken = new TextButton("AWAKEN IN THE SHELTER   [ENTER / SPACE]", btnStyle);
        awaken.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                awakenInShelter();
            }
        });
        footer.add(awaken).minWidth(760f).height(64f).expandX();

        Label hint = new Label("[ESC] RETURN",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_TEXT_MUTED));
        footer.add(hint).right();
        return footer;
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

    /** How long the blood takes to clear off the screen once it appears. */
    private static final float REVEAL_DURATION = 0.30f;

    /**
     * Starts finished. The blood belongs to the handover, not to the screen -- a death screen
     * built directly (capture harness, tests) has no blood to clear and must not paint itself red.
     */
    private float revealElapsed = REVEAL_DURATION;

    /** Called by the handover when this screen is inheriting a fully opaque blood wipe. */
    public void beginBloodReveal() {
        revealElapsed = 0f;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(14f / 255f, 4f / 255f, 4f / 255f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();

        // The handover happens under fully opaque blood, so this screen inherits it and has to
        // clear it. Without this the world cuts to a finished screen at full opacity and the
        // transition ends a beat early.
        revealElapsed += delta;
        if (revealElapsed < REVEAL_DURATION) {
            float alpha = 1f - (revealElapsed / REVEAL_DURATION);
            com.badlogic.gdx.graphics.g2d.Batch batch = stage.getBatch();
            Gdx.gl.glEnable(GL20.GL_BLEND);
            batch.setProjectionMatrix(stage.getCamera().combined);
            batch.begin();
            batch.setColor(HudSkin.COL_BLOOD.r, HudSkin.COL_BLOOD.g, HudSkin.COL_BLOOD.b, alpha);
            batch.draw(hudSkin.getWhitePixel(), 0f, 0f,
                    stage.getViewport().getWorldWidth(), stage.getViewport().getWorldHeight());
            batch.setColor(Color.WHITE);
            batch.end();
        }
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
