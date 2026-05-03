package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.Difficulty;

import java.util.Random;

public class PlayerStats {

    private int currentHP;
    private int currentMP;
    // private int food; // REPLACED BY SATIETY
    private int arrows;
    private int dexterity;
    private int strength;
    private int constitution;
    private int intelligence;
    private int wisdom;
    private int agility;
    private int charisma;

    // --- Experience and Leveling Fields ---
    private int level;
    private int experience;
    private int experienceToNextLevel;

    // --- NEW: Bone & Die System ---
    private java.util.List<com.bpm.minotaur.gamedata.dice.Die> dicePool = new java.util.ArrayList<>();
    private java.util.List<com.bpm.minotaur.gamedata.dice.Artifact> artifacts = new java.util.ArrayList<>();
    private int stamina = 3; // Initial stamina for dice selection

    // XP curve: BASE * LOG_BASE^(level-1). LOG_BASE 1.7 raises L2 wall to ~425,
    // L3 to ~722, slowing the early snowball versus the previous 1.6 base.
    private static final int BASE_XP_REQUIRED = 300;
    private static final double LOG_BASE = 1.7;

    private int treasureScore = 0;

    private int maxHP;
    private int maxMP;

    // --- Toxic Communion Stats ---
    private int toxicity = 0;
    private int maxToxicity = 100;

    public int getToxicity() {
        return toxicity;
    }

    public void setToxicity(int toxicity) {
        this.toxicity = toxicity;
    }

    public int getMaxToxicity() {
        return maxToxicity;
    }

    public void setMaxToxicity(int maxToxicity) {
        this.maxToxicity = maxToxicity;
    }

    public void modifyToxicity(int amount) {
        this.toxicity += amount;
        if (this.toxicity < 0)
            this.toxicity = 0;
        if (this.toxicity > 100)
            this.toxicity = 100;
    }

    private final float vulnerabilityMultiplier;

    // --- Survival Stats ---
    private float satiety = 80.0f; // 0-100, formerly 'food'
    private float hydration = 80.0f; // 0-100
    private float bodyTemperature = 37.0f; // Celsius. Normal ~37.

    public static final float MAX_SATIETY = 100.0f;
    public static final float MAX_HYDRATION = 100.0f;
    public static final float BODY_TEMP_NORMAL = 37.0f;
    public static final float BODY_TEMP_FREEZING = 32.0f; // Hypothermia start
    public static final float BODY_TEMP_OVERHEAT = 41.0f; // Hyperthermia start

    public int getSatiety() {
        return (int) satiety;
    }

    public float getSatietyFloat() {
        return satiety;
    }

    public void setSatiety(float v) {
        this.satiety = Math.max(0, Math.min(MAX_SATIETY, v));
    }

    public void modifySatiety(float amount) {
        setSatiety(this.satiety + amount);
    }

    public int getHydration() {
        return (int) hydration;
    }

    public float getHydrationFloat() {
        return hydration;
    }

    public void setHydration(float v) {
        this.hydration = Math.max(0, Math.min(MAX_HYDRATION, v));
    }

    public void modifyHydration(float amount) {
        setHydration(this.hydration + amount);
    }

    public float getBodyTemperature() {
        return bodyTemperature;
    }

    public void setBodyTemperature(float v) {
        this.bodyTemperature = v;
    }

    public PlayerStats(Difficulty difficulty) {
        // Set stats based on difficulty
        this.currentHP = difficulty.startWarStrength; // Note: Difficulty field names might still be old
        this.currentMP = difficulty.startSpiritualStrength;
        this.satiety = difficulty.startFood; // Map startFood to Satiety
        this.arrows = difficulty.startArrows;
        this.vulnerabilityMultiplier = difficulty.vulnerabilityMultiplier;

        this.maxHP = difficulty.startWarStrength;
        this.maxMP = difficulty.startSpiritualStrength;

        this.dexterity = 10;
        this.strength = 10;
        this.constitution = 10;
        this.intelligence = 10;
        this.wisdom = 10;
        this.agility = 10;
        this.charisma = 10;

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

        // HP scales with CON, MP scales with INT. Base 3 ± 1, minimum 1.
        int hpIncrease = Math.max(1, 2 + getConModifier() + new Random().nextInt(3));
        int mpIncrease = Math.max(1, 2 + getIntModifier() + new Random().nextInt(3));

        this.maxHP += hpIncrease;
        this.maxMP += mpIncrease;

        // Partial heal (25% of new max) — rewarding but not HP-resetting.
        // Full heals trivialized early floors by enabling HP-farming on weak mobs.
        this.currentHP = Math.min(this.maxHP, this.currentHP + this.maxHP / 4);
        this.currentMP = Math.min(this.maxMP, this.currentMP + this.maxMP / 4);
    }

    /**
     * To-hit bonus: quarter level + DEX modifier. DEX 10 = no change from base.
     * DEX 16 gives +3 to-hit on top of level contribution.
     */
    public int getToHitBonus() {
        return Math.max(1, this.level / 4 + getDexModifier());
    }

    /**
     * Damage bonus from STR (NetHack-style: (STR-10)/2, min 0).
     */
    public int getDamageBonus() {
        return Math.max(0, (getEffectiveStrength() - 10) / 2);
    }

    /**
     * Legacy: kept for UI display and backward compatibility. Do NOT use in combat calculations.
     * Use getToHitBonus() and getDamageBonus() separately instead.
     */
    public int getAttackModifier() {
        return this.level;
    }

    public void addArrows(int amount) {
        this.arrows += amount;
        if (this.arrows > 99) {
            this.arrows = 99; // Cap at 99 as per the manual
        }
    }

    public void addFood(int amount) {
        // Alias to modifySatiety
        modifySatiety(amount);
    }

    public void decrementArrow() {
        if (this.arrows > 0) {
            this.arrows--;
        }
    }

    public void addHydration(int amount) {
        modifyHydration(amount);
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
        return (int) satiety;
    }

    public void setFood(int food) {
        this.satiety = food;
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

    public void restoreMP(int amount) {
        if (amount <= 0)
            return;
        this.currentMP += amount;
        if (this.currentMP > this.maxMP) {
            this.currentMP = this.maxMP;
        }
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

    public int getStrength() {
        return strength;
    }

    public void setStrength(int strength) {
        this.strength = strength;
    }

    public int getConstitution() { return constitution; }
    public void setConstitution(int v) { this.constitution = v; }
    public void modifyConstitution(int amount) { this.constitution += amount; }

    public int getIntelligence() { return intelligence; }
    public void setIntelligence(int v) { this.intelligence = v; }
    public void modifyIntelligence(int amount) { this.intelligence += amount; }

    public int getWisdom() { return wisdom; }
    public void setWisdom(int v) { this.wisdom = v; }
    public void modifyWisdom(int amount) { this.wisdom += amount; }

    public int getAgility() { return agility; }
    public void setAgility(int v) { this.agility = v; }
    public void modifyAgility(int amount) { this.agility += amount; }

    public int getCharisma() { return charisma; }
    public void setCharisma(int v) { this.charisma = v; }
    public void modifyCharisma(int amount) { this.charisma += amount; }

    // --- Primary Attribute Modifiers (NetHack-style: (stat-10)/2) ---
    public int getStrModifier() { return (strength - 10) / 2; }
    public int getDexModifier() { return (dexterity - 10) / 2; }
    public int getConModifier() { return (constitution - 10) / 2; }
    public int getIntModifier() { return (intelligence - 10) / 2; }
    public int getWisModifier() { return (wisdom - 10) / 2; }
    public int getAgiModifier() { return (agility - 10) / 2; }

    /** Base stamina before CON and equipment bonuses. */
    public int getBaseStamina() { return stamina; }

    /** Base crit chance constant. All stat-dependent contributions are added in Player.getCritChance(). */
    public float getBaseCritChance() {
        return 0.05f;
    }

    // --- Effective Stats (Toxicity Modifiers) ---

    /** Effective strength using raw thresholds (no equipment shift). */
    public int getEffectiveStrength() {
        return getEffectiveStrength(0);
    }

    /**
     * Effective strength with a toxicity threshold shift applied.
     * Pass player.getToxicityThresholdShift() to include equipment and ring bonuses.
     */
    public int getEffectiveStrength(int thresholdShift) {
        int eff = strength;
        int medLow  = 26 + thresholdShift;
        int medHigh = 75 + thresholdShift;
        int critLow = 76 + thresholdShift;
        if (toxicity >= medLow && toxicity <= medHigh) {
            eff += Math.max(1, (int) (strength * 0.1f));
        }
        if (toxicity >= critLow) {
            eff += Math.max(2, (int) (strength * 0.2f));
        }
        return eff;
    }

    /** Defense multiplier using raw thresholds. */
    public float getDefenseMultiplier() {
        return getDefenseMultiplier(0);
    }

    /**
     * Defense multiplier with a toxicity threshold shift applied.
     * Pass player.getToxicityThresholdShift() to include equipment and ring bonuses.
     */
    public float getDefenseMultiplier(int thresholdShift) {
        int medLow  = 26 + thresholdShift;
        int medHigh = 75 + thresholdShift;
        int critLow = 76 + thresholdShift;
        if (toxicity >= medLow && toxicity <= medHigh) return 0.9f;
        if (toxicity >= critLow) return 0.8f;
        return 1.0f;
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

    // --- Cooking Stats ---
    private int kindlingCount = 0;
    private int cookingWaterCount = 0;
    private int cookingSkill = 0;

    public int getKindlingCount() { return kindlingCount; }
    public void modifyKindlingCount(int amount) {
        this.kindlingCount += amount;
        if (this.kindlingCount < 0) this.kindlingCount = 0;
    }

    public int getCookingWaterCount() { return cookingWaterCount; }
    public void modifyCookingWaterCount(int amount) {
        this.cookingWaterCount += amount;
        if (this.cookingWaterCount < 0) this.cookingWaterCount = 0;
    }

    public int getCookingSkill() { return cookingSkill; }
    public void incrementCookingSkill() { this.cookingSkill++; }
}
