package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.math.MathUtils;
import com.bpm.minotaur.gamedata.ModifierType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemModifier;

import java.util.Random;

public class LootModifierManager {

    private final Random random = new Random();

    /**
     * Applies a random modifier to an item based on its level and type.
     */
    public void applyModifiers(Item item, int level) {
        // Only apply modifiers to weapons, armor, and rings
        if (!item.isWeapon() && !item.isArmor() && !item.isRing()) return;

        // Chance to add a Suffix (e.g., "of Brawn")
        if (random.nextFloat() < 0.5f) {
            applySuffix(item, level);
        }

        // Chance to add a Prefix (e.g., "Sharp")
        if (random.nextFloat() < 0.3f) {
            applyPrefix(item, level);
        }
    }

    private void applySuffix(Item item, int level) {
        int power = 1 + (level / 3); // Scale power with level

        if (item.isArmor() || item.isRing()) {
            int roll = random.nextInt(3);
            switch (roll) {
                case 0:
                    // Brawn: Increases Max War Strength (HP)
                    item.addModifier(new ItemModifier(ModifierType.BONUS_WAR_STRENGTH, 5 * power, "of Brawn"));
                    break;
                case 1:
                    // Spirit: Increases Max Spiritual Strength (Mana)
                    item.addModifier(new ItemModifier(ModifierType.BONUS_SPIRITUAL_STRENGTH, 5 * power, "of Spirit"));
                    break;
                case 2:
                    // Protection: Increases Armor Class directly
                    item.addModifier(new ItemModifier(ModifierType.BONUS_DEFENSE, 1 + (power / 2), "of Protection"));
                    break;
            }
        } else if (item.isWeapon()) {
            // Weapon Suffixes
            item.addModifier(new ItemModifier(ModifierType.BONUS_DAMAGE, power, "of Power"));
        }
    }

    private void applyPrefix(Item item, int level) {
        int power = 1 + (level / 4);

        if (item.isWeapon()) {
            int roll = random.nextInt(3);
            switch (roll) {
                case 0:
                    item.addModifier(new ItemModifier(ModifierType.BONUS_DAMAGE, 1 * power, "Sharp"));
                    break;
                case 1:
                    item.addModifier(new ItemModifier(ModifierType.ADD_POISON_DAMAGE, 1, "Venomous"));
                    break;
                case 2:
                    item.addModifier(new ItemModifier(ModifierType.BONUS_DAMAGE, 2 * power, "Deadly"));
                    break;
            }
        } else if (item.isArmor()) {
            // Armor Prefixes
            item.addModifier(new ItemModifier(ModifierType.BONUS_DEFENSE, 1 + (power/2), "Sturdy"));
        }
    }
}
