package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.trait.TraitCatalog;
import com.bpm.minotaur.gamedata.trait.TraitDefinition;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.ui.UiGlyphs;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

/**
 * Pick one of three personality traits, or keep the one you have. Shown on a new game and after
 * every fifth respawn; the offer is saved with the character, so quitting does not skip it.
 *
 * <p>Each card shows what the trait gives and what it costs, because a penalty that is hidden until
 * it bites feels unfair in a game the player starts over often.
 */
public class TraitChoiceScreen extends BaseScreen {

    /** Width of one trait card on the 1920 wide canvas. */
    private static final float CARD_W = 520f;

    private final GameScreen parent;
    private final Player player;
    private final HudSkin skin;
    private Stage stage;

    public TraitChoiceScreen(Tarmin2 game, GameScreen parent, Player player) {
        super(game);
        this.parent = parent;
        this.player = player;
        this.skin = new HudSkin();
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        Gdx.input.setInputProcessor(new com.badlogic.gdx.InputMultiplexer(stage, this));

        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(skin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(UiTheme.PAD_XXL);

        Label title = new Label("CHOOSE YOUR PERSONALITY", new Label.LabelStyle(skin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        root.add(title).padBottom(UiTheme.PAD_MD).row();
        Label sub = new Label(UiGlyphs.sanitize(player.getTrait() == null
                ? "It stays with you until the next time you are asked. Press 1, 2 or 3, or click a card."
                : "You are the " + player.getTrait().name + ". Choose a new self, or keep this one (Enter)."),
                UiStyles.caption(skin, HudSkin.COL_GOLD_MUTED));
        root.add(sub).padBottom(UiTheme.PAD_XL).row();

        Table cards = new Table();
        TraitCatalog catalog = TraitCatalog.getInstance();
        for (String id : player.getPendingTraitOffer()) {
            TraitDefinition def = catalog.get(id);
            if (def != null) {
                cards.add(card(def)).width(CARD_W).top().pad(UiTheme.PAD_MD);
            }
        }
        root.add(cards).row();

        if (player.getTrait() != null) {
            TextButton keep = new TextButton(UiGlyphs.sanitize("KEEP " + player.getTrait().name.toUpperCase()), UiStyles.secondary(skin));
            keep.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    finish(null);
                }
            });
            root.add(keep).padTop(UiTheme.PAD_XL).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).row();
        }
        stage.addActor(root);
    }

    private Table card(final TraitDefinition def) {
        Table t = new Table();
        t.setBackground(skin.getDoubleBorderPanel());
        t.pad(UiTheme.PAD_XL);
        t.add(new Label(UiGlyphs.sanitize(def.name.toUpperCase()),
                new Label.LabelStyle(skin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT))).left().padBottom(UiTheme.PAD_MD).row();

        t.add(new Label("GIVES", UiStyles.caption(skin, HudSkin.COL_GOLD_MUTED))).left().row();
        Label good = new Label(UiGlyphs.sanitize(def.good), UiStyles.body(skin, HudSkin.COL_FOOD_ON_DARK));
        good.setWrap(true);
        t.add(good).growX().padBottom(UiTheme.PAD_MD).row();

        t.add(new Label("COSTS", UiStyles.caption(skin, HudSkin.COL_GOLD_MUTED))).left().row();
        Label bad = new Label(UiGlyphs.sanitize(def.bad), UiStyles.body(skin, HudSkin.COL_HP_ON_DARK));
        bad.setWrap(true);
        t.add(bad).growX().padBottom(UiTheme.PAD_XL).row();

        TextButton choose = new TextButton("BECOME " + UiGlyphs.sanitize(def.name.toUpperCase()), UiStyles.primary(skin));
        choose.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                finish(def.id);
            }
        });
        t.add(choose).growX().height(UiTheme.BUTTON_H).row();
        return t;
    }

    private void finish(String idOrNull) {
        player.chooseTrait(idOrNull);
        game.setScreen(parent);
    }

    @Override
    public boolean keyDown(int keycode) {
        java.util.List<String> offer = player.getPendingTraitOffer();
        int n = keycode - com.badlogic.gdx.Input.Keys.NUM_1;
        if (n >= 0 && n < offer.size()) {
            finish(offer.get(n));
            return true;
        }
        if (keycode == com.badlogic.gdx.Input.Keys.ENTER && player.getTrait() != null) {
            finish(null);
            return true;
        }
        return false;
    }

    @Override
    public void hide() {
        dispose();
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
        if (stage != null) {
            stage.dispose();
            stage = null;
        }
        if (skin != null) {
            skin.dispose();
        }
    }
}
