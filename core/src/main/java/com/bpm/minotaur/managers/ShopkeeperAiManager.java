package com.bpm.minotaur.managers;

import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.injury.InjuryRecord;
import com.bpm.minotaur.telemetry.TelemetryManager;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.laser.LaserBurst;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.utils.DiceRoller;

import java.util.Random;

/**
 * AI driver for the ShopkeeperNpc.
 *
 * Priority per turn:
 * 1. If TRADING state — skip all movement.
 * 2. If player is adjacent (dist == 1) and cooldown == 0 → fire
 * SHOPKEEPER_INTERACTION event.
 * 3. If a monster is adjacent → auto-attack it.
 * 4. If low HP → flee away from nearest monster.
 * 5. Otherwise → wander randomly (30% chance to move).
 */
public class ShopkeeperAiManager {

    private static final float WANDER_CHANCE = 0.30f;
    private static final int TRADING_COOLDOWN = 5; // turns before re-triggering shop

    private final Random random;

    /** What he says after one of his strays clips the player. */
    public static final String[] APOLOGIES = {
            "The merchant winces. \"Forgive me, friend -- the cell runs wild when it climbs!\"",
            "The merchant lowers the weapon. \"That one was never meant for you. Here, for the burn.\"",
            "The merchant mutters an apology. \"It wasn't built for halls this crowded.\"",
            "The merchant presses a salve on you. \"Stand clear of my line next time, eh?\""
    };

    public ShopkeeperAiManager() {
        this(new Random());
    }

    /** Test seam: a seeded RNG makes the burst, wander and apology rolls reproducible. */
    public ShopkeeperAiManager(Random random) {
        this.random = random;
    }
    private final Array<GridPoint2> possibleMoves = new Array<>();
    private final GridPoint2 tempPos = new GridPoint2();

    /**
     * Test/back-compat entry point: no item or asset manager on hand, so a clip
     * still hits, burns, and apologizes, but cannot physically hand over the
     * salve -- see the 6-arg overload for the full behaviour.
     */
    public void update(ShopkeeperNpc shopkeeper, Maze maze, Player player, GameEventManager eventManager) {
        update(shopkeeper, maze, player, eventManager, null, null);
    }

    public void update(ShopkeeperNpc shopkeeper, Maze maze, Player player, GameEventManager eventManager,
                       ItemDataManager itemDataManager, AssetManager assetManager) {
        if (shopkeeper == null || !shopkeeper.isAlive() || maze == null)
            return;

        // Tick trading cooldown each AI update
        shopkeeper.tickTradingCooldown();

        // 1. Frozen during trade
        if (shopkeeper.getState() == ShopkeeperNpc.ShopkeeperState.TRADING) {
            return;
        }

        GridPoint2 shopPos = shopkeeper.getGridPosition();
        GridPoint2 playerPos = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);

        // 2. Player adjacent → trigger shop
        int distToPlayer = manDist(shopPos, playerPos);
        if (distToPlayer == 1 && shopkeeper.getTradingCooldown() == 0) {
            shopkeeper.setState(ShopkeeperNpc.ShopkeeperState.TRADING);
            shopkeeper.setTradingCooldown(TRADING_COOLDOWN);
            eventManager.addEvent(new GameEvent(GameEvent.EventType.SHOPKEEPER_INTERACTION, shopkeeper));
            return;
        }

        // 3. Void chain laser: his weapon of choice whenever a monster stands in range and in sight.
        //    After a burst the weapon cycles for LASER_COOLDOWN_TURNS. He holds his ground meanwhile,
        //    clubbing anything that closes to melee range, rather than wandering off mid-fight.
        boolean laserCharged = shopkeeper.isLaserCharged();
        shopkeeper.tickLaserCooldown();
        Monster laserTarget = findLaserTarget(shopkeeper, maze);
        if (laserTarget != null) {
            if (laserCharged && LaserBurst.isInLineOfFire(maze, shopkeeper.getPosition(),
                    laserTarget.getPosition(), player.getPosition())) {
                // Fire discipline: he will not open up with the player squarely in the lane.
                eventManager.addEvent(new GameEvent(
                        "The merchant swings his weapon aside -- you are in his line of fire!", 1.5f));
            } else if (laserCharged) {
                fireLaser(shopkeeper, laserTarget, maze, player, eventManager, itemDataManager, assetManager);
                shopkeeper.startLaserCooldown();
            } else {
                Monster closeIn = findAdjacentMonster(shopkeeper, maze);
                if (closeIn != null) {
                    performAttack(shopkeeper, closeIn, maze, eventManager);
                }
            }
            return;
        }

        // 4. Adjacent monster attack
        Monster adjacentMonster = findAdjacentMonster(shopkeeper, maze);
        if (adjacentMonster != null) {
            performAttack(shopkeeper, adjacentMonster, maze, eventManager);
            return;
        }

        // 4. Flee if low HP
        if (shopkeeper.isLowHP()) {
            shopkeeper.setState(ShopkeeperNpc.ShopkeeperState.FLEEING);
            performFleeMove(shopkeeper, maze, player);
            return;
        }

        // 5. Wander
        shopkeeper.setState(ShopkeeperNpc.ShopkeeperState.WANDERING);
        if (random.nextFloat() < WANDER_CHANCE) {
            performRandomMove(shopkeeper, maze, player);
        }
    }

    // ── Combat ────────────────────────────────────────────────────────────────

    private void performAttack(ShopkeeperNpc shopkeeper, Monster monster, Maze maze, GameEventManager eventManager) {
        int dmg = DiceRoller.roll("2d4") + 2; // 2d4+2, ~6-10 damage
        monster.takeDamage(dmg);

        eventManager.addEvent(new GameEvent(
                "The merchant defends themselves! (-" + dmg + " dmg)", 2f));

        com.badlogic.gdx.Gdx.app.log("ShopkeeperAI", "Shopkeeper attacked " +
                monster.getMonsterType() + " for " + dmg + " dmg. Monster HP: " + monster.getCurrentHP());

        if (monster.getCurrentHP() <= 0) {
            GridPoint2 mPos = new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y);
            maze.getMonsters().remove(mPos);
            eventManager.addEvent(new GameEvent("The merchant defeated the " + monster.getMonsterType() + "!", 2.5f));
        }
    }

    /**
     * Fires one burst and applies what it resolved. The burst goes out as a
     * LASER_BURST event for the renderer; beams that connected are burned
     * into the target.
     */
    private void fireLaser(ShopkeeperNpc shopkeeper, Monster target, Maze maze, Player player,
                           GameEventManager eventManager, ItemDataManager itemDataManager, AssetManager assetManager) {
        float cellOutput = LaserBurst.cellOutputAtDepth(maze.getLevel());
        LaserBurst.BurstResult result = LaserBurst.fire(maze, shopkeeper.getPosition(), target, player,
                cellOutput, random);
        eventManager.addEvent(new GameEvent(GameEvent.EventType.LASER_BURST, result));

        if (result.getDamageToTarget() > 0) {
            target.takeDamage(result.getDamageToTarget(), DamageType.FIRE);
        }
        removeIfSlain(target, maze, eventManager);

        if (result.isPlayerClipped()) {
            applyFriendlyFire(shopkeeper, result, player, eventManager, itemDataManager, assetManager);
        }
    }

    /**
     * One of his strays found the player. Ordinary armour does nothing against
     * coherent light, so the hit goes straight through unless they wear the
     * hardened set; it can burn them through the usual trauma gate; and then he
     * makes amends in the vocabulary of the wound: a salve and a better price.
     */
    private void applyFriendlyFire(ShopkeeperNpc shopkeeper, LaserBurst.BurstResult result, Player player,
                                   GameEventManager eventManager, ItemDataManager itemDataManager,
                                   AssetManager assetManager) {
        int totalRaw = result.getDamageToPlayer();
        int totalMitigated = LaserBurst.mitigateForArmour(player, totalRaw);
        player.takeTrueDamage(totalMitigated);

        TelemetryManager telemetry = TelemetryManager.getInstance();
        telemetry.recordDamageTaken(totalMitigated);
        telemetry.recordMerchantFriendlyFire(totalMitigated);
        telemetry.setLastDamageSource("TRAVELING_MERCHANT");
        eventManager.addEvent(new GameEvent("A stray beam sears you! (-" + totalMitigated + " HP)", 2.0f));

        // Trauma is rolled per individual stray, never against the burst's summed total: more than
        // one stray can clip the same bystander in one burst (their angles are close enough near the
        // merchant that two adjacent beams both cross a tight corridor), and Q15/Q23 only verified the
        // trauma floor and the hardened-armour halving against a single beam's magnitude. Rolling on
        // the sum would let several individually-safe grazes launder into one over-floor burn.
        if (player.getInjuryManager() != null) {
            for (int rawClip : result.getPlayerClipDamages()) {
                int mitigatedClip = LaserBurst.mitigateForArmour(player, rawClip);
                InjuryRecord burn = player.getInjuryManager().rollForInjury(
                        DamageType.FIRE, mitigatedClip, player.getStats().getMaxHP(), false);
                if (burn != null) {
                    telemetry.recordInjurySustained();
                    eventManager.addEvent(new GameEvent("The beam leaves a " + burn.getInjuryType().getDisplayName()
                            + " on your " + burn.getBodyPart().getDisplayName() + "!", 3.0f));
                }
            }
        }

        shopkeeper.offerRestitution();
        eventManager.addEvent(new GameEvent(APOLOGIES[random.nextInt(APOLOGIES.length)], 3.0f));
        eventManager.addEvent(new GameEvent(GameEvent.EventType.SHOPKEEPER_RESTITUTION, shopkeeper));

        // The apology in his own vocabulary: slime residue is the exact SEVERE_BURN
        // treatment InjuryManager.applyTreatment already recognizes, so the moment
        // he burns someone he hands them the specific thing that treats it.
        if (itemDataManager != null) {
            try {
                Item salve = itemDataManager.createItem(Item.ItemType.SLIME_RESIDUE, 0, 0, null, assetManager);
                if (salve != null && player.getInventory().pickupToBackpack(salve)) {
                    eventManager.addEvent(new GameEvent("The merchant presses a dab of slime residue into your hand.", 2.5f));
                }
            } catch (Exception ignored) {
                // Backpack full or item unavailable -- the apology and discount still stand.
            }
        }
    }

    private void removeIfSlain(Monster monster, Maze maze, GameEventManager eventManager) {
        if (monster.getCurrentHP() > 0) {
            return;
        }
        GridPoint2 mPos = new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y);
        maze.getMonsters().remove(mPos);
        eventManager.addEvent(new GameEvent("The merchant's beam cuts down the " + monster.getMonsterType() + "!", 2.5f));
    }

    /** Nearest living monster within the laser's range that he has a clear line of sight to. */
    private Monster findLaserTarget(ShopkeeperNpc shopkeeper, Maze maze) {
        Vector2 origin = shopkeeper.getPosition();
        Monster best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Monster monster : maze.getMonsters().values()) {
            if (monster.getCurrentHP() <= 0) {
                continue;
            }
            float distance = origin.dst(monster.getPosition());
            if (distance > LaserBurst.MAX_RANGE_TILES || distance >= bestDistance) {
                continue;
            }
            if (!LaserBurst.hasLineOfSight(maze, origin, monster.getPosition())) {
                continue;
            }
            best = monster;
            bestDistance = distance;
        }
        return best;
    }

    // ── Movement helpers ──────────────────────────────────────────────────────

    private void performRandomMove(ShopkeeperNpc shopkeeper, Maze maze, Player player) {
        possibleMoves.clear();
        GridPoint2 pos = shopkeeper.getGridPosition();

        for (Direction dir : Direction.values()) {
            if (!maze.isWallBlocking(pos.x, pos.y, dir)) {
                int nx = pos.x + (int) dir.getVector().x;
                int ny = pos.y + (int) dir.getVector().y;
                if (isTileAvailable(maze, player, nx, ny)) {
                    possibleMoves.add(new GridPoint2(nx, ny));
                }
            }
        }

        if (!possibleMoves.isEmpty()) {
            possibleMoves.shuffle();
            GridPoint2 target = possibleMoves.get(0);
            moveShopkeeperTo(shopkeeper, maze, target.x, target.y);
        }
    }

    private void performFleeMove(ShopkeeperNpc shopkeeper, Maze maze, Player player) {
        // Find nearest monster and move away from it
        GridPoint2 shopPos = shopkeeper.getGridPosition();
        Monster nearest = findNearestMonster(shopkeeper, maze);
        if (nearest == null) {
            performRandomMove(shopkeeper, maze, player);
            return;
        }

        GridPoint2 monsterPos = new GridPoint2(
                (int) nearest.getPosition().x, (int) nearest.getPosition().y);

        possibleMoves.clear();
        for (Direction dir : Direction.values()) {
            if (!maze.isWallBlocking(shopPos.x, shopPos.y, dir)) {
                int nx = shopPos.x + (int) dir.getVector().x;
                int ny = shopPos.y + (int) dir.getVector().y;
                if (isTileAvailable(maze, player, nx, ny)) {
                    possibleMoves.add(new GridPoint2(nx, ny));
                }
            }
        }

        if (!possibleMoves.isEmpty()) {
            // Pick tile that maximises distance from monster
            GridPoint2 best = possibleMoves.get(0);
            int bestDist = manDist(best, monsterPos);
            for (int i = 1; i < possibleMoves.size; i++) {
                GridPoint2 candidate = possibleMoves.get(i);
                int d = manDist(candidate, monsterPos);
                if (d > bestDist) {
                    bestDist = d;
                    best = candidate;
                }
            }
            moveShopkeeperTo(shopkeeper, maze, best.x, best.y);
        }
    }

    private void moveShopkeeperTo(ShopkeeperNpc shopkeeper, Maze maze, int tx, int ty) {
        shopkeeper.getPosition().set(tx + 0.5f, ty + 0.5f);
    }

    // ── Scanning helpers ──────────────────────────────────────────────────────

    private Monster findAdjacentMonster(ShopkeeperNpc shopkeeper, Maze maze) {
        GridPoint2 pos = shopkeeper.getGridPosition();
        int[] dx = { 0, 0, 1, -1 };
        int[] dy = { 1, -1, 0, 0 };
        for (int i = 0; i < 4; i++) {
            tempPos.set(pos.x + dx[i], pos.y + dy[i]);
            Monster m = maze.getMonsters().get(tempPos);
            if (m != null && m.getCurrentHP() > 0)
                return m;
        }
        return null;
    }

    private Monster findNearestMonster(ShopkeeperNpc shopkeeper, Maze maze) {
        GridPoint2 pos = shopkeeper.getGridPosition();
        Monster nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (java.util.Map.Entry<GridPoint2, Monster> entry : maze.getMonsters().entrySet()) {
            if (entry.getValue().getCurrentHP() <= 0)
                continue;
            int d = manDist(pos, entry.getKey());
            if (d < minDist) {
                minDist = d;
                nearest = entry.getValue();
            }
        }
        return nearest;
    }

    private boolean isTileAvailable(Maze maze, Player player, int x, int y) {
        if (!maze.isPassable(x, y))
            return false;
        tempPos.set(x, y);
        Scenery s = maze.getScenery().get(tempPos);
        if (s != null && s.isImpassable())
            return false;
        if ((int) player.getPosition().x == x && (int) player.getPosition().y == y)
            return false;
        if (maze.getMonsters().containsKey(tempPos))
            return false;
        return true;
    }

    private static int manDist(GridPoint2 a, GridPoint2 b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }
}
