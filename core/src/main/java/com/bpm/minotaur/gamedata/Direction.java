package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.Vector2;

public enum Direction {
    NORTH(0, 1),
    EAST(1, 0),
    SOUTH(0, -1),
    WEST(-1, 0);

    private final Vector2 vector;

    Direction(int x, int y) {
        this.vector = new Vector2(x, y);
    }

    public Vector2 getVector() {
        return vector.cpy(); // Return a copy to prevent modification of the original
    }

    public Direction turnLeft() {
        switch (this) {
            case NORTH: return WEST;
            case WEST:  return SOUTH;
            case SOUTH: return EAST;
            case EAST:  return NORTH;
            default:    return this; // Should not happen
        }
    }

    public Direction turnRight() {
        switch (this) {
            case NORTH: return EAST;
            case EAST:  return SOUTH;
            case SOUTH: return WEST;
            case WEST:  return NORTH;
            default:    return this; // Should not happen
        }
    }
}

