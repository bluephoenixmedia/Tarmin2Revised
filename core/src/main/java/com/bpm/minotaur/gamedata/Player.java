package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;

public class Player {

    private final Vector2 position;
    private Direction facing;
    private final Vector2 directionVector;
    private final Vector2 cameraPlane;

    public Player(float startX, float startY) {
        this.position = new Vector2(startX + 0.5f, startY + 0.5f);
        this.facing = Direction.NORTH;
        this.directionVector = new Vector2();
        this.cameraPlane = new Vector2();
        updateVectors();
    }

    private void updateVectors() {
        directionVector.set(facing.getVector());
        cameraPlane.set(-directionVector.y, directionVector.x).scl(0.66f);
    }

    public void moveForward(Maze maze) {
        move(facing, maze);
    }

    public void moveBackward(Maze maze) {
        move(facing.getOpposite(), maze);
    }

    private void move(Direction direction, Maze maze) {
        Gdx.app.log("PlayerMovement", "Attempting to move " + direction + " from (" + (int)position.x + "," + (int)position.y + ")");

        int currentX = (int) position.x;
        int currentY = (int) position.y;

        // Check for a wall on the current tile in the direction of movement.
        if (!maze.isWallBlocking(currentX, currentY, direction)) {
            // Calculate the new integer grid position
            int nextX = currentX + (int)direction.getVector().x;
            int nextY = currentY + (int)direction.getVector().y;
            // Move player to the center of the next tile
            position.set(nextX + 0.5f, nextY + 0.5f);
        } else {
            Gdx.app.log("PlayerMovement", "Collision detected. Movement blocked.");
        }
    }

    public void turnLeft() {
        facing = facing.getLeft();
        updateVectors();
        Gdx.app.log("PlayerMovement", "Player turned left, now facing " + facing);
    }

    public void turnRight() {
        facing = facing.getRight();
        updateVectors();
        Gdx.app.log("PlayerMovement", "Player turned right, now facing " + facing);
    }

    public void interact(Maze maze) {
        int targetX = (int) (position.x + facing.getVector().x);
        int targetY = (int) (position.y + facing.getVector().y);

        Gdx.app.log("Interaction", "Player interacting with tile (" + targetX + ", " + targetY + ")");
        maze.openDoorAt(targetX, targetY);
    }

    // --- Getters ---
    public Vector2 getPosition() {
        return position;
    }

    public Direction getFacing() {
        return facing;
    }

    public Vector2 getDirectionVector() {
        return directionVector;
    }

    public Vector2 getCameraPlane() {
        return cameraPlane;
    }
}

