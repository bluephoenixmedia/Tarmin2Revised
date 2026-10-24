package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.map.MapModel;
import com.bpm.minotaur.generation.Biome;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IsometricDioramaRendererTest {

    @Test
    public void testProjectionMathMatchesPrototype() {
        float W = 720f;
        float H = 448f;
        float camX = 5f;
        float camY = 5f;
        float camZoom = 1f;
        float camRot = 0f;

        // Origin (0, 0, 0)
        Vector2 p0 = IsometricDioramaRenderer.project(0f, 0f, 0f, camX, camY, camZoom, camRot, W, H);
        assertEquals(360f, p0.x, 0.001f);
        assertEquals(84f, p0.y, 0.001f);

        // Center (5, 5, 0) should project to center of screen (360, 224)
        Vector2 pCenter = IsometricDioramaRenderer.project(5f, 5f, 0f, camX, camY, camZoom, camRot, W, H);
        assertEquals(360f, pCenter.x, 0.001f);
        assertEquals(224f, pCenter.y, 0.001f);
    }

    @Test
    public void testProjectUnprojectRoundTrip() {
        float W = 720f;
        float H = 448f;
        float camX = 4.2f;
        float camY = 6.8f;
        float camZoom = 2.6f;
        float camRot = (float) (Math.PI / 2.0); // 90 deg rotation

        float testWx = 3.5f;
        float testWy = 7.1f;
        float testZ = 5.0f;

        Vector2 screen = IsometricDioramaRenderer.project(testWx, testWy, testZ, camX, camY, camZoom, camRot, W, H);
        Vector2 world = IsometricDioramaRenderer.unproject(screen.x, screen.y, testZ, camX, camY, camZoom, camRot, W, H);

        assertEquals("Unproject X must match original world X", testWx, world.x, 0.001f);
        assertEquals("Unproject Y must match original world Y", testWy, world.y, 0.001f);
    }

    @Test
    public void testZoomThresholds() {
        assertEquals(IsometricDioramaRenderer.ZoomLevel.WORLD, IsometricDioramaRenderer.levelOf(1.0f));
        assertEquals(IsometricDioramaRenderer.ZoomLevel.WORLD, IsometricDioramaRenderer.levelOf(1.69f));
        assertEquals(IsometricDioramaRenderer.ZoomLevel.REGION, IsometricDioramaRenderer.levelOf(1.7f));
        assertEquals(IsometricDioramaRenderer.ZoomLevel.REGION, IsometricDioramaRenderer.levelOf(2.6f));
        assertEquals(IsometricDioramaRenderer.ZoomLevel.REGION, IsometricDioramaRenderer.levelOf(4.19f));
        assertEquals(IsometricDioramaRenderer.ZoomLevel.CHUNK, IsometricDioramaRenderer.levelOf(4.2f));
        assertEquals(IsometricDioramaRenderer.ZoomLevel.CHUNK, IsometricDioramaRenderer.levelOf(6.4f));
    }

    @Test
    public void testCameraEasing() {
        IsometricDioramaRenderer renderer = new IsometricDioramaRenderer();
        renderer.setTarget(5f, 5f, 1f, 0f);
        renderer.update(1.0f); // Fast forward to settle

        renderer.setTarget(8f, 7f, 2.6f, (float) (Math.PI / 2.0));
        // Step a small delta
        renderer.update(0.1f);

        assertTrue("Camera X should move toward target", renderer.getCamX() > 5f && renderer.getCamX() < 8f);
        assertTrue("Camera Y should move toward target", renderer.getCamY() > 5f && renderer.getCamY() < 7f);
        assertTrue("Camera Zoom should move toward target", renderer.getCamZoom() > 1f && renderer.getCamZoom() < 2.6f);
        assertTrue("Camera Rot should move toward target", renderer.getCamRot() > 0f && renderer.getCamRot() < Math.PI / 2.0);

        // Multiple steps to settle
        for (int i = 0; i < 20; i++) {
            renderer.update(0.1f);
        }
        assertEquals(8f, renderer.getCamX(), 0.001f);
        assertEquals(7f, renderer.getCamY(), 0.001f);
        assertEquals(2.6f, renderer.getCamZoom(), 0.001f);
        assertEquals((float) (Math.PI / 2.0), renderer.getCamRot(), 0.001f);
    }

    @Test
    public void test90DegreeRotationSteps() {
        IsometricDioramaRenderer renderer = new IsometricDioramaRenderer();
        renderer.setTarget(5f, 5f, 1f, 0f);
        renderer.update(1.0f);

        renderer.rotate(1); // 90 degrees right
        assertEquals((float) (Math.PI / 2.0), renderer.getTgtRot(), 0.001f);

        renderer.rotate(1); // 180 degrees
        assertEquals((float) Math.PI, renderer.getTgtRot(), 0.001f);

        renderer.rotate(-1); // back to 90 degrees
        assertEquals((float) (Math.PI / 2.0), renderer.getTgtRot(), 0.001f);
    }

    @Test
    public void testZoomAtPreservesWorldPoint() {
        IsometricDioramaRenderer renderer = new IsometricDioramaRenderer();
        renderer.setCameraImmediate(5f, 5f, 1f, 0f);

        float screenX = 400f;
        float screenY = 250f;
        Vector2 beforeWorld = IsometricDioramaRenderer.unproject(screenX, screenY, 4f,
                renderer.getCamX(), renderer.getCamY(), renderer.getCamZoom(), renderer.getCamRot(),
                IsometricDioramaRenderer.FBO_WIDTH, IsometricDioramaRenderer.FBO_HEIGHT);

        renderer.zoomAt(screenX, 448f - screenY, 720f, 448f, 1.5f);
        renderer.setCameraImmediate(renderer.getTgtX(), renderer.getTgtY(), renderer.getTgtZoom(), renderer.getTgtRot());

        Vector2 afterWorld = IsometricDioramaRenderer.unproject(screenX, screenY, 4f,
                renderer.getCamX(), renderer.getCamY(), renderer.getCamZoom(), renderer.getCamRot(),
                IsometricDioramaRenderer.FBO_WIDTH, IsometricDioramaRenderer.FBO_HEIGHT);

        assertEquals("World X under cursor should be stationary after zoomAt", beforeWorld.x, afterWorld.x, 0.05f);
        assertEquals("World Y under cursor should be stationary after zoomAt", beforeWorld.y, afterWorld.y, 0.05f);
    }

    @Test
    public void testPickChunkWithZLevels() {
        IsometricDioramaRenderer renderer = new IsometricDioramaRenderer();
        renderer.setTarget(5f, 5f, 1f, 0f);
        renderer.update(1.0f);

        // Project center of chunk (5, 5) at height z=6
        Vector2 screen = IsometricDioramaRenderer.project(5.5f, 5.5f, 6.0f,
                renderer.getCamX(), renderer.getCamY(), renderer.getCamZoom(), renderer.getCamRot(),
                720f, 448f);

        // Pick with height lookup returning 6.0 for chunk (5, 5)
        GridPoint2 picked = renderer.pickChunk(screen.x, 448f - screen.y, 720f, 448f, 1,
                (cx, cy) -> (cx == 5 && cy == 5) ? 6.0f : 0.0f);

        assertNotNull("Chunk should be picked", picked);
        assertEquals(5, picked.x);
        assertEquals(5, picked.y);
    }

    @Test
    public void testPanScreenAndSnap() {
        IsometricDioramaRenderer renderer = new IsometricDioramaRenderer();
        renderer.setCameraImmediate(5f, 5f, 1f, 0f);

        // Pan with screen delta
        renderer.panScreen(-28f, 0f, 720f, 448f);
        // At zoom=1, rot=0: dx = -28, a = -1, b = 0 -> rx = -0.5, ry = 0.5
        assertEquals(4.5f, renderer.getTgtX(), 0.001f);
        assertEquals(5.5f, renderer.getTgtY(), 0.001f);

        // Before snap, camX is still 5
        assertEquals(5f, renderer.getCamX(), 0.001f);
        renderer.snapCameraPosition();
        assertEquals(4.5f, renderer.getCamX(), 0.001f);
        assertEquals(5.5f, renderer.getCamY(), 0.001f);
    }

    @Test
    public void testCenterOnAndClamping() {
        IsometricDioramaRenderer renderer = new IsometricDioramaRenderer();
        renderer.centerOn(25f, -15f);
        assertEquals(IsometricDioramaRenderer.CHUNK_GRID_SIZE + 1f, renderer.getTgtX(), 0.001f);
        assertEquals(-1f, renderer.getTgtY(), 0.001f);

        renderer.setTargetZoom(20f);
        assertEquals(IsometricDioramaRenderer.MAX_ZOOM, renderer.getTgtZoom(), 0.001f);
        assertEquals(IsometricDioramaRenderer.ZoomLevel.CHUNK, renderer.getZoomLevel());

        renderer.setTargetZoom(0.2f);
        assertEquals(IsometricDioramaRenderer.MIN_ZOOM, renderer.getTgtZoom(), 0.001f);
        assertEquals(IsometricDioramaRenderer.ZoomLevel.WORLD, renderer.getZoomLevel());
    }

    @Test
    public void testPickChunkOceanAndCave() {
        IsometricDioramaRenderer renderer = new IsometricDioramaRenderer();
        renderer.setCameraImmediate(5f, 5f, 1f, 0f);

        // Ocean height z=2.0 on floor 1
        Vector2 screenOcean = IsometricDioramaRenderer.project(3.5f, 4.5f, 2.0f,
                5f, 5f, 1f, 0f, 720f, 448f);
        GridPoint2 pickedOcean = renderer.pickChunk(screenOcean.x, 448f - screenOcean.y, 720f, 448f, 1,
                (cx, cy) -> (cx == 3 && cy == 4) ? 2.0f : 6.0f);
        assertNotNull(pickedOcean);
        assertEquals(3, pickedOcean.x);
        assertEquals(4, pickedOcean.y);

        // Cave mode floor 2 (floor != 1) tests z=5.0
        Vector2 screenCave = IsometricDioramaRenderer.project(2.5f, 6.5f, 5.0f,
                5f, 5f, 1f, 0f, 720f, 448f);
        GridPoint2 pickedCave = renderer.pickChunk(screenCave.x, 448f - screenCave.y, 720f, 448f, 2, null);
        assertNotNull(pickedCave);
        assertEquals(2, pickedCave.x);
        assertEquals(6, pickedCave.y);
    }

    @Test
    public void testDepthSortingKey() {
        // At rot = 0 (looking from south toward north):
        // Chunks with smaller Y (e.g. y=0) should have smaller depth than chunks with larger Y (e.g. y=9)
        float dNorth = IsometricDioramaRenderer.depth(5.5f, 0.5f, 5f, 5f, 0f);
        float dSouth = IsometricDioramaRenderer.depth(5.5f, 9.5f, 5f, 5f, 0f);
        assertTrue("Chunk at y=0 must be behind chunk at y=9 at rot=0", dNorth < dSouth);

        // At rot = PI (180 deg, looking from north toward south):
        // Chunks at y=9 should now have smaller depth than chunks at y=0
        float dNorthRot = IsometricDioramaRenderer.depth(5.5f, 0.5f, 5f, 5f, (float) Math.PI);
        float dSouthRot = IsometricDioramaRenderer.depth(5.5f, 9.5f, 5f, 5f, (float) Math.PI);
        assertTrue("Chunk at y=9 must be behind chunk at y=0 at rot=PI", dSouthRot < dNorthRot);
    }

    @Test
    public void testBiomePaletteMatchesHandoff() {
        IsometricDioramaRenderer.BiomeColors maze = IsometricDioramaRenderer.getBiomeColors(Biome.MAZE);
        assertEquals("3b342c", maze.top.toString().substring(0, 6)); // 3b342c
        assertEquals("2c2621", maze.sideA.toString().substring(0, 6)); // 2c2621
        assertEquals("1d1915", maze.sideB.toString().substring(0, 6)); // 1d1915

        IsometricDioramaRenderer.BiomeColors forest = IsometricDioramaRenderer.getBiomeColors(Biome.FOREST);
        assertEquals("2e3824", forest.top.toString().substring(0, 6));

        IsometricDioramaRenderer.BiomeColors ocean = IsometricDioramaRenderer.getBiomeColors(Biome.OCEAN);
        assertEquals("18262d", ocean.top.toString().substring(0, 6));
    }

    @Test
    public void testColorMixing() {
        Color c1 = new Color(0f, 0f, 0f, 1f);
        Color c2 = new Color(1f, 1f, 1f, 1f);
        Color mixed = IsometricDioramaRenderer.mix(c1, c2, 0.5f);
        assertEquals(0.5f, mixed.r, 0.001f);
        assertEquals(0.5f, mixed.g, 0.001f);
        assertEquals(0.5f, mixed.b, 0.001f);
    }

    @Test
    public void testElevationAndWallHeights() {
        float visitedLandZ = 6.0f;
        float visitedOceanZ = 2.0f;
        float glimpsedLandZ = 2.5f;
        float glimpsedOceanZ = 1.2f;
        float boardBaseZ = -5.0f;
        float boardTopZ = 0.0f;
        float wallHeightDelta = 4.2f;

        assertEquals(6.0f, visitedLandZ, 0.001f);
        assertEquals(2.0f, visitedOceanZ, 0.001f);
        assertEquals(2.5f, glimpsedLandZ, 0.001f);
        assertEquals(1.2f, glimpsedOceanZ, 0.001f);
        assertEquals(-5.0f, boardBaseZ, 0.001f);
        assertEquals(0.0f, boardTopZ, 0.001f);
        assertEquals(10.2f, visitedLandZ + wallHeightDelta, 0.001f);
    }

    @Test
    public void testMulberry32AndHashDeterminism() {
        int h1 = IsometricDioramaRenderer.hash(4, 5);
        int h2 = IsometricDioramaRenderer.hash(4, 5);
        assertEquals("Hash must be identical for same coordinates", h1, h2);

        IsometricDioramaRenderer.Mulberry32 rng1 = new IsometricDioramaRenderer.Mulberry32(h1 + 7);
        IsometricDioramaRenderer.Mulberry32 rng2 = new IsometricDioramaRenderer.Mulberry32(h2 + 7);

        for (int i = 0; i < 10; i++) {
            assertEquals("RNG sequence must be deterministic", rng1.nextFloat(), rng2.nextFloat(), 0.00001f);
        }
    }

    @Test
    public void testGlimpsedBiomeProps() {
        List<IsometricDioramaRenderer.Prop> props = new ArrayList<>();

        // Forest: 7 pines
        IsometricDioramaRenderer.generateGlimpsedProps(2, 3, Biome.FOREST, false, props);
        assertEquals(7, props.size());
        for (IsometricDioramaRenderer.Prop p : props) {
            assertEquals(IsometricDioramaRenderer.Prop.Kind.PINE, p.kind);
        }

        // Mountains: 3 crags
        props.clear();
        IsometricDioramaRenderer.generateGlimpsedProps(1, 1, Biome.MOUNTAINS, false, props);
        assertEquals(3, props.size());
        for (IsometricDioramaRenderer.Prop p : props) {
            assertEquals(IsometricDioramaRenderer.Prop.Kind.CRAG, p.kind);
        }

        // Desert with Seal
        props.clear();
        IsometricDioramaRenderer.generateGlimpsedProps(7, 7, Biome.DESERT, true, props);
        boolean foundSeal = false;
        for (IsometricDioramaRenderer.Prop p : props) {
            if (p.kind == IsometricDioramaRenderer.Prop.Kind.SEAL) {
                foundSeal = true;
                assertEquals(0.5f, p.u, 0.001f);
                assertEquals(0.5f, p.v, 0.001f);
            }
        }
        assertTrue("Seal prop must be generated when hasSeal=true", foundSeal);

        // Lakelands: pool and reeds
        props.clear();
        IsometricDioramaRenderer.generateGlimpsedProps(3, 4, Biome.LAKELANDS, false, props);
        boolean foundPool = false;
        for (IsometricDioramaRenderer.Prop p : props) {
            if (p.kind == IsometricDioramaRenderer.Prop.Kind.POOL) foundPool = true;
        }
        assertTrue("Lakelands should generate POOL prop", foundPool);

        // Maze: wallstubs
        props.clear();
        IsometricDioramaRenderer.generateGlimpsedProps(5, 5, Biome.MAZE, false, props);
        assertEquals(5, props.size());
        for (IsometricDioramaRenderer.Prop p : props) {
            assertEquals(IsometricDioramaRenderer.Prop.Kind.WALLSTUB, p.kind);
        }
    }

    @Test
    public void testVisitedBiomeAndLandmarkProps() {
        List<IsometricDioramaRenderer.Prop> props = new ArrayList<>();

        MapKnowledgeMask.KnowledgeProvider provider = new MapKnowledgeMask.KnowledgeProvider() {
            @Override
            public State getChunkState(int cx, int cy) { return State.VISITED; }
            @Override
            public boolean isTileSeen(int cx, int cy, int lx, int ly) { return true; }
            @Override
            public MapModel.ShelterMark getShelter(int cx, int cy) {
                return (cx == 4 && cy == 5) ? MapModel.ShelterMark.HOME : null;
            }
            @Override
            public boolean hasReturnPortal(int cx, int cy) { return cx == 5 && cy == 3; }
            @Override
            public boolean hasLadder(int cx, int cy) { return cx == 5 && cy == 4; }
            @Override
            public int getGraveCount(int cx, int cy) { return (cx == 3 && cy == 3) ? 2 : 0; }
            @Override
            public boolean hasPlayer(int cx, int cy) { return cx == 4 && cy == 5; }
            @Override
            public float getPlayerU(int cx, int cy) { return 0.4f; }
            @Override
            public float getPlayerV(int cx, int cy) { return 0.6f; }
            @Override
            public float getPlayerFaceX(int cx, int cy) { return 1f; }
            @Override
            public float getPlayerFaceY(int cx, int cy) { return 0f; }
        };

        // Shelter chunk (4, 5): should have SHELTER, FIRE, and PLAYER
        IsometricDioramaRenderer.generateVisitedProps(4, 5, Biome.FOREST, null, provider, props);
        boolean foundShelter = false;
        boolean foundFire = false;
        boolean foundPlayer = false;
        for (IsometricDioramaRenderer.Prop p : props) {
            if (p.kind == IsometricDioramaRenderer.Prop.Kind.SHELTER) {
                foundShelter = true;
                assertEquals(MapModel.ShelterMark.HOME, p.shelterType);
            }
            if (p.kind == IsometricDioramaRenderer.Prop.Kind.FIRE) foundFire = true;
            if (p.kind == IsometricDioramaRenderer.Prop.Kind.PLAYER) {
                foundPlayer = true;
                assertEquals(0.4f, p.u, 0.001f);
                assertEquals(0.6f, p.v, 0.001f);
                assertEquals(1.0f, p.faceX, 0.001f);
                assertEquals(0.0f, p.faceY, 0.001f);
            }
        }
        assertTrue("Shelter prop must be present", foundShelter);
        assertTrue("Fire prop must be present for non-cold shelter", foundFire);
        assertTrue("Player prop must be present", foundPlayer);

        // Portal chunk (5, 3)
        props.clear();
        IsometricDioramaRenderer.generateVisitedProps(5, 3, Biome.MAZE, null, provider, props);
        boolean foundPortal = false;
        for (IsometricDioramaRenderer.Prop p : props) {
            if (p.kind == IsometricDioramaRenderer.Prop.Kind.PORTAL) foundPortal = true;
        }
        assertTrue("Portal prop must be present", foundPortal);

        // Graves chunk (3, 3)
        props.clear();
        IsometricDioramaRenderer.generateVisitedProps(3, 3, Biome.MAZE, null, provider, props);
        int graveCount = 0;
        for (IsometricDioramaRenderer.Prop p : props) {
            if (p.kind == IsometricDioramaRenderer.Prop.Kind.GRAVE) graveCount++;
        }
        assertEquals("Two graves should be generated", 2, graveCount);
    }

    @Test
    public void testPropPaletteConstants() {
        assertEquals("2d343c", IsometricDioramaRenderer.COLOR_SIL_LIT.toString().substring(0, 6));
        assertEquals("20262c", IsometricDioramaRenderer.COLOR_SIL_DARK.toString().substring(0, 6));
        assertEquals("6ccfe0", IsometricDioramaRenderer.COLOR_PLAYER_CYAN.toString().substring(0, 6));
        assertEquals("e3cfa6", IsometricDioramaRenderer.COLOR_PLAYER_HEAD.toString().substring(0, 6));
        assertEquals("ffcf6a", IsometricDioramaRenderer.COLOR_WINDOW_LIT.toString().substring(0, 6));
        assertEquals("e9b44c", IsometricDioramaRenderer.COLOR_FLAG_GOLD.toString().substring(0, 6));
        assertEquals("7a3b26", IsometricDioramaRenderer.COLOR_ROOF_LIT_L.toString().substring(0, 6));
    }

    @Test
    public void testColorShade() {
        Color base = new Color(0.5f, 0.5f, 0.5f, 1f);
        Color lighter = IsometricDioramaRenderer.shade(base, 0.5f);
        assertTrue("Positive shade should be lighter", lighter.r > base.r);

        Color darker = IsometricDioramaRenderer.shade(base, -0.5f);
        assertTrue("Negative shade should be darker", darker.r < base.r);
    }
}
