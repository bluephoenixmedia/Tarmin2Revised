package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.rendering.attract.AttractAtmosphereManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class AttractAtmosphereManagerTest {

    private AttractAtmosphereManager atmosphere;

    @Before
    public void setUp() {
        atmosphere = new AttractAtmosphereManager();
    }

    @Test
    public void test24HourLightingArcProgression() {
        // Lakelands Dawn (t = 5s)
        atmosphere.update(0.1f, 5.0f, new Vector3(), null);
        Color dawnAmb = atmosphere.getAmbientColor();
        assertTrue("Dawn ambient should have blue morning tint", dawnAmb.b >= dawnAmb.r);

        // Forest High Noon (t = 25s)
        atmosphere.update(0.1f, 25.0f, new Vector3(), null);
        Color noonDir = atmosphere.getDirectionalLightColor();
        assertTrue("Noon directional light should be bright (> 0.7)", noonDir.r > 0.7f && noonDir.g > 0.7f);

        // Desert Dusk (t = 45s)
        atmosphere.update(0.1f, 45.0f, new Vector3(), null);
        Color duskDir = atmosphere.getDirectionalLightColor();
        assertTrue("Dusk directional light should be warm fiery red/orange (r > b)", duskDir.r > duskDir.b);

        // Mountain Twilight (t = 60s)
        atmosphere.update(0.1f, 60.0f, new Vector3(), null);
        Color twiAmb = atmosphere.getAmbientColor();
        assertTrue("Twilight ambient should be darker (< 0.4)", twiAmb.r < 0.4f);

        // Castle Midnight Storm (t = 80s)
        atmosphere.update(0.1f, 80.0f, new Vector3(), null);
        assertTrue("Castle sector should have rain active", atmosphere.isPrecipitationActive());
    }

    @Test
    public void testLightningFlashMechanic() {
        // Trigger lightning flash manually or advance in Castle sector
        atmosphere.triggerLightningFlash(null);
        assertTrue("Lightning should be flashing", atmosphere.isLightningActive());

        Color flashLight = atmosphere.getDirectionalLightColor();
        assertTrue("Lightning flash directional light should spike (> 1.0)", flashLight.b >= 1.0f);

        // Advance 0.3s past flash duration
        atmosphere.update(0.3f, 80.0f, new Vector3(), null);
        assertFalse("Flash should subside after duration", atmosphere.isLightningActive());
    }

    @Test
    public void testFogDistanceModulation() {
        // In the Castle storm, fog should be dense (closer)
        atmosphere.update(0.1f, 80.0f, new Vector3(), null);
        float castleFogDist = atmosphere.getFogDistance();

        // In Lakelands dawn, fog should be more open
        atmosphere.update(0.1f, 5.0f, new Vector3(), null);
        float dawnFogDist = atmosphere.getFogDistance();

        assertTrue("Castle storm fog distance should be denser than dawn", castleFogDist < dawnFogDist);
    }
}
