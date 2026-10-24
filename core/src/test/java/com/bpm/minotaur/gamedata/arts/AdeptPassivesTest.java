package com.bpm.minotaur.gamedata.arts;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.WeaponFamily;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.telemetry.TelemetryManager;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** The ten Adept Passives: six melee (#243) and four ranged (#244), spec section 2. */
public class AdeptPassivesTest {

    /** Records the free strikes the Passives ask for. */
    private final List<Monster> struck = new ArrayList<>();
    private final List<String> words = new ArrayList<>();
    private final AdeptPassives.FreeStrike strike = (target, word) -> {
        struck.add(target);
        words.add(word);
    };

    private Player player;
    private Maze maze;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        TelemetryManager.getInstance().startNewRun();
        player = new Player(3f, 3f); // stands on tile (3, 3)
        maze = new Maze(1, new int[8][8]);
    }

    /** Rolls always under the chance: every Passive that can fire, fires. */
    private static AdeptPassives alwaysFires() {
        return new AdeptPassives(() -> 0.0);
    }

    private static AdeptPassives neverFires() {
        return new AdeptPassives(() -> 0.999);
    }

    private static Item weapon(String family) {
        ItemTemplate t = new ItemTemplate();
        t.isWeapon = true;
        t.weaponFamily = family;
        return Item.fromTemplate(Item.ItemType.SWORD, t);
    }

    private Monster monsterAt(int x, int y) {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 1;
        t.maxHP = 30; // setTemplate copies HP from the template
        Monster m = new Monster(Monster.MonsterType.KOBOLD, 30, 10, x, y);
        m.setTemplate(t);
        m.setCurrentHP(30);
        maze.addMonster(m);
        return m;
    }

    private void know(CombatArt art) {
        player.learnArt(art, null);
        player.getStats().getMastery().setRank(art.family, Rank.ADEPT);
    }

    @Test
    public void anArtTheSoulDoesNotKnowNeverFires() {
        Monster foe = monsterAt(4, 3);
        assertNull(alwaysFires().onFullHit(player, weapon("BLUNT"), foe, maze, 6, strike));
        assertFalse(foe.isDazed());
    }

    @Test
    public void aPassiveFiresOnlyForTheWieldedFamily() {
        know(CombatArt.RINGING_BLOW);
        Monster foe = monsterAt(4, 3);
        assertNull("a sword is no club", alwaysFires().onFullHit(player, weapon("BLADE"), foe, maze, 6, strike));
        assertFalse(foe.isDazed());
    }

    @Test
    public void aRollOverTheChanceDoesNothing() {
        know(CombatArt.RINGING_BLOW);
        Monster foe = monsterAt(4, 3);
        assertNull(neverFires().onFullHit(player, weapon("BLUNT"), foe, maze, 6, strike));
        assertFalse(foe.isDazed());
    }

    @Test
    public void ringingBlowDazesThenStuns() {
        know(CombatArt.RINGING_BLOW);
        Monster foe = monsterAt(4, 3);
        AdeptPassives passives = alwaysFires();

        assertEquals("DAZE", passives.onFullHit(player, weapon("BLUNT"), foe, maze, 6, strike));
        assertTrue(foe.isDazed());
        assertEquals("STUN", passives.onFullHit(player, weapon("BLUNT"), foe, maze, 6, strike));
        assertTrue(foe.isStunned());
    }

    @Test
    public void cutTheSinewBleedsAndSlows() {
        know(CombatArt.CUT_THE_SINEW);
        Monster foe = monsterAt(4, 3);

        assertEquals("BLEED SLOW", alwaysFires().onFullHit(player, weapon("DAGGER"), foe, maze, 4, strike));
        assertTrue(foe.getBleedTurns() > 0);
        assertTrue(foe.getStatusManager().hasEffect(StatusEffectType.SLOWED));
    }

    @Test
    public void quickKnucklesLandsHalfTheBlowAgain() {
        know(CombatArt.QUICK_KNUCKLES);
        Monster foe = monsterAt(4, 3);
        int hp = foe.getCurrentHP();

        assertEquals("+3 BLOW", alwaysFires().onFullHit(player, null, foe, maze, 6, strike));
        assertEquals(hp - 3, foe.getCurrentHP());
    }

    @Test
    public void wideChopStrikesEveryOtherFoeBesideYouButNotOneFurtherOff() {
        know(CombatArt.WIDE_CHOP);
        Monster target = monsterAt(4, 3);
        Monster flank = monsterAt(3, 4);
        Monster behind = monsterAt(2, 3);
        Monster distant = monsterAt(6, 3);

        assertEquals("+1 CUT", alwaysFires().onFullHit(player, weapon("AXE"), target, maze, 5, strike));
        assertTrue(struck.contains(flank));
        assertTrue(struck.contains(behind));
        assertFalse("the one already hit", struck.contains(target));
        assertFalse(struck.contains(distant));
    }

    @Test
    public void wideChopWithNoOneElseBesideYouIsNoProc() {
        know(CombatArt.WIDE_CHOP);
        Monster target = monsterAt(4, 3);
        assertNull(alwaysFires().onFullHit(player, weapon("AXE"), target, maze, 5, strike));
    }

    @Test
    public void answeringSteelCountersAMissedMeleeBlow() {
        know(CombatArt.ANSWERING_STEEL);
        Monster foe = monsterAt(4, 3);

        alwaysFires().onMonsterMeleeMiss(player, weapon("BLADE"), foe, strike);

        assertEquals(List.of(foe), struck);
        assertEquals(List.of("COUNTER"), words);
    }

    @Test
    public void holdTheLineStrikesFirstOnlyTheFirstTimeAMonsterCloses() {
        know(CombatArt.HOLD_THE_LINE);
        Monster foe = monsterAt(4, 3);
        AdeptPassives passives = alwaysFires();

        passives.onMonsterStepsAdjacent(player, weapon("POLEARM"), foe, strike);
        passives.onMonsterStepsAdjacent(player, weapon("POLEARM"), foe, strike);

        assertEquals("once per monster", List.of(foe), struck);
        assertEquals(List.of("FIRST STRIKE"), words);
    }

    @Test
    public void everyProcCountsInTelemetry() {
        know(CombatArt.RINGING_BLOW);
        Monster foe = monsterAt(4, 3);
        alwaysFires().onFullHit(player, weapon("BLUNT"), foe, maze, 6, strike);

        assertTrue(TelemetryManager.getInstance().toJson().contains("\"RINGING_BLOW\": 1"));
    }

    // --- The ranged Passives (#244) ---

    @Test
    public void swiftNockLoosesASecondArrowAtTheSameTarget() {
        know(CombatArt.SWIFT_NOCK);
        Monster foe = monsterAt(6, 3);

        String word = alwaysFires().onFullHit(player, weapon("BOW"), foe, maze, 5, strike);

        assertEquals(List.of(foe), struck);
        assertEquals(List.of(AdeptPassives.SECOND_ARROW), words);
        assertNull("the second arrow carries the word, not the first", word);
        assertTrue(TelemetryManager.getInstance().toJson().contains("\"SWIFT_NOCK\": 1"));
    }

    @Test
    public void staggeringBoltDazesAndThenStuns() {
        know(CombatArt.STAGGERING_BOLT);
        Monster foe = monsterAt(6, 3);
        AdeptPassives passives = alwaysFires();

        assertEquals("DAZE", passives.onFullHit(player, weapon("CROSSBOW"), foe, maze, 5, strike));
        assertEquals("a dazed target is stunned", "STUN", passives.onFullHit(player, weapon("CROSSBOW"), foe, maze, 5, strike));
        assertTrue(TelemetryManager.getInstance().toJson().contains("\"STAGGERING_BOLT\": 2"));
    }

    @Test
    public void practisedHandsCutsTheNextReloadOnce() {
        know(CombatArt.PRACTISED_HANDS);
        Monster foe = monsterAt(6, 3);

        assertEquals(AdeptPassives.QUICK_LOAD, alwaysFires().onFullHit(player, weapon("FIREARM"), foe, maze, 9, strike));

        assertTrue(player.takeReloadCut());
        assertFalse("one hit cuts one reload", player.takeReloadCut());
        assertTrue(TelemetryManager.getInstance().toJson().contains("\"PRACTISED_HANDS\": 1"));
    }

    @Test
    public void aReloadCutNeverDropsBelowOneTurn() {
        com.bpm.minotaur.gamedata.firearm.ReloadChannel full =
                new com.bpm.minotaur.gamedata.firearm.ReloadChannel(Item.ItemType.MUSKET);
        com.bpm.minotaur.gamedata.firearm.ReloadChannel cut =
                new com.bpm.minotaur.gamedata.firearm.ReloadChannel(Item.ItemType.MUSKET, 1);
        assertEquals(Math.max(1, full.getTurnsRequired() - 1), cut.getTurnsRequired());
        assertEquals(1, new com.bpm.minotaur.gamedata.firearm.ReloadChannel(Item.ItemType.MUSKET, 99).getTurnsRequired());
    }

    @Test
    public void tangleSlowsOnAThrownHitWithAnyWeapon() {
        know(CombatArt.TANGLE);
        Monster foe = monsterAt(6, 3);

        assertEquals(AdeptPassives.TANGLED, alwaysFires().onThrownHit(player, weapon("BLADE"), foe));

        assertTrue(foe.getStatusManager().hasEffect(StatusEffectType.SLOWED));
        assertTrue(TelemetryManager.getInstance().toJson().contains("\"TANGLE\": 1"));
    }

    @Test
    public void tangleIsAThrowArtNotASwingArt() {
        know(CombatArt.TANGLE);
        Monster foe = monsterAt(4, 3);

        assertNull("a dagger in the hand is a Blade", alwaysFires().onFullHit(player, weapon("BLADE"), foe, maze, 5, strike));
        assertFalse(foe.getStatusManager().hasEffect(StatusEffectType.SLOWED));
    }

    @Test
    public void theRangedPassivesRespectTheRoll() {
        for (CombatArt art : new CombatArt[]{CombatArt.SWIFT_NOCK, CombatArt.STAGGERING_BOLT, CombatArt.PRACTISED_HANDS, CombatArt.TANGLE}) {
            know(art);
        }
        Monster foe = monsterAt(6, 3);
        AdeptPassives passives = neverFires();
        for (String family : new String[]{"BOW", "CROSSBOW", "FIREARM"}) {
            assertNull(family, passives.onFullHit(player, weapon(family), foe, maze, 5, strike));
        }
        assertNull(passives.onThrownHit(player, weapon("BLADE"), foe));
        assertTrue(struck.isEmpty());
        assertFalse(player.takeReloadCut());
    }

    @Test
    public void anUnknownRangedArtNeverFires() {
        Monster foe = monsterAt(6, 3);
        assertNull(alwaysFires().onFullHit(player, weapon("BOW"), foe, maze, 5, strike));
        assertNull(alwaysFires().onThrownHit(player, weapon("BLADE"), foe));
        assertTrue(struck.isEmpty());
    }
}
