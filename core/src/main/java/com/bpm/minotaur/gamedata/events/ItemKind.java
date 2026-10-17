package com.bpm.minotaur.gamedata.events;

import com.bpm.minotaur.gamedata.item.Item;

/**
 * The kinds of carried item a choice event can ask for or take.
 *
 * <p>Not {@link com.bpm.minotaur.gamedata.item.ItemCategory}: that reports scrolls and potions as
 * USEFUL, so "a scroll" has to be asked of the item itself.
 */
public enum ItemKind {
    ANY, FOOD, SCROLL, POTION, RING, WEAPON, RANGED_WEAPON, ARMOR, SHIELD, CURSED, UNBLESSED;

    public boolean matches(Item item) {
        if (item == null) {
            return false;
        }
        switch (this) {
            case FOOD:
                return item.isFood();
            case SCROLL:
                return item.isScroll();
            case POTION:
                return item.isPotion();
            case RING:
                return item.isRing();
            case WEAPON:
                return item.isWeapon();
            case RANGED_WEAPON:
                return item.isRanged();
            case ARMOR:
                return item.isArmor();
            case SHIELD:
                return item.isShield();
            case CURSED:
                return item.getBeatitude() == Item.Beatitude.CURSED;
            case UNBLESSED:
                return item.getBeatitude() != Item.Beatitude.BLESSED;
            case ANY:
            default:
                return true;
        }
    }
}
