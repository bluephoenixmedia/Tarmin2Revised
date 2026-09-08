package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import org.junit.Before;
import org.junit.Test;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class ExplorationTest {

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> null
            );
        }
    }

    @Test
    public void testDebugManagerFovTuning() {
        DebugManager dm = DebugManager.getInstance();
        dm.setFov3d(DebugManager.DEFAULT_FOV_3D);
        assertEquals(80.0f, dm.getFov3d(), 0.001f);

        // Adjust FOV up
        float increased = dm.adjustFov3d(1.0f);
        assertEquals(81.0f, increased, 0.001f);
        assertEquals(81.0f, dm.getFov3d(), 0.001f);

        // Adjust FOV down
        float decreased = dm.adjustFov3d(-2.0f);
        assertEquals(79.0f, decreased, 0.001f);
        assertEquals(79.0f, dm.getFov3d(), 0.001f);

        // Clamp upper bound
        dm.setFov3d(150.0f);
        assertEquals(110.0f, dm.getFov3d(), 0.001f);

        // Clamp lower bound
        dm.setFov3d(20.0f);
        assertEquals(45.0f, dm.getFov3d(), 0.001f);

        // Reset to default
        dm.setFov3d(DebugManager.DEFAULT_FOV_3D);
    }

    @Test
    public void testAmbientAndSightConeExploration() {
        int[][] wallData = new int[10][10];
        // Wall south on tile (5, 5) blocks sight from south (y < 5)
        wallData[5][5] = Direction.SOUTH.getWallMask();

        Maze maze = new Maze(1, wallData);
        Player player = new Player(5, 2);
        // Player faces NORTH
        player.getDirectionVector().set(0, 1);
        player.getCameraPlane().set(0.66f, 0);

        WorldManager wm = new WorldManager(null, null, 1, null, null, null, null, null, null);
        wm.updateExploration(player, maze);

        // 1. Ambient 3x3 footprint around (5, 2)
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                assertTrue("Ambient tile (" + (5 + dx) + ", " + (2 + dy) + ") should be visited",
                        maze.isVisited(5 + dx, 2 + dy));
            }
        }

        // 2. Sight cone forward towards (5, 5)
        assertTrue("Corridor tile (5, 3) should be visited", maze.isVisited(5, 3));
        assertTrue("Corridor tile (5, 4) should be visited", maze.isVisited(5, 4));
        assertTrue("Wall tile (5, 5) should be visited", maze.isVisited(5, 5));

        // 3. Tile behind wall (5, 6) should NOT be visited
        assertFalse("Tile behind wall (5, 6) should not be visited", maze.isVisited(5, 6));
        assertFalse("Tile behind wall (5, 7) should not be visited", maze.isVisited(5, 7));

        // 4. Behind the player (5, 0) is outside ambient 3x3 and behind sight cone
        assertFalse("Tile behind player (5, 0) should not be visited", maze.isVisited(5, 0));
    }
}
