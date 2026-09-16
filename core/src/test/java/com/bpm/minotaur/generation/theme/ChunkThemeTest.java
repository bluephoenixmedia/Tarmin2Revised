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
    public void testExactlyOneThemedChunkPerThreeByThreeCluster() {
        int clusterX = 0;
        int clusterY = 0;
        int themedCount = 0;

        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                GridPoint2 chunkId = new GridPoint2(clusterX * 3 + dx, clusterY * 3 + dy);
                ChunkTheme theme = worldManager.getChunkTheme(chunkId, 1);
                if (theme != null) {
                    themedCount++;
                }
            }
        }

        assertEquals("Cluster (0, 0) must contain exactly 1 themed chunk", 1, themedCount);

        // Test another cluster (-1, 2)
        int cluster2X = -1;
        int cluster2Y = 2;
        int themedCount2 = 0;
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                int cx = cluster2X * 3 + dx;
                int cy = cluster2Y * 3 + dy;
                ChunkTheme theme = worldManager.getChunkTheme(new GridPoint2(cx, cy), 1);
                if (theme != null) {
                    themedCount2++;
                }
            }
        }
        assertEquals("Cluster (-1, 2) must contain exactly 1 themed chunk", 1, themedCount2);
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
