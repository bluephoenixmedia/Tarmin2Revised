package com.bpm.minotaur.gamedata.history.text;

import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Names the history gives its figures, which depend on who is speaking (plan D29): the same
 * lord is the Steadfast to their own house and the Usurper to everyone else.
 */
public final class Epithets {

    private static final Map<HistoryWorld, Index> CACHE = new WeakHashMap<>();

    private Epithets() {
    }

    /**
     * The epithet {@code viewerHouseId}'s chronicler gives {@code f}, or null if none fits.
     * A viewer of the figure's own house is sympathetic; anyone else is not.
     */
    public static String of(HistoryWorld world, Figure f, int viewerHouseId) {
        if (f == null || f.ageless) return null;
        boolean own = viewerHouseId >= 0 && viewerHouseId == f.houseId;
        Set<EventType> deeds = index(world).deeds.get(f.id);
        if (deeds != null) {
            if (deeds.contains(EventType.USURPATION)) return own ? "the Steadfast" : "the Usurper";
            if (deeds.contains(EventType.DISPUTED_SUCCESSION)) return own ? "the Restorer" : "Half-Crowned";
            if (deeds.contains(EventType.ASSASSINATION)) return own ? "the Quiet" : "the Poisoner";
            if (deeds.contains(EventType.HOSTAGE_EXECUTED)) return own ? "the Just" : "the Hangman";
            if (deeds.contains(EventType.BETRAYAL)) return own ? "the Shrewd" : "the Faithless";
        }
        if (!index(world).lords.contains(f.id) || f.traits.isEmpty()) return null;
        switch (f.traits.get(0)) {
            case CRUEL: return own ? "the Stern" : "the Cruel";
            case CRAVEN: return own ? "the Prudent" : "the Craven";
            case WRATHFUL: return own ? "the Fierce" : "the Mad";
            case AMBITIOUS: return own ? "the Great" : "the Grasping";
            case ZEALOT: return own ? "the Devout" : "the Fanatic";
            case PATIENT: return own ? "the Patient" : "the Slow";
            case CUNNING: return own ? "the Wise" : "the Sly";
            case HONOURABLE: return own ? "the Just" : "the Naive";
            default: return null;
        }
    }

    /** What the Maze calls the player, from what they have done to it (plan D10). */
    public static String player(HistoryWorld world) {
        int kills = 0;
        boolean gashLord = false;
        Index idx = index(world);
        for (HistoryEvent e : world.events()) {
            if (e.type != EventType.SLAIN_BY_PLAYER) continue;
            kills++;
            House h = world.house(e.houseB);
            if (h != null && h.isGreat() && idx.lords.contains(e.figureB)) gashLord = true;
        }
        if (gashLord) return "Gashbreaker";
        if (kills >= 3) return "Lordsbane";
        if (kills >= 1) return "the Bloodied";
        return "the Seeker";
    }

    private static synchronized Index index(HistoryWorld world) {
        Index idx = CACHE.get(world);
        if (idx == null || idx.eventCount != world.events().size()) {
            idx = new Index(world);
            CACHE.put(world, idx);
        }
        return idx;
    }

    /** Which figures did what, and who ever ruled; rebuilt whenever the history grows. */
    private static final class Index {
        final int eventCount;
        final Map<Integer, Set<EventType>> deeds = new HashMap<>();
        final Set<Integer> lords = new java.util.HashSet<>();

        Index(HistoryWorld world) {
            eventCount = world.events().size();
            for (HistoryEvent e : world.events()) {
                if (e.figureA < 0) continue;
                deeds.computeIfAbsent(e.figureA, k -> EnumSet.noneOf(EventType.class)).add(e.type);
                switch (e.type) {
                    case HOUSE_FOUNDED:
                    case SUCCESSION:
                    case DISPUTED_SUCCESSION:
                    case USURPATION:
                    case TARMIN_ZUL_RISES:
                        lords.add(e.figureA);
                        break;
                    default:
                        break;
                }
            }
            for (House h : world.houses()) {
                if (h.lordId >= 0) lords.add(h.lordId);
            }
        }
    }
}
