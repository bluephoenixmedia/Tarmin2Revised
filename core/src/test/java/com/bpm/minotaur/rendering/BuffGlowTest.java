package com.bpm.minotaur.rendering;

import com.bpm.minotaur.gamedata.effects.ActiveStatusEffect;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.ui.UiTheme;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** The idle first-person weapon glows while any buff is up (#199). */
public class BuffGlowTest {

    private static List<ActiveStatusEffect> fx(ActiveStatusEffect... e) {
        return Arrays.asList(e);
    }

    private static ActiveStatusEffect timed(StatusEffectType t) {
        return new ActiveStatusEffect(t, 20, 1);
    }

    @Test
    public void noBuffNoGlow() {
        assertNull(BuffGlow.colorFor(Collections.emptyList(), 0));
    }

    @Test
    public void debuffsDoNotGlow() {
        assertNull(BuffGlow.colorFor(fx(timed(StatusEffectType.POISONED), timed(StatusEffectType.OBSCURED)), 0));
        assertNull(BuffGlow.colorFor(fx(timed(StatusEffectType.CHILLED), timed(StatusEffectType.HYPOTHERMIA),
                timed(StatusEffectType.HEATSTROKE)), 0));
    }

    @Test
    public void namedBuffsTakeTheirThemeColour() {
        assertEquals(UiTheme.GOLD, BuffGlow.colorFor(fx(timed(StatusEffectType.HEROISM)), 0));
        assertEquals(UiTheme.SCHOOL_WARFARE, BuffGlow.colorFor(fx(timed(StatusEffectType.GIANT_STRENGTH)), 0));
        assertEquals(UiTheme.INFO, BuffGlow.colorFor(fx(timed(StatusEffectType.HASTED)), 0));
    }

    @Test
    public void anyOtherTimedBuffGlowsToo() {
        assertEquals(UiTheme.SCHOOL_ARCANA, BuffGlow.colorFor(fx(timed(StatusEffectType.RESIST_FIRE)), 0));
    }

    @Test
    public void heroismWinsOverOtherBuffs() {
        assertEquals(UiTheme.GOLD, BuffGlow.colorFor(fx(timed(StatusEffectType.HASTED),
                timed(StatusEffectType.HEROISM)), 0));
    }

    @Test
    public void aPermanentIntrinsicIsNotABuffThatShouldGlowForever() {
        assertNull(BuffGlow.colorFor(fx(new ActiveStatusEffect(StatusEffectType.TELEPATHY, -1, 1)), 0));
    }

    @Test
    public void anEnhancedWeaponGlowsWhenNothingElseDoes() {
        assertEquals(UiTheme.LINE_BRIGHT, BuffGlow.colorFor(Collections.emptyList(), 1));
        assertEquals(UiTheme.INFO, BuffGlow.colorFor(fx(timed(StatusEffectType.TEMP_SPEED)), 2));
    }

    @Test
    public void theGlowFadesOutDuringASwingAndBackWhenIdle() {
        float s = 1f;
        s = BuffGlow.approach(s, false, 1f);
        assertEquals(0f, s, 1e-6);
        s = BuffGlow.approach(s, true, 0.05f);
        assertTrue(s > 0f && s < 1f);
        s = BuffGlow.approach(s, true, 1f);
        assertEquals(1f, s, 1e-6);
    }

    @Test
    public void theGlowingSetIsTheOldOrderRuleWrittenOut() {
        // The list replaced "every effect from FOCUSED on, minus the cold and heat states".
        // Pin that it changed nothing: any status added later must be listed on purpose.
        java.util.Set<com.bpm.minotaur.gamedata.effects.StatusEffectType> oldRule =
                java.util.EnumSet.noneOf(com.bpm.minotaur.gamedata.effects.StatusEffectType.class);
        for (com.bpm.minotaur.gamedata.effects.StatusEffectType t : com.bpm.minotaur.gamedata.effects.StatusEffectType.values()) {
            if (t.ordinal() >= com.bpm.minotaur.gamedata.effects.StatusEffectType.FOCUSED.ordinal()
                    && t != com.bpm.minotaur.gamedata.effects.StatusEffectType.CHILLED
                    && t != com.bpm.minotaur.gamedata.effects.StatusEffectType.HYPOTHERMIA
                    && t != com.bpm.minotaur.gamedata.effects.StatusEffectType.HEATSTROKE) {
                oldRule.add(t);
            }
        }
        assertEquals(oldRule, BuffGlow.GLOWING_BUFFS);
    }
}
