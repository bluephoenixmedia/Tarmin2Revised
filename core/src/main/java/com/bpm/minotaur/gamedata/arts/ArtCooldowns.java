package com.bpm.minotaur.gamedata.arts;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The body's Arts on cooldown (#245; spec section 5), counted in the player's turns. The soul keeps
 * its Arts from life to life; the cooldowns belong to one body and die with it.
 */
public final class ArtCooldowns {

    private final Map<CombatArt, Integer> left = new EnumMap<>(CombatArt.class);
    /** Started this turn: the turn an Art is used in does not count toward its cooldown. */
    private final Set<CombatArt> fresh = EnumSet.noneOf(CombatArt.class);

    /** {@code art} was used: it cools for the {@code turns} of the player's turns that follow. */
    public void start(CombatArt art, int turns) {
        if (art == null) return;
        if (turns > 0) {
            left.put(art, turns);
            fresh.add(art);
        } else {
            left.remove(art);
        }
    }

    /** Turns until {@code art} is ready again; 0 when it is ready. */
    public int turnsLeft(CombatArt art) {
        Integer turns = left.get(art);
        return turns != null ? turns : 0;
    }

    public boolean isCooling(CombatArt art) {
        return turnsLeft(art) > 0;
    }

    /** One of the player's turns passes. */
    public void tick() {
        left.replaceAll((art, turns) -> fresh.contains(art) ? turns : turns - 1);
        left.values().removeIf(turns -> turns <= 0);
        fresh.clear();
    }

    /** For the save: turns left by Art name. */
    public Map<String, Integer> toSave() {
        Map<String, Integer> out = new LinkedHashMap<>();
        left.forEach((art, turns) -> out.put(art.name(), turns));
        return out;
    }

    /** Restores the save's cooldowns; names that are no Art are dropped. */
    public void restore(Map<String, Integer> saved) {
        left.clear();
        if (saved == null) return;
        saved.forEach((name, turns) -> {
            try {
                if (turns != null && turns > 0) left.put(CombatArt.valueOf(name), turns);
            } catch (IllegalArgumentException ignored) {
                // An Art removed since the save was written.
            }
        });
    }
}
