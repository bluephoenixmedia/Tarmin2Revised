package com.bpm.minotaur.gamedata.progression;

import com.bpm.minotaur.gamedata.item.Item;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ArchiveLecternStationTest {

    private ShelterAltar altar;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
        altar = ShelterAltar.getInstance();
        altar.reset();
    }

    @Test
    public void testArchiveLecternStationProperties() {
        ShelterAltar.Station station = ShelterAltar.Station.ARCHIVE_LECTERN;
        assertNotNull("ARCHIVE_LECTERN station must exist", station);
        assertEquals("Archive Lectern", station.getDisplayName());
        assertEquals(15, station.getCost());
        assertEquals(Item.ItemType.HOME_ARCHIVE_LECTERN, station.getItemType());
        assertNull("Archive Lectern has no portable counterpart", station.getPortableKit());
    }

    @Test
    public void testArchiveLecternUnlockState() {
        assertFalse("Archive Lectern should start locked after reset",
                altar.hasStation(ShelterAltar.Station.ARCHIVE_LECTERN));

        altar.setStationUnlocked(ShelterAltar.Station.ARCHIVE_LECTERN, true);
        assertTrue("Archive Lectern should be unlocked",
                altar.hasStation(ShelterAltar.Station.ARCHIVE_LECTERN));

        altar.setStationUnlocked(ShelterAltar.Station.ARCHIVE_LECTERN, false);
        assertFalse("Archive Lectern should be locked again",
                altar.hasStation(ShelterAltar.Station.ARCHIVE_LECTERN));
    }

    @Test
    public void testWarTableDoesNotAppearWhenLecternLocked() {
        com.bpm.minotaur.generation.MazeChunkGenerator gen = new com.bpm.minotaur.generation.MazeChunkGenerator();
        com.bpm.minotaur.gamedata.item.ItemDataManager idm = new com.bpm.minotaur.gamedata.item.ItemDataManager() {
            @Override
            public Item createItem(Item.ItemType type, int x, int y,
                                   com.bpm.minotaur.gamedata.item.ItemColor color,
                                   com.badlogic.gdx.assets.AssetManager assetManager) {
                com.bpm.minotaur.gamedata.item.ItemTemplate t = new com.bpm.minotaur.gamedata.item.ItemTemplate();
                t.friendlyName = type.name();
                t.baseValue = 50;
                Item item = Item.fromTemplate(type, t);
                item.setPosition(x, y);
                return item;
            }
        };

        com.bpm.minotaur.gamedata.Maze maze = gen.generateChunk(new com.badlogic.gdx.math.GridPoint2(0, 0), 1, 1,
                com.bpm.minotaur.gamedata.Difficulty.EASY, com.bpm.minotaur.gamedata.GameMode.ADVANCED,
                null, null, null, idm, null, null, null, 12345L, 0);

        assertFalse(altar.hasStation(ShelterAltar.Station.ARCHIVE_LECTERN));
        altar.furnish(maze, idm, null);

        boolean hasWarTable = false;
        boolean hasLectern = false;
        for (Item item : maze.getItems().values()) {
            if (item.getType() == Item.ItemType.HOME_WAR_TABLE) hasWarTable = true;
            if (item.getType() == Item.ItemType.HOME_ARCHIVE_LECTERN) hasLectern = true;
        }

        assertFalse("War Table must NOT appear in shelter on a new game before Archive Lectern is unlocked", hasWarTable);
        assertFalse("Archive Lectern must NOT appear in shelter on a new game before unlocked", hasLectern);
    }

    @Test
    public void testWarTableAppearsWhenLecternUnlocked() {
        com.bpm.minotaur.generation.MazeChunkGenerator gen = new com.bpm.minotaur.generation.MazeChunkGenerator();
        com.bpm.minotaur.gamedata.item.ItemDataManager idm = new com.bpm.minotaur.gamedata.item.ItemDataManager() {
            @Override
            public Item createItem(Item.ItemType type, int x, int y,
                                   com.bpm.minotaur.gamedata.item.ItemColor color,
                                   com.badlogic.gdx.assets.AssetManager assetManager) {
                com.bpm.minotaur.gamedata.item.ItemTemplate t = new com.bpm.minotaur.gamedata.item.ItemTemplate();
                t.friendlyName = type.name();
                t.baseValue = 50;
                Item item = Item.fromTemplate(type, t);
                item.setPosition(x, y);
                return item;
            }
        };

        com.bpm.minotaur.gamedata.Maze maze = gen.generateChunk(new com.badlogic.gdx.math.GridPoint2(0, 0), 1, 1,
                com.bpm.minotaur.gamedata.Difficulty.EASY, com.bpm.minotaur.gamedata.GameMode.ADVANCED,
                null, null, null, idm, null, null, null, 12345L, 0);

        altar.setStationUnlocked(ShelterAltar.Station.ARCHIVE_LECTERN, true);
        altar.furnish(maze, idm, null);

        boolean hasWarTable = false;
        boolean hasLectern = false;
        for (Item item : maze.getItems().values()) {
            if (item.getType() == Item.ItemType.HOME_WAR_TABLE) hasWarTable = true;
            if (item.getType() == Item.ItemType.HOME_ARCHIVE_LECTERN) hasLectern = true;
        }

        assertTrue("Archive Lectern must appear when unlocked and furnished", hasLectern);
        assertTrue("War Table must appear beside Archive Lectern when unlocked and furnished", hasWarTable);
    }
}
