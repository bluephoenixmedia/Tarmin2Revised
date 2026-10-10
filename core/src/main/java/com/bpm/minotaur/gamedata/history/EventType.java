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
    QUEST_DONE,
    /** The player laid a trophy of the houses before a megabeast, and it took their peace. */
    MEGABEAST_PACIFIED,
    /**
     * A house ({@code houseA}) secretly buys a mortal settlement ({@code place}; {@code detail} is
     * its id). Never a rumour: only found in fragments (plan T4.6, D40).
     */
    TOWN_SUBORNED,
    /** The bought settlement betrays the mortals to its house, and turns on strangers. */
    TOWN_BETRAYED,
    /**
     * A seal lord ({@code figureB} of {@code houseB}) killed in its own court by another hand:
     * {@code houseA}'s, or -1 for a beast of the deep. Its slayer carries off the seal.
     */
    LORD_SLAIN_IN_COURT,
    /** A seeker swore to a house ({@code houseA}) at its war camp (Living War W20). */
    SEEKER_SWORN,
    /** A seeker broke its oath to a house ({@code houseB}) (W21): the Maze's betrayal by a mortal. */
    OATH_BROKEN,
    /** A house's lord ({@code houseA}) granted a sworn seeker the seal of its gash (W26). */
    SEAL_GRANTED
}
