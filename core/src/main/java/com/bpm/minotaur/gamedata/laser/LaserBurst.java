package com.bpm.minotaur.gamedata.laser;

import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerEquipment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The merchant's Void chain laser — salvage from the Ancient Raycast Void of
 * Tarmin-Zul, and the only coherent-light weapon anyone in these depths has
 * ever seen.
 *
 * <p>This class is the pure simulation half of the weapon: burst geometry,
 * spread, falloff and damage resolution, with no rendering and no LibGDX
 * graphics dependency, so all of it is testable.
 *
 * <p><b>Distance cuts both ways.</b> Real beams diverge and attenuate as they
 * travel, so a stray that has flown a long way is wide and wobbly (easy to
 * blunder into) but has bled off most of its energy (barely stings). A stray
 * caught at point-blank range is the reverse: rarely in your way, but it
 * arrives at full strength.
 */
public final class LaserBurst {

    // =========================================================================
    // BALANCE TUNING — every number the weapon is felt through lives here.
    // =========================================================================

    /** Travel distance (tiles) at or below which a beam is still at full energy. */
    public static final float POINT_BLANK_TILES = 2f;
    /** The weapon's effective range; both curves clamp here. */
    public static final float MAX_RANGE_TILES = 6f;

    /** Chance a stray beam in your lane actually clips you, at point-blank travel. */
    public static final float CLIP_CHANCE_NEAR = 0.20f;
    /** ...and at maximum range, where divergence has made the beam wide and wandering. */
    public static final float CLIP_CHANCE_FAR = 0.60f;

    /** Damage multiplier at point-blank travel. */
    public static final float ATTENUATION_NEAR = 1.00f;
    /** ...and at maximum range, after the beam has bled off most of its energy. */
    public static final float ATTENUATION_FAR = 0.40f;

    /**
     * Half-width of the danger corridor around a beam, in tiles. Roughly a
     * body's radius, so what the beam VFX visibly crosses and what actually
     * gets hit agree with each other.
     */
    public static final float CORRIDOR_HALF_WIDTH = 0.5f;

    /** Beams per burst. */
    public static final int BEAMS_PER_BURST = 5;
    /** Each beam rolls 1d6+2 before falloff and depth scaling. */
    private static final int BEAM_DAMAGE_DIE = 6;
    private static final int BEAM_DAMAGE_BONUS = 2;

    /**
     * Progressive climb: beam N deviates at least N x this many degrees off the
     * aim line. At a typical 3-tile engagement this lands beams 1-3 and walks
     * beams 4-5 off the target; at long range fewer connect and more stray.
     */
    public static final float CLIMB_STEP_DEGREES = 4f;
    /** Extra per-beam wobble on top of the climb, strictly less than one step
     *  so the climb can never walk back toward the aim line. */
    public static final float CLIMB_JITTER_DEGREES = 1f;

    /** A stray flies until it hits a wall, capped here so an open field still terminates. */
    public static final float MAX_BEAM_LENGTH_TILES = 16f;
    /** March resolution for wall tracing; must stay below one tile so no cell edge is skipped. */
    private static final float TRACE_STEP_TILES = 0.05f;

    private LaserBurst() {
    }

    /** Minimum armour-class bonus for a torso piece to count as the AC-5 plate family. */
    public static final int HARDENED_TORSO_MIN_AC = 5;
    /** Damage multiplier on a beam that strikes someone in the hardened set. */
    public static final float HARDENED_ARMOUR_FACTOR = 0.5f;

    /**
     * Extra cell output per strata below the first. Deliberately tied to depth,
     * not to the Doom multiplier: Doom scales with deaths and caps at 1.5x, so a
     * careful player who never dies would face a surface-strength beam forever
     * and outgrow the trauma floor within a few levels.
     */
    public static final float CELL_OUTPUT_PER_STRATA = 0.25f;
    /** Ceiling so the deepest merchants stay dangerous rather than absurd. */
    public static final float CELL_OUTPUT_MAX = 4.0f;

    /**
     * Beam output multiplier for a merchant found at the given strata: the deeper
     * he has wandered, the hotter his cell runs.
     */
    public static float cellOutputAtDepth(int strata) {
        if (strata <= 1) {
            return 1.0f;
        }
        return Math.min(CELL_OUTPUT_MAX, 1.0f + CELL_OUTPUT_PER_STRATA * (strata - 1));
    }

    // =========================================================================
    // ARMOUR
    // =========================================================================

    /**
     * True when the player wears the one armour that turns coherent light: an
     * AC-5 plate-family torso piece plus a helm and legs, every piece at colour
     * tier 4 or above (Pink / Purple). The game has no armour-set system, so a
     * top-tier torso worn with a matching top-tier helm and legs is the set.
     * Any of the three torso slots (chest, breastplate, hauberk) may carry it.
     */
    public static boolean isBeamHardened(Player player) {
        if (player == null || player.getEquipment() == null) {
            return false;
        }
        PlayerEquipment eq = player.getEquipment();
        boolean plateTorso = isHardenedTorso(eq.getWornChest())
                || isHardenedTorso(eq.getWornBreastplate())
                || isHardenedTorso(eq.getWornHauberk());
        return plateTorso && isTopColourTier(eq.getWornHelmet()) && isTopColourTier(eq.getWornLegs());
    }

    /**
     * Beam damage after armour. Ordinary armour does nothing -- it was never
     * designed for this. The hardened set halves each beam, which keeps every
     * base-output beam under the trauma floor: chipped, never scarred. A clip
     * always stings for at least 1.
     */
    public static int mitigateForArmour(Player player, int rawDamage) {
        if (rawDamage <= 0) {
            return 0;
        }
        if (!isBeamHardened(player)) {
            return rawDamage;
        }
        return Math.max(1, Math.round(rawDamage * HARDENED_ARMOUR_FACTOR));
    }

    private static boolean isHardenedTorso(Item item) {
        return item != null && item.getArmorClassBonus() >= HARDENED_TORSO_MIN_AC && isTopColourTier(item);
    }

    private static boolean isTopColourTier(Item item) {
        if (item == null) {
            return false;
        }
        ItemColor color = item.getItemColor();
        return color == ItemColor.PINK || color == ItemColor.PURPLE;
    }

    // =========================================================================
    // BURST RESOLUTION
    // =========================================================================

    /** What a beam was absorbed by. */
    public enum Struck {
        /** Connected with the monster he was aiming at. */
        TARGET,
        /** A stray that clipped the player standing in its lane. */
        PLAYER,
        /** A stray that struck some other monster standing in its lane. */
        BYSTANDER_MONSTER,
        /** A stray that found nobody and burned into a wall. */
        WALL
    }

    /** One beam of a burst: where it went and what absorbed it. */
    public static final class Beam {
        private final int index;
        private final Vector2 origin;
        private final Vector2 end;
        private final Struck struck;

        Beam(int index, Vector2 origin, Vector2 end, Struck struck) {
            this.index = index;
            this.origin = origin;
            this.end = end;
            this.struck = struck;
        }

        public Struck getStruck() {
            return struck;
        }

        /** Position in the burst, 0 = the opening, dead-accurate shot. */
        public int getIndex() {
            return index;
        }

        public Vector2 getOrigin() {
            return origin;
        }

        /** The target's centre for a connected beam; the wall it struck for a stray. */
        public Vector2 getEnd() {
            return end;
        }

        /** True when the beam was absorbed by its target rather than flying on downrange. */
        public boolean isConnected() {
            return struck == Struck.TARGET;
        }
    }

    /** Everything a burst did, resolved but not yet applied to anyone. */
    public static final class BurstResult {
        private final List<Beam> beams;
        private final int damageToTarget;
        private final int damageToPlayer;
        private final boolean playerClipped;
        private final Map<Monster, Integer> collateralDamage;
        private final List<Integer> playerClipDamages;

        BurstResult(List<Beam> beams, int damageToTarget, int damageToPlayer, boolean playerClipped,
                    Map<Monster, Integer> collateralDamage, List<Integer> playerClipDamages) {
            this.beams = Collections.unmodifiableList(beams);
            this.damageToTarget = damageToTarget;
            this.damageToPlayer = damageToPlayer;
            this.playerClipped = playerClipped;
            this.collateralDamage = Collections.unmodifiableMap(collateralDamage);
            this.playerClipDamages = Collections.unmodifiableList(playerClipDamages);
        }

        public List<Beam> getBeams() {
            return beams;
        }

        /** Total damage the beams that connected deal to the monster he aimed at. */
        public int getDamageToTarget() {
            return damageToTarget;
        }

        /** Raw beam damage to the player from strays that clipped them, before armour. */
        public int getDamageToPlayer() {
            return damageToPlayer;
        }

        public boolean isPlayerClipped() {
            return playerClipped;
        }

        /**
         * Unmitigated damage from each individual stray that clipped the player
         * this burst, in the order they landed. More than one stray CAN clip the
         * same bystander in one burst -- their angles are close enough near the
         * merchant that two adjacent beams both cross a tight corridor. Trauma
         * and armour mitigation must be rolled against each entry separately,
         * never against their sum: Q15 and Q23 only verified the trauma floor
         * and the hardened-armour halving against a single beam's magnitude, and
         * summing first would let several individually-safe grazes launder into
         * one over-floor hit.
         */
        public List<Integer> getPlayerClipDamages() {
            return playerClipDamages;
        }

        /** Damage dealt by strays to monsters other than the target, keyed by identity. */
        public Map<Monster, Integer> getCollateralDamage() {
            return collateralDamage;
        }
    }

    /** Someone standing in a stray's lane, and how far the beam had flown to reach them. */
    private static final class Bystander {
        final Monster monster; // null means the player
        final Vector2 position;
        final float travel;

        Bystander(Monster monster, Vector2 position, float travel) {
            this.monster = monster;
            this.position = position;
            this.travel = travel;
        }
    }

    /**
     * Resolves one burst from the merchant at {@code origin} at {@code target}.
     * Pure: nothing is damaged here; the caller applies the result.
     *
     * @param damageScale depth-linked multiplier on beam output (the hotter cell
     *                    the deeper he's wandered). Applies only to damage against
     *                    {@code target}, never to friendly fire -- see the note at
     *                    the stray-resolution branch below for why.
     */
    public static BurstResult fire(Maze maze, Vector2 origin, Monster target, Player player,
                                   float damageScale, Random rng) {
        Vector2 targetPos = target.getPosition();
        Vector2 aim = new Vector2(targetPos).sub(origin).nor();
        float distanceToTarget = origin.dst(targetPos);

        // Recoil pulls one way for the whole burst, like a real weapon walking off its mark.
        float climbSign = rng.nextBoolean() ? 1f : -1f;

        List<Beam> beams = new ArrayList<>(BEAMS_PER_BURST);
        int damageToTarget = 0;
        int damageToPlayer = 0;
        boolean playerClipped = false;
        Map<Monster, Integer> collateral = new IdentityHashMap<>();
        List<Integer> playerClipDamages = new ArrayList<>();

        for (int i = 0; i < BEAMS_PER_BURST; i++) {
            float deviation = (i == 0) ? 0f : CLIMB_STEP_DEGREES * i + rng.nextFloat() * CLIMB_JITTER_DEGREES;
            Vector2 direction = new Vector2(aim).rotateDeg(climbSign * deviation);

            Vector2 wallHit = traceToWall(maze, origin, direction);
            boolean reachesTarget = origin.dst(wallHit) >= distanceToTarget;
            float missDistance = perpendicularDistanceToSegment(targetPos, origin, wallHit);
            boolean connected = reachesTarget && missDistance <= CORRIDOR_HALF_WIDTH;

            if (connected) {
                beams.add(new Beam(i, new Vector2(origin), new Vector2(targetPos), Struck.TARGET));
                damageToTarget += beamDamage(rng, distanceToTarget, damageScale);
                continue;
            }

            // Depth scaling is the merchant's threat output keeping pace with rising max HP
            // (a hotter cell, deeper down) -- it must never reach friendly fire. Q15 and Q23
            // each verified an invariant that holds at every depth: a far stray cannot burn,
            // and the hardened armour set halves any beam under the trauma floor. Scaling
            // stray damage by the same multiplier as target damage would break both once the
            // cell runs hot enough, so strays always resolve at the unscaled (1.0x) output.
            Bystander clipped = findClippedBystander(maze, origin, wallHit, target, player, rng);
            if (clipped == null) {
                beams.add(new Beam(i, new Vector2(origin), wallHit, Struck.WALL));
            } else if (clipped.monster == null) {
                beams.add(new Beam(i, new Vector2(origin), new Vector2(clipped.position), Struck.PLAYER));
                int strayDamage = beamDamage(rng, clipped.travel, 1.0f);
                damageToPlayer += strayDamage;
                playerClipDamages.add(strayDamage);
                playerClipped = true;
            } else {
                beams.add(new Beam(i, new Vector2(origin), new Vector2(clipped.position), Struck.BYSTANDER_MONSTER));
                collateral.merge(clipped.monster, beamDamage(rng, clipped.travel, 1.0f), Integer::sum);
            }
        }

        return new BurstResult(beams, damageToTarget, damageToPlayer, playerClipped, collateral, playerClipDamages);
    }

    /**
     * Walks everyone standing in a stray's lane, nearest first, and rolls the
     * distance-scaled clip chance for each. The first one clipped absorbs the
     * beam; if nobody is, the stray burns on into the wall.
     *
     * <p>Only strays are checked: a beam that connected with its target was
     * absorbed there, and fire discipline keeps him from opening a burst with
     * the player standing in the aim line.
     */
    private static Bystander findClippedBystander(Maze maze, Vector2 origin, Vector2 wallHit,
                                                  Monster target, Player player, Random rng) {
        List<Bystander> inLane = new ArrayList<>();
        if (player != null && isInBeamCorridor(player.getPosition(), origin, wallHit)) {
            inLane.add(new Bystander(null, player.getPosition(),
                    travelDistanceTo(player.getPosition(), origin, wallHit)));
        }
        for (Monster monster : maze.getMonsters().values()) {
            if (monster == target || monster.getCurrentHP() <= 0) {
                continue;
            }
            if (isInBeamCorridor(monster.getPosition(), origin, wallHit)) {
                inLane.add(new Bystander(monster, monster.getPosition(),
                        travelDistanceTo(monster.getPosition(), origin, wallHit)));
            }
        }
        inLane.sort(Comparator.comparingDouble(b -> b.travel));

        for (Bystander bystander : inLane) {
            if (rng.nextFloat() < clipChanceAt(bystander.travel)) {
                return bystander;
            }
        }
        return null;
    }

    /**
     * True when {@code point} stands in the aim line from {@code origin} toward
     * {@code target}, anywhere up to the wall the opening beam would strike:
     * between the two, or right behind the target.
     */
    public static boolean isInLineOfFire(Maze maze, Vector2 origin, Vector2 target, Vector2 point) {
        Vector2 direction = new Vector2(target).sub(origin);
        if (direction.len2() <= 0.000001f) {
            return false;
        }
        direction.nor();
        return isInBeamCorridor(point, origin, traceToWall(maze, origin, direction));
    }

    /** True when a beam from {@code from} toward {@code to} would reach it before striking any wall. */
    public static boolean hasLineOfSight(Maze maze, Vector2 from, Vector2 to) {
        float distance = from.dst(to);
        if (distance <= 0.0001f) {
            return true;
        }
        Vector2 direction = new Vector2(to).sub(from).nor();
        return from.dst(traceToWall(maze, from, direction)) >= distance - TRACE_STEP_TILES;
    }

    /** Rolls one beam's damage and applies falloff for the distance it flew and depth scaling. */
    private static int beamDamage(Random rng, float travelTiles, float damageScale) {
        int roll = 1 + rng.nextInt(BEAM_DAMAGE_DIE) + BEAM_DAMAGE_BONUS;
        return Math.max(1, Math.round(roll * attenuationAt(travelTiles) * damageScale));
    }

    /**
     * Marches along the beam until it enters a solid tile or crosses a thin
     * (edge) wall between two open tiles, returning where it stopped.
     */
    private static Vector2 traceToWall(Maze maze, Vector2 origin, Vector2 direction) {
        Vector2 position = new Vector2(origin);
        int cellX = (int) Math.floor(position.x);
        int cellY = (int) Math.floor(position.y);

        for (float travelled = 0f; travelled < MAX_BEAM_LENGTH_TILES; travelled += TRACE_STEP_TILES) {
            Vector2 next = new Vector2(origin).mulAdd(direction, travelled + TRACE_STEP_TILES);
            int nextX = (int) Math.floor(next.x);
            int nextY = (int) Math.floor(next.y);

            if (nextX != cellX) {
                Direction stepX = nextX > cellX ? Direction.EAST : Direction.WEST;
                if (maze.isWallBlocking(cellX, cellY, stepX) || maze.isWall(nextX, cellY)) {
                    return position;
                }
                cellX = nextX;
            }
            if (nextY != cellY) {
                Direction stepY = nextY > cellY ? Direction.NORTH : Direction.SOUTH;
                if (maze.isWallBlocking(cellX, cellY, stepY) || maze.isWall(cellX, nextY)) {
                    return position;
                }
                cellY = nextY;
            }
            position.set(next);
        }
        return position;
    }

    /**
     * True when a bystander is close enough to a beam's path to be in danger.
     * Only the span the beam actually travelled counts — standing behind the
     * merchant, or past the wall the beam stopped at, is not downrange.
     */
    public static boolean isInBeamCorridor(Vector2 point, Vector2 origin, Vector2 end) {
        return perpendicularDistanceToSegment(point, origin, end) <= CORRIDOR_HALF_WIDTH;
    }

    /**
     * How far the beam had flown by the time it reached this bystander. This,
     * not the beam's total length, is what feeds the falloff curves.
     */
    public static float travelDistanceTo(Vector2 point, Vector2 origin, Vector2 end) {
        return origin.dst(closestPointOnSegment(point, origin, end));
    }

    private static float perpendicularDistanceToSegment(Vector2 point, Vector2 origin, Vector2 end) {
        return point.dst(closestPointOnSegment(point, origin, end));
    }

    /**
     * Projects the point onto the beam segment, clamped to the segment's ends so
     * that anything off either end resolves to that end rather than to an
     * imaginary extension of the beam.
     */
    private static Vector2 closestPointOnSegment(Vector2 point, Vector2 origin, Vector2 end) {
        float dx = end.x - origin.x;
        float dy = end.y - origin.y;
        float lengthSq = dx * dx + dy * dy;
        if (lengthSq <= 0.000001f) {
            return new Vector2(origin);
        }
        float t = ((point.x - origin.x) * dx + (point.y - origin.y) * dy) / lengthSq;
        t = Math.max(0f, Math.min(1f, t));
        return new Vector2(origin.x + dx * t, origin.y + dy * t);
    }

    /**
     * Chance that a stray beam passing through your lane actually connects,
     * as a function of how far it has already flown. Rises with travel:
     * divergence widens the beam and makes its path less predictable.
     */
    public static float clipChanceAt(float travelTiles) {
        return lerpOverRange(travelTiles, CLIP_CHANCE_NEAR, CLIP_CHANCE_FAR);
    }

    /**
     * Damage multiplier for a beam that has flown the given distance. Falls
     * with travel as the beam attenuates.
     */
    public static float attenuationAt(float travelTiles) {
        return lerpOverRange(travelTiles, ATTENUATION_NEAR, ATTENUATION_FAR);
    }

    /**
     * Interpolates between the point-blank and maximum-range values, clamping
     * flat outside that span so neither curve runs away at extreme distances.
     */
    private static float lerpOverRange(float travelTiles, float nearValue, float farValue) {
        if (travelTiles <= POINT_BLANK_TILES) {
            return nearValue;
        }
        if (travelTiles >= MAX_RANGE_TILES) {
            return farValue;
        }
        float t = (travelTiles - POINT_BLANK_TILES) / (MAX_RANGE_TILES - POINT_BLANK_TILES);
        return nearValue + (farValue - nearValue) * t;
    }
}
