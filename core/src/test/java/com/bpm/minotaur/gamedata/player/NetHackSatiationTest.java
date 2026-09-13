package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Verifies the NetHack Satiation Spectrum: food grants zero direct HP,
 * satiety state transitions land on the documented thresholds, and eating
 * tainted monster flesh applies poison and carries a chance to grant a
 * permanent intrinsic resistance.
 */
public class NetHackSatiationTest {

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
    public void testSatiationStateThresholds() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);

        stats.setSatiety(0f);
        assertEquals(PlayerStats.SatiationState.STARVING, stats.getSatiationState());

        stats.setSatiety(25f);
        assertEquals(PlayerStats.SatiationState.HUNGRY, stats.getSatiationState());

        stats.setSatiety(50f);
        assertEquals(PlayerStats.SatiationState.NORMAL, stats.getSatiationState());

        stats.setSatiety(105f);
        assertEquals(PlayerStats.SatiationState.SATIATED, stats.getSatiationState());

        stats.setSatiety(120f);
        assertEquals(PlayerStats.SatiationState.CHOKING, stats.getSatiationState());
    }

    @Test
    public void testFeastingGrantsZeroDirectHealing() {
        Player player = new Player(2, 2);
        player.getStats().setSatiety(50f);
        int maxHp = player.getStats().getMaxHP();
        player.getStats().setCurrentHP(maxHp / 2);
        int hpBefore = player.getCurrentHP();

        ItemTemplate mealTemplate = new ItemTemplate();
        mealTemplate.friendlyName = "Hearty Stew";
        mealTemplate.isFood = true;
        mealTemplate.nutrition = 30;
        mealTemplate.hydrationValue = 20;
        Item meal = Item.fromTemplate(Item.ItemType.MEAL, mealTemplate);

        GameEventManager eventManager = new GameEventManager();
        player.feastOnMeal(meal, eventManager);

        assertEquals("Eating must never directly restore HP (NetHack model)", hpBefore, player.getCurrentHP());
        assertTrue("Feasting should still raise satiety", player.getStats().getSatiety() > 50);
    }

    @Test
    public void testEatingTaintedFleshAppliesPoison() {
        Player player = new Player(2, 2);
        player.getStats().setSatiety(50f);

        Item flesh = tenderFlesh(Monster.MonsterType.GIANT_SCORPION);
        GameEventManager eventManager = new GameEventManager();
        player.useItem(flesh, eventManager, null, null);

        assertTrue("Raw scorpion flesh should poison the eater",
                player.getStatusManager().hasEffect(StatusEffectType.POISONED));
    }

    @Test
    public void testEatingSnakeFleshAppliesPoison() {
        // The plan explicitly names SNAKE_FLESH as a poison trigger alongside scorpion/ghoul/etc.
        Player player = new Player(2, 2);
        player.getStats().setSatiety(50f);

        Item flesh = tenderFlesh(Monster.MonsterType.GIANT_SNAKE);
        GameEventManager eventManager = new GameEventManager();
        player.useItem(flesh, eventManager, null, null);

        assertTrue("Raw snake flesh should poison the eater",
                player.getStatusManager().hasEffect(StatusEffectType.POISONED));
    }

    @Test
    public void testTaintedFleshCarriesAChanceOfPoisonResistance() {
        int trials = 300;
        int grantedCount = 0;
        for (int i = 0; i < trials; i++) {
            Player player = new Player(2, 2);
            player.getStats().setSatiety(50f);
            Item flesh = tenderFlesh(Monster.MonsterType.GIANT_SCORPION);
            player.useItem(flesh, new GameEventManager(), null, null);
            if (player.getStatusManager().hasEffect(StatusEffectType.RESIST_POISON)) {
                grantedCount++;
            }
        }
        // ~25% expected; a wide tolerance keeps this from flaking on RNG variance
        // while still catching a broken (0% or 100%) intrinsic roll.
        assertTrue("Expected some but not all trials to grant Poison Resistance (~25%), got " + grantedCount + "/" + trials,
                grantedCount > 10 && grantedCount < trials - 10);
    }

    private Item tenderFlesh(Monster.MonsterType source) {
        ItemTemplate template = new ItemTemplate();
        template.friendlyName = "Flesh";
        template.isFood = true;
        template.nutrition = 8;
        template.hydrationValue = 0;
        Item item = Item.fromTemplate(Item.ItemType.MEAT, template);
        item.setCorpseSource(source);
        return item;
    }
}
