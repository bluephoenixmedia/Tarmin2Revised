package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.injury.BodyPart;
import com.bpm.minotaur.gamedata.injury.InjuryManager;
import com.bpm.minotaur.gamedata.injury.InjuryRecord;
import com.bpm.minotaur.gamedata.injury.InjuryType;
import org.junit.Test;

import static org.junit.Assert.*;

public class DamageTextAnimationTest {

    @Test
    public void testDamageTextAnimationInitialization() {
        GridPoint2 pos = new GridPoint2(10, 15);
        Animation anim = new Animation(
                Animation.AnimationType.DAMAGE_TEXT,
                pos,
                "CRIT! 28",
                Color.GOLD,
                1.2f,
                true,
                false,
                DamageType.PHYSICAL
        );

        assertEquals(Animation.AnimationType.DAMAGE_TEXT, anim.getType());
        assertEquals("CRIT! 28", anim.getDamageText());
        assertEquals(pos, anim.getTextPosition());
        assertTrue("Should be critical", anim.isCritical());
        assertFalse("Should not be player damage", anim.isPlayerDamage());
        assertEquals(DamageType.PHYSICAL, anim.getDamageType());

        // Drift offset should be within ±15 range
        float drift = anim.getDriftOffset();
        assertTrue("Drift offset should be within [-15, 15]", drift >= -15.01f && drift <= 15.01f);
    }

    @Test
    public void testPlayerDamageTextAnimation() {
        Animation anim = new Animation(
                Animation.AnimationType.DAMAGE_TEXT,
                null,
                "-14 HP",
                Color.RED,
                1.2f,
                false,
                true,
                DamageType.PHYSICAL
        );

        assertEquals("-14 HP", anim.getDamageText());
        assertNull(anim.getTextPosition());
        assertTrue("Should be player damage", anim.isPlayerDamage());
        assertFalse("Should not be critical", anim.isCritical());
    }

    @Test
    public void testInjuryManagerIsBleedingLifecycle() {
        InjuryManager manager = new InjuryManager();
        assertFalse("New manager should not report bleeding", manager.isBleeding());

        InjuryRecord rec = manager.inflictInjury(BodyPart.LEGS, InjuryType.LACERATION_BLEEDING, 2);
        assertNotNull(rec);
        assertTrue("Inflicted laceration should be bleeding", rec.isBleeding());
        assertTrue("Manager should report active bleeding", manager.isBleeding());

        // Treat the wound
        rec.setTreated(true);
        assertFalse("Treated wound should stop bleeding", rec.isBleeding());
        assertFalse("Manager should no longer report bleeding", manager.isBleeding());
    }
}
