package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Pathfinder;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;

import java.util.List;

public class MonsterAiManager {

    private final GridPoint2 tempPos = new GridPoint2();
    private final Array<GridPoint2> possibleMoves = new Array<>();
    private final Array<Monster> monsterTurnOrder = new Array<>();
    private final GridPoint2 playerGridPos = new GridPoint2();
    private final GridPoint2 monsterGridPos = new GridPoint2();

    public MonsterAiManager() {
        // Constructor
    }

    /**
     * The main AI update loop.
     * @param maze The current Maze object.
     * @param player The Player object.
     * @param allowMonsterMovement A boolean to enable/disable movement.
     * @param combatManager The CombatManager (needed for ranged attacks).
     */
    public void updateMonsterLogic(Maze maze, Player player, boolean allowMonsterMovement, CombatManager combatManager) {
        if (maze == null || maze.getMonsters() == null || player == null || !allowMonsterMovement) {
            return;
        }

        monsterTurnOrder.clear();
        for (Monster m : maze.getMonsters().values()) {
            monsterTurnOrder.add(m);
        }

        // Get player's position once for all monsters
        playerGridPos.set((int) player.getPosition().x, (int) player.getPosition().y);

        for (Monster monster : monsterTurnOrder) {
            if (monster == null) continue;

            // Get monster's position once
            monsterGridPos.set((int) monster.getPosition().x, (int) monster.getPosition().y);

            // --- NEW: Ranged Attack Logic ---
            if (monster.hasRangedAttack() && combatManager != null) {
                // Check distance
                int dist = Math.abs(monsterGridPos.x - playerGridPos.x) + Math.abs(monsterGridPos.y - playerGridPos.y);

                // Only shoot if within range AND not adjacent (adjacent is melee range)
                if (dist <= monster.getAttackRange() && dist > 1) {

                    // Check alignment (Cardinal only for now)
                    boolean alignedX = (monsterGridPos.x == playerGridPos.x);
                    boolean alignedY = (monsterGridPos.y == playerGridPos.y);

                    if (alignedX || alignedY) {
                        // Try to fire
                        if (combatManager.performMonsterRangedAttack(monster)) {
                            continue; // Monster attacked, skip movement this turn
                        }
                    }
                }
            }
            // --------------------------------

            int intel = monster.getIntelligence();
            if (intel == 0) {
                performRandomMove(monster, maze, player);
            } else if (intel > 0) {
                performSeekingMove(monster, player, maze);
            }
        }
    }

    /**
     * Moves a monster one step along the shortest path to the player.
     */
    private void performSeekingMove(Monster monster, Player player, Maze maze) {
        int dist = Math.abs(monsterGridPos.x - playerGridPos.x) + Math.abs(monsterGridPos.y - playerGridPos.y);
        if (dist == 1) {
            return; // Already next to the player
        }

        List<GridPoint2> path = Pathfinder.findPath(maze, player, monsterGridPos, playerGridPos);

        if (path != null && !path.isEmpty()) {
            GridPoint2 target = path.get(0);

            if (target.x == playerGridPos.x && target.y == playerGridPos.y) return;

            tempPos.set(target.x, target.y);
            if (maze.getMonsters().containsKey(tempPos)) return;

            moveMonsterTo(monster, maze, target.x, target.y);
        }
    }

    /**
     * Moves a monster to a random, valid, adjacent tile.
     */
    private void performRandomMove(Monster monster, Maze maze, Player player) {
        possibleMoves.clear();

        for (Direction dir : Direction.values()) {
            if (!maze.isWallBlocking(monsterGridPos.x, monsterGridPos.y, dir)) {
                int nextX = monsterGridPos.x + (int)dir.getVector().x;
                int nextY = monsterGridPos.y + (int)dir.getVector().y;

                if (isTileAvailableForAI(maze, player, nextX, nextY)) {
                    possibleMoves.add(new GridPoint2(nextX, nextY));
                }
            }
        }

        if (!possibleMoves.isEmpty()) {
            possibleMoves.shuffle();
            GridPoint2 target = possibleMoves.get(0);
            moveMonsterTo(monster, maze, target.x, target.y);
        }
    }

    private boolean isTileAvailableForAI(Maze maze, Player player, int x, int y) {
        tempPos.set(x, y);

        if (!maze.isPassable(x, y)) return false;

        Scenery s = maze.getScenery().get(tempPos);
        if (s != null && s.isImpassable()) return false;

        if (playerGridPos.x == x && playerGridPos.y == y) return false;

        if (maze.getMonsters().containsKey(tempPos)) return false;

        return true;
    }

    private void moveMonsterTo(Monster monster, Maze maze, int targetX, int targetY) {
        tempPos.set((int)monster.getPosition().x, (int)monster.getPosition().y);
        maze.getMonsters().remove(tempPos);
        monster.getPosition().set(targetX + 0.5f, targetY + 0.5f);
        maze.getMonsters().put(new GridPoint2(targetX, targetY), monster);
    }
}
