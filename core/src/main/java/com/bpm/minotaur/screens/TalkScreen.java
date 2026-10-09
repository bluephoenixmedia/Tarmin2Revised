package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.managers.TownTalk;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.ui.KeyHintLegend;
import com.bpm.minotaur.ui.UiContexts;
import com.bpm.minotaur.ui.UiGlyphs;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * Talking with one of a town's folk (Houses of the Maze plan D38, T4.3): Talk, Trade, Rumours,
 * Quest, Leave, and Accuse when there is an agent to unmask. Everything said is written by
 * {@link TownTalk}; this screen only lays it out and keeps the transcript.
 */
public class TalkScreen extends BaseScreen {

    private static final String CONTEXT = "TALK";
    /** Lines of the conversation kept on screen. */
    static final int TRANSCRIPT_LINES = 12;

    private final GameScreen parentScreen;
    private final TownTalk talk;
    /** Opens the town merchant's stall, or null if no merchant stands in this town. */
    private final Runnable trade;
    private final List<String> transcript = new ArrayList<>();

    private Stage stage;
    private HudSkin hudSkin;
    private Table transcriptTable;
    private Table actions;

    public TalkScreen(Tarmin2 game, GameScreen parentScreen, TownTalk talk, Runnable trade) {
        super(game);
        this.parentScreen = parentScreen;
        this.talk = talk;
        this.trade = trade;
        this.transcript.add(talk.greet());
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
                    leave();
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
        stage.clear();
        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(UiTheme.SAFE * 2, UiTheme.SAFE * 6, UiTheme.SAFE * 2, UiTheme.SAFE * 6);

        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(UiTheme.PAD_MD);
        header.add(UiLabels.ellipsized(talk.header(), new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT)))
                .growX().row();
        header.add(UiLabels.ellipsized(UiGlyphs.sanitize("Standing: " + talk.standingWord()),
                new Label.LabelStyle(hudSkin.getFontSmall(), talk.hostile() ? HudSkin.COL_HP_RED : HudSkin.COL_GOLD_MUTED)))
                .growX().padTop(UiTheme.PAD_SM).row();
        root.add(header).growX().padBottom(UiTheme.PAD_MD).row();

        transcriptTable = new Table();
        transcriptTable.top().left();
        ScrollPane scroll = new ScrollPane(transcriptTable, UiStyles.scrollPane(hudSkin));
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        Table body = new Table();
        body.setBackground(hudSkin.getSlotRecessed());
        body.pad(UiTheme.PAD_MD);
        body.add(scroll).grow();
        root.add(body).grow().padBottom(UiTheme.PAD_MD).row();

        actions = new Table();
        root.add(actions).growX().padBottom(UiTheme.PAD_SM).row();
        root.add(new KeyHintLegend(hudSkin).escapeHint("Leave")).right().row();
        stage.addActor(root);
        refresh();
    }

    private void refresh() {
        transcriptTable.clear();
        for (String line : transcript) {
            transcriptTable.add(UiLabels.wrapping(UiGlyphs.sanitize(line),
                    new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_MUTED))).growX().left().padBottom(UiTheme.PAD_SM).row();
        }
        actions.clear();
        if (!talk.hostile()) {
            action("Talk", () -> say(talk.greet()));
            if (trade != null) action("Trade", () -> {
                leave();
                trade.run();
            });
            action("Rumours", () -> {
                for (String r : talk.rumours()) say(r);
            });
            if (talk.hasQuestBusiness()) action("Quest", () -> say(talk.quest()));
            if (talk.canAccuse()) action("Accuse", () -> say(talk.accuse()));
        }
        action("Leave", this::leave);
    }

    private void action(String label, Runnable run) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = hudSkin.getFontMain();
        style.up = hudSkin.getPanelBg();
        style.down = hudSkin.getPrimaryButtonDown();
        style.over = hudSkin.getSlotRecessed();
        style.fontColor = HudSkin.COL_GOLD_BRIGHT;
        TextButton b = new TextButton(label, style);
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                run.run();
            }
        });
        actions.add(b).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).padRight(UiTheme.PAD_SM);
    }

    private void say(String line) {
        transcript.add(line);
        while (transcript.size() > TRANSCRIPT_LINES) transcript.remove(0);
        refresh();
    }

    private void leave() {
        game.setScreen(parentScreen);
        dispose();
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.04f, 0.03f, 0.02f, 1f);
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
