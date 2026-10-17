package com.bpm.minotaur.gamedata.history.text;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Archive Lectern's view of the history (plan D28, D29, T1.11): what the player has learned,
 * by house and by era, every entry told by both sides.
 */
public final class Annals {

    private static final String[] CENTURIES = {"The First Century", "The Second Century", "The Third Century"};
    private static final String PRESENT = "The Present Age";

    /** One telling of an event. */
    public static final class Account {
        public final String byline;
        public final String text;

        Account(String byline, String text) {
            this.byline = byline;
            this.text = text;
        }
    }

    /** One learned event: its year and its two contradicting accounts. */
    public static final class Entry {
        public final int eventId;
        public final int year;
        /** As the house that acted tells it. */
        public final Account ownSide;
        /** As its enemy tells it. */
        public final Account otherSide;

        Entry(int eventId, int year, Account ownSide, Account otherSide) {
            this.eventId = eventId;
            this.year = year;
            this.ownSide = ownSide;
            this.otherSide = otherSide;
        }
    }

    public static final class Era {
        public final String title;
        public final List<Entry> entries = new ArrayList<>();

        Era(String title) {
            this.title = title;
        }
    }

    private Annals() {
    }

    /** Houses the learned history names, great houses first, then the castle, then the rest. */
    public static List<House> knownHouses(HistoryWorld world, Set<Integer> unlocked) {
        List<House> great = new ArrayList<>();
        List<House> rest = new ArrayList<>();
        for (House h : world.houses()) {
            boolean named = false;
            for (int id : unlocked) {
                HistoryEvent e = world.event(id);
                if (e != null && e.involvesHouse(h.id)) {
                    named = true;
                    break;
                }
            }
            if (!named) continue;
            if (h.isGreat()) great.add(h);
            else if (h.holdsCastle) rest.add(0, h);
            else rest.add(h);
        }
        great.addAll(rest);
        return great;
    }

    /** Learned events in order, grouped by era; {@code houseId} -1 shows every house. */
    public static List<Era> entries(HistoryWorld world, Set<Integer> unlocked, int houseId,
            ChronicleGrammar grammar, DoctrineCatalog catalog) {
        List<Era> eras = new ArrayList<>();
        Era current = null;
        for (HistoryEvent e : world.events()) {
            if (!unlocked.contains(e.id) || (houseId >= 0 && !e.involvesHouse(houseId))) continue;
            int year = e.season / HistoryWorld.SEASONS_PER_YEAR + 1;
            String title = eraOf(year);
            if (current == null || !current.title.equals(title)) {
                current = new Era(title);
                eras.add(current);
            }
            current.entries.add(new Entry(e.id, year,
                    account(world, e, ChronicleGrammar.Bias.FOR, grammar, catalog),
                    account(world, e, ChronicleGrammar.Bias.AGAINST, grammar, catalog)));
        }
        return eras;
    }

    static String eraOf(int year) {
        int century = (year - 1) / 100;
        return century < CENTURIES.length ? CENTURIES[century] : PRESENT;
    }

    private static Account account(HistoryWorld world, HistoryEvent e, ChronicleGrammar.Bias bias,
            ChronicleGrammar grammar, DoctrineCatalog catalog) {
        Chronicler teller = Chronicler.of(world, e, bias, catalog);
        return new Account(teller.byline, grammar.render(world, e, teller));
    }
}
