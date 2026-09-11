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

    public List<GridPoint2> homeTiles = new ArrayList<>();

    public List<ItemData> items = new ArrayList<>();
    public List<MonsterData> monsters = new ArrayList<>();
    public List<DoorData> doors = new ArrayList<>();
    public List<WindowData> windows = new ArrayList<>();
    public List<GateData> gates = new ArrayList<>();
    public List<LadderData> ladders = new ArrayList<>();
    public List<SceneryData> scenery = new ArrayList<>();
    public List<EventData> events = new ArrayList<>();
    public List<DecalData> surfaceDecals = new ArrayList<>();
    public List<WallDecalData> wallDecals = new ArrayList<>();
    public List<GibData> gibs = new ArrayList<>();

    public ChunkData() {
    }

    public ChunkData(Maze maze) {
        this.level = maze.getLevel();
        this.wallData = maze.getWallData();

        // --- NEW: Copy exploration state ---
        this.explorationState = maze.getExplorationState();

        this.homeTiles.addAll(maze.getHomeTiles());

        for (Map.Entry<GridPoint2, Item> entry : maze.getItems().entrySet()) {
            this.items.add(new ItemData(entry.getValue()));
        }

        for (Map.Entry<GridPoint2, Monster> entry : maze.getMonsters().entrySet()) {
            this.monsters.add(new MonsterData(entry.getValue()));
        }

        for (Object obj : maze.getGameObjects().values()) {
            if (obj instanceof Door) {
                this.doors.add(new DoorData((Door) obj));
            } else if (obj instanceof Window) {
                this.windows.add(new WindowData((Window) obj));
            }
        }

        for (Map.Entry<GridPoint2, Gate> entry : maze.getGates().entrySet()) {
            this.gates.add(new GateData(entry.getValue()));
        }

        for (Map.Entry<GridPoint2, Ladder> entry : maze.getLadders().entrySet()) {
            this.ladders.add(new LadderData(entry.getValue()));
        }

        for (Map.Entry<GridPoint2, String> entry : maze.getEventTriggers().entrySet()) {
            this.events.add(new EventData(entry.getKey().x, entry.getKey().y, entry.getValue()));
        }
    }

    public Maze buildMaze(MonsterDataManager dataManager, ItemDataManager itemDataManager, AssetManager assetManager) {
        Maze maze = new Maze(this.level, this.wallData);

        // --- NEW: Restore exploration state ---
        // Handle legacy saves that might not have this field yet
        if (this.explorationState != null) {
            maze.setExplorationState(this.explorationState);
        }

        if (this.homeTiles != null) {
            maze.setHomeTiles(this.homeTiles);
        }

        for (ItemData data : items) {
            Item item = new Item(data.type, data.x, data.y, data.color,
                    itemDataManager, assetManager);
            if (data.modifiers != null) {
                for (ItemModifier mod : data.modifiers) {
                    item.addModifier(mod);
                }
            }
            if (data.contents != null && !data.contents.isEmpty()) {
                List<Item> insideItems = new ArrayList<>();
                for (ItemData insideData : data.contents) {
                    Item inside = new Item(insideData.type, insideData.x, insideData.y, insideData.color, itemDataManager, assetManager);
                    if (insideData.modifiers != null) {
                        for (ItemModifier mod : insideData.modifiers) {
                            inside.addModifier(mod);
                        }
                    }
                    insideItems.add(inside);
                }
                item.setContents(insideItems);
            }
            maze.addItem(item);
        }

        for (MonsterData data : monsters) {
            Monster monster = new Monster(data.type, data.x, data.y, data.color, dataManager, assetManager);
            monster.setCurrentHP(data.warStrength);
            monster.setCurrentMP(data.spiritualStrength);
            maze.addMonster(monster);
        }

        for (DoorData data : doors) {
            Door door = new Door();
            door.setState(data.state, data.animationProgress);
            if (data.orientation != null) {
                door.setOrientation(data.orientation);
            } else {
                // Fallback for legacy saves: deduce from walls
                int wall = maze.getWallDataAt(data.x, data.y);
                boolean northSouthWalls = (wall & (0b01000000 | 0b00010000)) != 0;
                door.setOrientation(northSouthWalls ? Door.Orientation.EAST_WEST : Door.Orientation.NORTH_SOUTH);
            }
            maze.addGameObject(door, data.x, data.y);
        }

        if (this.windows != null) {
            for (WindowData data : windows) {
                maze.addGameObject(new Window(data.x, data.y), data.x, data.y);
            }
        }

        // Fallback for legacy saves without WindowData
        if ((this.windows == null || this.windows.isEmpty()) && this.homeTiles != null && !this.homeTiles.isEmpty()) {
            int minX = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (GridPoint2 pt : this.homeTiles) {
                if (pt.x < minX) minX = pt.x;
                if (pt.y > maxY) maxY = pt.y;
            }
            int targetX = minX;
            int targetY = maxY - 2;
            if (maze.getGameObjectAt(targetX, targetY) == null) {
                maze.addGameObject(new Window(targetX, targetY), targetX, targetY);
            }
        }

        for (GateData data : gates) {
            Gate gate;
            if (data.targetChunkId != null) {
                gate = new Gate(data.x, data.y, data.targetChunkId, data.targetPlayerPos);
            } else {
                gate = new Gate(data.x, data.y);
            }
            gate.setState(data.state, data.animationProgress);
            if (data.orientation != null) {
                gate.setOrientation(data.orientation);
            } else {
                boolean isEW = (data.x == 0 || data.x == maze.getWidth() - 1);
                gate.setOrientation(isEW ? Door.Orientation.EAST_WEST : Door.Orientation.NORTH_SOUTH);
            }
            maze.addGate(gate);
        }

        for (LadderData data : ladders) {
            Ladder.LadderType type = (data.type != null) ? data.type : Ladder.LadderType.DOWN;
            Ladder.EntranceStyle style = (data.style != null) ? data.style : Ladder.EntranceStyle.LADDER;
            maze.addLadder(new Ladder(data.x, data.y, type, style));
        }

        for (SceneryData data : scenery) {
            maze.addScenery(new Scenery(data.type, data.x, data.y));
        }

        if (this.events != null) {
            for (EventData data : events) {
                maze.addEvent(data.x, data.y, data.eventId);
            }
        }

        return maze;
    }

    // --- Static classes remain unchanged ---
    public static class EventData {
        public int x;
        public int y;
        public String eventId;

        public EventData() {
        }

        public EventData(int x, int y, String eventId) {
            this.x = x;
            this.y = y;
            this.eventId = eventId;
        }
    }

    public static class SceneryData {
        public Scenery.SceneryType type;
        public int x;
        public int y;

        public SceneryData() {
        }

        public SceneryData(Scenery s) {
            this.type = s.getType();
            this.x = (int) s.getPosition().x;
            this.y = (int) s.getPosition().y;
        }
    }

    public static class ItemData {
        public Item.ItemType type;
        public ItemColor color;
        public int x;
        public int y;
        public List<ItemModifier> modifiers = new ArrayList<>();
        public List<ItemData> contents = new ArrayList<>();

        public ItemData() {
        }

        public ItemData(Item item) {
            this.type = item.getType();
            this.color = item.getItemColor();
            this.x = (int) item.getPosition().x;
            this.y = (int) item.getPosition().y;
            this.modifiers = new ArrayList<>(item.getModifiers());
            if (item.getContents() != null && !item.getContents().isEmpty()) {
                for (Item inside : item.getContents()) {
                    if (inside != null) {
                        this.contents.add(new ItemData(inside));
                    }
                }
            }
        }
    }

    public static class MonsterData {
        public Monster.MonsterType type;
        public MonsterColor color;
        public int x;
        public int y;
        public int warStrength;
        public int spiritualStrength;

        public MonsterData() {
        }

        public MonsterData(Monster monster) {
            this.type = monster.getType();
            this.color = monster.getMonsterColor();
            this.x = (int) monster.getPosition().x;
            this.y = (int) monster.getPosition().y;
            this.warStrength = monster.getWarStrength();
            this.spiritualStrength = monster.getSpiritualStrength();
        }
    }

    public static class DoorData {
        public int x;
        public int y;
        public DoorState state;
        public float animationProgress;
        public Door.Orientation orientation;

        public DoorData() {
        }

        public DoorData(Door door) {
            GridPoint2 pos = (door.getMaze() != null && door.getMaze().getGameObjects() != null)
                    ? door.findPositionIn(door.getMaze().getGameObjects())
                    : null;
            this.x = (pos != null) ? pos.x : 0;
            this.y = (pos != null) ? pos.y : 0;
            this.state = door.getState();
            this.animationProgress = door.getAnimationProgress();
            this.orientation = door.getOrientation();
        }
    }

    public static class GateData {
        public int x;
        public int y;
        public GridPoint2 targetChunkId;
        public GridPoint2 targetPlayerPos;
        public GateState state;
        public float animationProgress;
        public Door.Orientation orientation;

        public GateData() {
        }

        public GateData(Gate gate) {
            this.x = (int) gate.getPosition().x;
            this.y = (int) gate.getPosition().y;
            this.targetChunkId = gate.getTargetChunkId();
            this.targetPlayerPos = gate.getTargetPlayerPos();
            this.state = gate.getState();
            this.animationProgress = gate.getAnimationProgress();
            this.orientation = gate.getOrientation();
        }
    }

    public static class LadderData {
        public int x;
        public int y;
        public Ladder.LadderType type = Ladder.LadderType.DOWN;
        public Ladder.EntranceStyle style = Ladder.EntranceStyle.LADDER;

        public LadderData() {
        }

        public LadderData(Ladder ladder) {
            this.x = (int) ladder.getPosition().x;
            this.y = (int) ladder.getPosition().y;
            this.type = ladder.getType();
            this.style = ladder.getStyle();
        }
    }

    public static class WindowData {
        public int x;
        public int y;

        public WindowData() {
        }

        public WindowData(Window window) {
            this.x = (int) window.getPosition().x;
            this.y = (int) window.getPosition().y;
        }
    }

    public static class DecalData {
        public float x;
        public float y;
        public float z;
        public float size;
        public float r;
        public float g;
        public float b;
        public float a;
        public float lifeTimer;

        public DecalData() {}
    }

    public static class WallDecalData {
        public int gridX;
        public int gridY;
        public String dir;
        public float wallX;
        public float height;
        public float radius;
        public float r;
        public float g;
        public float b;
        public float a;
        public float lifeTimer;

        public WallDecalData() {}
    }

    public static class GibData {
        public float x;
        public float y;
        public float z;
        public float rotation;
        public float r;
        public float g;
        public float b;
        public float a;
        public float lifeTimer;

        public GibData() {}
    }
}
