package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;

import static org.junit.Assert.*;

public class DirectionWallBlockingTest {

    @Test
    public void testDirectionFromDelta() {
        assertEquals(Direction.NORTH, Direction.fromDelta(0, 1));
        assertEquals(Direction.SOUTH, Direction.fromDelta(0, -1));
        assertEquals(Direction.EAST, Direction.fromDelta(1, 0));
        assertEquals(Direction.WEST, Direction.fromDelta(-1, 0));
    }

    @Test
    public void testWallBlockingPreventsMelee() {
        Maze maze = new Maze(1, new int[5][5]);
        // Place a North wall at (2, 2)
        maze.setTile(2, 2, Direction.NORTH.getWallMask());

        // Target is at (2, 3) directly north of player at (2, 2)
        Direction dir = Direction.fromDelta(2 - 2, 3 - 2);
        assertEquals(Direction.NORTH, dir);
        assertTrue("Wall must block melee bump attack through north wall", maze.isWallBlocking(2, 2, dir));
    }

    @Test
    public void testArcaneWarBookDamageDice() throws Exception {
        File itemsFile = new File("../assets/data/items.json");
        if (!itemsFile.exists()) {
            itemsFile = new File("assets/data/items.json");
        }
        assertTrue("items.json exists", itemsFile.exists());

        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(new FileReader(itemsFile));

        JsonValue warBook = root.get("WAR_BOOK");
        assertNotNull("WAR_BOOK found in items.json", warBook);
        assertEquals("War Book must have 1d8 damage dice", "1d8", warBook.getString("damageDice"));
    }

    @Test
    public void testTemporaryHpMechanics() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);
        stats.setCurrentHP(20);
        stats.setMaxHP(20);

        stats.addTemporaryHP(10);
        assertEquals(10, stats.getTemporaryHP());

        // Taking 4 damage: absorbs 4 temp HP, current HP remains 20
        stats.setCurrentHP(stats.getCurrentHP() - 4);
        assertEquals(6, stats.getTemporaryHP());
        assertEquals(20, stats.getCurrentHP());

        // Taking 10 damage: absorbs remaining 6 temp HP, takes 4 real HP damage
        stats.setCurrentHP(stats.getCurrentHP() - 10);
        assertEquals(0, stats.getTemporaryHP());
        assertEquals(16, stats.getCurrentHP());
    }
}
