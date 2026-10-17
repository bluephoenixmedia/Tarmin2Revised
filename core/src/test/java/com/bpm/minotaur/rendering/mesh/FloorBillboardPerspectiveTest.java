package com.bpm.minotaur.rendering.mesh;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/**
 * Tests that floor billboards (monster corpses and floor decals) lie flat on the ground
 * plane and transform to match the player's viewing perspective across all cardinal headings.
 */
public class FloorBillboardPerspectiveTest {

    @BeforeClass
    public static void setupGdx() {
        GdxNativesLoader.load();
        GL20 glMock = (GL20) Proxy.newProxyInstance(
                GL20.class.getClassLoader(),
                new Class<?>[]{GL20.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hashCode")) return 41;
                    if (method.getName().equals("equals")) return args[0] == proxy;
                    Class<?> ret = method.getReturnType();
                    if (ret == int.class) return 1;
                    if (ret == boolean.class) return true;
                    return null;
                }
        );
        Gdx.gl = glMock;
        Gdx.gl20 = glMock;

        Gdx.app = (Application) Proxy.newProxyInstance(
                Application.class.getClassLoader(),
                new Class<?>[]{Application.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hashCode")) return 42;
                    if (method.getName().equals("equals")) return args[0] == proxy;
                    if (method.getName().equals("toString")) return "MockApplication";
                    return null;
                }
        );
    }

    private TextureRegion createMockRegion(float u1, float v1, float u2, float v2) {
        return new TextureRegion() {
            @Override public float getU() { return u1; }
            @Override public float getV2() { return v1; }
            @Override public float getU2() { return u2; }
            @Override public float getV() { return v2; }
        };
    }

    @Test
    public void facingNorthPlacesBottomNearAndTopFarAlongNegativeZ() {
        DynamicQuadBatcher batcher = new DynamicQuadBatcher();
        TextureRegion reg = createMockRegion(0f, 1f, 1f, 0f);

        float centerX = 10f;
        float floorY = 0.0035f;
        float centerZ = -20f;
        float width = 1f;  // halfW = 0.5f
        float height = 2f; // halfH = 1.0f

        // Camera looking North: camDir = (0, 0, -1), camRight = (1, 0, 0)
        Vector3 camDir = new Vector3(0f, 0f, -1f);
        Vector3 camRight = new Vector3(1f, 0f, 0f);

        batcher.addFloorBillboard(centerX, floorY, centerZ, width, height, reg, Color.WHITE, camRight, camDir);

        FloatArray verts = batcher.getVertices();
        assertEquals(36, verts.size); // 4 vertices * 9 floats each

        // Vertex 1: Bottom-Left (u1, v1)
        float v1X = verts.get(0);
        float v1Y = verts.get(1);
        float v1Z = verts.get(2);
        float v1Nx = verts.get(3);
        float v1Ny = verts.get(4);
        float v1Nz = verts.get(5);
        assertEquals(9.5f, v1X, 1e-4f);
        assertEquals(0.0035f, v1Y, 1e-4f);
        assertEquals(-19.0f, v1Z, 1e-4f); // Closer to player (Z = -19 > -20)
        assertEquals(0f, v1Nx, 1e-4f);
        assertEquals(1f, v1Ny, 1e-4f);   // Normal points UP
        assertEquals(0f, v1Nz, 1e-4f);

        // Vertex 2: Bottom-Right (u2, v1)
        float v2X = verts.get(9);
        float v2Y = verts.get(10);
        float v2Z = verts.get(11);
        assertEquals(10.5f, v2X, 1e-4f);
        assertEquals(0.0035f, v2Y, 1e-4f);
        assertEquals(-19.0f, v2Z, 1e-4f); // Closer to player

        // Vertex 3: Top-Right (u2, v2)
        float v3X = verts.get(18);
        float v3Y = verts.get(19);
        float v3Z = verts.get(20);
        assertEquals(10.5f, v3X, 1e-4f);
        assertEquals(0.0035f, v3Y, 1e-4f);
        assertEquals(-21.0f, v3Z, 1e-4f); // Further into depth (Z = -21 < -20)

        // Vertex 4: Top-Left (u1, v2)
        float v4X = verts.get(27);
        float v4Y = verts.get(28);
        float v4Z = verts.get(29);
        assertEquals(9.5f, v4X, 1e-4f);
        assertEquals(0.0035f, v4Y, 1e-4f);
        assertEquals(-21.0f, v4Z, 1e-4f); // Further into depth

        batcher.dispose();
    }

    @Test
    public void facingEastPlacesBottomNearAndTopFarAlongPositiveX() {
        DynamicQuadBatcher batcher = new DynamicQuadBatcher();
        TextureRegion reg = createMockRegion(0f, 1f, 1f, 0f);

        float centerX = 10f;
        float floorY = 0.0035f;
        float centerZ = -20f;
        float width = 1f;  // halfW = 0.5f
        float height = 2f; // halfH = 1.0f

        // Camera looking East: camDir = (1, 0, 0), camRight = (0, 0, 1)
        Vector3 camDir = new Vector3(1f, 0f, 0f);
        Vector3 camRight = new Vector3(0f, 0f, 1f);

        batcher.addFloorBillboard(centerX, floorY, centerZ, width, height, reg, Color.WHITE, camRight, camDir);

        FloatArray verts = batcher.getVertices();
        assertEquals(36, verts.size);

        // Looking East: bottom is nearer (X = 9.0), top is further (X = 11.0)
        float v1X = verts.get(0);
        float v1Z = verts.get(2);
        float v3X = verts.get(18);
        float v3Z = verts.get(20);

        assertEquals(9.0f, v1X, 1e-4f);
        assertEquals(-20.5f, v1Z, 1e-4f); // To player's left (North)
        assertEquals(11.0f, v3X, 1e-4f);
        assertEquals(-19.5f, v3Z, 1e-4f); // To player's right (South)

        batcher.dispose();
    }

    @Test
    public void handlesNullRegionAndNullVectorsGracefully() {
        DynamicQuadBatcher batcher = new DynamicQuadBatcher();
        batcher.addFloorBillboard(0f, 0f, 0f, 1f, 1f, null, Color.WHITE, null, null);
        assertEquals(0, batcher.getVertices().size);

        TextureRegion reg = createMockRegion(0f, 1f, 1f, 0f);
        batcher.addFloorBillboard(0f, 0f, 0f, 1f, 1f, reg, Color.WHITE, null, null);
        assertEquals(36, batcher.getVertices().size);

        batcher.dispose();
    }
}
