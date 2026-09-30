package com.bpm.minotaur.gamedata.spells;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;

/**
 * Whether a monster's spell can reach the player.
 *
 * <p>Monsters used to cast through walls. The AI's own sighting test never looked at the start or
 * end tile and only read whole-cell wall data, so a wall on the caster's edge did not stop it, and
 * the combat-mode cast had no test at all. Every spell now needs an unbroken line, walked edge by
 * edge and read from both sides of each edge (wall data in this maze format is not always written
 * symmetrically).
 *
 * <p>The one exception is deliberate and two-keyed: a spell that is itself wall-piercing (psychic,
 * or flagged {@code ignoresLineOfSight}) cast by a caster that is one (tagged, or of extreme
 * intelligence). Either key alone changes nothing, and a pierced cast is reported as such so the
 * player is told rather than left to wonder whether it was a bug.
 */
public final class MonsterSpellSight {

    /** Intelligence at which a monster's mind reaches through stone on its own. */
    public static final int EXTREME_INTELLIGENCE = 22;

    public enum Reach {
        /** An unbroken line. */
        CLEAR,
        /** Blocked, but the spell and the caster are both wall-piercing. */
        THROUGH_WALLS,
        /** Blocked, and nothing overrides it. */
        BLOCKED;

        public boolean canCast() {
            return this != BLOCKED;
        }
    }

    private MonsterSpellSight() {
    }

    /** Assess a cast by a real monster with a real spell. A null spell is treated as an ordinary one. */
    public static Reach assess(Maze maze, Monster caster, Vector2 target, SpellTemplate spell) {
        boolean spellPierces = spell != null && spell.pierces();
        boolean casterPierces = caster != null && caster.canCastThroughWalls();
        Vector2 from = (caster != null) ? caster.getPosition() : target;
        return assess(maze, from, target, spellPierces, casterPierces);
    }

    public static Reach assess(Maze maze, Vector2 from, Vector2 to, boolean spellPierces, boolean casterPierces) {
        if (hasClearLine(maze, (int) Math.floor(from.x), (int) Math.floor(from.y),
                (int) Math.floor(to.x), (int) Math.floor(to.y))) {
            return Reach.CLEAR;
        }
        return (spellPierces && casterPierces) ? Reach.THROUGH_WALLS : Reach.BLOCKED;
    }

    /** True when nothing solid, closed or fogged lies between the two tiles. */
    public static boolean hasClearLine(Maze maze, int x0, int y0, int x1, int y1) {
        if (maze == null) {
            return true;
        }
        if (maze.fogBlocksSight(x0, y0, x1, y1)) {
            return false;
        }

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int cx = x0;
        int cy = y0;
        while (cx != x1 || cy != y1) {
            int e2 = 2 * err;
            boolean stepX = e2 > -dy;
            boolean stepY = e2 < dx;
            int nx = cx;
            int ny = cy;
            if (stepX) {
                err -= dy;
                nx += sx;
            }
            if (stepY) {
                err += dx;
                ny += sy;
            }

            if (stepX && stepY) {
                // A diagonal step must not squeeze through a corner: one of the two ways round it
                // has to be open along both of its edges.
                boolean viaX = edgeOpen(maze, cx, cy, sx > 0 ? Direction.EAST : Direction.WEST)
                        && edgeOpen(maze, nx, cy, sy > 0 ? Direction.NORTH : Direction.SOUTH);
                boolean viaY = edgeOpen(maze, cx, cy, sy > 0 ? Direction.NORTH : Direction.SOUTH)
                        && edgeOpen(maze, cx, ny, sx > 0 ? Direction.EAST : Direction.WEST);
                if (!viaX && !viaY) {
                    return false;
                }
            } else if (stepX) {
                if (!edgeOpen(maze, cx, cy, sx > 0 ? Direction.EAST : Direction.WEST)) {
                    return false;
                }
            } else if (stepY) {
                if (!edgeOpen(maze, cx, cy, sy > 0 ? Direction.NORTH : Direction.SOUTH)) {
                    return false;
                }
            }
            cx = nx;
            cy = ny;
        }
        return true;
    }

    /** One orthogonal step from (x, y): read from both sides, and stopped by a closed door or gate. */
    private static boolean edgeOpen(Maze maze, int x, int y, Direction dir) {
        int nx = x + (int) dir.getVector().x;
        int ny = y + (int) dir.getVector().y;
        if (maze.isWallBlocking(x, y, dir) || maze.isWallBlocking(nx, ny, dir.getOpposite())) {
            return false;
        }
        Object obj = maze.getGameObjectAt(nx, ny);
        if (obj instanceof Door) {
            Door.DoorState state = ((Door) obj).getState();
            if (state != Door.DoorState.OPEN && state != Door.DoorState.OPENING) {
                return false;
            }
        }
        if (obj instanceof Gate && ((Gate) obj).getState() != Gate.GateState.OPEN) {
            return false;
        }
        return true;
    }

    /** Convenience for callers holding grid points. */
    public static Reach assess(Maze maze, Monster caster, GridPoint2 target, SpellTemplate spell) {
        return assess(maze, caster, new Vector2(target.x + 0.5f, target.y + 0.5f), spell);
    }
}
