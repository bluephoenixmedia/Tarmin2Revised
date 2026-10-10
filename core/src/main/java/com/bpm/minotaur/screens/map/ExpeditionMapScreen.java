package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
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

    private static final float PANEL_W = 160 * UiTheme.VU;
    private static final float RAIL_W = 46 * UiTheme.VU;

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
    private TextButton zoomInBtn;
    private TextButton zoomOutBtn;

    private Label sealsLabel;
    private Label doomLabel;
    private Label waypointLabel;

    // Left rail & overlay buttons
    private Table depthRail;
    private TextButton fitAreaBtn;
    private TextButton centeredOnYouBtn;

    // Side panel & footer
    private Table panel;
    private Label messageLabel;

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
        surface = new MapSurface(skin, this::loadSummary);
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
        root.pad(UiTheme.SAFE);

        // Header (row 0)
        root.add(buildHeader()).growX().colspan(3).padBottom(UiTheme.PAD_MD).row();

        // Left Depth Rail
        depthRail = new Table();
        depthRail.top().setBackground(skin.getPanelBg());
        depthRail.pad(UiTheme.PAD_SM);
        root.add(depthRail).width(RAIL_W).growY().padRight(UiTheme.PAD_MD);

        // Center Map Canvas with Overlay Buttons
        Table frame = new Table();
        frame.setBackground(skin.getDoubleBorderPanel());
        Stack mapStack = new Stack();
        mapStack.add(surface);

        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.top().left().pad(UiTheme.PAD_SM);

        fitAreaBtn = new TextButton(UiGlyphs.sanitize("[ FIT TO KNOWN AREA ]"), UiStyles.secondary(skin));
        fitAreaBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                fitToKnownArea();
            }
        });

        centeredOnYouBtn = new TextButton(UiGlyphs.sanitize("[ CENTRED ON YOU ]"), UiStyles.secondary(skin));
        centeredOnYouBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                recenter();
            }
        });

        overlay.add(fitAreaBtn).left();
        overlay.add(centeredOnYouBtn).left().padLeft(UiTheme.PAD_SM);
        mapStack.add(overlay);

        frame.add(mapStack).grow().pad(UiTheme.PAD_SM);
        root.add(frame).grow();

        // Right Sidebar Panel
        panel = new Table();
        panel.top().left();
        ScrollPane scroll = new ScrollPane(panel, UiStyles.scrollPane(skin));
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        Table side = new Table();
        side.setBackground(skin.getPanelBg());
        side.add(scroll).grow().pad(UiTheme.PAD_MD);
        root.add(side).width(PANEL_W).growY().padLeft(UiTheme.PAD_MD).row();

        // Footer
        Table footer = new Table();
        messageLabel = UiLabels.ellipsized("", UiStyles.caption(skin, UiTheme.GOLD));
        footer.add(messageLabel).left().growX().minWidth(0);
        footer.add(buildLegend()).right();
        root.add(footer).growX().colspan(3).padTop(UiTheme.PAD_MD);

        stage.addActor(root);
        surface.addListener(new SurfaceInput());
        stage.setScrollFocus(surface);

        InputMultiplexer input = new InputMultiplexer();
        input.addProcessor(stage);
        input.addProcessor(new Keys());
        Gdx.input.setInputProcessor(input);

        refresh();
        if (reopenChunk) zoomIn();
    }

    private Table buildHeader() {
        Table header = new Table();

        // Left breadcrumbs cluster
        Table breadcrumbRow = new Table();
        breadcrumbRow.add(UiLabels.of("EXPEDITION MAP", UiStyles.display(skin))).left().padRight(UiTheme.PAD_MD);

        floorBreadcrumbLabel = UiLabels.of("", UiStyles.body(skin, UiTheme.GOLD));
        breadcrumbRow.add(floorBreadcrumbLabel).left().padRight(UiTheme.PAD_SM);

        breadcrumbRow.add(UiLabels.of(">", UiStyles.caption(skin))).left().padRight(UiTheme.PAD_SM);

        worldZoomBtn = new TextButton("WORLD", UiStyles.secondary(skin));
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
        breadcrumbRow.add(worldZoomBtn).left().padRight(UiTheme.PAD_XS);

        regionZoomBtn = new TextButton("REGION", UiStyles.secondary(skin));
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
        breadcrumbRow.add(regionZoomBtn).left().padRight(UiTheme.PAD_XS);

        chunkZoomBtn = new TextButton("CHUNK", UiStyles.secondary(skin));
        chunkZoomBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (surface.getZoom() != MapSurface.Zoom.CHUNK) {
                    zoomIn();
                }
            }
        });
        breadcrumbRow.add(chunkZoomBtn).left().padRight(UiTheme.PAD_SM);

        zoomInBtn = new TextButton("[+]", UiStyles.secondary(skin));
        zoomInBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                zoomIn();
            }
        });
        breadcrumbRow.add(zoomInBtn).left().padRight(UiTheme.PAD_XS);

        zoomOutBtn = new TextButton("[-]", UiStyles.secondary(skin));
        zoomOutBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                zoomOut();
            }
        });
        breadcrumbRow.add(zoomOutBtn).left();

        header.add(breadcrumbRow).left().growX();

        // Right status badges
        Table status = new Table();
        sealsLabel = UiLabels.ellipsized("", UiStyles.body(skin));
        doomLabel = UiLabels.ellipsized("", UiStyles.body(skin, UiTheme.DANGER));
        waypointLabel = UiLabels.ellipsized("", UiStyles.body(skin, UiTheme.GOLD));

        status.add(sealsLabel).right().padLeft(UiTheme.PAD_MD);
        status.add(doomLabel).right().padLeft(UiTheme.PAD_MD);
        status.add(waypointLabel).right().padLeft(UiTheme.PAD_MD);
        header.add(status).right().bottom();

        return header;
    }

    private void rebuildDepthRail() {
        depthRail.clearChildren();
        float btnW = RAIL_W - 2 * UiTheme.PAD_SM;

        TextButton upBtn = new TextButton(UiGlyphs.sanitize("[^]"), UiStyles.secondary(skin));
        upBtn.setDisabled(surface.getFloor() <= 1);
        upBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                changeFloor(-1);
            }
        });
        depthRail.add(upBtn).width(btnW).padBottom(UiTheme.PAD_SM).row();

        int maxFloor = Math.max(surface.getFloor(), model.getMaxFloor());
        for (int f = 1; f <= maxFloor; f++) {
            String name;
            if (f == 1) {
                name = "^ SURFACE";
            } else {
                boolean visited = !model.visited(f).isEmpty();
                name = visited ? (f - 1) + " STRATUM" : "? STRATUM";
            }
            final int targetFloor = f;
            boolean isCurrent = (f == surface.getFloor());
            TextButton floorBtn = new TextButton(UiGlyphs.sanitize(name),
                    isCurrent ? UiStyles.primary(skin) : UiStyles.secondary(skin));
            floorBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    changeFloor(targetFloor - surface.getFloor());
                }
            });
            depthRail.add(floorBtn).width(btnW).padBottom(UiTheme.PAD_XS).row();
        }

        TextButton downBtn = new TextButton(UiGlyphs.sanitize("[v]"), UiStyles.secondary(skin));
        downBtn.setDisabled(surface.getFloor() >= model.getMaxFloor());
        downBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                changeFloor(1);
            }
        });
        depthRail.add(downBtn).width(btnW).padTop(UiTheme.PAD_SM).padBottom(UiTheme.PAD_MD).row();

        depthRail.add(UiLabels.of("DEPTH", UiStyles.caption(skin))).center();
    }

    private KeyHintLegend buildLegend() {
        return new KeyHintLegend(skin)
                .hint(KeyHintLegend.ARROWS_ALL, "Move")
                .hint("+ -", "Zoom")
                .hint("[ ]", "Floor")
                .hint("TAB", "Marker")
                .hint(keyName("MAP_WAYPOINT"), "Waypoint")
                .hint(keyName("MAP_SUGGEST"), "Suggested")
                .hint(keyName("MAP_PIN"), "Pin")
                .hint(keyName("MAP_RECENTER"), "Centre")
                .escapeHint("Back");
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
        say("Centred on you.");
    }

    private void fitToKnownArea() {
        Set<GridPoint2> visited = model.visited(surface.getFloor());
        if (visited.isEmpty()) {
            setCursor(new GridPoint2(0, 0));
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
        setCursor(new GridPoint2((minX + maxX) / 2, (minY + maxY) / 2));
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
        show(messageLabel, message);
    }

    private static void show(Label label, String text) {
        label.setText(UiGlyphs.sanitize(text));
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
        worldZoomBtn.setStyle(z == MapSurface.Zoom.WORLD ? UiStyles.primary(skin) : UiStyles.secondary(skin));
        regionZoomBtn.setStyle(z == MapSurface.Zoom.REGION ? UiStyles.primary(skin) : UiStyles.secondary(skin));
        chunkZoomBtn.setStyle(z == MapSurface.Zoom.CHUNK ? UiStyles.primary(skin) : UiStyles.secondary(skin));

        fitAreaBtn.setVisible(z == MapSurface.Zoom.WORLD);
        centeredOnYouBtn.setVisible(z == MapSurface.Zoom.CHUNK);

        int sealCount = network.getSealCount();
        int totalSeals = ShelterRoads.ROAD_COUNT - 1;
        String sealsStr = "SEALS: " + "*".repeat(Math.max(0, sealCount)) + "-".repeat(Math.max(0, totalSeals - sealCount));
        show(sealsLabel, sealsStr);

        int doomStage = DoomManager.getInstance().getDoomStage();
        String doomStr = "DOOM: [" + "#".repeat(Math.min(6, doomStage)) + ".".repeat(Math.max(0, 6 - doomStage)) + "]";
        show(doomLabel, doomStr);

        show(waypointLabel, waypointText());

        rebuildDepthRail();
        rebuildPanel(floor, c);
    }

    private String waypointText() {
        MapKnowledge.Spot wp = knowledge.getWaypoint();
        if (wp == null) return "[W] NONE";
        MapSurface.PlayerMark p = playerMark();
        if (wp.getFloor() != p.floor) return "[W] ON " + MapNames.floor(wp.getFloor()).toUpperCase(Locale.ROOT);
        return "[W] " + MapNames.chunks(MapModel.distance(p.chunk, wp.getChunk())).toUpperCase(Locale.ROOT);
    }

    private void rebuildPanel(int floor, GridPoint2 c) {
        panel.clearChildren();
        float w = PANEL_W - 2 * UiTheme.PAD_MD - UiTheme.SCROLL_W;

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

        String title;
        if (floor == 1) {
            title = known == MapModel.Knowledge.UNKNOWN && model.shelterAt(c) == null ? "Unknown Land"
                    : MapNames.biome(biomes == null ? null : biomes.getBiome(c));
        } else {
            title = MapNames.floor(floor);
        }
        panel.add(UiLabels.ellipsized(title.toUpperCase(Locale.ROOT), UiStyles.body(skin, UiTheme.GOLD))).width(w).left().row();
        panel.add(UiLabels.ellipsized("Chunk " + c.x + ", " + c.y, UiStyles.caption(skin))).width(w).left().padTop(UiTheme.PAD_XS).row();

        Table badgeRow = new Table();
        String kStr = known == MapModel.Knowledge.VISITED ? "[EXPLORED]" :
                known == MapModel.Knowledge.GLIMPSED ? "[GLIMPSED]" : "[UNEXPLORED]";
        badgeRow.add(UiLabels.of(kStr, known == MapModel.Knowledge.VISITED ? UiStyles.caption(skin, UiTheme.SUCCESS) : UiStyles.caption(skin))).left();

        MapSurface.PlayerMark p = playerMark();
        if (p.floor == floor && c.equals(p.chunk)) {
            badgeRow.add(UiLabels.of("[YOU ARE HERE]", UiStyles.caption(skin, UiTheme.FOCUS))).left().padLeft(UiTheme.PAD_SM);
        }
        panel.add(badgeRow).width(w).left().padTop(UiTheme.PAD_SM).padBottom(UiTheme.PAD_SM).row();

        if (known == MapModel.Knowledge.VISITED && worldManager != null) {
            int danger = worldManager.calculateEffectiveDifficulty(c, floor);
            panel.add(UiLabels.of("Danger Level: " + danger, UiStyles.body(skin, danger >= 4 ? UiTheme.DANGER : UiTheme.TEXT))).width(w).left().padBottom(UiTheme.PAD_SM).row();
        }

        ChunkSummary summary = known == MapModel.Knowledge.VISITED ? surface.summary(floor, c, true) : null;
        List<String> lines = new ArrayList<>();

        if (floor == 1) {
            MapModel.ShelterMark mark = model.shelterAt(c);
            if (mark != null) {
                ShelterRoads.Site site = biomes == null ? null : biomes.getShelterSite(c);
                String where = mark == MapModel.ShelterMark.HOME || site == null ? "" : ", on " + MapNames.road(site.getRoad());
                lines.add(MapNames.shelter(mark) + where);
                if (summary != null && !summary.getStations().isEmpty()) {
                    StringBuilder sb = new StringBuilder("Stations: ");
                    for (int i = 0; i < summary.getStations().size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(MapNames.station(summary.getStations().get(i)));
                    }
                    lines.add(sb.toString());
                }
            }
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

            int depth = model.deepestStratumBelow(c);
            if (depth > 0) lines.add("Explored down to stratum " + depth);
        }

        if (summary != null) {
            if (summary.hasUpLadder()) lines.add("A ladder up");
            if (summary.hasDownLadder()) lines.add("A ladder down");
            if (summary.hasReturnPortal()) lines.add("A return portal");
            if (summary.getLoot() > 0) lines.add(UiNames.plural(summary.getLoot(), "item") + " left here");
        }

        if (p.floor == floor && !c.equals(p.chunk)) {
            lines.add(MapNames.chunks(MapModel.distance(p.chunk, c)) + " from you");
        }
        if (floor == 1) {
            GridPoint2 lit = model.nearestLitShelter(c);
            if (!lit.equals(c)) lines.add("Nearest lit shelter " + MapNames.chunks(MapModel.distance(c, lit)) + " away");
        }

        MapKnowledge.Pin pin = knowledge.getPin(floor, c);
        if (pin != null) lines.add("Pin: " + MapNames.pin(pin));
        MapKnowledge.Pin topTilePin = knowledge.getHighestPriorityTilePin(floor, c);
        if (topTilePin != null) lines.add("Tile Pin: " + MapNames.pin(topTilePin));
        MapKnowledge.Spot wp = knowledge.getWaypoint();
        if (wp != null && wp.is(floor, c)) lines.add("Your waypoint");
        if (floor == 1 && c.equals(surface.getSuggestion())) lines.add("Suggested next step");

        for (String line : lines) {
            UiLabels.wrapped(panel, line, UiStyles.body(skin), w).padBottom(UiTheme.PAD_XS).row();
        }

        if (summary != null) {
            for (ChunkSummary.Grave g : summary.getGraves()) {
                String state = g.isDefeated() ? "Laid to rest" : g.isAwakened() ? "Risen" : "Sleeping";
                UiLabels.wrapped(panel, "Grave of " + g.getName(), UiStyles.body(skin, UiTheme.TEXT), w)
                        .padTop(UiTheme.PAD_SM).row();
                UiLabels.wrapped(panel, g.getEpitaph() + " (" + state + ")", UiStyles.caption(skin), w).row();
            }
        }

        panel.add(UiLabels.of("[ENTER / CLICK] Open Chunk", UiStyles.caption(skin, UiTheme.GOLD))).width(w).left().padTop(UiTheme.PAD_MD).row();
    }

    private void rebuildWorldPanel(int floor, GridPoint2 c, float w) {
        MapModel.MacroMarker markerAtCursor = null;
        for (MapModel.MacroMarker m : model.macroMarkers(floor)) {
            if (m.getChunk().equals(c)) {
                markerAtCursor = m;
                break;
            }
        }

        String headerTitle = markerAtCursor != null ? markerAtCursor.name : "WORLD OVERVIEW";
        panel.add(UiLabels.ellipsized(headerTitle.toUpperCase(Locale.ROOT), UiStyles.body(skin, UiTheme.GOLD))).width(w).left().row();
        panel.add(UiLabels.ellipsized("Chunk " + c.x + ", " + c.y, UiStyles.caption(skin))).width(w).left().padTop(UiTheme.PAD_XS).padBottom(UiTheme.PAD_SM).row();

        panel.add(UiLabels.of("From Home: " + MapModel.bearing(new GridPoint2(0, 0), c)
                + " (" + MapNames.chunks(MapModel.distance(new GridPoint2(0, 0), c)) + ")", UiStyles.body(skin))).width(w).left().padBottom(UiTheme.PAD_XS).row();

        MapSurface.PlayerMark p = playerMark();
        if (p.chunk != null) {
            panel.add(UiLabels.of("From You: " + MapModel.bearing(p.chunk, c)
                    + " (" + MapNames.chunks(MapModel.distance(p.chunk, c)) + ")", UiStyles.body(skin))).width(w).left().padBottom(UiTheme.PAD_MD).row();
        }

        if (floor == 1) {
            panel.add(UiLabels.of("GATE DESTINATIONS", UiStyles.caption(skin, UiTheme.GOLD))).width(w).left().padBottom(UiTheme.PAD_XS).row();
            for (BiomePortal portal : BiomePortal.values()) {
                GridPoint2 gate = model.gateChunk(portal);
                if (gate != null) {
                    String info = portal.getDisplayName() + ": " + MapModel.bearing(new GridPoint2(0, 0), gate)
                            + " " + MapNames.chunks(MapModel.distance(new GridPoint2(0, 0), gate));
                    UiLabels.wrapped(panel, info, UiStyles.body(skin), w).padBottom(UiTheme.PAD_XS).row();
                }
            }
        }

        panel.add(UiLabels.of("ALSO SIGHTED", UiStyles.caption(skin, UiTheme.GOLD))).width(w).left().padTop(UiTheme.PAD_SM).padBottom(UiTheme.PAD_XS).row();
        MapModel.ThreatMark tm = surface.getThreat();
        if (tm != null) {
            String tStr = tm.name + " (" + tm.status + ") - " + MapModel.bearing(c, tm.chunk) + " " + MapNames.chunks(MapModel.distance(c, tm.chunk));
            UiLabels.wrapped(panel, "Threat: " + tStr, UiStyles.body(skin, UiTheme.DANGER), w).padBottom(UiTheme.PAD_XS).row();
        } else {
            UiLabels.wrapped(panel, "No imminent threats nearby", UiStyles.caption(skin), w).padBottom(UiTheme.PAD_XS).row();
        }

        Set<GridPoint2> visited = model.visited(floor);
        UiLabels.wrapped(panel, "Explored: " + visited.size() + " chunks", UiStyles.body(skin), w).padBottom(UiTheme.PAD_XS).row();
        if (floor == 1) {
            UiLabels.wrapped(panel, "Lit shelters: " + network.getClaimed().size(), UiStyles.body(skin), w).padBottom(UiTheme.PAD_XS).row();
        }

        panel.add(UiLabels.of("[TAB] Cycle Markers  [+] Region View", UiStyles.caption(skin, UiTheme.GOLD))).width(w).left().padTop(UiTheme.PAD_MD).row();
    }

    private void rebuildChunkPanel(int floor, GridPoint2 c, float w) {
        panel.add(UiLabels.ellipsized(("CHUNK " + c.x + ", " + c.y).toUpperCase(Locale.ROOT), UiStyles.body(skin, UiTheme.GOLD))).width(w).left().row();
        panel.add(UiLabels.ellipsized(MapNames.floor(floor).toUpperCase(Locale.ROOT), UiStyles.caption(skin))).width(w).left().padTop(UiTheme.PAD_XS).padBottom(UiTheme.PAD_SM).row();

        GridPoint2 tc = surface.getTileCursor();
        panel.add(UiLabels.of("SELECTED TILE (" + tc.x + ", " + tc.y + ")", UiStyles.caption(skin, UiTheme.GOLD))).width(w).left().padBottom(UiTheme.PAD_XS).row();

        ChunkTiles tiles = surface.getTiles();
        if (tiles != null && tiles.isSeen(tc.x, tc.y)) {
            String type = tiles.isSolid(tc.x, tc.y) ? "Solid wall" : "Open floor";
            UiLabels.wrapped(panel, type, UiStyles.body(skin), w).row();
            String event = tiles.events.get(tc);
            if (event != null) {
                UiLabels.wrapped(panel, "Feature: " + UiGlyphs.sanitize(event), UiStyles.body(skin, UiTheme.GOLD), w).row();
            }
        } else {
            UiLabels.wrapped(panel, "Unexplored tile", UiStyles.caption(skin), w).row();
        }

        MapKnowledge.Pin tilePin = knowledge.getTilePin(floor, c, tc);
        if (tilePin != null) {
            UiLabels.wrapped(panel, "Pin: " + MapNames.pin(tilePin), UiStyles.body(skin, UiTheme.FOCUS), w).padTop(UiTheme.PAD_XS).row();
        }

        panel.add(UiLabels.of("IN THIS CHUNK", UiStyles.caption(skin, UiTheme.GOLD))).width(w).left().padTop(UiTheme.PAD_MD).padBottom(UiTheme.PAD_XS).row();
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

        panel.add(UiLabels.of("OPEN LEADS", UiStyles.caption(skin, UiTheme.GOLD))).width(w).left().padTop(UiTheme.PAD_MD).padBottom(UiTheme.PAD_XS).row();
        if (tiles != null) {
            List<String> leads = tiles.describeOpenLeads();
            if (leads.isEmpty()) {
                UiLabels.wrapped(panel, "No unexplored leads in this chunk", UiStyles.caption(skin), w).row();
            } else {
                for (String lead : leads) {
                    UiLabels.wrapped(panel, "> " + lead, UiStyles.body(skin, UiTheme.GOLD), w).padBottom(UiTheme.PAD_XS).row();
                }
            }
        } else {
            UiLabels.wrapped(panel, "No open lead data", UiStyles.caption(skin), w).row();
        }

        panel.add(UiLabels.of("[ARROWS] Move reticle  [P] Pin tile  [-] Exit", UiStyles.caption(skin, UiTheme.GOLD))).width(w).left().padTop(UiTheme.PAD_MD).row();
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    /** Click a chunk to select it, click it again to open it; drag to pan; the wheel zooms. */
    private final class SurfaceInput extends InputListener {
        private float downX;
        private float downY;
        private GridPoint2 downCursor;
        private boolean dragged;

        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            downX = x;
            downY = y;
            downCursor = surface.getCursor();
            dragged = false;
            return true;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
            if (surface.getZoom() == MapSurface.Zoom.CHUNK) return;
            float cell = surface.getZoom().cell;
            int dx = Math.round((downX - x) / cell);
            int dy = Math.round((downY - y) / cell);
            if (Math.abs(downX - x) > 6 || Math.abs(downY - y) > 6) dragged = true;
            if (dragged) {
                GridPoint2 next = new GridPoint2(downCursor).add(dx, dy);
                if (!next.equals(surface.getCursor())) setCursor(next);
            }
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
            if (keycode == s.getKey("MAP_RECENTER")) {
                recenter();
                return true;
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
