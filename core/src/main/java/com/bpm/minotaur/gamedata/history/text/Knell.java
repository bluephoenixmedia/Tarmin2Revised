package com.bpm.minotaur.gamedata.history.text;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;

import java.util.EnumSet;

/**
 * Tarmin's Knell (plan K2, K3): which of the history's events are great enough for Tarmin-Zul to
 * toll over the whole Maze, and what he says of them. Lesser news stays a shelter rumour.
 */
public final class Knell {

    /** Great whenever they happen. */
    private static final EnumSet<EventType> ALWAYS = EnumSet.of(EventType.SEAT_SEIZED, EventType.HOUSE_EXTINGUISHED,
            EventType.LORD_SLAIN_IN_COURT, EventType.SLAIN_BY_PLAYER, EventType.PEACE, EventType.MEGABEAST_STIRS,
            EventType.MEGABEAST_SLAIN, EventType.MEGABEAST_PACIFIED, EventType.TOWN_BETRAYED, EventType.TARMIN_ASCENDANT);
    /** Great when the head of a great house is the one who died. */
    private static final EnumSet<EventType> HEAD_OF_A_GREAT_HOUSE = EnumSet.of(EventType.SUCCESSION,
            EventType.USURPATION, EventType.DISPUTED_SUCCESSION);

    private Knell() {
    }

    /** Whether an event of this type can ever toll. */
    public static boolean couldToll(EventType type) {
        return ALWAYS.contains(type) || HEAD_OF_A_GREAT_HOUSE.contains(type) || type == EventType.BATTLE;
    }

    /** Whether {@code e} tolls the knell. */
    public static boolean tolls(HistoryWorld world, HistoryEvent e) {
        if (e == null) return false;
        if (ALWAYS.contains(e.type)) return true;
        if (HEAD_OF_A_GREAT_HOUSE.contains(e.type)) {
            House house = world.house(e.houseA);
            return house != null && (house.isGreat() || house.holdsCastle);
        }
        // A battle tolls when a lord or captain fell in it.
        return e.type == EventType.BATTLE && e.figureB >= 0;
    }

    /** What Tarmin says of {@code e}, in the player's head. */
    public static String line(HistoryWorld world, HistoryEvent e, ChronicleGrammar grammar, DoctrineCatalog catalog) {
        Chronicler voice = Chronicler.of(world, e, ChronicleGrammar.Bias.NEUTRAL, catalog);
        return com.bpm.minotaur.ui.UiGlyphs.sanitize(grammar.renderKnell(world, e, voice));
    }
}
