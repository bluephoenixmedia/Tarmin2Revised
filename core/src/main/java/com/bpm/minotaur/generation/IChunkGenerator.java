package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Maze;

/**
 * Interface defining the "contract" for all chunk generators.
 * Each biome (Maze, Forest, etc.) will have its own generator
 * that implements this interface.
 */
public interface IChunkGenerator {

    /**
     * Generates a new Maze object (a "chunk") for a specific coordinate.
     *
     * @param chunkId    The (X,Y) coordinate of the chunk in the world.
     * @param level      The dungeon level.
     * @param difficulty The game difficulty.
     * @param gameMode   The current game mode (CLASSIC or ADVANCED).
     * @return A fully populated Maze object for this biome.
     */
    Maze generateChunk(GridPoint2 chunkId, int level, Difficulty difficulty, GameMode gameMode);

    /**
     * Gets the default player starting position for this generator.
     * This is typically only used by the MAZE generator for the
     * very first chunk the player spawns in.
     *
     * @return A (X,Y) grid coordinate.
     */
    GridPoint2 getInitialPlayerStartPos();
}
