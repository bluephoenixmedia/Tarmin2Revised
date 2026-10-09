package com.bpm.minotaur.gamedata.history;

/** Why a house went to war (plan D25). Stored on every declaration so the prose can say why. */
public enum CasusBelli {
    /** A kinsman was assassinated, executed as a hostage, or cut down. */
    SLAIN_KIN,
    /** An alliance was broken against them. */
    BROKEN_PACT,
    /** Their blood is held hostage. */
    HOSTAGE_HELD,
    /** Their seat was taken, or they were forced to kneel. */
    USURPED_SEAT,
    /** They want a gash they do not hold. */
    CLAIM_TO_GASH,
    /** They want Tarmin-Zul's castle. */
    CLAIM_TO_CASTLE,
    /** A vassal throwing off its liege. */
    INDEPENDENCE,
    /** An ally struck while the other was weak. */
    TREACHERY,
    /** Nothing but appetite. */
    AMBITION
}
