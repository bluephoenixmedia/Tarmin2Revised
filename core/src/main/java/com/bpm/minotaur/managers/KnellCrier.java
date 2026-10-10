package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.text.ChronicleGrammar;
import com.bpm.minotaur.gamedata.history.text.Knell;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens to the history for Tarmin's Knell (plan K4): only what happened since it last listened,
 * so a loaded game never tolls old news; only what is great enough to toll; one gong's worth at a
 * time -- up to {@link #MAX_LINES} lines, then Tarmin waving at the rest. What tolls becomes known
 * history, readable at the Lectern.
 */
public final class KnellCrier {

    /** Lines told under one gong; a crowded day ends in a shrug. */
    public static final int MAX_LINES = 3;

    private final HistoryManager history;
    private final ChronicleGrammar grammar;
    private final DoctrineCatalog catalog;
    private int heard;

    public KnellCrier(HistoryManager history, ChronicleGrammar grammar, DoctrineCatalog catalog) {
        this.history = history;
        this.grammar = grammar;
        this.catalog = catalog;
        this.heard = history.world().events().size();
    }

    /** Whether this crier listens to {@code h}: a new history (a new world) wants a new crier. */
    public boolean listensTo(HistoryManager h) {
        return history == h;
    }

    /** What Tarmin tells now, in order; empty if nothing great has happened since last asked. */
    public List<String> listen() {
        List<HistoryEvent> events = history.world().events();
        List<String> lines = new ArrayList<>();
        int great = 0;
        for (int i = heard; i < events.size(); i++) {
            HistoryEvent e = events.get(i);
            if (!Knell.tolls(history.world(), e)) continue;
            history.unlock(e.id);
            if (great++ < MAX_LINES) lines.add(Knell.line(history.world(), e, grammar, catalog));
        }
        heard = events.size();
        if (great > MAX_LINES) {
            lines.add(com.bpm.minotaur.ui.UiGlyphs.sanitize(grammar.knellMore(new java.util.Random(heard))));
        }
        return lines;
    }
}
