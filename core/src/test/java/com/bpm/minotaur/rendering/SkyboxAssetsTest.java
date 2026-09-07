package com.bpm.minotaur.rendering;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertTrue;

public class SkyboxAssetsTest {

    private static final String ASSET_DIR = "../assets/models/skybox/";

    @Test
    public void testGeneratedLandmarkModelsExist() {
        String[] requiredAssets = {
                "castle_tarmin.obj",
                "castle_tarmin.mtl",
                "south_spire.obj",
                "south_spire.mtl",
                "mountain_ring.obj",
                "mountain_ring.mtl",
                "west_cumulus.obj",
                "west_cumulus.mtl",
                "celestial_sun.obj",
                "celestial_moon.obj",
                "skybox_landmarks.blend"
        };

        for (String assetName : requiredAssets) {
            File f = new File(ASSET_DIR + assetName);
            if (!f.exists()) {
                // Fallback to direct path from root
                f = new File("assets/models/skybox/" + assetName);
            }
            assertTrue("Required skybox asset must exist: " + assetName, f.exists());
            assertTrue("Skybox asset must not be empty: " + assetName, f.length() > 0);
        }
    }

    @Test
    public void testShadersExist() {
        String[] shaders = {
                "shaders/retro_skybox.vert",
                "shaders/retro_skybox.frag"
        };

        for (String shader : shaders) {
            File f = new File("../assets/" + shader);
            if (!f.exists()) {
                f = new File("assets/" + shader);
            }
            assertTrue("Shader must exist: " + shader, f.exists());
            assertTrue("Shader must not be empty: " + shader, f.length() > 0);
        }
    }
}
