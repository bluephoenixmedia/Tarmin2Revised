package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Door.DoorState;
import com.bpm.minotaur.gamedata.Gate.GateState;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A serializable "snapshot" of a Maze.
 * This class holds all the persistent data needed to save and restore
 * the state of a game chunk. It is designed to be easily converted
 * to and from JSON.
 */
public class ChunkData {

    // --- Public fields for easy JSON serialization ---
    public int level;
    public int[][] wallData;
    public List<ItemData> items = new ArrayList<>();
    public List<MonsterData> monsters = new ArrayList<>();
    public List<DoorData> doors = new ArrayList<>();
    public List<GateData> gates = new ArrayList<>();
    public List<LadderData> ladders = new ArrayList<>();

    public List<SceneryData> scenery = new ArrayList<>();


    /**
     * No-arg constructor required for JSON deserialization.
     */
    public ChunkData() {}

    /**
     * Creates a new ChunkData snapshot from an existing Maze object.
     * This constructor performs the "Maze -> ChunkData" conversion.
     * @param maze The live Maze object to snapshot.
     */
    public ChunkData(Maze maze) {
        this.level = maze.getLevel();
        this.wallData = maze.getWallData();

        // Snapshot all items on the floor
        for (Map.Entry<GridPoint2, Item> entry : maze.getItems().entrySet()) {
            this.items.add(new ItemData(entry.getValue()));
        }

        // Snapshot all monsters (including their current health)
        for (Map.Entry<GridPoint2, Monster> entry : maze.getMonsters().entrySet()) {
            this.monsters.add(new MonsterData(entry.getValue()));
        }

        // Snapshot all doors and their states
        for (Object obj : maze.getGameObjects().values()) {
            if (obj instanceof Door) {
                this.doors.add(new DoorData((Door) obj));
            }
        }

        // Snapshot all gates and their states
        for (Map.Entry<GridPoint2, Gate> entry : maze.getGates().entrySet()) {
            this.gates.add(new GateData(entry.getValue()));
        }

        // Snapshot all ladders
        for (Map.Entry<GridPoint2, Ladder> entry : maze.getLadders().entrySet()) {
            this.ladders.add(new LadderData(entry.getValue()));
        }
    }

    /**
     * Reconstructs a live Maze object from this data snapshot.
     * This method performs the "ChunkData -> Maze" conversion.
     * @return A new, fully populated Maze object.
     */
    public Maze buildMaze(MonsterDataManager dataManager, AssetManager assetManager) {
        Maze maze = new Maze(this.level, this.wallData);

        for (ItemData data : items) {
            Item item = new Item(data.type, data.x, data.y, data.color);
            if (data.modifiers != null) {
                for (ItemModifier mod : data.modifiers) {
                    item.addModifier(mod);
                }
            }
            maze.addItem(item);
        }

        for (MonsterData data : monsters) {
            Monster monster = new Monster(data.type, data.x, data.y, data.color, dataManager, assetManager);
            // CRITICAL: Restore the monster's saved health
            monster.setWarStrength(data.warStrength);
            monster.setSpiritualStrength(data.spiritualStrength);
            maze.addMonster(monster);
        }

        for (DoorData data : doors) {
            Door door = new Door();
            door.setState(data.state, data.animationProgress);
            maze.addGameObject(door, data.x, data.y);
        }

        for (GateData data : gates) {
            Gate gate;
            if (data.targetChunkId != null) {
                // Recreate an ADVANCED mode transition gate
                gate = new Gate(data.x, data.y, data.targetChunkId, data.targetPlayerPos);
            } else {
                // Recreate a CLASSIC mode stat-jumbling gate
                gate = new Gate(data.x, data.y);
            }
            gate.setState(data.state, data.animationProgress);
            maze.addGate(gate);
        }

        for (LadderData data : ladders) {
            maze.addLadder(new Ladder(data.x, data.y));
        }

        for (SceneryData data : scenery) {
            maze.addScenery(new Scenery(data.type, data.x, data.y));
        }

        return maze;
    }

    // --- Nested static classes for serialization ---
    // These must be public or static nested for the JSON serializer to access them.

    public static class SceneryData {
        public Scenery.SceneryType type;
        public int x;
        public int y;
        public SceneryData() {} // No-arg constructor for JSON
        public SceneryData(Scenery s) {
            this.type = s.getType();
            this.x = (int)s.getPosition().x;
            this.y = (int)s.getPosition().y;
        }
    }

    public static class ItemData {
        public Item.ItemType type;
        public ItemColor color;
        public int x;
        public int y;
        public List<ItemModifier> modifiers = new ArrayList<>(); //
        public ItemData() {} // No-arg constructor for JSON
        public ItemData(Item item) {
            this.type = item.getType();
            this.color = item.getItemColor();
            this.x = (int)item.getPosition().x;
            this.y = (int)item.getPosition().y;
            this.modifiers = new ArrayList<>(item.getModifiers()); //
        }
    }

    public static class MonsterData {
        public Monster.MonsterType type;
        public MonsterColor color;
        public int x;
        public int y;
        public int warStrength;
        public int spiritualStrength;
        public MonsterData() {} // No-arg constructor for JSON
        public MonsterData(Monster monster) {
            this.type = monster.getType();
            this.color = monster.getMonsterColor();
            this.x = (int)monster.getPosition().x;
            this.y = (int)monster.getPosition().y;
            this.warStrength = monster.getWarStrength();
            this.spiritualStrength = monster.getSpiritualStrength();
        }
    }

    public static class DoorData {
        public int x;
        public int y;
        public DoorState state;
        public float animationProgress;
        public DoorData() {} // No-arg constructor for JSON
        public DoorData(Door door) {
            // Find the door's position from the maze's gameObjects map (this is a bit of a hack)
            GridPoint2 pos = door.findPositionIn(door.getMaze().getGameObjects());
            this.x = (pos != null) ? pos.x : 0; // Fallback, though it should always be found
            this.y = (pos != null) ? pos.y : 0;
            this.state = door.getState();
            this.animationProgress = door.getAnimationProgress();
        }
    }

    public static class GateData {
        public int x;
        public int y;
        public GridPoint2 targetChunkId;
        public GridPoint2 targetPlayerPos;
        public GateState state;
        public float animationProgress;
        public GateData() {} // No-arg constructor for JSON
        public GateData(Gate gate) {
            this.x = (int)gate.getPosition().x;
            this.y = (int)gate.getPosition().y;
            this.targetChunkId = gate.getTargetChunkId();
            this.targetPlayerPos = gate.getTargetPlayerPos();
            this.state = gate.getState();
            this.animationProgress = gate.getAnimationProgress();
        }
    }

    public static class LadderData {
        public int x;
        public int y;
        public LadderData() {} // No-arg constructor for JSON
        public LadderData(Ladder ladder) {
            this.x = (int)ladder.getPosition().x;
            this.y = (int)ladder.getPosition().y;
        }
    }
}
