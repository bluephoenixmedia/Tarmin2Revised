package com.bpm.minotaur.rendering.mesh;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.managers.WorldManager;

import com.bpm.minotaur.gamedata.Gate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages caching, streaming, and GPU disposal of 3D chunk sub-meshes.
 * Supports static level-wide caching for dungeons and seamless adjacent sector streaming for overland.
 */
public class WorldMeshCache implements Disposable {

    private final Map<String, List<ChunkSubMesh>> cachedChunks = new HashMap<>();
    private Maze currentMaze = null;
    private int currentLevel = -1;
    private final GridPoint2 lastCenterChunk = new GridPoint2(Integer.MIN_VALUE, Integer.MIN_VALUE);

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

        GridPoint2 currentChunkId = (worldManager != null) ? worldManager.getCurrentPlayerChunkId() : new GridPoint2(0, 0);

        // If level, maze reference, or player sector chunk changed, invalidate and rebuild
        if (maze != currentMaze || level != currentLevel || currentChunkId.x != lastCenterChunk.x || currentChunkId.y != lastCenterChunk.y) {
            invalidate();
            this.currentMaze = maze;
            this.currentLevel = level;
            this.lastCenterChunk.set(currentChunkId.x, currentChunkId.y);
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
                        true, 0f, 0f
                );
                cachedChunks.put(dungeonKey, dungeonMeshes);
            }
            result.addAll(dungeonMeshes);
        } else {
            // --- OVERLAND (Level 1): Active Sector Chunk + Connected Neighbor Chunks ---
            String currentChunkKey = "SECTOR_" + currentChunkId.x + "_" + currentChunkId.y;
            List<ChunkSubMesh> currentMeshes = cachedChunks.get(currentChunkKey);
            if (currentMeshes == null) {
                currentMeshes = ChunkMeshBuilder.buildChunk(
                        maze,
                        0, 0, maze.getWidth(), maze.getHeight(),
                        wallTexture, floorTexture, ceilingTexture,
                        false, 0f, 0f
                );
                cachedChunks.put(currentChunkKey, currentMeshes);
            }
            result.addAll(currentMeshes);

            // Stream adjacent sectors connected via transition gates
            if (worldManager != null && maze.getGates() != null) {
                for (Gate gate : maze.getGates().values()) {
                    if (gate.isChunkTransitionGate()) {
                        GridPoint2 targetId = gate.getTargetChunkId();
                        if (targetId != null) {
                            int dx = targetId.x - currentChunkId.x;
                            int dy = targetId.y - currentChunkId.y;
                            float offsetX = dx * maze.getWidth();
                            float offsetZ = -dy * maze.getHeight();

                            String neighborKey = "NEIGHBOR_" + currentChunkId.x + "_" + currentChunkId.y + "_TO_" + targetId.x + "_" + targetId.y;
                            List<ChunkSubMesh> neighborMeshes = cachedChunks.get(neighborKey);
                            if (neighborMeshes == null) {
                                Maze neighborMaze = worldManager.requestLoadChunk(targetId);
                                if (neighborMaze != null) {
                                    neighborMeshes = ChunkMeshBuilder.buildChunk(
                                            neighborMaze,
                                            0, 0, neighborMaze.getWidth(), neighborMaze.getHeight(),
                                            wallTexture, floorTexture, ceilingTexture,
                                            false, offsetX, offsetZ
                                    );
                                    cachedChunks.put(neighborKey, neighborMeshes);
                                }
                            }
                            if (neighborMeshes != null) {
                                result.addAll(neighborMeshes);
                            }
                        }
                    }
                }
            }
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
