package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.GridPoint2;
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
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.war.WarReport;
import com.bpm.minotaur.managers.WorldManager;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.ui.HouseHeraldry;
import com.bpm.minotaur.ui.UiContexts;
import com.bpm.minotaur.ui.UiGlyphs;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

import java.util.List;

/**
 * The War Table (Living War W27): a shelter's map of the war. Every war under way -- its houses,
 * why it was declared, since when, how its battles have gone and where its front stands -- and
 * the gashes whose holders are bleeding out.
 */
public class WarTableScreen extends BaseScreen {

    private static final String CONTEXT = "WAR_TABLE";

    private final GameScreen parentScreen;
    private final WorldManager worldManager;
    private final HudSkin hudSkin;
    private Stage stage;

    public WarTableScreen(Tarmin2 game, GameScreen parentScreen, WorldManager worldManager) {
        super(game);
        this.parentScreen = parentScreen;
        this.worldManager = worldManager;
        this.hudSkin = new HudSkin();
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    returnToShelter();
                    return true;
                }
                return false;
            }
        });
        Gdx.input.setInputProcessor(multiplexer);
        UiContexts.push(CONTEXT, UiContexts.Kind.PANEL);
        build();
    }

    private Label.LabelStyle style(com.badlogic.gdx.graphics.g2d.BitmapFont font, com.badlogic.gdx.graphics.Color color) {
        return new Label.LabelStyle(font, color);
    }

    private void build() {
        stage.clear();
        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(30, 80, 30, 80);

        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(16, 24, 16, 24);
        header.add(UiLabels.of("THE WAR TABLE", style(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT))).center().row();
        header.add(UiLabels.of("Where the houses of the Maze are fighting, and who is winning",
                style(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED))).center().padTop(4).row();
        root.add(header).fillX().padBottom(14).row();

        HistoryWorld world = worldManager.getHistory().world();
        GridPoint2 here = worldManager.getCurrentPlayerChunkId();
        List<WarReport.WarLine> wars = WarReport.wars(world, worldManager.currentFronts(), here);

        Table content = new Table();
        content.top().left();
        if (wars.isEmpty()) {
            content.add(UiLabels.wrapping("The Maze is at peace. It never lasts.",
                    style(hudSkin.getFontMain(), HudSkin.COL_GOLD_MUTED))).growX().pad(UiTheme.PAD_XL).row();
        }
        DoctrineCatalog catalog = DoctrineCatalog.getInstance();
        for (WarReport.WarLine w : wars) {
            Table row = new Table();
            row.setBackground(hudSkin.getDoubleBorderPanel());
            row.pad(UiTheme.PAD_MD);
            Table names = new Table();
            names.add(UiLabels.ellipsized(UiGlyphs.sanitize(w.attacker),
                    style(hudSkin.getFontMain(), legible(HouseHeraldry.primary(world, w.attackerId, catalog))))).left().growX();
            names.add(UiLabels.of("against", style(hudSkin.getFontSmall(), HudSkin.COL_TEXT_MUTED))).padLeft(UiTheme.PAD_MD).padRight(UiTheme.PAD_MD);
            names.add(UiLabels.ellipsized(UiGlyphs.sanitize(w.defender),
                    style(hudSkin.getFontMain(), legible(HouseHeraldry.primary(world, w.defenderId, catalog))))).right().growX();
            row.add(names).growX().colspan(2).padBottom(UiTheme.PAD_SM).row();
            row.add(UiLabels.ellipsized(UiGlyphs.sanitize(w.cause + ", since the year " + w.sinceYear),
                    style(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE))).left().growX();
            row.add(UiLabels.ellipsized(UiGlyphs.sanitize("Battles " + w.winsA + " to " + w.winsB + ": " + w.standing()),
                    style(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE))).right().row();
            if (w.front != null) {
                row.add(UiLabels.ellipsized(UiGlyphs.sanitize("The front: " + w.front),
                        style(hudSkin.getFontSmall(), HudSkin.COL_TEXT_MUTED))).left().colspan(2).padTop(UiTheme.PAD_SM).row();
            }
            content.add(row).growX().padBottom(UiTheme.PAD_SM).row();
        }

        List<WarReport.SeatAtRisk> risk = WarReport.seatsAtRisk(world);
        if (!risk.isEmpty()) {
            content.add(UiLabels.ellipsized("SEATS AT RISK", style(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT)))
                    .left().growX().padTop(UiTheme.PAD_MD).padBottom(UiTheme.PAD_SM).row();
            for (WarReport.SeatAtRisk s : risk) {
                content.add(UiLabels.wrapping(UiGlyphs.sanitize(s.house + " holds " + s.gash + " with its strength at "
                        + s.strength + ". One more lost battle may cost it the gash."),
                        style(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE))).growX().padBottom(UiTheme.PAD_SM).row();
            }
        }

        ScrollPane scroll = new ScrollPane(content, UiStyles.scrollPane(hudSkin));
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        root.add(scroll).grow().row();

        TextButton back = new TextButton("RETURN", buttonStyle());
        back.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                returnToShelter();
            }
        });
        root.add(back).right().minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).padTop(UiTheme.PAD_MD);
        stage.addActor(root);
    }

    /** A house's colour lifted toward the gold of the text, so a dark heraldry still reads on the table. */
    private static com.badlogic.gdx.graphics.Color legible(com.badlogic.gdx.graphics.Color house) {
        return house.cpy().lerp(HudSkin.COL_GOLD_BRIGHT, UiTheme.HERALDRY_TEXT_LIFT);
    }

    private TextButton.TextButtonStyle buttonStyle() {
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
        s.font = hudSkin.getFontMain();
        s.up = hudSkin.getPanelBg();
        s.down = hudSkin.getPrimaryButtonDown();
        s.over = hudSkin.getSlotRecessed();
        s.fontColor = HudSkin.COL_GOLD_BRIGHT;
        return s;
    }

    private void returnToShelter() {
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
