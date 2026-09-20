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

    private static final Map<Item.ItemType, Integer> RELOAD_TURNS = new EnumMap<>(Item.ItemType.class);
    private static final Map<Item.ItemType, Integer> NOISE_RADIUS = new EnumMap<>(Item.ItemType.class);

    static {
        // Noise is the axis these five differ on. With their authored damage dice kept,
        // it is what stops the blunderbuss simply retiring the pistol.
        register(Item.ItemType.PISTOL_STARWHEEL, SIDEARM_RELOAD_TURNS, 9);
        register(Item.ItemType.TUFENK, LONG_GUN_RELOAD_TURNS, 10);
        register(Item.ItemType.MUSKET, LONG_GUN_RELOAD_TURNS, 12);
        register(Item.ItemType.ARQUEBUS, LONG_GUN_RELOAD_TURNS, 12);
        register(Item.ItemType.BLUNDERBUS, LONG_GUN_RELOAD_TURNS, 16);
    }

    private static void register(Item.ItemType type, int reloadTurns, int noiseRadius) {
        RELOAD_TURNS.put(type, reloadTurns);
        NOISE_RADIUS.put(type, Math.max(MIN_AUDIBLE_RADIUS, noiseRadius));
    }

    private FirearmProfile() {
    }

    /** Every weapon that burns powder. */
    public static Set<Item.ItemType> all() {
        return Collections.unmodifiableSet(EnumSet.copyOf(RELOAD_TURNS.keySet()));
    }

    public static boolean isFirearm(Item.ItemType type) {
        return type != null && RELOAD_TURNS.containsKey(type);
    }

    /** Convenience for the common case of asking about a held weapon. */
    public static boolean isFirearmHeld(Item item) {
        return item != null && isFirearm(item.getType());
    }

    /** Turns spent reloading after a shot, or 0 for anything that is not a firearm. */
    public static int reloadTurns(Item.ItemType type) {
        Integer turns = RELOAD_TURNS.get(type);
        return turns != null ? turns : 0;
    }

    /** How far the report carries, in tiles, or 0 for a weapon that makes no report. */
    public static int noiseRadius(Item.ItemType type) {
        Integer radius = NOISE_RADIUS.get(type);
        return radius != null ? radius : 0;
    }
}
