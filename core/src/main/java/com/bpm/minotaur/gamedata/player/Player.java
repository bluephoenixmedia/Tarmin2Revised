package com.bpm.minotaur.gamedata.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.*;
import com.bpm.minotaur.managers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Player {

    // --- Stats ---
    private final PlayerStats stats;

    // --- Equipment ---
    private final PlayerEquipment equipment = new PlayerEquipment();

    private final StatusManager statusManager;

    // --- Position and Movement ---
    private final Vector2 position;
    private Direction facing;
    private final Vector2 directionVector;
    private final Vector2 cameraPlane;

    // --- Inventory ---
    private final Inventory inventory = new Inventory();

    // --- Managers ---
    private final SoundManager soundManager;
    private final ItemDataManager itemDataManager;
    private final AssetManager assetManager;


    public Player(float startX, float startY, Difficulty difficulty,
                  ItemDataManager itemDataManager, AssetManager assetManager) {

        this.position = new Vector2(startX + 0.5f, startY + 0.5f);
        this.facing = Direction.NORTH;
        this.directionVector = new Vector2();
        this.cameraPlane = new Vector2();
        this.soundManager = new SoundManager(null);
        this.itemDataManager = itemDataManager;
        this.assetManager = assetManager;

        Gdx.app.log("Player [DEBUG]", "Constructor: Creating starting items...");
        updateVectors();

        this.stats = new PlayerStats(difficulty);
        this.statusManager = new StatusManager();

        inventory.setRightHand(
            itemDataManager.createItem(Item.ItemType.KNIFE, 0, 0, ItemColor.TAN, assetManager)
        );

        equipment.setWornBack(
            itemDataManager.createItem(Item.ItemType.MEDIUM_PACK, 0, 0, ItemColor.GRAY, assetManager)
        );

        Gdx.app.log("Player [DEBUG]", "Constructor: Finished creating items.");
    }

    public void setPosition(GridPoint2 newPos) {
        this.position.set(newPos.x + 0.5f, newPos.y + 0.5f);
    }

    public boolean pickupItem(Item item) {
        return inventory.pickup(item);
    }

    public void interactWithItem(Maze maze, GameEventManager eventManager, SoundManager soundManager) {
        int playerGridX = (int) position.x;
        int playerGridY = (int) position.y;
        GridPoint2 playerTile2 = new GridPoint2(playerGridX, playerGridY);

        // 1. Check item at feet first
        Item itemAtFeet = maze.getItems().get(playerTile2);

        if (itemAtFeet != null) {
            if (pickupItem(itemAtFeet)) {
                maze.getItems().remove(playerTile2);
                soundManager.playPickupItemSound();
                eventManager.addEvent(new GameEvent("Picked up " + itemAtFeet.getDisplayName(), 2f));

                // --- LOGGING ---
                BalanceLogger.getInstance().logEconomy("PICKUP", itemAtFeet.getDisplayName(), itemAtFeet.getBaseValue());
                // ---------------
            } else {
                eventManager.addEvent(new GameEvent("Inventory is full.", 2f));
            }
            return;
        }

        // 2. Check item in front
        int targetX = (int) (position.x + facing.getVector().x);
        int targetY = (int) (position.y + facing.getVector().y);
        GridPoint2 targetTile = new GridPoint2(targetX, targetY);
        Item itemInFront = maze.getItems().get(targetTile);

        if (itemInFront != null) {

            if (itemInFront.getCategory() == ItemCategory.TREASURE) {
                soundManager.playPickupItemSound();
                stats.incrementTreasureScore(itemInFront.getBaseValue());
                maze.getItems().remove(targetTile);
                eventManager.addEvent(new GameEvent("You found " + itemInFront.getDisplayName() + "!", 2f));

                // --- LOGGING ---
                BalanceLogger.getInstance().logEconomy("TREASURE", itemInFront.getDisplayName(), itemInFront.getBaseValue());
                // ---------------
                return;
            }
            if (itemInFront.getType() == Item.ItemType.QUIVER) {
                soundManager.playPickupItemSound();
                int arrowsFound = new Random().nextInt(4) + 6;
                stats.addArrows(arrowsFound);
                maze.getItems().remove(targetTile);
                eventManager.addEvent(new GameEvent("You found " + arrowsFound + " arrows.", 2f));

                // --- LOGGING ---
                BalanceLogger.getInstance().logEconomy("RES_GAIN", "Arrows", arrowsFound);
                // ---------------
                return;
            }
            if (itemInFront.getType() == Item.ItemType.FLOUR_SACK) {
                soundManager.playPickupItemSound();
                int foodFound = new Random().nextInt(4) + 6;
                stats.addFood(foodFound);
                maze.getItems().remove(targetTile);
                eventManager.addEvent(new GameEvent("You found " + foodFound + " food.", 2f));

                // --- LOGGING ---
                BalanceLogger.getInstance().logEconomy("RES_GAIN", "Food", foodFound);
                // ---------------
                return;
            }

            if (pickupItem(itemInFront)) {
                maze.getItems().remove(targetTile);
                soundManager.playPickupItemSound();
                eventManager.addEvent(new GameEvent("Picked up " + itemInFront.getDisplayName(), 2f));

                // --- LOGGING ---
                BalanceLogger.getInstance().logEconomy("PICKUP", itemInFront.getDisplayName(), itemInFront.getBaseValue());
                // ---------------
            } else {
                eventManager.addEvent(new GameEvent("Inventory is full.", 2f));
            }
        }
        else if (inventory.getRightHand() != null) {
            GridPoint2 playerTile = new GridPoint2((int)position.x, (int)position.y);
            if (!maze.getItems().containsKey(playerTile)) {
                Item itemInHand = inventory.getRightHand();
                itemInHand.getPosition().set(playerTile.x + 0.5f, playerTile.y + 0.5f);
                maze.addItem(itemInHand);
                inventory.setRightHand(null);
                eventManager.addEvent(new GameEvent("Dropped " + itemInHand.getDisplayName(), 2f));
            } else {
                eventManager.addEvent(new GameEvent("No space to drop here.", 2f));
            }
        }
        else {
            eventManager.addEvent(new GameEvent("Nothing to interact with.", 2f));
        }
    }

    public void useItem(GameEventManager eventManager, PotionManager potionManager) {
        Item itemInHand = inventory.getRightHand();

        if (itemInHand == null) {
            eventManager.addEvent(new GameEvent("You have nothing in hand.", 2f));
            return;
        }

        // --- NEW: Handle Food Eating ---
        if (itemInHand.isFood()) {
            // Basic food value
            stats.addFood(5);
            inventory.setRightHand(null); // Consume it
            eventManager.addEvent(new GameEvent("You ate the " + itemInHand.getDisplayName() + ".", 2f));
            soundManager.playPickupItemSound(); // Re-using pickup sound for eating for now

            // Log the meal
            BalanceLogger.getInstance().logEconomy("RES_GAIN", "Food (Item)", 5);
            return;
        }
        // -------------------------------

        if (!itemInHand.isUsable() && !itemInHand.isPotion() && !itemInHand.isArmor() && !itemInHand.isRing()) {
            eventManager.addEvent(new GameEvent("You can't use that.", 2f));
            return;
        }

        if (itemInHand.isPotion()) {
            potionManager.consumePotion(this, itemInHand);
            inventory.setRightHand(null);

            // --- LOGGING ---
            BalanceLogger.getInstance().logEconomy("RES_USED", "Potion", 1);
            // ---------------
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

    private void equipRing(Item ring, GameEventManager eventManager) {
        if (ring.getCategory() != ItemCategory.RING) return;

        eventManager.addEvent(new GameEvent("Equipped " + ring.getDisplayName(), 2f));
        Item previouslyWornRing = this.equipment.getWornRing();
        this.equipment.setWornRing(ring);
        inventory.setRightHand(previouslyWornRing);
    }

    // --- REPLACED: Flexible Equip Logic (Allows Swapping) ---
    private void equipArmor(Item armor, GameEventManager eventManager) {
        Item previousItem = null;
        String slotName = "";

        switch (armor.getType()) {
            case HELMET:
                previousItem = equipment.getWornHelmet();
                equipment.setWornHelmet(armor);
                slotName = "Head";
                break;
            case SMALL_SHIELD:
            case LARGE_SHIELD:
                previousItem = equipment.getWornShield();
                equipment.setWornShield(armor);
                slotName = "Off-hand";
                break;
            case GAUNTLETS:
                previousItem = equipment.getWornGauntlets();
                equipment.setWornGauntlets(armor);
                slotName = "Hands";
                break;
            case HAUBERK:
            case BREASTPLATE:
                previousItem = equipment.getWornChest();
                equipment.setWornChest(armor);
                slotName = "Chest";
                break;
            case BOOTS:
                previousItem = equipment.getWornBoots();
                equipment.setWornBoots(armor);
                slotName = "Feet";
                break;
            case LEGS:
                previousItem = equipment.getWornLegs();
                equipment.setWornLegs(armor);
                slotName = "Legs";
                break;
            case ARMS:
                previousItem = equipment.getWornArms();
                equipment.setWornArms(armor);
                slotName = "Arms";
                break;
            case EYES:
                previousItem = equipment.getWornEyes();
                equipment.setWornEyes(armor);
                slotName = "Eyes";
                break;
            case CLOAK:
                previousItem = equipment.getWornBack();
                equipment.setWornBack(armor);
                slotName = "Back";
                break;
            case AMULET:
                previousItem = equipment.getWornNeck();
                equipment.setWornNeck(armor);
                slotName = "Neck";
                break;
            default:
                eventManager.addEvent(new GameEvent("Cannot equip this.", 2f));
                return;
        }

        // Put the new item in the slot, and put the old item (if any) in the hand
        inventory.setRightHand(previousItem);

        eventManager.addEvent(new GameEvent("Equipped " + armor.getDisplayName() + " to " + slotName, 2f));

        // --- NEW: Recalculate Stats Immediately ---
        // This ensures that if you equip "of Brawn", your Max HP updates.
        // Optional: If you want "Brawn" to heal you for the difference, do this:
        int oldMaxWar = stats.getMaxWarStrength(); // Base max
        // Note: getEffectiveMax... calculates total with gear.

        // Log the change
        int newDefense = equipment.getArmorDefense();
        int newMaxHP = getEffectiveMaxWarStrength();

        BalanceLogger.getInstance().log("EQUIPMENT", "Changed " + slotName + ". AC: " + newDefense + " MaxHP: " + newMaxHP);
        // ------------------------------------------
    }

    private void useConsumable(Item item, GameEventManager eventManager) {
        switch (item.getType()) {
            case WAR_BOOK:
                stats.setMaxWarStrength(stats.getMaxWarStrength() + 10);
                this.setWarStrength(this.getEffectiveMaxWarStrength());
                inventory.setRightHand(null);
                eventManager.addEvent(new GameEvent("Your knowledge of war grows.", 2f));
                break;
            case SPIRITUAL_BOOK:
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
        cameraPlane.set(-directionVector.y, directionVector.x).scl(0.66f);
    }

    public void rest(GameEventManager eventManager) {
        if (stats.getFood() > 0) {
            stats.setFood(stats.getFood() - 1);

            int warStrengthGained = 5;
            int spiritualStrengthGained = 5;

            this.setWarStrength(Math.min(this.getEffectiveMaxWarStrength(), this.getWarStrength() + warStrengthGained));
            stats.setSpiritualStrength(Math.min(this.getEffectiveMaxSpiritualStrength(), stats.getSpiritualStrength() + spiritualStrengthGained));

            eventManager.addEvent(new GameEvent(("WS restored to " + stats.getWarStrength() + ", SS restored to " + stats.getSpiritualStrength()), 2f));

            // --- LOGGING ---
            BalanceLogger.getInstance().logEconomy("RES_USED", "Food", 1);
            // ---------------

        } else {
            eventManager.addEvent(new GameEvent("You have no food to rest.", 2f));
        }
    }

    public void setFacing(Direction facing) {
        this.facing = facing;
        updateVectors();
    }

    public int takeDamage(int amount, DamageType type) {
        if (type == DamageType.PHYSICAL) {
            int defense = getArmorDefense();
            amount = Math.max(0, amount - defense);
        }

        // --- CHIP DAMAGE FIX: Always take at least 1 damage ---
        int minDamage = 1;
        amount = Math.max(minDamage, amount);
        // ----------------------------------------------------

        int finalDamage = Math.max(0, (int)(amount * stats.getVulnerabilityMultiplier()));
        stats.setWarStrength(stats.getWarStrength() - finalDamage);

        return finalDamage;
    }

    public void takeSpiritualDamage(int amount, DamageType type) {
        int damageReduction = equipment.getRingDefense();
        int resistance = getResistance(type);

        int finalDamage = (int)(amount * stats.getVulnerabilityMultiplier());
        finalDamage = Math.max(0, finalDamage - damageReduction - resistance);

        stats.setSpiritualStrength(stats.getSpiritualStrength() - finalDamage);

        if (stats.getSpiritualStrength() < 0) {
            stats.setSpiritualStrength(0);
        }
    }

    public int getArmorDefense() {
        return equipment.getArmorDefense();
    }

    public int getEffectiveMaxWarStrength() {
        return stats.getMaxWarStrength() + equipment.getEquippedModifierSum(ModifierType.BONUS_WAR_STRENGTH);
    }

    public int getEffectiveMaxSpiritualStrength() {
        return stats.getMaxSpiritualStrength() + equipment.getEquippedModifierSum(ModifierType.BONUS_SPIRITUAL_STRENGTH);
    }

    public Item getWornRing() {
        return equipment.getWornRing();
    }

    public void setWarStrength(int amount) {
        stats.setWarStrength(Math.min(amount, this.getEffectiveMaxWarStrength()));
    }

    public void setMaxWarStrength(int amount) {
        stats.setMaxWarStrength(amount);
    }


    private int getRingDefense() {
        return equipment.getRingDefense();
    }

    private int getResistance(DamageType type) {
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
            default: return 0;
        }

        return equipment.getEquippedModifierSum(modTypeToFind);
    }

    public void moveForward(Maze maze, GameEventManager eventManager, GameMode gameMode) {
        move(facing, maze, eventManager, gameMode);
    }

    public void moveBackward(Maze maze, GameEventManager eventManager, GameMode gameMode) {
        move(facing.getOpposite(), maze, eventManager, gameMode);
    }

    private void move(Direction direction, Maze maze, GameEventManager eventManager, GameMode gameMode) {
        int currentX = (int) position.x;
        int currentY = (int) position.y;

        int nextX = currentX + (int)direction.getVector().x;
        int nextY = currentY + (int)direction.getVector().y;
        GridPoint2 nextTile = new GridPoint2(nextX, nextY);

        Object nextObject = maze.getGameObjectAt(nextX, nextY);

        if (nextObject instanceof Gate gate && gameMode == GameMode.ADVANCED) {
            if (gate.isChunkTransitionGate() && gate.getState() == Gate.GateState.OPEN) {
                eventManager.addEvent(new GameEvent(GameEvent.EventType.CHUNK_TRANSITION, gate));
                return;
            }
        }

        // --- MOVEMENT DEBUGGING ---
        if (!maze.isPassable(nextX, nextY)) {
            // Check specific reasons for blockage
            if (maze.getWallDataAt(nextX, nextY) == 1) {
                Gdx.app.log("Player [DEBUG]", "Move blocked by WALL data at (" + nextX + "," + nextY + ")");
            }
            Item item = maze.getItems().get(nextTile);
            if (item != null && item.isImpassable()) {
                Gdx.app.log("Player [DEBUG]", "Move blocked by IMPASSABLE ITEM: " + item.getDisplayName() + " at (" + nextX + "," + nextY + ")");
            }
            // Check for doors/gates
            Object obj = maze.getGameObjectAt(nextX, nextY);
            if (obj instanceof Door && ((Door) obj).getState() != Door.DoorState.OPEN) {
                Gdx.app.log("Player [DEBUG]", "Move blocked by CLOSED DOOR at (" + nextX + "," + nextY + ")");
            }
            return;
        }

        if (maze.isWallBlocking(currentX, currentY, direction)) {
            Gdx.app.log("Player [DEBUG]", "Move blocked by WALL MASK from (" + currentX + "," + currentY + ") facing " + direction);
            return;
        }

        if (maze.getScenery().containsKey(nextTile)) {
            Scenery s = maze.getScenery().get(nextTile);
            if (s.isImpassable()) {
                Gdx.app.log("Player [DEBUG]", "Move blocked by SCENERY at (" + nextX + "," + nextY + ")");
                return;
            }
        }

        Object doorObject = maze.getGameObjectAt(nextX, nextY);

        if (doorObject instanceof Door) {
            int finalX = nextX + (int)direction.getVector().x;
            int finalY = nextY + (int)direction.getVector().y;

            if (!maze.isWallBlocking(nextX, nextY, direction)) {
                position.set(finalX + 0.5f, finalY + 0.5f);
                UnlockManager.getInstance().incrementStat("steps", 1);
            } else {
                Gdx.app.log("Player [DEBUG]", "Move into door blocked by wall behind it.");
            }
        } else {
            position.set(nextX + 0.5f, nextY + 0.5f);
            UnlockManager.getInstance().incrementStat("steps", 1);
        }
    }

    public void turnLeft() {
        facing = facing.getLeft();
        updateVectors();
    }

    public void turnRight() {
        facing = facing.getRight();
        updateVectors();
    }

    public void interact(Maze maze, GameEventManager eventManager, SoundManager soundManager, GameMode gameMode, WorldManager worldManager) {
        int targetX = (int) (position.x + facing.getVector().x);
        int targetY = (int) (position.y + facing.getVector().y);
        GridPoint2 targetTile = new GridPoint2(targetX, targetY);

        // 1. Handle Gates (Keep existing logic)
        Gate gateObj = maze.getGates().get(targetTile);
        if (gateObj != null) {
            if (gameMode == GameMode.ADVANCED && gateObj.isChunkTransitionGate()) {
                if (gateObj.getState() == Gate.GateState.CLOSED) {
                    gateObj.startOpening(worldManager);
                    eventManager.addEvent(new GameEvent("The gate rumbles and opens...", 2f));
                    if (soundManager != null) soundManager.playDoorOpenSound();
                }
            } else {
                useGate(maze, eventManager, gateObj);
            }
            return;
        }

        // 2. Handle Doors (UPDATED: Toggle Logic)
        Object obj = maze.getGameObjectAt(targetX, targetY);
        if (obj instanceof Door) {
            Door door = (Door) obj;
            // Use the new toggle method from Milestone 1
            maze.toggleDoorAt(targetX, targetY);

            // Check state AFTER toggle to determine event/sound
            if (door.getState() == Door.DoorState.OPENING) {
                eventManager.addEvent(new GameEvent("You open the door.", 1f));
                UnlockManager.getInstance().incrementStat("doors", 1);
                if (soundManager != null) soundManager.playDoorOpenSound();
            } else if (door.getState() == Door.DoorState.CLOSING) {
                eventManager.addEvent(new GameEvent("You close the door.", 1f));
                // Reuse open sound for now, or add specific close sound later
                if (soundManager != null) soundManager.playDoorOpenSound();
            }
            return;
        }

        // 3. Handle Containers (Keep existing logic)
        Item itemInFront = maze.getItems().get(targetTile);
        if (itemInFront != null && itemInFront.getCategory() == ItemCategory.CONTAINER) {
            String containerName = itemInFront.getDisplayName();

            if (itemInFront.isLocked()) {
                Item key = findKey();
                if (key != null && itemInFront.unlocks(key)) {
                    itemInFront.unlock();
                    consumeKey(key);
                    eventManager.addEvent(new GameEvent("You unlocked the " + containerName + "!", 2f));
                } else {
                    eventManager.addEvent(new GameEvent("The " + containerName + " is locked.", 2f));
                    return;
                }
            }

            List<Item> contents = new ArrayList<>(itemInFront.getContents());
            maze.getItems().remove(targetTile);

            if (contents.isEmpty()) {
                eventManager.addEvent(new GameEvent("The " + containerName + " is empty.", 2f));
            } else {
                eventManager.addEvent(new GameEvent("You open the " + containerName + ".", 2f));
                for (Item contentItem : contents) {
                    GridPoint2 dropTile = targetTile;
                    if (maze.getItems().containsKey(dropTile)) {
                        dropTile = new GridPoint2((int)position.x, (int)position.y);
                    }
                    if (!maze.getItems().containsKey(dropTile)) {
                        contentItem.getPosition().set(dropTile.x + 0.5f, dropTile.y + 0.5f);
                        maze.addItem(contentItem);
                    } else {
                        eventManager.addEvent(new GameEvent("No space to drop " + contentItem.getDisplayName() + ".", 2f));
                    }
                }
            }
            return;
        }

        eventManager.addEvent(new GameEvent("Nothing to interact with here.", 2f));
    }

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
        for (Item item : inv.getMainInventory()) {
            if (item.getType() == Item.ItemType.KEY) {
                return item;
            }
        }
        return null;
    }

    public int getDexterity() {
        return stats.getDexterity();
    }

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
        inv.getMainInventory().remove(keyToRemove);
    }

    private void useGate(Maze maze, GameEventManager eventManager, Gate gate) {
        eventManager.addEvent(new GameEvent("You touch the strange mural...", 2f));

        int outcome = new Random().nextInt(4);
        switch (outcome) {
            case 1:
                int temp = stats.getWarStrength();
                setWarStrength(stats.getSpiritualStrength());
                stats.setSpiritualStrength(Math.min(temp, stats.getMaxSpiritualStrength()));
                eventManager.addEvent(new GameEvent("Your strengths feel reversed!", 2f));
                break;
            case 2:
                setWarStrength((int)(stats.getWarStrength() * 0.75f));
                eventManager.addEvent(new GameEvent("You feel weaker!", 2f));
                break;
            case 3:
                stats.setSpiritualStrength(Math.min((int)(stats.getSpiritualStrength() * 0.75f), stats.getMaxSpiritualStrength()));
                eventManager.addEvent(new GameEvent("Your spirit feels drained!", 2f));
                break;
            default:
                eventManager.addEvent(new GameEvent("Nothing seems to happen.", 2f));
                break;
        }

        List<GridPoint2> emptyTiles = new ArrayList<>();
        for (int y = 0; y < maze.getHeight(); y++) {
            for (int x = 0; x < maze.getWidth(); x++) {
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
            do {
                newPos = emptyTiles.get(new Random().nextInt(emptyTiles.size()));
                attempts++;
            } while (newPos.equals(currentPos) && attempts < 10 && emptyTiles.size() > 1);

            position.set(newPos.x + 0.5f, newPos.y + 0.5f);
            eventManager.addEvent(new GameEvent("Space warps around you!", 2f));
        } else {
            eventManager.addEvent(new GameEvent("The mural shimmers weakly.", 2f));
        }
    }


    public void addArrows(int amount) {
        stats.addArrows(amount);
    }

    public void addFood(int amount) {
        stats.addFood(amount);
    }

    public void decrementArrow() {
        stats.decrementArrow();
        // --- LOGGING ---
        BalanceLogger.getInstance().logEconomy("RES_USED", "Arrow", 1);
        // ---------------
    }

    public void addExperience(int amount, GameEventManager eventManager) {
        if (amount <= 0) return;

        boolean leveledUp = stats.addExperience(amount);

        eventManager.addEvent(new GameEvent("You gained " + amount + " experience!", 2f));

        if (leveledUp) {
            levelUp(eventManager);
        }
    }

    private void levelUp(GameEventManager eventManager) {
        soundManager.playPlayerLevelUpSound();
        eventManager.addEvent(new GameEvent("You reached level " + stats.getLevel() + "!", 3f));
    }

    public void takeStatusEffectDamage(int amount, DamageType type) {
        int resistance = getResistance(type);

        int finalDamage = (int)(amount * stats.getVulnerabilityMultiplier());
        finalDamage = Math.max(0, finalDamage - resistance);

        stats.setWarStrength(stats.getWarStrength() - finalDamage);

        if (stats.getWarStrength() < 0) {
            stats.setWarStrength(0);
        }
    }

    /**
     * UPDATED: Returns attack modifier including LEVEL BONUS and EQUIPMENT BONUSES.
     * Use this for all combat calculations.
     */
    public int getAttackModifier() {
        // Level Bonus + All "BONUS_DAMAGE" on equipped items
        return stats.getAttackModifier() + equipment.getEquippedModifierSum(ModifierType.BONUS_DAMAGE);
    }

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

    public PlayerEquipment getEquipment() {
        return equipment;
    }

    public StatusManager getStatusManager() {
        return statusManager;
    }

    public void setMaze(Maze newMaze) {
    }

    public PlayerStats getStats() {
        return this.stats;
    }

    /**
     * Drops an item. Tries Feet -> Front -> Adjacent tiles.
     * @return true if successfully dropped, false if no space.
     */
    public boolean dropItem(Maze maze, Item item) {
        if (item == null) return false;

        GridPoint2 playerTile = new GridPoint2((int)position.x, (int)position.y);

        // 1. Try Feet
        if (!maze.getItems().containsKey(playerTile)) {
            item.getPosition().set(playerTile.x + 0.5f, playerTile.y + 0.5f);
            maze.addItem(item);
            return true;
        }

        // 2. Try Front
        int targetX = (int) (position.x + facing.getVector().x);
        int targetY = (int) (position.y + facing.getVector().y);
        GridPoint2 frontTile = new GridPoint2(targetX, targetY);

        // Ensure we don't drop through a wall
        if (!maze.isWallBlocking((int)position.x, (int)position.y, facing) && !maze.getItems().containsKey(frontTile)) {
            item.getPosition().set(frontTile.x + 0.5f, frontTile.y + 0.5f);
            maze.addItem(item);
            return true;
        }

        // 3. Try Other Directions (Back, Left, Right)
        for (Direction d : Direction.values()) {
            if (d == facing) continue;

            if (!maze.isWallBlocking((int)position.x, (int)position.y, d)) {
                int nx = (int)(position.x + d.getVector().x);
                int ny = (int)(position.y + d.getVector().y);
                GridPoint2 neighborTile = new GridPoint2(nx, ny);

                if (!maze.getItems().containsKey(neighborTile)) {
                    item.getPosition().set(neighborTile.x + 0.5f, neighborTile.y + 0.5f);
                    maze.addItem(item);
                    return true;
                }
            }
        }

        // No space found
        return false;
    }
}
