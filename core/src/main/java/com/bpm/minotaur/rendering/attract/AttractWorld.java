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
        // Global coordinates within the 72x72 tile 2x2 central citadel complex
        int gxBase = (cx == 4) ? 0 : CHUNK_TILES;
        int gyBase = (cy == 4) ? 0 : CHUNK_TILES;

        for (int y = 0; y < CHUNK_TILES; y++) {
            int gy = gyBase + y;
            for (int x = 0; x < CHUNK_TILES; x++) {
                int gx = gxBase + x;

                // 1. Outer perimeter moat (3 tiles wide of water)
                if (gx < 3 || gx >= 69 || gy < 3 || gy >= 69) {
                    maze.getLiquidManager().setLiquidAt(x, y, LiquidType.WATER);
                    continue;
                }

                // 2. Outer fortress curtain wall with grand cardinal gatehouse archways
                if (gx == 3 || gx == 68 || gy == 3 || gy == 68) {
                    boolean isGate = ((gy == 3 || gy == 68) && (gx >= 34 && gx <= 37))
                            || ((gx == 3 || gx == 68) && (gy >= 34 && gy <= 37));
                    if (!isGate) {
                        maze.setTile(x, y, ChunkMeshBuilder.ALL_WALLS);
                    }
                    continue;
                }

                // 3. Central Grand Ceremonial Avenues (wide open thoroughfares connecting gates)
                if ((gx >= 34 && gx <= 37) || (gy >= 34 && gy <= 37)) {
                    // Central royal fountain at the grand crossroads
                    if (gx >= 34 && gx <= 37 && gy >= 34 && gy <= 37) {
                        maze.getLiquidManager().setLiquidAt(x, y, LiquidType.WATER);
                    }
                    continue;
                }

                // 4. Royal Courtyard Plazas and Colonnades
                if (gx >= 30 && gx <= 41 && gy >= 30 && gy <= 41) {
                    // Corner pillars of the royal courtyard
                    if ((gx == 30 || gx == 41) && (gy == 30 || gy == 41)) {
                        maze.setTile(x, y, ChunkMeshBuilder.ALL_WALLS);
                    }
                    continue;
                }

                // 5. Authentic Stone Castle Labyrinth Corridors and Chambers
                int rx = gx % 4;
                int ry = gy % 4;
                if (rx == 0 || ry == 0) {
                    // Regularly spaced archways keep the maze navigable and open to cinematic flight
                    boolean isArchway = (rx == 0 && ry == 2) || (ry == 0 && rx == 2);
                    if (!isArchway) {
                        maze.setTile(x, y, ChunkMeshBuilder.ALL_WALLS);
                    }
                } else if (rx == 2 && ry == 2 && ((gx / 4 + gy / 4) % 2 == 0)) {
                    // Ornamental stone pillars inside select royal chambers
                    maze.setTile(x, y, ChunkMeshBuilder.ALL_WALLS);
                }
            }
        }
    }

    private void populateLakelandsChunk(int cx, int cy, Maze maze) {
        // Sunken labyrinth: canals, flooded rooms, and ancient ruin walls
        float scale = 0.14f;
        for (int y = 0; y < CHUNK_TILES; y++) {
            for (int x = 0; x < CHUNK_TILES; x++) {
                float wx = (cx * CHUNK_TILES + x) * scale;
                float wy = (cy * CHUNK_TILES + y) * scale;
                float water = (float) (Math.sin(wx * 0.8f) * Math.cos(wy * 0.8f) + Math.cos(wx * 0.4f + wy * 0.5f) * 0.5f);
                if (water > 0.12f) {
                    maze.getLiquidManager().setLiquidAt(x, y, LiquidType.WATER);
                }

                // Keep central canal thoroughfares open
                if ((x >= 16 && x <= 19) || (y >= 16 && y <= 19)) {
                    continue;
                }

                // Sunken stone walls and causeways
                int rx = x % 6;
                int ry = y % 6;
                if ((rx == 0 || ry == 0) && (rx != 3 && ry != 3) && water <= 0.45f) {
                    maze.setTile(x, y, ChunkMeshBuilder.ALL_WALLS);
                }
            }
        }
    }

    private void populateForestChunk(int cx, int cy, Maze maze) {
        // Ancient overgrown woodland labyrinth: winding mossy cliff corridors and clearings
        float scale = 0.13f;
        for (int y = 0; y < CHUNK_TILES; y++) {
            for (int x = 0; x < CHUNK_TILES; x++) {
                // Keep thoroughfares open for smooth inter-chunk camera flight
                if ((x >= 16 && x <= 19) || (y >= 16 && y <= 19)) {
                    continue;
                }
                float wx = (cx * CHUNK_TILES + x) * scale;
                float wy = (cy * CHUNK_TILES + y) * scale;
                float n = (float) (Math.sin(wx * 0.9f) * Math.cos(wy * 0.9f) + Math.sin(wx * 1.5f + wy * 0.6f) * 0.4f);
                if (Math.abs(n) < 0.32f) {
                    maze.setTile(x, y, ChunkMeshBuilder.ALL_WALLS);
                } else if ((x % 6 == 0) && (y % 6 == 0)) {
                    // Ancient standing stones / giant trunk pillars
                    maze.setTile(x, y, ChunkMeshBuilder.ALL_WALLS);
                }
            }
        }
    }

    private void populateDesertChunk(int cx, int cy, Maze maze) {
        // Winding sandstone slot canyons and sun-baked ruins
        float scale = 0.13f;
        for (int y = 0; y < CHUNK_TILES; y++) {
            for (int x = 0; x < CHUNK_TILES; x++) {
                if ((x >= 16 && x <= 19) || (y >= 16 && y <= 19)) {
                    continue;
                }
                float wx = (cx * CHUNK_TILES + x) * scale;
                float wy = (cy * CHUNK_TILES + y) * scale;
                float canyon = (float) (Math.cos(wx * 0.7f - wy * 0.7f) + Math.sin(wx * 1.1f + wy * 0.3f) * 0.4f);
                if (Math.abs(canyon) > 0.48f) {
                    maze.setTile(x, y, ChunkMeshBuilder.ALL_WALLS);
                } else if ((x % 8 == 2 || x % 8 == 6) && (y % 8 == 2 || y % 8 == 6)) {
                    // Buried sandstone colonnade columns
                    maze.setTile(x, y, ChunkMeshBuilder.ALL_WALLS);
                }
            }
        }
    }

    private void populateMountainsChunk(int cx, int cy, Maze maze) {
        // Craggy mountain gorges and rock labyrinths
        float scale = 0.16f;
        for (int y = 0; y < CHUNK_TILES; y++) {
            for (int x = 0; x < CHUNK_TILES; x++) {
                if ((x >= 16 && x <= 19) || (y >= 16 && y <= 19)) {
                    continue;
                }
                float wx = (cx * CHUNK_TILES + x) * scale;
                float wy = (cy * CHUNK_TILES + y) * scale;
                float gorge = (float) (Math.sin(wx * 1.1f) * Math.sin(wy * 1.1f) + Math.cos(wx * 0.6f + wy * 0.6f) * 0.5f);
                if (Math.abs(gorge) > 0.40f) {
                    maze.setTile(x, y, ChunkMeshBuilder.ALL_WALLS);
                }
            }
        }
    }

    private void populateOceanChunk(int cx, int cy, Maze maze) {
        // Outer ocean water and perimeter sea barrier
        for (int y = 0; y < CHUNK_TILES; y++) {
            for (int x = 0; x < CHUNK_TILES; x++) {
                maze.getLiquidManager().setLiquidAt(x, y, LiquidType.WATER);
                if (cx == 0 && x == 0 || cx == GRID_SIZE - 1 && x == CHUNK_TILES - 1
                        || cy == 0 && y == 0 || cy == GRID_SIZE - 1 && y == CHUNK_TILES - 1) {
                    maze.setTile(x, y, ChunkMeshBuilder.ALL_WALLS);
                }
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
