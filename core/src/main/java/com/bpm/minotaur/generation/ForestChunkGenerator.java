package com.bpm.minotaur.generation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;

/**
 * A simple placeholder generator for the FOREST biome.
 * It creates an empty, walled-in room with transition gates.
 */
public class ForestChunkGenerator implements IChunkGenerator {

    @Override
    public Maze generateChunk(GridPoint2 chunkId, int level, Difficulty difficulty, GameMode gameMode) {
        Gdx.app.log("ForestChunkGenerator", "Generating new FOREST chunk at " + chunkId);

        // 1. Create a simple 20x20 empty maze
        int[][] wallData = new int[Maze.MAZE_HEIGHT][Maze.MAZE_WIDTH];
        Maze maze = new Maze(level, wallData);

        // 2. Build the outer walls
        for (int y = 0; y < maze.getHeight(); y++) {
            for (int x = 0; x < maze.getWidth(); x++) {
                if (y == 0) { // Bottom wall
                    wallData[y][x] |= 0b00010000; // Wall South
                } else if (y == maze.getHeight() - 1) { // Top wall
                    wallData[y][x] |= 0b01000000; // Wall North
                }

                if (x == 0) { // Left wall
                    wallData[y][x] |= 0b00000001; // Wall West
                } else if (x == maze.getWidth() - 1) { // Right wall
                    wallData[y][x] |= 0b00000100; // Wall East
                }
            }
        }

        // 3. Add transition gates (only in ADVANCED mode)
        if (gameMode == GameMode.ADVANCED) {
            spawnTransitionGates(maze, chunkId);
        }

        // 4. (Optional) Spawn some "foresty" entities
        // SpawnManager spawnManager = new SpawnManager(maze, difficulty, level, ...);
        // spawnManager.spawnForestEntities(); // A new method we could add later

        return maze;
    }

    @Override
    public GridPoint2 getInitialPlayerStartPos() {
        // This generator is not used for starting, so just return a valid default
        return new GridPoint2(Maze.MAZE_WIDTH / 2, Maze.MAZE_HEIGHT / 2);
    }

    /**
     * Spawns four transition gates for ADVANCED mode.
     * (Copied from MazeChunkGenerator)
     */
    private void spawnTransitionGates(Maze maze, GridPoint2 chunkId) {
        int height = maze.getHeight();
        int width = maze.getWidth();

        // Hardcoded positions (middle of each wall)
        GridPoint2 northPos = new GridPoint2(width / 2, height - 1);
        GridPoint2 southPos = new GridPoint2(width / 2, 0);
        GridPoint2 eastPos = new GridPoint2(width - 1, height / 2);
        GridPoint2 westPos = new GridPoint2(0, height / 2);

        // Define where gates lead
        GridPoint2 northTargetChunk = new GridPoint2(chunkId.x, chunkId.y + 1);
        GridPoint2 southTargetChunk = new GridPoint2(chunkId.x, chunkId.y - 1);
        GridPoint2 eastTargetChunk = new GridPoint2(chunkId.x + 1, chunkId.y);
        GridPoint2 westTargetChunk = new GridPoint2(chunkId.x - 1, chunkId.y);

        // Define where player appears in the new chunk
        GridPoint2 northTargetPlayer = new GridPoint2(width / 2, 1);
        GridPoint2 southTargetPlayer = new GridPoint2(width / 2, height - 2);
        GridPoint2 eastTargetPlayer = new GridPoint2(1, height / 2);
        GridPoint2 westTargetPlayer = new GridPoint2(width - 2, height / 2);

        // Add the four gates
        maze.addGate(new Gate(northPos.x, northPos.y, northTargetChunk, northTargetPlayer));
        maze.addGate(new Gate(southPos.x, southPos.y, southTargetChunk, southTargetPlayer));
        maze.addGate(new Gate(eastPos.x, eastPos.y, eastTargetChunk, eastTargetPlayer));
        maze.addGate(new Gate(westPos.x, westPos.y, westTargetChunk, westTargetPlayer));
    }
}
