package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.SoundManager;
import com.bpm.minotaur.managers.WorldManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Player {

    // --- All stat fields removed ---

    // --- New PlayerStats field ---
    private final PlayerStats stats;

    private final Vector2 position;
    private Direction facing;
    private final Vector2 directionVector;
    private final Vector2 cameraPlane;

    private final Inventory inventory = new Inventory();

    private SoundManager soundManager;
    private DebugManager debugManager;


    // Equipment slots
    private Item wornHelmet = null;
    private Item wornShield = null;
    private Item wornGauntlets = null;
    private Item wornHauberk = null;
    private Item wornBreastplate = null;
    private Item wornRing; // ADD THIS LINE

    private Maze maze; // <-- [NEW] Reference to the player's current maze



    public Player(float startX, float startY, Difficulty difficulty) {
        this.position = new Vector2(startX + 0.5f, startY + 0.5f);
        this.facing = Direction.NORTH;
        this.directionVector = new Vector2();
        this.cameraPlane = new Vector2();
        this.soundManager = new SoundManager(null);
        updateVectors();

        // --- All stat initialization is replaced by creating the PlayerStats object ---
        this.stats = new PlayerStats(difficulty);

        inventory.setRightHand(new Item(Item.ItemType.BOW, 0, 0, ItemColor.TAN));

        // --- Leveling stat init removed, it's in PlayerStats constructor ---
    }

    /**
     * Sets the player's current maze reference.
     * @param maze The new maze the player is in.
     */
    public void setMaze(Maze maze) {
        this.maze = maze;
    }

    /**
     * Sets the player's position based on a GridPoint2,
     * automatically adding 0.5f to center them.
     * @param newPos The new grid position.
     */
    public void setPosition(GridPoint2 newPos) {
        this.position.set(newPos.x + 0.5f, newPos.y + 0.5f);
    }



    public void interactWithItem(Maze maze, GameEventManager eventManager, SoundManager soundManager) {

        int playerGridX = (int) position.x;
        int playerGridY = (int) position.y;
        GridPoint2 playerTile2 = new GridPoint2(playerGridX, playerGridY);

        Item itemAtFeet = maze.getItems().get(playerTile2);

        if (itemAtFeet != null) {
            // Found an item at our feet. Try to pick it up.

            Item itemInHand = inventory.getRightHand();
            if (itemInHand != null) { // Swap with item in front
                inventory.setRightHand(maze.getItems().remove(playerTile2));
                itemInHand.getPosition().set(playerGridX + 0.5f, playerGridY + 0.5f);
                maze.getItems().put(playerTile2, itemInHand);
                eventManager.addEvent(new GameEvent("Swapped items", 2f));
                Gdx.app.log("Player", "Swapped with item in front.");
            } else { // Pick up item from front
                inventory.setRightHand(maze.getItems().remove(playerTile2));
                eventManager.addEvent(new GameEvent("Picked up " + inventory.getRightHand().getDisplayName(), 2f));
                Gdx.app.log("Player", "Picked up item from front.");
                return;
            }
        }


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
                // --- MODIFIED: Use stats object ---
                stats.incrementTreasureScore(itemInFront.getValue());
                maze.getItems().remove(targetTile);
                eventManager.addEvent(new GameEvent("You found " + itemInFront.getDisplayName() + "! Your treasure score is now " + stats.getTreasureScore(), 2f));
                Gdx.app.log("Player", "Picked up " + itemInFront.getDisplayName() + " with value " + itemInFront.getValue());
                return;
            }

            // Handle Quiver pickup
            if (itemInFront.getType() == Item.ItemType.QUIVER) {
                int arrowsFound = new Random().nextInt(4) + 6; // 6 to 9 arrows, as per the manual
                // --- MODIFIED: Use stats object ---
                stats.addArrows(arrowsFound);
                maze.getItems().remove(targetTile);
                eventManager.addEvent(new GameEvent("You found a quiver with " + arrowsFound + " arrows.", 2f));
                return;
            }

            // Handle Flour Sack pickup
            if (itemInFront.getType() == Item.ItemType.FLOUR_SACK) {
                int foodFound = new Random().nextInt(4) + 6; // 6 to 9 food, as per the manual
                // --- MODIFIED: Use stats object ---
                stats.addFood(foodFound);
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
                eventManager.addEvent(new GameEvent("Picked up " + inventory.getRightHand().getDisplayName(), 2f));
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
                eventManager.addEvent(new GameEvent("Dropped " + itemInHand.getDisplayName(), 2f));
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

    // --- MODIFIED: This method now delegates to stats, but it's private and only used in useConsumable... ---
    // --- We can remove it and just call stats.getMaxWarStrength() directly in useConsumable ---
    // private int getMaxWarStrength() {
    //     return stats.getMaxWarStrength();
    // }

    private void equipRing(Item ring, GameEventManager eventManager) {
        if (ring.getCategory() != Item.ItemCategory.RING) return;

        eventManager.addEvent(new GameEvent("Equipped " + ring.getDisplayName(), 2f));

        // Swap the ring in hand with the worn ring
        Item previouslyWornRing = this.wornRing;
        this.wornRing = ring;
        inventory.setRightHand(previouslyWornRing);
    }

    private void equipArmor(Item armor, GameEventManager eventManager) {
        // --- MODIFIED: Use getDisplayName ---
        switch (armor.getType()) {
            case HELMET:
                if (wornHelmet == null || armor.getArmorStats().defense > wornHelmet.getArmorStats().defense) {
                    wornHelmet = armor;
                    inventory.setRightHand(null);
                    eventManager.addEvent(new GameEvent("Equipped " + armor.getDisplayName(), 2f));
                } else {
                    eventManager.addEvent(new GameEvent("This helmet is not better.", 2f));
                }
                break;
            case SMALL_SHIELD: // Fall-through
            case LARGE_SHIELD:
                if (wornShield == null || armor.getArmorStats().defense > wornShield.getArmorStats().defense) {
                    wornShield = armor;
                    inventory.setRightHand(null);
                    eventManager.addEvent(new GameEvent("Equipped " + armor.getDisplayName(), 2f));
                } else {
                    eventManager.addEvent(new GameEvent("This shield is not better.", 2f));
                }
                break;
            case GAUNTLETS:
                if (wornGauntlets == null || armor.getArmorStats().defense > wornGauntlets.getArmorStats().defense) {
                    wornGauntlets = armor;
                    inventory.setRightHand(null);
                    eventManager.addEvent(new GameEvent("Equipped " + armor.getDisplayName(), 2f));
                } else {
                    eventManager.addEvent(new GameEvent("These Gauntlets are not better.", 2f));
                }
                break;
            case HAUBERK:
                if (wornHauberk == null || armor.getArmorStats().defense > wornHauberk.getArmorStats().defense) {
                    wornHauberk = armor;
                    inventory.setRightHand(null);
                    eventManager.addEvent(new GameEvent("Equipped " + armor.getDisplayName(), 2f));
                } else {
                    eventManager.addEvent(new GameEvent("This Hauberk is not better.", 2f));
                }
                break;
            case BREASTPLATE:
                if (wornBreastplate == null || armor.getArmorStats().defense > wornBreastplate.getArmorStats().defense) {
                    wornBreastplate = armor;
                    inventory.setRightHand(null);
                    eventManager.addEvent(new GameEvent("Equipped " + armor.getDisplayName(), 2f));
                } else {
                    eventManager.addEvent(new GameEvent("This Breastplate is not better.", 2f));
                }
                break;
        }
    }

    private void useConsumable(Item item, GameEventManager eventManager) {
        switch (item.getType()) {
            case SMALL_POTION:
                // Refresh war & spiritual strength to maximum
                // --- MODIFIED: Use stats object and Player.setWarStrength ---
                this.setWarStrength(this.getEffectiveMaxWarStrength());
                stats.setSpiritualStrength(this.getEffectiveMaxSpiritualStrength());
                // --- END MODIFIED ---
                inventory.setRightHand(null);
                eventManager.addEvent(new GameEvent("You feel refreshed.", 2f));
                break;
            case LARGE_POTION:
                // Raise war strength score by 10
                // --- MODIFIED: Use stats object ---
                stats.setMaxWarStrength(stats.getMaxWarStrength() + 10);
                this.setWarStrength(this.getEffectiveMaxWarStrength()); // Heal to new max
                inventory.setRightHand(null);
                eventManager.addEvent(new GameEvent("You feel stronger.", 2f));
                break;
            case WAR_BOOK:
                // --- MODIFIED: Use stats object ---
                stats.setMaxWarStrength(stats.getMaxWarStrength() + 10);
                this.setWarStrength(this.getEffectiveMaxWarStrength());
                inventory.setRightHand(null);
                eventManager.addEvent(new GameEvent("Your knowledge of war grows.", 2f));
                break;
            case SPIRITUAL_BOOK:
                // --- MODIFIED: Use stats object ---
                stats.setMaxSpiritualStrength(stats.getMaxSpiritualStrength() + 10);
                stats.setSpiritualStrength(this.getEffectiveMaxSpiritualStrength());
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

    public void rest(GameEventManager eventManager) {
        // --- MODIFIED: Use stats object ---
        if (stats.getFood() > 0) {
            stats.setFood(stats.getFood() - 1);

            int warStrengthGained = 5;
            int spiritualStrengthGained = 5;

            this.setWarStrength(Math.min(this.getEffectiveMaxWarStrength(), this.getWarStrength() + warStrengthGained));
            stats.setSpiritualStrength(Math.min(this.getEffectiveMaxSpiritualStrength(), stats.getSpiritualStrength() + spiritualStrengthGained));

            Gdx.app.log("Player", "Player rests. Food remaining: " + stats.getFood());
            Gdx.app.log("Player", "WS restored to " + stats.getWarStrength() + ", SS restored to " + stats.getSpiritualStrength());
            eventManager.addEvent(new GameEvent(("WS restored to " + stats.getWarStrength() + ", SS restored to " + stats.getSpiritualStrength()), 2f));

        } else {
            Gdx.app.log("Player", "Cannot rest. No food remaining.");
            eventManager.addEvent(new GameEvent("You have no food to rest.", 2f));
        }
    }

    public void setFacing(Direction facing) {
        this.facing = facing;
        updateVectors();
    }

    public void takeDamage(int amount, DamageType type) {
        int damageReduction = getArmorDefense(); // Gets base armor + BONUS_DEFENSE
        int resistance = getResistance(type); // Gets elemental/effect resistance

        // Apply vulnerability multiplier to the incoming damage amount first
        // --- MODIFIED: Use stats object ---
        int finalDamage = (int)(amount * stats.getVulnerabilityMultiplier());

        // Then, subtract armor defense and elemental resistance
        finalDamage = Math.max(0, finalDamage - damageReduction - resistance);

        // --- MODIFIED: Use stats object ---
        stats.setWarStrength(stats.getWarStrength() - finalDamage);

        if (stats.getWarStrength() < 0) {
            stats.setWarStrength(0);
        }
        Gdx.app.log("Player", "Player takes " + finalDamage + " " + type.name() + " damage. WS is now " + stats.getWarStrength());
    }

    public void takeSpiritualDamage(int amount, DamageType type) {
        int damageReduction = getRingDefense(); // Gets base ring defense + BONUS_DEFENSE
        int resistance = getResistance(type); // Gets elemental/effect resistance

        // Apply vulnerability multiplier to the incoming damage amount first
        // --- MODIFIED: Use stats object ---
        int finalDamage = (int)(amount * stats.getVulnerabilityMultiplier());

        // Then, subtract ring defense and elemental resistance
        finalDamage = Math.max(0, finalDamage - damageReduction - resistance);

        // --- MODIFIED: Use stats object ---
        stats.setSpiritualStrength(stats.getSpiritualStrength() - finalDamage);

        if (stats.getSpiritualStrength() < 0) {
            stats.setSpiritualStrength(0);
        }
        Gdx.app.log("Player", "Player takes " + finalDamage + " " + type.name() + " spiritual damage. SS is now " + stats.getSpiritualStrength());
    }

    public int getArmorDefense() {
        int totalDefense = 0;

        // Base defense from armor stats
        if (wornHelmet != null && wornHelmet.getArmorStats() != null) totalDefense += wornHelmet.getArmorStats().defense;
        if (wornShield != null && wornShield.getArmorStats() != null) totalDefense += wornShield.getArmorStats().defense;
        if (wornGauntlets != null && wornGauntlets.getArmorStats() != null) totalDefense += wornGauntlets.getArmorStats().defense;
        if (wornHauberk != null && wornHauberk.getArmorStats() != null) totalDefense += wornHauberk.getArmorStats().defense;
        if (wornBreastplate != null && wornBreastplate.getArmorStats() != null) totalDefense += wornBreastplate.getArmorStats().defense;

        // Add bonus defense from all equipped items (including rings)
        totalDefense += getEquippedModifierSum(ModifierType.BONUS_DEFENSE);

        return totalDefense;
    }

    public int getEffectiveMaxWarStrength() {
        // --- MODIFIED: Use stats object ---
        return stats.getMaxWarStrength() + getEquippedModifierSum(ModifierType.BONUS_WAR_STRENGTH);
    }
    // --- END MODIFIED ---

    // --- NEW METHOD ---
    public int getEffectiveMaxSpiritualStrength() {
        // --- MODIFIED: Use stats object ---
        return stats.getMaxSpiritualStrength() + getEquippedModifierSum(ModifierType.BONUS_SPIRITUAL_STRENGTH);
    }

    public Item getWornRing() {
        return wornRing;
    }

    public void setWarStrength(int amount) {
        // --- MODIFIED: Use stats object ---
        stats.setWarStrength(Math.min(amount, this.getEffectiveMaxWarStrength()));
    }

    public void setMaxWarStrength(int amount) {
        // --- MODIFIED: Use stats object ---
        stats.setMaxWarStrength(amount);
    }


    private int getRingDefense() {
        int totalDefense = 0;

        // Base defense from ring stats
        if (wornRing != null && wornRing.getRingStats() != null) {
            totalDefense += wornRing.getRingStats().defense;
        }

        // Add bonus defense from all equipped items (including armor)
        totalDefense += getEquippedModifierSum(ModifierType.BONUS_DEFENSE);

        return totalDefense;
    }

    /**
     * Gets the total resistance value for a specific DamageType from all equipped gear.
     * @param type The DamageType to check for.
     * @return The total resistance value.
     */
    private int getResistance(DamageType type) {
        // Physical and Spiritual are handled by Armor/Ring defense, not elemental resistance.
        if (type == DamageType.PHYSICAL || type == DamageType.SPIRITUAL) {
            return 0;
        }

        ModifierType modTypeToFind;
        switch (type) {
            case FIRE: modTypeToFind = ModifierType.RESIST_FIRE; break;
            case ICE: modTypeToFind = ModifierType.RESIST_ICE; break;
            case POISON: modTypeToFind = ModifierType.RESIST_POISON; break;
            case BLEED: modTypeToFind = ModifierType.RESIST_BLEED; break;
            case DISEASE: modTypeToFind = ModifierType.RESIST_DISEASE; break;
            case DARK: modTypeToFind = ModifierType.RESIST_DARK; break;
            case LIGHT: modTypeToFind = ModifierType.RESIST_LIGHT; break;
            case SORCERY: modTypeToFind = ModifierType.RESIST_SORCERY; break;
            default: return 0; // No resistance for this type
        }

        return getEquippedModifierSum(modTypeToFind);
    }

    /**
     * A generic helper to sum up the value of a specific modifier from ALL equipped items.
     * @param typeToFind The ModifierType to search for.
     * @return The sum of all values for that modifier.
     */
    private int getEquippedModifierSum(ModifierType typeToFind) {
        int total = 0;
        Item[] equippedItems = { wornHelmet, wornShield, wornGauntlets, wornHauberk, wornBreastplate, wornRing };

        for (Item item : equippedItems) {
            if (item != null) {
                for (ItemModifier mod : item.getModifiers()) {
                    if (mod.type == typeToFind) {
                        total += mod.value;
                    }
                }
            }
        }
        return total;
    }

    public void moveForward(Maze maze, GameEventManager eventManager, GameMode gameMode) { // --- ADD PARAMS ---
        move(facing, maze, eventManager, gameMode); // --- PASS PARAMS ---
    }

    public void moveBackward(Maze maze, GameEventManager eventManager, GameMode gameMode) { // --- ADD PARAMS ---
        move(facing.getOpposite(), maze, eventManager, gameMode); // --- PASS PARAMS ---
    }

    // In Player.java

    private void move(Direction direction, Maze maze, GameEventManager eventManager, GameMode gameMode) { // --- ADD PARAMS ---        Gdx.app.log("PlayerMovement", "Attempting to move " + direction + " from (" + (int)position.x + "," + (int)position.y + ")");
        Gdx.app.log("PlayerMovement", "Attempting to move " + direction + " from (" + (int)position.x + "," + (int)position.y + ")");

        int currentX = (int) position.x;
        int currentY = (int) position.y;

        // --- NEW CHUNK TRANSITION CHECK ---
        int nextX = currentX + (int)direction.getVector().x;
        int nextY = currentY + (int)direction.getVector().y;
        GridPoint2 nextTile = new GridPoint2(nextX, nextY); // <-- NEW

        Object nextObject = maze.getGameObjectAt(nextX, nextY); // This checks gates and doors

        if (nextObject instanceof Gate && gameMode == GameMode.ADVANCED) {
            Gate gate = (Gate) nextObject;
            if (gate.isChunkTransitionGate() && gate.getState() == Gate.GateState.OPEN) {
                Gdx.app.log("PlayerMovement", "Stepped onto open transition gate. Firing event.");
                // Fire the event. GameScreen will handle the "teleport".
                eventManager.addEvent(new GameEvent(GameEvent.EventType.CHUNK_TRANSITION, gate));
                return; // Stop. Do not move normally.
            }
        }

        // First, check if the immediate path is blocked. This handles walls and closed doors.
        if (maze.isWallBlocking(currentX, currentY, direction)) {
            Gdx.app.log("PlayerMovement", "Movement blocked by a wall or closed door.");
            return;
        }

        // --- NEW: Check for impassable scenery ---
        if (maze.getScenery().containsKey(nextTile)) {
            Scenery s = maze.getScenery().get(nextTile);
            if (s.isImpassable()) {
                Gdx.app.log("PlayerMovement", "Movement blocked by impassable scenery: " + s.getType());
                return;
            }
        }
        Object doorObject = maze.getGameObjectAt(nextX, nextY);

        if (doorObject instanceof Door) {
            Gdx.app.log("Player", "Move - nextObject = door");

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
            // Standard movement for non-door, non-gate tiles.
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

    public void interact(Maze maze, GameEventManager eventManager, SoundManager soundManager, GameMode gameMode, WorldManager worldManager) { // <-- Added gameMode parameter
        int targetX = (int) (position.x + facing.getVector().x);
        Gdx.app.log("Player", "Interact method: targetX = " + targetX);

        int targetY = (int) (position.y + facing.getVector().y);
        Gdx.app.log("Player", "Interact method: targetY = " + targetY);
        GridPoint2 targetTile = new GridPoint2(targetX, targetY);
        Gdx.app.log("Interaction", "Player interacting with tile (" + targetX + ", " + targetY + ") in mode " + gameMode);

        // --- Gate Interaction Logic ---
        Object gateObj = maze.getGates().get(targetTile); // Check gates map

        if (gateObj instanceof Gate) {
            Gate gate = (Gate) gateObj;

            if (gameMode == GameMode.ADVANCED && gate.isChunkTransitionGate()) {
                // In ADVANCED mode, just open the gate.
                if (gate.getState() == Gate.GateState.CLOSED) {
                    gate.startOpening(worldManager);
                    eventManager.addEvent(new GameEvent("The gate rumbles and opens...", 2f));
                    if (soundManager != null) soundManager.playDoorOpenSound(); // Re-use door sound for now
                }
            } else {
                // In CLASSIC mode, OR if it's not a transition gate, use old logic
                Gdx.app.log("Interaction", "Classic Gate detected. Performing stat jumble/teleport.");
                useGate(maze, eventManager, gate); // Pass the specific gate
            }
            return; // Interaction handled
        }

        // --- Door Interaction Logic ---
        Object obj = maze.getGameObjectAt(targetX, targetY);
        if (obj instanceof Door) {
            Door door = (Door) obj;
            if (door.getState() == Door.DoorState.CLOSED) {
                door.startOpening();
                eventManager.addEvent(new GameEvent("You opened the door.", 2f));
                if (soundManager != null) soundManager.playDoorOpenSound(); // Added null check
            }
            return; // Interaction handled
        }

        // --- Container Interaction Logic ---
        Item itemInFront = maze.getItems().get(targetTile);
        if (itemInFront != null && itemInFront.getCategory() == Item.ItemCategory.CONTAINER) {
            // --- MODIFIED: Use getDisplayName ---
            String containerName = itemInFront.getDisplayName();

            if (itemInFront.isLocked()) {
                Item key = findKey(); // Use helper to find key
                if (key != null && itemInFront.unlocks(key)) { // Check if key unlocks this container
                    itemInFront.unlock();
                    consumeKey(key); // Consume the specific key used
                    eventManager.addEvent(new GameEvent("You unlocked the " + containerName + "!", 2f));
                } else {
                    eventManager.addEvent(new GameEvent("The " + containerName + " is locked.", 2f));
                    return; // Locked and no/wrong key
                }
            }

            // Container is unlocked (or was just unlocked)
            List<Item> contents = new ArrayList<>(itemInFront.getContents());
            maze.getItems().remove(targetTile); // Remove the container itself

            if (contents.isEmpty()) {
                eventManager.addEvent(new GameEvent("The " + containerName + " is empty.", 2f));
            } else {
                eventManager.addEvent(new GameEvent("You open the " + containerName + ".", 2f));
                for (Item contentItem : contents) {
                    // Try to place on the container's tile, fallback to player tile if occupied
                    GridPoint2 dropTile = targetTile;
                    if (maze.getItems().containsKey(dropTile)) {
                        dropTile = new GridPoint2((int)position.x, (int)position.y);
                    }
                    // Avoid overwriting if player tile is also occupied
                    if (!maze.getItems().containsKey(dropTile)) {
                        contentItem.getPosition().set(dropTile.x + 0.5f, dropTile.y + 0.5f);
                        maze.addItem(contentItem);
                        Gdx.app.log("Interaction", "Dropped " + contentItem.getDisplayName() + " at " + dropTile);
                    } else {
                        eventManager.addEvent(new GameEvent("No space to drop " + contentItem.getDisplayName() + ".", 2f));
                        Gdx.app.log("Interaction", "Failed to drop " + contentItem.getDisplayName() + ", tile " + dropTile + " occupied.");
                        // Item remains 'in limbo' - ideally add back to inventory or handle differently
                    }
                }
            }
            // --- END MODIFIED ---
            return; // Interaction handled
        }

        // --- No Interaction ---
        eventManager.addEvent(new GameEvent("Nothing to interact with here.", 2f));
    }

    /**
     * Finds the first available key in the player's inventory (hands first, then backpack).
     * @return The found Item (key), or null if no key is held.
     */
    private Item findKey() {
        Inventory inv = getInventory();
        if (inv.getRightHand() != null && inv.getRightHand().getType() == Item.ItemType.KEY) {
            return inv.getRightHand();
        }
        if (inv.getLeftHand() != null && inv.getLeftHand().getType() == Item.ItemType.KEY) {
            return inv.getLeftHand();
        }
        for (Item item : inv.getBackpack()) {
            if (item != null && item.getType() == Item.ItemType.KEY) {
                return item;
            }
        }
        return null;
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
                // --- MODIFIED: Use stats object ---
                int temp = stats.getWarStrength();
                setWarStrength(stats.getSpiritualStrength());
                stats.setSpiritualStrength(temp);
                Gdx.app.log("Player", "Gate swapped stats!");
                break;
            case 2: // Reduce War Strength
                // --- MODIFIED: Use stats object ---
                setWarStrength((int)(stats.getWarStrength() * 0.75f)); // Reduce by 25%
                Gdx.app.log("Player", "Gate reduced War Strength!");
                break;
            case 3: // Reduce Spiritual Strength
                // --- MODIFIED: Use stats object ---
                stats.setSpiritualStrength((int)(stats.getSpiritualStrength() * 0.75f)); // Reduce by 25%
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

    /**
     * Consumes a specific key item from the player's inventory.
     * @param keyToRemove The specific key item to remove.
     */
    private void consumeKey(Item keyToRemove) {
        Inventory inv = getInventory();
        if (inv.getRightHand() == keyToRemove) {
            inv.setRightHand(null);
            return;
        }
        if (inv.getLeftHand() == keyToRemove) {
            inv.setLeftHand(null);
            return;
        }
        for (int i = 0; i < inv.getBackpack().length; i++) {
            if (inv.getBackpack()[i] == keyToRemove) {
                inv.getBackpack()[i] = null;
                return;
            }
        }
    }

    /**
     * Handles the original gate logic (stat jumble, teleport within chunk).
     * Called in CLASSIC mode or for non-transition gates in ADVANCED mode.
     * @param maze The current maze.
     * @param eventManager For displaying messages.
     * @param gate The specific gate being used.
     */
    private void useGate(Maze maze, GameEventManager eventManager, Gate gate) {
        Gdx.app.log("Player", "Player used a non-transition gate at (" + (int)gate.getPosition().x + "," + (int)gate.getPosition().y + ")");
        eventManager.addEvent(new GameEvent("You touch the strange mural...", 2f));

        // Stat Jumbling Logic
        int outcome = new Random().nextInt(4); // 4 possible outcomes
        switch (outcome) {
            case 1: // Swap stats
                // --- MODIFIED: Use stats object ---
                int temp = stats.getWarStrength();
                setWarStrength(stats.getSpiritualStrength()); // Use setter to respect max
                stats.setSpiritualStrength(Math.min(temp, stats.getMaxSpiritualStrength())); // Respect max
                Gdx.app.log("Player", "Gate swapped stats! WS=" + stats.getWarStrength() + ", SS=" + stats.getSpiritualStrength());
                eventManager.addEvent(new GameEvent("Your strengths feel reversed!", 2f));
                break;
            case 2: // Reduce War Strength
                // --- MODIFIED: Use stats object ---
                setWarStrength((int)(stats.getWarStrength() * 0.75f)); // Reduce by 25%
                Gdx.app.log("Player", "Gate reduced War Strength to " + stats.getWarStrength());
                eventManager.addEvent(new GameEvent("You feel weaker!", 2f));
                break;
            case 3: // Reduce Spiritual Strength
                // --- MODIFIED: Use stats object ---
                stats.setSpiritualStrength(Math.min((int)(stats.getSpiritualStrength() * 0.75f), stats.getMaxSpiritualStrength())); // Reduce by 25%
                Gdx.app.log("Player", "Gate reduced Spiritual Strength to " + stats.getSpiritualStrength());
                eventManager.addEvent(new GameEvent("Your spirit feels drained!", 2f));
                break;
            default: // No change
                Gdx.app.log("Player", "Gate had no effect on stats.");
                eventManager.addEvent(new GameEvent("Nothing seems to happen.", 2f));
                break;
        }

        // Teleport Logic (within the current chunk)
        List<GridPoint2> emptyTiles = new ArrayList<>();
        for (int y = 0; y < maze.getHeight(); y++) {
            for (int x = 0; x < maze.getWidth(); x++) {
                // Check for floor tile, no object, no item, no monster
                if (maze.getWallDataAt(x, y) == 0
                    && maze.getGameObjectAt(x, y) == null
                    && !maze.getItems().containsKey(new GridPoint2(x, y))
                    && !maze.getMonsters().containsKey(new GridPoint2(x, y)))
                {
                    emptyTiles.add(new GridPoint2(x, y));
                }
            }
        }

        if (!emptyTiles.isEmpty()) {
            GridPoint2 currentPos = new GridPoint2((int)position.x, (int)position.y);
            GridPoint2 newPos;
            int attempts = 0;
            // Try to find a *different* empty tile
            do {
                newPos = emptyTiles.get(new Random().nextInt(emptyTiles.size()));
                attempts++;
            } while (newPos.equals(currentPos) && attempts < 10 && emptyTiles.size() > 1); // Avoid infinite loop if only one empty tile

            position.set(newPos.x + 0.5f, newPos.y + 0.5f);
            Gdx.app.log("Player", "Teleported within chunk to (" + newPos.x + ", " + newPos.y + ")");
            eventManager.addEvent(new GameEvent("Space warps around you!", 2f));
        } else {
            Gdx.app.log("Player", "Teleport failed - no valid empty tiles found.");
            eventManager.addEvent(new GameEvent("The mural shimmers weakly.", 2f));
        }
    }

    // --- METHODS MOVED TO PlayerStats ---
    // public void addArrows(int amount) { ... }
    // public void addFood(int amount) { ... }
    // public void decrementArrow() { ... }

    // --- Experience and Leveling Logic ---

    // --- METHOD MOVED TO PlayerStats ---
    // private int calculateXpForLevel(int targetLevel) { ... }

    public void addExperience(int amount, GameEventManager eventManager) {
        if (amount <= 0) return;

        // --- MODIFIED: Delegate to PlayerStats ---
        boolean leveledUp = stats.addExperience(amount);
        // --- END MODIFIED ---

        Gdx.app.log("Player", "Gained " + amount + " XP. Total: " + stats.getExperience() + "/" + stats.getExperienceToNextLevel());
        eventManager.addEvent(new GameEvent("You gained " + amount + " experience!", 2f));

        // --- MODIFIED: Check boolean returned from stats.addExperience ---
        if (leveledUp) {
            levelUp(eventManager); // Call private helper to handle effects
        }
    }

    // --- MODIFIED: This method now only handles the *effects* of leveling up ---
    private void levelUp(GameEventManager eventManager) {
        soundManager.playPlayerLevelUpSound();

        // Stat increases and healing are already handled inside PlayerStats.levelUp()
        // We just log and create the event.

        Gdx.app.log("Player", "Leveled up to level " + stats.getLevel() + "!");
        eventManager.addEvent(new GameEvent("You reached level " + stats.getLevel() + "!", 3f));
    }

    /**
     * Attack modifier based on player level.
     * @return The bonus damage to add to attacks.
     */
    public int getAttackModifier() {
        // --- MODIFIED: Delegate to PlayerStats ---
        return stats.getAttackModifier();
    }

    // --- MODIFIED: All getters now delegate to the stats object ---

    public int getLevel() { return stats.getLevel(); }
    public int getExperience() { return stats.getExperience(); }
    public int getExperienceToNextLevel() { return stats.getExperienceToNextLevel(); }
    public int getTreasureScore() { return stats.getTreasureScore(); }
    public Vector2 getPosition() { return position; }
    public Direction getFacing() { return facing; }
    public Vector2 getDirectionVector() { return directionVector; }
    public Vector2 getCameraPlane() { return cameraPlane; }
    public int getWarStrength() { return stats.getWarStrength(); }
    public int getSpiritualStrength() { return stats.getSpiritualStrength(); }
    public int getFood() { return stats.getFood(); }
    public int getArrows() { return stats.getArrows(); }
    public Inventory getInventory() { return inventory; }

    public void addArrows(int amount) {
        stats.addArrows(amount);
    }

    public void addFood(int amount) {
        stats.addFood(amount);
    }

    public void decrementArrow() {
        stats.decrementArrow();
    }
}
