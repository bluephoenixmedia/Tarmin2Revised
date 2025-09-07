package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;

public class Player {

    private final Vector2 position;
    private Direction facing;
    private final Vector2 directionVector;
    private final Vector2 cameraPlane;

    public Player(float startX, float startY) {
        // Player's starting position is set to the center of the start tile.
        this.position = new Vector2(startX + 0.5f, startY + 0.5f);
        this.facing = Direction.NORTH;
        this.directionVector = new Vector2();
        this.cameraPlane = new Vector2();
        updateVectors();
    }

    /**
     * Updates the direction and camera plane vectors based on the current facing direction.
     * This is called whenever the player turns.
     */
    private void updateVectors() {
        directionVector.set(facing.getVector());
        // The camera plane is perpendicular to the direction vector, creating the field of view.
        cameraPlane.set(-directionVector.y, directionVector.x).scl(0.66f);
    }

    /**
     * Moves the player one tile forward if the path is not blocked.
     * @param maze The maze to check for collisions.
     */
    public void moveForward(Maze maze) {
        move(facing, maze);
    }

    /**
     * Moves the player one tile backward if the path is not blocked.
     * @param maze The maze to check for collisions.
     */
    public void moveBackward(Maze maze) {
        move(facing.getOpposite(), maze);
    }

    /**
     * Handles the core movement and collision detection logic. It performs a move and then
     * checks if the player has landed in a doorway, triggering a second move to pass through.
     * @param direction The direction in which to attempt movement.
     * @param maze The maze to check for collisions.
     */
    private void move(Direction direction, Maze maze) {
        Gdx.app.log("PlayerMovement", "Attempting to move " + direction + " from (" + (int)position.x + "," + (int)position.y + ")");

        int currentX = (int) position.x;
        int currentY = (int) position.y;

        // Check if the immediate path is blocked by a wall or a closed door.
        if (!maze.isWallBlocking(currentX, currentY, direction)) {
            // The path is clear. Move one step into the next tile.
            int nextX = currentX + (int)direction.getVector().x;
            int nextY = currentY + (int)direction.getVector().y;
            position.set(nextX + 0.5f, nextY + 0.5f);
            Gdx.app.log("PlayerMovement", "Moved to (" + (int)position.x + "," + (int)position.y + ")");

            // --- Post-Move Door Pass-Through Check ---
            // Get the object on the tile we just landed on.
            Object objectOnCurrentTile = maze.getGameObjectAt((int)position.x, (int)position.y);
            if (objectOnCurrentTile != null) {
                Gdx.app.log("PlayerMovement", "object on current tile = " + objectOnCurrentTile.toString());
            }
             // If we landed on an open door, we must immediately move forward again.
            if (objectOnCurrentTile instanceof Door) {// && ((Door) objectOnCurrentTile).getState() == Door.DoorState.OPEN) {
                Gdx.app.log("PlayerMovement", "Landed on an open door. Passing through.");

                // Re-calculate current position after the first move.
                int doorX = (int) position.x;
                int doorY = (int) position.y;

                // Check if the path OUT of the door is blocked.
                if (!maze.isWallBlocking(doorX, doorY, direction)) {
                    int finalX = doorX + (int)direction.getVector().x;
                    int finalY = doorY + (int)direction.getVector().y;
                    position.set(finalX + 0.5f, finalY + 0.5f);
                    Gdx.app.log("PlayerMovement", "Auto-moved to (" + (int)position.x + "," + (int)position.y + ")");
                }
            }
        } else {
            Gdx.app.log("PlayerMovement", "Collision detected. Movement blocked.");
        }
    }

    /**
     * Rotates the player's facing direction 90 degrees to the left.
     */
    public void turnLeft() {
        facing = facing.getLeft();
        updateVectors();
        Gdx.app.log("PlayerMovement", "Player turned left, now facing " + facing);
    }

    /**
     * Rotates the player's facing direction 90 degrees to the right.
     */
    public void turnRight() {
        facing = facing.getRight();
        updateVectors();
        Gdx.app.log("PlayerMovement", "Player turned right, now facing " + facing);
    }

    /**
     * Interacts with the tile directly in front of the player (e.g., to open a door).
     * @param maze The maze containing the object to interact with.
     */
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

