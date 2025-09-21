package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
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


    public Monster(MonsterType type, int x, int y) {
        this.type = type;
        this.position = new Vector2(x + 0.5f, y + 0.5f); // Center of the tile

        switch (type) {
            // --- BAD MONSTERS (Spiritual Attacks) ---
            case GIANT_ANT:
                this.color = Color.PURPLE;
                this.warStrength = 5;
                this.spiritualStrength = 15;
                this.armor = 2;
                this.spriteData = MonsterSpriteData.GIANT_ANT; // Correctly assigned
                break;
            case DWARF:
                this.color = Color.TAN;
                this.warStrength = 10;
                this.spiritualStrength = 10;
                this.armor = 4;
                this.spriteData = null; // Add this line
                break;
            case GIANT_SCORPION:
                this.color = Color.BLUE;
                this.warStrength = 8;
                this.spiritualStrength = 20;
                this.armor = 3;
                this.spriteData = null; // Add this line
                break;
            case GIANT_SNAKE:
                this.color = Color.ORANGE;
                this.warStrength = 12;
                this.spiritualStrength = 15;
                this.armor = 2;
                this.spriteData = null; // Add this line
                break;

            // --- NASTY MONSTERS (War Attacks) ---
            case GHOUL:
                this.color = Color.PINK;
                this.warStrength = 20;
                this.spiritualStrength = 5;
                this.armor = 3;
                this.spriteData = null; // Add this line
                break;
            case SKELETON:
                this.color = Color.WHITE;
                this.warStrength = 15;
                this.spiritualStrength = 0;
                this.armor = 3;
                this.spriteData = null; // Add this line
                break;
            case CLOAKED_SKELETON:
                this.color = Color.GRAY;
                this.warStrength = 18;
                this.spiritualStrength = 5;
                this.armor = 4;
                this.spriteData = null; // Add this line
                break;

            // --- HORRIBLE MONSTERS (War & Spiritual Attacks) ---
            case ALLIGATOR:
                this.color = Color.PURPLE;
                this.warStrength = 30;
                this.spiritualStrength = 15;
                this.armor = 6;
                this.spriteData = null; // Add this line
                break;
            case DRAGON:
                this.color = Color.ORANGE;
                this.warStrength = 40;
                this.spiritualStrength = 30;
                this.armor = 8;
                this.spriteData = null; // Add this line
                break;
            case WRAITH:
                this.color = Color.WHITE;
                this.warStrength = 25;
                this.spiritualStrength = 35;
                this.armor = 5;
                this.spriteData = null; // Add this line
                break;
            case GIANT:
                this.color = Color.YELLOW;
                this.warStrength = 35;
                this.spiritualStrength = 10;
                this.armor = 7;
                this.spriteData = null; // Add this line
                break;
            case MINOTAUR:
                this.color = Color.PURPLE;
                this.warStrength = 50;
                this.spiritualStrength = 25;
                this.armor = 10;
                this.spriteData = null; // Add this line
                break;

            // --- EXISTING MONSTERS ---
            case SPIDER:
                this.color = Color.BLACK;
                this.warStrength = 10;
                this.spiritualStrength = 5;
                this.armor = 1;
                this.spriteData = null; // Add this line
                break;
            case ORC:
                this.color = Color.GREEN;
                this.warStrength = 25;
                this.spiritualStrength = 5;
                this.armor = 5;
                this.spriteData = null; // Add this line
                break;

            default:
                this.color = Color.RED;
                this.warStrength = 10;
                this.spiritualStrength = 10;
                this.armor = 2;
                this.spriteData = null; // Add this line
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
    public MonsterType getType() { return type; }
    @Override public Vector2 getPosition() { return position; }
    @Override public Color getColor() { return color; }
    public int getWarStrength() { return warStrength; }
    public int getSpiritualStrength() { return spiritualStrength; }
}
