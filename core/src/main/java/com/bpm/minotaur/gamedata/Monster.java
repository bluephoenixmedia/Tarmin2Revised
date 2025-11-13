package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.assets.AssetManager;

public class Monster implements Renderable {



    public enum MonsterType {
        // Bad Monsters
        GIANT_ANT,
        DWARF,
        GIANT_SCORPION,
        GIANT_SNAKE,

        // Nasty Monsters
        GHOUL,
        SKELETON,
        CLOAKED_SKELETON,

        // Horrible Monsters
        ALLIGATOR,
        DRAGON,
        WRAITH,
        GIANT,
        MINOTAUR,
        SPIDER, // Existing monster
        ORC     // Existing monster
    }

    private final MonsterType type;
    private final Vector2 position;
    private final MonsterColor monsterColor; // Changed from Color to MonsterColor
    private int warStrength;
    private int spiritualStrength;
    private int armor;
    private int baseExperience; // New field for base XP

    private final String[] spriteData; // Add this line
    private Texture texture = null; // Add this line to store the monster's texture
    public Vector2 scale; // Add this Vector2 scale field
    private MonsterFamily family;


    /**
     * The NEW, simplified constructor.
     * It requires the data/asset managers to build the monster.
     */
    public Monster(MonsterType type, int x, int y, MonsterColor monsterColor,
                   MonsterDataManager dataManager, AssetManager assetManager)
    {
        this.type = type;
        this.position = new Vector2(x + 0.5f, y + 0.5f);
        this.monsterColor = monsterColor;

        // --- Get the template for this monster type ---
        MonsterTemplate template = dataManager.getTemplate(type);

        // --- Copy all data from the template ---
        this.warStrength = template.warStrength;
        this.spiritualStrength = template.spiritualStrength;
        this.armor = template.armor;
        this.baseExperience = template.baseExperience;
        this.family = template.family;
        this.spriteData = template.spriteData; // Copied directly from template
        this.scale = new Vector2(template.scale.x, template.scale.y);

        // --- Get the PRE-LOADED texture from the AssetManager ---
        // This avoids "new Texture(...)" and saves memory!
        this.texture = assetManager.get(template.texturePath, Texture.class);
    }

    public void takeDamage(int amount) {
        int damageReduction = this.armor;
        Gdx.app.log("MONSTER", "damagereduction =  " + damageReduction);

        int finalDamage = Math.max(0, amount - damageReduction);
        Gdx.app.log("MONSTER", "finalDamage =  " + finalDamage);


        this.warStrength -= finalDamage;

        if (this.warStrength < 0) {
            this.warStrength = 0;
        }
        Gdx.app.log("Monster", this.getType() + " takes " + finalDamage + " damage. WS is now " + this.warStrength);
    }

    public void takeSpiritualDamage(int amount) {
        this.spiritualStrength -= amount;

        if (this.spiritualStrength < 0) {
            this.spiritualStrength = 0;
        }
        Gdx.app.log("Monster", this.getType() + " takes " + amount + " spiritual damage. SS is now " + this.spiritualStrength);
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
        int levelBonus = level / 2;
        this.warStrength += levelBonus;
        this.spiritualStrength += levelBonus;
        this.armor += levelBonus / 2;
    }
    public MonsterType getType() { return type; }
    public String getMonsterType() { return type.name(); }
    @Override public Vector2 getPosition() { return position; }
   // @Override public Color getColor() { return color; }
    public int getWarStrength() { return warStrength; }
    public int getSpiritualStrength() { return spiritualStrength; }
    public int getBaseExperience() { return baseExperience;
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


}

