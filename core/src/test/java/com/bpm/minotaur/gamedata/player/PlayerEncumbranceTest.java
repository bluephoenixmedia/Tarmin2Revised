package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class PlayerEncumbranceTest {

    private Player player;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[] { com.badlogic.gdx.Application.class },
                    (proxy, method, args) -> null);
        }
        player = new Player(0, 0);
        player.getInventory().getMainInventory().clear();
        player.getInventory().clearQuickSlots();
        player.getInventory().setRightHand(null);
        player.getInventory().setLeftHand(null);
        player.getEquipment().stripAllEquipped();
        player.getStats().setStrength(10);
        player.getStats().setTreasureScore(0);
        player.getStats().setArrows(0);
        player.getStats().setShot(0);
    }

    private static Item weightOf(float weight) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = "Ballast";
        t.weight = weight;
        return Item.fromTemplate(Item.ItemType.SWORD, t);
    }

    @Test
    public void anEmptyHandedPlayerCarriesNothingAndIsUnencumbered() {
        assertEquals(0f, player.getCarriedWeight(), 0.001f);
        assertEquals(Encumbrance.Tier.UNENCUMBERED, player.getEncumbrance());
    }

    @Test
    public void everyPlaceAnItemCanBeCarriedCounts() {
        player.getInventory().setRightHand(weightOf(2f));
        player.getInventory().setLeftHand(weightOf(3f));
        player.getInventory().setQuickSlot(0, weightOf(4f));
        player.getInventory().getMainInventory().add(weightOf(5f));
        player.getEquipment().setWornChest(weightOf(6f));

        assertEquals(2f + 3f + 4f + 5f + 6f, player.getCarriedWeight(), 0.001f);
    }

    @Test
    public void goldAndAmmunitionWeighToo() {
        player.getStats().setTreasureScore(1000);
        player.getStats().setArrows(40);
        assertTrue(player.getCarriedWeight() >= 10f);
    }

    @Test
    public void carryingMoreThanTheLimitBurdensAndSlowsThePlayer() {
        int freeSpeed = player.getEffectiveSpeed();
        float limit = Encumbrance.capacity(10);
        player.getInventory().getMainInventory().add(weightOf(limit + 1f));

        assertEquals(Encumbrance.Tier.BURDENED, player.getEncumbrance());
        assertTrue(player.getEffectiveSpeed() < freeSpeed);
    }

    @Test
    public void theHeavierTiersSlowFurtherAndDrainFoodFaster() {
        float limit = Encumbrance.capacity(10);
        player.getInventory().getMainInventory().add(weightOf(limit * 1.6f));
        assertEquals(Encumbrance.Tier.STRESSED, player.getEncumbrance());
        int stressedSpeed = player.getEffectiveSpeed();
        assertTrue(player.getMetabolicDrainFactor() > 1f);

        player.getInventory().getMainInventory().add(weightOf(limit * 0.6f));
        assertEquals(Encumbrance.Tier.OVERLOADED, player.getEncumbrance());
        assertTrue(player.getEffectiveSpeed() < stressedSpeed);
        assertTrue(player.getMetabolicDrainFactor() > 1.5f);
    }

    @Test
    public void burdenedDoesNotYetDrainFaster() {
        player.getInventory().getMainInventory().add(weightOf(Encumbrance.capacity(10) + 1f));
        assertEquals(1f, player.getMetabolicDrainFactor(), 0.001f);
    }

    @Test
    public void aStrengthBuffRaisesTheLimitToo() {
        player.getInventory().getMainInventory().add(weightOf(Encumbrance.capacity(10) + 1f));
        assertEquals(Encumbrance.Tier.BURDENED, player.getEncumbrance());

        player.getStatusManager().addEffect(
                com.bpm.minotaur.gamedata.effects.StatusEffectType.GIANT_STRENGTH, 50, 21, false);

        assertEquals("a Giant Strength potion lets you carry more",
                Encumbrance.Tier.UNENCUMBERED, player.getEncumbrance());
    }

    @Test
    public void aFreshCharacterStartsWellUnderTheLimit() {
        Player fresh = new Player(0, 0);
        assertTrue("starting kit weighs " + fresh.getCarriedWeight() + " of " + fresh.getCarryCapacity(),
                fresh.getCarriedWeight() < fresh.getCarryCapacity() * 0.5f);
        assertEquals(Encumbrance.Tier.UNENCUMBERED, fresh.getEncumbrance());
    }

    @Test
    public void strengthRaisesTheLimit() {
        player.getInventory().getMainInventory().add(weightOf(Encumbrance.capacity(10) + 1f));
        assertEquals(Encumbrance.Tier.BURDENED, player.getEncumbrance());

        player.getStats().setStrength(18);

        assertEquals(Encumbrance.Tier.UNENCUMBERED, player.getEncumbrance());
    }
}
