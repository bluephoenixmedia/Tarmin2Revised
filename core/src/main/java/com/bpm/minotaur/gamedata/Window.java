package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.Vector2;

public class Window {
    private final Vector2 position;

    public Window(int x, int y) {
        this.position = new Vector2(x, y);
    }

    public Vector2 getPosition() {
        return position;
    }
}
