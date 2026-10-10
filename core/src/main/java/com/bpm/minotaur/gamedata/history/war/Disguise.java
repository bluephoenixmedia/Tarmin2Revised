package com.bpm.minotaur.gamedata.history.war;

import com.bpm.minotaur.gamedata.history.Doctrine;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.HistoryWorld;

/**
 * The rogue's lucky disguise (Living War W16): a seeker polymorphed into a monster type that a
 * house fields is taken by that house's soldiers for one of their own -- until they strike, or
 * one of them looks too closely for {@link #SCRUTINY_TURNS} turns at their side. The houses at
 * war with it see an enemy soldier, which they would have fought anyway.
 */
public final class Disguise {

    /** Turns a soldier spends beside a disguised seeker before it sees through them. */
    public static final int SCRUTINY_TURNS = 3;

    private Disguise() {
    }

    /** Whether house {@code houseId}'s soldiers take a seeker in the body of {@code formType} for kin. */
    public static boolean passesAmong(HistoryWorld world, DoctrineCatalog catalog, int houseId, String formType) {
        if (world == null || catalog == null || formType == null) return false;
        House h = world.house(houseId);
        if (h == null || h.isExtinct()) return false;
        Doctrine d = catalog.get(h.doctrineId);
        return d != null && d.roster.contains(formType);
    }
}
