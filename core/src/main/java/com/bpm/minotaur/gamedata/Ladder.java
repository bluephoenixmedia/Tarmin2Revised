package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

public class Ladder implements Renderable {

    public enum LadderType {
        UP,
        DOWN
    }

    private final Vector2 position;
    private final Color color = new Color(0.6f, 0.4f, 0.2f, 1); // Brown color
    private final LadderType type;

    // Default constructor for backward compatibility (defaults to DOWN)
    public Ladder(int x, int y) {
        this(x, y, LadderType.DOWN);
    }

    public Ladder(int x, int y, LadderType type) {
        this.position = new Vector2(x + 0.5f, y + 0.5f); // Center of the tile
        this.type = type;
    }

    public LadderType getType() {
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
