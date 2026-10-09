package com.bpm.minotaur.managers;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What a broken army leaves on the field for a player who stayed (plan D18, T2.7): the arms and
 * armour of the fallen, a signet ring and a torn banner as trophies, and, if a lord fell, that
 * lord's own blade.
 */
public final class BattleSpoils implements BattleDirector.Spoils {

    static final int FIELD_MIN = 3;
    static final int FIELD_MAX = 6;
    /** Gear a common soldier might carry; anything pricier is a lord's. */
    static final int FIELD_VALUE_MAX = 300;
    static final int LORD_ENCHANTMENT = 3;

    private final ItemDataManager items;
    private final AssetManager assets;
    private final Random rng;
    /** A house's name by id, for naming its trophies; may answer null. */
    private final java.util.function.IntFunction<String> houseNames;

    public BattleSpoils(ItemDataManager items, AssetManager assets, long seed) {
        this(items, assets, seed, id -> null);
    }

    public BattleSpoils(ItemDataManager items, AssetManager assets, long seed, java.util.function.IntFunction<String> houseNames) {
        this.items = items;
        this.assets = assets;
        this.rng = new Random(seed);
        this.houseNames = houseNames;
    }

    @Override
    public void drop(Maze maze, GridPoint2 near, int loserHouseId, boolean lordFell) {
        List<Item.ItemType> field = new ArrayList<>();
        List<Item.ItemType> weapons = new ArrayList<>();
        for (Item.ItemType t : Item.ItemType.values()) {
            ItemTemplate tpl = items.getTemplate(t);
            if (tpl == null || !(tpl.isWeapon || tpl.isArmor)) continue;
            if (tpl.baseValue > 0 && tpl.baseValue <= FIELD_VALUE_MAX) field.add(t);
            if (tpl.isWeapon) weapons.add(t);
        }
        // A lord carries one of the costliest third of the weapons there are.
        weapons.sort((x, y) -> Integer.compare(items.getTemplate(y).baseValue, items.getTemplate(x).baseValue));
        List<Item.ItemType> fine = weapons.subList(0, (weapons.size() + 2) / 3);
        int count = FIELD_MIN + rng.nextInt(FIELD_MAX - FIELD_MIN + 1);
        for (int i = 0; i < count && !field.isEmpty(); i++) {
            place(maze, near, field.get(rng.nextInt(field.size())), ItemColor.TAN);
        }
        // The trophies are the broken house's own: a reeve who wants one house's signet wants that one.
        String house = houseNames.apply(loserHouseId);
        trophy(place(maze, near, Item.ItemType.SIGNET_RING, ItemColor.YELLOW), loserHouseId, house != null ? "Signet of " + house : null);
        trophy(place(maze, near, Item.ItemType.TORN_BANNER, ItemColor.TAN), loserHouseId, house != null ? "Banner of " + house : null);
        if (lordFell && !fine.isEmpty()) {
            Item blade = place(maze, near, fine.get(rng.nextInt(fine.size())), ItemColor.PURPLE);
            if (blade != null) blade.setEnchantment(blade.getEnchantment() + LORD_ENCHANTMENT);
        }
    }

    private static void trophy(Item item, int houseId, String name) {
        if (item == null) return;
        item.setTrophyHouseId(houseId);
        if (name != null) item.setName(com.bpm.minotaur.ui.UiGlyphs.sanitize(name));
    }

    private Item place(Maze maze, GridPoint2 near, Item.ItemType type, ItemColor color) {
        GridPoint2 at = WorldManager.findSafeArrivalTile(maze, near.x + rng.nextInt(5) - 2, near.y + rng.nextInt(5) - 2);
        if (at == null) return null;
        Item item = items.createItem(type, at.x, at.y, color, assets);
        if (item != null) maze.addItem(item);
        return item;
    }
}
