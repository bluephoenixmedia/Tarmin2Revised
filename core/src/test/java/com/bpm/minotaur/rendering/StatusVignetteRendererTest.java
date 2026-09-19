package com.bpm.minotaur.rendering;

import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.injury.BodyPart;
import com.bpm.minotaur.gamedata.injury.InjuryType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.rendering.vfx.StatusVignetteRenderer;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class StatusVignetteRendererTest {

    private StatusVignetteRenderer renderer;
    private Player player;

    @Before
    public void setUp() {
        renderer = new StatusVignetteRenderer();
        player = new Player(0, 0);
    }

    @Test
    public void testBleedWeightWhenHealthyVsBleeding() {
        // Healthy player has no bleeding
        assertEquals(0f, renderer.getBleedWeight(player), 0.001f);

        // Inflict bleeding laceration
        player.getInjuryManager().inflictInjury(BodyPart.TORSO, InjuryType.LACERATION_BLEEDING, 2);
        assertTrue("Player should report active bleeding", player.getInjuryManager().isBleeding());

        float weight = renderer.getBleedWeight(player);
        assertTrue("Bleed weight should be > 0", weight > 0.15f);
        assertTrue("Bleed weight should not exceed maximum clamp (0.40f)", weight <= 0.40f);
    }

    @Test
    public void testColdWeightTiers() {
        // Normal body temperature (37°C)
        player.getStats().setBodyTemperature(37.0f);
        assertEquals(0f, renderer.getColdWeight(player), 0.001f);

        // Chilled tier (34°C)
        player.getStats().setBodyTemperature(34.0f);
        float chilledWeight = renderer.getColdWeight(player);
        assertTrue("Chilled weight should be > 0", chilledWeight > 0.10f);
        assertTrue("Chilled weight should be < 0.25f", chilledWeight < 0.25f);

        // Hypothermia tier (31°C)
        player.getStats().setBodyTemperature(31.0f);
        float hypothermiaWeight = renderer.getColdWeight(player);
        assertTrue("Hypothermia weight should be >= 0.25f", hypothermiaWeight >= 0.25f);
    }

    @Test
    public void testPoisonWeight() {
        // Normal
        assertEquals(0f, renderer.getPoisonWeight(player), 0.001f);

        // Active poison effect
        player.getStatusManager().addEffect(StatusEffectType.POISONED, 10, 1, false);
        assertTrue("Poison weight should be > 0", renderer.getPoisonWeight(player) > 0.20f);

        // High toxicity
        player.getStatusManager().removeEffect(StatusEffectType.POISONED);
        player.getStats().setToxicity(75);
        assertTrue("High toxicity should trigger poison weight", renderer.getPoisonWeight(player) > 0.15f);
    }

    @Test
    public void testStarvationAndDehydrationWeight() {
        // Full satiety and hydration
        player.getStats().setSatiety(80f);
        player.getStats().setHydration(80f);
        assertEquals(0f, renderer.getStarveWeight(player), 0.001f);

        // Starving to 0
        player.getStats().setSatiety(0f);
        assertTrue("Starvation should trigger weight", renderer.getStarveWeight(player) > 0.20f);

        // Dehydrated to 0
        player.getStats().setSatiety(80f);
        player.getStats().setHydration(0f);
        assertTrue("Dehydration should trigger weight", renderer.getStarveWeight(player) > 0.20f);
    }

    @Test
    public void testMaxTotalVignetteAlphaConstraint() {
        // Ensure constant is at 45% (0.45f)
        assertEquals(0.45f, StatusVignetteRenderer.MAX_TOTAL_VIGNETTE_ALPHA, 0.001f);
    }
}
