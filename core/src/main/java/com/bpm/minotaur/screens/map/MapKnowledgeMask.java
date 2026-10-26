package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.map.MapModel;
import com.bpm.minotaur.generation.Biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The world-space knowledge mask the fog-of-war shader cuts its holes with.
 *
 * <p>{@link #R} pixels per chunk side, covering the known area plus {@link #PAD} chunks of fog
 * on every side, so the mask grows with the explored world instead of stopping at a fixed
 * radius. Red is the blurred knowledge value (0 = fog, 0.42 = glimpsed, 1 = seen ground); green
 * is the frontier wisp strength {@code 4.4 * v * (1 - v)}. Built per chunk, not per pixel, and
 * only when knowledge or the floor changes.
 */
public final class MapKnowledgeMask implements Disposable {

    /** Mask pixels per chunk side: the prototype's one pixel per tile of an 8x8 chunk. */
    public static final int R = 8;
    /** Chunks of fog kept around the known area, as the prototype's board padding. */
    public static final int PAD = 7;
    /** An explored chunk's unseen ground: lighter than the prototype's 0.5, which buried real chunks. */
    public static final float UNSEEN_EXPLORED = 0.72f;
    /** Widest area the mask will cover, in chunks; past this the known area is clipped. */
    public static final int MAX_SPAN = 192;

    /** What the map knows, as the renderer asks for it. Chunk coordinates are the game's (north = +y). */
    public interface KnowledgeProvider {
        enum State { UNKNOWN, GLIMPSED, VISITED }

        /** Knowledge of a surface chunk. */
        State getChunkState(int cx, int cy);

        boolean isTileSeen(int cx, int cy, int lx, int ly);

        /** Every surface chunk that is not {@link State#UNKNOWN}. */
        default Collection<GridPoint2> knownChunks() {
            List<GridPoint2> out = new ArrayList<>();
            int r = IsometricDioramaRenderer.WORLD_RADIUS;
            for (int y = -r; y <= r; y++) {
                for (int x = -r; x <= r; x++) {
                    if (getChunkState(x, y) != State.UNKNOWN) out.add(new GridPoint2(x, y));
                }
            }
            return out;
        }

        /** Knowledge of a chunk on the stratum being viewed; UNKNOWN on the surface. */
        default State getCaveState(int cx, int cy) {
            return State.UNKNOWN;
        }

        /** Every chunk of the viewed stratum that is not UNKNOWN. */
        default Collection<GridPoint2> caveChunks() {
            return Collections.emptyList();
        }

        default Biome getBiome(int cx, int cy) {
            return Biome.MAZE;
        }

        /** A surface chunk's layout, or null if it has none. */
        default ChunkTiles getChunkTiles(int cx, int cy) {
            return null;
        }

        /** A chunk's layout on the stratum being viewed, or null. */
        default ChunkTiles getCavernTiles(int cx, int cy) {
            return null;
        }

        default MapModel.ShelterMark getShelter(int cx, int cy) {
            return null;
        }

        default boolean hasSeal(int cx, int cy) {
            return false;
        }

        default boolean isSealWon(int cx, int cy) {
            return false;
        }

        default boolean hasReturnPortal(int cx, int cy) {
            return false;
        }

        default boolean hasLadder(int cx, int cy) {
            return false;
        }

        default int getGraveCount(int cx, int cy) {
            return 0;
        }

        default int getChestCount(int cx, int cy) {
            return 0;
        }

        /** The chunk the player stands in, on the floor being viewed; null when elsewhere. */
        default GridPoint2 getPlayerChunk() {
            return null;
        }

        default boolean hasPlayer(int cx, int cy) {
            GridPoint2 p = getPlayerChunk();
            return p != null && p.x == cx && p.y == cy;
        }

        default float getPlayerU(int cx, int cy) {
            return 0.5f;
        }

        default float getPlayerV(int cx, int cy) {
            return 0.5f;
        }

        default float getPlayerFaceX(int cx, int cy) {
            return 0f;
        }

        default float getPlayerFaceY(int cx, int cy) {
            return 1f;
        }

        default List<RoadInfo> getRoadSegments() {
            return Collections.emptyList();
        }

        default GridPoint2 getSelectedChunk() {
            return null;
        }

        default GridPoint2 getWaypointChunk() {
            return null;
        }

        default GridPoint2 getSuggestionChunk() {
            return null;
        }

        default com.badlogic.gdx.math.Vector2 getCastleSite() {
            return null;
        }

        default boolean isCastleKnown() {
            return false;
        }

        default String getChunkPin(int cx, int cy) {
            return null;
        }

        /** Chunks that carry a pin, known ground or not. */
        default Collection<GridPoint2> pinnedChunks() {
            return Collections.emptyList();
        }

        default int getStratumDepth(int cx, int cy) {
            return 0;
        }

        default int getFloor() {
            return 1;
        }

        default List<MapSurface.FrontMark> getFronts() {
            return Collections.emptyList();
        }

        default List<MapSurface.WarSign> getWarSigns() {
            return Collections.emptyList();
        }

        default MapModel.ThreatMark getThreat() {
            return null;
        }

        default List<GateInfo> getGates() {
            return Collections.emptyList();
        }

        /** The suggested road from the player toward the suggestion, chunk by chunk; empty to hide it. */
        default List<GridPoint2> getSuggestedRoute() {
            return Collections.emptyList();
        }

        /** The tile picked in the selected chunk, in the chunk view; null otherwise. */
        default GridPoint2 getTileCursor() {
            return null;
        }

        /** Tile pins in the selected chunk, by tile, as glyph names. */
        default Map<GridPoint2, String> getTilePins() {
            return Collections.emptyMap();
        }
    }

    public static final class RoadInfo {
        public final float fromX, fromY, toX, toY;
        public final boolean spent;
        public final Color color;

        public RoadInfo(float fromX, float fromY, float toX, float toY, boolean spent, Color color) {
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.spent = spent;
            this.color = color;
        }
    }

    /** An ancient gate's destination chunk and its tint, for the gate rays. */
    public static final class GateInfo {
        public final GridPoint2 chunk;
        public final Color tint;

        public GateInfo(GridPoint2 chunk, Color tint) {
            this.chunk = chunk;
            this.tint = tint;
        }
    }

    private final boolean cave;
    private Texture maskTexture;
    private float[] grid = new float[0];
    private int originX;
    private int originY;
    private int span;

    /** A mask for the surface, or for the stratum being viewed (the prototype's {@code maskCave}). */
    public MapKnowledgeMask(boolean cave) {
        this.cave = cave;
    }

    public MapKnowledgeMask() {
        this(false);
    }

    public Texture getMaskTexture() {
        return maskTexture;
    }

    /** World chunk x at the mask's left edge. */
    public int getOriginX() {
        return originX;
    }

    /** World chunk y at the mask's first row. */
    public int getOriginY() {
        return originY;
    }

    /** Chunks along each side of the (square) mask. */
    public int getSpan() {
        return span;
    }

    public static float calcFrontier(float v) {
        return Math.max(0f, Math.min(1f, 4.4f * v * (1f - v)));
    }

    /** The square of chunks a known area occupies once padded, as {originX, originY, span}. */
    public static int[] layout(int minX, int minY, int maxX, int maxY) {
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int side = Math.min(MAX_SPAN, Math.max(w, h) + 2 * PAD);
        int ox = Math.floorDiv(minX + maxX + 1, 2) - side / 2;
        int oy = Math.floorDiv(minY + maxY + 1, 2) - side / 2;
        return new int[]{ox, oy, side};
    }

    /** The value one mask pixel takes for a chunk's 1/R-wide block (bx, by), before blurring. */
    static float blockValue(KnowledgeProvider provider, boolean cave, int cx, int cy, int bx, int by) {
        if (cave) {
            if (provider.getCaveState(cx, cy) == KnowledgeProvider.State.VISITED) {
                return seenFraction(provider.getCavernTiles(cx, cy), provider, cx, cy, bx, by) > 0f ? 1f : 0.42f;
            }
            KnowledgeProvider.State s = provider.getChunkState(cx, cy);
            if (s == KnowledgeProvider.State.VISITED) return 0.22f;
            if (s == KnowledgeProvider.State.GLIMPSED) return 0.18f;
            return 0f;
        }
        KnowledgeProvider.State s = provider.getChunkState(cx, cy);
        if (s == KnowledgeProvider.State.VISITED) {
            // The game's chunks are 36 tiles across and a walk sees only its corridors, so a block
            // counts as seen if any of its tiles was; the rest of an explored chunk is a light haze.
            return seenFraction(provider.getChunkTiles(cx, cy), provider, cx, cy, bx, by) > 0f ? 1f : UNSEEN_EXPLORED;
        }
        if (s == KnowledgeProvider.State.GLIMPSED) return 0.42f;
        return 0f;
    }

    private static float seenFraction(ChunkTiles tiles, KnowledgeProvider provider, int cx, int cy, int bx, int by) {
        if (tiles != null) return tiles.blockSeenFraction(bx, by, R);
        return provider.isTileSeen(cx, cy, bx, by) ? 1f : 0f;
    }

    /**
     * Rebuilds the mask over the known area {@code [minX..maxX] x [minY..maxY]}: fills each known
     * chunk's pixels, applies the prototype's two box blurs (r=2, then r=1) and uploads.
     */
    public void rebuild(KnowledgeProvider provider, Collection<GridPoint2> known, int minX, int minY, int maxX, int maxY) {
        int[] l = layout(minX, minY, maxX, maxY);
        originX = l[0];
        originY = l[1];
        span = l[2];
        int m = span * R;
        if (grid.length != m * m) grid = new float[m * m];
        else java.util.Arrays.fill(grid, 0f);

        if (provider != null && known != null) {
            for (GridPoint2 c : known) {
                int gx = (c.x - originX) * R;
                int gy = (c.y - originY) * R;
                if (gx < 0 || gy < 0 || gx + R > m || gy + R > m) continue;
                for (int by = 0; by < R; by++) {
                    for (int bx = 0; bx < R; bx++) {
                        grid[(gy + by) * m + gx + bx] = blockValue(provider, cave, c.x, c.y, bx, by);
                    }
                }
            }
        }

        if (cave) {
            boxBlur(grid, m, 1);
            boxBlur(grid, m, 1);
        } else {
            boxBlur(grid, m, 2);
            boxBlur(grid, m, 1);
        }
        uploadTexture(m);
    }

    public static void boxBlur(float[] a, int size, int rad) {
        float[] temp = new float[a.length];
        for (int y = 0; y < size; y++) {
            int rowOffset = y * size;
            for (int x = 0; x < size; x++) {
                float sum = 0f;
                int count = 0;
                for (int k = -rad; k <= rad; k++) {
                    int xx = x + k;
                    if (xx >= 0 && xx < size) {
                        sum += a[rowOffset + xx];
                        count++;
                    }
                }
                temp[rowOffset + x] = sum / (float) count;
            }
        }
        for (int y = 0; y < size; y++) {
            int rowOffset = y * size;
            for (int x = 0; x < size; x++) {
                float sum = 0f;
                int count = 0;
                for (int k = -rad; k <= rad; k++) {
                    int yy = y + k;
                    if (yy >= 0 && yy < size) {
                        sum += temp[yy * size + x];
                        count++;
                    }
                }
                a[rowOffset + x] = sum / (float) count;
            }
        }
    }

    /** The blurred value at a mask pixel, for tests. */
    float valueAt(int px, int py) {
        int m = span * R;
        if (px < 0 || py < 0 || px >= m || py >= m) return 0f;
        return grid[py * m + px];
    }

    private void uploadTexture(int m) {
        if (com.badlogic.gdx.Gdx.gl == null || com.badlogic.gdx.Gdx.graphics == null) return; // no GL context: tests
        Pixmap pixmap = new Pixmap(m, m, Pixmap.Format.RGBA8888);
        for (int y = 0; y < m; y++) {
            for (int x = 0; x < m; x++) {
                float v = Math.max(0f, Math.min(1f, grid[y * m + x]));
                int r = Math.round(v * 255f);
                int g = Math.round(calcFrontier(v) * 255f);
                pixmap.drawPixel(x, y, (r << 24) | (g << 16) | 0xFF);
            }
        }
        if (maskTexture != null && maskTexture.getWidth() != m) {
            maskTexture.dispose();
            maskTexture = null;
        }
        if (maskTexture == null) {
            maskTexture = new Texture(pixmap);
            maskTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            maskTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        } else {
            maskTexture.draw(pixmap, 0, 0);
        }
        pixmap.dispose();
    }

    @Override
    public void dispose() {
        if (maskTexture != null) {
            maskTexture.dispose();
            maskTexture = null;
        }
    }
}
