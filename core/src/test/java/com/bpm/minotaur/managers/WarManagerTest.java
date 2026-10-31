package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.war.army.ArmyBattle;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import static org.junit.Assert.*;

/** Plan T2.3, Living War A21, A26: the horns, or none, and what going away means. */
public class WarManagerTest {

    private static DoctrineCatalog catalog;
    private BattleFixture fixture;
    private HistoryManager history;
    private WarManager wars;
    private WarManager.Ground ground;
    private final BattleDirector.Spoils spoils = (maze, near, loser, lordFell) -> { };

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Before
    public void atWar() {
        fixture = new BattleFixture(50L, catalog, 30);
        history = fixture.history;
        wars = new WarManager(catalog);
        ground = new WarManager.Ground();
        ground.chunk = new GridPoint2(BattleFixture.X, BattleFixture.Y);
        ground.level = 1;
        ground.layer = fixture.layer;
        ground.battle = fixture.battle;
        ground.maze = new Maze(1, new int[36][36]);
        ground.playerTile = new GridPoint2(18, 18);
    }

    private WarManager.Turn turn() {
        history.tickWarClock();
        return wars.onTurn(history, ground, BattleFixture::soldier, spoils);
    }

    @Test
    public void aBattleBeginningAroundThePlayerSoundsTheHorns() {
        WarManager.Turn t = turn();
        assertNotNull(wars.active());
        assertTrue(t.messages.get(0).startsWith("War-horns"));
        assertTrue("and the horns are heard (plan T2.9)", t.cues.contains(WarManager.Cue.HORNS));
        assertFalse("nothing to choose yet", t.offerSides);
    }

    @Test
    public void walkingIntoOneUnderWayGivesNoWarningAndAsksWhereYouStand() {
        fixture.battle.turn = 20;
        WarManager.Turn t = turn();
        assertFalse(t.cues.contains(WarManager.Cue.HORNS));
        assertTrue(t.offerSides);
        assertEquals(BattleDirector.Phase.BATTLE, wars.active().phase());
        assertTrue("the lines are there at once", !ground.maze.getMonsters().isEmpty());
    }

    @Test
    public void thePromptComesAsTheLinesCloseAndOnlyOnce() {
        int offers = 0;
        for (int i = 0; i < BattleDirector.WARNING_TURNS + 5; i++) {
            if (turn().offerSides) offers++;
        }
        assertEquals(1, offers);
    }

    @Test
    public void leavingOnlyStopsTheWatching() {
        turn();
        for (int i = 0; i < 5; i++) turn();
        ground.chunk = new GridPoint2(6, 5);
        ground.battle = null;
        WarManager.Turn t = turn();
        assertNull(wars.active());
        assertTrue(t.messages.get(0).contains("slip away"));
        assertTrue(ground.maze.getMonsters().isEmpty());
        assertNull("the numbers go on alone", fixture.layer.watchedBattle());
        assertTrue(history.roster().battles.contains(fixture.battle));
    }

    @Test
    public void goingBelowLetsItGoToo() {
        turn();
        ground.level = 2;
        turn();
        assertNull(wars.active());
        assertNull(fixture.layer.watchedBattle());
    }

    @Test
    public void noBattleOnSanctuaryGround() {
        ground.sanctuary = true;
        turn();
        assertNull(wars.active());
    }

    @Test
    public void aSleepEndsTheWatchingAndTheNextTurnFindsWhatIsLeft() {
        fixture.battle.turn = 20;
        turn();
        turn();
        fixture.layer.watchBattle(null);
        fixture.armyA().soldiers = 12;
        turn();
        assertEquals("raised again from the numbers", fixture.battle, wars.active().battle);
        assertEquals(12, wars.active().side(true).size());
    }

    @Test
    public void stayingFightsItAndItsOutcomeEntersTheHistoryOnce() {
        turn();
        Random rng = new Random(5);
        java.util.List<WarManager.Cue> heard = new ArrayList<>();
        ArmyBattle b = fixture.battle;
        for (int i = 0; i < 600 && wars.active() != null; i++) {
            heard.addAll(turn().cues);
            for (Monster m : new ArrayList<>(ground.maze.getMonsters().values())) {
                if (m.isWarBand() && rng.nextInt(10) == 0) {
                    m.setCurrentHP(0);
                    ground.maze.removeMonster(m);
                }
            }
        }
        assertNull("the battle is over", wars.active());
        assertFalse(history.roster().battles.contains(b));
        assertEquals("the drums once as the lines close, and the rout once (plan T2.9)",
                java.util.Arrays.asList(WarManager.Cue.JOINED, WarManager.Cue.ROUT), heard);
        assertEquals(1, fixture.layer.battlesEnded());
    }
}
