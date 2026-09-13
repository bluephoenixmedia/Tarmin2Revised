package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.GridPoint2;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ChunkDataMapQueriesTest {

    @Test
    public void testHasShelterFalseWhenHomeTilesEmptyOrNull() {
        ChunkData data = new ChunkData();
        data.homeTiles = new ArrayList<>();
        assertFalse(data.hasShelter());

        data.homeTiles = null;
        assertFalse(data.hasShelter());
    }

    @Test
    public void testHasShelterTrueWhenHomeTilesPresent() {
        ChunkData data = new ChunkData();
        data.homeTiles = new ArrayList<>(Arrays.asList(new GridPoint2(1, 1)));
        assertTrue(data.hasShelter());
    }

    @Test
    public void testHasUpAndDownLadderDetection() {
        ChunkData data = new ChunkData();
        assertFalse(data.hasUpLadder());
        assertFalse(data.hasDownLadder());

        ChunkData.LadderData up = new ChunkData.LadderData();
        up.x = 2;
        up.y = 3;
        up.type = Ladder.LadderType.UP;
        data.ladders.add(up);

        assertTrue(data.hasUpLadder());
        assertFalse(data.hasDownLadder());

        ChunkData.LadderData down = new ChunkData.LadderData();
        down.x = 5;
        down.y = 6;
        down.type = Ladder.LadderType.DOWN;
        data.ladders.add(down);

        assertTrue(data.hasUpLadder());
        assertTrue(data.hasDownLadder());
    }
}
