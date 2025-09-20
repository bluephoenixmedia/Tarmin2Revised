package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;

public class Player {

    private int warStrength;
    private int spiritualStrength;
    private int food;
    private int arrows;

    private final int maxWarStrength;
    private final int maxSpiritualStrength;

    private final Vector2 position;
    private Direction facing;
    private final Vector2 directionVector;
    private final Vector2 cameraPlane;

    private final Inventory inventory = new Inventory();

    public Player(float startX, float startY) {
        this.position = new Vector2(startX + 0.5f, startY + 0.5f);
        this.facing = Direction.NORTH;
        this.directionVector = new Vector2();
        this.cameraPlane = new Vector2();
        updateVectors();

        // --- STATS BOOSTED FOR TESTING ---
        this.warStrength = 80;
        this.spiritualStrength = 40;
        this.food = 99;
        this.arrows = 99;

        // Set max values higher for testing
        this.maxWarStrength = 150;
        this.maxSpiritualStrength = 100;
    }

    private void updateVectors() {
        directionVector.set(facing.getVector());
        cameraPlane.set(-directionVector.y, directionVector.x).scl(0.66f);
    }

    public void rest() {
        if (food > 0) {
            food--;

            // Restore WS and SS, ensuring they don't exceed the max values
            int warStrengthGained = 5;
            int spiritualStrengthGained = 2;

            this.warStrength = Math.min(this.maxWarStrength, this.warStrength + warStrengthGained);
            this.spiritualStrength = Math.min(this.maxSpiritualStrength, this.spiritualStrength + spiritualStrengthGained);

            Gdx.app.log("Player", "Player rests. Food remaining: " + food);
            Gdx.app.log("Player", "WS restored to " + warStrength + ", SS restored to " + spiritualStrength);
        } else {
            Gdx.app.log("Player", "Cannot rest. No food remaining.");
        }
    }


    public void setFacing(Direction facing) {
        this.facing = facing;
        updateVectors();
    }

    public void takeDamage(int amount) {
        int damageReduction = getArmor();
        int finalDamage = Math.max(0, amount - damageReduction);
        this.warStrength -= finalDamage;

        if (this.warStrength < 0) {
            this.warStrength = 0;
        }
        Gdx.app.log("Player", "Player takes " + finalDamage + " damage. WS is now " + this.warStrength);
    }

    public int getArmor() {
        // For now, a placeholder value. This will be calculated based on equipped armor.
        return 2;
    }


    public void checkForItemPickup(Maze maze) {
        GridPoint2 playerTile = new GridPoint2((int)position.x, (int)position.y);
        if (maze.getItems().containsKey(playerTile)) {
            Item item = maze.getItems().get(playerTile);
            if (inventory.pickup(item)) {
                maze.getItems().remove(playerTile);
                Gdx.app.log("Inventory", "Player picked up " + item.getType());
            } else {
                Gdx.app.log("Inventory", "Inventory is full. Cannot pick up " + item.getType());
            }
        }
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

        if (!maze.isWallBlocking(currentX, currentY, direction)) {
            int nextX = currentX + (int)direction.getVector().x;
            int nextY = currentY + (int)direction.getVector().y;
            position.set(nextX + 0.5f, nextY + 0.5f);
            Gdx.app.log("PlayerMovement", "Moved to (" + (int)position.x + "," + (int)position.y + ")");

            checkForItemPickup(maze); // Check for items after moving

            Object objectOnCurrentTile = maze.getGameObjectAt((int)position.x, (int)position.y);
            if (objectOnCurrentTile instanceof Door) {
                Gdx.app.log("PlayerMovement", "Landed on an open door. Passing through.");
                int doorX = (int) position.x;
                int doorY = (int) position.y;
                if (!maze.isWallBlocking(doorX, doorY, direction)) {
                    int finalX = doorX + (int)direction.getVector().x;
                    int finalY = doorY + (int)direction.getVector().y;
                    position.set(finalX + 0.5f, finalY + 0.5f);
                    Gdx.app.log("PlayerMovement", "Auto-moved to (" + (int)position.x + "," + (int)position.y + ")");
                    checkForItemPickup(maze); // Check again after passing through door
                }
            }
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

    public Vector2 getPosition() { return position; }
    public Direction getFacing() { return facing; }
    public Vector2 getDirectionVector() { return directionVector; }
    public Vector2 getCameraPlane() { return cameraPlane; }
    public int getWarStrength() { return warStrength; }
    public int getSpiritualStrength() { return spiritualStrength; }
    public int getFood() { return food; }
    public int getArrows() { return arrows; }
    public Inventory getInventory() { return inventory; }
}
