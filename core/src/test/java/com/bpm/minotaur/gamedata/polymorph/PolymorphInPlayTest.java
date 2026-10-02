package com.bpm.minotaur.gamedata.polymorph;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.ScrollEffectType;
import com.bpm.minotaur.gamedata.item.WandEffectType;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterFamily;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class PolymorphInPlayTest {

    private Player player;
    private GameEventManager events;

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (p, m, a) -> null);
        }
        player = new Player(5f, 5f);
        events = new GameEventManager();
    }

    private static PlayerForm wolf() {
        MonsterTemplate t = new MonsterTemplate();
        t.family = MonsterFamily.BEAST;
        t.baseLevel = 3;
        t.maxHP = 20;
        t.armorClass = 11;
        t.moveSpeed = 24;
        t.damageDice = "1d8";
        return PlayerForm.of("Wolf", t, 200);
    }

    @Test
    public void inAFormTheFormTakesTheBlowsAndTheRealHitPointsAreSafe() {
        int real = player.getStats().getCurrentHP();
        player.enterForm(wolf());
        player.takeDamage(6, com.bpm.minotaur.gamedata.DamageType.PHYSICAL);
        assertEquals(real, player.getStats().getCurrentHP());
        assertTrue(player.getForm().hp() < 20);
        assertTrue(player.isPolymorphed());
    }

    @Test
    public void whenTheFormGivesOutYouAreYourselfAgainAndNotDead() {
        int real = player.getStats().getCurrentHP();
        player.enterForm(wolf());
        for (int i = 0; i < 40 && player.isPolymorphed(); i++) {
            player.takeDamage(10, com.bpm.minotaur.gamedata.DamageType.PHYSICAL);
        }
        assertFalse(player.isPolymorphed());
        assertEquals(real, player.getStats().getCurrentHP());
    }

    @Test
    public void aFormEndsWhenItsTimeRunsOut() {
        PlayerForm f = new PlayerForm("Rat", 5, 10, 1f, "1d2", false, false, 2);
        player.enterForm(f);
        player.tickForm(events);
        assertTrue(player.isPolymorphed());
        player.tickForm(events);
        assertFalse(player.isPolymorphed());
    }

    @Test
    public void theFormSetsArmourClassAndSpeed() {
        int baseSpeed = player.getEffectiveSpeed();
        player.enterForm(wolf());
        assertEquals(11, player.getArmorClass());
        assertTrue(player.getEffectiveSpeed() > baseSpeed);
    }

    @Test
    public void aHandlessFormCannotCastOrUseItems() {
        assertTrue(player.handsFree());
        player.enterForm(wolf());
        assertFalse(player.handsFree());
        assertFalse(player.castPreparedSpell(0, new Maze(1, new int[12][12]), events, null));
        assertFalse(player.useQuickSlot(0, events, null, new Maze(1, new int[12][12])));
    }

    @Test
    public void thePolymorphWandAndScrollExist() {
        assertNotNull(WandEffectType.valueOf("POLYMORPH"));
        assertNotNull(ScrollEffectType.valueOf("POLYMORPH"));
    }
}
