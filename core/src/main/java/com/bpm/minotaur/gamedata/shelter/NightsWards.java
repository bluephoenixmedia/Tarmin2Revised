package com.bpm.minotaur.gamedata.shelter;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/**
 * The wards being readied for one night, none paid for until the sleeper lies down, so changing
 * one's mind costs nothing (Living War A64).
 */
public final class NightsWards {

    private final EnumMap<Ward, Boolean> castNotLaid = new EnumMap<>(Ward.class);

    /** Whether this ward can be readied with what is carried. */
    public boolean canLay(Ward ward, boolean carried, boolean fire) {
        return carried && open(ward, fire);
    }

    /** Whether this ward can be cast, the MP of those already readied being spoken for. */
    public boolean canCast(Ward ward, boolean known, int mp, boolean fire) {
        return known && mp - mpDue() >= ward.mpCost && open(ward, fire);
    }

    private boolean open(Ward ward, boolean fire) {
        return !castNotLaid.containsKey(ward) && !(ward == Ward.CONCEALMENT && fire);
    }

    /** No fire can burn inside Concealment. */
    public boolean canKindle() {
        return !castNotLaid.containsKey(Ward.CONCEALMENT);
    }

    public void lay(Ward ward) {
        castNotLaid.put(ward, false);
    }

    public void cast(Ward ward) {
        castNotLaid.put(ward, true);
    }

    public void takeDown(Ward ward) {
        castNotLaid.remove(ward);
    }

    public boolean has(Ward ward) {
        return castNotLaid.containsKey(ward);
    }

    public boolean isCast(Ward ward) {
        return Boolean.TRUE.equals(castNotLaid.get(ward));
    }

    public Set<Ward> wards() {
        return castNotLaid.isEmpty() ? EnumSet.noneOf(Ward.class) : EnumSet.copyOf(castNotLaid.keySet());
    }

    /** MP the spells among them will take. */
    public int mpDue() {
        int due = 0;
        for (Ward ward : castNotLaid.keySet()) {
            if (isCast(ward)) due += ward.mpCost;
        }
        return due;
    }

    /** Free rounds on this sleeper: an Alarm gives none away, however drawn the ambush (A63, A64). */
    public int freeRounds(boolean fire) {
        return has(Ward.ALARM) ? 0 : Bivouac.freeRounds(fire);
    }

    /** What a Shield will take of the free rounds. */
    public int shield() {
        return has(Ward.SHIELD) ? Ward.SHIELD_ABSORBS : 0;
    }

    /** Whether this ambusher passes the sleeper by. */
    public boolean hiddenFrom(int intelligence, boolean spellcaster) {
        return has(Ward.CONCEALMENT) && Ward.concealmentHidesFrom(intelligence, spellcaster);
    }

    /** What woke the sleeper, an army being no thing an Alarm can hear coming: it wakes them when it arrives (A62, A64). */
    public Waking waking(boolean armyArrived, int ambushersFound) {
        if (armyArrived) return Waking.ARMY;
        if (ambushersFound <= 0) return Waking.MORNING;
        return has(Ward.ALARM) ? Waking.ALARM : Waking.AMBUSH;
    }

    public enum Waking { MORNING, ALARM, AMBUSH, ARMY }
}
