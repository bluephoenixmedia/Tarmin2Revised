package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.Difficulty;

import java.util.Random;

public class PlayerStats {

    private int warStrength;
    private int spiritualStrength;
    private int food;
    private int arrows;
    private int dexterity; // <--- NEW: RPG Attribute for Accuracy/Evasion

    // --- Experience and Leveling Fields ---
    private int level;
    private int experience;
    private int experienceToNextLevel;
    private static final int BASE_XP_REQUIRED = 100;
    private static final double LOG_BASE = 1.2;

    private int treasureScore = 0;

    private int maxWarStrength;
    private int maxSpiritualStrength;

    private final float vulnerabilityMultiplier;

    public PlayerStats(Difficulty difficulty) {
        // Set stats based on difficulty
        this.warStrength = difficulty.startWarStrength;
        this.spiritualStrength = difficulty.startSpiritualStrength;
        this.food = difficulty.startFood;
        this.arrows = difficulty.startArrows;
        this.vulnerabilityMultiplier = difficulty.vulnerabilityMultiplier;

        this.maxWarStrength = difficulty.startWarStrength;
        this.maxSpiritualStrength = difficulty.startSpiritualStrength;

        this.dexterity = 10; // <--- NEW: Default starting dexterity

        // --- Initialize Leveling Stats ---
        this.level = 1;
        this.experience = 0;
        this.experienceToNextLevel = calculateXpForLevel(2);
    }

    // --- Stat-Only Logic Methods (Moved from Player) ---

    private int calculateXpForLevel(int targetLevel) {
        if (targetLevel <= 1) return 0;
        // Formula: base * (log_base ^ (level -1))
        return (int) (BASE_XP_REQUIRED * Math.pow(LOG_BASE, targetLevel - 1));
    }

    /**
     * Adds experience and handles leveling up internally.
     * @param amount The amount of experience to add.
     * @return true if the player leveled up, false otherwise.
     */
    public boolean addExperience(int amount) {
        if (amount <= 0) return false;
        this.experience += amount;

        boolean leveledUp = false;
        while (this.experience >= this.experienceToNextLevel) {
            levelUp();
            leveledUp = true;
        }
        return leveledUp;
    }

    private void levelUp() {
        this.experience -= this.experienceToNextLevel; // Carry over remaining XP
        this.level++;
        this.experienceToNextLevel = calculateXpForLevel(this.level + 1);

        // Increase stats
        int wsIncrease = 2 + new Random().nextInt(2); // Increase by 2 or 3
        int ssIncrease = 2 + new Random().nextInt(2); // Increase by 2 or 3

        this.maxWarStrength += wsIncrease;
        this.maxSpiritualStrength += ssIncrease;

        // Fully heal player on level up
        this.warStrength = this.maxWarStrength;
        this.spiritualStrength = this.maxSpiritualStrength;
    }

    /**
     * Attack modifier based on player level.
     * @return The bonus damage to add to attacks.
     */
    public int getAttackModifier() {
        return this.level; // Simple: +1 damage per level.
    }

    public void addArrows(int amount) {
        this.arrows += amount;
        if (this.arrows > 99) {
            this.arrows = 99; // Cap at 99 as per the manual
        }
    }

    public void addFood(int amount) {
        this.food += amount;
        if (this.food > 99) {
            this.food = 99; // Cap at 99 as per the manual
        }
    }

    public void decrementArrow() {
        if (this.arrows > 0) {
            this.arrows--;
        }
    }

    public void incrementTreasureScore(int amount) {
        this.treasureScore += amount;
    }

    // --- Getters and Setters ---

    public int getWarStrength() {
        return warStrength;
    }

    public void setWarStrength(int warStrength) {
        this.warStrength = warStrength;
    }

    public int getSpiritualStrength() {
        return spiritualStrength;
    }

    public void setSpiritualStrength(int spiritualStrength) {
        this.spiritualStrength = spiritualStrength;
    }

    public int getFood() {
        return food;
    }

    public void setFood(int food) {
        this.food = food;
    }

    public int getArrows() {
        return arrows;
    }

    public void setArrows(int arrows) {
        this.arrows = arrows;
    }

    public int getLevel() {
        return level;
    }

    public int getExperience() {
        return experience;
    }

    public int getExperienceToNextLevel() {
        return experienceToNextLevel;
    }

    public int getTreasureScore() {
        return treasureScore;
    }

    public void setTreasureScore(int treasureScore) {
        this.treasureScore = treasureScore;
    }

    public int getMaxWarStrength() {
        return maxWarStrength;
    }

    public void setMaxWarStrength(int maxWarStrength) {
        this.maxWarStrength = maxWarStrength;
    }

    public int getMaxSpiritualStrength() {
        return maxSpiritualStrength;
    }

    public void setMaxSpiritualStrength(int maxSpiritualStrength) {
        this.maxSpiritualStrength = maxSpiritualStrength;
    }

    public float getVulnerabilityMultiplier() {
        return vulnerabilityMultiplier;
    }

    /**
     * Heals the player's War Strength (health).
     * Cannot heal beyond maximum.
     * @param amount The amount to heal.
     */
    public void heal(int amount) {
        if (amount <= 0) return;
        this.warStrength += amount;
        if (this.warStrength > this.maxWarStrength) {
            this.warStrength = this.maxWarStrength;
        }
    }

    /**
     * Permanently increases the player's base (max) War Strength.
     * Also heals the player for the same amount.
     * @param amount The amount to increase max strength by.
     */
    public void modifyBaseWarStrength(int amount) {
        if (amount <= 0) return;
        this.maxWarStrength += amount;
        heal(amount); // Also heal the player by the amount gained
    }
    public int getDexterity() {
        return dexterity;
    }

    public void setDexterity(int dexterity) {
        this.dexterity = dexterity;
    }
}
