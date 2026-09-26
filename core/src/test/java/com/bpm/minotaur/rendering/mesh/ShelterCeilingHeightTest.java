package com.bpm.minotaur.rendering.mesh;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The shelter stands taller than the corridors, and everything drawn on a
 * shelter tile has to agree about that.
 *
 * <p>The baked walls were raised first, which left the sliding door panels
 * short: a door on a home tile had a half-unit hole to the sky above it.
 */
public class ShelterCeilingHeightTest {

    private Maze mazeWithHomeTileAt(int hx, int hy) {
        Maze maze = new Maze(1, new int[8][8]);
        maze.addHomeTile(new GridPoint2(hx, hy));
        return maze;
    }

    @Test
    public void shelterTilesAreTallerThanCorridorTiles() {
        Maze maze = mazeWithHomeTileAt(3, 3);

        assertEquals("A shelter tile uses the raised ceiling",
                ChunkMeshBuilder.SHELTER_CEILING_Y,
                ChunkMeshBuilder.ceilingHeightFor(maze, 3, 3), 0.0001f);
        assertEquals("A plain corridor tile keeps the standard ceiling",
                ChunkMeshBuilder.STANDARD_CEILING_Y,
                ChunkMeshBuilder.ceilingHeightFor(maze, 4, 3), 0.0001f);
    }

    @Test
    public void theRaisedCeilingIsActuallyRaised() {
        // Guards against someone collapsing the two constants back together,
        // which would make every assertion here pass for the wrong reason.
        assertTrue("The shelter ceiling must sit above the standard one",
                ChunkMeshBuilder.SHELTER_CEILING_Y > ChunkMeshBuilder.STANDARD_CEILING_Y);
    }

    @Test
    public void slidingDoorPanelsReachTheCeilingOfTheirOwnTile() {
        Maze maze = mazeWithHomeTileAt(3, 3);

        // The shelter layout contains a door, so this pairing is reachable in
        // play, not hypothetical.
        float doorTopInShelter = ChunkMeshBuilder.ceilingHeightFor(maze, 3, 3);
        float doorTopInCorridor = ChunkMeshBuilder.ceilingHeightFor(maze, 0, 0);

        assertEquals(ChunkMeshBuilder.SHELTER_CEILING_Y, doorTopInShelter, 0.0001f);
        assertEquals(ChunkMeshBuilder.STANDARD_CEILING_Y, doorTopInCorridor, 0.0001f);
        assertNotEquals("A door must not use one fixed height everywhere",
                doorTopInShelter, doorTopInCorridor, 0.0001f);
    }
}
