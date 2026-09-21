package com.bpm.minotaur.gamedata.firearm;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster;

/**
 * The report of a firearm, and what it wakes.
 *
 * <p>This is the cost that makes firing a decision rather than a rotation: you trade one
 * dead enemy for several awake ones. Without it the drawback of a black-powder weapon is
 * imaginary, and a 2d8 shot at bow range simply retires the bow.
 *
 * <p>Noise deliberately ignores line of sight. Sound goes through walls; that is what
 * separates it from being seen, and it is why the radius is set beyond the 8-tile sight
 * range -- a gun only the things already looking at you can hear costs nothing.
 */
public final class GunshotNoise {

    private GunshotNoise() {
    }

    /**
     * Wakes everything within earshot of a shot and points it at the source.
     *
     * @return how many monsters this shot newly roused, for the caller to report.
     */
    public static int wake(Maze maze, Vector2 origin, Item.ItemType weaponType) {
        if (maze == null || origin == null) {
            return 0;
        }

        int radius = FirearmProfile.noiseRadius(weaponType);
        if (radius <= 0) {
            return 0;
        }

        GridPoint2 shotTile = new GridPoint2((int) origin.x, (int) origin.y);
        float radiusSq = (float) radius * radius;
        int woken = 0;

        for (Monster monster : maze.getMonsters().values()) {
            if (monster == null || monster.getState() == Monster.MonsterState.HUNTING) {
                continue;
            }
            if (monster.getPosition().dst2(origin) > radiusSq) {
                continue;
            }

            monster.setState(Monster.MonsterState.HUNTING);
            monster.setLastKnownTargetPos(new GridPoint2(shotTile));
            woken++;
        }

        return woken;
    }
}
