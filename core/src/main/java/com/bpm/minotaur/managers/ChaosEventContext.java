package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;

/**
 * Context passed to {@link ChaosSurgeEvent} hooks during activation, turn ticks,
 * and expiration.
 */
public class ChaosEventContext {

    public final Maze maze;
    public final Player player;
    public final WorldManager worldManager;
    public final GameEventManager eventManager;
    public final int turnsRemaining;
    public final int totalDuration;

    public ChaosEventContext(Maze maze, Player player, WorldManager worldManager,
                             GameEventManager eventManager, int turnsRemaining, int totalDuration) {
        this.maze = maze;
        this.player = player;
        this.worldManager = worldManager;
        this.eventManager = eventManager;
        this.turnsRemaining = turnsRemaining;
        this.totalDuration = totalDuration;
    }
}
