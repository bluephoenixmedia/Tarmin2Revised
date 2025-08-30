package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.GridPoint2;

public class Player {
    private GridPoint2 position;
    private Direction facing;

    // TODO: Add player stats (warStrength, etc.) from design doc later.

    public Player() {
        this.position = new GridPoint2(0, 0);
        this.facing = Direction.SOUTH;
    }

    public GridPoint2 getPosition() {
        return position;
    }

    public Direction getFacing() {
        return facing;
    }

    public void turnLeft() {
        facing = facing.turnLeft();
    }

    public void turnRight() {
        facing = facing.turnRight();
    }

    public void moveForward(Maze maze) {
        int currentX = position.x;
        int currentY = position.y;

        // Check for a wall in the direction the player is facing.
        if (!maze.isWall(currentX, currentY, facing)) {
            position.x += facing.getVector().x;
            position.y += facing.getVector().y;
        }
    }

    public void moveBackward(Maze maze) {
        int currentX = position.x;
        int currentY = position.y;

        // Check for a wall in the opposite direction.
        Direction opposite = facing.turnLeft().turnLeft();
        if (!maze.isWall(currentX, currentY, opposite)) {
            position.x += opposite.getVector().x;
            position.y += opposite.getVector().y;
        }
    }
}

