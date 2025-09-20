package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

public class Ladder implements Renderable {

    private final Vector2 position;
    private final Color color = new Color(0.6f, 0.4f, 0.2f, 1); // Brown color

    public Ladder(int x, int y) {
        this.position = new Vector2(x + 0.5f, y + 0.5f); // Center of the tile
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
