package com.bpm.minotaur.gamedata.gore;

import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.rendering.vfx.FxClipIds;
import org.junit.Test;

import static org.junit.Assert.*;

public class HitFxTest {

    @Test
    public void anOrdinaryHitOnAFleshyCreatureIsASmallSpurt() {
        HitFx.Spec spec = HitFx.forHit(GoreProfile.FLESH, 0.10f, false, false);
        assertEquals(FxClipIds.BLOOD_SMALL, spec.clipId);
    }

    @Test
    public void aCritAKillOrAHeavyBlowIsALargeSpurt() {
        assertEquals(FxClipIds.BLOOD_LARGE, HitFx.forHit(GoreProfile.FLESH, 0.10f, true, false).clipId);
        assertEquals(FxClipIds.BLOOD_LARGE, HitFx.forHit(GoreProfile.FLESH, 0.10f, false, true).clipId);
        assertEquals(FxClipIds.BLOOD_LARGE, HitFx.forHit(GoreProfile.FLESH, 0.30f, false, false).clipId);
        assertEquals(FxClipIds.BLOOD_SMALL, HitFx.forHit(GoreProfile.FLESH, 0.29f, false, false).clipId);
    }

    @Test
    public void aLargeSpurtIsBiggerOnScreenThanASmallOne() {
        assertTrue(HitFx.forHit(GoreProfile.FLESH, 0.5f, false, false).scale
                > HitFx.forHit(GoreProfile.FLESH, 0.05f, false, false).scale);
    }

    @Test
    public void somethingThatDoesNotBleedGetsSmokeOrSparksNeverBlood() {
        HitFx.Spec ghost = HitFx.forHit(GoreProfile.INCORPOREAL, 0.5f, true, true);
        assertEquals(FxClipIds.HIT_SMOKE, ghost.clipId);
        for (GoreProfile p : GoreProfile.values()) {
            if (!p.hasBlood) {
                String id = HitFx.forHit(p, 0.5f, true, true).clipId;
                assertTrue(p + " must not spurt blood", !id.startsWith("blood"));
            }
        }
    }

    @Test
    public void theNewCastersBleedAsWhatTheyAre() {
        assertEquals(GoreProfile.INCORPOREAL, GoreProfile.fromMonsterType(Monster.MonsterType.SPECTER));
        assertEquals(GoreProfile.SKELETAL, GoreProfile.fromMonsterType(Monster.MonsterType.SKELETAL_WIZARD));
        assertEquals(GoreProfile.FLESH, GoreProfile.fromMonsterType(Monster.MonsterType.BAT));
    }
}
