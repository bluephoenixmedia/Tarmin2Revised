package com.bpm.minotaur.rendering;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.gamedata.Direction;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Peering out through a barred window: where the eye is and where it may look.
 */
public class WindowLookTest {

    private static final float EPS = 1e-4f;

    @Test
    public void theEyeIsJustBehindTheBarsOnTheRoomSide() {
        // A window in the west wall: the player stands to its east and looks west.
        WindowLook look = new WindowLook(new GridPoint2(3, 5), Direction.WEST);
        Vector2 eye = look.eye(new Vector2());
        assertEquals("the bars stand mid-wall at x = 3.5; the eye is just east of them",
                3.5f + WindowLook.EYE_BEHIND_BARS, eye.x, EPS);
        assertTrue(WindowLook.EYE_BEHIND_BARS > 0f && WindowLook.EYE_BEHIND_BARS < 0.5f);
    }

    @Test
    public void theEyeLooksThroughTheGapBetweenTwoBarsNotAtOne() {
        WindowLook look = new WindowLook(new GridPoint2(3, 5), Direction.WEST);
        float across = look.eye(new Vector2()).y; // across the opening, for a west-wall window
        float halfWidth = com.bpm.minotaur.rendering.mesh.ChunkMeshBuilder.WINDOW_HALF_WIDTH;
        int bars = com.bpm.minotaur.rendering.mesh.ChunkMeshBuilder.WINDOW_BARS;
        float gap = 2f * halfWidth / (bars + 1);
        float clearest = Float.MAX_VALUE;
        for (int i = 1; i <= bars; i++) {
            float bar = 5.5f - halfWidth + i * gap;
            clearest = Math.min(clearest, Math.abs(across - bar));
        }
        assertEquals("midway between two bars", gap / 2f, clearest, EPS);
        assertTrue("inside the opening", Math.abs(across - 5.5f) < halfWidth);
    }

    @Test
    public void theEyeIsAtTheMiddleOfTheOpening() {
        WindowLook look = new WindowLook(new GridPoint2(0, 0), Direction.NORTH);
        assertEquals(0.5f, look.eyeHeight(), EPS); // halfway between sill 0.2 and lintel 0.8
    }

    @Test
    public void atRestTheViewIsStraightOut() {
        Vector3 dir = new WindowLook(new GridPoint2(3, 5), Direction.WEST).direction(new Vector3());
        assertEquals(-1f, dir.x, EPS);
        assertEquals(0f, dir.y, EPS);
        assertEquals(0f, dir.z, EPS);

        // Maze north is world -Z.
        Vector3 north = new WindowLook(new GridPoint2(3, 5), Direction.NORTH).direction(new Vector3());
        assertEquals(-1f, north.z, EPS);
    }

    @Test
    public void turningLeftAndLookingUpGoWhereTheySay() {
        WindowLook look = new WindowLook(new GridPoint2(3, 5), Direction.WEST);
        look.turn(30f, 20f);
        Vector3 dir = look.direction(new Vector3());
        // Facing west, left is south: maze -Y, which is world +Z.
        assertTrue("turned left, toward the south", dir.z > 0f);
        assertTrue("looking up", dir.y > 0f);
        assertEquals(1f, dir.len(), EPS);
    }

    @Test
    public void theBarsLimitHowFarTheHeadTurns() {
        WindowLook look = new WindowLook(new GridPoint2(3, 5), Direction.WEST);
        look.turn(500f, 500f);
        assertEquals(WindowLook.MAX_YAW, look.getYaw(), EPS);
        assertEquals(WindowLook.MAX_PITCH, look.getPitch(), EPS);
        look.turn(-5000f, -5000f);
        assertEquals(-WindowLook.MAX_YAW, look.getYaw(), EPS);
        assertEquals(-WindowLook.MAX_PITCH, look.getPitch(), EPS);
    }
}
