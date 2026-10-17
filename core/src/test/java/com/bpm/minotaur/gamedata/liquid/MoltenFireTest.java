package com.bpm.minotaur.gamedata.liquid;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Test;

import static org.junit.Assert.*;

/** The Bridge of Souls' chasm: molten fire nothing can cross, and a keeper who will not leave his island. */
public class MoltenFireTest {

    @Test
    public void moltenFireIsTheOnlyLiquidNothingCanWadeInto() {
        for (LiquidType t : LiquidType.values()) {
            assertEquals(t.name(), t == LiquidType.MOLTEN_FIRE, t.isImpassable());
        }
    }

    @Test
    public void theMazeWillNotLetAnythingStepIntoTheFireAndTheChunkRemembersIt() {
        Maze maze = new Maze(1, new int[8][8]);
        LiquidManager liquids = new LiquidManager();
        maze.setLiquidManager(liquids);
        assertTrue(maze.isPassable(3, 3));
        liquids.setLiquidAt(3, 3, LiquidType.MOLTEN_FIRE);
        assertFalse(maze.isPassable(3, 3));
        assertTrue("water is still wadeable", maze.isPassable(4, 4));
        assertEquals(LiquidType.MOLTEN_FIRE, LiquidManager.deserialize(liquids.serialize()).getLiquidAt(3, 3));
    }

    @Test
    public void aTetheredMonsterMayOnlyStandWithinItsBounds() {
        Monster keeper = new Monster(Monster.MonsterType.HOBGOBLIN, 4, 4);
        assertTrue("untethered, anywhere", keeper.mayStandAt(30, 30));
        keeper.setTether(2, 2, 6, 6);
        assertTrue(keeper.mayStandAt(6, 2));
        assertFalse(keeper.mayStandAt(7, 4));
        assertFalse(keeper.mayStandAt(4, 1));
    }
}
