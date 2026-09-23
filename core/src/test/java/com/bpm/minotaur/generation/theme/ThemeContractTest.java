package com.bpm.minotaur.generation.theme;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.liquid.LiquidManager;
import com.bpm.minotaur.gamedata.prop.PropCatalog;
import com.bpm.minotaur.gamedata.prop.PropDefinition;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Enforces the seven-slot contract from
 * {@code docs/DEsign/Themed Chunk Contract.md}.
 *
 * <p>Two halves, deliberately. Data assertions alone would pass a theme whose
 * decorator silently places nothing; output assertions alone would pass a theme
 * pointing at PNGs that are not on disk. Both are required.
 */
public class ThemeContractTest {

    private static final int CHUNK = 32;

    @Before
    public void setUp() {
        PropCatalog.resetInstance();
        ThemeDataManager.resetInstance();
    }

    // ------------------------------------------------------------------
    // Data half
    // ------------------------------------------------------------------

    @Test
    public void everyThemeHasADefinition() {
        for (ChunkTheme theme : ChunkTheme.values()) {
            assertNotNull("Theme " + theme + " has no entry in themes.json",
                    ThemeDataManager.getInstance().get(theme));
        }
    }

    @Test
    public void everyThemeFillsAllSevenContractSlots() {
        for (ChunkTheme theme : ChunkTheme.values()) {
            ThemeDefinition def = ThemeDataManager.getInstance().get(theme);
            assertNotNull(theme + ": no definition", def);

            // (b) prop dressing
            assertFalse(theme + ": slot (b) prop table is empty", def.getProps().isEmpty());
            assertTrue(theme + ": slot (b) prop density must be > 0", def.getPropDensity() > 0f);

            // (c) hazard
            assertNotNull(theme + ": slot (c) declares no hazard", def.getHazard());

            // (d) encounter table
            assertFalse(theme + ": slot (d) encounter table is empty", def.getMonsters().isEmpty());

            // (e) objective
            assertNotNull(theme + ": slot (e) declares no objective", def.getObjective());

            // (f) reward
            assertTrue(theme + ": slot (f) must award at least one Crest", def.getCrestAward() >= 1);

            // (g) sensory signature
            assertNotNull(theme + ": slot (g) declares no fog tint", def.getFogTint());
            assertNotNull(theme + ": slot (g) declares no entry stinger", def.getStinger());
            assertNotNull(theme + ": slot (g) declares no rune texture", def.getRuneTexture());
            assertNotNull(theme + ": enum carries no rune colour", theme.getRuneColor());
        }
    }

    @Test
    public void everyReferencedPropResolvesInTheCatalogue() {
        for (ChunkTheme theme : ChunkTheme.values()) {
            ThemeDefinition def = ThemeDataManager.getInstance().get(theme);
            if (def == null) continue;
            for (ThemeDefinition.PropEntry entry : def.getProps()) {
                assertTrue(theme + " references unknown propId '" + entry.propId + "'",
                        PropCatalog.getInstance().contains(entry.propId));
            }
        }
    }

    @Test
    public void objectiveMarkerPropsExist() {
        // Objectives place these by id; a typo would silently produce a chunk
        // whose objective cannot be completed, and every themed chunk is sealed.
        for (String id : new String[]{"grave_mound", "heart_bloom", "drowned_cache", "bramble"}) {
            assertTrue("Objective-critical prop '" + id + "' is missing from props.json",
                    PropCatalog.getInstance().contains(id));
        }
    }

    @Test
    public void everyCatalogueAssetExistsOnDisk() {
        for (PropDefinition def : PropCatalog.getInstance().all().values()) {
            assertNotNull(def.getId() + " has no asset path", def.getAsset());
            assertTrue("Prop '" + def.getId() + "' points at a missing file: " + def.getAsset(),
                    assetExists(def.getAsset()));
        }
    }

    @Test
    public void everyRuneSigilExistsOnDisk() {
        for (ChunkTheme theme : ChunkTheme.values()) {
            ThemeDefinition def = ThemeDataManager.getInstance().get(theme);
            if (def == null || def.getRuneTexture() == null) continue;
            assertTrue(theme + " rune sigil missing: " + def.getRuneTexture(),
                    assetExists(def.getRuneTexture()));
        }
    }

    @Test
    public void everyStingerExistsOnDisk() {
        for (ChunkTheme theme : ChunkTheme.values()) {
            ThemeDefinition def = ThemeDataManager.getInstance().get(theme);
            if (def == null || def.getStinger() == null) continue;
            assertTrue(theme + " stinger missing: " + def.getStinger(),
                    assetExists(def.getStinger()));
        }
    }

    // ------------------------------------------------------------------
    // Output half
    // ------------------------------------------------------------------

    @Test
    public void decoratingPlacesPropsForEveryTheme() {
        for (ChunkTheme theme : ChunkTheme.values()) {
            Maze maze = decorate(theme, 1234L);
            assertFalse(theme + " decorated to zero props", maze.getScenery().isEmpty());
        }
    }

    @Test
    public void decoratingSpawnsMonstersForEveryTheme() {
        for (ChunkTheme theme : ChunkTheme.values()) {
            Maze maze = decorate(theme, 99L);
            assertFalse(theme + " decorated to zero monsters", maze.getMonsters().isEmpty());
        }
    }

    @Test
    public void decoratingInstallsACompletableObjective() {
        for (ChunkTheme theme : ChunkTheme.values()) {
            Maze maze = decorate(theme, 7L);
            ThemeObjectiveState state = maze.getThemeObjective();

            assertNotNull(theme + " has no objective state", state);
            assertNotNull(theme + " objective has no kind", state.getKind());
            assertFalse(theme + " starts already completed", state.isCompleted());
            assertTrue(theme + " requires a non-positive count", state.getRequired() >= 1);
        }
    }

    @Test
    public void championThemesPlaceAChampion() {
        for (ChunkTheme theme : ChunkTheme.values()) {
            ThemeDefinition def = ThemeDataManager.getInstance().get(theme);
            if (def == null || def.getObjective() != ThemeObjectiveKind.SLAY_CHAMPION) continue;

            Maze maze = decorate(theme, 31L);
            boolean found = false;
            for (com.bpm.minotaur.gamedata.monster.Monster m : maze.getMonsters().values()) {
                if (m != null && m.isThemeChampion()) {
                    found = true;
                    break;
                }
            }
            assertTrue(theme + " declares SLAY_CHAMPION but placed no champion", found);
        }
    }

    @Test
    public void graveyardObjectivePlacesItsMarkersAndAnInvulnerableGravedigger() {
        Maze maze = decorate(ChunkTheme.MAKESHIFT_GRAVEYARD, 11L);

        int markers = 0;
        for (Scenery s : maze.getScenery().values()) {
            if (s != null && s.isObjectiveMarker()) markers++;
        }
        assertTrue("Graveyard placed no objective markers", markers >= 1);
        assertEquals("Objective must require exactly the markers that exist",
                markers, maze.getThemeObjective().getRequired());

        boolean gravedigger = false;
        for (com.bpm.minotaur.gamedata.monster.Monster m : maze.getMonsters().values()) {
            if (m != null && m.isInvulnerable()) gravedigger = true;
        }
        assertTrue("Gravedigger must be invulnerable: the chunk is sealed behind him", gravedigger);
    }

    @Test
    public void everyThemeSealsItsGates() {
        for (ChunkTheme theme : ChunkTheme.values()) {
            Maze maze = decorate(theme, 5L);
            for (com.bpm.minotaur.gamedata.Gate gate : maze.getGates().values()) {
                assertTrue(theme + " left a gate unsealed", gate.isLocked());
            }
        }
    }

    @Test
    public void decoratingNeverBuriesTheDownLadder() {
        // The carve is a destructive post-pass; burying the descent would strand
        // the player on the floor. See contract section 8.
        for (ChunkTheme theme : ChunkTheme.values()) {
            for (long seed = 0; seed < 12; seed++) {
                Maze maze = decorate(theme, seed);

                GridPoint2 ladder = null;
                for (java.util.Map.Entry<GridPoint2, com.bpm.minotaur.gamedata.Ladder> e
                        : maze.getLadders().entrySet()) {
                    if (e.getValue().getType() == com.bpm.minotaur.gamedata.Ladder.LadderType.DOWN) {
                        ladder = e.getKey();
                    }
                }
                assertNotNull(theme + " seed " + seed + ": down ladder vanished", ladder);
                assertFalse(theme + " seed " + seed + ": down ladder is inside a wall",
                        maze.isWall(ladder.x, ladder.y));
                assertTrue(theme + " seed " + seed + ": down ladder is walled in",
                        reachableFromAnyOpenNeighbour(maze, ladder));
            }
        }
    }

    @Test
    public void impassablePropsNeverSealTheChunkOff() {
        // A prop that blocks the only corridor would wall the player away from
        // the objective, in a chunk whose gates are sealed behind them.
        for (ChunkTheme theme : ChunkTheme.values()) {
            for (long seed = 0; seed < 8; seed++) {
                Maze maze = decorate(theme, seed);

                GridPoint2 start = firstPassableTile(maze);
                assertNotNull(theme + " seed " + seed + ": chunk has no passable tile", start);

                Set<GridPoint2> reachable = floodFill(maze, start);
                int passable = countPassable(maze);

                // Allow a few genuinely isolated pockets from the underlying
                // generator, but the chunk must stay overwhelmingly connected.
                // A loose bar here would pass a prop density that walls the
                // player away from an objective they cannot leave without.
                assertTrue(theme + " seed " + seed + ": props fragmented the chunk ("
                                + reachable.size() + "/" + passable + " reachable)",
                        reachable.size() >= passable * 0.90);
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Builds a plain open chunk with walls on the border and a down ladder. */
    private Maze buildBlankChunk() {
        int[][] wallData = new int[CHUNK][CHUNK];
        for (int y = 0; y < CHUNK; y++) {
            for (int x = 0; x < CHUNK; x++) {
                boolean border = (x == 0 || y == 0 || x == CHUNK - 1 || y == CHUNK - 1);
                wallData[y][x] = border ? 1 : 0;
            }
        }

        Maze maze = new Maze(3, wallData);
        maze.setLiquidManager(new LiquidManager());

        // The ladder is deliberately placed where the Colosseum carve wants to
        // raise a pillar (minX + pillarInset for a 32-tile chunk). Parking it in
        // a quiet corner would let this whole suite pass without ever exercising
        // the guard rail it exists to prove.
        maze.addLadder(new com.bpm.minotaur.gamedata.Ladder(
                10, 10, com.bpm.minotaur.gamedata.Ladder.LadderType.DOWN));
        maze.addGate(new com.bpm.minotaur.gamedata.Gate(
                CHUNK / 2, CHUNK - 2, new GridPoint2(1, 0), new GridPoint2(2, 2)));
        return maze;
    }

    /** Confirms the guard rail is actually load-bearing for this layout. */
    @Test
    public void theLadderSitsWhereACarveWouldOtherwiseBuryIt() {
        Maze plain = buildBlankChunk();
        GridPoint2 ladder = null;
        for (GridPoint2 pt : plain.getLadders().keySet()) {
            ladder = pt;
        }
        assertNotNull(ladder);

        ChunkThemeDecorator.Rect arena = ChunkThemeDecorator.centralRect(plain, 0.20f);
        assertTrue("Test fixture no longer places the ladder inside the carve rect; "
                        + "the ladder-burial guard is going untested",
                arena.contains(ladder.x, ladder.y));
    }

    private Maze decorate(ChunkTheme theme, long seed) {
        Maze maze = buildBlankChunk();
        ChunkThemeDecorator.decorate(maze, theme, seed, null, null, null);
        return maze;
    }

    private boolean reachableFromAnyOpenNeighbour(Maze maze, GridPoint2 pt) {
        return !maze.isWall(pt.x + 1, pt.y) || !maze.isWall(pt.x - 1, pt.y)
                || !maze.isWall(pt.x, pt.y + 1) || !maze.isWall(pt.x, pt.y - 1);
    }

    private GridPoint2 firstPassableTile(Maze maze) {
        for (int y = 1; y < maze.getHeight() - 1; y++) {
            for (int x = 1; x < maze.getWidth() - 1; x++) {
                if (isOpen(maze, x, y)) return new GridPoint2(x, y);
            }
        }
        return null;
    }

    private boolean isOpen(Maze maze, int x, int y) {
        if (maze.isWall(x, y)) return false;
        Scenery prop = maze.getScenery().get(new GridPoint2(x, y));
        return prop == null || !prop.isImpassable();
    }

    private int countPassable(Maze maze) {
        int count = 0;
        for (int y = 1; y < maze.getHeight() - 1; y++) {
            for (int x = 1; x < maze.getWidth() - 1; x++) {
                if (isOpen(maze, x, y)) count++;
            }
        }
        return count;
    }

    private Set<GridPoint2> floodFill(Maze maze, GridPoint2 start) {
        Set<GridPoint2> seen = new HashSet<>();
        Deque<GridPoint2> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        while (!queue.isEmpty()) {
            GridPoint2 cur = queue.poll();
            for (int i = 0; i < 4; i++) {
                int nx = cur.x + dx[i];
                int ny = cur.y + dy[i];
                GridPoint2 next = new GridPoint2(nx, ny);
                if (seen.contains(next)) continue;
                if (!isOpen(maze, nx, ny)) continue;
                seen.add(next);
                queue.add(next);
            }
        }
        return seen;
    }

    private boolean assetExists(String path) {
        return new File("assets/" + path).exists() || new File("../assets/" + path).exists();
    }
}
