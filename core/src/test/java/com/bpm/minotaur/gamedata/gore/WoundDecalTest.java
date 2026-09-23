package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Test;

import static org.junit.Assert.*;

public class WoundDecalTest {

    @Test
    public void testDefaultConstructor() {
        WoundDecal decal = new WoundDecal();
        assertEquals(WoundDecal.WoundType.SLASH, decal.type);
        assertEquals(0.5f, decal.u, 0.001f);
        assertEquals(0.5f, decal.v, 0.001f);
        assertEquals(0.0f, decal.angle, 0.001f);
        assertEquals(0.25f, decal.length, 0.001f);
        assertEquals(0.06f, decal.width, 0.001f);
        assertEquals(Color.WHITE, decal.color);
    }

    @Test
    public void testWoundTypeValues() {
        assertNotNull(WoundDecal.WoundType.valueOf("SLASH"));
        assertNotNull(WoundDecal.WoundType.valueOf("SLICE"));
        assertNotNull(WoundDecal.WoundType.valueOf("STAB"));
        assertNotNull(WoundDecal.WoundType.valueOf("PUNCTURE"));
        assertNotNull(WoundDecal.WoundType.valueOf("CRUSH"));
        assertNotNull(WoundDecal.WoundType.valueOf("SCORCH"));
    }

    @Test
    public void testCustomConstructorAndCopy() {
        Color crimson = new Color(0.8f, 0.1f, 0.1f, 1f);
        WoundDecal decal = new WoundDecal(
                WoundDecal.WoundType.CRUSH,
                0.3f, 0.7f, 1.25f, 0.35f, 0.08f, crimson
        );

        assertEquals(WoundDecal.WoundType.CRUSH, decal.type);
        assertEquals(0.3f, decal.u, 0.001f);
        assertEquals(0.7f, decal.v, 0.001f);
        assertEquals(1.25f, decal.angle, 0.001f);
        assertEquals(0.35f, decal.length, 0.001f);
        assertEquals(0.08f, decal.width, 0.001f);
        assertEquals(crimson, decal.color);

        WoundDecal copy = decal.cpy();
        assertEquals(decal.type, copy.type);
        assertEquals(decal.u, copy.u, 0.001f);
        assertEquals(decal.v, copy.v, 0.001f);
        assertEquals(decal.angle, copy.angle, 0.001f);
        assertEquals(decal.length, copy.length, 0.001f);
        assertEquals(decal.width, copy.width, 0.001f);
        assertEquals(decal.color, copy.color);

        // Ensure distinct instances
        assertNotSame(decal, copy);
        assertNotSame(decal.color, copy.color);
    }

    @Test
    public void testMonsterDecalListMaxClamp() {
        Monster monster = new Monster(Monster.MonsterType.SKELETON, 0, 0);
        assertTrue(monster.getWoundDecals().isEmpty());

        // Add 15 decals (max is 12)
        for (int i = 0; i < 15; i++) {
            WoundDecal d = new WoundDecal(WoundDecal.WoundType.SLASH, 0.5f, 0.5f, 0f, 0.2f, 0.05f, Color.RED);
            d.angle = (float) i; // Unique identifier
            monster.addWoundDecal(d);
        }

        assertEquals("Monster decals must be capped at 12", 12, monster.getWoundDecals().size());
        // First 3 should have been evicted (oldest: index 0 was i=0, 1, 2)
        // Decal 0 should now be i=3
        assertEquals(3.0f, monster.getWoundDecals().get(0).angle, 0.001f);
        assertEquals(14.0f, monster.getWoundDecals().get(11).angle, 0.001f);
    }
}
