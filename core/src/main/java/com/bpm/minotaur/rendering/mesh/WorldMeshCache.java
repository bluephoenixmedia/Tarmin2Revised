package com.bpm.minotaur.rendering.mesh;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.managers.WorldManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages caching, streaming, and GPU disposal of 3D chunk sub-meshes.
 * Supports static level-wide caching for dungeons and sliding 3x3 chunk streaming for overland.
 */
public class WorldMeshCache implements Disposable {

    public static final int CHUNK_SIZE = 16;

    private final Map<String, List<ChunkSubMesh>> cachedChunks = new HashMap<>();
    private Maze currentMaze = null;
    private int currentLevel = -1;
    private GridPoint2 lastCenterChunk = new GridPoint2(Integer.MIN_VALUE, Integer.MIN_VALUE);

    /**
     * Retrieves or builds all visible chunk sub-meshes for the current player location and maze.
     */
    public List<ChunkSubMesh> getVisibleSubMeshes(
            Maze maze,
            float playerX,
            float playerY,
            int level,
            boolean isIndoors,
            Texture wallTexture,
            Texture floorTexture,
            Texture ceilingTexture,
            WorldManager worldManager
    ) {
        List<ChunkSubMesh> result = new ArrayList<>();

        if (maze == null) return result;

        // If level or maze reference changed, invalidate everything
        if (maze != currentMaze || level != currentLevel) {
            invalidate();
            this.currentMaze = maze;
            this.currentLevel = level;
        }

        if (level > 1) {
            // --- DUNGEONS (Level > 1): Static Single-Floor Bake ---
            String dungeonKey = "DUNGEON_L" + level;
            List<ChunkSubMesh> dungeonMeshes = cachedChunks.get(dungeonKey);
            if (dungeonMeshes == null) {
                dungeonMeshes = ChunkMeshBuilder.buildChunk(
                        maze,
                        0, 0, maze.getWidth(), maze.getHeight(),
                        wallTexture, floorTexture, ceilingTexture,
                        true // Dungeons always have ceilings
                );
                cachedChunks.put(dungeonKey, dungeonMeshes);
            }
            result.addAll(dungeonMeshes);
        } else {
            // --- OVERLAND (Level 1): Sliding 3x3 Chunk Streaming ---
            int centerChunkX = (int) Math.floor(playerX / CHUNK_SIZE);
            int centerChunkY = (int) Math.floor(playerY / CHUNK_SIZE);

            GridPoint2 currentCenter = new GridPoint2(centerChunkX, centerChunkY);

            // Determine the 3x3 needed chunk keys
            Set<String> neededKeys = new HashSet<>();
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = centerChunkX + dx;
                    int cy = centerChunkY + dy;
                    neededKeys.add(cx + "_" + cy);
                }
            }

            // Evict and dispose chunks outside the sliding window
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, List<ChunkSubMesh>> entry : cachedChunks.entrySet()) {
                if (!neededKeys.contains(entry.getKey())) {
                    for (ChunkSubMesh subMesh : entry.getValue()) {
                        subMesh.dispose();
                    }
                    toRemove.add(entry.getKey());
                }
            }
            for (String key : toRemove) {
                cachedChunks.remove(key);
            }

            // Build any missing chunks in the 3x3 window
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = centerChunkX + dx;
                    int cy = centerChunkY + dy;
                    String key = cx + "_" + cy;

                    List<ChunkSubMesh> chunkMeshes = cachedChunks.get(key);
                    if (chunkMeshes == null) {
                        int minX = cx * CHUNK_SIZE;
                        int minY = cy * CHUNK_SIZE;
                        int maxX = minX + CHUNK_SIZE;
                        int maxY = minY + CHUNK_SIZE;

                        // Check if this chunk is indoors (e.g. shelter/home)
                        boolean chunkIndoors = isIndoors || maze.isHomeTile(minX + CHUNK_SIZE / 2, minY + CHUNK_SIZE / 2);

                        chunkMeshes = ChunkMeshBuilder.buildChunk(
                                maze,
                                minX, minY, maxX, maxY,
                                wallTexture, floorTexture, ceilingTexture,
                                chunkIndoors
                        );
                        cachedChunks.put(key, chunkMeshes);
                    }
                    result.addAll(chunkMeshes);
                }
            }
            lastCenterChunk.set(centerChunkX, centerChunkY);
        }

        return result;
    }

    /**
     * Clears and disposes all cached GPU meshes.
     */
    public void invalidate() {
        for (List<ChunkSubMesh> subMeshes : cachedChunks.values()) {
            for (ChunkSubMesh subMesh : subMeshes) {
                subMesh.dispose();
            }
        }
        cachedChunks.clear();
        lastCenterChunk.set(Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    @Override
    public void dispose() {
        invalidate();
    }
}
