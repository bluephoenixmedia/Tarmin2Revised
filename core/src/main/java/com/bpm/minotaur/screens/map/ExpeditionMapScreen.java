package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.Megabeast;
import com.bpm.minotaur.gamedata.history.beast.BeastTracks;
import com.bpm.minotaur.gamedata.history.war.Front;
import com.bpm.minotaur.gamedata.map.ChunkSummary;
import com.bpm.minotaur.gamedata.map.MapKnowledge;
import com.bpm.minotaur.gamedata.map.MapModel;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.progression.BiomePortal;
import com.bpm.minotaur.gamedata.shelter.ShelterNetwork;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.ShelterRoads;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.DoomManager;
import com.bpm.minotaur.managers.SettingsManager;
import com.bpm.minotaur.managers.SoundManager;
import com.bpm.minotaur.managers.WorldManager;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.screens.BaseScreen;
import com.bpm.minotaur.screens.GameScreen;
import com.bpm.minotaur.ui.HouseHeraldry;
import com.bpm.minotaur.ui.UiGlyphs;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiNames;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The expedition map: where to go next, what is safe, and where the player has been.
 *
 * <p>The handoff's layout (docs/DEsign/tarmin-map-handoff) in the Carved Frame: a header of
 * breadcrumbs and badges, the isometric diorama ({@link MapSurface}) with its floating view box,
 * floor stack, compass and legend, the selected-chunk panel, and a key legend. The diorama's camera
 * is the one source of the zoom level; the wheel, the keys and the buttons all move it. Sizes are
 * budgeted for the game's fonts, which are about one em per character.
 */
public class ExpeditionMapScreen extends BaseScreen {

    private static final float PANEL_W = 400f;
    private static final float PANEL_PAD = 16f;
    private static final float MAP_W = 1440f;
    private static final float MAP_H = 896f;
    /** The map frame's inset: the frame is 1456 x 912, so the map draws at exactly 2x its buffer. */
    private static final float FRAME_PAD = 8f;
    private static final float STAT_LABEL_W = 152f;
    /** Keyboard pan per press, in diorama buffer pixels; finer in the chunk view. */
    private static final float PAN_STEP = 48f;
    private static final float PAN_STEP_CHUNK = 14f;
    /** The home shelter's chunk, where every expedition starts. */
    private static final GridPoint2 HOME = new GridPoint2(0, 0);

    /** Where the player left the map this session, so reopening it does not lose their place. */
    private static BiomeManager rememberedWorld;
    private static MapSurface.Zoom rememberedZoom;
    private static int rememberedFloor;
    private static final Map<Integer, GridPoint2> rememberedCursors = new HashMap<>();

    private final Player player;
    private final Maze maze;
    private final GameScreen gameScreen;
    private final WorldManager worldManager;
    private final MapKnowledge knowledge = MapKnowledge.getInstance();
    private final ShelterNetwork network = ShelterNetwork.getInstance();
    private final Map<Integer, GridPoint2> cursors = new HashMap<>();
    /** Chunk saves read this visit; the tiles and the summary of a chunk share one read. */
    private final Map<String, ChunkData> chunkData = new HashMap<>();

    private Stage stage;
    private HudSkin skin;
    private TextureRegion white;
    private MapSurface surface;
    private MapModel model;

    // Header
    private Label floorLabel;
    private Table sealsRow;
    private Table doomRow;
    private Label waypointCaption;
    private Label waypointLabel;
    private Label suggestedCaption;
    private Label suggestedLabel;
    private TextButton worldZoomBtn;
    private TextButton regionZoomBtn;
    private TextButton chunkZoomBtn;

    // Over the map
    private Label viewLevelLabel;
    private Label viewHintLabel;
    private Table floorStackTable;
    private CompassWidget compassWidget;
    private Table toastTable;
    private Label toastLabel;
    private float toastTimer = 0f;

    // Side panel
    private Table panel;
    private TextButton setWpBtn;
    private TextButton zoomActionBtn;

    private TextButton.TextButtonStyle compactPrimary;
    private TextButton.TextButtonStyle compactSecondary;

    /** The map was closed in the chunk view; it reopens there once the panels exist. */
    private boolean reopenChunk;
    private int macroMarkerIndex = -1;

    /** Every war's front, in its two houses' colours. */
    private List<MapSurface.FrontMark> frontMarks() {
        List<MapSurface.FrontMark> marks = new ArrayList<>();
        if (worldManager == null) return marks;
        HistoryWorld world = worldManager.getHistory().world();
        DoctrineCatalog catalog = DoctrineCatalog.getInstance();
        for (Front f : worldManager.currentFronts()) {
            marks.add(new MapSurface.FrontMark(f.chunks(),
                    HouseHeraldry.primary(world, f.attackerId, catalog),
                    HouseHeraldry.primary(world, f.defenderId, catalog)));
        }
        return marks;
    }

    /** Fresh battlefields, columns on the march and war camps (Living War W28). */
    private List<MapSurface.WarSign> warSigns() {
        List<MapSurface.WarSign> signs = new ArrayList<>();
        if (worldManager == null) return signs;
        HistoryWorld world = worldManager.getHistory().world();
        DoctrineCatalog catalog = DoctrineCatalog.getInstance();
        com.bpm.minotaur.gamedata.history.war.EncounterLedger ledger = worldManager.getHistory().encounterLedger();
        for (com.bpm.minotaur.gamedata.history.war.EncounterLedger.Dressing d : ledger.dressings) {
            if ("AFTERMATH".equals(d.kind)) {
                signs.add(new MapSurface.WarSign(MapSurface.WarSign.Kind.BATTLEFIELD, new GridPoint2(d.chunkX, d.chunkY),
                        null, UiTheme.MAP_BATTLEFIELD));
            }
        }
        long clock = worldManager.getHistory().warClock();
        if (worldManager.getCurrentLevel() == 1) {
            for (com.bpm.minotaur.gamedata.history.war.Encounter e : worldManager.currentEncounters()) {
                if (e.kind != com.bpm.minotaur.gamedata.history.war.Encounter.Kind.COLUMN) continue;
                signs.add(new MapSurface.WarSign(MapSurface.WarSign.Kind.COLUMN, e.chunkAt(clock), e.to,
                        HouseHeraldry.primary(world, e.houseA, catalog)));
            }
        }
        for (com.bpm.minotaur.gamedata.history.war.Encounter c : worldManager.currentCamps()) {
            signs.add(new MapSurface.WarSign(MapSurface.WarSign.Kind.CAMP, c.from, null, HouseHeraldry.primary(world, c.houseA, catalog)));
        }
        return signs;
    }

    public ExpeditionMapScreen(Tarmin2 game, Player player, Maze maze, GameScreen gameScreen) {
        super(game);
        this.player = player;
        this.maze = maze;
        this.gameScreen = gameScreen;
        this.worldManager = gameScreen != null ? gameScreen.getWorldManager() : null;
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    @Override
    public void show() {
        SoundManager.getInstance().playEvent("map_open");

        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        skin = new HudSkin();
        white = new TextureRegion(skin.getWhitePixel());
        initButtonStyles();
        surface = new MapSurface(skin, this::loadSummary);
        surface.setTileLoader(this::tilesFor);
        model = buildModel();
        surface.setModel(model, knowledge);
        surface.setFronts(frontMarks());
        surface.setWarSigns(warSigns());
        surface.setPlayer(playerMark());
        surface.setThreat(findThreatMark(surface.getFloor()));
        surface.setZoomListener(this::onZoomChanged);
        restoreView();

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(skin.getScreenBackdrop());
        root.pad(12f, 24f, 12f, 24f);

        root.add(buildHeader()).height(80f).width(1872f).colspan(2).padBottom(12f).row();

        Table frame = new Table();
        frame.setBackground(skin.getDoubleBorderPanel());
        Stack mapStack = new Stack();
        mapStack.add(surface);
        mapStack.add(buildOverlay());
        frame.add(mapStack).size(MAP_W, MAP_H).pad(FRAME_PAD);
        root.add(frame).width(MAP_W + 2 * FRAME_PAD).height(MAP_H + 2 * FRAME_PAD);

        Table side = new Table();
        side.setBackground(skin.getDoubleBorderPanel());
        side.pad(PANEL_PAD);
        panel = new Table();
        panel.top().left();
        ScrollPane scroll = new ScrollPane(panel, UiStyles.scrollPane(skin));
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        side.add(scroll).grow().width(PANEL_W - 2 * PANEL_PAD).padBottom(12f).row();
        side.add(buildActionGrid()).width(PANEL_W - 2 * PANEL_PAD);
        root.add(side).width(PANEL_W).height(MAP_H + 2 * FRAME_PAD).padLeft(16f).row();

        root.add(buildFooter()).height(40f).width(1872f).colspan(2).padTop(12f);

        stage.addActor(root);
        surface.addListener(new SurfaceInput());
        stage.setScrollFocus(surface);
        stage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        InputMultiplexer input = new InputMultiplexer();
        input.addProcessor(stage);
        input.addProcessor(new Keys());
        Gdx.input.setInputProcessor(input);

        refresh();
        if (reopenChunk) setZoom(MapSurface.Zoom.CHUNK);
    }

    private Label.LabelStyle micro(Color color) {
        return new Label.LabelStyle(skin.getFontMicro(), color);
    }

    private Label.LabelStyle value(Color color) {
        return new Label.LabelStyle(skin.getFontMain(), color);
    }

    private static Color alpha(Color c, float a) {
        return new Color(c.r, c.g, c.b, a);
    }

    /** A caption over a value, the header's cluster. */
    private Table cluster(Label caption, Actor value, float width) {
        Table t = new Table();
        t.add(caption).width(width).left().row();
        t.add(value).width(width).left().padTop(8f);
        return t;
    }

    private Actor divider() {
        Table d = new Table();
        d.setBackground(skin.solid(UiTheme.LINE_DIM));
        return d;
    }

    private Table buildHeader() {
        Table header = new Table();
        header.setBackground(skin.getDoubleBorderPanel());
        header.pad(0f, 20f, 0f, 20f);

        Table title = new Table();
        title.add(UiLabels.of("EXPEDITION MAP", new Label.LabelStyle(skin.getFontHeader(), UiTheme.GOLD))).left().row();
        title.add(UiLabels.of("THE KNOWN WORLD", micro(UiTheme.TEXT_DIM))).left().padTop(8f);
        header.add(title).width(270f).left();

        header.add(divider()).width(2f).height(44f).padLeft(10f).padRight(10f);
        floorLabel = UiLabels.ellipsized("SURFACE", value(UiTheme.TEXT));
        header.add(cluster(UiLabels.of("FLOOR", micro(UiTheme.TEXT_DIM)), floorLabel, 200f));

        header.add(divider()).width(2f).height(44f).padLeft(10f).padRight(10f);
        sealsRow = new Table();
        header.add(cluster(UiLabels.of("SEALS WON", micro(UiTheme.TEXT_DIM)), sealsRow, 130f));

        header.add(divider()).width(2f).height(44f).padLeft(10f).padRight(10f);
        doomRow = new Table();
        header.add(cluster(UiLabels.of("DOOM", micro(UiTheme.TEXT_DIM)), doomRow, 200f));

        header.add(divider()).width(2f).height(44f).padLeft(10f).padRight(10f);
        waypointCaption = UiLabels.ellipsized("WAYPOINT", micro(UiTheme.TEXT_DIM));
        waypointLabel = UiLabels.ellipsized("NONE", value(UiTheme.GOLD));
        header.add(cluster(waypointCaption, waypointLabel, 210f));

        header.add(divider()).width(2f).height(44f).padLeft(10f).padRight(10f);
        suggestedCaption = UiLabels.ellipsized("SUGGESTED", micro(UiTheme.TEXT_DIM));
        suggestedLabel = UiLabels.ellipsized("NONE", value(UiTheme.TEXT));
        header.add(cluster(suggestedCaption, suggestedLabel, 210f));

        header.add().growX();

        Table zoomGroup = new Table();
        worldZoomBtn = zoomButton("WORLD", MapSurface.Zoom.WORLD);
        regionZoomBtn = zoomButton("REGION", MapSurface.Zoom.REGION);
        chunkZoomBtn = zoomButton("CHUNK", MapSurface.Zoom.CHUNK);
        zoomGroup.add(worldZoomBtn).width(80f).height(44f);
        zoomGroup.add(regionZoomBtn).width(80f).height(44f);
        zoomGroup.add(chunkZoomBtn).width(80f).height(44f);
        header.add(zoomGroup).padRight(10f);

        header.add(iconButton("rotl", () -> rotate(-1))).size(44f).padRight(6f);
        header.add(iconButton("rotr", () -> rotate(1))).size(44f).padRight(10f);

        TextButton closeBtn = new TextButton("CLOSE  M", compactSecondary);
        closeBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                close();
            }
        });
        header.add(closeBtn).width(112f).height(44f);
        return header;
    }

    private TextButton zoomButton(String text, MapSurface.Zoom level) {
        TextButton b = new TextButton(text, compactSecondary);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setZoom(level);
            }
        });
        return b;
    }

    private Button iconButton(String glyph, Runnable action) {
        Button b = new Button(compactSecondary);
        b.add(new MapGlyph(white, glyph, null, UiTheme.TEXT, 3, false));
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return b;
    }

    private void initButtonStyles() {
        compactPrimary = new TextButton.TextButtonStyle();
        compactPrimary.font = skin.getFontMicro();
        compactPrimary.up = skin.getTabActive();
        compactPrimary.down = skin.solid(UiTheme.GOLD);
        compactPrimary.over = skin.getTabActive();
        compactPrimary.focused = skin.getTabActive();
        compactPrimary.fontColor = UiTheme.FOCUS;
        compactPrimary.downFontColor = UiTheme.ON_GOLD;
        compactPrimary.overFontColor = UiTheme.FOCUS;

        compactSecondary = new TextButton.TextButtonStyle();
        compactSecondary.font = skin.getFontMicro();
        compactSecondary.up = skin.getCardBg();
        compactSecondary.down = skin.solid(UiTheme.GOLD);
        compactSecondary.over = skin.getTabActive();
        compactSecondary.focused = skin.getTabActive();
        compactSecondary.fontColor = UiTheme.TEXT;
        compactSecondary.downFontColor = UiTheme.ON_GOLD;
        compactSecondary.overFontColor = UiTheme.GOLD;
    }

    /** A dark floating box over the map, as the prototype's rgba(11, 9, 7, .86) panels. */
    private Table floatingBox() {
        Table t = new Table();
        t.setBackground(skin.solid(alpha(UiTheme.BG_VOID, 0.86f)));
        return t;
    }

    private Table buildOverlay() {
        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.pad(16f);
        overlay.setTouchable(Touchable.childrenOnly);

        Table topRow = new Table();
        Table view = floatingBox();
        view.pad(14f, 16f, 14f, 16f);
        view.add(UiLabels.of("VIEW", micro(UiTheme.TEXT_DIM))).left().row();
        viewLevelLabel = UiLabels.of("REGION", value(UiTheme.GOLD));
        view.add(viewLevelLabel).left().padTop(8f).row();
        viewHintLabel = UiLabels.of("+ PINS GRAVES PORTALS DEPTH", micro(UiTheme.TEXT_DIM));
        view.add(viewHintLabel).left().padTop(8f);
        view.setTouchable(Touchable.disabled);
        topRow.add(view).left().top();

        toastTable = new Table();
        toastTable.setBackground(skin.solid(UiTheme.BG_PANEL));
        toastLabel = UiLabels.of("", micro(UiTheme.FOCUS));
        toastTable.add(toastLabel).pad(14f, 18f, 14f, 18f);
        toastTable.setVisible(false);
        toastTable.setTouchable(Touchable.disabled);
        topRow.add(toastTable).expandX().top().padTop(4f);

        compassWidget = new CompassWidget(skin, () -> surface == null ? 0f : surface.getDioramaRenderer().getCameraRotation());
        compassWidget.setTouchable(Touchable.disabled);
        topRow.add(compassWidget).size(104f).right().top();
        overlay.add(topRow).growX().top().row();

        floorStackTable = floatingBox();
        floorStackTable.pad(14f, 12f, 14f, 12f);
        overlay.add(floorStackTable).left().top().padTop(120f).row();

        overlay.add().grow().row();

        Table legend = buildLegend();
        legend.setTouchable(Touchable.disabled);
        overlay.add(legend).left().bottom();
        return overlay;
    }

    private void rebuildFloorStack() {
        if (floorStackTable == null) return;
        floorStackTable.clearChildren();
        floorStackTable.add(UiLabels.of("FLOOR  ] UP  [ DOWN", micro(UiTheme.TEXT_DIM))).left().padBottom(8f).row();
        int maxFloor = Math.max(surface.getFloor(), model.getMaxFloor());
        for (int f = 1; f <= maxFloor; f++) {
            final int target = f;
            TextButton b = new TextButton(UiGlyphs.sanitize(MapNames.floor(f).toUpperCase(Locale.ROOT)),
                    f == surface.getFloor() ? compactPrimary : compactSecondary);
            b.getLabel().setAlignment(Align.left);
            b.getLabelCell().padLeft(12f);
            b.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    changeFloor(target - surface.getFloor());
                }
            });
            floorStackTable.add(b).width(176f).height(40f).padBottom(6f).row();
        }
    }

    private Table legendItem(String glyph, Color bg, Color ink, String name) {
        Table item = new Table();
        item.add(new MapGlyph(white, glyph, bg, ink, 3, true)).padRight(8f);
        item.add(UiLabels.of(name, micro(UiTheme.TEXT)));
        return item;
    }

    private Table buildLegend() {
        Table t = floatingBox();
        t.pad(14f, 16f, 14f, 16f);
        t.add(UiLabels.of("PINS  (P CYCLES THE SELECTED CHUNK)", micro(UiTheme.TEXT_DIM))).left().padBottom(10f).row();
        Color ink = IsometricDioramaRenderer.COLOR_INK;

        Table pins = new Table();
        for (MapKnowledge.Pin pin : MapKnowledge.Pin.values()) {
            String g = MapSurface.glyphOf(pin);
            pins.add(legendItem(g, IsometricDioramaRenderer.PIN_COLORS.get(g), ink,
                    MapNames.pin(pin).toUpperCase(Locale.ROOT))).padRight(16f);
        }
        t.add(pins).left().padBottom(8f).row();

        Table marks = new Table();
        marks.add(legendItem("home", IsometricDioramaRenderer.COLOR_BRIGHT_GOLD, ink, "HOME")).padRight(16f);
        marks.add(legendItem("shelter", IsometricDioramaRenderer.COLOR_GOLD, ink, "LIT")).padRight(16f);
        marks.add(legendItem("shelter", IsometricDioramaRenderer.COLOR_COLD_SHELTER, ink, "COLD")).padRight(16f);
        marks.add(legendItem("shelter", IsometricDioramaRenderer.COLOR_RUMOUR_BG, IsometricDioramaRenderer.COLOR_GOLD, "RUMOUR")).padRight(16f);
        marks.add(legendItem("seal", IsometricDioramaRenderer.COLOR_SEAL_AMBER, ink, "SEAL")).padRight(16f);
        marks.add(legendItem("grave", IsometricDioramaRenderer.COLOR_GRAVE_STONE, ink, "GRAVE"));
        t.add(marks).left().padBottom(8f).row();

        Table more = new Table();
        more.add(legendItem("portal", IsometricDioramaRenderer.COLOR_PORTAL_LAVENDER, ink, "PORTAL")).padRight(16f);
        more.add(legendItem("ladder", IsometricDioramaRenderer.COLOR_LADDER_BRONZE, ink, "LADDER")).padRight(16f);
        more.add(legendItem("castle", IsometricDioramaRenderer.COLOR_CASTLE_PURPLE, UiTheme.BG_VOID, "CASTLE")).padRight(16f);
        Table you = new Table();
        you.add(new MapGlyph(white, "diamond_full", null, IsometricDioramaRenderer.COLOR_CYAN, 2, false)).padRight(8f);
        you.add(UiLabels.of("YOU", micro(UiTheme.TEXT)));
        more.add(you);
        t.add(more).left();
        return t;
    }

    private Table buildActionGrid() {
        Table grid = new Table();
        setWpBtn = actionButton("WAYPOINT  W", compactPrimary, this::toggleWaypoint);
        TextButton takeSug = actionButton("SUGGESTION  G", compactSecondary, this::takeSuggestion);
        zoomActionBtn = actionButton("ZOOM IN  ENTER", compactSecondary, () -> {
            if (surface.getZoom() == MapSurface.Zoom.CHUNK) zoomOut();
            else zoomIn();
        });
        TextButton pin = actionButton("CYCLE PIN  P", compactSecondary, this::cyclePin);

        float btnW = (PANEL_W - 2 * PANEL_PAD - 8f) / 2f;
        grid.add(setWpBtn).width(btnW).height(44f).padRight(8f).padBottom(8f);
        grid.add(takeSug).width(btnW).height(44f).padBottom(8f).row();
        grid.add(zoomActionBtn).width(btnW).height(44f).padRight(8f);
        grid.add(pin).width(btnW).height(44f);
        return grid;
    }

    private TextButton actionButton(String text, TextButton.TextButtonStyle style, Runnable action) {
        TextButton b = new TextButton(text, style);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return b;
    }

    private Table buildFooter() {
        Table footer = new Table();
        String[][] keyHints = {
                {"DRAG", "ARROWS", "PAN"},
                {"WHEEL", "+ -", "ZOOM"},
                {"[ ]", null, "FLOOR"},
                {"Q E", null, "ROTATE"},
                {"W", null, "WAYPOINT"},
                {"G", null, "SUGGEST"},
                {"P", null, "PIN"},
                {"C", null, "CENTRE"},
                {"ENTER", null, "ZOOM IN"},
                {"ESC", null, "BACK"},
                {"M", null, "CLOSE"}
        };
        Table hints = new Table();
        for (String[] kh : keyHints) {
            hints.add(keycap(kh[0])).padRight(4f);
            if (kh[1] != null) hints.add(keycap(kh[1])).padRight(4f);
            hints.add(UiLabels.of(kh[2], micro(UiTheme.TEXT_DIM))).padRight(14f);
        }
        footer.add(hints).left();
        footer.add().growX();
        footer.add(UiLabels.of("CLICK TWICE TO ZOOM", micro(UiTheme.TEXT_DIM))).right();
        return footer;
    }

    private Table keycap(String key) {
        Table badge = new Table();
        badge.setBackground(skin.getKeycap() != null ? skin.getKeycap() : skin.solid(UiTheme.BG_RAISED));
        badge.add(UiLabels.of(key, micro(UiTheme.TEXT))).pad(4f, 6f, 4f, 6f);
        return badge;
    }

    private MapModel buildModel() {
        if (worldManager != null) return worldManager.buildMapModel();
        return new MapModel(null, network, knowledge, floor -> new java.util.HashSet<>(), 1);
    }

    private MapSurface.PlayerMark playerMark() {
        MapSurface.PlayerMark mark = new MapSurface.PlayerMark();
        mark.floor = worldManager != null ? worldManager.getCurrentLevel() : 1;
        mark.chunk = worldManager != null && worldManager.getCurrentPlayerChunkId() != null
                ? new GridPoint2(worldManager.getCurrentPlayerChunkId()) : new GridPoint2(0, 0);
        if (player != null) {
            mark.tileX = player.getPosition().x;
            mark.tileY = player.getPosition().y;
            mark.facing = player.getFacing();
        }
        if (maze != null) {
            mark.chunkWidth = maze.getWidth();
            mark.chunkHeight = maze.getHeight();
        }
        return mark;
    }

    private MapModel.ThreatMark findThreatMark(int floor) {
        if (worldManager == null) return null;
        long worldSeed = worldManager.getWorldSeed();
        HistoryWorld world = worldManager.getHistory().world();
        if (world == null) return null;
        long clock = worldManager.getHistory().warClock();
        BeastTracks.Hunt hunt = worldManager.getHistory().hunt();
        for (Megabeast b : world.megabeasts()) {
            if (!b.isAlive() || !b.isAwake(world.season())) continue;
            if (hunt != null && hunt.beastId == b.id && clock < hunt.until && hunt.level == floor) {
                return new MapModel.ThreatMark(hunt.chunk(), floor, b.name, "Hunting you", 5);
            }
            if (floor == b.lairLevel) {
                GridPoint2 pos = BeastTracks.roamChunk(worldSeed, b, clock);
                return new MapModel.ThreatMark(pos, floor, b.name, "Roaming", 4);
            }
        }
        return null;
    }

    /** Reopens where the player left the map; a new world, or the first look, starts on the player. */
    private void restoreView() {
        BiomeManager world = model.getBiomes();
        MapSurface.PlayerMark p = playerMark();
        MapSurface.Zoom zoom;
        if (rememberedWorld != null && rememberedWorld == world && rememberedZoom != null) {
            cursors.putAll(rememberedCursors);
            surface.setFloor(Math.min(rememberedFloor, model.getMaxFloor()));
            zoom = rememberedZoom == MapSurface.Zoom.CHUNK ? MapSurface.Zoom.REGION : rememberedZoom;
        } else {
            surface.setFloor(p.floor);
            zoom = MapSurface.Zoom.REGION;
        }
        cursors.putIfAbsent(p.floor, new GridPoint2(p.chunk));
        surface.setCursor(cursorFor(surface.getFloor()));
        surface.getDioramaRenderer().setFloorImmediate(surface.getFloor());
        GridPoint2 c = surface.getCursor();
        float z = zoom == MapSurface.Zoom.WORLD ? IsometricDioramaRenderer.ZOOM_WORLD : IsometricDioramaRenderer.ZOOM_REGION;
        surface.getDioramaRenderer().setCameraImmediate(c.x + 0.5f, c.y + 0.5f, z, 0f);
        surface.setZoom(zoom);
        reopenChunk = rememberedWorld == world && rememberedZoom == MapSurface.Zoom.CHUNK;
    }

    private void rememberView() {
        rememberedWorld = model.getBiomes();
        rememberedZoom = surface.getZoom();
        rememberedFloor = surface.getFloor();
        cursors.put(surface.getFloor(), surface.getCursor());
        rememberedCursors.clear();
        rememberedCursors.putAll(cursors);
    }

    private GridPoint2 cursorFor(int floor) {
        GridPoint2 c = cursors.get(floor);
        if (c == null) {
            MapSurface.PlayerMark p = playerMark();
            c = new GridPoint2(p.floor == floor || floor == 1 ? p.chunk : new GridPoint2(0, 0));
            cursors.put(floor, c);
        }
        return new GridPoint2(c);
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private IsometricDioramaRenderer diorama() {
        return surface.getDioramaRenderer();
    }

    /** Keyboard pan: the view moves, and the selection follows what lands in the middle of it. */
    private void pan(float dx, float dy) {
        boolean chunk = surface.getZoom() == MapSurface.Zoom.CHUNK;
        float step = chunk ? PAN_STEP_CHUNK : PAN_STEP;
        diorama().panFbo(dx * step, dy * step);
        GridPoint2 c = diorama().pickChunkAtTarget(surface.getFloor(), surface::chunkTopZ);
        if (c == null) return;
        if (!c.equals(surface.getCursor())) selectChunk(c, false);
        if (chunk) {
            ChunkTiles t = surface.getTiles();
            float z = surface.getFloor() > 1 ? 5f : surface.chunkTopZ(c.x, c.y);
            com.badlogic.gdx.math.Vector2 w = diorama().targetCentre(z);
            if (t != null && t.width > 0) {
                int tx = Math.max(0, Math.min(t.width - 1, (int) ((w.x - c.x) * t.width)));
                int ty = Math.max(0, Math.min(t.height - 1, (int) ((w.y - c.y) * t.height)));
                surface.setTileCursor(new GridPoint2(tx, ty));
            }
        }
        refresh();
    }

    /** Selects a chunk; the camera follows only when asked (clicks select in place). */
    private void selectChunk(GridPoint2 c, boolean centre) {
        surface.setCursor(c);
        cursors.put(surface.getFloor(), new GridPoint2(c));
        if (surface.getZoom() == MapSurface.Zoom.CHUNK) loadSelectedTiles();
        if (centre) diorama().centerOn(c.x + 0.5f, c.y + 0.5f);
    }

    private void setCursor(GridPoint2 c) {
        selectChunk(c, false);
        refresh();
    }

    /** The selected chunk's tiles for the chunk view, with the tile cursor on the player or the middle. */
    private void loadSelectedTiles() {
        GridPoint2 c = surface.getCursor();
        ChunkTiles tiles = surface.getOrLoadTiles(surface.getFloor(), c);
        surface.setTiles(tiles);
        if (tiles == null) return;
        MapSurface.PlayerMark p = playerMark();
        if (p.floor == surface.getFloor() && c.equals(p.chunk) && player != null) {
            surface.setTileCursor(new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y));
        } else {
            surface.setTileCursor(new GridPoint2(tiles.width / 2, tiles.height / 2));
        }
    }

    private void changeFloor(int delta) {
        int next = Math.max(1, Math.min(model.getMaxFloor(), surface.getFloor() + delta));
        if (next == surface.getFloor()) return;
        cursors.put(surface.getFloor(), surface.getCursor());
        surface.setFloor(next);
        surface.setCursor(cursorFor(next));
        surface.setThreat(findThreatMark(next));
        if (surface.getZoom() == MapSurface.Zoom.CHUNK) loadSelectedTiles();
        say(next == 1 ? "Back on the surface." : MapNames.floor(next) + ": only explored chunks show below.");
        refresh();
    }

    /** Moves to a zoom level from a button or key. */
    private void setZoom(MapSurface.Zoom level) {
        surface.setZoom(level);
        onZoomChanged();
    }

    /** The zoom level changed, by a button, a key or the wheel. */
    private void onZoomChanged() {
        if (surface.getZoom() == MapSurface.Zoom.CHUNK) loadSelectedTiles();
        refresh();
    }

    private void zoomIn() {
        MapSurface.Zoom z = surface.getZoom();
        if (z == MapSurface.Zoom.CHUNK) return;
        setZoom(z.in());
    }

    private void zoomOut() {
        setZoom(surface.getZoom().out());
    }

    private void rotate(int dir) {
        diorama().rotate(dir);
    }

    private void toggleWaypoint() {
        int floor = surface.getFloor();
        GridPoint2 c = surface.getCursor();
        MapKnowledge.Spot wp = knowledge.getWaypoint();
        if (wp != null && wp.is(floor, c)) {
            knowledge.clearWaypoint();
            say("Waypoint cleared.");
        } else if (model.isMarkable(floor, c)) {
            knowledge.setWaypoint(floor, c);
            say("Waypoint set at " + coord(c) + ".");
        } else {
            say("You can only mark ground you know.");
        }
        refresh();
    }

    private void takeSuggestion() {
        GridPoint2 next = surface.getSuggestion();
        if (next == null) {
            say("No road to suggest in this world.");
            return;
        }
        MapKnowledge.Spot wp = knowledge.getWaypoint();
        if (wp != null && wp.is(1, next)) {
            say("Already following the suggestion.");
            return;
        }
        surface.setShowSuggestedRoute(true);
        knowledge.setWaypoint(1, next);
        if (surface.getFloor() != 1) changeFloor(1 - surface.getFloor());
        say("Suggestion taken: waypoint " + coord(next) + ".");
        selectChunk(next, true);
        refresh();
    }

    private void cyclePin() {
        int floor = surface.getFloor();
        GridPoint2 c = surface.getCursor();
        if (surface.getZoom() == MapSurface.Zoom.CHUNK && surface.getTiles() != null) {
            GridPoint2 tc = surface.getTileCursor();
            knowledge.cycleTilePin(floor, c, tc);
            MapKnowledge.Pin pin = knowledge.getTilePin(floor, c, tc);
            say(pin == null ? "Tile pin removed." : "Tile pin: " + MapNames.pin(pin) + ".");
            refresh();
            return;
        }
        if (!model.isMarkable(floor, c)) {
            say("You can only mark ground you know.");
            return;
        }
        knowledge.cyclePin(floor, c);
        MapKnowledge.Pin pin = knowledge.getPin(floor, c);
        say(pin == null ? "Pin removed." : "Pin: " + MapNames.pin(pin) + ".");
        refresh();
    }

    private void recenter() {
        MapSurface.PlayerMark p = playerMark();
        if (surface.getFloor() != p.floor) changeFloor(p.floor - surface.getFloor());
        selectChunk(p.chunk, false);
        diorama().centerOn(p.chunk.x + (p.tileX + 0.5f) / Math.max(1, p.chunkWidth),
                p.chunk.y + (p.tileY + 0.5f) / Math.max(1, p.chunkHeight));
        say("Centred on you.");
        refresh();
    }

    private void nextMarker() {
        List<MapModel.MacroMarker> markers = model.macroMarkers(surface.getFloor());
        if (markers.isEmpty()) {
            say("No macro markers on this floor.");
            return;
        }
        macroMarkerIndex = (macroMarkerIndex + 1) % markers.size();
        MapModel.MacroMarker m = markers.get(macroMarkerIndex);
        selectChunk(m.getChunk(), true);
        say("Marker: " + m.name + " (" + m.details + ")");
        refresh();
    }

    private void close() {
        rememberView();
        game.setScreen(gameScreen);
    }

    private void say(String message) {
        if (toastLabel != null && toastTable != null) {
            toastLabel.setText(UiGlyphs.sanitize(message.toUpperCase(Locale.ROOT)));
            toastTable.setVisible(true);
            toastTimer = 2.0f;
        }
    }

    private static void show(Label label, String text) {
        if (label != null) {
            label.setText(UiGlyphs.sanitize(text));
        }
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private boolean isLiveChunk(int floor, GridPoint2 c) {
        return worldManager != null && maze != null && floor == worldManager.getCurrentLevel()
                && c.equals(worldManager.getCurrentPlayerChunkId());
    }

    private ChunkData savedChunk(int floor, GridPoint2 c) {
        if (worldManager == null) return null;
        String key = floor + ":" + c.x + ":" + c.y;
        if (!chunkData.containsKey(key)) chunkData.put(key, worldManager.loadChunkDataReadOnly(floor, c));
        return chunkData.get(key);
    }

    /** A chunk's tiles: the live maze for the player's chunk, the save otherwise. Item icons are not needed. */
    private ChunkTiles tilesFor(int floor, GridPoint2 c) {
        if (isLiveChunk(floor, c)) return ChunkTiles.fromMaze(maze);
        return ChunkTiles.fromChunkData(savedChunk(floor, c), null, null);
    }

    private ChunkSummary loadSummary(int floor, GridPoint2 c) {
        if (isLiveChunk(floor, c)) return ChunkSummary.of(new ChunkData(maze));
        return ChunkSummary.of(savedChunk(floor, c));
    }

    // ------------------------------------------------------------------
    // Header and panel
    // ------------------------------------------------------------------

    private void refresh() {
        int floor = surface.getFloor();
        GridPoint2 c = surface.getCursor();
        MapSurface.Zoom z = surface.getZoom();

        show(floorLabel, MapNames.floor(floor).toUpperCase(Locale.ROOT));
        worldZoomBtn.setStyle(z == MapSurface.Zoom.WORLD ? compactPrimary : compactSecondary);
        regionZoomBtn.setStyle(z == MapSurface.Zoom.REGION ? compactPrimary : compactSecondary);
        chunkZoomBtn.setStyle(z == MapSurface.Zoom.CHUNK ? compactPrimary : compactSecondary);

        show(viewLevelLabel, MapNames.zoom(z).toUpperCase(Locale.ROOT));
        show(viewHintLabel, z == MapSurface.Zoom.WORLD ? "BIOMES ROADS SHELTERS"
                : z == MapSurface.Zoom.REGION ? "+ PINS GRAVES PORTALS DEPTH" : "TILES ITEMS EVENTS FACING");

        refreshSeals();
        refreshDoom();

        MapSurface.PlayerMark p = playerMark();
        MapKnowledge.Spot wp = knowledge.getWaypoint();
        if (wp == null) {
            show(waypointCaption, "WAYPOINT");
            show(waypointLabel, "NONE");
        } else {
            show(waypointCaption, "WAYPOINT / " + placeName(wp.getFloor(), wp.getChunk()));
            show(waypointLabel, wp.getFloor() != p.floor ? "ON " + MapNames.floor(wp.getFloor()).toUpperCase(Locale.ROOT)
                    : distance(p.chunk, wp.getChunk()));
        }

        GridPoint2 sug = surface.getSuggestion();
        if (sug == null) {
            show(suggestedCaption, "SUGGESTED");
            show(suggestedLabel, "NONE");
        } else if (wp != null && wp.is(1, sug)) {
            show(suggestedCaption, "SUGGESTED / IN HAND");
            show(suggestedLabel, "ON ROUTE");
        } else {
            show(suggestedCaption, "SUGGESTED / " + placeName(1, sug));
            show(suggestedLabel, distance(p.chunk, sug) + "  G");
        }

        setWpBtn.setText(wp != null && wp.is(floor, c) ? "CLEAR WAYPT  W" : "WAYPOINT  W");
        zoomActionBtn.setText(z == MapSurface.Zoom.CHUNK ? "ZOOM OUT  ESC" : "ZOOM IN  ENTER");

        rebuildFloorStack();
        rebuildPanel(floor, c);
    }

    private void refreshSeals() {
        sealsRow.clearChildren();
        int won = network.getSealCount();
        int total = ShelterRoads.ROAD_COUNT - 1;
        for (int i = 0; i < total; i++) {
            sealsRow.add(new MapGlyph(white, i < won ? "diamond_full" : "diamond", null,
                    i < won ? UiTheme.GOLD : UiTheme.LINE, 2, false)).padRight(6f);
        }
        sealsRow.add(UiLabels.of(won + "/" + total, value(UiTheme.TEXT))).padLeft(6f);
    }

    private void refreshDoom() {
        doomRow.clearChildren();
        int stage = DoomManager.getInstance().getDoomStage();
        for (int i = 0; i < 4; i++) {
            Table bar = new Table();
            bar.setBackground(skin.solid(i < stage ? IsometricDioramaRenderer.COLOR_THREAT : UiTheme.BG_RAISED));
            doomRow.add(bar).size(8f, 18f).padRight(3f);
        }
        doomRow.add(UiLabels.of("STAGE " + MapNames.roman(Math.max(1, stage)), value(UiTheme.TEXT))).padLeft(10f);
    }

    public static String coord(GridPoint2 c) {
        if (c == null) return "??";
        int x = c.x;
        int y = c.y;
        char col = (x >= 0 && x < 26) ? (char) ('A' + x) : '?';
        return x >= 0 && x < 26 && y >= 0 ? "" + col + (y + 1) : "(" + x + ", " + y + ")";
    }

    /** "3 CH NE": chunks and bearing, or HERE. */
    private static String distance(GridPoint2 from, GridPoint2 to) {
        int d = MapModel.distance(from, to);
        if (d == 0) return "HERE";
        return d + " CH " + compass(from, to);
    }

    /** A bearing without the arrows MapModel.bearing prefixes the cardinals with. */
    private static String compass(GridPoint2 from, GridPoint2 to) {
        String b = MapModel.bearing(from, to);
        return b.length() > 2 ? b.substring(b.length() - 1) : b;
    }

    /** What a chunk is called in the header: a seal site, a shelter, or its biome. */
    private String placeName(int floor, GridPoint2 c) {
        if (floor != 1) return MapNames.floor(floor).toUpperCase(Locale.ROOT);
        BiomeManager biomes = model.getBiomes();
        int sealRoad = biomes == null ? -1 : biomes.getSealRoad(c);
        if (sealRoad > 0 && model.isSealSiteKnown(sealRoad)) return "SEAL SITE";
        MapModel.ShelterMark mark = model.shelterAt(c);
        if (mark == MapModel.ShelterMark.RUMOURED) return "RUMOUR";
        if (mark != null) return "SHELTER " + coord(c);
        return MapNames.biome(biomes == null ? null : biomes.getBiome(c)).toUpperCase(Locale.ROOT);
    }

    static String dangerWord(int d) {
        switch (d) {
            case 1: return "Low";
            case 2: return "Wary";
            case 3: return "Perilous";
            case 4: return "Deadly";
            case 5: return "Lethal";
            default: return d > 5 ? "Extreme" : "Unknown";
        }
    }

    static String dangerPips(int d) {
        int filled = Math.min(5, Math.max(0, d));
        return "[" + "#".repeat(filled) + ".".repeat(5 - filled) + "]";
    }

    private String shelterText(GridPoint2 c, boolean known) {
        MapModel.ShelterMark mark = model.shelterAt(c);
        if (mark == MapModel.ShelterMark.HOME) return "Home shelter, lit";
        if (mark == MapModel.ShelterMark.LIT) return "Lit, claimed";
        if (mark == MapModel.ShelterMark.COLD) return "Cold, not yet claimed";
        if (mark == MapModel.ShelterMark.RUMOURED) return "Rumoured, beacon seen";
        return known ? "None" : "Unknown";
    }

    static String biomeDescription(Biome b) {
        if (b == null) return "Explored terrain. Paths and hazards are charted.";
        switch (b) {
            case MAZE: return "Dark stone walls and corridors that double back on themselves.";
            case FOREST: return "Dense pines. Little light reaches the ground.";
            case DESERT: return "Dunes, hoodoos, sandstone arches and bleached bones.";
            case MOUNTAINS: return "Crags and narrow ledges. Hard going, long views.";
            case LAKELANDS: return "Still water and reeds. Something lies wrecked on the shore.";
            case TUNDRA: return "Snow and ice. Tracks fill in fast.";
            case BLIGHT: return "Corrupted, cracked earth that glows where it splits.";
            case OCEAN: return "Dark water at the edge of the world.";
            default: return "Explored terrain. Paths and hazards are charted.";
        }
    }

    private String strataText(GridPoint2 c, boolean known) {
        int depth = model.deepestStratumBelow(c);
        if (depth > 0) return depth + (depth == 1 ? " stratum" : " strata") + ", ladder down";
        return known ? "None found" : "Unknown";
    }

    private String fromYouText(GridPoint2 c, int floor) {
        MapSurface.PlayerMark p = playerMark();
        if (p.floor != floor) return "On " + MapNames.floor(p.floor);
        return distance(p.chunk, c);
    }

    private String nearestFireText(GridPoint2 c, int floor) {
        if (floor != 1) return "None below";
        GridPoint2 lit = model.nearestLitShelter(c);
        if (lit == null) return "None lit";
        if (lit.equals(c)) return "Here";
        return coord(lit) + ", " + distance(c, lit);
    }

    private String pinText(GridPoint2 c, int floor) {
        MapKnowledge.Pin pin = knowledge.getPin(floor, c);
        if (pin != null) return MapNames.pin(pin);
        MapKnowledge.Pin topTilePin = knowledge.getHighestPriorityTilePin(floor, c);
        if (topTilePin != null) return "Tile: " + MapNames.pin(topTilePin);
        return "None  (P drops one)";
    }

    private float panelW() {
        return PANEL_W - 2 * PANEL_PAD - UiTheme.SCROLL_W;
    }

    private void statRow(Table table, String label, Actor value) {
        table.add(UiLabels.of(label, micro(UiTheme.TEXT_DIM))).width(STAT_LABEL_W).left().top().padTop(4f).padBottom(10f);
        table.add(value).width(panelW() - STAT_LABEL_W).left().padBottom(10f).row();
    }

    private void statRow(Table table, String label, String text, Color color) {
        Label v = UiLabels.wrapping(UiGlyphs.sanitize(text), value(color));
        statRow(table, label, v);
    }

    private void section(String title) {
        panel.add(UiLabels.of(title, micro(UiTheme.TEXT_DIM))).width(panelW()).left().padTop(6f).padBottom(8f).row();
    }

    private void line(String text, Color color) {
        UiLabels.wrapped(panel, UiGlyphs.sanitize(text), value(color), panelW()).left().padBottom(6f).row();
    }

    private void rule() {
        Table r = new Table();
        r.setBackground(skin.solid(UiTheme.LINE_DIM));
        panel.add(r).width(panelW()).height(2f).padTop(4f).padBottom(14f).row();
    }

    /** The selected chunk, at every zoom; the world and chunk views add their own sections below. */
    private void rebuildPanel(int floor, GridPoint2 c) {
        panel.clearChildren();
        MapModel.Knowledge known = model.knowledgeOf(floor, c);
        BiomeManager biomes = model.getBiomes();
        boolean visited = known == MapModel.Knowledge.VISITED;
        boolean isKnown = known != MapModel.Knowledge.UNKNOWN;

        section("SELECTED CHUNK");
        String title;
        if (floor == 1) {
            title = !isKnown && model.shelterAt(c) == null ? "Uncharted" : MapNames.biome(biomes == null ? null : biomes.getBiome(c));
        } else {
            title = MapNames.floor(floor);
        }
        Table titleRow = new Table();
        Table titleLeft = new Table();
        titleLeft.add(UiLabels.ellipsized(UiGlyphs.sanitize(title.toUpperCase(Locale.ROOT)),
                new Label.LabelStyle(skin.getFontHeader(), UiTheme.GOLD))).width(panelW() - 116f).left().row();
        titleLeft.add(UiLabels.of("CHUNK " + coord(c), micro(UiTheme.TEXT))).left().padTop(8f);
        titleRow.add(titleLeft).left().top().growX();

        String chip = visited ? "VISITED" : known == MapModel.Knowledge.SURVEYED ? "SURVEYED"
                : known == MapModel.Knowledge.GLIMPSED ? "GLIMPSED" : "UNKNOWN";
        Color chipInk = visited ? UiTheme.GOLD : isKnown ? UiTheme.TEXT : UiTheme.TEXT_DIM;
        Table chipBox = new Table();
        chipBox.setBackground(skin.solid(visited ? UiTheme.LINE_DIM : UiTheme.BG_VOID));
        chipBox.add(UiLabels.of(chip, micro(chipInk))).pad(8f, 10f, 8f, 10f);
        titleRow.add(chipBox).right().top();
        panel.add(titleRow).width(panelW()).left().padBottom(14f).row();

        String desc;
        if (visited) {
            desc = biomeDescription(floor == 1 && biomes != null ? biomes.getBiome(c) : null);
            if (floor != 1) desc = "Explored cave. Walls, ladders and loot are charted.";
        } else if (known == MapModel.Knowledge.SURVEYED) {
            int tier = com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().getCartographerDeskTier();
            desc = "Cartographer survey (tier " + tier + "). Charted from afar.";
        } else if (known == MapModel.Knowledge.GLIMPSED) {
            desc = "Glimpsed from a neighbouring chunk. Only its biome is known, faintly.";
        } else {
            desc = "Never seen. Still lost in the fog.";
        }
        line(desc, UiTheme.TEXT_DIM);
        rule();

        Table stats = new Table();
        stats.left().top();
        int danger = (worldManager != null && (visited || (known == MapModel.Knowledge.SURVEYED
                && com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().getCartographerDeskTier() >= 3)))
                ? worldManager.calculateEffectiveDifficulty(c, floor) : 0;
        Table dangerRow = new Table();
        for (int i = 0; i < 5; i++) {
            Table pip = new Table();
            pip.setBackground(skin.solid(i < danger ? IsometricDioramaRenderer.COLOR_SEAL_AMBER : UiTheme.LINE_DIM));
            dangerRow.add(pip).size(12f).padRight(4f);
        }
        dangerRow.add(UiLabels.of(danger > 0 ? dangerWord(danger) : "Unknown", value(danger >= 4 ? UiTheme.DANGER : UiTheme.TEXT))).padLeft(6f);
        statRow(stats, "DANGER", dangerRow);
        statRow(stats, "SHELTER", shelterText(c, visited), UiTheme.TEXT);
        if (floor == 1) statRow(stats, "STRATA BELOW", strataText(c, visited), UiTheme.TEXT);
        statRow(stats, "FROM YOU", fromYouText(c, floor), UiTheme.TEXT);
        statRow(stats, "NEAREST FIRE", nearestFireText(c, floor), UiTheme.TEXT);
        statRow(stats, "FROM HOME", floor == 1 ? distance(HOME, c) : "-", UiTheme.TEXT);
        MapKnowledge.Pin pin = knowledge.getPin(floor, c);
        Table pinRow = new Table();
        if (pin != null) {
            String g = MapSurface.glyphOf(pin);
            pinRow.add(new MapGlyph(white, g, IsometricDioramaRenderer.PIN_COLORS.get(g), IsometricDioramaRenderer.COLOR_INK, 3, true)).padRight(10f);
        }
        pinRow.add(UiLabels.wrapping(UiGlyphs.sanitize(pinText(c, floor)), value(UiTheme.TEXT))).growX();
        statRow(stats, "PIN", pinRow);
        panel.add(stats).width(panelW()).left().row();
        rule();

        ChunkSummary summary = visited ? surface.summary(floor, c, true) : null;
        List<String> notes = new ArrayList<>();
        if (floor == 1) {
            if (biomes != null && biomes.isCastleChunk(c) && model.isCastleKnown()) notes.add("Castle Tarmin");
            int sealRoad = biomes == null ? -1 : biomes.getSealRoad(c);
            if (sealRoad > 0 && model.isSealSiteKnown(sealRoad)) {
                String holder = worldManager == null ? ""
                        : com.bpm.minotaur.gamedata.boss.SealLord.holderLine(worldManager.getHistory().world(),
                        com.bpm.minotaur.gamedata.boss.SealLord.gashIndexForRoad(sealRoad));
                notes.add("Seal site of " + MapNames.road(sealRoad)
                        + (network.hasSeal(sealRoad) ? ", seal won" : "") + (holder.isEmpty() ? "" : ", " + holder));
            }
            for (BiomePortal portal : BiomePortal.values()) {
                GridPoint2 gate = model.gateChunk(portal);
                if (gate != null && gate.equals(c)) notes.add("Ancient gate: " + portal.getDisplayName());
            }
        }
        if (summary != null) {
            if (summary.hasUpLadder()) notes.add("Ladder up");
            if (summary.hasDownLadder()) notes.add("Ladder down");
            if (summary.hasReturnPortal()) notes.add("Return portal");
        }
        for (String n : notes) line("* " + n, UiTheme.GOLD);

        section("LOOT LEFT BEHIND");
        if (summary != null && summary.getLoot() > 0) {
            line(UiNames.plural(summary.getLoot(), "item") + " left here", UiTheme.TEXT);
        } else {
            line(visited ? "Nothing left behind here." : "Unknown until visited.", UiTheme.TEXT_DIM);
        }

        section("GRAVES OF PAST LIVES");
        if (summary != null && !summary.getGraves().isEmpty()) {
            for (ChunkSummary.Grave g : summary.getGraves()) {
                String state = g.isDefeated() ? "Laid to rest" : g.isAwakened() ? "Risen" : "Sleeping";
                line("Grave of " + g.getName(), UiTheme.TEXT);
                line(g.getEpitaph() + " (" + state + ")", UiTheme.TEXT_DIM);
            }
        } else {
            line(visited ? "No past life fell here." : "Unknown until visited.", UiTheme.TEXT_DIM);
        }

        if (surface.getZoom() == MapSurface.Zoom.CHUNK) appendChunkView(floor, c);
        if (surface.getZoom() == MapSurface.Zoom.WORLD) appendWorldView(floor, c);
    }

    private void appendChunkView(int floor, GridPoint2 c) {
        rule();
        ChunkTiles tiles = surface.getTiles();
        GridPoint2 tc = surface.getTileCursor();
        section(tiles == null ? "TILES" : "SELECTED TILE  " + tc.x + ", " + tc.y);
        if (tiles == null) {
            line("No tiles charted here.", UiTheme.TEXT_DIM);
            return;
        }
        if (tiles.isSeen(tc.x, tc.y)) {
            line(tiles.isSolid(tc.x, tc.y) ? "Solid wall" : "Open floor", UiTheme.TEXT);
            String event = tiles.events.get(tc);
            if (event != null) line("Feature: " + UiNames.fromConstant(event), UiTheme.GOLD);
            if (tiles.itemTiles.contains(tc)) line("Something lies here", UiTheme.GOLD);
        } else {
            line("Unexplored tile", UiTheme.TEXT_DIM);
        }
        MapKnowledge.Pin tilePin = knowledge.getTilePin(floor, c, tc);
        if (tilePin != null) line("Pin: " + MapNames.pin(tilePin), UiTheme.FOCUS);

        section("OPEN LEADS");
        List<String> leads = tiles.describeOpenLeads();
        if (leads.isEmpty()) line("No unexplored leads here.", UiTheme.TEXT_DIM);
        for (String lead : leads) line("> " + lead, UiTheme.GOLD);
    }

    private void appendWorldView(int floor, GridPoint2 c) {
        if (floor == 1) {
            rule();
            section("GATE DESTINATIONS");
            for (BiomePortal portal : BiomePortal.values()) {
                GridPoint2 gate = model.gateChunk(portal);
                if (gate == null) continue;
                line(portal.getDisplayName() + ": " + distance(HOME, gate), UiTheme.TEXT);
            }
        }
        rule();
        section("ALSO SIGHTED");
        MapModel.ThreatMark tm = surface.getThreat();
        if (tm != null) {
            line(tm.name + " (" + tm.status + "), " + distance(c, tm.chunk), UiTheme.DANGER);
        } else {
            line("No threat nearby.", UiTheme.TEXT_DIM);
        }
        Set<GridPoint2> visited = model.visited(floor);
        line("Explored: " + MapNames.chunks(visited.size()), UiTheme.TEXT);
        if (floor == 1) line("Lit shelters: " + network.getClaimed().size(), UiTheme.TEXT);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    /** Click a chunk to select it, click it again to zoom in; in the chunk view, click a tile. Drag pans; the wheel zooms. */
    private final class SurfaceInput extends InputListener {
        private float downX;
        private float downY;
        private float lastX;
        private float lastY;
        private boolean dragged;

        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            downX = x;
            downY = y;
            lastX = x;
            lastY = y;
            dragged = false;
            return true;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
            if (Math.abs(downX - x) > 6 || Math.abs(downY - y) > 6) dragged = true;
            if (dragged) diorama().panScreen(-(x - lastX), -(y - lastY), surface.getWidth(), surface.getHeight());
            lastX = x;
            lastY = y;
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            if (dragged) return;
            GridPoint2 c = surface.chunkAt(x, y);
            if (c == null) return;
            boolean same = c.equals(surface.getCursor());
            if (surface.getZoom() == MapSurface.Zoom.CHUNK) {
                if (!same) {
                    setCursor(c);
                    return;
                }
                GridPoint2 tc = surface.tileAt(x, y);
                if (tc != null) {
                    surface.setTileCursor(tc);
                    refresh();
                }
                return;
            }
            if (same) zoomIn();
            else setCursor(c);
        }

        @Override
        public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
            diorama().zoomAt(x, y, surface.getWidth(), surface.getHeight(), (float) Math.exp(-amountY * 0.2f));
            return true;
        }
    }

    private final class Keys extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            SettingsManager s = SettingsManager.getInstance();
            if (keycode == s.getKey("MAP")) {
                close();
                return true;
            }
            if (keycode == s.getKey("MAP_WAYPOINT")) {
                toggleWaypoint();
                return true;
            }
            if (keycode == s.getKey("MAP_SUGGEST")) {
                takeSuggestion();
                return true;
            }
            if (keycode == s.getKey("MAP_PIN") || keycode == Input.Keys.P) {
                cyclePin();
                return true;
            }
            if (keycode == s.getKey("MAP_RECENTER") || keycode == Input.Keys.C) {
                recenter();
                return true;
            }
            switch (keycode) {
                case Input.Keys.Q: rotate(-1); return true;
                case Input.Keys.E: rotate(1); return true;
                case Input.Keys.TAB: nextMarker(); return true;
                case Input.Keys.ESCAPE:
                    if (surface.getZoom() == MapSurface.Zoom.WORLD) close();
                    else zoomOut();
                    return true;
                case Input.Keys.LEFT: pan(-1, 0); return true;
                case Input.Keys.RIGHT: pan(1, 0); return true;
                case Input.Keys.UP: pan(0, -1); return true;
                case Input.Keys.DOWN: pan(0, 1); return true;
                case Input.Keys.ENTER:
                case Input.Keys.SPACE:
                    zoomIn();
                    return true;
                case Input.Keys.PLUS:
                case Input.Keys.EQUALS:
                case Input.Keys.NUMPAD_ADD:
                    diorama().zoomBy(1.4f);
                    return true;
                case Input.Keys.MINUS:
                case Input.Keys.NUMPAD_SUBTRACT:
                    diorama().zoomBy(1f / 1.4f);
                    return true;
                case Input.Keys.RIGHT_BRACKET:
                case Input.Keys.PAGE_UP:
                    changeFloor(-1); // up, toward the surface
                    return true;
                case Input.Keys.LEFT_BRACKET:
                case Input.Keys.PAGE_DOWN:
                    changeFloor(1); // down, into the strata
                    return true;
                default:
                    return false;
            }
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void render(float delta) {
        if (toastTimer > 0f) {
            toastTimer -= delta;
            if (toastTimer <= 0f && toastTable != null) toastTable.setVisible(false);
        }
        Gdx.gl.glClearColor(UiTheme.BG_VOID.r, UiTheme.BG_VOID.g, UiTheme.BG_VOID.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        if (compassWidget != null) {
            compassWidget.dispose();
            compassWidget = null;
        }
        if (stage != null) {
            stage.dispose();
            stage = null;
        }
        if (surface != null) {
            surface.dispose();
            surface = null;
        }
        if (skin != null) {
            skin.dispose();
            skin = null;
        }
    }
}
