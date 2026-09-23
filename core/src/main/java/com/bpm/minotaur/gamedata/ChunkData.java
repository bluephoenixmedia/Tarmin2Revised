package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
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

    public String chunkTheme;
    public String liquidData;

    /**
     * Progress toward the themed objective. Null on saves written before themed
     * objectives existed; {@link #buildMaze} rebuilds a fresh state in that case
     * so a reloaded themed chunk is never left sealed with no way to resolve it.
     */
    public ThemeObjectiveData themeObjective;

    public ChunkData() {
    }

    /** True if this chunk's saved data marks it as containing the player's home shelter. */
    public boolean hasShelter() {
        return homeTiles != null && !homeTiles.isEmpty();
    }

    public boolean hasUpLadder() {
        return hasLadderOfType(Ladder.LadderType.UP);
    }

    public boolean hasDownLadder() {
        return hasLadderOfType(Ladder.LadderType.DOWN);
    }

    private boolean hasLadderOfType(Ladder.LadderType type) {
        if (ladders == null) return false;
        for (LadderData ladder : ladders) {
            if (ladder.type == type) return true;
        }
        return false;
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

        if (maze.getScenery() != null) {
            for (Scenery s : maze.getScenery().values()) {
                this.scenery.add(new SceneryData(s));
            }
        }

        if (maze.getChunkTheme() != null) {
            this.chunkTheme = maze.getChunkTheme().name();
        }
        if (maze.getLiquidManager() != null) {
            this.liquidData = maze.getLiquidManager().serialize();
        }
        if (maze.getThemeObjective() != null) {
            this.themeObjective = new ThemeObjectiveData(maze.getThemeObjective());
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

        if (this.chunkTheme != null) {
            try {
                maze.setChunkTheme(com.bpm.minotaur.generation.theme.ChunkTheme.valueOf(this.chunkTheme));
            } catch (Exception ignored) {
            }
        }
        if (this.liquidData != null && !this.liquidData.trim().isEmpty()) {
            maze.setLiquidManager(com.bpm.minotaur.gamedata.liquid.LiquidManager.deserialize(this.liquidData));
        }

        restoreThemeObjective(maze);

        for (ItemData data : items) {
            Item item = new Item(data.type, data.x, data.y, data.color,
                    itemDataManager, assetManager);
            if (data.modifiers != null) {
                for (ItemModifier mod : data.modifiers) {
                    item.addModifier(mod);
                }
            }
            data.applyTo(item);
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
            if (data.theme != null) {
                try {
                    gate.setTheme(com.bpm.minotaur.generation.theme.ChunkTheme.valueOf(data.theme));
                } catch (Exception ignored) {
                }
            }
            gate.setLocked(data.isLocked);
            maze.addGate(gate);
        }

        for (LadderData data : ladders) {
            Ladder.LadderType type = (data.type != null) ? data.type : Ladder.LadderType.DOWN;
            Ladder.EntranceStyle style = (data.style != null) ? data.style : Ladder.EntranceStyle.LADDER;
            maze.addLadder(new Ladder(data.x, data.y, type, style));
        }

        for (SceneryData data : scenery) {
            // Themed props rebuild from the catalogue so a retuned scale or
            // passability in props.json applies to already-saved chunks.
            Scenery s = null;
            if (data.propId != null) {
                s = Scenery.fromProp(data.propId, data.x, data.y);
                if (s != null) {
                    s.setObjectiveMarker(data.objectiveMarker);
                    s.setObjectiveConsumed(data.objectiveConsumed);
                }
            }
            if (s == null) {
                s = new Scenery(data.type, data.x, data.y, data.texturePath);
            }
            if (data.texturePath != null && assetManager != null) {
                if (Gdx.files != null && Gdx.files.internal(data.texturePath).exists()) {
                    if (!assetManager.isLoaded(data.texturePath)) {
                        assetManager.load(data.texturePath, Texture.class);
                        assetManager.finishLoading();
                    }
                    s.setTexture(assetManager.get(data.texturePath, Texture.class));
                }
            }
            maze.addScenery(s);
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

    /**
     * Rebuilds the themed objective on load.
     *
     * <p>Themed chunks seal their gates and the lock state persists, so a chunk
     * that reloads without an objective is sealed with nothing able to open it:
     * the completion check bails on a null state, and so does the Rune of
     * Surrender. That is an unrecoverable save. A themed chunk therefore always
     * ends up with a state, freshly seeded from its theme definition when the
     * save predates objectives.
     */
    private void restoreThemeObjective(Maze maze) {
        if (maze.getChunkTheme() == null) return;

        com.bpm.minotaur.generation.theme.ThemeObjectiveState state =
                new com.bpm.minotaur.generation.theme.ThemeObjectiveState();

        if (this.themeObjective != null) {
            com.bpm.minotaur.generation.theme.ThemeObjectiveKind kind = null;
            if (this.themeObjective.kind != null) {
                try {
                    kind = com.bpm.minotaur.generation.theme.ThemeObjectiveKind
                            .valueOf(this.themeObjective.kind);
                } catch (IllegalArgumentException ignored) {
                }
            }
            state.restore(kind,
                    this.themeObjective.progress,
                    this.themeObjective.required,
                    this.themeObjective.completed,
                    this.themeObjective.surrendered,
                    this.themeObjective.rewardGranted);
        } else {
            // Pre-objective save: seed from the theme so the chunk is clearable.
            com.bpm.minotaur.generation.theme.ThemeDefinition def =
                    com.bpm.minotaur.generation.theme.ThemeDataManager.getInstance()
                            .get(maze.getChunkTheme());
            if (def != null) {
                state.setKind(def.getObjective());
                state.setRequired(def.getObjectiveCount());
            }
        }

        maze.setThemeObjective(state);
    }

    /** Serialised form of {@code ThemeObjectiveState}. */
    public static class ThemeObjectiveData {
        public String kind;
        public int progress;
        public int required = 1;
        public boolean completed;
        public boolean surrendered;
        public boolean rewardGranted;

        public ThemeObjectiveData() {
        }

        public ThemeObjectiveData(com.bpm.minotaur.generation.theme.ThemeObjectiveState state) {
            this.kind = state.getKind() != null ? state.getKind().name() : null;
            this.progress = state.getProgress();
            this.required = state.getRequired();
            this.completed = state.isCompleted();
            this.surrendered = state.isSurrendered();
            this.rewardGranted = state.isRewardGranted();
        }
    }

    public static class SceneryData {
        public Scenery.SceneryType type;
        public int x;
        public int y;
        public String texturePath;

        // Additive fields: saves written before themed props deserialise with
        // propId null and the flags false, which is exactly the old behaviour.
        public String propId;
        public boolean objectiveMarker;
        public boolean objectiveConsumed;

        public SceneryData() {
        }

        public SceneryData(Scenery s) {
            this.type = s.getType();
            this.x = (int) s.getPosition().x;
            this.y = (int) s.getPosition().y;
            this.texturePath = s.getTexturePath();
            this.propId = s.getPropId();
            this.objectiveMarker = s.isObjectiveMarker() || s.isObjectiveConsumed();
            this.objectiveConsumed = s.isObjectiveConsumed();
        }
    }

    public static class ItemData {
        public Item.ItemType type;
        public ItemColor color;
        public int x;
        public int y;
        public List<ItemModifier> modifiers = new ArrayList<>();
        public List<ItemData> contents = new ArrayList<>();
        public int studyProgress;
        public boolean isMimic;
        public boolean mimicSeen;
        public boolean mimicRollSpent;

        public ItemData() {
        }

        public ItemData(Item item) {
            this.type = item.getType();
            this.color = item.getItemColor();
            this.x = (int) item.getPosition().x;
            this.y = (int) item.getPosition().y;
            this.modifiers = new ArrayList<>(item.getModifiers());
            this.studyProgress = item.getStudyProgress();
            this.isMimic = item.isMimic();
            this.mimicSeen = item.isMimicSeen();
            this.mimicRollSpent = item.isMimicRollSpent();
            if (item.getContents() != null && !item.getContents().isEmpty()) {
                for (Item inside : item.getContents()) {
                    if (inside != null) {
                        this.contents.add(new ItemData(inside));
                    }
                }
            }
        }

        /**
         * Applies the non-constructor state this DTO carries onto a freshly built Item.
         * Keeping it here means the write path and the read path stay in one place --
         * a field added above without a matching line here would silently evaporate.
         */
        public void applyTo(Item item) {
            if (item == null) {
                return;
            }
            item.setStudyProgress(this.studyProgress);
            item.setMimic(this.isMimic);
            item.setMimicSeen(this.mimicSeen);
            item.setMimicRollSpent(this.mimicRollSpent);
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
        public String theme;
        public boolean isLocked;

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
            if (gate.getTheme() != null) {
                this.theme = gate.getTheme().name();
            }
            this.isLocked = gate.isLocked();
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
