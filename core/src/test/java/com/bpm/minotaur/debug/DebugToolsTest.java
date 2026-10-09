package com.bpm.minotaur.debug;

import com.badlogic.gdx.Input;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.ShelterChest;
import com.bpm.minotaur.gamedata.player.Player;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class DebugToolsTest {

    private Player player;

    @Before
    public void setUp() {
        player = new Player(0, 0);
    }

    @Test
    public void debugKeysDoNothingUnlessDebugModeIsOn() {
        for (DebugKeys.Entry e : DebugKeys.DEBUG_ONLY) {
            assertNull(DebugKeys.actionFor(e.keycode, false));
            assertEquals(e.action, DebugKeys.actionFor(e.keycode, true));
        }
        assertNull(DebugKeys.actionFor(Input.Keys.W, true));
    }

    @Test
    public void everyDebugKeyIsUniqueAndAppearsInTheLegend() {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        String legend = String.join("\n", DebugKeys.legend(true));
        for (DebugKeys.Entry e : DebugKeys.DEBUG_ONLY) {
            assertTrue("duplicate key " + e.keyName, seen.add(e.keycode));
            assertTrue(legend.contains(e.keyName));
            assertTrue(legend.contains(e.description));
        }
    }

    @Test
    public void theChoiceEventKeyPicksAnEventThatFitsThePlace() {
        com.bpm.minotaur.gamedata.events.EventCatalog catalog = com.bpm.minotaur.gamedata.events.EventCatalog.fromJson(
                "{ \"events\": [ { \"id\": \"DEEP\", \"biomes\": [\"MAZE\"], \"minDepth\": 4 } ] }");
        assertEquals("DEEP", DebugCheats.choiceEventFor(catalog, "MAZE", 5, new java.util.Random(1)).id);
        assertNull(DebugCheats.choiceEventFor(catalog, "MAZE", 1, new java.util.Random(1)));
        assertNull(DebugCheats.choiceEventFor(catalog, "FOREST", 5, new java.util.Random(1)));
    }

    @Test
    public void theLegendSaysWhetherDebugModeIsOn() {
        assertTrue(DebugKeys.legend(true).get(0).contains("ON"));
        assertTrue(DebugKeys.legend(false).get(0).contains("OFF"));
    }

    @Test
    public void learnAllSpellsTeachesOnlyWhatIsMissing() {
        assertEquals(3, DebugCheats.learnAllSpells(player, Arrays.asList("fireball", "haste", "shield")));
        assertTrue(player.getKnownSpellIds().contains("FIREBALL"));
        assertEquals(1, DebugCheats.learnAllSpells(player, Arrays.asList("fireball", "bless")));
    }

    @Test
    public void levelUpGivesExactlyOneLevelAndRaisesTheModal() {
        int before = player.getStats().getLevel();
        DebugCheats.levelUp(player, null);
        assertEquals(before + 1, player.getStats().getLevel());
        assertTrue(player.hasPendingLevelUpModal());
        DebugCheats.levelUp(player, null);
        assertEquals(before + 2, player.getStats().getLevel());
    }

    @Test
    public void refillRestoresVitals() {
        player.getStats().setCurrentHP(1);
        player.getStats().setCurrentMP(0);
        player.getStats().setSatiety(0f);
        player.getStats().setHydration(0f);
        DebugCheats.refill(player);
        assertEquals(player.getStats().getMaxHP(), player.getStats().getCurrentHP());
        assertEquals(player.getStats().getMaxMP(), player.getStats().getCurrentMP());
        assertTrue(player.getStats().getSatietyFloat() > 25f);
        assertTrue(player.getStats().getHydration() > 0f);
    }

    private static Item make(Item.ItemType type) {
        return Item.fromTemplate(type, new ItemTemplate());
    }

    @Test
    public void theCatalogChestKeepsEveryItemWhateverIsTakenAndNeverSaves() {
        List<Item.ItemType> types = Arrays.asList(Item.ItemType.SWORD, Item.ItemType.FOOD, Item.ItemType.BRASS_LANTERN);
        ShelterChest chest = DebugCheats.allItemsChest(types, DebugToolsTest::make);
        assertTrue(chest.isCatalog());
        assertEquals(3, chest.getItemCount());

        Item taken = chest.getItems().get(1);
        assertTrue(chest.removeItem(taken));
        assertEquals("a fresh copy replaces it", 3, chest.getItemCount());
        assertNotSame(taken, chest.getItems().get(1));
        assertEquals(taken.getType(), chest.getItems().get(1).getType());

        chest.save(); // must be a no-op: there is no active save slot in this test, so a real save would throw or log
    }

    @Test
    public void theShelterRoadCheatsCycleAlongTheRoads() {
        com.bpm.minotaur.generation.ShelterRoads roads = new com.bpm.minotaur.managers.BiomeManager(5L).getRoads();
        java.util.List<com.badlogic.gdx.math.GridPoint2> stops = roads.getRoad(0).getShelters();
        assertEquals(stops.get(0), DebugCheats.roadShelter(roads, 0, 0));
        assertEquals("wraps to the start", stops.get(0), DebugCheats.roadShelter(roads, 0, stops.size()));
        assertNull(DebugCheats.roadShelter(null, 0, 0));
        assertEquals(1, DebugCheats.sealRoad(0));
        assertEquals(3, DebugCheats.sealRoad(2));
        assertEquals(1, DebugCheats.sealRoad(3));
        assertNotNull(DebugKeys.actionFor(Input.Keys.SEMICOLON, true));
        assertNull("debug-only", DebugKeys.actionFor(Input.Keys.SEMICOLON, false));
    }
}
