package com.bpm.minotaur.gamedata.events;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Hides at most one choice event in a freshly generated chunk.
 *
 * <p>Wilderness chunks roll at {@link #WILDERNESS_CHANCE} and maze chunks at {@link #MAZE_CHANCE};
 * the Shelter chunk never gets one. The event is drawn from {@link EventCatalog#pick} and marked
 * seen at once, so the run never places it twice.
 */
public final class EventPlacer {

    static final float WILDERNESS_CHANCE = 0.25f;
    static final float MAZE_CHANCE = 0.15f;

    private EventPlacer() {
    }

    public static float chanceFor(String biome) {
        return "MAZE".equals(biome) ? MAZE_CHANCE : WILDERNESS_CHANCE;
    }

    /**
     * Places an event and returns its id, or null when the roll fails, the chunk is the Shelter,
     * the pool for this biome and depth is spent, or there is no free tile.
     */
    public static String place(Maze maze, String biome, int depth, boolean isShelterChunk,
                               EventCatalog catalog, Collection<String> seen, Random rng) {
        if (isShelterChunk || rng.nextFloat() >= chanceFor(biome)) {
            return null;
        }
        List<GridPoint2> tiles = freeTiles(maze);
        if (tiles.isEmpty()) {
            return null;
        }
        EventDefinition def = catalog.pick(biome, depth, seen, rng);
        if (def == null) {
            return null;
        }
        GridPoint2 tile = tiles.get(rng.nextInt(tiles.size()));
        maze.addChoiceEvent(tile.x, tile.y, def.id);
        seen.add(def.id);
        return def.id;
    }

    /**
     * Open floor with nothing on it: no wall, object, scenery, item, monster or other event.
     *
     * <p>The outer ring is left out. Crossing into a wilderness chunk sets the player's position
     * directly rather than stepping, so an event on an edge tile would be walked over unfired.
     */
    static List<GridPoint2> freeTiles(Maze maze) {
        List<GridPoint2> out = new ArrayList<>();
        for (int y = 1; y < maze.getHeight() - 1; y++) {
            for (int x = 1; x < maze.getWidth() - 1; x++) {
                GridPoint2 p = new GridPoint2(x, y);
                if (maze.isWall(x, y) || !maze.isPassable(x, y) || maze.isHomeTile(x, y)
                        || maze.getGameObjectAt(x, y) != null
                        || maze.getScenery().containsKey(p)
                        || maze.getItems().containsKey(p)
                        || maze.getMonsters().containsKey(p)
                        || maze.getLadders().containsKey(p)
                        || maze.getGates().containsKey(p)
                        || maze.getEventAt(x, y) != null
                        || maze.getChoiceEventAt(x, y) != null) {
                    continue;
                }
                out.add(p);
            }
        }
        return out;
    }
}
