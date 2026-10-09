package com.bpm.minotaur.gamedata.history;

import com.bpm.minotaur.gamedata.history.beast.MegabeastCatalog;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** Plan T3.1, T3.2, D19, D33-D35: the beasts of the deep, in the history. */
public class MegabeastTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Test
    public void everyArchetypeHasABodyThatIsARealMonster() throws IOException {
        JsonValue monsters = new JsonReader().parse(DoctrineCatalogTest.readAsset("data/monsters.json"));
        Set<String> known = new HashSet<>();
        for (JsonValue m : monsters) known.add(m.name);
        MegabeastCatalog beasts = catalog.beasts();
        assertTrue("about six archetypes", beasts.archetypes.size() >= 6);
        for (MegabeastCatalog.Archetype a : beasts.archetypes) {
            assertTrue(a.id + " body " + a.body, known.contains(a.body));
            assertTrue(a.id, a.hp > 0);
        }
        for (MegabeastCatalog.Weakness w : beasts.weaknesses) {
            com.bpm.minotaur.gamedata.DamageType.valueOf(w.damageType);
        }
        assertFalse(beasts.materials.isEmpty());
        assertFalse(beasts.breaths.isEmpty());
    }

    @Test
    public void threeToSixPerWorldAndOneAlwaysWithinReach() {
        for (long seed = 0; seed < 100; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            int n = w.megabeasts().size();
            assertTrue("seed " + seed + ": " + n, n >= 3 && n <= 6);
            int shallowest = Integer.MAX_VALUE;
            Set<String> names = new HashSet<>();
            for (Megabeast b : w.megabeasts()) {
                shallowest = Math.min(shallowest, b.lairLevel);
                assertTrue(b.lairLevel >= 2 && b.lairLevel <= 8);
                assertTrue("unique: " + b.name, names.add(b.name));
            }
            assertTrue("seed " + seed + " has a lair within strata 1-2", shallowest <= 3);
        }
    }

    @Test
    public void beastsRaidAndAreBargainedWithInTheChronicle() {
        int raids = 0, bargains = 0, stirs = 0;
        for (long seed = 0; seed < 30; seed++) {
            for (HistoryEvent e : HistorySimulator.prehistory(seed, catalog).events()) {
                if (e.type == EventType.MEGABEAST_RAID) raids++;
                if (e.type == EventType.MEGABEAST_BARGAIN) bargains++;
                if (e.type == EventType.MEGABEAST_STIRS) stirs++;
                if (e.type.name().startsWith("MEGABEAST")) assertTrue(e.toString(), e.beastId >= 0);
            }
        }
        assertTrue("stirs " + stirs, stirs >= 90);
        assertTrue("raids " + raids, raids >= 30);
        assertTrue("bargains " + bargains, bargains >= 3);
    }

    @Test
    public void aSlainBeastIsChronicledAndStaysDead() {
        HistoryWorld w = HistorySimulator.prehistory(5L, catalog);
        Megabeast b = w.megabeasts().get(0);
        HistorySimulator.applyDeed(w, new PlayerDeed(PlayerDeed.Kind.SLEW_MEGABEAST, b.id, 0));
        assertFalse(b.isAlive());
        assertEquals(EventType.MEGABEAST_SLAIN, w.events().get(w.events().size() - 1).type);
        int before = w.events().size();
        HistorySimulator.applyDeed(w, new PlayerDeed(PlayerDeed.Kind.SLEW_MEGABEAST, b.id, 0));
        assertEquals("cannot die twice", before, w.events().size());
        for (int i = 0; i < 40; i++) HistorySimulator.tickSeason(w, catalog);
        for (HistoryEvent e : w.events().subList(before, w.events().size())) {
            assertNotEquals("the dead do not raid", b.id, e.beastId);
        }
    }
}
