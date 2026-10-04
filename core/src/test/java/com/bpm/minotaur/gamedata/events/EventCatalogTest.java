package com.bpm.minotaur.gamedata.events;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EventCatalogTest {

    private static final String JSON = "{ \"events\": ["
            + "{ \"id\": \"FOREST_ONLY\", \"biomes\": [\"FOREST\"], \"maxDepth\": 1 },"
            + "{ \"id\": \"MAZE_SHALLOW\", \"biomes\": [\"MAZE\"], \"maxDepth\": 3 },"
            + "{ \"id\": \"MAZE_DEEP\", \"biomes\": [\"MAZE\"], \"minDepth\": 4 },"
            + "{ \"id\": \"EVERYWHERE\", \"biomes\": [\"MAZE\", \"FOREST\"], \"weight\": 3,"
            + "  \"choices\": [ { \"text\": \"Leave.\" } ] }"
            + "] }";

    private final EventCatalog catalog = EventCatalog.fromJson(JSON);

    @Test
    public void loadsEveryEventById() {
        assertEquals(4, catalog.all().size());
        EventDefinition everywhere = catalog.get("EVERYWHERE");
        assertNotNull(everywhere);
        assertEquals(3, everywhere.weight);
        assertEquals("Leave.", everywhere.choices.get(0).text);
    }

    @Test
    public void eligibleFiltersByBiomeAndDepth() {
        assertEquals(ids("MAZE_SHALLOW", "EVERYWHERE"), idsOf(catalog.eligible("MAZE", 2, Collections.emptySet())));
        assertEquals(ids("MAZE_DEEP", "EVERYWHERE"), idsOf(catalog.eligible("MAZE", 6, Collections.emptySet())));
        assertEquals(ids("FOREST_ONLY", "EVERYWHERE"), idsOf(catalog.eligible("FOREST", 1, Collections.emptySet())));
        assertTrue(catalog.eligible("DESERT", 1, Collections.emptySet()).isEmpty());
    }

    @Test
    public void eligibleSkipsEventsAlreadySeenThisRun() {
        assertEquals(ids("MAZE_SHALLOW"), idsOf(catalog.eligible("MAZE", 1, ids("EVERYWHERE"))));
    }

    @Test
    public void pickReturnsNullWhenNothingIsEligible() {
        assertNull(catalog.pick("DESERT", 1, Collections.emptySet(), new Random(1)));
        assertNull(catalog.pick("MAZE", 1, ids("MAZE_SHALLOW", "EVERYWHERE"), new Random(1)));
    }

    @Test
    public void pickHonoursWeight() {
        Random rng = new Random(42);
        int everywhere = 0;
        int trials = 4000;
        for (int i = 0; i < trials; i++) {
            if ("EVERYWHERE".equals(catalog.pick("MAZE", 2, Collections.emptySet(), rng).id)) {
                everywhere++;
            }
        }
        // Weight 3 against weight 1: three in four.
        float share = everywhere / (float) trials;
        assertTrue("share was " + share, share > 0.70f && share < 0.80f);
    }

    private static Set<String> ids(String... ids) {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, ids);
        return set;
    }

    private static Set<String> idsOf(List<EventDefinition> defs) {
        Set<String> set = new HashSet<>();
        for (EventDefinition d : defs) {
            set.add(d.id);
        }
        return set;
    }
}
