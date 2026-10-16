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
 * Verifies NetHack's nutrition scale (#191): 900 at the start burning 1 a turn, the
 * Satiated / Hungry / Weak / Fainting / Starved thresholds and their effects, choking,
 * food values, legacy save migration, that food grants zero direct HP, and that eating
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
    public void aFedStartLastsAboutOneLongExpeditionBeforeHunger() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);
        assertEquals(900f, stats.getSatietyFloat(), 0f);
        assertEquals(Hunger.Stage.NOT_HUNGRY, stats.getHungerStage());

        int turns = 0;
        while (!stats.getHungerStage().isHungry()) {
            stats.modifySatiety(-Hunger.burn(1f, 1f, 1f));
            turns++;
        }
        assertEquals("1 nutrition a turn from 900 reaches Hungry (below 150) on turn 751", 751, turns);
    }

    @Test
    public void aHeavyLoadBurnsFaster() {
        assertEquals(1f, Hunger.burn(1f, 1f, 1f), 0f);
        assertTrue(Hunger.burn(1.5f, 1f, 1f) > Hunger.burn(1f, 1f, 1f));
    }

    @Test
    public void stagesLandOnTheNetHackThresholds() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM); // CON 10: starved below -200
        float[] nutrition = {1001, 1000, 150, 149, 50, 49, 0, -1, -200, -201};
        Hunger.Stage[] expected = {
                Hunger.Stage.SATIATED, Hunger.Stage.NOT_HUNGRY, Hunger.Stage.NOT_HUNGRY,
                Hunger.Stage.HUNGRY, Hunger.Stage.HUNGRY, Hunger.Stage.WEAK, Hunger.Stage.WEAK,
                Hunger.Stage.FAINTING, Hunger.Stage.FAINTING, Hunger.Stage.STARVED
        };
        for (int i = 0; i < nutrition.length; i++) {
            stats.setSatiety(nutrition[i]);
            assertEquals("nutrition " + nutrition[i], expected[i], stats.getHungerStage());
        }
    }

    @Test
    public void theStoreRunsNegativeButNeverPastTheStarvationLine() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);
        stats.setSatiety(-50f);
        assertEquals(-50f, stats.getSatietyFloat(), 0f);
        stats.setSatiety(-100000f);
        assertEquals(Hunger.Stage.STARVED, stats.getHungerStage());
        assertTrue(stats.getSatietyFloat() > -1000f);
        stats.setSatiety(100000f);
        assertEquals(Hunger.CHOKE_AT, stats.getSatietyFloat(), 0f);
    }

    @Test
    public void weakCostsTwoStrengthAndOneToHit() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);
        stats.setSatiety(500f);
        int str = stats.getEffectiveStrength();
        int toHit = stats.getToHitBonus();
        stats.setSatiety(100f); // Hungry: a warning only
        assertEquals(str, stats.getEffectiveStrength());
        assertEquals(toHit, stats.getToHitBonus());
        stats.setSatiety(40f); // Weak
        assertEquals(str - 2, stats.getEffectiveStrength());
        assertEquals(toHit - 1, stats.getToHitBonus());
        stats.setSatiety(-40f); // Fainting keeps the penalty
        assertEquals(str - 2, stats.getEffectiveStrength());
    }

    @Test
    public void faintingIsAChanceThatDeepensBelowZero() {
        assertEquals(0f, Hunger.faintChance(0f), 0f);
        assertEquals(0f, Hunger.faintChance(40f), 0f);
        float shallow = Hunger.faintChance(-1f);
        float deep = Hunger.faintChance(-150f);
        assertTrue(shallow > 0f && shallow < 0.1f);
        assertTrue(deep > shallow && deep < 1f);
        for (int i = 0; i < 100; i++) {
            int t = Hunger.faintTurns(i / 100f);
            assertTrue("faints last 1-5 turns, got " + t, t >= 1 && t <= 5);
        }
        assertEquals(1, Hunger.faintTurns(0f));
        assertEquals(5, Hunger.faintTurns(0.999f));
    }

    @Test
    public void onlyAnOverfullMealChokesAndOnlyAVeryOverfullOneKills() {
        float chokeAt = Hunger.CHOKE_AT;
        assertEquals("not satiated: a ration always goes down",
                Hunger.EatOutcome.EAT, Hunger.eat(1000f, 800f, chokeAt, 0f));
        assertEquals("satiated but room to spare",
                Hunger.EatOutcome.EAT, Hunger.eat(1100f, 200f, chokeAt, 0f));
        assertEquals("satiated and past the limit: vomit, never death",
                Hunger.EatOutcome.VOMIT, Hunger.eat(1100f, 1000f, chokeAt, 0f));
        assertEquals("very overfull: a small death chance",
                Hunger.EatOutcome.CHOKE_DEATH, Hunger.eat(1600f, 800f, chokeAt, 0.01f));
        assertEquals(Hunger.EatOutcome.VOMIT, Hunger.eat(1600f, 800f, chokeAt, 0.5f));
    }

    @Test
    public void vomitingLosesTheMealAndStoredNutrition() {
        Player player = new Player(2, 2);
        player.getStats().setSatiety(1200f); // Satiated, but not very overfull: never fatal
        Item ration = Item.fromTemplate(Item.ItemType.FOOD, foodTemplate(Hunger.RATION));
        player.getInventory().pickupToBackpack(ration);
        player.useItem(ration, new GameEventManager(), null, null);
        assertEquals(1200f - Hunger.VOMIT_LOSS, player.getStats().getSatietyFloat(), 0.01f);
        assertTrue(player.getCurrentHP() > 0);
    }

    @Test
    public void aRationIsWorthEightHundred() {
        Item ration = Item.fromTemplate(Item.ItemType.FOOD, foodTemplate(0));
        assertEquals(800, Player.nutritionOf(ration));
    }

    @Test
    public void cookedPartsScaleByPartAndMonsterSize() {
        int kobold = Hunger.cookedPart(Item.ItemType.MEAT, Monster.MonsterType.KOBOLD);
        int orc = Hunger.cookedPart(Item.ItemType.MEAT, Monster.MonsterType.ORC);
        int dragon = Hunger.cookedPart(Item.ItemType.MEAT, Monster.MonsterType.DRAGON);
        assertTrue(kobold < orc && orc < dragon);
        assertTrue("meat feeds more than bone",
                orc > Hunger.cookedPart(Item.ItemType.GIB_BONE, Monster.MonsterType.ORC));
        assertEquals(0, Hunger.cookedPart(Item.ItemType.KNIFE, Monster.MonsterType.ORC));
    }

    @Test
    public void aCookedMealIsWorthItsIngredients() {
        Item meat = Item.fromTemplate(Item.ItemType.MEAT, foodTemplate(100));
        meat.setCorpseSource(Monster.MonsterType.ORC);
        Item organ = Item.fromTemplate(Item.ItemType.GIB_ORGAN, new ItemTemplate());
        organ.setCorpseSource(Monster.MonsterType.ORC);
        assertEquals(Hunger.cookedPart(Item.ItemType.MEAT, Monster.MonsterType.ORC)
                        + Hunger.cookedPart(Item.ItemType.GIB_ORGAN, Monster.MonsterType.ORC),
                Hunger.cookedMeal(java.util.Arrays.asList(meat, organ)));
    }

    @Test
    public void legacySatietyKeepsItsStage() {
        assertEquals(0f, Hunger.fromLegacySatiety(0f), 0f);
        assertEquals(Hunger.HUNGRY_BELOW, Hunger.fromLegacySatiety(25f), 0.01f);
        assertEquals(Hunger.START, Hunger.fromLegacySatiety(80f), 0.01f);
        assertEquals(Hunger.Stage.SATIATED, Hunger.stageOf(Hunger.fromLegacySatiety(120f), 10));
        assertEquals(Hunger.Stage.HUNGRY, Hunger.stageOf(Hunger.fromLegacySatiety(20f), 10));
        assertEquals(Hunger.Stage.NOT_HUNGRY, Hunger.stageOf(Hunger.fromLegacySatiety(60f), 10));
    }

    @Test
    public void testFeastingGrantsZeroDirectHealing() {
        Player player = new Player(2, 2);
        player.getStats().setSatiety(500f);
        int maxHp = player.getStats().getMaxHP();
        player.getStats().setCurrentHP(maxHp / 2);
        int hpBefore = player.getCurrentHP();

        ItemTemplate mealTemplate = new ItemTemplate();
        mealTemplate.friendlyName = "Hearty Stew";
        mealTemplate.isFood = true;
        mealTemplate.nutrition = 300;
        mealTemplate.hydrationValue = 20;
        Item meal = Item.fromTemplate(Item.ItemType.MEAL, mealTemplate);

        GameEventManager eventManager = new GameEventManager();
        player.feastOnMeal(meal, eventManager);

        assertEquals("Eating must never directly restore HP (NetHack model)", hpBefore, player.getCurrentHP());
        assertEquals("Feasting adds exactly the meal's nutrition", 800, player.getStats().getSatiety());
    }

    @Test
    public void testEatingTaintedFleshAppliesPoison() {
        Player player = new Player(2, 2);
        player.getStats().setSatiety(500f);

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
        player.getStats().setSatiety(500f);

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
            player.getStats().setSatiety(500f);
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

    private static ItemTemplate foodTemplate(int nutrition) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = "Food";
        t.isFood = true;
        t.nutrition = nutrition;
        return t;
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
