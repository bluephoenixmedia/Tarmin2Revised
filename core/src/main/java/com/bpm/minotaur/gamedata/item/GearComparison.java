package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerEquipment;

/**
 * Utility for evaluating and comparing weapons and armor against the player's
 * currently equipped gear in equivalent equipment slots.
 *
 * Provides visual indicators:
 * - Green up-chevron: ▲ (+X) for upgrades
 * - Red down-chevron: ▼ (-X) for downgrades
 * - White dash: - (=) for equal stats / sidegrades
 */
public class GearComparison {

    public static final Color COLOR_UPGRADE = new Color(0.13f, 0.77f, 0.37f, 1f); // Vibrant emerald green
    public static final Color COLOR_DOWNGRADE = new Color(0.94f, 0.27f, 0.27f, 1f); // Vibrant crimson red
    public static final Color COLOR_SIDEGRADE = new Color(0.85f, 0.85f, 0.85f, 1f); // Crisp white/light gray

    public static class ComparisonResult {
        public final float diff;
        public final String badge;
        public final Color color;
        public final boolean isUpgrade;
        public final boolean isDowngrade;

        public ComparisonResult(float diff, String badge, Color color, boolean isUpgrade, boolean isDowngrade) {
            this.diff = diff;
            this.badge = badge;
            this.color = color;
            this.isUpgrade = isUpgrade;
            this.isDowngrade = isDowngrade;
        }
    }

    /**
     * Compares a candidate item against the player's currently equipped gear in the matching slot.
     * Returns null if the item is not equippable gear (e.g. food, potions, keys, misc).
     */
    public static ComparisonResult compare(Item candidate, Player player) {
        if (candidate == null || player == null) return null;

        if (candidate.isWeapon()) {
            return compareWeapon(candidate, player);
        } else if (candidate.isArmor() || candidate.isShield()) {
            return compareArmor(candidate, player);
        }

        return null;
    }

    private static ComparisonResult compareWeapon(Item candidate, Player player) {
        Inventory inv = player.getInventory();
        Item equipped = (inv != null) ? inv.getRightHand() : null;

        float candAvg = calculateAverageDamage(candidate.getDamageDice()) + candidate.getEnchantment();
        float eqAvg = (equipped != null && equipped.isWeapon())
                ? calculateAverageDamage(equipped.getDamageDice()) + equipped.getEnchantment()
                : 0f;

        float diff = candAvg - eqAvg;
        if (diff > 0.05f) {
            String badge = (equipped == null)
                    ? String.format("UP (+%.1f Dmg)", candAvg)
                    : String.format("UP (+%.1f Dmg)", diff);
            return new ComparisonResult(diff, badge, COLOR_UPGRADE, true, false);
        } else if (diff < -0.05f) {
            String badge = String.format("DOWN (%.1f Dmg)", diff);
            return new ComparisonResult(diff, badge, COLOR_DOWNGRADE, false, true);
        } else {
            return new ComparisonResult(0f, "- (= Dmg)", COLOR_SIDEGRADE, false, false);
        }
    }

    private static ComparisonResult compareArmor(Item candidate, Player player) {
        Item equipped = getEquippedArmorInSlot(candidate, player);

        int candAC = candidate.getArmorClassBonus();
        int eqAC = (equipped != null) ? equipped.getArmorClassBonus() : 0;

        int diff = candAC - eqAC;
        if (diff > 0) {
            String badge = (equipped == null)
                    ? "UP (+" + candAC + " AC)"
                    : "UP (+" + diff + " AC)";
            return new ComparisonResult(diff, badge, COLOR_UPGRADE, true, false);
        } else if (diff < 0) {
            String badge = "DOWN (" + diff + " AC)";
            return new ComparisonResult(diff, badge, COLOR_DOWNGRADE, false, true);
        } else {
            return new ComparisonResult(0f, "- (= AC)", COLOR_SIDEGRADE, false, false);
        }
    }

    private static Item getEquippedArmorInSlot(Item item, Player player) {
        if (player == null || item == null) return null;
        PlayerEquipment eq = player.getEquipment();
        Inventory inv = player.getInventory();

        if (item.isShield()) {
            if (eq != null && eq.getWornShield() != null) return eq.getWornShield();
            if (inv != null && inv.getLeftHand() != null && inv.getLeftHand().isShield()) return inv.getLeftHand();
            return null;
        }
        if (eq == null) return null;

        if (item.isHelmet()) {
            return eq.getWornHelmet();
        } else if (item.isGauntlets()) {
            return eq.getWornGauntlets();
        } else if (item.isBoots()) {
            return eq.getWornBoots();
        } else if (item.isLegs()) {
            return eq.getWornLegs();
        } else if (item.isArms()) {
            return eq.getWornArms();
        } else if (item.isCloak()) {
            return eq.getWornBack();
        } else if (item.isAmulet()) {
            return eq.getWornNeck();
        } else if (item.isRing()) {
            return eq.getWornRing() != null ? eq.getWornRing() : eq.getWornRing2();
        } else if (item.isTorso() || item.isArmor()) {
            return eq.getWornChest();
        }

        return null;
    }

    public static float calculateAverageDamage(String diceStr) {
        if (diceStr == null || diceStr.trim().isEmpty()) return 0f;

        try {
            String clean = diceStr.toLowerCase().trim();
            int plusIdx = clean.indexOf('+');
            int minusIdx = clean.indexOf('-');
            int bonus = 0;
            String dicePart = clean;

            if (plusIdx != -1) {
                bonus = Integer.parseInt(clean.substring(plusIdx + 1).trim());
                dicePart = clean.substring(0, plusIdx).trim();
            } else if (minusIdx != -1) {
                bonus = -Integer.parseInt(clean.substring(minusIdx + 1).trim());
                dicePart = clean.substring(0, minusIdx).trim();
            }

            if (dicePart.contains("d")) {
                String[] parts = dicePart.split("d");
                int count = parts[0].isEmpty() ? 1 : Integer.parseInt(parts[0]);
                int sides = Integer.parseInt(parts[1]);
                return count * (1f + sides) / 2.0f + bonus;
            } else {
                return Float.parseFloat(dicePart) + bonus;
            }
        } catch (Exception e) {
            return 0f;
        }
    }
}
