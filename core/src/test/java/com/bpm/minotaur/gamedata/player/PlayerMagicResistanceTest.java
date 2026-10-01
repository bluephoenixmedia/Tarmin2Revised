package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.MagicResistance;
import com.bpm.minotaur.gamedata.ModifierType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class PlayerMagicResistanceTest {

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
        player.getEquipment().stripAllEquipped();
        player.getInventory().setRightHand(null);
        player.getInventory().setLeftHand(null);
        // No dodge, so a connecting hit always lands and the comparison is deterministic.
        player.getStats().setAgility(10);
    }

    private static Item warded(int percent) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = "Warded";
        Item item = Item.fromTemplate(Item.ItemType.SWORD, t);
        item.addModifier(new ItemModifier(ModifierType.BONUS_MAGIC_RESISTANCE, percent, "Warded"));
        return item;
    }

    @Test
    public void aPlayerWithNothingEnchantedResistsNothing() {
        assertEquals(0, player.getMagicResistance());
    }

    @Test
    public void wornArmourAndAHeldWeaponBothContribute() {
        player.getEquipment().setWornChest(warded(10));
        player.getEquipment().setWornHelmet(warded(5));
        player.getInventory().setRightHand(warded(15));

        assertEquals(30, player.getMagicResistance());
    }

    @Test
    public void resistanceStacksAdditivelyUpToTheCap() {
        player.getEquipment().setWornChest(warded(40));
        player.getEquipment().setWornHelmet(warded(40));
        player.getEquipment().setWornBoots(warded(40));

        assertEquals(MagicResistance.PLAYER_CAP, player.getMagicResistance());
    }

    @Test
    public void spellDamageIsCutByTheResistanceButPlainDamageIsNot() {
        Player bare = new Player(0, 0);
        bare.getStats().setAgility(10);
        int bareStart = bare.getStats().getCurrentHP();
        bare.takeSpellDamage(10, DamageType.FIRE);
        int bareLoss = bareStart - bare.getStats().getCurrentHP();

        player.getEquipment().setWornChest(warded(50));
        int start = player.getStats().getCurrentHP();
        player.takeSpellDamage(10, DamageType.FIRE);
        int wardedLoss = start - player.getStats().getCurrentHP();

        assertTrue("warded " + wardedLoss + " vs bare " + bareLoss, wardedLoss < bareLoss);

        // The same ward does nothing against a blade.
        int before = player.getStats().getCurrentHP();
        player.takeDamage(10, DamageType.PHYSICAL);
        Player bareBlade = new Player(0, 0);
        bareBlade.getStats().setAgility(10);
        int bareBefore = bareBlade.getStats().getCurrentHP();
        bareBlade.takeDamage(10, DamageType.PHYSICAL);
        assertEquals(bareBefore - bareBlade.getStats().getCurrentHP(), before - player.getStats().getCurrentHP());
    }
}
