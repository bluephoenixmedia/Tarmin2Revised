package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.laser.LaserBurst;

/**
 * Whether a hostile is in view of a point: a living, untamed, visible monster
 * within {@link #SIGHT_RANGE_TILES} with a clear line of sight, in any direction.
 * Gates calm-only actions such as changing Spell Slots and studying a Tome.
 */
public final class HostileSight {

    public static final int SIGHT_RANGE_TILES = 8;

    private HostileSight() {
    }

    public static boolean anyInView(Maze maze, Vector2 from) {
        if (maze == null || from == null) {
            return false;
        }
        for (Monster monster : maze.getMonsters().values()) {
            if (monster == null || !monster.isAlive() || monster.getTameness() > 0
                    || monster.isHidden() || monster.isInvisible()) {
                continue;
            }
            Vector2 at = monster.getPosition();
            if (from.dst(at) <= SIGHT_RANGE_TILES && LaserBurst.hasLineOfSight(maze, from, at)) {
                return true;
            }
        }
        return false;
    }
}
