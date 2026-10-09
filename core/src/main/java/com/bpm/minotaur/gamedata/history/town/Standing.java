package com.bpm.minotaur.gamedata.history.town;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * How the underground's mortal powers regard the player (plan D41, T4.4). Each town keeps its
 * own account; what the player does in one town is also heard by its sister towns, at half the
 * weight, and by the powers it feuds with, the other way round at a quarter.
 */
public final class Standing {

    /** At or below this, a town turns its guards on the player. */
    public static final int HOSTILE = -25;
    public static final int MIN = -100;
    public static final int MAX = 100;

    /** A crime against a town: striking its guards. */
    public static final int CRIME = -40;
    /** A task done for a town. */
    public static final int FAVOUR = 20;

    private final Map<String, Integer> byTown = new HashMap<>();
    private final Map<Allegiance, Integer> byAllegiance = new EnumMap<>(Allegiance.class);

    public int of(Town town) {
        int v = town.welcome + byTown.getOrDefault(town.key, 0) + byAllegiance.getOrDefault(town.allegiance, 0);
        return Math.max(MIN, Math.min(MAX, v));
    }

    public boolean isHostile(Town town) {
        return of(town) <= HOSTILE;
    }

    /** {@code delta} in {@code town}: its sisters hear half of it; its rivals a quarter, reversed. */
    public void change(Town town, int delta) {
        byTown.merge(town.key, delta, Integer::sum);
        for (Allegiance a : Allegiance.values()) {
            if (a == town.allegiance) byAllegiance.merge(a, delta / 2, Integer::sum);
            else if (town.allegiance.feudsWith(a)) byAllegiance.merge(a, -delta / 4, Integer::sum);
        }
    }

    /** How a town's folk greet the player, in a word. */
    public String word(Town town) {
        int v = of(town);
        if (v <= HOSTILE) return "Hated";
        if (v < 0) return "Distrusted";
        if (v < 20) return "A stranger";
        if (v < 50) return "Welcome";
        return "Honoured";
    }

    // --- persistence: a flat list the save can hold

    public Map<String, Integer> towns() {
        return byTown;
    }

    public Map<Allegiance, Integer> allegiances() {
        return byAllegiance;
    }
}
