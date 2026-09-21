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
}
