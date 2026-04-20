package com.bpm.minotaur.managers;

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

    private final Random random = new Random();
    private final Array<GridPoint2> possibleMoves = new Array<>();
    private final GridPoint2 tempPos = new GridPoint2();

    public void update(ShopkeeperNpc shopkeeper, Maze maze, Player player, GameEventManager eventManager) {
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

        // 3. Adjacent monster attack
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
