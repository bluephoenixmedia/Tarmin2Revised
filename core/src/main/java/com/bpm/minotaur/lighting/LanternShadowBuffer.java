package com.bpm.minotaur.lighting;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Window;

/**
 * High-performance 1D radial shadow buffer for the player's lantern.
 *
 * <p>Casts 360-degree rays from the player's coordinates across the maze grid using 2D DDA.
 * Stores radial line-of-sight distances into a 1D texture that is sampled in the fragment
 * shader (O(1) texture fetch with PCF penumbra filtering) to eliminate wall light bleed.
 */
public class LanternShadowBuffer implements Disposable {

    public static final int DEFAULT_SAMPLES = 256;
    public static final float DEFAULT_MAX_DISTANCE = 16.0f;

    private final int sampleCount;
    private final float maxDistance;
    private final float[] distances;

    private Pixmap pixmap;
    private Texture texture;
    private boolean textureDirty = false;

    public LanternShadowBuffer() {
        this(DEFAULT_SAMPLES, DEFAULT_MAX_DISTANCE);
    }

    public LanternShadowBuffer(int sampleCount) {
        this(sampleCount, DEFAULT_MAX_DISTANCE);
    }

    public LanternShadowBuffer(int sampleCount, float maxDistance) {
        this.sampleCount = sampleCount;
        this.maxDistance = maxDistance;
        this.distances = new float[sampleCount];
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public float getMaxDistance() {
        return maxDistance;
    }

    public float[] getDistances() {
        return distances;
    }

    /**
     * Updates the shadow buffer by raymarching against the maze from position (px, py).
     */
    public void update(float px, float py, Maze maze) {
        if (maze == null) {
            for (int i = 0; i < sampleCount; i++) {
                distances[i] = maxDistance;
            }
            textureDirty = true;
            return;
        }

        float angleStep = (float) (2.0 * Math.PI / sampleCount);

        for (int i = 0; i < sampleCount; i++) {
            // Angle sweeps from -PI to +PI matching atan2(y, x)
            float angle = (float) (-Math.PI + i * angleStep);
            float dirX = (float) Math.cos(angle);
            float dirY = (float) Math.sin(angle);

            distances[i] = traceRay(px, py, dirX, dirY, maxDistance, maze);
        }

        textureDirty = true;
    }

    /**
     * Fast 2D DDA raymarch against the maze grid.
     */
    private float traceRay(float px, float py, float dirX, float dirY, float maxDist, Maze maze) {
        int curX = (int) Math.floor(px);
        int curY = (int) Math.floor(py);

        float stepX = (dirX > 0f) ? 1f : ((dirX < 0f) ? -1f : 0f);
        float stepY = (dirY > 0f) ? 1f : ((dirY < 0f) ? -1f : 0f);

        float deltaDistX = (dirX == 0f) ? 1e30f : Math.abs(1.0f / dirX);
        float deltaDistY = (dirY == 0f) ? 1e30f : Math.abs(1.0f / dirY);

        float sideDistX = (dirX > 0f) ? (curX + 1.0f - px) * deltaDistX : (px - curX) * deltaDistX;
        float sideDistY = (dirY > 0f) ? (curY + 1.0f - py) * deltaDistY : (py - curY) * deltaDistY;

        float traveled = 0f;

        while (traveled < maxDist) {
            if (sideDistX < sideDistY) {
                traveled = sideDistX;
                sideDistX += deltaDistX;
                Direction boundaryDir = (stepX > 0f) ? Direction.EAST : Direction.WEST;
                int nextX = curX + (int) stepX;
                Direction opposite = boundaryDir.getOpposite();
                boolean blocked = maze.isWallBlocking(curX, curY, boundaryDir)
                        || (nextX >= 0 && nextX < maze.getWidth() && (maze.isWallBlocking(nextX, curY, opposite) || maze.isWall(nextX, curY)));
                if (blocked) {
                    Object obj = maze.getGameObjectAt(curX, curY);
                    Object nextObj = (nextX >= 0 && nextX < maze.getWidth()) ? maze.getGameObjectAt(nextX, curY) : null;
                    if (!(obj instanceof Window || nextObj instanceof Window)) {
                        return Math.min(traveled, maxDist);
                    }
                }
                curX += (int) stepX;
            } else {
                traveled = sideDistY;
                sideDistY += deltaDistY;
                Direction boundaryDir = (stepY > 0f) ? Direction.NORTH : Direction.SOUTH;

                int nextY = curY + (int) stepY;
                Direction opposite = boundaryDir.getOpposite();
                boolean blocked = maze.isWallBlocking(curX, curY, boundaryDir)
                        || (nextY >= 0 && nextY < maze.getHeight() && (maze.isWallBlocking(curX, nextY, opposite) || maze.isWall(curX, nextY)));
                if (blocked) {
                    Object obj = maze.getGameObjectAt(curX, curY);
                    Object nextObj = (nextY >= 0 && nextY < maze.getHeight()) ? maze.getGameObjectAt(curX, nextY) : null;
                    if (!(obj instanceof Window || nextObj instanceof Window)) {
                        return Math.min(traveled, maxDist);
                    }
                }
                curY += (int) stepY;
            }

            if (curX < 0 || curX >= maze.getWidth() || curY < 0 || curY >= maze.getHeight()) {
                return Math.min(traveled, maxDist);
            }
        }

        return maxDist;
    }

    /**
     * Looks up the occluded distance in a specific angular direction [-PI, PI].
     */
    public float getDistanceAtAngle(float angle) {
        // Normalize angle to [-PI, PI]
        while (angle < -Math.PI) angle += (float) (2.0 * Math.PI);
        while (angle >= Math.PI) angle -= (float) (2.0 * Math.PI);

        float u = (float) ((angle + Math.PI) / (2.0 * Math.PI));
        int index = Math.min(sampleCount - 1, Math.max(0, (int) (u * sampleCount)));
        return distances[index];
    }

    /**
     * Encodes distance [0, maxDistance] into a 16-bit integer for RGBA texture packing.
     */
    public static int encodeDistance(float dist, float maxDist) {
        float norm = Math.max(0f, Math.min(1.0f, dist / maxDist));
        return (int) (norm * 65535.0f);
    }

    /**
     * Decodes a 16-bit integer back to float distance.
     */
    public static float decodeDistance(int encoded, float maxDist) {
        return (encoded / 65535.0f) * maxDist;
    }

    /**
     * Returns or creates the OpenGL Texture uploading distance values.
     */
    public Texture getTexture() {
        if (pixmap == null) {
            pixmap = new Pixmap(sampleCount, 1, Pixmap.Format.RGBA8888);
            pixmap.setBlending(Pixmap.Blending.None);
        }

        if (textureDirty) {
            for (int i = 0; i < sampleCount; i++) {
                int enc = encodeDistance(distances[i], maxDistance);
                int r = (enc >> 8) & 0xFF;
                int g = enc & 0xFF;
                // Pack into RGBA8888: R = high byte, G = low byte, B = 0, A = 255
                int pixel = (r << 24) | (g << 16) | 0x0000FF;
                pixmap.drawPixel(i, 0, pixel);
            }

            if (texture == null) {
                texture = new Texture(pixmap);
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge);
            } else {
                texture.draw(pixmap, 0, 0);
            }
            textureDirty = false;
        }

        return texture;
    }

    @Override
    public void dispose() {
        if (texture != null) {
            texture.dispose();
            texture = null;
        }
        if (pixmap != null) {
            pixmap.dispose();
            pixmap = null;
        }
    }
}
