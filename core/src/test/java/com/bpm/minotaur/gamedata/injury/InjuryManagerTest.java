package com.bpm.minotaur.gamedata.injury;

import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit test suite for the anatomical trauma, wound triage, and disease progression mechanics.
 */
public class InjuryManagerTest {

    private InjuryManager injuryManager;
    private Player player;

    @Before
    public void setUp() {
        injuryManager = new InjuryManager();
        player = new Player(1, 1);
    }

    private Item createItem(ItemType type, String name) {
        ItemTemplate template = new ItemTemplate();
        template.friendlyName = name;
        template.isUsable = true;
        return Item.fromTemplate(type, template);
    }

    @Test
    public void testLimbTraumaAndPhysicalDebuffs() {
        // 1. Inflict Leg Fracture
        InjuryRecord legInjury = injuryManager.inflictInjury(BodyPart.LEGS, InjuryType.BONE_FRACTURE, 2);
        assertNotNull(legInjury);
        assertEquals(BodyPart.LEGS, legInjury.getBodyPart());
        assertEquals(InjuryType.BONE_FRACTURE, legInjury.getInjuryType());
        assertFalse(legInjury.isTreated());

        // Speed penalty scales with severity: a moderate fracture is 0.75x
        assertEquals(0.75f, injuryManager.getEffectiveSpeedModifier(), 0.001f);

        // 2. Inflict Arm Fracture -- to-hit penalty is -1 per severity rank
        InjuryRecord armInjury = injuryManager.inflictInjury(BodyPart.ARMS, InjuryType.BONE_FRACTURE, 2);
        assertNotNull(armInjury);
        assertEquals(-2, injuryManager.getEffectiveAttackModifier());
        assertFalse(injuryManager.canWieldTwoHanded());

        // 3. Inflict Head Concussion
        InjuryRecord headInjury = injuryManager.inflictInjury(BodyPart.HEAD, InjuryType.BONE_FRACTURE, 2);
        assertTrue(injuryManager.isHeadConcussed());

        // 4. Inflict Torso Laceration
        InjuryRecord torsoInjury = injuryManager.inflictInjury(BodyPart.TORSO, InjuryType.LACERATION_BLEEDING, 2);
        assertTrue(injuryManager.hasUntreatedInjuries());
        assertFalse(torsoInjury.isTreated());
    }

    @Test
    public void testSterileSpiderSilkTreatment() {
        injuryManager.inflictInjury(BodyPart.TORSO, InjuryType.LACERATION_BLEEDING, 2);
        Item spiderSilk = createItem(ItemType.SPIDER_SILK, "Spider Silk Suture");
        player.getInventory().addItem(spiderSilk);

        InjuryManager.TreatmentResult result = injuryManager.applyTreatment(BodyPart.TORSO, spiderSilk, false, player, null);
        assertTrue(result.success);
        assertFalse(result.infectionTriggered);
        assertTrue(result.hpRestored > 0);

        InjuryRecord rec = injuryManager.getInjury(BodyPart.TORSO);
        assertTrue(rec.isTreated());
        assertFalse(rec.isInfected());
        assertFalse(player.getInventory().getAllItems().contains(spiderSilk));
    }

    @Test
    public void testBoneSplintForFracturedLegAndArm() {
        injuryManager.inflictInjury(BodyPart.LEGS, InjuryType.BONE_FRACTURE, 2);
        injuryManager.inflictInjury(BodyPart.ARMS, InjuryType.BONE_FRACTURE, 2);

        assertEquals(0.75f, injuryManager.getEffectiveSpeedModifier(), 0.001f);
        assertFalse(injuryManager.canWieldTwoHanded());
        assertEquals(-2, injuryManager.getEffectiveAttackModifier());

        Item bone = createItem(ItemType.BONE, "Animal Bone Splint");
        player.getInventory().addItem(bone);

        // Splint arm
        InjuryManager.TreatmentResult resultArm = injuryManager.applyTreatment(BodyPart.ARMS, bone, false, player, null);
        assertTrue(resultArm.success);
        assertTrue(injuryManager.canWieldTwoHanded());
        assertEquals(-1, injuryManager.getEffectiveAttackModifier());

        // Splint leg
        Item rope = createItem(ItemType.ROTTEN_ROPE, "Frayed Cordage");
        player.getInventory().addItem(rope);
        InjuryManager.TreatmentResult resultLeg = injuryManager.applyTreatment(BodyPart.LEGS, rope, false, player, null);
        assertTrue(resultLeg.success);
        assertEquals(0.90f, injuryManager.getEffectiveSpeedModifier(), 0.001f);
    }

    @Test
    public void testPotionOfHealingCuresTraumaAndInfection() {
        injuryManager.inflictInjury(BodyPart.TORSO, InjuryType.LACERATION_BLEEDING, 3);
        injuryManager.getInjury(BodyPart.TORSO).setInfected(true);
        injuryManager.setIllnessStage(IllnessStage.STAGE_1_INFECTED_WOUND);

        Item potion = createItem(ItemType.POTION_OF_HEALING, "Potion of Healing");
        player.getInventory().addItem(potion);

        InjuryManager.TreatmentResult result = injuryManager.applyTreatment(BodyPart.TORSO, potion, false, player, null);
        assertTrue(result.success);
        assertTrue(injuryManager.getInjury(BodyPart.TORSO).isTreated());
        assertFalse(injuryManager.getInjury(BodyPart.TORSO).isInfected());
        assertEquals(IllnessStage.HEALTHY, injuryManager.getIllnessStage());
    }

    @Test
    public void testAntidoteAndLichenCuresSystemicFever() {
        injuryManager.setIllnessStage(IllnessStage.STAGE_2_ACUTE_FEVER);
        assertTrue(injuryManager.getIllnessStage().isIll());

        Item lichen = createItem(ItemType.GLOWING_LICHEN, "Glowing Cave Lichen");
        player.getInventory().addItem(lichen);

        InjuryManager.TreatmentResult result = injuryManager.applyTreatment(BodyPart.HEAD, lichen, false, player, null);
        assertTrue(result.success);
        assertEquals(IllnessStage.HEALTHY, injuryManager.getIllnessStage());
    }

    @Test
    public void testSlimeResidueSoothesBurns() {
        injuryManager.inflictInjury(BodyPart.ARMS, InjuryType.SEVERE_BURN, 2);
        Item slime = createItem(ItemType.SLIME_RESIDUE, "Cooling Slime Gel");
        player.getInventory().addItem(slime);

        InjuryManager.TreatmentResult result = injuryManager.applyTreatment(BodyPart.ARMS, slime, false, player, null);
        assertTrue(result.success);
        assertTrue(injuryManager.getInjury(BodyPart.ARMS).isTreated());
    }

    @Test
    public void testCrudeBareHandsPressureEmergencyFallback() {
        injuryManager.inflictInjury(BodyPart.TORSO, InjuryType.LACERATION_BLEEDING, 1);

        // Try bare hands pressure multiple times until successful (stochastic 60% chance)
        boolean everSucceeded = false;
        for (int i = 0; i < 20; i++) {
            InjuryManager.TreatmentResult result = injuryManager.applyTreatment(BodyPart.TORSO, null, true, player, null);
            if (result.success) {
                everSucceeded = true;
                assertTrue(injuryManager.getInjury(BodyPart.TORSO).isTreated());
                break;
            }
        }
        assertTrue("Bare hands pressure should eventually succeed over multiple attempts", everSucceeded);

        // Bare hands cannot fix fractures
        injuryManager.inflictInjury(BodyPart.LEGS, InjuryType.BONE_FRACTURE, 2);
        InjuryManager.TreatmentResult fracResult = injuryManager.applyTreatment(BodyPart.LEGS, null, true, player, null);
        assertFalse(fracResult.success);
    }

    @Test
    public void testSanctuaryRecoveryClearsTreatedTrauma() {
        injuryManager.inflictInjury(BodyPart.LEGS, InjuryType.BONE_FRACTURE, 2);
        injuryManager.getInjury(BodyPart.LEGS).setTreated(true);

        injuryManager.inflictInjury(BodyPart.TORSO, InjuryType.LACERATION_BLEEDING, 3);
        // Torso is NOT treated

        injuryManager.setIllnessStage(IllnessStage.STAGE_2_ACUTE_FEVER);

        injuryManager.restAtSanctuary(player, null);

        // Treated leg fracture should be healed
        assertNull(injuryManager.getInjury(BodyPart.LEGS));
        // Untreated torso laceration remains, but rest steps it down a rank and
        // always stops the bleeding -- neglect costs time, not the run.
        InjuryRecord torso = injuryManager.getInjury(BodyPart.TORSO);
        assertNotNull(torso);
        assertEquals(2, torso.getSeverity());
        assertFalse(torso.isBleeding());
        // Fever should be broken
        assertEquals(IllnessStage.HEALTHY, injuryManager.getIllnessStage());
    }

    /**
     * A minor untreated wound closes over entirely after a single night's rest.
     */
    @Test
    public void testSanctuaryRestClosesMinorUntreatedWound() {
        injuryManager.inflictInjury(BodyPart.TORSO, InjuryType.LACERATION_BLEEDING, 1);
        injuryManager.restAtSanctuary(player, null);
        assertNull(injuryManager.getInjury(BodyPart.TORSO));
    }

    /**
     * An untreated bleed costs a bounded pool of HP and then clots on its own.
     * Before this bound existed, a single laceration drained 1 HP every step
     * forever and reliably killed a 12-18 HP starting character outright.
     */
    @Test
    public void testUntreatedBleedClotsAfterFinitePool() {
        InjuryRecord rec = injuryManager.inflictInjury(BodyPart.TORSO, InjuryType.LACERATION_BLEEDING, 3);
        assertTrue(rec.isBleeding());
        int pool = rec.getBleedTicksRemaining();
        assertTrue("Bleed pool must be bounded", pool > 0 && pool <= 12);

        player.getStats().setMaxHP(200);
        player.getStats().setCurrentHP(200);
        for (int step = 0; step < 500; step++) {
            injuryManager.updateStep(player, null, null);
        }

        assertFalse("Wound must clot once its bleed pool is spent", rec.isBleeding());
        assertEquals(pool, injuryManager.getBleedDamageThisRun());
        assertTrue("Bleeding alone must not be able to drain a full health pool",
                player.getCurrentHP() > 200 - 20);
    }

    /**
     * Trauma must not leak across expeditions: the Player instance survives death,
     * so respawn calls cureAll() and every wound, fever, and counter must reset.
     */
    @Test
    public void testCureAllWipesTraumaBetweenRuns() {
        injuryManager.inflictInjury(BodyPart.TORSO, InjuryType.LACERATION_BLEEDING, 3);
        injuryManager.inflictInjury(BodyPart.LEGS, InjuryType.BONE_FRACTURE, 2);
        injuryManager.setIllnessStage(IllnessStage.STAGE_3_SEPTIC_DELIRIUM);
        injuryManager.updateStep(player, null, null);

        injuryManager.cureAll();

        assertFalse(injuryManager.hasAnyInjuries());
        assertFalse(injuryManager.hasUntreatedInjuries());
        assertEquals(IllnessStage.HEALTHY, injuryManager.getIllnessStage());
        assertEquals(0, injuryManager.getBleedDamageThisRun());
        assertEquals(1.0f, injuryManager.getEffectiveSpeedModifier(), 0.001f);
        assertEquals(0, injuryManager.getEffectiveAttackModifier());
    }

    /**
     * Chip damage must not maim. A 3-point hit on a 14 HP character clears neither
     * the fractional nor the absolute trauma threshold.
     */
    @Test
    public void testChipDamageIsNotTraumatic() {
        assertFalse(InjuryManager.isTraumaticHit(3, 14));
        assertFalse(InjuryManager.isTraumaticHit(4, 14));
        assertTrue(InjuryManager.isTraumaticHit(6, 14));
        // Absolute floor protects high-HP characters from death by a thousand cuts
        assertFalse(InjuryManager.isTraumaticHit(4, 8));
    }

    /**
     * Illness is not a one-way ratchet: once nothing is festering, the body
     * fights the fever back down instead of marching to sepsis regardless.
     */
    @Test
    public void testIllnessRecoversWhenNothingIsFestering() {
        injuryManager.setIllnessStage(IllnessStage.STAGE_2_ACUTE_FEVER);

        // No injuries at all -- nothing can be feeding the fever.
        for (int step = 0; step < 400; step++) {
            injuryManager.updateStep(player, null, null);
        }

        assertEquals(IllnessStage.HEALTHY, injuryManager.getIllnessStage());
    }

    @Test
    public void testItemViabilityAndPreview() {
        Item silk = createItem(ItemType.SPIDER_SILK, "Spider Silk");
        Item cloth = createItem(ItemType.DIRTY_CLOTH, "Dirty Cloth");
        Item bone = createItem(ItemType.BONE, "Animal Bone");
        Item sword = createItem(ItemType.SWORD_BROAD, "Iron Broadsword");

        assertTrue(injuryManager.isItemViableFirstAid(silk));
        assertTrue(injuryManager.isItemViableFirstAid(cloth));
        assertTrue(injuryManager.isItemViableFirstAid(bone));
        assertFalse(injuryManager.isItemViableFirstAid(sword));

        String silkPreview = injuryManager.getItemSuitabilityPreview(BodyPart.TORSO, silk);
        assertNotNull(silkPreview);
        assertTrue(silkPreview.contains("Spider Silk"));

        String clothPreview = injuryManager.getItemSuitabilityPreview(BodyPart.TORSO, cloth);
        assertNotNull(clothPreview);
        assertTrue(clothPreview.contains("35% INFECTION RISK"));
    }
}
