package com.bpm.minotaur.gamedata.monster;

import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.MagicResistance;
import org.junit.Test;

import static org.junit.Assert.*;

public class MonsterMagicResistanceTest {

    /** A monster with no type affinity for weapons or spells, so only magic resistance shows. */
    private static Monster.MonsterType neutralType() {
        for (Monster.MonsterType t : Monster.MonsterType.values()) {
            if (Monster.getAffinity(t, DamageType.SPIRITUAL) == Monster.Affinity.NEUTRAL
                    && Monster.getAffinity(t, DamageType.PHYSICAL) == Monster.Affinity.NEUTRAL) {
                return t;
            }
        }
        throw new AssertionError("no monster type is neutral to both weapons and spells");
    }

    private static Monster monster(int resistance) {
        Monster m = new Monster(neutralType(), 100, 10);
        m.setMagicResistance(resistance);
        return m;
    }

    @Test
    public void aMonsterWithNoResistanceTakesFullSpellDamage() {
        Monster m = monster(0);
        assertEquals(20, m.takeSpellDamage(20, DamageType.SPIRITUAL, false));
        assertEquals(80, m.getCurrentHP());
    }

    @Test
    public void resistanceCutsSpellDamageByThatPercentage() {
        Monster m = monster(50);
        int dealt = m.takeSpellDamage(20, DamageType.SPIRITUAL, false);
        assertEquals(10, dealt);
        assertEquals(90, m.getCurrentHP());
    }

    @Test
    public void aWeaponBlowIsNotAMagicalOneAndIgnoresTheResistance() {
        Monster m = monster(60);
        int dealt = m.takeDamage(20, DamageType.PHYSICAL, false);
        assertEquals(20, dealt);
    }

    @Test
    public void monsterResistanceIsCappedBelowImmunity() {
        Monster m = monster(500);
        assertEquals(MagicResistance.MONSTER_CAP, m.getMagicResistance());
        assertTrue("even a maximum resist lets some magic through",
                m.takeSpellDamage(100, DamageType.SPIRITUAL, false) >= 1);
    }

    @Test
    public void theStrongestMonstersCarryRealResistanceInTheData() throws Exception {
        java.io.File f = new java.io.File("../assets/data/monsters.json");
        if (!f.exists()) {
            f = new java.io.File("assets/data/monsters.json");
        }
        com.badlogic.gdx.utils.JsonValue root = new com.badlogic.gdx.utils.JsonReader().parse(new java.io.FileReader(f));
        assertTrue("a Lich should resist magic",
                root.get("LICH").getInt("magicResistance", 0) >= 40);
        assertTrue("a Beholder should resist magic",
                root.get("BEHOLDER").getInt("magicResistance", 0) >= 40);
        assertEquals("a Goblin is no sorcerer's match", 0, root.get("GOBLIN").getInt("magicResistance", 0));
        for (com.badlogic.gdx.utils.JsonValue m = root.child; m != null; m = m.next) {
            int r = m.getInt("magicResistance", 0);
            assertTrue(m.name + " resistance " + r, r >= 0 && r <= MagicResistance.MONSTER_CAP);
        }
    }
}
