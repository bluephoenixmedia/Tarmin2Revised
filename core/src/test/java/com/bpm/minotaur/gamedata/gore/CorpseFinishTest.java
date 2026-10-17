package com.bpm.minotaur.gamedata.gore;

import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Scenery;
import org.junit.Test;

import static org.junit.Assert.*;

public class CorpseFinishTest {

    @Test
    public void aCharredBodyIsStillCharredAfterASave() {
        Scenery body = new Scenery(Scenery.SceneryType.MONSTER_REMAINS, 3, 4);
        body.setCorpseFinish(CorpseFinish.CHARRED);

        ChunkData.SceneryData saved = new ChunkData.SceneryData(body);

        assertEquals("CHARRED", saved.corpseFinish);
        assertEquals(CorpseFinish.CHARRED, CorpseFinish.parse(saved.corpseFinish));
    }

    @Test
    public void aPlainBodySavesNoFinish() {
        Scenery body = new Scenery(Scenery.SceneryType.MONSTER_REMAINS, 3, 4);
        assertNull(new ChunkData.SceneryData(body).corpseFinish);
    }

    @Test
    public void anUnknownOrMissingFinishIsAPlainBody() {
        assertEquals(CorpseFinish.NONE, CorpseFinish.parse(null));
        assertEquals(CorpseFinish.NONE, CorpseFinish.parse("SPLATTERED"));
    }
}
