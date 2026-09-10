package com.bpm.minotaur.rendering.mesh;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.gore.WallDecal;

/**
 * High-performance dynamic quad batcher for 3D camera-facing billboards,
 * coplanar wall decals, floor corpses, and dynamic sliding doors.
 * Uses a reusable client-side vertex array to guarantee 0 per-frame heap allocations.
 */
public class DynamicQuadBatcher implements Disposable {

    private static final int MAX_QUADS = 4000;
    private static final int MAX_VERTICES = MAX_QUADS * 4;
    private static final int MAX_INDICES = MAX_QUADS * 6;

    private final Mesh mesh;
    private final FloatArray vertices = new FloatArray(MAX_VERTICES * 9);
    private final ShortArray indices = new ShortArray(MAX_INDICES);

    private final Vector3 scratchV1 = new Vector3();
    private final Vector3 scratchV2 = new Vector3();
    private final Vector3 scratchV3 = new Vector3();
    private final Vector3 scratchV4 = new Vector3();

    private final Vector3 scratchStreakDir = new Vector3();
    private final Vector3 scratchStreakSide = new Vector3();
    private final Vector3 scratchCamVec = new Vector3();

    public DynamicQuadBatcher() {
        this.mesh = new Mesh(false, MAX_VERTICES, MAX_INDICES, ChunkMeshBuilder.VERTEX_ATTRIBUTES);
    }

    /**
     * Emits a camera-facing billboard quad centered at the specified base/feet point.
     *
     * @param feetX    World X
     * @param feetY    World Y (base ground level)
     * @param feetZ    World Z
     * @param width    World width
     * @param height   World height
     * @param region   TextureRegion containing UV coordinates
     * @param color    Color tint
     * @param camRight Camera right unit vector
     * @param camUp    Camera up unit vector
     * @param camDir   Camera direction vector
     */
    public void addBillboard(
            float feetX, float feetY, float feetZ,
            float width, float height,
            TextureRegion region,
            Color color,
            Vector3 camRight,
            Vector3 camUp,
            Vector3 camDir
    ) {
        if (region == null) return;
        if (!ensureCapacity(1)) return;

        float halfW = width * 0.5f;
        float centerY = feetY + height * 0.5f;

        float u1 = region.getU();
        float v1 = region.getV2(); // LibGDX V2 is image bottom
        float u2 = region.getU2();
        float v2 = region.getV();  // LibGDX V is image top

        // Corners: Bottom-Left, Bottom-Right, Top-Right, Top-Left
        scratchV1.set(feetX, centerY, feetZ)
                .add(-camRight.x * halfW - camUp.x * (height * 0.5f),
                     -camRight.y * halfW - camUp.y * (height * 0.5f),
                     -camRight.z * halfW - camUp.z * (height * 0.5f));

        scratchV2.set(feetX, centerY, feetZ)
                .add(camRight.x * halfW - camUp.x * (height * 0.5f),
                     camRight.y * halfW - camUp.y * (height * 0.5f),
                     camRight.z * halfW - camUp.z * (height * 0.5f));

        scratchV3.set(feetX, centerY, feetZ)
                .add(camRight.x * halfW + camUp.x * (height * 0.5f),
                     camRight.y * halfW + camUp.y * (height * 0.5f),
                     camRight.z * halfW + camUp.z * (height * 0.5f));

        scratchV4.set(feetX, centerY, feetZ)
                .add(-camRight.x * halfW + camUp.x * (height * 0.5f),
                     -camRight.y * halfW + camUp.y * (height * 0.5f),
                     -camRight.z * halfW + camUp.z * (height * 0.5f));

        float packedColor = (color != null) ? color.toFloatBits() : Color.WHITE.toFloatBits();
        float nx = -camDir.x;
        float ny = -camDir.y;
        float nz = -camDir.z;

        ChunkMeshBuilder.addQuad(
                vertices, indices,
                scratchV1.x, scratchV1.y, scratchV1.z, u1, v1,
                scratchV2.x, scratchV2.y, scratchV2.z, u2, v1,
                scratchV3.x, scratchV3.y, scratchV3.z, u2, v2,
                scratchV4.x, scratchV4.y, scratchV4.z, u1, v2,
                nx, ny, nz, packedColor
        );
    }

    /**
     * Emits a camera-facing billboard quad rotated around the view normal by angleDeg.
     */
    public void addRotatedBillboard(
            float feetX, float feetY, float feetZ,
            float width, float height,
            TextureRegion region,
            Color color,
            Vector3 camRight,
            Vector3 camUp,
            Vector3 camDir,
            float angleDeg
    ) {
        if (region == null) return;
        if (!ensureCapacity(1)) return;

        float halfW = width * 0.5f;
        float halfH = height * 0.5f;
        float centerY = feetY + halfH;

        float cos = MathUtils.cosDeg(angleDeg);
        float sin = MathUtils.sinDeg(angleDeg);

        // Rotated right and up vectors in view plane
        float rx = camRight.x * cos + camUp.x * sin;
        float ry = camRight.y * cos + camUp.y * sin;
        float rz = camRight.z * cos + camUp.z * sin;

        float ux = -camRight.x * sin + camUp.x * cos;
        float uy = -camRight.y * sin + camUp.y * cos;
        float uz = -camRight.z * sin + camUp.z * cos;

        float u1 = region.getU();
        float v1 = region.getV2();
        float u2 = region.getU2();
        float v2 = region.getV();

        scratchV1.set(feetX, centerY, feetZ).add(-rx * halfW - ux * halfH, -ry * halfW - uy * halfH, -rz * halfW - uz * halfH);
        scratchV2.set(feetX, centerY, feetZ).add( rx * halfW - ux * halfH,  ry * halfW - uy * halfH,  rz * halfW - uz * halfH);
        scratchV3.set(feetX, centerY, feetZ).add( rx * halfW + ux * halfH,  ry * halfW + uy * halfH,  rz * halfW + uz * halfH);
        scratchV4.set(feetX, centerY, feetZ).add(-rx * halfW + ux * halfH, -ry * halfW + uy * halfH, -rz * halfW + uz * halfH);

        float packedColor = (color != null) ? color.toFloatBits() : Color.WHITE.toFloatBits();
        float nx = -camDir.x;
        float ny = -camDir.y;
        float nz = -camDir.z;

        ChunkMeshBuilder.addQuad(
                vertices, indices,
                scratchV1.x, scratchV1.y, scratchV1.z, u1, v1,
                scratchV2.x, scratchV2.y, scratchV2.z, u2, v1,
                scratchV3.x, scratchV3.y, scratchV3.z, u2, v2,
                scratchV4.x, scratchV4.y, scratchV4.z, u1, v2,
                nx, ny, nz, packedColor
        );
    }

    /**
     * Emits a flat horizontal quad resting on the floor (e.g. monster corpses, floor blood).
     */
    public void addFloorQuad(
            float centerX, float y, float centerZ,
            float halfW, float halfH,
            TextureRegion region,
            Color color
    ) {
        if (region == null) return;
        if (!ensureCapacity(1)) return;

        float u1 = region.getU();
        float v1 = region.getV2();
        float u2 = region.getU2();
        float v2 = region.getV();

        float packedColor = (color != null) ? color.toFloatBits() : Color.WHITE.toFloatBits();

        ChunkMeshBuilder.addQuad(
                vertices, indices,
                centerX - halfW, y, centerZ + halfH, u1, v1,
                centerX + halfW, y, centerZ + halfH, u2, v1,
                centerX + halfW, y, centerZ - halfH, u2, v2,
                centerX - halfW, y, centerZ - halfH, u1, v2,
                0f, 1f, 0f, packedColor
        );
    }

    /**
     * Emits a coplanar wall decal quad offset by eps along the wall normal to eliminate Z-fighting.
     */
    public void addWallDecal(WallDecal decal, Color color) {
        if (decal == null) return;
        addWallDecal(decal, decal.gridX, decal.gridY, color);
    }

    /**
     * Emits a coplanar wall decal quad using explicit local grid coordinates (for multi-chunk camera transforms).
     */
    public void addWallDecal(WallDecal decal, float localGridX, float localGridY, Color color) {
        if (decal == null || decal.textureRegion == null) return;
        if (!ensureCapacity(1)) return;

        TextureRegion region = decal.textureRegion;
        float r = Math.max(0.08f, Math.min(0.35f, decal.radius));
        float h = MathUtils.clamp(decal.height, r + 0.01f, 1.0f - r - 0.01f);
        float wX = MathUtils.clamp(decal.wallX, r + 0.01f, 1.0f - r - 0.01f);
        float eps = 0.003f;

        float u1 = region.getU();
        float v1 = region.getV2(); // LibGDX V2 is texture bottom
        float u2 = region.getU2();
        float v2 = region.getV();  // LibGDX V is texture top
        float packedColor = (color != null) ? color.toFloatBits() : decal.color.toFloatBits();

        Direction dir = decal.dir;
        if (dir == null) {
            dir = (decal.side == 0) ? Direction.WEST : Direction.NORTH;
        }

        switch (dir) {
            case EAST: {
                // Moving EAST: hit EAST boundary of cell (X = localGridX + 1.0)
                // Face normal points WEST (-1, 0, 0) into cell
                float xPos = localGridX + 1.0f - eps;
                float zCenter = -(localGridY + wX);
                ChunkMeshBuilder.addQuad(
                        vertices, indices,
                        xPos, h - r, zCenter + r, u1, v1,
                        xPos, h - r, zCenter - r, u2, v1,
                        xPos, h + r, zCenter - r, u2, v2,
                        xPos, h + r, zCenter + r, u1, v2,
                        -1f, 0f, 0f, packedColor
                );
                break;
            }
            case WEST: {
                // Moving WEST: hit WEST boundary of cell (X = localGridX)
                // Face normal points EAST (1, 0, 0) into cell
                float xPos = localGridX + eps;
                float zCenter = -(localGridY + wX);
                ChunkMeshBuilder.addQuad(
                        vertices, indices,
                        xPos, h - r, zCenter - r, u1, v1,
                        xPos, h - r, zCenter + r, u2, v1,
                        xPos, h + r, zCenter + r, u2, v2,
                        xPos, h + r, zCenter - r, u1, v2,
                        1f, 0f, 0f, packedColor
                );
                break;
            }
            case NORTH: {
                // Moving NORTH (+Y): hit NORTH boundary of cell (Z = -(localGridY + 1.0))
                // Face normal points SOUTH (0, 0, 1) into cell
                float zPos = -(localGridY + 1.0f) + eps;
                float xCenter = localGridX + wX;
                ChunkMeshBuilder.addQuad(
                        vertices, indices,
                        xCenter - r, h - r, zPos, u1, v1,
                        xCenter + r, h - r, zPos, u2, v1,
                        xCenter + r, h + r, zPos, u2, v2,
                        xCenter - r, h + r, zPos, u1, v2,
                        0f, 0f, 1f, packedColor
                );
                break;
            }
            case SOUTH: {
                // Moving SOUTH (-Y): hit SOUTH boundary of cell (Z = -localGridY)
                // Face normal points NORTH (0, 0, -1) into cell
                float zPos = -localGridY - eps;
                float xCenter = localGridX + wX;
                ChunkMeshBuilder.addQuad(
                        vertices, indices,
                        xCenter + r, h - r, zPos, u1, v1,
                        xCenter - r, h - r, zPos, u2, v1,
                        xCenter - r, h + r, zPos, u2, v2,
                        xCenter + r, h + r, zPos, u1, v2,
                        0f, 0f, -1f, packedColor
                );
                break;
            }
        }
    }

    /**
     * Emits a dynamic sliding door panel.
     */
    public void addSlidingDoor(
            float gridX, float gridY,
            boolean isEastWest,
            float openProgress,
            Color color
    ) {
        if (!ensureCapacity(2)) return;
        float yBottom = 0.0f;
        float yTop = 1.0f;
        float packedColor = (color != null) ? color.toFloatBits() : Color.WHITE.toFloatBits();

        // Subtle mechanical rumble vibration during opening/closing
        float rumble = 0.0f;
        if (openProgress > 0.0f && openProgress < 1.0f) {
            rumble = (float) Math.sin(openProgress * Math.PI * 16.0) * 0.015f;
        }

        if (isEastWest) {
            // East-West barrier: sits in X plane at gridX + 0.5f, blocks East-West passage.
            // Slides horizontally along Z into the flanking wall pocket (-Z direction).
            float zShift = -openProgress * 1.0f;
            float x = gridX + 0.5f + rumble;
            float z1 = -gridY + zShift;
            float z2 = -(gridY + 1) + zShift;

            // Face 1: looking East (+X)
            ChunkMeshBuilder.addQuad(
                    vertices, indices,
                    x, yBottom, z1, 0f, 1f,
                    x, yBottom, z2, 1f, 1f,
                    x, yTop, z2, 1f, 0f,
                    x, yTop, z1, 0f, 0f,
                    1f, 0f, 0f, packedColor
            );
            // Face 2: looking West (-X)
            ChunkMeshBuilder.addQuad(
                    vertices, indices,
                    x, yBottom, z2, 0f, 1f,
                    x, yBottom, z1, 1f, 1f,
                    x, yTop, z1, 1f, 0f,
                    x, yTop, z2, 0f, 0f,
                    -1f, 0f, 0f, packedColor
            );
        } else {
            // North-South barrier: sits in Z plane at -(gridY + 0.5f), blocks North-South passage.
            // Slides horizontally along X into the flanking wall pocket (-X direction).
            float xShift = -openProgress * 1.0f;
            float z = -(gridY + 0.5f) + rumble;
            float x1 = gridX + xShift;
            float x2 = gridX + 1.0f + xShift;

            // Face 1: looking South (+Z)
            ChunkMeshBuilder.addQuad(
                    vertices, indices,
                    x1, yBottom, z, 0f, 1f,
                    x2, yBottom, z, 1f, 1f,
                    x2, yTop, z, 1f, 0f,
                    x1, yTop, z, 0f, 0f,
                    0f, 0f, 1f, packedColor
            );
            // Face 2: looking North (-Z)
            ChunkMeshBuilder.addQuad(
                    vertices, indices,
                    x2, yBottom, z, 0f, 1f,
                    x1, yBottom, z, 1f, 1f,
                    x1, yTop, z, 1f, 0f,
                    x2, yTop, z, 0f, 0f,
                    0f, 0f, -1f, packedColor
            );
        }
    }

    /**
     * Binds the texture and renders all buffered quads, then resets the batch.
     */
    public void flush(ShaderProgram shader, Texture texture) {
        if (indices.size <= 0) return;

        if (texture != null) {
            texture.bind(0);
            shader.setUniformi("u_diffuseTexture", 0);
        }

        mesh.setVertices(vertices.items, 0, vertices.size);
        mesh.setIndices(indices.items, 0, indices.size);
        mesh.render(shader, GL20.GL_TRIANGLES, 0, indices.size);

        vertices.clear();
        indices.clear();
    }

    public int getQuadCount() {
        return indices.size / 6;
    }

    /**
     * Emits a camera-facing or billboard 3D quad with custom vertex positions.
     */
    public void addParticleQuad(
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float nx, float ny, float nz,
            Color color
    ) {
        if (!ensureCapacity(1)) return;
        float packed = (color != null) ? color.toFloatBits() : Color.WHITE.toFloatBits();
        ChunkMeshBuilder.addQuad(
                vertices, indices,
                x1, y1, z1, 0f, 1f,
                x2, y2, z2, 1f, 1f,
                x3, y3, z3, 1f, 0f,
                x4, y4, z4, 0f, 0f,
                nx, ny, nz, packed
        );
    }

    /**
     * Emits a thin, velocity-oriented camera-facing ribbon quad for rain/storm streaks.
     */
    public void addRainStreak(
            float headX, float headY, float headZ,
            float tailX, float tailY, float tailZ,
            float halfWidth,
            Vector3 camPos,
            Vector3 fallbackRight,
            Color color
    ) {
        if (!ensureCapacity(1)) return;

        // Direction of streak from tail to head
        scratchStreakDir.set(headX - tailX, headY - tailY, headZ - tailZ);
        float lenSq = scratchStreakDir.len2();
        if (lenSq < 1e-6f) return;
        scratchStreakDir.nor();

        // Vector from head to camera
        scratchCamVec.set(camPos.x - headX, camPos.y - headY, camPos.z - headZ);
        float camDistSq = scratchCamVec.len2();
        if (camDistSq < 1e-6f) return;
        scratchCamVec.nor();

        // Cross product: side vector across streak perpendicular to view
        scratchStreakSide.set(scratchStreakDir).crs(scratchCamVec);
        if (scratchStreakSide.len2() < 1e-6f) {
            scratchStreakSide.set(fallbackRight);
        } else {
            scratchStreakSide.nor();
        }
        scratchStreakSide.scl(halfWidth);

        // 4 corners: Head-Left, Head-Right, Tail-Right, Tail-Left
        scratchV1.set(headX, headY, headZ).sub(scratchStreakSide);
        scratchV2.set(headX, headY, headZ).add(scratchStreakSide);
        scratchV3.set(tailX, tailY, tailZ).add(scratchStreakSide);
        scratchV4.set(tailX, tailY, tailZ).sub(scratchStreakSide);

        float packedColor = (color != null) ? color.toFloatBits() : Color.WHITE.toFloatBits();

        ChunkMeshBuilder.addQuad(
                vertices, indices,
                scratchV1.x, scratchV1.y, scratchV1.z, 0f, 1f,
                scratchV2.x, scratchV2.y, scratchV2.z, 1f, 1f,
                scratchV3.x, scratchV3.y, scratchV3.z, 1f, 0f,
                scratchV4.x, scratchV4.y, scratchV4.z, 0f, 0f,
                scratchCamVec.x, scratchCamVec.y, scratchCamVec.z, packedColor
        );
    }

    private boolean ensureCapacity(int numQuads) {
        return ((indices.size / 6) + numQuads <= MAX_QUADS);
    }

    @Override
    public void dispose() {
        mesh.dispose();
    }
}
