package com.bpm.minotaur.gamedata.progression;

import com.bpm.minotaur.gamedata.progression.ShelterAltar.Station;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * The shelter names what your Divinity can buy (#310). Entering a shelter with its Altar, or
 * waking in one after a death, posts one line naming the best station the banked Divinity
 * affords and the player has not bought. It speaks at most once a visit, and says nothing when
 * nothing on offer is affordable.
 */
public final class AltarNudge {

    /** Most useful first: the Bed, then what a fresh expedition feels soonest. Stations not named here follow in their own order. */
    static final List<Station> PREFERENCE = Arrays.asList(
            Station.BED, Station.LANTERN, Station.CAMPFIRE, Station.STASH_CHEST, Station.TRAINING_DUMMY,
            Station.CRAFTING_BENCH, Station.CARTOGRAPHER_DESK, Station.ARCHIVE_LECTERN);

    private boolean inShelter;

    /**
     * Called as each turn ends.
     *
     * @param onShelterGround whether the player stands in a shelter that holds an Altar
     * @param owned           whether a station is already bought
     * @param offered         whether a station is offered at all (a portal waits for its depth)
     * @return the line to post on entering, or null
     */
    public String step(boolean onShelterGround, int divinity, Predicate<Station> owned, Predicate<Station> offered) {
        if (!onShelterGround) {
            inShelter = false;
            return null;
        }
        if (inShelter) return null;
        inShelter = true;
        Station best = bestAffordable(divinity, owned, offered);
        return best != null ? line(best) : null;
    }

    /** Waking in the shelter after a death begins a new visit. */
    public void wake() {
        inShelter = false;
    }

    /** The first station in {@link #PREFERENCE} that is offered, not owned and affordable; or null. */
    static Station bestAffordable(int divinity, Predicate<Station> owned, Predicate<Station> offered) {
        List<Station> order = new ArrayList<>(PREFERENCE);
        for (Station s : Station.values()) if (!order.contains(s)) order.add(s);
        for (Station s : order) {
            if (s.getCost() <= divinity && !owned.test(s) && offered.test(s)) return s;
        }
        return null;
    }

    static String line(Station station) {
        return "The Altar stirs: your Divinity could buy the " + station.getDisplayName()
                + " (" + station.getCost() + ").";
    }
}
