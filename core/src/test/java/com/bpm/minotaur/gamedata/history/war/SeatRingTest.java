package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/** Living War A32: a ring about the centre, and two ways through it. */
public class SeatRingTest {

    private static final int W = 36, H = 36;

    private static Set<GridPoint2> reachableFrom(GridPoint2 start, Set<GridPoint2> wall) {
        Set<GridPoint2> seen = new HashSet<>();
        ArrayDeque<GridPoint2> todo = new ArrayDeque<>();
        todo.add(start);
        seen.add(start);
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!todo.isEmpty()) {
            GridPoint2 at = todo.poll();
            for (int[] s : steps) {
                GridPoint2 n = new GridPoint2(at.x + s[0], at.y + s[1]);
                if (n.x < 0 || n.y < 0 || n.x >= W || n.y >= H || wall.contains(n) || !seen.add(n)) continue;
                todo.add(n);
            }
        }
        return seen;
    }

    @Test
    public void theGatesAreTheOnlyWayIn() {
        Set<GridPoint2> wall = new HashSet<>(SeatRing.wall(W, H));
        assertTrue("through a gate", reachableFrom(new GridPoint2(0, 0), wall).contains(new GridPoint2(W / 2, H / 2)));
        List<GridPoint2> gates = SeatRing.gates(W, H);
        assertEquals(2, gates.size());
        for (int dx = -1; dx <= 1; dx++) {
            wall.add(new GridPoint2(gates.get(0).x + dx, gates.get(0).y));
            wall.add(new GridPoint2(gates.get(1).x + dx, gates.get(1).y));
        }
        assertFalse("both shut, the yard is sealed", reachableFrom(new GridPoint2(0, 0), wall).contains(new GridPoint2(W / 2, H / 2)));
    }

    @Test
    public void theWallFitsTheChunkAndHasNoRepeats() {
        List<GridPoint2> wall = SeatRing.wall(W, H);
        assertEquals(wall.size(), new HashSet<>(wall).size());
        for (GridPoint2 t : wall) {
            assertTrue(t.x >= 0 && t.y >= 0 && t.x < W && t.y < H);
            assertFalse("the wall is not inside itself", SeatRing.inside(W, H, t.x, t.y));
        }
        assertTrue(SeatRing.inside(W, H, W / 2, H / 2));
        assertFalse(SeatRing.inside(W, H, 0, 0));
    }
}
