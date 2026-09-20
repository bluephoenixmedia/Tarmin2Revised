package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The moment the disguise drops: the chest Item leaves the maze and a live Monster
 * takes its tile. Items and monsters live in separate maps keyed by tile, so getting
 * this swap wrong leaves either a ghost chest or an unreachable monster.
 */
public class MimicRevealTest {

    private static final GridPoint2 TILE = new GridPoint2(4, 6);

    private static Maze openRoom() {
        return new Maze(1, new int[12][12]);
    }

    private static Item mimicChestAt(int x, int y) {
        Item chest = new Item(Item.ItemType.REGULAR_CHEST, x, y, null, null, null);
        chest.setMimic(true);
        return chest;
    }

    private static Monster mimicMonster() {
        return new Monster(Monster.MonsterType.MIMIC, 0, 0, 50, 0);
    }

    @Test
    public void testRevealRemovesTheChestFromTheMaze() {
        Maze maze = openRoom();
        maze.addItem(mimicChestAt(TILE.x, TILE.y));
        assertNotNull(maze.getItems().get(TILE));

        MimicReveal.swap(maze, TILE, mimicMonster());

        assertNull("the chest must not linger once it has stood up", maze.getItems().get(TILE));
    }

    @Test
    public void testRevealPlacesTheMonsterOnTheChestTile() {
        Maze maze = openRoom();
        maze.addItem(mimicChestAt(TILE.x, TILE.y));

        Monster mimic = mimicMonster();
        MimicReveal.swap(maze, TILE, mimic);

        assertSame(mimic, maze.getMonsters().get(TILE));
        assertEquals(TILE.x + 0.5f, mimic.getPosition().x, 0.001f);
        assertEquals(TILE.y + 0.5f, mimic.getPosition().y, 0.001f);
    }

    @Test
    public void testRevealedTileBecomesImpassable() {
        Maze maze = openRoom();
        maze.addItem(mimicChestAt(TILE.x, TILE.y));
        assertTrue("a chest can be walked over", maze.isPassable(TILE.x, TILE.y));

        MimicReveal.swap(maze, TILE, mimicMonster());

        assertFalse("a mimic cannot", maze.isPassable(TILE.x, TILE.y));
    }

    @Test
    public void testRevealIgnoresATileHoldingAnOrdinaryChest() {
        Maze maze = openRoom();
        Item ordinary = new Item(Item.ItemType.REGULAR_CHEST, TILE.x, TILE.y, null, null, null);
        maze.addItem(ordinary);

        assertFalse(MimicReveal.swap(maze, TILE, mimicMonster()));

        assertSame("an honest chest stays a chest", ordinary, maze.getItems().get(TILE));
        assertTrue(maze.getMonsters().isEmpty());
    }

    @Test
    public void testRevealIgnoresAnEmptyTile() {
        Maze maze = openRoom();
        assertFalse(MimicReveal.swap(maze, TILE, mimicMonster()));
        assertTrue(maze.getMonsters().isEmpty());
    }

    @Test
    public void testRevealIsNotRepeatable() {
        Maze maze = openRoom();
        maze.addItem(mimicChestAt(TILE.x, TILE.y));

        assertTrue(MimicReveal.swap(maze, TILE, mimicMonster()));

        Monster second = mimicMonster();
        assertFalse("the chest is gone; nothing left to reveal",
                MimicReveal.swap(maze, TILE, second));
        assertNotSame(second, maze.getMonsters().get(TILE));
    }

    @Test
    public void testRevealToleratesNulls() {
        assertFalse(MimicReveal.swap(null, TILE, mimicMonster()));
        assertFalse(MimicReveal.swap(openRoom(), null, mimicMonster()));
        assertFalse(MimicReveal.swap(openRoom(), TILE, null));
    }

    @Test
    public void testFindDisguisedMimicOnlyMatchesMimics() {
        Maze maze = openRoom();
        maze.addItem(new Item(Item.ItemType.REGULAR_CHEST, 1, 1, null, null, null));
        maze.addItem(mimicChestAt(TILE.x, TILE.y));

        assertNull(MimicReveal.disguisedMimicAt(maze, new GridPoint2(1, 1)));
        assertNotNull(MimicReveal.disguisedMimicAt(maze, TILE));
        assertNull(MimicReveal.disguisedMimicAt(maze, new GridPoint2(9, 9)));
        assertNull(MimicReveal.disguisedMimicAt(null, TILE));
    }
}
