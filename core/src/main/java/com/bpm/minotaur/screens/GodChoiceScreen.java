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
import com.bpm.minotaur.gamedata.history.OldGod;
import com.bpm.minotaur.managers.DoomManager;
import com.bpm.minotaur.managers.HistoryManager;
import com.bpm.minotaur.managers.WorldLogManager;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.ui.UiGlyphs;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

import java.util.List;

/**
 * Tarmin has crossed (#276, #278, #290): before the soul wakes again, one of the old gods must
 * drag him back, and is spent doing it. The player chooses which; Asterion, the old warden, is
 * only offered when no other is left. The choice cannot be put off, since the soul wakes after it.
 */
public class GodChoiceScreen extends BaseScreen {

    /** Gods offered per row. */
    private static final int COLUMNS = 3;

    private final GameScreen parent;
    private final HistoryManager history;
    private final HudSkin skin;
    private Stage stage;

    public GodChoiceScreen(Tarmin2 game, GameScreen parent, HistoryManager history) {
        super(game);
        this.parent = parent;
        this.history = history;
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
        root.add(UiLabels.wrapping("WHICH OF US WILL HOLD HIM BACK?", new Label.LabelStyle(skin.getFontHeader(), UiTheme.GOLD)))
                .colspan(COLUMNS).width(UiTheme.LEDGER_PAGE_W).padBottom(UiTheme.PAD_MD).row();
        int keepers = OldGod.keepers(history.world());
        root.add(UiLabels.wrapping("Tarmin has crossed the Bridge of Souls. One of the old gods must drag him back, "
                        + "and will be spent doing it: its shrines go dark for good. "
                        + com.bpm.minotaur.ui.UiNames.plural(keepers, "keeper", "keepers") + " remain.",
                UiStyles.body(skin))).colspan(COLUMNS).width(UiTheme.LEDGER_PAGE_W).padBottom(UiTheme.PAD_XL).row();

        List<OldGod> gods = OldGod.spendable(history.world());
        int column = 0;
        for (final OldGod god : gods) {
            Table card = new Table();
            card.setBackground(skin.getDoubleBorderPanel());
            card.pad(UiTheme.PAD_MD);
            card.add(UiLabels.wrapping(god.fullName.toUpperCase(), new Label.LabelStyle(skin.getFontMain(), UiTheme.GOLD)))
                    .growX().row();
            card.add(UiLabels.wrapping("Of " + god.domain + ". " + god.kindred.title + ".",
                    UiStyles.caption(skin, UiTheme.TEXT_DIM))).growX().padBottom(UiTheme.PAD_SM).row();
            TextButton spend = new TextButton("LET IT HOLD HIM", UiStyles.secondary(skin));
            spend.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    choose(god);
                }
            });
            card.add(spend).growX().height(UiTheme.BUTTON_H);
            root.add(card).width(UiTheme.LIST_COLUMN_W + UiTheme.PAD_XL).pad(UiTheme.PAD_SM).top();
            if (++column % COLUMNS == 0) root.row();
        }
        stage.addActor(root);
    }

    private void choose(OldGod god) {
        WorldLogManager.getInstance().spendGodForCrossing(history, god);
        DoomManager.getInstance().clearCrossing();
        // The god's spending is a deed of the history, saved with the slot: write it with the Reckoning.
        com.bpm.minotaur.managers.SaveManager.getInstance().saveActiveSlot(parent.getPlayer(), parent.getWorldManager());
        game.setScreen(parent);
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
