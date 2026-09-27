package com.bpm.minotaur.gamedata.boss;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.generation.WorldConstants;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * The boss stands somewhere random on the surface, never in the shelter, and
 * stays where it stood.
 */
public class BridgeBossTest {

    private static final long SEED = 0x0BADF00DCAFEBABEL;

    @Test
    public void aSummoningAlwaysStandsInTheSamePlace() {
        // Chunks unload on every gate crossing. If placement were rolled at
        // load time the boss would teleport each time the player left and came
        // back, or a second copy would spawn.
        GridPoint2 first = BridgeBoss.chunkFor(SEED, 1);
        for (int i = 0; i < 50; i++) {
            assertEquals("Placement must be a pure function of seed and summon",
                    first, BridgeBoss.chunkFor(SEED, 1));
        }
    }

    @Test
    public void itNeverStandsInTheShelter() {
        // The shelter is the preparation space the feature exists to reward,
        // and the respawn tile -- a boss there is a death loop.
        GridPoint2 shelter = new GridPoint2(0, 0);
        for (int summons = 1; summons <= 500; summons++) {
            assertNotEquals("Summon " + summons + " placed the boss in the shelter",
                    shelter, BridgeBoss.chunkFor(SEED, summons));
        }
    }

    @Test
    public void itAlwaysStandsInsideTheMaze() {
        int r = WorldConstants.CENTRAL_MAZE_RADIUS;
        for (int summons = 1; summons <= 500; summons++) {
            GridPoint2 c = BridgeBoss.chunkFor(SEED, summons);
            assertTrue("Summon " + summons + " placed the boss outside the maze at " + c,
                    Math.abs(c.x) <= r && Math.abs(c.y) <= r);
        }
    }

    @Test
    public void eachSummoningPicksAFreshChunk() {
        // Otherwise the player learns one address and the search stops being
        // part of the fight.
        Set<GridPoint2> seen = new HashSet<>();
        for (int summons = 1; summons <= 30; summons++) {
            seen.add(BridgeBoss.chunkFor(SEED, summons));
        }
        assertTrue("30 summonings produced only " + seen.size() + " distinct chunks",
                seen.size() > 20);
    }

    @Test
    public void placementSpreadsAcrossTheMazeRatherThanClustering() {
        Set<Integer> xs = new HashSet<>();
        Set<Integer> ys = new HashSet<>();
        for (int summons = 1; summons <= 400; summons++) {
            GridPoint2 c = BridgeBoss.chunkFor(SEED, summons);
            xs.add(c.x);
            ys.add(c.y);
        }
        int span = WorldConstants.CENTRAL_MAZE_RADIUS * 2 + 1;
        assertTrue("Only " + xs.size() + "/" + span + " columns ever used", xs.size() > span / 2);
        assertTrue("Only " + ys.size() + "/" + span + " rows ever used", ys.size() > span / 2);
    }

    @Test
    public void isBossChunkOnlyMatchesTheSurface() {
        GridPoint2 where = BridgeBoss.chunkFor(SEED, 1);
        assertTrue(BridgeBoss.isBossChunk(SEED, 1, 1, where));
        assertFalse("The boss is a surface guardian; it must not appear underground",
                BridgeBoss.isBossChunk(SEED, 1, 2, where));
        assertFalse("No summoning means no boss chunk",
                BridgeBoss.isBossChunk(SEED, 0, 1, where));
    }

    @Test
    public void theSpriteClearsTheWallsItIsMeantToBeSeenOver() {
        // World3DRenderer clamps billboard WIDTH to 0.82 and scales height down
        // to match, so an authored scale.x above that shrinks the boss instead
        // of widening it. BRINGER_OF_DEATH at (2.2, 2.2) rendered 0.82 high --
        // shorter than the 1.0 wall it was supposed to loom over.
        assertEquals("scale.x must sit exactly on the clamp so height survives",
                0.82f, BridgeBoss.SCALE_X, 0.0001f);
        assertTrue("The boss must be visibly taller than a 1.0 wall",
                BridgeBoss.SCALE_Y > 1.5f);

        float renderedHeight = BridgeBoss.SCALE_X > 0.82f
                ? BridgeBoss.SCALE_Y * (0.82f / BridgeBoss.SCALE_X)
                : BridgeBoss.SCALE_Y;
        assertTrue("After the clamp the boss still stands " + renderedHeight + " high",
                renderedHeight > 1.0f);
    }
}
