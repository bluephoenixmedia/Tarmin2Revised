package com.bpm.minotaur.gamedata.history.war.army;

/**
 * A* over the {@link Terrain}, with its search arrays kept between calls (Living War A59): at the
 * densest load a fresh set per path would make tens of megabytes of garbage each sleep.
 */
final class Pathfinder {

    private static final int[] NONE = new int[0];

    private final Terrain terrain;
    private final int size;
    private final int radius;
    private final int[] cost;
    private final int[] from;
    /** Which search last wrote each chunk, so the arrays need no clearing. */
    private final int[] stamp;
    private int search;
    private int[] heapF = new int[1024];
    private int[] heapI = new int[1024];
    private int heapN;
    /** Searches run, for the path-call budget. */
    long calls;

    Pathfinder(Terrain terrain) {
        this.terrain = terrain;
        this.size = terrain.size;
        this.radius = terrain.radius;
        int n = size * size;
        cost = new int[n];
        from = new int[n];
        stamp = new int[n];
    }

    /**
     * The chunks to cross from one chunk to another, as indices, the start left out. Empty if they
     * are the same or no way exists. The target itself may be closed ground (a castle, a seat on a
     * road's end); nothing else on the way may be.
     */
    int[] find(int fromX, int fromY, int toX, int toY) {
        if (fromX == toX && fromY == toY) return NONE;
        if (!terrain.contains(fromX, fromY) || !terrain.contains(toX, toY)) return NONE;
        calls++;
        search++;
        heapN = 0;
        int s = terrain.index(fromX, fromY), t = terrain.index(toX, toY);
        cost[s] = 0;
        stamp[s] = search;
        from[s] = -1;
        push(0, s);
        boolean found = false;
        while (heapN > 0) {
            int i = pop();
            if (i == t) {
                found = true;
                break;
            }
            int x = i % size - radius, y = i / size - radius;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = x + dx, ny = y + dy;
                    if (Math.abs(nx) > radius || Math.abs(ny) > radius) continue;
                    int j = (ny + radius) * size + (nx + radius);
                    int step = terrain.turnsAt(j);
                    if (step == 0) {
                        if (j != t) continue;
                        step = Terrain.OFF_ROAD_TURNS;
                    }
                    int ng = cost[i] + step;
                    if (stamp[j] != search || ng < cost[j]) {
                        stamp[j] = search;
                        cost[j] = ng;
                        from[j] = i;
                        int h = Math.max(Math.abs(nx - toX), Math.abs(ny - toY)) * Terrain.ROAD_TURNS;
                        push(ng + h, j);
                    }
                }
            }
        }
        if (!found) return NONE;
        int len = 0;
        for (int i = t; i != s; i = from[i]) len++;
        int[] path = new int[len];
        for (int i = t, k = len - 1; i != s; i = from[i], k--) path[k] = i;
        return path;
    }

    private void push(int f, int i) {
        if (heapN == heapF.length) {
            heapF = java.util.Arrays.copyOf(heapF, heapN * 2);
            heapI = java.util.Arrays.copyOf(heapI, heapN * 2);
        }
        int c = heapN++;
        heapF[c] = f;
        heapI[c] = i;
        while (c > 0) {
            int p = (c - 1) >> 1;
            if (heapF[p] <= heapF[c]) break;
            swap(p, c);
            c = p;
        }
    }

    private int pop() {
        int top = heapI[0];
        heapN--;
        heapF[0] = heapF[heapN];
        heapI[0] = heapI[heapN];
        int c = 0;
        for (;;) {
            int l = 2 * c + 1, r = l + 1, m = c;
            if (l < heapN && heapF[l] < heapF[m]) m = l;
            if (r < heapN && heapF[r] < heapF[m]) m = r;
            if (m == c) return top;
            swap(m, c);
            c = m;
        }
    }

    private void swap(int a, int b) {
        int f = heapF[a];
        heapF[a] = heapF[b];
        heapF[b] = f;
        int i = heapI[a];
        heapI[a] = heapI[b];
        heapI[b] = i;
    }
}
