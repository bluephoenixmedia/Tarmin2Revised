package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.bpm.minotaur.gamedata.DamageType;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * The variant overrides are applied in the Monster constructor, which is the only place that
 * matters: a monster spawned in a colourway has to cast and shoot as that colourway.
 */
public class NewMonsterConstructionTest {

    private MonsterDataManager dataManager;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[] { com.badlogic.gdx.Application.class },
                    (proxy, method, args) -> null);
        }
        final MonsterTemplate template = new MonsterTemplate();
        template.scale = new MonsterTemplate.ScaleData();
        template.scale.x = 1f;
        template.scale.y = 1f;
        template.maxHP = 20;
        template.armorClass = 12;
        template.intelligence = 18;
        template.isSpellcaster = true;
        template.innateSpells = Arrays.asList("FIRE_BOLT");
        template.hasRangedAttack = true;
        template.rangedProjectile = "ARROW";
        template.rangedDamageType = DamageType.PHYSICAL;

        MonsterVariant plain = new MonsterVariant();
        plain.color = MonsterColor.WHITE;
        MonsterVariant frost = new MonsterVariant();
        frost.color = MonsterColor.BLUE;
        frost.rangedProjectile = "DEATH_RAY";
        frost.rangedDamageType = DamageType.ICE;
        frost.innateSpells = Arrays.asList("RAY_OF_FROST", "CHILL_TOUCH");
        template.variants = Arrays.asList(plain, frost);

        dataManager = new MonsterDataManager() {
            @Override
            public MonsterTemplate getTemplate(Monster.MonsterType type) {
                return template;
            }
        };
    }

    private Monster spawn(MonsterColor colour) {
        com.bpm.minotaur.gamedata.spells.SpellDataManager.getInstance().load();
        AssetManager assets = new AssetManager(new InternalFileHandleResolver(), false);
        return new Monster(Monster.MonsterType.SAGE, 0, 0, colour, dataManager, assets);
    }

    @Test
    public void aColourwayWithItsOwnElementShootsThatElement() {
        Monster frost = spawn(MonsterColor.BLUE);
        assertEquals("DEATH_RAY", frost.getRangedProjectile());
        assertEquals(DamageType.ICE, frost.getRangedDamageType());
    }

    @Test
    public void aColourwayWithNoOverridesKeepsTheSpeciesBehaviour() {
        Monster plain = spawn(MonsterColor.WHITE);
        assertEquals("ARROW", plain.getRangedProjectile());
        assertEquals(DamageType.PHYSICAL, plain.getRangedDamageType());
    }

    @Test
    public void aColourwayCastsItsOwnSpellsNotTheSpeciesOnes() {
        java.util.List<String> frostSpells = spawn(MonsterColor.BLUE).getSpellbook().getPreparedSpells();
        assertTrue(frostSpells.toString(), frostSpells.contains("RAY_OF_FROST"));
        assertFalse(frostSpells.toString(), frostSpells.contains("FIRE_BOLT"));
        assertTrue(spawn(MonsterColor.WHITE).getSpellbook().getPreparedSpells().contains("FIRE_BOLT"));
    }
}
