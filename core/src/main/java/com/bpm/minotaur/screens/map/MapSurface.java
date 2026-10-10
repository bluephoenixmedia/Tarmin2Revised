package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Ladder;
import com.bpm.minotaur.gamedata.map.ChunkSummary;
import com.bpm.minotaur.gamedata.map.MapKnowledge;
import com.bpm.minotaur.gamedata.map.MapModel;
import com.bpm.minotaur.gamedata.progression.BiomePortal;
import com.bpm.minotaur.gamedata.shelter.BeaconPalette;
import com.bpm.minotaur.gamedata.shelter.ShelterNetwork;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.ShelterRoads;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.ui.UiTheme;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * The expedition map's drawing surface: dark vellum, biome washes, roads, icons.
 *
 * <p>The one part of the map screen that is custom drawing rather than a Scene2D layout,
 * because a pannable tiled canvas is not a table. It draws through the stage's batch with a
 * white pixel and tinted white icons, and reads everything it shows from {@link MapModel}
 * and {@link MapKnowledge}. The cursor chunk is always at the centre: moving the cursor is
 * panning. See docs/DEsign/Requirements_ Expedition Map.md, section 5.
 */
final class MapSurface extends Actor implements Disposable {

    /** The three zoom steps. Each cell size is an integer, so nothing draws at a fractional scale. */
    enum Zoom {
        WORLD(12, 24),
        REGION(96, 48),
        CHUNK(0, 0);

        final int cell;
        final int icon;

        Zoom(int cell, int icon) {
            this.cell = cell;
            this.icon = icon;
        }

        Zoom in() { return this == WORLD ? REGION : CHUNK; }
        Zoom out() { return this == CHUNK ? REGION : WORLD; }
    }

    /** Where the player stands, for the marker. */
    static final class PlayerMark {
        int floor;
        GridPoint2 chunk;
        float tileX;
        float tileY;
        int chunkWidth = 1;
        int chunkHeight = 1;
        Direction facing = Direction.NORTH;
    }

    /** A war front to draw: its chunks, washed in the attacker's colour, ringed in the defender's. */
    static final class FrontMark {
        final List<GridPoint2> chunks;
        final Color attacker;
        final Color defender;

        FrontMark(List<GridPoint2> chunks, Color attacker, Color defender) {
            this.chunks = chunks;
            this.attacker = attacker;
            this.defender = defender;
        }
    }

    /**
     * A mark of the war on the map (Living War W28): a fresh battlefield, a column on the march
     * toward where it is bound, or a house's war camp.
     */
    static final class WarSign {
        enum Kind { BATTLEFIELD, COLUMN, CAMP }

        final Kind kind;
        final GridPoint2 at;
        /** Where a column is bound; null for the rest. */
        final GridPoint2 toward;
        final Color color;

        WarSign(Kind kind, GridPoint2 at, GridPoint2 toward, Color color) {
            this.kind = kind;
            this.at = at;
            this.toward = toward;
            this.color = color;
        }
    }

    private static final String[] ICON_NAMES = {
            "home", "shelter", "castle", "seal", "seal_won", "ladder_up", "ladder_down", "player",
            "waypoint", "grave", "portal",
            "pin_danger", "pin_loot", "pin_return", "pin_trader", "pin_locked", "pin_unknown"
    };
    /** Summaries read from disk per frame at most, so panning into new ground never hitches. */
    private static final int SUMMARY_LOADS_PER_FRAME = 6;

    private final HudSkin skin;
    private final TextureRegion white;
    private final Texture vellum;
    private final Map<String, Texture> icons = new HashMap<>();
    private final Map<MapKnowledge.Pin, String> pinIcons = new EnumMap<>(MapKnowledge.Pin.class);
    private final BiFunction<Integer, GridPoint2, ChunkSummary> summaryLoader;
    private final Map<String, ChunkSummary> summaries = new HashMap<>();
    private final Map<GridPoint2, Biome> biomeCache = new HashMap<>();
    private final Color tint = new Color();

    private MapModel model;
    private MapKnowledge knowledge;
    private List<MapModel.RoadSegment> roads;
    private List<MapModel.KnownShelter> shelters;
    private Set<GridPoint2> glimpsed;
    private GridPoint2 suggestion;
    private List<FrontMark> fronts = java.util.Collections.emptyList();
    private List<WarSign> warSigns = java.util.Collections.emptyList();

    private Zoom zoom = Zoom.REGION;
    private int floor = 1;
    private final GridPoint2 cursor = new GridPoint2();
    private final GridPoint2 tileCursor = new GridPoint2(0, 0);
    private MapModel.ThreatMark threat;
    private boolean showSuggestedRoute = true;
    private ChunkTiles tiles;
    private PlayerMark player;
    private float time;
    private int loadsThisFrame;

    MapSurface(HudSkin skin, BiFunction<Integer, GridPoint2, ChunkSummary> summaryLoader) {
        this.skin = skin;
        this.white = new TextureRegion(skin.getWhitePixel());
        this.summaryLoader = summaryLoader;
        this.vellum = new Texture(Gdx.files.internal("images/map/dark_vellum.png"));
        for (String name : ICON_NAMES) {
            Texture t = new Texture(Gdx.files.internal("images/map/icons/" + name + ".png"));
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            icons.put(name, t);
        }
        pinIcons.put(MapKnowledge.Pin.DANGER, "pin_danger");
        pinIcons.put(MapKnowledge.Pin.LOOT, "pin_loot");
        pinIcons.put(MapKnowledge.Pin.RETURN, "pin_return");
        pinIcons.put(MapKnowledge.Pin.TRADER, "pin_trader");
        pinIcons.put(MapKnowledge.Pin.LOCKED, "pin_locked");
        pinIcons.put(MapKnowledge.Pin.UNKNOWN, "pin_unknown");
    }

    /** The wars being fought on the surface right now (Houses of the Maze plan T2.2). */
    void setWarSigns(List<WarSign> signs) {
        this.warSigns = signs == null ? java.util.Collections.emptyList() : signs;
    }

    void setFronts(List<FrontMark> fronts) {
        this.fronts = fronts == null ? java.util.Collections.emptyList() : fronts;
    }

    /** Points the surface at a fresh read of the world. Call when shelters, seals or knowledge change. */
    void setModel(MapModel model, MapKnowledge knowledge) {
        this.model = model;
        this.knowledge = knowledge;
        this.roads = model.knownRoadSegments();
        this.shelters = model.knownShelters();
        this.glimpsed = knowledge.getGlimpsed();
        this.suggestion = model.suggestion();
        this.summaries.clear();
    }

    void setPlayer(PlayerMark player) { this.player = player; }
    void setTiles(ChunkTiles tiles) { this.tiles = tiles; }
    ChunkTiles getTiles() { return tiles; }
    void setZoom(Zoom zoom) { this.zoom = zoom; }
    void setFloor(int floor) { this.floor = floor; }
    void setCursor(GridPoint2 c) { cursor.set(c); }
    void setTileCursor(GridPoint2 tc) { if (tc != null) tileCursor.set(tc); }
    void moveTileCursor(int dx, int dy) {
        if (tiles != null && tiles.width > 0 && tiles.height > 0) {
            tileCursor.x = MathUtils.clamp(tileCursor.x + dx, 0, tiles.width - 1);
            tileCursor.y = MathUtils.clamp(tileCursor.y + dy, 0, tiles.height - 1);
        }
    }
    void setThreat(MapModel.ThreatMark threat) { this.threat = threat; }
    MapModel.ThreatMark getThreat() { return threat; }
    void setShowSuggestedRoute(boolean show) { this.showSuggestedRoute = show; }
    boolean isShowSuggestedRoute() { return showSuggestedRoute; }

    Zoom getZoom() { return zoom; }
    int getFloor() { return floor; }
    GridPoint2 getCursor() { return new GridPoint2(cursor); }
    GridPoint2 getTileCursor() { return new GridPoint2(tileCursor); }
    GridPoint2 getSuggestion() { return suggestion == null ? null : new GridPoint2(suggestion); }

    /** The chunk under a point in this actor's coordinates, or null in the chunk view. */
    GridPoint2 chunkAt(float localX, float localY) {
        if (zoom == Zoom.CHUNK) return null;
        int cell = zoom.cell;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        int dx = MathUtils.floor((localX - cx) / cell + 0.5f);
        int dy = MathUtils.floor((localY - cy) / cell + 0.5f);
        return new GridPoint2(cursor.x + dx, cursor.y + dy);
    }

    /** The tile under a point in this actor's coordinates in the chunk view, or null if outside. */
    GridPoint2 tileAt(float localX, float localY) {
        if (zoom != Zoom.CHUNK || tiles == null || tiles.width <= 0 || tiles.height <= 0) return null;
        int tile = (int) Math.floor(Math.min((getWidth() - 40) / tiles.width, (getHeight() - 40) / tiles.height));
        if (tile <= 0) return null;
        float ox = Math.round((getWidth() - tiles.width * tile) / 2f);
        float oy = Math.round((getHeight() - tiles.height * tile) / 2f);
        int tx = (int) Math.floor((localX - ox) / tile);
        int ty = (int) Math.floor((localY - oy) / tile);
        if (tx >= 0 && tx < tiles.width && ty >= 0 && ty < tiles.height) {
            return new GridPoint2(tx, ty);
        }
        return null;
    }

    /** The summary of a saved chunk, loaded lazily and a few per frame; null until loaded. */
    ChunkSummary summary(int onFloor, GridPoint2 c, boolean loadNow) {
        String key = onFloor + ":" + c.x + ":" + c.y;
        ChunkSummary s = summaries.get(key);
        if (s == null && (loadNow || loadsThisFrame < SUMMARY_LOADS_PER_FRAME)) {
            loadsThisFrame++;
            s = summaryLoader.apply(onFloor, c);
            summaries.put(key, s);
        }
        return s;
    }

    private Biome biomeOf(GridPoint2 c) {
        Biome b = biomeCache.get(c);
        if (b == null && model.getBiomes() != null) {
            b = model.getBiomes().getBiome(c);
            biomeCache.put(new GridPoint2(c), b);
        }
        return b;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        time += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (model == null) return;
        loadsThisFrame = 0;
        batch.setColor(Color.WHITE);
        batch.draw(vellum, getX(), getY(), getWidth(), getHeight());
        if (!clipBegin(getX(), getY(), getWidth(), getHeight())) return;
        if (zoom == Zoom.CHUNK) {
            drawChunk(batch);
        } else {
            drawChunks(batch);
            if (floor == 1) {
                drawAreas(batch);
                drawFronts(batch);
                drawWarSigns(batch);
                drawRoads(batch);
                drawSuggestedRoute(batch);
                drawSurfaceIcons(batch);
                if (zoom == Zoom.REGION) drawGateRays(batch);
            }
            drawMarkers(batch);
            drawThreat(batch);
            drawCursor(batch);
            if (zoom == Zoom.REGION) drawCompass(batch);
        }
        drawScaleBar(batch);
        batch.flush();
        clipEnd();
        batch.setColor(Color.WHITE);
    }

    // ------------------------------------------------------------------
    // World and region views
    // ------------------------------------------------------------------

    private float cellX(int chunkX) {
        return Math.round(getX() + getWidth() / 2f + (chunkX - cursor.x - 0.5f) * zoom.cell);
    }

    private float cellY(int chunkY) {
        return Math.round(getY() + getHeight() / 2f + (chunkY - cursor.y - 0.5f) * zoom.cell);
    }

    private boolean inView(GridPoint2 c) {
        int rx = (int) Math.ceil(getWidth() / 2f / zoom.cell) + 1;
        int ry = (int) Math.ceil(getHeight() / 2f / zoom.cell) + 1;
        return Math.abs(c.x - cursor.x) <= rx && Math.abs(c.y - cursor.y) <= ry;
    }

    private void drawChunks(Batch batch) {
        int cell = zoom.cell;
        int gap = zoom == Zoom.REGION ? 2 : 0;
        Set<GridPoint2> visited = model.visited(floor);
        if (floor == 1) {
            for (GridPoint2 c : glimpsed) {
                if (visited.contains(c) || !inView(c)) continue;
                fill(batch, washOf(biomeOf(c)), UiTheme.MAP_GLIMPSED_ALPHA, cellX(c.x) + gap, cellY(c.y) + gap,
                        cell - 2 * gap, cell - 2 * gap);
            }
        } else {
            // What lies overhead, faintly, so a stratum is never a void with no bearings.
            for (GridPoint2 c : model.visited(1)) {
                if (!inView(c)) continue;
                fill(batch, washOf(biomeOf(c)), UiTheme.MAP_UNDERLAY_ALPHA, cellX(c.x), cellY(c.y), cell, cell);
            }
        }
        for (GridPoint2 c : visited) {
            if (!inView(c)) continue;
            Color wash = floor == 1 ? washOf(biomeOf(c)) : UiTheme.MAP_UNDERGROUND;
            fill(batch, wash, UiTheme.MAP_WASH_ALPHA, cellX(c.x) + gap, cellY(c.y) + gap, cell - 2 * gap, cell - 2 * gap);
        }
    }

    /** Fronts are news, not discovery: every one is shown, explored ground or not. */
    private void drawFronts(Batch batch) {
        int cell = zoom.cell;
        float rim = zoom == Zoom.REGION ? UiTheme.FRONT_RIM : 1f;
        for (FrontMark f : fronts) {
            for (GridPoint2 c : f.chunks) {
                if (!inView(c)) continue;
                fill(batch, f.attacker, UiTheme.MAP_FRONT_ALPHA, cellX(c.x), cellY(c.y), cell, cell);
                dashed(batch, f.defender, 1f, cellX(c.x), cellY(c.y), cell, cell, rim);
            }
        }
    }

    /** Battlefields, columns and camps: news, like the fronts, so shown on any ground. */
    private void drawWarSigns(Batch batch) {
        int size = zoom.icon;
        float half = zoom.cell / 2f;
        float thickness = zoom == Zoom.REGION ? 4f : 2f;
        for (WarSign s : warSigns) {
            if (!inView(s.at)) continue;
            float x = cellX(s.at.x) + half;
            float y = cellY(s.at.y) + half;
            switch (s.kind) {
                case BATTLEFIELD:
                    icon(batch, "grave", s.color, 1f, x, y, size, 0f);
                    break;
                case CAMP:
                    icon(batch, "pin_danger", s.color, 1f, x, y, size, 0f);
                    break;
                default:
                    if (s.toward != null) {
                        line(batch, s.color, 0.9f, x, y, cellX(s.toward.x) + half, cellY(s.toward.y) + half, thickness);
                    }
                    icon(batch, "waypoint", s.color, 1f, x, y, size, 0f);
                    break;
            }
        }
    }

    private void drawRoads(Batch batch) {
        float thickness = zoom == Zoom.REGION ? 5f : 2f;
        float half = zoom.cell / 2f;
        for (MapModel.RoadSegment s : roads) {
            Color c = BeaconPalette.roadColor(s.getRoad());
            float alpha = s.isSpent() ? BeaconPalette.SPENT : 0.9f;
            GridPoint2 a = s.getFrom();
            GridPoint2 b = s.getTo();
            line(batch, c, alpha, cellX(a.x) + half, cellY(a.y) + half, cellX(b.x) + half, cellY(b.y) + half, thickness);
        }
    }

    private void drawSurfaceIcons(Batch batch) {
        int size = zoom.icon;
        float half = zoom.cell / 2f;
        for (MapModel.KnownShelter s : shelters) {
            GridPoint2 c = s.getChunk();
            if (!inView(c)) continue;
            float x = cellX(c.x) + half;
            float y = cellY(c.y) + half;
            switch (s.getMark()) {
                case HOME:
                    icon(batch, "home", UiTheme.GOLD, 1f, x, y, size, 0f);
                    break;
                case LIT:
                    icon(batch, "shelter", BeaconPalette.roadColor(s.getRoad()), 1f, x, y, size, 0f);
                    break;
                case COLD:
                    icon(batch, "shelter", UiTheme.TEXT_DIM, 1f, x, y, size, 0f);
                    break;
                case RUMOURED:
                    // Dashed and unlit: seen on the horizon, not yet reached.
                    icon(batch, "shelter", BeaconPalette.roadColor(s.getRoad()), UiTheme.MAP_GHOST_ALPHA, x, y, size, 0f);
                    if (zoom == Zoom.REGION) {
                        dashed(batch, BeaconPalette.roadColor(s.getRoad()), UiTheme.MAP_GHOST_ALPHA,
                                cellX(c.x) + 6, cellY(c.y) + 6, zoom.cell - 12, zoom.cell - 12, 2f);
                    }
                    break;
            }
        }
        ShelterRoads roadLayout = model.getBiomes() == null ? null : model.getBiomes().getRoads();
        if (roadLayout != null) {
            for (ShelterRoads.Road road : roadLayout.getRoads()) {
                if (road.getKind() != ShelterRoads.Kind.SEAL || !model.isSealSiteKnown(road.getIndex())) continue;
                GridPoint2 end = road.getEnd();
                if (!inView(end)) continue;
                boolean won = ShelterNetwork.getInstance().hasSeal(road.getIndex());
                icon(batch, won ? "seal_won" : "seal", BeaconPalette.roadColor(road.getIndex()), won ? BeaconPalette.SPENT : 1f,
                        cellX(end.x) + half, cellY(end.y) + half, size, 0f);
            }
        }
        drawCastle(batch);
        if (zoom == Zoom.REGION) {
            for (GridPoint2 c : model.visited(1)) {
                if (!inView(c)) continue;
                int depth = model.deepestStratumBelow(c);
                if (depth > 0) badge(batch, c, "v" + depth);
            }
        }
    }

    private void drawCastle(Batch batch) {
        GridPoint2 site = model.getBiomes() == null ? null : model.getBiomes().getCastleSite();
        if (site == null) return;
        float half = zoom.cell / 2f;
        float x = cellX(site.x) + half;
        float y = cellY(site.y) + half;
        boolean known = model.isCastleKnown();
        float margin = zoom.icon;
        boolean onScreen = x >= getX() + margin && x <= getRight() - margin && y >= getY() + margin && y <= getTop() - margin;
        if (known && onScreen) {
            icon(batch, "castle", BeaconPalette.CASTLE, 1f, x, y, zoom.icon, 0f);
            return;
        }
        // Its direction is always known; its place only once reached. An arrow on the edge says which way.
        float cx = getX() + getWidth() / 2f;
        float cy = getY() + getHeight() / 2f;
        float dx = site.x - cursor.x;
        float dy = site.y - cursor.y;
        if (dx == 0 && dy == 0) return;
        float limitX = getWidth() / 2f - margin;
        float limitY = getHeight() / 2f - margin;
        float scale = Math.min(limitX / Math.max(1e-3f, Math.abs(dx)), limitY / Math.max(1e-3f, Math.abs(dy)));
        float ax = cx + dx * scale;
        float ay = cy + dy * scale;
        float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees - 90f;
        icon(batch, "player", BeaconPalette.CASTLE, 0.9f, ax, ay, zoom.icon, angle);
        icon(batch, "castle", BeaconPalette.CASTLE, 0.9f, ax - MathUtils.cosDeg(angle + 90f) * zoom.icon,
                ay - MathUtils.sinDeg(angle + 90f) * zoom.icon, zoom.icon * 0.75f, 0f);
    }

    private void drawMarkers(Batch batch) {
        float half = zoom.cell / 2f;
        if (zoom == Zoom.REGION) {
            int small = zoom.icon / 2;
            for (Map.Entry<GridPoint2, MapKnowledge.Pin> pin : knowledge.getPins(floor).entrySet()) {
                GridPoint2 c = pin.getKey();
                if (!inView(c)) continue;
                icon(batch, pinIcons.get(pin.getValue()), UiTheme.FOCUS, 1f, cellX(c.x) + small / 2f + 4,
                        cellY(c.y) + zoom.cell - small / 2f - 4, small, 0f);
            }
            for (GridPoint2 c : model.visited(floor)) {
                if (!inView(c)) continue;
                ChunkSummary s = summary(floor, c, false);
                if (s == null) continue;
                float x = cellX(c.x);
                float y = cellY(c.y);
                if (!s.getGraves().isEmpty()) icon(batch, "grave", UiTheme.TEXT, 0.9f, x + 18, y + 18, small, 0f);
                if (s.hasReturnPortal()) icon(batch, "portal", BiomePortal.RETURN_PORTAL_TINT, 1f, x + 44, y + 18, small, 0f);
                if (floor > 1) {
                    if (s.hasUpLadder()) icon(batch, "ladder_up", UiTheme.GOLD, 1f, x + zoom.cell - 18, y + zoom.cell - 18, small, 0f);
                    if (s.hasDownLadder()) icon(batch, "ladder_down", UiTheme.DANGER, 1f, x + zoom.cell - 18, y + 18, small, 0f);
                }
                // Chunk corner pin badge for tile pins inside this chunk
                MapKnowledge.Pin topTilePin = knowledge.getHighestPriorityTilePin(floor, c);
                if (topTilePin != null) {
                    icon(batch, pinIcons.get(topTilePin), UiTheme.FOCUS, 1f,
                            x + zoom.cell - small / 2f - 4, y + zoom.cell - small / 2f - 4, small, 0f);
                }
            }
        }
        if (floor == 1 && suggestion != null && inView(suggestion)) {
            MapKnowledge.Spot wp = knowledge.getWaypoint();
            if (wp == null || !wp.is(1, suggestion)) {
                float pulse = 0.7f + 0.3f * MathUtils.sin(time * 3f);
                icon(batch, "waypoint", UiTheme.TEXT, UiTheme.MAP_GHOST_ALPHA * pulse,
                        cellX(suggestion.x) + half, cellY(suggestion.y) + half + zoom.icon * 0.4f, zoom.icon, 0f);
            }
        }
        MapKnowledge.Spot wp = knowledge.getWaypoint();
        if (wp != null && wp.getFloor() == floor && inView(wp.getChunk())) {
            GridPoint2 c = wp.getChunk();
            icon(batch, "waypoint", UiTheme.GOLD, 1f, cellX(c.x) + half, cellY(c.y) + half + zoom.icon * 0.4f, zoom.icon, 0f);
        }
        if (player != null && player.floor == floor && player.chunk != null && inView(player.chunk)) {
            float fx = MathUtils.clamp(player.tileX / Math.max(1, player.chunkWidth), 0f, 1f);
            float fy = MathUtils.clamp(player.tileY / Math.max(1, player.chunkHeight), 0f, 1f);
            float x = cellX(player.chunk.x) + fx * zoom.cell;
            float y = cellY(player.chunk.y) + fy * zoom.cell;
            if (zoom == Zoom.WORLD) {
                x = cellX(player.chunk.x) + half;
                y = cellY(player.chunk.y) + half;
            }
            icon(batch, "player", UiTheme.MAP_PLAYER, 1f, x, y, zoom.icon, facingAngle(player.facing));
        }
    }

    private void drawCornerReticle(Batch batch, float x, float y, float w, float h, float arm, float t, Color color, float alpha) {
        fill(batch, color, alpha, x, y + h - t, arm, t);
        fill(batch, color, alpha, x, y + h - arm, t, arm);
        fill(batch, color, alpha, x + w - arm, y + h - t, arm, t);
        fill(batch, color, alpha, x + w - t, y + h - arm, t, arm);
        fill(batch, color, alpha, x, y, arm, t);
        fill(batch, color, alpha, x, y, t, arm);
        fill(batch, color, alpha, x + w - arm, y, arm, t);
        fill(batch, color, alpha, x + w - t, y, t, arm);
    }

    private void drawCursor(Batch batch) {
        float pulse = 0.7f + 0.3f * MathUtils.sin(time * 5f);
        float t = zoom == Zoom.REGION ? 3f : 2f;
        int pad = zoom == Zoom.WORLD ? -2 : 0;
        float arm = zoom == Zoom.REGION ? 18f : 5f;
        drawCornerReticle(batch, cellX(cursor.x) + pad, cellY(cursor.y) + pad,
                zoom.cell - 2 * pad, zoom.cell - 2 * pad, arm, t, UiTheme.FOCUS, pulse);
    }

    private void drawScaleBar(Batch batch) {
        BitmapFont font = skin.getFontSmall();
        font.setColor(UiTheme.TEXT_DIM);
        float cx = getX() + getWidth() / 2f;
        float y = getY() + 18f;
        float barWidth;
        String text;
        if (zoom == Zoom.WORLD) {
            barWidth = 5 * zoom.cell;
            text = "5 CHUNKS";
        } else if (zoom == Zoom.REGION) {
            barWidth = zoom.cell;
            text = "1 CHUNK";
        } else {
            if (tiles == null || tiles.width <= 0) return;
            int tile = (int) Math.floor(Math.min((getWidth() - 40) / tiles.width, (getHeight() - 40) / tiles.height));
            barWidth = 5 * tile;
            text = "5 TILES";
        }
        float x1 = cx - barWidth / 2f;
        float x2 = cx + barWidth / 2f;
        line(batch, UiTheme.TEXT_DIM, 0.75f, x1, y + 8, x2, y + 8, 2f);
        line(batch, UiTheme.TEXT_DIM, 0.75f, x1, y + 4, x1, y + 12, 2f);
        line(batch, UiTheme.TEXT_DIM, 0.75f, x2, y + 4, x2, y + 12, 2f);
        font.draw(batch, text, cx - 60f, y + 4, 120f, Align.center, false);
    }

    private void drawCompass(Batch batch) {
        float cx = getX() + getWidth() - 40f;
        float cy = getY() + getHeight() - 40f;
        icon(batch, "player", UiTheme.GOLD, 0.9f, cx, cy, 20f, 0f);
        BitmapFont font = skin.getFontSmall();
        font.setColor(UiTheme.GOLD);
        font.draw(batch, "N", cx - 15f, cy - 14f, 30f, Align.center, false);
    }

    private void drawGateRays(Batch batch) {
        if (model == null) return;
        float half = zoom.cell / 2f;
        float hx = cellX(0) + half;
        float hy = cellY(0) + half;
        BitmapFont font = skin.getFontSmall();

        for (com.bpm.minotaur.gamedata.progression.BiomePortal p : com.bpm.minotaur.gamedata.progression.BiomePortal.values()) {
            GridPoint2 target = model.gateChunk(p);
            if (target == null) continue;
            Color color = p.getTint();
            float tx = cellX(target.x) + half;
            float ty = cellY(target.y) + half;
            dashedLine(batch, color, 0.45f, hx, hy, tx, ty, 2f);

            float margin = 80f;
            float minX = getX() + margin, maxX = getRight() - margin;
            float minY = getY() + margin, maxY = getTop() - margin;
            float lx = tx, ly = ty;
            if (lx < minX || lx > maxX || ly < minY || ly > maxY) {
                float dx = tx - hx;
                float dy = ty - hy;
                float scale = Math.min((dx > 0 ? maxX - hx : minX - hx) / (dx == 0 ? 1e-4f : dx),
                                       (dy > 0 ? maxY - hy : minY - hy) / (dy == 0 ? 1e-4f : dy));
                scale = Math.abs(scale);
                lx = hx + dx * scale;
                ly = hy + dy * scale;
            }
            String bearing = MapModel.bearing(new GridPoint2(0, 0), target);
            String label = p.getDisplayName() + " " + (bearing.length() > 0 ? bearing.substring(0, 1) : "^");
            font.setColor(color);
            fill(batch, color, 0.9f, lx - 4, ly - 4, 8, 8);
            font.draw(batch, label, lx - 70f, ly + 18f, 140f, Align.center, false);
        }
    }

    private void drawThreat(Batch batch) {
        if (threat == null || model == null) return;
        float half = zoom.cell / 2f;
        float tx = cellX(threat.chunk.x) + half;
        float ty = cellY(threat.chunk.y) + half;
        BitmapFont font = skin.getFontSmall();
        Color c = UiTheme.DANGER;

        float margin = 50f;
        float minX = getX() + margin, maxX = getRight() - margin;
        float minY = getY() + margin, maxY = getTop() - margin;
        boolean offMap = tx < minX || tx > maxX || ty < minY || ty > maxY;

        if (offMap) {
            float cx = getX() + getWidth() / 2f;
            float cy = getY() + getHeight() / 2f;
            float dx = tx - cx;
            float dy = ty - cy;
            float scale = Math.min((dx > 0 ? maxX - cx : minX - cx) / (dx == 0 ? 1e-4f : dx),
                                   (dy > 0 ? maxY - cy : minY - cy) / (dy == 0 ? 1e-4f : dy));
            scale = Math.abs(scale);
            float lx = cx + dx * scale;
            float ly = cy + dy * scale;
            float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees - 90f;
            icon(batch, "player", c, 0.95f, lx, ly, 18f, angle);
            font.setColor(c);
            String label = zoom == Zoom.WORLD ? "Threat off-map" : "Threat";
            font.draw(batch, label, lx - 60f, ly + 20f, 120f, Align.center, false);
        } else {
            icon(batch, "player", c, 1f, tx, ty, zoom.icon, 0f);
            font.setColor(c);
            font.draw(batch, "Threat", tx - 40f, ty + zoom.icon * 0.7f, 80f, Align.center, false);
        }
    }

    private void drawAreas(Batch batch) {
        if (zoom != Zoom.WORLD) return;
        BitmapFont font = skin.getFontSmall();
        for (FrontMark f : fronts) {
            if (f.chunks.isEmpty()) continue;
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (GridPoint2 c : f.chunks) {
                minX = Math.min(minX, c.x);
                maxX = Math.max(maxX, c.x);
                minY = Math.min(minY, c.y);
                maxY = Math.max(maxY, c.y);
            }
            float x = cellX(minX);
            float y = cellY(minY);
            float w = (maxX - minX + 1) * zoom.cell;
            float h = (maxY - minY + 1) * zoom.cell;
            dashed(batch, f.defender, 0.8f, x, y, w, h, 2f);
            font.setColor(f.defender);
            font.draw(batch, "Sighted - hostile", x - 20f, y + h + 14f, w + 40f, Align.center, false);
        }
    }

    private void drawSuggestedRoute(Batch batch) {
        if (!showSuggestedRoute || suggestion == null || player == null || player.chunk == null) return;
        List<GridPoint2> route = model.routeToSuggestion(player.chunk);
        if (route.size() < 2) return;
        float half = zoom.cell / 2f;
        float pulse = 0.7f + 0.3f * MathUtils.sin(time * 6f);
        Color color = UiTheme.FOCUS;
        for (int i = 0; i + 1 < route.size(); i++) {
            GridPoint2 a = route.get(i);
            GridPoint2 b = route.get(i + 1);
            line(batch, color, pulse, cellX(a.x) + half, cellY(a.y) + half,
                    cellX(b.x) + half, cellY(b.y) + half, 4f);
        }
    }

    private void badge(Batch batch, GridPoint2 c, String text) {
        BitmapFont font = skin.getFontSmall();
        font.setColor(UiTheme.TEXT);
        float right = cellX(c.x) + zoom.cell - 8;
        float top = cellY(c.y) + font.getCapHeight() + 10;
        font.draw(batch, text, right - zoom.cell, top, zoom.cell, Align.right, false);
    }

    // ------------------------------------------------------------------
    // Chunk view
    // ------------------------------------------------------------------

    private void drawChunk(Batch batch) {
        if (tiles == null || tiles.width <= 0 || tiles.height <= 0) return;
        int tile = (int) Math.floor(Math.min((getWidth() - 40) / tiles.width, (getHeight() - 40) / tiles.height));
        if (tile <= 0) return;
        float ox = Math.round(getX() + (getWidth() - tiles.width * tile) / 2f);
        float oy = Math.round(getY() + (getHeight() - tiles.height * tile) / 2f);
        float wall = Math.max(2f, tile / 8f);

        for (int y = 0; y < tiles.height; y++) {
            for (int x = 0; x < tiles.width; x++) {
                if (!tiles.isSeen(x, y)) continue; // fog: the vellum shows through
                float rx = ox + x * tile;
                float ry = oy + y * tile;
                if (tiles.isSolid(x, y)) {
                    fill(batch, UiTheme.MAP_WALL, 0.35f, rx, ry, tile, tile);
                    continue;
                }
                fill(batch, UiTheme.MAP_FLOOR, 0.9f, rx, ry, tile, tile);
                if (tiles.homeTiles.contains(new GridPoint2(x, y))) fill(batch, UiTheme.GOLD, 0.22f, rx, ry, tile, tile);
                int mask = tiles.wallAt(x, y);
                edge(batch, mask, Direction.NORTH.getWallMask(), 0b10000000, rx, ry + tile - wall, tile, wall);
                edge(batch, mask, Direction.SOUTH.getWallMask(), 0b00100000, rx, ry, tile, wall);
                edge(batch, mask, Direction.EAST.getWallMask(), 0b00001000, rx + tile - wall, ry, wall, tile);
                edge(batch, mask, Direction.WEST.getWallMask(), 0b00000010, rx, ry, wall, tile);
                String event = tiles.events.get(new GridPoint2(x, y));
                if (event != null) {
                    Color c = event.contains("STATUE") ? UiTheme.SUCCESS : UiTheme.SCHOOL_ARCANA;
                    fill(batch, c, 1f, rx + tile * 0.35f, ry + tile * 0.35f, tile * 0.3f, tile * 0.3f);
                }
            }
        }
        for (Map.Entry<GridPoint2, TextureRegion> e : tiles.itemIcons.entrySet()) {
            GridPoint2 p = e.getKey();
            if (!tiles.isSeen(p.x, p.y)) continue;
            float pad = tile * 0.2f;
            batch.setColor(Color.WHITE);
            batch.draw(e.getValue(), ox + p.x * tile + pad, oy + p.y * tile + pad, tile - 2 * pad, tile - 2 * pad);
        }
        for (Map.Entry<GridPoint2, Ladder.LadderType> e : tiles.ladders.entrySet()) {
            GridPoint2 p = e.getKey();
            if (!tiles.isSeen(p.x, p.y)) continue;
            boolean up = e.getValue() == Ladder.LadderType.UP;
            icon(batch, up ? "ladder_up" : "ladder_down", up ? UiTheme.GOLD : UiTheme.DANGER, 1f,
                    ox + p.x * tile + tile / 2f, oy + p.y * tile + tile / 2f, tile * 0.8f, 0f);
        }
        if (player != null && player.floor == floor && cursor.equals(player.chunk)) {
            icon(batch, "player", UiTheme.MAP_PLAYER, 1f, ox + player.tileX * tile, oy + player.tileY * tile,
                    tile * 1.1f, facingAngle(player.facing));
        }

        // Draw tile reticle
        float crx = ox + tileCursor.x * tile;
        float cry = oy + tileCursor.y * tile;
        float pulse = 0.7f + 0.3f * MathUtils.sin(time * 5f);
        drawCornerReticle(batch, crx, cry, tile, tile, Math.max(3f, tile * 0.3f), 2f, UiTheme.FOCUS, pulse);

        // Draw tile pins
        Map<GridPoint2, MapKnowledge.Pin> tpins = knowledge.getTilePins(floor, cursor);
        for (Map.Entry<GridPoint2, MapKnowledge.Pin> tp : tpins.entrySet()) {
            GridPoint2 p = tp.getKey();
            float px = ox + p.x * tile + tile / 2f;
            float py = oy + p.y * tile + tile / 2f;
            icon(batch, pinIcons.get(tp.getValue()), UiTheme.FOCUS, 1f, px, py, tile * 0.75f, 0f);
        }

        // Draw open leads
        List<ChunkTiles.OpenLead> leads = tiles.findOpenLeads();
        BitmapFont font = skin.getFontSmall();
        for (ChunkTiles.OpenLead lead : leads) {
            float lx = ox + lead.tile.x * tile + tile / 2f;
            float ly = oy + lead.tile.y * tile + tile / 2f;
            float angle = facingAngle(lead.direction);
            float leadPulse = 0.6f + 0.4f * MathUtils.sin(time * 4f);
            icon(batch, "player", UiTheme.GOLD, leadPulse, lx, ly, tile * 0.6f, angle);

            if (tileCursor.equals(lead.tile) || Math.abs(tileCursor.x - lead.tile.x) + Math.abs(tileCursor.y - lead.tile.y) <= 1) {
                font.setColor(UiTheme.GOLD);
                String arrow = lead.direction == Direction.EAST ? "unexplored ->" :
                               lead.direction == Direction.WEST ? "<- unexplored" :
                               lead.direction == Direction.NORTH ? "unexplored ^" : "unexplored v";
                font.draw(batch, arrow, lx - 50f, ly + 22f, 100f, Align.center, false);
            }
        }
    }

    private void edge(Batch batch, int mask, int wallBit, int doorBit, float x, float y, float w, float h) {
        if ((mask & doorBit) != 0) fill(batch, UiTheme.GOLD, 0.9f, x, y, w, h);
        else if ((mask & wallBit) != 0) fill(batch, UiTheme.MAP_WALL, 0.9f, x, y, w, h);
    }

    // ------------------------------------------------------------------
    // Primitives
    // ------------------------------------------------------------------

    private static float facingAngle(Direction facing) {
        Vector2 v = (facing == null ? Direction.NORTH : facing).getVector();
        return MathUtils.atan2(v.y, v.x) * MathUtils.radiansToDegrees - 90f;
    }

    private static Color washOf(Biome biome) {
        if (biome == null) return UiTheme.MAP_MAZE;
        switch (biome) {
            case FOREST: return UiTheme.MAP_FOREST;
            case DESERT: return UiTheme.MAP_DESERT;
            case LAKELANDS: return UiTheme.MAP_LAKELANDS;
            case TUNDRA: return UiTheme.MAP_TUNDRA;
            case BLIGHT: return UiTheme.MAP_BLIGHT;
            case MOUNTAINS: return UiTheme.MAP_MOUNTAINS;
            case OCEAN: return UiTheme.MAP_OCEAN;
            default: return UiTheme.MAP_MAZE;
        }
    }

    private void fill(Batch batch, Color color, float alpha, float x, float y, float w, float h) {
        batch.setColor(tint.set(color.r, color.g, color.b, alpha));
        batch.draw(white, x, y, w, h);
    }

    private void outline(Batch batch, Color color, float alpha, float x, float y, float w, float h, float t) {
        fill(batch, color, alpha, x, y, w, t);
        fill(batch, color, alpha, x, y + h - t, w, t);
        fill(batch, color, alpha, x, y, t, h);
        fill(batch, color, alpha, x + w - t, y, t, h);
    }

    /** A rectangle outlined in dashes, for what is known only by rumour. */
    private void dashed(Batch batch, Color color, float alpha, float x, float y, float w, float h, float t) {
        float dash = 8f;
        float gap = 6f;
        for (float d = 0; d < w; d += dash + gap) {
            float len = Math.min(dash, w - d);
            fill(batch, color, alpha, x + d, y, len, t);
            fill(batch, color, alpha, x + d, y + h - t, len, t);
        }
        for (float d = 0; d < h; d += dash + gap) {
            float len = Math.min(dash, h - d);
            fill(batch, color, alpha, x, y + d, t, len);
            fill(batch, color, alpha, x + w - t, y + d, t, len);
        }
    }

    private void dashedLine(Batch batch, Color color, float alpha, float x1, float y1, float x2, float y2, float t) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;
        float dash = 8f;
        float gap = 6f;
        float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        batch.setColor(tint.set(color.r, color.g, color.b, alpha));
        for (float d = 0; d < len; d += dash + gap) {
            float seg = Math.min(dash, len - d);
            float cx = x1 + (dx / len) * d;
            float cy = y1 + (dy / len) * d;
            batch.draw(white, cx, cy - t / 2f, 0f, t / 2f, seg, t, 1f, 1f, angle);
        }
    }

    private void line(Batch batch, Color color, float alpha, float x1, float y1, float x2, float y2, float t) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        batch.setColor(tint.set(color.r, color.g, color.b, alpha));
        batch.draw(white, x1, y1 - t / 2f, 0f, t / 2f, len, t, 1f, 1f, MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
    }

    private void icon(Batch batch, String name, Color color, float alpha, float cx, float cy, float size, float rotation) {
        Texture t = icons.get(name);
        if (t == null) return;
        size = Math.round(size);
        batch.setColor(tint.set(color.r, color.g, color.b, alpha));
        batch.draw(t, cx - size / 2f, cy - size / 2f, size / 2f, size / 2f, size, size, 1f, 1f, rotation,
                0, 0, t.getWidth(), t.getHeight(), false, false);
    }

    @Override
    public void dispose() {
        vellum.dispose();
        for (Texture t : icons.values()) t.dispose();
    }
}
