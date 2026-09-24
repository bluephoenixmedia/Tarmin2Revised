package com.bpm.minotaur.gamedata.progression;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.WorldConstants;
import com.bpm.minotaur.managers.BiomeManager;
import org.junit.After;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * The shelter portals exist because the maze is 21x21 chunks and the wilderness
 * starts eleven chunks out. These pin the parts of that which can be asserted
 * without a graphics context.
 */
public class BiomePortalTest {

    @After
    public void tearDown() {
        BiomePortal.DEBUG_UNLOCK_ALL = false;
    }

    @Test
    public void everyPortalMapsToADistinctStationBiomeAndItem() {
        for (BiomePortal portal : BiomePortal.values()) {
            assertNotNull(portal + " has no station", portal.getStation());
            assertNotNull(portal + " has no destination biome", portal.getDestination());
            assertNotNull(portal + " has no item type", portal.getItemType());
            assertNotNull(portal + " has no tint", portal.getTint());

            assertSame("forStation must round-trip", portal, BiomePortal.forStation(portal.getStation()));
            assertSame("forItem must round-trip", portal, BiomePortal.forItem(portal.getItemType()));
        }

        for (BiomePortal a : BiomePortal.values()) {
            for (BiomePortal b : BiomePortal.values()) {
                if (a == b) continue;
                assertNotSame(a + " and " + b + " share a destination", a.getDestination(), b.getDestination());
                assertNotSame(a + " and " + b + " share an item type", a.getItemType(), b.getItemType());
            }
        }
    }

    @Test
    public void portalDestinationsAreReachableBiomes() {
        // A portal to a biome the world never generates would be a dead button.
        BiomeManager biomes = new BiomeManager();

        for (BiomePortal portal : BiomePortal.values()) {
            boolean found = false;
            outer:
            for (int r = 1; r <= 40 && !found; r++) {
                for (int x = -r; x <= r; x++) {
                    for (int y = -r; y <= r; y++) {
                        if (Math.max(Math.abs(x), Math.abs(y)) != r) continue;
                        if (biomes.getBiome(new GridPoint2(x, y)) == portal.getDestination()) {
                            found = true;
                            break outer;
                        }
                    }
                }
            }
            assertTrue(portal + " targets " + portal.getDestination()
                    + ", which the world never generates", found);
        }
    }

    @Test
    public void portalDestinationsAreNotImpassableBiomes() {
        // OCEAN and MOUNTAINS are flagged impassable, so warping into one would
        // strand the player inside terrain they cannot walk out of.
        for (BiomePortal portal : BiomePortal.values()) {
            assertNotSame(portal + " targets impassable OCEAN", Biome.OCEAN, portal.getDestination());
            assertNotSame(portal + " targets impassable MOUNTAINS", Biome.MOUNTAINS, portal.getDestination());
        }
    }

    @Test
    public void portalDestinationsLieOutsideTheMaze() {
        // The whole point is skipping the overland walk. A portal whose biome
        // sits inside the maze would save nothing.
        BiomeManager biomes = new BiomeManager();
        for (BiomePortal portal : BiomePortal.values()) {
            assertNotSame(portal + " targets the maze itself", Biome.MAZE, portal.getDestination());
        }
        assertEquals("Maze radius drives every distance assumption here",
                Biome.MAZE, biomes.getBiome(new GridPoint2(WorldConstants.CENTRAL_MAZE_RADIUS, 0)));
        assertNotSame("One chunk past the radius must leave the maze",
                Biome.MAZE, biomes.getBiome(new GridPoint2(WorldConstants.CENTRAL_MAZE_RADIUS + 1, 0)));
    }

    @Test
    public void portalStationsAreGatedOnDepthAndTheOthersAreNot() {
        for (BiomePortal portal : BiomePortal.values()) {
            assertTrue(portal + " must require a dungeon depth before it is offered",
                    portal.getStation().getRequiredDepth() > 0);
        }

        // Every pre-existing station must stay available from the start.
        for (ShelterAltar.Station station : ShelterAltar.Station.values()) {
            if (BiomePortal.forStation(station) != null) continue;
            assertEquals("Station " + station + " gained an unexpected depth gate",
                    0, station.getRequiredDepth());
            assertTrue(station + " must remain revealed from the start", station.isRevealed());
        }
    }

    @Test
    public void theDebugOverrideUnlocksEveryPortalWithoutTouchingTheSave() {
        BiomePortal.DEBUG_UNLOCK_ALL = false;
        int ownedBefore = ShelterAltar.getInstance().getUnlockedStations().size();

        BiomePortal.DEBUG_UNLOCK_ALL = true;
        for (BiomePortal portal : BiomePortal.values()) {
            assertTrue(portal + " must be usable under the debug override", portal.isAvailable());
            assertTrue(portal + " must be revealed under the debug override",
                    portal.getStation().isRevealed());
        }

        assertEquals("The debug override must never grant a real station",
                ownedBefore, ShelterAltar.getInstance().getUnlockedStations().size());
    }

    @Test
    public void theDebugOverrideDefaultsOffInSource() throws Exception {
        // A debug flag left on is how a cheat ships. Read the source rather than
        // the field, since a prior test may have toggled it.
        File src = new File("src/main/java/com/bpm/minotaur/gamedata/progression/BiomePortal.java");
        if (!src.exists()) {
            src = new File("core/src/main/java/com/bpm/minotaur/gamedata/progression/BiomePortal.java");
        }
        assertTrue("Could not locate BiomePortal source", src.exists());

        String source = new String(java.nio.file.Files.readAllBytes(src.toPath()), "UTF-8");
        assertTrue("DEBUG_UNLOCK_ALL must be declared false in source",
                source.contains("DEBUG_UNLOCK_ALL = false"));
    }

    @Test
    public void portalArtExistsOnDisk() {
        for (String path : new String[]{
                "images/portals/portal_vortex.png",
                "images/portals/portal_rune_circle.png",
                "images/portals/portal_vortex_still.png"}) {
            assertTrue("Missing portal art: " + path, assetExists(path));
        }
    }

    private boolean assetExists(String path) {
        return new File("assets/" + path).exists() || new File("../assets/" + path).exists();
    }
}
