package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BloodSpatterGeneratorTest {

    private static final int RED = 0xC41E1E;

    @Test
    public void everySplashLandsOnTheCanvas() {
        Random rng = new Random(1);
        for (int i = 0; i < 200; i++) {
            for (BloodStain s : BloodSpatterGenerator.forKill(8, RED, rng)) {
                assertTrue(s.x >= 0f && s.x < 1024f);
                assertTrue(s.y >= 0f && s.y < 1536f);
            }
            for (BloodStain s : BloodSpatterGenerator.forWound(9, 14, RED, rng)) {
                assertTrue(s.x >= 0f && s.x < 1024f);
                assertTrue(s.y >= 0f && s.y < 1536f);
            }
        }
    }

    @Test
    public void aKillDrenchesMoreThanAHit() {
        Random rng = new Random(2);
        float hitArea = 0f;
        float killArea = 0f;
        for (int i = 0; i < 100; i++) {
            hitArea += area(BloodSpatterGenerator.forHitDealt(5, RED, rng));
            killArea += area(BloodSpatterGenerator.forKill(5, RED, rng));
        }
        assertTrue(killArea > hitArea * 3f);
    }

    @Test
    public void harderHitsSprayMore() {
        Random rng = new Random(3);
        float chip = 0f;
        float massive = 0f;
        for (int i = 0; i < 100; i++) {
            chip += area(BloodSpatterGenerator.forHitDealt(2, RED, rng));
            massive += area(BloodSpatterGenerator.forHitDealt(8, RED, rng));
        }
        assertTrue(massive > chip * 2f);
    }

    @Test
    public void sprayFavoursTheWeaponArm() {
        // His sword arm is out front, and it is on the viewer's left.
        Random rng = new Random(4);
        int weaponSide = 0;
        int offSide = 0;
        for (int i = 0; i < 300; i++) {
            for (BloodStain s : BloodSpatterGenerator.forHitDealt(5, RED, rng)) {
                if (s.x < 300f) {
                    weaponSide++;
                } else if (s.x > 724f) {
                    offSide++;
                }
            }
        }
        assertTrue("weapon side " + weaponSide + " vs off side " + offSide, weaponSide > offSide * 2);
    }

    @Test
    public void aWoundRunsDown() {
        List<BloodStain> wound = BloodSpatterGenerator.forWound(6, 14, RED, new Random(5));
        assertTrue("the wound itself drips", wound.get(0).drip >= 2f);
    }

    @Test
    public void aHeavierWoundBleedsMore() {
        float light = BloodSpatterGenerator.forWound(1, 20, RED, new Random(6)).get(0).radius;
        float heavy = BloodSpatterGenerator.forWound(18, 20, RED, new Random(6)).get(0).radius;
        assertTrue(heavy > light * 2f);
    }

    @Test
    public void nothingForNoBlood() {
        assertTrue(BloodSpatterGenerator.forHitDealt(0, RED, new Random()).isEmpty());
        assertTrue(BloodSpatterGenerator.forKill(0, RED, new Random()).isEmpty());
        assertTrue(BloodSpatterGenerator.forWound(0, 20, RED, new Random()).isEmpty());
    }

    @Test
    public void theBloodKeepsTheVictimsColour() {
        for (BloodStain s : BloodSpatterGenerator.forKill(5, 0x38E047, new Random(7))) {
            assertEquals(0x38E047, s.rgb);
        }
        assertEquals(0xC41F1F, BloodSpatterGenerator.rgb(0.77f, 0.12f, 0.12f));
    }

    @Test
    public void sameRandomSameBlood() {
        List<BloodStain> a = BloodSpatterGenerator.forKill(8, RED, new Random(42));
        List<BloodStain> b = BloodSpatterGenerator.forKill(8, RED, new Random(42));
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).x, b.get(i).x, 0f);
            assertEquals(a.get(i).seed, b.get(i).seed);
        }
    }

    @Test
    public void aimingMatchesTheMeasuredBody() throws Exception {
        // Blood is aimed with landmarks copied from the measured file. If the body art is
        // re-measured, these have to follow or the spray lands beside him.
        File file = new File("assets/data/paperdoll_landmarks.json");
        if (!file.exists()) {
            file = new File("../assets/data/paperdoll_landmarks.json");
        }
        JsonValue points = new JsonReader()
                .parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))
                .get("points");
        assertPoint(points, "hand_left", BloodSpatterGenerator.WEAPON_HAND);
        assertPoint(points, "shoulder_left", BloodSpatterGenerator.WEAPON_SHOULDER);
        assertPoint(points, "hand_right", BloodSpatterGenerator.OFF_HAND);
        assertPoint(points, "shoulder_right", BloodSpatterGenerator.OFF_SHOULDER);
        assertPoint(points, "chin", BloodSpatterGenerator.CHIN);
        assertPoint(points, "head_top", BloodSpatterGenerator.HEAD_TOP);
        assertPoint(points, "hip_left", BloodSpatterGenerator.HIP_LEFT);
        assertPoint(points, "hip_right", BloodSpatterGenerator.HIP_RIGHT);
        assertPoint(points, "ankle_left", BloodSpatterGenerator.ANKLE_LEFT);
        assertPoint(points, "ankle_right", BloodSpatterGenerator.ANKLE_RIGHT);
        assertEquals(points.get("waist_left").getFloat(1), BloodSpatterGenerator.WAIST_Y, 0.5f);
    }

    private static void assertPoint(JsonValue points, String name, float[] expected) {
        JsonValue p = points.get(name);
        assertEquals(name + " x", p.getFloat(0), expected[0], 0.5f);
        assertEquals(name + " y", p.getFloat(1), expected[1], 0.5f);
    }

    private static float area(List<BloodStain> stains) {
        float a = 0f;
        for (BloodStain s : stains) {
            a += (float) Math.PI * s.radius * s.radius;
        }
        return a;
    }
}
