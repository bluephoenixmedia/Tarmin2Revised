package com.bpm.minotaur.gamedata.player;

import com.badlogic.gdx.Gdx;
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

    private Item wornShield = null; // Kept for legacy compatibility / explicit shield slot logic

    public PlayerEquipment() {
    }

    public int getEquippedModifierSum(ModifierType typeToFind) {
        int total = 0;
        Item[] equippedItems = {
            wornHelmet, wornEyes, wornNeck, wornBack, wornChest,
            wornArms, wornGauntlets, wornLegs, wornBoots, wornRing, wornShield
        };

        // Debug Log Builder
        StringBuilder debugLog = new StringBuilder();
        boolean debug = false; // Only log if we find something, to reduce spam, or toggle true for full spam

        for (Item item : equippedItems) {
            if (item != null && item.getModifiers() != null) {
                for (ItemModifier mod : item.getModifiers()) {
                    if (mod.type == typeToFind) {
                        total += mod.value;
                        debugLog.append(" [").append(item.getDisplayName()).append(": ").append(mod.value).append("]");
                        debug = true;
                    }
                }
            }
        }

        // Only log if we found modifiers or if it's a critical stat check (optional)
        if (debug) {
            Gdx.app.log("Equipment", "Modifier Calc (" + typeToFind + "): Total=" + total + " Sources:" + debugLog.toString());
        }

        return total;
    }

    public int getArmorDefense() {
        int totalDefense = 0;
        StringBuilder log = new StringBuilder();

        if (wornHelmet != null) {
            totalDefense += wornHelmet.getArmorDefense();
            log.append(" Head(").append(wornHelmet.getArmorDefense()).append(")");
        }
        if (wornChest != null) {
            totalDefense += wornChest.getArmorDefense();
            log.append(" Chest(").append(wornChest.getArmorDefense()).append(")");
        }
        if (wornGauntlets != null) {
            totalDefense += wornGauntlets.getArmorDefense();
            log.append(" Hands(").append(wornGauntlets.getArmorDefense()).append(")");
        }
        if (wornBoots != null) {
            totalDefense += wornBoots.getArmorDefense();
            log.append(" Feet(").append(wornBoots.getArmorDefense()).append(")");
        }
        if (wornLegs != null) {
            totalDefense += wornLegs.getArmorDefense();
            log.append(" Legs(").append(wornLegs.getArmorDefense()).append(")");
        }
        if (wornArms != null) {
            totalDefense += wornArms.getArmorDefense();
            log.append(" Arms(").append(wornArms.getArmorDefense()).append(")");
        }
        if (wornShield != null) {
            totalDefense += wornShield.getArmorDefense();
            log.append(" Shield(").append(wornShield.getArmorDefense()).append(")");
        }

        // Add bonus defense from all equipped items
        int modifierDefense = getEquippedModifierSum(ModifierType.BONUS_DEFENSE);
        if (modifierDefense > 0) {
            totalDefense += modifierDefense;
            log.append(" Mods(").append(modifierDefense).append(")");
        }

       // Gdx.app.log("Equipment", "Armor Calc: Total=" + totalDefense + " Breakdown:" + log.toString());

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

    public Item getWornChest() { return wornChest; }
    public void setWornChest(Item item) { this.wornChest = item; }

    // Legacy getters
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
