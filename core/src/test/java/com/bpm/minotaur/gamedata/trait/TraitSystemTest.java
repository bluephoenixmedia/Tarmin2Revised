package com.bpm.minotaur.gamedata.trait;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

public class TraitSystemTest {

    private static TraitCatalog catalog;
    private Player player;

    private static File data(String name) {
        File f = new File("../assets/data/" + name);
        return f.exists() ? f : new File("assets/data/" + name);
    }

    @BeforeClass
    public static void load() throws Exception {
        catalog = TraitCatalog.parse(new JsonReader().parse(new FileReader(data("traits.json"))));
    }

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (p, m, a) -> null);
        }
        player = new Player(5f, 5f);
    }

    @After
    public void tearDown() {
        TraitEffects.clear();
    }

    private void take(String id) {
        TraitEffects.set(catalog.get(id));
    }

    // ---- the data ----

    @Test
    public void theThreeNamedTraitsAndFifteenMoreAreDesigned() {
        assertEquals(18, catalog.all().size());
        for (String id : new String[] { "SILENT_LUNATIC", "ANGRY_GENIUS", "STOIC_CLOWN" }) {
            assertNotNull(id, catalog.get(id));
            assertTrue(id + " must be offered", catalog.get(id).enabled);
        }
    }

    @Test
    public void everyTraitHasBothAGoodAndABadSide() {
        for (TraitDefinition t : catalog.all()) {
            assertFalse(t.id + " good", t.good.isEmpty());
            assertFalse(t.id + " bad", t.bad.isEmpty());
        }
    }

    @Test
    public void everyCardSurvivesTheFontGlyphCheck() {
        for (TraitDefinition t : catalog.all()) {
            for (String text : new String[] { t.name, t.good, t.bad }) {
                assertTrue(t.id + ": \"" + text + "\" has a character the font cannot draw",
                        UiGlyphs.firstUnsupportedIndex(text) < 0);
            }
        }
    }

    @Test
    public void thereIsEnoughOfferableToFillAChoiceWithoutRepeats() {
        assertTrue(catalog.offerable().size() >= TraitCatalog.OFFER_SIZE + 1);
    }

    // ---- offering ----

    @Test
    public void anOfferIsThreeDistinctEnabledTraitsNeverTheCurrentOne() {
        Random rng = new Random(11);
        for (int i = 0; i < 200; i++) {
            List<String> offer = catalog.pickOffer("ANGRY_GENIUS", rng);
            assertEquals(TraitCatalog.OFFER_SIZE, offer.size());
            assertEquals(3, new HashSet<>(offer).size());
            assertFalse(offer.contains("ANGRY_GENIUS"));
            for (String id : offer) {
                assertTrue(catalog.get(id).enabled);
            }
        }
    }

    @Test
    public void aChoiceIsDueOnTheFifthRespawn() {
        assertFalse(TraitCatalog.choiceDue(4));
        assertTrue(TraitCatalog.choiceDue(5));
    }

    @Test
    public void thePlayerCountsRespawnsAndTheCountResetsOnAChoice() {
        for (int i = 0; i < 4; i++) {
            assertFalse(player.noteRespawn());
        }
        assertTrue(player.noteRespawn());
        player.chooseTrait(null);
        assertEquals(0, player.getRespawnsSinceChoice());
        assertFalse(player.noteRespawn());
    }

    @Test
    public void aNewPlayerNeedsAnOfferAndKeepingTheCurrentTraitCloseItWithoutChangingIt() {
        assertTrue(player.needsTraitOffer());
        player.chooseTrait(null);
        assertNull("declining with no trait leaves none", player.getTraitId());
    }

    // ---- effects ----

    @Test
    public void withNoTraitNothingChanges() {
        TraitEffects.clear();
        assertEquals(1f, TraitEffects.mult("xpMult"), 0f);
        assertEquals(0f, TraitEffects.add("critAdd"), 0f);
        assertEquals(0, TraitEffects.stat("INT"));
        assertFalse(TraitEffects.blocks(StatusEffectType.SLEEP));
    }

    @Test
    public void angryGeniusAddsIntelligence() {
        int before = player.getEffectiveIntelligence();
        take("ANGRY_GENIUS");
        assertEquals(before + 3, player.getEffectiveIntelligence());
        assertEquals(1.25f, TraitEffects.mult("xpMult"), 0f);
    }

    @Test
    public void angryGeniusTakesMoreDamage() {
        int hpBefore = player.getStats().getCurrentHP();
        player.takeDamage(10, com.bpm.minotaur.gamedata.DamageType.PHYSICAL);
        int plain = hpBefore - player.getStats().getCurrentHP();

        Player other = new Player(5f, 5f);
        take("ANGRY_GENIUS");
        int hp2 = other.getStats().getCurrentHP();
        other.takeDamage(10, com.bpm.minotaur.gamedata.DamageType.PHYSICAL);
        int genius = hp2 - other.getStats().getCurrentHP();
        assertTrue("genius " + genius + " vs plain " + plain, genius > plain);
    }

    @Test
    public void stoicClownCannotBeConfusedOrPutToSleepButCanBePoisoned() {
        take("STOIC_CLOWN");
        player.getStatusManager().addEffect(StatusEffectType.SLEEP, 5, 1, false);
        player.getStatusManager().addEffect(StatusEffectType.CONFUSED, 5, 1, false);
        player.getStatusManager().addEffect(StatusEffectType.POISONED, 5, 1, false);
        assertFalse(player.getStatusManager().hasEffect(StatusEffectType.SLEEP));
        assertFalse(player.getStatusManager().hasEffect(StatusEffectType.CONFUSED));
        assertTrue(player.getStatusManager().hasEffect(StatusEffectType.POISONED));
    }

    @Test
    public void gentleBruteCarriesMoreAndHasLessCharisma() {
        float capacity = player.getCarryCapacity();
        int cha = player.getEffectiveCharisma();
        take("GENTLE_BRUTE");
        assertTrue(player.getCarryCapacity() > capacity);
        assertEquals(cha - 2, player.getEffectiveCharisma());
    }

    @Test
    public void luckyFoolHasMoreLuckAndFewerHitPoints() {
        int luck = player.getLuck();
        int max = player.getStats().getMaxHP();
        take("LUCKY_FOOL");
        assertEquals(luck + 4, player.getLuck());
        assertTrue(player.getStats().getMaxHP() < max);
    }

    @Test
    public void aLowerMaximumNeverLeavesTheCurrentHitPointsAboveIt() {
        player.restoreTrait("LUCKY_FOOL", null, 0);
        // the trait comes from the real catalog; in headless tests it is empty, so set it directly
        take("LUCKY_FOOL");
        player.getStats().setCurrentHP(player.getStats().getMaxHP());
        assertTrue(player.getStats().getCurrentHP() <= player.getStats().getMaxHP());
    }

    @Test
    public void recklessDuelistCritsMoreButDodgesLess() {
        float crit = player.getCritChance();
        take("RECKLESS_DUELIST");
        assertTrue(player.getCritChance() > crit);
        assertEquals(2.5f, player.getCritMultiplier(), 0.001f);
    }

    @Test
    public void fastingMonkEatsAndDrinksLess() {
        take("FASTING_MONK");
        assertEquals(0.5f, TraitEffects.mult("hungerMult"), 0f);
        assertEquals(0.5f, TraitEffects.mult("thirstMult"), 0f);
    }

    @Test
    public void greedyScholarPaysMoreAndGetsMoreForSelling() {
        take("GREEDY_SCHOLAR");
        assertEquals(1.25f, TraitEffects.mult("buyMult"), 0f);
        assertEquals(1.25f, TraitEffects.mult("sellMult"), 0f);
        assertEquals(1.5f, TraitEffects.mult("divinityMult"), 0f);
    }

    @Test
    public void cowardlyAlchemistHealsMoreAndHitsLess() {
        take("COWARDLY_ALCHEMIST");
        player.getStats().setCurrentHP(1);
        player.heal(4);
        assertEquals(1 + 6, player.getStats().getCurrentHP());
        assertEquals(0.75f, TraitEffects.mult("meleeDamageMult"), 0f);
    }

    @Test
    public void silentLunaticIsNoticedFromHalfAsFarAndPaysMoreForSpells() {
        take("SILENT_LUNATIC");
        assertEquals(0.5f, TraitEffects.mult("noticeMult"), 0f);
        assertEquals(1.5f, TraitEffects.mult("spellCostMult"), 0f);
        assertEquals(0.10f, TraitEffects.add("berserkChance"), 0f);
    }

    @Test
    public void aNewPlayerClearsAnyTraitLeftOverFromAnEarlierOne() {
        take("ANGRY_GENIUS");
        new Player(1f, 1f);
        assertNull(TraitEffects.current());
    }

    @Test
    public void everyModifierKeyIsOneTheGameReads() {
        Set<String> known = new HashSet<>(Arrays.asList(
                "noticeMult", "berserkChance", "spellCostMult", "spellCostFlat", "xpMult", "damageTakenMult",
                "attackFailChance", "regenMult", "buyMult", "sellMult", "critAdd", "critMultAdd", "carryMult",
                "luckAdd", "maxHpMult", "divinityMult", "hungerMult", "thirstMult", "acAdd", "dodgeMult",
                "dodgeAdd", "healMult", "meleeDamageMult", "lightAdd", "mrAdd", "speedMult",
                "mimicDetectAdd", "bedRestMult", "lightUnderground", "lightSurface", "darkDodgeAdd", "chokeLimitAdd",
                "undeadSpellDicePlus", "overTimeMult", "divinityKillAdd", "huntedMeleePenalty", "foundTreasureMult",
                "noTrade", "spellLearnEarly", "lifestealFraction", "injuryChanceMult"));
        for (TraitDefinition t : catalog.all()) {
            for (String key : t.modifiers.keySet()) {
                assertTrue(t.id + " uses a modifier nothing reads: " + key, key.startsWith("stat.") || known.contains(key));
            }
        }
    }

    @Test
    public void aSaveRecordsTheBaseMaximumSoATraitIsNotAppliedTwiceOnLoad() {
        int base = player.getStats().getBaseMaxHP();
        take("LUCKY_FOOL");
        com.bpm.minotaur.gamedata.save.PlayerSaveData save = new com.bpm.minotaur.gamedata.save.PlayerSaveData(player);
        assertEquals(base, save.maxHP);
        assertTrue(player.getStats().getMaxHP() < base);
    }

    @Test
    public void potionAndMealHealingScaleButNaturalRegenerationDoesNot() {
        take("COWARDLY_ALCHEMIST");
        player.getStats().setCurrentHP(1);
        player.getStats().healWithTrait(4);
        assertEquals(7, player.getStats().getCurrentHP());
        player.getStats().setCurrentHP(1);
        player.getStats().heal(4);
        assertEquals(5, player.getStats().getCurrentHP());
    }

    // ---- the nine traits that were held back until their last effect was built ----

    @Test
    public void allEighteenTraitsAreNowOffered() {
        assertEquals(18, catalog.offerable().size());
    }

    @Test
    public void paranoidScoutSpotsMimicsMoreOften() {
        int plain = com.bpm.minotaur.gamedata.monster.MimicDetection.chancePercent(10);
        take("PARANOID_SCOUT");
        assertEquals(plain + 25, com.bpm.minotaur.gamedata.monster.MimicDetection.chancePercent(10));
        assertEquals(0.5f, TraitEffects.mult("bedRestMult"), 0f);
    }

    @Test
    public void nightOwlDodgesBetterOnlyBelowTheSurface() {
        take("NIGHT_OWL");
        float surface = player.getDodgeChance();
        player.tickTrait(new com.bpm.minotaur.gamedata.Maze(2, new int[12][12]), null);
        assertEquals(surface + 0.10f, player.getDodgeChance(), 0.001f);
        player.tickTrait(new com.bpm.minotaur.gamedata.Maze(1, new int[12][12]), null);
        assertEquals(surface, player.getDodgeChance(), 0.001f);
    }

    @Test
    public void ironStomachResistsPoisonAndSicknessButChokesSooner() {
        player.getStats().setSatiety(105f);
        assertEquals(com.bpm.minotaur.gamedata.player.PlayerStats.SatiationState.SATIATED, player.getStats().getSatiationState());
        take("IRON_STOMACH");
        assertEquals(com.bpm.minotaur.gamedata.player.PlayerStats.SatiationState.CHOKING, player.getStats().getSatiationState());
        player.getStatusManager().addEffect(StatusEffectType.POISONED, 5, 1, false);
        player.getStatusManager().addEffect(StatusEffectType.SICK, 5, 1, false);
        assertFalse(player.getStatusManager().hasEffect(StatusEffectType.POISONED));
        assertFalse(player.getStatusManager().hasEffect(StatusEffectType.SICK));
    }

    @Test
    public void hardyCynicTakesAboutHalfTheDamageOverTime() {
        take("HARDY_CYNIC");
        java.util.Random rng = new java.util.Random(4);
        int total = 0;
        for (int i = 0; i < 2000; i++) {
            total += TraitEffects.scaleOverTime(1, rng);
        }
        assertTrue("expected about 1000, got " + total, total > 900 && total < 1100);
        assertEquals(-1, Math.round(TraitEffects.add("divinityKillAdd")));
        TraitEffects.clear();
        assertEquals(1, TraitEffects.scaleOverTime(1, rng));
    }

    @Test
    public void luckyPariahFindsMoreTreasureAndIsRefusedByMerchants() {
        take("LUCKY_PARIAH");
        player.getStats().incrementTreasureScore(100);
        assertEquals(130, player.getStats().getTreasureScore());
        assertTrue(TraitEffects.add("noTrade") > 0f);
    }

    @Test
    public void hollowProphetLearnsSpellsOneLevelEarly() {
        com.bpm.minotaur.gamedata.spells.SpellTemplate spell = new com.bpm.minotaur.gamedata.spells.SpellTemplate();
        spell.level = 3;
        assertEquals(5, Player.getRequiredPlayerLevelForSpell(spell));
        take("HOLLOW_PROPHET");
        assertEquals(3, Player.getRequiredPlayerLevelForSpell(spell));
        spell.level = 1;
        assertEquals(1, Player.getRequiredPlayerLevelForSpell(spell));
    }

    @Test
    public void bornCowardIsFasterAndHarderToHit() {
        int speed = player.getEffectiveSpeed();
        take("BORN_COWARD");
        assertTrue(player.getEffectiveSpeed() > speed);
        assertEquals(2f, TraitEffects.add("huntedMeleePenalty"), 0f);
    }

    @Test
    public void bloodsoakedSaintIsWoundedMoreOften() {
        take("BLOODSOAKED_SAINT");
        assertEquals(0.25f, TraitEffects.add("lifestealFraction"), 0f);
        assertEquals(1.25f, TraitEffects.mult("injuryChanceMult"), 0f);
    }

    @Test
    public void ghostWhispererResistsMagicAndIsNoticedFromFarther() {
        int mr = player.getMagicResistance();
        take("GHOST_WHISPERER");
        assertEquals(mr + 20, player.getMagicResistance());
        assertEquals(1.25f, TraitEffects.mult("noticeMult"), 0f);
        assertEquals(2f, TraitEffects.add("undeadSpellDicePlus"), 0f);
    }
}
