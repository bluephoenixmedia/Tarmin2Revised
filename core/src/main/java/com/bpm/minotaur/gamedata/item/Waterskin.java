package com.bpm.minotaur.gamedata.item;

import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.player.Thirst;

import java.util.List;

/**
 * What a waterskin does with its contents (#193): it holds {@link Thirst#WATERSKIN_CAPACITY}
 * drinks of one liquid and remembers which, so a skin filled at a blood pool stays
 * "Waterskin (Blood)" until it is emptied. Rain tops up a skin that is empty or holds water.
 * The numbers live in {@link Thirst}; this class only moves liquid in and out.
 */
public final class Waterskin {

    private Waterskin() {
    }

    /** One draught poured out of a skin or taken from the map. */
    public static final class Draught {
        public final LiquidType liquid;
        public final boolean strata;

        public Draught(LiquidType liquid, boolean strata) {
            this.liquid = liquid;
            this.strata = strata;
        }
    }

    public static boolean is(Item item) {
        return item != null && item.getType() == Item.ItemType.WATERSKIN;
    }

    public static boolean isEmpty(Item skin) {
        return skin.getCurrentCharges() <= 0 || skin.getLiquidContent() == null
                || skin.getLiquidContent() == LiquidType.NONE;
    }

    public static boolean isFull(Item skin) {
        return !isEmpty(skin) && skin.getCurrentCharges() >= skin.getMaxCharges();
    }

    /**
     * Fills the skin to the brim from a source. A different liquid is poured out first, so
     * the skin only ever holds one; topping up strata water with surface water still carries
     * the strata risk. Returns false if the liquid can't be drunk.
     */
    public static boolean fill(Item skin, LiquidType liquid, boolean strata) {
        if (!is(skin) || !Thirst.isDrinkable(liquid)) return false;
        if (skin.getMaxCharges() <= 0) skin.setMaxCharges(Thirst.WATERSKIN_CAPACITY);
        boolean sameLiquid = !isEmpty(skin) && skin.getLiquidContent() == liquid;
        skin.setStrataSource(sameLiquid ? skin.isStrataSource() || strata : strata);
        skin.setLiquidContent(liquid);
        skin.setCurrentCharges(skin.getMaxCharges());
        return true;
    }

    /** Pours one drink out, or null if the skin is empty. An emptied skin forgets its contents. */
    public static Draught drinkOne(Item skin) {
        if (!is(skin) || isEmpty(skin)) return null;
        Draught d = new Draught(skin.getLiquidContent(), skin.isStrataSource());
        skin.setCurrentCharges(skin.getCurrentCharges() - 1);
        if (skin.getCurrentCharges() <= 0) {
            skin.setLiquidContent(LiquidType.NONE);
            skin.setStrataSource(false);
        }
        return d;
    }

    /** Whether rain can add to this skin: it is empty or holds water, and is not full. */
    public static boolean catchesRain(Item skin) {
        if (!is(skin) || isFull(skin)) return false;
        return isEmpty(skin) || skin.getLiquidContent() == LiquidType.WATER;
    }

    /** Adds one drink of rainwater. Returns false if the skin can't take it. */
    public static boolean catchRain(Item skin) {
        if (!catchesRain(skin)) return false;
        if (skin.getMaxCharges() <= 0) skin.setMaxCharges(Thirst.WATERSKIN_CAPACITY);
        if (isEmpty(skin)) {
            skin.setLiquidContent(LiquidType.WATER);
            skin.setStrataSource(false);
            skin.setCurrentCharges(0);
        }
        skin.setCurrentCharges(skin.getCurrentCharges() + 1);
        return true;
    }

    /** The first carried skin that rain can add to, or null. */
    public static Item findRainCatcher(List<Item> items) {
        if (items == null) return null;
        for (Item i : items) {
            if (catchesRain(i)) return i;
        }
        return null;
    }

    /** The carried skin to fill: the first that is not already full of this liquid, or null. */
    public static Item findToFill(List<Item> items, LiquidType liquid) {
        if (items == null) return null;
        Item any = null;
        for (Item i : items) {
            if (!is(i)) continue;
            if (isEmpty(i) || (i.getLiquidContent() == liquid && !isFull(i))) return i;
            if (any == null && !(isFull(i) && i.getLiquidContent() == liquid)) any = i;
        }
        return any;
    }

    /** The first carried skin with something in it, or null. */
    public static Item findToDrink(List<Item> items) {
        if (items == null) return null;
        for (Item i : items) {
            if (is(i) && !isEmpty(i)) return i;
        }
        return null;
    }
}
