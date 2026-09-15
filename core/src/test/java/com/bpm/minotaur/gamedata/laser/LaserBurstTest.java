package com.bpm.minotaur.gamedata.laser;

import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.injury.InjuryManager;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

/**
 * Behaviour of the merchant's Void chain laser.
 *
 * <p>The agreed design: a 5-beam burst walks progressively off target as the
 * weapon climbs. Beams that connect are absorbed; beams that miss keep flying
 * until they hit a wall, and those strays are what endanger anything standing
 * in the lane. Clip chance rises with beam travel while damage falls, so
 * distant firefights sting often and harmlessly while close ones rarely but
 * genuinely maim.
 */
public class LaserBurstTest {

    /**
     * One RNG stream per test, the way the merchant keeps one long-lived RNG in
     * play. Re-seeding java.util.Random for every burst correlates the first
     * draw across adjacent seeds, which pinned the recoil to the same side in
     * every burst and hid half of the climb's behaviour.
     */
    private final Random rng = new Random(20260915L);

    @Test
    public void testDistanceSplitsClipChanceFromDamage() {
        // Point blank: seldom clips a bystander, but lands at full strength.
        assertEquals(0.20f, LaserBurst.clipChanceAt(2f), 0.001f);
        assertEquals(1.00f, LaserBurst.attenuationAt(2f), 0.001f);

        // Maximum range: frequently clips, but the beam has bled off most of its energy.
        assertEquals(0.60f, LaserBurst.clipChanceAt(6f), 0.001f);
        assertEquals(0.40f, LaserBurst.attenuationAt(6f), 0.001f);

        // The two curves must move in opposite directions across the whole span.
        float previousClip = LaserBurst.clipChanceAt(0f);
        float previousAttenuation = LaserBurst.attenuationAt(0f);
        for (float travel = 0.5f; travel <= 8f; travel += 0.5f) {
            float clip = LaserBurst.clipChanceAt(travel);
            float attenuation = LaserBurst.attenuationAt(travel);
            assertTrue("Clip chance must never fall as travel grows", clip >= previousClip);
            assertTrue("Attenuation must never rise as travel grows", attenuation <= previousAttenuation);
            previousClip = clip;
            previousAttenuation = attenuation;
        }

        // Both curves clamp beyond the weapon's effective range rather than running away.
        assertEquals(0.60f, LaserBurst.clipChanceAt(40f), 0.001f);
        assertEquals(0.40f, LaserBurst.attenuationAt(40f), 0.001f);
    }

    /**
     * Only what is actually standing in the beam's path is at risk. The corridor
     * is a body's width, so "I could see daylight between me and that beam" and
     * "it missed me" agree with each other.
     */
    @Test
    public void testOnlyBystandersInTheLaneAreAtRisk() {
        Vector2 origin = new Vector2(1.5f, 1.5f);
        Vector2 end = new Vector2(7.5f, 1.5f);

        // Squarely in the lane.
        assertTrue(LaserBurst.isInBeamCorridor(new Vector2(4.5f, 1.5f), origin, end));
        // Just inside a body's width of it.
        assertTrue(LaserBurst.isInBeamCorridor(new Vector2(4.5f, 1.9f), origin, end));
        // Clear of it.
        assertFalse(LaserBurst.isInBeamCorridor(new Vector2(4.5f, 2.3f), origin, end));

        // Behind the merchant is not downrange.
        assertFalse(LaserBurst.isInBeamCorridor(new Vector2(0.2f, 1.5f), origin, end));
        // Past where the beam stopped is not downrange either.
        assertFalse(LaserBurst.isInBeamCorridor(new Vector2(9.0f, 1.5f), origin, end));
    }

    /**
     * How far a stray had flown by the time it reached a bystander is what feeds
     * the falloff curves, so it must be measured from the merchant to the
     * bystander -- not the beam's full length.
     */
    @Test
    public void testTravelIsMeasuredToTheBystanderNotTheWall() {
        Vector2 origin = new Vector2(1.5f, 1.5f);
        Vector2 end = new Vector2(7.5f, 1.5f);
        Vector2 bystander = new Vector2(3.5f, 1.5f);

        assertEquals(2.0f, LaserBurst.travelDistanceTo(bystander, origin, end), 0.001f);
    }

    /**
     * The first shots are his; the last shots are nobody's. Beam 1 is dead on,
     * each successive beam walks further off the aim line, beams that connect
     * are absorbed in the target, and the ones that miss fly on downrange.
     */
    @Test
    public void testBurstClimbsOffTargetAndMissesFlyDownrange() {
        Maze maze = openRoom(12, 12);
        Vector2 merchant = new Vector2(1.5f, 5.5f);
        Monster goblin = new Monster(Monster.MonsterType.GOBLIN, 50, 10, 4, 5);
        maze.addMonster(goblin);

        for (int burst = 0; burst < 25; burst++) {
            LaserBurst.BurstResult result = LaserBurst.fire(maze, merchant, goblin, null, 1f, rng);

            assertEquals(LaserBurst.BEAMS_PER_BURST, result.getBeams().size());

            LaserBurst.Beam first = result.getBeams().get(0);
            assertTrue("The opening beam is dead accurate", first.isConnected());
            assertEquals(goblin.getPosition().x, first.getEnd().x, 0.001f);
            assertEquals(goblin.getPosition().y, first.getEnd().y, 0.001f);

            float previousDeviation = -1f;
            boolean anyStray = false;
            for (LaserBurst.Beam beam : result.getBeams()) {
                float deviation = deviationFromAimDegrees(merchant, goblin.getPosition(), beam);
                assertTrue("The climb must never walk back toward the aim line",
                        deviation >= previousDeviation - 0.001f);
                previousDeviation = deviation;
                if (!beam.isConnected()) {
                    anyStray = true;
                    assertTrue("A miss keeps flying past the target",
                            merchant.dst(beam.getEnd()) > merchant.dst(goblin.getPosition()));
                }
            }
            assertTrue("By the end of the burst the climb has walked off a 3-tile target", anyStray);
            assertTrue(result.getDamageToTarget() > 0);
        }
    }

    /**
     * The shots that miss are the ones that find you. When the climb pulls the
     * burst toward a bystander, strays crossing their lane can clip them, and a
     * clipped stray is absorbed right there. Anyone outside every lane is safe.
     */
    @Test
    public void testStraysClipThePlayerOnlyWhenTheyCrossTheirLane() {
        Maze maze = openRoom(12, 12);
        Vector2 merchant = new Vector2(1.5f, 5.5f);
        Monster goblin = new Monster(Monster.MonsterType.GOBLIN, 50, 10, 4, 5);
        maze.addMonster(goblin);

        // Downrange, off-axis at roughly the angle beams 4-5 climb to.
        Player inTheLane = new Player(0, 0);
        inTheLane.getPosition().set(5.5f, 4.5f);
        int clips = 0;
        for (int burst = 0; burst < 300; burst++) {
            LaserBurst.BurstResult result = LaserBurst.fire(maze, merchant, goblin, inTheLane, 1f, rng);
            if (!result.isPlayerClipped()) {
                continue;
            }
            clips++;
            assertTrue(result.getDamageToPlayer() > 0);
            LaserBurst.Beam clipping = beamThatStruck(result, LaserBurst.Struck.PLAYER);
            assertNotNull("A clip must be attributable to a specific stray", clipping);
            assertTrue("The stray is absorbed where it clips you",
                    clipping.getEnd().epsilonEquals(inTheLane.getPosition(), 0.001f));
        }
        assertTrue("Standing in the climb's lane must eventually get you clipped", clips > 0);

        // Behind him, and far off to the side: never in any beam's lane.
        Player behind = new Player(0, 0);
        behind.getPosition().set(0.5f, 5.5f);
        Player farOffAxis = new Player(0, 0);
        farOffAxis.getPosition().set(1.5f, 10.5f);
        for (int burst = 0; burst < 300; burst++) {
            assertFalse(LaserBurst.fire(maze, merchant, goblin, behind, 1f, rng).isPlayerClipped());
            assertFalse(LaserBurst.fire(maze, merchant, goblin, farOffAxis, 1f, rng).isPlayerClipped());
        }
    }

    /**
     * Strays do not care who is standing downrange: a monster in a stray's lane
     * takes the hit too, which is how a merchant firefight in a crowded corridor
     * turns into chaos worth watching from a safe angle.
     */
    @Test
    public void testStraysAlsoStrikeOtherMonstersInTheirLane() {
        Maze maze = openRoom(12, 12);
        Vector2 merchant = new Vector2(1.5f, 5.5f);
        Monster goblin = new Monster(Monster.MonsterType.GOBLIN, 50, 10, 4, 5);
        Monster bystander = new Monster(Monster.MonsterType.GIANT_ANT, 50, 10, 5, 6);
        maze.addMonster(goblin);
        maze.addMonster(bystander);

        int collateralHits = 0;
        for (int burst = 0; burst < 300; burst++) {
            LaserBurst.BurstResult result = LaserBurst.fire(maze, merchant, goblin, null, 1f, rng);
            Integer damage = result.getCollateralDamage().get(bystander);
            if (damage != null) {
                collateralHits++;
                assertTrue(damage > 0);
                assertNotNull(beamThatStruck(result, LaserBurst.Struck.BYSTANDER_MONSTER));
            }
            assertFalse("The aimed-at target is never counted as collateral",
                    result.getCollateralDamage().containsKey(goblin));
        }
        assertTrue("A monster standing in the climb's lane must eventually be struck", collateralHits > 0);
    }

    /**
     * Ordinary armour was never designed for coherent light: beams go straight
     * through it. Only the strongest set turns them -- a plate torso of the AC-5
     * family worn with a matching helm and legs, all at colour tier 4 or above
     * (Pink / Purple). There is no armour-set system, so this is what "set" means.
     */
    @Test
    public void testOnlyTheStrongestPlateSetTurnsTheBeam() {
        Player player = new Player(0, 0);
        assertFalse("Bare skin turns nothing", LaserBurst.isBeamHardened(player));

        Item chest = armour(Item.ItemType.FULL_PLATE, 5, ItemColor.PURPLE);
        Item helm = armour(Item.ItemType.BASINET, 2, ItemColor.PINK);
        Item legs = armour(Item.ItemType.BRONZE_LEGGINGS, 2, ItemColor.PURPLE);
        player.getEquipment().setWornChest(chest);
        player.getEquipment().setWornHelmet(helm);
        player.getEquipment().setWornLegs(legs);
        assertTrue("Purple/Pink plate with helm and legs turns the beam", LaserBurst.isBeamHardened(player));

        chest.setItemColor(ItemColor.WHITE);
        assertFalse("Same plate at an ordinary colour tier is just steel to a laser",
                LaserBurst.isBeamHardened(player));
        chest.setItemColor(ItemColor.PURPLE);

        player.getEquipment().setWornChest(armour(Item.ItemType.FULL_PLATE, 4, ItemColor.PURPLE));
        assertFalse("Top-tier colour on lighter armour is not enough", LaserBurst.isBeamHardened(player));
        player.getEquipment().setWornChest(chest);

        player.getEquipment().setWornLegs(null);
        assertFalse("A plate torso alone is not the set", LaserBurst.isBeamHardened(player));
    }

    /**
     * The Q23 contract, stated in the game's own numbers: hardened armour halves
     * each beam, which drops every base-output beam under the trauma floor. The
     * merchant can chip someone in the best armour in the game, but never scar
     * them. The same beam on an ordinary adventurer can.
     */
    @Test
    public void testHardenedArmourHalvesBeamsAndMakesBurnsImpossible() {
        Player hardened = new Player(0, 0);
        hardened.getEquipment().setWornChest(armour(Item.ItemType.FULL_PLATE, 5, ItemColor.PURPLE));
        hardened.getEquipment().setWornHelmet(armour(Item.ItemType.BASINET, 2, ItemColor.PURPLE));
        hardened.getEquipment().setWornLegs(armour(Item.ItemType.BRONZE_LEGGINGS, 2, ItemColor.PURPLE));
        Player ordinary = new Player(0, 0);

        assertEquals(4, LaserBurst.mitigateForArmour(hardened, 8));
        assertEquals(3, LaserBurst.mitigateForArmour(hardened, 6));
        assertEquals("Ordinary armour does nothing", 8, LaserBurst.mitigateForArmour(ordinary, 8));
        assertEquals("A clip always stings", 1, LaserBurst.mitigateForArmour(hardened, 1));

        int startingMaxHp = 14;
        for (int raw = 3; raw <= 8; raw++) { // the full 1d6+2 range, point blank, no depth scaling
            assertFalse("Hardened armour must make a base beam burn impossible (raw " + raw + ")",
                    InjuryManager.isTraumaticHit(LaserBurst.mitigateForArmour(hardened, raw), startingMaxHp));
        }
        assertTrue("The same point-blank beam can scar an ordinary adventurer",
                InjuryManager.isTraumaticHit(LaserBurst.mitigateForArmour(ordinary, 8), startingMaxHp));
    }

    /**
     * Q24: the deeper he has wandered, the hotter his cell runs. The trauma floor
     * is relative (35% of max HP), so without depth scaling a fixed 1d6+2 beam is
     * outgrown within a few levels and the close-range scarring quietly dies.
     */
    @Test
    public void testCellRunsHotterWithDepthSoCloseRangeScarringSurvives() {
        assertEquals(1.0f, LaserBurst.cellOutputAtDepth(1), 0.001f);
        float previous = LaserBurst.cellOutputAtDepth(1);
        for (int strata = 2; strata <= 20; strata++) {
            float output = LaserBurst.cellOutputAtDepth(strata);
            assertTrue("Cell output must never fall with depth", output >= previous);
            previous = output;
        }

        int maxBeam = 8; // 1d6+2 rolled high, point blank
        int veteranMaxHp = 60;
        assertFalse("At the surface a max beam cannot scar a 60-HP veteran",
                InjuryManager.isTraumaticHit(Math.round(maxBeam * LaserBurst.cellOutputAtDepth(1)), veteranMaxHp));
        assertTrue("Ten strata down, the same point-blank beam can",
                InjuryManager.isTraumaticHit(Math.round(maxBeam * LaserBurst.cellOutputAtDepth(10)), veteranMaxHp));
    }

    /**
     * Depth scaling exists so the merchant's threat output keeps pace with rising
     * max HP (Q24) -- it must never leak into friendly fire. Q15 and Q23 already
     * fixed two invariants at every depth: a far stray cannot burn (attenuation
     * keeps it under the trauma floor), and the hardened armour set halves any
     * beam under that same floor. A flat depth multiplier applied to strays would
     * break both at depth (a strong cell would let far shots and armoured hits
     * clear the floor) -- so depth scaling must apply only to damage against the
     * monster the merchant is actually aiming at, never to a stray.
     */
    @Test
    public void testDepthScalingNeverAppliesToFriendlyFire() {
        Maze maze = openRoom(12, 12);
        Vector2 merchant = new Vector2(1.5f, 5.5f);
        Monster goblin = new Monster(Monster.MonsterType.GOBLIN, 100000, 10, 4, 5);
        maze.addMonster(goblin);
        Player inTheLane = new Player(0, 0);
        inTheLane.getPosition().set(5.5f, 4.5f);

        float deepCellOutput = LaserBurst.cellOutputAtDepth(20); // the scaling cap
        assertTrue("This test is only meaningful once depth scaling is actually large",
                deepCellOutput > 2f);

        int maxUnscaledRoll = 8; // 1d6+2 rolled high; attenuation only ever reduces this, never scale
        boolean anyClip = false;
        for (int burst = 0; burst < 2000; burst++) {
            LaserBurst.BurstResult result = LaserBurst.fire(maze, merchant, goblin, inTheLane, deepCellOutput, rng);
            if (result.getDamageToTarget() > 0) {
                assertTrue("Target damage is allowed to scale with depth",
                        result.getDamageToTarget() > maxUnscaledRoll);
            }
            if (result.isPlayerClipped()) {
                anyClip = true;
                // More than one stray can clip the player in a single burst (adjacent beams'
                // angles are close enough near the merchant to both cross a tight corridor),
                // so the invariant is checked per individual clip, not against their sum --
                // exactly how ShopkeeperAiManager rolls injury, per clip rather than on the total.
                assertFalse("A clip list must not be reported empty when the flag is set",
                        result.getPlayerClipDamages().isEmpty());
                for (int clipDamage : result.getPlayerClipDamages()) {
                    assertTrue("Each individual stray's damage must never exceed what an unscaled beam could do",
                            clipDamage <= maxUnscaledRoll);
                    assertFalse("At maximum depth scaling, no single stray can clear the trauma floor",
                            InjuryManager.isTraumaticHit(clipDamage, 60));
                }
            }
        }
        assertTrue("The scenario must actually exercise a clip to be meaningful", anyClip);
    }

    // -------------------------------------------------------------------------

    private static Item armour(Item.ItemType type, int armorClassBonus, ItemColor color) {
        ItemTemplate template = new ItemTemplate();
        template.friendlyName = type.name();
        template.isArmor = true;
        template.armorClassBonus = armorClassBonus;
        Item item = Item.fromTemplate(type, template);
        item.setItemColor(color);
        return item;
    }

    private static LaserBurst.Beam beamThatStruck(LaserBurst.BurstResult result, LaserBurst.Struck struck) {
        for (LaserBurst.Beam beam : result.getBeams()) {
            if (beam.getStruck() == struck) {
                return beam;
            }
        }
        return null;
    }

    private static Maze openRoom(int width, int height) {
        return new Maze(1, new int[height][width]);
    }

    private static float deviationFromAimDegrees(Vector2 origin, Vector2 aim, LaserBurst.Beam beam) {
        Vector2 aimDir = new Vector2(aim).sub(origin).nor();
        Vector2 beamDir = new Vector2(beam.getEnd()).sub(origin).nor();
        return Math.abs(aimDir.angleDeg(beamDir) > 180f ? 360f - aimDir.angleDeg(beamDir) : aimDir.angleDeg(beamDir));
    }
}
