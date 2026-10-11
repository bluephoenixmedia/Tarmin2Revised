package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.favour.Contract;
import com.bpm.minotaur.gamedata.history.favour.Favour;
import com.bpm.minotaur.gamedata.history.favour.Oath;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.save.WorldSaveData;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/** Living War W18-W26: favour, the oath, contracts and what they pay. */
public class TheSwornTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static HistoryEvent last(HistoryManager h) {
        return h.world().events().get(h.world().events().size() - 1);
    }

    @Test
    public void favourHasItsTiers() {
        assertEquals(Favour.Tier.ENEMY, Favour.tierOf(-50));
        assertEquals(Favour.Tier.DISTRUSTED, Favour.tierOf(-10));
        assertEquals(Favour.Tier.UNKNOWN, Favour.tierOf(0));
        assertEquals(Favour.Tier.UNKNOWN, Favour.tierOf(24));
        assertEquals(Favour.Tier.TOLERATED, Favour.tierOf(25));
        assertEquals(Favour.Tier.FRIEND, Favour.tierOf(60));
    }

    @Test
    public void favourWithOneHouseCostsHalfWithItsEnemies() {
        HistoryManager h = HistoryManager.create(61L, catalog);
        War w = h.world().activeWars().get(0);
        h.favour().change(h.world(), w.attackerId, 20);
        assertEquals(20, h.favour().of(w.attackerId));
        assertEquals("its enemy hears half, reversed", -10, h.favour().of(w.defenderId));
        h.favour().change(h.world(), w.attackerId, 500);
        assertEquals(Favour.MAX, h.favour().of(w.attackerId));
    }

    @Test
    public void killingASoldierCostsItsHouse() {
        HistoryManager h = HistoryManager.create(63L, catalog);
        int house = h.world().livingHouses().get(0).id;
        h.onSoldierSlain(house, -1);
        assertEquals(Favour.KILLED_SOLDIER, h.favour().of(house));
    }

    @Test
    public void swornTheEnemiesOfYourHouseAreYoursAndYourHouseNeverHostile() {
        HistoryManager h = HistoryManager.create(65L, catalog);
        War w = h.world().activeWars().get(0);
        h.favour().set(w.attackerId, Favour.FRIEND_AT);
        h.swear(w.attackerId);
        assertEquals(EventType.SEEKER_SWORN, last(h).type);
        assertEquals(Favour.Tier.ENEMY, h.stanceOf(w.defenderId));
        h.favour().set(w.attackerId, -30);
        assertEquals("the sworn house never less than tolerated", Favour.Tier.TOLERATED, h.stanceOf(w.attackerId));
    }

    @Test
    public void breakingTheOathIsChronicledAndEveryHouseThinksLessOfYou() {
        HistoryManager h = HistoryManager.create(67L, catalog);
        War w = h.world().activeWars().get(0);
        House other = null;
        for (House x : h.world().livingHouses()) if (x.id != w.attackerId && x.id != w.defenderId) other = x;
        h.favour().set(w.attackerId, Favour.FRIEND_AT);
        h.swear(w.attackerId);
        int before = other.id >= 0 ? h.favour().of(other.id) : 0;
        h.breakOath();
        assertEquals(EventType.OATH_BROKEN, last(h).type);
        assertEquals(w.attackerId, last(h).houseB);
        assertFalse(h.oath().sworn());
        assertEquals("the betrayed hunts you", Favour.Tier.ENEMY, h.stanceOf(w.attackerId));
        assertEquals(before + Favour.OATH_BROKEN_ALL, h.favour().of(other.id));
    }

    @Test
    public void swearingToAnotherHouseBreaksTheFirstOath() {
        HistoryManager h = HistoryManager.create(69L, catalog);
        List<House> houses = h.world().livingHouses();
        h.swear(houses.get(0).id);
        h.swear(houses.get(1).id);
        boolean broken = false;
        for (HistoryEvent e : h.world().events()) if (e.type == EventType.OATH_BROKEN && e.houseB == houses.get(0).id) broken = true;
        assertTrue(broken);
        assertEquals(houses.get(1).id, h.oath().houseId);
    }

    @Test
    public void aContractIsSetAgainstAnEnemyAtWarAndKillingItsCaptainKeepsIt() {
        HistoryManager h = HistoryManager.create(71L, catalog);
        War w = h.world().activeWars().get(0);
        h.swear(w.attackerId);
        Contract c = null;
        for (int i = 0; i < 40 && (c == null || c.kind != Contract.Kind.KILL_CAPTAIN); i++) {
            h.oath().done = i;
            c = h.offerContract(null, new GridPoint2(0, 0));
        }
        assertNotNull(c);
        assertEquals(Contract.Kind.KILL_CAPTAIN, c.kind);
        assertEquals(w.defenderId, c.targetId);
        assertFalse("a common soldier is no captain", h.onSoldierSlain(w.defenderId, -1));
        assertTrue(h.onSoldierSlain(w.defenderId, 3));
        assertTrue(c.done);
    }

    @Test
    public void holdingTheFrontCountsOnlyOnItsChunk() {
        Contract c = new Contract();
        c.kind = Contract.Kind.HOLD_CHUNK;
        c.chunkX = 4;
        c.chunkY = 5;
        for (int i = 0; i < Contract.HOLD_TURNS - 1; i++) assertFalse(c.held(new GridPoint2(4, 5)));
        assertFalse("elsewhere does not count", c.held(new GridPoint2(0, 0)));
        assertTrue(c.held(new GridPoint2(4, 5)));
    }

    @Test
    public void everyThirdContractEarnsARelicAndThreeHardOnesTheSeal() {
        Oath o = new Oath();
        o.swear(2);
        boolean[] relics = new boolean[6];
        for (int i = 0; i < 6; i++) {
            Contract c = new Contract();
            c.done = true;
            c.hard = true;
            o.contract = c;
            relics[i] = o.complete();
        }
        assertArrayEquals(new boolean[]{false, false, true, false, false, true}, relics);
        assertTrue(o.sealDue());
        o.sealGranted = true;
        assertFalse(o.sealDue());
    }

    @Test
    public void aGreatHouseAtFriendSetsHardContracts() {
        HistoryManager h = HistoryManager.create(73L, catalog);
        HistoryWorld w = h.world();
        for (int g = 0; g < HistoryWorld.GASH_COUNT; g++) {
            House holder = w.gashHolder(g);
            if (w.activeWarCount(holder.id) == 0) continue;
            h.favour().set(holder.id, Favour.FRIEND_AT);
            h.swear(holder.id);
            Contract c = h.offerContract(null, new GridPoint2(0, 0));
            assertNotNull(c);
            assertTrue(c.hard);
            assertEquals(Contract.HARD_FAVOUR, c.favour());
            h.recordSealGranted(holder.id);
            assertEquals(EventType.SEAL_GRANTED, last(h).type);
            return;
        }
    }

    @Test
    public void favourAndTheOathSurviveTheSaveFile() {
        HistoryManager live = HistoryManager.create(75L, catalog);
        War w = live.world().activeWars().get(0);
        live.favour().set(w.attackerId, 70);
        live.swear(w.attackerId);
        live.offerContract(null, new GridPoint2(1, 1));
        WorldSaveData save = new WorldSaveData();
        save.history = live.toSave();
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(true);
        HistoryManager restored = HistoryManager.fromSave(-1L, json.fromJson(WorldSaveData.class, json.toJson(save)).history, catalog);
        assertEquals(live.world().fingerprint(), restored.world().fingerprint());
        assertEquals(70, restored.favour().of(w.attackerId));
        assertEquals(w.attackerId, restored.oath().houseId);
        assertEquals(live.oath().contract.kind, restored.oath().contract.kind);
    }

    @Test
    public void aRelicCarriesTwoDifferentModifiersAndItsHousesWeapon() {
        List<ItemModifier> mods = Relics.modifiers(10, new Random(3));
        assertEquals(Relics.MODIFIERS, mods.size());
        assertNotEquals(mods.get(0).type, mods.get(1).type);
        assertEquals("FLAIL_CHAIN", Relics.weaponOf("chainwrights"));
        for (com.bpm.minotaur.gamedata.history.Doctrine d : catalog.selectable()) {
            com.bpm.minotaur.gamedata.item.Item.ItemType.valueOf(Relics.weaponOf(d.id));
        }
    }
}
