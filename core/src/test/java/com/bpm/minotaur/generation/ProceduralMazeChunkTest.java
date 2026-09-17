package com.bpm.minotaur.generation;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.telemetry.TelemetryManager;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ProceduralMazeChunkTest {

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> null
            );
        }
        if (Gdx.files == null) {
            Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(
                    com.badlogic.gdx.Files.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Files.class},
                    (proxy, method, args) -> {
                        if ("local".equals(method.getName()) || "internal".equals(method.getName())) {
                            String path = (String) args[0];
                            File file = new File(path);
                            if (!file.exists()) {
                                file = new File("assets/" + path);
                            }
                            if (!file.exists()) {
                                file = new File("../assets/" + path);
                            }
                            return new com.badlogic.gdx.files.FileHandle(file);
                        }
                        return null;
                    }
            );
        }
    }

    @Test
    public void testModularChunkRotationAndSockets() {
        Map<String, String> sockets = new HashMap<>();
        sockets.put("NORTH", "CORRIDOR_1");
        sockets.put("EAST", "WALL");
        sockets.put("SOUTH", "CORRIDOR_1");
        sockets.put("WEST", "WALL");

        String[] layout = new String[]{
                "#####.######",
                "#####.######",
                "#####.######",
                "#####.######",
                "#####.######",
                ".....o......",
                ".....o......",
                "#####.######",
                "#####.######",
                "#####.######",
                "#####.######",
                "#####.######"
        };

        ModularChunk chunk = new ModularChunk("test_chunk", "CHOKE", sockets, layout);
        assertEquals("CORRIDOR_1", chunk.getSocket("NORTH"));
        assertEquals("WALL", chunk.getSocket("EAST"));

        // Rotate 90 degrees CW (rotation 1)
        // Under 1 CW rotation: New North was Old West ("WALL"), New East was Old North ("CORRIDOR_1")
        ModularChunk rot1 = chunk.createRotated(1);
        assertEquals("WALL", rot1.getSocket("NORTH"));
        assertEquals("CORRIDOR_1", rot1.getSocket("EAST"));
        assertEquals("WALL", rot1.getSocket("SOUTH"));
        assertEquals("CORRIDOR_1", rot1.getSocket("WEST"));

        // Rotate 180 degrees (rotation 2)
        ModularChunk rot2 = chunk.createRotated(2);
        assertEquals("CORRIDOR_1", rot2.getSocket("NORTH"));
        assertEquals("WALL", rot2.getSocket("EAST"));
        assertEquals("CORRIDOR_1", rot2.getSocket("SOUTH"));
        assertEquals("WALL", rot2.getSocket("WEST"));

        // Rotate 270 degrees CW (rotation 3)
        ModularChunk rot3 = chunk.createRotated(3);
        assertEquals("WALL", rot3.getSocket("NORTH"));
        assertEquals("CORRIDOR_1", rot3.getSocket("EAST"));
        assertEquals("WALL", rot3.getSocket("SOUTH"));
        assertEquals("CORRIDOR_1", rot3.getSocket("WEST"));
    }

    @Test
    public void testModularChunkLoaderLoadsAuthoredPrefabs() {
        ModularChunkLoader loader = ModularChunkLoader.getInstance();
        assertNotNull(loader);

        assertTrue("Expected at least 10 base chunks loaded", loader.getBaseChunks().size() >= 10);
        assertTrue("Expected 4 rotations per chunk precomputed", loader.getAllRotatedChunks().size() >= 40);

        String[] expectedArchetypes = {
                "PANOPTICON", "CHOKE", "CHASM_WALKWAY", "TRAP_ROOM", "BLIND_VAULT",
                "SANCTUARY", "FLOODED_CROSSROADS", "DESCENT_SHAFT", "ASYMMETRIC_FLANK", "DEBRIS_FIELD"
        };

        for (String arch : expectedArchetypes) {
            assertFalse("Expected chunks for archetype " + arch,
                    loader.getRotatedChunksForArchetype(arch, 2).isEmpty());
        }
    }

    @Test
    public void testMacroMissionGraph3x3Strata() {
        ModularChunkLoader loader = ModularChunkLoader.getInstance();
        MacroMissionGraphGenerator generator = new MacroMissionGraphGenerator(loader, 12345678L);

        MacroMissionGraphGenerator.GeneratedMapResult result = generator.generate3x3Strata(
                1, TelemetryManager.DirectorPacingState.NEUTRAL, true, null);

        assertNotNull(result);
        assertEquals(36, result.width);
        assertEquals(36, result.height);
        assertEquals(36, result.layout.length);

        for (String row : result.layout) {
            assertEquals("Each row in 3x3 stitched map must be 36 characters", 36, row.length());
        }

        // Verify that champion and descent nodes are set
        assertNotNull(result.championChunkPos);
        assertNotNull(result.descentChunkPos);
    }

    @Test
    public void testLowCoverMechanicsAndCoverBonus() {
        int[][] wallData = new int[10][10];
        Maze maze = new Maze(1, wallData);

        // Add a low-cover rubble obstacle at (5, 5)
        Scenery rubble = new Scenery(Scenery.SceneryType.LOW_COVER_RUBBLE, 5, 5, "images/debris/broken_column.png");
        maze.addScenery(rubble);

        assertTrue("Rubble must be recognized as low cover", rubble.isLowCover());
        assertTrue("Rubble must be impassable to footsteps", rubble.isImpassable());
        assertTrue("Maze query must detect low cover at (5, 5)", maze.isLowCover(5, 5));
        assertFalse("Maze movement into low cover must be blocked", maze.isPassable(5, 5));

        // Test combat manager cover check
        CombatManager combatManager = new CombatManager(null, maze, null, null, null, null, null, null, null, null, null);
        Vector2 attackerPos = new Vector2(3, 5);
        Vector2 defenderPos = new Vector2(6, 5);

        // (5, 5) lies adjacent to defender (6, 5) directly towards attacker (3, 5)
        assertTrue("Defender at (6, 5) behind low cover at (5, 5) must gain cover bonus from attacker at (3, 5)",
                combatManager.hasLowCoverBetween(attackerPos, defenderPos));

        Vector2 openDefenderPos = new Vector2(3, 8);
        assertFalse("Target in open terrain must not gain cover bonus",
                combatManager.hasLowCoverBetween(attackerPos, openDefenderPos));
    }

    @Test
    public void testRetroModeClassicIsolation() {
        MazeChunkGenerator generator = new MazeChunkGenerator();

        // CLASSIC mode must generate a 2x2 map (24x24 tiles)
        Maze classicMaze = generator.generateChunk(
                new GridPoint2(0, 0), 1, 1,
                com.bpm.minotaur.gamedata.Difficulty.MEDIUM,
                GameMode.CLASSIC,
                com.bpm.minotaur.rendering.RetroTheme.STANDARD_THEME,
                com.bpm.minotaur.rendering.RetroTheme.STANDARD_THEME,
                null, null, null, null, null, 42L, 10
        );

        assertNotNull(classicMaze);
        assertEquals("Classic mode must strictly preserve 24-tile width", 24, classicMaze.getWidth());
        assertEquals("Classic mode must strictly preserve 24-tile height", 24, classicMaze.getHeight());
    }
}
