package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.Renderable;
import com.bpm.minotaur.managers.StatusManager;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;

public class Monster implements Renderable {

    public enum MonsterType {
        // Bad Monsters
        GIANT_ANT,
        DWARF,
        GIANT_SCORPION,
        GIANT_SNAKE,
        KOBOLD,
        GOBLIN,
        TROGLODYTE,
        HOBGOBLIN,

        // Nasty Monsters
        GHOUL,
        SKELETON,
        CLOAKED_SKELETON,
        ZOMBIE,
        MUMMY,
        HARPY,
        GARGOYLE,
        WERERAT,

        // Horrible Monsters
        ALLIGATOR,
        DRAGON,
        WRAITH,
        GIANT,
        MINOTAUR,
        GHAST,
        BEHOLDER,
        GELATINOUS_CUBE,
        RUST_MONSTER,
        LICH,
        MIMIC,
        MIND_FLAYER,
        OWLBEAR,
        DISPLACER_BEAST,
        UMBER_HULK,
        SPIDER,
        ORC,
        WEREWOLF,
        TROLL,
        OGRE,
        BASILISK,
        MEDUSA,
        WYVERN,
        CHIMERA,
        HYDRA,
        VAMPIRE,
        IRON_GOLEM,
        PURPLE_WORM
    }

    private final MonsterType type;
    private final Vector2 position;
    private final MonsterColor monsterColor;
    private int warStrength;
    private int maxWarStrength; // New: For healing logic
    private int spiritualStrength;
    private int armor;
    private int ac; // Descending armor class
    private int magicResistance; // 0-100%
    private int moveSpeed;
    private int baseExperience;

    private final String[] spriteData;
    private Texture texture = null;
    public Vector2 scale;
    private MonsterFamily family;

    private int intelligence;

    private int dexterity;
    private boolean hasRangedAttack;
    private int attackRange;

    // --- AI Fields ---
    private MonsterTemplate.AiType aiType;
    private float healThreshold;
    private int spellChance;

    // --- Refactored Instance Data ---
    private com.bpm.minotaur.gamedata.Inventory inventory;
    private int tameness = 0; // 0 = Hostile
    private boolean invisible = false;
    private boolean hidden = false; // e.g., underwater, buried

    private final StatusManager statusManager;
    private final MonsterDataManager dataManager; // Stored reference for template access

    public Monster(MonsterType type, float startX, float startY, MonsterColor color,
            MonsterDataManager dataManager, AssetManager assetManager) {
        this.type = type;
        this.position = new Vector2(startX + 0.5f, startY + 0.5f); // Center
        this.monsterColor = color;
        this.dataManager = dataManager;

        MonsterTemplate template = dataManager.getTemplate(type);

        this.ac = template.baseAC; // NetHack-style (lower is better)
        this.magicResistance = template.magicResistance;

        this.warStrength = template.warStrength;
        // Derive Damage Reduction (armor) from AC
        // AC 10 = 0 DR, AC 0 = 5 DR, AC -10 = 10 DR
        this.armor = Math.max(0, (10 - this.ac) / 2);

        this.warStrength = template.warStrength;
        this.maxWarStrength = template.warStrength; // Init Max
        this.spiritualStrength = template.spiritualStrength;

        this.baseExperience = template.baseExperience;
        this.moveSpeed = template.moveSpeed; // Init speed

        this.family = template.family;
        this.spriteData = template.spriteData;
        this.scale = new Vector2(template.scale.x, template.scale.y);
        this.statusManager = new StatusManager();
        this.texture = assetManager.get(template.texturePath, Texture.class);

        this.intelligence = template.intelligence;
        this.dexterity = template.dexterity;
        this.hasRangedAttack = template.hasRangedAttack;
        this.attackRange = template.attackRange;

        // --- AI Initialization ---
        this.aiType = template.aiType != null ? template.aiType : MonsterTemplate.AiType.AGGRESSIVE;
        this.healThreshold = template.healThreshold;
        this.spellChance = template.spellChance;

        this.inventory = new com.bpm.minotaur.gamedata.Inventory();
    }

    // --- AI Getters ---
    public MonsterTemplate.AiType getAiType() {
        return aiType;
    }

    public float getHealThreshold() {
        return healThreshold;
    }

    public int getSpellChance() {
        return spellChance;
    }

    public int getMaxWarStrength() {
        return maxWarStrength;
    }

    public int takeDamage(int amount) {
        int damageReduction = this.armor;
        int finalDamage = Math.max(0, amount - damageReduction);
        this.warStrength -= finalDamage;
        if (this.warStrength < 0) {
            this.warStrength = 0;
        }
        return finalDamage; // NEW: Return the actual value
    }

    public void takeSpiritualDamage(int amount) {
        this.spiritualStrength -= amount;
        if (this.spiritualStrength < 0) {
            this.spiritualStrength = 0;
        }
    }

    public String[] getSpriteData() {
        return spriteData;
    }

    public Texture getTexture() {
        return texture;
    }

    public Vector2 getScale() {
        return scale;
    }

    public void scaleStats(int level) {
        if (level <= 1)
            return;
        float multiplier = 1.0f + ((level - 1) * 0.15f);
        float armorMultiplier = 1.0f + ((level - 1) * 0.05f);
        this.warStrength = (int) (this.warStrength * multiplier);
        this.spiritualStrength = (int) (this.spiritualStrength * multiplier);
        this.armor = (int) (this.armor * armorMultiplier);
        this.baseExperience = (int) (this.baseExperience * (1.0f + ((level - 1) * 0.10f)));
    }

    public MonsterType getType() {
        return type;
    }

    public String getMonsterType() {
        return type.name();
    }

    @Override
    public Vector2 getPosition() {
        return position;
    }

    public int getWarStrength() {
        return warStrength;
    }

    public int getSpiritualStrength() {
        return spiritualStrength;
    }

    public int getBaseExperience() {
        return baseExperience;
    }

    public void setWarStrength(int warStrength) {
        this.warStrength = warStrength;
    }

    public void setSpiritualStrength(int spiritualStrength) {
        this.spiritualStrength = spiritualStrength;
    }

    @Override
    public Color getColor() {
        return monsterColor.getColor();
    }

    public MonsterColor getMonsterColor() {
        return monsterColor;
    }

    public MonsterFamily getFamily() {
        return family;
    }

    public int getIntelligence() {
        return intelligence;
    }

    public StatusManager getStatusManager() {
        return statusManager;
    }

    public int getDexterity() {
        return dexterity;
    }

    public boolean hasRangedAttack() {
        return hasRangedAttack;
    }

    public int getAttackRange() {
        return attackRange;
    }

    public int getArmor() {
        return armor;
    }

    public com.bpm.minotaur.gamedata.Inventory getInventory() {
        return inventory;
    }

    public int getTameness() {
        return tameness;
    }

    public void setTameness(int tameness) {
        this.tameness = tameness;
    }

    public boolean isInvisible() {
        return invisible;
    }

    public void setInvisible(boolean invisible) {
        this.invisible = invisible;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public int getAC() {
        return ac;
    }

    public int getMagicResistance() {
        return magicResistance;
    }

    public MonsterTemplate getTemplate() {
        if (dataManager != null)
            return dataManager.getTemplate(type);
        return null;
    }

    public int getEffectiveSpeed() {
        int speed = moveSpeed;
        if (statusManager.hasEffect(StatusEffectType.SLOWED)) {
            speed /= 2;
        }
        if (statusManager.hasEffect(StatusEffectType.SLOW)) {
            speed /= 2;
        }
        return Math.max(1, speed);
    }

    private float energy = 0;

    public float getEnergy() {
        return energy;
    }

    public void setEnergy(float energy) {
        this.energy = energy;
    }

    public void addEnergy(float amount) {
        this.energy += amount;
    }
}
