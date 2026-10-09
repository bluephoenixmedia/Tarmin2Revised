package com.bpm.minotaur.gamedata.history;

/** Every kind of event the history records (plan D24, plus founding and endings). */
public enum EventType {
    HOUSE_FOUNDED,
    TARMIN_ZUL_RISES,
    SUCCESSION,
    DISPUTED_SUCCESSION,
    USURPATION,
    NATURAL_DEATH,
    ASSASSINATION,
    MARRIAGE_PACT,
    BETRAYAL,
    VASSAL_OATH,
    VASSAL_REBELLION,
    WAR_DECLARED,
    BATTLE,
    SEAT_SEIZED,
    HOSTAGE_TAKEN,
    HOSTAGE_EXECUTED,
    PEACE,
    HOUSE_EXTINGUISHED,
    SLAIN_BY_PLAYER,
    SEEKER_FELL,
    /** The Doom Clock reached a new stage: Tarmin-Zul grows stronger by bleeding the houses (D42). */
    TARMIN_ASCENDANT,
    /** A megabeast wakes in the deep and takes its lair. */
    MEGABEAST_STIRS,
    /** A megabeast falls on a house ({@code houseB}). */
    MEGABEAST_RAID,
    /** A house ({@code houseA}) sets a megabeast on its rival ({@code houseB}). */
    MEGABEAST_BARGAIN,
    /** The player killed a megabeast. */
    MEGABEAST_SLAIN,
    /** The player did a town's work ({@code place} is the town). */
    QUEST_DONE
}
