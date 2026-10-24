package com.bpm.minotaur.gamedata.history;

import com.bpm.minotaur.managers.HistoryManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.*;

/** "Ages, Tarmin slain and the throne" (#291) and "a monster takes Tarmin's throne" (#292). */
public class AgesHistoryTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Test
    public void returningTheTreasureEndsTheAgeWakesTheGodsAndAHouseTakesTheName() {
        HistoryManager m = HistoryManager.create(11L, catalog);
        assertEquals(0, m.world().age());
        assertEquals("Tarmin-Zul", m.world().tarminTitle());
        m.recordGodSpent(OldGod.IXXATH);

        AgeEnding e = m.recordTarminSlain(false, "Brannoc Thatch");
        assertEquals(AgeEnding.Kind.RETURNED, e.kind);
        assertEquals(0, e.age);
        assertEquals(1, m.world().age());
        assertFalse("the sealed Rift wakes every spent god", m.world().isSpent(OldGod.IXXATH));
        assertTrue(m.world().tarminTitle(), m.world().tarminTitle().startsWith("Tarmin-"));
        assertNotEquals("Tarmin-Zul", m.world().tarminTitle());
    }

    @Test
    public void takingTheThroneMakesTheLifeTheNewTarmin() {
        HistoryManager m = HistoryManager.create(11L, catalog);
        m.recordGodSpent(OldGod.IXXATH);
        AgeEnding e = m.recordTarminSlain(true, "Brannoc Thatch");
        assertEquals(AgeEnding.Kind.THRONE, e.kind);
        assertEquals("Tarmin-Brannoc", m.world().tarminTitle());
        assertTrue("the dark gods stay dark", m.world().isSpent(OldGod.IXXATH));
        assertEquals(1, m.world().ageEndings().size());
    }

    @Test
    public void theAgesReplay() {
        HistoryManager m = HistoryManager.create(11L, catalog);
        m.recordTarminSlain(true, "Brannoc Thatch");
        m.recordTarminSlain(false, "Maud Fenn");
        HistoryManager loaded = HistoryManager.fromSave(11L, m.toSave(), catalog);
        assertEquals(2, loaded.world().age());
        assertEquals(m.world().tarminTitle(), loaded.world().tarminTitle());
        assertEquals(m.world().ageEndings().get(1).newTitle, loaded.world().ageEndings().get(1).newTitle);
    }

    /** A death that brings the Hunger to a new Era stage, at Hunger {@code hunger}. */
    private static SeekerDeath stageDeath(int hunger) {
        return SeekerDeath.by("a beast", "KOBOLD", -1).of("A Life").at(2, 0, 0).hunger(hunger);
    }

    @Test
    public void aMonsterTakesTheThroneRarelyAtANewEraAndAlwaysTheSameWay() {
        int usurped = 0;
        int histories = 400;
        Set<AgeEnding.Kind> kinds = EnumSet.noneOf(AgeEnding.Kind.class);
        for (long seed = 0; seed < histories; seed++) {
            HistoryManager m = HistoryManager.create(seed, catalog);
            m.recordSeekerFell(stageDeath(10));
            if (m.world().age() > 0) {
                usurped++;
                AgeEnding e = m.world().ageEndings().get(0);
                kinds.add(e.kind);
                assertTrue(e.newTitle, e.newTitle.startsWith("Tarmin-"));
                assertTrue(e.fullTitle(), e.fullTitle().contains(", who was "));
                HistoryManager again = HistoryManager.fromSave(seed, m.toSave(), catalog);
                assertEquals("the replay raises the same usurper", e.newTitle, again.world().ageEndings().get(0).newTitle);
            }
        }
        assertTrue("about one Era in twenty: " + usurped, usurped >= 8 && usurped <= 40);
        assertTrue("a House's siege is the likeliest way: " + kinds, kinds.contains(AgeEnding.Kind.SIEGE));
    }

    @Test
    public void onlyANewEraRollsForTheThrone() {
        for (long seed = 0; seed < 200; seed++) {
            HistoryManager m = HistoryManager.create(seed, catalog);
            m.recordSeekerFell(stageDeath(11));
            m.recordSeekerFell(stageDeath(-1));
            assertEquals("a death inside an Era rolls nothing", 0, m.world().age());
        }
    }
}
