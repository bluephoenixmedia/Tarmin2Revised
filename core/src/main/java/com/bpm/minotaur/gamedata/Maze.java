package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.gore.GoreManager;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.effects.area.AreaEffectManager;
import com.bpm.minotaur.gamedata.liquid.LiquidManager;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.rendering.RetroTheme;

import java.util.*;

public class Maze {

    public static final byte VISIBILITY_UNSEEN = 0;
    public static final byte VISIBILITY_SEEN = 1;

    private final int level;
    private final int[][] wallData;
    private final int width;
    private final int height;

    private byte[][] explorationState;

    private final Set<GridPoint2> homeTiles = new HashSet<>();

    private final Map<GridPoint2, Object> gameObjects = new HashMap<>();
    private final Map<GridPoint2, Item> items = new HashMap<>();
    private final Map<GridPoint2, Monster> monsters = new HashMap<>();
    private final Map<GridPoint2, Ladder> ladders = new HashMap<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final Map<GridPoint2, Gate> gates = new HashMap<>();
    private final Map<GridPoint2, String> eventTriggers = new HashMap<>(); // Encounters

    private final Map<GridPoint2, Scenery> scenery = new HashMap<>();
    private final List<Scenery> backdropScenery = new ArrayList<>();
    private final Map<GridPoint2, Float> bloodMap = new HashMap<>();
    private LiquidManager liquidManager = new LiquidManager();

    // --- Debris Only ---
    private final List<CorpsePart> corpses = new ArrayList<>();

    private GoreManager goreManager;

    private RetroTheme.Theme theme;
    private ShopkeeperNpc shopkeeper;

    public ShopkeeperNpc getShopkeeper() {
        return shopkeeper;
    }

    public void setShopkeeper(ShopkeeperNpc shopkeeper) {
        this.shopkeeper = shopkeeper;
    }

    private AreaEffectManager areaEffects;

    public Maze(int level, int[][] wallData) {
        this.level = level;
        this.wallData = wallData;
        this.goreManager = new GoreManager();
        this.height = wallData.length;
        this.width = (height > 0) ? wallData[0].length : 0;
        this.explorationState = new byte[height][width];
    }

    public List<CorpsePart> getCorpses() {
        return corpses;
    }

    private GridPoint2 chunkId = new GridPoint2(0, 0);

    /**
     * A badly wounded monster leaves blood on the tile it is leaving, so the
     * player can follow it and the level remembers where it ran.
     */
    public void bleedTrail(Monster monster, int fromTileX, int fromTileY) {
        if (goreManager == null || monster == null) return;
        com.bpm.minotaur.gamedata.gore.GoreProfile profile = com.bpm.minotaur.gamedata.gore.GoreProfile.fromMonster(monster);
        if (!GoreManager.leavesBloodTrail(monster.getCurrentHP(), monster.getMaxHP(), profile)) return;
        int cx = (chunkId != null) ? chunkId.x : 0;
        int cy = (chunkId != null) ? chunkId.y : 0;
        goreManager.spawnWoundTrail(cx * 36.0f + fromTileX + 0.5f, cy * 36.0f + fromTileY + 0.5f, profile);
    }

    public GridPoint2 getChunkId() {
        return chunkId;
    }

    public void setChunkId(GridPoint2 chunkId) {
        this.chunkId = (chunkId != null) ? new GridPoint2(chunkId) : new GridPoint2(0, 0);
    }

    private com.bpm.minotaur.generation.theme.ChunkTheme chunkTheme;

    private com.bpm.minotaur.generation.theme.ThemeObjectiveState themeObjective;

    /**
     * Progress toward this chunk's themed objective. Null for unthemed chunks.
     */
    public com.bpm.minotaur.generation.theme.ThemeObjectiveState getThemeObjective() {
        return themeObjective;
    }

    public void setThemeObjective(com.bpm.minotaur.generation.theme.ThemeObjectiveState state) {
        this.themeObjective = state;
    }

    public com.bpm.minotaur.generation.theme.ChunkTheme getChunkTheme() {
        return chunkTheme;
    }

    public void setChunkTheme(com.bpm.minotaur.generation.theme.ChunkTheme chunkTheme) {
        this.chunkTheme = chunkTheme;
    }

    public void addHomeTile(GridPoint2 pos) {
        homeTiles.add(pos);
    }

    /**
     * Lingering tile effects -- clouds -- in this chunk.
     *
     * <p>Created on first use and never serialised: the world is regenerated from a seed, so a
     * cloud belongs to the chunk it was cast in and dies with it.
     */
    public AreaEffectManager getAreaEffects() {
        if (areaEffects == null) {
            areaEffects = new AreaEffectManager(getWidth(), getHeight());
        }
        return areaEffects;
    }

    /** True when any lingering tile effect is active, so callers can skip the work entirely. */
    public boolean hasAreaEffects() {
        return areaEffects != null && areaEffects.activeTileCount() > 0;
    }

    /**
     * Whether obscuring fog stands on a tile.
     *
     * <p>Here rather than at each call site because five of them -- monster sight, hostile
     * detection, the projectile raycast, the fog wash and the status sync -- would otherwise
     * each write the same {@code hasAreaEffects() && getAreaEffects().isObscured(...)} walk,
     * and each would have to remember the null-and-empty guard.
     */
    public boolean isObscured(int x, int y) {
        return hasAreaEffects() && areaEffects.isObscured(x, y);
    }

    /** Whether obscuring fog lies on the line between two tiles. See {@link #isObscured}. */
    public boolean fogBlocksSight(int x0, int y0, int x1, int y1) {
        return hasAreaEffects() && areaEffects.blocksSight(x0, y0, x1, y1);
    }

    public LiquidManager getLiquidManager() {
        return liquidManager;
    }

    public void setLiquidManager(LiquidManager liquidManager) {
        if (liquidManager != null) {
            this.liquidManager = liquidManager;
        }
    }

    public LiquidType getLiquidAt(int x, int y) {
        return liquidManager != null ? liquidManager.getLiquidAt(x, y) : LiquidType.NONE;
    }

    public boolean isHomeTile(int x, int y) {
        return homeTiles.contains(new GridPoint2(x, y));
    }

    public boolean isHomeTile(GridPoint2 pos) {
        return homeTiles.contains(pos);
    }

    public boolean isIndoors(int x, int y) {
        return level > 1 || isHomeTile(x, y);
    }

    public Set<GridPoint2> getHomeTiles() {
        return homeTiles;
    }

    public void setHomeTiles(List<GridPoint2> tiles) {
        this.homeTiles.clear();
        this.homeTiles.addAll(tiles);
    }

    public void addGate(Gate gate) {
        gates.put(new GridPoint2((int) gate.getPosition().x, (int) gate.getPosition().y), gate);
    }

    public Map<GridPoint2, Gate> getGates() {
        return gates;
    }

    public Gate getGateAt(int x, int y) {
        if (gates == null || gates.isEmpty()) return null;
        for (Map.Entry<GridPoint2, Gate> entry : gates.entrySet()) {
            GridPoint2 p = entry.getKey();
            if (p.x == x && p.y == y) {
                return entry.getValue();
            }
        }
        return null;
    }

    public boolean hasGateAt(int x, int y) {
        return getGateAt(x, y) != null;
    }

    public int getLevel() {
        return level;
    }

    public int getWidth() {
        if (wallData == null || wallData.length == 0)
            return 0;
        return wallData[0].length;
    }

    public void markVisited(int x, int y) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            explorationState[y][x] = VISIBILITY_SEEN;
        }
    }

    public void addEvent(int x, int y, String id) {
        eventTriggers.put(new GridPoint2(x, y), id);
    }

    public String getEventAt(int x, int y) {
        return eventTriggers.get(new GridPoint2(x, y));
    }

    public void removeEvent(int x, int y) {
        eventTriggers.remove(new GridPoint2(x, y));
    }

    public Map<GridPoint2, String> getEventTriggers() {
        return eventTriggers;
    }

    public boolean isVisited(int x, int y) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            return explorationState[y][x] == VISIBILITY_SEEN;
        }
        return false;
    }

    public byte[][] getExplorationState() {
        return explorationState;
    }

    public void setExplorationState(byte[][] state) {
        if (state != null && state.length == height && state[0].length == width) {
            this.explorationState = state;
        }
    }

    public void addBlood(int x, int y, float amount) {
        GridPoint2 pos = new GridPoint2(x, y);
        float current = bloodMap.getOrDefault(pos, 0f);
        bloodMap.put(pos, Math.min(1.0f, current + amount));
    }

    public float getBloodIntensity(int x, int y) {
        return bloodMap.getOrDefault(new GridPoint2(x, y), 0f);
    }

    public Map<GridPoint2, Float> getBloodMap() {
        return bloodMap;
    }

    public int getBloodCount() {
        return bloodMap.size();
    }

    public int getHeight() {
        if (wallData == null)
            return 0;
        return wallData.length;
    }

    public int[][] getWallData() {
        return wallData;
    }

    public int getWallDataAt(int x, int y) {
        if (x < 0 || x >= getWidth() || y < 0 || y >= getHeight()) {
            return 0b11111111;
        }
        return wallData[y][x];
    }

    public boolean isWall(int x, int y) {
        if (x < 0 || x >= getWidth() || y < 0 || y >= getHeight()) {
            return true;
        }
        return wallData[y][x] == 1 || (getWallDataAt(x, y) & 0b1111) == 0b1111;
    }

    public void setTile(int x, int y, int type) {
        if (x >= 0 && x < getWidth() && y >= 0 && y < getHeight()) {
            wallData[y][x] = type;
        }
    }

    public void toggleDoorAt(int x, int y) {
        Object obj = getGameObjectAt(x, y);
        if (obj instanceof Door) {
            Door door = (Door) obj;
            if (door.getState() == Door.DoorState.CLOSED || door.getState() == Door.DoorState.CLOSING) {
                door.startOpening();
            } else if (door.getState() == Door.DoorState.OPEN || door.getState() == Door.DoorState.OPENING) {
                if (!monsters.containsKey(new GridPoint2(x, y))) {
                    door.startClosing();
                }
            }
        }
    }

    public Object getGameObjectAt(int x, int y) {
        GridPoint2 pos = new GridPoint2(x, y);
        Object obj = gameObjects.get(pos);
        if (obj != null)
            return obj;
        return gates.get(pos);
    }

    public void addGameObject(Object object, int x, int y) {
        if (object instanceof Door) {
            ((Door) object).setMaze(this);
        }
        gameObjects.put(new GridPoint2(x, y), object);
    }

    public Map<GridPoint2, Object> getGameObjects() {
        return gameObjects;
    }

    public Map<GridPoint2, Item> getItems() {
        return items;
    }

    public void addItem(Item item) {
        items.put(new GridPoint2((int) item.getPosition().x, (int) item.getPosition().y), item);
    }

    public void removeItem(Item item) {
        if (item == null) return;
        if (item.getPosition() != null) {
            items.remove(new GridPoint2((int) item.getPosition().x, (int) item.getPosition().y));
        }
        Iterator<Map.Entry<GridPoint2, Item>> it = items.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() == item) {
                it.remove();
                break;
            }
        }
    }

    public Map<GridPoint2, Monster> getMonsters() {
        return monsters;
    }

    public void addMonster(Monster monster) {
        monsters.put(new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y), monster);
    }

    /**
     * Takes this monster off the map, wherever it is filed.
     *
     * <p>Removing by {@code monster.getPosition()} is only right while the map
     * key and the position agree. When they drifted apart -- a push that moved
     * the key but not the position -- a kill removed nothing, and the corpse
     * stayed on the map to be killed again for experience, forever.
     *
     * @return whether it was on the map
     */
    public boolean removeMonster(Monster monster) {
        if (monster == null) return false;
        return monsters.values().removeIf(m -> m == monster);
    }

    /**
     * Moves a monster to another tile, keeping its map key and its position
     * together. Every push and knockback goes through here so the two cannot
     * drift apart.
     */
    public void moveMonster(Monster monster, int tileX, int tileY) {
        if (monster == null) return;
        bleedTrail(monster, (int) monster.getPosition().x, (int) monster.getPosition().y);
        removeMonster(monster);
        monster.getPosition().set(tileX + 0.5f, tileY + 0.5f);
        monsters.put(new GridPoint2(tileX, tileY), monster);
    }

    public Map<GridPoint2, Ladder> getLadders() {
        return ladders;
    }

    public void addLadder(Ladder ladder) {
        ladders.put(new GridPoint2((int) ladder.getPosition().x, (int) ladder.getPosition().y), ladder);
    }

    public List<Projectile> getProjectiles() {
        return projectiles;
    }

    public void addProjectile(Projectile projectile) {
        projectiles.add(projectile);
    }

    private final com.badlogic.gdx.utils.Array<com.bpm.minotaur.lighting.LightSource> lights = new com.badlogic.gdx.utils.Array<>(false, 16);

    public com.badlogic.gdx.utils.Array<com.bpm.minotaur.lighting.LightSource> getLights() {
        return lights;
    }

    public void addLight(com.bpm.minotaur.lighting.LightSource light) {
        if (light == null) return;
        removeLight(light.getId());
        lights.add(light);
    }

    public void removeLight(String id) {
        for (int i = lights.size - 1; i >= 0; i--) {
            if (lights.get(i).getId().equals(id)) {
                lights.removeIndex(i);
            }
        }
    }

    public void removeLightAt(float x, float y) {
        for (int i = lights.size - 1; i >= 0; i--) {
            com.bpm.minotaur.lighting.LightSource l = lights.get(i);
            if (Math.abs(l.getPosition().x - x) < 0.6f && Math.abs(l.getPosition().y - y) < 0.6f) {
                lights.removeIndex(i);
            }
        }
    }

    public void setTheme(RetroTheme.Theme theme) {
        this.theme = theme;
    }

    public RetroTheme.Theme getTheme() {
        return (this.theme != null) ? this.theme : RetroTheme.STANDARD_THEME;
    }

    private RetroTheme.Theme secondaryTheme;

    public void setSecondaryTheme(RetroTheme.Theme theme) {
        this.secondaryTheme = theme;
    }

    public RetroTheme.Theme getSecondaryTheme() {
        return (this.secondaryTheme != null) ? this.secondaryTheme : getTheme();
    }

    private com.bpm.minotaur.generation.Biome biome = com.bpm.minotaur.generation.Biome.MAZE;

    public void setBiome(com.bpm.minotaur.generation.Biome biome) {
        this.biome = biome;
    }

    public com.bpm.minotaur.generation.Biome getBiome() {
        return (this.biome != null) ? this.biome : com.bpm.minotaur.generation.Biome.MAZE;
    }

    public boolean isWallBlocking(int x, int y, Direction direction) {
        int nextX = x + (int) direction.getVector().x;
        int nextY = y + (int) direction.getVector().y;

        // If moving into or out of a Gate opening, wall bitmask does not block
        if (hasGateAt(nextX, nextY) || hasGateAt(x, y)) {
            return false;
        }

        int wallMask = direction.getWallMask();
        int doorMask = wallMask << 1;
        int currentCellData = getWallDataAt(x, y);

        if ((currentCellData & wallMask) != 0)
            return true;

        if ((currentCellData & doorMask) != 0) {
            Object obj = getGameObjectAt(x, y);
            if (obj instanceof Door) {
                Door door = (Door) obj;
                return door.getState() != Door.DoorState.OPEN && door.getState() != Door.DoorState.OPENING;
            }

            obj = getGameObjectAt(nextX, nextY);
            if (obj instanceof Door) {
                Door door = (Door) obj;
                return door.getState() != Door.DoorState.OPEN && door.getState() != Door.DoorState.OPENING;
            }
            return true;
        }
        return false;
    }

    /**
     * Whether a melee blow can cross from (px,py) into the adjacent tile (tx,ty): the edge
     * between them is open and the target tile holds no closed door or gate.
     *
     * <p>Deliberately does not ask {@link #isWall}. That treats wall data of exactly 1 as a
     * solid block, but 1 is also WEST's edge mask, so a tile whose only wall is its west
     * edge read as solid and a monster standing in it could not be struck.
     */
    public boolean canMeleeInto(int px, int py, int tx, int ty) {
        if (tx < 0 || tx >= getWidth() || ty < 0 || ty >= getHeight()) {
            return false;
        }
        Direction dir = Direction.fromDelta(tx - px, ty - py);
        if (dir != null && isWallBlocking(px, py, dir)) {
            return false;
        }
        Object obj = getGameObjectAt(tx, ty);
        if (obj instanceof Door door
                && (door.getState() == Door.DoorState.CLOSED || door.getState() == Door.DoorState.CLOSING)) {
            return false;
        }
        return !(obj instanceof Gate gate && gate.getState() != Gate.GateState.OPEN);
    }

    public boolean isPassable(int x, int y) {
        if (x < 0 || x >= wallData[0].length || y < 0 || y >= wallData.length) {
            return false;
        }

        // --- BUG FIX: Removed this check ---
        // if (wallData[y][x] == 1) return false;
        // -----------------------------------

        Object obj = getGameObjectAt(x, y);
        if (obj instanceof Window)
            return false;
        if (obj instanceof Door && ((Door) obj).getState() != Door.DoorState.OPEN && ((Door) obj).getState() != Door.DoorState.OPENING)
            return false;
        if (obj instanceof Gate && ((Gate) obj).getState() != Gate.GateState.OPEN)
            return false;

        // Check for impassable items (props)
        Item item = items.get(new GridPoint2(x, y));
        if (item != null && item.isImpassable()) {
            return false;
        }

        // Themed scenery props block movement for player and monsters alike.
        // Without this, props placed by ChunkThemeDecorator would be ghosts the
        // player walks straight through.
        Scenery prop = scenery.get(new GridPoint2(x, y));
        if (prop != null && prop.isImpassable()) {
            return false;
        }

        // Monsters are impassable
        if (monsters.containsKey(new GridPoint2(x, y))) {
            return false;
        }

        return true;
    }

    public void openDoorAt(int x, int y) {
        Object obj = getGameObjectAt(x, y);
        if (obj instanceof Door) {
            Door door = (Door) obj;
            if (door.getState() == Door.DoorState.CLOSED || door.getState() == Door.DoorState.CLOSING) {
                door.startOpening();
            }
        }
    }

    public Map<GridPoint2, Scenery> getScenery() {
        return scenery;
    }

    public void addScenery(Scenery s) {
        if (s == null)
            return;
        GridPoint2 pos = new GridPoint2((int) s.getPosition().x, (int) s.getPosition().y);
        scenery.put(pos, s);
    }

    /**
     * Render-only scenery: the extra trunks that thicken a forest's trail edges.
     * Kept out of the tile map so they never block movement, take a tile, or
     * turn up in anything that reads scenery for game logic.
     */
    public List<Scenery> getBackdropScenery() {
        return backdropScenery;
    }

    public void addBackdropScenery(Scenery s) {
        if (s != null) backdropScenery.add(s);
    }

    public void removeScenery(int x, int y) {
        scenery.remove(new GridPoint2(x, y));
    }

    public void update(float delta) {
        for (Object object : gameObjects.values()) {
            if (object instanceof Door)
                ((Door) object).update(delta);
        }
        for (Gate gate : gates.values())
            gate.update(delta);

        projectiles.removeIf(projectile -> {
            projectile.update(delta);
            return !projectile.isAlive();
        });

        for (CorpsePart part : corpses)
            part.update(delta);
        if (goreManager != null) {
            goreManager.update(delta, this);
        }
    }

    public GoreManager getGoreManager() {
        return goreManager;
    }

    public void setGoreManager(GoreManager goreManager) {
        if (goreManager != null) {
            this.goreManager = goreManager;
        }
    }
}
