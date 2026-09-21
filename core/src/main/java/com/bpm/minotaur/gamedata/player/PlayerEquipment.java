package com.bpm.minotaur.gamedata.player;

// import com.badlogic.gdx.Gdx;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.ModifierType;

public class PlayerEquipment {

    // Expanded Equipment slots
    private Item wornHelmet = null; // Head
    private Item wornEyes = null; // Eyes (New)
    private Item wornNeck = null; // Amulet (New)
    private Item wornBack = null; // Cloak/Back (New)
    private Item wornChest = null; // Chest (Hauberk/Breastplate)
    private Item wornArms = null; // Arms (New)
    private Item wornGauntlets = null; // Hands
    private Item wornLegs = null; // Legs (New)
    private Item wornBoots = null; // Feet (New)
    private Item wornRing = null; // Ring 1
    private Item wornRing2 = null; // Ring 2

    private Item wornShield = null; // Kept for legacy compatibility / explicit shield slot logic

    // Ceiling on the equipment-derived portion of Armor Class (base 10 + Dex is separate).
    // Without this, summing AC from all 7 armor slots plus every BONUS_AC modifier lets a
    // fully-geared player's AC climb past what monster to-hit rolls can ever clear.
    public static final int MAX_EQUIPMENT_AC_BONUS = 15;

    public PlayerEquipment() {
    }

    public int getEquippedModifierSum(ModifierType typeToFind) {
        int total = 0;
        Item[] equippedItems = {
                wornHelmet, wornEyes, wornNeck, wornBack, wornChest,
                wornArms, wornGauntlets, wornLegs, wornBoots, wornRing, wornRing2, wornShield
        };

        // Debug Log Builder
        // StringBuilder debugLog = new StringBuilder();
        // boolean debug = false; // Only log if we find something, to reduce spam, or
        // toggle true for full spam

        for (Item item : equippedItems) {
            if (item != null && item.getModifiers() != null) {
                for (ItemModifier mod : item.getModifiers()) {
                    if (mod.type == typeToFind) {
                        total += mod.value;
                        // debugLog.append(" [").append(item.getDisplayName()).append(":
                        // ").append(mod.value).append("]");
                        // debug = true;
                    }
                }
            }
        }

        // Only log if we found modifiers or if it's a critical stat check (optional)
        // Only log if we found modifiers or if it's a critical stat check (optional)
        /*
         * if (debug) {
         * Gdx.app.log("Equipment",
         * "Modifier Calc (" + typeToFind + "): Total=" + total + " Sources:" +
         * debugLog.toString());
         * }
         */

        return total;
    }

    public int getACBonus() {
        int totalDefense = 0;
        StringBuilder log = new StringBuilder();

        // Base (template-only) AC per slot. BONUS_AC modifiers are added exactly
        // once below via getEquippedModifierSum() -- summing
        // Item.getArmorClassBonus() here as well would double-count them, since
        // that method already folds an item's own modifiers into its total.
        if (wornHelmet != null) {
            totalDefense += wornHelmet.getBaseArmorClassBonus();
            log.append(" Head(").append(wornHelmet.getBaseArmorClassBonus()).append(")");
        }
        if (wornChest != null) {
            totalDefense += wornChest.getBaseArmorClassBonus();
            log.append(" Chest(").append(wornChest.getBaseArmorClassBonus()).append(")");
        }
        if (wornGauntlets != null) {
            totalDefense += wornGauntlets.getBaseArmorClassBonus();
            log.append(" Hands(").append(wornGauntlets.getBaseArmorClassBonus()).append(")");
        }
        if (wornBoots != null) {
            totalDefense += wornBoots.getBaseArmorClassBonus();
            log.append(" Feet(").append(wornBoots.getBaseArmorClassBonus()).append(")");
        }
        if (wornLegs != null) {
            totalDefense += wornLegs.getBaseArmorClassBonus();
            log.append(" Legs(").append(wornLegs.getBaseArmorClassBonus()).append(")");
        }
        if (wornArms != null) {
            totalDefense += wornArms.getBaseArmorClassBonus();
            log.append(" Arms(").append(wornArms.getBaseArmorClassBonus()).append(")");
        }
        if (wornShield != null) {
            totalDefense += wornShield.getBaseArmorClassBonus();
            log.append(" Shield(").append(wornShield.getBaseArmorClassBonus()).append(")");
        }

        // Add bonus defense from all equipped items' modifiers (covers the 7 armor
        // slots above, plus rings/eyes/neck/back, which have no base-AC term of
        // their own).
        int modifierDefense = getEquippedModifierSum(ModifierType.BONUS_AC);
        if (modifierDefense > 0) {
            totalDefense += modifierDefense;
            log.append(" Mods(").append(modifierDefense).append(")");
        }

        return Math.min(MAX_EQUIPMENT_AC_BONUS, totalDefense);
    }

    public int getArmorDefense() {
        return getACBonus();
    }

    public int getTotalDamageReduction() {
        int dr = 0;
        if (wornHelmet != null) dr += wornHelmet.getDamageReduction();
        if (wornChest != null) dr += wornChest.getDamageReduction();
        if (wornGauntlets != null) dr += wornGauntlets.getDamageReduction();
        if (wornBoots != null) dr += wornBoots.getDamageReduction();
        if (wornLegs != null) dr += wornLegs.getDamageReduction();
        if (wornArms != null) dr += wornArms.getDamageReduction();
        if (wornShield != null) dr += wornShield.getDamageReduction();
        return dr;
    }

    public String getArmorTier() {
        if (wornChest != null && wornChest.getArmorCategory() != null) {
            return wornChest.getArmorCategory();
        }
        return "LIGHT";
    }

    public boolean isWearingHeavyArmor() {
        return "HEAVY".equalsIgnoreCase(getArmorTier());
    }

    public int getMaxDexBonus() {
        if (wornChest != null) {
            return wornChest.getMaxDexBonus();
        }
        return 99;
    }

    public boolean hasStealthDisadvantage() {
        if (wornChest != null && wornChest.hasStealthDisadvantage()) {
            return true;
        }
        Item[] pieces = { wornHelmet, wornChest, wornGauntlets, wornLegs, wornBoots, wornShield };
        for (Item p : pieces) {
            if (p != null && "HEAVY".equalsIgnoreCase(p.getArmorCategory()) && p.hasStealthDisadvantage()) {
                return true;
            }
        }
        return false;
    }

    public int getRingDefense() {
        int totalDefense = 0;
        if (wornRing != null) {
            totalDefense += wornRing.getArmorClassBonus();
        }
        if (wornRing2 != null) {
            totalDefense += wornRing2.getArmorClassBonus();
        }

        // Ring of Protection Effect (+2 AC)
        if (hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.PROTECTION)) {
            totalDefense += 2;
        }

        totalDefense += getEquippedModifierSum(ModifierType.BONUS_AC);
        return totalDefense;
    }

    // --- Getters and Setters ---

    public Item getWornHelmet() {
        return wornHelmet;
    }

    public void setWornHelmet(Item item) {
        this.wornHelmet = item;
    }

    public Item getWornEyes() {
        return wornEyes;
    }

    public void setWornEyes(Item item) {
        this.wornEyes = item;
    }

    public Item getWornNeck() {
        return wornNeck;
    }

    public void setWornNeck(Item item) {
        this.wornNeck = item;
    }

    public Item getWornBack() {
        return wornBack;
    }

    public void setWornBack(Item item) {
        this.wornBack = item;
    }

    public Item getWornChest() {
        return wornChest;
    }

    public void setWornChest(Item item) {
        this.wornChest = item;
    }

    // Legacy getters
    public Item getWornHauberk() {
        return wornChest;
    }

    public void setWornHauberk(Item item) {
        this.wornChest = item;
    }

    public Item getWornBreastplate() {
        return wornChest;
    }

    public void setWornBreastplate(Item item) {
        this.wornChest = item;
    }

    public Item getWornArms() {
        return wornArms;
    }

    public void setWornArms(Item item) {
        this.wornArms = item;
    }

    public Item getWornGauntlets() {
        return wornGauntlets;
    }

    public void setWornGauntlets(Item item) {
        this.wornGauntlets = item;
    }

    public Item getWornLegs() {
        return wornLegs;
    }

    public void setWornLegs(Item item) {
        this.wornLegs = item;
    }

    public Item getWornBoots() {
        return wornBoots;
    }

    public void setWornBoots(Item item) {
        this.wornBoots = item;
    }

    public Item getWornRing() {
        return wornRing;
    }

    public void setWornRing(Item item) {
        this.wornRing = item;
    }

    public Item getWornRing2() {
        return wornRing2;
    }

    public void setWornRing2(Item item) {
        this.wornRing2 = item;
    }

    public Item getWornShield() {
        return wornShield;
    }

    public void setWornShield(Item item) {
        this.wornShield = item;
    }

    public java.util.List<Item> getAllEquipped() {
        java.util.List<Item> items = new java.util.ArrayList<>();
        if (wornHelmet != null)
            items.add(wornHelmet);
        if (wornEyes != null)
            items.add(wornEyes);
        if (wornNeck != null)
            items.add(wornNeck);
        if (wornBack != null)
            items.add(wornBack);
        if (wornChest != null)
            items.add(wornChest);
        if (wornArms != null)
            items.add(wornArms);
        if (wornGauntlets != null)
            items.add(wornGauntlets);
        if (wornLegs != null)
            items.add(wornLegs);
        if (wornBoots != null)
            items.add(wornBoots);
        if (wornRing != null)
            items.add(wornRing);
        if (wornRing2 != null)
            items.add(wornRing2);
        if (wornShield != null)
            items.add(wornShield);
        return items;
    }

    public boolean hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType type) {
        if (wornRing != null && wornRing.getRingEffect() == type) {
            return true;
        }
        if (wornRing2 != null && wornRing2.getRingEffect() == type) {
            return true;
        }
        return false;
    }

    public int countRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType type) {
        int count = 0;
        if (wornRing != null && wornRing.getRingEffect() == type) count++;
        if (wornRing2 != null && wornRing2.getRingEffect() == type) count++;
        return count;
    }

    public int getRingCharges(com.bpm.minotaur.gamedata.item.RingEffectType type) {
        int total = 0;
        if (wornRing != null && wornRing.getRingEffect() == type) {
            total += wornRing.getCurrentCharges();
        }
        if (wornRing2 != null && wornRing2.getRingEffect() == type) {
            total += wornRing2.getCurrentCharges();
        }
        return total;
    }

    public boolean expendRingCharge(com.bpm.minotaur.gamedata.item.RingEffectType type) {
        if (wornRing != null && wornRing.getRingEffect() == type && wornRing.hasCharges()) {
            return wornRing.decrementCharges();
        }
        if (wornRing2 != null && wornRing2.getRingEffect() == type && wornRing2.hasCharges()) {
            return wornRing2.decrementCharges();
        }
        return false;
    }

    public void rechargeRings(int amount) {
        if (wornRing != null) wornRing.recharge(amount);
        if (wornRing2 != null) wornRing2.recharge(amount);
    }

    public void fullyRechargeRings() {
        if (wornRing != null) wornRing.fullyRecharge();
        if (wornRing2 != null) wornRing2.fullyRecharge();
    }
}
