package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.Nemesis;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** A Nemesis in the maze wears its name and grows a level with every life it took (#283). */
public class NemesisForgeTest {

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
    }

    private static Monster kobold() {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 2;
        Monster m = new Monster(Monster.MonsterType.KOBOLD, 5, 5);
        m.setTemplate(t);
        m.setMaxHP(20);
        m.setCurrentHP(20);
        return m;
    }

    private static Nemesis gorvak(int lives) {
        Nemesis n = new Nemesis(4, "Gorvak the Gnawer", -1, "KOBOLD", 3, 1, 1);
        for (int i = 0; i < lives; i++) n.victims.add("Life " + i);
        return n;
    }

    @Test
    public void itWearsItsNameAndEpithet() {
        Monster m = kobold();
        NemesisForge.dress(m, gorvak(1));
        assertEquals("Gorvak the Gnawer, Slayer of Life 0", m.getName());
        assertEquals(4, m.getNemesisId());
    }

    @Test
    public void itIsOneLevelHigherAndHardierForEveryLifeItTook() {
        Monster once = kobold();
        NemesisForge.dress(once, gorvak(1));
        Monster thrice = kobold();
        NemesisForge.dress(thrice, gorvak(3));
        assertEquals(3, once.getLevel());
        assertEquals(5, thrice.getLevel());
        assertTrue(thrice.getMaxHP() > once.getMaxHP());
        assertEquals(thrice.getMaxHP(), thrice.getCurrentHP());
    }
}
