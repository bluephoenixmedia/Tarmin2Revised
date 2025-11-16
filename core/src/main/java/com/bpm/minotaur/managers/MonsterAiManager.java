package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.bpm.minotaur.gamedata.Direction; // <-- NEW IMPORT
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Pathfinder;
import com.bpm.minotaur.gamedata.Scenery; // <-- NEW IMPORT
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;

import java.util.Collections;
import java.util.List;

/**
 * Manages the turn-based AI logic for all monsters in the current maze.
 * This manager is called once per player turn.
 */
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
     * The main AI update loop. This is called by GameScreen after the player's action.
     * @param maze The current Maze object.
     * @param player The Player object.
     * @param allowMonsterMovement A boolean (from GameScreen) to enable/disable movement.
     */
    public void updateMonsterLogic(Maze maze, Player player, boolean allowMonsterMovement) {
        // Added allowMonsterMovement check
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
     * Uses the Pathfinder, which now contains all correct collision logic.
     */
    private void performSeekingMove(Monster monster, Player player, Maze maze) {
        // 1. Check if already adjacent
        int dist = Math.abs(monsterGridPos.x - playerGridPos.x) + Math.abs(monsterGridPos.y - playerGridPos.y);
        if (dist == 1) {
            return; // Already next to the player, do not move.
        }

        // 2. Find the path. Pathfinder now does ALL collision checks.
        List<GridPoint2> path = Pathfinder.findPath(maze, player, monsterGridPos, playerGridPos);

        // 3. Move along the path
        if (path != null && !path.isEmpty()) {
            GridPoint2 target = path.get(0);

            // Safety Check: Don't move ONTO the player
            if (target.x == playerGridPos.x && target.y == playerGridPos.y) {
                return; // Next step is the player, so stay put.
            }

            // Safety Check: Don't move onto another monster
            tempPos.set(target.x, target.y);
            if (maze.getMonsters().containsKey(tempPos)) {
                return; // Target tile is blocked by another monster (that moved this turn)
            }

            // Move the monster
            moveMonsterTo(monster, maze, target.x, target.y);
        }
    }

    /**
     * Moves a monster to a random, valid, adjacent tile.
     * This now uses the new, unified AI collision check.
     */
    private void performRandomMove(Monster monster, Maze maze, Player player) {
        possibleMoves.clear();

        // Check all 4 directions using the Player's exact logic
        for (Direction dir : Direction.values()) {
            // 1. Check EDGE (like Player.move)
            if (!maze.isWallBlocking(monsterGridPos.x, monsterGridPos.y, dir)) {

                int nextX = monsterGridPos.x + (int)dir.getVector().x;
                int nextY = monsterGridPos.y + (int)dir.getVector().y;

                // 2. Check NODE (like Player.move)
                if (isTileAvailableForAI(maze, player, nextX, nextY)) {
                    possibleMoves.add(new GridPoint2(nextX, nextY));
                }
            }
        }

        if (!possibleMoves.isEmpty()) {
            // Pick a random valid move
            possibleMoves.shuffle();
            GridPoint2 target = possibleMoves.get(0);

            // Move the monster
            moveMonsterTo(monster, maze, target.x, target.y);
        }
    }

    /**
     * --- [ NEW UNIFIED HELPER METHOD ] ---
     * This is the "master" collision check for AI random movement.
     * It checks if a TILE (node) is available to be stood on.
     * It mirrors the checks from Player.move() and Pathfinder.findPath()
     */
    private boolean isTileAvailableForAI(Maze maze, Player player, int x, int y) {
        tempPos.set(x, y); // Use our re-usable GridPoint2

        // 1. Check for solid wall blocks, closed doors, closed gates
        if (!maze.isPassable(x, y)) {
            // isPassable logs its own failure
            return false;
        }

        // 2. Check for impassable scenery (from Player.move)
        Scenery s = maze.getScenery().get(tempPos);
        if (s != null && s.isImpassable()) {
            return false;
        }

        // 3. Check for Player
        if (playerGridPos.x == x && playerGridPos.y == y) {
            return false;
        }

        // 4. Check for other Monsters
        if (maze.getMonsters().containsKey(tempPos)) {
            return false;
        }

        // All checks passed
        return true;
    }

    /**
     * Helper method to move a monster from its old position to a new one.
     */
    private void moveMonsterTo(Monster monster, Maze maze, int targetX, int targetY) {
        // 1. Get old position and remove from map
        tempPos.set((int)monster.getPosition().x, (int)monster.getPosition().y);
        maze.getMonsters().remove(tempPos);

        // 2. Update monster's internal Vector2 to be CENTER-TILE (+0.5f)
        monster.getPosition().set(targetX + 0.5f, targetY + 0.5f);

        // 3. Add monster back to map at new grid position
        maze.getMonsters().put(new GridPoint2(targetX, targetY), monster);
    }
}
