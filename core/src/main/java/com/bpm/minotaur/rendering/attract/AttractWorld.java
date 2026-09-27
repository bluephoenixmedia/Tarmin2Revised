package com.bpm.minotaur.rendering.attract;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.rendering.mesh.ChunkMeshBuilder;

/**
 * 100-chunk macro-world generator and data container for the Attract Mode 3D flyover.
 * Arranges the 10x10 chunk grid as a Concentric Kingdom:
 * Central Castle Citadel & High Spire surrounded by Lakelands (East), Forest (North),
 * Mountains (West), Desert Canyons (South), and Ocean perimeter cliffs.
 */
public class AttractWorld {

    public static final int GRID_SIZE = 10; // 10x10 chunks = 100 chunks
    public static final int CHUNK_TILES = 36; // 36x36 tiles per chunk

    private final Maze[][] chunks = new Maze[GRID_SIZE][GRID_SIZE];

    public AttractWorld() {
        generateWorld();
    }

    private void generateWorld() {
        for (int cy = 0; cy < GRID_SIZE; cy++) {
            for (int cx = 0; cx < GRID_SIZE; cx++) {
                Biome biome = getBiomeForChunk(cx, cy);
                int[][] wallData = new int[CHUNK_TILES][CHUNK_TILES];
                Maze maze = new Maze(1, wallData);
                maze.setBiome(biome);

                populateChunk(cx, cy, biome, maze);
                chunks[cy][cx] = maze;
            }
        }
    }

    public Biome getBiomeForChunk(int cx, int cy) {
        // Outer Rim (Ocean / perimeter abyss)
        if (cx == 0 || cx == GRID_SIZE - 1 || cy == 0 || cy == GRID_SIZE - 1) {
            return Biome.OCEAN;
        }

        // Center Citadel (Chunks 4,4; 4,5; 5,4; 5,5)
        if ((cx == 4 || cx == 5) && (cy == 4 || cy == 5)) {
            return Biome.MAZE;
        }

        // Cardinal Biome Sectors
        if (cy >= 6 && cx >= 2 && cx <= 7) {
            return Biome.FOREST; // North
        }
        if (cx >= 6 && cy >= 2 && cy <= 7) {
            return Biome.LAKELANDS; // East
        }
        if (cy <= 3 && cx >= 2 && cx <= 7) {
            return Biome.DESERT; // South
        }
        if (cx <= 3 && cy >= 2 && cy <= 7) {
            return Biome.MOUNTAINS; // West
        }

        // Intermediate corner quadrants blend into neighboring terrain
        if (cx <= 3 && cy >= 6) return Biome.FOREST;
        if (cx >= 6 && cy >= 6) return Biome.LAKELANDS;
        if (cx <= 3 && cy <= 3) return Biome.MOUNTAINS;
        return Biome.DESERT;
    }

    private void populateChunk(int cx, int cy, Biome biome, Maze maze) {
        switch (biome) {
            case MAZE:
                populateCitadelChunk(cx, cy, maze);
                break;
            case LAKELANDS:
                populateLakelandsChunk(cx, cy, maze);
                break;
            case FOREST:
                populateForestChunk(cx, cy, maze);
                break;
            case DESERT:
                populateDesertChunk(cx, cy, maze);
                break;
            case MOUNTAINS:
                populateMountainsChunk(cx, cy, maze);
                break;
            case OCEAN:
            default:
                populateOceanChunk(cx, cy, maze);
                break;
        }
    }

    private void populateCitadelChunk(int cx, int cy, Maze maze) {
        // Moat water around the outer perimeter of the 2x2 citadel block
        boolean isWestEdge = (cx == 4);
        boolean isEastEdge = (cx == 5);
        boolean isSouthEdge = (cy == 4);
        boolean isNorthEdge = (cy == 5);

        for (int y = 0; y < CHUNK_TILES; y++) {
            for (int x = 0; x < CHUNK_TILES; x++) {
                // Moat trench 3 tiles wide on outer perimeter
                if ((isWestEdge && x < 3) || (isEastEdge && x >= CHUNK_TILES - 3)
                        || (isSouthEdge && y < 3) || (isNorthEdge && y >= CHUNK_TILES - 3)) {
                    maze.getLiquidManager().setLiquidAt(x, y, LiquidType.WATER);
                } else if ((isWestEdge && x == 3) || (isEastEdge && x == CHUNK_TILES - 4)
                        || (isSouthEdge && y == 3) || (isNorthEdge && y == CHUNK_TILES - 4)) {
                    // Outer stone curtain wall
                    // Leave gap for fortress gate on South at x=18..19
                    if (!(isSouthEdge && (x == 17 || x == 18 || x == 19))) {
                        maze.setTile(x, y, ChunkMeshBuilder.WALL_NORTH | ChunkMeshBuilder.WALL_SOUTH |
                                ChunkMeshBuilder.WALL_EAST | ChunkMeshBuilder.WALL_WEST);
                    }
                }
            }
        }
    }

    private void populateLakelandsChunk(int cx, int cy, Maze maze) {
        // Organic water pools and lakes
        float scale = 0.18f;
        for (int y = 0; y < CHUNK_TILES; y++) {
            for (int x = 0; x < CHUNK_TILES; x++) {
                float wx = (cx * CHUNK_TILES + x) * scale;
                float wy = (cy * CHUNK_TILES + y) * scale;
                float n = (float) (Math.sin(wx) * Math.cos(wy) + Math.sin(wx * 0.5f + wy * 0.7f));
                if (n > 0.15f) {
                    maze.getLiquidManager().setLiquidAt(x, y, LiquidType.WATER);
                } else if (n < -1.4f) {
                    // Scattered sunken stone ruins
                    maze.setTile(x, y, ChunkMeshBuilder.WALL_NORTH | ChunkMeshBuilder.WALL_WEST);
                }
            }
        }
    }

    private void populateForestChunk(int cx, int cy, Maze maze) {
        // Ancient moss cliff formations
        float scale = 0.14f;
        for (int y = 0; y < CHUNK_TILES; y++) {
            for (int x = 0; x < CHUNK_TILES; x++) {
                float wx = (cx * CHUNK_TILES + x) * scale;
                float wy = (cy * CHUNK_TILES + y) * scale;
                float n = (float) (Math.sin(wx * 0.8f) + Math.cos(wy * 0.8f));
                if (n > 1.35f) {
                    // Mossy cliff ridge
                    maze.setTile(x, y, ChunkMeshBuilder.WALL_NORTH | ChunkMeshBuilder.WALL_SOUTH |
                            ChunkMeshBuilder.WALL_EAST | ChunkMeshBuilder.WALL_WEST);
                }
            }
        }
    }

    private void populateDesertChunk(int cx, int cy, Maze maze) {
        // Winding sandstone canyon walls
        float scale = 0.12f;
        for (int y = 0; y < CHUNK_TILES; y++) {
            for (int x = 0; x < CHUNK_TILES; x++) {
                float wx = (cx * CHUNK_TILES + x) * scale;
                float wy = (cy * CHUNK_TILES + y) * scale;
                float n = (float) (Math.cos(wx * 0.7f - wy * 0.7f) + Math.sin(wy * 1.1f));
                if (n > 1.4f) {
                    maze.setTile(x, y, ChunkMeshBuilder.WALL_NORTH | ChunkMeshBuilder.WALL_SOUTH |
                            ChunkMeshBuilder.WALL_EAST | ChunkMeshBuilder.WALL_WEST);
                }
            }
        }
    }

    private void populateMountainsChunk(int cx, int cy, Maze maze) {
        // Craggy mountain ridges
        float scale = 0.20f;
        for (int y = 0; y < CHUNK_TILES; y++) {
            for (int x = 0; x < CHUNK_TILES; x++) {
                float wx = (cx * CHUNK_TILES + x) * scale;
                float wy = (cy * CHUNK_TILES + y) * scale;
                float n = (float) (Math.sin(wx * 1.2f) * Math.sin(wy * 1.2f) + Math.cos(wx * 0.5f));
                if (n > 0.85f) {
                    maze.setTile(x, y, ChunkMeshBuilder.WALL_NORTH | ChunkMeshBuilder.WALL_SOUTH |
                            ChunkMeshBuilder.WALL_EAST | ChunkMeshBuilder.WALL_WEST);
                }
            }
        }
    }

    private void populateOceanChunk(int cx, int cy, Maze maze) {
        // Outer ocean water
        for (int y = 0; y < CHUNK_TILES; y++) {
            for (int x = 0; x < CHUNK_TILES; x++) {
                maze.getLiquidManager().setLiquidAt(x, y, LiquidType.WATER);
            }
        }
    }

    public Maze getChunk(int cx, int cy) {
        if (cx < 0 || cx >= GRID_SIZE || cy < 0 || cy >= GRID_SIZE) {
            return null;
        }
        return chunks[cy][cx];
    }

    public int getTotalChunkCount() {
        return GRID_SIZE * GRID_SIZE;
    }
}
