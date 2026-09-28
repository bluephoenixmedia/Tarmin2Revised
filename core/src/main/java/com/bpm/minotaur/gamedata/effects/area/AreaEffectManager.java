package com.bpm.minotaur.gamedata.effects.area;

import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;

/**
 * Lingering effects that occupy tiles for a while: clouds, in practice.
 *
 * <p>One of these belongs to a {@link Maze}, so a cloud lives in the chunk it was cast in and
 * dies with it. Nothing here is serialised -- {@code WorldSaveData} is a seed and a dozen
 * scalars, the world is regenerated rather than stored, and a ten-turn cloud is not worth
 * inventing a tile-state save format for. The consequence to know about: saving and reloading
 * inside a cloud loses it, and so does walking out of the chunk and back.
 *
 * <p>Deliberately free of rendering and of libGDX's graphics packages, so it can be tested
 * headless. {@link #forEachActive} is how a renderer gets at it.
 */
public final class AreaEffectManager {

    /**
     * Wind speed at which a cloud starts to come apart, in the units
     * {@code WeatherManager.getWindVector()} produces.
     *
     * <p>That vector runs from zero in clear air through roughly 4.8 to 8.7 in a storm to about
     * 22 in a tornado, so this threshold is 5e's "a wind of moderate or greater speed": storms
     * and worse tear fog apart, and underground, where there is no weather, a cloud holds for
     * its full duration.
     */
    public static final float DISPERSING_WIND_SPEED = 4.0f;

    private final int width;
    private final int height;

    /** 0 means empty; otherwise the {@link AreaEffectType} ordinal plus one. */
    private final byte[][] types;
    private final short[][] turns;

    private int activeTiles;

    public AreaEffectManager(int width, int height) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.types = new byte[this.height][this.width];
        this.turns = new short[this.height][this.width];
    }

    // --- Casting ---------------------------------------------------------

    /**
     * Spreads an effect out from a tile and returns how many tiles it took.
     *
     * <p>A breadth-first flood fill, not a circle: {@code radius} is a number of steps through
     * tiles a creature could walk, so a cloud cast at a doorway fills the room and turns the
     * corner, and never occupies solid stone. Casting into a wall fills nothing.
     *
     * <p>Recasting over an existing cloud refreshes the overlap to whichever duration is longer
     * and unions the footprints. Durations never add: otherwise a wall of fog could be held
     * open indefinitely for three mana a turn.
     */
    public int apply(Maze maze, int originX, int originY, AreaEffectType type, int radius, int turnsToLast) {
        if (maze == null || type == null || turnsToLast <= 0 || !inBounds(originX, originY)) {
            return 0;
        }
        if (isSolid(maze, originX, originY)) {
            return 0;
        }

        // Breadth-first over a bounded neighbourhood. The frontier can never exceed the tiles
        // within `radius` steps, so a plain array queue is enough and nothing allocates per
        // tile.
        int span = radius * 2 + 1;
        int[] queueX = new int[span * span];
        int[] queueY = new int[span * span];
        int[] queueDepth = new int[span * span];
        boolean[][] seen = new boolean[height][width];

        int head = 0;
        int tail = 0;
        queueX[tail] = originX;
        queueY[tail] = originY;
        queueDepth[tail] = 0;
        tail++;
        seen[originY][originX] = true;

        int filled = 0;
        while (head < tail) {
            int x = queueX[head];
            int y = queueY[head];
            int depth = queueDepth[head];
            head++;

            if (set(x, y, type, turnsToLast)) {
                filled++;
            }

            if (depth >= radius) {
                continue;
            }
            for (Direction dir : Direction.values()) {
                int nx = x + (int) dir.getVector().x;
                int ny = y + (int) dir.getVector().y;
                if (!inBounds(nx, ny) || seen[ny][nx]) {
                    continue;
                }
                // A cloud flows through the openings a creature would walk through, so a
                // closed door or a directional wall stops it exactly where it stops you.
                if (maze.isWallBlocking(x, y, dir) || isSolid(maze, nx, ny)) {
                    continue;
                }
                seen[ny][nx] = true;
                queueX[tail] = nx;
                queueY[tail] = ny;
                queueDepth[tail] = depth + 1;
                tail++;
            }
        }
        return filled;
    }

    /** Writes one tile, refreshing rather than shortening. Returns true if the tile is newly held. */
    private boolean set(int x, int y, AreaEffectType type, int turnsToLast) {
        byte code = (byte) (type.ordinal() + 1);
        boolean wasEmpty = types[y][x] == 0;
        if (wasEmpty) {
            activeTiles++;
        }
        types[y][x] = code;
        if (turnsToLast > turns[y][x]) {
            turns[y][x] = (short) turnsToLast;
        }
        return wasEmpty;
    }

    // --- The turn --------------------------------------------------------

    /**
     * Ages every cloud by one world turn.
     *
     * <p>{@code windSpeed} is the magnitude of {@code WeatherManager.getWindVector()}. At or
     * above {@link #DISPERSING_WIND_SPEED} what is left is halved before the ordinary
     * decrement, so a cloud that would have lasted ten turns is gone in three.
     *
     * <p>Exposure is decided per tile, not per player: a cloud out under the sky keeps
     * dispersing whether or not the caster has since stepped into a doorway, and a cloud
     * indoors or underground holds even in a gale. {@code maze} may be null, in which case
     * nothing is treated as exposed.
     */
    public void tick(Maze maze, float windSpeed) {
        if (activeTiles == 0) {
            return;
        }
        boolean windy = windSpeed >= DISPERSING_WIND_SPEED;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (types[y][x] == 0) {
                    continue;
                }
                boolean dispersing = windy && maze != null && !maze.isIndoors(x, y);
                int left = turns[y][x];
                if (dispersing) {
                    left /= 2;
                }
                left--;
                if (left <= 0) {
                    types[y][x] = 0;
                    turns[y][x] = 0;
                    activeTiles--;
                } else {
                    turns[y][x] = (short) left;
                }
            }
        }
    }

    /** Drops every effect. For leaving a level, or a debug key. */
    public void clear() {
        if (activeTiles == 0) {
            return;
        }
        for (int y = 0; y < height; y++) {
            java.util.Arrays.fill(types[y], (byte) 0);
            java.util.Arrays.fill(turns[y], (short) 0);
        }
        activeTiles = 0;
    }

    // --- Reading ---------------------------------------------------------

    public boolean isActive(int x, int y) {
        return inBounds(x, y) && types[y][x] != 0;
    }

    /** The effect on a tile, or null. */
    public AreaEffectType typeAt(int x, int y) {
        if (!isActive(x, y)) {
            return null;
        }
        return AreaEffectType.values()[types[y][x] - 1];
    }

    public int turnsRemainingAt(int x, int y) {
        return inBounds(x, y) ? turns[y][x] : 0;
    }

    public boolean isObscured(int x, int y) {
        return typeAt(x, y) == AreaEffectType.OBSCURING;
    }

    /** How many tiles hold an effect. Zero means {@link #tick} and the renderer can bail early. */
    public int activeTileCount() {
        return activeTiles;
    }

    // --- Sight -----------------------------------------------------------

    /**
     * Whether obscuring fog lies on the line between two tiles.
     *
     * <p>Both endpoints count: fog you are standing in is fog you cannot see out of, and fog
     * someone is standing in hides them. Adjacent tiles are the exception -- a monster in the
     * same cloud one square away has not lost you, and a swing that can land must have a target
     * to land on.
     *
     * <p>The traversal is the same Bresenham walk {@code MonsterAiManager.checkLineOfSight}
     * uses for walls, so fog and stone block along identical lines.
     */
    public boolean blocksSight(int x0, int y0, int x1, int y1) {
        if (activeTiles == 0) {
            return false;
        }
        if (Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) <= 1) {
            return false;
        }

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int cx = x0;
        int cy = y0;
        while (true) {
            if (isObscured(cx, cy)) {
                return true;
            }
            if (cx == x1 && cy == y1) {
                return false;
            }
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                cx += sx;
            }
            if (e2 < dx) {
                err += dx;
                cy += sy;
            }
        }
    }

    // --- Plumbing --------------------------------------------------------

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** A tile fog cannot occupy: solid stone, or off the map. */
    private boolean isSolid(Maze maze, int x, int y) {
        return !inBounds(x, y) || maze.isWall(x, y);
    }
}
