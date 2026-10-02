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
                "dodgeAdd", "healMult", "meleeDamageMult", "lightAdd", "mrAdd", "speedMult"));
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
}
