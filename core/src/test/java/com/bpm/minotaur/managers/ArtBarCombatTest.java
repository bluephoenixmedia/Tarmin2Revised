package com.bpm.minotaur.managers;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.arts.ActiveArts;
import com.bpm.minotaur.gamedata.arts.CombatArt;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.headless.HeadlessSoundManager;
import com.bpm.minotaur.rendering.AnimationManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/** Battering Ram from the Art bar, through the real combat code (#245). */
public class ArtBarCombatTest {

    private Player player;
    private Maze maze;
    private CombatManager combat;
    private final GameEventManager events = new GameEventManager();

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (proxy, method, args) -> null);
        }
        player = new Player(3f, 3f);
        player.setFacing(Direction.EAST);
        maze = new Maze(1, new int[8][8]);
        combat = new CombatManager(player, maze, null, new AnimationManager() {
            @Override
            public void addAnimation(com.bpm.minotaur.rendering.Animation animation) {
            }
        }, events,
                new HeadlessSoundManager(), new com.bpm.minotaur.gamedata.item.ItemDataManager(), null, null, null, null);
        ItemTemplate t = new ItemTemplate();
        t.isWeapon = true;
        t.weaponFamily = "BLUNT";
        t.damageDice = "1d2";
        player.getInventory().setRightHand(Item.fromTemplate(Item.ItemType.CLUB, t));
        player.learnArt(CombatArt.BATTERING_RAM, null);
        player.getStats().getMastery().setRank(CombatArt.BATTERING_RAM.family, Rank.EXPERT);
    }

    private Monster foeAt(int x, int y) {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 1;
        t.maxHP = 200;
        Monster m = new Monster(Monster.MonsterType.KOBOLD, 200, 0, x, y);
        m.setTemplate(t);
        m.setCurrentHP(200);
        m.setArmorClass(0);
        maze.addMonster(m);
        return m;
    }

    @Test
    public void batteringRamFromTheBarKnocksBackDazesAndLogsALine() {
        Monster foe = foeAt(4, 3);

        assertEquals(ActiveArts.Outcome.USED, combat.useArt(0));

        assertEquals(new GridPoint2(5, 3), new GridPoint2((int) foe.getPosition().x, (int) foe.getPosition().y));
        assertTrue(foe.isDazed());
        assertTrue(foe.getCurrentHP() < 200);
        assertTrue(events.getMessageHistory().toString(),
                events.getMessageHistory().stream().anyMatch(m -> m.startsWith("Battering Ram knocks")));
        assertEquals(CombatArt.ACTIVE_COOLDOWN, player.getArtCooldowns().turnsLeft(CombatArt.BATTERING_RAM));
    }

    @Test
    public void aLeftHandArtSwingsTheLeftHandWeapon() {
        ItemTemplate sword = new ItemTemplate();
        sword.isWeapon = true;
        sword.weaponFamily = "BLADE";
        sword.damageDice = "1d2";
        Item club = player.getInventory().getRightHand();
        player.getInventory().setRightHand(Item.fromTemplate(Item.ItemType.SWORD, sword));
        player.getInventory().setLeftHand(club);
        Monster foe = foeAt(4, 3);
        int bluntHits = player.getStats().getMastery().toSave().getOrDefault("BLUNT", 0);

        assertEquals("Battering Ram rides in slot 3, the left hand's", ActiveArts.Outcome.USED, combat.useArt(2));

        assertTrue("the club struck", foe.getCurrentHP() < 200);
        assertEquals("the club's Family trained", bluntHits + 1,
                (int) player.getStats().getMastery().toSave().getOrDefault("BLUNT", 0));
        assertFalse(player.getStats().getMastery().toSave().containsKey("BLADE"));
    }

    @Test
    public void aCombatTurnCoolsTheArtsToo() {
        player.getArtCooldowns().start(CombatArt.BATTERING_RAM, 5);
        combat.tickPlayerTimers(); // the turn of use
        combat.tickPlayerTimers();
        assertEquals(4, player.getArtCooldowns().turnsLeft(CombatArt.BATTERING_RAM));
        combat.setCurrentState(CombatManager.CombatState.PLAYER_TURN);
        combat.passTurnToMonster();
        assertEquals(3, player.getArtCooldowns().turnsLeft(CombatArt.BATTERING_RAM));
    }

    private void wieldAndKnow(String family, CombatArt art) {
        ItemTemplate t = new ItemTemplate();
        t.isWeapon = true;
        t.weaponFamily = family;
        t.damageDice = "1d2";
        player.getInventory().setRightHand(Item.fromTemplate(Item.ItemType.SWORD, t));
        player.learnArt(art, null);
        player.getStats().getMastery().setRank(art.family, Rank.EXPERT);
    }

    @Test
    public void closingThrustReallyStepsThePlayerIn() {
        wieldAndKnow("BLADE", CombatArt.CLOSING_THRUST);
        Monster foe = foeAt(5, 3);

        assertEquals(ActiveArts.Outcome.USED, combat.useArt(0));

        assertEquals(new GridPoint2(4, 3), new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y));
        assertTrue(foe.getCurrentHP() < 200);
    }

    @Test
    public void closingThrustWillNotLungeThroughACreatureInTheWay() {
        wieldAndKnow("BLADE", CombatArt.CLOSING_THRUST);
        Monster near = foeAt(4, 3);
        near.setAllyTurns(50);
        foeAt(5, 3);

        assertEquals(ActiveArts.Outcome.NO_TARGET, combat.useArt(0));
        assertEquals(new GridPoint2(3, 3), new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y));
    }

    @Test
    public void aFoeReeledInDoesNotSetOffHoldTheLine() {
        wieldAndKnow("POLEARM", CombatArt.REEL_IN);
        player.learnArt(CombatArt.HOLD_THE_LINE, null);
        combat.setArtRoll(() -> 0.0);
        Monster foe = foeAt(5, 3);

        assertEquals(ActiveArts.Outcome.USED, combat.useArt(0));

        assertEquals(new GridPoint2(4, 3), new GridPoint2((int) foe.getPosition().x, (int) foe.getPosition().y));
        assertEquals("no free strike: being pulled in is not stepping in", 200, foe.getCurrentHP());
    }

    @Test
    public void aGrippedMonsterCannotWalkAway() {
        player.learnArt(CombatArt.IRON_GRIP, null);
        player.getStats().getMastery().setRank(CombatArt.IRON_GRIP.family, Rank.EXPERT);
        player.getInventory().setRightHand(null);
        Monster foe = foeAt(4, 3);
        assertEquals(ActiveArts.Outcome.USED, combat.useArt(0));
        // The player backs off: a free monster would follow.
        player.getPosition().set(1f, 3f);
        foe.setState(Monster.MonsterState.HUNTING);
        foe.setLastKnownTargetPos(new GridPoint2(1, 3));

        new MonsterAiManager().updateMonster(foe, maze, player, true, combat);

        assertEquals(new GridPoint2(4, 3), new GridPoint2((int) foe.getPosition().x, (int) foe.getPosition().y));
    }

    @Test
    public void anUngrippedMonsterFollows() {
        Monster foe = foeAt(4, 3);
        player.getPosition().set(1f, 3f);
        foe.setState(Monster.MonsterState.HUNTING);
        foe.setLastKnownTargetPos(new GridPoint2(1, 3));

        new MonsterAiManager().updateMonster(foe, maze, player, true, combat);

        assertEquals("the control for the grip test: free, it walks", 3, (int) foe.getPosition().x);
    }

    @Test
    public void aGrippedMonsterSwingsTwoWorse() {
        Monster foe = foeAt(4, 3);
        int free = CombatManager.calculateMonsterAttackBonus(foe);
        foe.applyGrip();
        assertEquals(free - Monster.GRIPPED_TO_HIT, CombatManager.calculateMonsterAttackBonus(foe));
    }

    @Test
    public void aWallBetweenThePlayerAndTheFoeMeansNoFoeInReach() {
        maze.setTile(3, 3, Direction.EAST.getWallMask());
        foeAt(4, 3);

        assertEquals(ActiveArts.Outcome.NO_TARGET, combat.useArt(0));
        assertFalse(player.getArtCooldowns().isCooling(CombatArt.BATTERING_RAM));
    }

    @Test
    public void theArtKeysAreCtrlOneToFourAndClashWithNothing() {
        for (int slot = 0; slot < 4; slot++) {
            String action = SettingsManager.artAction(slot);
            assertEquals(Input.Keys.NUM_1 + slot, SettingsManager.defaultKeyFor(action));
            assertNull(FixedKeys.clashFor(action, Input.Keys.NUM_1 + slot));
            assertTrue(SettingsManager.getInstance().getKeyBindingDescriptions().containsKey(action));
        }
        assertEquals("Ctrl+1", SettingsManager.keyLabel("ART_1", Input.Keys.NUM_1));
        assertEquals("1", SettingsManager.keyLabel("MOVE_FORWARD", Input.Keys.NUM_1));
    }
}
