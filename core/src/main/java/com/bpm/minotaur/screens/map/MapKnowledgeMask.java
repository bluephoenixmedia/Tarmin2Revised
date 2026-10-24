package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

/**
 * Builds and updates the world-space knowledge mask texture for the fog-of-war shader.
 *
 * <p>Dimensions: {@code (N + 2 * PAD) * R = (10 + 2 * 7) * 8 = 192x192}.
 * <ul>
 *   <li>Red channel: blurred knowledge mask value {@code v} (0 = unknown/opaque fog, 0.42 = glimpsed, 1.0 = visited seen).</li>
 *   <li>Green channel: frontier wisp mask {@code 4.4 * v * (1 - v)} (peaks at frontiers and over glimpsed chunks).</li>
 * </ul>
 */
public final class MapKnowledgeMask implements Disposable {

    public static final int N = 10;
    public static final int R = 8;
    public static final int PAD = 7;
    public static final int M = (N + 2 * PAD) * R; // 192

    public interface KnowledgeProvider {
        enum State { UNKNOWN, GLIMPSED, VISITED }
        State getChunkState(int cx, int cy);
        boolean isTileSeen(int cx, int cy, int lx, int ly);
    }

    private Texture maskTexture;
    private final float[] rawGrid = new float[M * M];

    public Texture getMaskTexture() {
        return maskTexture;
    }

    public static int chunkX(int px) {
        int tx = px - PAD * R;
        return (int) Math.floor((double) tx / R);
    }

    public static int chunkY(int py) {
        int ty = py - PAD * R;
        return (int) Math.floor((double) ty / R);
    }

    public static int localTileX(int px) {
        int tx = px - PAD * R;
        int cx = chunkX(px);
        return tx - cx * R;
    }

    public static int localTileY(int py) {
        int ty = py - PAD * R;
        int cy = chunkY(py);
        return ty - cy * R;
    }

    public static float calcFrontier(float v) {
        return Math.max(0f, Math.min(1f, 4.4f * v * (1f - v)));
    }

    /**
     * Builds raw grid values from the provider, applies the 2-pass blur (r=2, r=1),
     * and updates the GPU texture.
     */
    public void rebuild(KnowledgeProvider provider) {
        for (int py = 0; py < M; py++) {
            int cy = chunkY(py);
            int ly = localTileY(py);
            for (int px = 0; px < M; px++) {
                int cx = chunkX(px);
                int lx = localTileX(px);

                float v = 0f;
                if (cx >= 0 && cx < N && cy >= 0 && cy < N && provider != null) {
                    KnowledgeProvider.State state = provider.getChunkState(cx, cy);
                    if (state == KnowledgeProvider.State.VISITED) {
                        v = provider.isTileSeen(cx, cy, lx, ly) ? 1.0f : 0.5f;
                    } else if (state == KnowledgeProvider.State.GLIMPSED) {
                        v = 0.42f;
                    }
                }
                rawGrid[py * M + px] = v;
            }
        }

        // Two passes of separable box blur matching prototype
        boxBlur(rawGrid, M, 2);
        boxBlur(rawGrid, M, 1);

        uploadTexture();
    }

    public static void boxBlur(float[] a, int size, int rad) {
        float[] temp = new float[a.length];
        // Horizontal pass
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

        // Vertical pass
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

    private void uploadTexture() {
        Pixmap pixmap = new Pixmap(M, M, Pixmap.Format.RGBA8888);
        for (int y = 0; y < M; y++) {
            for (int x = 0; x < M; x++) {
                float v = Math.max(0f, Math.min(1f, rawGrid[y * M + x]));
                float front = calcFrontier(v);

                int r = Math.round(v * 255f);
                int g = Math.round(front * 255f);
                int b = 0;
                int a = 255;
                int rgba = (r << 24) | (g << 16) | (b << 8) | a;
                pixmap.drawPixel(x, y, rgba);
            }
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
