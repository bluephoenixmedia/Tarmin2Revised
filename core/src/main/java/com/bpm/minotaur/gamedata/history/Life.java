package com.bpm.minotaur.gamedata.history;

import com.bpm.minotaur.gamedata.history.town.Allegiance;

/**
 * Who one Incarnation was before the soul woke in them (#268, #279): a condemned prisoner with a
 * name, a home settlement of the history, and how they came to the maze.
 */
public final class Life {

    public enum Origin {
        /** Taken from their settlement by Tarmin's Antlered Host. */
        RAIDED,
        /** Sent to the maze by a mortal power, for a crime. */
        TITHED
    }

    public final String name;
    public final int settlementId;
    /** The home settlement's name, kept so the life reads the same if the history changes. */
    public final String settlement;
    public final Allegiance allegiance;
    public final Origin origin;
    /** Who led the raid, for the raided; null for the tithed. */
    public final String raider;
    /** The crime, for the tithed; null for the raided. */
    public final String crime;
    /** The power that tithed them, for the tithed; null for the raided. */
    public final String kingdom;

    public Life(String name, int settlementId, String settlement, Allegiance allegiance, Origin origin,
            String raider, String crime, String kingdom) {
        this.name = name;
        this.settlementId = settlementId;
        this.settlement = settlement;
        this.allegiance = allegiance;
        this.origin = origin;
        this.raider = raider;
        this.crime = crime;
        this.kingdom = kingdom;
    }

    /** "Brenna Thatch of the Ashen Ford" */
    public String title() {
        return name + " of " + settlement;
    }

    /** "Taken from the Ashen Ford in a raid by the Antlered Host." */
    public String howTheyCame() {
        if (origin == Origin.RAIDED) {
            return "Taken from " + settlement + " in a raid by " + raider + ".";
        }
        return "Tithed to the maze by " + kingdom + " for " + crime + ".";
    }

    /** One line: who they were and how they came to the maze. */
    public String summary() {
        return title() + ". " + howTheyCame();
    }
}
