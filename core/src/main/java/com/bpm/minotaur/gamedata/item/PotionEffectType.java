package com.bpm.minotaur.gamedata.item;

import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.managers.StatusManager;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;

/**
 * Defines the true, underlying effect of a randomized potion.
 */
public enum PotionEffectType {

    HEALING(true, "Healing", "You feel better.", "It feels restorative!"),
    POISON(true, "Poison", "You feel very sick...", "You feel sick..."),
    GAIN_STRENGTH(true, "Gain Strength", "You feel stronger!", "You feel stronger!"),
    BLINDNESS(true, "Blindness", "You can't see!", "You are blinded!"),
    CONFUSION(true, "Confusion", "You feel confused...", "You feel confused..."),
    LEVITATION(false, "Levitation", "You float in the air!", "You begin to float!"); // Example of non-self-identifying
    // ... we can add more effects from the NetHack list later.

    private final boolean selfIdentifies;
    private final String baseName; // "Potion of [baseName]"
    private final String description; // "A Potion of [baseName]."
    private final String consumeMessage; // Message shown on consumption

    PotionEffectType(boolean selfIdentifies, String baseName, String description, String consumeMessage) {
        this.selfIdentifies = selfIdentifies;
        this.baseName = baseName;
        this.description = "A Potion of " + description;
        this.consumeMessage = consumeMessage;
    }

    public String getBaseName() { return baseName; }
    public String getDescription() { return description; }
    public String getConsumeMessage() { return consumeMessage; }
    public boolean doesSelfIdentify() { return selfIdentifies; }

    /**
     * Applies the potion's effect to the player.
     * @param player The player consuming the potion.
     * @param statusManager A reference to the game's StatusManager.
     */
    public void applyEffect(Player player, StatusManager statusManager) {
        // We get the StatusManager from the player when needed
        PlayerStats stats = player.getStats();

        switch (this) {
            case HEALING:
                // Heal for 25% of max HP (War Strength), or a flat 10, whichever is greater
                int healAmount = Math.max(10, stats.getMaxWarStrength() / 4);
                stats.heal(healAmount); // We added this method to PlayerStats in M1
                break;
            case POISON:
                // Apply POISONED status for 10 turns with potency 2 (damage)
                statusManager.addEffect(StatusEffectType.POISONED, 10, 2, true);
                break;
            case GAIN_STRENGTH:
                // Permanently increase max War Strength
                stats.modifyBaseWarStrength(1); // We added this method to PlayerStats in M1
                break;
            case BLINDNESS:
                // Apply BLIND status for 20 turns
                // We'll need to add BLIND to StatusEffectType enum
                // statusManager.addEffect(StatusEffectType.BLIND, 20, 1, false);
                break;
            case CONFUSION:
                statusManager.addEffect(StatusEffectType.CONFUSED, 15, 1, false);
                break;
            case LEVITATION:
                statusManager.addEffect(StatusEffectType.FLOATING, 30, 1, false);
                break;
        }
    }
}
