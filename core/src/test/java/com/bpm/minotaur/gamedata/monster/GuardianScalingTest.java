package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.generation.theme.ChunkTheme;
import com.bpm.minotaur.generation.theme.ThemeDataManager;
import com.bpm.minotaur.generation.theme.ThemeDefinition;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/** A theme Guardian is met at most a little out of its depth (#302; the #256 grilling). */
public class GuardianScalingTest {

    @Test
    public void aGuardianIsCappedTwoLevelsOverTheStratum() {
        assertEquals(4, GuardianScaling.cappedLevel(18, 2));
        assertEquals("never raised above its own level", 5, GuardianScaling.cappedLevel(5, 9));
        assertEquals(1, GuardianScaling.cappedLevel(18, -3));
    }

    @Test
    public void hpAndDamageAreCutInProportion() {
        // The Bringer at depth 2: level 18 to level 4.
        GuardianScaling.Stats s = GuardianScaling.scale(18, 260, "4d12+10", 2, null);
        assertEquals(4, s.level);
        assertEquals(Math.round(260 * 4 / 18f), s.maxHp);
        assertEquals("36 a hit becomes about 8", 36.0 * 4 / 18, GuardianScaling.averageOf(s.damageDice), 0.6);
    }

    @Test
    public void aGuardianAlreadyWithinTheCapIsLeftAlone() {
        GuardianScaling.Stats s = GuardianScaling.scale(5, 84, "1d10+1", 5, null);
        assertEquals(5, s.level);
        assertEquals(84, s.maxHp);
        assertEquals("1d10+1", s.damageDice);
    }

    @Test
    public void theFloorKeepsAScaledGuardianAtSixtyPercentOfASameLevelHeavy() {
        GuardianScaling.Heavy heavy = new GuardianScaling.Heavy(100, "4d6");
        GuardianScaling.Stats s = GuardianScaling.scale(18, 60, "1d4", 1, heavy);
        assertTrue("hp floored at 60%: " + s.maxHp, s.maxHp >= 60);
        assertTrue("damage floored at 60%: " + s.damageDice,
                GuardianScaling.averageOf(s.damageDice) >= 0.6 * GuardianScaling.averageOf("4d6") - 0.5);
    }

    @Test
    public void theCeilingHoldsACutGuardianToASameLevelHeavy() {
        // Cut in proportion the Bringer would keep 58 hit points and 8 a blow: far over a level-4 Heavy.
        GuardianScaling.Heavy heavy = new GuardianScaling.Heavy(30, "1d8+2");
        GuardianScaling.Stats s = GuardianScaling.scale(18, 260, "4d12+10", 2, heavy);
        assertTrue("hp at most the Heavy's: " + s.maxHp, s.maxHp <= Math.round(GuardianScaling.HP_CEILING * 30));
        assertTrue("damage at most the ceiling: " + s.damageDice, GuardianScaling.averageOf(s.damageDice)
                <= GuardianScaling.DAMAGE_CEILING * GuardianScaling.averageOf("1d8+2") + 0.6);
    }

    @Test
    public void anUncutGuardianStillOutOfItsDepthIsHeldToTheCeiling() {
        // The Ogre at EDL 3: level 5 is within EDL+2, so it is not cut, but its Guardian bonus
        // would still make it a certain death: out of its depth, it is held all the same.
        GuardianScaling.Heavy heavy = new GuardianScaling.Heavy(34, "1d10+1");
        GuardianScaling.Stats s = GuardianScaling.scale(5, 84, "1d10+1", 3, heavy, 1.6f);
        assertEquals(5, s.level);
        assertEquals(Math.round(1.6f * 34), s.maxHp);
        GuardianScaling.Stats home = GuardianScaling.scale(5, 84, "1d10+1", 5, heavy, 1.6f);
        assertEquals("at its own depth, it is itself", 84, home.maxHp);
    }

    @Test
    public void aCutGuardianCarriesTheFactorItsShotsAndSpellsAreCutBy() {
        GuardianScaling.Stats cut = GuardianScaling.scale(18, 260, "4d12+10", 2, null);
        assertEquals(GuardianScaling.averageOf(cut.damageDice) / 36.0, cut.damageScale, 1e-3);
        assertEquals("uncut, nothing changes", 1f, GuardianScaling.scale(5, 84, "1d10+1", 5, null).damageScale, 0f);
        Monster m = new Monster(Monster.MonsterType.OGRE, 30, 10, 0, 0);
        m.setDamageScale(0.25f);
        assertEquals(5, m.scaledDamage(20));
        assertEquals("never under one", 1, m.scaledDamage(1));
    }

    @Test
    public void theYardstickHeavyIsTheSameWhateverOrderTheMonstersLoaded() {
        MonsterTemplate owlbear = heavy(5, 32), ogre = heavy(5, 34), alligator = heavy(4, 30);
        java.util.Map<Monster.MonsterType, MonsterTemplate> t = new java.util.HashMap<>();
        t.put(Monster.MonsterType.OWLBEAR, owlbear);
        t.put(Monster.MonsterType.OGRE, ogre);
        t.put(Monster.MonsterType.ALLIGATOR, alligator);
        for (java.util.List<Monster.MonsterType> order : java.util.Arrays.asList(
                java.util.Arrays.asList(Monster.MonsterType.OWLBEAR, Monster.MonsterType.OGRE, Monster.MonsterType.ALLIGATOR),
                java.util.Arrays.asList(Monster.MonsterType.ALLIGATOR, Monster.MonsterType.OGRE, Monster.MonsterType.OWLBEAR))) {
            MonsterDataManager data = new MonsterDataManager() {
                @Override
                public MonsterTemplate getTemplate(Monster.MonsterType type) {
                    return t.get(type);
                }

                @Override
                public java.util.List<Monster.MonsterType> getLoadedTypes() {
                    return order;
                }
            };
            assertEquals("the toughest of the highest level: the Ogre", 34, GuardianScaling.heavyOfLevel(5, data).hp);
        }
    }

    private static MonsterTemplate heavy(int level, int hp) {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = level;
        t.maxHP = hp;
        t.damageDice = "1d8+2";
        t.role = MonsterRole.HEAVY;
        return t;
    }

    @Test
    public void diceKeepTheirAverage() {
        assertEquals(36.0, GuardianScaling.averageOf("4d12+10"), 1e-9);
        assertEquals(6.5, GuardianScaling.averageOf("1d10+1"), 1e-9);
        assertEquals(5.0, GuardianScaling.averageOf("5"), 1e-9);
        for (double target : new double[]{1.2, 3, 8, 15.5, 30}) {
            String dice = GuardianScaling.diceForAverage("4d12+10", target);
            assertEquals(dice, target, GuardianScaling.averageOf(dice), 0.6);
            assertTrue(dice, dice.matches("\\d+d\\d+(\\+\\d+)?"));
        }
    }

    /** Every theme with a Guardian, met at its theme's shallowest depth, is within EDL+2. */
    @Test
    public void everyGuardianAtItsThemesShallowestDepthIsWithinTheCap() throws IOException {
        JsonValue monsters = monsters();
        int guardians = 0;
        for (ChunkTheme theme : ChunkTheme.values()) {
            ThemeDefinition def = ThemeDataManager.getInstance().get(theme);
            if (def == null || def.getChampionType() == null) continue;
            guardians++;
            JsonValue m = find(monsters, def.getChampionType().name());
            assertNotNull(def.getChampionType() + " in monsters.json", m);
            int base = m.getInt("baseLevel");
            int depth = theme.getMinLevel();
            GuardianScaling.Stats s = GuardianScaling.scale(base, m.getInt("maxHP") + def.getChampionHpBonus(),
                    m.getString("damageDice"), depth, null);
            assertTrue(theme + ": level " + s.level + " at depth " + depth, s.level <= depth + GuardianScaling.OUT_OF_DEPTH);
            if (base > depth + GuardianScaling.OUT_OF_DEPTH) {
                assertTrue(theme + ": damage cut", GuardianScaling.averageOf(s.damageDice)
                        < GuardianScaling.averageOf(m.getString("damageDice")));
            }
            assertNotNull(theme + ": every Guardian carries a title", def.getGuardianTitle());
            assertEquals(def.getGuardianTitle(), com.bpm.minotaur.ui.UiGlyphs.sanitize(def.getGuardianTitle()));
        }
        assertTrue(guardians >= 4);
    }

    private static JsonValue monsters() throws IOException {
        for (String candidate : new String[]{"assets/data/monsters.json", "../assets/data/monsters.json"}) {
            Path path = Paths.get(candidate);
            if (Files.exists(path)) {
                return new JsonReader().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            }
        }
        throw new IOException("monsters.json not found");
    }

    /** The monster's entry, wherever monsters.json nests it. */
    private static JsonValue find(JsonValue node, String name) {
        for (JsonValue c = node.child; c != null; c = c.next) {
            if (name.equals(c.name) && c.has("baseLevel")) return c;
            JsonValue deeper = find(c, name);
            if (deeper != null) return deeper;
        }
        return null;
    }
}
