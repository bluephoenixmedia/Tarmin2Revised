package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistorySaveData;
import com.bpm.minotaur.gamedata.history.Nemesis;
import com.bpm.minotaur.gamedata.history.PlayerDeed;
import com.bpm.minotaur.gamedata.history.SeekerDeath;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/** "World Log: the Nemesis is born and endures" (#283). */
public class NemesisHistoryTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static SeekerDeath byKobold(String victim) {
        return SeekerDeath.by("a Kobold", "KOBOLD", -1).of(victim).at(3, 4, -2);
    }

    @Test
    public void whatKillsALifeBecomesANamedNemesisInThatRegion() {
        HistoryManager m = HistoryManager.create(11L, catalog);
        m.recordSeekerFell(byKobold("Brannoc Thatch"));

        List<Nemesis> nemeses = m.world().nemeses();
        assertEquals(1, nemeses.size());
        Nemesis n = nemeses.get(0);
        assertEquals("KOBOLD", n.monsterType);
        assertEquals(3, n.level);
        assertEquals(4, n.chunkX);
        assertEquals(-2, n.chunkY);
        assertEquals(1, n.tally());
        assertTrue(n.isAlive());
        assertEquals("Slayer of Brannoc Thatch", n.epithet());
        assertFalse(n.name.isEmpty());
        assertEquals(n.title(), UiGlyphs.sanitize(n.title()));
    }

    @Test
    public void aNemesisThatKillsAgainGainsTheLifeNotANewName() {
        HistoryManager m = HistoryManager.create(11L, catalog);
        m.recordSeekerFell(byKobold("Brannoc Thatch"));
        Nemesis n = m.world().nemeses().get(0);
        m.recordSeekerFell(SeekerDeath.by(n.name, "KOBOLD", -1).nemesis(n.id).of("Skrag Ratbite").at(5, 0, 0));

        assertEquals(1, m.world().nemeses().size());
        assertEquals(2, n.tally());
        assertEquals("Bane of Two Lives", n.epithet());
        assertEquals("it stays where it first killed", 3, n.level);
    }

    @Test
    public void aFigureOfTheHousesThatKillsBecomesANemesisUnderItsOwnName() {
        HistoryManager m = HistoryManager.create(11L, catalog);
        Figure f = m.world().livingHouses().stream()
                .flatMap(h -> m.world().livingMembers(h).stream()).findFirst().orElseThrow(AssertionError::new);
        m.recordSeekerFell(SeekerDeath.by(f.name, "ORC", f.houseId).figure(f.id).of("Brannoc Thatch").at(2, 1, 1));
        m.recordSeekerFell(SeekerDeath.by(f.name, "ORC", f.houseId).figure(f.id).of("Skrag Ratbite").at(2, 1, 1));

        assertEquals(1, m.world().nemeses().size());
        Nemesis n = m.world().nemeses().get(0);
        assertEquals(f.name, n.name);
        assertEquals(f.id, n.figureId);
        assertEquals(2, n.tally());
    }

    @Test
    public void theReplayRaisesTheSameNemeses() {
        HistoryManager live = HistoryManager.create(11L, catalog);
        live.recordSeekerFell(byKobold("Brannoc Thatch"));
        Nemesis first = live.world().nemeses().get(0);
        live.recordSeekerFell(SeekerDeath.by(first.name, "KOBOLD", -1).nemesis(first.id).of("Skrag Ratbite").at(3, 4, -2));
        live.recordSeekerFell(SeekerDeath.by("an Ogre", "OGRE", -1).of("Maud Fenn").at(6, 0, 1));

        HistorySaveData save = live.toSave();
        HistoryManager loaded = HistoryManager.fromSave(11L, save, catalog);
        assertEquals(2, loaded.world().nemeses().size());
        for (int i = 0; i < 2; i++) {
            Nemesis a = live.world().nemeses().get(i);
            Nemesis b = loaded.world().nemeses().get(i);
            assertEquals(a.title(), b.title());
            assertEquals(a.tally(), b.tally());
            assertEquals(a.victims, b.victims);
            assertEquals(a.level, b.level);
        }
    }

    @Test
    public void anOlderDeathThatNamedNoKillerRaisesNoNemesis() {
        HistoryManager m = HistoryManager.create(11L, catalog);
        m.recordSeekerFell(SeekerDeath.by("the maze", null, -1).of("Brannoc Thatch").at(1, 0, 0));
        assertTrue(m.world().nemeses().isEmpty());
        assertEquals(1, m.world().seekersFallen());
    }

    @Test
    public void aSlainNemesisStaysDead() {
        HistoryManager m = HistoryManager.create(11L, catalog);
        m.recordSeekerFell(byKobold("Brannoc Thatch"));
        Nemesis n = m.world().nemeses().get(0);
        m.recordNemesisSlain(n.id);
        assertFalse(n.isAlive());

        HistoryManager loaded = HistoryManager.fromSave(11L, m.toSave(), catalog);
        assertFalse(loaded.world().nemeses().get(0).isAlive());
        assertTrue(m.toSave().deeds.stream().anyMatch(d -> d.kind == PlayerDeed.Kind.SLEW_NEMESIS));
    }

    @Test
    public void nemesisNamesAreTheSameForTheSameHistory() {
        HistoryManager a = HistoryManager.create(11L, catalog);
        HistoryManager b = HistoryManager.create(11L, catalog);
        a.recordSeekerFell(byKobold("Brannoc Thatch"));
        b.recordSeekerFell(byKobold("Brannoc Thatch"));
        assertEquals(a.world().nemeses().get(0).name, b.world().nemeses().get(0).name);
    }
}
