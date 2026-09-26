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
import com.bpm.minotaur.gamedata.Window;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.Door;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds static 3D quad geometry for a maze region/chunk.
 * Constructs separate sub-meshes grouped by texture (walls, floors, ceilings)
 * with native hardware UV tiling and lighting normals.
 */
public class ChunkMeshBuilder {

    /** Ceiling height for ordinary dungeon and wilderness tiles. */
    public static final float STANDARD_CEILING_Y = 1.0f;
    /** The shelter stands half again as tall, so the hub reads as a room. */
    public static final float SHELTER_CEILING_Y = 1.5f;


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
        return buildChunk(maze, minX, minY, maxX, maxY, wallTexture, floorTexture, ceilingTexture, isIndoors, 0f, 0f);
    }

    public static List<ChunkSubMesh> buildChunk(
            Maze maze,
            int minX, int minY, int maxX, int maxY,
            Texture wallTexture,
            Texture floorTexture,
            Texture ceilingTexture,
            boolean isIndoors,
            float worldOffsetX,
            float worldOffsetZ
    ) {
        return buildChunk(maze, minX, minY, maxX, maxY, wallTexture, floorTexture, ceilingTexture,
                isIndoors, worldOffsetX, worldOffsetZ, null, 0L);
    }

    /**
     * Builds a chunk, optionally varying the wall texture per face.
     *
     * <p>When {@code wallProvider} is supplied and the maze is MAZE-biome, wall
     * quads are bucketed by variant and emitted as one sub-mesh per variant in
     * use. That costs at most six draw calls per chunk instead of one, and buys
     * walls that are not the same image to the horizon. Every other biome, and a
     * null provider, take the original single-texture path unchanged.
     *
     * @param wallProvider supplies variant textures, or null for uniform walls
     * @param chunkSeed    the chunk's deterministic seed, so the choice survives
     *                     leaving and re-entering the chunk
     */
    public static List<ChunkSubMesh> buildChunk(
            Maze maze,
            int minX, int minY, int maxX, int maxY,
            Texture wallTexture,
            Texture floorTexture,
            Texture ceilingTexture,
            boolean isIndoors,
            float worldOffsetX,
            float worldOffsetZ,
            WallTextureProvider wallProvider,
            long chunkSeed
    ) {
        List<ChunkSubMesh> subMeshes = new ArrayList<>();

        // Only the maze wears variants; forest, desert and lakelands have their
        // own biome wall art and must not be overwritten with masonry.
        boolean varied = wallProvider != null
                && maze != null
                && maze.getBiome() == com.bpm.minotaur.generation.Biome.MAZE;
        WallVariants.Palette palette = varied ? WallVariants.paletteFor(chunkSeed) : null;
        int buckets = varied ? WallVariants.VARIANT_COUNT : 1;

        FloatArray[] wallVertsByVariant = new FloatArray[buckets];
        ShortArray[] wallIndicesByVariant = new ShortArray[buckets];
        for (int i = 0; i < buckets; i++) {
            wallVertsByVariant[i] = new FloatArray();
            wallIndicesByVariant[i] = new ShortArray();
        }
        // Windows and any non-varied build write here; bucket 0 is the base texture.
        FloatArray wallVerts = wallVertsByVariant[0];
        ShortArray wallIndices = wallIndicesByVariant[0];

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
                // If this cell contains a Window, emit the complete 3D window embrasure
                if (maze.getGameObjectAt(x, y) instanceof Window) {
                    addWindowWallMesh(wallVerts, wallIndices, ceilVerts, ceilIndices, x, y, whitePacked,
                            worldOffsetX, worldOffsetZ, ceilingHeightFor(maze, x, y));
                    continue;
                }

                // Shelter rooms stand 50% taller than the dungeon. The walls rise
                // with the ceiling and the texture tiles into the extra height
                // rather than stretching, which would smear the masonry in the
                // one room the player sees every single run.
                float ceilY = ceilingHeightFor(maze, x, y);
                // Wall V runs 1 at the floor to 0 at the top; pushing the top
                // below 0 repeats the texture instead of scaling it.
                float wallTopV = 1f - ceilY;

                int currentData = maze.getWallDataAt(x, y);

                // If completely solid pillar/rock, skip floor and ceiling
                boolean isSolidBlock = (currentData & ALL_WALLS) == ALL_WALLS;

                if (!isSolidBlock) {
                    int westData  = (x > 0) ? maze.getWallDataAt(x - 1, y) : ALL_WALLS;
                    int eastData  = (x < maze.getWidth() - 1) ? maze.getWallDataAt(x + 1, y) : ALL_WALLS;
                    int southData = (y > 0) ? maze.getWallDataAt(x, y - 1) : ALL_WALLS;
                    int northData = (y < maze.getHeight() - 1) ? maze.getWallDataAt(x, y + 1) : ALL_WALLS;

                    Object westObj  = (x > 0) ? maze.getGameObjectAt(x - 1, y) : null;
                    boolean isWestWindow = (westObj instanceof Window);
                    Object eastObj  = (x < maze.getWidth() - 1) ? maze.getGameObjectAt(x + 1, y) : null;
                    boolean isEastWindow = (eastObj instanceof Window);
                    Object northObj = (y < maze.getHeight() - 1) ? maze.getGameObjectAt(x, y + 1) : null;
                    boolean isNorthWindow = (northObj instanceof Window);
                    Object southObj = (y > 0) ? maze.getGameObjectAt(x, y - 1) : null;
                    boolean isSouthWindow = (southObj instanceof Window);

                    Gate gateAtCell = (maze != null) ? maze.getGateAt(x, y) : null;
                    Gate northGate  = (maze != null && y < maze.getHeight() - 1) ? maze.getGateAt(x, y + 1) : null;
                    Gate southGate  = (maze != null && y > 0) ? maze.getGateAt(x, y - 1) : null;
                    Gate westGate   = (maze != null && x > 0) ? maze.getGateAt(x - 1, y) : null;
                    Gate eastGate   = (maze != null && x < maze.getWidth() - 1) ? maze.getGateAt(x + 1, y) : null;

                    // Gate portal openings: omit wall across the portal passageway (both at the gate tile and from adjacent hallway)
                    boolean isNorthGateOpening = (gateAtCell != null && gateAtCell.getOrientation() == com.bpm.minotaur.gamedata.Door.Orientation.NORTH_SOUTH)
                                              || (northGate != null && northGate.getOrientation() == com.bpm.minotaur.gamedata.Door.Orientation.NORTH_SOUTH);
                    boolean isSouthGateOpening = (gateAtCell != null && gateAtCell.getOrientation() == com.bpm.minotaur.gamedata.Door.Orientation.NORTH_SOUTH)
                                              || (southGate != null && southGate.getOrientation() == com.bpm.minotaur.gamedata.Door.Orientation.NORTH_SOUTH);
                    boolean isWestGateOpening  = (gateAtCell != null && gateAtCell.getOrientation() == com.bpm.minotaur.gamedata.Door.Orientation.EAST_WEST)
                                              || (westGate != null && westGate.getOrientation() == com.bpm.minotaur.gamedata.Door.Orientation.EAST_WEST);
                    boolean isEastGateOpening  = (gateAtCell != null && gateAtCell.getOrientation() == com.bpm.minotaur.gamedata.Door.Orientation.EAST_WEST)
                                              || (eastGate != null && eastGate.getOrientation() == com.bpm.minotaur.gamedata.Door.Orientation.EAST_WEST);

                    // Gate flanking walls: gate tile itself needs solid flanking walls on its sides
                    boolean isGateFlankingNorth = (gateAtCell != null && gateAtCell.getOrientation() == com.bpm.minotaur.gamedata.Door.Orientation.EAST_WEST);
                    boolean isGateFlankingSouth = (gateAtCell != null && gateAtCell.getOrientation() == com.bpm.minotaur.gamedata.Door.Orientation.EAST_WEST);
                    boolean isGateFlankingWest  = (gateAtCell != null && gateAtCell.getOrientation() == com.bpm.minotaur.gamedata.Door.Orientation.NORTH_SOUTH);
                    boolean isGateFlankingEast  = (gateAtCell != null && gateAtCell.getOrientation() == com.bpm.minotaur.gamedata.Door.Orientation.NORTH_SOUTH);

                    boolean hasNorthDoor = (currentData & DOOR_NORTH) != 0 || (northData & DOOR_SOUTH) != 0;
                    boolean hasNorthWall = !hasNorthDoor && !isNorthWindow && !isNorthGateOpening && (isGateFlankingNorth || (currentData & WALL_NORTH) != 0 || (northData & WALL_SOUTH) != 0 || (northData & ALL_WALLS) == ALL_WALLS || y == maze.getHeight() - 1);

                    boolean hasSouthDoor = (currentData & DOOR_SOUTH) != 0 || (southData & DOOR_NORTH) != 0;
                    boolean hasSouthWall = !hasSouthDoor && !isSouthWindow && !isSouthGateOpening && (isGateFlankingSouth || (currentData & WALL_SOUTH) != 0 || (southData & WALL_NORTH) != 0 || (southData & ALL_WALLS) == ALL_WALLS || y == 0);

                    boolean hasWestDoor = (currentData & DOOR_WEST) != 0 || (westData & DOOR_EAST) != 0;
                    boolean hasWestWall = !hasWestDoor && !isWestWindow && !isWestGateOpening && (isGateFlankingWest || (currentData & WALL_WEST) != 0 || (westData & WALL_EAST) != 0 || (westData & ALL_WALLS) == ALL_WALLS || x == 0);

                    boolean hasEastDoor = (currentData & DOOR_EAST) != 0 || (eastData & DOOR_WEST) != 0;
                    boolean hasEastWall = !hasEastDoor && !isEastWindow && !isEastGateOpening && (isGateFlankingEast || (currentData & WALL_EAST) != 0 || (eastData & WALL_WEST) != 0 || (eastData & ALL_WALLS) == ALL_WALLS || x == maze.getWidth() - 1);

                    // --- 1. FLOOR QUAD (Y = 0.0, Normal = Up) ---
                    addQuad(floorVerts, floorIndices,
                            x + worldOffsetX, 0.0f, -y + worldOffsetZ, 0f, 0f,
                            x + 1 + worldOffsetX, 0.0f, -y + worldOffsetZ, 1f, 0f,
                            x + 1 + worldOffsetX, 0.0f, -(y + 1) + worldOffsetZ, 1f, 1f,
                            x + worldOffsetX, 0.0f, -(y + 1) + worldOffsetZ, 0f, 1f,
                            0f, 1f, 0f, whitePacked
                    );

                    // --- 2. CEILING QUAD (Y = ceilY, Normal = Down) ---
                    // Ceilings are emitted only when inside the player's shelter OR underground in the maze (level > 1)
                    boolean tileHasCeiling = (maze != null) ? maze.isIndoors(x, y) : isIndoors;
                    if (tileHasCeiling) {
                        addQuad(ceilVerts, ceilIndices,
                                x + worldOffsetX, ceilY, -y + worldOffsetZ, 0f, 0f,
                                x + worldOffsetX, ceilY, -(y + 1) + worldOffsetZ, 0f, 1f,
                                x + 1 + worldOffsetX, ceilY, -(y + 1) + worldOffsetZ, 1f, 1f,
                                x + 1 + worldOffsetX, ceilY, -y + worldOffsetZ, 1f, 0f,
                                0f, -1f, 0f, whitePacked
                        );
                    }

                    // --- 3. WALL FACES ---
                    // A. North boundary (Z = -(y + 1), facing South towards camera inside cell)
                    if (hasNorthWall) {
                        int b = varied ? WallVariants.variantFor(chunkSeed, x, y, WallVariants.FACE_NORTH) : 0;
                        addQuad(wallVertsByVariant[b], wallIndicesByVariant[b],
                                x + worldOffsetX, 0.0f, -(y + 1) + worldOffsetZ, 0f, 1f,
                                x + 1 + worldOffsetX, 0.0f, -(y + 1) + worldOffsetZ, 1f, 1f,
                                x + 1 + worldOffsetX, ceilY, -(y + 1) + worldOffsetZ, 1f, wallTopV,
                                x + worldOffsetX, ceilY, -(y + 1) + worldOffsetZ, 0f, wallTopV,
                                0f, 0f, 1f, whitePacked
                        );
                    }

                    // B. South boundary (Z = -y, facing North towards camera inside cell)
                    if (hasSouthWall) {
                        int b = varied ? WallVariants.variantFor(chunkSeed, x, y, WallVariants.FACE_SOUTH) : 0;
                        addQuad(wallVertsByVariant[b], wallIndicesByVariant[b],
                                x + 1 + worldOffsetX, 0.0f, -y + worldOffsetZ, 0f, 1f,
                                x + worldOffsetX, 0.0f, -y + worldOffsetZ, 1f, 1f,
                                x + worldOffsetX, ceilY, -y + worldOffsetZ, 1f, wallTopV,
                                x + 1 + worldOffsetX, ceilY, -y + worldOffsetZ, 0f, wallTopV,
                                0f, 0f, -1f, whitePacked
                        );
                    }

                    // C. West boundary (X = x, facing East towards camera inside cell)
                    if (hasWestWall) {
                        int b = varied ? WallVariants.variantFor(chunkSeed, x, y, WallVariants.FACE_WEST) : 0;
                        addQuad(wallVertsByVariant[b], wallIndicesByVariant[b],
                                x + worldOffsetX, 0.0f, -y + worldOffsetZ, 0f, 1f,
                                x + worldOffsetX, 0.0f, -(y + 1) + worldOffsetZ, 1f, 1f,
                                x + worldOffsetX, ceilY, -(y + 1) + worldOffsetZ, 1f, wallTopV,
                                x + worldOffsetX, ceilY, -y + worldOffsetZ, 0f, wallTopV,
                                1f, 0f, 0f, whitePacked
                        );
                    }

                    // D. East boundary (X = x + 1, facing West towards camera inside cell)
                    if (hasEastWall) {
                        int b = varied ? WallVariants.variantFor(chunkSeed, x, y, WallVariants.FACE_EAST) : 0;
                        addQuad(wallVertsByVariant[b], wallIndicesByVariant[b],
                                x + 1 + worldOffsetX, 0.0f, -(y + 1) + worldOffsetZ, 0f, 1f,
                                x + 1 + worldOffsetX, 0.0f, -y + worldOffsetZ, 1f, 1f,
                                x + 1 + worldOffsetX, ceilY, -y + worldOffsetZ, 1f, wallTopV,
                                x + 1 + worldOffsetX, ceilY, -(y + 1) + worldOffsetZ, 0f, wallTopV,
                                -1f, 0f, 0f, whitePacked
                        );
                    }
                }
            }
        }

        // Build GPU Meshes if geometry was created
        if (floorIndices.size > 0 && floorTexture != null) {
            Mesh floorMesh = createMesh(floorVerts, floorIndices);
            subMeshes.add(new ChunkSubMesh(floorTexture, floorMesh, floorIndices.size,
                    ChunkSubMesh.Surface.FLOOR));
        }

        for (int i = 0; i < buckets; i++) {
            if (wallIndicesByVariant[i].size == 0) continue;
            Texture tex = varied ? wallProvider.get(palette, i) : wallTexture;
            if (tex == null) continue;
            Mesh wallMesh = createMesh(wallVertsByVariant[i], wallIndicesByVariant[i]);
            subMeshes.add(new ChunkSubMesh(tex, wallMesh, wallIndicesByVariant[i].size,
                    ChunkSubMesh.Surface.WALL));
        }

        if (ceilIndices.size > 0 && ceilingTexture != null) {
            Mesh ceilMesh = createMesh(ceilVerts, ceilIndices);
            subMeshes.add(new ChunkSubMesh(ceilingTexture, ceilMesh, ceilIndices.size,
                    ChunkSubMesh.Surface.CEILING));
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

    /**
     * Emits the complete 3D barred shelter window embrasure within wall cell (x, y).
     * Builds a physical 0.50 x 0.40 opening tunneling 1.0m deep through the stone wall
     * between West facade (X = x) and East facade (X = x + 1), with stone sill, lintel,
     * jambs, connecting reveal surfaces, wall ceiling, and 3 double-sided vertical iron bars.
     */
    /** Shelter tiles stand half again as tall as the rest of the world. */
    public static float ceilingHeightFor(Maze maze, int x, int y) {
        return maze.isHomeTile(x, y) ? SHELTER_CEILING_Y : STANDARD_CEILING_Y;
    }

    private static void addWindowWallMesh(
            FloatArray wallVerts, ShortArray wallIndices,
            FloatArray ceilVerts, ShortArray ceilIndices,
            int x, int y,
            float whitePacked,
            float worldOffsetX,
            float worldOffsetZ,
            float ceilY
    ) {
        // The sill and lintel stay put -- the opening is a fixed size -- but the
        // masonry above the lintel and the ceiling rise with the room. Without
        // this a shelter window would sit in a 1.0-high cell while the walls
        // beside it reached 1.5, leaving an open band above the frame.
        float ySill = 0.30f;
        float yLintel = 0.70f;
        float zFar = -(y + 1) + worldOffsetZ;
        float zNear = -y + worldOffsetZ;
        float zLeft = -(y + 0.75f) + worldOffsetZ;
        float zRight = -(y + 0.25f) + worldOffsetZ;
        float xWest = x + worldOffsetX;
        float xEast = x + 1 + worldOffsetX;

        // =========================================================================
        // 1. WEST FACADE (Exterior face at X = x, facing West: Normal (-1, 0, 0))
        // =========================================================================
        // 1a. Bottom Sill Wall: Y in [0.0, 0.30], Z in [zFar, zNear]
        addQuad(wallVerts, wallIndices,
                xWest, 0.0f, zFar, 0f, 1f,
                xWest, 0.0f, zNear, 1f, 1f,
                xWest, ySill, zNear, 1f, 0.70f,
                xWest, ySill, zFar, 0f, 0.70f,
                -1f, 0f, 0f, whitePacked
        );

        // 1b. Top Header Lintel: Y in [0.70, 1.0], Z in [zFar, zNear]
        addQuad(wallVerts, wallIndices,
                xWest, yLintel, zFar, 0f, 0.30f,
                xWest, yLintel, zNear, 1f, 0.30f,
                xWest, ceilY, zNear, 1f, 0f,
                xWest, ceilY, zFar, 0f, 0f,
                -1f, 0f, 0f, whitePacked
        );

        // 1c. Left (North) Jamb Wall: Y in [0.30, 0.70], Z in [zFar, zLeft]
        addQuad(wallVerts, wallIndices,
                xWest, ySill, zFar, 0.75f, 0.70f,
                xWest, ySill, zLeft, 1f, 0.70f,
                xWest, yLintel, zLeft, 1f, 0.30f,
                xWest, yLintel, zFar, 0.75f, 0.30f,
                -1f, 0f, 0f, whitePacked
        );

        // 1d. Right (South) Jamb Wall: Y in [0.30, 0.70], Z in [zRight, zNear]
        addQuad(wallVerts, wallIndices,
                xWest, ySill, zRight, 0f, 0.70f,
                xWest, ySill, zNear, 0.25f, 0.70f,
                xWest, yLintel, zNear, 0.25f, 0.30f,
                xWest, yLintel, zRight, 0f, 0.30f,
                -1f, 0f, 0f, whitePacked
        );

        // =========================================================================
        // 2. EAST FACADE (Interior face at X = x + 1, facing East: Normal (1, 0, 0))
        // =========================================================================
        // 2a. Bottom Sill Wall: Y in [0.0, 0.30], Z in [zNear, zFar]
        addQuad(wallVerts, wallIndices,
                xEast, 0.0f, zNear, 0f, 1f,
                xEast, 0.0f, zFar, 1f, 1f,
                xEast, ySill, zFar, 1f, 0.70f,
                xEast, ySill, zNear, 0f, 0.70f,
                1f, 0f, 0f, whitePacked
        );

        // 2b. Top Header Lintel: Y in [0.70, 1.0], Z in [zNear, zFar]
        addQuad(wallVerts, wallIndices,
                xEast, yLintel, zNear, 0f, 0.30f,
                xEast, yLintel, zFar, 1f, 0.30f,
                xEast, ceilY, zFar, 1f, 0f,
                xEast, ceilY, zNear, 0f, 0f,
                1f, 0f, 0f, whitePacked
        );

        // 2c. Left (North) Jamb Wall: Y in [0.30, 0.70], Z in [zLeft, zFar]
        addQuad(wallVerts, wallIndices,
                xEast, ySill, zLeft, 0.75f, 0.70f,
                xEast, ySill, zFar, 1f, 0.70f,
                xEast, yLintel, zFar, 1f, 0.30f,
                xEast, yLintel, zLeft, 0.75f, 0.30f,
                1f, 0f, 0f, whitePacked
        );

        // 2d. Right (South) Jamb Wall: Y in [0.30, 0.70], Z in [zNear, zRight]
        addQuad(wallVerts, wallIndices,
                xEast, ySill, zNear, 0f, 0.70f,
                xEast, ySill, zRight, 0.25f, 0.70f,
                xEast, yLintel, zRight, 0.25f, 0.30f,
                xEast, yLintel, zNear, 0f, 0.30f,
                1f, 0f, 0f, whitePacked
        );

        // =========================================================================
        // 3. EMBRASURE TUNNEL REVEALS (Connecting X = x to X = x + 1)
        // =========================================================================
        // 3a. Sill Shelf reveal (Y = ySill, facing UP: Normal (0, 1, 0))
        addQuad(wallVerts, wallIndices,
                xWest, ySill, zRight, 0f, 0f,
                xEast, ySill, zRight, 1f, 0f,
                xEast, ySill, zLeft, 1f, 0.50f,
                xWest, ySill, zLeft, 0f, 0.50f,
                0f, 1f, 0f, whitePacked
        );

        // 3b. Lintel Underside reveal (Y = yLintel, facing DOWN: Normal (0, -1, 0))
        addQuad(wallVerts, wallIndices,
                xWest, yLintel, zRight, 0f, 0f,
                xWest, yLintel, zLeft, 0f, 0.50f,
                xEast, yLintel, zLeft, 1f, 0.50f,
                xEast, yLintel, zRight, 1f, 0f,
                0f, -1f, 0f, whitePacked
        );

        // 3c. Left (North) Reveal Wall (Z = zLeft, facing South into opening: Normal (0, 0, 1))
        addQuad(wallVerts, wallIndices,
                xWest, ySill, zLeft, 0f, 0.70f,
                xEast, ySill, zLeft, 1f, 0.70f,
                xEast, yLintel, zLeft, 1f, 0.30f,
                xWest, yLintel, zLeft, 0f, 0.30f,
                0f, 0f, 1f, whitePacked
        );

        // 3d. Right (South) Reveal Wall (Z = zRight, facing North into opening: Normal (0, 0, -1))
        addQuad(wallVerts, wallIndices,
                xEast, ySill, zRight, 0f, 0.70f,
                xWest, ySill, zRight, 1f, 0.70f,
                xWest, yLintel, zRight, 1f, 0.30f,
                xEast, yLintel, zRight, 0f, 0.30f,
                0f, 0f, -1f, whitePacked
        );

        // =========================================================================
        // 4. CEILING OVER WALL CELL (Y = ceilY, facing DOWN: Normal (0, -1, 0))
        // =========================================================================
        addQuad(ceilVerts, ceilIndices,
                xWest, ceilY, zNear, 0f, 0f,
                xWest, ceilY, zFar, 0f, 1f,
                xEast, ceilY, zFar, 1f, 1f,
                xEast, ceilY, zNear, 1f, 0f,
                0f, -1f, 0f, whitePacked
        );

        // =========================================================================
        // 5. 3 VERTICAL IRON BARS (Centered in wall thickness at X = x + 0.5)
        // =========================================================================
        float ironPacked = new Color(0.133f, 0.133f, 0.149f, 1f).toFloatBits();
        float openingWidth = zRight - zLeft; // 0.50
        float barHalfWidth = 0.0175f; // total width 0.035
        float xBar = x + 0.5f; // Centered inside the 1m wall tunnel

        for (int i = 1; i <= 3; i++) {
            float t = i / 4.0f; // 0.25, 0.50, 0.75
            float zCenter = zLeft + t * openingWidth;
            float z1 = zCenter - barHalfWidth;
            float z2 = zCenter + barHalfWidth;

            // Front face (facing East +X)
            addQuad(wallVerts, wallIndices,
                    xBar, ySill, z2, 0f, 1f,
                    xBar, ySill, z1, 1f, 1f,
                    xBar, yLintel, z1, 1f, 0f,
                    xBar, yLintel, z2, 0f, 0f,
                    1f, 0f, 0f, ironPacked
            );

            // Back face (facing West -X)
            addQuad(wallVerts, wallIndices,
                    xBar, ySill, z1, 0f, 1f,
                    xBar, ySill, z2, 1f, 1f,
                    xBar, yLintel, z2, 1f, 0f,
                    xBar, yLintel, z1, 0f, 0f,
                    -1f, 0f, 0f, ironPacked
            );
        }
    }
}
