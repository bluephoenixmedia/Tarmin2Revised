package com.bpm.minotaur.rendering;

import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class MonsterProceduralKinematicsTest {

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
    }

    @Test
    public void testWeightScaledInertia() {
        // Goblin (light, low HP) should have high recoil & flinch
        Monster goblin = new Monster(MonsterType.GOBLIN, 10, 10);
        float goblinInertia = goblin.getKinematicInertiaFactor();

        // Minotaur or Iron Golem (megabeast, heavy) should have low flinch factor (heavy inertia)
        Monster minotaur = new Monster(MonsterType.MINOTAUR, 120, 10);
        float minotaurInertia = minotaur.getKinematicInertiaFactor();

        assertTrue("Goblin should flinch more easily than Minotaur", goblinInertia > minotaurInertia);
        assertTrue("Minotaur inertia factor should be significantly anchored", minotaurInertia <= 0.65f);
    }

    @Test
    public void testSlashingCutAngleDeformation() {
        Monster skeleton = new Monster(MonsterType.SKELETON, 20, 10);
        // Record a slash from TOP_RIGHT to BOTTOM_LEFT (-45 degrees)
        skeleton.recordHitKinematics(Monster.KinematicHitType.SLASHING, -45f, 0.8f, null);

        float roll = skeleton.getKinematicRollDegrees();
        assertTrue("Slashing strike should cause billboard roll tilt along cut line", Math.abs(roll) > 0.01f);
        assertTrue("Roll should tilt in the direction of the cut vector", roll < 0f);
    }

    @Test
    public void testBluntSquashAndStretch() {
        Monster orc = new Monster(MonsterType.ORC, 30, 10);
        // Record an overhead blunt slam
        orc.recordHitKinematics(Monster.KinematicHitType.BLUNT_CHOP, 0f, 1.0f, null);

        float scaleY = skeletonScaleY(orc);
        float scaleX = skeletonScaleX(orc);

        assertTrue("Blunt slam should compress vertically (squash < 1.0)", scaleY < 1.0f);
        assertTrue("Blunt slam should expand horizontally (stretch > 1.0)", scaleX > 1.0f);
    }

    private float skeletonScaleY(Monster m) {
        return m.getKinematicScaleY();
    }

    private float skeletonScaleX(Monster m) {
        return m.getKinematicScaleX();
    }

    @Test
    public void testTelegraphArchetypeProfiles() {
        Monster spider = new Monster(MonsterType.SPIDER, 15, 10);
        assertEquals("Spider should use BEAST_CROUCH telegraph archetype",
                Monster.TelegraphArchetype.BEAST_CROUCH, spider.getTelegraphArchetype());

        Monster knight = new Monster(MonsterType.SKELETON, 25, 10);
        assertEquals("Skeleton warrior should use HUMANOID_COIL telegraph archetype",
                Monster.TelegraphArchetype.HUMANOID_COIL, knight.getTelegraphArchetype());

        Monster dragon = new Monster(MonsterType.DRAGON, 200, 10);
        assertEquals("Dragon/Colossus should use COLOSSUS_SEISMIC telegraph archetype",
                Monster.TelegraphArchetype.COLOSSUS_SEISMIC, dragon.getTelegraphArchetype());

        Monster ghost = new Monster(MonsterType.PLAYER_GHOST, 30, 10);
        assertEquals("Ghost/Caster should use CASTER_PULSE telegraph archetype",
                Monster.TelegraphArchetype.CASTER_PULSE, ghost.getTelegraphArchetype());
    }
}
