package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.rendering.mesh.ChunkMeshBuilder;
import org.junit.Test;

import static org.junit.Assert.*;

public class ChunkGeometryTest {

    @Test
    public void testDebugManagerRenderEngineToggle() {
        DebugManager dm = DebugManager.getInstance();
        dm.setRenderEngine(DebugManager.RenderEngine.PLANAR_3D);
        assertEquals(DebugManager.RenderEngine.PLANAR_3D, dm.getRenderEngine());

        dm.toggleRenderEngine();
        assertEquals(DebugManager.RenderEngine.RAYCASTER, dm.getRenderEngine());

        dm.toggleRenderEngine();
        assertEquals(DebugManager.RenderEngine.PLANAR_3D, dm.getRenderEngine());
    }

    @Test
    public void testQuadAdditionGeometryAndIndices() {
        FloatArray verts = new FloatArray();
        ShortArray indices = new ShortArray();

        float whitePacked = Color.WHITE.toFloatBits();

        ChunkMeshBuilder.addQuad(
                verts, indices,
                0f, 0f, 0f, 0f, 0f,
                1f, 0f, 0f, 1f, 0f,
                1f, 1f, 0f, 1f, 1f,
                0f, 1f, 0f, 0f, 1f,
                0f, 0f, 1f, whitePacked
        );

        // 4 vertices * 9 floats = 36 floats
        assertEquals(36, verts.size);
        // 2 triangles = 6 indices
        assertEquals(6, indices.size);

        // Verify normal components on first vertex
        assertEquals(0f, verts.get(3), 0.001f); // nx
        assertEquals(0f, verts.get(4), 0.001f); // ny
        assertEquals(1f, verts.get(5), 0.001f); // nz

        // Second quad
        ChunkMeshBuilder.addQuad(
                verts, indices,
                1f, 0f, 0f, 0f, 0f,
                2f, 0f, 0f, 1f, 0f,
                2f, 1f, 0f, 1f, 1f,
                1f, 1f, 0f, 0f, 1f,
                0f, 0f, 1f, whitePacked
        );

        assertEquals(72, verts.size);
        assertEquals(12, indices.size);

        // Index offset for second quad should start at 4
        assertEquals(4, indices.get(6));
        assertEquals(5, indices.get(7));
        assertEquals(6, indices.get(8));
    }

    @Test
    public void testDirectionBitmaskAlignment() {
        // Verify North, South, East, West masks align with Maze wall representations
        assertEquals(ChunkMeshBuilder.WALL_WEST, Direction.WEST.getWallMask());
        assertEquals(ChunkMeshBuilder.WALL_EAST, Direction.EAST.getWallMask());
        assertEquals(ChunkMeshBuilder.WALL_SOUTH, Direction.SOUTH.getWallMask());
        assertEquals(ChunkMeshBuilder.WALL_NORTH, Direction.NORTH.getWallMask());

        assertEquals(ChunkMeshBuilder.DOOR_WEST, Direction.WEST.getWallMask() << 1);
        assertEquals(ChunkMeshBuilder.DOOR_EAST, Direction.EAST.getWallMask() << 1);
        assertEquals(ChunkMeshBuilder.DOOR_SOUTH, Direction.SOUTH.getWallMask() << 1);
        assertEquals(ChunkMeshBuilder.DOOR_NORTH, Direction.NORTH.getWallMask() << 1);
    }

    @Test
    public void testWallDecalClampingAndDirection() {
        com.bpm.minotaur.gamedata.gore.WallDecal decal = new com.bpm.minotaur.gamedata.gore.WallDecal();

        // 1. Decal spawned high in the sky (e.g. Y = 3.5 from upward blood velocity)
        decal.init(4, 7, Direction.EAST, 0.5f, 3.5f, 0.15f, Color.RED, null);
        assertEquals(Direction.EAST, decal.dir);
        assertEquals(0, decal.side);
        // Height must be clamped to prevent floating into the sky above the 1.0 wall
        assertTrue("Decal height should be clamped below 1.0", decal.height <= 1.0f - decal.radius);
        assertTrue("Decal height should be above 0.0", decal.height >= decal.radius);

        // 2. Decal spawned below floor (e.g. Y = -0.5)
        decal.init(4, 7, Direction.NORTH, 0.5f, -0.5f, 0.15f, Color.RED, null);
        assertEquals(Direction.NORTH, decal.dir);
        assertEquals(1, decal.side);
        assertTrue("Decal height should be clamped above floor", decal.height >= decal.radius);
    }

    @Test
    public void testCeilingIndoorsRule() {
        int[][] wallData = new int[5][5];
        Maze overlandMaze = new Maze(1, wallData);
        // Mark (2, 2) as a shelter tile
        java.util.List<com.badlogic.gdx.math.GridPoint2> shelter = new java.util.ArrayList<>();
        shelter.add(new com.badlogic.gdx.math.GridPoint2(2, 2));
        overlandMaze.setHomeTiles(shelter);

        // On Level 1, corridor tile (1, 1) must NOT be indoors (no ceiling)
        assertFalse("Level 1 corridor must not have ceiling", overlandMaze.isIndoors(1, 1));
        // On Level 1, shelter tile (2, 2) MUST be indoors (has ceiling)
        assertTrue("Level 1 shelter must have ceiling", overlandMaze.isIndoors(2, 2));

        // On Level 2 (1 Z level down in the maze), all open tiles MUST be indoors (have ceiling)
        Maze dungeonMaze = new Maze(2, wallData);
        assertTrue("Dungeon corridor must have ceiling", dungeonMaze.isIndoors(1, 1));
        assertTrue("Dungeon tile must have ceiling", dungeonMaze.isIndoors(2, 2));
    }
}
