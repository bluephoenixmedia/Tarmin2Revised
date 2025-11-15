package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.ModifierType;

public class PlayerEquipment {

    // Equipment slots
    private Item wornHelmet = null;
    private Item wornShield = null;
    private Item wornGauntlets = null;
    private Item wornHauberk = null;
    private Item wornBreastplate = null;
    private Item wornRing = null;

    /**
     * Constructor for PlayerEquipment.
     * All slots are initialized to null by default.
     */
    public PlayerEquipment() {
        // All fields are already null
    }

    /**
     * A generic helper to sum up the value of a specific modifier from ALL equipped items.
     * (Moved from Player.java)
     * @param typeToFind The ModifierType to search for.
     * @return The sum of all values for that modifier.
     */
    public int getEquippedModifierSum(ModifierType typeToFind) {
        int total = 0;
        Item[] equippedItems = { wornHelmet, wornShield, wornGauntlets, wornHauberk, wornBreastplate, wornRing };

        for (Item item : equippedItems) {
            if (item != null && item.getModifiers() != null) { // Added null check for getModifiers
                for (ItemModifier mod : item.getModifiers()) {
                    if (mod.type == typeToFind) {
                        total += mod.value;
                    }
                }
            }
        }
        return total;
    }

    /**
     * Calculates total armor defense from all equipped armor.
     * (Moved from Player.java)
     * @return The total defense value.
     */
    public int getArmorDefense() {
        int totalDefense = 0;

        // Base defense from armor stats
        if (wornHelmet != null) totalDefense += wornHelmet.getArmorDefense();
        if (wornShield != null) totalDefense += wornShield.getArmorDefense();
        if (wornGauntlets != null) totalDefense += wornGauntlets.getArmorDefense();
        if (wornHauberk != null) totalDefense += wornHauberk.getArmorDefense();
        if (wornBreastplate != null) totalDefense += wornBreastplate.getArmorDefense();

        // Add bonus defense from all equipped items (including rings)
        totalDefense += getEquippedModifierSum(ModifierType.BONUS_DEFENSE);

        return totalDefense;
    }

    /**
     * Calculates total spiritual defense from the equipped ring.
     * (Moved from Player.java)
     * @return The total spiritual defense value.
     */
    public int getRingDefense() {
        int totalDefense = 0;

        // Base defense from ring stats
        if (wornRing != null) {
            totalDefense += wornRing.getArmorDefense();
        }

        // Add bonus defense from all equipped items (including armor)
        totalDefense += getEquippedModifierSum(ModifierType.BONUS_DEFENSE);

        return totalDefense;
    }

    // --- Getters and Setters for all equipment slots ---

    public Item getWornHelmet() {
        return wornHelmet;
    }

    public void setWornHelmet(Item wornHelmet) {
        this.wornHelmet = wornHelmet;
    }

    public Item getWornShield() {
        return wornShield;
    }

    public void setWornShield(Item wornShield) {
        this.wornShield = wornShield;
    }

    public Item getWornGauntlets() {
        return wornGauntlets;
    }

    public void setWornGauntlets(Item wornGauntlets) {
        this.wornGauntlets = wornGauntlets;
    }

    public Item getWornHauberk() {
        return wornHauberk;
    }

    public void setWornHauberk(Item wornHauberk) {
        this.wornHauberk = wornHauberk;
    }

    public Item getWornBreastplate() {
        return wornBreastplate;
    }

    public void setWornBreastplate(Item wornBreastplate) {
        this.wornBreastplate = wornBreastplate;
    }

    public Item getWornRing() {
        return wornRing;
    }

    public void setWornRing(Item wornRing) {
        this.wornRing = wornRing;
    }
}
