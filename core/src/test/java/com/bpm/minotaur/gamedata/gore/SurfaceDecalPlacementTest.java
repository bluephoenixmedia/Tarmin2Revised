package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Blood belongs on the floor, and a doorway has no floor.
 *
 * <p>Two independent defects put it in the air. Decals kept the Y they were
 * spawned at -- kill stains are born at the victim's chest, y=0.5 -- and while
 * the 3D renderer overrode the height when drawing, the raycaster derives
 * screen Y from the stored value, so the same stain hung at eye level. And
 * {@code spawnSurfaceDecal} took no Maze, so no caller could reject a doorway
 * even in principle.
 */
public class SurfaceDecalPlacementTest {

    private Maze mazeWithDoorAt(int doorX, int doorY) {
        int size = 16;
        int[][] wallData = new int[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                wallData[y][x] = (x == 0 || y == 0 || x == size - 1 || y == size - 1) ? 1 : 0;
            }
        }
        Maze maze = new Maze(1, wallData);
        maze.addGameObject(new Door(), doorX, doorY);
        return maze;
    }

    @Test
    public void aDecalIsAlwaysFlattenedToTheFloor() {
        SurfaceDecal decal = new SurfaceDecal();

        // A kill stain spawned from the victim's chest.
        decal.init(new Vector3(4f, 0.5f, 6f), new Color(0.6f, 0f, 0f, 1f), 0.3f, null);

        assertEquals("A surface decal must sit on the floor regardless of spawn height",
                SurfaceDecal.FLOOR_Y, decal.position.y, 0.0001f);
        assertEquals("X must be preserved", 4f, decal.position.x, 0.0001f);
        assertEquals("Z must be preserved", 6f, decal.position.z, 0.0001f);
    }

    @Test
    public void aFlattenedDecalRendersBelowTheHorizonNotAtEyeLevel() {
        // Mirrors EntityRenderer's raycaster arithmetic:
        //   screenY = halfHeight + (position.y - 0.5f) * spriteScale
        SurfaceDecal decal = new SurfaceDecal();
        decal.init(new Vector3(4f, 0.5f, 6f), new Color(0.6f, 0f, 0f, 1f), 0.3f, null);

        float halfHeight = 300f;
        float spriteScale = 200f;
        float screenY = halfHeight + (decal.position.y - 0.5f) * spriteScale;

        assertTrue("A floor decal must draw below the horizon, not at eye level ("
                        + screenY + " vs horizon " + halfHeight + ")",
                screenY < halfHeight - 1f);
    }

    @Test
    public void doorwaysAndGatesCannotHoldADecal() {
        Maze maze = mazeWithDoorAt(5, 5);

        assertFalse("A doorway has no floor quad; blood there floats in the opening",
                GoreManager.canHoldSurfaceDecal(maze, new Vector3(5.5f, 0.002f, 5.5f)));
        assertTrue("An ordinary floor tile must still take blood",
                GoreManager.canHoldSurfaceDecal(maze, new Vector3(4.5f, 0.002f, 5.5f)));
    }

    @Test
    public void theGuardFailsOpenOnCoordinatesItCannotResolve() {
        // Callers disagree on chunk-local vs chunk-absolute positions. Rejecting
        // what it cannot place would silently delete blood from every chunk but
        // the origin, which is far worse than the bug being fixed.
        Maze maze = mazeWithDoorAt(5, 5);

        assertTrue("Out-of-range coordinates must not suppress the decal",
                GoreManager.canHoldSurfaceDecal(maze, new Vector3(400f, 0.002f, 400f)));
        assertTrue("A null maze must not suppress the decal",
                GoreManager.canHoldSurfaceDecal(null, new Vector3(4.5f, 0.002f, 5.5f)));
    }
}
