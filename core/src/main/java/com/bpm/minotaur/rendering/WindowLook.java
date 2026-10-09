package com.bpm.minotaur.rendering;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.rendering.mesh.ChunkMeshBuilder;

/**
 * The player's face pressed to a barred window, looking out.
 *
 * <p>The eye sits just behind the bars, in the middle of the opening, facing out through
 * the wall; the player may turn their head and look up or down, but only as far as the
 * embrasure allows. Free of rendering, so the view's geometry can be tested; the 3D
 * renderer reads it in place of the player's own camera while it is active.
 */
public final class WindowLook {

    /** How far the eye sits behind the bars, on the room side, in tiles. */
    public static final float EYE_BEHIND_BARS = 0.1f;
    /** How far the head turns left or right of straight out, in degrees. */
    public static final float MAX_YAW = 60f;
    /** How far the eyes lift or drop from level, in degrees. */
    public static final float MAX_PITCH = 35f;

    private final GridPoint2 window;
    private final Direction outward;
    private float yaw;
    private float pitch;

    /**
     * @param window  the window's wall cell
     * @param outward the direction from the room out through the window
     */
    public WindowLook(GridPoint2 window, Direction outward) {
        this.window = new GridPoint2(window);
        this.outward = outward;
    }

    /** Turns the head: positive yaw is left, positive pitch is up. Clamped to the embrasure. */
    public void turn(float yawDegrees, float pitchDegrees) {
        yaw = MathUtils.clamp(yaw + yawDegrees, -MAX_YAW, MAX_YAW);
        pitch = MathUtils.clamp(pitch + pitchDegrees, -MAX_PITCH, MAX_PITCH);
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public GridPoint2 getWindow() { return new GridPoint2(window); }

    /**
     * The eye in maze space: a little behind the bars, which stand mid-cell, and between two
     * of them -- a face pressed to the gap, not to the iron. With an odd number of bars the
     * middle of the opening is a bar, so the eye shifts half a gap to the left.
     */
    public Vector2 eye(Vector2 out) {
        Vector2 o = outward.getVector();
        float halfGap = ChunkMeshBuilder.WINDOW_HALF_WIDTH / (ChunkMeshBuilder.WINDOW_BARS + 1);
        float side = halfGap * (ChunkMeshBuilder.WINDOW_BARS % 2); // no shift when the middle is a gap
        // Left of facing (x, y) is (-y, x).
        return out.set(window.x + 0.5f - o.x * EYE_BEHIND_BARS - o.y * side,
                window.y + 0.5f - o.y * EYE_BEHIND_BARS + o.x * side);
    }

    /** Eye height: the middle of the opening. */
    public float eyeHeight() {
        return (ChunkMeshBuilder.WINDOW_SILL_Y + ChunkMeshBuilder.WINDOW_LINTEL_Y) / 2f;
    }

    /** The view direction in world space (x east, y up, z = -maze y), unit length. */
    public Vector3 direction(Vector3 out) {
        Vector2 o = outward.getVector();
        out.set(o.x, 0f, -o.y).nor();
        // Yaw about the vertical (positive is counter-clockwise from above: a turn to the left),
        // then pitch about the view's own right-hand axis.
        out.rotate(Vector3.Y, yaw);
        Vector3 right = new Vector3(out).crs(Vector3.Y).nor();
        return out.rotate(right, pitch).nor();
    }
}
