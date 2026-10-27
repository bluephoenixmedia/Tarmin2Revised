package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Ladder;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** One chunk's explored layout, resolved for the map's chunk view. */
final class ChunkTiles {

    int width;
    int height;
    int[][] wallData;
    byte[][] explorationState;
    final Map<GridPoint2, Ladder.LadderType> ladders = new HashMap<>();
    final Map<GridPoint2, TextureRegion> itemIcons = new HashMap<>();
    final Map<GridPoint2, String> events = new HashMap<>();
    final Set<GridPoint2> homeTiles = new HashSet<>();

    /** Tiles that hold an item, whether or not its icon could be resolved. */
    final Set<GridPoint2> itemTiles = new HashSet<>();
    private Boolean anySeen;

    boolean isSeen(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return false;
        if (explorationState == null) return true; // a chunk saved without exploration (a sanctuary) is all known
        return explorationState[y][x] == Maze.VISIBILITY_SEEN;
    }

    /** The tiles one block of an n x n split covers along a side: [from, to). */
    private static int blockFrom(int b, int size, int n) {
        return (b * size) / n;
    }

    private static int blockTo(int b, int size, int n) {
        return Math.max(blockFrom(b, size, n) + 1, ((b + 1) * size) / n);
    }

    /** Whether any tile of block (bx, by) of an 8 x 8 split has been seen. */
    boolean isBlockSeen(int bx, int by) {
        return blockSeenFraction(bx, by, 8) > 0f;
    }

    /** The share of block (bx, by) of an n x n split that has been seen, 0..1. */
    float blockSeenFraction(int bx, int by, int n) {
        if (width <= 0 || height <= 0) return 0f;
        if (explorationState == null) return 1f;
        int x0 = blockFrom(bx, width, n), x1 = Math.min(width, blockTo(bx, width, n));
        int y0 = blockFrom(by, height, n), y1 = Math.min(height, blockTo(by, height, n));
        int seen = 0;
        int total = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                total++;
                if (explorationState[y][x] == Maze.VISIBILITY_SEEN) seen++;
            }
        }
        return total == 0 ? 0f : seen / (float) total;
    }

    /** True if any tile has been seen; a chunk with no exploration state counts as seen. */
    boolean hasAnySeenTile() {
        if (anySeen == null) {
            boolean any = explorationState == null && width > 0;
            if (explorationState != null) {
                outer:
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        if (explorationState[y][x] == Maze.VISIBILITY_SEEN) {
                            any = true;
                            break outer;
                        }
                    }
                }
            }
            anySeen = any;
        }
        return anySeen;
    }

    int wallAt(int x, int y) {
        if (wallData == null || x < 0 || x >= width || y < 0 || y >= height) return 0;
        return wallData[y][x];
    }

    /** A solid block rather than a floor with walls on some sides; the same test as {@code Maze.isWall}. */
    boolean isSolid(int x, int y) {
        int w = wallAt(x, y);
        return w == 1 || (w & 0b1111) == 0b1111 || (w & 0b01010101) == 0b01010101;
    }

    /** No wall on an edge. */
    static final int EDGE_NONE = 0;
    static final int EDGE_WALL = 1;
    static final int EDGE_DOOR = 2;

    /**
     * The edge along {@code y = ey} under tile column {@code tx}: between tile (tx, ey - 1) to
     * its south and (tx, ey) to its north. Walls are stored on both tiles of an edge, or on one.
     */
    int horizontalEdge(int tx, int ey) {
        int north = ey < height ? wallAt(tx, ey) : 0;
        int south = ey > 0 ? wallAt(tx, ey - 1) : 0;
        if ((north & DOOR_S) != 0 || (south & DOOR_N) != 0) return EDGE_DOOR;
        if ((north & WALL_S) != 0 || (south & WALL_N) != 0) return EDGE_WALL;
        return EDGE_NONE;
    }

    /** The edge along {@code x = ex} beside tile row {@code ty}: between (ex - 1, ty) and (ex, ty). */
    int verticalEdge(int ex, int ty) {
        int east = ex < width ? wallAt(ex, ty) : 0;
        int west = ex > 0 ? wallAt(ex - 1, ty) : 0;
        if ((east & DOOR_W) != 0 || (west & DOOR_E) != 0) return EDGE_DOOR;
        if ((east & WALL_W) != 0 || (west & WALL_E) != 0) return EDGE_WALL;
        return EDGE_NONE;
    }

    // Bit layout of Direction.getWallMask() and the door bits beside each wall bit.
    private static final int WALL_N = 0b01000000, DOOR_N = 0b10000000;
    private static final int WALL_S = 0b00010000, DOOR_S = 0b00100000;
    private static final int WALL_E = 0b00000100, DOOR_E = 0b00001000;
    private static final int WALL_W = 0b00000001, DOOR_W = 0b00000010;

    /** Checks if the majority of tiles in this 8x8 block's footprint are solid. */
    boolean isBlockSolid(int bx, int by) {
        if (width <= 0 || height <= 0 || wallData == null) return false;
        if (width <= 8 && height <= 8) return isSolid(bx, by);

        int x0 = (bx * width) / 8;
        int x1 = Math.max(x0 + 1, ((bx + 1) * width) / 8);
        int y0 = (by * height) / 8;
        int y1 = Math.max(y0 + 1, ((by + 1) * height) / 8);
        int total = (x1 - x0) * (y1 - y0);
        int solidCount = 0;
        for (int y = y0; y < y1 && y < height; y++) {
            for (int x = x0; x < x1 && x < width; x++) {
                if (isSolid(x, y)) solidCount++;
            }
        }
        return solidCount * 2 >= total;
    }

    /** An unexplored opening or passage exiting from an explored floor tile. */
    static final class OpenLead {
        final com.bpm.minotaur.gamedata.Direction direction;
        final GridPoint2 tile;
        final int width;

        OpenLead(com.bpm.minotaur.gamedata.Direction direction, GridPoint2 tile, int width) {
            this.direction = direction;
            this.tile = new GridPoint2(tile);
            this.width = width;
        }

        public com.bpm.minotaur.gamedata.Direction getDirection() { return direction; }
        public GridPoint2 getTile() { return new GridPoint2(tile); }
        public int getWidth() { return width; }
    }

    /** Finds every open lead from explored floor tiles into unexplored fog or chunk boundaries. */
    java.util.List<OpenLead> findOpenLeads() {
        java.util.List<OpenLead> leads = new java.util.ArrayList<>();
        if (width <= 0 || height <= 0 || explorationState == null) return leads;

        for (com.bpm.minotaur.gamedata.Direction d : com.bpm.minotaur.gamedata.Direction.values()) {
            boolean[][] visited = new boolean[height][width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (visited[y][x] || !isSeen(x, y) || isSolid(x, y)) continue;
                    int mask = wallAt(x, y);
                    if ((mask & d.getWallMask()) != 0) continue;
                    int nx = x + (int) d.getVector().x;
                    int ny = y + (int) d.getVector().y;
                    if (nx >= 0 && nx < width && ny >= 0 && ny < height && isSeen(nx, ny)) continue;

                    // Trace contiguous run along the perpendicular axis
                    int count = 1;
                    visited[y][x] = true;
                    int minX = x, maxX = x, minY = y, maxY = y;

                    if (d == com.bpm.minotaur.gamedata.Direction.NORTH || d == com.bpm.minotaur.gamedata.Direction.SOUTH) {
                        for (int rx = x + 1; rx < width; rx++) {
                            if (!isSeen(rx, y) || isSolid(rx, y) || (wallAt(rx, y) & d.getWallMask()) != 0) break;
                            int rnx = rx + (int) d.getVector().x;
                            int rny = y + (int) d.getVector().y;
                            if (rnx >= 0 && rnx < width && rny >= 0 && rny < height && isSeen(rnx, rny)) break;
                            visited[y][rx] = true;
                            maxX = rx;
                            count++;
                        }
                    } else {
                        for (int ry = y + 1; ry < height; ry++) {
                            if (!isSeen(x, ry) || isSolid(x, ry) || (wallAt(x, ry) & d.getWallMask()) != 0) break;
                            int rnx = x + (int) d.getVector().x;
                            int rny = ry + (int) d.getVector().y;
                            if (rnx >= 0 && rnx < width && rny >= 0 && rny < height && isSeen(rnx, rny)) break;
                            visited[ry][x] = true;
                            maxY = ry;
                            count++;
                        }
                    }
                    leads.add(new OpenLead(d, new GridPoint2((minX + maxX) / 2, (minY + maxY) / 2), count));
                }
            }
        }
        return leads;
    }

    /** Formats open leads into natural summary lines matching the design canvas. */
    java.util.List<String> describeOpenLeads() {
        java.util.List<OpenLead> leads = findOpenLeads();
        if (leads.isEmpty()) return java.util.Collections.emptyList();
        java.util.Map<com.bpm.minotaur.gamedata.Direction, Integer> counts = new java.util.EnumMap<>(com.bpm.minotaur.gamedata.Direction.class);
        for (OpenLead l : leads) {
            counts.put(l.direction, counts.getOrDefault(l.direction, 0) + 1);
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (com.bpm.minotaur.gamedata.Direction d : com.bpm.minotaur.gamedata.Direction.values()) {
            int count = counts.getOrDefault(d, 0);
            if (count == 0) continue;
            String dir = d.name().toLowerCase(java.util.Locale.ROOT);
            if (count == 1) {
                String cap = Character.toUpperCase(dir.charAt(0)) + dir.substring(1);
                lines.add(cap + " corridor unexplored");
            } else {
                lines.add(count + " passages " + dir + " unexplored");
            }
        }
        return lines;
    }

    /** The live chunk the player stands in: its exploration is newer than its save. */
    static ChunkTiles fromMaze(Maze maze) {
        ChunkTiles v = new ChunkTiles();
        v.width = maze.getWidth();
        v.height = maze.getHeight();
        v.wallData = maze.getWallData();
        v.explorationState = maze.getExplorationState();
        v.homeTiles.addAll(maze.getHomeTiles());
        for (Map.Entry<GridPoint2, Ladder> e : maze.getLadders().entrySet()) {
            v.ladders.put(e.getKey(), e.getValue().getType());
        }
        for (Map.Entry<GridPoint2, Item> e : maze.getItems().entrySet()) {
            v.itemTiles.add(new GridPoint2(e.getKey()));
            TextureRegion region = e.getValue().getTextureRegion();
            if (region != null) v.itemIcons.put(e.getKey(), region);
        }
        v.events.putAll(maze.getEventTriggers());
        return v;
    }

    /** A saved chunk, or null if it has no layout. */
    static ChunkTiles fromChunkData(ChunkData data, ItemDataManager itemDataManager, AssetManager assetManager) {
        if (data == null || data.wallData == null) return null;
        ChunkTiles v = new ChunkTiles();
        v.wallData = data.wallData;
        v.height = v.wallData.length;
        v.width = v.height > 0 ? v.wallData[0].length : 0;
        v.explorationState = data.explorationState;
        if (data.homeTiles != null) v.homeTiles.addAll(data.homeTiles);
        if (data.ladders != null) {
            for (ChunkData.LadderData ld : data.ladders) v.ladders.put(new GridPoint2(ld.x, ld.y), ld.type);
        }
        if (data.events != null) {
            for (ChunkData.EventData ed : data.events) v.events.put(new GridPoint2(ed.x, ed.y), ed.eventId);
        }
        if (data.items != null) {
            for (ChunkData.ItemData id : data.items) v.itemTiles.add(new GridPoint2(id.x, id.y));
        }
        if (data.items != null && itemDataManager != null && assetManager != null) {
            for (ChunkData.ItemData id : data.items) {
                try {
                    Item item = new Item(id.type, id.x, id.y, id.color, itemDataManager, assetManager);
                    TextureRegion region = item.getTextureRegion();
                    if (region != null) v.itemIcons.put(new GridPoint2(id.x, id.y), region);
                } catch (Exception ignored) {
                    // An unresolvable icon: the tile still draws, just without its item.
                }
            }
        }
        return v;
    }
}
