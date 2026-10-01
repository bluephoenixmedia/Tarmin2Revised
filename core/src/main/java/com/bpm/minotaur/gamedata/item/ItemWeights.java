package com.bpm.minotaur.gamedata.item;

/**
 * What an item weighs, for encumbrance.
 *
 * <p>The item data gives almost everything the default weight of 1, so the stored number says
 * nothing about how heavy a thing is: a plate cuirass and a ring both read 1. A weight written
 * into the data (anything other than that default) is honoured. Otherwise it is derived from what
 * the item is, so the ordering is right even where the data was never filled in. The values are
 * tuning.
 */
public final class ItemWeights {

    /** The template default, which in practice means "never set". */
    private static final float UNSET = 1.0f;

    private ItemWeights() {
    }

    public static float of(Item item) {
        if (item == null) {
            return 0f;
        }
        ItemTemplate t = item.getTemplate();
        float own = (t != null && t.weight != UNSET) ? t.weight : derive(item);

        float contents = 0f;
        if (item.isContainer()) {
            for (Item inside : item.getContents()) {
                contents += of(inside);
            }
        }
        return own + contents;
    }

    private static float derive(Item item) {
        if (item.isContainer()) {
            return 2f;
        }
        if (item.isShield()) {
            return 8f;
        }
        if (item.isArmor()) {
            return armourSlotWeight(item) * armourCategoryFactor(item);
        }
        if (item.isRing()) {
            return 0.2f;
        }
        if (item.isAmulet()) {
            return 0.5f;
        }
        if (item.isWeapon()) {
            return weaponWeight(item);
        }
        if (item.isScroll()) {
            return 0.2f;
        }
        if (item.isKey()) {
            return 0.3f;
        }
        if (item.isGem()) {
            return 0.3f;
        }
        if (item.isTreasure()) {
            return 0.5f;
        }
        if (item.isSpellbook()) {
            return 3f;
        }
        // Potions, food, wands, tools and whatever else is carried in a pocket.
        return 1f;
    }

    private static float armourSlotWeight(Item item) {
        if (item.isTorso()) {
            return 15f;
        }
        if (item.isLegs()) {
            return 8f;
        }
        if (item.isArms()) {
            return 5f;
        }
        if (item.isHelmet() || item.isBoots()) {
            return 4f;
        }
        if (item.isGauntlets() || item.isCloak()) {
            return 3f;
        }
        return 5f;
    }

    private static float armourCategoryFactor(Item item) {
        String category = item.getArmorCategory();
        if ("HEAVY".equals(category)) {
            return 1.5f;
        }
        if ("MEDIUM".equals(category)) {
            return 1.0f;
        }
        return 0.6f;
    }

    private static float weaponWeight(Item item) {
        if (item.isThrown()) {
            return 1f;
        }
        if (item.isRanged()) {
            return item.getType() == Item.ItemType.CROSSBOW ? 7f : 3f;
        }
        if (item.isTwoHanded() || item.isReach()) {
            return 10f;
        }
        if (item.isFinesse()) {
            return 2f;
        }
        return 5f;
    }
}
