package com.bpm.minotaur.gamedata.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a death takes from the Incarnation and what the Eternal Champion carries on (#206).
 *
 * <p>The death screen reads its two panels from here, so the copy lives next to the rule it
 * describes ({@link Player#beginNewIncarnation}) rather than in screen code. Every line is
 * plain ASCII in the font's set, so it survives {@code UiGlyphs.sanitize} unchanged.
 */
public final class IncarnationLedger {

    /** One label and value row on the death screen. */
    public static final class Line {
        public final String label;
        public final String value;

        Line(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    private IncarnationLedger() {
    }

    /** What dies with the Incarnation: its level, its stats and the gear it carried. */
    public static List<Line> lost(int carriedItemsLost) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("LEVEL AND XP", "GONE"));
        lines.add(new Line("STATS AND SKILLS", "GONE"));
        lines.add(new Line("WEAPON MASTERY", "GONE"));
        int items = Math.max(0, carriedItemsLost);
        lines.add(new Line("CARRIED GEAR", items + (items == 1 ? " ITEM" : " ITEMS")));
        lines.add(new Line("YOUR GEAR", "LEFT WITH YOUR REMAINS, SOMEWHERE IN THE MAZE"));
        return Collections.unmodifiableList(lines);
    }

    /**
     * What survives: the soul remembers its spells and its sword, the world remembers the rest
     * (CONTEXT.md, Eternal Champion).
     */
    public static List<Line> kept() {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("KNOWN SPELLS", "SOUL"));
        lines.add(new Line("COMBAT ARTS", "SOUL"));
        lines.add(new Line("FATHER'S SWORD", "SOUL"));
        lines.add(new Line("DIVINITY", "WORLD")); // never lost, not even on death (#310)
        lines.add(new Line("UNLOCKS", "WORLD"));
        lines.add(new Line("ALTAR STATIONS", "WORLD"));
        lines.add(new Line("SHELTER CHEST", "WORLD"));
        lines.add(new Line("TARMIN'S HUNGER", "WORLD"));
        return Collections.unmodifiableList(lines);
    }
}
