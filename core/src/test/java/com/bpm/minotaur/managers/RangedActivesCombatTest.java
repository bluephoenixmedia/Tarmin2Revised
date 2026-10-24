package com.bpm.minotaur.managers;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
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
import com.bpm.minotaur.rendering.Animation;
import com.bpm.minotaur.rendering.AnimationManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/**
 * The ranged Actives through the real combat code (#247), and Held Arrow's readied-action cases
 * from the #225 prototype: a foe stepping into the line, an archer shooting from inside it, and a
 * fast monster killed between its actions.
 */
public class RangedActivesCombatTest {

    private Player player;
    private Maze maze;
    private CombatManager combat;
    private MonsterAiManager ai;
    private final GameEventManager events = new GameEventManager();

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (proxy, method, args) -> null);
        }
        player = new Player(3f, 3f);
        player.setFacing(Direction.EAST);
        player.getStats().setMaxHP(500);
        player.getStats().setCurrentHP(500);
        maze = new Maze(1, new int[10][10]);
        combat = new CombatManager(player, maze, null, new AnimationManager() {
            @Override
            public void addAnimation(Animation animation) {
            }
        }, events, new HeadlessSoundManager(), new com.bpm.minotaur.gamedata.item.ItemDataManager(), null, null, null, null);
        combat.setArtRoll(() -> 0.0); // a reaction shot is never spoiled by its x0.7
        ai = new MonsterAiManager();
    }

    private void arm(Item.ItemType type, String family, CombatArt art, boolean ranged) {
        ItemTemplate t = new ItemTemplate();
        t.isWeapon = true;
        t.isRanged = ranged;
        t.isThrown = !ranged;
        t.range = 8;
        t.weaponFamily = family;
        t.damageDice = "1d2";
        player.getInventory().setRightHand(Item.fromTemplate(type, t));
        player.learnArt(art, null);
        player.getStats().getMastery().setRank(art.family, Rank.EXPERT);
    }

    /** An easy foe: AC -10, so every ordinary roll lands. */
    private Monster foeAt(int x, int y, int hp) {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 1;
        t.maxHP = hp;
        Monster m = new Monster(Monster.MonsterType.KOBOLD, hp, 0, x, y);
        m.setTemplate(t);
        m.setCurrentHP(hp);
        m.setArmorClass(-10);
        maze.addMonster(m);
        return m;
    }

    private void hunting(Monster m) {
        m.setState(Monster.MonsterState.HUNTING);
        m.setLastKnownTargetPos(new GridPoint2(3, 3));
    }

    private static GridPoint2 tile(Monster m) {
        return new GridPoint2((int) m.getPosition().x, (int) m.getPosition().y);
    }

    // --- Held Arrow ---

    @Test
    public void aFoeWalkingAlongTheLineTakesTheHeldArrow() {
        arm(Item.ItemType.BOW, "BOW", CombatArt.HELD_ARROW, true);
        player.getStats().setArrows(10);
        Monster foe = foeAt(7, 3, 200);
        hunting(foe);

        assertEquals(ActiveArts.Outcome.READIED, combat.useArt(0));
        ai.updateMonster(foe, maze, player, true, combat);

        assertEquals("it stepped along the line", new GridPoint2(6, 3), tile(foe));
        assertTrue("the shot struck", foe.getCurrentHP() < 200);
        assertEquals(9, player.getArrows());
        assertNull(player.getHeldArt());
        assertTrue(player.getArtCooldowns().isCooling(CombatArt.HELD_ARROW));
    }

    @Test
    public void aFoeWalkingOutsideTheLineDrawsNoShot() {
        arm(Item.ItemType.BOW, "BOW", CombatArt.HELD_ARROW, true);
        player.getStats().setArrows(10);
        Monster foe = foeAt(3, 7, 200); // straight south, off the line east
        hunting(foe);

        combat.useArt(0);
        ai.updateMonster(foe, maze, player, true, combat);

        assertEquals(200, foe.getCurrentHP());
        assertEquals(CombatArt.HELD_ARROW, player.getHeldArt());
        assertEquals(10, player.getArrows());
    }

    @Test
    public void anArcherShootingFromInsideTheLineIsShotFirst() {
        arm(Item.ItemType.BOW, "BOW", CombatArt.HELD_ARROW, true);
        player.getStats().setArrows(10);
        Monster archer = foeAt(6, 3, 1);
        archer.getTemplate().hasRangedAttack = true;
        combat.useArt(0);

        assertTrue("its action was spent dying", combat.performMonsterRangedAttack(archer));

        assertTrue(archer.getCurrentHP() <= 0);
        assertEquals("it never loosed", 500, player.getCurrentHP());
    }

    @Test
    public void aFastMonsterKilledByTheReactionShotSpendsNoMoreActions() {
        arm(Item.ItemType.BOW, "BOW", CombatArt.HELD_ARROW, true);
        player.getStats().setArrows(10);
        Monster bat = foeAt(8, 3, 1);
        hunting(bat);
        bat.setEnergy(TurnManager.BASE_TURN_COST * 4); // four actions banked
        combat.useArt(0);

        new TurnManager().processTurn(maze, player, ai, combat, null, events);

        assertTrue(bat.getCurrentHP() <= 0);
        assertFalse("a dead monster does not walk on, back onto the map", maze.getMonsters().containsValue(bat));
    }

    @Test
    public void aWaitKeepsTheArrowDrawnAndAStepLetsItLapse() {
        arm(Item.ItemType.BOW, "BOW", CombatArt.HELD_ARROW, true);
        player.getStats().setArrows(10);
        combat.useArt(0);
        combat.tickPlayerTimers(); // the turn it was drawn
        player.keepHold();
        combat.tickPlayerTimers(); // a wait
        assertEquals(CombatArt.HELD_ARROW, player.getHeldArt());

        combat.tickPlayerTimers(); // anything else
        assertNull(player.getHeldArt());
        assertEquals("the lapse starts the full cooldown", CombatArt.ACTIVE_COOLDOWN,
                player.getArtCooldowns().turnsLeft(CombatArt.HELD_ARROW));
    }

    // --- Nail in Place ---

    @Test
    public void nailInPlaceSpendsABoltAndPinsTheFoe() {
        arm(Item.ItemType.CROSSBOW, "CROSSBOW", CombatArt.NAIL_IN_PLACE, true);
        player.getStats().setArrows(5);
        Monster foe = foeAt(6, 3, 200);

        assertEquals(ActiveArts.Outcome.USED, combat.useArt(0));

        assertTrue(foe.isPinned());
        assertTrue(foe.getCurrentHP() < 200);
        assertEquals(4, player.getArrows());
    }

    // --- Marked Skull ---

    @Test
    public void markedSkullAimsThenTakesTheHeadAndTheGunMustReload() {
        arm(Item.ItemType.MUSKET, "FIREARM", CombatArt.MARKED_SKULL, true);
        player.getStats().setShot(3);
        Monster foe = foeAt(6, 3, 200);
        foe.setArmorClass(50); // no ordinary shot would land

        assertEquals(ActiveArts.Outcome.READIED, combat.useArt(0));
        assertEquals("aiming spends no shot", 3, player.getStats().getShot());
        assertEquals(ActiveArts.Outcome.USED, combat.useArt(0));

        assertTrue("certain, even through AC 50", foe.getCurrentHP() < 200);
        assertTrue(foe.isDazed());
        assertEquals(2, player.getStats().getShot());
        assertNotNull("the shot starts the reload", combat.getActiveReload());
    }

    @Test
    public void aGunStillReloadingCannotAim() {
        arm(Item.ItemType.MUSKET, "FIREARM", CombatArt.MARKED_SKULL, true);
        player.getStats().setShot(3);
        Monster foe = foeAt(6, 3, 200);
        combat.useArt(0);
        combat.useArt(0); // fired: now reloading
        player.getArtCooldowns().start(CombatArt.MARKED_SKULL, 0);

        assertEquals(ActiveArts.Outcome.NOT_LOADED, combat.useArt(0));
    }

    // --- Shattering Throw ---

    @Test
    public void shatteringThrowBurstsOnTheFoesBesideItsTargetAndOnTheAdjacentPlayer() {
        arm(Item.ItemType.DART, "THROWN", CombatArt.SHATTERING_THROW, false);
        Monster target = foeAt(4, 3, 200);
        Monster beyond = foeAt(5, 3, 200);
        Item dart = player.getInventory().getRightHand();

        assertEquals(ActiveArts.Outcome.USED, combat.useArt(0));

        assertTrue(target.getCurrentHP() < 200);
        assertTrue("the foe beside the target is caught", beyond.getCurrentHP() < 200);
        assertTrue("the player beside the target is caught", player.getCurrentHP() < 500);
        assertNotSame("the weapon was thrown", dart, player.getInventory().getRightHand());
    }
}
