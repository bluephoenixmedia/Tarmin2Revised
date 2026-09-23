package com.bpm.minotaur.generation.theme;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.liquid.LiquidManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Every themed chunk seals its gates, so an objective that cannot be completed
 * would be an unrecoverable save. The Rune of Surrender is the escape hatch
 * that makes universal sealing survivable.
 *
 * <p>See {@code docs/DEsign/Themed Chunk Contract.md} section 3.
 */
public class SurrenderRuneTest {

    private static final int CHUNK = 24;

    private Maze maze;

    @Before
    public void setUp() {
        PropCatalogReset.reset();

        int[][] wallData = new int[CHUNK][CHUNK];
        for (int y = 0; y < CHUNK; y++) {
            for (int x = 0; x < CHUNK; x++) {
                wallData[y][x] = (x == 0 || y == 0 || x == CHUNK - 1 || y == CHUNK - 1) ? 1 : 0;
            }
        }
        maze = new Maze(3, wallData);
        maze.setLiquidManager(new LiquidManager());
        maze.setChunkTheme(ChunkTheme.BLOOD_COLOSSEUM);
        maze.setThemeObjective(new ThemeObjectiveState(ThemeObjectiveKind.LAST_COMBATANT_STANDING, 1));

        Gate gate = new Gate(CHUNK / 2, CHUNK - 2, new GridPoint2(1, 0), new GridPoint2(2, 2));
        gate.setLocked(true);
        maze.addGate(gate);
    }

    @Test
    public void channellingForTheFullDurationUnsealsTheGates() {
        assertTrue(anyGateLocked());

        ThemeObjectiveManager.tryBeginSurrender(maze, null);
        assertTrue(maze.getThemeObjective().isChannellingSurrender());

        for (int i = 0; i < ThemeObjectiveState.SURRENDER_CHANNEL_TURNS; i++) {
            ThemeObjectiveManager.onTurn(maze, null);
        }

        assertTrue("Surrender must resolve the objective", maze.getThemeObjective().isSurrendered());
        assertFalse("Gates must open after a full channel", anyGateLocked());
    }

    @Test
    public void surrenderForfeitsTheCrest() {
        ThemeObjectiveManager.tryBeginSurrender(maze, null);
        for (int i = 0; i < ThemeObjectiveState.SURRENDER_CHANNEL_TURNS; i++) {
            ThemeObjectiveManager.onTurn(maze, null);
        }

        assertFalse("A surrendered chunk must never pay out",
                maze.getThemeObjective().isRewardGranted());
        assertFalse("A surrendered chunk must not count as completed",
                maze.getThemeObjective().isCompleted());
    }

    @Test
    public void anInterruptedChannelDoesNotOpenTheGates() {
        ThemeObjectiveManager.tryBeginSurrender(maze, null);
        ThemeObjectiveManager.onTurn(maze, null); // one tick of three

        ThemeObjectiveManager.interruptSurrender(maze, null);
        assertFalse(maze.getThemeObjective().isChannellingSurrender());

        for (int i = 0; i < 10; i++) {
            ThemeObjectiveManager.onTurn(maze, null);
        }
        assertTrue("Interrupted channel must leave the chunk sealed", anyGateLocked());
        assertFalse(maze.getThemeObjective().isSurrendered());
    }

    @Test
    public void surrenderCannotBeStartedOnceTheObjectiveIsComplete() {
        maze.getThemeObjective().complete();
        assertFalse("Nothing to surrender from once the objective is done",
                ThemeObjectiveManager.tryBeginSurrender(maze, null));
    }

    @Test
    public void completingTheObjectiveUnsealsAndPaysOnce() {
        int before = com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().getCrestsOfValor();

        maze.getThemeObjective().complete();
        ThemeObjectiveManager.resolveIfComplete(maze, null);
        ThemeObjectiveManager.resolveIfComplete(maze, null); // idempotence

        assertFalse("Completing must unseal", anyGateLocked());

        int after = com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().getCrestsOfValor();
        int award = ThemeDataManager.getInstance().get(ChunkTheme.BLOOD_COLOSSEUM).getCrestAward();
        assertEquals("Reward must be granted exactly once", before + award, after);
    }

    private boolean anyGateLocked() {
        for (Gate gate : maze.getGates().values()) {
            if (gate.isLocked()) return true;
        }
        return false;
    }

    /** Tiny helper so both theme tests reset the same singletons. */
    static final class PropCatalogReset {
        static void reset() {
            com.bpm.minotaur.gamedata.prop.PropCatalog.resetInstance();
            ThemeDataManager.resetInstance();
        }
    }
}
