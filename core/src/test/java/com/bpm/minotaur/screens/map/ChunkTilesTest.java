package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ChunkTilesTest {

    @Test
    public void findsOpenLeadsBorderingUnexploredFog() {
        ChunkTiles tiles = new ChunkTiles();
        tiles.width = 16;
        tiles.height = 16;
        tiles.wallData = new int[16][16];
        tiles.explorationState = new byte[16][16];

        // Tile (5, 5) is seen floor
        tiles.explorationState[5][5] = Maze.VISIBILITY_SEEN;
        tiles.wallData[5][5] = 0; // open on all sides

        // Neighbor (6, 5) to the East is unseen
        tiles.explorationState[5][6] = Maze.VISIBILITY_UNSEEN;

        List<ChunkTiles.OpenLead> leads = tiles.findOpenLeads();
        assertFalse(leads.isEmpty());

        boolean hasEast = false;
        for (ChunkTiles.OpenLead lead : leads) {
            if (lead.direction == Direction.EAST && lead.tile.equals(new GridPoint2(5, 5))) {
                hasEast = true;
                break;
            }
        }
        assertTrue("Must detect open lead east into fog", hasEast);
    }

    @Test
    public void describesOpenLeadsMatchingMockupPhrasing() {
        ChunkTiles tiles = new ChunkTiles();
        tiles.width = 16;
        tiles.height = 16;
        tiles.wallData = new int[16][16];
        tiles.explorationState = new byte[16][16];

        // One opening to the East at (5, 5)
        tiles.explorationState[5][5] = Maze.VISIBILITY_SEEN;
        // Two separate openings to the North at (3, 8) and (10, 8)
        tiles.explorationState[8][3] = Maze.VISIBILITY_SEEN;
        tiles.explorationState[8][10] = Maze.VISIBILITY_SEEN;

        // Put walls on all sides except the desired open leads
        // East opening at (5,5): block North, South, West
        tiles.wallData[5][5] = Direction.NORTH.getWallMask() | Direction.SOUTH.getWallMask() | Direction.WEST.getWallMask();
        // North opening at (8,3): block East, South, West
        tiles.wallData[8][3] = Direction.EAST.getWallMask() | Direction.SOUTH.getWallMask() | Direction.WEST.getWallMask();
        // North opening at (8,10): block East, South, West
        tiles.wallData[8][10] = Direction.EAST.getWallMask() | Direction.SOUTH.getWallMask() | Direction.WEST.getWallMask();

        List<String> summaries = tiles.describeOpenLeads();
        assertTrue("Contains 2 passages north unexplored: " + summaries,
                summaries.contains("2 passages north unexplored"));
        assertTrue("Contains East corridor unexplored: " + summaries,
                summaries.contains("East corridor unexplored"));
    }

    private static ChunkTiles blank(int size) {
        ChunkTiles t = new ChunkTiles();
        t.width = size;
        t.height = size;
        t.wallData = new int[size][size];
        t.explorationState = new byte[size][size];
        return t;
    }

    @Test
    public void wallEdgesReadFromEitherSideAndDoorsWin() {
        ChunkTiles t = blank(4);
        t.wallData[1][2] = Direction.NORTH.getWallMask();      // north wall of (2, 1) ...
        assertEquals(ChunkTiles.EDGE_WALL, t.horizontalEdge(2, 2)); // ... is the edge y = 2
        t.wallData[2][3] = Direction.SOUTH.getWallMask();      // south wall of (3, 2), same edge y = 2
        assertEquals(ChunkTiles.EDGE_WALL, t.horizontalEdge(3, 2));
        t.wallData[0][1] = Direction.EAST.getWallMask();       // east wall of (1, 0) is the edge x = 2
        assertEquals(ChunkTiles.EDGE_WALL, t.verticalEdge(2, 0));
        assertEquals(ChunkTiles.EDGE_NONE, t.verticalEdge(1, 0));
        t.wallData[0][2] = 0b00000010;                          // a door on the west side of (2, 0)
        assertEquals(ChunkTiles.EDGE_DOOR, t.verticalEdge(2, 0));
    }

    @Test
    public void aSavedChunkNobodyLookedAtIsNotSeen() {
        ChunkTiles t = blank(36);
        assertFalse(t.hasAnySeenTile());
        assertEquals(0f, t.blockSeenFraction(0, 0, 8), 0.0001f);
        t.explorationState[0][0] = Maze.VISIBILITY_SEEN;
        assertTrue(new ChunkTilesProbe(t).anySeen());
        assertTrue(t.blockSeenFraction(0, 0, 8) > 0f);
        assertEquals(0f, t.blockSeenFraction(7, 7, 8), 0.0001f);
    }

    @Test
    public void aChunkSavedWithoutExplorationIsAllKnown() {
        ChunkTiles t = blank(8);
        t.explorationState = null;
        assertTrue(t.hasAnySeenTile());
        assertTrue(t.isSeen(3, 3));
        assertEquals(1f, t.blockSeenFraction(2, 2, 8), 0.0001f);
    }

    /** hasAnySeenTile caches its answer; a fresh copy re-reads the tiles. */
    private static final class ChunkTilesProbe {
        private final ChunkTiles copy = new ChunkTiles();

        ChunkTilesProbe(ChunkTiles t) {
            copy.width = t.width;
            copy.height = t.height;
            copy.wallData = t.wallData;
            copy.explorationState = t.explorationState;
        }

        boolean anySeen() {
            return copy.hasAnySeenTile();
        }
    }
}
