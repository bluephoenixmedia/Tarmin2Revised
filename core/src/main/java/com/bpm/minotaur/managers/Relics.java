package com.bpm.minotaur.managers;

import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.LootTable;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemCategory;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A doctrine relic (Living War W25): a weapon in a house's own style, given for every third
 * contract kept, rolled from the loot's modifier pool as a fixed rare -- two modifiers, never
 * fewer.
 */
public final class Relics {

    /** Modifiers a relic always carries. */
    static final int MODIFIERS = 2;

    /** Each doctrine's weapon: the Chainwrights' flail, the Penitents' great mace, and so on. */
    private static final Map<String, String> WEAPON = Map.of(
            "flensed_choir", "FLAIL_BLADEBACK",
            "chainwrights", "FLAIL_CHAIN",
            "hollow_court", "POLEARM_BEC_DE_CORBIN",
            "gilded_rot", "MORNING_STAR",
            "ashen_penitents", "MACE_GREAT",
            "ossuary_lords", "POLEARM_GLAIVE_GUISARME4",
            "glass_hunger", "KUKRI",
            "antlered_host", "AXE_BATTLE");

    private Relics() {
    }

    /** The weapon type a doctrine's relic is. */
    public static String weaponOf(String doctrineId) {
        return WEAPON.getOrDefault(doctrineId, "AXE_BATTLE");
    }

    /** {@link #MODIFIERS} weapon modifiers a relic of {@code level} carries, each a different kind. */
    public static List<ItemModifier> modifiers(int level, Random r) {
        List<LootTable.ModInfo> pool = new ArrayList<>();
        for (LootTable.ModInfo m : LootTable.MODIFIER_POOL) {
            if (m.category == ItemCategory.WAR_WEAPON && level >= m.minLevel) pool.add(m);
        }
        List<ItemModifier> out = new ArrayList<>();
        while (out.size() < MODIFIERS && !pool.isEmpty()) {
            LootTable.ModInfo m = pool.remove(r.nextInt(pool.size()));
            boolean taken = false;
            for (ItemModifier o : out) if (o.type == m.type) taken = true;
            if (taken) continue;
            int value = m.maxBonus > m.minBonus ? m.minBonus + r.nextInt(m.maxBonus - m.minBonus + 1) : m.minBonus;
            out.add(new ItemModifier(m.type, value, m.displayName.contains("+") ? "+" + value : m.displayName));
        }
        return out;
    }

    /** Forges a relic of {@code houseName}'s doctrine for a seeker of {@code level}; null if it cannot. */
    public static Item forge(ItemDataManager items, AssetManager assets, String doctrineId, String houseName, int level,
            Random r, int x, int y) {
        if (items == null) return null;
        Item item;
        try {
            item = items.createItem(Item.ItemType.valueOf(weaponOf(doctrineId)), x, y, ItemColor.GOLD, assets);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (item == null) return null;
        for (ItemModifier m : modifiers(level, r)) item.addModifier(m);
        item.setFriendlyName(com.bpm.minotaur.ui.UiGlyphs.sanitize("Relic of " + houseName));
        return item;
    }
}
