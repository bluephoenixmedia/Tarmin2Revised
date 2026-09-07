package com.bpm.minotaur.lighting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class LightingManagerTest {

    private LightingManager lightingManager;

    @Before
    public void setUp() {
        lightingManager = new LightingManager();
    }

    @Test
    public void testLightSourceCreationAndProfiles() {
        LightSource campfire = new LightSource("cook_pot", 5f, 5f, LightingManager.COLOR_CAMPFIRE, 4.5f, 1.2f, LightSource.FlickerProfile.CAMPFIRE_FLICKER);
        assertEquals("cook_pot", campfire.getId());
        assertEquals(5f, campfire.getPosition().x, 0.001f);
        assertEquals(5f, campfire.getPosition().y, 0.001f);
        assertEquals(4.5f, campfire.getBaseRadius(), 0.001f);
        assertEquals(1.2f, campfire.getBaseIntensity(), 0.001f);
        assertTrue(campfire.isActive());

        // Update oscillations
        campfire.update(0.1f);
        assertNotEquals(0f, campfire.getCurrentRadius());
        assertNotEquals(0f, campfire.getCurrentIntensity());

        // Toggle active
        campfire.setActive(false);
        assertFalse(campfire.isActive());
    }

    @Test
    public void testLanternBreathProfileOscillation() {
        LightSource lantern = new LightSource("lantern_1", 4f, 4f, LightingManager.COLOR_LANTERN, 5.0f, 1.0f, LightSource.FlickerProfile.LANTERN_BREATH);
        lantern.update(1.0f);

        // Lantern has gentle breathing (within 2% of base radius: 4.9f to 5.1f)
        assertTrue("Lantern radius should stay within gentle breathing bounds",
                lantern.getCurrentRadius() >= 4.88f && lantern.getCurrentRadius() <= 5.12f);
    }

    @Test
    public void testPlayerLanternUpgrade() {
        Player player = new Player(10f, 10f);

        // Default: Torch
        lightingManager.update(0.016f, player, null);
        assertEquals(LightingManager.TORCH_RADIUS, lightingManager.getPlayerLight().getBaseRadius(), 0.01f);
        assertEquals(LightSource.FlickerProfile.TORCH_FLUTTER, lightingManager.getPlayerLight().getProfile());

        // Equip Brass Lantern in off-hand
        Item lantern = Item.fromTemplate(ItemType.BRASS_LANTERN, new ItemTemplate());
        player.getInventory().setLeftHand(lantern);

        lightingManager.update(0.016f, player, null);
        assertEquals(LightingManager.LANTERN_RADIUS, lightingManager.getPlayerLight().getBaseRadius(), 0.01f);
        assertEquals(LightSource.FlickerProfile.LANTERN_BREATH, lightingManager.getPlayerLight().getProfile());
        assertEquals(1.15f, lightingManager.getPlayerLight().getBaseIntensity(), 0.01f);
        assertEquals(LightingManager.COLOR_LANTERN.r, lightingManager.getPlayerLight().getBaseColor().r, 0.01f);

        // Unequip Lantern
        player.getInventory().setLeftHand(null);
        lightingManager.update(0.016f, player, null);
        assertEquals(LightingManager.TORCH_RADIUS, lightingManager.getPlayerLight().getBaseRadius(), 0.01f);
        assertEquals(LightSource.FlickerProfile.TORCH_FLUTTER, lightingManager.getPlayerLight().getProfile());
    }

    @Test
    public void testLineOfSightOcclusionThroughWall() {
        // Create 3x3 maze with solid wall between (0, 0) and (1, 0)
        int[][] wallData = new int[3][3];
        // West wall bitmask = 0b00000001, East wall bitmask = 0b00000100
        wallData[0][0] = 0b00000100; // Wall on East of (0,0)
        wallData[0][1] = 0b00000001; // Wall on West of (1,0)
        Maze maze = new Maze(1, wallData);

        // Ray from (0.5, 0.5) to (1.5, 0.5) must be occluded by the solid wall
        boolean occluded = lightingManager.isOccluded(0.5f, 0.5f, 1.5f, 0.5f, maze);
        assertTrue("Ray across solid wall must be occluded", occluded);

        // Ray in open space (0.5, 0.5) to (0.5, 1.5) without north wall should NOT be occluded
        boolean openRay = lightingManager.isOccluded(0.5f, 0.5f, 0.5f, 1.5f, maze);
        assertFalse("Ray in open corridor must not be occluded", openRay);
    }

    @Test
    public void testLineOfSightThroughDoorway() {
        int[][] wallData = new int[3][3];
        Maze maze = new Maze(1, wallData);
        Door door = new Door();
        maze.addGameObject(door, 1, 0);

        // Closed door blocks light
        door.setState(Door.DoorState.CLOSED, 0f);
        // Add door bitmasks: EAST door on (0,0), WEST door on (1,0)
        wallData[0][0] = Direction.EAST.getWallMask() << 1;
        wallData[0][1] = Direction.WEST.getWallMask() << 1;

        assertTrue("Closed door must block line of sight", lightingManager.isOccluded(0.5f, 0.5f, 1.5f, 0.5f, maze));

        // Open door allows light to pass through!
        door.setState(Door.DoorState.OPEN, 1f);
        assertFalse("Open door must allow line of sight light transmission", lightingManager.isOccluded(0.5f, 0.5f, 1.5f, 0.5f, maze));
    }

    @Test
    public void testCalculateLightAtWithDistanceAttenuation() {
        Color outColor = new Color();

        // 1. In pitch dark void with no lights nearby
        lightingManager.calculateLightAt(50f, 50f, null, outColor, 0.04f);
        assertTrue(outColor.r <= 0.05f);
        assertTrue(outColor.g <= 0.05f);
        assertTrue(outColor.b <= 0.05f);

        // 2. Add campfire at (10, 10)
        LightSource campfire = new LightSource("fire", 10f, 10f, LightingManager.COLOR_CAMPFIRE, 4f, 1.0f, LightSource.FlickerProfile.STEADY);
        lightingManager.addLight(campfire);

        // At center of light (10, 10)
        lightingManager.calculateLightAt(10f, 10f, null, outColor, 0.04f);
        assertTrue(outColor.r >= 0.9f);
        assertTrue(outColor.g >= 0.4f);

        // Halfway to radius (dist 2.0 of 4.0)
        lightingManager.calculateLightAt(12f, 10f, null, outColor, 0.04f);
        assertTrue(outColor.r > 0.15f && outColor.r < 0.9f);

        // Outside radius (dist 5.0 of 4.0)
        lightingManager.calculateLightAt(15f, 10f, null, outColor, 0.04f);
        assertTrue(outColor.r <= 0.05f);
    }

    @Test
    public void testGetNearestLightsSorted() {
        Vector2 origin = new Vector2(0f, 0f);
        LightSource l1 = new LightSource("far", 10f, 0f, Color.RED, 5f, 1f, LightSource.FlickerProfile.STEADY);
        LightSource l2 = new LightSource("near", 2f, 0f, Color.BLUE, 5f, 1f, LightSource.FlickerProfile.STEADY);
        LightSource l3 = new LightSource("mid", 5f, 0f, Color.GREEN, 5f, 1f, LightSource.FlickerProfile.STEADY);

        lightingManager.addLight(l1);
        lightingManager.addLight(l2);
        lightingManager.addLight(l3);

        Array<LightSource> nearest = new Array<>(false, 4);
        lightingManager.getNearestLights(origin, 3, nearest);

        assertEquals(3, nearest.size);
        // Player light at (0,0) is distance 0
        assertEquals("player_light", nearest.get(0).getId());
        assertEquals("near", nearest.get(1).getId());
        assertEquals("mid", nearest.get(2).getId());
    }
}
