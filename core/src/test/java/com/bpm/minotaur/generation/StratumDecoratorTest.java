package com.bpm.minotaur.generation;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.lighting.LightSource;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** Plan T4.1: a stratum chunk, dressed and lit. */
public class StratumDecoratorTest {

    @Test
    public void eachStratumScattersItsOwnPassablePropsAndAMazeNone() {
        for (Stratum s : Stratum.values()) {
            Maze maze = new Maze(3, new int[20][20]);
            int placed = StratumDecorator.decorate(maze, s, 42L, null);
            if (s == Stratum.MAZE) {
                assertEquals(0, placed);
                continue;
            }
            assertTrue(s + " placed " + placed, placed >= StratumDecorator.PROPS_MIN && placed <= StratumDecorator.PROPS_MAX);
            Set<String> allowed = new HashSet<>(Arrays.asList(s.props));
            for (Scenery prop : maze.getScenery().values()) {
                assertTrue(s + " has " + prop.getPropId(), allowed.contains(prop.getPropId()));
                assertFalse("never walls a corridor shut", prop.isImpassable());
            }
        }
    }

    @Test
    public void theGlowIsTheSameOnEveryLoad() {
        Maze first = new Maze(3, new int[20][20]);
        Maze again = new Maze(3, new int[20][20]);
        StratumDecorator.light(first, Stratum.FUNGAL_FOREST, 7L);
        StratumDecorator.light(again, Stratum.FUNGAL_FOREST, 7L);
        assertEquals(StratumDecorator.GLOWS, first.getLights().size);
        for (int i = 0; i < first.getLights().size; i++) {
            LightSource a = first.getLights().get(i), b = again.getLights().get(i);
            assertEquals(a.getPosition(), b.getPosition());
        }
        StratumDecorator.light(first, Stratum.FUNGAL_FOREST, 7L);
        assertEquals("relighting replaces, never doubles", StratumDecorator.GLOWS, first.getLights().size);
    }

    @Test
    public void theMazeStratumHasNoGlowOrFog() {
        Maze maze = new Maze(3, new int[20][20]);
        StratumDecorator.light(maze, Stratum.MAZE, 7L);
        assertEquals(0, maze.getLights().size);
        assertNull(Stratum.MAZE.fogColor);
    }
}
