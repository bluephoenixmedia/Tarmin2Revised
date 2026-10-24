package com.bpm.minotaur.gamedata.arts;

import com.bpm.minotaur.gamedata.item.WeaponFamily;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The body's Arts on cooldown (#245; spec section 5), counted in the player's turns. The soul keeps
 * its Arts from life to life; the cooldowns belong to one body and die with it.
 *
 * <p>It also keeps the capstones' windows (#248): while one is open, its Family's Adept Passive is
 * certain, and when it closes the Family's Expert Active is handed its cooldown. A window lasts a
 * few turns and is not saved.
 */
public final class ArtCooldowns {

    private final Map<CombatArt, Integer> left = new EnumMap<>(CombatArt.class);
    /** Started this turn: the turn an Art is used in does not count toward its cooldown. */
    private final Set<CombatArt> fresh = EnumSet.noneOf(CombatArt.class);
    /** Open capstone windows: turns left, and the cooldown handed to the Expert Active after. */
    private final Map<WeaponFamily, Integer> certain = new EnumMap<>(WeaponFamily.class);
    private final Map<WeaponFamily, Integer> handOff = new EnumMap<>(WeaponFamily.class);
    private final Set<WeaponFamily> freshWindows = EnumSet.noneOf(WeaponFamily.class);

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

    /**
     * A capstone opens {@code family}'s window: its Adept Passive is certain for the {@code turns}
     * that follow, and then its Expert Active cools for {@code expertCooldown} (none if 0).
     */
    public void openWindow(WeaponFamily family, int turns, int expertCooldown) {
        if (family == null || turns <= 0) return;
        certain.put(family, turns);
        handOff.put(family, expertCooldown);
        freshWindows.add(family);
    }

    /** Whether a capstone has made {@code family}'s Adept Passive certain this turn. */
    public boolean isCertain(WeaponFamily family) {
        return certain.containsKey(family);
    }

    /** One of the player's turns passes. */
    public void tick() {
        left.replaceAll((art, turns) -> fresh.contains(art) ? turns : turns - 1);
        left.values().removeIf(turns -> turns <= 0);
        fresh.clear();
        certain.replaceAll((family, turns) -> freshWindows.contains(family) ? turns : turns - 1);
        freshWindows.clear();
        certain.entrySet().removeIf(window -> {
            if (window.getValue() > 0) return false;
            // The window closes: the Family's Expert Active is handed its cooldown.
            CombatArt expert = CombatArt.of(window.getKey(), Rank.EXPERT);
            Integer cooldown = handOff.remove(window.getKey());
            if (expert != null && cooldown != null && cooldown > 0) left.put(expert, cooldown);
            return true;
        });
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
