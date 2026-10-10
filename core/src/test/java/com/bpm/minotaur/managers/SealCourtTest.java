package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.boss.SealLord;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/** Plan T1.12: a seal lord's court, played out on real monsters. */
public class SealCourtTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static SealLord.Spec spec(SealLord.Behaviour... behaviours) {
        SealLord.Spec s = new SealLord.Spec();
        s.figureId = 7;
        s.houseId = 2;
        s.name = "Vora the Restorer of House Gullet";
        s.hpMult = 1f;
        s.damageBonus = 2;
        s.armorBonus = 1;
        for (SealLord.Behaviour b : behaviours) s.behaviours.add(b);
        return s;
    }

    private static Monster lord(SealLord.Spec spec, Maze maze) {
        Monster m = new Monster(Monster.MonsterType.MIND_FLAYER, 40, 15);
        m.setDamageDice("2d10+6");
        SealCourt.dressLord(m, spec, 1);
        maze.addMonster(m);
        return m;
    }

    @Test
    public void theCourtIsTwoStrataBelowAnUnclaimedSealSite() {
        assertTrue(SealCourt.isCourt(SealCourt.COURT_LEVEL, 2, false));
        assertFalse("seal already taken", SealCourt.isCourt(SealCourt.COURT_LEVEL, 2, true));
        assertFalse("not a seal road", SealCourt.isCourt(SealCourt.COURT_LEVEL, -1, false));
        assertFalse("the castle road has no seal", SealCourt.isCourt(SealCourt.COURT_LEVEL, 0, false));
        assertFalse("wrong depth", SealCourt.isCourt(1, 2, false));
    }

    @Test
    public void aDressedLordIsANamedMazeHouseBossThatHoldsItsGround() {
        Maze maze = new Maze(SealCourt.COURT_LEVEL, new int[12][12]);
        Monster m = lord(spec(), maze);
        assertEquals(Faction.MAZE_HOUSE, m.getFaction());
        assertEquals(2, m.getHouseId());
        assertEquals(7, m.getFigureId());
        assertEquals("Vora the Restorer of House Gullet", m.getDisplayName());
        assertEquals(SealLord.BASE_HP, m.getMaxHP());
        assertEquals(SealLord.BASE_HP, m.getCurrentHP());
        assertEquals("the frame's bite, not the body's", "2d8+5", m.getDamageDice());
        assertEquals(SealLord.BASE_ARMOR + 1, m.getArmorClass());
        assertTrue(m.holdsCourt());
    }

    @Test
    public void aWrathfulLordRagesOnceBelowHalf() {
        Maze maze = new Maze(SealCourt.COURT_LEVEL, new int[12][12]);
        Monster m = lord(spec(SealLord.Behaviour.BERSERK_AT_HALF), maze);
        int speed = m.getMoveSpeed();
        SealCourt.onTurn(m, maze);
        assertEquals("2d8+5", m.getDamageDice());
        m.setCurrentHP(50);
        SealCourt.onTurn(m, maze);
        assertEquals("2d8+9", m.getDamageDice());
        assertEquals(speed + SealCourt.RAGE_SPEED, m.getMoveSpeed());
        SealCourt.onTurn(m, maze);
        assertEquals("only once", "2d8+9", m.getDamageDice());
    }

    @Test
    public void aCravenLordCowersAndCallsItsRetinue() {
        Maze maze = new Maze(SealCourt.COURT_LEVEL, new int[12][12]);
        SealLord.Spec s = spec(SealLord.Behaviour.CALLS_RETINUE, SealLord.Behaviour.DUELIST);
        Monster m = lord(s, maze);
        Monster sword = new Monster(Monster.MonsterType.HOBGOBLIN, 26, 10);
        sword.getPosition().set(3, 3);
        SealCourt.dressRetainer(sword, new SealLord.Retainer(9, "Haskric, sworn to House Gullet", "HOBGOBLIN"), s, 1);
        maze.addMonster(sword);
        int armor = m.getArmorClass();

        assertTrue("an honourable lord's sword stands back", SealCourt.onTurn(sword, maze));
        m.setCurrentHP(30);
        SealCourt.onTurn(m, maze);
        assertEquals(armor + SealCourt.COWER_ARMOR, m.getArmorClass());
        assertEquals(Monster.MonsterState.HUNTING, sword.getState());
        assertFalse("the lord is wounded: the sword fights", SealCourt.onTurn(sword, maze));
    }

    @Test
    public void aZealousLordClosesItsWounds() {
        Maze maze = new Maze(SealCourt.COURT_LEVEL, new int[12][12]);
        Monster m = lord(spec(SealLord.Behaviour.REGENERATES), maze);
        m.setCurrentHP(60);
        SealCourt.onTurn(m, maze);
        assertEquals(60 + Math.max(1, SealLord.BASE_HP / SealCourt.REGEN_DIVISOR), m.getCurrentHP());
    }

    @Test
    public void aReloadedLordGetsItsTraitsBackFromTheHistory() {
        HistoryWorld w = HistorySimulator.prehistory(15L, catalog);
        SealLord.Spec s = SealLord.compose(w, 0, catalog);
        Monster spawned = new Monster(Monster.MonsterType.MIND_FLAYER, 40, 15);
        spawned.setDamageDice("2d10+6");
        SealCourt.dressLord(spawned, s, 1);

        // What a save keeps: type, health, identity. Everything else comes back from the history.
        Monster reloaded = new Monster(Monster.MonsterType.MIND_FLAYER, 40, 15);
        reloaded.setDamageDice("2d10+6");
        reloaded.setMaxHP(spawned.getMaxHP());
        reloaded.setFigureId(spawned.getFigureId());
        reloaded.setSealRoad(1);
        reloaded.setSealRole(Monster.SEAL_LORD);
        SealCourt.reapply(reloaded, w, catalog);

        assertEquals(spawned.getDisplayName(), reloaded.getDisplayName());
        assertEquals(spawned.getDamageDice(), reloaded.getDamageDice());
        assertEquals(spawned.getArmorClass(), reloaded.getArmorClass());
        assertEquals(spawned.getSealBehaviours(), reloaded.getSealBehaviours());
        assertEquals(spawned.getMaxHP(), reloaded.getMaxHP());
    }

    @Test
    public void aLordThatHasRagedStaysEnragedThroughAReload() {
        HistoryWorld w = HistorySimulator.prehistory(15L, catalog);
        SealLord.Spec s = SealLord.compose(w, 0, catalog);
        Monster spawned = new Monster(Monster.MonsterType.MIND_FLAYER, 40, 15);
        SealCourt.dressLord(spawned, s, 1);
        Monster reloaded = new Monster(Monster.MonsterType.MIND_FLAYER, 40, 15);
        reloaded.setFigureId(spawned.getFigureId());
        reloaded.setSealRoad(1);
        reloaded.setSealRole(Monster.SEAL_LORD);
        reloaded.setSealRageSpent(true);
        SealCourt.reapply(reloaded, w, catalog);
        assertEquals(SealLord.withDamageBonus(spawned.getDamageDice(), SealCourt.RAGE_DAMAGE), reloaded.getDamageDice());
        assertEquals(spawned.getMoveSpeed() + SealCourt.RAGE_SPEED, reloaded.getMoveSpeed());
    }

    @Test
    public void aCourtIsInSessionWhileItsLordLivesAndAdmitsNoStrays() {
        Maze maze = new Maze(SealCourt.COURT_LEVEL, new int[12][12]);
        assertFalse("an empty hall is no court", SealCourt.inSession(maze));
        Monster m = lord(spec(), maze);
        assertTrue(SealCourt.inSession(maze));
        m.setCurrentHP(0);
        assertFalse("the lord is dead: the Doom's stragglers may wander in again", SealCourt.inSession(maze));
    }

    @Test
    public void aLordTheSeekerFoughtGivesThemItsSealWhoeverStruckLast() {
        Maze maze = new Maze(SealCourt.COURT_LEVEL, new int[12][12]);
        Monster m = lord(spec(), maze);
        assertFalse("a seeker who never drew its blood has no claim", SealCourt.sealFallsToSeeker(m));
        m.markSeekerDrewBlood();
        assertTrue("the seal is on the body, and the seeker earned it", SealCourt.sealFallsToSeeker(m));
        Monster sword = new Monster(Monster.MonsterType.HOBGOBLIN, 26, 10);
        sword.markSeekerDrewBlood();
        assertFalse("only a seal's holder carries one", SealCourt.sealFallsToSeeker(sword));
    }

    @Test
    public void whoeverElseKillsTheLordTakesTheSealAndMustBeKilledForIt() {
        Maze maze = new Maze(SealCourt.COURT_LEVEL, new int[12][12]);
        Monster m = lord(spec(), maze);
        Monster rival = new Monster(Monster.MonsterType.HOBGOBLIN, 26, 10);
        rival.setDisplayName("Grask");

        m.setCurrentHP(0);
        SealCourt.takeSeal(rival, m, "the Weeping Gash");
        assertEquals(Monster.SEAL_BEARER, rival.getSealRole());
        assertEquals(m.getSealRoad(), rival.getSealRoad());
        assertTrue(rival.getDisplayName(), rival.getDisplayName().contains("Grask") && rival.getDisplayName().contains("seal"));
        assertFalse("a bearer is no court", SealCourt.inSession(maze));

        // And the next to kill the bearer, if not the seeker, takes it in turn.
        Monster next = new Monster(Monster.MonsterType.HOBGOBLIN, 26, 10);
        next.setDisplayName("Vell");
        SealCourt.takeSeal(next, rival, "the Weeping Gash");
        assertEquals(Monster.SEAL_BEARER, next.getSealRole());
        assertTrue("a bearer the seeker struck gives the seal up", SealCourt.sealFallsToSeeker(markStruck(next)));
    }

    private static Monster markStruck(Monster m) {
        m.markSeekerDrewBlood();
        return m;
    }

    @Test
    public void slayingABearerYieldsTheSeal() {
        HistoryManager history = HistoryManager.create(15L, catalog);
        com.bpm.minotaur.gamedata.shelter.ShelterNetwork net = com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance();
        Monster bearer = new Monster(Monster.MonsterType.HOBGOBLIN, 26, 10);
        bearer.setSealRoad(1);
        bearer.setSealRole(Monster.SEAL_BEARER);
        bearer.setDisplayName("Grask, bearing a seal");
        net.resetForNewGame();
        String said = SealCourt.onSlain(bearer, history, net);
        assertNotNull("no figure of the history, but it carried the seal", said);
        assertTrue(net.hasSeal(1));
        assertEquals(1, net.getSealCount());
        net.resetForNewGame();
    }

    @Test
    public void theSealGateNamesItsLord() {
        HistoryWorld w = HistorySimulator.prehistory(16L, catalog);
        String line = SealCourt.knockLine(w, 2, catalog, false);
        assertTrue(line, line.contains(SealLord.compose(w, 1, catalog).name));
        assertTrue(line, line.contains(w.gashName(1)));
        assertTrue(SealCourt.knockLine(w, 2, catalog, true).contains("already hold"));
    }

    @Test
    public void slayingTheLordYieldsTheSealAndEntersTheChronicle() {
        com.bpm.minotaur.gamedata.shelter.ShelterNetwork net = com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance();
        net.resetForNewGame();
        HistoryManager history = HistoryManager.create(17L, catalog);
        SealLord.Spec s = SealLord.compose(history.world(), 2, catalog);
        Monster m = new Monster(Monster.MonsterType.MIND_FLAYER, 40, 15);
        SealCourt.dressLord(m, s, 3);

        String told = SealCourt.onSlain(m, history, net);

        assertTrue(net.hasSeal(3));
        assertEquals(1, net.getSealCount());
        assertTrue(told, told.contains(history.world().gashName(2)));
        assertFalse(history.world().figure(s.figureId).isAlive());
        assertEquals(com.bpm.minotaur.gamedata.history.EventType.SLAIN_BY_PLAYER,
                history.world().events().get(history.world().events().size() - 1).type);
        net.resetForNewGame();
    }

    @Test
    public void aRetainerIsChronicledButCarriesNoSeal() {
        com.bpm.minotaur.gamedata.shelter.ShelterNetwork net = com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance();
        net.resetForNewGame();
        HistoryManager history = HistoryManager.create(18L, catalog);
        SealLord.Spec s = SealLord.compose(history.world(), 0, catalog);
        org.junit.Assume.assumeFalse(s.retinue.isEmpty());
        Monster sword = new Monster(Monster.MonsterType.HOBGOBLIN, 26, 10);
        SealCourt.dressRetainer(sword, s.retinue.get(0), s, 1);

        assertNull(SealCourt.onSlain(sword, history, net));
        assertEquals(0, net.getSealCount());
        assertFalse(history.world().figure(s.retinue.get(0).figureId).isAlive());
    }

    @Test
    public void aLordWhoLostTheGashIsDeposedWithItsRetinue() {
        HistoryWorld w = HistorySimulator.prehistory(19L, catalog);
        SealLord.Spec current = SealLord.compose(w, 0, catalog);
        SealLord.Spec stale = spec();
        stale.figureId = current.figureId + 1000;
        Maze maze = new Maze(SealCourt.COURT_LEVEL, new int[12][12]);
        Monster old = lord(stale, maze);
        Monster sword = new Monster(Monster.MonsterType.HOBGOBLIN, 26, 10);
        sword.getPosition().set(4, 4);
        SealCourt.dressRetainer(sword, new SealLord.Retainer(5, "Brann, sworn", "HOBGOBLIN"), stale, 1);
        maze.addMonster(sword);

        java.util.List<Monster> gone = SealCourt.deposed(maze, w, catalog);
        assertTrue(gone.contains(old));
        assertTrue(gone.contains(sword));

        Maze fresh = new Maze(SealCourt.COURT_LEVEL, new int[12][12]);
        Monster sitting = new Monster(Monster.MonsterType.MIND_FLAYER, 40, 15);
        SealCourt.dressLord(sitting, current, 1);
        fresh.addMonster(sitting);
        assertTrue("the rightful lord stays", SealCourt.deposed(fresh, w, catalog).isEmpty());
    }
}
