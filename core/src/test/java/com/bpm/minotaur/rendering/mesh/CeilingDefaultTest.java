package com.bpm.minotaur.rendering.mesh;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * The dungeon ceiling defaults to the standard wall texture.
 *
 * <p>A ceiling is the same masonry seen from below, so the wall is the right
 * default and the authored ceiling_N images are the quarter that breaks it up.
 *
 * <p>Two wrong answers were shipped before this one. It reused floor.png, which
 * is what prompted the request. Then, when ceiling.png was assumed to exist and
 * did not, the set silently dropped it and promoted ceiling_1.jpg -- putting one
 * arbitrary image on three quarters of every dungeon ceiling.
 */
public class CeilingDefaultTest {

    private File asset(String name) {
        File f = new File("assets/images/" + name);
        if (!f.exists()) f = new File("../assets/images/" + name);
        return f;
    }

    @Test
    public void theCeilingDefaultTextureExists() {
        // The whole failure mode was a default that was silently absent.
        assertTrue("wall.png must exist to serve as the ceiling default",
                asset("wall.png").exists());
    }

    @Test
    public void theCeilingVariantsExist() {
        int found = 0;
        for (int i = 1; i <= 8; i++) {
            if (asset("ceiling_" + i + ".jpg").exists()) found++;
        }
        assertEquals("All eight authored ceiling variants should be present", 8, found);
    }

    @Test
    public void thereIsStillNoCeilingPngToMistakeForADefault() {
        // If one is ever added, this fails and whoever adds it has to decide
        // deliberately whether it replaces wall.png as the default, rather than
        // silently becoming it.
        assertFalse("A ceiling.png now exists -- decide whether it should become"
                + " the default instead of wall.png", asset("ceiling.png").exists());
    }

    @Test
    public void theFloorDefaultIsNotReusedForCeilings() {
        // The original complaint: ceilings were just the floor texture.
        assertTrue(asset("floor.png").exists());
        assertNotEquals("The ceiling default must not be the floor texture",
                "floor.png", "wall.png");
    }
}
