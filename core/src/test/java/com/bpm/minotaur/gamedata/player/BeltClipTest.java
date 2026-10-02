package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.lighting.LightSource;
import com.bpm.minotaur.lighting.LightingManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class BeltClipTest {

    private Player player;

    private static Item lantern() {
        ItemTemplate t = new ItemTemplate();
        t.isBeltClip = true;
        return Item.fromTemplate(Item.ItemType.BRASS_LANTERN, t);
    }

    private static Item sword() {
        return Item.fromTemplate(Item.ItemType.SWORD, new ItemTemplate());
    }

    @Before
    public void setUp() {
        player = new Player(0, 0);
    }

    @Test
    public void theBeltClipFlagComesFromTheTemplate() {
        assertTrue(lantern().isBeltClip());
        assertFalse(sword().isBeltClip());
    }

    @Test
    public void aLanternIsFoundOnTheBeltOrInEitherHand() {
        assertFalse(player.hasLantern());

        player.getEquipment().setWornBelt(lantern());
        assertTrue(player.hasLantern());
        player.getEquipment().setWornBelt(null);

        player.getInventory().setLeftHand(lantern());
        assertTrue(player.hasLantern());
        player.getInventory().setLeftHand(null);

        player.getInventory().setRightHand(lantern());
        assertTrue(player.hasLantern());
    }

    @Test
    public void aSwordOnTheBeltIsNotALantern() {
        player.getEquipment().setWornBelt(sword());
        assertFalse(player.hasLantern());
    }

    @Test
    public void theStarterLanternIsClippedEvenWhenTheHandsAreFull() {
        player.getInventory().setLeftHand(sword());
        Item starter = lantern();
        assertTrue(player.giveStarterLantern(starter));
        assertSame(starter, player.getEquipment().getWornBelt());
        assertNotNull(player.getInventory().getLeftHand());
    }

    @Test
    public void noSecondLanternIsIssuedWhileOneIsCarriedInAHand() {
        player.getInventory().setLeftHand(lantern());
        assertFalse(player.giveStarterLantern(lantern()));
        assertNull(player.getEquipment().getWornBelt());
    }

    @Test
    public void aClippedLanternCountsForTheCritBonus() {
        float bare = player.getCritChance();
        player.getEquipment().setWornBelt(lantern());
        assertEquals(bare + 0.10f, player.getCritChance(), 0.0001f);
    }

    @Test
    public void aClippedLanternLightsThePlayer() {
        LightingManager lighting = new LightingManager();
        lighting.update(0.016f, player, null);
        assertEquals(LightSource.FlickerProfile.TORCH_FLUTTER, lighting.getPlayerLight().getProfile());

        player.getEquipment().setWornBelt(lantern());
        lighting.update(0.016f, player, null);
        assertEquals(LightSource.FlickerProfile.LANTERN_BREATH, lighting.getPlayerLight().getProfile());
    }

    @Test
    public void anOldSaveWithTheLanternInTheLeftHandIsMovedToTheBelt() {
        Item old = lantern();
        player.getInventory().setLeftHand(old);
        player.migrateLanternToBelt();
        assertSame(old, player.getEquipment().getWornBelt());
        assertNull(player.getInventory().getLeftHand());
    }

    @Test
    public void migrationNeverDisplacesAnAlreadyClippedLantern() {
        Item clipped = lantern();
        Item held = lantern();
        player.getEquipment().setWornBelt(clipped);
        player.getInventory().setLeftHand(held);
        player.migrateLanternToBelt();
        assertSame(clipped, player.getEquipment().getWornBelt());
        assertSame(held, player.getInventory().getLeftHand());
    }

    @Test
    public void deathStripsTheBelt() {
        player.getEquipment().setWornBelt(lantern());
        assertEquals(1, player.getEquipment().getAllEquipped().size());
        player.getEquipment().stripAllEquipped();
        assertNull(player.getEquipment().getWornBelt());
        assertFalse(player.hasLantern());
    }
}
