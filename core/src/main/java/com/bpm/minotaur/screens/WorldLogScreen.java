package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.worldlog.Era;
import com.bpm.minotaur.gamedata.worldlog.Fact;
import com.bpm.minotaur.gamedata.worldlog.LifeRecord;
import com.bpm.minotaur.gamedata.worldlog.WorldLog;
import com.bpm.minotaur.managers.DoomManager;
import com.bpm.minotaur.managers.WorldLogManager;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.ui.UiGlyphs;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiNames;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * The World Log as the Ledger (#270, #281): the Bridge of Souls across the top, every life
 * newest first grouped by Era on the left, and the open life's page on the right. Opened from
 * the Book of Lives beside the Shelter Altar, and from the death screen.
 */
public class WorldLogScreen extends BaseScreen {

    private final Screen back;
    private final String soulName;
    private final HudSkin skin;
    private final WorldLog log;
    /** Stands for the page of what the soul has learned of the myth (#288). */
    private static final LifeRecord LEARNED = new LifeRecord();

    /** Every life in the order the list shows them; null stands for the lives unremembered. */
    private final List<LifeRecord> order = new ArrayList<>();
    private int selected;
    private Stage stage;

    /** How many old gods still keep the soul; -1 when unknown. */
    private final int keepers;
    /** The list entries that stand for a Bridge's Reckoning, and the Bridge each stands for. */
    private final java.util.Map<LifeRecord, Ending> reckoningPages = new java.util.HashMap<>();

    /** A page that tells how an Age or a Bridge ended. */
    private static final class Ending {
        final String name;
        final com.bpm.minotaur.gamedata.worldlog.Reckoning reckoning;

        Ending(String name, com.bpm.minotaur.gamedata.worldlog.Reckoning reckoning) {
            this.name = name;
            this.reckoning = reckoning;
        }
    }

    public WorldLogScreen(Tarmin2 game, Screen back, String soulName, int keepers) {
        super(game);
        this.back = back;
        this.soulName = soulName;
        this.keepers = keepers;
        this.skin = new HudSkin();
        this.log = WorldLogManager.getInstance().log();
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
        root.top().pad(UiTheme.PAD_XL);
        root.add(UiLabels.of("THE BOOK OF LIVES", new Label.LabelStyle(skin.getFontHeader(), UiTheme.GOLD)))
                .colspan(2).padBottom(UiTheme.PAD_SM).row();
        root.add(bridgeBar()).colspan(2).padBottom(UiTheme.PAD_XL).row();

        order.clear();
        reckoningPages.clear();
        Table list = new Table();
        list.top().left();
        // Age by Age, then Bridge by Bridge, newest first; each ended one's Reckoning heads it (#290, #291).
        for (com.bpm.minotaur.gamedata.worldlog.Age age : log.ages()) {
            list.add(UiLabels.ellipsized(UiGlyphs.sanitize(age.name.toUpperCase()), new Label.LabelStyle(skin.getFontHeader(), UiTheme.GOLD)))
                    .width(UiTheme.LIST_COLUMN_W).left().padTop(UiTheme.PAD_XL).row();
            if (age.reckoning != null) list.add(endingEntry(new Ending(age.name, age.reckoning))).width(UiTheme.LIST_COLUMN_W).row();
            for (com.bpm.minotaur.gamedata.worldlog.Bridge bridge : age.bridges) {
                list.add(UiLabels.ellipsized(UiGlyphs.sanitize(bridge.name.toUpperCase()), new Label.LabelStyle(skin.getFontMain(), UiTheme.GOLD)))
                        .width(UiTheme.LIST_COLUMN_W).left().padTop(UiTheme.PAD_MD).row();
                // A Bridge its Age's end closed is told by the Age's page above.
                if (bridge.reckoning != null && bridge.reckoning.end != com.bpm.minotaur.gamedata.worldlog.Reckoning.End.AGE_ENDED) {
                    list.add(endingEntry(new Ending(bridge.name, bridge.reckoning))).width(UiTheme.LIST_COLUMN_W).row();
                }
                for (Era era : bridge.eras) {
                    Label heading = UiLabels.ellipsized(UiGlyphs.sanitize(era.name.toUpperCase()), UiStyles.caption(skin, UiTheme.GOLD));
                    list.add(heading).width(UiTheme.LIST_COLUMN_W).left().padTop(UiTheme.PAD_SM).row();
                    for (LifeRecord life : era.lives) list.add(entry(life)).width(UiTheme.LIST_COLUMN_W).row();
                }
            }
        }
        if (log.unremembered() > 0) list.add(entry(null)).width(UiTheme.LIST_COLUMN_W).padTop(UiTheme.PAD_MD).row();
        // Last, so the Book still opens on the newest life.
        if (!com.bpm.minotaur.gamedata.history.Reveal.learned(log).isEmpty()) {
            list.add(entry(LEARNED)).width(UiTheme.LIST_COLUMN_W).padTop(UiTheme.PAD_MD).row();
        }
        selected = Math.max(0, Math.min(selected, order.size() - 1));

        ScrollPane scroll = new ScrollPane(list, UiStyles.scrollPane(skin));
        scroll.setFadeScrollBars(false);
        root.add(scroll).width(UiTheme.LIST_COLUMN_W + UiTheme.PAD_XL).growY().top().padRight(UiTheme.PAD_XL);
        root.add(page()).width(UiTheme.LEDGER_PAGE_W).growY().top().row();

        root.add(UiLabels.of("[UP / DOWN] TURN THE PAGE    [ESC / ENTER] CLOSE", UiStyles.caption(skin, UiTheme.TEXT_OFF)))
                .colspan(2).padTop(UiTheme.PAD_MD);
        stage.addActor(root);
        stage.setScrollFocus(scroll);
    }

    /** Fifty stones, one per death: the Bridge Tarmin is building from the soul. */
    private Table bridgeBar() {
        int laid = DoomManager.getInstance().getDeathCount();
        int span = DoomManager.maxDeaths();
        Table bar = new Table();
        Table stones = new Table();
        for (int i = 0; i < span; i++) {
            Image stone = new Image(skin.getWhitePixelDrawable());
            stone.setColor(i < laid ? UiTheme.GOLD : UiTheme.LINE_DIM);
            stones.add(stone).width(UiTheme.BRIDGE_STONE_W).height(UiTheme.BRIDGE_STONE_H).padRight(UiTheme.PAD_XS);
        }
        bar.add(stones).row();
        bar.add(UiLabels.of(UiGlyphs.sanitize("THE BRIDGE OF SOULS: " + laid + " OF " + span + " STONES LAID"),
                UiStyles.caption(skin, UiTheme.TEXT_DIM))).padTop(UiTheme.PAD_XS).row();
        if (keepers >= 0) {
            bar.add(UiLabels.of(UiNames.plural(keepers, "KEEPER", "KEEPERS") + " REMAIN",
                    UiStyles.caption(skin, keepers == 0 ? UiTheme.DANGER : UiTheme.GOLD))).padTop(UiTheme.PAD_XS);
        }
        return bar;
    }

    private TextButton endingEntry(Ending ending) {
        LifeRecord page = new LifeRecord();
        reckoningPages.put(page, ending);
        return entry(page);
    }

    private TextButton entry(final LifeRecord life) {
        final int index = order.size();
        order.add(life);
        String text = reckoningPages.containsKey(life) ? "HOW IT ENDED"
                : life == LEARNED ? "WHAT THE SOUL HAS LEARNED"
                : life == null ? UiNames.plural(log.unremembered(), "LIFE", "LIVES") + " UNREMEMBERED"
                : life.number + ". " + life.name.toUpperCase() + (life.motleyReckoning != null ? " [MOTLEY]" : "");
        TextButton button = new TextButton(UiGlyphs.sanitize(text), UiStyles.menu(skin));
        button.getLabel().setEllipsis(true);
        button.getLabel().setAlignment(com.badlogic.gdx.utils.Align.left);
        button.setChecked(index == selected);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selected = index;
                build();
            }
        });
        return button;
    }

    private Table page() {
        Table page = new Table();
        page.setBackground(skin.getDoubleBorderPanel());
        page.top().left().pad(UiTheme.PAD_XL);
        page.defaults().left().width(UiTheme.LEDGER_PAGE_W - 2 * UiTheme.PAD_XL);
        if (order.isEmpty()) {
            page.add(wrap("No life has been written here yet.", UiStyles.body(skin))).row();
            return page;
        }
        LifeRecord life = order.get(selected);
        if (reckoningPages.containsKey(life)) {
            Ending ending = reckoningPages.get(life);
            page.add(wrap(ending.name.toUpperCase(), new Label.LabelStyle(skin.getFontHeader(), UiTheme.GOLD)))
                    .padBottom(UiTheme.PAD_MD).row();
            page.add(wrap(ending.reckoning.plain, UiStyles.body(skin))).padBottom(UiTheme.PAD_MD).row();
            page.add(UiLabels.ellipsized("AS THE KNELL TELLS IT", UiStyles.caption(skin, UiTheme.GOLD))).row();
            page.add(wrap(ending.reckoning.mythic, UiStyles.body(skin, UiTheme.TEXT_DIM))).row();
            return page;
        }
        if (life == LEARNED) {
            page.add(wrap("WHAT THE SOUL HAS LEARNED", new Label.LabelStyle(skin.getFontHeader(), UiTheme.GOLD)))
                    .padBottom(UiTheme.PAD_MD).row();
            for (String revelation : com.bpm.minotaur.gamedata.history.Reveal.learned(log)) {
                page.add(wrap(revelation, UiStyles.body(skin))).padBottom(UiTheme.PAD_SM).row();
            }
            return page;
        }
        if (life == null) {
            page.add(wrap(UiNames.plural(log.unremembered(), "life", "lives").toUpperCase() + " UNREMEMBERED",
                    new Label.LabelStyle(skin.getFontHeader(), UiTheme.GOLD))).padBottom(UiTheme.PAD_MD).row();
            page.add(wrap("Before the Book was kept, the soul lived " + UiNames.plural(log.unremembered(), "life", "lives")
                    + " in the maze. Their names are lost; their stones are in the Bridge.", UiStyles.body(skin))).row();
            return page;
        }

        page.add(wrap(life.title.toUpperCase(), new Label.LabelStyle(skin.getFontHeader(), UiTheme.GOLD))).row();
        page.add(wrap("Life " + life.number + ": the Champion " + soulName + " reborn.", UiStyles.caption(skin, UiTheme.TEXT_DIM)))
                .padBottom(UiTheme.PAD_MD).row();
        page.add(UiLabels.ellipsized(UiNames.caps(life.origin), UiStyles.caption(skin, UiTheme.GOLD))).row();
        page.add(wrap(life.howTheyCame, UiStyles.body(skin))).padBottom(UiTheme.PAD_MD).row();

        page.add(UiLabels.ellipsized("DEEDS", UiStyles.caption(skin, UiTheme.GOLD))).row();
        if (life.lines.isEmpty()) {
            page.add(wrap("Nothing the Book keeps.", UiStyles.body(skin, UiTheme.TEXT_OFF))).row();
        }
        for (Fact line : life.lines) page.add(wrap("- " + line.plain(), UiStyles.body(skin))).row();

        page.add(UiLabels.ellipsized("DEATH", UiStyles.caption(skin, UiTheme.GOLD))).padTop(UiTheme.PAD_MD).row();
        if (life.isDead()) {
            if (life.motleyReckoning != null) {
                page.add(wrap("MOTLEY RECKONING: " + life.motleyReckoning.toUpperCase(),
                        UiStyles.caption(skin, HudSkin.COL_HP_CRITICAL))).padBottom(UiTheme.PAD_XS).row();
            }
            page.add(wrap(life.epitaph, UiStyles.body(skin))).row();
            page.add(wrap("Felled by " + life.killer + " on " + life.place + ", turn " + life.turn + ".",
                    UiStyles.body(skin, UiTheme.TEXT_DIM))).row();
        } else {
            page.add(wrap("Lives still.", UiStyles.body(skin))).row();
        }

        page.add(UiLabels.ellipsized("AS THE CHRONICLES TELL IT", UiStyles.caption(skin, UiTheme.GOLD))).padTop(UiTheme.PAD_MD).row();
        boolean anyFound = false;
        for (com.bpm.minotaur.gamedata.worldlog.Telling t : life.tellings) {
            if (!t.found) continue;
            anyFound = true;
            page.add(wrap(t.teller + ": " + t.mythic, UiStyles.body(skin))).padBottom(UiTheme.PAD_XS).row();
        }
        if (!anyFound) {
            page.add(wrap(life.tellings.isEmpty() ? "No House tells of this life."
                    : "No telling of this life has been found.", UiStyles.body(skin, UiTheme.TEXT_OFF))).row();
        }
        return page;
    }

    private static Label wrap(String text, Label.LabelStyle style) {
        return UiLabels.wrapping(text, style);
    }

    private void close() {
        game.setScreen(back);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.ENTER) {
            close();
            return true;
        }
        if ((keycode == Input.Keys.DOWN || keycode == Input.Keys.S) && selected < order.size() - 1) {
            selected++;
            build();
            return true;
        }
        if ((keycode == Input.Keys.UP || keycode == Input.Keys.W) && selected > 0) {
            selected--;
            build();
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
