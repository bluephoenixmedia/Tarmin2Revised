package com.bpm.minotaur.gamedata.player;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.bpm.minotaur.gamedata.item.WeaponFamily;
import com.bpm.minotaur.gamedata.monster.MonsterFamily;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.polymorph.PlayerForm;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/** Unarmed Mastery hardens the fist (#313; the #303 grilling). */
public class FistDiceTest {

    private Player player;

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (p, m, a) -> null);
        }
        player = new Player(5f, 5f);
    }

    @Test
    public void eachUnarmedRankStepsTheFistDieUp() {
        String[] expected = Mastery.FIST_DICE;
        Mastery.Rank[] ranks = Mastery.Rank.values();
        assertEquals("one die per rank", ranks.length, expected.length);
        double lastMean = 0;
        for (Mastery.Rank rank : ranks) {
            player.getStats().getMastery().setRank(WeaponFamily.UNARMED, rank);
            String dice = player.getUnarmedDice();
            assertEquals(rank.name(), expected[rank.ordinal()], dice);
            double mean = com.bpm.minotaur.utils.DiceRoller.average(dice);
            assertTrue(rank + " hits harder than the rank below", mean > lastMean);
            lastMean = mean;
        }
    }

    @Test
    public void anUntrainedFistIsHarderThanTheOldOneDTwo() {
        assertNotEquals("1d2", player.getUnarmedDice());
    }

    @Test
    public void aPolymorphedBodyKeepsItsFormsDice() {
        player.getStats().getMastery().setRank(WeaponFamily.UNARMED, Mastery.Rank.MASTER);
        MonsterTemplate t = new MonsterTemplate();
        t.family = MonsterFamily.BEAST;
        t.baseLevel = 1;
        t.maxHP = 8;
        t.armorClass = 11;
        t.moveSpeed = 12;
        t.damageDice = "1d3";
        player.enterForm(PlayerForm.of("Rat", t, 100));

        assertEquals("1d3", player.getUnarmedDice());
    }
}
