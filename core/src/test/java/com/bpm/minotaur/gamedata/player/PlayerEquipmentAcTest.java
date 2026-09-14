package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.ModifierType;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Regression coverage for the "near invincible" AC bug: PlayerEquipment.getACBonus()
 * was double-counting BONUS_AC item modifiers (once via Item.getArmorClassBonus(),
 * again via getEquippedModifierSum(BONUS_AC)) and had no ceiling on the sum of all
 * seven armor slots, letting a fully-geared player's AC climb past what monster
 * to-hit rolls could ever clear.
 */
public class PlayerEquipmentAcTest {

    private PlayerEquipment equipment;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        equipment = new PlayerEquipment();
    }

    private static Item armorPiece(Item.ItemType type, int baseAc) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = type.name();
        t.armorClassBonus = baseAc;
        t.isArmor = true;
        return Item.fromTemplate(type, t);
    }

    @Test
    public void bonusAcModifierIsNotDoubleCounted() {
        Item chest = armorPiece(Item.ItemType.FULL_PLATE, 4);
        chest.addModifier(new ItemModifier(ModifierType.BONUS_AC, 1, "Blessed"));
        equipment.setWornChest(chest);

        // 4 base + 1 enchant = 5, NOT 6 (which is what double-counting the
        // modifier through both getArmorClassBonus() and
        // getEquippedModifierSum(BONUS_AC) would produce).
        assertEquals(5, equipment.getACBonus());
    }

    @Test
    public void totalAcBonusAcrossAllSlotsIsCapped() {
        equipment.setWornHelmet(armorPiece(Item.ItemType.HELMET, 6));
        equipment.setWornChest(armorPiece(Item.ItemType.FULL_PLATE, 8));
        equipment.setWornGauntlets(armorPiece(Item.ItemType.GAUNTLETS, 6));
        equipment.setWornBoots(armorPiece(Item.ItemType.BOOTS, 6));
        equipment.setWornLegs(armorPiece(Item.ItemType.LEGS, 6));
        equipment.setWornArms(armorPiece(Item.ItemType.ARMS, 6));
        equipment.setWornShield(armorPiece(Item.ItemType.SHIELD, 6));

        // Raw sum would be 44; the equipment AC bonus must be clamped so gearing
        // up can't make a player effectively unhittable regardless of loadout.
        int uncappedRawSum = 44;
        assertTrue("Cap should actually constrain the total", equipment.getACBonus() < uncappedRawSum);
        assertEquals(PlayerEquipment.MAX_EQUIPMENT_AC_BONUS, equipment.getACBonus());
    }
}
