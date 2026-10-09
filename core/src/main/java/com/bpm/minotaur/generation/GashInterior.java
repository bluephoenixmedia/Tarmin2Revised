package com.bpm.minotaur.generation;

import com.bpm.minotaur.gamedata.boss.SealLord;
import com.bpm.minotaur.gamedata.history.Doctrine;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.managers.SealCourt;

/**
 * The inside of a gash (plan T1.13, D43): the strata under a seal site, from the first stratum
 * down to the lord's court, wear the doctrine of the house that holds the gash. Asked on every
 * load, so when a war gives the gash to another house its interior changes on the next entry.
 */
public final class GashInterior {

    /** The first stratum of a gash: the one just under the seal site. */
    public static final int TOP_LEVEL = 2;

    private GashInterior() {
    }

    /** The interior at {@code level} under the seal site of {@code road}, or null if that is no gash. */
    public static Stratum of(HistoryWorld world, DoctrineCatalog catalog, int road, int level) {
        if (world == null || catalog == null || level < TOP_LEVEL || level > SealCourt.COURT_LEVEL) return null;
        int gash = SealLord.gashIndexForRoad(road);
        if (gash < 0) return null;
        House holder = world.gashHolder(gash);
        Doctrine doctrine = holder != null ? catalog.get(holder.doctrineId) : null;
        return doctrine != null ? Stratum.interior(doctrine.interiorTheme) : null;
    }
}
