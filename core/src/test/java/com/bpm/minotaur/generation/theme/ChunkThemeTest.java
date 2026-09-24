package com.bpm.minotaur.generation.theme;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.WorldManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ChunkThemeTest {

    private WorldManager worldManager;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
        worldManager = new WorldManager(GameMode.ADVANCED, Difficulty.EASY, 1, null, null, null, null, null, null);
    }

    @Test
    public void testShelterChunkNeverThemedOnLevelOne() {
        GridPoint2 shelterPos = new GridPoint2(0, 0);
        ChunkTheme theme = worldManager.getChunkTheme(shelterPos, 1);
        assertNull("Shelter at (0, 0) on Level 1 must never be themed", theme);
    }

    @Test
    public void atMostOneThemedChunkPerThreeByThreeCluster() {
        // The cluster roll designates one chunk, but a designated chunk outside
        // the MAZE biome is dropped, so a cluster may legitimately have none.
        for (int clusterX = -2; clusterX <= 2; clusterX++) {
            for (int clusterY = -2; clusterY <= 2; clusterY++) {
                int themedCount = 0;
                for (int dx = 0; dx < 3; dx++) {
                    for (int dy = 0; dy < 3; dy++) {
                        GridPoint2 chunkId = new GridPoint2(clusterX * 3 + dx, clusterY * 3 + dy);
                        if (worldManager.getChunkTheme(chunkId, 2) != null) {
                            themedCount++;
                        }
                    }
                }
                assertTrue("Cluster (" + clusterX + ", " + clusterY
                                + ") must never contain more than one themed chunk, saw " + themedCount,
                        themedCount <= 1);
            }
        }
    }

    @Test
    public void themedChunksOnlyAppearInTheMazeBiome() {
        // A stone arena or a flooded crypt stamped into open wilderness reads as
        // a bug, and its impassable props land on forest trails one tile wide.
        com.bpm.minotaur.managers.BiomeManager biomes = worldManager.getBiomeManager();

        for (int x = -12; x <= 12; x++) {
            for (int y = -12; y <= 12; y++) {
                GridPoint2 chunkId = new GridPoint2(x, y);
                for (int level = 1; level <= 4; level++) {
                    ChunkTheme theme = worldManager.getChunkTheme(chunkId, level);
                    if (theme == null) continue;
                    assertEquals("Themed chunk " + theme + " at " + chunkId
                                    + " (level " + level + ") is outside the MAZE biome",
                            com.bpm.minotaur.generation.Biome.MAZE, biomes.getBiome(chunkId));
                }
            }
        }
    }

    @Test
    public void testLevelGatedThemes() {
        // Ruined Castle and Blood Colosseum have minLevel 3
        assertEquals(3, ChunkTheme.RUINED_CASTLE.getMinLevel());
        assertEquals(3, ChunkTheme.BLOOD_COLOSSEUM.getMinLevel());

        // Sample 100 clusters on level 1 - Ruined Castle and Blood Colosseum should never appear
        for (int i = 1; i <= 100; i++) {
            GridPoint2 testChunk = new GridPoint2(i * 3, i * 3);
            // find the themed chunk in this cluster
            for (int dx = 0; dx < 3; dx++) {
                for (int dy = 0; dy < 3; dy++) {
                    ChunkTheme theme = worldManager.getChunkTheme(new GridPoint2(testChunk.x + dx, testChunk.y + dy), 1);
                    if (theme != null) {
                        assertNotEquals("Ruined Castle must not appear on level 1", ChunkTheme.RUINED_CASTLE, theme);
                        assertNotEquals("Blood Colosseum must not appear on level 1", ChunkTheme.BLOOD_COLOSSEUM, theme);
                    }
                }
            }
        }
    }

    @Test
    public void testShelterSanctuaryBufferNeverThemedOnLevelOne() {
        // Chunks within the shelter buffer (x <= 1 && y <= 1) in cluster (0,0) must never be themed on Level 1
        assertNull("Shelter (0,0) must not be themed", worldManager.getChunkTheme(new GridPoint2(0, 0), 1));
        assertNull("Buffer (0,1) must not be themed", worldManager.getChunkTheme(new GridPoint2(0, 1), 1));
        assertNull("Buffer (1,0) must not be themed", worldManager.getChunkTheme(new GridPoint2(1, 0), 1));
        assertNull("Buffer (1,1) must not be themed", worldManager.getChunkTheme(new GridPoint2(1, 1), 1));
    }

    @Test
    public void testLockedGateCannotBeOpened() {
        Gate gate = new Gate(5, 5);
        gate.setTheme(ChunkTheme.BLOOD_COLOSSEUM);
        gate.setLocked(true);

        assertEquals(Gate.GateState.CLOSED, gate.getState());
        gate.startOpening(worldManager);
        assertEquals("Locked gate must remain CLOSED", Gate.GateState.CLOSED, gate.getState());

        gate.setLocked(false);
        gate.startOpening(worldManager);
        assertEquals("Unlocked gate should transition to OPENING", Gate.GateState.OPENING, gate.getState());
    }

    @Test
    public void testGateRuneDisplayNameWisdom() {
        Gate gate = new Gate(1, 1);
        gate.setTheme(ChunkTheme.BLOOD_COLOSSEUM);

        Player wisePlayer = new Player(1, 0);
        wisePlayer.getStats().setWisdom(15);
        // Wisdom >= 14 deciphers the true name
        assertEquals("Rune of Blood Colosseum", gate.getRuneDisplayName(wisePlayer));

        Player novicePlayer = new Player(1, 0);
        novicePlayer.getStats().setWisdom(8);
        novicePlayer.getStats().setIntelligence(8);
        // Wisdom and Intelligence < 13 gives omen
        assertEquals("Rune of Blood Sigil (High Danger)", gate.getRuneDisplayName(novicePlayer));
    }
}
