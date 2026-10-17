package com.bpm.minotaur.gamedata.item;

import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import org.junit.Test;

import static org.junit.Assert.*;

public class ThrowRulesTest {

    private static Item of(Item.ItemType type, boolean weapon, boolean potion, boolean thrown, int range) {
        ItemTemplate t = new ItemTemplate();
        t.isWeapon = weapon;
        t.isPotion = potion;
        t.isThrown = thrown;
        t.range = range;
        return Item.fromTemplate(type, t);
    }

    @Test
    public void itemsAreClassifiedByWhatTheyAre() {
        assertEquals(ThrowRules.Kind.WEAPON, ThrowRules.kindOf(of(Item.ItemType.SWORD, true, false, false, 1)));
        assertEquals(ThrowRules.Kind.POTION, ThrowRules.kindOf(of(Item.ItemType.POTION_BLUE, false, true, false, 1)));
        assertEquals(ThrowRules.Kind.OTHER, ThrowRules.kindOf(of(Item.ItemType.FOOD, false, false, false, 1)));
    }

    @Test
    public void aThrowingWeaponKeepsItsOwnRangeWithAFloorOfThree() {
        assertEquals(6, ThrowRules.range(of(Item.ItemType.SWORD, true, false, true, 6), 0, 5f));
        assertEquals(3, ThrowRules.range(of(Item.ItemType.SWORD, true, false, true, 1), 0, 5f));
    }

    @Test
    public void otherItemsFlyFartherWhenLightAndWhenTheThrowerIsStrong() {
        Item trinket = of(Item.ItemType.FOOD, false, false, false, 1);
        assertTrue(ThrowRules.range(trinket, 0, 0.2f) > ThrowRules.range(trinket, 0, 10f));
        assertTrue(ThrowRules.range(trinket, 3, 4f) > ThrowRules.range(trinket, 0, 4f));
    }

    @Test
    public void rangeStaysInsideTheBounds() {
        Item any = of(Item.ItemType.FOOD, false, false, false, 1);
        assertEquals(ThrowRules.MIN_RANGE, ThrowRules.range(any, -5, 40f));
        assertEquals(ThrowRules.MAX_RANGE, ThrowRules.range(any, 10, 0f));
    }

    @Test
    public void onlyAWeaponNotMadeForThrowingTakesThePenalty() {
        assertEquals(0, ThrowRules.toHitPenalty(of(Item.ItemType.SWORD, true, false, true, 6)));
        assertEquals(ThrowRules.NON_THROWING_PENALTY, ThrowRules.toHitPenalty(of(Item.ItemType.SWORD, true, false, false, 1)));
    }

    @Test
    public void healingPotionsHealAndHarmfulOnesAfflict() {
        assertTrue(ThrowRules.splashFor(PotionEffectType.HEALING).healFraction > 0f);
        assertEquals(1f, ThrowRules.splashFor(PotionEffectType.SUPREME_HEALING).healFraction, 0f);
        assertEquals(StatusEffectType.POISONED, ThrowRules.splashFor(PotionEffectType.POISON).status);
        assertEquals(StatusEffectType.CONFUSED, ThrowRules.splashFor(PotionEffectType.CONFUSION).status);
    }

    @Test
    public void onlyVapoursSpreadToNeighbouringTiles() {
        assertTrue(ThrowRules.splashFor(PotionEffectType.POISON).gas);
        assertTrue(ThrowRules.splashFor(PotionEffectType.SLEEP).gas);
        assertFalse(ThrowRules.splashFor(PotionEffectType.HEALING).gas);
        assertFalse(ThrowRules.splashFor(PotionEffectType.SPEED).gas);
    }

    @Test
    public void aPotionWithNoTargetEffectJustBreaks() {
        assertNull(ThrowRules.splashFor(PotionEffectType.CLIMBING));
        assertNull(ThrowRules.splashFor(null));
    }

    @Test
    public void everyPotionEffectIsEitherMappedOrDeliberatelyHarmless() {
        // Guards against a new potion silently doing nothing when thrown: every effect named here
        // must be a conscious choice. Update this list when a potion is added.
        java.util.Set<PotionEffectType> harmless = java.util.EnumSet.of(
                PotionEffectType.GAIN_STRENGTH, PotionEffectType.LEVITATION, PotionEffectType.RESTORE_ENERGY,
                PotionEffectType.SUPER_VISION, PotionEffectType.CLIMBING, PotionEffectType.OIL_OF_SHARPNESS,
                PotionEffectType.CLARITY, PotionEffectType.SLIPPERINESS, PotionEffectType.ETHEREALNESS);
        for (PotionEffectType e : PotionEffectType.values()) {
            if (ThrowRules.splashFor(e) == null) {
                assertTrue(e + " has no thrown effect and is not listed as harmless", harmless.contains(e));
            }
        }
    }
}
