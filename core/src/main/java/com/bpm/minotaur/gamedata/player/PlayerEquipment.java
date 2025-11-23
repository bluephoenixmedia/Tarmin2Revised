package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.ModifierType;

public class PlayerEquipment {

    // Expanded Equipment slots
    private Item wornHelmet = null;      // Head
    private Item wornEyes = null;        // Eyes (New)
    private Item wornNeck = null;        // Amulet (New)
    private Item wornBack = null;        // Cloak/Back (New)
    private Item wornChest = null;       // Chest (Hauberk/Breastplate)
    private Item wornArms = null;        // Arms (New)
    private Item wornGauntlets = null;   // Hands
    private Item wornLegs = null;        // Legs (New)
    private Item wornBoots = null;       // Feet (New)
    private Item wornRing = null;        // Ring

    // Note: Left/Right hand are usually managed in Inventory, but we can track armor stats here if shields are involved.
    // For now, hands remain in Inventory logic, but shields are accessed here for defense calc.
    // We will assume 'wornShield' is maintained for backward compatibility or linked to Inventory.leftHand.
    // For the purpose of this refactor, let's keep specific armor slots here.

    private Item wornShield = null; // Kept for legacy compatibility / explicit shield slot logic

    public PlayerEquipment() {
    }

    public int getEquippedModifierSum(ModifierType typeToFind) {
        int total = 0;
        Item[] equippedItems = {
            wornHelmet, wornEyes, wornNeck, wornBack, wornChest,
            wornArms, wornGauntlets, wornLegs, wornBoots, wornRing, wornShield
        };

        for (Item item : equippedItems) {
            if (item != null && item.getModifiers() != null) {
                for (ItemModifier mod : item.getModifiers()) {
                    if (mod.type == typeToFind) {
                        total += mod.value;
                    }
                }
            }
        }
        return total;
    }

    public int getArmorDefense() {
        int totalDefense = 0;

        if (wornHelmet != null) totalDefense += wornHelmet.getArmorDefense();
        if (wornChest != null) totalDefense += wornChest.getArmorDefense(); // New unified Chest
        if (wornGauntlets != null) totalDefense += wornGauntlets.getArmorDefense();
        if (wornBoots != null) totalDefense += wornBoots.getArmorDefense();
        if (wornLegs != null) totalDefense += wornLegs.getArmorDefense();
        if (wornArms != null) totalDefense += wornArms.getArmorDefense();
        if (wornShield != null) totalDefense += wornShield.getArmorDefense();

        // Add bonus defense from all equipped items
        totalDefense += getEquippedModifierSum(ModifierType.BONUS_DEFENSE);

        return totalDefense;
    }

    public int getRingDefense() {
        int totalDefense = 0;
        if (wornRing != null) {
            totalDefense += wornRing.getArmorDefense();
        }
        totalDefense += getEquippedModifierSum(ModifierType.BONUS_DEFENSE);
        return totalDefense;
    }

    // --- Getters and Setters ---

    public Item getWornHelmet() { return wornHelmet; }
    public void setWornHelmet(Item item) { this.wornHelmet = item; }

    public Item getWornEyes() { return wornEyes; }
    public void setWornEyes(Item item) { this.wornEyes = item; }

    public Item getWornNeck() { return wornNeck; }
    public void setWornNeck(Item item) { this.wornNeck = item; }

    public Item getWornBack() { return wornBack; }
    public void setWornBack(Item item) { this.wornBack = item; }

    // Consolidated Chest Slot (Handles Hauberk or Breastplate)
    public Item getWornChest() { return wornChest; }
    public void setWornChest(Item item) { this.wornChest = item; }

    // Legacy getters for Hauberk/Breastplate redirect to Chest
    public Item getWornHauberk() { return wornChest; }
    public void setWornHauberk(Item item) { this.wornChest = item; }
    public Item getWornBreastplate() { return wornChest; }
    public void setWornBreastplate(Item item) { this.wornChest = item; }

    public Item getWornArms() { return wornArms; }
    public void setWornArms(Item item) { this.wornArms = item; }

    public Item getWornGauntlets() { return wornGauntlets; }
    public void setWornGauntlets(Item item) { this.wornGauntlets = item; }

    public Item getWornLegs() { return wornLegs; }
    public void setWornLegs(Item item) { this.wornLegs = item; }

    public Item getWornBoots() { return wornBoots; }
    public void setWornBoots(Item item) { this.wornBoots = item; }

    public Item getWornRing() { return wornRing; }
    public void setWornRing(Item item) { this.wornRing = item; }

    public Item getWornShield() { return wornShield; }
    public void setWornShield(Item item) { this.wornShield = item; }
}
