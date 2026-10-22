package com.bpm.minotaur.gamedata.history;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.save.ItemSaveData;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** What a Nemesis took from the lives it ended, and gives up when it falls (#284). */
public class NemesisHoardTest {

    private static ItemSaveData item(Item.ItemType type) {
        return new ItemSaveData(type, ItemColor.GRAY);
    }

    @Test
    public void itKeepsTheGearOfEveryLifeItTookAndGivesItAllUpOnce() {
        NemesisHoard hoard = new NemesisHoard();
        assertTrue(hoard.take(2, item(Item.ItemType.AXE)));
        assertTrue(hoard.take(2, item(Item.ItemType.SHIELD)));
        assertTrue(hoard.take(5, item(Item.ItemType.BOW)));

        List<ItemSaveData> spoils = hoard.claim(2);
        assertEquals(2, spoils.size());
        assertTrue(hoard.claim(2).isEmpty());
        assertEquals(1, hoard.claim(5).size());
    }

    @Test
    public void whatEveryLifeGetsBackIsNotTaken() {
        NemesisHoard hoard = new NemesisHoard();
        assertFalse(hoard.take(2, item(com.bpm.minotaur.gamedata.item.FathersSword.TYPE)));
        assertFalse(hoard.take(2, item(Item.ItemType.BRASS_LANTERN)));
        assertFalse(hoard.take(2, null));
        assertTrue(hoard.claim(2).isEmpty());
    }

    @Test
    public void theHoardSurvivesTheSave() {
        NemesisHoard hoard = new NemesisHoard();
        hoard.take(2, item(Item.ItemType.AXE));
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        NemesisHoard loaded = json.fromJson(NemesisHoard.class, json.toJson(hoard));
        assertEquals(Item.ItemType.AXE, loaded.claim(2).get(0).type);
    }
}
