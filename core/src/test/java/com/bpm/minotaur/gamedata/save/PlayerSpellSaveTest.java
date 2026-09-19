package com.bpm.minotaur.gamedata.save;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.spells.SpellDataManager;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Known Spells, unlocked Spell Slots and prepared slots are persistent progression:
 * they must survive a save/load round trip.
 */
public class PlayerSpellSaveTest {

    private Json json;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        SpellDataManager.getInstance().load();
        json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
    }

    private Player roundTrip(Player original) {
        String saved = json.toJson(new PlayerSaveData(original));
        PlayerSaveData restored = json.fromJson(PlayerSaveData.class, saved);
        Player loaded = new Player(0, 0);
        restored.applyToPlayer(loaded, null, null);
        return loaded;
    }

    @Test
    public void knownSpellsSlotsAndPreparedSpellsSurviveSaveAndLoad() {
        Player player = new Player(2, 2);
        player.setUnlockedSpellSlots(3);
        player.learnSpellId("FIREBALL");
        player.learnSpellId("MAGIC_MISSILE");
        player.prepareSpell(2, "FIREBALL");

        Player loaded = roundTrip(player);

        assertEquals(3, loaded.getUnlockedSpellSlots());
        assertEquals(Arrays.asList("MOTE_OF_LIGHT", "FIREBALL", "MAGIC_MISSILE"), loaded.getKnownSpellIds());
        assertEquals("MOTE_OF_LIGHT", loaded.getPreparedSpell(0));
        assertNull(loaded.getPreparedSpell(1));
        assertEquals("FIREBALL", loaded.getPreparedSpell(2));
    }

    @Test
    public void clearedSlotStaysClearedAfterLoad() {
        Player player = new Player(2, 2);
        player.prepareSpell(0, null);

        Player loaded = roundTrip(player);

        assertNull(loaded.getPreparedSpell(0));
        assertTrue(loaded.getKnownSpellIds().contains("MOTE_OF_LIGHT"));
    }

    @Test
    public void legacySaveKeepsStartingSpellbookAndMapsMatchingNames() {
        // A save written before spell ids were persisted only carried the legacy list.
        PlayerSaveData legacy = new PlayerSaveData();
        legacy.knownSpells.add("MOTE_OF_LIGHT");
        legacy.knownSpells.add("FIREBALL");
        legacy.knownSpells.add("IRON_SKIN"); // no spells.json entry: dropped

        Player loaded = new Player(0, 0);
        legacy.applyToPlayer(loaded, null, null);

        assertEquals(Arrays.asList("MOTE_OF_LIGHT", "FIREBALL"), loaded.getKnownSpellIds());
        assertEquals("MOTE_OF_LIGHT", loaded.getPreparedSpell(0));
        assertEquals(1, loaded.getUnlockedSpellSlots());
    }
}
