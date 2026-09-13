package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
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

/**
 * Scene2D interface for the Shelter Altar: spend banked Divinities on three
 * permanent upgrade trees (Provisions, Repertoire, Monument). Mirrors the
 * "Forged Iron & Ember" styling used by {@link ShelterChestScreen}.
 */
public class ShelterAltarScreen extends BaseScreen {

    private final GameScreen parentScreen;
    private final Player player;
    private final HudSkin hudSkin;

    private Stage stage;
    private Label divinityLabel;
    private Label statusLabel;

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
        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(60, 120, 60, 120);

        // --- HEADER ---
        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(18, 24, 18, 24);
        Label title = new Label("THE SHELTER ALTAR", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        title.setFontScale(1.3f);
        header.add(title).center().row();
        Label subtitle = new Label("Banked Divinities never fade with death -- spend them here on permanent upgrades",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        header.add(subtitle).center().padTop(6).row();
        divinityLabel = new Label("", new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT));
        header.add(divinityLabel).center().padTop(10).row();
        root.add(header).fillX().padBottom(24).row();

        // --- BODY: Three upgrade tree cards ---
        Table body = new Table();

        Table provisionsCard = buildTreeCard("PROVISIONS",
                "Extra bread, waterskins, and bandages at the start of every expedition.");
        provisionsTierLabel = new Label("", new Label.LabelStyle(hudSkin.getFontMain(), Color()));
        provisionsCard.add(provisionsTierLabel).left().padTop(10).row();
        provisionsBtn = createActionButton("UPGRADE PROVISIONS");
        provisionsBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                purchase(ShelterAltar.Tree.PROVISIONS);
            }
        });
        provisionsCard.add(provisionsBtn).width(320).height(52).padTop(16).row();
        body.add(provisionsCard).width(380).expandY().fillY().padRight(20);

        Table repertoireCard = buildTreeCard("REPERTOIRE",
                "Unseals advanced weaponry -- Composite Bows, Heavy Crossbows, Warhammers, and Morning Stars -- directly into the Stash Chest.");
        repertoireTierLabel = new Label("", new Label.LabelStyle(hudSkin.getFontMain(), Color()));
        repertoireCard.add(repertoireTierLabel).left().padTop(10).row();
        repertoireBtn = createActionButton("UPGRADE REPERTOIRE");
        repertoireBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                purchase(ShelterAltar.Tree.REPERTOIRE);
            }
        });
        repertoireCard.add(repertoireBtn).width(320).height(52).padTop(16).row();
        body.add(repertoireCard).width(380).expandY().fillY().padRight(20);

        Table monumentCard = buildTreeCard("MONUMENT",
                "Raises the frequency of statue encounter events from a 15% baseline up to 35% per chunk.");
        monumentTierLabel = new Label("", new Label.LabelStyle(hudSkin.getFontMain(), Color()));
        monumentCard.add(monumentTierLabel).left().padTop(10).row();
        monumentBtn = createActionButton("UPGRADE MONUMENT");
        monumentBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                purchase(ShelterAltar.Tree.MONUMENT);
            }
        });
        monumentCard.add(monumentBtn).width(320).height(52).padTop(16).row();
        body.add(monumentCard).width(380).expandY().fillY();

        root.add(body).expand().fill().padBottom(20).row();

        // --- FOOTER ---
        Table footer = new Table();
        footer.setBackground(hudSkin.getPanelBg());
        footer.pad(14, 22, 14, 22);
        Label keyHints = new Label("[ESC] LEAVE ALTAR", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        footer.add(keyHints).left().expandX();
        statusLabel = new Label("Approach and spend your Divinities wisely.",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_FOOD_GREEN));
        footer.add(statusLabel).right();
        root.add(footer).fillX();

        stage.addActor(root);

        refresh();
    }

    private com.badlogic.gdx.graphics.Color Color() {
        return HudSkin.COL_GOLD_ANTIQUE;
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

    private void refresh() {
        ShelterAltar altar = ShelterAltar.getInstance();
        int divinities = DivinityManager.getInstance().getCurrentDivinities();
        divinityLabel.setText(DivinityManager.DIVINITY_NAME + ": " + divinities);

        refreshTree(altar, ShelterAltar.Tree.PROVISIONS, provisionsTierLabel, provisionsBtn, divinities);
        refreshTree(altar, ShelterAltar.Tree.REPERTOIRE, repertoireTierLabel, repertoireBtn, divinities);
        refreshTree(altar, ShelterAltar.Tree.MONUMENT, monumentTierLabel, monumentBtn, divinities);
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

    private void purchase(ShelterAltar.Tree tree) {
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

    /** Grants the advanced weapon unlocked by this Repertoire tier directly into the Stash Chest. */
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
