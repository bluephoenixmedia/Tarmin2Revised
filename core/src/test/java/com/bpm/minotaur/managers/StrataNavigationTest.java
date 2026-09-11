package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Ladder;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class StrataNavigationTest {

    @Test
    public void testLadderDataSerialization() {
        // Test UP ladder with SINKHOLE entrance style
        Ladder upSinkhole = new Ladder(5, 7, Ladder.LadderType.UP, Ladder.EntranceStyle.SINKHOLE);
        assertEquals(5.5f, upSinkhole.getPosition().x, 0.001f);
        assertEquals(7.5f, upSinkhole.getPosition().y, 0.001f);
        assertEquals(Ladder.LadderType.UP, upSinkhole.getType());
        assertEquals(Ladder.EntranceStyle.SINKHOLE, upSinkhole.getStyle());

        ChunkData.LadderData data = new ChunkData.LadderData(upSinkhole);
        assertEquals(5, data.x);
        assertEquals(7, data.y);
        assertEquals(Ladder.LadderType.UP, data.type);
        assertEquals(Ladder.EntranceStyle.SINKHOLE, data.style);

        // Test DOWN ladder with STONE_STAIRS entrance style
        Ladder downStairs = new Ladder(10, 12, Ladder.LadderType.DOWN, Ladder.EntranceStyle.STONE_STAIRS);
        ChunkData.LadderData stairsData = new ChunkData.LadderData(downStairs);
        assertEquals(10, stairsData.x);
        assertEquals(12, stairsData.y);
        assertEquals(Ladder.LadderType.DOWN, stairsData.type);
        assertEquals(Ladder.EntranceStyle.STONE_STAIRS, stairsData.style);

        // JSON Round-trip
        Json json = new Json();
        String jsonStr = json.toJson(data);
        ChunkData.LadderData deserialized = json.fromJson(ChunkData.LadderData.class, jsonStr);
        assertEquals(5, deserialized.x);
        assertEquals(7, deserialized.y);
        assertEquals(Ladder.LadderType.UP, deserialized.type);
        assertEquals(Ladder.EntranceStyle.SINKHOLE, deserialized.style);
    }

    @Test
    public void testHazardScalingFormula() {
        // Formula: 1 + max(0, depth - 1) * 3 + (int)(horizontalDistance * 0.5f) + offset
        int offset = 0;

        // Surface Level 1 at Shelter (0, 0)
        int diff1 = calculateDifficulty(new GridPoint2(0, 0), 1, offset);
        assertEquals(1, diff1);

        // Surface Level 1 at (2, 2)
        int diffSurfaceDistant = calculateDifficulty(new GridPoint2(2, 2), 1, offset);
        assertEquals(3, diffSurfaceDistant); // 1 + 0 + 2 = 3

        // Strata 1 (Level 2) at (0, 0)
        int diffStrata1 = calculateDifficulty(new GridPoint2(0, 0), 2, offset);
        assertEquals(4, diffStrata1); // 1 + 3 + 0 = 4

        // Strata 2 (Level 3) at (1, 1)
        int diffStrata2 = calculateDifficulty(new GridPoint2(1, 1), 3, offset);
        assertEquals(8, diffStrata2); // 1 + 6 + 1 = 8

        // Strata 3 (Level 4) at (0, 0)
        int diffStrata3 = calculateDifficulty(new GridPoint2(0, 0), 4, offset);
        assertEquals(10, diffStrata3); // 1 + 9 + 0 = 10
    }

    @Test
    public void testCastleCitadelModelAssetsExist() {
        String[] citadelFiles = {
                "castle_citadel.obj",
                "castle_citadel.mtl",
                "castle_citadel_diffuse.png"
        };

        for (String file : citadelFiles) {
            File f = new File("assets/models/skybox/" + file);
            if (!f.exists()) {
                f = new File("../assets/models/skybox/" + file);
            }
            assertTrue("Citadel asset must exist: " + file, f.exists());
            assertTrue("Citadel asset must not be empty: " + file, f.length() > 0);
        }
    }

    private int calculateDifficulty(GridPoint2 chunkId, int depth, int difficultyOffset) {
        int horizontalDistance = Math.abs(chunkId.x) + Math.abs(chunkId.y);
        int distancePenalty = (int) (horizontalDistance * 0.5f);
        int depthScale = Math.max(0, depth - 1) * 3;
        return 1 + depthScale + distancePenalty + difficultyOffset;
    }
}
