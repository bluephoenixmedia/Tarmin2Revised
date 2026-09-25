package com.bpm.minotaur.weather;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.RingEffectType;
import com.bpm.minotaur.gamedata.liquid.LiquidManager;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Validates the liquid hazard simulation in the Lakelands Biome:
 * - Shallow water and toxic muck slow movement
 * - Prolonged wading in water corrodes/rusts equipped metal armor
 * - Exposure to toxic muck increases toxicity and inflicts poison damage
 * - Solid ground drains liquid exposure back to baseline
 */
public class LakelandsHazardTest {

    private LiquidManager liquidManager;
    private Player player;
    private GameEventManager eventManager;

    @Before
    public void setUp() {
        liquidManager = new LiquidManager();
        player = new Player(5, 5);
        eventManager = new GameEventManager();
    }

    @Test
    public void wetlandLiquidsSlowMovementByDefault() {
        liquidManager.setLiquidAt(5, 5, LiquidType.WATER);
        liquidManager.setLiquidAt(5, 6, LiquidType.BLACK_MUCK);
        liquidManager.setLiquidAt(5, 7, LiquidType.NONE);

        assertTrue("Water must slow movement", liquidManager.slowsMovement(5, 5, player));
        assertTrue("Black muck must slow movement", liquidManager.slowsMovement(5, 6, player));
        assertFalse("Dry ground does not slow movement", liquidManager.slowsMovement(5, 7, player));
    }

    @Test
    public void levitationBypassesLiquidMovementPenalty() {
        liquidManager.setLiquidAt(5, 5, LiquidType.WATER);

        Item ring = new Item(Item.ItemType.RING, 0, 0, ItemColor.GOLD, null, null);
        ring.setRingEffect(RingEffectType.LEVITATION);
        player.getEquipment().setWornRing(ring);

        assertFalse("Levitating player must not be slowed by water",
                liquidManager.slowsMovement(5, 5, player));
    }

    @Test
    public void prolongedWaterWadingRustsEquippedMetalArmor() {
        liquidManager.setLiquidAt(5, 5, LiquidType.WATER);

        Item ironChest = new Item(Item.ItemType.BREASTPLATE, 0, 0, ItemColor.GRAY, null, null);
        assertEquals(0, ironChest.getErosion());
        player.getEquipment().setWornChest(ironChest);

        // Step 1 to 11: exposure builds, no rust yet
        for (int i = 1; i <= 11; i++) {
            liquidManager.onPlayerStep(5, 5, player, eventManager);
            assertEquals("Should not rust before step 12", 0, ironChest.getErosion());
        }

        // Step 12: threshold reached (>=12 && %6 == 0)
        liquidManager.onPlayerStep(5, 5, player, eventManager);
        assertEquals("Iron breastplate must rust after 12 steps in water", 1, ironChest.getErosion());

        // Step 13 to 17: no additional rust
        for (int i = 13; i <= 17; i++) {
            liquidManager.onPlayerStep(5, 5, player, eventManager);
            assertEquals(1, ironChest.getErosion());
        }

        // Step 18: second corrosion tick (18 % 6 == 0)
        liquidManager.onPlayerStep(5, 5, player, eventManager);
        assertEquals("Iron breastplate must corrode further at step 18", 2, ironChest.getErosion());
    }

    @Test
    public void toxicMuckIncreasesToxicityAndInflictsDamage() {
        liquidManager.setLiquidAt(5, 5, LiquidType.BLACK_MUCK);

        int initialTox = player.getStats().getToxicity();
        int initialHp = player.getCurrentHP();

        // Steps 1 to 4: builds exposure
        for (int i = 1; i <= 4; i++) {
            liquidManager.onPlayerStep(5, 5, player, eventManager);
            assertEquals("No toxicity before step 5", initialTox, player.getStats().getToxicity());
        }

        // Step 5: toxicity threshold reached (+3)
        liquidManager.onPlayerStep(5, 5, player, eventManager);
        assertTrue("Toxicity must increase at step 5 in muck", player.getStats().getToxicity() > initialTox);

        // Steps 6 to 17: continued wading
        for (int i = 6; i <= 17; i++) {
            liquidManager.onPlayerStep(5, 5, player, eventManager);
        }

        // Step 18: necrotic poison damage threshold
        liquidManager.onPlayerStep(5, 5, player, eventManager);
        assertTrue("Muck exposure >= 18 must inflict damage", player.getCurrentHP() < initialHp);
    }

    @Test
    public void solidGroundDrainsLiquidExposure() {
        liquidManager.setLiquidAt(5, 5, LiquidType.WATER);
        liquidManager.setLiquidAt(6, 6, LiquidType.NONE);

        // Wading in water
        for (int i = 0; i < 5; i++) {
            liquidManager.onPlayerStep(5, 5, player, eventManager);
        }
        assertEquals(5, liquidManager.getExposureSteps());
        assertEquals(LiquidType.WATER, liquidManager.getCurrentExposureType());

        // Moving onto solid ground
        for (int i = 0; i < 5; i++) {
            liquidManager.onPlayerStep(6, 6, player, eventManager);
        }
        assertEquals(0, liquidManager.getExposureSteps());
        assertEquals(LiquidType.NONE, liquidManager.getCurrentExposureType());
    }
}
