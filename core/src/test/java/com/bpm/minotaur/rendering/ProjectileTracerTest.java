package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.ShortArray;
import com.bpm.minotaur.rendering.mesh.ChunkMeshBuilder;
import com.bpm.minotaur.rendering.mesh.DynamicQuadBatcher;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProjectileTracerTest {

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

    private FloatArray getVertices(DynamicQuadBatcher batcher) throws Exception {
        Field f = DynamicQuadBatcher.class.getDeclaredField("vertices");
        f.setAccessible(true);
        return (FloatArray) f.get(batcher);
    }

    private ShortArray getIndices(DynamicQuadBatcher batcher) throws Exception {
        Field f = DynamicQuadBatcher.class.getDeclaredField("indices");
        f.setAccessible(true);
        return (ShortArray) f.get(batcher);
    }

    @Test
    public void chunkMeshBuilderSupportsPerVertexColors() {
        FloatArray verts = new FloatArray();
        ShortArray indices = new ShortArray();

        float c1 = Color.WHITE.toFloatBits();
        float c2 = Color.YELLOW.toFloatBits();
        float c3 = Color.RED.toFloatBits();
        float c4 = Color.BLUE.toFloatBits();

        ChunkMeshBuilder.addQuad(
                verts, indices,
                0, 0, 0, 0, 1, c1,
                1, 0, 0, 1, 1, c2,
                1, 1, 0, 1, 0, c3,
                0, 1, 0, 0, 0, c4,
                0, 0, 1
        );

        // 4 vertices * 9 floats = 36 floats
        assertEquals(36, verts.size);
        assertEquals(6, indices.size);

        // Color is at vertex offset 6 (x, y, z, nx, ny, nz, color, u, v)
        assertEquals(c1, verts.get(6), 1e-4f);
        assertEquals(c2, verts.get(15), 1e-4f);
        assertEquals(c3, verts.get(24), 1e-4f);
        assertEquals(c4, verts.get(33), 1e-4f);
    }

    @Test
    public void tracerStreakEmitsTaperedRibbonWithGradientColors() throws Exception {
        DynamicQuadBatcher batcher = new DynamicQuadBatcher();

        // Arrow flying along -Z (North): from (0, 0.45, 0) to (0, 0.45, -5)
        // Camera looking from (0, 0.5, 2)
        Vector3 camPos = new Vector3(0f, 0.5f, 2f);
        Vector3 fallbackRight = new Vector3(1f, 0f, 0f);

        float headX = 0f, headY = 0.45f, headZ = -2.0f;
        float tailX = 0f, tailY = 0.45f, tailZ = -1.2f; // tail is behind head along -Z
        float headWidth = 0.026f;
        float tailWidth = 0.010f;

        Color headCol = new Color(1.0f, 0.96f, 0.86f, 1.0f);
        Color tailCol = new Color(0.82f, 0.70f, 0.48f, 0.15f);

        batcher.addTracerStreak(
                headX, headY, headZ,
                tailX, tailY, tailZ,
                headWidth, tailWidth,
                camPos, fallbackRight,
                headCol, tailCol
        );

        FloatArray verts = getVertices(batcher);
        ShortArray indices = getIndices(batcher);

        assertEquals(36, verts.size);
        assertEquals(6, indices.size);

        // Head vertices (v1, v2) should carry headColor
        float packedHead = headCol.toFloatBits();
        float packedTail = tailCol.toFloatBits();

        assertEquals(packedHead, verts.get(6), 1e-4f);  // V1 color
        assertEquals(packedHead, verts.get(15), 1e-4f); // V2 color
        assertEquals(packedTail, verts.get(24), 1e-4f); // V3 color
        assertEquals(packedTail, verts.get(33), 1e-4f); // V4 color

        // V1 is head left: X should be negative (subtracted side * headWidth)
        float v1X = verts.get(0);
        float v1Z = verts.get(2);
        // V2 is head right: X should be positive (added side * headWidth)
        float v2X = verts.get(9);
        float v2Z = verts.get(11);

        assertEquals(-headWidth, v1X, 1e-3f);
        assertEquals(headWidth, v2X, 1e-3f);
        assertEquals(headZ, v1Z, 1e-3f);
        assertEquals(headZ, v2Z, 1e-3f);

        // V3 and V4 are tail vertices: width should be tailWidth
        float v3X = verts.get(18);
        float v3Z = verts.get(20);
        float v4X = verts.get(27);
        float v4Z = verts.get(29);

        assertEquals(tailWidth, v3X, 1e-3f);
        assertEquals(-tailWidth, v4X, 1e-3f);
        assertEquals(tailZ, v3Z, 1e-3f);
        assertEquals(tailZ, v4Z, 1e-3f);

        batcher.dispose();
    }

    @Test
    public void tracerStreakViewedEndOnFallsBackToRightVector() throws Exception {
        DynamicQuadBatcher batcher = new DynamicQuadBatcher();

        // Exact line of sight collinear with camera
        Vector3 camPos = new Vector3(0f, 0.45f, 0f);
        Vector3 fallbackRight = new Vector3(1f, 0f, 0f);

        // Flying straight away from camera along -Z
        float headX = 0f, headY = 0.45f, headZ = -3f;
        float tailX = 0f, tailY = 0.45f, tailZ = -2f;

        batcher.addTracerStreak(
                headX, headY, headZ,
                tailX, tailY, tailZ,
                0.02f, 0.01f,
                camPos, fallbackRight,
                Color.WHITE, Color.GRAY
        );

        FloatArray verts = getVertices(batcher);
        // Width expansion must have occurred along fallbackRight (X axis)
        float v1X = verts.get(0);
        float v2X = verts.get(9);
        assertTrue("V1 should expand left along X axis", v1X < 0f);
        assertTrue("V2 should expand right along X axis", v2X > 0f);

        batcher.dispose();
    }
}
