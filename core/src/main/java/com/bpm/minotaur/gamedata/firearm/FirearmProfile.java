package com.bpm.minotaur.gamedata.firearm;

import com.bpm.minotaur.gamedata.item.Item;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * What it costs to fire black powder.
 *
 * <p>A firearm hits far harder than a bow at the same range -- an arquebus rolls 2d8
 * against a longbow's 1d8 -- and the balance is carried entirely by what firing costs:
 * a multi-turn reload you are committed to, a noise pulse that wakes monsters well
 * beyond sight, and ammunition that never litters the floor.
 *
 * <p>These are the three knobs. They are placeholders in the honest sense: reload length
 * and noise radius cannot be judged from a table, only at the pace combat actually runs,
 * and are expected to move after play.
 */
public final class FirearmProfile {

    /** Turns to reload a one-handed sidearm. */
    private static final int SIDEARM_RELOAD_TURNS = 2;
    /** Turns to reload anything shouldered. */
    private static final int LONG_GUN_RELOAD_TURNS = 3;

    /**
     * Player sight is 8 tiles. Every firearm is heard past it on purpose -- a gun only
     * a monster already staring at you can hear has no drawback at all.
     */
    private static final int MIN_AUDIBLE_RADIUS = 9;

    /** Everything that distinguishes one firearm from another, on one line each. */
    private static final class Spec {
        final int reloadTurns;
        final int noiseRadius;
        final int minDepth;

        Spec(int reloadTurns, int noiseRadius, int minDepth) {
            this.reloadTurns = reloadTurns;
            this.noiseRadius = Math.max(MIN_AUDIBLE_RADIUS, noiseRadius);
            this.minDepth = minDepth;
        }
    }

    private static final Map<Item.ItemType, Spec> SPECS = new EnumMap<>(Item.ItemType.class);

    static {
        // Noise and depth are the axes these five differ on. With their authored damage
        // dice kept, noise is what stops the blunderbuss simply retiring the pistol, and
        // depth is what stops a level-1 character finding a 2d8 weapon.
        //                                          reload  noise  depth
        SPECS.put(Item.ItemType.PISTOL_STARWHEEL, new Spec(SIDEARM_RELOAD_TURNS, 9, 2));
        SPECS.put(Item.ItemType.BLUNDERBUS, new Spec(LONG_GUN_RELOAD_TURNS, 16, 2));
        SPECS.put(Item.ItemType.TUFENK, new Spec(LONG_GUN_RELOAD_TURNS, 10, 6));
        SPECS.put(Item.ItemType.ARQUEBUS, new Spec(LONG_GUN_RELOAD_TURNS, 12, 8));
        SPECS.put(Item.ItemType.MUSKET, new Spec(LONG_GUN_RELOAD_TURNS, 12, 10));
    }

    private FirearmProfile() {
    }

    /** Every weapon that burns powder. */
    public static Set<Item.ItemType> all() {
        return Collections.unmodifiableSet(EnumSet.copyOf(SPECS.keySet()));
    }

    public static boolean isFirearm(Item.ItemType type) {
        return type != null && SPECS.containsKey(type);
    }

    /** Turns spent reloading after a shot, or 0 for anything that is not a firearm. */
    public static int reloadTurns(Item.ItemType type) {
        Spec spec = SPECS.get(type);
        return spec != null ? spec.reloadTurns : 0;
    }

    /** How far the report carries, in tiles, or 0 for a weapon that makes no report. */
    public static int noiseRadius(Item.ItemType type) {
        Spec spec = SPECS.get(type);
        return spec != null ? spec.noiseRadius : 0;
    }

    /**
     * Shallowest depth this weapon may be found at; 1 for anything that is not a
     * firearm. Kept here beside reload and noise so a new firearm cannot be added with
     * a reload time but no depth and silently drop from level 1.
     */
    public static int minDepth(Item.ItemType type) {
        Spec spec = SPECS.get(type);
        return spec != null ? spec.minDepth : 1;
    }
}
