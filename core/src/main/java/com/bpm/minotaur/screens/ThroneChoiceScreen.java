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
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

/**
 * Tarmin lies dead at the Champion's feet (#276, #291): return the Treasure and seal the Rift, or
 * take the throne. Neither is simply better; either way this life ends, and an Age with it.
 */
public class ThroneChoiceScreen extends BaseScreen {

    private final GameScreen parent;
    private final HudSkin skin;
    private Stage stage;

    public ThroneChoiceScreen(Tarmin2 game, GameScreen parent) {
        super(game);
        this.parent = parent;
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
        root.add(UiLabels.wrapping("TARMIN IS SLAIN", new Label.LabelStyle(skin.getFontHeader(), UiTheme.GOLD)))
                .colspan(2).width(UiTheme.LEDGER_PAGE_W).padBottom(UiTheme.PAD_MD).row();
        root.add(UiLabels.wrapping("The throne of the Maze stands empty, and the Treasure lies within reach. "
                        + "Whatever you choose, this life ends here, and an Age with it.", UiStyles.body(skin)))
                .colspan(2).width(UiTheme.LEDGER_PAGE_W).padBottom(UiTheme.PAD_XL).row();

        root.add(choice("RETURN THE TREASURE",
                "Seal the Rift as your first life broke it. The old gods wake, and the soul is allowed to rest, "
                        + "until the curse wakes it again. Some lord of the Maze will take the empty throne.", false))
                .width(UiTheme.LIST_COLUMN_W + UiTheme.PAD_XL).pad(UiTheme.PAD_SM).top();
        root.add(choice("TAKE THE THRONE",
                "Sit on it. This life becomes the new Tarmin, and the dark gods stay dark. The soul's next life "
                        + "will be sent to hunt you.", true))
                .width(UiTheme.LIST_COLUMN_W + UiTheme.PAD_XL).pad(UiTheme.PAD_SM).top();
        stage.addActor(root);
    }

    private Table choice(String title, String body, final boolean throne) {
        Table card = new Table();
        card.setBackground(skin.getDoubleBorderPanel());
        card.pad(UiTheme.PAD_MD);
        card.add(UiLabels.wrapping(title, new Label.LabelStyle(skin.getFontMain(), UiTheme.GOLD))).growX().row();
        card.add(UiLabels.wrapping(body, UiStyles.caption(skin, UiTheme.TEXT_DIM))).growX().padBottom(UiTheme.PAD_SM).row();
        TextButton choose = new TextButton("CHOOSE", throne ? UiStyles.danger(skin) : UiStyles.primary(skin));
        choose.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(parent);
                parent.endAgeByChampion(throne);
            }
        });
        card.add(choose).growX().height(UiTheme.BUTTON_H);
        return card;
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
