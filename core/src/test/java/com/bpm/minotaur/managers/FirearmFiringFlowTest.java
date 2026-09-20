package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.firearm.FirearmProfile;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.headless.HeadlessSoundManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * End-to-end coverage of firing, which the unit tests around FirearmProfile,
 * ReloadChannel, PowderDampness and GunshotNoise cannot reach.
 *
 * <p>Those unit tests all passed while the feature was badly broken: firing in combat
 * fell down the melee branch and skipped the misfire roll, the noise and the range
 * check; the reload never advanced during a fight; firing into empty air was free and
 * repeatable; and the reload only started on one of four firing paths. Every one of
 * those defects lived in the seam between the pieces rather than in any piece.
 */
public class FirearmFiringFlowTest {

    private CombatManager combatManager;
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
        player = new Player(5f, 5f);
        player.setFacing(com.bpm.minotaur.gamedata.Direction.EAST);
        maze = new Maze(1, new int[16][16]);
        com.bpm.minotaur.rendering.AnimationManager animationManager =
                new com.bpm.minotaur.rendering.AnimationManager() {};
        combatManager = new CombatManager(player, maze, null, animationManager, new GameEventManager(),
                new HeadlessSoundManager(), null, null, null, null, null);
    }

    private static Item weapon(Item.ItemType type, String dice) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = type.name();
        t.isWeapon = true;
        t.isRanged = true;
        t.range = 10;
        t.damageDice = dice;
        return Item.fromTemplate(type, t);
    }

    private Item equipMusket() {
        Item musket = weapon(Item.ItemType.MUSKET, "1d12");
        player.getInventory().setRightHand(musket);
        return musket;
    }

    /** A target directly east of the player, within the weapon's reach. */
    private Monster targetInLine() {
        Monster m = new Monster(Monster.MonsterType.GOBLIN, 400, 30, 9f, 5f);
        maze.addMonster(m);
        return m;
    }

    /**
     * Returns control to the player as the monster's turn would, without touching the
     * reload. Firing passes the turn, so without this a second shot is refused by the
     * combat state and a reload test would pass for the wrong reason.
     */
    private void handControlBackToPlayer(Monster target) {
        combatManager.endCombat();
        combatManager.startCombat(target);
    }

    // ---------------------------------------------------------------- ammunition

    @Test
    public void testFiringAMusketSpendsShotAndNotArrows() {
        equipMusket();
        player.getStats().setShot(3);
        player.getStats().setArrows(7);

        combatManager.startCombat(targetInLine());
        combatManager.playerAttackInstant();

        assertEquals("a musket draws shot", 2, player.getStats().getShot());
        assertEquals("and must never touch the arrow pool", 7, player.getArrows());
    }

    @Test
    public void testFiringABowSpendsArrowsAndNotShot() {
        player.getInventory().setRightHand(weapon(Item.ItemType.BOW, "1d4"));
        player.getStats().setShot(3);
        player.getStats().setArrows(7);

        combatManager.startCombat(targetInLine());
        combatManager.playerAttackInstant();

        assertEquals(6, player.getArrows());
        assertEquals("a bow cannot fire musket balls", 3, player.getStats().getShot());
    }

    @Test
    public void testAnEmptyMusketCannotBeFired() {
        equipMusket();
        player.getStats().setShot(0);

        combatManager.startCombat(targetInLine());
        combatManager.playerAttackInstant();

        assertEquals(0, player.getStats().getShot());
        assertNull("a shot that never happened starts no reload", combatManager.getActiveReload());
    }

    // -------------------------------------------------------------------- reload

    @Test
    public void testEveryShotStartsAReload() {
        equipMusket();
        player.getStats().setShot(5);

        combatManager.startCombat(targetInLine());
        combatManager.playerAttackInstant();

        assertNotNull("a firearm that fires without reloading is just a bow",
                combatManager.getActiveReload());
        assertEquals(FirearmProfile.reloadTurns(Item.ItemType.MUSKET),
                combatManager.getActiveReload().getTurnsRemaining());
    }

    @Test
    public void testAReloadingMusketCannotFireAgain() {
        equipMusket();
        player.getStats().setShot(5);

        Monster target = targetInLine();
        combatManager.startCombat(target);
        combatManager.playerAttackInstant();
        int afterFirstShot = player.getStats().getShot();

        handControlBackToPlayer(target);
        combatManager.playerAttackInstant();

        assertEquals("the reload is the entire balance for a 2d8 weapon",
                afterFirstShot, player.getStats().getShot());
        assertNotNull(combatManager.getActiveReload());
    }

    @Test
    public void testTheReloadFinishesAfterItsFullLength() {
        equipMusket();
        player.getStats().setShot(5);
        combatManager.startCombat(targetInLine());
        combatManager.playerAttackInstant();

        int turns = FirearmProfile.reloadTurns(Item.ItemType.MUSKET);
        for (int i = 0; i < turns - 1; i++) {
            combatManager.tickReload();
            assertNotNull("finished after " + (i + 1) + " of " + turns + " turns",
                    combatManager.getActiveReload());
        }
        combatManager.tickReload();

        assertNull("loaded and ready", combatManager.getActiveReload());
    }

    @Test
    public void testAReloadedMusketFiresAgain() {
        equipMusket();
        player.getStats().setShot(5);
        Monster target = targetInLine();
        combatManager.startCombat(target);
        combatManager.playerAttackInstant();

        for (int i = 0; i < FirearmProfile.reloadTurns(Item.ItemType.MUSKET); i++) {
            combatManager.tickReload();
        }
        int beforeSecondShot = player.getStats().getShot();
        handControlBackToPlayer(target);
        combatManager.playerAttackInstant();

        assertEquals("a finished reload must actually let you fire",
                beforeSecondShot - 1, player.getStats().getShot());
    }

    /**
     * The reload is yours to abandon, not the enemy's to deny. A three-turn commitment
     * any goblin could erase would read as the game cheating.
     */
    @Test
    public void testTakingDamageDoesNotCancelTheReload() {
        equipMusket();
        player.getStats().setShot(5);
        combatManager.startCombat(targetInLine());
        combatManager.playerAttackInstant();

        for (int i = 0; i < FirearmProfile.reloadTurns(Item.ItemType.MUSKET); i++) {
            player.takeDamage(1, com.bpm.minotaur.gamedata.DamageType.PHYSICAL);
            combatManager.tickReload();
        }

        assertNull("being hit must not cost the reload", combatManager.getActiveReload());
    }

    @Test
    public void testThePlayerCanGiveUpTheReloadThemselves() {
        equipMusket();
        player.getStats().setShot(5);
        combatManager.startCombat(targetInLine());
        combatManager.playerAttackInstant();
        assertNotNull(combatManager.getActiveReload());

        combatManager.abandonReload();

        assertNull("walking away from a half-loaded gun is the player's call",
                combatManager.getActiveReload());
    }

    @Test
    public void testABowNeverReloads() {
        player.getInventory().setRightHand(weapon(Item.ItemType.BOW, "1d4"));
        player.getStats().setArrows(7);

        combatManager.startCombat(targetInLine());
        combatManager.playerAttackInstant();

        assertNull(combatManager.getActiveReload());
    }

    // ------------------------------------------------------------------- misfire

    /**
     * A misfire costs the shot but not the reload. Whether the powder catches or
     * fizzles, exactly one round leaves the pouch and the ramrod comes out -- so this
     * holds without having to force the roll.
     */
    @Test
    public void testSoakedPowderStillSpendsExactlyOneRoundAndReloads() {
        equipMusket();
        player.getStats().setPowderDampness(1f);

        for (int shot = 0; shot < 12; shot++) {
            player.getStats().setShot(5);
            combatManager.endCombat();
            combatManager.startCombat(targetInLine());

            combatManager.playerAttackInstant();

            assertEquals("wet or dry, one round per trigger pull", 4, player.getStats().getShot());
            assertNotNull("a fizzle still means reloading", combatManager.getActiveReload());

            for (int i = 0; i < FirearmProfile.reloadTurns(Item.ItemType.MUSKET); i++) {
                combatManager.tickReload();
            }
        }
    }

    // --------------------------------------------------------------------- noise

    @Test
    public void testFiringWakesWhatItCanBeHeardBy() {
        equipMusket();
        player.getStats().setShot(5);

        Monster sleeper = new Monster(Monster.MonsterType.GOBLIN, 20, 10, 12f, 5f);
        sleeper.setState(Monster.MonsterState.IDLE);
        maze.addMonster(sleeper);

        combatManager.startCombat(targetInLine());
        combatManager.playerAttackInstant();

        assertEquals("the report carries past sight",
                Monster.MonsterState.HUNTING, sleeper.getState());
    }

    // ------------------------------------------------------- firing at no target

    @Test
    public void testFiringIntoAnEmptyCorridorStillCostsTheRound() {
        equipMusket();
        player.getStats().setShot(5);

        // No monsters at all: combat is INACTIVE, so this is the opening-fire path.
        combatManager.playerAttackInstant();

        assertEquals("a wasted shot is still a spent shot", 4, player.getStats().getShot());
        assertNotNull("and still has to be reloaded", combatManager.getActiveReload());
    }

    @Test
    public void testOpeningFireEngagesADistantTarget() {
        equipMusket();
        player.getStats().setShot(5);
        Monster distant = targetInLine();

        combatManager.playerAttackInstant();

        assertNotNull("a ranged weapon must be able to start a fight", combatManager.getMonster());
        assertSame(distant, combatManager.getMonster());
        assertEquals(4, player.getStats().getShot());
    }
}
