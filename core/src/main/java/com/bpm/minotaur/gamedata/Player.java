package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.GridPoint2;

/**
 * Represents the player character and their state within the game.
 */
public class Player {
    private int warStrength;
    private int spiritualStrength;
    private int food;
    private int arrows;

    // Using GridPoint2 for integer-based coordinates.
    private GridPoint2 position;
    private Direction facing;

    public Player() {
        // Initialize with default starting values.
        this.warStrength = 10;
        this.spiritualStrength = 10;
        this.food = 100;
        this.arrows = 20;
        this.position = new GridPoint2(0, 0); // Start at the top-left corner
        this.facing = Direction.SOUTH; // Start facing down into the maze
    }

    // Getters for player attributes
    public int getWarStrength() { return warStrength; }
    public int getSpiritualStrength() { return spiritualStrength; }
    public int getFood() { return food; }
    public int getArrows() { return arrows; }
    public GridPoint2 getPosition() { return position; }
    public Direction getFacing() { return facing; }

    // Placeholder methods for player actions to be implemented later.
    public void move(Direction direction) {
        // Movement logic will be added in a future task.
    }

    public void turnLeft() {
        // Turning logic will be added later.
    }

    public void turnRight() {
        // Turning logic will be added later.
    }
}
