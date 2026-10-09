package com.bpm.minotaur.gamedata.history;

import java.util.EnumSet;
import java.util.Set;

/**
 * A kind of found history (plan D12, T1.10), and which events it can tell of. What a fragment
 * says is chosen when it is read, from the events of its kind the player has not yet learned.
 */
public enum FragmentKind {
    /** A page torn from some house's chronicle: anything of note. */
    PAGE(EnumSet.of(EventType.HOUSE_FOUNDED, EventType.SUCCESSION, EventType.DISPUTED_SUCCESSION,
            EventType.USURPATION, EventType.ASSASSINATION, EventType.MARRIAGE_PACT, EventType.BETRAYAL,
            EventType.HOSTAGE_TAKEN, EventType.HOSTAGE_EXECUTED, EventType.HOUSE_EXTINGUISHED, EventType.SEEKER_FELL)),
    /** A herald's proclamation, nailed up or carried by an impaled herald: matters of state. */
    PROCLAMATION(EnumSet.of(EventType.WAR_DECLARED, EventType.PEACE, EventType.SEAT_SEIZED,
            EventType.VASSAL_OATH, EventType.VASSAL_REBELLION, EventType.SLAIN_BY_PLAYER,
            EventType.TARMIN_ASCENDANT)),
    /** A torn banner from a battlefield. */
    BANNER(EnumSet.of(EventType.BATTLE, EventType.SEAT_SEIZED, EventType.HOUSE_EXTINGUISHED)),
    /** One of the Void's lore glyphs: the oldest story, how Tarmin-Zul came through. */
    VOID_GLYPH(EnumSet.of(EventType.TARMIN_ZUL_RISES));

    private final Set<EventType> tells;

    FragmentKind(Set<EventType> tells) {
        this.tells = tells;
    }

    public boolean tells(EventType type) {
        return tells.contains(type);
    }
}
