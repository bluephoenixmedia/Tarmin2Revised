package com.bpm.minotaur.rendering.mesh;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import com.bpm.minotaur.gamedata.Maze;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds static 3D quad geometry for a maze region/chunk.
 * Constructs separate sub-meshes grouped by texture (walls, floors, ceilings)
 * with native hardware UV tiling and lighting normals.
 */
public class ChunkMeshBuilder {

    // Wall & Door bitmasks matching Maze and FirstPersonRenderer
    public static final int WALL_WEST  = 0b00000001; // 1
    public static final int WALL_EAST  = 0b00000100; // 4
    public static final int WALL_SOUTH = 0b00010000; // 16
    public static final int WALL_NORTH = 0b01000000; // 64
    public static final int ALL_WALLS  = WALL_WEST | WALL_EAST | WALL_SOUTH | WALL_NORTH; // 85

    public static final int DOOR_WEST  = 0b00000010; // 2
    public static final int DOOR_EAST  = 0b00001000; // 8
    public static final int DOOR_SOUTH = 0b00100000; // 32
    public static final int DOOR_NORTH = 0b10000000; // 128
    public static final int ALL_DOORS  = DOOR_WEST | DOOR_EAST | DOOR_SOUTH | DOOR_NORTH;

    public static final VertexAttributes VERTEX_ATTRIBUTES = new VertexAttributes(
            new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0")
    );

    /**
     * Builds the static sub-meshes for the specified rectangle within the maze.
     *
     * @param maze           The active maze
     * @param minX           Chunk min grid X
     * @param minY           Chunk min grid Y
     * @param maxX           Chunk max grid X (exclusive)
     * @param maxY           Chunk max grid Y (exclusive)
     * @param wallTexture    Wall texture with Repeat wrap
     * @param floorTexture   Floor texture with Repeat wrap
     * @param ceilingTexture Ceiling texture with Repeat wrap
     * @param isIndoors      Whether ceilings should be emitted at Y = 1.0
     * @return List of built sub-meshes
     */
    public static List<ChunkSubMesh> buildChunk(
            Maze maze,
            int minX, int minY, int maxX, int maxY,
            Texture wallTexture,
            Texture floorTexture,
            Texture ceilingTexture,
            boolean isIndoors
    ) {
        List<ChunkSubMesh> subMeshes = new ArrayList<>();

        FloatArray wallVerts = new FloatArray();
        ShortArray wallIndices = new ShortArray();

        FloatArray floorVerts = new FloatArray();
        ShortArray floorIndices = new ShortArray();

        FloatArray ceilVerts = new FloatArray();
        ShortArray ceilIndices = new ShortArray();

        float whitePacked = Color.WHITE.toFloatBits();

        int clampedMinX = Math.max(0, minX);
        int clampedMinY = Math.max(0, minY);
        int clampedMaxX = Math.min(maze.getWidth(), maxX);
        int clampedMaxY = Math.min(maze.getHeight(), maxY);

        for (int y = clampedMinY; y < clampedMaxY; y++) {
            for (int x = clampedMinX; x < clampedMaxX; x++) {
                int currentData = maze.getWallDataAt(x, y);

                // If completely solid pillar/rock, skip floor and ceiling
                boolean isSolidBlock = (currentData & ALL_WALLS) == ALL_WALLS;

                if (!isSolidBlock) {
                    // --- 1. FLOOR QUAD (Y = 0.0, Normal = Up) ---
                    addQuad(floorVerts, floorIndices,
                            x, 0.0f, -y, 0f, 0f,
                            x + 1, 0.0f, -y, 1f, 0f,
                            x + 1, 0.0f, -(y + 1), 1f, 1f,
                            x, 0.0f, -(y + 1), 0f, 1f,
                            0f, 1f, 0f, whitePacked
                    );

                    // --- 2. CEILING QUAD (Y = 1.0, Normal = Down) ---
                    // Ceilings are emitted only when inside the player's shelter OR underground in the maze (level > 1)
                    boolean tileHasCeiling = isIndoors || (maze != null && maze.isIndoors(x, y));
                    if (tileHasCeiling) {
                        addQuad(ceilVerts, ceilIndices,
                                x, 1.0f, -y, 0f, 0f,
                                x, 1.0f, -(y + 1), 0f, 1f,
                                x + 1, 1.0f, -(y + 1), 1f, 1f,
                                x + 1, 1.0f, -y, 1f, 0f,
                                0f, -1f, 0f, whitePacked
                        );
                    }
                }

                // --- 3. WALL FACES ---
                // We inspect the 4 boundaries around cell (x, y) if this is an open corridor
                if (!isSolidBlock) {
                    int westData  = (x > 0) ? maze.getWallDataAt(x - 1, y) : ALL_WALLS;
                    int eastData  = (x < maze.getWidth() - 1) ? maze.getWallDataAt(x + 1, y) : ALL_WALLS;
                    int southData = (y > 0) ? maze.getWallDataAt(x, y - 1) : ALL_WALLS;
                    int northData = (y < maze.getHeight() - 1) ? maze.getWallDataAt(x, y + 1) : ALL_WALLS;

                    // A. North boundary (Z = -(y + 1), facing South towards camera inside cell)
                    boolean hasNorthDoor = (currentData & DOOR_NORTH) != 0 || (northData & DOOR_SOUTH) != 0;
                    boolean hasNorthWall = !hasNorthDoor && ((currentData & WALL_NORTH) != 0 || (northData & WALL_SOUTH) != 0 || (northData & ALL_WALLS) == ALL_WALLS || y == maze.getHeight() - 1);
                    if (hasNorthWall) {
                        addQuad(wallVerts, wallIndices,
                                x, 0.0f, -(y + 1), 0f, 1f,
                                x + 1, 0.0f, -(y + 1), 1f, 1f,
                                x + 1, 1.0f, -(y + 1), 1f, 0f,
                                x, 1.0f, -(y + 1), 0f, 0f,
                                0f, 0f, 1f, whitePacked
                        );
                    }

                    // B. South boundary (Z = -y, facing North towards camera inside cell)
                    boolean hasSouthDoor = (currentData & DOOR_SOUTH) != 0 || (southData & DOOR_NORTH) != 0;
                    boolean hasSouthWall = !hasSouthDoor && ((currentData & WALL_SOUTH) != 0 || (southData & WALL_NORTH) != 0 || (southData & ALL_WALLS) == ALL_WALLS || y == 0);
                    if (hasSouthWall) {
                        addQuad(wallVerts, wallIndices,
                                x + 1, 0.0f, -y, 0f, 1f,
                                x, 0.0f, -y, 1f, 1f,
                                x, 1.0f, -y, 1f, 0f,
                                x + 1, 1.0f, -y, 0f, 0f,
                                0f, 0f, -1f, whitePacked
                        );
                    }

                    // C. West boundary (X = x, facing East towards camera inside cell)
                    boolean hasWestDoor = (currentData & DOOR_WEST) != 0 || (westData & DOOR_EAST) != 0;
                    boolean hasWestWall = !hasWestDoor && ((currentData & WALL_WEST) != 0 || (westData & WALL_EAST) != 0 || (westData & ALL_WALLS) == ALL_WALLS || x == 0);
                    if (hasWestWall) {
                        addQuad(wallVerts, wallIndices,
                                x, 0.0f, -y, 0f, 1f,
                                x, 0.0f, -(y + 1), 1f, 1f,
                                x, 1.0f, -(y + 1), 1f, 0f,
                                x, 1.0f, -y, 0f, 0f,
                                1f, 0f, 0f, whitePacked
                        );
                    }

                    // D. East boundary (X = x + 1, facing West towards camera inside cell)
                    boolean hasEastDoor = (currentData & DOOR_EAST) != 0 || (eastData & DOOR_WEST) != 0;
                    boolean hasEastWall = !hasEastDoor && ((currentData & WALL_EAST) != 0 || (eastData & WALL_WEST) != 0 || (eastData & ALL_WALLS) == ALL_WALLS || x == maze.getWidth() - 1);
                    if (hasEastWall) {
                        addQuad(wallVerts, wallIndices,
                                x + 1, 0.0f, -(y + 1), 0f, 1f,
                                x + 1, 0.0f, -y, 1f, 1f,
                                x + 1, 1.0f, -y, 1f, 0f,
                                x + 1, 1.0f, -(y + 1), 0f, 0f,
                                -1f, 0f, 0f, whitePacked
                        );
                    }
                }
            }
        }

        // Build GPU Meshes if geometry was created
        if (floorIndices.size > 0 && floorTexture != null) {
            Mesh floorMesh = createMesh(floorVerts, floorIndices);
            subMeshes.add(new ChunkSubMesh(floorTexture, floorMesh, floorIndices.size));
        }

        if (wallIndices.size > 0 && wallTexture != null) {
            Mesh wallMesh = createMesh(wallVerts, wallIndices);
            subMeshes.add(new ChunkSubMesh(wallTexture, wallMesh, wallIndices.size));
        }

        if (ceilIndices.size > 0 && ceilingTexture != null) {
            Mesh ceilMesh = createMesh(ceilVerts, ceilIndices);
            subMeshes.add(new ChunkSubMesh(ceilingTexture, ceilMesh, ceilIndices.size));
        }

        return subMeshes;
    }

    private static Mesh createMesh(FloatArray verts, ShortArray indices) {
        Mesh mesh = new Mesh(true, verts.size / 9, indices.size, VERTEX_ATTRIBUTES);
        mesh.setVertices(verts.toArray());
        mesh.setIndices(indices.toArray());
        return mesh;
    }

    public static void addQuad(
            FloatArray verts, ShortArray indices,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            float x4, float y4, float z4, float u4, float v4,
            float nx, float ny, float nz, float packedColor
    ) {
        short baseIndex = (short) (verts.size / 9);

        // V1
        verts.add(x1); verts.add(y1); verts.add(z1);
        verts.add(nx); verts.add(ny); verts.add(nz);
        verts.add(packedColor);
        verts.add(u1); verts.add(v1);

        // V2
        verts.add(x2); verts.add(y2); verts.add(z2);
        verts.add(nx); verts.add(ny); verts.add(nz);
        verts.add(packedColor);
        verts.add(u2); verts.add(v2);

        // V3
        verts.add(x3); verts.add(y3); verts.add(z3);
        verts.add(nx); verts.add(ny); verts.add(nz);
        verts.add(packedColor);
        verts.add(u3); verts.add(v3);

        // V4
        verts.add(x4); verts.add(y4); verts.add(z4);
        verts.add(nx); verts.add(ny); verts.add(nz);
        verts.add(packedColor);
        verts.add(u4); verts.add(v4);

        // Triangle 1: 0, 1, 2
        indices.add(baseIndex);
        indices.add((short) (baseIndex + 1));
        indices.add((short) (baseIndex + 2));

        // Triangle 2: 0, 2, 3
        indices.add(baseIndex);
        indices.add((short) (baseIndex + 2));
        indices.add((short) (baseIndex + 3));
    }
}
