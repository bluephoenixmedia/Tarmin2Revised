package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.ui.KeyHintLegend;
import com.bpm.minotaur.ui.UiContexts;
import com.bpm.minotaur.ui.UiGlyphs;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Coming upon a battle under way (Living War A26): help one side, help the other, or keep away.
 * Which sides are on offer is the caller's to say, since an oath narrows them.
 */
public class BattleSideScreen extends BaseScreen {

    private static final String CONTEXT = "BATTLE_SIDE";

    private final GameScreen parentScreen;
    private final String header;
    private final String body;
    private final Map<String, Runnable> offered;
    private final Runnable keepAway;

    private Stage stage;
    private HudSkin hudSkin;

    /** @param offered what can be chosen, by label, in order; "keep away" is added by this screen. */
    public BattleSideScreen(Tarmin2 game, GameScreen parentScreen, String header, String body,
            Map<String, Runnable> offered, Runnable keepAway) {
        super(game);
        this.parentScreen = parentScreen;
        this.header = header;
        this.body = body;
        this.offered = new LinkedHashMap<>(offered);
        this.keepAway = keepAway;
        this.offered.put("Keep away", keepAway);
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        hudSkin = new HudSkin();
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    choose(keepAway);
                    return true;
                }
                return false;
            }
        });
        Gdx.input.setInputProcessor(multiplexer);
        UiContexts.push(CONTEXT, UiContexts.Kind.PANEL);
        build();
    }

    private void build() {
        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(UiTheme.SAFE * 2, UiTheme.SAFE * 6, UiTheme.SAFE * 2, UiTheme.SAFE * 6);

        Table panel = new Table();
        panel.setBackground(hudSkin.getDoubleBorderPanel());
        panel.pad(UiTheme.PAD_MD);
        panel.add(UiLabels.ellipsized(UiGlyphs.sanitize(header), new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT)))
                .growX().padBottom(UiTheme.PAD_MD).row();
        panel.add(UiLabels.wrapping(UiGlyphs.sanitize(body), new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_MUTED)))
                .growX().padBottom(UiTheme.PAD_MD).row();
        for (Map.Entry<String, Runnable> o : offered.entrySet()) {
            TextButton b = new TextButton(UiGlyphs.sanitize(o.getKey()), UiStyles.secondary(hudSkin));
            b.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    choose(o.getValue());
                }
            });
            panel.add(b).growX().height(UiTheme.BUTTON_H).padBottom(UiTheme.PAD_SM).row();
        }
        root.add(panel).growX().row();
        root.add(new KeyHintLegend(hudSkin).escapeHint("Keep away")).right().padTop(UiTheme.PAD_SM).row();
        stage.addActor(root);
    }

    /** Chooses an option as a click would; false if it is not on offer. For the play-test driver. */
    public boolean perform(String label) {
        Runnable run = offered.get(label);
        if (run == null) return false;
        choose(run);
        return true;
    }

    /** The labels on offer, in order. For the play-test driver. */
    public java.util.Set<String> labels() {
        return offered.keySet();
    }

    private void choose(Runnable choice) {
        choice.run();
        game.setScreen(parentScreen);
        dispose();
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(UiTheme.BG_VOID);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        UiContexts.pop(CONTEXT);
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (hudSkin != null) hudSkin.dispose();
    }
}
