package com.bpm.minotaur.generation.theme;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.liquid.LiquidManager;
import com.bpm.minotaur.gamedata.prop.PropCatalog;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * A themed chunk seals its gates and that lock state persists. If the objective
 * does not persist with it, a reloaded chunk is sealed with nothing able to
 * open it: the completion check bails on a null state and so does the Rune of
 * Surrender. That is an unrecoverable save, and it is what these tests exist to
 * stop coming back.
 *
 * <p>See {@code docs/DEsign/Themed Chunk Contract.md} sections 3 and 10.
 */
public class ThemeSaveRoundTripTest {

    private static final int CHUNK = 24;

    @Before
    public void setUp() {
        PropCatalog.resetInstance();
        ThemeDataManager.resetInstance();
    }

    @Test
    public void objectiveProgressSurvivesASaveAndReload() {
        Maze original = decorated(ChunkTheme.MAKESHIFT_GRAVEYARD);
        ThemeObjectiveState state = original.getThemeObjective();
        assertNotNull(state);

        int required = state.getRequired();
        state.advance(); // Reconsecrate one grave.
        assertEquals(1, state.getProgress());

        Maze reloaded = new ChunkData(original).buildMaze(null, null, null);
        ThemeObjectiveState restored = reloaded.getThemeObjective();

        assertNotNull("Objective state must survive the round trip", restored);
        assertEquals("Progress must survive", 1, restored.getProgress());
        assertEquals("Required count must survive", required, restored.getRequired());
        assertEquals("Objective kind must survive",
                ThemeObjectiveKind.RECONSECRATE_GRAVES, restored.getKind());
    }

    @Test
    public void aCompletedObjectiveDoesNotPayOutTwiceAcrossAReload() {
        Maze original = decorated(ChunkTheme.BLOOD_COLOSSEUM);
        original.getThemeObjective().complete();
        ThemeObjectiveManager.resolveIfComplete(original, null);
        assertTrue(original.getThemeObjective().isRewardGranted());

        Maze reloaded = new ChunkData(original).buildMaze(null, null, null);

        assertTrue("A already-paid objective must stay paid",
                reloaded.getThemeObjective().isRewardGranted());

        int before = com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().getCrestsOfValor();
        ThemeObjectiveManager.resolveIfComplete(reloaded, null);
        int after = com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().getCrestsOfValor();
        assertEquals("Reloading must not re-award the Crest", before, after);
    }

    @Test
    public void consumedObjectiveMarkersStayConsumed() {
        Maze original = decorated(ChunkTheme.MAKESHIFT_GRAVEYARD);

        int consumed = 0;
        for (Scenery s : original.getScenery().values()) {
            if (s.isObjectiveMarker()) {
                s.consumeObjective();
                consumed++;
                break;
            }
        }
        assertEquals("Fixture must have had a marker to consume", 1, consumed);

        Maze reloaded = new ChunkData(original).buildMaze(null, null, null);

        int stillConsumed = 0;
        for (Scenery s : reloaded.getScenery().values()) {
            if (s.isObjectiveConsumed()) stillConsumed++;
        }
        assertEquals("Consumed markers must not come back unconsumed", 1, stillConsumed);
    }

    @Test
    public void aThemedChunkNeverReloadsSealedWithNoObjective() {
        // The disaster case: gates locked, objective null, surrender disabled.
        Maze original = decorated(ChunkTheme.RUINED_CASTLE);
        boolean sealed = false;
        for (Gate g : original.getGates().values()) {
            if (g.isLocked()) sealed = true;
        }
        assertTrue("Fixture should be sealed", sealed);

        ChunkData data = new ChunkData(original);
        data.themeObjective = null; // Simulate a save written before objectives existed.

        Maze reloaded = data.buildMaze(null, null, null);

        assertNotNull("A themed chunk must always reload with an objective state",
                reloaded.getThemeObjective());
        assertTrue("A reloaded sealed chunk must be escapable",
                ThemeObjectiveManager.tryBeginSurrender(reloaded, null));
    }

    @Test
    public void propsRebuildFromTheCatalogueOnReload() {
        Maze original = decorated(ChunkTheme.OVERGROWN_THICKET);
        assertFalse(original.getScenery().isEmpty());

        Maze reloaded = new ChunkData(original).buildMaze(null, null, null);

        assertEquals("Every prop must survive the round trip",
                original.getScenery().size(), reloaded.getScenery().size());

        for (Scenery s : reloaded.getScenery().values()) {
            if (s.getType() == Scenery.SceneryType.PROP) {
                assertNotNull("A reloaded PROP must keep its propId", s.getPropId());
                assertTrue("A reloaded propId must still resolve",
                        PropCatalog.getInstance().contains(s.getPropId()));
            }
        }
    }

    private Maze decorated(ChunkTheme theme) {
        int[][] wallData = new int[CHUNK][CHUNK];
        for (int y = 0; y < CHUNK; y++) {
            for (int x = 0; x < CHUNK; x++) {
                wallData[y][x] = (x == 0 || y == 0 || x == CHUNK - 1 || y == CHUNK - 1) ? 1 : 0;
            }
        }
        Maze maze = new Maze(3, wallData);
        maze.setLiquidManager(new LiquidManager());
        maze.addLadder(new com.bpm.minotaur.gamedata.Ladder(
                4, 4, com.bpm.minotaur.gamedata.Ladder.LadderType.DOWN));
        maze.addGate(new Gate(CHUNK / 2, CHUNK - 2, new GridPoint2(1, 0), new GridPoint2(2, 2)));

        ChunkThemeDecorator.decorate(maze, theme, 2024L, null, null, null);

        // ChunkData.buildMaze needs a MonsterDataManager to rehydrate monsters,
        // which a headless test has no way to supply. These tests are about
        // objective and prop persistence, so the spawns are dropped after
        // decoration -- viability was already computed while they existed.
        maze.getMonsters().clear();
        return maze;
    }
}
