package com.bpm.minotaur.paperdoll;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class PaperDoll3DAssetsTest {

    private static final String ASSET_DIR = "../assets/models/player/";
    private static final String FALLBACK_DIR = "assets/models/player/";

    private File getFile(String relativePath) {
        File f = new File(ASSET_DIR + relativePath);
        if (!f.exists()) {
            f = new File(FALLBACK_DIR + relativePath);
        }
        return f;
    }

    @Test
    public void testPaladinPlayerAssetsExist() {
        String[] requiredFiles = {
                "paladin.obj",
                "paladin.mtl",
                "Paladin_diffuse.png",
                "paladin_sword.obj",
                "paladin_shield.obj"
        };

        for (String file : requiredFiles) {
            File f = getFile(file);
            assertTrue("Required 3D paperdoll asset must exist: " + file, f.exists());
            assertTrue("3D paperdoll asset must not be empty: " + file, f.length() > 0);
        }
    }

    @Test
    public void testPaladinObjStructure() throws Exception {
        File objFile = getFile("paladin.obj");
        assertTrue("paladin.obj must exist", objFile.exists());

        List<String> lines = Files.readAllLines(objFile.toPath());
        long vCount = lines.stream().filter(l -> l.startsWith("v ")).count();
        long vtCount = lines.stream().filter(l -> l.startsWith("vt ")).count();
        long fCount = lines.stream().filter(l -> l.startsWith("f ")).count();

        assertTrue("paladin.obj must have vertices (found " + vCount + ")", vCount >= 7000);
        assertTrue("paladin.obj must have UVs (found " + vtCount + ")", vtCount >= 20000);
        assertTrue("paladin.obj must have faces (found " + fCount + ")", fCount >= 7000);
        assertTrue("paladin.obj must reference paladin.mtl",
                lines.stream().anyMatch(l -> l.contains("mtllib paladin.mtl")));
    }
}
