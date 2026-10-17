package com.bpm.minotaur.gamedata.polymorph;

import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The choices polymorph makes, kept apart from the engine so they can be tested: which monster a
 * monster becomes, and which items count as the "same kind" as another.
 *
 * <p>A monster becomes another monster of about its own level (so the fight stays fair), and never
 * its own kind. An item becomes another item of the same category, so a weapon is always another
 * weapon and a potion another potion.
 */
public final class PolymorphRules {

    /** How far above or below its own level a polymorphed monster may land. */
    public static final int LEVEL_WINDOW = 3;

    private PolymorphRules() {
    }

    /**
     * A new type for a monster, or null if there is none to offer.
     *
     * @param levels  base level of every monster type that can be made
     * @param current the type it is now (never chosen again)
     * @param anyLevel true for the high-level spell, which ignores the level window
     */
    public static Monster.MonsterType pickMonsterType(Map<Monster.MonsterType, Integer> levels,
            Monster.MonsterType current, int level, boolean anyLevel, Random rng) {
        List<Monster.MonsterType> pool = new ArrayList<>();
        for (Map.Entry<Monster.MonsterType, Integer> e : levels.entrySet()) {
            if (e.getKey() == current) {
                continue;
            }
            if (anyLevel || Math.abs(e.getValue() - level) <= LEVEL_WINDOW) {
                pool.add(e.getKey());
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(rng.nextInt(pool.size()));
    }

    /**
     * What kind of thing an item is, for deciding what it can become; null when it cannot be
     * polymorphed at all (keys, containers, treasure, shelter fittings and the like).
     */
    public static String categoryOf(ItemTemplate t) {
        if (t == null || t.isKey || t.isContainer || t.isTreasure || t.isBeltClip) {
            return null;
        }
        if (t.isWeapon) {
            return t.isRanged ? "weapon:ranged" : "weapon:melee";
        }
        if (t.isAmmunition) {
            return "ammunition";
        }
        if (t.isShield) {
            return "armor:shield";
        }
        if (t.isArmor) {
            if (t.isHelmet) return "armor:helmet";
            if (t.isGauntlets) return "armor:gauntlets";
            if (t.isBoots) return "armor:boots";
            if (t.isLegs) return "armor:legs";
            if (t.isArms) return "armor:arms";
            if (t.isCloak) return "armor:cloak";
            if (t.isTorso) return "armor:torso";
            return "armor";
        }
        if (t.isFood) {
            return "food";
        }
        if (t.isGem) {
            return "gem";
        }
        return null;
    }

    /** Item types of the same category as the original, other than itself; empty if it cannot change. */
    public static <T> List<T> sameCategory(Map<T, String> categories, T original) {
        List<T> out = new ArrayList<>();
        String mine = categories.get(original);
        if (mine == null) {
            return out;
        }
        for (Map.Entry<T, String> e : categories.entrySet()) {
            if (e.getKey() != original && mine.equals(e.getValue())) {
                out.add(e.getKey());
            }
        }
        return out;
    }
}
