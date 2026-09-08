package com.bpm.minotaur.rendering.mesh;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import com.bpm.minotaur.gamedata.gore.WallDecal;

/**
 * High-performance dynamic quad batcher for 3D camera-facing billboards,
 * coplanar wall decals, floor corpses, and dynamic sliding doors.
 * Uses a reusable client-side vertex array to guarantee 0 per-frame heap allocations.
 */
public class DynamicQuadBatcher implements Disposable {

    private static final int MAX_QUADS = 1000;
    private static final int MAX_VERTICES = MAX_QUADS * 4;
    private static final int MAX_INDICES = MAX_QUADS * 6;

    private final Mesh mesh;
    private final FloatArray vertices = new FloatArray(MAX_VERTICES * 9);
    private final ShortArray indices = new ShortArray(MAX_INDICES);

    private final Vector3 scratchV1 = new Vector3();
    private final Vector3 scratchV2 = new Vector3();
    private final Vector3 scratchV3 = new Vector3();
    private final Vector3 scratchV4 = new Vector3();

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
        ensureCapacity(1);

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
     * Emits a flat horizontal quad resting on the floor (e.g. monster corpses, floor blood).
     */
    public void addFloorQuad(
            float centerX, float y, float centerZ,
            float halfW, float halfH,
            TextureRegion region,
            Color color
    ) {
        if (region == null) return;
        ensureCapacity(1);

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
     * Emits a coplanar wall decal quad offset by +0.002 along the wall normal to eliminate Z-fighting.
     */
    public void addWallDecal(WallDecal decal, Color color) {
        if (decal == null || decal.textureRegion == null) return;
        ensureCapacity(1);

        TextureRegion region = decal.textureRegion;
        float r = Math.max(0.1f, decal.radius);
        float h = decal.height; // 0.0 to 1.0 world height
        float wX = decal.wallX; // 0.0 to 1.0 along wall face
        float eps = 0.002f;

        float u1 = region.getU();
        float v1 = region.getV2();
        float u2 = region.getU2();
        float v2 = region.getV();
        float packedColor = (color != null) ? color.toFloatBits() : decal.color.toFloatBits();

        if (decal.side == 0) {
            // East/West wall plane at X = decal.gridX or gridX + 1
            float xPos = decal.gridX + (wX > 0.5f ? 1.0f - eps : eps);
            float zCenter = -(decal.gridY + wX);
            float nx = (wX > 0.5f) ? -1f : 1f;

            ChunkMeshBuilder.addQuad(
                    vertices, indices,
                    xPos, h - r, zCenter + r, u1, v1,
                    xPos, h - r, zCenter - r, u2, v1,
                    xPos, h + r, zCenter - r, u2, v2,
                    xPos, h + r, zCenter + r, u1, v2,
                    nx, 0f, 0f, packedColor
            );
        } else {
            // North/South wall plane at Z = -(decal.gridY)
            float zPos = -(decal.gridY + (wX > 0.5f ? 1.0f - eps : eps));
            float xCenter = decal.gridX + wX;
            float nz = (wX > 0.5f) ? 1f : -1f;

            ChunkMeshBuilder.addQuad(
                    vertices, indices,
                    xCenter - r, h - r, zPos, u1, v1,
                    xCenter + r, h - r, zPos, u2, v1,
                    xCenter + r, h + r, zPos, u2, v2,
                    xCenter - r, h + r, zPos, u1, v2,
                    0f, 0f, nz, packedColor
            );
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
        ensureCapacity(2);
        float yBottom = openProgress * 1.0f;
        float yTop = yBottom + 1.0f;
        float packedColor = (color != null) ? color.toFloatBits() : Color.WHITE.toFloatBits();

        if (isEastWest) {
            float x = gridX + 0.5f;
            // Face 1: looking East (+X)
            ChunkMeshBuilder.addQuad(
                    vertices, indices,
                    x, yBottom, -gridY, 0f, 1f,
                    x, yBottom, -(gridY + 1), 1f, 1f,
                    x, yTop, -(gridY + 1), 1f, 0f,
                    x, yTop, -gridY, 0f, 0f,
                    1f, 0f, 0f, packedColor
            );
            // Face 2: looking West (-X)
            ChunkMeshBuilder.addQuad(
                    vertices, indices,
                    x, yBottom, -(gridY + 1), 0f, 1f,
                    x, yBottom, -gridY, 1f, 1f,
                    x, yTop, -gridY, 1f, 0f,
                    x, yTop, -(gridY + 1), 0f, 0f,
                    -1f, 0f, 0f, packedColor
            );
        } else {
            float z = -(gridY + 0.5f);
            // Face 1: looking South (+Z)
            ChunkMeshBuilder.addQuad(
                    vertices, indices,
                    gridX, yBottom, z, 0f, 1f,
                    gridX + 1, yBottom, z, 1f, 1f,
                    gridX + 1, yTop, z, 1f, 0f,
                    gridX, yTop, z, 0f, 0f,
                    0f, 0f, 1f, packedColor
            );
            // Face 2: looking North (-Z)
            ChunkMeshBuilder.addQuad(
                    vertices, indices,
                    gridX + 1, yBottom, z, 0f, 1f,
                    gridX, yBottom, z, 1f, 1f,
                    gridX, yTop, z, 1f, 0f,
                    gridX + 1, yTop, z, 0f, 0f,
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

    private void ensureCapacity(int numQuads) {
        if ((indices.size / 6) + numQuads > MAX_QUADS) {
            throw new IllegalStateException("DynamicQuadBatcher exceeded MAX_QUADS capacity: " + MAX_QUADS);
        }
    }

    @Override
    public void dispose() {
        mesh.dispose();
    }
}
