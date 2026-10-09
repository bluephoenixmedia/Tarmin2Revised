package com.bpm.minotaur.gamedata.map;

import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Ladder;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.bones.BonesData;
import com.bpm.minotaur.gamedata.item.Item;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * What the map's side panel reads from a chunk save: only what the player has seen
 * (docs/DEsign/Requirements_ Expedition Map.md, sections 3 and 6).
 */
public class ChunkSummaryTest {

    private static ChunkData chunk() {
        ChunkData d = new ChunkData();
        d.explorationState = new byte[4][4];
        d.explorationState[1][1] = Maze.VISIBILITY_SEEN; // tile (1, 1)
        d.explorationState[2][3] = Maze.VISIBILITY_SEEN; // tile (3, 2)
        return d;
    }

    private static ChunkData.ItemData item(Item.ItemType type, int x, int y) {
        ChunkData.ItemData i = new ChunkData.ItemData();
        i.type = type;
        i.x = x;
        i.y = y;
        return i;
    }

    @Test
    public void lootCountsOnlyItemsOnSeenTiles() {
        ChunkData d = chunk();
        d.items.add(item(Item.ItemType.values()[0], 1, 1));
        d.items.add(item(Item.ItemType.values()[0], 0, 0));
        assertEquals(1, ChunkSummary.of(d).getLoot());
    }

    @Test
    public void aSeenReturnPortalIsAPortalNotLoot() {
        ChunkData d = chunk();
        d.items.add(item(Item.ItemType.BIOME_RETURN_PORTAL, 3, 2));
        ChunkSummary s = ChunkSummary.of(d);
        assertTrue(s.hasReturnPortal());
        assertEquals(0, s.getLoot());

        ChunkData hidden = chunk();
        hidden.items.add(item(Item.ItemType.BIOME_RETURN_PORTAL, 0, 3));
        assertFalse(ChunkSummary.of(hidden).hasReturnPortal());
    }

    @Test
    public void aSeenHerosBonesAreAGraveWithTheirEpitaph() {
        ChunkData d = chunk();
        ChunkData.SceneryData bones = new ChunkData.SceneryData();
        bones.x = 1;
        bones.y = 1;
        bones.bonesData = new BonesData();
        bones.bonesData.playerName = "Aldric";
        bones.bonesData.epitaph = "Fell to a minotaur";
        bones.bonesData.defeated = true;
        d.scenery.add(bones);
        ChunkData.SceneryData unseen = new ChunkData.SceneryData();
        unseen.x = 2;
        unseen.y = 2;
        unseen.bonesData = new BonesData();
        d.scenery.add(unseen);

        ChunkSummary s = ChunkSummary.of(d);
        assertEquals(1, s.getGraves().size());
        ChunkSummary.Grave g = s.getGraves().get(0);
        assertEquals("Aldric", g.getName());
        assertEquals("Fell to a minotaur", g.getEpitaph());
        assertTrue(g.isDefeated());
        assertFalse(g.isAwakened());
    }

    @Test
    public void laddersCountOnlyOnceSeen() {
        ChunkData d = chunk();
        ChunkData.LadderData down = new ChunkData.LadderData();
        down.x = 3;
        down.y = 2;
        down.type = Ladder.LadderType.DOWN;
        ChunkData.LadderData up = new ChunkData.LadderData();
        up.x = 0;
        up.y = 0;
        up.type = Ladder.LadderType.UP;
        d.ladders.add(down);
        d.ladders.add(up);
        ChunkSummary s = ChunkSummary.of(d);
        assertTrue(s.hasDownLadder());
        assertFalse(s.hasUpLadder());
    }

    @Test
    public void noSaveMeansNothingKnown() {
        ChunkSummary s = ChunkSummary.of(null);
        assertEquals(0, s.getLoot());
        assertTrue(s.getGraves().isEmpty());
        assertTrue(s.getStations().isEmpty());
    }
}
