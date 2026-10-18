package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.injury.IllnessStage;
import com.bpm.minotaur.gamedata.injury.InjuryManager;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.PotionEffectType;
import com.bpm.minotaur.gamedata.item.Waterskin;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.save.ItemSaveData;
import com.bpm.minotaur.gamedata.save.PlayerSaveData;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The thirst rework (#193, decided in #180): NetHack-style scale mirroring hunger, the
 * refillable waterskin, drinking any liquid with its risk, and waterborne illness that blocks
 * regeneration until Vitality cures it.
 */
public class ThirstTest {

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

    // --- The scale ---------------------------------------------------------------------

    @Test
    public void aFreshStartDrinksRoughlyEverySixHundredTurns() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);
        assertEquals(900f, stats.getHydrationFloat(), 0f);
        assertEquals(Thirst.Stage.HYDRATED, stats.getThirstStage());

        int turns = 0;
        while (!stats.getThirstStage().isThirsty()) {
            stats.modifyHydration(-Thirst.burn(1f, 1f, 1f));
            turns++;
        }
        assertEquals("1.5 a turn from 900 reaches Thirsty (below 150) on turn 501", 501, turns);
        while (!stats.getThirstStage().isDehydrated()) {
            stats.modifyHydration(-Thirst.burn(1f, 1f, 1f));
            turns++;
        }
        assertEquals("and Dehydrated (below 50) on turn 567", 567, turns);
    }

    @Test
    public void stagesLandOnTheirThresholds() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM); // CON 10: dead below -200
        float[] hydration = {900, 150, 149, 50, 49, 0, -1, -200, -201};
        Thirst.Stage[] expected = {
                Thirst.Stage.HYDRATED, Thirst.Stage.HYDRATED, Thirst.Stage.THIRSTY, Thirst.Stage.THIRSTY,
                Thirst.Stage.DEHYDRATED, Thirst.Stage.DEHYDRATED, Thirst.Stage.COLLAPSE, Thirst.Stage.COLLAPSE,
                Thirst.Stage.DEAD
        };
        for (int i = 0; i < hydration.length; i++) {
            stats.setHydration(hydration[i]);
            assertEquals("hydration " + hydration[i], expected[i], stats.getThirstStage());
        }
    }

    @Test
    public void theStoreRunsNegativeButNeverPastTheDeathLineOrTheCap() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);
        stats.setHydration(-100000f);
        assertEquals(Thirst.Stage.DEAD, stats.getThirstStage());
        assertTrue(stats.getHydrationFloat() > -1000f);
        stats.setHydration(100000f);
        assertEquals(Thirst.MAX_STORE, stats.getHydrationFloat(), 0f);
    }

    @Test
    public void dehydrationCostsTwoDexAndSlowsWithoutTouchingTheStoredStat() {
        Player player = new Player(2, 2);
        int dex = player.getStats().getDexterity();
        int effective = player.getEffectiveDexterity();
        int speed = player.getEffectiveSpeed();

        player.getStats().setHydration(10f);
        assertEquals(Thirst.Stage.DEHYDRATED, player.getStats().getThirstStage());
        assertEquals("the stored stat is untouched, so a save can't lose it", dex, player.getStats().getDexterity());
        assertEquals(effective - 2, player.getEffectiveDexterity());
        assertTrue("dehydrated actions are slower", player.getEffectiveSpeed() < speed);

        player.getStats().setHydration(100f); // Thirsty: a warning only
        assertEquals(effective, player.getEffectiveDexterity());
        assertEquals(speed, player.getEffectiveSpeed());
    }

    @Test
    public void collapseOnlyBelowZeroAndLastsOneToFiveTurns() {
        assertEquals(0f, Thirst.collapseChance(0f), 0f);
        assertTrue(Thirst.collapseChance(-1f) > 0f);
        assertTrue(Thirst.collapseChance(-150f) > Thirst.collapseChance(-10f));
        assertEquals(1, Thirst.collapseTurns(0f));
        assertEquals(5, Thirst.collapseTurns(0.999f));
    }

    @Test
    public void aLegacySaveKeepsItsThirstStage() {
        assertEquals(0f, Thirst.fromLegacyHydration(0f), 0f);
        assertEquals(Thirst.THIRSTY_BELOW, Thirst.fromLegacyHydration(20f), 0.01f);
        assertEquals(Thirst.START, Thirst.fromLegacyHydration(80f), 0.01f);
        assertTrue(Thirst.fromLegacyHydration(100f) > Thirst.START);

        Player saved = new Player(0, 0);
        PlayerSaveData legacy = new PlayerSaveData(saved);
        legacy.thirstScale = 0; // written before #193
        legacy.hydration = 80f; // the old fresh start
        Player loaded = new Player(0, 0);
        legacy.applyToPlayer(loaded, null, null);
        assertEquals(Thirst.START, loaded.getStats().getHydrationFloat(), 0.01f);

        Player current = new Player(0, 0);
        current.getStats().setHydration(40f);
        Player reloaded = new Player(0, 0);
        new PlayerSaveData(current).applyToPlayer(reloaded, null, null);
        assertEquals("a current save is not migrated again", 40f, reloaded.getStats().getHydrationFloat(), 0.01f);
    }

    // --- Liquids -----------------------------------------------------------------------

    @Test
    public void onlyRealLiquidsCanBeDrunk() {
        assertTrue(Thirst.isDrinkable(LiquidType.WATER));
        assertTrue(Thirst.isDrinkable(LiquidType.BLOOD));
        assertTrue(Thirst.isDrinkable(LiquidType.BLACK_MUCK));
        assertTrue(Thirst.isDrinkable(LiquidType.FREEZING_SLUSH));
        assertFalse(Thirst.isDrinkable(LiquidType.MOLTEN_FIRE));
        assertFalse(Thirst.isDrinkable(LiquidType.QUICKSAND));
        assertFalse(Thirst.isDrinkable(LiquidType.PACKED_ICE));
        assertFalse(Thirst.isDrinkable(LiquidType.NONE));
    }

    @Test
    public void liquidsQuenchAndRiskPerTheTable() {
        assertEquals(Thirst.DRINK_FULL, Thirst.hydrationGain(LiquidType.WATER));
        assertEquals(Thirst.DRINK_FULL, Thirst.hydrationGain(LiquidType.FREEZING_SLUSH));
        assertEquals(Thirst.DRINK_FULL / 2, Thirst.hydrationGain(LiquidType.BLOOD));
        assertEquals(Thirst.DRINK_FULL / 2, Thirst.hydrationGain(LiquidType.BLACK_MUCK));

        assertEquals(0f, Thirst.illnessRisk(LiquidType.WATER, false), 0f);
        assertEquals(0.10f, Thirst.illnessRisk(LiquidType.WATER, true), 0f);
        assertEquals(0.70f, Thirst.illnessRisk(LiquidType.BLOOD, false), 0f);
        assertEquals(1f, Thirst.illnessRisk(LiquidType.BLACK_MUCK, true), 0f);
        assertEquals(0f, Thirst.illnessRisk(LiquidType.FREEZING_SLUSH, true), 0f);
        assertTrue(Thirst.isChilling(LiquidType.FREEZING_SLUSH));

        assertEquals(IllnessStage.STAGE_2_ACUTE_FEVER, Thirst.illnessFrom(LiquidType.BLOOD, false, 0.69f));
        assertEquals(IllnessStage.HEALTHY, Thirst.illnessFrom(LiquidType.BLOOD, false, 0.71f));
        assertEquals(IllnessStage.STAGE_1_INFECTED_WOUND, Thirst.illnessFrom(LiquidType.BLACK_MUCK, false, 0.999f));
        assertEquals(IllnessStage.STAGE_1_INFECTED_WOUND, Thirst.illnessFrom(LiquidType.WATER, true, 0.05f));
        assertEquals(IllnessStage.HEALTHY, Thirst.illnessFrom(LiquidType.WATER, true, 0.5f));
        assertEquals(IllnessStage.HEALTHY, Thirst.illnessFrom(LiquidType.WATER, false, 0f));
    }

    @Test
    public void drinkingHydratesButNeverHeals() {
        Player player = new Player(2, 2);
        player.getStats().setCurrentHP(player.getStats().getMaxHP() / 2);
        int hp = player.getCurrentHP();
        player.getStats().setHydration(100f);
        assertTrue(player.drinkLiquid(LiquidType.WATER, false, 0f, new GameEventManager()));
        assertEquals(300f, player.getStats().getHydrationFloat(), 0.01f);
        assertEquals(hp, player.getCurrentHP());
        assertFalse(player.getInjuryManager().getIllnessStage().isIll());

        assertFalse(player.drinkLiquid(LiquidType.MOLTEN_FIRE, false, 0f, new GameEventManager()));
    }

    @Test
    public void bloodBringsFeverThatBlocksRegenUntilVitalityCuresIt() {
        Player player = new Player(2, 2);
        InjuryManager im = player.getInjuryManager();
        player.drinkLiquid(LiquidType.BLOOD, false, 0.1f, new GameEventManager());
        assertEquals(IllnessStage.STAGE_2_ACUTE_FEVER, im.getIllnessStage());
        assertTrue(im.isWaterborneIllness());
        assertTrue(im.blocksNaturalRegen());

        PotionEffectType.VITALITY.applyEffect(player, player.getStatusManager());
        assertFalse(im.getIllnessStage().isIll());
        assertFalse(im.blocksNaturalRegen());
    }

    @Test
    public void muckSicknessWorsensUntreatedAndIsCalledSick() {
        Player player = new Player(2, 2);
        InjuryManager im = player.getInjuryManager();
        player.drinkLiquid(LiquidType.BLACK_MUCK, false, 0.999f, new GameEventManager());
        assertEquals(IllnessStage.STAGE_1_INFECTED_WOUND, im.getIllnessStage());
        assertEquals("Sick", im.getIllnessDisplayName());

        for (int i = 0; i < 125; i++) im.updateStep(player, null, null);
        assertEquals("no wound to clean, so it marches on", IllnessStage.STAGE_2_ACUTE_FEVER, im.getIllnessStage());
    }

    @Test
    public void aWoundInfectionStillRecoversOnItsOwn() {
        Player player = new Player(2, 2);
        InjuryManager im = player.getInjuryManager();
        im.restoreClocks(IllnessStage.STAGE_1_INFECTED_WOUND, 0, 0, 0);
        assertFalse(im.blocksNaturalRegen());
        for (int i = 0; i < 125; i++) im.updateStep(player, null, null);
        assertEquals(IllnessStage.HEALTHY, im.getIllnessStage());
    }

    // --- The waterskin -----------------------------------------------------------------

    private static Item newSkin() {
        return Item.fromTemplate(Item.ItemType.WATERSKIN, new ItemTemplate());
    }

    @Test
    public void aNewWaterskinHoldsFiveDrinksOfWater() {
        Item skin = newSkin();
        assertEquals(Thirst.WATERSKIN_CAPACITY, skin.getCurrentCharges());
        assertEquals(LiquidType.WATER, skin.getLiquidContent());
        assertEquals("Waterskin (Water)", skin.getDisplayName());
    }

    @Test
    public void theWaterskinRemembersWhatItWasFilledWith() {
        Item skin = newSkin();
        assertTrue(Waterskin.fill(skin, LiquidType.BLOOD, true));
        assertEquals("Waterskin (Blood)", skin.getDisplayName());
        assertEquals(5, skin.getCurrentCharges());
        assertFalse("molten fire can't be carried", Waterskin.fill(skin, LiquidType.MOLTEN_FIRE, true));

        for (int i = 0; i < 5; i++) {
            Waterskin.Draught d = Waterskin.drinkOne(skin);
            assertEquals(LiquidType.BLOOD, d.liquid);
            assertTrue(d.strata);
        }
        assertNull(Waterskin.drinkOne(skin));
        assertEquals("Waterskin (Empty)", skin.getDisplayName());
    }

    @Test
    public void drinkingFromTheSkinUsesWhatItHolds() {
        Player player = new Player(2, 2);
        Item skin = newSkin();
        player.getStats().setHydration(0f);
        assertTrue(player.drinkFromWaterskin(skin, new GameEventManager()));
        assertEquals(Thirst.DRINK_FULL, player.getStats().getHydrationFloat(), 0.01f);
        assertEquals(4, skin.getCurrentCharges());
    }

    @Test
    public void rainTopsUpAnEmptyOrWaterSkinButNotOneHoldingBlood() {
        Item skin = newSkin();
        assertFalse("full", Waterskin.catchesRain(skin));
        Waterskin.drinkOne(skin);
        assertTrue(Waterskin.catchRain(skin));
        assertEquals(5, skin.getCurrentCharges());

        Waterskin.fill(skin, LiquidType.BLOOD, false);
        Waterskin.drinkOne(skin);
        assertFalse(Waterskin.catchesRain(skin));

        for (int i = 0; i < 4; i++) Waterskin.drinkOne(skin);
        assertTrue(Waterskin.catchRain(skin));
        assertEquals("Waterskin (Water)", skin.getDisplayName());
        assertEquals(1, skin.getCurrentCharges());
    }

    @Test
    public void theWaterskinsContentsSurviveASave() {
        Item skin = newSkin();
        Waterskin.fill(skin, LiquidType.WATER, true);
        Waterskin.drinkOne(skin);
        ItemSaveData data = new ItemSaveData(skin);
        assertEquals(4, data.currentCharges);
        assertEquals(LiquidType.WATER, data.liquidContent);
        assertTrue(data.strataSource);

        ItemSaveData other = new ItemSaveData(Item.fromTemplate(Item.ItemType.FOOD, new ItemTemplate()));
        assertNull("only a waterskin saves contents", other.liquidContent);
    }

    @Test
    public void acuteFeverBringsRapidThirst() {
        assertEquals(Thirst.FEVER_THIRST_MULT,
                Thirst.illnessMult(com.bpm.minotaur.gamedata.injury.IllnessStage.STAGE_2_ACUTE_FEVER), 0.0001f);
        assertEquals(1f, Thirst.illnessMult(com.bpm.minotaur.gamedata.injury.IllnessStage.STAGE_1_INFECTED_WOUND), 0.0001f);
        assertEquals(1f, Thirst.illnessMult(null), 0.0001f);
        assertTrue(Thirst.burn(1f, 1f, Thirst.illnessMult(com.bpm.minotaur.gamedata.injury.IllnessStage.STAGE_2_ACUTE_FEVER))
                > Thirst.burn(1f, 1f, 1f));
    }
}
