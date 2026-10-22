package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.Nemesis;
import com.bpm.minotaur.gamedata.history.PlayerDeed;
import com.bpm.minotaur.gamedata.history.SeekerDeath;
import com.bpm.minotaur.gamedata.worldlog.Fact;
import org.junit.Test;

import static org.junit.Assert.*;

/** Felling a Nemesis is a notable line in the life that did it (#284). */
public class NemesisFallTest {

    @Test
    public void slayingANemesisIsALineNamingItAndWhoItTook() throws java.io.IOException {
        HistoryManager m = HistoryManager.create(11L, DoctrineCatalogTest.loadCatalog());
        Nemesis n = m.recordSeekerFell(SeekerDeath.by("a Kobold", "KOBOLD", -1).of("Brannoc Thatch").at(3, 0, 0));
        Fact line = WorldLogManager.factFor(new PlayerDeed(PlayerDeed.Kind.SLEW_NEMESIS, n.id, 0), m.world());
        assertNotNull(line);
        assertEquals(Fact.Kind.NEMESIS_SLAIN, line.kind);
        assertTrue(line.plain(), line.plain().contains(n.name));
        assertTrue(line.plain(), line.plain().contains("Brannoc Thatch"));
        assertTrue(Fact.Kind.NEMESIS_SLAIN.weight > Fact.Kind.BOSS_SLAIN.weight);
        assertTrue(Fact.Kind.NEMESIS_SLAIN.weight < Fact.Kind.WARDEN_DESTROYED.weight);
    }
}
