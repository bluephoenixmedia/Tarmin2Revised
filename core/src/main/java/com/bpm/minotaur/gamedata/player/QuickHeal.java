package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.PotionEffectType;

import java.util.List;
import java.util.function.Predicate;

/**
 * Quick heal (H, #190): picks the best healing item carried. A Poultice comes first; then
 * the weakest known healing potion that covers the wound, or the strongest when none does,
 * so a scratch never burns a rare draught.
 */
public final class QuickHeal {

    private QuickHeal() {
    }

    /**
     * @param missingHp how much HP the player is down
     * @param maxHp     the player's max HP, which sets a potion's floor
     * @param known     whether the player knows a potion effect, so an unknown one is never drunk
     * @return the item to use, or null when nothing carried heals
     */
    public static Item choose(List<Item> carried, int missingHp, int maxHp, Predicate<PotionEffectType> known) {
        Item best = null;
        int bestHeal = 0;
        boolean bestCovers = false;
        for (Item item : carried) {
            if (item == null) continue;
            if (item.getType() == Item.ItemType.POULTICE) return item;
            PotionEffectType effect = item.getTrueEffect();
            int heal = expectedHeal(effect, maxHp);
            if (heal <= 0) continue;
            if (!item.isIdentified() && !known.test(effect)) continue;
            boolean covers = heal >= missingHp;
            boolean better = best == null
                    || (covers && !bestCovers)
                    || (covers && heal < bestHeal)
                    || (!covers && !bestCovers && heal > bestHeal);
            if (better) {
                best = item;
                bestHeal = heal;
                bestCovers = covers;
            }
        }
        return best;
    }

    /** A healing potion's guaranteed floor (see {@link PotionEffectType#applyEffect}); 0 for anything else. */
    static int expectedHeal(PotionEffectType effect, int maxHp) {
        if (effect == null) return 0;
        switch (effect) {
            case HEALING: return Math.max((int) (maxHp * 0.20f), 10);
            case GREATER_HEALING: return Math.max((int) (maxHp * 0.40f), 20);
            case SUPERIOR_HEALING: return Math.max((int) (maxHp * 0.60f), 35);
            case SUPREME_HEALING: return Math.max((int) (maxHp * 0.85f), 60);
            default: return 0;
        }
    }
}
