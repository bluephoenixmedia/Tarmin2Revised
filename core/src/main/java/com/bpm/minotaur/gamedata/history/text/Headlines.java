package com.bpm.minotaur.gamedata.history.text;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistoryWorld;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The news the player wakes to after a season passes (plan T1.6): the loudest events, as rumour. */
public final class Headlines {

    private Headlines() {
    }

    /** At most {@code max} rumours, the most consequential first-chosen, told in the order they happened. */
    public static List<String> of(HistoryWorld world, List<HistoryEvent> events, int max,
            ChronicleGrammar grammar, DoctrineCatalog catalog) {
        List<String> out = new ArrayList<>();
        for (HistoryEvent e : pick(events, max)) {
            out.add(grammar.render(world, e, Chronicler.of(world, e, ChronicleGrammar.Bias.NEUTRAL, catalog)));
        }
        return out;
    }

    /** The {@code max} loudest events, in the order they happened. */
    public static List<HistoryEvent> pick(List<HistoryEvent> events, int max) {
        List<HistoryEvent> ranked = new ArrayList<>();
        for (HistoryEvent e : events) {
            if (weight(e) > 0) ranked.add(e);
        }
        ranked.sort(Comparator.comparingInt(Headlines::weight).reversed().thenComparingInt(e -> e.id));
        List<HistoryEvent> chosen = new ArrayList<>(ranked.subList(0, Math.min(max, ranked.size())));
        chosen.sort(Comparator.comparingInt(e -> e.id));
        return chosen;
    }

    /** How loudly an event travels; zero for what nobody outside the house hears of. */
    static int weight(HistoryEvent e) {
        switch (e.type) {
            case SEAT_SEIZED:
            case TARMIN_ZUL_RISES:
            case TARMIN_ASCENDANT:
                return 10;
            case WAR_DECLARED:
            case HOUSE_EXTINGUISHED:
                return 8;
            case USURPATION:
            case ASSASSINATION:
            case BETRAYAL:
            case HOSTAGE_EXECUTED:
            case VASSAL_REBELLION:
                return 7;
            case BATTLE:
                return e.figureB >= 0 ? 7 : 4;
            case MEGABEAST_SLAIN:
                return 9;
            case MEGABEAST_RAID:
            case MEGABEAST_BARGAIN:
                return 7;
            case MEGABEAST_STIRS:
            case QUEST_DONE:
                return 6;
            case DISPUTED_SUCCESSION:
            case PEACE:
                return 5;
            case MARRIAGE_PACT:
            case VASSAL_OATH:
            case HOSTAGE_TAKEN:
            case SUCCESSION:
            case HOUSE_FOUNDED:
                return 3;
            default:
                return 0;
        }
    }
}
