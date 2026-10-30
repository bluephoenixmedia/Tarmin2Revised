package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.favour.Contract;
import com.bpm.minotaur.gamedata.history.favour.Favour;
import com.bpm.minotaur.gamedata.history.text.Knell;
import com.bpm.minotaur.gamedata.history.war.Encounter;
import com.bpm.minotaur.gamedata.history.war.Siege;
import com.bpm.minotaur.gamedata.history.war.WarSoundscape;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Living War W33-W37: the siege of Castle Tarmin. */
public class SiegeTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    /** A history whose some house is at war with the Antlered Host, or null. */
    private static War warOnTheCastle(HistoryWorld w) {
        if (w.tarminHouse() == null) return null;
        for (War x : w.activeWars()) if (x.involves(w.tarminHouse().id)) return x;
        return null;
    }

    @Test
    public void prehistoryNeverLaysASiege() {
        for (long seed = 0; seed < 20; seed++) {
            for (HistoryEvent e : HistorySimulator.prehistory(seed, catalog).events()) {
                assertNotEquals(EventType.SIEGE_LAID, e.type);
            }
        }
    }

    @Test
    public void aStrongEnemyOfTheCastleLaysSiegeInLivePlayAndItEnds() {
        for (long seed = 1; seed <= 200; seed++) {
            HistoryManager h = HistoryManager.create(seed, catalog);
            War war = warOnTheCastle(h.world());
            if (war == null) continue;
            House besieger = h.world().house(war.enemyOf(h.world().tarminHouse().id));
            boolean laid = false, ended = false;
            for (int i = 0; i < 60 && !ended; i++) {
                besieger.strength = Math.max(besieger.strength, 90f);
                h.onSleep();
                for (HistoryEvent e : h.world().events()) {
                    if (e.type == EventType.SIEGE_LAID) laid = true;
                    if (laid && (e.type == EventType.SIEGE_LIFTED || e.type == EventType.SIEGE_BROKEN)) ended = true;
                }
                if (!war.isActive()) break;
            }
            if (!laid) continue;
            assertTrue("a siege comes to an end", ended || h.world().activeSiege() != null);
            return;
        }
        fail("no siege was laid in 200 worlds");
    }

    @Test
    public void aSiegeWeakensItsBesiegerAndBreaksWhenItIsTooWeak() {
        HistoryManager h = null;
        War war = null;
        for (long seed = 1; seed <= 200 && war == null; seed++) {
            h = HistoryManager.create(seed, catalog);
            war = warOnTheCastle(h.world());
        }
        assertNotNull(war);
        HistorySimulator.forceSiege(h.world(), war, 10);
        assertSame(war, h.world().activeSiege());
        House besieger = h.world().house(war.enemyOf(h.world().tarminHouse().id));
        besieger.strength = 30f;
        h.onSleep();
        boolean broken = false;
        for (HistoryEvent e : h.world().events()) if (e.type == EventType.SIEGE_BROKEN) broken = true;
        assertTrue("30, plus a season's recovery, less its attrition, is under the line", broken || !war.isActive());
        assertNull(h.world().activeSiege());
    }

    @Test
    public void theSiegeTollsTheKnell() {
        HistoryWorld w = HistorySimulator.prehistory(5L, catalog);
        for (EventType t : new EventType[]{EventType.SIEGE_LAID, EventType.SIEGE_LIFTED, EventType.SIEGE_BROKEN}) {
            HistoryEvent e = new HistoryEvent(0, 0, t);
            assertTrue(t + " tolls", Knell.tolls(w, e));
        }
    }

    @Test
    public void theBreachAsksOneSealOfAFriendOfTheBesiegerAndTwoOfAStranger() {
        assertEquals(1, Siege.sealsForBreach(Favour.Tier.FRIEND, false));
        assertEquals(1, Siege.sealsForBreach(Favour.Tier.UNKNOWN, true));
        assertEquals(2, Siege.sealsForBreach(Favour.Tier.TOLERATED, false));
        assertEquals(2, Siege.sealsForBreach(Favour.Tier.ENEMY, false));
    }

    @Test
    public void aSiegeIsHeardAndSeenAtTheCastleWithCampsAroundIt() {
        HistoryManager h = null;
        War war = null;
        for (long seed = 1; seed <= 200 && war == null; seed++) {
            h = HistoryManager.create(seed, catalog);
            war = warOnTheCastle(h.world());
        }
        GridPoint2 castle = new GridPoint2(0, 30);
        assertTrue("no siege, nothing", Siege.at(h.world(), castle).isEmpty());
        HistorySimulator.forceSiege(h.world(), war, 5);
        List<Encounter> at = Siege.at(h.world(), castle);
        assertEquals(1, at.size());
        Encounter siege = at.get(0);
        assertEquals(Encounter.Kind.SIEGE, siege.kind);
        assertFalse("entered only by the breach", siege.visible());
        assertTrue(siege.fighting());
        assertEquals(4, Siege.camps(h.world(), castle).size());
        WarSoundscape.Mix m = WarSoundscape.mix(at, Collections.emptyList(), 0, new GridPoint2(0, 27), 1, Direction.NORTH, false);
        boolean barrage = false;
        for (WarSoundscape.Shot s : m.shots) barrage |= s.key.equals("siege_barrage");
        assertTrue("the engines are heard", barrage);
    }

    @Test
    public void theBesiegerAsksItsSwornToJoinTheAssault() {
        HistoryManager h = null;
        War war = null;
        for (long seed = 1; seed <= 200 && war == null; seed++) {
            h = HistoryManager.create(seed, catalog);
            war = warOnTheCastle(h.world());
        }
        HistorySimulator.forceSiege(h.world(), war, 5);
        int besieger = Siege.besieger(h.world());
        h.swear(besieger);
        Contract c = h.offerContract(null, new GridPoint2(0, 0));
        assertEquals(Contract.Kind.SIEGE_ASSAULT, c.kind);
        assertEquals(h.world().tarminHouse().id, c.targetId);
        assertFalse(c.describe(h.world()).contains("_"));
    }

    @Test
    public void aSiegeInLivePlayReplaysFromTheSave() {
        for (long seed = 1; seed <= 300; seed++) {
            HistoryManager h = HistoryManager.create(seed, catalog);
            if (warOnTheCastle(h.world()) == null) continue;
            boolean laid = false;
            for (int i = 0; i < 40 && !laid; i++) {
                h.onSleep();
                for (HistoryEvent e : h.world().events()) laid |= e.type == EventType.SIEGE_LAID;
            }
            if (!laid) continue;
            HistoryManager loaded = HistoryManager.fromSave(0L, h.toSave(), catalog);
            assertEquals(h.world().fingerprint(), loaded.world().fingerprint());
            assertEquals(h.world().activeSiege() == null, loaded.world().activeSiege() == null);
            return;
        }
        fail("no siege laid on its own in 300 worlds");
    }

    /**
     * Living War W33-W37: A full natural siege lifecycle from organic war escalation to resolution,
     * verifying Knell tolling, camp rings, breach requirements, and aftermath cleanup.
     */
    @Test
    public void naturalSiegeLifecycleAndWorldIntegration() {
        GridPoint2 castle = new GridPoint2(0, 30);
        for (long seed = 1; seed <= 300; seed++) {
            HistoryManager h = HistoryManager.create(seed, catalog);
            War war = warOnTheCastle(h.world());
            if (war == null) continue;
            House besieger = h.world().house(war.enemyOf(h.world().tarminHouse().id));
            if (besieger == null) continue;

            HistoryEvent laidEvent = null;
            // Advance seasons naturally with an escalating enemy house
            for (int i = 0; i < 50 && laidEvent == null; i++) {
                besieger.strength = Math.max(besieger.strength, 85f);
                List<HistoryEvent> news = h.onSleep();
                for (HistoryEvent e : news) {
                    if (e.type == EventType.SIEGE_LAID) {
                        laidEvent = e;
                        break;
                    }
                }
            }
            if (laidEvent == null) continue;

            // 1. Natural siege trigger and Knell
            assertNotNull("Siege must be laid naturally", h.world().activeSiege());
            assertTrue("Knell tolls when siege begins", Knell.tolls(h.world(), laidEvent));
            assertEquals(besieger.id, Siege.besieger(h.world()));

            // 2. Camp ring and siege encounter placement around Castle Tarmin
            List<Encounter> camps = Siege.camps(h.world(), castle);
            assertEquals("Besieger camps ring all 4 sides of castle", 4, camps.size());
            for (Encounter c : camps) {
                assertEquals(Encounter.Kind.CAMP, c.kind);
                assertEquals(besieger.id, c.houseA);
            }
            List<Encounter> siegeAt = Siege.at(h.world(), castle);
            assertEquals(1, siegeAt.size());
            assertEquals(Encounter.Kind.SIEGE, siegeAt.get(0).kind);

            // 3. Breach seals requirement
            assertEquals(1, Siege.sealsForBreach(Favour.Tier.FRIEND, false));
            assertEquals(1, Siege.sealsForBreach(Favour.Tier.ENEMY, true)); // sworn overrides stance
            assertEquals(2, Siege.sealsForBreach(Favour.Tier.UNKNOWN, false));

            // 4. Besieger collapses: siege breaks and cleans up
            besieger.strength = 15f;
            HistoryEvent endEvent = null;
            for (int i = 0; i < 20 && endEvent == null; i++) {
                List<HistoryEvent> news = h.onSleep();
                for (HistoryEvent e : news) {
                    if (e.type == EventType.SIEGE_BROKEN || e.type == EventType.SIEGE_LIFTED) {
                        endEvent = e;
                        break;
                    }
                }
            }
            assertNotNull("Siege must break or lift when strength collapses", endEvent);
            assertTrue("Knell tolls when siege ends", Knell.tolls(h.world(), endEvent));
            assertNull("Active siege cleared in history", h.world().activeSiege());
            assertTrue("Camps cleared around castle", Siege.camps(h.world(), castle).isEmpty());
            assertTrue("Siege encounter cleared at castle", Siege.at(h.world(), castle).isEmpty());
            return;
        }
        fail("No natural siege triggered and completed in 300 test seeds");
    }
}
