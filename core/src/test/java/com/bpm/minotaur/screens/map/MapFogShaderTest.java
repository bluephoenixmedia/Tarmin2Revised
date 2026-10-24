package com.bpm.minotaur.screens.map;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class MapFogShaderTest {

    @Test
    public void testShaderFilesExistAndDeclareUniforms() throws Exception {
        File vert = new File("../assets/shaders/map_fog.vert");
        if (!vert.exists()) vert = new File("assets/shaders/map_fog.vert");
        assertTrue("map_fog.vert must exist", vert.exists());
        assertTrue("map_fog.vert must not be empty", vert.length() > 0);

        File frag = new File("../assets/shaders/map_fog.frag");
        if (!frag.exists()) frag = new File("assets/shaders/map_fog.frag");
        assertTrue("map_fog.frag must exist", frag.exists());
        assertTrue("map_fog.frag must not be empty", frag.length() > 0);

        String fragContent = new String(Files.readAllBytes(frag.toPath()));
        assertTrue("Must declare u_texBody", fragContent.contains("uniform sampler2D u_texBody;"));
        assertTrue("Must declare u_texShade", fragContent.contains("uniform sampler2D u_texShade;"));
        assertTrue("Must declare u_texHi", fragContent.contains("uniform sampler2D u_texHi;"));
        assertTrue("Must declare u_texWisp", fragContent.contains("uniform sampler2D u_texWisp;"));
        assertTrue("Must declare u_mask", fragContent.contains("uniform sampler2D u_mask;"));
        assertTrue("Must declare u_origin", fragContent.contains("uniform vec2 u_origin;"));
        assertTrue("Must declare u_camPos", fragContent.contains("uniform vec2 u_camPos;"));
        assertTrue("Must declare u_camZoom", fragContent.contains("uniform float u_camZoom;"));
        assertTrue("Must declare u_camRot", fragContent.contains("uniform float u_camRot;"));
    }
}
