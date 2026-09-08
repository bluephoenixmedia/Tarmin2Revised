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
}
