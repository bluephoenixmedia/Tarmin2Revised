package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;

public class Player {
    // Using float for position to support smooth rendering, but movement is grid-based.
    private float worldX;
    private float worldY;
    private int gridX;
    private int gridY;

    private Direction facing;
    private final Vector2 directionVector = new Vector2();
    private final Vector2 cameraPlane = new Vector2();

    public Player(int startGridX, int startGridY) {
        this.gridX = startGridX;
        this.gridY = startGridY;
        this.worldX = startGridX + 0.5f; // Center of the tile
        this.worldY = startGridY + 0.5f; // Center of the tile
        this.facing = Direction.SOUTH; // Default starting direction
        updateVectors();
    }

    public Vector2 getPosition() {
        return new Vector2(worldX, worldY);
    }

    public int getPositionAsGridX() {
        return gridX;
    }

    public int getPositionAsGridY() {
        return gridY;
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

    private void updateVectors() {
        directionVector.set(facing.getVector());
        // Camera plane is perpendicular to the direction vector
        cameraPlane.set(-directionVector.y, directionVector.x).scl(0.66f); // 0.66 for a 66-degree FOV
    }

    public void turnLeft() {
        facing = facing.turnLeft();
        updateVectors();
        Gdx.app.log("PlayerMovement", "Player turned left, now facing " + facing);
    }

    public void turnRight() {
        facing = facing.turnRight();
        updateVectors();
        Gdx.app.log("PlayerMovement", "Player turned right, now facing " + facing);
    }

    public void moveForward(Maze maze) {
        Gdx.app.log("PlayerMovement", "Attempting to move forward from (" + gridX + "," + gridY + ") facing " + facing);
        int targetX = gridX + (int) facing.getVector().x;
        int targetY = gridY + (int) facing.getVector().y;

        // Two-way collision check
        boolean currentTileClear = !maze.isWallBlocking(gridX, gridY, facing);
        boolean targetTileClear = !maze.isWallBlocking(targetX, targetY, facing.opposite());

        if (currentTileClear && targetTileClear) {
            gridX = targetX;
            gridY = targetY;
            worldX = gridX + 0.5f;
            worldY = gridY + 0.5f;
        } else {
            Gdx.app.log("PlayerMovement", "Collision detected. Movement blocked.");
        }
    }

    public void moveBackward(Maze maze) {
        Gdx.app.log("PlayerMovement", "Attempting to move backward from (" + gridX + "," + gridY + ") facing " + facing);
        Direction opposite = facing.opposite();
        int targetX = gridX + (int) opposite.getVector().x;
        int targetY = gridY + (int) opposite.getVector().y;

        // Two-way collision check
        boolean currentTileClear = !maze.isWallBlocking(gridX, gridY, opposite);
        boolean targetTileClear = !maze.isWallBlocking(targetX, targetY, opposite.opposite());

        if (currentTileClear && targetTileClear) {
            gridX = targetX;
            gridY = targetY;
            worldX = gridX + 0.5f;
            worldY = gridY + 0.5f;
        } else {
            Gdx.app.log("PlayerMovement", "Collision detected. Movement blocked.");
        }
    }

    public void interact(Maze maze) {
        // Determine the tile in front of the player
        int targetX = this.gridX + (int)this.facing.getVector().x;
        int targetY = this.gridY + (int)this.facing.getVector().y;

        Gdx.app.log("PlayerInteraction", "Player attempting to interact with tile at (" + targetX + ", " + targetY + ")");
        maze.openDoorAt(targetX, targetY);
    }
}

