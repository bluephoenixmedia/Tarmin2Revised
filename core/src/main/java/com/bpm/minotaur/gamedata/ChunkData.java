package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Door.DoorState;
import com.bpm.minotaur.gamedata.Gate.GateState;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A serializable "snapshot" of a Maze.
 */
public class ChunkData {

    // --- Public fields for easy JSON serialization ---
    public int level;
    public int[][] wallData;

    // --- NEW: Persist Exploration Data ---
    public byte[][] explorationState;

    public List<ItemData> items = new ArrayList<>();
    public List<MonsterData> monsters = new ArrayList<>();
    public List<DoorData> doors = new ArrayList<>();
    public List<GateData> gates = new ArrayList<>();
    public List<LadderData> ladders = new ArrayList<>();
    public List<SceneryData> scenery = new ArrayList<>();


    public ChunkData() {}

    public ChunkData(Maze maze) {
        this.level = maze.getLevel();
        this.wallData = maze.getWallData();

        // --- NEW: Copy exploration state ---
        this.explorationState = maze.getExplorationState();

        for (Map.Entry<GridPoint2, Item> entry : maze.getItems().entrySet()) {
            this.items.add(new ItemData(entry.getValue()));
        }

        for (Map.Entry<GridPoint2, Monster> entry : maze.getMonsters().entrySet()) {
            this.monsters.add(new MonsterData(entry.getValue()));
        }

        for (Object obj : maze.getGameObjects().values()) {
            if (obj instanceof Door) {
                this.doors.add(new DoorData((Door) obj));
            }
        }

        for (Map.Entry<GridPoint2, Gate> entry : maze.getGates().entrySet()) {
            this.gates.add(new GateData(entry.getValue()));
        }

        for (Map.Entry<GridPoint2, Ladder> entry : maze.getLadders().entrySet()) {
            this.ladders.add(new LadderData(entry.getValue()));
        }
    }

    public Maze buildMaze(MonsterDataManager dataManager, ItemDataManager itemDataManager, AssetManager assetManager) {
        Maze maze = new Maze(this.level, this.wallData);

        // --- NEW: Restore exploration state ---
        // Handle legacy saves that might not have this field yet
        if (this.explorationState != null) {
            maze.setExplorationState(this.explorationState);
        }

        for (ItemData data : items) {
            Item item = new Item(data.type, data.x, data.y, data.color,
                itemDataManager, assetManager);
            if (data.modifiers != null) {
                for (ItemModifier mod : data.modifiers) {
                    item.addModifier(mod);
                }
            }
            maze.addItem(item);
        }

        for (MonsterData data : monsters) {
            Monster monster = new Monster(data.type, data.x, data.y, data.color, dataManager, assetManager);
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
                gate = new Gate(data.x, data.y, data.targetChunkId, data.targetPlayerPos);
            } else {
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

    // --- Static classes remain unchanged ---
    public static class SceneryData {
        public Scenery.SceneryType type;
        public int x;
        public int y;
        public SceneryData() {}
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
        public List<ItemModifier> modifiers = new ArrayList<>();
        public ItemData() {}
        public ItemData(Item item) {
            this.type = item.getType();
            this.color = item.getItemColor();
            this.x = (int)item.getPosition().x;
            this.y = (int)item.getPosition().y;
            this.modifiers = new ArrayList<>(item.getModifiers());
        }
    }

    public static class MonsterData {
        public Monster.MonsterType type;
        public MonsterColor color;
        public int x;
        public int y;
        public int warStrength;
        public int spiritualStrength;
        public MonsterData() {}
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
        public DoorData() {}
        public DoorData(Door door) {
            GridPoint2 pos = door.findPositionIn(door.getMaze().getGameObjects());
            this.x = (pos != null) ? pos.x : 0;
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
        public GateData() {}
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
        public LadderData() {}
        public LadderData(Ladder ladder) {
            this.x = (int)ladder.getPosition().x;
            this.y = (int)ladder.getPosition().y;
        }
    }
}
