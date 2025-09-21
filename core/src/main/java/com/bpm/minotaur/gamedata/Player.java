package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.managers.GameEventManager;

import java.util.ArrayList;
import java.util.List;

public class Player {

    private int warStrength;
    private int spiritualStrength;
    private int food;
    private int arrows;

    private int treasureScore = 0;

    private final int maxWarStrength;
    private final int maxSpiritualStrength;

    private final Vector2 position;
    private Direction facing;
    private final Vector2 directionVector;
    private final Vector2 cameraPlane;

    private final Inventory inventory = new Inventory();

    // Equipment slots
    private Item wornHelmet = null;
    private Item wornShield = null;


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

        this.maxWarStrength = 150;
        this.maxSpiritualStrength = 100;

        inventory.setRightHand(new Item(Item.ItemType.BOW, 0, 0));
    }

    public void interactWithItem(Maze maze, GameEventManager eventManager) {
        // Determine the target tile in front of the player
        int targetX = (int) (position.x + facing.getVector().x);
        int targetY = (int) (position.y + facing.getVector().y);
        GridPoint2 targetTile = new GridPoint2(targetX, targetY);
        Item itemInFront = maze.getItems().get(targetTile);

        // --- PICKUP/SWAP LOGIC (targets tile in front) ---
        if (itemInFront != null) {
            // Handle Treasure pickup
            if (itemInFront.getCategory() == Item.ItemCategory.TREASURE) {
                treasureScore += itemInFront.getValue();
                maze.getItems().remove(targetTile);
                eventManager.addEvent(new GameEvent("You found " + itemInFront.getType() + "! Your treasure score is now " + treasureScore, 2f));
                Gdx.app.log("Player", "Picked up " + itemInFront.getType() + " with value " + itemInFront.getValue());
                return;
            }

            Item itemInHand = inventory.getRightHand();
            if (itemInHand != null) { // Swap with item in front
                inventory.setRightHand(maze.getItems().remove(targetTile));
                itemInHand.getPosition().set(targetX + 0.5f, targetY + 0.5f);
                maze.getItems().put(targetTile, itemInHand);
                eventManager.addEvent(new GameEvent("Swapped items", 2f));
                Gdx.app.log("Player", "Swapped with item in front.");
            } else { // Pick up item from front
                inventory.setRightHand(maze.getItems().remove(targetTile));
                eventManager.addEvent(new GameEvent("Picked up " + inventory.getRightHand().getType(), 2f));
                Gdx.app.log("Player", "Picked up item from front.");
            }
        }
        // --- DROP LOGIC (targets player's current tile) ---
        else if (inventory.getRightHand() != null) {
            GridPoint2 playerTile = new GridPoint2((int)position.x, (int)position.y);
            // Check if there's already an item at the player's feet
            if (maze.getItems().containsKey(playerTile)) {
                eventManager.addEvent(new GameEvent("Cannot drop, item at your feet.", 2f));
                Gdx.app.log("Player", "Drop failed, tile occupied.");
            } else {
                Item itemInHand = inventory.getRightHand();
                itemInHand.getPosition().set(playerTile.x + 0.5f, playerTile.y + 0.5f);
                maze.addItem(itemInHand);
                inventory.setRightHand(null);
                eventManager.addEvent(new GameEvent("Dropped " + itemInHand.getType(), 2f));
                Gdx.app.log("Player", "Dropped item at feet.");
            }
        }
        // --- NO ACTION ---
        else {
            eventManager.addEvent(new GameEvent("Nothing to interact with.", 2f));
        }
    }

    public void useItem(GameEventManager eventManager) {
        Item itemInHand = inventory.getRightHand();
        if (itemInHand == null) {
            eventManager.addEvent(new GameEvent("Right hand is empty.", 2f));
            return;
        }

        switch (itemInHand.getCategory()) {
            case ARMOR:
                equipArmor(itemInHand, eventManager);
                break;
            case USEFUL:
                useConsumable(itemInHand, eventManager);
                break;
            default:
                eventManager.addEvent(new GameEvent("Cannot use this item.", 2f));
                Gdx.app.log("Player", "Cannot use " + itemInHand.getType());
                break;
        }
    }

    private void equipArmor(Item armor, GameEventManager eventManager) {
        switch (armor.getType()) {
            case HELMET:
                if (wornHelmet == null || armor.getArmorStats().defense > wornHelmet.getArmorStats().defense) {
                    wornHelmet = armor;
                    inventory.setRightHand(null);
                    eventManager.addEvent(new GameEvent("Equipped Helmet.", 2f));
                } else {
                    eventManager.addEvent(new GameEvent("This helmet is not better.", 2f));
                }
                break;
            case SHIELD:
                if (wornShield == null || armor.getArmorStats().defense > wornShield.getArmorStats().defense) {
                    wornShield = armor;
                    inventory.setRightHand(null);
                    eventManager.addEvent(new GameEvent("Equipped Shield.", 2f));
                } else {
                    eventManager.addEvent(new GameEvent("This shield is not better.", 2f));
                }
                break;
        }
    }

    private void useConsumable(Item item, GameEventManager eventManager) {
        if (item.getType() == Item.ItemType.POTION_HEALING) {
            int healAmount = 25; // Example heal amount
            this.warStrength = Math.min(this.maxWarStrength, this.warStrength + healAmount);
            inventory.setRightHand(null); // Consume the potion
            eventManager.addEvent(new GameEvent("You used a Healing Potion.", 2f));
            Gdx.app.log("Player", "Used healing potion. WS is now " + this.warStrength);
        } else {
            eventManager.addEvent(new GameEvent("Cannot use this item.", 2f));
        }
    }


    private void updateVectors() {
        directionVector.set(facing.getVector());
        cameraPlane.set(-directionVector.y, directionVector.x).scl(0.66f);
    }

    public void rest() {
        if (food > 0) {
            food--;

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
        int damageReduction = getArmorDefense();
        int finalDamage = Math.max(0, amount - damageReduction);
        this.warStrength -= finalDamage;

        if (this.warStrength < 0) {
            this.warStrength = 0;
        }
        Gdx.app.log("Player", "Player takes " + finalDamage + " damage. WS is now " + this.warStrength);
    }

    public void takeSpiritualDamage(int amount) {
        int damageReduction = getRingDefense();
        int finalDamage = Math.max(0, amount - damageReduction);
        this.spiritualStrength -= finalDamage;

        if (this.spiritualStrength < 0) {
            this.spiritualStrength = 0;
        }
        Gdx.app.log("Player", "Player takes " + finalDamage + " spiritual damage. SS is now " + this.spiritualStrength);
    }

    public int getArmorDefense() {
        int totalDefense = 0;
        if (wornHelmet != null) {
            totalDefense += wornHelmet.getArmorStats().defense;
        }
        if (wornShield != null) {
            totalDefense += wornShield.getArmorStats().defense;
        }
        return totalDefense;
    }

    public int getRingDefense() {
        return 0;
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

        // Check for a wall on the current tile blocking the path
        if (maze.isWallBlocking(currentX, currentY, direction)) {
            Gdx.app.log("PlayerMovement", "Movement blocked by a wall.");
            return;
        }

        int nextX = currentX + (int)direction.getVector().x;
        int nextY = currentY + (int)direction.getVector().y;

        Object nextObject = maze.getGameObjectAt(nextX, nextY);

        if (nextObject instanceof Door) {
            Door door = (Door) nextObject;
            if (door.getState() == Door.DoorState.OPEN) {
                // If the door is open, move the player to the tile *beyond* the door
                int finalX = nextX + (int)direction.getVector().x;
                int finalY = nextY + (int)direction.getVector().y;

                // Before the final move, check for a wall after the door.
                if (!maze.isWallBlocking(nextX, nextY, direction)) {
                    position.set(finalX + 0.5f, finalY + 0.5f);
                    Gdx.app.log("PlayerMovement", "Passed through open door to (" + finalX + "," + finalY + ")");
                } else {
                    Gdx.app.log("PlayerMovement", "Movement blocked by a wall after the door.");
                }

            } else {
                // If the door is not open, the player cannot move.
                Gdx.app.log("PlayerMovement", "Door is not open. Movement blocked.");
            }
        } else {
            // If there's no door, it's a regular move.
            position.set(nextX + 0.5f, nextY + 0.5f);
            Gdx.app.log("PlayerMovement", "Moved to (" + nextX + "," + nextY + ")");
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

    public void interact(Maze maze, GameEventManager eventManager) {
        int targetX = (int) (position.x + facing.getVector().x);
        int targetY = (int) (position.y + facing.getVector().y);
        GridPoint2 targetTile = new GridPoint2(targetX, targetY);
        Gdx.app.log("Interaction", "Player interacting with tile (" + targetX + ", " + targetY + ")");

        // Priority 1: Open a door
        Object obj = maze.getGameObjectAt(targetX, targetY);
        if (obj instanceof Door) {
            Door door = (Door) obj;
            if (door.getState() == Door.DoorState.CLOSED) {
                door.startOpening();
                eventManager.addEvent(new GameEvent("You opened the door.", 2f));
            }
            return; // Interaction handled
        }

        // Priority 2: Open a container
        Item itemInFront = maze.getItems().get(targetTile);
        if (itemInFront != null && itemInFront.getCategory() == Item.ItemCategory.CONTAINER) {
            if (itemInFront.isLocked()) {
                if (hasKey()) {
                    itemInFront.unlock();
                    consumeKey();
                    eventManager.addEvent(new GameEvent("You unlocked the " + itemInFront.getType() + "!", 2f));
                } else {
                    eventManager.addEvent(new GameEvent("The " + itemInFront.getType() + " is locked.", 2f));
                    return;
                }
            }

            List<Item> contents = new ArrayList<>(itemInFront.getContents());
            maze.getItems().remove(targetTile); // Remove the container itself from the maze first

            if (contents.isEmpty()) {
                eventManager.addEvent(new GameEvent("The " + itemInFront.getType() + " is empty.", 2f));
            } else {
                eventManager.addEvent(new GameEvent("You open the " + itemInFront.getType() + ".", 2f));
                for (Item contentItem : contents) {
                    // Drop the item on the same tile as the container
                    contentItem.getPosition().set(targetX + 0.5f, targetY + 0.5f);
                    maze.addItem(contentItem);
                }
            }
            return;
        }

        eventManager.addEvent(new GameEvent("Nothing to open.", 2f));
    }

    private boolean hasKey() {
        Inventory inv = getInventory();
        if (inv.getRightHand() != null && inv.getRightHand().getType() == Item.ItemType.KEY) {
            return true;
        }
        if (inv.getLeftHand() != null && inv.getLeftHand().getType() == Item.ItemType.KEY) {
            return true;
        }
        for (Item item : inv.getBackpack()) {
            if (item != null && item.getType() == Item.ItemType.KEY) {
                return true;
            }
        }
        return false;
    }

    private void consumeKey() {
        Inventory inv = getInventory();
        if (inv.getRightHand() != null && inv.getRightHand().getType() == Item.ItemType.KEY) {
            inv.setRightHand(null);
            return;
        }
        if (inv.getLeftHand() != null && inv.getLeftHand().getType() == Item.ItemType.KEY) {
            inv.setLeftHand(null);
            return;
        }
        for (int i = 0; i < inv.getBackpack().length; i++) {
            if (inv.getBackpack()[i] != null && inv.getBackpack()[i].getType() == Item.ItemType.KEY) {
                inv.getBackpack()[i] = null;
                return;
            }
        }
    }
    public int getTreasureScore() { return treasureScore; }
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
