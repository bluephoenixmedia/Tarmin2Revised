package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.Difficulty;

import java.util.Random;

public class PlayerStats {

    private int currentHP;
    private int currentMP;
    private int food;
    private int arrows;
    private int dexterity;

    // --- Experience and Leveling Fields ---
    private int level;
    private int experience;
    private int experienceToNextLevel;

    // --- NEW: Bone & Die System ---
    private java.util.List<com.bpm.minotaur.gamedata.dice.Die> dicePool = new java.util.ArrayList<>();
    private java.util.List<com.bpm.minotaur.gamedata.dice.Artifact> artifacts = new java.util.ArrayList<>();
    private int stamina = 3; // Initial stamina for dice selection

    // --- UPDATED: XP CONSTANTS FOR SLOWER PROGRESSION ---
    private static final int BASE_XP_REQUIRED = 250; // Increased from 150
    private static final double LOG_BASE = 1.6; // Changed from 1.5 to 1.6

    private int treasureScore = 0;

    private int maxHP;
    private int maxMP;

    private final float vulnerabilityMultiplier;

    public PlayerStats(Difficulty difficulty) {
        // Set stats based on difficulty
        this.currentHP = difficulty.startWarStrength; // Note: Difficulty field names might still be old
        this.currentMP = difficulty.startSpiritualStrength;
        this.food = difficulty.startFood;
        this.arrows = difficulty.startArrows;
        this.vulnerabilityMultiplier = difficulty.vulnerabilityMultiplier;

        this.maxHP = difficulty.startWarStrength;
        this.maxMP = difficulty.startSpiritualStrength;

        this.dexterity = 10;

        // --- Initialize Leveling Stats ---
        this.level = 1;
        this.experience = 0;
        this.experienceToNextLevel = calculateXpForLevel(2);
    }

    // --- Stat-Only Logic Methods ---

    private int calculateXpForLevel(int targetLevel) {
        if (targetLevel <= 1)
            return 0;
        // Formula: base * (log_base ^ (level -1))
        return (int) (BASE_XP_REQUIRED * Math.pow(LOG_BASE, targetLevel - 1));
    }

    /**
     * Adds experience. Does NOT automatically level up.
     * 
     * @param amount The amount of experience to add.
     * @return true if the player now has enough XP to level up.
     */
    public boolean addExperience(int amount) {
        if (amount <= 0)
            return false;
        this.experience += amount;

        return canLevelUp();
    }

    public boolean canLevelUp() {
        return this.experience >= this.experienceToNextLevel;
    }

    public void performLevelUp() {
        if (!canLevelUp())
            return;

        this.experience -= this.experienceToNextLevel; // Carry over remaining XP
        this.level++;
        this.experienceToNextLevel = calculateXpForLevel(this.level + 1);

        // --- UPDATED: Increased Stat Growth ---
        int hpIncrease = 3 + new Random().nextInt(3);
        int mpIncrease = 3 + new Random().nextInt(3);

        this.maxHP += hpIncrease;
        this.maxMP += mpIncrease;

        // Fully heal player on level up
        this.currentHP = this.maxHP;
        this.currentMP = this.maxMP;
    }

    /**
     * Attack modifier based on player level.
     * 
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

    public int getCurrentHP() {
        return currentHP;
    }

    public void setCurrentHP(int currentHP) {
        this.currentHP = currentHP;
    }

    public int getCurrentMP() {
        return currentMP;
    }

    public void setCurrentMP(int currentMP) {
        this.currentMP = currentMP;
    }

    // Deprecated Aliases for compatibility during refactor
    public int getWarStrength() {
        return currentHP;
    }

    public int getSpiritualStrength() {
        return currentMP;
    }

    public void setWarStrength(int val) {
        this.currentHP = val;
    }

    public void setSpiritualStrength(int val) {
        this.currentMP = val;
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

    public int getMaxHP() {
        return maxHP;
    }

    public void setMaxHP(int maxHP) {
        this.maxHP = maxHP;
    }

    public int getMaxMP() {
        return maxMP;
    }

    public void setMaxMP(int maxMP) {
        this.maxMP = maxMP;
    }

    public float getVulnerabilityMultiplier() {
        return vulnerabilityMultiplier;
    }

    /**
     * Heals the player's Hit Points.
     * Cannot heal beyond maximum.
     * 
     * @param amount The amount to heal.
     */
    public void heal(int amount) {
        if (amount <= 0)
            return;
        this.currentHP += amount;
        if (this.currentHP > this.maxHP) {
            this.currentHP = this.maxHP;
        }
    }

    /**
     * Permanently increases the player's base (max) HP.
     * Also heals the player for the same amount.
     * 
     * @param amount The amount to increase max HP by.
     */
    public void modifyBaseHP(int amount) {
        if (amount <= 0)
            return;
        this.maxHP += amount;
        heal(amount); // Also heal the player by the amount gained
    }

    public int getDexterity() {
        return dexterity;
    }

    public void setDexterity(int dexterity) {
        this.dexterity = dexterity;
    }

    // --- Bone & Die Getters ---
    public java.util.List<com.bpm.minotaur.gamedata.dice.Die> getDicePool() {
        return dicePool;
    }

    public java.util.List<com.bpm.minotaur.gamedata.dice.Artifact> getArtifacts() {
        return artifacts;
    }

    public int getStamina() {
        return stamina;
    }

    public void setStamina(int stamina) {
        this.stamina = stamina;
    }

    // --- Luck System ---
    private int luck = 0;

    public int getLuck() {
        return luck;
    }

    public void setLuck(int luck) {
        this.luck = luck;
    }

    public void modifyLuck(int amount) {
        this.luck += amount;
        // Clamp luck if desired, e.g., -10 to 10 approx NetHack style
        // For now leaving uncapped or soft capped?
        // NetHack is complicated. Let's just allow int range for now or clamp -13 to
        // 13.
        if (this.luck > 13)
            this.luck = 13;
        if (this.luck < -13)
            this.luck = -13;
    }
}
