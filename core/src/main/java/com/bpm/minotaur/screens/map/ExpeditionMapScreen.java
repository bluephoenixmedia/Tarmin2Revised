package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.map.ChunkSummary;
import com.bpm.minotaur.gamedata.map.MapKnowledge;
import com.bpm.minotaur.gamedata.map.MapModel;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.shelter.ShelterNetwork;
import com.bpm.minotaur.generation.ShelterRoads;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.DoomManager;
import com.bpm.minotaur.managers.SettingsManager;
import com.bpm.minotaur.managers.WorldManager;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.screens.BaseScreen;
import com.bpm.minotaur.screens.GameScreen;
import com.bpm.minotaur.ui.KeyHintLegend;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The expedition map: where to go next, what is safe, and where the player has been.
 *
 * <p>A Scene2D layout -- header, side panel, key legend -- around one custom actor,
 * {@link MapSurface}, that draws the map itself. Everything shown comes from
 * {@link MapModel}. {@code GameMode.CLASSIC} keeps its old map. See
 * docs/DEsign/Requirements_ Expedition Map.md.
 */
public class ExpeditionMapScreen extends BaseScreen {

    private static final float PANEL_W = 160 * UiTheme.VU;

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
    private Label floorLabel;
    private Label sealsLabel;
    private Label doomLabel;
    private Label waypointLabel;
    private Label messageLabel;
    private Table panel;
    /** The map was closed in the chunk view; it reopens there once the panels exist. */
    private boolean reopenChunk;

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
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        skin = new HudSkin();
        surface = new MapSurface(skin, this::loadSummary);
        model = buildModel();
        surface.setModel(model, knowledge);
        surface.setPlayer(playerMark());
        restoreView();

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(skin.getScreenBackdrop());
        root.pad(UiTheme.SAFE);
        root.add(buildHeader()).growX().colspan(2).padBottom(UiTheme.PAD_MD).row();

        Table frame = new Table();
        frame.setBackground(skin.getDoubleBorderPanel());
        frame.add(surface).grow().pad(UiTheme.PAD_SM);
        root.add(frame).grow();

        panel = new Table();
        panel.top().left();
        ScrollPane scroll = new ScrollPane(panel, UiStyles.scrollPane(skin));
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        Table side = new Table();
        side.setBackground(skin.getPanelBg());
        side.add(scroll).grow().pad(UiTheme.PAD_MD);
        root.add(side).width(PANEL_W).growY().padLeft(UiTheme.PAD_MD).row();

        Table footer = new Table();
        messageLabel = UiLabels.ellipsized("", UiStyles.caption(skin, UiTheme.GOLD));
        footer.add(messageLabel).left().growX().minWidth(0);
        footer.add(buildLegend()).right();
        root.add(footer).growX().colspan(2).padTop(UiTheme.PAD_MD);

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
        Table titles = new Table();
        titles.add(UiLabels.of("EXPEDITION MAP", UiStyles.display(skin))).left().row();
        floorLabel = UiLabels.ellipsized("", UiStyles.label(skin));
        titles.add(floorLabel).left().width(PANEL_W);
        header.add(titles).left().growX();

        Table status = new Table();
        sealsLabel = UiLabels.ellipsized("", UiStyles.body(skin));
        doomLabel = UiLabels.ellipsized("", UiStyles.body(skin, UiTheme.DANGER));
        waypointLabel = UiLabels.ellipsized("", UiStyles.body(skin, UiTheme.GOLD));
        status.add(sealsLabel).width(PANEL_W * 0.5f).right().padLeft(UiTheme.PAD_XL);
        status.add(doomLabel).width(PANEL_W * 0.8f).right().padLeft(UiTheme.PAD_XL);
        status.add(waypointLabel).width(PANEL_W * 0.9f).right().padLeft(UiTheme.PAD_XL);
        header.add(status).right().bottom();
        return header;
    }

    private KeyHintLegend buildLegend() {
        return new KeyHintLegend(skin)
                .hint(KeyHintLegend.ARROWS_ALL, "Move")
                .hint("+ -", "Zoom")
                .hint("[ ]", "Floor")
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
        int maxFloor = worldManager == null ? 1 : Math.max(worldManager.getCurrentLevel(), worldManager.getMaxVisitedLevel());
        BiomeManager biomes = worldManager == null ? null : worldManager.getBiomeManager();
        return new MapModel(biomes, network, knowledge, floor -> {
            java.util.Set<GridPoint2> visited = worldManager == null ? new java.util.HashSet<>()
                    : new java.util.HashSet<>(worldManager.getVisitedChunkIds(floor));
            // The chunk the player stands in counts as entered even before its first save.
            if (worldManager != null && floor == worldManager.getCurrentLevel() && worldManager.getCurrentPlayerChunkId() != null) {
                visited.add(new GridPoint2(worldManager.getCurrentPlayerChunkId()));
            }
            return visited;
        }, maxFloor);
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
            // A floor seen for the first time opens beneath the player, else at home.
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
        GridPoint2 next = surface.getCursor().add(dx, dy);
        if (surface.getZoom() == MapSurface.Zoom.CHUNK) {
            ChunkTiles tiles = tilesFor(surface.getFloor(), next);
            if (tiles == null) {
                say("Nothing explored that way.");
                return;
            }
            surface.setTiles(tiles);
        }
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
        knowledge.setWaypoint(1, next);
        if (surface.getFloor() != 1) changeFloor(1 - surface.getFloor());
        say("Waypoint set on the suggested step.");
        setCursor(next);
    }

    private void cyclePin() {
        int floor = surface.getFloor();
        GridPoint2 c = surface.getCursor();
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
        if (surface.getZoom() == MapSurface.Zoom.CHUNK) surface.setTiles(tilesFor(p.floor, p.chunk));
        setCursor(p.chunk);
    }

    private void close() {
        rememberView();
        game.setScreen(gameScreen);
    }

    private void say(String message) {
        messageLabel.setText(com.bpm.minotaur.ui.UiGlyphs.sanitize(message));
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
        String zoomName = surface.getZoom() == MapSurface.Zoom.WORLD ? "World"
                : surface.getZoom() == MapSurface.Zoom.REGION ? "Region" : "Chunk";
        floorLabel.setText(MapNames.floor(floor).toUpperCase(java.util.Locale.ROOT) + "  -  " + zoomName.toUpperCase(java.util.Locale.ROOT));
        sealsLabel.setText("Seals " + network.getSealCount() + " of " + (ShelterRoads.ROAD_COUNT - 1));
        doomLabel.setText("Doom: " + MapNames.doom(DoomManager.getInstance().getDoomStage()));
        waypointLabel.setText(waypointText());
        rebuildPanel(floor, c);
    }

    private String waypointText() {
        MapKnowledge.Spot wp = knowledge.getWaypoint();
        if (wp == null) return "No waypoint";
        MapSurface.PlayerMark p = playerMark();
        if (wp.getFloor() != p.floor) return "Waypoint on " + MapNames.floor(wp.getFloor());
        return "Waypoint " + MapNames.chunks(MapModel.distance(p.chunk, wp.getChunk())) + " away";
    }

    private void rebuildPanel(int floor, GridPoint2 c) {
        panel.clearChildren();
        float w = PANEL_W - 2 * UiTheme.PAD_MD - UiTheme.SCROLL_W;
        MapModel.Knowledge known = model.knowledgeOf(floor, c);
        BiomeManager biomes = model.getBiomes();

        List<String> lines = new ArrayList<>();
        String title;
        if (floor == 1) {
            title = known == MapModel.Knowledge.UNKNOWN && model.shelterAt(c) == null ? "Unknown land"
                    : MapNames.biome(biomes == null ? null : biomes.getBiome(c));
        } else {
            title = MapNames.floor(floor);
        }
        panel.add(UiLabels.ellipsized(title, UiStyles.body(skin, UiTheme.GOLD))).width(w).left().row();
        panel.add(UiLabels.ellipsized("Chunk " + c.x + ", " + c.y + "  -  " + MapNames.knowledge(known),
                UiStyles.caption(skin))).width(w).left().padTop(UiTheme.PAD_XS).padBottom(UiTheme.PAD_MD).row();

        ChunkSummary summary = known == MapModel.Knowledge.VISITED ? surface.summary(floor, c, true) : null;
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
                lines.add("Seal site of " + MapNames.road(sealRoad) + (network.hasSeal(sealRoad) ? ", seal won" : ""));
            }
        }
        if (known == MapModel.Knowledge.VISITED && worldManager != null) {
            lines.add("Danger level " + worldManager.calculateEffectiveDifficulty(c, floor));
        }
        if (floor == 1) {
            int depth = model.deepestStratumBelow(c);
            if (depth > 0) lines.add("Explored down to stratum " + depth);
        }
        if (summary != null) {
            if (summary.hasUpLadder()) lines.add("A ladder up");
            if (summary.hasDownLadder()) lines.add("A ladder down");
            if (summary.hasReturnPortal()) lines.add("A return portal");
            if (summary.getLoot() > 0) lines.add(com.bpm.minotaur.ui.UiNames.plural(summary.getLoot(), "item") + " left here");
        }
        MapSurface.PlayerMark p = playerMark();
        if (p.floor == floor) {
            int d = MapModel.distance(p.chunk, c);
            lines.add(d == 0 ? "You are here" : MapNames.chunks(d) + " from you");
        }
        if (floor == 1) {
            GridPoint2 lit = model.nearestLitShelter(c);
            if (!lit.equals(c)) lines.add("Nearest lit shelter " + MapNames.chunks(MapModel.distance(c, lit)) + " away");
        }
        MapKnowledge.Pin pin = knowledge.getPin(floor, c);
        if (pin != null) lines.add("Pin: " + MapNames.pin(pin));
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
            if (keycode == s.getKey("MAP_PIN")) {
                cyclePin();
                return true;
            }
            if (keycode == s.getKey("MAP_RECENTER")) {
                recenter();
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
                case Input.Keys.PAGE_DOWN:
                    changeFloor(-1);
                    return true;
                case Input.Keys.RIGHT_BRACKET:
                case Input.Keys.PAGE_UP:
                    changeFloor(1);
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
