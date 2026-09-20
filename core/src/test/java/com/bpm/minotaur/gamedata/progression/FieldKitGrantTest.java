package com.bpm.minotaur.gamedata.progression;

import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.item.Item;
import org.junit.Before;
import org.junit.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * The portable field kits are earned at the Shelter Altar, not handed out on death.
 *
 * <p>They have no other source in the game -- no spawn table, loot table, recipe or shop
 * stocks them -- so this rule is the sole gate on field crafting and field cooking.
 */
public class FieldKitGrantTest {

    private Inventory inventory;

    @Before
    public void setUp() {
        inventory = new Inventory();
    }

    private static Set<ShelterAltar.Station> stations(ShelterAltar.Station... s) {
        Set<ShelterAltar.Station> set = EnumSet.noneOf(ShelterAltar.Station.class);
        for (ShelterAltar.Station station : s) {
            set.add(station);
        }
        return set;
    }

    private void carry(Item.ItemType type) {
        inventory.pickupToBackpack(new Item(type, 0, 0, null, null, null));
    }

    @Test
    public void testEachKitComesFromItsStationaryCounterpart() {
        assertEquals(Item.ItemType.CRAFTING_TOOLKIT,
                ShelterAltar.Station.CRAFTING_BENCH.getPortableKit());
        assertEquals(Item.ItemType.COOKING_KIT,
                ShelterAltar.Station.CAMPFIRE.getPortableKit());
    }

    @Test
    public void testStationsWithNoPortableVersionGrantNothing() {
        assertNull(ShelterAltar.Station.BED.getPortableKit());
        assertNull(ShelterAltar.Station.STASH_CHEST.getPortableKit());
        assertNull(ShelterAltar.Station.LANTERN.getPortableKit());
    }

    @Test
    public void testAPlayerWhoHasBoughtNothingIsOwedNothing() {
        List<Item.ItemType> owed = FieldKitGrant.owedKits(stations(), inventory);
        assertTrue("field kits are not a death handout", owed.isEmpty());
    }

    @Test
    public void testOwningTheBenchOwesTheToolkitOnly() {
        List<Item.ItemType> owed = FieldKitGrant.owedKits(
                stations(ShelterAltar.Station.CRAFTING_BENCH), inventory);

        assertEquals(1, owed.size());
        assertEquals(Item.ItemType.CRAFTING_TOOLKIT, owed.get(0));
    }

    @Test
    public void testOwningTheFirePotOwesTheCookwareOnly() {
        List<Item.ItemType> owed = FieldKitGrant.owedKits(
                stations(ShelterAltar.Station.CAMPFIRE), inventory);

        assertEquals(1, owed.size());
        assertEquals(Item.ItemType.COOKING_KIT, owed.get(0));
    }

    @Test
    public void testOwningBothStationsOwesBothKits() {
        List<Item.ItemType> owed = FieldKitGrant.owedKits(
                stations(ShelterAltar.Station.CRAFTING_BENCH, ShelterAltar.Station.CAMPFIRE), inventory);

        assertEquals(2, owed.size());
        assertTrue(owed.contains(Item.ItemType.CRAFTING_TOOLKIT));
        assertTrue(owed.contains(Item.ItemType.COOKING_KIT));
    }

    @Test
    public void testUnrelatedStationsDoNotOweKits() {
        List<Item.ItemType> owed = FieldKitGrant.owedKits(
                stations(ShelterAltar.Station.BED, ShelterAltar.Station.STASH_CHEST,
                        ShelterAltar.Station.LANTERN), inventory);

        assertTrue(owed.isEmpty());
    }

    @Test
    public void testAKitAlreadyCarriedIsNotDuplicated() {
        carry(Item.ItemType.CRAFTING_TOOLKIT);

        List<Item.ItemType> owed = FieldKitGrant.owedKits(
                stations(ShelterAltar.Station.CRAFTING_BENCH, ShelterAltar.Station.CAMPFIRE), inventory);

        assertEquals("only the missing one is owed", 1, owed.size());
        assertEquals(Item.ItemType.COOKING_KIT, owed.get(0));
    }

    @Test
    public void testAPlayerCarryingBothIsOwedNothing() {
        carry(Item.ItemType.CRAFTING_TOOLKIT);
        carry(Item.ItemType.COOKING_KIT);

        List<Item.ItemType> owed = FieldKitGrant.owedKits(
                stations(ShelterAltar.Station.CRAFTING_BENCH, ShelterAltar.Station.CAMPFIRE), inventory);

        assertTrue(owed.isEmpty());
    }

    /**
     * Retention keeps a kit through death independently of the altar, so a player can
     * hold one without owning the station. That must not be confiscated or duplicated.
     */
    @Test
    public void testAKitHeldWithoutTheStationIsLeftAlone() {
        carry(Item.ItemType.COOKING_KIT);

        List<Item.ItemType> owed = FieldKitGrant.owedKits(stations(), inventory);

        assertTrue(owed.isEmpty());
        assertTrue("the kit they already earned stays theirs",
                inventory.hasItemOfType(Item.ItemType.COOKING_KIT));
    }

    @Test
    public void testNullsAreTolerated() {
        assertTrue(FieldKitGrant.owedKits(null, inventory).isEmpty());
        assertTrue(FieldKitGrant.owedKits(stations(ShelterAltar.Station.CAMPFIRE), null).isEmpty());
    }

    /**
     * A full pack is the one way a grant can fail. It has to come back distinguishable
     * from "nothing was owed", or the caller cannot tell the player why they paid
     * divinities and received nothing.
     */
    @Test
    public void testAnUndeliverableKitIsReportedRatherThanLost() {
        FieldKitGrant.Result nothingOwed = FieldKitGrant.grantOwed(stations(), inventory, null, null);

        assertTrue(nothingOwed.isEmpty());
        assertTrue(nothingOwed.getGranted().isEmpty());
        assertTrue(nothingOwed.getNoRoom().isEmpty());
    }

    @Test
    public void testResultListsAreNotModifiableByCallers() {
        FieldKitGrant.Result result = FieldKitGrant.grantOwed(stations(), inventory, null, null);
        try {
            result.getGranted().add(new Item());
            fail("callers must not be able to edit the grant record");
        } catch (UnsupportedOperationException expected) {
            // exactly right
        }
    }
}
