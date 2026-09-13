package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerEquipment;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.managers.CookingManager;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.StatusManager;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class Open5ePotionsAndRingsTest {

    private Player player;
    private StatusManager statusManager;
    private GameEventManager eventManager;

    @BeforeClass
    public static void initGdx() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> null
            );
        }
    }

    @Before
    public void setUp() {
        eventManager = new GameEventManager();
        player = new Player(5, 5);
        player.getStatusManager().initialize(eventManager, player);
        statusManager = player.getStatusManager();
    }

    private Item createTestRing(ItemType type, RingEffectType effect, int charges) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = "Test Ring";
        t.isRing = true;
        t.isUsable = true;
        t.maxCharges = charges;
        Item ring = Item.fromTemplate(type, t);
        ring.setRingEffect(effect);
        ring.setMaxCharges(charges);
        ring.setCurrentCharges(charges);
        return ring;
    }

    @Test
    public void testHealingPotionsRestoreAndToxicity() {
        PlayerStats stats = player.getStats();
        stats.modifyBaseHP(50);
        stats.setCurrentHP(10);
        int initialTox = stats.getToxicity();

        PotionEffectType.HEALING.applyEffect(player, statusManager);
        assertTrue("Player should be healed above 10 HP", stats.getCurrentHP() > 10);
        assertEquals("Standard potion should add 5 toxicity", initialTox + 5, stats.getToxicity());

        stats.setCurrentHP(10);
        PotionEffectType.GREATER_HEALING.applyEffect(player, statusManager);
        assertTrue("Greater healing should heal substantially", stats.getCurrentHP() >= 30);
    }

    @Test
    public void testSpeedPotionHasteAndComedown() {
        PlayerStats stats = player.getStats();
        int initialTox = stats.getToxicity();

        PotionEffectType.SPEED.applyEffect(player, statusManager);
        assertTrue("Player should be HASTED", statusManager.hasEffect(StatusEffectType.HASTED));
        assertEquals("Speed potion should add 8 toxicity", initialTox + 8, stats.getToxicity());

        // Fast forward turns until HASTE expires (duration is 20 turns)
        for (int i = 0; i < 20; i++) {
            statusManager.updateTurn();
        }

        // When HASTE expires, the 2-turn SLOWED comedown must be applied
        assertFalse("Haste should have expired", statusManager.hasEffect(StatusEffectType.HASTED));
        assertTrue("Player should be SLOWED due to Haste comedown", statusManager.hasEffect(StatusEffectType.SLOWED));
    }

    @Test
    public void testHeroismPotionAndTemporaryHP() {
        PlayerStats stats = player.getStats();
        int maxHP = stats.getMaxHP();
        stats.setCurrentHP(maxHP);

        PotionEffectType.HEROISM.applyEffect(player, statusManager);
        assertTrue("Heroism status effect applied", statusManager.hasEffect(StatusEffectType.HEROISM));
        assertEquals("Should gain 10 temporary HP", 10, stats.getTemporaryHP());

        // Drain 6 damage through stats.setCurrentHP / setWarStrength
        stats.setCurrentHP(stats.getCurrentHP() - 6);
        assertEquals("Temporary HP should absorb first 6 damage", 4, stats.getTemporaryHP());
        assertEquals("Actual HP should remain untouched at maxHP", maxHP, stats.getCurrentHP());

        // Drain 10 more damage: 4 from temporary HP, 6 from actual HP
        stats.setCurrentHP(stats.getCurrentHP() - 10);
        assertEquals("Temporary HP should be exhausted", 0, stats.getTemporaryHP());
        assertEquals("Actual HP should now absorb the remainder", maxHP - 6, stats.getCurrentHP());
    }

    @Test
    public void testGiantStrengthPotionOverridesStrength() {
        PotionEffectType.GIANT_STRENGTH.applyEffect(player, statusManager);
        assertTrue("Giant Strength buff active", statusManager.hasEffect(StatusEffectType.GIANT_STRENGTH));
        assertEquals("Effective strength should be overridden to 21", 21, player.getEffectiveStrength());
    }

    @Test
    public void testDualRingEquipSlotsAndStacking() {
        PlayerEquipment eq = player.getEquipment();

        Item ring1 = createTestRing(ItemType.RING_WARMTH, RingEffectType.WARMTH, 0);
        Item ring2 = createTestRing(ItemType.RING_RESISTANCE_FIRE, RingEffectType.RESISTANCE_FIRE, 0);

        eq.setWornRing(ring1);
        eq.setWornRing2(ring2);

        assertSame("Left ring should be ring1", ring1, eq.getWornRing());
        assertSame("Right ring should be ring2", ring2, eq.getWornRing2());

        assertTrue("Should have WARMTH effect", eq.hasRingEffect(RingEffectType.WARMTH));
        assertTrue("Should have RESISTANCE_FIRE effect", eq.hasRingEffect(RingEffectType.RESISTANCE_FIRE));
        assertEquals("Should have 1 WARMTH ring equipped", 1, eq.countRingEffect(RingEffectType.WARMTH));

        // Take off left ring
        eq.setWornRing(null);
        assertNull(eq.getWornRing());
        assertSame("Right ring should still be equipped", ring2, eq.getWornRing2());
        assertFalse("Should no longer have WARMTH effect", eq.hasRingEffect(RingEffectType.WARMTH));
        assertTrue("Should still have RESISTANCE_FIRE effect", eq.hasRingEffect(RingEffectType.RESISTANCE_FIRE));
    }

    @Test
    public void testRingOfEvasionCheatDeath() {
        PlayerEquipment eq = player.getEquipment();
        Item ring = createTestRing(ItemType.RING_EVASION_CHARGED, RingEffectType.EVASION_CHARGED, 3);
        eq.setWornRing(ring);

        PlayerStats stats = player.getStats();
        stats.setCurrentHP(5);

        // Deliver fatal damage (20 damage > 5 HP)
        int damageTaken = player.takeDamage(20, DamageType.PHYSICAL);

        assertEquals("Fatal damage should be negated (0 damage taken)", 0, damageTaken);
        assertEquals("Ring charge should be decremented to 2", 2, eq.getRingCharges(RingEffectType.EVASION_CHARGED));
        assertEquals("Player should remain alive with 5 HP", 5, stats.getCurrentHP());
    }

    @Test
    public void testRingOfFreeActionImmunity() {
        PlayerEquipment eq = player.getEquipment();
        Item ring = createTestRing(ItemType.RING_FREE_ACTION, RingEffectType.FREE_ACTION, 0);
        eq.setWornRing(ring);

        assertTrue("Player should have intrinsic immunity to SLOWED", player.hasIntrinsic(StatusEffectType.SLOWED));
        assertTrue("Player should have intrinsic immunity to PARALYZED", player.hasIntrinsic(StatusEffectType.PARALYZED));

        // Attempt to apply SLOWED effect
        statusManager.addEffect(StatusEffectType.SLOWED, 10, 1, false);
        assertFalse("SLOWED effect should be rejected by Ring of Free Action", statusManager.hasEffect(StatusEffectType.SLOWED));
    }

    @Test
    public void testRingOfResistanceElementalHalving() {
        PlayerEquipment eq = player.getEquipment();
        Item ring = createTestRing(ItemType.RING_RESISTANCE_FIRE, RingEffectType.RESISTANCE_FIRE, 0);

        PlayerStats stats = player.getStats();
        stats.modifyBaseHP(50);
        stats.setCurrentHP(stats.getMaxHP());

        eq.setWornRing(null);
        int unresistedDamage = player.takeDamage(20, DamageType.FIRE);

        eq.setWornRing(ring);
        int resistedDamage = player.takeDamage(20, DamageType.FIRE);

        assertEquals("Resisted fire damage should be approximately half of unresisted", unresistedDamage / 2, resistedDamage);
    }

    @Test
    public void testRingOfSpellStoringImbue() {
        Item ring = createTestRing(ItemType.RING_SPELL_STORING, RingEffectType.SPELL_STORING, 0);
        player.learnSpellId("FIREBALL");

        boolean imbued = player.imbueRingOfSpellStoring(ring, "FIREBALL");
        assertTrue("Imbuing known spell into Ring of Spell Storing should succeed", imbued);
        assertEquals("FIREBALL", ring.getStoredSpellId());
        assertEquals("Should grant 1 charge for casting", 1, ring.getCurrentCharges());

        // Attempting to imbue unknown spell should fail
        boolean unknownImbued = player.imbueRingOfSpellStoring(ring, "METEOR_SWARM");
        assertFalse("Imbuing unknown spell should fail", unknownImbued);
    }

    @Test
    public void testCampCookingAlchemyRecipesExist() {
        CookingManager cm = new CookingManager();
        assertNotNull("CookingManager recipes list should not be null", cm.getAllRecipes());
        assertFalse("CookingManager should have recipes", cm.getAllRecipes().isEmpty());

        boolean foundAlchemyRecipe = false;
        for (CookingManager.CookingRecipe recipe : cm.getAllRecipes()) {
            if (recipe.resultItemType != null && recipe.resultItemType.name().contains("POTION")) {
                foundAlchemyRecipe = true;
                break;
            }
        }
        assertTrue("At least one camp cooking recipe should produce a potion", foundAlchemyRecipe);
    }
}
