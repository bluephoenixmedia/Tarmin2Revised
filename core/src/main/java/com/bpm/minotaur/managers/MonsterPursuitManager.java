package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.player.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages inter-zonal pursuit for high-intelligence monsters.
 * Handles horizontal gate pursuit across chunks and vertical ladder/stair pursuit across strata depths.
 */
public class MonsterPursuitManager {

    private static MonsterPursuitManager instance;

    public enum PursuitType {
        GATE,
        LADDER
    }

    public static class PendingPursuer {
        public Monster monster;
        public PursuitType type;
        public int originLevel;
        public int targetLevel;
        public GridPoint2 originChunk;
        public GridPoint2 targetChunk;
        public GridPoint2 arrivalTile;
        public int turnsRemaining;
        public int searchTurnsRemaining = 8;
        public boolean isDescending = true;
        public boolean warned = false;

        public PendingPursuer() {}

        public PendingPursuer(Monster monster, PursuitType type, int originLevel, int targetLevel,
                              GridPoint2 originChunk, GridPoint2 targetChunk, GridPoint2 arrivalTile,
                              int turnsRemaining, boolean isDescending) {
            this.monster = monster;
            this.type = type;
            this.originLevel = originLevel;
            this.targetLevel = targetLevel;
            this.originChunk = (originChunk != null) ? new GridPoint2(originChunk) : null;
            this.targetChunk = (targetChunk != null) ? new GridPoint2(targetChunk) : null;
            this.arrivalTile = (arrivalTile != null) ? new GridPoint2(arrivalTile) : new GridPoint2(0, 0);
            this.turnsRemaining = turnsRemaining;
            this.isDescending = isDescending;
        }
    }

    private final List<PendingPursuer> pendingPursuers = new ArrayList<>();

    public static synchronized MonsterPursuitManager getInstance() {
        if (instance == null) {
            instance = new MonsterPursuitManager();
        }
        return instance;
    }

    public static synchronized void resetInstance() {
        instance = new MonsterPursuitManager();
    }

    public List<PendingPursuer> getPendingPursuers() {
        return pendingPursuers;
    }

    public void clear() {
        pendingPursuers.clear();
    }

    /**
     * Registers up to 2 hunting monsters pursuing the player through a chunk gate.
     */
    public void registerGatePursuit(List<Monster> pursuers, GridPoint2 originChunk, GridPoint2 targetChunk,
                                    GridPoint2 arrivalTile, int level) {
        if (pursuers == null || pursuers.isEmpty()) return;

        int count = Math.min(2, pursuers.size());
        for (int i = 0; i < count; i++) {
            Monster m = pursuers.get(i);
            int turns = (i == 0) ? 2 : 4;
            pendingPursuers.add(new PendingPursuer(m, PursuitType.GATE, level, level, originChunk, targetChunk,
                    arrivalTile, turns, true));
        }
    }

    /**
     * Registers up to 2 hunting monsters pursuing the player up or down a ladder/stairs.
     */
    public void registerLadderPursuit(List<Monster> pursuers, int originLevel, int targetLevel,
                                      GridPoint2 arrivalTile, boolean isDescending) {
        if (pursuers == null || pursuers.isEmpty()) return;

        int count = Math.min(2, pursuers.size());
        for (int i = 0; i < count; i++) {
            Monster m = pursuers.get(i);
            int turns = (i == 0) ? 3 : 5;
            pendingPursuers.add(new PendingPursuer(m, PursuitType.LADDER, originLevel, targetLevel, null, null,
                    arrivalTile, turns, isDescending));
        }
    }

    /**
     * Invoked on each player turn to advance pursuit countdowns, emit warnings, and spawn arrivals.
     */
    public void onPlayerTurn(Maze currentMaze, Player player, WorldManager worldManager, SoundManager soundManager,
                             GameEventManager eventManager, CombatManager combatManager) {
        if (pendingPursuers.isEmpty() || currentMaze == null || player == null) return;

        GridPoint2 playerChunk = (worldManager != null) ? worldManager.getCurrentPlayerChunkId() : null;
        int currentLevel = (worldManager != null) ? worldManager.getCurrentLevel() : currentMaze.getLevel();

        Iterator<PendingPursuer> iter = pendingPursuers.iterator();
        while (iter.hasNext()) {
            PendingPursuer pursuer = iter.next();

            // Ladder Dread Warning on first tick
            if (pursuer.type == PursuitType.LADDER && !pursuer.warned) {
                pursuer.warned = true;
                if (eventManager != null) {
                    String warning = pursuer.isDescending
                            ? "Heavy footsteps rattle the ladder from above..."
                            : "Heavy footsteps rattle the ladder from below...";
                    eventManager.addEvent(new GameEvent(warning, 2.5f));
                }
                if (soundManager != null) {
                    soundManager.playDoorCreak();
                }
            }

            pursuer.turnsRemaining--;

            if (pursuer.turnsRemaining <= 0) {
                // Check if player is in the matching target area
                boolean matches = false;
                if (pursuer.type == PursuitType.GATE) {
                    if (currentLevel == pursuer.targetLevel) {
                        if (pursuer.targetChunk == null || playerChunk == null || pursuer.targetChunk.equals(playerChunk)) {
                            matches = true;
                        }
                    }
                } else if (pursuer.type == PursuitType.LADDER) {
                    if (currentLevel == pursuer.targetLevel) {
                        matches = true;
                    }
                }

                if (matches) {
                    spawnPursuer(pursuer, currentMaze, player, soundManager, eventManager, combatManager);
                    iter.remove();
                }
            }
        }
    }

    private void spawnPursuer(PendingPursuer pursuer, Maze currentMaze, Player player, SoundManager soundManager,
                              GameEventManager eventManager, CombatManager combatManager) {
        Monster monster = pursuer.monster;
        if (monster == null) return;

        GridPoint2 arrivalTile = pursuer.arrivalTile;
        GridPoint2 playerPos = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);

        // Find best spawn tile
        GridPoint2 spawnPos = findBestSpawnTile(currentMaze, arrivalTile, playerPos);

        // Anti-Cheese Shove: If no free tile is found and player blocks the arrival tile
        if (spawnPos == null) {
            if (playerPos.equals(arrivalTile)) {
                // Force shove player to an adjacent tile
                GridPoint2 shovePos = findAdjacentOpenTile(currentMaze, playerPos, null);
                if (shovePos != null) {
                    player.getPosition().set(shovePos.x + 0.5f, shovePos.y + 0.5f);
                    spawnPos = new GridPoint2(arrivalTile);
                    if (eventManager != null) {
                        eventManager.addEvent(new GameEvent("The " + monster.getMonsterType()
                                + " shoves forcefully through the passage!", 2.5f));
                    }
                    if (combatManager != null) {
                        combatManager.monsterMeleeStrike(monster);
                    }
                } else {
                    spawnPos = new GridPoint2(arrivalTile);
                }
            } else {
                spawnPos = new GridPoint2(arrivalTile);
            }
        }

        // Place monster
        monster.getPosition().set(spawnPos.x + 0.5f, spawnPos.y + 0.5f);
        monster.setState(Monster.MonsterState.HUNTING);
        monster.setLastKnownTargetPos(new GridPoint2(playerPos));
        monster.setSearchTurnsRemaining(pursuer.searchTurnsRemaining > 0 ? pursuer.searchTurnsRemaining : 8);

        currentMaze.getMonsters().put(new GridPoint2(spawnPos), monster);

        // Feedback & Audio
        if (eventManager != null) {
            String arrivalMsg = (pursuer.type == PursuitType.GATE)
                    ? "The " + monster.getMonsterType() + " followed you through the gate!"
                    : "The " + monster.getMonsterType() + " scrambles down the ladder behind you!";
            eventManager.addEvent(new GameEvent(arrivalMsg, 3.0f));
        }

        if (soundManager != null) {
            soundManager.playDoorOpenSound();
        }
    }

    private GridPoint2 findBestSpawnTile(Maze maze, GridPoint2 arrivalTile, GridPoint2 playerPos) {
        if (isTileFree(maze, arrivalTile, playerPos)) {
            return new GridPoint2(arrivalTile);
        }

        return findAdjacentOpenTile(maze, arrivalTile, playerPos);
    }

    private GridPoint2 findAdjacentOpenTile(Maze maze, GridPoint2 center, GridPoint2 playerPos) {
        for (Direction dir : Direction.values()) {
            int nx = center.x + (int) dir.getVector().x;
            int ny = center.y + (int) dir.getVector().y;
            GridPoint2 cand = new GridPoint2(nx, ny);
            if (isTileFree(maze, cand, playerPos)) {
                return cand;
            }
        }
        return null;
    }

    private boolean isTileFree(Maze maze, GridPoint2 pt, GridPoint2 playerPos) {
        if (pt.x < 0 || pt.x >= maze.getWidth() || pt.y < 0 || pt.y >= maze.getHeight()) return false;
        if (playerPos != null && pt.equals(playerPos)) return false;
        if (maze.getMonsters().containsKey(pt)) return false;
        if (maze.getWallDataAt(pt.x, pt.y) == 1) return false;

        Scenery s = maze.getScenery().get(pt);
        if (s != null && s.isImpassable()) return false;

        if (maze.getHomeTiles() != null && maze.getHomeTiles().contains(pt)) return false;

        return true;
    }

    public List<com.bpm.minotaur.gamedata.save.WorldSaveData.PendingPursuerSaveData> toSaveData() {
        List<com.bpm.minotaur.gamedata.save.WorldSaveData.PendingPursuerSaveData> list = new ArrayList<>();
        for (PendingPursuer p : pendingPursuers) {
            if (p.monster == null) continue;
            com.bpm.minotaur.gamedata.save.WorldSaveData.PendingPursuerSaveData d = new com.bpm.minotaur.gamedata.save.WorldSaveData.PendingPursuerSaveData();
            d.monsterType = p.monster.getType();
            d.color = p.monster.getMonsterColor();
            d.currentHP = p.monster.getCurrentHP();
            d.currentMP = p.monster.getCurrentMP();
            d.pursuitType = p.type.name();
            d.originLevel = p.originLevel;
            d.targetLevel = p.targetLevel;
            if (p.targetChunk != null) {
                d.targetChunkX = p.targetChunk.x;
                d.targetChunkY = p.targetChunk.y;
            }
            if (p.arrivalTile != null) {
                d.arrivalTileX = p.arrivalTile.x;
                d.arrivalTileY = p.arrivalTile.y;
            }
            d.turnsRemaining = p.turnsRemaining;
            d.searchTurnsRemaining = p.searchTurnsRemaining;
            d.isDescending = p.isDescending;
            d.warned = p.warned;
            list.add(d);
        }
        return list;
    }

    public void loadFromSaveData(List<com.bpm.minotaur.gamedata.save.WorldSaveData.PendingPursuerSaveData> list,
                                 MonsterDataManager dataManager, AssetManager assetManager) {
        pendingPursuers.clear();
        if (list == null) return;
        for (com.bpm.minotaur.gamedata.save.WorldSaveData.PendingPursuerSaveData d : list) {
            if (d.monsterType == null) continue;
            Monster m = new Monster(d.monsterType, d.arrivalTileX, d.arrivalTileY, d.color, dataManager, assetManager);
            m.setCurrentHP(d.currentHP);
            m.setCurrentMP(d.currentMP);
            PendingPursuer p = new PendingPursuer();
            p.monster = m;
            p.type = "GATE".equalsIgnoreCase(d.pursuitType) ? PursuitType.GATE : PursuitType.LADDER;
            p.originLevel = d.originLevel;
            p.targetLevel = d.targetLevel;
            p.targetChunk = new GridPoint2(d.targetChunkX, d.targetChunkY);
            p.arrivalTile = new GridPoint2(d.arrivalTileX, d.arrivalTileY);
            p.turnsRemaining = d.turnsRemaining;
            p.searchTurnsRemaining = d.searchTurnsRemaining;
            p.isDescending = d.isDescending;
            p.warned = d.warned;
            pendingPursuers.add(p);
        }
    }
}
