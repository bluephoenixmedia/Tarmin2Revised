package com.bpm.minotaur.gamedata.polymorph;

import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import com.bpm.minotaur.gamedata.monster.MonsterFamily;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import org.junit.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;

public class PolymorphRulesTest {

    private static Map<MonsterType, Integer> levels() {
        Map<MonsterType, Integer> m = new EnumMap<>(MonsterType.class);
        m.put(MonsterType.KOBOLD, 1);
        m.put(MonsterType.GOBLIN, 1);
        m.put(MonsterType.ORC, 3);
        m.put(MonsterType.OGRE, 5);
        m.put(MonsterType.TROLL, 6);
        m.put(MonsterType.DRAGON, 10);
        m.put(MonsterType.LICH, 15);
        return m;
    }

    @Test
    public void aMonsterBecomesAnotherOfAboutItsLevelAndNeverItself() {
        Random rng = new Random(7);
        for (int i = 0; i < 300; i++) {
            MonsterType t = PolymorphRules.pickMonsterType(levels(), MonsterType.ORC, 3, false, rng);
            assertNotNull(t);
            assertNotEquals(MonsterType.ORC, t);
            assertTrue(t + " is too far from level 3", Math.abs(levels().get(t) - 3) <= PolymorphRules.LEVEL_WINDOW);
            assertNotEquals("a level 3 monster should never become a dragon", MonsterType.DRAGON, t);
        }
    }

    @Test
    public void theHighLevelSpellIgnoresTheLevelWindow() {
        Random rng = new Random(3);
        boolean sawFar = false;
        for (int i = 0; i < 300; i++) {
            MonsterType t = PolymorphRules.pickMonsterType(levels(), MonsterType.KOBOLD, 1, true, rng);
            sawFar |= levels().get(t) > 1 + PolymorphRules.LEVEL_WINDOW;
        }
        assertTrue(sawFar);
    }

    @Test
    public void noCandidateMeansNoPolymorph() {
        Map<MonsterType, Integer> only = new EnumMap<>(MonsterType.class);
        only.put(MonsterType.LICH, 15);
        assertNull(PolymorphRules.pickMonsterType(only, MonsterType.LICH, 15, true, new Random(1)));
        Map<MonsterType, Integer> far = new EnumMap<>(MonsterType.class);
        far.put(MonsterType.KOBOLD, 1);
        far.put(MonsterType.LICH, 15);
        assertNull("nothing near level 15 but itself", PolymorphRules.pickMonsterType(far, MonsterType.LICH, 15, false, new Random(1)));
    }

    @Test
    public void weaponsStayWeaponsAndMeleeStaysMelee() {
        ItemTemplate sword = new ItemTemplate();
        sword.isWeapon = true;
        ItemTemplate bow = new ItemTemplate();
        bow.isWeapon = true;
        bow.isRanged = true;
        assertEquals("weapon:melee", PolymorphRules.categoryOf(sword));
        assertEquals("weapon:ranged", PolymorphRules.categoryOf(bow));
    }

    @Test
    public void armourKeepsItsSlot() {
        ItemTemplate helm = new ItemTemplate();
        helm.isArmor = true;
        helm.isHelmet = true;
        ItemTemplate boots = new ItemTemplate();
        boots.isArmor = true;
        boots.isBoots = true;
        ItemTemplate shield = new ItemTemplate();
        shield.isArmor = true;
        shield.isShield = true;
        assertEquals("armor:helmet", PolymorphRules.categoryOf(helm));
        assertEquals("armor:boots", PolymorphRules.categoryOf(boots));
        assertEquals("armor:shield", PolymorphRules.categoryOf(shield));
    }

    @Test
    public void keysContainersTreasureAndBeltItemsCannotBePolymorphed() {
        ItemTemplate key = new ItemTemplate();
        key.isKey = true;
        ItemTemplate chest = new ItemTemplate();
        chest.isContainer = true;
        ItemTemplate gold = new ItemTemplate();
        gold.isTreasure = true;
        ItemTemplate lantern = new ItemTemplate();
        lantern.isBeltClip = true;
        assertNull(PolymorphRules.categoryOf(key));
        assertNull(PolymorphRules.categoryOf(chest));
        assertNull(PolymorphRules.categoryOf(gold));
        assertNull(PolymorphRules.categoryOf(lantern));
        assertNull(PolymorphRules.categoryOf(new ItemTemplate()));
    }

    @Test
    public void anItemOnlyBecomesSomethingOfTheSameCategory() {
        Map<String, String> cats = new HashMap<>();
        cats.put("SWORD", "weapon:melee");
        cats.put("AXE", "weapon:melee");
        cats.put("BOW", "weapon:ranged");
        cats.put("FOOD", "food");
        cats.put("KEY", null);
        List<String> options = PolymorphRules.sameCategory(cats, "SWORD");
        assertEquals(1, options.size());
        assertEquals("AXE", options.get(0));
        assertTrue(PolymorphRules.sameCategory(cats, "KEY").isEmpty());
        assertTrue("a lone item has nothing to become", PolymorphRules.sameCategory(cats, "BOW").isEmpty());
    }

    private static MonsterTemplate template(MonsterFamily family, int level, int hp, int ac, int speed, String dice) {
        MonsterTemplate t = new MonsterTemplate();
        t.family = family;
        t.baseLevel = level;
        t.maxHP = hp;
        t.armorClass = ac;
        t.moveSpeed = speed;
        t.damageDice = dice;
        return t;
    }

    @Test
    public void aFormTakesItsNumbersFromTheMonster() {
        PlayerForm f = PlayerForm.of("Wolf", template(MonsterFamily.BEAST, 3, 20, 13, 18, "1d8"), 150);
        assertEquals(20, f.hp());
        assertEquals(13, f.armorClass());
        assertEquals("1d8", f.damageDice());
        assertFalse("a beast has no hands", f.hasHands());
        assertFalse(f.burstsArmor());
        assertEquals(24, f.applySpeed(16));
    }

    @Test
    public void humanoidsKeepTheirHandsAndGiantsBurstTheirArmour() {
        assertTrue(PlayerForm.of("Orc", template(MonsterFamily.HUMANOID, 3, 20, 13, 12, "1d6"), 100).hasHands());
        assertTrue(PlayerForm.of("Troll", template(MonsterFamily.HUMANOID, 9, 70, 15, 12, "2d10"), 100).burstsArmor());
    }

    @Test
    public void aFormIsSpentWhenItsHitPointsOrItsTimeRunOut() {
        PlayerForm f = PlayerForm.of("Orc", template(MonsterFamily.HUMANOID, 3, 10, 13, 12, "1d6"), 3);
        assertFalse(f.isSpent());
        f.absorb(4);
        assertEquals(6, f.hp());
        assertFalse(f.isSpent());
        f.absorb(99);
        assertTrue(f.isSpent());

        PlayerForm timed = PlayerForm.of("Orc", template(MonsterFamily.HUMANOID, 3, 10, 13, 12, "1d6"), 2);
        assertFalse(timed.tick());
        assertTrue(timed.tick());
        assertTrue(timed.isSpent());
    }

    @Test
    public void durationsAreWithinTheAgreedRange() {
        Random rng = new Random(5);
        for (int i = 0; i < 500; i++) {
            int t = PlayerForm.randomTurns(rng);
            assertTrue(t >= PlayerForm.MIN_TURNS && t <= PlayerForm.MAX_TURNS);
        }
    }
}
