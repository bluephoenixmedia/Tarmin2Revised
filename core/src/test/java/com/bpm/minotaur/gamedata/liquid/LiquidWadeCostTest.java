package com.bpm.minotaur.gamedata.liquid;

import com.bpm.minotaur.gamedata.item.Item;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Wading costs a second turn tick, so monsters get a free action while the
 * player is in the water. Deterministic and visible: you can see the liquid, so
 * you can choose to fight on dry stone instead.
 *
 * <p>See {@code docs/DEsign/Themed Chunk Contract.md} section 7.
 */
public class LiquidWadeCostTest {

    @Test
    public void dryGroundCostsNoExtraTurn() {
        LiquidManager lm = new LiquidManager();
        assertFalse(lm.slowsMovement(4, 4, null));
    }

    @Test
    public void everyLiquidSlowsAWader() {
        for (LiquidType type : new LiquidType[]{LiquidType.WATER, LiquidType.BLOOD, LiquidType.BLACK_MUCK}) {
            LiquidManager lm = new LiquidManager();
            lm.setLiquidAt(4, 4, type);
            assertTrue(type + " must cost an extra turn tick", lm.slowsMovement(4, 4, null));
        }
    }

    @Test
    public void bloodFrenzyOnlyTriggersAfterRealExposure() {
        LiquidManager lm = new LiquidManager();
        assertFalse("A dry player is not scented", lm.isBloodFrenzyActive());

        lm.setLiquidAt(2, 2, LiquidType.BLOOD);
        assertFalse("Frenzy must not fire before the player has waded", lm.isBloodFrenzyActive());
    }

    @Test
    public void liquidDataSurvivesASaveRoundTrip() {
        LiquidManager lm = new LiquidManager();
        lm.setLiquidAt(3, 7, LiquidType.BLACK_MUCK);
        lm.setLiquidAt(11, 2, LiquidType.WATER);
        lm.setLiquidAt(0, 0, LiquidType.BLOOD);

        LiquidManager restored = LiquidManager.deserialize(lm.serialize());

        assertEquals(LiquidType.BLACK_MUCK, restored.getLiquidAt(3, 7));
        assertEquals(LiquidType.WATER, restored.getLiquidAt(11, 2));
        assertEquals(LiquidType.BLOOD, restored.getLiquidAt(0, 0));
        assertEquals(LiquidType.NONE, restored.getLiquidAt(5, 5));
    }

    @Test
    public void corrosionCostsArmourClass() {
        // Rust has to actually bite: before this, Item.erosion was a field
        // nothing read, so a "corroding" hazard corroded nothing.
        Item armour = new Item();
        armour.addModifier(new com.bpm.minotaur.gamedata.item.ItemModifier(
                com.bpm.minotaur.gamedata.ModifierType.BONUS_AC, 4, "Test Plate"));

        assertEquals(4, armour.getArmorClassBonus());

        armour.incrementErosion();
        assertEquals("One erosion level must cost 1 AC", 3, armour.getArmorClassBonus());

        armour.setErosion(10);
        assertEquals("AC must floor at zero, never go negative", 0, armour.getArmorClassBonus());
    }
}
