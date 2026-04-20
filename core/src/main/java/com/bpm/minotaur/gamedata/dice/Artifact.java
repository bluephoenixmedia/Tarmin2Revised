package com.bpm.minotaur.gamedata.dice;

/**
 * An Artifact (inspired by Jokers in Balatro) that provides passive bonuses
 * or modifies the rules of the Dice resolution phase.
 */
public abstract class Artifact {
    private String name;
    private String description;

    public Artifact(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // Example hook: Trigger when a hand is resolved
    public void onHandResolved(CombatHandContext context) {
        // Override to add logic
    }

    // Example hook: Modify a die face before it's calculated
    public void onProcessFace(DieFace face, CombatHandContext context) {
        // Override
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    // Placeholder context class to avoiding circular dep issues for now
    public static class CombatHandContext {
        public int finalDamage;
        public int finalBlock;
        public int finalHealing;
        public int multiplier = 1;
        // Add more context as needed
    }
}
