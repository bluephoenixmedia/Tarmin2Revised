package com.bpm.minotaur.gamedata.monster;

import com.bpm.minotaur.gamedata.DamageType;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * A variant used to be only a colour tint. It can now carry its own sprite and the elemental
 * flavour that goes with it, so a Specter's four colourways look and cast differently.
 */
public class MonsterVariantTest {

    private static MonsterVariant variant(MonsterColor color, String texture) {
        MonsterVariant v = new MonsterVariant();
        v.color = color;
        v.texturePath = texture;
        return v;
    }

    @Test
    public void aVariantWithItsOwnSpriteSuppliesIt() {
        List<MonsterVariant> variants = Arrays.asList(
                variant(MonsterColor.BLUE, "images/monsters/batch1/specter_frost.png"),
                variant(MonsterColor.ORANGE, "images/monsters/batch1/specter_fire.png"));

        assertEquals("images/monsters/batch1/specter_fire.png",
                MonsterVariant.textureFor(variants, MonsterColor.ORANGE, "images/monsters/specter.png"));
        assertEquals("images/monsters/batch1/specter_frost.png",
                MonsterVariant.textureFor(variants, MonsterColor.BLUE, "images/monsters/specter.png"));
    }

    @Test
    public void aVariantWithoutASpriteFallsBackToTheSpeciesOne() {
        List<MonsterVariant> variants = Arrays.asList(variant(MonsterColor.GREEN, null));
        assertEquals("images/monsters/goblin.png",
                MonsterVariant.textureFor(variants, MonsterColor.GREEN, "images/monsters/goblin.png"));
    }

    @Test
    public void anUnknownColourOrNoVariantsFallsBack() {
        assertEquals("base.png", MonsterVariant.textureFor(null, MonsterColor.RED, "base.png"));
        assertEquals("base.png", MonsterVariant.textureFor(
                Arrays.asList(variant(MonsterColor.BLUE, "blue.png")), MonsterColor.RED, "base.png"));
    }

    @Test
    public void aVariantCanCarryItsOwnElement() {
        MonsterVariant fire = variant(MonsterColor.RED, "fire.png");
        fire.rangedProjectile = "FIREBALL";
        fire.rangedDamageType = DamageType.FIRE;
        fire.innateSpells = Arrays.asList("FIRE_BOLT", "BURNING_HANDS");

        MonsterVariant found = MonsterVariant.forColor(Arrays.asList(fire), MonsterColor.RED);

        assertSame(fire, found);
        assertEquals(DamageType.FIRE, found.rangedDamageType);
        assertEquals("FIREBALL", found.rangedProjectile);
        assertEquals(2, found.innateSpells.size());
        assertNull(MonsterVariant.forColor(Arrays.asList(fire), MonsterColor.BLUE));
        assertNull(MonsterVariant.forColor(null, MonsterColor.RED));
    }
}
