package com.bpm.minotaur.gamedata.progression;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.managers.DivinityManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class StartingWeaponProgressionTest {

    private ShelterAltar altar;
    private DivinityManager divinityManager;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
        altar = ShelterAltar.getInstance();
        altar.reset();

        divinityManager = DivinityManager.getInstance();
        // Spend down or set divinities to 0 for a known test state
        divinityManager.spendDivinities(divinityManager.getCurrentDivinities());
    }

    @Test
    public void testRustySwordIsAlwaysUnlockedByDefault() {
        assertTrue("Rusty sword should always be unlocked by default",
                altar.isStartingWeaponUnlocked(StartingWeapon.RUSTY_SWORD));
        assertTrue("Rusty sword should be unlocked when queried by ItemType",
                altar.isStartingWeaponUnlocked(Item.ItemType.RUSTY_SWORD));
        assertEquals("Default selected starting weapon must be RUSTY_SWORD",
                Item.ItemType.RUSTY_SWORD, altar.getSelectedStartingWeapon());
    }

    @Test
    public void testOtherWeaponsAreLockedInitially() {
        assertFalse("Knife should be locked initially",
                altar.isStartingWeaponUnlocked(StartingWeapon.KNIFE));
        assertFalse("Axe should be locked initially",
                altar.isStartingWeaponUnlocked(StartingWeapon.AXE));
        assertFalse("Bow should be locked initially",
                altar.isStartingWeaponUnlocked(StartingWeapon.BOW));
    }

    @Test
    public void testCannotUnlockWithoutSufficientDivinities() {
        divinityManager.spendDivinities(divinityManager.getCurrentDivinities());
        assertEquals(0, divinityManager.getCurrentDivinities());

        boolean unlocked = altar.unlockStartingWeapon(StartingWeapon.KNIFE);
        assertFalse("Unlock should fail when player lacks divinities", unlocked);
        assertFalse(altar.isStartingWeaponUnlocked(StartingWeapon.KNIFE));
    }

    @Test
    public void testUnlockStartingWeaponWithDivinities() {
        divinityManager.addDivinities(50);
        int initialDiv = divinityManager.getCurrentDivinities();
        assertTrue(initialDiv >= 50);

        int cost = StartingWeapon.KNIFE.getDivinityCost();
        boolean unlocked = altar.unlockStartingWeapon(StartingWeapon.KNIFE);
        assertTrue("Unlock should succeed when player has sufficient divinities", unlocked);
        assertTrue("Knife should now be unlocked", altar.isStartingWeaponUnlocked(StartingWeapon.KNIFE));
        assertEquals("Divinities should be deducted by cost", initialDiv - cost, divinityManager.getCurrentDivinities());

        // Attempting to unlock again should return false (already unlocked)
        assertFalse("Cannot re-unlock an already unlocked weapon", altar.unlockStartingWeapon(StartingWeapon.KNIFE));
    }

    @Test
    public void testSetSelectedStartingWeapon() {
        // Cannot select a locked weapon
        altar.setSelectedStartingWeapon(StartingWeapon.AXE);
        assertEquals("Should not be able to select a locked weapon",
                Item.ItemType.RUSTY_SWORD, altar.getSelectedStartingWeapon());

        // Unlock axe
        divinityManager.addDivinities(50);
        altar.unlockStartingWeapon(StartingWeapon.AXE);
        assertTrue(altar.isStartingWeaponUnlocked(StartingWeapon.AXE));

        // Now select it
        altar.setSelectedStartingWeapon(StartingWeapon.AXE);
        assertEquals("Selected starting weapon should now be AXE",
                Item.ItemType.AXE, altar.getSelectedStartingWeapon());
    }

    @Test
    public void testStartingWeaponEnumProperties() {
        StartingWeapon bow = StartingWeapon.BOW;
        assertEquals(Item.ItemType.BOW, bow.getItemType());
        assertEquals(30, bow.getDivinityCost());
        assertEquals(15, bow.getStartingAmmo());
        assertEquals("1d4 Ranged", bow.getDamageDice());

        StartingWeapon fromType = StartingWeapon.fromItemType(Item.ItemType.BOW);
        assertEquals(StartingWeapon.BOW, fromType);

        StartingWeapon nullFallback = StartingWeapon.fromItemType(null);
        assertEquals(StartingWeapon.RUSTY_SWORD, nullFallback);
    }
}
