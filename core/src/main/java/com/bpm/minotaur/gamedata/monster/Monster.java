package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.Renderable;
import com.bpm.minotaur.managers.StatusManager;

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
    private int spiritualStrength;
    private int armor;
    private int baseExperience;

    private final String[] spriteData;
    private Texture texture = null;
    public Vector2 scale;
    private MonsterFamily family;

    private int intelligence;

    private int dexterity;
    private boolean hasRangedAttack;
    private int attackRange;

    private final StatusManager statusManager;

    public Monster(MonsterType type, int x, int y, MonsterColor monsterColor,
                   MonsterDataManager dataManager, AssetManager assetManager)
    {
        this.type = type;
        this.position = new Vector2(x + 0.5f, y + 0.5f);
        this.monsterColor = monsterColor;

        MonsterTemplate template = dataManager.getTemplate(type);

        this.warStrength = template.warStrength;
        this.spiritualStrength = template.spiritualStrength;
        this.armor = template.armor;
        this.baseExperience = template.baseExperience;
        this.family = template.family;
        this.spriteData = template.spriteData;
        this.scale = new Vector2(template.scale.x, template.scale.y);
        this.statusManager = new StatusManager();
        this.texture = assetManager.get(template.texturePath, Texture.class);

        this.intelligence = template.intelligence;
        this.dexterity = template.dexterity;
        this.hasRangedAttack = template.hasRangedAttack;
        this.attackRange = template.attackRange;
    }

    public void takeDamage(int amount) {
        int damageReduction = this.armor;
        int finalDamage = Math.max(0, amount - damageReduction);
        this.warStrength -= finalDamage;
        if (this.warStrength < 0) {
            this.warStrength = 0;
        }
    }

    public void takeSpiritualDamage(int amount) {
        this.spiritualStrength -= amount;
        if (this.spiritualStrength < 0) {
            this.spiritualStrength = 0;
        }
    }

    public String[] getSpriteData() { return spriteData; }
    public Texture getTexture() { return texture; }
    public Vector2 getScale() { return scale; }

    public void scaleStats(int level) {
        if (level <= 1) return;
        float multiplier = 1.0f + ((level - 1) * 0.15f);
        float armorMultiplier = 1.0f + ((level - 1) * 0.05f);
        this.warStrength = (int) (this.warStrength * multiplier);
        this.spiritualStrength = (int) (this.spiritualStrength * multiplier);
        this.armor = (int) (this.armor * armorMultiplier);
        this.baseExperience = (int) (this.baseExperience * (1.0f + ((level - 1) * 0.10f)));
    }

    public MonsterType getType() { return type; }
    public String getMonsterType() { return type.name(); }
    @Override public Vector2 getPosition() { return position; }
    public int getWarStrength() { return warStrength; }
    public int getSpiritualStrength() { return spiritualStrength; }
    public int getBaseExperience() { return baseExperience; }
    public void setWarStrength(int warStrength) { this.warStrength = warStrength; }
    public void setSpiritualStrength(int spiritualStrength) { this.spiritualStrength = spiritualStrength; }
    @Override public Color getColor() { return monsterColor.getColor(); }
    public MonsterColor getMonsterColor() { return monsterColor; }
    public MonsterFamily getFamily() { return family; }
    public int getIntelligence() { return intelligence; }
    public StatusManager getStatusManager() { return statusManager; }
    public int getDexterity() { return dexterity; }
    public boolean hasRangedAttack() { return hasRangedAttack; }
    public int getAttackRange() { return attackRange; }
}
