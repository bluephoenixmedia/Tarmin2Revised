package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.Rectangle;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;

import java.util.*;

/**
 * MazeGenerator: Hauberk-style generator tuned for fewer repetitive walls.
 *
 * Pipeline:
 *  1) Rooms (odd-aligned, no touching).
 *  2) Growing-tree maze fill.
 *  3) Region connectors (doors/openings) + extra loops.
 *  4) Dead-end JOINING (carve to nearby space) then light dead-end REMOVAL (optional).
 *  5) Wall thinning (open separators that split floors) for extra loops.
 *  6) Finalize to bitmask compatible with existing renderer.
 *
 * map codes: 0=floor, 1=wall, 2=door
 */
public class MazeGenerator {
    private final Random rng = new Random();

    private int[][] map;
    private int[][] regions;
    private int currentRegion;

    private final List<Rectangle> rooms = new ArrayList<>();

    // ---------- Tunables (dial for vibe) ----------
    private static final int NUM_ROOM_TRIES = 60;      // a few more attempts than default
    private static final int EXTRA_CONNECTOR_CHANCE = 2;  // 1-in-N kept as extra loop (more loops)
    private static final int ROOM_EXTRA_SIZE = 1;      // allow slightly larger rooms
    private static final int WINDING_PERCENT = 10;     // a touch straighter

    private static final boolean SPAWN_DOOR_OBJECTS = false; // avoid D+d duplication

    // Dead-end joining/removal
    private static final int JOIN_DEAD_END_PASSES = 2;     // carve from cul-de-sacs into nearby space
    private static final int JOIN_DEAD_END_CHANCE = 70;    // % chance to join a given cul-de-sac
    private static final int REMOVE_DEAD_END_PASSES = 1;   // optional light removal afterwards
    private static final int REMOVE_DEAD_END_CHANCE = 40;  // % chance to remove a given cul-de-sac

    // Wall thinning
    private static final int WALL_THINNING_PASSES = 2;
    private static final int WALL_THINNING_CHANCE = 45;    // stronger thinning to break long walls

    public MazeGenerator() {}
    public void setSeed(long seed) { rng.setSeed(seed); }

    public Maze generate(int level) {
        map = new int[Maze.MAZE_HEIGHT][Maze.MAZE_WIDTH];
        regions = new int[Maze.MAZE_HEIGHT][Maze.MAZE_WIDTH];
        for (int y=0; y<Maze.MAZE_HEIGHT; y++) {
            Arrays.fill(map[y], 1);
            Arrays.fill(regions[y], -1);
        }
        rooms.clear();
        currentRegion = -1;

        addRooms();
        // Maze fill
        for (int y=1; y<Maze.MAZE_HEIGHT; y+=2) {
            for (int x=1; x<Maze.MAZE_WIDTH; x+=2) {
                if (map[y][x] == 1) growMaze(x, y);
            }
        }
        connectRegions();
        joinDeadEnds();        // carve from cul-de-sacs to reduce redundant walls
        removeDeadEnds();      // optional light clean-up
        thinWalls();           // break up long straight separators

        int[][] wallData = finalizeWalls();
        Maze mz = new Maze(level, wallData);
        populateDoorObjects(mz);
        return mz;
    }

    /* -------------------- Rooms -------------------- */
    private void addRooms() {
        for (int i=0; i<NUM_ROOM_TRIES; i++) {
            int size = range(1, 3 + ROOM_EXTRA_SIZE) * 2 + 1; // odd
            int rectangularity = range(0, 1 + (size / 2)) * 2;
            int w = size, h = size;
            if (oneIn(2)) w += rectangularity; else h += rectangularity;

            if (w >= Maze.MAZE_WIDTH - 2 || h >= Maze.MAZE_HEIGHT - 2) continue;

            int x = range(0, (Maze.MAZE_WIDTH - w) / 2) * 2 + 1;
            int y = range(0, (Maze.MAZE_HEIGHT - h) / 2) * 2 + 1;

            Rectangle room = new Rectangle(x, y, w, h);

            boolean overlaps = false;
            for (Rectangle other : rooms) {
                if (touchesOrOverlaps(room, other)) { overlaps = true; break; }
            }
            if (overlaps) continue;

            rooms.add(room);
            startRegion();
            for (int yy=(int)room.y; yy<room.y+room.height; yy++) {
                for (int xx=(int)room.x; xx<room.x+room.width; xx++) {
                    carve(xx, yy);
                }
            }
        }
    }

    private boolean touchesOrOverlaps(Rectangle a, Rectangle b) {
        float ax0 = a.x - 1, ay0 = a.y - 1, ax1 = a.x + a.width, ay1 = a.y + a.height;
        float bx0 = b.x, by0 = b.y, bx1 = b.x + b.width - 1, by1 = b.y + b.height - 1;
        return !(ax1 < bx0 || bx1 < ax0 || ay1 < by0 || by1 < ay0);
    }

    /* -------------------- Maze (growing tree) -------------------- */
    private static final int[][] DIRS = { {0,-2}, {0,2}, {2,0}, {-2,0} }; // two-step moves

    private void growMaze(int sx, int sy) {
        Deque<int[]> cells = new ArrayDeque<>();
        int[] lastDir = null;
        startRegion();
        carve(sx, sy);
        cells.push(new int[]{sx, sy});

        while (!cells.isEmpty()) {
            int[] cell = cells.peek();
            int cx = cell[0], cy = cell[1];

            List<int[]> unmade = new ArrayList<>(4);
            for (int[] d : DIRS) if (canCarve(cx, cy, d)) unmade.add(d);

            if (!unmade.isEmpty()) {
                int[] dir;
                if (lastDir != null && containsDir(unmade, lastDir) && range(0,99) > WINDING_PERCENT) {
                    dir = lastDir;
                } else {
                    dir = unmade.get(range(0, unmade.size()-1));
                }
                // carve one step and into the next cell
                carve(cx + dir[0]/2, cy + dir[1]/2);
                carve(cx + dir[0],   cy + dir[1]);
                cells.push(new int[]{cx + dir[0], cy + dir[1]});
                lastDir = dir;
            } else {
                cells.pop();
                lastDir = null;
            }
        }
    }

    private boolean canCarve(int x, int y, int[] dir) {
        int nx = x + dir[0];
        int ny = y + dir[1];
        int bx = x + dir[0]/2;
        int by = y + dir[1]/2;
        if (!isValid(nx, ny) || !isValid(bx, by)) return false;
        return map[by][bx] == 1 && map[ny][nx] == 1;
    }

    /* -------------------- Connect regions -------------------- */
    private void connectRegions() {
        List<Point> connectors = new ArrayList<>();
        Map<Point, Set<Integer>> connToRegions = new HashMap<>();
        for (int y=1; y<Maze.MAZE_HEIGHT-1; y++) {
            for (int x=1; x<Maze.MAZE_WIDTH-1; x++) {
                if (map[y][x] != 1) continue;
                Set<Integer> set = new HashSet<>();
                if (regions[y-1][x] != -1) set.add(regions[y-1][x]);
                if (regions[y+1][x] != -1) set.add(regions[y+1][x]);
                if (regions[y][x-1] != -1) set.add(regions[y][x-1]);
                if (regions[y][x+1] != -1) set.add(regions[y][x+1]);
                if (set.size() >= 2) {
                    Point p = new Point(x,y);
                    connectors.add(p);
                    connToRegions.put(p, set);
                }
            }
        }
        if (connectors.isEmpty()) return;

        Map<Integer,Integer> merged = new HashMap<>();
        Set<Integer> open = new HashSet<>();
        for (int i=0; i<=currentRegion; i++) { merged.put(i, i); open.add(i); }

        while (open.size() > 1 && !connectors.isEmpty()) {
            Point connector = connectors.get(range(0, connectors.size()-1));
            addJunction(connector.x, connector.y);

            Set<Integer> around = new LinkedHashSet<>();
            for (int r : connToRegions.get(connector)) around.add(merged.get(r));
            Iterator<Integer> it = around.iterator();
            int dest = it.next();
            List<Integer> sources = new ArrayList<>();
            while (it.hasNext()) sources.add(it.next());

            for (int i=0; i<=currentRegion; i++) {
                int m = merged.get(i);
                if (sources.contains(m)) merged.put(i, dest);
            }
            open.removeAll(sources);

            connectors.removeIf(p -> {
                if (chebyshev(connector, p) < 2) return true;
                Set<Integer> regs = new HashSet<>();
                for (int r : connToRegions.get(p)) regs.add(merged.get(r));
                if (regs.size() > 1) return false;
                if (oneIn(EXTRA_CONNECTOR_CHANCE)) addJunction(p.x, p.y);
                return true;
            });
        }
    }

    private void addJunction(int x, int y) {
        if (oneIn(4)) {
            map[y][x] = oneIn(3) ? 0 : 2; // sometimes open, often a door
        } else {
            map[y][x] = 2;
        }
        regions[y][x] = currentRegion;
    }

    /* -------------------- Dead-end JOIN then light REMOVE -------------------- */
    private void joinDeadEnds() {
        for (int pass=0; pass<JOIN_DEAD_END_PASSES; pass++) {
            for (int y=1; y<Maze.MAZE_HEIGHT-1; y++) {
                for (int x=1; x<Maze.MAZE_WIDTH-1; x++) {
                    if (map[y][x] != 0) continue;
                    int openDir = singleOpenDir(x, y);
                    if (openDir == -1) continue; // not a cul-de-sac
                    if (rng.nextInt(100) >= JOIN_DEAD_END_CHANCE) continue;

                    // Try to punch to a nearby floor: straight 2, or side step
                    int[][] dirs4 = { {0,-1},{0,1},{1,0},{-1,0} };
                    // Prioritize directions other than the current exit to encourage loops
                    List<int[]> candidates = new ArrayList<>();
                    for (int i=0;i<4;i++) {
                        if (i == openDir) continue;
                        candidates.add(dirs4[i]);
                    }
                    Collections.shuffle(candidates, rng);

                    boolean carved = false;
                    for (int[] d : candidates) {
                        int wx = x + d[0], wy = y + d[1];      // wall to punch
                        int fx = x + d[0]*2, fy = y + d[1]*2;  // floor beyond
                        if (!isValid(fx, fy)) continue;
                        if (map[wy][wx] == 1 && map[fy][fx] == 0) {
                            map[wy][wx] = 0;
                            map[fy][fx] = 0;
                            carved = true;
                            break;
                        }
                    }
                    // As a fallback, try straight ahead through the dead-end's back wall
                    if (!carved) {
                        int[] exit = dirByIndex(openDir);
                        int bx = x - exit[0], by = y - exit[1];
                        int fx = x - exit[0]*2, fy = y - exit[1]*2;
                        if (isValid(fx, fy) && map[by][bx] == 1 && map[fy][fx] == 0) {
                            map[by][bx] = 0;
                            map[fy][fx] = 0;
                        }
                    }
                }
            }
        }
    }

    private void removeDeadEnds() {
        for (int pass=0; pass<REMOVE_DEAD_END_PASSES; pass++) {
            boolean any = false;
            for (int y=1; y<Maze.MAZE_HEIGHT-1; y++) {
                for (int x=1; x<Maze.MAZE_WIDTH-1; x++) {
                    if (map[y][x] != 0) continue;
                    int open = countOpenNeighbors(x, y);
                    if (open == 1 && rng.nextInt(100) < REMOVE_DEAD_END_CHANCE) {
                        map[y][x] = 1;
                        any = true;
                    }
                }
            }
            if (!any) break;
        }
    }

    private int singleOpenDir(int x, int y) {
        int open = 0, idx = -1;
        int[][] dirs4 = { {0,-1},{0,1},{1,0},{-1,0} };
        for (int i=0;i<4;i++) {
            int nx = x + dirs4[i][0], ny = y + dirs4[i][1];
            if (map[ny][nx] != 1) { open++; idx = i; }
        }
        return open == 1 ? idx : -1;
    }
    private int countOpenNeighbors(int x, int y) {
        int c=0;
        if (map[y-1][x] != 1) c++;
        if (map[y+1][x] != 1) c++;
        if (map[y][x-1] != 1) c++;
        if (map[y][x+1] != 1) c++;
        return c;
    }
    private int[] dirByIndex(int idx){
        switch(idx){
            case 0: return new int[]{0,-1};
            case 1: return new int[]{0,1};
            case 2: return new int[]{1,0};
            default:return new int[]{-1,0};
        }
    }

    /* -------------------- Wall thinning -------------------- */
    private void thinWalls() {
        for (int pass=0; pass<WALL_THINNING_PASSES; pass++) {
            for (int y=1; y<Maze.MAZE_HEIGHT-1; y++) {
                for (int x=1; x<Maze.MAZE_WIDTH-1; x++) {
                    if (map[y][x] != 1) continue;
                    boolean horiz = map[y][x-1] == 0 && map[y][x+1] == 0;
                    boolean vert  = map[y-1][x] == 0 && map[y+1][x] == 0;
                    if ((horiz || vert) && rng.nextInt(100) < WALL_THINNING_CHANCE) {
                        map[y][x] = 0;
                    }
                }
            }
        }
    }

    /* -------------------- Finalization -------------------- */
    private int[][] finalizeWalls() {
        int[][] out = new int[Maze.MAZE_HEIGHT][Maze.MAZE_WIDTH];
        for (int y=0; y<Maze.MAZE_HEIGHT; y++) {
            for (int x=0; x<Maze.MAZE_WIDTH; x++) {
                if (map[y][x] == 0) {
                    out[y][x] = 0; // floor
                } else {
                    int mask = 0;
                    if (hasWallOrBoundary(x, y-1)) mask |= 0b00010000; // top neighbor
                    if (hasWallOrBoundary(x, y+1)) mask |= 0b01000000; // bottom neighbor
                    if (hasWallOrBoundary(x-1, y)) mask |= 0b00000001; // left neighbor
                    if (hasWallOrBoundary(x+1, y)) mask |= 0b00000100; // right neighbor
                    if (map[y][x] == 2) {
                        if (hasFloor(x, y-1)) mask |= 0b00100000; // door top
                        if (hasFloor(x, y+1)) mask |= 0b10000000; // door bottom
                        if (hasFloor(x-1, y)) mask |= 0b00000010; // door left
                        if (hasFloor(x+1, y)) mask |= 0b00001000; // door right
                    }
                    out[y][x] = mask;
                }
            }
        }
        return out;
    }

    private boolean hasWallOrBoundary(int x, int y) { return !isValid(x, y) || map[y][x] >= 1; }
    private boolean hasFloor(int x, int y) { return isValid(x, y) && map[y][x] == 0; }

    private void populateDoorObjects(Maze mz) {
        if (!SPAWN_DOOR_OBJECTS) return;
        for (int y=0; y<Maze.MAZE_HEIGHT; y++) {
            for (int x=0; x<Maze.MAZE_WIDTH; x++) {
                if (map[y][x] != 2) continue;
                if (hasFloor(x+1, y)) mz.addGameObject(new Door(), x+1, y);
                else if (hasFloor(x-1, y)) mz.addGameObject(new Door(), x-1, y);
                else if (hasFloor(x, y+1)) mz.addGameObject(new Door(), x, y+1);
                else if (hasFloor(x, y-1)) mz.addGameObject(new Door(), x, y-1);
            }
        }
    }

    /* -------------------- API helpers -------------------- */
    public int getPlayerStartX() {
        if (!rooms.isEmpty()) {
            Rectangle r = rooms.get(0);
            return (int)(r.x + r.width / 2);
        }
        for (int y=1; y<Maze.MAZE_HEIGHT; y++) {
            for (int x=1; x<Maze.MAZE_WIDTH; x++) {
                if (map[y][x]==0) return x;
            }
        }
        return 1;
    }
    public int getPlayerStartY() {
        if (!rooms.isEmpty()) {
            Rectangle r = rooms.get(0);
            return (int)(r.y + r.height / 2);
        }
        for (int y=1; y<Maze.MAZE_HEIGHT; y++) {
            for (int x=1; x<Maze.MAZE_WIDTH; x++) {
                if (map[y][x]==0) return y;
            }
        }
        return 1;
    }
    public int getRoomCount() { return rooms.size(); }

    /* -------------------- Low-level -------------------- */
    private void startRegion() { currentRegion++; }
    private void carve(int x, int y) {
        if (isValid(x,y)) { map[y][x] = 0; regions[y][x] = currentRegion; }
    }
    private boolean isValid(int x, int y) { return x>=0 && x<Maze.MAZE_WIDTH && y>=0 && y<Maze.MAZE_HEIGHT; }
    private int range(int lo, int hi) { return lo + rng.nextInt(Math.max(1, hi - lo + 1)); }
    private boolean oneIn(int n) { return rng.nextInt(n) == 0; }
    private boolean containsDir(List<int[]> list, int[] dir) {
        for (int[] d : list) if (d[0]==dir[0] && d[1]==dir[1]) return true;
        return false;
    }
    private int chebyshev(Point a, Point b) { return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y)); }

    private static class Point {
        final int x,y; Point(int x,int y){this.x=x;this.y=y;}
        @Override public boolean equals(Object o){ if(!(o instanceof Point)) return false; Point p=(Point)o; return x==p.x&&y==p.y; }
        @Override public int hashCode(){ return (x*73856093) ^ (y*19349663); }
    }
}
