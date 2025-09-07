package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.Vector2;

public enum Direction {
    NORTH(0, 1, 0b01000000),
    EAST(1, 0, 0b00000100),
    SOUTH(0, -1, 0b00010000),
    WEST(-1, 0, 0b00000001);

    private final Vector2 vector;
    private final int wallMask;

    Direction(int x, int y, int wallMask) {
        this.vector = new Vector2(x, y);
        this.wallMask = wallMask;
    }

    public Vector2 getVector() {
        return vector.cpy();
    }

    public int getWallMask() {
        return wallMask;
    }

    public Direction getOpposite() {
        switch (this) {
            case NORTH: return SOUTH;
            case EAST:  return WEST;
            case SOUTH: return NORTH;
            case WEST:  return EAST;
            default: throw new IllegalStateException("Unexpected direction: " + this);
        }
    }

    public Direction getLeft() {
        switch (this) {
            case NORTH: return WEST;
            case EAST:  return NORTH;
            case SOUTH: return EAST;
            case WEST:  return SOUTH;
            default: throw new IllegalStateException("Unexpected direction: " + this);
        }
    }

    public Direction getRight() {
        switch (this) {
            case NORTH: return EAST;
            case EAST:  return SOUTH;
            case SOUTH: return WEST;
            case WEST:  return NORTH;
            default: throw new IllegalStateException("Unexpected direction: " + this);
        }
    }
}
