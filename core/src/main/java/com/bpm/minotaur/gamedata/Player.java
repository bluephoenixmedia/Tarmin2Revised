package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.SoundManager;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Player {

    private int warStrength;
    private int spiritualStrength;
    private int food;
    private int arrows;

    private int treasureScore = 0;

    private int maxWarStrength;
    private int maxSpiritualStrength;

    private final Vector2 position;
    private Direction facing;
    private final Vector2 directionVector;
    private final Vector2 cameraPlane;

    private final Inventory inventory = new Inventory();

    private final float vulnerabilityMultiplier; // Add this line


    // Equipment slots
    private Item wornHelmet = null;
    private Item wornShield = null;
    private Item wornRing; // ADD THIS LINE




    public Player(float startX, float startY, Difficulty difficulty) {
        this.position = new Vector2(startX + 0.5f, startY + 0.5f);
        this.facing = Direction.NORTH;
        this.directionVector = new Vector2();
        this.cameraPlane = new Vector2();
        updateVectors();

        // Set stats based on difficulty
        this.warStrength = difficulty.startWarStrength;
        this.spiritualStrength = difficulty.startSpiritualStrength;
        this.food = difficulty.startFood;
        this.arrows = difficulty.startArrows;
        this.vulnerabilityMultiplier = difficulty.vulnerabilityMultiplier;

        this.maxWarStrength = difficulty.startWarStrength;
        this.maxSpiritualStrength = difficulty.startSpiritualStrength;


        inventory.setRightHand(new Item(Item.ItemType.BOW, 0, 0));
    }



    public void interactWithItem(Maze maze, GameEventManager eventManager, SoundManager soundManager) {
        // Determine the target tile in front of the player
        int targetX = (int) (position.x + facing.getVector().x);
        int targetY = (int) (position.y + facing.getVector().y);
        GridPoint2 targetTile = new GridPoint2(targetX, targetY);
        Item itemInFront = maze.getItems().get(targetTile);

        // --- PICKUP/SWAP LOGIC (targets tile in front) ---
        if (itemInFront != null) {
            soundManager.playPickupItemSound();
            // Handle Treasure pickup
            if (itemInFront.getCategory() == Item.ItemCategory.TREASURE) {
                treasureScore += itemInFront.getValue();
                maze.getItems().remove(targetTile);
                eventManager.addEvent(new GameEvent("You found " + itemInFront.getType() + "! Your treasure score is now " + treasureScore, 2f));
                Gdx.app.log("Player", "Picked up " + itemInFront.getType() + " with value " + itemInFront.getValue());
                return;
            }

            // Handle Quiver pickup
            if (itemInFront.getType() == Item.ItemType.QUIVER) {
                int arrowsFound = new Random().nextInt(4) + 6; // 6 to 9 arrows, as per the manual
                addArrows(arrowsFound);
                maze.getItems().remove(targetTile);
                eventManager.addEvent(new GameEvent("You found a quiver with " + arrowsFound + " arrows.", 2f));
                return;
            }

            // Handle Flour Sack pickup
            if (itemInFront.getType() == Item.ItemType.FLOUR_SACK) {
                int foodFound = new Random().nextInt(4) + 6; // 6 to 9 food, as per the manual
                addFood(foodFound);
                maze.getItems().remove(targetTile);
                eventManager.addEvent(new GameEvent("You found a flour sack with " + foodFound + " food.", 2f));
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
            case RING:
                equipRing(itemInHand, eventManager);
                break;
            case USEFUL:
                useConsumable(itemInHand, eventManager);
                break;
            default:
                eventManager.addEvent(new GameEvent("Cannot use this item.", 2f));
                break;
        }
    }

    private int getMaxWarStrength() {
        return maxWarStrength;
    }

    private void equipRing(Item ring, GameEventManager eventManager) {
        if (ring.getCategory() != Item.ItemCategory.RING) return;

        eventManager.addEvent(new GameEvent("Equipped " + ring.getType(), 2f));

        // Swap the ring in hand with the worn ring
        Item previouslyWornRing = this.wornRing;
        this.wornRing = ring;
        inventory.setRightHand(previouslyWornRing);
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
            case SMALL_SHIELD:
            case LARGE_SHIELD:
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
        switch (item.getType()) {
            case SMALL_POTION:
                // Refresh war & spiritual strength to maximum
                this.warStrength = this.maxWarStrength;
                this.spiritualStrength = this.maxSpiritualStrength;
                inventory.setRightHand(null);
                eventManager.addEvent(new GameEvent("You feel refreshed.", 2f));
                break;
            case LARGE_POTION:
                // Raise war strength score by 10
                this.warStrength = Math.min(this.maxWarStrength, this.warStrength + 10);
                inventory.setRightHand(null);
                eventManager.addEvent(new GameEvent("You feel stronger.", 2f));
                break;
            case WAR_BOOK:
                this.maxWarStrength += 10;
                this.warStrength = this.maxWarStrength;
                inventory.setRightHand(null);
                eventManager.addEvent(new GameEvent("Your knowledge of war grows.", 2f));
                break;
            case SPIRITUAL_BOOK:
                this.maxSpiritualStrength += 10;
                this.spiritualStrength = this.maxSpiritualStrength;
                inventory.setRightHand(null);
                eventManager.addEvent(new GameEvent("Your spiritual knowledge grows.", 2f));
                break;
            default:
                eventManager.addEvent(new GameEvent("Cannot use this item.", 2f));
                break;
        }
    }


    private void updateVectors() {
        directionVector.set(facing.getVector());
        cameraPlane.set(-directionVector.y, directionVector.x).scl(0.66f);  // Changed second component
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
        // Apply vulnerability multiplier to the incoming damage amount first
        int finalDamage = (int)(amount * vulnerabilityMultiplier);
        // Then, subtract armor defense
        finalDamage = Math.max(0, finalDamage - damageReduction);

        this.warStrength -= finalDamage;

        if (this.warStrength < 0) {
            this.warStrength = 0;
        }
        Gdx.app.log("Player", "Player takes " + finalDamage + " damage. WS is now " + this.warStrength);
    }

    public void takeSpiritualDamage(int amount) {
        int damageReduction = getRingDefense();
        // Apply vulnerability multiplier to the incoming damage amount first
        int finalDamage = (int)(amount * vulnerabilityMultiplier);
        // Then, subtract ring defense
        finalDamage = Math.max(0, finalDamage - damageReduction);

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

    public Item getWornRing() {
        return wornRing;
    }

    public void setWarStrength(int amount) {
        this.warStrength = Math.min(amount, this.maxWarStrength);
    }
    public void setMaxWarStrength(int amount) { this.maxWarStrength = amount; }


    private int getRingDefense() {
        if (wornRing != null && wornRing.getRingStats() != null) {
            return wornRing.getRingStats().defense;
        }
        return 0;
    }

    public void moveForward(Maze maze) {
        move(facing, maze);
    }

    public void moveBackward(Maze maze) {
        move(facing.getOpposite(), maze);
    }

    // In Player.java

    private void move(Direction direction, Maze maze) {
        Gdx.app.log("PlayerMovement", "Attempting to move " + direction + " from (" + (int)position.x + "," + (int)position.y + ")");

        int currentX = (int) position.x;
        int currentY = (int) position.y;

        // First, check if the immediate path is blocked. This handles walls and closed doors.
        if (maze.isWallBlocking(currentX, currentY, direction)) {
            Gdx.app.log("PlayerMovement", "Movement blocked by a wall or closed door.");
            return;
        }

        // If not blocked, calculate the next tile's position.
        int nextX = currentX + (int)direction.getVector().x;
        int nextY = currentY + (int)direction.getVector().y;

        // Check if the tile we are moving onto contains a door.
        Object nextObject = maze.getGameObjectAt(nextX, nextY);

        if (nextObject instanceof Door) {
            // Since isWallBlocking was false, the door must be open.
            // We move an extra tile to "skip" over the door's space.
            int finalX = nextX + (int)direction.getVector().x;
            int finalY = nextY + (int)direction.getVector().y;

            // As a safety measure, check that the space beyond the door is also clear.
            if (!maze.isWallBlocking(nextX, nextY, direction)) {
                position.set(finalX + 0.5f, finalY + 0.5f);
                Gdx.app.log("PlayerMovement", "Passed through open door to (" + finalX + "," + finalY + ")");
            } else {
                // This case would be rare, like a door leading directly into a wall.
                Gdx.app.log("PlayerMovement", "Movement blocked by a wall immediately after the door.");
            }
        } else {
            // Standard movement for non-door tiles.
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

    public void interact(Maze maze, GameEventManager eventManager, SoundManager soundManager) {
        int targetX = (int) (position.x + facing.getVector().x);
        int targetY = (int) (position.y + facing.getVector().y);
        GridPoint2 targetTile = new GridPoint2(targetX, targetY);
        Gdx.app.log("Interaction", "Player interacting with tile (" + targetX + ", " + targetY + ")");

        if (maze.getGates().containsKey(targetTile)) {
            useGate(maze, eventManager);
            return;
        }


        // Priority 1: Open a door
        Object obj = maze.getGameObjectAt(targetX, targetY);
        if (obj instanceof Door) {
            Door door = (Door) obj;
            if (door.getState() == Door.DoorState.CLOSED) {
                door.startOpening();
                eventManager.addEvent(new GameEvent("You opened the door.", 2f));
                soundManager.playDoorOpenSound();
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

    private void useGate(Maze maze, GameEventManager eventManager) {
        Gdx.app.log("Player", "Player used a gate.");
        eventManager.addEvent(new GameEvent("You touch the strange mural...", 2f));

        // Stat Jumbling Logic
        int outcome = new Random().nextInt(4); // 4 possible outcomes
        switch (outcome) {
            case 1: // Swap stats
                int temp = warStrength;
                setWarStrength(getSpiritualStrength());
                spiritualStrength = temp;
                Gdx.app.log("Player", "Gate swapped stats!");
                break;
            case 2: // Reduce War Strength
                setWarStrength((int)(getWarStrength() * 0.75f)); // Reduce by 25%
                Gdx.app.log("Player", "Gate reduced War Strength!");
                break;
            case 3: // Reduce Spiritual Strength
                spiritualStrength = (int)(spiritualStrength * 0.75f); // Reduce by 25%
                Gdx.app.log("Player", "Gate reduced Spiritual Strength!");
                break;
            default: // No change
                Gdx.app.log("Player", "Gate had no effect on stats.");
                break;
        }

        // Teleport Logic
        List<GridPoint2> emptyTiles = new ArrayList<>();
        for (int y = 0; y < maze.getHeight(); y++) {
            for (int x = 0; x < maze.getWidth(); x++) {
                if (maze.getWallDataAt(x, y) == 0 && maze.getGameObjectAt(x, y) == null) {
                    emptyTiles.add(new GridPoint2(x, y));
                }
            }
        }

        if (!emptyTiles.isEmpty()) {
            GridPoint2 newPos = emptyTiles.get(new Random().nextInt(emptyTiles.size()));
            position.set(newPos.x + 0.5f, newPos.y + 0.5f);
            Gdx.app.log("Player", "Teleported to (" + newPos.x + ", " + newPos.y + ")");
        }
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

    public void addArrows(int amount) {
        this.arrows += amount;
        if (this.arrows > 99) {
            this.arrows = 99; // Cap at 99 as per the manual
        }
    }

    public void addFood(int amount) {
        this.food += amount;
        if (this.food > 99) {
            this.food = 99; // Cap at 99 as per the manual
        }
    }

    public void decrementArrow() {
        if (this.arrows > 0) {
            this.arrows--;
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
