package com.bpm.minotaur.rendering;

import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.rendering.attract.AttractActor;
import com.bpm.minotaur.rendering.attract.AttractWarDirector;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class AttractWarDirectorTest {

    private AttractWarDirector director;

    @Before
    public void setUp() {
        director = new AttractWarDirector();
    }

    @Test
    public void testWarringHousesInitialized() {
        assertNotNull("Defending house should be set", director.getDefendingHouse());
        assertNotNull("Invading house should be set", director.getInvadingHouse());
        assertNotEquals("Houses must be opposing rivals",
                director.getDefendingHouse().getId(),
                director.getInvadingHouse().getId());
        assertNotNull("Doctrine id must exist for defending house", director.getDefendingHouse().getDoctrineId());
        assertNotNull("Doctrine id must exist for invading house", director.getInvadingHouse().getDoctrineId());
    }

    @Test
    public void testSectorDistributionAndActorPopulations() {
        // All 5 sectors must have seeded actors
        for (int sector = 0; sector < 5; sector++) {
            List<AttractActor> actors = director.getActorsInSector(sector);
            assertFalse("Sector " + sector + " must have seeded actors", actors.isEmpty());
            assertTrue("Sector " + sector + " should have substantial actor count (>= 5)", actors.size() >= 5);
        }
    }

    @Test
    public void testCastleSiegeComposition() {
        // Sector 4 is Castle Tarmin Midnight Siege
        List<AttractActor> castleActors = director.getActorsInSector(4);
        boolean hasDefenders = false;
        boolean hasInvaders = false;
        boolean hasArchers = false;
        boolean hasMelee = false;
        boolean hasCampfireOrTorch = false;

        String defId = director.getDefendingHouse().getDoctrineId();
        String invId = director.getInvadingHouse().getDoctrineId();

        for (AttractActor a : castleActors) {
            if (defId.equals(a.getHouseId())) hasDefenders = true;
            if (invId.equals(a.getHouseId())) hasInvaders = true;
            if (a.getType() == AttractActor.ActorType.SOLDIER_ARCHER) hasArchers = true;
            if (a.getType() == AttractActor.ActorType.SOLDIER_MELEE) hasMelee = true;
            if (a.getType() == AttractActor.ActorType.CAMPFIRE || a.getType() == AttractActor.ActorType.TORCH) {
                hasCampfireOrTorch = true;
            }
        }

        assertTrue("Castle siege must have defenders", hasDefenders);
        assertTrue("Castle siege must have invading forces", hasInvaders);
        assertTrue("Castle siege must have archers", hasArchers);
        assertTrue("Castle siege must have melee fighters", hasMelee);
        assertTrue("Castle siege must have campfires or braziers", hasCampfireOrTorch);
    }

    @Test
    public void testMegabeastPresenceInMountainSector() {
        // Sector 3 (Mountain gorge pass) must feature a Megabeast
        List<AttractActor> mountainActors = director.getActorsInSector(3);
        boolean hasMegabeast = false;
        for (AttractActor a : mountainActors) {
            if (a.getType() == AttractActor.ActorType.MEGABEAST) {
                hasMegabeast = true;
                assertTrue("Megabeast scale must be large (> 2.0)", a.getScale().x >= 2.0f);
            }
        }
        assertTrue("Mountain sector must contain a Megabeast encounter", hasMegabeast);
    }

    @Test
    public void testProximityQuerying() {
        // Query near Castle entrance (180, -150)
        Vector3 castleGatePos = new Vector3(180f, 1.5f, -152f);
        List<AttractActor> nearby = director.getActorsNear(castleGatePos, 35f);
        assertFalse("Must find actors near castle gate", nearby.isEmpty());

        for (AttractActor a : nearby) {
            float dst2 = castleGatePos.dst2(a.getPosition());
            assertTrue("Actor must be within query radius", dst2 <= 35f * 35f + 1e-3f);
        }
    }

    @Test
    public void testActorUpdateAndAnimationTick() {
        director.update(0.16f, 10.0f);
        List<AttractActor> actors = director.getActorsInSector(1);
        AttractActor first = actors.get(0);
        assertTrue("Actor animTimer should advance", first.getAnimTimer() > 0f);
    }
}
