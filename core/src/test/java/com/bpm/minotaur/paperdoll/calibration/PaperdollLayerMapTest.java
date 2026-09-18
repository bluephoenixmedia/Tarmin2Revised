package com.bpm.minotaur.paperdoll.calibration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The layer map replaces the runtime name guessing in getCandidateNames(), which tried
 * an item's texture basename, then its enum name, then its display name slugified.
 * That guessing is why the baker had to emit duplicate alias PNGs under every name a
 * lookup might use -- 31 redundant copies across the slots.
 */
public class PaperdollLayerMapTest {

    private static final String SAMPLE =
            "{\"items\":{"
            + "\"BANDED_MAIL\":{\"slot\":\"chest\",\"layer\":\"banded\"},"
            + "\"RING_MAIL\":{\"slot\":\"chest\",\"layer\":\"chain_mail\"},"
            + "\"LAMELLAR_SHIRT\":{\"slot\":\"chest\",\"layer\":\"chain_mail\"},"
            + "\"BASINET\":{\"slot\":\"head\",\"layer\":\"bascinet\"}"
            + "}}";

    private PaperdollLayerMap loaded() {
        PaperdollLayerMap map = new PaperdollLayerMap();
        map.parse(SAMPLE);
        return map;
    }

    @Test
    public void resolvesAnItemToItsSlotAndLayer() {
        PaperdollLayerMap map = loaded();
        PaperdollLayerMap.LayerRef ref = map.resolve("BANDED_MAIL");
        assertEquals("chest", ref.slot);
        assertEquals("banded", ref.layer);
    }

    @Test
    public void severalItemsMayShareOneLayer() {
        // This is the point of the map: distinct items legitimately share artwork,
        // and they should share the layer file and its calibration rather than each
        // owning a byte-identical copy that can drift out of sync.
        PaperdollLayerMap map = loaded();
        assertEquals("chain_mail", map.resolve("RING_MAIL").layer);
        assertEquals("chain_mail", map.resolve("LAMELLAR_SHIRT").layer);
        assertEquals(map.resolve("RING_MAIL").layerId(), map.resolve("LAMELLAR_SHIRT").layerId());
    }

    @Test
    public void layerIdMatchesTheCalibrationStoreKey() {
        PaperdollLayerMap map = loaded();
        assertEquals(CalibrationStore.layerId("head", "bascinet"), map.resolve("BASINET").layerId());
    }

    @Test
    public void unmappedItemResolvesToNullSoTheCallerCanFallBack() {
        // Ammunition and projectiles have no paperdoll layer at all; that is a normal
        // state, not an error, and must not blow up the doll.
        PaperdollLayerMap map = loaded();
        assertNull(map.resolve("ARROW_WAR"));
        assertNull(map.resolve(null));
        assertFalse(map.has("ARROW_WAR"));
        assertTrue(map.has("BANDED_MAIL"));
    }

    @Test
    public void emptyOrAbsentFileLeavesTheMapUsable() {
        PaperdollLayerMap map = new PaperdollLayerMap();
        assertNull(map.resolve("BANDED_MAIL"));
        map.parse("");
        assertNull(map.resolve("BANDED_MAIL"));
        map.parse("{}");
        assertNull(map.resolve("BANDED_MAIL"));
        assertEquals(0, map.size());
    }

    @Test
    public void entriesMissingASlotOrLayerAreSkippedRatherThanHalfLoaded() {
        PaperdollLayerMap map = new PaperdollLayerMap();
        map.parse("{\"items\":{"
                + "\"GOOD\":{\"slot\":\"chest\",\"layer\":\"banded\"},"
                + "\"NO_LAYER\":{\"slot\":\"chest\"},"
                + "\"NO_SLOT\":{\"layer\":\"banded\"}"
                + "}}");
        assertEquals(1, map.size());
        assertNull(map.resolve("NO_LAYER"));
        assertNull(map.resolve("NO_SLOT"));
    }
}
