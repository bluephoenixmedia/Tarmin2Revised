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
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.war.Dispatches;
import com.bpm.minotaur.gamedata.history.war.WarReport;
import com.bpm.minotaur.gamedata.map.ArmyKnowledge;
import com.bpm.minotaur.gamedata.map.MapKnowledge;
import com.bpm.minotaur.managers.WorldManager;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.ui.ArmsAtlas;
import com.bpm.minotaur.ui.HouseHeraldry;
import com.bpm.minotaur.ui.UiContexts;
import com.bpm.minotaur.ui.UiGlyphs;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The War Table as Dispatches (Living War A50): the news down the main column, and a rail of the wars,
 * the seats at risk and the player's standing. Selecting a war swaps the news for that war's page.
 * It tells only what the player knows ({@link Dispatches}), and draws no map of its own: <b>W</b> on a
 * row sets the Expedition Map's waypoint.
 */
public class WarTableScreen extends BaseScreen {

    private static final String CONTEXT = "WAR_TABLE";

    private final GameScreen parentScreen;
    private final WorldManager worldManager;
    private final HudSkin hudSkin;
    private final ArmsAtlas arms;
    private Stage stage;
    /** The war whose page is showing, or null for the news. */
    private WarReport.WarLine page;
    /** The rows showing that can take a waypoint, top to bottom, and which is selected. */
    private final List<Dispatches.Row> placed = new ArrayList<>();
    private int selected;
    private ScrollPane newsScroll;
    private final List<Table> placedRows = new ArrayList<>();
    private Label footerHint;
    private static final String KEYS_ON_THE_NEWS = "UP DN select    W waypoint    1-9 a war    ESC return";
    private static final String KEYS_ON_A_PAGE = "UP DN select    W waypoint    ESC the news";

    public WarTableScreen(Tarmin2 game, GameScreen parentScreen, WorldManager worldManager) {
        super(game);
        this.parentScreen = parentScreen;
        this.worldManager = worldManager;
        this.hudSkin = new HudSkin();
        this.arms = new ArmsAtlas(worldManager.getHistory().world(), DoctrineCatalog.getInstance());
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                switch (keycode) {
                    case Input.Keys.ESCAPE:
                        if (page != null) showWar(null);
                        else returnToShelter();
                        return true;
                    case Input.Keys.UP:
                        select(selected - 1);
                        return true;
                    case Input.Keys.DOWN:
                        select(selected + 1);
                        return true;
                    case Input.Keys.W:
                        setWaypoint();
                        return true;
                    default:
                        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
                            List<WarReport.WarLine> wars = wars();
                            int i = keycode - Input.Keys.NUM_1;
                            if (i < wars.size()) showWar(wars.get(i));
                            return true;
                        }
                        return false;
                }
            }
        });
        Gdx.input.setInputProcessor(multiplexer);
        UiContexts.push(CONTEXT, UiContexts.Kind.PANEL);
        firstWord();
        build();
    }

    private Label.LabelStyle style(com.badlogic.gdx.graphics.g2d.BitmapFont font, com.badlogic.gdx.graphics.Color color) {
        return new Label.LabelStyle(font, color);
    }

    /** A Table with no word yet gives what it has on being first read, rather than waiting for a sleep. */
    private void firstWord() {
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        ArmyKnowledge known = MapKnowledge.getInstance().getArmies();
        if (known.markers(history.roster(), -1, java.util.Collections.emptyList(), history.warClock()).isEmpty()) {
            known.hearWarTable(history.roster(), history.warClock());
            MapKnowledge.getInstance().saveArmies();
        }
    }

    private List<WarReport.WarLine> wars() {
        return WarReport.wars(worldManager.getHistory().world(), worldManager.currentFronts(), worldManager.getCurrentPlayerChunkId());
    }

    private void showWar(WarReport.WarLine war) {
        page = war;
        selected = 0;
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
        root.pad(UiTheme.PAD_MD, UiTheme.PAD_XL, UiTheme.PAD_MD, UiTheme.PAD_XL);

        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(UiTheme.PAD_SM, UiTheme.PAD_MD, UiTheme.PAD_SM, UiTheme.PAD_MD);
        header.add(UiLabels.of("THE WAR TABLE", style(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT))).center().row();
        header.add(UiLabels.of("What the Shelter has heard", style(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED))).center().padTop(UiTheme.PAD_XS);
        root.add(header).colspan(2).growX().padBottom(UiTheme.PAD_SM).row();

        List<Dispatches.Row> news = news();
        List<WarReport.WarLine> wars = wars();
        newsScroll = scrollOf(page != null ? warPage(page, news) : newsColumn(news, wars));
        root.add(newsScroll).grow().padRight(UiTheme.PAD_MD);

        Table railBox = new Table();
        railBox.setBackground(hudSkin.getDoubleBorderPanel());
        railBox.add(scrollOf(rail(wars))).grow().pad(UiTheme.PAD_SM);
        root.add(railBox).width(UiTheme.WAR_TABLE_RAIL).growY().row();

        Table footer = new Table();
        footerHint = UiLabels.ellipsized(page != null ? KEYS_ON_A_PAGE : KEYS_ON_THE_NEWS, style(hudSkin.getFontSmall(), HudSkin.COL_TEXT_MUTED));
        footer.add(footerHint).left().growX();
        TextButton back = new TextButton(page != null ? "THE NEWS" : "RETURN", buttonStyle());
        back.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (page != null) showWar(null);
                else returnToShelter();
            }
        });
        footer.add(back).right().minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H);
        root.add(footer).colspan(2).growX().padTop(UiTheme.PAD_SM);
        stage.addActor(root);
        select(selected);
    }

    private ScrollPane scrollOf(Table content) {
        ScrollPane scroll = new ScrollPane(content, UiStyles.scrollPane(hudSkin));
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        return scroll;
    }

    private List<Dispatches.Row> news() {
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        HistoryWorld world = history.world();
        com.bpm.minotaur.gamedata.history.favour.Oath oath = history.oath();
        ArmyKnowledge known = MapKnowledge.getInstance().getArmies();
        long clock = history.warClock();
        int sworn = oath.sworn() ? oath.houseId : -1;
        List<ArmyKnowledge.Marker> markers = known.markers(history.roster(), sworn, ArmyKnowledge.sieges(world, history.roster(), worldManager.getBiomeManager(), clock), clock);
        String task = !oath.sworn() || oath.contract == null ? null
                : oath.contract.done ? "Your task is done. Go to a war banner of your house to be paid." : oath.contract.describe(world);
        return Dispatches.news(markers, shelter(), Dispatches.hunted(history.roster().armies), task, clock,
                h -> {
                    com.bpm.minotaur.gamedata.history.House house = world.house(h);
                    return house != null ? house.name : "a house of the Maze";
                },
                f -> {
                    com.bpm.minotaur.gamedata.history.Figure figure = world.figure(f);
                    return figure != null ? figure.name : null;
                });
    }

    /** Where the Table stands, which is where the player is reading it. */
    private GridPoint2 shelter() {
        GridPoint2 here = worldManager.getCurrentPlayerChunkId();
        return here != null ? here : new GridPoint2(0, 0);
    }

    private Table newsColumn(List<Dispatches.Row> news, List<WarReport.WarLine> wars) {
        placed.clear();
        placedRows.clear();
        Table column = new Table();
        column.top().left();
        Map<Dispatches.Section, List<Dispatches.Row>> sections = Dispatches.bySection(news);
        if (sections.isEmpty()) {
            column.add(UiLabels.wrapping(wars.isEmpty() ? "The Maze is at peace. It never lasts." : "No word of any host has reached the Shelter yet.",
                    style(hudSkin.getFontMain(), HudSkin.COL_GOLD_MUTED))).growX().pad(UiTheme.PAD_MD).row();
        }
        for (Map.Entry<Dispatches.Section, List<Dispatches.Row>> section : sections.entrySet()) {
            column.add(UiLabels.ellipsized(section.getKey().heading, style(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT)))
                    .left().growX().padTop(UiTheme.PAD_SM).padBottom(UiTheme.PAD_XS).row();
            for (Dispatches.Row r : section.getValue()) addRow(column, r);
        }
        return column;
    }

    private Table warPage(WarReport.WarLine w, List<Dispatches.Row> news) {
        placed.clear();
        placedRows.clear();
        Table column = new Table();
        column.top().left();
        column.add(UiLabels.wrapping(UiGlyphs.sanitize(w.attacker + " against " + w.defender), style(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT)))
                .growX().padBottom(UiTheme.PAD_SM).row();
        String[] lines = {
                w.cause + ", since the year " + w.sinceYear,
                "Battles " + w.winsA + " to " + w.winsB + ": " + w.standing(),
                w.front != null ? "The front: " + w.front : "The front: unknown"
        };
        for (String line : lines) {
            column.add(UiLabels.wrapping(UiGlyphs.sanitize(line), style(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE))).growX().padBottom(UiTheme.PAD_XS).row();
        }
        column.add(UiLabels.ellipsized("ITS HOSTS", style(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT))).left().growX().padTop(UiTheme.PAD_SM).padBottom(UiTheme.PAD_XS).row();
        List<Dispatches.Row> rows = Dispatches.ofWar(news, w.attackerId, w.defenderId);
        if (rows.isEmpty()) {
            column.add(UiLabels.wrapping("No word of its hosts has reached the Shelter.", style(hudSkin.getFontSmall(), HudSkin.COL_TEXT_MUTED))).growX().row();
        }
        for (Dispatches.Row r : rows) addRow(column, r);
        return column;
    }

    private void addRow(Table column, Dispatches.Row r) {
        boolean warning = r.section == Dispatches.Section.AIMED_AT_YOU;
        Table row = new Table();
        row.pad(UiTheme.PAD_XS);
        Table text = new Table();
        text.add(UiLabels.wrapping(UiGlyphs.sanitize(r.title), style(hudSkin.getFontMain(), warning ? UiTheme.DANGER : HudSkin.COL_GOLD_BRIGHT))).growX().left().row();
        text.add(UiLabels.wrapping(UiGlyphs.sanitize(r.detail), style(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE))).growX().left().padTop(UiTheme.PAD_SM).row();
        if (r.distanceWords != null) {
            text.add(UiLabels.wrapping(UiGlyphs.sanitize(r.distanceWords), style(hudSkin.getFontSmall(), HudSkin.COL_TEXT_MUTED))).growX().left().padTop(UiTheme.PAD_XS);
        }
        com.badlogic.gdx.graphics.g2d.TextureRegion shield = r.houseId >= 0 ? arms.shield(r.houseId, (int) UiTheme.WAR_TABLE_ARMS) : null;
        if (shield != null) row.add(new Image(shield)).top().padRight(UiTheme.PAD_SM);
        else row.add().width(UiTheme.WAR_TABLE_ARMS).padRight(UiTheme.PAD_SM);
        row.add(text).growX().top();
        column.add(row).growX().padBottom(UiTheme.PAD_XS).row();
        if (r.at == null) return;
        final int index = placed.size();
        placed.add(r);
        placedRows.add(row);
        row.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        row.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                select(index);
            }
        });
    }

    private void select(int index) {
        for (Table row : placedRows) row.setBackground((com.badlogic.gdx.scenes.scene2d.utils.Drawable) null);
        if (placed.isEmpty()) {
            selected = 0;
            return;
        }
        selected = Math.max(0, Math.min(placed.size() - 1, index));
        Table row = placedRows.get(selected);
        row.setBackground(hudSkin.getSlotRecessed());
        if (newsScroll != null) {
            newsScroll.layout();
            if (row.getParent() != null) newsScroll.scrollTo(row.getX(), row.getY(), row.getWidth(), row.getHeight());
        }
    }

    private void setWaypoint() {
        if (selected >= placed.size()) return;
        MapKnowledge.getInstance().setWaypoint(1, placed.get(selected).at);
        footerHint.setText("Waypoint set on the Expedition Map.");
    }

    private Table rail(List<WarReport.WarLine> wars) {
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        HistoryWorld world = history.world();
        DoctrineCatalog catalog = DoctrineCatalog.getInstance();
        Table rail = new Table();
        rail.top().left();
        railHeading(rail, "THE WARS");
        if (wars.isEmpty()) rail.add(UiLabels.wrapping("None.", style(hudSkin.getFontSmall(), HudSkin.COL_TEXT_MUTED))).growX().left().row();
        for (int i = 0; i < wars.size(); i++) {
            final WarReport.WarLine w = wars.get(i);
            Table entry = new Table();
            entry.pad(UiTheme.PAD_XS);
            if (w == page || (page != null && w.warId == page.warId)) entry.setBackground(hudSkin.getSlotRecessed());
            warSide(entry, w.attackerId, (i + 1) + " " + railName(w.attacker), w.winsA, world, catalog);
            warSide(entry, w.defenderId, "against " + railName(w.defender), w.winsB, world, catalog);
            entry.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
            entry.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    showWar(w);
                }
            });
            rail.add(entry).growX().padBottom(UiTheme.PAD_XS).row();
        }

        List<WarReport.SeatAtRisk> risk = WarReport.seatsAtRisk(world);
        if (!risk.isEmpty()) {
            railHeading(rail, "SEATS AT RISK");
            for (WarReport.SeatAtRisk s : risk) {
                rail.add(UiLabels.wrapping(UiGlyphs.sanitize(railName(s.house) + " holds " + s.gash + " at strength " + s.strength + "."),
                        style(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE))).growX().left().padBottom(UiTheme.PAD_XS).row();
            }
        }

        railHeading(rail, "YOUR STANDING");
        com.bpm.minotaur.gamedata.history.favour.Oath oath = history.oath();
        if (oath.sworn() && world.house(oath.houseId) != null) {
            rail.add(UiLabels.wrapping(UiGlyphs.sanitize("Sworn to " + railName(world.house(oath.houseId).name) + "."),
                    style(hudSkin.getFontSmall(), HudSkin.COL_GOLD_BRIGHT))).growX().left().padBottom(UiTheme.PAD_XS).row();
        }
        for (com.bpm.minotaur.gamedata.history.House h : world.livingHouses()) {
            Table line = new Table();
            com.badlogic.gdx.graphics.g2d.TextureRegion shield = arms.shield(h.id, (int) UiTheme.WAR_TABLE_RAIL_ARMS);
            if (shield != null) line.add(new Image(shield)).padRight(UiTheme.PAD_XS);
            line.add(UiLabels.ellipsized(UiGlyphs.sanitize(railName(h.name)),
                    style(hudSkin.getFontSmall(), legible(HouseHeraldry.primary(world, h.id, catalog))))).left().growX();
            line.add(UiLabels.of(UiGlyphs.sanitize(com.bpm.minotaur.gamedata.history.favour.Favour.word(history.stanceOf(h.id))
                    + " " + history.favour().of(h.id)), style(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE))).right();
            rail.add(line).growX().padBottom(UiTheme.PAD_XS).row();
        }
        return rail;
    }

    private void railHeading(Table rail, String words) {
        rail.add(UiLabels.ellipsized(words, style(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT))).left().growX().padTop(UiTheme.PAD_XS).padBottom(UiTheme.PAD_XS).row();
    }

    private void warSide(Table entry, int houseId, String words, int wins, HistoryWorld world, DoctrineCatalog catalog) {
        Table side = new Table();
        com.badlogic.gdx.graphics.g2d.TextureRegion shield = arms.shield(houseId, (int) UiTheme.WAR_TABLE_RAIL_ARMS);
        if (shield != null) side.add(new Image(shield)).padRight(UiTheme.PAD_XS);
        side.add(UiLabels.ellipsized(UiGlyphs.sanitize(words), style(hudSkin.getFontSmall(), legible(HouseHeraldry.primary(world, houseId, catalog))))).left().growX();
        side.add(UiLabels.of(Integer.toString(wins), style(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE))).right();
        entry.add(side).growX().row();
    }

    /** The rail has no room for "House". */
    private static String railName(String house) {
        return house.startsWith("House ") ? house.substring("House ".length()) : house;
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
        arms.dispose();
    }
}
