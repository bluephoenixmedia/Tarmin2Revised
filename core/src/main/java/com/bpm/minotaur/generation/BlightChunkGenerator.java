package com.bpm.minotaur.generation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.encounters.Encounter;
import com.bpm.minotaur.gamedata.encounters.EncounterManager;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterVariant;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.lighting.LightSource;
import com.bpm.minotaur.lighting.LightingManager;
import com.bpm.minotaur.managers.SpawnManager;
import com.bpm.minotaur.rendering.RetroTheme;

import java.util.*;

/**
 * Procedural generator for the Blighted Marches, the far band and the land
 * around Castle Tarmin.
 *
 * <p>A 36x36 chunk on the Tundra contract -- four edge gates, a flood-filled
 * guarantee that every gate reaches every other, and solid walls where it
 * borders the maze -- dressed as burnt country: low ash ridges, rot pools of
 * necrotic sludge, charred timber, gibbets and grave mounds, and now and then
 * a wrecked Tarmin Legion camp.
 *
 * <p>The castle chunk is generated in castle mode: the castle billboard stands
 * in a block of impassable ruin, ringed by a road, with its one sealed gate on
 * the side facing the maze. See
 * docs/DEsign/Requirements_ Procedural World &amp; The Blighted Marches.md.
 */
public class BlightChunkGenerator implements IChunkGenerator {

    public static final int CHUNK_SIZE = 36;
    private static final int MID = CHUNK_SIZE / 2;

    /** Half-width of the castle's impassable block, in tiles (Chebyshev). */
    static final int CASTLE_HALF = 7;
    /** The road ring around the block. */
    static final int CASTLE_RING = CASTLE_HALF + 2;

    public static final String CASTLE_BILLBOARD_PROP = "castle_billboard";
    public static final String CASTLE_GATE_PROP = "castle_gate";

    private final Random random = new Random();
    private String[] finalLayout;
    private final GridPoint2 playerSpawnPoint = new GridPoint2(MID, MID);
    private GridPoint2 forcedUpLadderPos = null;
    private GridPoint2 castleSite = null;

    /** Set in castle mode: the tile the player stands on to knock at the gate. */
    private GridPoint2 castleDoorstep = null;
    private final List<GridPoint2> campSites = new ArrayList<>();

    /** A baked Blight sprite and the billboard size its canvas was rendered at. */
    public record Sprite(String path, float width, float height) {
        public void applyTo(Scenery s, float jitter) {
            s.scale.set(width * jitter, height * jitter);
            s.setPixelOffsetY(0f);
        }
    }

    public static final Sprite CHARRED_01 = new Sprite("images/blight/tree_charred_01.png", 1.9f, 3.0f);
    public static final Sprite CHARRED_02 = new Sprite("images/blight/tree_charred_02.png", 1.7f, 2.8f);
    public static final Sprite CHARRED_03 = new Sprite("images/blight/tree_charred_03.png", 2.0f, 3.6f);
    public static final Sprite SNAG = new Sprite("images/blight/tree_snag_01.png", 1.6f, 3.0f);
    public static final Sprite ASH_MOUND = new Sprite("images/blight/ash_mound_01.png", 1.1f, 0.38f);
    public static final Sprite STUMP = new Sprite("images/blight/stump_charred_01.png", 0.8f, 0.6f);
    public static final Sprite BONES = new Sprite("images/blight/bones_ash_01.png", 1.0f, 0.5f);

    public static final Sprite[] TREES = { CHARRED_01, CHARRED_02, CHARRED_03, SNAG };
    private static final Sprite[] GROUND_SPRITES = { ASH_MOUND, STUMP, BONES };

    /** Passable dressing from props.json. */
    private static final String[] GROUND_PROPS = { "skull_pile", "bone_pile", "grave_mound", "bramble", "twisted_root" };
    /** Blocking dressing from props.json, placed only where it cuts no path. */
    private static final String[] BLOCKING_PROPS = { "gravestone", "gibbet_cage", "head_spike", "ruined_pillar", "rubble_pile" };

    // --- Roster (section 9) ------------------------------------------------
    /** The RUINED_CASTLE Legion without its golem. */
    static final MonsterType[] LEGION = { MonsterType.ORC, MonsterType.HOBGOBLIN, MonsterType.GARGOYLE };
    static final MonsterType[] DENIZENS = { MonsterType.SPECTER, MonsterType.SKELETAL_WIZARD, MonsterType.DEMON_SLIME };
    static final MonsterType[] BLIGHTABLE_FAUNA = {
            MonsterType.WEREWOLF, MonsterType.OWLBEAR, MonsterType.GHOUL, MonsterType.ZOMBIE, MonsterType.GIANT_SCORPION
    };
    static final MonsterType RARE_ELITE = MonsterType.FALL_ANGEL;
    static final float RARE_ELITE_CHANCE = 1f / 12f;
    /** Blighted fauna are this much tougher than the template. */
    static final float BLIGHTED_HP_MULT = 1.25f;

    static final float ASHWATER_CHANCE = 0.35f;
    static final float WARD_CHARM_CHANCE = 0.08f;
    static final float CAMP_CHANCE = 0.5f;

    public static List<String> textures() {
        List<String> paths = new ArrayList<>();
        for (Sprite s : TREES) paths.add(s.path());
        for (Sprite s : GROUND_SPRITES) paths.add(s.path());
        return paths;
    }

    private final Map<String, Texture> textureCache = new HashMap<>();

    public void setForcedUpLadderPos(GridPoint2 pos) {
        this.forcedUpLadderPos = pos;
    }

    /** The castle chunk of the current world, or null (legacy worlds have none). */
    public void setCastleSite(GridPoint2 site) {
        this.castleSite = (site == null) ? null : new GridPoint2(site);
    }

    /** Where the castle's sealed gate stands in the last castle chunk generated; null otherwise. */
    public GridPoint2 getCastleDoorstep() {
        return castleDoorstep == null ? null : new GridPoint2(castleDoorstep);
    }

    @Override
    public Maze generateChunk(GridPoint2 chunkId, int layoutLevel, int spawnDifficulty, Difficulty difficulty,
                              GameMode gameMode, RetroTheme.Theme theme, RetroTheme.Theme mazeTheme,
                              MonsterDataManager dataManager,
                              ItemDataManager itemDataManager,
                              AssetManager assetManager,
                              EncounterManager encounterManager,
                              SpawnTableData spawnTableData,
                              long chunkSeed,
                              int playerLuck) {

        random.setSeed(chunkSeed);
        boolean castleMode = castleSite != null && castleSite.equals(chunkId);
        castleDoorstep = null;
        campSites.clear();

        char[][] grid = createLayout(castleMode ? frontFacingMaze(chunkId) : null);
        sealMazeBorders(grid, chunkId);
        this.finalLayout = toLayout(grid);

        if (forcedUpLadderPos != null) {
            int ly = CHUNK_SIZE - 1 - forcedUpLadderPos.y;
            if (ly >= 0 && ly < CHUNK_SIZE && forcedUpLadderPos.x >= 0 && forcedUpLadderPos.x < CHUNK_SIZE) {
                char[] r = finalLayout[ly].toCharArray();
                r[forcedUpLadderPos.x] = '.';
                finalLayout[ly] = new String(r);
            }
        }

        Maze maze = createMazeFromText(layoutLevel, finalLayout);
        maze.setBiome(Biome.BLIGHT);
        maze.setTheme(theme != null ? theme : RetroTheme.BLIGHT_THEME);
        maze.setSecondaryTheme(mazeTheme);

        if (castleMode) {
            placeCastle(maze, assetManager);
        }

        distributeRotPools(maze, chunkSeed, castleMode);

        Set<GridPoint2> reachable = computeReachableTiles(maze);

        if (!castleMode) {
            spawnCamp(maze, reachable, assetManager);
        }
        spawnTrees(maze, reachable, assetManager, chunkSeed, castleMode);
        scatterGroundCover(maze, reachable, assetManager, chunkSeed);

        reachable = computeReachableTiles(maze);

        spawnSupplies(maze, difficulty, spawnDifficulty, dataManager, itemDataManager, assetManager,
                spawnTableData, chunkSeed, playerLuck, reachable);
        spawnBlightItems(maze, reachable, itemDataManager, assetManager, chunkSeed, castleMode);
        spawnRoster(maze, reachable, dataManager, assetManager, spawnDifficulty, chunkSeed, castleMode);
        spawnEncounters(maze, encounterManager, reachable, assetManager);
        spawnLadder(maze, reachable, castleMode);
        spawnTransitionGates(maze, chunkId);
        findPlayerStart();

        return maze;
    }

    @Override
    public GridPoint2 getInitialPlayerStartPos() {
        return playerSpawnPoint;
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /**
     * The side of the castle chunk that faces the maze, as a world-space unit step.
     * The gate goes there, so the walk out from home ends at the door.
     */
    static GridPoint2 frontFacingMaze(GridPoint2 chunkId) {
        int dx = -chunkId.x;
        int dy = -chunkId.y;
        if (Math.abs(dx) >= Math.abs(dy)) {
            return new GridPoint2(dx < 0 ? -1 : 1, 0);
        }
        return new GridPoint2(0, dy < 0 ? -1 : 1);
    }

    /** Grid indexed [layoutRow][x]; layoutRow = CHUNK_SIZE - 1 - worldY. */
    private char[][] createLayout(GridPoint2 castleFront) {
        char[][] grid = new char[CHUNK_SIZE][CHUNK_SIZE];
        for (int y = 0; y < CHUNK_SIZE; y++) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                boolean edge = x == 0 || y == 0 || x == CHUNK_SIZE - 1 || y == CHUNK_SIZE - 1;
                grid[y][x] = edge ? '#' : '.';
            }
        }

        // Low ash ridges.
        FastNoiseLite noise = new FastNoiseLite(random.nextInt());
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(0.09f);
        for (int y = 2; y < CHUNK_SIZE - 2; y++) {
            for (int x = 2; x < CHUNK_SIZE - 2; x++) {
                if (noise.GetNoise(x, y) > 0.34f) grid[y][x] = '#';
            }
        }

        // Gate mouths (width 3) and the four avenues to the middle.
        for (int i = 0; i <= 3; i++) {
            for (int o = -1; o <= 1; o++) {
                grid[i][MID + o] = '.';
                grid[CHUNK_SIZE - 1 - i][MID + o] = '.';
                grid[MID + o][i] = '.';
                grid[MID + o][CHUNK_SIZE - 1 - i] = '.';
            }
        }

        if (castleFront == null) {
            // A blasted clearing in the middle.
            for (int y = MID - 5; y <= MID + 5; y++) {
                for (int x = MID - 5; x <= MID + 5; x++) {
                    float dx = x - MID, dy = y - MID;
                    if (dx * dx + dy * dy <= 4.6f * 4.6f + 3f * (float) Math.sin(dx + dy)) grid[y][x] = '.';
                }
            }
            carveCauseway(grid, MID, 3, MID, MID - 3, 1);
            carveCauseway(grid, MID, CHUNK_SIZE - 4, MID, MID + 3, 1);
            carveCauseway(grid, 3, MID, MID - 3, MID, 1);
            carveCauseway(grid, CHUNK_SIZE - 4, MID, MID + 3, MID, 1);
            ensureGateConnectivity(grid, MID, MID);
        } else {
            stampCastle(grid, castleFront);
        }
        return grid;
    }

    /**
     * The castle block, its ring road, and the gate.
     *
     * <p>The block is solid ruin; the billboard stands in a small courtyard at its
     * heart so its base is not buried in a wall mesh. Each edge gate runs straight
     * in along its axis to the ring, which is outside the block, so all four meet
     * without crossing it. The front avenue continues to the gate.
     */
    private void stampCastle(char[][] grid, GridPoint2 front) {
        for (int y = 1; y < CHUNK_SIZE - 1; y++) {
            for (int x = 1; x < CHUNK_SIZE - 1; x++) {
                int wx = x, wy = CHUNK_SIZE - 1 - y;
                int cheb = Math.max(Math.abs(wx - MID), Math.abs(wy - MID));
                if (cheb <= 1) {
                    grid[y][x] = '.';              // courtyard under the billboard
                } else if (cheb <= CASTLE_HALF) {
                    grid[y][x] = '#';              // the castle's impassable footprint
                } else if (cheb <= CASTLE_RING) {
                    grid[y][x] = '.';              // ring road
                }
            }
        }
        // Avenues from each gate to the ring.
        for (int i = 1; i < CHUNK_SIZE - 1; i++) {
            int distFromMid = Math.abs(i - MID);
            if (distFromMid <= CASTLE_HALF) continue;
            for (int o = -1; o <= 1; o++) {
                grid[i][MID + o] = '.';
                grid[MID + o][i] = '.';
            }
        }

        // The gate: the block's outermost tile on the front, and the doorstep just outside it.
        int gateWX = MID + front.x * CASTLE_HALF;
        int gateWY = MID + front.y * CASTLE_HALF;
        grid[CHUNK_SIZE - 1 - gateWY][gateWX] = '.';
        castleDoorstep = new GridPoint2(MID + front.x * (CASTLE_HALF + 1), MID + front.y * (CASTLE_HALF + 1));
        castleGateTile = new GridPoint2(gateWX, gateWY);
    }

    private GridPoint2 castleGateTile;

    private void placeCastle(Maze maze, AssetManager assetManager) {
        Scenery billboard = Scenery.fromProp(CASTLE_BILLBOARD_PROP, MID, MID);
        if (billboard != null) {
            loadTextureSafely(billboard, billboard.getTexturePath(), assetManager);
            maze.addScenery(billboard);
        }
        if (castleGateTile != null) {
            Scenery gate = Scenery.fromProp(CASTLE_GATE_PROP, castleGateTile.x, castleGateTile.y);
            if (gate != null) {
                loadTextureSafely(gate, gate.getTexturePath(), assetManager);
                maze.addScenery(gate);
            }
            // Twin braziers flank the doorstep: the one warm light in the Marches is the castle's.
            int sideX = castleGateTile.y == MID ? 0 : 1;
            int sideY = castleGateTile.y == MID ? 1 : 0;
            for (int s = -1; s <= 1; s += 2) {
                int bx = castleDoorstep.x + sideX * s * 2;
                int by = castleDoorstep.y + sideY * s * 2;
                if (bx <= 0 || by <= 0 || bx >= CHUNK_SIZE - 1 || by >= CHUNK_SIZE - 1 || maze.isWall(bx, by)) continue;
                Scenery brazier = Scenery.fromProp("brazier", bx, by);
                if (brazier != null) {
                    loadTextureSafely(brazier, brazier.getTexturePath(), assetManager);
                    maze.addScenery(brazier);
                    maze.addLight(new LightSource("blight_castle_brazier_" + s, bx + 0.5f, by + 0.5f,
                            LightingManager.COLOR_CAMPFIRE, 4.0f, 1.1f, LightSource.FlickerProfile.CAMPFIRE_FLICKER));
                }
            }
        }
    }

    private void sealMazeBorders(char[][] grid, GridPoint2 chunkId) {
        boolean north = isMaze(chunkId.x, chunkId.y + 1);
        boolean south = isMaze(chunkId.x, chunkId.y - 1);
        boolean east = isMaze(chunkId.x + 1, chunkId.y);
        boolean west = isMaze(chunkId.x - 1, chunkId.y);
        for (int i = 0; i < CHUNK_SIZE; i++) {
            if (north && grid[0][i] == '#') grid[0][i] = 'M';
            if (south && grid[CHUNK_SIZE - 1][i] == '#') grid[CHUNK_SIZE - 1][i] = 'M';
            if (west && grid[i][0] == '#') grid[i][0] = 'M';
            if (east && grid[i][CHUNK_SIZE - 1] == '#') grid[i][CHUNK_SIZE - 1] = 'M';
        }
    }

    private static String[] toLayout(char[][] grid) {
        String[] out = new String[grid.length];
        for (int y = 0; y < grid.length; y++) out[y] = new String(grid[y]);
        return out;
    }

    private void carveCauseway(char[][] grid, int x1, int y1, int x2, int y2, int halfWidth) {
        int curX = x1, curY = y1;
        while (curX != x2 || curY != y2) {
            for (int dy = -halfWidth; dy <= halfWidth; dy++) {
                for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                    int nx = curX + dx, ny = curY + dy;
                    if (nx > 0 && nx < CHUNK_SIZE - 1 && ny > 0 && ny < CHUNK_SIZE - 1) grid[ny][nx] = '.';
                }
            }
            if (curX != x2 && (curY == y2 || random.nextBoolean())) {
                curX += (x2 > curX) ? 1 : -1;
            } else if (curY != y2) {
                curY += (y2 > curY) ? 1 : -1;
            }
        }
    }

    private void ensureGateConnectivity(char[][] grid, int midX, int midY) {
        int[][] gates = { {midX, 1}, {midX, CHUNK_SIZE - 2}, {1, midY}, {CHUNK_SIZE - 2, midY} };
        Set<Integer> visited = floodFill(grid, midX, midY);
        for (int[] gate : gates) {
            if (!visited.contains(gate[1] * CHUNK_SIZE + gate[0])) {
                carveCauseway(grid, gate[0], gate[1], midX, midY, 1);
                visited = floodFill(grid, midX, midY);
            }
        }
    }

    private static Set<Integer> floodFill(char[][] grid, int startX, int startY) {
        Set<Integer> seen = new HashSet<>();
        if (grid[startY][startX] == '#') return seen;
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        seen.add(startY * CHUNK_SIZE + startX);
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int i = 0; i < 4; i++) {
                int nx = cur[0] + dx[i], ny = cur[1] + dy[i];
                if (nx < 0 || nx >= CHUNK_SIZE || ny < 0 || ny >= CHUNK_SIZE) continue;
                if (grid[ny][nx] == '#' || grid[ny][nx] == 'M') continue;
                if (seen.add(ny * CHUNK_SIZE + nx)) queue.add(new int[]{nx, ny});
            }
        }
        return seen;
    }

    // ------------------------------------------------------------------
    // Hazards and dressing
    // ------------------------------------------------------------------

    /** Rot pools: necrotic sludge in the hollows, which also feeds Taint (see Taint). */
    private void distributeRotPools(Maze maze, long seed, boolean castleMode) {
        FastNoiseLite rot = new FastNoiseLite((int) (seed ^ 0x2B0771ADL));
        rot.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        rot.SetFrequency(0.11f);
        for (int y = 1; y < CHUNK_SIZE - 1; y++) {
            for (int x = 1; x < CHUNK_SIZE - 1; x++) {
                if (maze.isWall(x, y)) continue;
                // Keep the avenues and the arrival clearing walkable without wading.
                if (Math.abs(x - MID) <= 1 || Math.abs(y - MID) <= 1) continue;
                if (!castleMode && Math.abs(x - MID) <= 3 && Math.abs(y - MID) <= 3) continue;
                if (rot.GetNoise(x, y) > 0.45f) {
                    maze.getLiquidManager().setLiquidAt(x, y, LiquidType.BLACK_MUCK);
                }
            }
        }
    }

    private void spawnCamp(Maze maze, Set<GridPoint2> reachable, AssetManager assetManager) {
        if (random.nextFloat() >= CAMP_CHANCE) return;
        int[][] corners = { {9, 9}, {26, 9}, {9, 26}, {26, 26} };
        int[] c = corners[random.nextInt(corners.length)];
        GridPoint2 centre = new GridPoint2(c[0], c[1]);
        if (!reachable.contains(centre) || maze.getScenery().containsKey(centre)) return;
        campSites.add(centre);

        // A burnt-out Legion bivouac: the brazier still smoulders.
        String[][] layout = { {"camp_tent", "-1", "1"}, {"war_banner", "1", "1"}, {"weapon_rack", "1", "-1"}, {"helmet_pile", "-1", "-1"} };
        for (String[] e : layout) {
            int x = centre.x + Integer.parseInt(e[1]);
            int y = centre.y + Integer.parseInt(e[2]);
            if (!isStandable(maze, x, y)) continue;
            Scenery s = Scenery.fromProp(e[0], x, y);
            if (s == null) continue;
            loadTextureSafely(s, s.getTexturePath(), assetManager);
            maze.addScenery(s);
            if (s.isImpassable() && computeReachableTiles(maze).size() < reachable.size() - 2) {
                maze.getScenery().remove(new GridPoint2(x, y));
            }
        }
        Scenery brazier = Scenery.fromProp("brazier", centre.x, centre.y);
        if (brazier != null) {
            loadTextureSafely(brazier, brazier.getTexturePath(), assetManager);
            maze.addScenery(brazier);
            maze.addLight(new LightSource("blight_camp_brazier", centre.x + 0.5f, centre.y + 0.5f,
                    LightingManager.COLOR_CAMPFIRE, 3.5f, 0.9f, LightSource.FlickerProfile.CAMPFIRE_FLICKER));
        }
    }

    private void spawnTrees(Maze maze, Set<GridPoint2> reachable, AssetManager assetManager, long seed,
                            boolean castleMode) {
        Random rng = new Random(seed ^ 0x6C1A77EDL);
        List<GridPoint2> candidates = new ArrayList<>();
        for (GridPoint2 pt : reachable) {
            if (Math.abs(pt.x - MID) <= 1 || Math.abs(pt.y - MID) <= 1) continue;
            if (pt.x <= 3 || pt.x >= CHUNK_SIZE - 4 || pt.y <= 3 || pt.y >= CHUNK_SIZE - 4) continue;
            if (!castleMode && Math.abs(pt.x - MID) <= 3 && Math.abs(pt.y - MID) <= 3) continue;
            if (castleMode && Math.max(Math.abs(pt.x - MID), Math.abs(pt.y - MID)) <= CASTLE_RING) continue;
            if (nearCamp(pt, 2)) continue;
            if (maze.getScenery().containsKey(pt) || maze.getGateAt(pt.x, pt.y) != null) continue;
            candidates.add(pt);
        }
        Collections.shuffle(candidates, rng);

        // Thinner than the taiga: the Marches are burnt open, not wooded.
        int target = (castleMode ? 8 : 16) + rng.nextInt(8);
        int current = reachable.size();
        int placed = 0;
        for (GridPoint2 pt : candidates) {
            if (placed >= target) break;
            Scenery tree = new Scenery(Scenery.SceneryType.TREE, pt.x, pt.y);
            tree.setImpassable(true);
            tree.setFlippedX(rng.nextBoolean());
            Sprite sp = TREES[rng.nextInt(TREES.length)];
            sp.applyTo(tree, 0.9f + rng.nextFloat() * 0.25f);
            loadTextureSafely(tree, sp.path(), assetManager);
            maze.addScenery(tree);
            int after = computeReachableTiles(maze).size();
            if (after < current - 1) {
                maze.getScenery().remove(pt);
                continue;
            }
            current = after;
            placed++;
        }

        // Dead timber along the ridge lines, as backdrop.
        for (int y = 2; y < CHUNK_SIZE - 2; y++) {
            for (int x = 2; x < CHUNK_SIZE - 2; x++) {
                if (!isWallAt(x, y) || !facesOpenGround(x, y)) continue;
                if (castleMode && Math.max(Math.abs(x - MID), Math.abs(y - MID)) <= CASTLE_HALF) continue;
                if (rng.nextFloat() > 0.22f) continue;
                Scenery t = new Scenery(Scenery.SceneryType.TREE, x, y);
                t.setFlippedX(rng.nextBoolean());
                Sprite sp = TREES[rng.nextInt(TREES.length)];
                sp.applyTo(t, 0.85f + rng.nextFloat() * 0.3f);
                loadTextureSafely(t, sp.path(), assetManager);
                maze.addBackdropScenery(t);
            }
        }
    }

    private void scatterGroundCover(Maze maze, Set<GridPoint2> reachable, AssetManager assetManager, long seed) {
        Random rng = new Random(seed ^ 0x3A5E5EEDL);
        int current = computeReachableTiles(maze).size();
        for (GridPoint2 pt : reachable) {
            if (Math.abs(pt.x - MID) <= 1 || Math.abs(pt.y - MID) <= 1) continue;
            if (maze.getScenery().containsKey(pt) || maze.getGateAt(pt.x, pt.y) != null) continue;
            if (castleDoorstep != null && pt.dst2(castleDoorstep) <= 4) continue;
            float roll = rng.nextFloat();
            if (roll < 0.03f) {
                // Gibbets and grave markers, only where they cut nothing off.
                Scenery s = Scenery.fromProp(BLOCKING_PROPS[rng.nextInt(BLOCKING_PROPS.length)], pt.x, pt.y);
                if (s == null) continue;
                loadTextureSafely(s, s.getTexturePath(), assetManager);
                maze.addScenery(s);
                int after = computeReachableTiles(maze).size();
                if (after < current - 1) {
                    maze.getScenery().remove(pt);
                } else {
                    current = after;
                }
            } else if (roll < 0.07f) {
                Scenery s = Scenery.fromProp(GROUND_PROPS[rng.nextInt(GROUND_PROPS.length)], pt.x, pt.y);
                if (s == null) continue;
                loadTextureSafely(s, s.getTexturePath(), assetManager);
                maze.addScenery(s);
            } else if (roll < 0.15f) {
                Sprite sp = GROUND_SPRITES[rng.nextInt(GROUND_SPRITES.length)];
                Scenery sc = new Scenery(Scenery.SceneryType.PROP, pt.x, pt.y, sp.path());
                sc.getPosition().set(pt.x + 0.5f + (rng.nextFloat() - 0.5f) * 0.4f,
                        pt.y + 0.5f + (rng.nextFloat() - 0.5f) * 0.4f);
                sc.setFlippedX(rng.nextBoolean());
                sp.applyTo(sc, 0.85f + rng.nextFloat() * 0.3f);
                loadTextureSafely(sc, sp.path(), assetManager);
                maze.addBackdropScenery(sc);
            }
        }
    }

    // ------------------------------------------------------------------
    // Spawns
    // ------------------------------------------------------------------

    private void spawnSupplies(Maze maze, Difficulty difficulty, int spawnDifficulty,
                               MonsterDataManager dataManager, ItemDataManager itemDataManager,
                               AssetManager assetManager, SpawnTableData spawnTableData, long chunkSeed,
                               int playerLuck, Set<GridPoint2> reachable) {
        if (spawnTableData == null) return;
        SpawnManager spawnManager = new SpawnManager(dataManager, itemDataManager, assetManager,
                maze, difficulty, spawnDifficulty, spawnDifficulty, playerLuck, finalLayout, spawnTableData,
                chunkSeed ^ 0xFEEDFACECAFEBABEL, reachable);
        spawnManager.spawnSuppliesOnly();
    }

    private void spawnBlightItems(Maze maze, Set<GridPoint2> reachable, ItemDataManager itemDataManager,
                                  AssetManager assetManager, long seed, boolean castleMode) {
        if (itemDataManager == null) return;
        Random rng = new Random(seed ^ 0xA5A3A7E2L);
        List<GridPoint2> spots = openTiles(maze, reachable);
        Collections.shuffle(spots, rng);
        Iterator<GridPoint2> it = spots.iterator();

        // The castle chunk always leaves one flask by the gate: the last place to need it.
        if ((castleMode || rng.nextFloat() < ASHWATER_CHANCE) && it.hasNext()) {
            GridPoint2 at = castleMode && castleDoorstep != null ? nearestOpen(maze, castleDoorstep) : it.next();
            if (at != null) {
                Item ash = itemDataManager.createItem(Item.ItemType.ASHWATER, at.x, at.y, ItemColor.GRAY, assetManager);
                if (ash != null) maze.addItem(ash);
            }
        }
        if (rng.nextFloat() < WARD_CHARM_CHANCE && it.hasNext()) {
            GridPoint2 at = it.next();
            Item ward = itemDataManager.createItem(Item.ItemType.WARD_CHARM, at.x, at.y, ItemColor.RED, assetManager);
            if (ward != null) maze.addItem(ward);
        }
    }

    private void spawnRoster(Maze maze, Set<GridPoint2> reachable, MonsterDataManager dataManager,
                             AssetManager assetManager, int level, long seed, boolean castleMode) {
        if (dataManager == null) return;
        Random rng = new Random(seed ^ 0x1E610A75L);
        List<GridPoint2> spots = new ArrayList<>();
        for (GridPoint2 pt : openTiles(maze, reachable)) {
            // Never on the arrival clearing or the castle doorstep.
            if (!castleMode && Math.abs(pt.x - MID) <= 4 && Math.abs(pt.y - MID) <= 4) continue;
            if (castleDoorstep != null && pt.dst2(castleDoorstep) <= 9) continue;
            spots.add(pt);
        }
        Collections.shuffle(spots, rng);
        Iterator<GridPoint2> it = spots.iterator();

        // Legion patrol: near the camp if there is one, else wherever it was marching.
        int legion = (castleMode ? 4 : 2) + rng.nextInt(2);
        List<GridPoint2> legionSpots = new ArrayList<>(spots);
        if (!campSites.isEmpty()) {
            GridPoint2 camp = campSites.get(0);
            legionSpots.sort(Comparator.comparingInt(p -> (p.x - camp.x) * (p.x - camp.x) + (p.y - camp.y) * (p.y - camp.y)));
        }
        Iterator<GridPoint2> lit = legionSpots.iterator();
        for (int i = 0; i < legion && lit.hasNext(); i++) {
            GridPoint2 pt = lit.next();
            if (maze.getMonsters().containsKey(pt)) { i--; continue; }
            spawn(maze, LEGION[rng.nextInt(LEGION.length)], pt, Faction.TARMIN_LEGION, false, level, dataManager, assetManager);
        }

        int denizens = 2 + rng.nextInt(3);
        for (int i = 0; i < denizens && it.hasNext(); i++) {
            GridPoint2 pt = it.next();
            if (maze.getMonsters().containsKey(pt)) { i--; continue; }
            spawn(maze, DENIZENS[rng.nextInt(DENIZENS.length)], pt, Faction.UNDEAD, false, level, dataManager, assetManager);
        }

        int fauna = 1 + rng.nextInt(2);
        for (int i = 0; i < fauna && it.hasNext(); i++) {
            GridPoint2 pt = it.next();
            if (maze.getMonsters().containsKey(pt)) { i--; continue; }
            spawn(maze, BLIGHTABLE_FAUNA[rng.nextInt(BLIGHTABLE_FAUNA.length)], pt, Faction.BEASTS_AND_VERMIN,
                    true, level, dataManager, assetManager);
        }

        if (rng.nextFloat() < RARE_ELITE_CHANCE) {
            while (it.hasNext()) {
                GridPoint2 pt = it.next();
                if (maze.getMonsters().containsKey(pt)) continue;
                spawn(maze, RARE_ELITE, pt, Faction.UNDEAD, false, level, dataManager, assetManager);
                break;
            }
        }
    }

    private static Monster spawn(Maze maze, MonsterType type, GridPoint2 pt, Faction faction, boolean blighted,
                                 int level, MonsterDataManager dataManager, AssetManager assetManager) {
        MonsterVariant variant = dataManager.getRandomVariantForMonster(type, level);
        MonsterColor color = (variant != null) ? variant.color : MonsterColor.WHITE;
        Monster m = new Monster(type, pt.x, pt.y, color, dataManager, assetManager);
        m.scaleStats(level);
        if (blighted) {
            m.setBlighted(true);
            m.setMaxHP(Math.round(m.getMaxHP() * BLIGHTED_HP_MULT));
        }
        m.setCurrentHP(m.getMaxHP());
        m.setFaction(faction);
        maze.addMonster(m);
        return m;
    }

    private void spawnEncounters(Maze maze, EncounterManager encounterManager, Set<GridPoint2> reachable,
                                 AssetManager assetManager) {
        if (encounterManager == null) return;
        List<GridPoint2> candidates = openTiles(maze, reachable);
        Collections.shuffle(candidates, random);
        int wanted = random.nextInt(2);
        for (int i = 0; i < candidates.size() && wanted > 0; i++) {
            GridPoint2 pos = candidates.get(i);
            if (maze.getMonsters().containsKey(pos)) continue;
            String encounterId = encounterManager.getRandomEncounterId();
            if (encounterId == null) continue;
            maze.addEvent(pos.x, pos.y, encounterId);
            Encounter enc = encounterManager.getEncounter(encounterId);
            String img = (enc != null) ? enc.imagePath : null;
            Scenery statue = new Scenery(Scenery.SceneryType.STATUE, pos.x, pos.y, img);
            if (img != null) loadTextureSafely(statue, img, assetManager);
            maze.addScenery(statue);
            wanted--;
        }
    }

    private void spawnLadder(Maze maze, Set<GridPoint2> reachable, boolean castleMode) {
        List<GridPoint2> candidates = new ArrayList<>();
        for (GridPoint2 tile : openTiles(maze, reachable)) {
            if (maze.getMonsters().containsKey(tile)) continue;
            if (castleDoorstep != null && tile.dst2(castleDoorstep) <= 9) continue;
            if (Math.hypot(tile.x - MID, tile.y - MID) >= (castleMode ? CASTLE_RING + 1 : 6)) candidates.add(tile);
        }
        if (candidates.isEmpty()) candidates.addAll(reachable);
        if (!candidates.isEmpty()) {
            Collections.shuffle(candidates, random);
            GridPoint2 p = candidates.get(0);
            maze.addLadder(new Ladder(p.x, p.y, Ladder.LadderType.DOWN, Ladder.EntranceStyle.SINKHOLE));
        }
        if (forcedUpLadderPos != null) {
            maze.addLadder(new Ladder(forcedUpLadderPos.x, forcedUpLadderPos.y, Ladder.LadderType.UP, Ladder.EntranceStyle.ROPE));
        }
    }

    private void spawnTransitionGates(Maze maze, GridPoint2 chunkId) {
        int w = CHUNK_SIZE, h = CHUNK_SIZE;
        addGate(maze, w / 2, h - 1, new GridPoint2(chunkId.x, chunkId.y + 1), new GridPoint2(w / 2, 1), Door.Orientation.NORTH_SOUTH);
        addGate(maze, w / 2, 0, new GridPoint2(chunkId.x, chunkId.y - 1), new GridPoint2(w / 2, h - 2), Door.Orientation.NORTH_SOUTH);
        addGate(maze, w - 1, h / 2, new GridPoint2(chunkId.x + 1, chunkId.y), new GridPoint2(1, h / 2), Door.Orientation.EAST_WEST);
        addGate(maze, 0, h / 2, new GridPoint2(chunkId.x - 1, chunkId.y), new GridPoint2(w - 2, h / 2), Door.Orientation.EAST_WEST);
    }

    private static void addGate(Maze maze, int x, int y, GridPoint2 target, GridPoint2 targetPos, Door.Orientation o) {
        Gate gate = new Gate(x, y, target, targetPos);
        gate.setOrientation(o);
        maze.addGate(gate);
    }

    private void findPlayerStart() {
        // In the castle chunk, arrive at the gate; elsewhere, the middle clearing.
        if (castleDoorstep != null) {
            playerSpawnPoint.set(castleDoorstep.x, castleDoorstep.y);
            return;
        }
        int ly = CHUNK_SIZE - 1 - MID;
        if (isTraversable(finalLayout[ly].charAt(MID))) {
            playerSpawnPoint.set(MID, MID);
            return;
        }
        for (int y = 0; y < CHUNK_SIZE; y++) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                if (isTraversable(finalLayout[CHUNK_SIZE - 1 - y].charAt(x))) {
                    playerSpawnPoint.set(x, y);
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private List<GridPoint2> openTiles(Maze maze, Set<GridPoint2> reachable) {
        List<GridPoint2> out = new ArrayList<>();
        for (GridPoint2 tile : reachable) {
            int ly = CHUNK_SIZE - 1 - tile.y;
            if (ly < 0 || ly >= finalLayout.length || finalLayout[ly].charAt(tile.x) != '.') continue;
            if (maze.getScenery().containsKey(tile) || maze.getItems().containsKey(tile)) continue;
            if (maze.getGateAt(tile.x, tile.y) != null || maze.getEventAt(tile.x, tile.y) != null) continue;
            out.add(tile);
        }
        // Reachable is a HashSet; sort so a seed always gives the same chunk.
        out.sort(Comparator.comparingInt((GridPoint2 p) -> p.y).thenComparingInt(p -> p.x));
        return out;
    }

    private GridPoint2 nearestOpen(Maze maze, GridPoint2 from) {
        for (int r = 1; r < 4; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int x = from.x + dx, y = from.y + dy;
                    if (isStandable(maze, x, y) && !maze.getItems().containsKey(new GridPoint2(x, y))) {
                        return new GridPoint2(x, y);
                    }
                }
            }
        }
        return null;
    }

    private boolean nearCamp(GridPoint2 pt, int radius) {
        for (GridPoint2 c : campSites) {
            if (Math.abs(pt.x - c.x) <= radius && Math.abs(pt.y - c.y) <= radius) return true;
        }
        return false;
    }

    private boolean isStandable(Maze maze, int x, int y) {
        return x > 0 && x < CHUNK_SIZE - 1 && y > 0 && y < CHUNK_SIZE - 1
                && !maze.isWall(x, y)
                && !maze.getScenery().containsKey(new GridPoint2(x, y));
    }

    private boolean facesOpenGround(int x, int y) {
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};
        for (int i = 0; i < 4; i++) {
            int nx = x + dx[i], ny = y + dy[i];
            if (nx >= 0 && nx < CHUNK_SIZE && ny >= 0 && ny < CHUNK_SIZE && !isWallAt(nx, ny)) return true;
        }
        return false;
    }

    private boolean isWallAt(int x, int y) {
        if (finalLayout == null) return false;
        int ly = CHUNK_SIZE - 1 - y;
        if (ly < 0 || ly >= CHUNK_SIZE || x < 0 || x >= CHUNK_SIZE) return true;
        return isWall(finalLayout[ly].charAt(x));
    }

    private void loadTextureSafely(Scenery s, String path, AssetManager assetManager) {
        if (path == null) return;
        s.setTexturePath(path);
        Texture tex = textureCache.get(path);
        if (tex != null) {
            s.setTexture(tex);
            return;
        }
        try {
            if (assetManager != null) {
                if (!assetManager.isLoaded(path) && Gdx.files != null && Gdx.files.internal(path).exists()) {
                    assetManager.load(path, Texture.class);
                    assetManager.finishLoadingAsset(path);
                }
                if (assetManager.isLoaded(path)) {
                    tex = assetManager.get(path, Texture.class);
                    textureCache.put(path, tex);
                    s.setTexture(tex);
                }
            }
        } catch (Exception ignored) {
            // Headless unit tests without a LibGDX context.
        }
    }

    private Maze createMazeFromText(int level, String[] layout) {
        int height = layout.length;
        int width = layout[0].length();
        int[][] data = new int[height][width];
        Maze maze = new Maze(level, data);
        for (int y = 0; y < height; y++) {
            int ly = height - 1 - y;
            for (int x = 0; x < width; x++) {
                if (isWall(layout[ly].charAt(x))) {
                    data[y][x] = 0b01010101;
                    continue;
                }
                int mask = 0;
                if (y + 1 < height) {
                    char n = layout[ly - 1].charAt(x);
                    if (isWall(n)) { mask |= 0b01000000; if (n == 'M') mask |= 2048; }
                }
                if (x + 1 < width) {
                    char n = layout[ly].charAt(x + 1);
                    if (isWall(n)) { mask |= 0b00000100; if (n == 'M') mask |= 512; }
                }
                if (y > 0) {
                    char n = layout[ly + 1].charAt(x);
                    if (isWall(n)) { mask |= 0b00010000; if (n == 'M') mask |= 1024; }
                }
                if (x > 0) {
                    char n = layout[ly].charAt(x - 1);
                    if (isWall(n)) { mask |= 0b00000001; if (n == 'M') mask |= 256; }
                }
                data[y][x] = mask;
            }
        }
        return maze;
    }

    /** Tiles walkable from the arrival point, honouring walls and blocking scenery. */
    Set<GridPoint2> computeReachableTiles(Maze maze) {
        Set<GridPoint2> component = new HashSet<>();
        GridPoint2 seed = startTileFor(maze);
        if (seed == null) return component;
        Deque<GridPoint2> queue = new ArrayDeque<>();
        component.add(seed);
        queue.add(seed);
        while (!queue.isEmpty()) {
            GridPoint2 cur = queue.poll();
            for (Direction dir : Direction.values()) {
                if ((maze.getWallDataAt(cur.x, cur.y) & dir.getWallMask()) != 0) continue;
                int nx = cur.x + (int) dir.getVector().x;
                int ny = cur.y + (int) dir.getVector().y;
                if (nx < 0 || nx >= maze.getWidth() || ny < 0 || ny >= maze.getHeight()) continue;
                GridPoint2 next = new GridPoint2(nx, ny);
                if (component.contains(next)) continue;
                if (!isTraversable(finalLayout[maze.getHeight() - 1 - ny].charAt(nx))) continue;
                Scenery s = maze.getScenery().get(next);
                if (s != null && s.isImpassable()) continue;
                component.add(next);
                queue.add(next);
            }
        }
        return component;
    }

    private GridPoint2 startTileFor(Maze maze) {
        if (castleDoorstep != null) return new GridPoint2(castleDoorstep);
        for (int r = 0; r < MID; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int x = MID + dx, y = MID + dy;
                    if (x <= 0 || y <= 0 || x >= CHUNK_SIZE - 1 || y >= CHUNK_SIZE - 1 || maze.isWall(x, y)) continue;
                    Scenery s = maze.getScenery().get(new GridPoint2(x, y));
                    if (s == null || !s.isImpassable()) return new GridPoint2(x, y);
                }
            }
        }
        return null;
    }

    private static boolean isMaze(int x, int y) {
        return Math.max(Math.abs(x), Math.abs(y)) <= WorldConstants.CENTRAL_MAZE_RADIUS;
    }

    private static boolean isWall(char c) {
        return c == '#' || c == 'M';
    }

    private static boolean isTraversable(char c) {
        return !isWall(c);
    }
}
