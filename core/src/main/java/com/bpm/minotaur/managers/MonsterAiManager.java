package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Pathfinder;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.Door; // NEW
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.FactionMatrix;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.spells.MonsterSpellExecutor;
import com.bpm.minotaur.gamedata.spells.SpellDataManager;
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

    private FactionMatrix factionMatrix = new FactionMatrix();

    public FactionMatrix getFactionMatrix() {
        return factionMatrix;
    }

    public void setFactionMatrix(FactionMatrix factionMatrix) {
        if (factionMatrix != null) {
            this.factionMatrix = factionMatrix;
        }
    }

    private void checkAwareness(Monster monster, Player player, Maze maze) {
        monster.decrementRetaliationTurns();

        int playerDist = Math.abs(monsterGridPos.x - playerGridPos.x) + Math.abs(monsterGridPos.y - playerGridPos.y);

        // 1. Check Visual Awareness (Line of Sight)
        int visualRange = 10 + (monster.getIntelligence() / 2);

        if (player.getEquipment() != null && player.getEquipment().hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.INVISIBILITY)) {
            visualRange = 2; // Drastically reduced range
        }

        boolean playerSeen = false;
        if (playerDist <= visualRange) {
            playerSeen = checkLineOfSight(maze, monsterGridPos, playerGridPos);
        }

        // 2. Check Audio Awareness (Hearing) if not seen
        if (!playerSeen) {
            int hearingRange = 5 + (monster.getIntelligence());
            if (playerDist <= hearingRange) {
                int chance = 50 + (monster.getIntelligence() * 5) - (playerDist * 5);
                if (chance > Math.random() * 100) {
                    playerSeen = true;
                }
            }
        }

        // 3. Check for Rival Monsters within visual range
        Monster closestRival = null;
        int closestRivalDist = Integer.MAX_VALUE;

        // If currently retaliating against an attacker, check if attacker is still valid
        if (monster.getTargetMonster() != null) {
            Monster currentTar = monster.getTargetMonster();
            GridPoint2 tarPos = new GridPoint2((int) currentTar.getPosition().x, (int) currentTar.getPosition().y);
            if (currentTar.isAlive() && currentTar.getCurrentHP() > 0 && maze != null && maze.getMonsters().containsKey(tarPos)) {
                closestRival = currentTar;
                closestRivalDist = Math.abs(monsterGridPos.x - tarPos.x) + Math.abs(monsterGridPos.y - tarPos.y);
            } else {
                monster.setTargetMonster(null);
            }
        }

        if (closestRival == null && maze != null && maze.getMonsters() != null) {
            for (Monster other : maze.getMonsters().values()) {
                if (other == null || other == monster || !other.isAlive() || other.getCurrentHP() <= 0) continue;
                if (factionMatrix != null && factionMatrix.isHostile(monster.getFaction(), other.getFaction())) {
                    GridPoint2 otherPos = new GridPoint2((int) other.getPosition().x, (int) other.getPosition().y);
                    int d = Math.abs(monsterGridPos.x - otherPos.x) + Math.abs(monsterGridPos.y - otherPos.y);
                    if (d <= visualRange && d < closestRivalDist) {
                        if (checkLineOfSight(maze, monsterGridPos, otherPos)) {
                            closestRival = other;
                            closestRivalDist = d;
                        }
                    }
                }
            }
        }

        // 4. Target Arbitration (Retaliation > Distance-weighted with player tie-break)
        if (monster.getTargetMonster() != null && monster.getRetaliationTurnsRemaining() > 0) {
            monster.setState(Monster.MonsterState.HUNTING);
            monster.setLastKnownTargetPos(new GridPoint2((int) monster.getTargetMonster().getPosition().x, (int) monster.getTargetMonster().getPosition().y));
            monster.setTurnsSinceLastSeen(0);
        } else if (playerSeen && closestRival != null) {
            if (playerDist <= closestRivalDist) {
                monster.setTargetMonster(null);
                monster.setState(Monster.MonsterState.HUNTING);
                monster.setLastKnownTargetPos(new GridPoint2(playerGridPos));
                monster.setTurnsSinceLastSeen(0);
            } else {
                monster.setTargetMonster(closestRival);
                monster.setState(Monster.MonsterState.HUNTING);
                monster.setLastKnownTargetPos(new GridPoint2((int) closestRival.getPosition().x, (int) closestRival.getPosition().y));
                monster.setTurnsSinceLastSeen(0);
            }
        } else if (playerSeen) {
            monster.setTargetMonster(null);
            monster.setState(Monster.MonsterState.HUNTING);
            monster.setLastKnownTargetPos(new GridPoint2(playerGridPos));
            monster.setTurnsSinceLastSeen(0);
        } else if (closestRival != null) {
            monster.setTargetMonster(closestRival);
            monster.setState(Monster.MonsterState.HUNTING);
            monster.setLastKnownTargetPos(new GridPoint2((int) closestRival.getPosition().x, (int) closestRival.getPosition().y));
            monster.setTurnsSinceLastSeen(0);
        }
    }

    private void handleHuntingBehavior(Monster monster, Maze maze, Player player,
            CombatManager combatManager) {
        Monster rival = monster.getTargetMonster();
        if (rival != null) {
            if (!rival.isAlive() || rival.getCurrentHP() <= 0) {
                monster.setTargetMonster(null);
                monster.setState(Monster.MonsterState.WANDERING);
                return;
            }
            GridPoint2 rivalPos = new GridPoint2((int) rival.getPosition().x, (int) rival.getPosition().y);
            int distToRival = Math.abs(monsterGridPos.x - rivalPos.x) + Math.abs(monsterGridPos.y - rivalPos.y);
            if (distToRival <= 1) {
                if (combatManager != null) {
                    combatManager.monsterVsMonsterStrike(monster, rival, maze);
                }
                return;
            }
            performSeekingMove(monster, rivalPos, maze, player, combatManager);
            return;
        }

        // --- Sanctuary Check: Home Shelter tiles are protected ---
        if (maze.getHomeTiles() != null && !maze.getHomeTiles().isEmpty()) {
            if (maze.getHomeTiles().contains(playerGridPos)) {
                monster.setState(Monster.MonsterState.WANDERING);
                monster.setLastKnownTargetPos(null);
                monster.setSearchTurnsRemaining(0);
                if (combatManager != null && combatManager.getGameScreen() != null) {
                    GameEventManager em = combatManager.getGameScreen().getEventManager();
                    if (em != null) {
                        em.addEvent(new GameEvent("The " + monster.getMonsterType() + " roars in fury at the warded shelter boundary and retreats!", 2.5f));
                    }
                }
                return;
            }
        }

        // Cross-zone hunt search decay
        if (monster.getSearchTurnsRemaining() > 0) {
            monster.setSearchTurnsRemaining(monster.getSearchTurnsRemaining() - 1);
            if (monster.getSearchTurnsRemaining() <= 0 && !checkLineOfSight(maze, monsterGridPos, playerGridPos)) {
                monster.setState(Monster.MonsterState.WANDERING);
                monster.setLastKnownTargetPos(null);
                return;
            }
        }

        // --- Spellcasting Logic (Tactical casting before physical actions) ---
        if (monster.isSpellcaster() && monster.getSpellbook() != null && combatManager != null) {
            boolean canCast = true;
            if (monster.getStatusManager() != null) {
                if (monster.getStatusManager().hasEffect(StatusEffectType.PARALYZED)) {
                    canCast = false;
                }
            }

            if (canCast) {
                int dist = Math.abs(monsterGridPos.x - playerGridPos.x) + Math.abs(monsterGridPos.y - playerGridPos.y);

                // Priority 1: Emergency heal/escape if HP < 35%
                if (monster.getCurrentHP() < (int) (monster.getMaxHP() * 0.35f)) {
                    String defSpell = monster.getSpellbook().selectDefensiveOrHealSpell(
                            monster.getCurrentMP(),
                            SpellDataManager.getInstance());
                    if (defSpell != null) {
                        GameEventManager em = (combatManager.getGameScreen() != null) ? combatManager.getGameScreen().getEventManager() : null;
                        if (MonsterSpellExecutor.castMonsterSpell(monster, defSpell, player, maze, em, combatManager)) {
                            return; // Cast spell, turn consumed
                        }
                    }
                }

                // Priority 2: Ranged Offensive Spellcasting (2 <= dist <= 8 with clear LoS)
                if (dist >= 2 && dist <= 8) {
                    if (checkLineOfSight(maze, monsterGridPos, playerGridPos)) {
                        int chance = monster.getSpellChance() > 0 ? monster.getSpellChance() : 60;
                        if (Math.random() * 100 < chance) {
                            String offSpell = monster.getSpellbook().selectOffensiveSpell(
                                    monster.getCurrentMP(),
                                    dist,
                                    SpellDataManager.getInstance());
                            if (offSpell != null) {
                                GameEventManager em = (combatManager.getGameScreen() != null) ? combatManager.getGameScreen().getEventManager() : null;
                                if (MonsterSpellExecutor.castMonsterSpell(monster, offSpell, player, maze, em, combatManager)) {
                                    return; // Cast spell, turn consumed
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ranged Attack Logic (Only if Hunting player and generally active)
        if (monster.hasRangedAttack() && combatManager != null) {
            int dist = Math.abs(monsterGridPos.x - playerGridPos.x) + Math.abs(monsterGridPos.y - playerGridPos.y);

            // Archer / Skirmisher Kiting: If too close (< 3 tiles) and an unblocked retreat path exists, step back
            if (dist < 3 && monster.getRangedPreferredDistance() >= 4) {
                GridPoint2 retreatCell = findRetreatStep(monsterGridPos, playerGridPos, maze, player);
                if (retreatCell != null) {
                    moveMonsterTo(monster, maze, retreatCell.x, retreatCell.y);
                    return;
                }
            }

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
            int distToRealPlayer = Math.abs(monsterGridPos.x - playerGridPos.x)
                    + Math.abs(monsterGridPos.y - playerGridPos.y);
            if (distToRealPlayer <= 1) {
                if (combatManager != null && monster != combatManager.getMonster()) {
                    if (combatManager.performMonsterFlankAttack(monster)) {
                        return; // Attacked
                    }
                }
                target = playerGridPos;
            } else {
                monster.setTurnsSinceLastSeen(monster.getTurnsSinceLastSeen() + 1);
                if (monster.getTurnsSinceLastSeen() > 5) {
                    monster.setState(Monster.MonsterState.WANDERING);
                    monster.setLastKnownTargetPos(null);
                }
                return;
            }
        }

        if (target == null)
            target = playerGridPos;

        performSeekingMove(monster, target, maze, player, combatManager);
    }

    private void performSeekingMove(Monster monster, GridPoint2 targetPos, Maze maze, Player player,
            CombatManager combatManager) {
        if (targetPos == null)
            return;

        List<GridPoint2> path = Pathfinder.findPath(maze, player, monsterGridPos, targetPos, monster.canOperateDoors());

        if (path != null && !path.isEmpty()) {
            GridPoint2 step = path.get(0);

            // Door Bump: if step moves into a closed door, open it and consume turn
            if (monster.canOperateDoors()) {
                Object objAtStep = maze.getGameObjectAt(step.x, step.y);
                Object objAtCurrent = maze.getGameObjectAt(monsterGridPos.x, monsterGridPos.y);
                com.bpm.minotaur.gamedata.Door doorToOpen = null;
                if (objAtStep instanceof com.bpm.minotaur.gamedata.Door) {
                    com.bpm.minotaur.gamedata.Door d = (com.bpm.minotaur.gamedata.Door) objAtStep;
                    if (d.getState() == com.bpm.minotaur.gamedata.Door.DoorState.CLOSED || d.getState() == com.bpm.minotaur.gamedata.Door.DoorState.CLOSING) {
                        doorToOpen = d;
                    }
                } else if (objAtCurrent instanceof com.bpm.minotaur.gamedata.Door) {
                    com.bpm.minotaur.gamedata.Door d = (com.bpm.minotaur.gamedata.Door) objAtCurrent;
                    if (d.getState() == com.bpm.minotaur.gamedata.Door.DoorState.CLOSED || d.getState() == com.bpm.minotaur.gamedata.Door.DoorState.CLOSING) {
                        doorToOpen = d;
                    }
                }

                if (doorToOpen != null) {
                    doorToOpen.startOpening();
                    int distToPlayer = Math.abs(monsterGridPos.x - playerGridPos.x) + Math.abs(monsterGridPos.y - playerGridPos.y);
                    if (distToPlayer <= 8) {
                        if (combatManager != null && combatManager.getGameScreen() != null) {
                            SoundManager sm = combatManager.getGameScreen().getSoundManager();
                            if (sm != null) {
                                sm.playDoorCreak();
                            }
                            GameEventManager em = combatManager.getGameScreen().getEventManager();
                            if (em != null) {
                                em.addEvent(new GameEvent("You hear a door creak open in the darkness...", 2.0f));
                            }
                        }
                    }
                    return; // Consumes monster's turn opening the door!
                }
            }

            if (step.x == playerGridPos.x && step.y == playerGridPos.y) {
                if (combatManager != null) {
                    if (monster.isSpellcaster() && monster.getSpellbook() != null && Math.random() < 0.35) {
                        String touchSpell = monster.getSpellbook().selectOffensiveSpell(
                                monster.getCurrentMP(),
                                1.0f,
                                SpellDataManager.getInstance());
                        if (touchSpell != null) {
                            GameEventManager em = (combatManager.getGameScreen() != null) ? combatManager.getGameScreen().getEventManager() : null;
                            if (MonsterSpellExecutor.castMonsterSpell(monster, touchSpell, player, maze, em, combatManager)) {
                                return;
                            }
                        }
                    }
                    combatManager.monsterMeleeStrike(monster);
                }
                return;
            }

            tempPos.set(step.x, step.y);
            Monster occupant = maze.getMonsters().get(tempPos);
            if (occupant != null) {
                if (occupant == monster.getTargetMonster() || (factionMatrix != null && factionMatrix.isHostile(monster.getFaction(), occupant.getFaction()))) {
                    if (combatManager != null) {
                        combatManager.monsterVsMonsterStrike(monster, occupant, maze);
                    }
                    return;
                }
                return; // Blocked by allied or non-hostile creature
            }

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

        if (maze.isHomeTile(x, y))
            return false;

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

    private GridPoint2 findRetreatStep(GridPoint2 monsterPos, GridPoint2 playerPos, Maze maze, Player player) {
        int dx = monsterPos.x - playerPos.x; // Positive = monster is East of player
        int dy = monsterPos.y - playerPos.y; // Positive = monster is North of player

        com.bpm.minotaur.gamedata.Direction primaryDir;
        com.bpm.minotaur.gamedata.Direction secondaryDir;

        if (Math.abs(dx) >= Math.abs(dy)) {
            primaryDir = (dx >= 0) ? com.bpm.minotaur.gamedata.Direction.EAST : com.bpm.minotaur.gamedata.Direction.WEST;
            secondaryDir = (dy >= 0) ? com.bpm.minotaur.gamedata.Direction.NORTH : com.bpm.minotaur.gamedata.Direction.SOUTH;
        } else {
            primaryDir = (dy >= 0) ? com.bpm.minotaur.gamedata.Direction.NORTH : com.bpm.minotaur.gamedata.Direction.SOUTH;
            secondaryDir = (dx >= 0) ? com.bpm.minotaur.gamedata.Direction.EAST : com.bpm.minotaur.gamedata.Direction.WEST;
        }

        GridPoint2 candidate = getRetreatCandidate(monsterPos, primaryDir, maze, player);
        if (candidate != null) return candidate;

        return getRetreatCandidate(monsterPos, secondaryDir, maze, player);
    }

    private GridPoint2 getRetreatCandidate(GridPoint2 fromPos, com.bpm.minotaur.gamedata.Direction dir, Maze maze, Player player) {
        if (dir == null) return null;
        if (maze.isWallBlocking(fromPos.x, fromPos.y, dir)) return null;

        int nx = fromPos.x + (int) dir.getVector().x;
        int ny = fromPos.y + (int) dir.getVector().y;

        if (nx < 0 || nx >= maze.getWidth() || ny < 0 || ny >= maze.getHeight()) return null;
        if (!isTileAvailableForAI(maze, player, nx, ny)) return null;

        return new GridPoint2(nx, ny);
    }

}
