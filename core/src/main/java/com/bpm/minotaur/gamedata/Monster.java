package com.bpm.minotaur.gamedata;

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

    public Monster(MonsterType type, int x, int y) {
        this.type = type;
        this.position = new Vector2(x + 0.5f, y + 0.5f); // Center of the tile

        switch (type) {
            case SKELETON:
                this.color = Color.WHITE;
                break;
            case SPIDER:
                this.color = Color.BLACK;
                break;
            case ORC:
                this.color = Color.GREEN;
                break;
            default:
                this.color = Color.RED;
                break;
        }
    }

    public MonsterType getType() {
        return type;
    }

    @Override
    public Vector2 getPosition() {
        return position;
    }

    @Override
    public Color getColor() {
        return color;
    }
}
