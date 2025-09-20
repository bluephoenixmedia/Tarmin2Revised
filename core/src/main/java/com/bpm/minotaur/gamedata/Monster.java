package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

public class Monster implements Renderable {

    public enum MonsterType {
        SKELETON,
        SPIDER,
        ORC
    }

    private final MonsterType type;
    private final Vector2 position;
    private final Color color;
    private int warStrength;
    private int spiritualStrength;
    private int armor;

    public Monster(MonsterType type, int x, int y) {
        this.type = type;
        this.position = new Vector2(x + 0.5f, y + 0.5f); // Center of the tile

        switch (type) {
            case SKELETON:
                this.color = Color.WHITE;
                this.warStrength = 15;
                this.spiritualStrength = 0;
                this.armor = 3;
                break;
            case SPIDER:
                this.color = Color.BLACK;
                this.warStrength = 10;
                this.spiritualStrength = 5;
                this.armor = 1;
                break;
            case ORC:
                this.color = Color.GREEN;
                this.warStrength = 25;
                this.spiritualStrength = 5;
                this.armor = 5;
                break;
            default:
                this.color = Color.RED;
                this.warStrength = 10;
                this.spiritualStrength = 10;
                this.armor = 2;
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

    public MonsterType getType() { return type; }
    @Override public Vector2 getPosition() { return position; }
    @Override public Color getColor() { return color; }
    public int getWarStrength() { return warStrength; }
    public int getSpiritualStrength() { return spiritualStrength; }
}
