package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
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
import com.bpm.minotaur.ui.KeyHintLegend;
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
 * <p>A Scene2D layout -- header breadcrumbs/badges, left depth rail, side panel, key legend -- around
 * one custom actor, {@link MapSurface}, that draws the map itself. Everything shown comes from
 * {@link MapModel}. {@code GameMode.CLASSIC} keeps its old map. See
 * docs/DEsign/Requirements_ Expedition Map.md and docs/DEsign/Expedition Map Redesign.pdf.
 */
public class ExpeditionMapScreen extends BaseScreen {

    private static final float PANEL_W = 400f;

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

    private Stage stage;
    private HudSkin skin;
    private MapSurface surface;
    private MapModel model;

    // Header actors
    private Label floorBreadcrumbLabel;
    private TextButton worldZoomBtn;
    private TextButton regionZoomBtn;
    private TextButton chunkZoomBtn;
    private TextButton rotLBtn;
    private TextButton rotRBtn;
    private TextButton closeBtn;

    private Label sealsLabel;
    private Label doomLabel;
    private Label waypointHeaderLabel;
    private Label waypointLabel;
    private Label suggestedHeaderLabel;
    private Label suggestedLabel;

    // Overlay floating widgets
    private Table viewIndicatorTable;
    private Label viewLevelLabel;
    private Label viewHintLabel;
    private Table floorStackTable;
    private Table pinLegendTable;
    private CompassWidget compassWidget;
    private Table toastTable;
    private Label toastLabel;
    private float toastTimer = 0f;
    private TextButton fitAreaBtn;
    private TextButton centeredOnYouBtn;

    // Side panel & action grid
    private Table panel;
    private TextButton setWpBtn;
    private TextButton takeSugBtn;
    private TextButton zoomActionBtn;
    private TextButton cyclePinBtn;
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
                signs.add(new MapSurface.WarSign(MapSurface.WarSign.Kind.BATTLEFIELD, new com.badlogic.gdx.math.GridPoint2(d.chunkX, d.chunkY),
                        null, com.bpm.minotaur.ui.UiTheme.MAP_BATTLEFIELD));
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
        initButtonStyles();
        surface = new MapSurface(skin, this::loadSummary);
        surface.setTileLoader(this::tilesFor);
        model = buildModel();
        surface.setModel(model, knowledge);
        surface.setFronts(frontMarks());
        surface.setWarSigns(warSigns());
        surface.setPlayer(playerMark());
        surface.setThreat(findThreatMark(surface.getFloor()));
        restoreView();

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(skin.getScreenBackdrop());
        root.pad(12f, 24f, 12f, 24f);

        // Header (row 0, height 80)
        root.add(buildHeader()).height(80f).growX().colspan(2).padBottom(12f).row();

        // Main Row: Frame (1456x912) and Side Panel (400x912)
        Table frame = new Table();
        frame.setBackground(skin.getDoubleBorderPanel());
        Stack mapStack = new Stack();
        mapStack.add(surface);
        mapStack.add(buildOverlay());
        frame.add(mapStack).grow().pad(6f);
        root.add(frame).width(1456f).height(912f);

        Table side = new Table();
        side.setBackground(skin.getDoubleBorderPanel());
        side.pad(16f);

        panel = new Table();
        panel.top().left();
        ScrollPane scroll = new ScrollPane(panel, UiStyles.scrollPane(skin));
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        side.add(scroll).grow().width(PANEL_W - 32f).padBottom(12f).row();

        side.add(buildActionGrid()).growX().height(96f);
        root.add(side).width(PANEL_W).height(912f).padLeft(16f).row();

        // Footer (row 2, height 40)
        root.add(buildFooter()).height(40f).growX().colspan(2).padTop(12f);

        stage.addActor(root);
        surface.addListener(new SurfaceInput());
        stage.setScrollFocus(surface);
        stage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        InputMultiplexer input = new InputMultiplexer();
        input.addProcessor(stage);
        input.addProcessor(new Keys());
        Gdx.input.setInputProcessor(input);

        refresh();
        if (reopenChunk) zoomIn();
    }

    private Table buildHeader() {
        Table header = new Table();
        header.setBackground(skin.getDoubleBorderPanel());
        header.pad(0, 20f, 0, 20f);

        // Title cluster
        Table titleTable = new Table();
        titleTable.add(UiLabels.of("EXPEDITION MAP", new Label.LabelStyle(skin.getFontHeader(), UiTheme.GOLD))).left().row();
        titleTable.add(UiLabels.of("TARMIN / THE KNOWN WORLD", UiStyles.caption(skin, UiTheme.TEXT_DIM))).left().padTop(2f);
        header.add(titleTable).left().padRight(16f);

        // Divider
        header.add(createDivider()).padRight(16f);

        // Floor cluster
        Table floorCluster = new Table();
        floorCluster.add(UiLabels.of("FLOOR", UiStyles.caption(skin, UiTheme.TEXT_DIM))).left().row();
        floorBreadcrumbLabel = UiLabels.of("SURFACE", UiStyles.body(skin, UiTheme.TEXT));
        floorCluster.add(floorBreadcrumbLabel).left().padTop(2f);
        header.add(floorCluster).left().padRight(16f);

        // Divider
        header.add(createDivider()).padRight(16f);

        // Seals Won cluster
        Table sealsCluster = new Table();
        sealsCluster.add(UiLabels.of("SEALS WON", UiStyles.caption(skin, UiTheme.TEXT_DIM))).left().row();
        sealsLabel = UiLabels.of("0/4", UiStyles.body(skin, UiTheme.TEXT));
        sealsCluster.add(sealsLabel).left().padTop(2f);
        header.add(sealsCluster).left().padRight(16f);

        // Divider
        header.add(createDivider()).padRight(16f);

        // Doom cluster
        Table doomCluster = new Table();
        doomCluster.add(UiLabels.of("DOOM", UiStyles.caption(skin, UiTheme.TEXT_DIM))).left().row();
        doomLabel = UiLabels.of("STAGE I", UiStyles.body(skin, UiTheme.DANGER));
        doomCluster.add(doomLabel).left().padTop(2f);
        header.add(doomCluster).left().padRight(16f);

        // Divider
        header.add(createDivider()).padRight(16f);

        // Waypoint cluster
        Table wpCluster = new Table();
        waypointHeaderLabel = UiLabels.of("WAYPOINT", UiStyles.caption(skin, UiTheme.TEXT_DIM));
        wpCluster.add(waypointHeaderLabel).left().row();
        waypointLabel = UiLabels.of("NONE", UiStyles.body(skin, UiTheme.GOLD));
        wpCluster.add(waypointLabel).left().padTop(2f);
        header.add(wpCluster).left().padRight(16f);

        // Divider
        header.add(createDivider()).padRight(16f);

        // Suggested cluster
        Table sugCluster = new Table();
        suggestedHeaderLabel = UiLabels.of("SUGGESTED", UiStyles.caption(skin, UiTheme.TEXT_DIM));
        sugCluster.add(suggestedHeaderLabel).left().row();
        suggestedLabel = UiLabels.of("NONE", UiStyles.body(skin, UiTheme.TEXT));
        sugCluster.add(suggestedLabel).left().padTop(2f);
        header.add(sugCluster).left().padRight(16f);

        // Flexible space
        header.add().growX();

        // Zoom button group
        Table zoomGroup = new Table();
        worldZoomBtn = new TextButton("WORLD", compactSecondary);
        worldZoomBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (surface.getZoom() != MapSurface.Zoom.WORLD) {
                    surface.setZoom(MapSurface.Zoom.WORLD);
                    surface.setTiles(null);
                    refresh();
                }
            }
        });
        zoomGroup.add(worldZoomBtn).width(76f).height(38f);

        regionZoomBtn = new TextButton("REGION", compactSecondary);
        regionZoomBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (surface.getZoom() != MapSurface.Zoom.REGION) {
                    surface.setZoom(MapSurface.Zoom.REGION);
                    surface.setTiles(null);
                    refresh();
                }
            }
        });
        zoomGroup.add(regionZoomBtn).width(76f).height(38f);

        chunkZoomBtn = new TextButton("CHUNK", compactSecondary);
        chunkZoomBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (surface.getZoom() != MapSurface.Zoom.CHUNK) {
                    zoomIn();
                }
            }
        });
        zoomGroup.add(chunkZoomBtn).width(76f).height(38f);
        header.add(zoomGroup).padRight(14f);

        // Rotate buttons
        Table rotGroup = new Table();
        rotLBtn = new TextButton("[Q]", compactSecondary);
        rotLBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (surface.isDioramaEnabled() && surface.getDioramaRenderer() != null) {
                    surface.getDioramaRenderer().rotate(-1);
                }
            }
        });
        rotGroup.add(rotLBtn).width(40f).height(38f).padRight(4f);

        rotRBtn = new TextButton("[E]", compactSecondary);
        rotRBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (surface.isDioramaEnabled() && surface.getDioramaRenderer() != null) {
                    surface.getDioramaRenderer().rotate(1);
                }
            }
        });
        rotGroup.add(rotRBtn).width(40f).height(38f);
        header.add(rotGroup).padRight(14f);

        // Close button
        closeBtn = new TextButton("CLOSE  M", compactSecondary);
        closeBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                close();
            }
        });
        header.add(closeBtn).width(96f).height(38f);

        return header;
    }

    private void initButtonStyles() {
        compactPrimary = new TextButton.TextButtonStyle();
        compactPrimary.font = skin.getFontSmall();
        compactPrimary.up = skin.getTabActive();
        compactPrimary.down = skin.solid(UiTheme.GOLD);
        compactPrimary.over = skin.getTabActive();
        compactPrimary.focused = skin.getTabActive();
        compactPrimary.fontColor = UiTheme.FOCUS;
        compactPrimary.downFontColor = UiTheme.ON_GOLD;
        compactPrimary.overFontColor = UiTheme.FOCUS;

        compactSecondary = new TextButton.TextButtonStyle();
        compactSecondary.font = skin.getFontSmall();
        compactSecondary.up = skin.getCardBg();
        compactSecondary.down = skin.solid(UiTheme.GOLD);
        compactSecondary.over = skin.getTabActive();
        compactSecondary.focused = skin.getTabActive();
        compactSecondary.fontColor = UiTheme.TEXT;
        compactSecondary.downFontColor = UiTheme.ON_GOLD;
        compactSecondary.overFontColor = UiTheme.GOLD;
    }

    private Actor createDivider() {
        Table divider = new Table();
        divider.setBackground(skin.solid(new Color(0x3a / 255f, 0x2a / 255f, 0x18 / 255f, 1f)));
        divider.setWidth(2f);
        divider.setHeight(44f);
        return divider;
    }

    private Actor createDividerHorizontal(float width) {
        Table divider = new Table();
        divider.setBackground(skin.solid(new Color(0x3a / 255f, 0x2a / 255f, 0x18 / 255f, 1f)));
        divider.setWidth(width);
        divider.setHeight(2f);
        return divider;
    }

    private Table buildOverlay() {
        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.pad(16f);

        // Top Row: View Indicator (left), Toast Banner (center), Compass (right)
        Table topRow = new Table();

        viewIndicatorTable = new Table();
        viewIndicatorTable.setBackground(skin.solid(new Color(0x0b / 255f, 0x09 / 255f, 0x07 / 255f, 0.86f)));
        viewIndicatorTable.pad(10f, 14f, 10f, 14f);
        viewIndicatorTable.add(UiLabels.of("VIEW", UiStyles.caption(skin, UiTheme.TEXT_DIM))).left().row();
        viewLevelLabel = UiLabels.of("REGION", UiStyles.body(skin, UiTheme.GOLD));
        viewIndicatorTable.add(viewLevelLabel).left().padTop(2f).row();
        viewHintLabel = UiLabels.of("+ PINS GRAVES PORTALS DEPTH", UiStyles.caption(skin, UiTheme.TEXT_DIM));
        viewIndicatorTable.add(viewHintLabel).left().padTop(2f);
        topRow.add(viewIndicatorTable).left().top();

        toastTable = new Table();
        toastTable.setBackground(skin.solid(new Color(0x15 / 255f, 0x10 / 255f, 0x0b / 255f, 0.95f)));
        toastLabel = UiLabels.of("", UiStyles.body(skin, UiTheme.FOCUS));
        toastTable.add(toastLabel).pad(10f, 18f, 10f, 18f);
        toastTable.setVisible(false);
        topRow.add(toastTable).growX().top().center();

        compassWidget = new CompassWidget(skin, () -> {
            if (surface != null && surface.getDioramaRenderer() != null) {
                return surface.getDioramaRenderer().getCameraRotation();
            }
            return 0f;
        });
        topRow.add(compassWidget).size(104f).right().top();

        overlay.add(topRow).growX().top().row();

        // Floor stack buttons
        floorStackTable = new Table();
        floorStackTable.setBackground(skin.solid(new Color(0x0b / 255f, 0x09 / 255f, 0x07 / 255f, 0.86f)));
        floorStackTable.pad(10f, 12f, 10f, 12f);
        overlay.add(floorStackTable).left().padTop(140f).row();

        overlay.add().grow().row();

        // Bottom Row: Pin Legend (left), Area/You buttons (right)
        Table bottomRow = new Table();
        pinLegendTable = buildPinLegend();
        bottomRow.add(pinLegendTable).left().bottom();

        Table overlayBtns = new Table();
        fitAreaBtn = new TextButton("FIT KNOWN AREA", compactSecondary);
        fitAreaBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                fitToKnownArea();
            }
        });

        centeredOnYouBtn = new TextButton("CENTRE ON YOU", compactSecondary);
        centeredOnYouBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                recenter();
            }
        });
        overlayBtns.add(fitAreaBtn).height(34f).padRight(8f);
        overlayBtns.add(centeredOnYouBtn).height(34f);
        bottomRow.add().growX();
        bottomRow.add(overlayBtns).right().bottom();

        overlay.add(bottomRow).growX().bottom();

        return overlay;
    }

    private void rebuildFloorStack() {
        if (floorStackTable == null) return;
        floorStackTable.clearChildren();
        floorStackTable.add(UiLabels.of("FLOOR  ] UP  [ DOWN", UiStyles.caption(skin, UiTheme.TEXT_DIM))).left().padBottom(6f).row();

        int maxFloor = Math.max(surface.getFloor(), model.getMaxFloor());
        for (int f = 1; f <= maxFloor; f++) {
            String name;
            if (f == 1) {
                name = "SURFACE";
            } else {
                boolean visited = !model.visited(f).isEmpty();
                name = visited ? (f - 1) + " STRATUM" : "? STRATUM";
            }
            final int targetFloor = f;
            boolean isCurrent = (f == surface.getFloor());
            TextButton floorBtn = new TextButton(UiGlyphs.sanitize(name),
                    isCurrent ? compactPrimary : compactSecondary);
            floorBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    changeFloor(targetFloor - surface.getFloor());
                }
            });
            floorStackTable.add(floorBtn).width(160f).height(34f).padBottom(4f).row();
        }
    }

    private Table buildPinLegend() {
        Table table = new Table();
        table.setBackground(skin.solid(new Color(0x0b / 255f, 0x09 / 255f, 0x07 / 255f, 0.88f)));
        table.pad(10f, 14f, 10f, 14f);

        table.add(UiLabels.of("PINS  (P CYCLES THE SELECTED CHUNK)", UiStyles.caption(skin, UiTheme.TEXT_DIM))).colspan(6).left().padBottom(8f).row();

        // Row 1: Pins
        String[] pinNames = {"DANGER", "LOOT", "RETURN", "TRADER", "LOCKED", "UNKNOWN"};
        Color[] pinColors = {
                new Color(0xd0 / 255f, 0x7a / 255f, 0x5a / 255f, 1f),
                new Color(0xe2 / 255f, 0xbe / 255f, 0x6a / 255f, 1f),
                new Color(0xcd / 255f, 0xbf / 255f, 0x9a / 255f, 1f),
                new Color(0xbf / 255f, 0xa8 / 255f, 0x6c / 255f, 1f),
                new Color(0xa8 / 255f, 0x97 / 255f, 0x7c / 255f, 1f),
                new Color(0xb3 / 255f, 0xa5 / 255f, 0x8e / 255f, 1f)
        };
        for (int i = 0; i < pinNames.length; i++) {
            Table item = new Table();
            Table pip = new Table();
            pip.setBackground(skin.solid(pinColors[i]));
            item.add(pip).size(9f).padRight(6f);
            item.add(UiLabels.of(pinNames[i], UiStyles.caption(skin, UiTheme.TEXT)));
            table.add(item).left().padRight(12f);
        }
        table.row().padTop(6f);

        // Row 2: Landmarks
        String[] markNames = {"SHELTER", "SEAL", "GRAVE", "PORTAL", "LADDER", "YOU"};
        Color[] markColors = {
                new Color(0xe9 / 255f, 0xb4 / 255f, 0x4c / 255f, 1f),
                new Color(0xd0 / 255f, 0x68 / 255f, 0x3f / 255f, 1f),
                new Color(0xb9 / 255f, 0xb1 / 255f, 0xa2 / 255f, 1f),
                new Color(0xc9 / 255f, 0xa6 / 255f, 0xe8 / 255f, 1f),
                new Color(0xb8 / 255f, 0x86 / 255f, 0x2f / 255f, 1f),
                new Color(0x6c / 255f, 0xcf / 255f, 0xe0 / 255f, 1f)
        };
        for (int i = 0; i < markNames.length; i++) {
            Table item = new Table();
            Table pip = new Table();
            pip.setBackground(skin.solid(markColors[i]));
            item.add(pip).size(9f).padRight(6f);
            item.add(UiLabels.of(markNames[i], UiStyles.caption(skin, UiTheme.TEXT)));
            table.add(item).left().padRight(12f);
        }

        return table;
    }

    private Table buildActionGrid() {
        Table grid = new Table();
        setWpBtn = new TextButton("SET WAYPOINT  W", compactPrimary);
        setWpBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleWaypoint();
            }
        });

        takeSugBtn = new TextButton("TAKE SUGGESTION  G", compactSecondary);
        takeSugBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                takeSuggestion();
            }
        });

        zoomActionBtn = new TextButton("ZOOM IN  ENTER", compactSecondary);
        zoomActionBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (surface.getZoom() == MapSurface.Zoom.CHUNK) zoomOut();
                else zoomIn();
            }
        });

        cyclePinBtn = new TextButton("CYCLE PIN  P", compactSecondary);
        cyclePinBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                cyclePin();
            }
        });

        float btnW = (PANEL_W - 32f - 8f) / 2f;
        grid.add(setWpBtn).width(btnW).height(44f).padRight(8f).padBottom(8f);
        grid.add(takeSugBtn).width(btnW).height(44f).padBottom(8f).row();
        grid.add(zoomActionBtn).width(btnW).height(44f).padRight(8f);
        grid.add(cyclePinBtn).width(btnW).height(44f);
        return grid;
    }

    private Table buildFooter() {
        Table footer = new Table();
        Table hints = new Table();
        String[][] keyHints = {
                {"ARROWS", "PAN"},
                {"+ -", "ZOOM"},
                {"[ ]", "FLOOR"},
                {"Q E", "ROTATE"},
                {"W", "WAYPOINT"},
                {"G", "SUGGESTION"},
                {"P", "PIN"},
                {"C", "CENTRE"},
                {"ENTER", "ZOOM IN"},
                {"ESC", "BACK OUT"},
                {"M", "CLOSE"}
        };
        for (String[] kh : keyHints) {
            Table badge = new Table();
            badge.setBackground(skin.getKeycap() != null ? skin.getKeycap() : skin.solid(new Color(0x1f / 255f, 0x17 / 255f, 0x0f / 255f, 1f)));
            badge.add(UiLabels.of(kh[0], UiStyles.caption(skin, UiTheme.TEXT))).pad(2f, 5f, 2f, 5f);
            hints.add(badge).padRight(4f);
            hints.add(UiLabels.of(kh[1], UiStyles.caption(skin, UiTheme.TEXT_DIM))).padRight(14f);
        }
        footer.add(hints).left().growX();

        Label clickNote = UiLabels.of("CLICK A CHUNK TO SELECT, AGAIN TO ZOOM", UiStyles.caption(skin, UiTheme.TEXT_DIM));
        footer.add(clickNote).right();

        return footer;
    }

    private static String keyName(String action) {
        String s = Input.Keys.toString(SettingsManager.getInstance().getKey(action));
        return s == null ? "?" : s;
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
        if (rememberedWorld != null && rememberedWorld == world && rememberedZoom != null) {
            cursors.putAll(rememberedCursors);
            surface.setFloor(Math.min(rememberedFloor, model.getMaxFloor()));
            surface.setZoom(rememberedZoom == MapSurface.Zoom.CHUNK ? MapSurface.Zoom.REGION : rememberedZoom);
        } else {
            surface.setFloor(p.floor);
            surface.setZoom(MapSurface.Zoom.REGION);
        }
        cursors.putIfAbsent(p.floor, new GridPoint2(p.chunk));
        surface.setCursor(cursorFor(surface.getFloor()));
        if (surface.isDioramaEnabled() && surface.getDioramaRenderer() != null) {
            GridPoint2 c = surface.getCursor();
            float z = surface.getZoom() == MapSurface.Zoom.WORLD ? IsometricDioramaRenderer.ZOOM_WORLD : IsometricDioramaRenderer.ZOOM_REGION;
            surface.getDioramaRenderer().setCameraImmediate(c.x + 0.5f, c.y + 0.5f, z, 0f);
        }
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

    private void moveCursor(int dx, int dy) {
        if (surface.getZoom() == MapSurface.Zoom.CHUNK) {
            surface.moveTileCursor(dx, dy);
            refresh();
            return;
        }
        GridPoint2 next = surface.getCursor().add(dx, dy);
        setCursor(next);
    }

    private void setCursor(GridPoint2 c) {
        surface.setCursor(c);
        cursors.put(surface.getFloor(), new GridPoint2(c));
        refresh();
    }

    private void changeFloor(int delta) {
        int next = Math.max(1, Math.min(model.getMaxFloor(), surface.getFloor() + delta));
        if (next == surface.getFloor()) return;
        cursors.put(surface.getFloor(), surface.getCursor());
        surface.setFloor(next);
        if (surface.getZoom() == MapSurface.Zoom.CHUNK) surface.setZoom(MapSurface.Zoom.REGION);
        surface.setCursor(cursorFor(next));
        refresh();
    }

    private void zoomIn() {
        MapSurface.Zoom z = surface.getZoom();
        if (z == MapSurface.Zoom.CHUNK) return;
        if (z == MapSurface.Zoom.REGION) {
            ChunkTiles tiles = tilesFor(surface.getFloor(), surface.getCursor());
            if (tiles == null) {
                say("Only explored ground can be opened.");
                return;
            }
            surface.setTiles(tiles);
            MapSurface.PlayerMark p = playerMark();
            if (p.floor == surface.getFloor() && surface.getCursor().equals(p.chunk)) {
                surface.setTileCursor(new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y));
            } else {
                surface.setTileCursor(new GridPoint2(tiles.width / 2, tiles.height / 2));
            }
        }
        surface.setZoom(z.in());
        refresh();
    }

    private void zoomOut() {
        surface.setZoom(surface.getZoom().out());
        surface.setTiles(null);
        refresh();
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
            say("Waypoint set.");
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
        surface.setShowSuggestedRoute(true);
        knowledge.setWaypoint(1, next);
        if (surface.getFloor() != 1) changeFloor(1 - surface.getFloor());
        say("Suggested route displayed. Waypoint set.");
        setCursor(next);
    }

    private void cyclePin() {
        int floor = surface.getFloor();
        GridPoint2 c = surface.getCursor();
        if (surface.getZoom() == MapSurface.Zoom.CHUNK) {
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
        say(pin == null ? "Pin removed." : "Pinned: " + MapNames.pin(pin) + ".");
        refresh();
    }

    private void recenter() {
        MapSurface.PlayerMark p = playerMark();
        if (surface.getFloor() != p.floor) changeFloor(p.floor - surface.getFloor());
        if (surface.getZoom() == MapSurface.Zoom.CHUNK) {
            surface.setTiles(tilesFor(p.floor, p.chunk));
            surface.setTileCursor(new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y));
        }
        setCursor(p.chunk);
        if (surface.isDioramaEnabled() && surface.getDioramaRenderer() != null) {
            surface.getDioramaRenderer().centerOn(p.chunk.x + (p.tileX + 0.5f) / 8f, p.chunk.y + (p.tileY + 0.5f) / 8f);
        }
        say("Centred on you.");
    }

    private void fitToKnownArea() {
        Set<GridPoint2> visited = model.visited(surface.getFloor());
        if (visited.isEmpty()) {
            setCursor(new GridPoint2(0, 0));
            if (surface.isDioramaEnabled() && surface.getDioramaRenderer() != null) {
                surface.getDioramaRenderer().centerOn(0.5f, 0.5f);
            }
            say("Centred on home.");
            return;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (GridPoint2 c : visited) {
            minX = Math.min(minX, c.x);
            maxX = Math.max(maxX, c.x);
            minY = Math.min(minY, c.y);
            maxY = Math.max(maxY, c.y);
        }
        GridPoint2 mid = new GridPoint2((minX + maxX) / 2, (minY + maxY) / 2);
        setCursor(mid);
        if (surface.isDioramaEnabled() && surface.getDioramaRenderer() != null) {
            surface.getDioramaRenderer().centerOn(mid.x + 0.5f, mid.y + 0.5f);
        }
        say("Centred on known area.");
    }

    private void nextMarker() {
        List<MapModel.MacroMarker> markers = model.macroMarkers(surface.getFloor());
        if (markers.isEmpty()) {
            say("No macro markers on this floor.");
            return;
        }
        macroMarkerIndex = (macroMarkerIndex + 1) % markers.size();
        MapModel.MacroMarker m = markers.get(macroMarkerIndex);
        setCursor(m.getChunk());
        say("Marker: " + m.name + " (" + m.details + ")");
    }

    private void close() {
        rememberView();
        game.setScreen(gameScreen);
    }

    private void say(String message) {
        if (toastLabel != null && toastTable != null) {
            toastLabel.setText(UiGlyphs.sanitize(message));
            toastTable.setVisible(true);
            toastTimer = 2.5f;
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

    private ChunkTiles tilesFor(int floor, GridPoint2 c) {
        if (isLiveChunk(floor, c)) return ChunkTiles.fromMaze(maze);
        if (worldManager == null) return null;
        return ChunkTiles.fromChunkData(worldManager.loadChunkDataReadOnly(floor, c),
                game.getItemDataManager(), game.getAssetManager());
    }

    private ChunkSummary loadSummary(int floor, GridPoint2 c) {
        if (isLiveChunk(floor, c)) return ChunkSummary.of(new ChunkData(maze));
        return ChunkSummary.of(worldManager == null ? null : worldManager.loadChunkDataReadOnly(floor, c));
    }

    // ------------------------------------------------------------------
    // Panels
    // ------------------------------------------------------------------

    private void refresh() {
        int floor = surface.getFloor();
        GridPoint2 c = surface.getCursor();

        surface.setThreat(findThreatMark(floor));

        show(floorBreadcrumbLabel, MapNames.floor(floor).toUpperCase(Locale.ROOT));
        MapSurface.Zoom z = surface.getZoom();
        worldZoomBtn.setStyle(z == MapSurface.Zoom.WORLD ? compactPrimary : compactSecondary);
        regionZoomBtn.setStyle(z == MapSurface.Zoom.REGION ? compactPrimary : compactSecondary);
        chunkZoomBtn.setStyle(z == MapSurface.Zoom.CHUNK ? compactPrimary : compactSecondary);

        if (z == MapSurface.Zoom.WORLD) {
            show(viewLevelLabel, "WORLD");
            show(viewHintLabel, "BIOMES ROADS SHELTERS");
        } else if (z == MapSurface.Zoom.REGION) {
            show(viewLevelLabel, "REGION");
            show(viewHintLabel, "+ PINS GRAVES PORTALS DEPTH");
        } else {
            show(viewLevelLabel, "CHUNK");
            show(viewHintLabel, "TILES ITEMS EVENTS FACING");
        }

        fitAreaBtn.setVisible(z == MapSurface.Zoom.WORLD);
        centeredOnYouBtn.setVisible(z == MapSurface.Zoom.CHUNK);

        int sealCount = network.getSealCount();
        int totalSeals = ShelterRoads.ROAD_COUNT - 1;
        String sealStars = "*".repeat(Math.max(0, sealCount)) + "-".repeat(Math.max(0, totalSeals - sealCount));
        show(sealsLabel, "[" + sealStars + "] " + sealCount + "/" + totalSeals);

        int doomStage = DoomManager.getInstance().getDoomStage();
        String romanStage = (doomStage == 1) ? "STAGE I" :
                (doomStage == 2) ? "STAGE II" :
                (doomStage == 3) ? "STAGE III" :
                (doomStage == 4) ? "STAGE IV" :
                (doomStage == 5) ? "STAGE V" :
                (doomStage >= 6) ? "STAGE VI" : "DORMANT";
        String doomBars = "#".repeat(Math.min(6, Math.max(0, doomStage))) + ".".repeat(Math.max(0, 6 - doomStage));
        show(doomLabel, "[" + doomBars + "] " + romanStage);

        MapKnowledge.Spot wp = knowledge.getWaypoint();
        if (wp == null) {
            show(waypointHeaderLabel, "WAYPOINT");
            show(waypointLabel, "NONE");
        } else {
            String wpCoord = coord(wp.getChunk());
            show(waypointHeaderLabel, "WAYPOINT / " + wpCoord);
            MapSurface.PlayerMark p = playerMark();
            if (wp.getFloor() != p.floor) {
                show(waypointLabel, "ON " + MapNames.floor(wp.getFloor()).toUpperCase(Locale.ROOT));
            } else {
                int d = MapModel.distance(p.chunk, wp.getChunk());
                String b = MapModel.bearing(p.chunk, wp.getChunk());
                show(waypointLabel, d == 0 ? "HERE" : d + " CH " + b);
            }
        }

        GridPoint2 sug = surface.getSuggestion();
        if (sug == null) {
            show(suggestedHeaderLabel, "SUGGESTED");
            show(suggestedLabel, "NONE");
        } else {
            boolean following = (wp != null && wp.is(1, sug));
            show(suggestedHeaderLabel, following ? "SUGGESTED / IN HAND" : "SUGGESTED / ROAD");
            if (following) {
                show(suggestedLabel, "ON ROUTE");
            } else {
                MapSurface.PlayerMark p = playerMark();
                int d = MapModel.distance(p.chunk, sug);
                String b = MapModel.bearing(p.chunk, sug);
                show(suggestedLabel, d + " CH " + b + "  G");
            }
        }

        if (setWpBtn != null) {
            if (wp != null && wp.is(floor, c)) {
                setWpBtn.setText("CLEAR WAYPOINT  W");
            } else {
                setWpBtn.setText("SET WAYPOINT  W");
            }
        }

        if (zoomActionBtn != null) {
            if (z == MapSurface.Zoom.CHUNK) {
                zoomActionBtn.setText("ZOOM OUT  ENTER");
            } else {
                zoomActionBtn.setText("ZOOM IN  ENTER");
            }
        }

        rebuildFloorStack();
        rebuildPanel(floor, c);
    }

    public static String coord(GridPoint2 c) {
        if (c == null) return "??";
        int x = c.x;
        int y = c.y;
        char col = (x >= 0 && x < 26) ? (char) ('A' + x) : '?';
        return "" + col + (y + 1);
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

    private String shelterText(GridPoint2 c) {
        MapModel.ShelterMark mark = model.shelterAt(c);
        if (mark == MapModel.ShelterMark.HOME) return "Home shelter, lit";
        if (mark == MapModel.ShelterMark.LIT) return "Lit, claimed";
        if (mark == MapModel.ShelterMark.COLD) return "Cold, not yet claimed";
        if (mark == MapModel.ShelterMark.RUMOURED) return "Rumoured, beacon seen";
        return "None";
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

    private String strataText(GridPoint2 c) {
        int depth = model.deepestStratumBelow(c);
        if (depth > 0) return depth + " stratum, ladder down";
        return "None found";
    }

    private String fromYouText(GridPoint2 c, int floor) {
        MapSurface.PlayerMark p = playerMark();
        if (p.floor == floor && c.equals(p.chunk)) {
            return "HERE";
        } else if (p.floor != floor) {
            return "ON " + MapNames.floor(p.floor).toUpperCase(Locale.ROOT);
        } else {
            int d = MapModel.distance(p.chunk, c);
            String b = MapModel.bearing(p.chunk, c);
            return d + " CH " + b;
        }
    }

    private String nearestFireText(GridPoint2 c, int floor) {
        if (floor == 1) {
            GridPoint2 lit = model.nearestLitShelter(c);
            if (lit == null) return "None lit";
            if (lit.equals(c)) return "AT CAMP";
            int d = MapModel.distance(c, lit);
            String b = MapModel.bearing(c, lit);
            return coord(lit) + ", " + d + " CH " + b;
        }
        return "None";
    }

    private String fromHomeText(GridPoint2 c) {
        GridPoint2 home = new GridPoint2(0, 0);
        int d = MapModel.distance(home, c);
        String b = MapModel.bearing(home, c);
        return d == 0 ? "HERE" : d + " CH " + b;
    }

    private String pinText(GridPoint2 c, int floor) {
        MapKnowledge.Pin pin = knowledge.getPin(floor, c);
        if (pin != null) return MapNames.pin(pin).toUpperCase(Locale.ROOT);
        MapKnowledge.Pin topTilePin = knowledge.getHighestPriorityTilePin(floor, c);
        if (topTilePin != null) return "Tile: " + MapNames.pin(topTilePin).toUpperCase(Locale.ROOT);
        return "None";
    }

    private void addStatRow(Table table, String label, String value, float totalW, Color valColor) {
        Label lbl = UiLabels.ellipsized(label, UiStyles.caption(skin, UiTheme.TEXT_DIM));
        Label val = UiLabels.ellipsized(value, UiStyles.body(skin, valColor));
        table.add(lbl).width(128f).left().padBottom(4f);
        table.add(val).width(totalW - 128f).left().padBottom(4f).row();
    }

    private void rebuildPanel(int floor, GridPoint2 c) {
        panel.clearChildren();
        float w = PANEL_W - 32f - UiTheme.SCROLL_W;

        switch (surface.getZoom()) {
            case WORLD:
                rebuildWorldPanel(floor, c, w);
                break;
            case REGION:
                rebuildRegionPanel(floor, c, w);
                break;
            case CHUNK:
                rebuildChunkPanel(floor, c, w);
                break;
        }
    }

    private void rebuildRegionPanel(int floor, GridPoint2 c, float w) {
        MapModel.Knowledge known = model.knowledgeOf(floor, c);
        BiomeManager biomes = model.getBiomes();

        // Selected chunk header
        panel.add(UiLabels.of("SELECTED CHUNK", UiStyles.caption(skin, UiTheme.TEXT_DIM))).width(w).left().row();

        String title;
        if (floor == 1) {
            title = (known == MapModel.Knowledge.UNKNOWN && model.shelterAt(c) == null) ? "Unknown Land"
                    : MapNames.biome(biomes == null ? null : biomes.getBiome(c));
        } else {
            title = MapNames.floor(floor);
        }

        Table titleRow = new Table();
        Table titleLeft = new Table();
        titleLeft.add(UiLabels.ellipsized(title.toUpperCase(Locale.ROOT), UiStyles.body(skin, UiTheme.GOLD))).left().row();
        titleLeft.add(UiLabels.of("CHUNK " + coord(c) + " (" + c.x + ", " + c.y + ")", UiStyles.caption(skin, UiTheme.TEXT))).left().padTop(2f);
        titleRow.add(titleLeft).left().growX();

        String kStr = known == MapModel.Knowledge.VISITED ? "EXPLORED" :
                known == MapModel.Knowledge.SURVEYED ? "SURVEYED" :
                known == MapModel.Knowledge.GLIMPSED ? "GLIMPSED" : "UNEXPLORED";
        Label.LabelStyle chipStyle = known == MapModel.Knowledge.VISITED ? UiStyles.caption(skin, UiTheme.SUCCESS) :
                known == MapModel.Knowledge.SURVEYED ? UiStyles.caption(skin, UiTheme.GOLD) :
                known == MapModel.Knowledge.GLIMPSED ? UiStyles.caption(skin, UiTheme.TEXT) : UiStyles.caption(skin, UiTheme.TEXT_DIM);
        Table chipTable = new Table();
        chipTable.setBackground(skin.solid(new Color(0x1f / 255f, 0x17 / 255f, 0x0f / 255f, 1f)));
        chipTable.add(UiLabels.of("[" + kStr + "]", chipStyle)).pad(4f, 8f, 4f, 8f);
        titleRow.add(chipTable).right();
        panel.add(titleRow).width(w).left().padTop(6f).row();

        String desc;
        if (known == MapModel.Knowledge.VISITED) {
            Biome b = biomes != null ? biomes.getBiome(c) : null;
            desc = biomeDescription(b);
        } else if (known == MapModel.Knowledge.SURVEYED) {
            int tier = com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().getCartographerDeskTier();
            desc = "Cartographer Survey (Tier " + tier + "). Elevation and contours charted from afar.";
        } else if (known == MapModel.Knowledge.GLIMPSED) {
            desc = "Glimpsed from a neighbouring chunk. Only its biome is known, faintly.";
        } else {
            desc = "Never seen. Still lost in the fog.";
        }
        UiLabels.wrapped(panel, desc, UiStyles.caption(skin, UiTheme.TEXT_DIM), w).padTop(8f).padBottom(10f).row();

        panel.add(createDividerHorizontal(w)).padBottom(10f).row();

        Table stats = new Table();
        stats.left();
        int danger = (worldManager != null && (known == MapModel.Knowledge.VISITED || (known == MapModel.Knowledge.SURVEYED && com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().getCartographerDeskTier() >= 3)))
                ? worldManager.calculateEffectiveDifficulty(c, floor) : 0;
        String dangerStr = (known == MapModel.Knowledge.VISITED || danger > 0)
                ? dangerPips(danger) + " " + dangerWord(danger) : "Unknown";
        addStatRow(stats, "DANGER", dangerStr, w, danger >= 4 ? UiTheme.DANGER : UiTheme.TEXT);
        addStatRow(stats, "SHELTER", shelterText(c), w, UiTheme.TEXT);
        addStatRow(stats, "STRATA BELOW", strataText(c), w, UiTheme.TEXT);
        addStatRow(stats, "FROM YOU", fromYouText(c, floor), w, UiTheme.TEXT);
        addStatRow(stats, "NEAREST FIRE", nearestFireText(c, floor), w, UiTheme.TEXT);
        addStatRow(stats, "FROM HOME", fromHomeText(c), w, UiTheme.TEXT);
        addStatRow(stats, "PIN", pinText(c, floor), w, UiTheme.GOLD);
        panel.add(stats).width(w).left().padBottom(10f).row();

        panel.add(createDividerHorizontal(w)).padBottom(10f).row();

        ChunkSummary summary = known == MapModel.Knowledge.VISITED ? surface.summary(floor, c, true) : null;
        List<String> lines = new ArrayList<>();
        if (floor == 1) {
            if (biomes != null && biomes.isCastleChunk(c) && model.isCastleKnown()) lines.add("Castle Tarmin");
            int sealRoad = biomes == null ? -1 : biomes.getSealRoad(c);
            if (sealRoad > 0 && model.isSealSiteKnown(sealRoad)) {
                String holder = worldManager == null ? ""
                        : com.bpm.minotaur.gamedata.boss.SealLord.holderLine(worldManager.getHistory().world(),
                        com.bpm.minotaur.gamedata.boss.SealLord.gashIndexForRoad(sealRoad));
                lines.add(UiGlyphs.sanitize("Seal site of " + MapNames.road(sealRoad)
                        + (network.hasSeal(sealRoad) ? ", seal won" : "") + (holder.isEmpty() ? "" : ", " + holder)));
            }
            for (BiomePortal portal : BiomePortal.values()) {
                GridPoint2 gate = model.gateChunk(portal);
                if (gate != null && gate.equals(c)) {
                    lines.add("Ancient Gate: " + portal.getDisplayName());
                }
            }
        }
        if (summary != null) {
            if (summary.hasUpLadder()) lines.add("Ladder up");
            if (summary.hasDownLadder()) lines.add("Ladder down");
            if (summary.hasReturnPortal()) lines.add("Return portal");
        }
        for (String line : lines) {
            UiLabels.wrapped(panel, "* " + line, UiStyles.body(skin, UiTheme.GOLD), w).padBottom(4f).row();
        }

        panel.add(UiLabels.of("LOOT LEFT BEHIND", UiStyles.caption(skin, UiTheme.TEXT_DIM))).width(w).left().padTop(6f).row();
        if (summary != null && summary.getLoot() > 0) {
            UiLabels.wrapped(panel, UiNames.plural(summary.getLoot(), "item") + " left here", UiStyles.body(skin, UiTheme.TEXT), w).padBottom(4f).row();
        } else {
            UiLabels.wrapped(panel, known == MapModel.Knowledge.VISITED ? "Nothing left behind here." : "Unknown until visited.",
                    UiStyles.caption(skin, UiTheme.TEXT_DIM), w).padBottom(4f).row();
        }

        panel.add(UiLabels.of("GRAVES OF PAST LIVES", UiStyles.caption(skin, UiTheme.TEXT_DIM))).width(w).left().padTop(6f).row();
        if (summary != null && !summary.getGraves().isEmpty()) {
            for (ChunkSummary.Grave g : summary.getGraves()) {
                String state = g.isDefeated() ? "Laid to rest" : g.isAwakened() ? "Risen" : "Sleeping";
                UiLabels.wrapped(panel, "Grave of " + g.getName(), UiStyles.body(skin, UiTheme.TEXT), w).row();
                UiLabels.wrapped(panel, g.getEpitaph() + " (" + state + ")", UiStyles.caption(skin, UiTheme.TEXT_DIM), w).padBottom(4f).row();
            }
        } else {
            UiLabels.wrapped(panel, known == MapModel.Knowledge.VISITED ? "No past life fell here." : "Unknown until visited.",
                    UiStyles.caption(skin, UiTheme.TEXT_DIM), w).padBottom(4f).row();
        }
    }

    private void rebuildWorldPanel(int floor, GridPoint2 c, float w) {
        MapModel.MacroMarker markerAtCursor = null;
        for (MapModel.MacroMarker m : model.macroMarkers(floor)) {
            if (m.getChunk().equals(c)) {
                markerAtCursor = m;
                break;
            }
        }

        panel.add(UiLabels.of("WORLD OVERVIEW", UiStyles.caption(skin, UiTheme.TEXT_DIM))).width(w).left().row();
        String headerTitle = markerAtCursor != null ? markerAtCursor.name : "KNOWN REALM";
        panel.add(UiLabels.ellipsized(headerTitle.toUpperCase(Locale.ROOT), UiStyles.body(skin, UiTheme.GOLD))).width(w).left().row();
        panel.add(UiLabels.of("CHUNK " + coord(c) + " (" + c.x + ", " + c.y + ")", UiStyles.caption(skin, UiTheme.TEXT))).width(w).left().padTop(2f).padBottom(8f).row();

        panel.add(createDividerHorizontal(w)).padBottom(10f).row();

        Table stats = new Table();
        stats.left();
        addStatRow(stats, "FROM HOME", fromHomeText(c), w, UiTheme.TEXT);
        addStatRow(stats, "FROM YOU", fromYouText(c, floor), w, UiTheme.TEXT);
        panel.add(stats).width(w).left().padBottom(10f).row();

        if (floor == 1) {
            panel.add(createDividerHorizontal(w)).padBottom(10f).row();
            panel.add(UiLabels.of("GATE DESTINATIONS", UiStyles.caption(skin, UiTheme.GOLD))).width(w).left().padBottom(4f).row();
            for (BiomePortal portal : BiomePortal.values()) {
                GridPoint2 gate = model.gateChunk(portal);
                if (gate != null) {
                    String info = portal.getDisplayName() + ": " + MapModel.bearing(new GridPoint2(0, 0), gate)
                            + " " + MapNames.chunks(MapModel.distance(new GridPoint2(0, 0), gate));
                    UiLabels.wrapped(panel, info, UiStyles.body(skin), w).padBottom(4f).row();
                }
            }
        }

        panel.add(createDividerHorizontal(w)).padBottom(10f).row();
        panel.add(UiLabels.of("ALSO SIGHTED", UiStyles.caption(skin, UiTheme.GOLD))).width(w).left().padBottom(4f).row();
        MapModel.ThreatMark tm = surface.getThreat();
        if (tm != null) {
            String tStr = tm.name + " (" + tm.status + ") - " + MapModel.bearing(c, tm.chunk) + " " + MapNames.chunks(MapModel.distance(c, tm.chunk));
            UiLabels.wrapped(panel, "Threat: " + tStr, UiStyles.body(skin, UiTheme.DANGER), w).padBottom(4f).row();
        } else {
            UiLabels.wrapped(panel, "No imminent threats nearby", UiStyles.caption(skin, UiTheme.TEXT_DIM), w).padBottom(4f).row();
        }

        Set<GridPoint2> visited = model.visited(floor);
        UiLabels.wrapped(panel, "Explored: " + visited.size() + " chunks", UiStyles.body(skin), w).padTop(6f).padBottom(4f).row();
        if (floor == 1) {
            UiLabels.wrapped(panel, "Lit shelters: " + network.getClaimed().size(), UiStyles.body(skin), w).padBottom(4f).row();
        }
    }

    private void rebuildChunkPanel(int floor, GridPoint2 c, float w) {
        panel.add(UiLabels.of("CHUNK VIEW", UiStyles.caption(skin, UiTheme.TEXT_DIM))).width(w).left().row();
        panel.add(UiLabels.ellipsized(("CHUNK " + coord(c) + " (" + c.x + ", " + c.y + ")").toUpperCase(Locale.ROOT), UiStyles.body(skin, UiTheme.GOLD))).width(w).left().row();
        panel.add(UiLabels.ellipsized(MapNames.floor(floor).toUpperCase(Locale.ROOT), UiStyles.caption(skin, UiTheme.TEXT))).width(w).left().padTop(2f).padBottom(8f).row();

        panel.add(createDividerHorizontal(w)).padBottom(10f).row();

        GridPoint2 tc = surface.getTileCursor();
        panel.add(UiLabels.of("SELECTED TILE (" + tc.x + ", " + tc.y + ")", UiStyles.caption(skin, UiTheme.GOLD))).width(w).left().padBottom(4f).row();

        ChunkTiles tiles = surface.getTiles();
        if (tiles != null && tiles.isSeen(tc.x, tc.y)) {
            String type = tiles.isSolid(tc.x, tc.y) ? "Solid wall" : "Open floor";
            UiLabels.wrapped(panel, type, UiStyles.body(skin), w).row();
            String event = tiles.events.get(tc);
            if (event != null) {
                UiLabels.wrapped(panel, "Feature: " + UiGlyphs.sanitize(event), UiStyles.body(skin, UiTheme.GOLD), w).row();
            }
        } else {
            UiLabels.wrapped(panel, "Unexplored tile", UiStyles.caption(skin, UiTheme.TEXT_DIM), w).row();
        }

        MapKnowledge.Pin tilePin = knowledge.getTilePin(floor, c, tc);
        if (tilePin != null) {
            UiLabels.wrapped(panel, "Pin: " + MapNames.pin(tilePin), UiStyles.body(skin, UiTheme.FOCUS), w).padTop(4f).row();
        }

        panel.add(createDividerHorizontal(w)).padTop(8f).padBottom(10f).row();
        panel.add(UiLabels.of("IN THIS CHUNK", UiStyles.caption(skin, UiTheme.GOLD))).width(w).left().padBottom(4f).row();
        ChunkSummary summary = surface.summary(floor, c, false);
        if (summary != null) {
            if (summary.getLoot() > 0) UiLabels.wrapped(panel, UiNames.plural(summary.getLoot(), "item") + " left", UiStyles.body(skin), w).row();
            if (summary.hasUpLadder()) UiLabels.wrapped(panel, "Ladder up", UiStyles.body(skin), w).row();
            if (summary.hasDownLadder()) UiLabels.wrapped(panel, "Ladder down", UiStyles.body(skin), w).row();
            if (summary.hasReturnPortal()) UiLabels.wrapped(panel, "Return portal", UiStyles.body(skin), w).row();
            for (ChunkSummary.Grave g : summary.getGraves()) {
                UiLabels.wrapped(panel, "Grave: " + g.getName(), UiStyles.body(skin), w).row();
            }
        }

        panel.add(createDividerHorizontal(w)).padTop(8f).padBottom(10f).row();
        panel.add(UiLabels.of("OPEN LEADS", UiStyles.caption(skin, UiTheme.GOLD))).width(w).left().padBottom(4f).row();
        if (tiles != null) {
            List<String> leads = tiles.describeOpenLeads();
            if (leads.isEmpty()) {
                UiLabels.wrapped(panel, "No unexplored leads in this chunk", UiStyles.caption(skin, UiTheme.TEXT_DIM), w).row();
            } else {
                for (String lead : leads) {
                    UiLabels.wrapped(panel, "> " + lead, UiStyles.body(skin, UiTheme.GOLD), w).padBottom(4f).row();
                }
            }
        } else {
            UiLabels.wrapped(panel, "No open lead data", UiStyles.caption(skin, UiTheme.TEXT_DIM), w).row();
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    /** Click a chunk to select it, click it again to open it; drag to pan; the wheel zooms. */
    private final class SurfaceInput extends InputListener {
        private float downX;
        private float downY;
        private float lastX;
        private float lastY;
        private GridPoint2 downCursor;
        private boolean dragged;

        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            downX = x;
            downY = y;
            lastX = x;
            lastY = y;
            downCursor = surface.getCursor();
            dragged = false;
            return true;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
            if (surface.getZoom() == MapSurface.Zoom.CHUNK) return;
            if (Math.abs(downX - x) > 6 || Math.abs(downY - y) > 6) dragged = true;
            if (dragged) {
                if (surface.isDioramaEnabled() && surface.getDioramaRenderer() != null) {
                    float dx = x - lastX;
                    float dy = y - lastY;
                    surface.getDioramaRenderer().panScreen(-dx, -dy, surface.getWidth(), surface.getHeight());
                } else {
                    float cell = surface.getZoom().cell;
                    int dx = Math.round((downX - x) / cell);
                    int dy = Math.round((downY - y) / cell);
                    GridPoint2 next = new GridPoint2(downCursor).add(dx, dy);
                    if (!next.equals(surface.getCursor())) setCursor(next);
                }
            }
            lastX = x;
            lastY = y;
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            if (dragged) return;
            if (surface.getZoom() == MapSurface.Zoom.CHUNK) {
                GridPoint2 tc = surface.tileAt(x, y);
                if (tc != null) {
                    surface.setTileCursor(tc);
                    refresh();
                }
                return;
            }
            GridPoint2 c = surface.chunkAt(x, y);
            if (c == null) return;
            if (c.equals(surface.getCursor())) zoomIn();
            else setCursor(c);
        }

        @Override
        public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
            if (surface.isDioramaEnabled() && surface.getDioramaRenderer() != null) {
                float factor = (float) Math.exp(-amountY * 0.2f);
                surface.getDioramaRenderer().zoomAt(x, y, surface.getWidth(), surface.getHeight(), factor);
                IsometricDioramaRenderer.ZoomLevel zl = surface.getDioramaRenderer().getZoomLevel();
                MapSurface.Zoom mappedZoom = (zl == IsometricDioramaRenderer.ZoomLevel.WORLD)
                        ? MapSurface.Zoom.WORLD
                        : MapSurface.Zoom.REGION;
                if (mappedZoom != surface.getZoom()) {
                    surface.setZoom(mappedZoom);
                    refresh();
                }
                return true;
            }
            if (amountY < 0) zoomIn();
            else if (amountY > 0) zoomOut();
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
            if (keycode == Input.Keys.Q) {
                if (surface.isDioramaEnabled() && surface.getDioramaRenderer() != null) {
                    surface.getDioramaRenderer().rotate(-1);
                    return true;
                }
            }
            if (keycode == Input.Keys.E) {
                if (surface.isDioramaEnabled() && surface.getDioramaRenderer() != null) {
                    surface.getDioramaRenderer().rotate(1);
                    return true;
                }
            }
            if (keycode == Input.Keys.TAB) {
                nextMarker();
                return true;
            }
            switch (keycode) {
                case Input.Keys.ESCAPE:
                    if (surface.getZoom() == MapSurface.Zoom.WORLD) close();
                    else zoomOut();
                    return true;
                case Input.Keys.LEFT: moveCursor(-1, 0); return true;
                case Input.Keys.RIGHT: moveCursor(1, 0); return true;
                case Input.Keys.UP: moveCursor(0, 1); return true;
                case Input.Keys.DOWN: moveCursor(0, -1); return true;
                case Input.Keys.ENTER:
                case Input.Keys.SPACE:
                case Input.Keys.PLUS:
                case Input.Keys.EQUALS:
                case Input.Keys.NUMPAD_ADD:
                    zoomIn();
                    return true;
                case Input.Keys.MINUS:
                case Input.Keys.NUMPAD_SUBTRACT:
                    zoomOut();
                    return true;
                case Input.Keys.LEFT_BRACKET:
                case Input.Keys.PAGE_UP:
                    changeFloor(-1); // up, toward the surface
                    return true;
                case Input.Keys.RIGHT_BRACKET:
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
            if (toastTimer <= 0f && toastTable != null) {
                toastTable.setVisible(false);
            }
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
