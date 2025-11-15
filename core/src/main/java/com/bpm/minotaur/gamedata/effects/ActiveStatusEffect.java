// NEW FILE: core/src/main/java/com/bpm/minotaur/gamedata/effects/ActiveStatusEffect.java
package com.bpm.minotaur.gamedata.effects;

public class ActiveStatusEffect {

    private StatusEffectType type;
    private int duration; // Remaining duration in game turns. -1 for infinite.
    private int potency;  // e.g., Poison damage per turn, or +5 STR

    // Constructor for LibGDX JSON serialization
    public ActiveStatusEffect() { }

    public ActiveStatusEffect(StatusEffectType type, int duration, int potency) {
        this.type = type;
        this.duration = duration;
        this.potency = potency;
    }

    /**
     * Decrements the duration.
     * @return true if the effect has expired, false otherwise.
     */
    public boolean updateTurn() {
        if (duration == -1) {
            return false; // Infinite duration
        }
        duration--;
        return duration <= 0;
    }

    // --- Getters ---
    public StatusEffectType getType() { return type; }
    public int getDuration() { return duration; }
    public int getPotency() { return potency; }

    // --- Setters (e.g., for extending duration) ---
    public void setDuration(int duration) { this.duration = duration; }
    public void addDuration(int additional) {
        if (duration == -1) return;
        this.duration += additional;
    }
    public void setPotency(int potency) { this.potency = potency; }
}
