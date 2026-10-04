package com.bpm.minotaur.gamedata.events;

import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.laser.LaserBurst;
import com.bpm.minotaur.gamedata.monster.Monster;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a choice event may fire where the player stands.
 *
 * <p>A scene should not open with something chasing the player, so a tile stays armed while any
 * hostile is close ({@link #NEAR} tiles, sight or no sight) or can see the player from up to
 * {@link #SIGHT} tiles away. It fires the next time the player steps on it in the clear.
 */
public final class EventTriggerRule {

    static final int NEAR = 4;
    static final int SIGHT = 8;

    /** A hostile's tile and whether it has line of sight to the player. */
    public static final class Threat {
        final int x;
        final int y;
        final boolean hasLineOfSight;

        public Threat(int x, int y, boolean hasLineOfSight) {
            this.x = x;
            this.y = y;
            this.hasLineOfSight = hasLineOfSight;
        }
    }

    private EventTriggerRule() {
    }

    public static boolean isClear(int px, int py, List<Threat> threats) {
        for (Threat t : threats) {
            int distance = Math.max(Math.abs(t.x - px), Math.abs(t.y - py));
            if (distance <= NEAR) {
                return false;
            }
            if (distance <= SIGHT && t.hasLineOfSight) {
                return false;
            }
        }
        return true;
    }

    /** Applies the rule to the living, non-allied monsters of a maze. */
    public static boolean isClear(Maze maze, int px, int py) {
        List<Threat> threats = new ArrayList<>();
        Vector2 from = new Vector2(px + 0.5f, py + 0.5f);
        for (Monster m : maze.getMonsters().values()) {
            if (m == null || !m.isAlive() || m.isAlly()) {
                continue;
            }
            int mx = (int) m.getPosition().x;
            int my = (int) m.getPosition().y;
            if (Math.max(Math.abs(mx - px), Math.abs(my - py)) > SIGHT) {
                continue;
            }
            boolean los = LaserBurst.hasLineOfSight(maze, from, new Vector2(mx + 0.5f, my + 0.5f));
            threats.add(new Threat(mx, my, los));
        }
        return isClear(px, py, threats);
    }
}
