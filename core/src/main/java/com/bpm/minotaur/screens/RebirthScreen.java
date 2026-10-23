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
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.Life;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.ui.UiGlyphs;
import com.bpm.minotaur.ui.UiNames;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

/**
 * Who the soul wakes in (#279): the prisoner's name, home and how they came to the maze, shown at
 * a new game and every rebirth, with one reroll of the whole identity. The reveal is saved with
 * the character, so quitting does not skip it.
 */
public class RebirthScreen extends BaseScreen {

    /** Width of the identity card on the 1920 wide canvas. */
    private static final float CARD_W = 1100f;

    private final GameScreen parent;
    private final Player player;
    private final HistoryWorld world;
    private final String soulName;
    private final HudSkin skin;
    private Stage stage;

    public RebirthScreen(Tarmin2 game, GameScreen parent, Player player, HistoryWorld world, String soulName) {
        super(game);
        this.parent = parent;
        this.player = player;
        this.world = world;
        this.soulName = soulName;
        this.skin = new HudSkin();
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        Gdx.input.setInputProcessor(new com.badlogic.gdx.InputMultiplexer(stage, this));
        build();
    }

    private void build() {
        stage.clear();
        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(skin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(UiTheme.PAD_XXL);

        root.add(new Label("YOU WAKE", new Label.LabelStyle(skin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT)))
                .padBottom(UiTheme.PAD_MD).row();
        Label sub = new Label(UiGlyphs.sanitize("The Champion " + soulName + " is reborn in a condemned prisoner."),
                UiStyles.caption(skin, HudSkin.COL_GOLD_MUTED));
        sub.setWrap(true);
        root.add(sub).width(CARD_W).padBottom(UiTheme.PAD_XL).row();

        Life life = player.getLife();
        Table card = new Table();
        card.setBackground(skin.getDoubleBorderPanel());
        card.pad(UiTheme.PAD_XL);
        Label name = new Label(UiGlyphs.sanitize(life.title().toUpperCase()),
                new Label.LabelStyle(skin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        name.setWrap(true);
        card.add(name).growX().padBottom(UiTheme.PAD_MD).row();
        card.add(new Label(UiNames.caps(life.origin), UiStyles.caption(skin, HudSkin.COL_GOLD_MUTED))).left().row();
        Label how = new Label(UiGlyphs.sanitize(life.howTheyCame()), UiStyles.body(skin));
        how.setWrap(true);
        card.add(how).growX().row();
        root.add(card).width(CARD_W).row();

        Table buttons = new Table();
        if (player.canRerollLife()) {
            TextButton reroll = new TextButton("ANOTHER PRISONER (R)", UiStyles.secondary(skin));
            reroll.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    reroll();
                }
            });
            buttons.add(reroll).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).padRight(UiTheme.PAD_MD);
        }
        TextButton rise = new TextButton("RISE (ENTER)", UiStyles.primary(skin));
        rise.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                rise();
            }
        });
        buttons.add(rise).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H);
        root.add(buttons).padTop(UiTheme.PAD_XL).row();
        stage.addActor(root);
    }

    private void reroll() {
        if (player.rerollLife(world, com.bpm.minotaur.managers.WorldLogManager.getInstance().lifeNames())) build();
    }

    private void rise() {
        player.acknowledgeLife();
        game.setScreen(parent);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == com.badlogic.gdx.Input.Keys.ENTER) {
            rise();
            return true;
        }
        if (keycode == com.badlogic.gdx.Input.Keys.R) {
            reroll();
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
        Gdx.gl.glClearColor(UiTheme.BG_VOID.r, UiTheme.BG_VOID.g, UiTheme.BG_VOID.b, 1f);
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
