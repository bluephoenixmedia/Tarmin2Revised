package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

public class Gate implements Renderable {
    private final Vector2 position;
    private final Color color = Color.CYAN; // A distinct color for gates

    public Gate(int x, int y) {
        this.position = new Vector2(x + 0.5f, y + 0.5f);
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
