package com.bpm.minotaur.gamedata.history.favour;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.save.WorldSaveData;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistorySaveData;
import com.bpm.minotaur.gamedata.history.PlayerDeed;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.gamedata.history.war.army.SeatSiege;
import com.bpm.minotaur.managers.HistoryManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/** Living War A37-A41: contracts on named things, favour by the player's part, and the turncoat. */
public class ContractAndFavourTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private final HistoryManager history = HistoryManager.create(71L, catalog);
    private final War war = history.world().activeWars().get(0);
    private final int liege = war.attackerId;
    private final int enemy = war.defenderId;

    private Army army(int id, int house, int x, int y) {
        Army a = new Army();
        a.id = id;
        a.houseId = house;
        a.x = x;
        a.y = y;
        a.soldiers = 15;
        a.captainId = 7;
        a.warId = war.id;
        history.roster().armies.add(a);
        return a;
    }

    private Contract contract(Contract.Kind kind, int targetArmy) {
        Contract c = new Contract();
        c.kind = kind;
        c.giverId = liege;
        c.targetId = enemy;
        c.targetArmy = targetArmy;
        return c;
    }

    private PlayerDeed deed(int winner, int stoodWith) {
        PlayerDeed d = new PlayerDeed(PlayerDeed.Kind.ARMY_BATTLE, winner, 0);
        if (stoodWith >= 0) d.stoodWith = new int[] {stoodWith};
        d.stoodAndWon = stoodWith == winner;
        return d;
    }

    // ---- A37: they name real things ----

    @Test
    public void aContractNamesARealThingInTheRoster() {
        Army host = army(50, enemy, 12, 9);
        boolean named = false;
        for (int count = 0; count < 40; count++) {
            Contract c = Contract.offer(history.world(), history.roster(), liege, count, false);
            assertNotNull(c);
            if (c.targetArmy == host.id) {
                named = true;
                assertEquals(12, c.chunkX);
                assertEquals(9, c.chunkY);
                assertFalse(c.describe(history.world()), c.describe(history.world()).isEmpty());
            }
        }
        assertTrue(named);
    }

    @Test
    public void aHouseWithNothingToAskSetsNothing() {
        assertNull("a roster empty of anything to name", Contract.offer(history.world(), new com.bpm.minotaur.gamedata.history.war.army.WarRoster(), liege, 0, false));
    }

    @Test
    public void aBesiegedSeatOrASiegeOfOneIsOnOffer() {
        SeatSiege s = new SeatSiege();
        s.id = 90;
        s.holder = liege;
        s.besieger = enemy;
        s.x = 3;
        s.y = 4;
        history.roster().sieges.add(s);
        Contract c = Contract.offer(history.world(), history.roster(), liege, 0, false);
        assertEquals(Contract.Kind.RELIEVE_SEAT, c.kind);
        assertEquals(90, c.targetSiege);
        history.roster().sieges.clear();
        s.holder = enemy;
        s.besieger = liege;
        history.roster().sieges.add(s);
        assertEquals(Contract.Kind.JOIN_ASSAULT, Contract.offer(history.world(), history.roster(), liege, 0, false).kind);
    }

    @Test
    public void theLastKnownChunkIsAsFarAsTheLiegeKnows() {
        Army target = army(50, enemy, 100, 100);
        Contract c = contract(Contract.Kind.BREAK_HOST, target.id);
        c.chunkX = 100;
        c.chunkY = 100;
        target.x = 110;
        c.reportWhereItIs(history.roster());
        assertEquals("nobody of ours is near", 100, c.chunkX);
        army(51, liege, 112, 100);
        c.reportWhereItIs(history.roster());
        assertEquals(110, c.chunkX);
    }

    // ---- A38: void, and its exception ----

    @Test
    public void aTargetGoneVoidsTheContract() {
        Army target = army(50, enemy, 1, 1);
        Contract c = contract(Contract.Kind.BREAK_HOST, target.id);
        assertFalse(c.targetGone(history.world(), history.roster()));
        history.roster().armies.remove(target);
        assertTrue(c.targetGone(history.world(), history.roster()));
    }

    @Test
    public void aWarEndedVoidsTheContract() {
        Army target = army(50, enemy, 1, 1);
        Contract c = contract(Contract.Kind.BREAK_HOST, target.id);
        war.endSeason = history.world().season();
        assertTrue(c.targetGone(history.world(), history.roster()));
    }

    @Test
    public void aDoneContractIsNeverVoid() {
        Contract c = contract(Contract.Kind.BREAK_HOST, 50);
        c.done = true;
        assertFalse(c.targetGone(history.world(), history.roster()));
    }

    /** A38's exception: fell in a battle the player fought in for their liege, whoever struck the blow. */
    @Test
    public void aTargetFallingInTheBattleThePlayerFoughtInCountsAsDone() {
        Contract c = contract(Contract.Kind.BREAK_HOST, 50);
        PlayerDeed d = deed(liege, liege);
        d.armiesRouted = new int[] {50};
        assertTrue(c.onBattle(d));
        assertTrue(c.done);
    }

    @Test
    public void aTargetFallingToSomeoneElseCountsForNothing() {
        Contract c = contract(Contract.Kind.BREAK_HOST, 50);
        PlayerDeed d = deed(liege, -1);
        d.armiesRouted = new int[] {50};
        assertFalse("the player wasn't there", c.onBattle(d));
        PlayerDeed against = deed(liege, enemy);
        against.armiesRouted = new int[] {50};
        assertFalse("nor on the wrong side", c.onBattle(against));
        assertFalse(c.done);
    }

    /** The liege's own host cut to nothing while the battle still goes: it may yet count as done. */
    @Test
    public void aTargetDownInABattleStillGoingIsNotYetGone() {
        Army target = army(50, enemy, 1, 1);
        target.soldiers = 0;
        target.battle = true;
        Contract c = contract(Contract.Kind.BREAK_HOST, target.id);
        assertFalse(c.targetGone(history.world(), history.roster()));
        target.battle = false;
        assertTrue(c.targetGone(history.world(), history.roster()));
    }

    @Test
    public void aLiegeWhoIsAnAllyOfTheLeadStillCounts() {
        Contract c = contract(Contract.Kind.BREAK_HOST, 50);
        PlayerDeed d = deed(enemy, enemy);
        d.stoodWith = new int[] {enemy, liege};
        d.armiesRouted = new int[] {50};
        assertTrue(c.onBattle(d));
    }

    @Test
    public void aNamedCaptainMayBeKilledOrTakenInBattleOrSlainByThePlayer() {
        Contract c = contract(Contract.Kind.TAKE_CAPTAIN, 50);
        c.targetFigure = 7;
        PlayerDeed d = deed(liege, liege);
        d.captainsTaken = new int[] {7};
        assertTrue(c.onBattle(d));
        Contract other = contract(Contract.Kind.TAKE_CAPTAIN, 50);
        other.targetFigure = 7;
        assertFalse("a different captain", other.onSoldierSlain(8));
        assertTrue(other.onSoldierSlain(7));
    }

    @Test
    public void standingWithAHostCountsWinOrLose() {
        Contract c = contract(Contract.Kind.STAND_WITH_HOST, 50);
        PlayerDeed d = deed(enemy, liege);
        d.armiesRouted = new int[] {50};
        assertTrue(c.onBattle(d));
        Contract won = contract(Contract.Kind.STAND_WITH_HOST, 50);
        PlayerDeed w = deed(liege, liege);
        w.armiesHeld = new int[] {50};
        assertTrue(won.onBattle(w));
    }

    // ---- A42: the seat contracts ----

    private Contract atTheSeat(Contract.Kind kind) {
        Contract c = contract(kind, -1);
        c.chunkX = 3;
        c.chunkY = 4;
        return c;
    }

    private PlayerDeed theAssault(int winner, int stoodWith, boolean fought) {
        PlayerDeed d = deed(winner, stoodWith);
        d.chunkX = 3;
        d.chunkY = 4;
        d.assault = true;
        d.fought = fought;
        d.seatFell = winner == liege;
        return d;
    }

    @Test
    public void joiningTheAssaultIsDoneByFightingInItEvenIfItFails() {
        Contract c = atTheSeat(Contract.Kind.JOIN_ASSAULT);
        assertTrue(c.onBattle(theAssault(enemy, liege, true)));
        assertEquals("the ordinary rate", Contract.FAVOUR, c.favour());
        assertEquals(Contract.COIN, c.coin());
    }

    @Test
    public void joiningTheAssaultPaysTheHardRateIfTheSeatFallsWithoutBeingAHardOne() {
        Contract c = atTheSeat(Contract.Kind.JOIN_ASSAULT);
        assertTrue(c.onBattle(theAssault(liege, liege, true)));
        assertEquals(Contract.HARD_FAVOUR, c.favour());
        assertEquals(Contract.COIN * 2, c.coin());
        assertFalse("nor does it count toward the seal", c.hard);
    }

    @Test
    public void standingByWhileOthersStormItIsNotJoiningIt() {
        assertFalse(atTheSeat(Contract.Kind.JOIN_ASSAULT).onBattle(theAssault(liege, liege, false)));
        PlayerDeed elsewhere = theAssault(liege, liege, true);
        elsewhere.chunkX = 9;
        assertFalse("another seat", atTheSeat(Contract.Kind.JOIN_ASSAULT).onBattle(elsewhere));
        PlayerDeed sally = theAssault(liege, liege, true);
        sally.assault = false;
        sally.seatFell = false;
        assertFalse("a sally is no assault", atTheSeat(Contract.Kind.JOIN_ASSAULT).onBattle(sally));
        assertFalse("fled before the end", atTheSeat(Contract.Kind.JOIN_ASSAULT).onBattle(theAssault(liege, -1, true)));
    }

    @Test
    public void theFirstLadderIsDoneOnlyIfTheSeatFalls() {
        SeatSiege s = new SeatSiege();
        s.id = 40;
        s.x = 3;
        s.y = 4;
        s.holder = enemy;
        Contract c = Contract.firstLadder(liege, s);
        assertTrue(c.hard);
        assertEquals(enemy, c.targetId);
        assertEquals(40, c.targetSiege);
        assertFalse("thrown back: it stands until the siege ends", c.onBattle(theAssault(enemy, liege, true)));
        assertFalse("nobody climbed", c.onBattle(theAssault(liege, liege, false)));
        assertTrue(c.onBattle(theAssault(liege, liege, true)));
        assertEquals(Contract.HARD_FAVOUR, c.favour());
    }

    @Test
    public void theFirstLadderIsOfOneSeatOnly() {
        SeatSiege s = new SeatSiege();
        s.id = 40;
        s.holder = enemy;
        Contract c = Contract.firstLadder(liege, s);
        assertTrue(c.firstLadderAt(40));
        assertFalse("another siege", c.firstLadderAt(41));
        c.done = true;
        assertFalse("already climbed", c.firstLadderAt(40));
        assertFalse("another task", contract(Contract.Kind.BREAK_HOST, 1).firstLadderAt(-1));
    }

    private Army besieging(SeatSiege s) {
        history.roster().sieges.add(s);
        Army host = army(60, liege, s.x, s.y);
        host.sieging = s.id;
        return host;
    }

    private SeatSiege laddersUp() {
        SeatSiege s = new SeatSiege();
        s.id = 40;
        s.x = 3;
        s.y = 4;
        s.holder = enemy;
        s.besieger = liege;
        s.works = SeatSiege.LADDER_TURNS;
        return s;
    }

    @Test
    public void aFriendWithTheHostIsAskedOnceWhenTheLaddersAreUp() {
        SeatSiege s = laddersUp();
        Army host = besieging(s);
        history.favour().set(liege, Favour.FRIEND_AT);
        history.swear(liege);
        assertNull("following no host", history.firstLadderDue());
        history.oath().following = host.id;
        assertSame(s, history.firstLadderDue());
        history.oath().ladderAsked = s.id;
        assertNull("once a siege", history.firstLadderDue());
    }

    @Test
    public void noFirstLadderBeforeTheWorksOrBelowFriendOrOnceTheFightIsOn() {
        SeatSiege s = laddersUp();
        Army host = besieging(s);
        history.favour().set(liege, Favour.FRIEND_AT);
        history.swear(liege);
        history.oath().following = host.id;
        s.works = SeatSiege.LADDER_TURNS - 1;
        assertNull("the works", history.firstLadderDue());
        s.works = SeatSiege.LADDER_TURNS;
        history.favour().set(liege, Favour.FRIEND_AT - 1);
        assertNull("Tolerated", history.firstLadderDue());
        history.favour().set(liege, Favour.FRIEND_AT);
        host.battle = true;
        assertNull("too late, the fight is on", history.firstLadderDue());
        host.battle = false;
        history.oath().contract = contract(Contract.Kind.BREAK_HOST, 99);
        assertSame("a task in hand is no bar: a Friend always has one", s, history.firstLadderDue());
    }

    private PlayerDeed raised(int x) {
        PlayerDeed d = new PlayerDeed(PlayerDeed.Kind.SIEGE_RAISED, enemy, 0);
        d.other = liege;
        d.chunkX = x;
        d.chunkY = 4;
        return d;
    }

    private PlayerDeed besiegersRouted() {
        PlayerDeed d = new PlayerDeed(PlayerDeed.Kind.ARMY_BATTLE, liege, 0);
        d.other = enemy;
        d.chunkX = 3;
        d.chunkY = 4;
        return d;
    }

    @Test
    public void reliefIsDoneIfTheSiegeIsRaisedOrTheBesiegersRoutWhileThePlayerIsThere() {
        Contract c = atTheSeat(Contract.Kind.RELIEVE_SEAT);
        assertTrue(c.onSiegeLifted(raised(3), true));
        c = atTheSeat(Contract.Kind.RELIEVE_SEAT);
        assertTrue(c.onSiegeLifted(besiegersRouted(), true));
    }

    @Test
    public void reliefIsNotDoneFromAfarNorByAnotherSeatsNorByAnyoneElsesRout() {
        assertFalse("not there", atTheSeat(Contract.Kind.RELIEVE_SEAT).onSiegeLifted(raised(3), false));
        assertFalse("another seat", atTheSeat(Contract.Kind.RELIEVE_SEAT).onSiegeLifted(raised(9), true));
        PlayerDeed lost = new PlayerDeed(PlayerDeed.Kind.ARMY_BATTLE, enemy, 0);
        lost.other = liege;
        lost.chunkX = 3;
        lost.chunkY = 4;
        assertFalse("they won", atTheSeat(Contract.Kind.RELIEVE_SEAT).onSiegeLifted(lost, true));
        assertFalse("another contract", atTheSeat(Contract.Kind.JOIN_ASSAULT).onSiegeLifted(raised(3), true));
    }

    @Test
    public void aSiegeRaisedWithThePlayerInsideKeepsTheContractBeforeItCanBeVoid() {
        SeatSiege s = new SeatSiege();
        s.id = 40;
        s.x = 3;
        s.y = 4;
        Contract c = atTheSeat(Contract.Kind.RELIEVE_SEAT);
        c.targetSiege = 40;
        history.favour().set(liege, Favour.FRIEND_AT);
        history.swear(liege);
        history.oath().contract = c;
        history.watchRelief((x, y) -> x == 3 && y == 4);
        history.recordArmyDeed(raised(3));
        assertTrue(c.done);
        assertTrue(history.takeContractKeptInBattle());
        assertNull("the siege gone, it is not void", history.contractVoided());
    }

    @Test
    public void aSiegeRaisedWithThePlayerElsewhereVoidsIt() {
        Contract c = atTheSeat(Contract.Kind.RELIEVE_SEAT);
        c.targetSiege = 40;
        history.favour().set(liege, Favour.FRIEND_AT);
        history.swear(liege);
        history.oath().contract = c;
        history.recordArmyDeed(raised(3));
        assertFalse(c.done);
        assertNotNull(history.contractVoided());
    }

    // ---- A40's table ----

    @Test
    public void favourForAWinIsFivePlusTheShareUpToFifteen() {
        assertEquals(5, BattleFavour.of(true, 0f, false));
        assertEquals(20, BattleFavour.of(true, 1f / 3f, false));
        assertEquals("no more past a third", 20, BattleFavour.of(true, 0.9f, false));
        assertEquals("half the share, half of fifteen", 13, BattleFavour.of(true, 1f / 6f, false));
    }

    @Test
    public void aCaptainAddsTen() {
        assertEquals(15, BattleFavour.of(true, 0f, true));
        assertEquals(30, BattleFavour.of(true, 1f, true));
    }

    @Test
    public void losingFoughtToTheEndIsThree() {
        assertEquals(3, BattleFavour.of(false, 0.9f, true));
    }

    /** Fleeing is the absence of a deed for the player: the battle has no one standing with a side. */
    @Test
    public void aPlayerWhoFledIsPaidNothing() {
        PlayerDeed fled = deed(liege, -1);
        assertNull(fled.stoodWith);
        Contract c = contract(Contract.Kind.BREAK_HOST, 50);
        fled.armiesRouted = new int[] {50};
        assertFalse(c.onBattle(fled));
    }

    @Test
    public void aFifthOfTheEnemyDeadOnAWinningSideTurnedIt() {
        assertTrue(BattleFavour.turned(true, 0.2f));
        assertFalse(BattleFavour.turned(true, 0.19f));
        assertFalse("not on a losing one", BattleFavour.turned(false, 0.9f));
    }

    // ---- Round trips ----

    @Test
    public void aTurnedBattleIsChronicledAndReplays() {
        PlayerDeed d = new PlayerDeed(PlayerDeed.Kind.ARMY_BATTLE, liege, history.world().liveSeasons());
        d.other = enemy;
        d.turned = true;
        history.recordArmyDeed(d);
        long turned = history.world().events().stream().filter((HistoryEvent e) -> e.type == EventType.BATTLE_TURNED).count();
        assertEquals(1, turned);
        HistoryManager loaded = HistoryManager.fromSave(0L, history.toSave(), catalog);
        assertEquals(history.world().fingerprint(), loaded.world().fingerprint());
        assertEquals(1, loaded.world().events().stream().filter((HistoryEvent e) -> e.type == EventType.BATTLE_TURNED).count());
    }

    @Test
    public void aSaveWithAnOldStyleContractLoadsWithItVoided() {
        history.favour().set(liege, 70);
        history.swear(liege);
        Contract old = new Contract();
        old.kind = Contract.Kind.HOLD_CHUNK;
        old.giverId = liege;
        old.chunkX = 4;
        history.oath().contract = old;
        WorldSaveData save = new WorldSaveData();
        save.history = history.toSave();
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(true);
        HistorySaveData back = json.fromJson(WorldSaveData.class, json.toJson(save)).history;
        HistoryManager loaded = HistoryManager.fromSave(-1L, back, catalog);
        assertEquals(liege, loaded.oath().houseId);
        assertNull("void, not a crash", loaded.oath().contract);
    }

    // ---- A41 ----

    /** Swearing to the enemy mid-war is allowed; the new liege keeps them at Tolerated for a Season. */
    @Test
    public void theTurncoatsFavourHoldsAtToleratedForASeason() {
        history.favour().set(liege, 70);
        history.swear(liege);
        history.favour().set(enemy, 80);
        history.swear(enemy);
        assertEquals(enemy, history.oath().houseId);
        assertTrue("held", history.favour().tier(enemy).ordinal() <= Favour.Tier.TOLERATED.ordinal());
        history.favour().change(history.world(), enemy, 60);
        assertEquals("still held, whatever they do", Favour.Tier.TOLERATED, history.favour().tier(enemy));
        history.onSleep();
        history.favour().change(history.world(), enemy, 60);
        assertEquals("a Season on, they may rise", Favour.Tier.FRIEND, history.favour().tier(enemy));
    }

    @Test
    public void hurtingItsEnemyCannotLiftTheHold() {
        history.favour().set(liege, 70);
        history.swear(liege);
        history.favour().set(enemy, 80);
        history.swear(enemy);
        history.favour().change(history.world(), liege, -100);
        assertEquals("the spillover is held too", Favour.Tier.TOLERATED, history.favour().tier(enemy));
    }

    @Test
    public void theHoldSurvivesASave() {
        history.favour().set(liege, 70);
        history.swear(liege);
        history.favour().set(enemy, 80);
        history.swear(enemy);
        HistoryManager loaded = HistoryManager.fromSave(0L, history.toSave(), catalog);
        loaded.favour().change(loaded.world(), enemy, 60);
        assertEquals(Favour.Tier.TOLERATED, loaded.favour().tier(enemy));
    }

    @Test
    public void aHoldLongOverIsNotAppliedAgainOnLoad() {
        history.favour().set(liege, 70);
        history.swear(liege);
        history.favour().set(enemy, 80);
        history.swear(enemy);
        history.onSleep();
        history.favour().set(enemy, 90);
        HistoryManager loaded = HistoryManager.fromSave(0L, history.toSave(), catalog);
        assertEquals(Favour.Tier.FRIEND, loaded.favour().tier(enemy));
    }

    @Test
    public void swearingElsewhereWithNoWarBetweenIsNoTurncoat() {
        int third = -1;
        for (var h : history.world().livingHouses()) {
            if (h.id != liege && history.world().activeWarBetween(h.id, liege) == null) third = h.id;
        }
        if (third < 0) return;
        history.favour().set(liege, 70);
        history.swear(liege);
        history.favour().set(third, 80);
        history.swear(third);
        history.favour().change(history.world(), third, 10);
        assertEquals(Favour.Tier.FRIEND, history.favour().tier(third));
    }

    @Test
    public void anOpenContractAndUnpaidRewardAreForfeit() {
        history.favour().set(liege, 70);
        history.swear(liege);
        Contract c = contract(Contract.Kind.BREAK_HOST, 50);
        c.done = true;
        history.oath().contract = c;
        history.breakOath();
        assertNull(history.oath().contract);
        assertFalse(history.oath().sworn());
    }

    @Test
    public void breakingTheOathCallsForHunters() {
        int[] betrayed = {-1};
        history.onOathBroken((h, followed) -> betrayed[0] = h);
        history.favour().set(liege, 70);
        history.swear(liege);
        history.breakOath();
        assertEquals(liege, betrayed[0]);
    }
}
