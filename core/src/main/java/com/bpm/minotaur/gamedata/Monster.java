package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;

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
    private final Color color;
    private int warStrength;
    private int spiritualStrength;
    private int armor;
    private final String[] spriteData; // Add this line
    private Texture texture = null; // Add this line to store the monster's texture
    public Vector2 scale; // Add this Vector2 scale field



    public Monster(MonsterType type, int x, int y) {
        this.type = type;
        this.position = new Vector2(x + 0.5f, y + 0.5f); // Center of the tile
        this.scale = new Vector2(1.0f, 1.0f); // Default scale

        switch (type) {
            // --- BAD MONSTERS (Spiritual Attacks) ---
            case GIANT_ANT:
                this.color = Color.PURPLE;
                this.warStrength = 5;
                this.spiritualStrength = 15;
                this.armor = 2;
                this.spriteData = MonsterSpriteData.GIANT_ANT; // Correctly assigned
                this.texture = new Texture(Gdx.files.internal("images/monsters/giant_ant.png")); // Example path
                this.scale.set(1.2f, 0.8f); // Make it wider than it is tall

                break;
            case DWARF:
                this.color = Color.TAN;
                this.warStrength = 10;
                this.spiritualStrength = 10;
                this.armor = 4;
                this.spriteData = MonsterSpriteData.DWARF; // Add this line
                this.texture = new Texture(Gdx.files.internal("images/monsters/dwarf.png"));
                this.scale.set(0.8f, 0.9f); // Short and stout

                break;
            case GIANT_SCORPION:
                this.color = Color.BLUE;
                this.warStrength = 8;
                this.spiritualStrength = 20;
                this.armor = 3;
                this.spriteData = MonsterSpriteData.GIANT_SCORPION; // Add this line
                this.texture = new Texture(Gdx.files.internal("images/monsters/giant_scorpion.png"));
                this.scale.set(1.1f, 0.9f);

                break;
            case GIANT_SNAKE:
                this.color = Color.ORANGE;
                this.warStrength = 12;
                this.spiritualStrength = 15;
                this.armor = 2;
                this.spriteData = MonsterSpriteData.GIANT_SNAKE; // Add this line
                this.texture = new Texture(Gdx.files.internal("images/monsters/giant_snake.png"));
                this.scale.set(1.5f, 0.6f); // Very wide and short

                break;

            // --- NASTY MONSTERS (War Attacks) ---
            case GHOUL:
                this.color = Color.PINK;
                this.warStrength = 20;
                this.spiritualStrength = 5;
                this.armor = 3;
                this.spriteData = MonsterSpriteData.GHOUL; // Add this line
                this.texture = new Texture(Gdx.files.internal("images/monsters/ghoul.png"));
                this.scale.set(0.8f, 1.1f); // Tall and thin

                break;
            case SKELETON:
                this.color = Color.WHITE;
                this.warStrength = 15;
                this.spiritualStrength = 0;
                this.armor = 3;
                this.spriteData = MonsterSpriteData.SKELETON; // Add this line
                this.texture = new Texture(Gdx.files.internal("images/monsters/skeleton.png"));
                this.scale.set(0.8f, 1.1f); // Tall and thin

                break;
            case CLOAKED_SKELETON:
                this.color = Color.GRAY;
                this.warStrength = 18;
                this.spiritualStrength = 5;
                this.armor = 4;
                this.spriteData = MonsterSpriteData.CLOAKED_SKELETON; // Add this line
                this.texture = new Texture(Gdx.files.internal("images/monsters/hooded_skeleton.png"));
                this.scale.set(0.8f, 1.1f); // Tall and thin

                break;

            // --- HORRIBLE MONSTERS (War & Spiritual Attacks) ---
            case ALLIGATOR:
                this.color = Color.PURPLE;
                this.warStrength = 30;
                this.spiritualStrength = 15;
                this.armor = 6;
                this.spriteData = MonsterSpriteData.ALLIGATOR; // Add this line
                this.texture = new Texture(Gdx.files.internal("images/monsters/alligator.png"));
                this.scale.set(1.0f, 1.0f); // Tall and thin

                break;
            case DRAGON:
                this.color = Color.ORANGE;
                this.warStrength = 40;
                this.spiritualStrength = 30;
                this.armor = 8;
                this.spriteData = MonsterSpriteData.DRAGON; // Add this line
                this.texture = new Texture(Gdx.files.internal("images/monsters/dragon.png"));
                this.scale.set(1.0f, 1.0f); // Tall and thin

                break;
            case WRAITH:
                this.color = Color.WHITE;
                this.warStrength = 25;
                this.spiritualStrength = 35;
                this.armor = 5;
                this.spriteData = MonsterSpriteData.WRAITH; // Add this line
                this.texture = new Texture(Gdx.files.internal("images/monsters/wraith.png"));
                this.scale.set(1.0f, 1.0f); // Tall and thin

                break;
            case GIANT:
                this.color = Color.YELLOW;
                this.warStrength = 35;
                this.spiritualStrength = 10;
                this.armor = 7;
                this.spriteData = MonsterSpriteData.GIANT; // Add this line
                this.texture = new Texture(Gdx.files.internal("images/monsters/giant.png"));
                this.scale.set(1.0f, 1.0f); // Tall and thin

                break;
            case MINOTAUR:
                this.color = Color.PURPLE;
                this.warStrength = 50;
                this.spiritualStrength = 25;
                this.armor = 10;
                this.spriteData = MonsterSpriteData.MINOTAUR; // Add this line
                this.texture = new Texture(Gdx.files.internal("images/monsters/minotaur.png"));
                this.scale.set(1.0f, 1.0f); // Tall and thin

                break;

            default:
                this.color = Color.RED;
                this.warStrength = 10;
                this.spiritualStrength = 10;
                this.armor = 2;
                this.spriteData = null; // Add this line
                this.scale.set(1.0f, 1.0f); // Tall and thin

                break;
        }
    }

    public void takeDamage(int amount) {
        int damageReduction = this.armor;
        int finalDamage = Math.max(0, amount - damageReduction);
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
    @Override public Color getColor() { return color; }
    public int getWarStrength() { return warStrength; }
    public int getSpiritualStrength() { return spiritualStrength; }
}
