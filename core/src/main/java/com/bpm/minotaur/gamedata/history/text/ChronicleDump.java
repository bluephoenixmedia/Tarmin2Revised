package com.bpm.minotaur.gamedata.history.text;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistoryWorld;

/** A whole chronicle as plain text, every event in all three voices, for reviewing the prose. */
public final class ChronicleDump {

    private ChronicleDump() {
    }

    public static String render(HistoryWorld world, ChronicleGrammar grammar, DoctrineCatalog catalog, int maxEvents) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(maxEvents, world.events().size());
        for (int i = 0; i < n; i++) {
            HistoryEvent e = world.events().get(i);
            sb.append("== ").append(e.type).append(" (event ").append(e.id).append(")\n");
            for (ChronicleGrammar.Bias bias : ChronicleGrammar.Bias.values()) {
                Chronicler teller = Chronicler.of(world, e, bias, catalog);
                sb.append("  ").append(teller.byline).append(": ").append(grammar.render(world, e, teller)).append('\n');
            }
        }
        return sb.toString();
    }
}
