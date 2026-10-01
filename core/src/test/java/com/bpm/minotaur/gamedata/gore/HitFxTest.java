package com.bpm.minotaur.gamedata.gore;

import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterFamily;
import com.bpm.minotaur.rendering.vfx.FxClipIds;
import org.junit.Test;

import static org.junit.Assert.*;

public class HitFxTest {

    private static HitFx.Spec hit(Monster.MonsterType type, MonsterFamily family, float share, boolean crit, boolean kill) {
        return HitFx.forHit(GoreProfile.fromMonsterType(type), family, type, share, crit, kill);
    }

    @Test
    public void anOrdinaryHitOnAFleshyCreatureIsASmallSpurt() {
        assertEquals(FxClipIds.BLOOD_SMALL,
                hit(Monster.MonsterType.GOBLIN, MonsterFamily.HUMANOID, 0.10f, false, false).clipId);
    }

    @Test
    public void aCritAKillOrAHeavyBlowIsALargeSpurt() {
        Monster.MonsterType t = Monster.MonsterType.GOBLIN;
        assertEquals(FxClipIds.BLOOD_LARGE, hit(t, MonsterFamily.HUMANOID, 0.10f, true, false).clipId);
        assertEquals(FxClipIds.BLOOD_LARGE, hit(t, MonsterFamily.HUMANOID, 0.10f, false, true).clipId);
        assertEquals(FxClipIds.BLOOD_LARGE, hit(t, MonsterFamily.HUMANOID, 0.30f, false, false).clipId);
        assertEquals(FxClipIds.BLOOD_SMALL, hit(t, MonsterFamily.HUMANOID, 0.29f, false, false).clipId);
    }

    @Test
    public void aLargeSpurtIsBiggerOnScreenThanASmallOne() {
        Monster.MonsterType t = Monster.MonsterType.GOBLIN;
        assertTrue(hit(t, MonsterFamily.HUMANOID, 0.5f, false, false).scale
                > hit(t, MonsterFamily.HUMANOID, 0.05f, false, false).scale);
    }

    @Test
    public void undeadPuffIntoSmokeEvenThoughTheirGoreProfileHasBlood() {
        // SKELETAL has blood in the gore system (marrow, for the floor stains); the hit animation is
        // about what the creature is, and a skeleton does not spurt red.
        assertEquals(FxClipIds.HIT_SMOKE,
                hit(Monster.MonsterType.SKELETON, MonsterFamily.UNDEAD, 0.5f, true, true).clipId);
        assertEquals(FxClipIds.HIT_SMOKE,
                hit(Monster.MonsterType.ZOMBIE, MonsterFamily.UNDEAD, 0.1f, false, false).clipId);
        assertEquals(FxClipIds.HIT_SMOKE,
                hit(Monster.MonsterType.SPECTER, MonsterFamily.UNDEAD, 0.1f, false, false).clipId);
    }

    @Test
    public void constructsThrowSparksAndSlimesDoNotSpurtBlood() {
        assertEquals(FxClipIds.HIT_SPARKS,
                hit(Monster.MonsterType.IRON_GOLEM, MonsterFamily.MAGICAL, 0.5f, true, false).clipId);
        assertEquals(FxClipIds.HIT_SPARKS,
                hit(Monster.MonsterType.GARGOYLE, MonsterFamily.MAGICAL, 0.1f, false, false).clipId);
        String slime = hit(Monster.MonsterType.GELATINOUS_CUBE, MonsterFamily.NONE, 0.5f, true, true).clipId;
        assertTrue("a slime must not spurt blood: " + slime, !slime.startsWith("blood"));
    }

    @Test
    public void nothingThatDoesNotBleedEverShowsBlood() {
        for (Monster.MonsterType type : Monster.MonsterType.values()) {
            GoreProfile profile = GoreProfile.fromMonsterType(type);
            if (!profile.hasBlood) {
                assertTrue(type + " must not spurt blood",
                        !hit(type, MonsterFamily.NONE, 0.5f, true, true).clipId.startsWith("blood"));
            }
        }
    }

    @Test
    public void theNewCastersBleedAsWhatTheyAre() {
        assertEquals(GoreProfile.INCORPOREAL, GoreProfile.fromMonsterType(Monster.MonsterType.SPECTER));
        assertEquals(GoreProfile.SKELETAL, GoreProfile.fromMonsterType(Monster.MonsterType.SKELETAL_WIZARD));
        assertEquals(GoreProfile.FLESH, GoreProfile.fromMonsterType(Monster.MonsterType.BAT));
    }

    @Test
    public void theEffectIsPlacedWhereTheMonsterIsDrawn() {
        // Monsters are drawn at z = -y, and the animation renderer negates the stored z, so the
        // stored z is +y. Passing -y here put the spurt on the mirrored tile.
        com.badlogic.gdx.math.Vector3 p = HitFx.position(7.5f, 3.5f);
        assertEquals(7.5f, p.x, 0.0001f);
        assertEquals(3.5f, p.z, 0.0001f);
        assertTrue("body height", p.y > 0.2f && p.y < 1.0f);
    }
}
