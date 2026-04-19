package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Pathfinder;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.Door; // NEW
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.badlogic.gdx.Gdx; // NEW

import java.util.List;

public class MonsterAiManager {

    private final GridPoint2 tempPos = new GridPoint2();
    private final Array<GridPoint2> possibleMoves = new Array<>();
    private final GridPoint2 playerGridPos = new GridPoint2();
    private final GridPoint2 monsterGridPos = new GridPoint2();

    public MonsterAiManager() {
        // Constructor
    }

    /**
     * The main AI update loop.
     * 
     * @param maze                 The current Maze object.
     * @param player               The Player object.
     * @param allowMonsterMovement A boolean to enable/disable movement.
     * @param combatManager        The CombatManager (needed for ranged attacks).
     */
    /**
     * Updates a single monster's AI.
     * 
     * @param monster              The monster to update.
     * @param maze                 The current Maze object.
     * @param player               The Player object.
     * @param allowMonsterMovement A boolean to enable/disable movement.
     * @param combatManager        The CombatManager (needed for ranged attacks).
     */
    /**
     * Updates a single monster's AI.
     */
    public void updateMonster(Monster monster, Maze maze, Player player, boolean allowMonsterMovement,
            CombatManager combatManager) {
        if (maze == null || monster == null || player == null || !allowMonsterMovement) {
            return;
        }

        playerGridPos.set((int) player.getPosition().x, (int) player.getPosition().y);
        monsterGridPos.set((int) monster.getPosition().x, (int) monster.getPosition().y);

        // --- NEW: State Machine Logic ---

        // 1. Check Awareness to potentially change state
        checkAwareness(monster, player, maze);

        // 2. Act based on State
        switch (monster.getState()) {
            case IDLE:
            case WANDERING:
                // Low chance to move randomly if Wandering
                if (monster.getState() == Monster.MonsterState.WANDERING) {
                    if (Math.random() < 0.2f) { // 20% chance to wander
                        performRandomMove(monster, maze, player);
                    }
                }
                // Determine if we switch between IDLE and WANDERING?
                // For now, keep as initialized or switched by external events.
                // Maybe switch to Wandering if Idle for too long?
                // Simple logic: If not Hunting, just chill or wander.
                break;

            case HUNTING:
                handleHuntingBehavior(monster, maze, player, combatManager);
                break;
        }
    }

    private void checkAwareness(Monster monster, Player player, Maze maze) {
        int dist = Math.abs(monsterGridPos.x - playerGridPos.x) + Math.abs(monsterGridPos.y - playerGridPos.y);

        // 1. Check Visual Awareness (Line of Sight)
        // High intel monsters might see further? Fixed range for now.
        int visualRange = 10 + (monster.getIntelligence() / 2);

        if (player.getEquipment().hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.INVISIBILITY)) {
            visualRange = 2; // Drastically reduced range
            // Could also log debug if close but not seeing
        }

        boolean hasLOS = false;

        if (dist <= visualRange) {
            hasLOS = checkLineOfSight(maze, monsterGridPos, playerGridPos);
            if (hasLOS) {
                if (monster.getState() != Monster.MonsterState.HUNTING) {
                    Gdx.app.log("AI", monster.getMonsterType() + " saw player! State -> HUNTING");
                }
                monster.setState(Monster.MonsterState.HUNTING);
                monster.setLastKnownTargetPos(new GridPoint2(playerGridPos));
                monster.setTurnsSinceLastSeen(0);
                return; // Seen! Immediately hunting.
            }
        }

        // 2. Check Audio Awareness (Hearing)
        int hearingRange = 5 + (monster.getIntelligence()); // Base 5 + Intel
        if (dist <= hearingRange) {
            // Chance to hear based on distance and intelligence
            // Closer = higher chance. Smarter = higher chance.
            int chance = 50 + (monster.getIntelligence() * 5) - (dist * 5);
            if (chance > Math.random() * 100) {
                if (monster.getState() != Monster.MonsterState.HUNTING) {
                    Gdx.app.log("AI", monster.getMonsterType() + " heard player! State -> HUNTING");
                }
                monster.setState(Monster.MonsterState.HUNTING);
                monster.setLastKnownTargetPos(new GridPoint2(playerGridPos));
                monster.setTurnsSinceLastSeen(0);
            }
        }
    }

    private void handleHuntingBehavior(Monster monster, Maze maze, Player player,
            CombatManager combatManager) {
        // Ranged Attack Logic (Only if Hunting and generally active)
        if (monster.hasRangedAttack() && combatManager != null) {
            int dist = Math.abs(monsterGridPos.x - playerGridPos.x) + Math.abs(monsterGridPos.y - playerGridPos.y);
            if (dist <= monster.getAttackRange() && dist > 1) {
                // Check LoS for shooting
                if (checkLineOfSight(maze, monsterGridPos, playerGridPos)) {
                    boolean alignedX = (monsterGridPos.x == playerGridPos.x);
                    boolean alignedY = (monsterGridPos.y == playerGridPos.y);
                    if (alignedX || alignedY) {
                        if (combatManager.performMonsterRangedAttack(monster)) {
                            return; // Attacked, skip move
                        }
                    }
                }
            }
        }

        // Pathfinding Logic
        GridPoint2 target = monster.getLastKnownTargetPos();

        // If we are at the last known position and player is not there...
        if (monsterGridPos.equals(target)) {
            // If we can see the player NOW, update target (should have happened in
            // checkAwareness)
            // If checkAwareness didn't update us (no LoS), then we lost them.
            int distToRealPlayer = Math.abs(monsterGridPos.x - playerGridPos.x)
                    + Math.abs(monsterGridPos.y - playerGridPos.y);
            if (distToRealPlayer <= 1) {
                if (combatManager != null && monster != combatManager.getMonster()) {
                    // Try to flank instead of moving/idle
                    if (combatManager.performMonsterFlankAttack(monster)) {
                        return; // Attacked
                    }
                }

                // Just ensure target is player.
                target = playerGridPos;
            } else {
                // Lost 'em.
                monster.setTurnsSinceLastSeen(monster.getTurnsSinceLastSeen() + 1);
                if (monster.getTurnsSinceLastSeen() > 5) {
                    monster.setState(Monster.MonsterState.WANDERING);
                    monster.setLastKnownTargetPos(null);
                }
                return; // Look around confusingly
            }
        }

        // If target is null (shouldn't be if hunting), default to player but risk
        // cheating
        if (target == null)
            target = playerGridPos;

        performSeekingMove(monster, target, maze, player, combatManager);
    }

    private void performSeekingMove(Monster monster, GridPoint2 targetPos, Maze maze, Player player,
            CombatManager combatManager) {
        if (targetPos == null)
            return;

        List<GridPoint2> path = Pathfinder.findPath(maze, player, monsterGridPos, targetPos);

        if (path != null && !path.isEmpty()) {
            GridPoint2 step = path.get(0);

            if (step.x == playerGridPos.x && step.y == playerGridPos.y) {
                if (combatManager != null) {
                    combatManager.monsterMeleeStrike(monster);
                }
                return;
            }

            tempPos.set(step.x, step.y);
            if (maze.getMonsters().containsKey(tempPos))
                return; // Blocked by friend

            moveMonsterTo(monster, maze, step.x, step.y);
        }
    }

    /**
     * Bresenham-like Line of Sight that respects walls.
     */
    private boolean checkLineOfSight(Maze maze, GridPoint2 start, GridPoint2 end) {
        int x0 = start.x;
        int y0 = start.y;
        int x1 = end.x;
        int y1 = end.y;

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);

        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;

        int err = dx - dy;

        int cx = x0;
        int cy = y0;

        while (true) {
            if (cx == x1 && cy == y1)
                return true;

            // Note: This checks the CELL.
            // Strict wall check:
            // If checking edge walls, we need to check direction of entry.
            // But simplified: If maze.isPassable(cx, cy) is false (wall), blocked.
            // Except for start and end.
            if (!(cx == start.x && cy == start.y)) {
                // We can use isWallBlocking-like logic check for solid blocks.
                // Since we don't know "Direction" of entry easily here without logic.
                // Let's rely on wallData != 0 roughly implies wall.
                // Actually, use `maze.getWallDataAt(cx, cy)`
                // If it's 0, it's open.
                int walls = maze.getWallDataAt(cx, cy);
                // 0 is open?
                // Let's assume 0 is open floor.
                if (walls != 0)
                    return false;

                // Check Door
                Object obj = maze.getGameObjectAt(cx, cy);
                if (obj instanceof Door) {
                    if (((Door) obj).getState() != Door.DoorState.OPEN)
                        return false;
                }
            }

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                cx += sx;
            }
            if (e2 < dx) {
                err += dx;
                cy += sy;
            }
        }
    }

    // Removed unused performSeekingMove wrapper

    private void moveMonsterTo(Monster monster, Maze maze, int targetX, int targetY) {
        tempPos.set((int) monster.getPosition().x, (int) monster.getPosition().y);
        maze.getMonsters().remove(tempPos);
        monster.getPosition().set(targetX + 0.5f, targetY + 0.5f);
        maze.getMonsters().put(new GridPoint2(targetX, targetY), monster);
    }

    // Keep random move
    private void performRandomMove(Monster monster, Maze maze, Player player) {
        possibleMoves.clear();

        for (Direction dir : Direction.values()) {
            if (!maze.isWallBlocking(monsterGridPos.x, monsterGridPos.y, dir)) {
                int nextX = monsterGridPos.x + (int) dir.getVector().x;
                int nextY = monsterGridPos.y + (int) dir.getVector().y;

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

        if (!maze.isPassable(x, y))
            return false;

        Scenery s = maze.getScenery().get(tempPos);
        if (s != null && s.isImpassable())
            return false;

        if (playerGridPos.x == x && playerGridPos.y == y)
            return false;

        if (maze.getMonsters().containsKey(tempPos))
            return false;

        return true;
    }

}
