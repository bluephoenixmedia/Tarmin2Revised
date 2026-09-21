package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;

/**
 * The moment a mimic stops being furniture.
 *
 * <p>Items and monsters live in separate tile-keyed maps on the Maze, so a disguised
 * mimic is genuinely an Item and becomes a Monster only here. Both paths that can
 * trigger it -- the player opening the chest, and the player attacking one they have
 * already seen through -- share this swap, so the maze can never end up holding both
 * halves of the creature.
 *
 * <p>Building the Monster is left to the caller, which owns the data managers and the
 * depth to scale it by; this class only does the maze bookkeeping.
 */
public final class MimicReveal {

    private MimicReveal() {
    }

    /**
     * Returns the disguised mimic standing on a tile, or null if that tile holds an
     * ordinary chest, something else, or nothing at all.
     */
    public static Item disguisedMimicAt(Maze maze, GridPoint2 tile) {
        if (maze == null || tile == null) {
            return null;
        }
        Item item = maze.getItems().get(tile);
        return (item != null && item.isMimic()) ? item : null;
    }

    /**
     * Swaps the disguised chest on a tile for a live monster.
     *
     * @return true if a disguise was actually dropped, false if there was no mimic there.
     */
    public static boolean swap(Maze maze, GridPoint2 tile, Monster mimic) {
        if (mimic == null) {
            return false;
        }
        Item disguise = disguisedMimicAt(maze, tile);
        if (disguise == null) {
            return false;
        }

        maze.removeItem(disguise);
        mimic.getPosition().set(tile.x + 0.5f, tile.y + 0.5f);
        maze.addMonster(mimic);
        return true;
    }
}
