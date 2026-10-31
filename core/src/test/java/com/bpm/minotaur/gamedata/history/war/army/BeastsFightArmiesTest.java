package com.bpm.minotaur.gamedata.history.war.army;

import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.Megabeast;
import com.bpm.minotaur.gamedata.history.PlayerDeed;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.HistoryManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Living War A28: a beast's raid is a fight with the house's armies, in the one battle state. */
public class BeastsFightArmiesTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static final class World {
        final long seed;
        final HistoryManager history;
        final BiomeManager biomes;
        final ArmyLayer layer;
        final List<PlayerDeed> deeds = new ArrayList<>();

        World(long seed) {
            this.seed = seed;
            history = HistoryManager.create(seed, catalog);
            biomes = new BiomeManager(seed);
            layer = new ArmyLayer(history.world(), Terrain.of(biomes), this::seats, history.roster(), d -> {
                deeds.add(d);
                history.recordArmyDeed(d);
            });
            layer.beastHealth(id -> history.beastHp(id, ArmyTuning.BEAST_WHOLE_HP));
        }

        SeatMap seats() {
            return SeatMap.of(history.world(), biomes.getRoads(), biomes.getCastleSite(), seed, biomes::isOpenLand);
        }

        void run(int turns) {
            for (int i = 0; i < turns; i++) {
                history.tickWarClock();
                layer.tick(history.warClock());
            }
        }

        House aHouse() {
            run(1);
            for (House h : history.world().livingHouses()) if (!h.holdsCastle && seats().seat(h.id) != null) return h;
            throw new AssertionError("no house");
        }

        Megabeast aBeast() {
            for (Megabeast b : history.world().megabeasts()) if (b.isAlive()) return b;
            throw new AssertionError("no beast");
        }

        ArmyBattle fight() {
            House h = aHouse();
            ArmyBattle b = layer.beastFightsNow(aBeast().id, h, 20, 20);
            assertNotNull(b);
            return b;
        }

        boolean over(ArmyBattle b) {
            return !history.roster().battles.contains(b);
        }

        long count(EventType type) {
            return history.world().events().stream().filter((HistoryEvent e) -> e.type == type).count();
        }
    }

    @Test
    public void aBeastIsSideAloneByItsHitPoints() {
        World w = new World(1000L);
        ArmyBattle b = w.fight();
        assertEquals(ArmyBattle.THE_BEAST, b.houseA);
        assertEquals(1, w.layer.aliveOn(b, true));
        int hp = b.beastHp;
        assertEquals(ArmyTuning.BEAST_WHOLE_HP, hp);
        w.run(60);
        assertTrue("soldiers wound it: " + hp + " to " + b.beastHp, b.beastHp < hp);
    }

    @Test
    public void theSameSeedReplaysTheSameFights() {
        String[] seen = new String[2];
        for (int i = 0; i < 2; i++) {
            World w = new World(1000L);
            ArmyBattle b = w.fight();
            w.run(400);
            StringBuilder s = new StringBuilder("hp " + b.beastHp + " turn " + b.turn + " over " + w.over(b));
            for (PlayerDeed d : w.deeds) s.append('|').append(d.kind).append(':').append(d.target).append(':').append(d.other);
            seen[i] = s.toString();
        }
        assertEquals(seen[0], seen[1]);
    }

    @Test
    public void wearingABeastDownKeepsItsWounds() {
        World w = new World(1000L);
        ArmyBattle b = w.fight();
        int id = b.beastId;
        b.beastHp = 123;
        b.moraleB = -100;
        for (int i = 0; i < 5 && !w.over(b); i++) w.run(1);
        assertTrue(w.over(b));
        assertTrue("a little worse", w.history.beastHp(id, 9999) <= 123);
        assertEquals("it still lives", true, w.history.world().megabeast(id).isAlive());
    }

    @Test
    public void aSlainBeastIsCreditedToTheHouseAndStaysDeadOnReload() {
        World w = new World(1000L);
        ArmyBattle b = w.fight();
        int id = b.beastId, house = b.houseB;
        float prestige = w.history.world().house(house).prestige;
        long before = w.count(EventType.MEGABEAST_FELLED);
        b.beastHp = 1;
        for (int i = 0; i < 200 && !w.over(b); i++) w.run(1);
        assertTrue(w.over(b));
        assertFalse(w.history.world().megabeast(id).isAlive());
        assertEquals(before + 1, w.count(EventType.MEGABEAST_FELLED));
        HistoryEvent felled = null;
        for (HistoryEvent e : w.history.world().events()) if (e.type == EventType.MEGABEAST_FELLED) felled = e;
        assertEquals("credited to the house", house, felled.houseA);
        assertTrue(w.history.world().house(house).prestige > prestige);
        HistoryManager loaded = HistoryManager.fromSave(0L, w.history.toSave(), catalog);
        assertFalse("still dead", loaded.world().megabeast(id).isAlive());
        assertEquals(w.history.world().fingerprint(), loaded.world().fingerprint());
    }

    @Test
    public void aFightWithNoOneOfNoteDeadIsNotChronicled() {
        World w = new World(1000L);
        ArmyBattle b = w.fight();
        int events = w.history.world().events().size();
        b.moraleB = -100;
        for (int i = 0; i < 5 && !w.over(b); i++) w.run(1);
        assertTrue(w.over(b));
        PlayerDeed deed = null;
        for (PlayerDeed d : w.deeds) if (d.kind == PlayerDeed.Kind.ARMY_BEAST) deed = d;
        assertNotNull(deed);
        long noted = 0;
        for (int i = events; i < w.history.world().events().size(); i++) {
            EventType t = w.history.world().events().get(i).type;
            if (t == EventType.MEGABEAST_RAID || t == EventType.MEGABEAST_FELLED) noted++;
        }
        assertEquals((deed.captainsSlain == null ? 0 : deed.captainsSlain.length) + (deed.beastSlain ? 1 : 0), noted);
    }

    @Test
    public void aRoutedHostGoesHomeAndABeastThatWinsIsNoHouse() {
        World w = new World(1000L);
        ArmyBattle b = w.fight();
        int armyId = b.armiesB[0];
        b.moraleB = -100;
        for (int i = 0; i < 5 && !w.over(b); i++) w.run(1);
        assertEquals(ArmyBattle.THE_BEAST, b.winner);
        Army a = w.history.roster().army(armyId);
        assertTrue("gone, or going home", a == null || a.objective == Army.Objective.RECOVER || a.objective == Army.Objective.DISBAND);
    }

    /** The Season sends it, the armies carry it out, and only once. */
    @Test
    public void theSeasonsRaidBringsABeastOnTheHousesNearestArmy() {
        World w = new World(1000L);
        House h = w.aHouse();
        Megabeast beast = w.aBeast();
        ArmyLayerAccess.ensureArmyOf(w, h);
        beast.raidingHouse = h.id;
        beast.raidSeason = w.history.world().season();
        int seasons = w.history.world().liveSeasons();
        w.history.onSleep();
        w.run(1);
        assertTrue(w.history.world().liveSeasons() > seasons);
        ArmyBattle b = null;
        for (ArmyBattle o : w.history.roster().battles) if (o.beastId == beast.id) b = o;
        assertNotNull("it fell on the house", b);
        assertEquals(h.id, b.houseB);
        assertTrue(w.history.roster().beastRaidDone(beast.id, beast.raidSeason));
    }

    @Test
    public void aRaidIsNoLongerABlowFromTheRoll() {
        World w = new World(1000L);
        long raids = w.count(EventType.MEGABEAST_RAID);
        for (int i = 0; i < 40; i++) w.history.onSleep();
        assertEquals("nothing chronicled without a fight", raids, w.count(EventType.MEGABEAST_RAID));
    }

    @Test
    public void aRaidIsBroughtAboutOnceEvenAcrossASave() {
        WarRoster r = new WarRoster();
        assertFalse(r.beastRaidDone(1, 5));
        r.markBeastRaid(1, 5);
        assertTrue(r.beastRaidDone(1, 5));
        assertFalse(r.beastRaidDone(1, 6));
        Json json = new Json();
        assertTrue(json.fromJson(WarRoster.class, json.toJson(r)).beastRaidDone(1, 5));
    }

    /** A beast that followed the player onto the surface fights any army it meets. */
    @Test
    public void aBeastThatFollowedThePlayerFightsTheArmyItMeets() {
        World w = new World(1000L);
        House h = w.aHouse();
        Army a = ArmyLayerAccess.ensureArmyOf(w, h);
        int id = w.aBeast().id;
        w.layer.watch(new ArmyLayer.PlayerWatch() {
            @Override
            public com.badlogic.gdx.math.GridPoint2 surfaceChunk() {
                return null;
            }

            @Override
            public boolean countsPlayerAnEnemy(int houseId) {
                return false;
            }

            @Override
            public int[] huntingBeast(long clock) {
                return new int[] {id, a.x, a.y};
            }
        });
        a.path = new int[0];
        w.run(1);
        ArmyBattle b = w.layer.battleIn(a.x, a.y);
        assertNotNull(b);
        assertEquals(id, b.beastId);
    }

    /** What these tests reach for that the layer only does at its own time. */
    private static final class ArmyLayerAccess {
        static Army ensureArmyOf(World w, House h) {
            for (Army a : w.history.roster().armies) if (a.houseId == h.id && !a.battle) return a;
            Army a = new Army();
            a.id = w.history.roster().nextArmyId++;
            a.houseId = h.id;
            a.kind = Army.Kind.HOST;
            a.soldiers = 15;
            a.startSoldiers = 15;
            a.x = 22;
            a.y = 22;
            a.targetX = 22;
            a.targetY = 22;
            w.history.roster().armies.add(a);
            return a;
        }
    }
}
