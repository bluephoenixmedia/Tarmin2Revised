package com.bpm.minotaur.generation.theme;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.Ladder;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Applies thematic mutations to an already-generated chunk.
 *
 * <p>This runs as a post-pass, which makes it dangerous: the generator has
 * already placed the down-ladder, gates, items and events. Every write here
 * goes through the guard rails in {@link #isProtected} so decoration cannot
 * destroy what generation produced. See
 * {@code docs/DEsign/Themed Chunk Contract.md} section 8.
 */
public class ChunkThemeDecorator {

    /** Item types that count as a fire source for the Thicket objective. */
    private static final Item.ItemType[] FIRE_SOURCES = {
            Item.ItemType.LAMP,
            Item.ItemType.SMALL_FIREBALL,
            Item.ItemType.LARGE_FIREBALL
    };

    public static void decorate(Maze maze, ChunkTheme theme, long seed,
                                MonsterDataManager monsterDataManager,
                                ItemDataManager itemDataManager,
                                AssetManager assetManager) {
        if (maze == null || theme == null) return;
        maze.setChunkTheme(theme);

        ThemeDefinition def = ThemeDataManager.getInstance().get(theme);
        if (def == null) {
            log("No definition for theme " + theme.name() + "; chunk left undecorated.");
            return;
        }

        Random rng = new Random(seed ^ 0x9E3779B97F4A7C15L);
        Set<GridPoint2> protectedTiles = collectProtectedTiles(maze);

        // (a) Layout mutation, bounded by the chunk's real dimensions.
        Rect room = carveForTheme(maze, theme, protectedTiles);

        if (theme == ChunkTheme.BLOOD_COLOSSEUM) raisePillars(maze, room, protectedTiles, assetManager);
        if (theme == ChunkTheme.BRIDGE_OF_SOULS) dressBridge(maze, room, protectedTiles, assetManager);

        // (c) Hazard.
        applyHazard(maze, def, rng, room, protectedTiles);

        // (b) Prop dressing.
        scatterProps(maze, def, rng, room, protectedTiles, assetManager);

        // (d) Encounter table.
        spawnEncounters(maze, def, rng, room, monsterDataManager, assetManager);

        // (e) Objective, with its per-theme completability guarantee.
        ThemeObjectiveState objective = installObjective(
                maze, def, theme, rng, room, monsterDataManager, itemDataManager, assetManager);
        maze.setThemeObjective(objective);

        // Every themed chunk seals on entry. The destination rune outside is the
        // forewarning that makes this fair; the Rune of Surrender is the escape.
        //
        // A chunk whose objective could not be placed is never sealed: there
        // would be nothing able to resolve it.
        if (objective.isViable()) {
            for (Gate gate : maze.getGates().values()) {
                gate.setLocked(true);
            }
        } else {
            log("Theme " + theme.name() + " could not place its objective; leaving gates open.");
        }

        // The carve may have cut the down-ladder off from the rest of the chunk.
        ensureDownLadderReachable(maze, room);
    }

    // ------------------------------------------------------------------
    // Guard rails
    // ------------------------------------------------------------------

    /**
     * Tiles the decorator must never overwrite: ladders, gates, items, events,
     * shelter home tiles, and the one-tile ring around each gate so a gate can
     * never be walled shut.
     */
    static Set<GridPoint2> collectProtectedTiles(Maze maze) {
        Set<GridPoint2> out = new HashSet<>();
        if (maze == null) return out;

        if (maze.getLadders() != null) out.addAll(maze.getLadders().keySet());
        if (maze.getItems() != null) out.addAll(maze.getItems().keySet());
        if (maze.getEventTriggers() != null) out.addAll(maze.getEventTriggers().keySet());
        if (maze.getHomeTiles() != null) out.addAll(maze.getHomeTiles());

        if (maze.getGates() != null) {
            for (GridPoint2 gatePos : maze.getGates().keySet()) {
                out.add(gatePos);
                // Keep the approach to each gate walkable, three tiles deep.
                //
                // Gates sit on the chunk border and the player arrives one tile
                // inside it, so this strip is also every arrival tile. A single
                // tile of clearance is not enough: a prop two tiles in can still
                // box the player in on arrival, in a chunk that has just sealed
                // its gates behind them.
                for (int d = 1; d <= 3; d++) {
                    out.add(new GridPoint2(gatePos.x + d, gatePos.y));
                    out.add(new GridPoint2(gatePos.x - d, gatePos.y));
                    out.add(new GridPoint2(gatePos.x, gatePos.y + d));
                    out.add(new GridPoint2(gatePos.x, gatePos.y - d));
                }
            }
        }
        return out;
    }

    private static boolean isProtected(Set<GridPoint2> protectedTiles, int x, int y) {
        return protectedTiles.contains(new GridPoint2(x, y));
    }

    /**
     * Walls are edges, not tiles: each tile's wall data carries a wall bit and a door bit per side
     * (see {@link com.bpm.minotaur.gamedata.Direction#getWallMask()}). A solid block is all four wall
     * bits. A plain {@code 1} -- what these carves once wrote for "solid" -- is a single west wall:
     * the floating slabs and orphaned doors a play-test found in a Bridge of Souls.
     */
    static final int SOLID = 0b01010101;
    /** Every door bit: a door bit sits one place above its side's wall bit. */
    static final int DOOR_BITS = SOLID << 1;
    private static final com.bpm.minotaur.gamedata.Direction[] SIDES = {
            com.bpm.minotaur.gamedata.Direction.NORTH, com.bpm.minotaur.gamedata.Direction.EAST,
            com.bpm.minotaur.gamedata.Direction.SOUTH, com.bpm.minotaur.gamedata.Direction.WEST};

    private static boolean inside(Maze maze, int x, int y) {
        return x >= 0 && y >= 0 && x < maze.getWidth() && y < maze.getHeight();
    }

    private static boolean isSolid(Maze maze, int x, int y) {
        // Never "data == 1": that is a tile with only a west wall.
        return (maze.getWallDataAt(x, y) & SOLID) == SOLID;
    }

    /** Takes away a door standing at (x, y), if there is one. */
    private static void removeDoor(Maze maze, int x, int y) {
        GridPoint2 at = new GridPoint2(x, y);
        if (maze.getGameObjects().get(at) instanceof com.bpm.minotaur.gamedata.Door) maze.getGameObjects().remove(at);
    }

    /** Makes (x, y) a block of wall, walled on every side and seen as wall from every neighbour. */
    static void makeSolid(Maze maze, Set<GridPoint2> protectedTiles, int x, int y) {
        if (!inside(maze, x, y) || isProtected(protectedTiles, x, y)) return;
        removeDoor(maze, x, y);
        maze.setTile(x, y, SOLID);
        for (com.bpm.minotaur.gamedata.Direction d : SIDES) {
            int nx = x + (int) d.getVector().x;
            int ny = y + (int) d.getVector().y;
            if (!inside(maze, nx, ny)) continue;
            int facing = d.getOpposite().getWallMask();
            int data = (maze.getWallDataAt(nx, ny) | facing) & ~(facing << 1);
            maze.setTile(nx, ny, data);
            if ((data & DOOR_BITS) == 0) removeDoor(maze, nx, ny);
        }
    }

    /**
     * Makes (x, y) open floor: its walls and doors come down, and so do its neighbours' walls and
     * doors facing it -- except toward a solid neighbour, and at the chunk's edge, which stay walls.
     */
    static void makeOpen(Maze maze, Set<GridPoint2> protectedTiles, int x, int y) {
        if (!inside(maze, x, y) || isProtected(protectedTiles, x, y)) return;
        removeDoor(maze, x, y);
        int own = 0;
        for (com.bpm.minotaur.gamedata.Direction d : SIDES) {
            int nx = x + (int) d.getVector().x;
            int ny = y + (int) d.getVector().y;
            if (!inside(maze, nx, ny) || isSolid(maze, nx, ny)) {
                own |= d.getWallMask();
                continue;
            }
            int facing = d.getOpposite().getWallMask();
            int data = maze.getWallDataAt(nx, ny) & ~facing & ~(facing << 1);
            maze.setTile(nx, ny, data);
            if ((data & DOOR_BITS) == 0) removeDoor(maze, nx, ny);
        }
        maze.setTile(x, y, own);
    }


    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /** A rectangle in tile coordinates, inclusive on both bounds. */
    static class Rect {
        final int minX, minY, maxX, maxY;

        Rect(int minX, int minY, int maxX, int maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        boolean contains(int x, int y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }

        int centerX() {
            return (minX + maxX) / 2;
        }

        int centerY() {
            return (minY + maxY) / 2;
        }
    }

    /**
     * Derives the themed room from the chunk's actual dimensions rather than
     * the hardcoded 7..24 the original decorator assumed.
     */
    static Rect centralRect(Maze maze, float insetFraction) {
        int w = maze.getWidth();
        int h = maze.getHeight();
        int insetX = Math.max(2, (int) (w * insetFraction));
        int insetY = Math.max(2, (int) (h * insetFraction));
        return new Rect(insetX, insetY, w - 1 - insetX, h - 1 - insetY);
    }

    private static Rect carveForTheme(Maze maze, ChunkTheme theme, Set<GridPoint2> protectedTiles) {
        switch (theme) {
            case BLOOD_COLOSSEUM:
                return carveArena(maze, protectedTiles);
            case RUINED_CASTLE:
                return carveCastle(maze, protectedTiles);
            case MAKESHIFT_GRAVEYARD:
                return carveClearing(maze, protectedTiles, 0.28f);
            case WANDERING_BATTALION:
                return carveClearing(maze, protectedTiles, 0.25f);
            case FLOODED_CAVERNS:
                return carveBasin(maze, protectedTiles);
            case BURIED_NECROPOLIS:
                return carveDuneBowl(maze, protectedTiles);
            case DROWNED_CAUSEWAY:
                return carveCauseway(maze, protectedTiles);
            case BRIDGE_OF_SOULS:
                return carveBridge(maze, protectedTiles);
            case OVERGROWN_THICKET:
            default:
                // The thicket keeps the generator's corridors; brambles do the work.
                return centralRect(maze, 0.10f);
        }
    }

    /** Sunken tomb cut into a dune bowl; the entrance is the only break in the rim. */
    private static Rect carveDuneBowl(Maze maze, Set<GridPoint2> protectedTiles) {
        Rect outer = centralRect(maze, 0.22f);
        for (int y = outer.minY; y <= outer.maxY; y++) {
            for (int x = outer.minX; x <= outer.maxX; x++) {
                makeOpen(maze, protectedTiles, x, y);
            }
        }

        int tombInset = Math.max(2, (outer.maxX - outer.minX) / 4);
        int tMinX = outer.minX + tombInset;
        int tMaxX = outer.maxX - tombInset;
        int tMinY = outer.minY + tombInset;
        int tMaxY = outer.maxY - tombInset;

        if (tMaxX - tMinX < 3 || tMaxY - tMinY < 3) {
            return outer;
        }

        for (int x = tMinX; x <= tMaxX; x++) {
            makeSolid(maze, protectedTiles, x, tMinY);
            makeSolid(maze, protectedTiles, x, tMaxY);
        }
        for (int y = tMinY; y <= tMaxY; y++) {
            makeSolid(maze, protectedTiles, tMinX, y);
            makeSolid(maze, protectedTiles, tMaxX, y);
        }
        for (int y = tMinY + 1; y < tMaxY; y++) {
            for (int x = tMinX + 1; x < tMaxX; x++) {
                makeOpen(maze, protectedTiles, x, y);
            }
        }
        int doorX = (tMinX + tMaxX) / 2;
        makeOpen(maze, protectedTiles, doorX, tMinY);

        return new Rect(tMinX + 1, tMinY + 1, tMaxX - 1, tMaxY - 1);
    }

    /** Single causeway between wetland banks. */
    private static Rect carveCauseway(Maze maze, Set<GridPoint2> protectedTiles) {
        Rect r = centralRect(maze, 0.18f);
        int midY = (r.minY + r.maxY) / 2;
        for (int y = r.minY; y <= r.maxY; y++) {
            for (int x = r.minX; x <= r.maxX; x++) {
                if (Math.abs(y - midY) <= 1) {
                    makeOpen(maze, protectedTiles, x, y);
                }
            }
        }
        return r;
    }

    /** Open vault with four pillars to break sightlines. */
    private static Rect carveArena(Maze maze, Set<GridPoint2> protectedTiles) {
        Rect r = centralRect(maze, 0.20f);
        for (int y = r.minY; y <= r.maxY; y++) {
            for (int x = r.minX; x <= r.maxX; x++) {
                makeOpen(maze, protectedTiles, x, y);
            }
        }
        return r;
    }

    /**
     * The arena's four pillars, a quarter in from each corner: the ruined pillar prop, impassable,
     * standing on open floor -- what the carve once tried to build out of single wall edges.
     */
    private static void raisePillars(Maze maze, Rect r, Set<GridPoint2> protectedTiles, AssetManager assetManager) {
        int inset = Math.max(2, (r.maxX - r.minX) / 4);
        int[] xs = {r.minX + inset, r.maxX - inset};
        int[] ys = {r.minY + inset, r.maxY - inset};
        for (int x : xs) {
            for (int y : ys) {
                if (isProtected(protectedTiles, x, y) || maze.getScenery().containsKey(new GridPoint2(x, y))) continue;
                Scenery pillar = Scenery.fromProp("ruined_pillar", x, y);
                if (pillar == null) continue;
                bindSceneryTexture(pillar, assetManager);
                maze.addScenery(pillar);
            }
        }
    }

    // ------------------------------------------------------------------
    // The Bridge of Souls
    // ------------------------------------------------------------------

    /** The island's half-width, as a share of the chunk's smaller side. */
    static final int ISLAND_DIVISOR = 6;

    /** The island at the chunk's heart. */
    static Rect bridgeIsland(Maze maze) {
        int r = Math.max(3, Math.min(maze.getWidth(), maze.getHeight()) / ISLAND_DIVISOR);
        int cx = maze.getWidth() / 2;
        int cy = maze.getHeight() / 2;
        return new Rect(cx - r, cy - r, cx + r, cy + r);
    }

    /**
     * The two-tile bridge from {@code start} to the island: straight in when it lines up with the
     * island, else along one axis to the island's middle and then across. The last entry is where
     * it lands on the island.
     */
    static List<GridPoint2> bridgePath(Maze maze, GridPoint2 start, Rect island) {
        List<GridPoint2> path = new ArrayList<>();
        int cx = (island.minX + island.maxX) / 2;
        int cy = (island.minY + island.maxY) / 2;
        int x = start.x;
        int y = start.y;
        boolean vertical = x >= island.minX && x < island.maxX
                || !(y >= island.minY && y < island.maxY) && Math.abs(y - cy) >= Math.abs(x - cx);
        for (int guard = 0; guard < maze.getWidth() + maze.getHeight() && !island.contains(x, y); guard++) {
            if (vertical) {
                int side = x + 1 <= maze.getWidth() - 2 ? 1 : -1;
                path.add(new GridPoint2(x, y));
                path.add(new GridPoint2(x + side, y));
                if (y == cy && !(x >= island.minX && x <= island.maxX)) {
                    vertical = false;
                    continue;
                }
                y += Integer.signum(cy - y);
            } else {
                int side = y + 1 <= maze.getHeight() - 2 ? 1 : -1;
                path.add(new GridPoint2(x, y));
                path.add(new GridPoint2(x, y + side));
                if (x == cx && !(y >= island.minY && y <= island.maxY)) {
                    vertical = true;
                    continue;
                }
                x += Integer.signum(cx - x);
            }
        }
        path.add(new GridPoint2(x, y));
        return path;
    }

    /** The tile just inside a border gate: where a bridge begins. */
    private static GridPoint2 inward(Maze maze, GridPoint2 gate) {
        int dx = gate.x == 0 ? 1 : gate.x == maze.getWidth() - 1 ? -1 : 0;
        int dy = gate.y == 0 ? 1 : gate.y == maze.getHeight() - 1 ? -1 : 0;
        return new GridPoint2(gate.x + dx, gate.y + dy);
    }

    /** Where bridges start: every gate, and anything else the chunk must keep within reach. */
    private static List<GridPoint2> bridgeStarts(Maze maze) {
        List<GridPoint2> starts = new ArrayList<>();
        for (GridPoint2 gate : maze.getGates().keySet()) starts.add(inward(maze, gate));
        if (maze.getLadders() != null) starts.addAll(maze.getLadders().keySet());
        if (maze.getEventTriggers() != null) starts.addAll(maze.getEventTriggers().keySet());
        if (maze.getHomeTiles() != null) starts.addAll(maze.getHomeTiles());
        return starts;
    }

    /**
     * The Bridge of Souls (agreed 2026-10-10): an island in a chasm of molten fire, with a bridge two
     * tiles wide from every gate across to it. The chasm is open to the eye and closed to the foot.
     */
    private static Rect carveBridge(Maze maze, Set<GridPoint2> protectedTiles) {
        Rect island = bridgeIsland(maze);
        Set<GridPoint2> dry = new HashSet<>(protectedTiles);
        for (int y = island.minY; y <= island.maxY; y++) {
            for (int x = island.minX; x <= island.maxX; x++) dry.add(new GridPoint2(x, y));
        }
        for (GridPoint2 start : bridgeStarts(maze)) dry.addAll(bridgePath(maze, start, island));

        List<com.bpm.minotaur.gamedata.item.Item> adrift = new ArrayList<>();
        for (int y = 1; y < maze.getHeight() - 1; y++) {
            for (int x = 1; x < maze.getWidth() - 1; x++) {
                makeOpen(maze, protectedTiles, x, y);
                GridPoint2 at = new GridPoint2(x, y);
                if (dry.contains(at)) continue;
                maze.getLiquidManager().setLiquidAt(x, y, com.bpm.minotaur.gamedata.liquid.LiquidType.MOLTEN_FIRE);
                Monster stranded = maze.getMonsters().get(at);
                if (stranded != null) maze.removeMonster(stranded);
                if (maze.getScenery().containsKey(at)) maze.removeScenery(x, y);
                com.bpm.minotaur.gamedata.item.Item item = maze.getItems().get(at);
                if (item != null) adrift.add(item);
            }
        }
        // Anything lying where the chasm opened washes up on the island.
        List<GridPoint2> shore = openTiles(maze, island);
        for (com.bpm.minotaur.gamedata.item.Item item : adrift) {
            maze.removeItem(item);
            for (GridPoint2 pt : shore) {
                if (maze.getItems().containsKey(pt)) continue;
                item.getPosition().set(pt.x + 0.5f, pt.y + 0.5f);
                maze.addItem(item);
                break;
            }
        }
        return island;
    }

    /** Bridge tiles: dry floor outside the island, where the undead stand guard. */
    private static List<GridPoint2> bridgeTiles(Maze maze, Rect island) {
        List<GridPoint2> out = new ArrayList<>();
        for (GridPoint2 pt : openTiles(maze, new Rect(1, 1, maze.getWidth() - 2, maze.getHeight() - 2))) {
            if (island.contains(pt.x, pt.y)) continue;
            if (maze.getLiquidManager().getLiquidAt(pt.x, pt.y).isImpassable()) continue;
            if (maze.getGates().containsKey(pt) || maze.getLadders().containsKey(pt)) continue;
            out.add(pt);
        }
        return out;
    }

    /**
     * Dresses the bridge: railings of bone spikes and chain standing in the fire along every bridge,
     * a stone arch where each lands on the island, pillars at the island's corners and braziers ringing
     * the dais at its heart.
     */
    private static void dressBridge(Maze maze, Rect island, Set<GridPoint2> protectedTiles, AssetManager assetManager) {
        Set<GridPoint2> railed = new HashSet<>();
        for (GridPoint2 start : bridgeStarts(maze)) {
            List<GridPoint2> path = bridgePath(maze, start, island);
            for (int i = 0; i < path.size() - 1; i++) {
                GridPoint2 at = path.get(i);
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int rx = at.x + d[0], ry = at.y + d[1];
                    if (rx < 1 || ry < 1 || rx > maze.getWidth() - 2 || ry > maze.getHeight() - 2) continue;
                    if (!maze.getLiquidManager().getLiquidAt(rx, ry).isImpassable()) continue;
                    if ((rx + ry) % 2 != 0 || !railed.add(new GridPoint2(rx, ry))) continue;
                    place(maze, (rx / 2 + ry / 2) % 2 == 0 ? "bone_spike_rail" : "arena_chain", rx, ry, assetManager);
                }
            }
            if (maze.getGates().containsKey(neighbourGate(maze, start))) {
                GridPoint2 landing = path.get(path.size() - 1);
                place(maze, "stone_arch", landing.x, landing.y, assetManager);
            }
        }
        int[][] corners = {{island.minX + 1, island.minY + 1}, {island.maxX - 1, island.minY + 1},
                {island.minX + 1, island.maxY - 1}, {island.maxX - 1, island.maxY - 1}};
        for (int[] c : corners) {
            if (!isProtected(protectedTiles, c[0], c[1])) place(maze, "ruined_pillar", c[0], c[1], assetManager);
        }
        int cx = (island.minX + island.maxX) / 2;
        int cy = (island.minY + island.maxY) / 2;
        int[][] ring = {{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] o : ring) place(maze, "brazier", cx + o[0], cy + o[1], assetManager);
    }

    /** The gate a bridge start sits just inside of, if it is one. */
    private static GridPoint2 neighbourGate(Maze maze, GridPoint2 start) {
        for (GridPoint2 gate : maze.getGates().keySet()) {
            if (inward(maze, gate).equals(start)) return gate;
        }
        return new GridPoint2(-1, -1);
    }

    private static void place(Maze maze, String propId, int x, int y, AssetManager assetManager) {
        if (maze.getScenery().containsKey(new GridPoint2(x, y))) return;
        Scenery prop = Scenery.fromProp(propId, x, y);
        if (prop == null) return;
        bindSceneryTexture(prop, assetManager);
        maze.addScenery(prop);
    }

    /** Courtyard wrapping an inner keep with a throne room. */
    private static Rect carveCastle(Maze maze, Set<GridPoint2> protectedTiles) {
        Rect outer = centralRect(maze, 0.22f);
        for (int y = outer.minY; y <= outer.maxY; y++) {
            for (int x = outer.minX; x <= outer.maxX; x++) {
                makeOpen(maze, protectedTiles, x, y);
            }
        }

        int keepInset = Math.max(2, (outer.maxX - outer.minX) / 4);
        int kMinX = outer.minX + keepInset;
        int kMaxX = outer.maxX - keepInset;
        int kMinY = outer.minY + keepInset;
        int kMaxY = outer.maxY - keepInset;

        if (kMaxX - kMinX < 3 || kMaxY - kMinY < 3) {
            return outer; // Chunk too small for an inner keep.
        }

        for (int x = kMinX; x <= kMaxX; x++) {
            makeSolid(maze, protectedTiles, x, kMinY);
            makeSolid(maze, protectedTiles, x, kMaxY);
        }
        for (int y = kMinY; y <= kMaxY; y++) {
            makeSolid(maze, protectedTiles, kMinX, y);
            makeSolid(maze, protectedTiles, kMaxX, y);
        }
        // Throne room interior.
        for (int y = kMinY + 1; y < kMaxY; y++) {
            for (int x = kMinX + 1; x < kMaxX; x++) {
                makeOpen(maze, protectedTiles, x, y);
            }
        }
        // A single doorway, so the keep is enterable -- and kept clear of props on both sides, or a
        // scattered rubble pile can seal the throne room as surely as a wall.
        int doorX = (kMinX + kMaxX) / 2;
        makeOpen(maze, protectedTiles, doorX, kMinY);
        protectedTiles.add(new GridPoint2(doorX, kMinY));
        protectedTiles.add(new GridPoint2(doorX, kMinY + 1));
        protectedTiles.add(new GridPoint2(doorX, kMinY - 1));

        return new Rect(kMinX + 1, kMinY + 1, kMaxX - 1, kMaxY - 1);
    }

    /** Plain open clearing. */
    private static Rect carveClearing(Maze maze, Set<GridPoint2> protectedTiles, float inset) {
        Rect r = centralRect(maze, inset);
        for (int y = r.minY; y <= r.maxY; y++) {
            for (int x = r.minX; x <= r.maxX; x++) {
                makeOpen(maze, protectedTiles, x, y);
            }
        }
        return r;
    }

    /** A shallow bowl the flood pools into; corridors are left intact. */
    private static Rect carveBasin(Maze maze, Set<GridPoint2> protectedTiles) {
        Rect r = centralRect(maze, 0.30f);
        for (int y = r.minY; y <= r.maxY; y++) {
            for (int x = r.minX; x <= r.maxX; x++) {
                makeOpen(maze, protectedTiles, x, y);
            }
        }
        return r;
    }

    // ------------------------------------------------------------------
    // Hazards (contract slot c)
    // ------------------------------------------------------------------

    private static void applyHazard(Maze maze, ThemeDefinition def, Random rng,
                                    Rect room, Set<GridPoint2> protectedTiles) {
        ThemeHazardKind hazard = def.getHazard();
        if (hazard == null) return;
        float density = def.getHazardDensity();

        switch (hazard) {
            case BLOOD_POOLS:
                for (int y = room.minY; y <= room.maxY; y++) {
                    for (int x = room.minX; x <= room.maxX; x++) {
                        if (maze.isWall(x, y)) continue;
                        if (rng.nextFloat() < density) {
                            maze.addBlood(x, y, 0.06f + rng.nextFloat() * 0.12f);
                            maze.getLiquidManager().setLiquidAt(x, y, LiquidType.BLOOD);
                        }
                    }
                }
                break;

            case LIQUID_FLOOD:
                floodChunk(maze, rng, density);
                break;

            case QUICKSAND_HAZARD:
                for (int y = room.minY; y <= room.maxY; y++) {
                    for (int x = room.minX; x <= room.maxX; x++) {
                        if (maze.isWall(x, y)) continue;
                        if (rng.nextFloat() < density) {
                            maze.getLiquidManager().setLiquidAt(x, y, LiquidType.QUICKSAND);
                        }
                    }
                }
                break;

            case RISING_TIDE:
                floodChunk(maze, rng, density);
                break;

            case BLOCKING_PROPS:
            case BURNING_TILES:
            case BRAMBLE_REGROWTH:
                // These are expressed through props placed by scatterProps and,
                // for brambles, by ThemeObjectiveManager's per-turn regrowth.
                break;
        }
    }

    /**
     * Floods open tiles across the whole chunk, deepening toward the basin so
     * the drowned cache sits in the worst of it.
     */
    private static void floodChunk(Maze maze, Random rng, float density) {
        int width = maze.getWidth();
        int height = maze.getHeight();
        int cx = width / 2;
        int cy = height / 2;
        float maxDist = (float) Math.sqrt(cx * cx + cy * cy);

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (maze.isWall(x, y)) continue;

                float dist = (float) Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                float depth = 1f - (dist / Math.max(1f, maxDist));
                // Wading costs a turn, so flooding must leave dry ground to
                // fight on. An earlier pass covered ~48% of the chunk, which
                // made the wade cost an unavoidable tax rather than a choice.
                if (rng.nextFloat() > density + depth * 0.30f) continue;

                if (depth > 0.72f) {
                    maze.getLiquidManager().setLiquidAt(x, y, LiquidType.BLACK_MUCK);
                } else if (depth > 0.45f) {
                    maze.getLiquidManager().setLiquidAt(x, y, LiquidType.WATER);
                } else if (rng.nextFloat() < 0.25f) {
                    maze.getLiquidManager().setLiquidAt(x, y, LiquidType.BLOOD);
                } else {
                    maze.getLiquidManager().setLiquidAt(x, y, LiquidType.WATER);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Props (contract slot b)
    // ------------------------------------------------------------------

    private static void scatterProps(Maze maze, ThemeDefinition def, Random rng,
                                     Rect room, Set<GridPoint2> protectedTiles,
                                     AssetManager assetManager) {
        if (def.getProps().isEmpty()) return;

        // Props scatter across the whole chunk, not just the carved room, so a
        // themed chunk reads as themed from the corridors too.
        List<GridPoint2> candidates = openTiles(maze, def.getTheme() == ChunkTheme.BRIDGE_OF_SOULS
                ? room : new Rect(1, 1, maze.getWidth() - 2, maze.getHeight() - 2));
        Collections.shuffle(candidates, rng);

        int budget = Math.round(candidates.size() * def.getPropDensity());
        int placed = 0;

        for (GridPoint2 pt : candidates) {
            if (placed >= budget) break;
            if (isProtected(protectedTiles, pt.x, pt.y)) continue;
            if (maze.getScenery().containsKey(pt)) continue;

            String propId = def.rollProp(rng);
            if (propId == null) continue;

            Scenery prop = Scenery.fromProp(propId, pt.x, pt.y);
            if (prop == null) continue;

            // An impassable prop must never be the only thing standing between
            // the player and the rest of the chunk.
            if (prop.isImpassable() && isChokePoint(maze, pt.x, pt.y)) continue;

            bindSceneryTexture(prop, assetManager);
            maze.addScenery(prop);
            placed++;
        }
    }

    private static void bindSceneryTexture(Scenery s, AssetManager assetManager) {
        if (s == null || s.getTexturePath() == null || assetManager == null) return;
        String path = s.getTexturePath();
        try {
            if (assetManager.isLoaded(path, com.badlogic.gdx.graphics.Texture.class)) {
                s.setTexture(assetManager.get(path, com.badlogic.gdx.graphics.Texture.class));
            } else if (com.badlogic.gdx.Gdx.files != null && com.badlogic.gdx.Gdx.files.internal(path) != null && com.badlogic.gdx.Gdx.files.internal(path).exists()) {
                assetManager.load(path, com.badlogic.gdx.graphics.Texture.class);
                assetManager.finishLoadingAsset(path);
                if (assetManager.isLoaded(path, com.badlogic.gdx.graphics.Texture.class)) {
                    s.setTexture(assetManager.get(path, com.badlogic.gdx.graphics.Texture.class));
                }
            }
        } catch (Exception ignored) {
            // Headless / mock test safety
        }
    }

    /**
     * True when a tile has two or fewer walkable neighbours, i.e. it is a
     * corridor or a doorway. Blocking one of these can sever the chunk.
     *
     * <p>Props already placed count as walls here. Judging only on terrain lets
     * two props that are each individually harmless combine into a barrier,
     * which is how a themed chunk ends up cut in half.
     */
    private static boolean isChokePoint(Maze maze, int x, int y) {
        int open = 0;
        if (isWalkable(maze, x + 1, y)) open++;
        if (isWalkable(maze, x - 1, y)) open++;
        if (isWalkable(maze, x, y + 1)) open++;
        if (isWalkable(maze, x, y - 1)) open++;
        return open <= 2;
    }

    private static boolean isWalkable(Maze maze, int x, int y) {
        if (maze.isWall(x, y)) return false;
        Scenery prop = maze.getScenery().get(new GridPoint2(x, y));
        return prop == null || !prop.isImpassable();
    }

    // ------------------------------------------------------------------
    // Encounters (contract slot d)
    // ------------------------------------------------------------------

    private static void spawnEncounters(Maze maze, ThemeDefinition def, Random rng, Rect room,
                                        MonsterDataManager monsterDataManager,
                                        AssetManager assetManager) {
        if (def.getMonsters().isEmpty()) return;

        List<GridPoint2> spawns = def.getTheme() == ChunkTheme.BRIDGE_OF_SOULS ? bridgeTiles(maze, room) : openTiles(maze, room);
        Collections.shuffle(spawns, rng);

        int depth = Math.max(1, maze.getLevel());
        int count = Math.min(spawns.size(), 5 + rng.nextInt(4) + Math.min(4, depth));

        Faction faction = factionFor(def.getTheme());

        for (int i = 0; i < count && i < spawns.size(); i++) {
            GridPoint2 pt = spawns.get(i);
            if (maze.getMonsters().containsKey(pt)) continue;

            ThemeDefinition.MonsterEntry entry = def.rollMonster(rng);
            if (entry == null) continue;

            Monster m = createMonster(entry.type, pt.x, pt.y, MonsterColor.RED, faction,
                    entry.hpBonus + depth * 2, monsterDataManager, assetManager);
            maze.addMonster(m);
        }
    }

    private static Faction factionFor(ChunkTheme theme) {
        switch (theme) {
            case BLOOD_COLOSSEUM:
                return Faction.CHAOS_BERSERK;
            case WANDERING_BATTALION:
            case RUINED_CASTLE:
                return Faction.TARMIN_LEGION;
            case MAKESHIFT_GRAVEYARD:
            case BRIDGE_OF_SOULS:
                return Faction.UNDEAD;
            case FLOODED_CAVERNS:
            case OVERGROWN_THICKET:
            default:
                return Faction.BEASTS_AND_VERMIN;
        }
    }

    // ------------------------------------------------------------------
    // Objectives (contract slot e) and their completability guarantees
    // ------------------------------------------------------------------

    private static ThemeObjectiveState installObjective(Maze maze, ThemeDefinition def, ChunkTheme theme,
                                                        Random rng, Rect room,
                                                        MonsterDataManager monsterDataManager,
                                                        ItemDataManager itemDataManager,
                                                        AssetManager assetManager) {
        ThemeObjectiveKind kind = def.getObjective();
        if (kind == null) {
            return new ThemeObjectiveState(null, 1);
        }

        ThemeObjectiveState state = new ThemeObjectiveState(kind, def.getObjectiveCount());

        switch (kind) {
            case LAST_COMBATANT_STANDING:
                // Nothing to place: the encounter table is the objective. An
                // arena that spawned nothing would complete on turn one.
                state.setRequired(1);
                state.setViable(!maze.getMonsters().isEmpty());
                break;

            case SLAY_CHAMPION:
                state.setRequired(1);
                state.setViable(placeChampion(maze, def, room, monsterDataManager, assetManager));
                break;

            case RECONSECRATE_GRAVES:
                int graves = placeObjectiveProps(maze, rng, room, "grave_mound",
                        def.getObjectiveCount(), assetManager);
                // If the chunk could not fit every grave, require only what exists.
                state.setRequired(Math.max(1, graves));
                state.setViable(graves > 0);
                placeGravedigger(maze, room, monsterDataManager, assetManager);
                break;

            case OPEN_DROWNED_CACHE:
                state.setRequired(1);
                state.setViable(placeDrownedCache(maze, room, itemDataManager, assetManager));
                break;

            case DESTROY_HEART_BLOOM:
                int blooms = placeObjectiveProps(maze, rng, room, "heart_bloom", 1, assetManager);
                state.setRequired(1);
                state.setViable(blooms > 0);
                guaranteeFireSource(maze, rng, itemDataManager, assetManager);
                break;

            case BREACH_SEALED_TOMB:
                int tombs = placeObjectiveProps(maze, rng, room, "sealed_tomb", 1, assetManager);
                boolean champPlaced = placeChampion(maze, def, room, monsterDataManager, assetManager);
                state.setRequired(1);
                state.setViable(tombs > 0 && champPlaced);
                break;

            case ACTIVATE_SUNKEN_SHRINE:
                int shrines = placeObjectiveProps(maze, rng, room, "drowned_cache", 1, assetManager);
                state.setRequired(1);
                state.setViable(shrines > 0);
                break;
        }

        return state;
    }

    /** @return true when the champion was actually placed. */
    private static boolean placeChampion(Maze maze, ThemeDefinition def, Rect room,
                                         MonsterDataManager monsterDataManager,
                                         AssetManager assetManager) {
        MonsterType type = def.getChampionType();
        if (type == null) type = MonsterType.OGRE;

        boolean keeper = def.getTheme() == ChunkTheme.BRIDGE_OF_SOULS;
        GridPoint2 heart = new GridPoint2((room.minX + room.maxX) / 2, (room.minY + room.maxY) / 2);
        GridPoint2 seat = keeper && maze.isPassable(heart.x, heart.y) ? heart : findPlacementTile(maze, room);
        if (seat == null) return false;

        Monster champion = createMonster(type, seat.x, seat.y, MonsterColor.RED,
                factionFor(def.getTheme()), def.getChampionHpBonus(),
                monsterDataManager, assetManager);
        champion.setThemeChampion(true);
        // The Bringer holds his island: the seeker chooses when to cross onto his ground.
        if (keeper) champion.setTether(room.minX, room.minY, room.maxX, room.maxY);
        maze.addMonster(champion);
        return true;
    }

    private static void placeGravedigger(Maze maze, Rect room,
                                         MonsterDataManager monsterDataManager,
                                         AssetManager assetManager) {
        GridPoint2 pt = findPlacementTile(maze, room);
        if (pt == null) return;

        Monster gravedigger = createMonster(MonsterType.DWARF, pt.x, pt.y, MonsterColor.WHITE,
                Faction.NEUTRAL, 40, monsterDataManager, assetManager);
        // The objective is anchored on him and the chunk is sealed, so he cannot
        // be allowed to die to a stray arrow or a faction brawl.
        gravedigger.setInvulnerable(true);
        maze.addMonster(gravedigger);
    }

    /** @return true when the cache was actually placed. */
    private static boolean placeDrownedCache(Maze maze, Rect room,
                                             ItemDataManager itemDataManager,
                                             AssetManager assetManager) {
        GridPoint2 pt = findDeepestTile(maze, room);
        if (pt == null) pt = findPlacementTile(maze, room);
        if (pt == null) return false;

        Scenery cache = Scenery.fromProp("drowned_cache", pt.x, pt.y);
        if (cache == null) return false;
        cache.setObjectiveMarker(true);
        bindSceneryTexture(cache, assetManager);
        maze.addScenery(cache);

        // The cache is the reward as well as the objective.
        if (itemDataManager != null) {
            Item treasure = itemDataManager.createItem(
                    Item.ItemType.REGULAR_CHEST, pt.x, pt.y, ItemColor.BLUE, assetManager);
            if (treasure != null) {
                maze.addItem(treasure);
            }
        }
        return true;
    }

    /**
     * Scatters objective-marker props across the room, returning how many were
     * actually placed so the objective can require only what exists.
     */
    private static int placeObjectiveProps(Maze maze, Random rng, Rect room, String propId, int count, AssetManager assetManager) {
        List<GridPoint2> spots = openTiles(maze, room);
        if (spots.isEmpty()) return 0;
        Collections.shuffle(spots, rng);

        int placed = 0;
        for (GridPoint2 pt : spots) {
            if (placed >= count) break;
            if (maze.getScenery().containsKey(pt)) continue;

            Scenery marker = Scenery.fromProp(propId, pt.x, pt.y);
            if (marker == null) return placed;
            marker.setObjectiveMarker(true);
            bindSceneryTexture(marker, assetManager);
            maze.addScenery(marker);
            placed++;
        }
        return placed;
    }

    /**
     * The Thicket objective needs fire. The chunk is sealed, so arriving without
     * a fire source would be an unwinnable trap.
     */
    private static void guaranteeFireSource(Maze maze, Random rng,
                                            ItemDataManager itemDataManager,
                                            AssetManager assetManager) {
        if (itemDataManager == null) return;

        for (Item existing : maze.getItems().values()) {
            for (Item.ItemType fire : FIRE_SOURCES) {
                if (existing.getType() == fire) return; // Already satisfied.
            }
        }

        List<GridPoint2> spots = openTiles(maze, new Rect(1, 1, maze.getWidth() - 2, maze.getHeight() - 2));
        if (spots.isEmpty()) return;
        GridPoint2 pt = spots.get(rng.nextInt(spots.size()));

        Item lamp = itemDataManager.createItem(Item.ItemType.LAMP, pt.x, pt.y, ItemColor.GOLD, assetManager);
        if (lamp != null) {
            maze.addItem(lamp);
        }
    }

    // ------------------------------------------------------------------
    // Reachability
    // ------------------------------------------------------------------

    /**
     * Relocates the down-ladder into the carved room when the carve has cut it
     * off. Burying the descent is the failure mode that made the original
     * hardcoded carve dangerous.
     */
    static void ensureDownLadderReachable(Maze maze, Rect room) {
        if (maze.getLadders() == null || maze.getLadders().isEmpty()) return;

        GridPoint2 ladderPos = null;
        Ladder downLadder = null;
        for (java.util.Map.Entry<GridPoint2, Ladder> e : maze.getLadders().entrySet()) {
            if (e.getValue() != null && e.getValue().getType() == Ladder.LadderType.DOWN) {
                ladderPos = e.getKey();
                downLadder = e.getValue();
                break;
            }
        }
        if (ladderPos == null) return;

        // A ladder sitting on an open tile with at least one open neighbour is fine.
        if (!maze.isWall(ladderPos.x, ladderPos.y) && !isFullyEnclosed(maze, ladderPos.x, ladderPos.y)) {
            return;
        }

        GridPoint2 rescue = findPlacementTile(maze, room);
        if (rescue == null) {
            // Last resort: open the tile the ladder is already on.
            maze.setTile(ladderPos.x, ladderPos.y, 0);
            return;
        }

        maze.getLadders().remove(ladderPos);
        Ladder relocated = new Ladder(rescue.x, rescue.y, Ladder.LadderType.DOWN,
                downLadder != null ? downLadder.getStyle() : null);
        maze.addLadder(relocated);
        log("Relocated buried down-ladder from " + ladderPos + " to " + rescue);
    }

    private static boolean isFullyEnclosed(Maze maze, int x, int y) {
        return maze.isWall(x + 1, y) && maze.isWall(x - 1, y)
                && maze.isWall(x, y + 1) && maze.isWall(x, y - 1);
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /** Open, unoccupied tiles inside a rectangle, clamped to the chunk. */
    static List<GridPoint2> openTiles(Maze maze, Rect rect) {
        List<GridPoint2> list = new ArrayList<>();
        int minX = Math.max(1, rect.minX);
        int minY = Math.max(1, rect.minY);
        int maxX = Math.min(maze.getWidth() - 2, rect.maxX);
        int maxY = Math.min(maze.getHeight() - 2, rect.maxY);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (maze.isWall(x, y)) continue;
                GridPoint2 pt = new GridPoint2(x, y);
                if (maze.getMonsters().containsKey(pt)) continue;
                if (maze.getGates().containsKey(pt)) continue;
                if (maze.getLadders().containsKey(pt)) continue;
                list.add(pt);
            }
        }
        return list;
    }

    private static GridPoint2 findPlacementTile(Maze maze, Rect room) {
        List<GridPoint2> tiles = openTiles(maze, room);
        for (GridPoint2 pt : tiles) {
            if (!maze.getScenery().containsKey(pt) && !maze.getItems().containsKey(pt)) {
                return pt;
            }
        }
        return tiles.isEmpty() ? null : tiles.get(0);
    }

    /**
     * The tile deepest in the flood that the player can actually get to.
     *
     * <p>The chunk seals behind the player, so a cache walled into a pocket
     * would be an objective that cannot be completed. Candidates enclosed by
     * walls or hemmed in by impassable props are rejected outright.
     */
    private static GridPoint2 findDeepestTile(Maze maze, Rect room) {
        GridPoint2 best = null;
        float bestDist = Float.MAX_VALUE;
        int cx = maze.getWidth() / 2;
        int cy = maze.getHeight() / 2;

        for (GridPoint2 pt : openTiles(maze, room)) {
            if (maze.getScenery().containsKey(pt)) continue;
            if (!hasOpenApproach(maze, pt.x, pt.y)) continue;

            float d = (pt.x - cx) * (pt.x - cx) + (pt.y - cy) * (pt.y - cy);
            if (d < bestDist) {
                bestDist = d;
                best = pt;
            }
        }
        return best;
    }

    /** True when at least one orthogonal neighbour is walkable. */
    private static boolean hasOpenApproach(Maze maze, int x, int y) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] off : offsets) {
            int nx = x + off[0];
            int ny = y + off[1];
            if (maze.isWall(nx, ny)) continue;

            Scenery blocker = maze.getScenery().get(new GridPoint2(nx, ny));
            if (blocker != null && blocker.isImpassable()) continue;
            return true;
        }
        return false;
    }

    private static Monster createMonster(MonsterType type, int x, int y, MonsterColor color,
                                         Faction faction, int hpBonus,
                                         MonsterDataManager dataManager, AssetManager assetManager) {
        Monster m;
        if (dataManager != null && assetManager != null) {
            m = new Monster(type, x, y, color, dataManager, assetManager);
        } else {
            m = new Monster(type, 30 + hpBonus, 12, x, y);
        }
        m.setFaction(faction);
        if (hpBonus > 0) {
            m.setMaxHP(m.getMaxHP() + hpBonus);
            m.setCurrentHP(m.getCurrentHP() + hpBonus);
        }
        return m;
    }

    private static void log(String message) {
        if (Gdx.app != null) {
            Gdx.app.log("ChunkThemeDecorator", message);
        }
    }
}
