package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;

/**
 * Moving a monster against its will: the knockback of Battering Ram and the pull of Reel In
 * (#242). Heavies and elites hold their ground; walls, the map's edge and other creatures stop it.
 */
public final class ForcedMovement {

    /** What became of the attempt. HIT_WALL is the impact Battering Ram punishes. */
    public enum Result { MOVED, HIT_WALL, BLOCKED, IMMOVABLE }

    /** A pull reaches this many tiles: a monster two tiles off is drawn into reach. */
    public static final int PULL_TILES = 2;

    private ForcedMovement() {
    }

    /** Knocks {@code monster} one tile in {@code direction}, away from the player. */
    public static Result knockBack(Maze maze, Monster monster, Direction direction, GridPoint2 player) {
        if (monster.isImmovable()) return Result.IMMOVABLE;
        return step(maze, monster, direction, player);
    }

    /**
     * Pulls {@code monster}, in a straight line with the player, up to {@link #PULL_TILES} tiles
     * toward them, stopping beside the player rather than on them.
     */
    public static Result pull(Maze maze, Monster monster, GridPoint2 player) {
        if (monster.isImmovable()) return Result.IMMOVABLE;
        GridPoint2 from = tileOf(monster);
        Direction toward = Direction.fromDelta(Integer.signum(player.x - from.x), Integer.signum(player.y - from.y));
        if (toward == null) return Result.BLOCKED; // not in a straight line
        Result result = Result.BLOCKED;
        for (int i = 0; i < PULL_TILES; i++) {
            GridPoint2 here = tileOf(monster);
            if (Math.abs(player.x - here.x) + Math.abs(player.y - here.y) <= 1) break; // already in reach
            Result stepped = step(maze, monster, toward, player);
            if (stepped != Result.MOVED) return i == 0 ? stepped : Result.MOVED;
            result = Result.MOVED;
        }
        return result;
    }

    private static Result step(Maze maze, Monster monster, Direction direction, GridPoint2 player) {
        GridPoint2 from = tileOf(monster);
        int toX = from.x + (int) direction.getVector().x;
        int toY = from.y + (int) direction.getVector().y;
        if (toX < 0 || toY < 0 || toX >= maze.getWidth() || toY >= maze.getHeight()
                || maze.isWallBlocking(from.x, from.y, direction)) {
            return Result.HIT_WALL;
        }
        GridPoint2 to = new GridPoint2(toX, toY);
        if (to.equals(player) || maze.getMonsters().containsKey(to) || !monster.mayStandAt(toX, toY)) {
            return Result.BLOCKED;
        }
        maze.moveMonster(monster, toX, toY); // keeps the map key and position together, and leaves the blood trail
        return Result.MOVED;
    }

    private static GridPoint2 tileOf(Monster monster) {
        return new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y);
    }
}
