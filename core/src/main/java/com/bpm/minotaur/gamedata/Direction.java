package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.Vector2;

public enum Direction {
    NORTH(0, 1, 0b11000000),
    EAST(1, 0, 0b00110000),
    SOUTH(0, -1, 0b00001100),
    WEST(-1, 0, 0b00000011);

    private final Vector2 vector;
    private final int wallMask;

    Direction(float x, float y, int wallMask) {
        this.vector = new Vector2(x, y);
        this.wallMask = wallMask;
    }

    public Vector2 getVector() {
        return vector.cpy();
    }

    public int getWallMask() {
        return wallMask;
    }

    public Direction turnLeft() {
        switch (this) {
            case NORTH: return WEST;
            case WEST: return SOUTH;
            case SOUTH: return EAST;
            case EAST: return NORTH;
        }
        return this; // Should not happen
    }

    public Direction turnRight() {
        switch (this) {
            case NORTH: return EAST;
            case EAST: return SOUTH;
            case SOUTH: return WEST;
            case WEST: return NORTH;
        }
        return this; // Should not happen
    }

    public Direction opposite() {
        switch (this) {
            case NORTH: return SOUTH;
            case SOUTH: return NORTH;
            case EAST: return WEST;
            case WEST: return EAST;
        }
        return this; // Should not happen
    }
}

