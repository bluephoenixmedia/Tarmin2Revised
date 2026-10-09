package com.bpm.minotaur.generation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.Ladder;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.lighting.LightSource;
import com.bpm.minotaur.lighting.LightingManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stamps an outpost shelter, or a seal site, into a surface chunk that is already generated.
 *
 * <p>Working on the finished maze, not inside each generator, keeps one shelter for
 * five biomes. The building is cleared out of whatever the generator put there,
 * walled, roofed (its tiles are home tiles), and joined to the nearest edge gate by a
 * cleared causeway, so it can always be walked into. It stands cold: its home tiles
 * are no sanctuary until the hearth is lit. See docs/DEsign/Requirements_ Shelter Roads.md.
 */
public final class ShelterBuilder {

    /** North row first. H is the hearth; the other letters are station slots as in the home shelter. */
    static final String[] LAYOUT = {
            "#######",
            "#L.A.R#",
            "#T...N#",
            "#F.H.C#",
            "#L..B.#",
            "#.....#",
            "###D###",
    };
    static final int SIZE = LAYOUT.length;

    public static final String SEAL_GATE_PROP = "seal_gate";

    /** Walls on all four edges: a solid block. Mirrors ChunkMeshBuilder.ALL_WALLS. */
    private static final int SOLID = 0b01010101;
    private static final int N = 0b01000000, E = 0b00000100, S = 0b00010000, W = 0b00000001;

    private ShelterBuilder() {
    }

    /**
     * Builds a cold outpost shelter in the middle of the chunk.
     *
     * @param biome picks the props that dress the doorstep
     */
    public static void buildOutpost(Maze maze, Biome biome, ItemDataManager itemDataManager, AssetManager assetManager) {
        int x0 = maze.getWidth() / 2 - SIZE / 2;
        int y0 = maze.getHeight() / 2 - SIZE / 2;
        clearArea(maze, x0 - 2, y0 - 2, x0 + SIZE + 1, y0 + SIZE + 1);

        boolean[][] solid = new boolean[SIZE][SIZE];
        List<GridPoint2> footprint = new ArrayList<>();
        GridPoint2 door = null;
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                char c = LAYOUT[row].charAt(col);
                int x = x0 + col;
                int y = y0 + SIZE - 1 - row;
                footprint.add(new GridPoint2(x, y));
                solid[row][col] = c == '#';
                if (c == 'D') door = new GridPoint2(x, y);
            }
        }
        // Wall data: the building's walls are solid blocks; everything else in the area is
        // recomputed from its neighbours so the cleared ring and the door read right.
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (solid[row][col]) maze.setTile(x0 + col, y0 + SIZE - 1 - row, SOLID);
            }
        }
        recomputeMasks(maze, x0 - 3, y0 - 3, x0 + SIZE + 2, y0 + SIZE + 2, door);

        maze.setHomeTiles(footprint);
        maze.setSanctuary(false);

        if (door != null) {
            Door d = new Door();
            d.setOrientation(Door.Orientation.NORTH_SOUTH);
            maze.addGameObject(d, door.x, door.y);
            GridPoint2 step = new GridPoint2(door.x, door.y - 1);
            maze.setShelterEntry(step);
            carveToNearestGate(maze, step);
            dressDoorstep(maze, biome, step, assetManager);
        }

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int x = x0 + col;
                int y = y0 + SIZE - 1 - row;
                switch (LAYOUT[row].charAt(col)) {
                    case 'L': maze.addStationSlot(ShelterAltar.Station.LANTERN, x, y); break;
                    case 'R': maze.addStationSlot(ShelterAltar.Station.ARCHIVE_LECTERN, x, y); break;
                    case 'T': maze.addStationSlot(ShelterAltar.Station.TRAINING_DUMMY, x, y); break;
                    case 'N': maze.addStationSlot(ShelterAltar.Station.CRAFTING_BENCH, x, y); break;
                    case 'F': maze.addStationSlot(ShelterAltar.Station.CAMPFIRE, x, y); break;
                    case 'C': maze.addStationSlot(ShelterAltar.Station.STASH_CHEST, x, y); break;
                    case 'B': maze.addStationSlot(ShelterAltar.Station.BED, x, y); break;
                    case 'A': maze.setAltarTile(new GridPoint2(x, y)); break;
                    case 'H':
                        maze.setHearthTile(new GridPoint2(x, y));
                        if (itemDataManager != null) {
                            Item hearth = itemDataManager.createItem(Item.ItemType.SHELTER_HEARTH_COLD, x, y,
                                    ItemColor.GRAY, assetManager);
                            if (hearth != null) maze.addItem(hearth);
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /**
     * Lights a cold shelter: the hearth burns, the home tiles become a sanctuary, and
     * the caller furnishes it with the unlocked stations.
     */
    public static void light(Maze maze, ItemDataManager itemDataManager, AssetManager assetManager) {
        GridPoint2 h = maze.getHearthTile();
        maze.setSanctuary(true);
        if (h == null) return;
        Item cold = maze.getItems().get(h);
        if (cold != null && cold.getType() == Item.ItemType.SHELTER_HEARTH_COLD) {
            maze.removeItem(cold);
        }
        if (itemDataManager != null && !maze.getItems().containsKey(h)) {
            Item lit = itemDataManager.createItem(Item.ItemType.SHELTER_HEARTH_LIT, h.x, h.y, ItemColor.TAN, assetManager);
            if (lit != null) maze.addItem(lit);
        }
        relightHearth(maze);
    }

    /** Lights are not saved with a chunk; a lit shelter's hearth needs its glow back on load. */
    public static void relightHearth(Maze maze) {
        GridPoint2 h = maze.getHearthTile();
        if (h == null || !maze.isSanctuary()) return;
        maze.removeLight("shelter_hearth");
        maze.addLight(new LightSource("shelter_hearth", h.x + 0.5f, h.y + 0.5f,
                LightingManager.COLOR_CAMPFIRE, 5.0f, 1.3f, LightSource.FlickerProfile.CAMPFIRE_FLICKER));
    }

    /** The sealed way down at the end of a seal road, lit in the road's colour. */
    public static void buildSealSite(Maze maze, Color tint, AssetManager assetManager) {
        int cx = maze.getWidth() / 2;
        int cy = maze.getHeight() / 2;
        clearArea(maze, cx - 2, cy - 2, cx + 2, cy + 2);
        recomputeMasks(maze, cx - 3, cy - 3, cx + 3, cy + 3, null);
        carveToNearestGate(maze, new GridPoint2(cx, cy - 1));
        Scenery gate = Scenery.fromProp(SEAL_GATE_PROP, cx, cy);
        if (gate != null) {
            loadTexture(gate, assetManager);
            maze.addScenery(gate);
        }
        maze.addLight(new LightSource("seal_site", cx + 0.5f, cy - 0.5f, tint, 5.0f, 1.2f,
                LightSource.FlickerProfile.LANTERN_BREATH));
    }

    public static boolean isSealGate(Scenery scenery) {
        return scenery != null && SEAL_GATE_PROP.equals(scenery.getPropId());
    }

    // ------------------------------------------------------------------

    /** Empties a rectangle (inclusive): floor everywhere, nothing standing, ladders moved out. */
    private static void clearArea(Maze maze, int minX, int minY, int maxX, int maxY) {
        List<Ladder> displaced = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (!inside(maze, x, y)) continue;
                clearTile(maze, x, y, displaced);
            }
        }
        for (Ladder l : displaced) {
            GridPoint2 at = freeTileOutside(maze, minX, minY, maxX, maxY);
            if (at != null) maze.addLadder(new Ladder(at.x, at.y, l.getType(), l.getStyle()));
        }
    }

    private static void clearTile(Maze maze, int x, int y, List<Ladder> displaced) {
        GridPoint2 p = new GridPoint2(x, y);
        if (maze.getGates().containsKey(p)) return; // never on the chunk edge, but never break a gate
        maze.setTile(x, y, 0);
        maze.getScenery().remove(p);
        maze.getItems().remove(p);
        maze.getMonsters().remove(p);
        maze.getGameObjects().remove(p);
        // A ladder is walkable, so a causeway (no list to move it to) leaves it standing.
        if (displaced != null) {
            Ladder l = maze.getLadders().remove(p);
            if (l != null) displaced.add(l);
        }
        if (maze.getLiquidManager() != null && maze.getLiquidAt(x, y) != LiquidType.NONE) {
            maze.getLiquidManager().setLiquidAt(x, y, LiquidType.NONE);
        }
    }

    /** Rebuilds the edge masks of every non-solid tile in the rectangle from its neighbours. */
    private static void recomputeMasks(Maze maze, int minX, int minY, int maxX, int maxY, GridPoint2 door) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (!inside(maze, x, y) || isSolid(maze, x, y)) continue;
                int high = maze.getWallDataAt(x, y) & ~0xFF; // cliff-variant bits outside our stamp
                int mask = 0;
                if (isSolid(maze, x, y + 1)) mask |= N;
                if (isSolid(maze, x + 1, y)) mask |= E;
                if (isSolid(maze, x, y - 1)) mask |= S;
                if (isSolid(maze, x - 1, y)) mask |= W;
                if (door != null) {
                    if (door.x == x && door.y == y + 1) mask |= N << 1;
                    if (door.x == x && door.y == y - 1) mask |= S << 1;
                }
                maze.setTile(x, y, mask | high);
            }
        }
    }

    private static boolean isSolid(Maze maze, int x, int y) {
        if (!inside(maze, x, y)) return true;
        return (maze.getWallDataAt(x, y) & SOLID) == SOLID;
    }

    /**
     * Clears a three-wide causeway from {@code from} to the nearest edge gate, so the
     * shelter is reachable whatever the generator stood between them. The route is
     * the shortest one around the building, ignoring everything else in the way.
     */
    private static void carveToNearestGate(Maze maze, GridPoint2 from) {
        Gate best = null;
        double bestD = Double.MAX_VALUE;
        for (Gate g : maze.getGates().values()) {
            double d = g.getPosition().dst(from.x, from.y);
            if (d < bestD) {
                bestD = d;
                best = g;
            }
        }
        if (best == null) return;
        // One tile in from the edge, so the gate itself is never touched.
        GridPoint2 goal = new GridPoint2(
                Math.max(1, Math.min(maze.getWidth() - 2, (int) best.getPosition().x)),
                Math.max(1, Math.min(maze.getHeight() - 2, (int) best.getPosition().y)));

        Set<GridPoint2> home = maze.getHomeTiles();
        java.util.Map<GridPoint2, GridPoint2> cameFrom = new java.util.HashMap<>();
        java.util.ArrayDeque<GridPoint2> queue = new java.util.ArrayDeque<>();
        cameFrom.put(from, from);
        queue.add(from);
        int[][] steps = {{0, -1}, {1, 0}, {-1, 0}, {0, 1}};
        while (!queue.isEmpty() && !cameFrom.containsKey(goal)) {
            GridPoint2 c = queue.poll();
            for (int[] st : steps) {
                GridPoint2 n = new GridPoint2(c.x + st[0], c.y + st[1]);
                if (n.x < 1 || n.y < 1 || n.x >= maze.getWidth() - 1 || n.y >= maze.getHeight() - 1) continue;
                if (cameFrom.containsKey(n) || nearHome(home, n)) continue;
                cameFrom.put(n, c);
                queue.add(n);
            }
        }
        if (!cameFrom.containsKey(goal)) return;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (GridPoint2 p = goal; ; p = cameFrom.get(p)) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int px = p.x + dx;
                    int py = p.y + dy;
                    if (px < 1 || py < 1 || px >= maze.getWidth() - 1 || py >= maze.getHeight() - 1) continue;
                    if (home.contains(new GridPoint2(px, py))) continue;
                    clearTile(maze, px, py, null);
                    minX = Math.min(minX, px);
                    minY = Math.min(minY, py);
                    maxX = Math.max(maxX, px);
                    maxY = Math.max(maxY, py);
                }
            }
            if (p.equals(from)) break;
        }
        GridPoint2 door = null;
        for (java.util.Map.Entry<GridPoint2, Object> e : maze.getGameObjects().entrySet()) {
            if (e.getValue() instanceof Door) door = e.getKey();
        }
        recomputeMasks(maze, minX - 1, minY - 1, maxX + 1, maxY + 1, door);
    }

    /** The causeway's centre line keeps one tile off the building, so its width never bites a wall. */
    private static boolean nearHome(Set<GridPoint2> home, GridPoint2 p) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (home.contains(new GridPoint2(p.x + dx, p.y + dy))) return true;
            }
        }
        return false;
    }

    /** Two props either side of the door, picked by biome, so each region's shelter reads as its own. */
    private static void dressDoorstep(Maze maze, Biome biome, GridPoint2 step, AssetManager assetManager) {
        String prop;
        switch (biome == null ? Biome.FOREST : biome) {
            case DESERT:    prop = "rubble_pile"; break;
            case LAKELANDS: prop = "twisted_root"; break;
            case TUNDRA:    prop = "brazier"; break;
            case BLIGHT:    prop = "war_banner"; break;
            default:        prop = "brazier"; break;
        }
        for (int side = -1; side <= 1; side += 2) {
            int x = step.x + side * 2;
            int y = step.y;
            if (!inside(maze, x, y) || isSolid(maze, x, y)) continue;
            GridPoint2 p = new GridPoint2(x, y);
            if (maze.getItems().containsKey(p) || maze.getScenery().containsKey(p)) continue;
            Scenery s = Scenery.fromProp(prop, x, y);
            if (s == null) continue;
            s.setImpassable(false);
            loadTexture(s, assetManager);
            maze.addScenery(s);
        }
    }

    private static GridPoint2 freeTileOutside(Maze maze, int minX, int minY, int maxX, int maxY) {
        for (int r = 1; r < maze.getWidth(); r++) {
            for (int y = minY - r; y <= maxY + r; y++) {
                for (int x = minX - r; x <= maxX + r; x++) {
                    if (x >= minX && x <= maxX && y >= minY && y <= maxY) continue;
                    if (x < 2 || y < 2 || x >= maze.getWidth() - 2 || y >= maze.getHeight() - 2) continue;
                    GridPoint2 p = new GridPoint2(x, y);
                    if (!maze.isPassable(x, y) || maze.getItems().containsKey(p) || maze.getLadders().containsKey(p)
                            || maze.getScenery().containsKey(p) || maze.getGates().containsKey(p)) continue;
                    return p;
                }
            }
        }
        return null;
    }

    private static boolean inside(Maze maze, int x, int y) {
        return x >= 0 && y >= 0 && x < maze.getWidth() && y < maze.getHeight();
    }

    private static void loadTexture(Scenery s, AssetManager assetManager) {
        String path = s.getTexturePath();
        if (path == null || assetManager == null || Gdx.files == null || !Gdx.files.internal(path).exists()) return;
        if (!assetManager.isLoaded(path)) {
            assetManager.load(path, Texture.class);
            assetManager.finishLoadingAsset(path);
        }
        s.setTexture(assetManager.get(path, Texture.class));
    }
}
