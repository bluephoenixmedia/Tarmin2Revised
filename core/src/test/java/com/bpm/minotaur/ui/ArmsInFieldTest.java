package com.bpm.minotaur.ui;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.arms.HouseArms;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/** Living War A45-A46: who bears Arms in the field, how large, and in which colours. */
public class ArmsInFieldTest {

    private static Monster soldier(int house) {
        Monster m = new Monster(Monster.MonsterType.GHOUL, 100, 30, 5f, 5f);
        m.setHouseId(house);
        return m;
    }

    @Test
    public void aShieldShrinksTileByTileAsThePrototypeDecided() {
        assertEquals(32, ArmsInField.shieldSize(0.5f));
        assertEquals(32, ArmsInField.shieldSize(1f));
        assertEquals(24, ArmsInField.shieldSize(2f));
        assertEquals(17, ArmsInField.shieldSize(3f));
        assertEquals("no smaller while it fades", 17, ArmsInField.shieldSize(4f));
    }

    @Test
    public void aShieldIsWhollySeenToThreeTilesAndFadesBeyond() {
        assertEquals(1f, ArmsInField.shieldAlpha(1f), 0f);
        assertEquals(1f, ArmsInField.shieldAlpha(3f), 0f);
        float fading = ArmsInField.shieldAlpha(3.75f);
        assertTrue("fading: " + fading, fading > 0f && fading < 1f);
        assertEquals(0f, ArmsInField.shieldAlpha(4.5f), 0f);
        assertEquals("and stays gone", 0f, ArmsInField.shieldAlpha(30f), 0f);
    }

    @Test
    public void onlyAMonsterWithAHouseBearsArms() {
        assertTrue(ArmsInField.bearsArms(soldier(3)));
        assertFalse("a native", ArmsInField.bearsArms(soldier(-1)));
    }

    /** A guard or knight of the regular spawns is Legion by name alone, and has no house to show. */
    @Test
    public void aRegularLegionSpawnShowsNothing() {
        Monster guard = soldier(-1);
        guard.setFaction(Faction.TARMIN_LEGION);
        assertFalse(ArmsInField.bearsArms(guard));
    }

    @Test
    public void theDeadBearNoArms() {
        Monster fallen = soldier(3);
        fallen.setCurrentHP(0);
        assertFalse(ArmsInField.bearsArms(fallen));
    }

    @Test
    public void aBannerIsTheCaptainsAlone() {
        Monster captain = soldier(3);
        captain.setStandardBearer(true);
        assertTrue(ArmsInField.bearsBanner(captain));
        assertFalse(ArmsInField.bearsBanner(soldier(3)));
        Monster fallen = soldier(3);
        fallen.setStandardBearer(true);
        fallen.setCurrentHP(0);
        assertFalse(ArmsInField.bearsBanner(fallen));
    }

    @Test
    public void aCaptainStillCarriesTheStandardAfterTheChunkIsSaved() {
        Monster captain = soldier(3);
        captain.setStandardBearer(true);
        assertTrue(new ChunkData.MonsterData(captain).standardBearer);
        assertFalse(new ChunkData.MonsterData(soldier(3)).standardBearer);
    }

    /** No GL is touched until a texture is asked for, so the colours can be read here. */
    @Test
    public void minimapColoursAreTheTwoFieldsOfTheHousesArms() throws IOException {
        DoctrineCatalog catalog = DoctrineCatalogTest.loadCatalog();
        HistoryWorld world = HistorySimulator.prehistory(77L, catalog);
        int house = world.houses().get(0).id;
        HouseArms arms = HouseArms.of(world, house);
        Color[] colours = new ArmsAtlas(world, catalog).colours(house);
        assertEquals(HouseArms.rgb(arms.field), Color.rgba8888(colours[0]) >>> 8);
        assertEquals(HouseArms.rgb(arms.secondField), Color.rgba8888(colours[1]) >>> 8);
        assertNull("a native has none", new ArmsAtlas(world, catalog).colours(-1));
    }
}
