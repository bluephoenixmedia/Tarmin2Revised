package com.bpm.minotaur.managers;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.headless.HeadlessSoundManager;
import com.bpm.minotaur.rendering.AnimationManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class RangedAttackPursuitTest {

    private CombatManager combatManager;
    private TurnManager turnManager;
    private MonsterAiManager aiManager;
    private GameEventManager eventManager;
    private Player player;
    private Maze maze;

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> null);
        }
        player = new Player(5f, 5f);
        player.setFacing(Direction.EAST);
        player.getStats().setDexterity(50);
        player.getStats().setStrength(50);
        player.getStats().setArrows(20);
        player.getStats().setShot(20);

        maze = new Maze(1, new int[16][16]);
        eventManager = new GameEventManager();
        turnManager = new TurnManager();
        aiManager = new MonsterAiManager();

        AnimationManager animationManager = new AnimationManager() {};
        combatManager = new CombatManager(player, maze, null, animationManager, eventManager,
                new HeadlessSoundManager(), null, null, null, null, null);
    }

    private static Item rangedWeapon(Item.ItemType type, String dice) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = type.name();
        t.isWeapon = true;
        t.isRanged = true;
        t.range = 10;
        t.damageDice = dice;
        return Item.fromTemplate(type, t);
    }

    @Test
    public void testMonsterPursuesPlayerAfterTakingRangedDamage() {
        Item crossbow = rangedWeapon(Item.ItemType.CROSSBOW, "1d8");
        player.getInventory().setRightHand(crossbow);

        // Ghoul 4 tiles east of player (player is at 5,5, ghoul is at 9,5)
        Monster ghoul = new Monster(Monster.MonsterType.GHOUL, 100, 30, 9f, 5f);
        ghoul.setArmorClass(0);
        ghoul.setState(Monster.MonsterState.IDLE);
        maze.addMonster(ghoul);

        // Player fires ranged attack directly east at the ghoul
        for (int i = 0; i < 5 && ghoul.getCurrentHP() == 100; i++) {
            combatManager.playerAttackInstant();
        }

        // Verify monster took damage and is registered in combatManager
        assertTrue("Monster should have taken damage", ghoul.getCurrentHP() < 100);
        assertEquals("CombatManager should track the engaged monster", ghoul, combatManager.getMonster());
        assertFalse("Stateless ranged combat must not be in modal turn-based combat", combatManager.isInCombat());

        // Verify monster is in HUNTING state targeting player's position
        assertEquals("Monster must transition to HUNTING state", Monster.MonsterState.HUNTING, ghoul.getState());
        assertEquals("Monster last known target position must be player position",
                new GridPoint2(5, 5), ghoul.getLastKnownTargetPos());

        // Process a world turn
        float initialX = ghoul.getPosition().x;
        turnManager.processTurn(maze, player, aiManager, combatManager, null, eventManager);

        // Monster should not be frozen: it must have received energy and stepped towards the player
        assertTrue("Monster must advance toward player on world turn (current x: " + ghoul.getPosition().x + ")",
                ghoul.getPosition().x < initialX);
    }

    @Test
    public void testModalCombatStillLocksMonsterToCombatManager() {
        Monster ghoul = new Monster(Monster.MonsterType.GHOUL, 100, 30, 6f, 5f);
        maze.addMonster(ghoul);

        // When explicitly in modal combat (e.g., turn-based menu combat)
        combatManager.startCombat(ghoul);
        assertTrue("CombatManager should be in combat", combatManager.isInCombat());
        assertEquals(ghoul, combatManager.getMonster());

        float initialEnergy = ghoul.getEnergy();
        float initialX = ghoul.getPosition().x;

        // In modal combat, TurnManager skips the engaged monster (actions are driven by CombatManager)
        turnManager.processTurn(maze, player, aiManager, combatManager, null, eventManager);

        assertEquals("Monster in modal combat must not gain energy from TurnManager",
                initialEnergy, ghoul.getEnergy(), 0.001f);
        assertEquals("Monster in modal combat must not move via TurnManager",
                initialX, ghoul.getPosition().x, 0.001f);
    }

    @Test
    public void testMonsterPursuesPlayerEvenIfRangedAttackWhiffs() {
        Item crossbow = rangedWeapon(Item.ItemType.CROSSBOW, "1d8");
        player.getInventory().setRightHand(crossbow);
        player.getStats().setDexterity(10);
        player.getStats().setStrength(10);

        // Ghoul with astronomically high AC (e.g. 99) so the attack roll is guaranteed to miss
        Monster ghoul = new Monster(Monster.MonsterType.GHOUL, 100, 99, 9f, 5f);
        ghoul.setState(Monster.MonsterState.IDLE);
        maze.addMonster(ghoul);

        combatManager.playerAttackInstant();

        // Monster took no damage because attack whiffed
        assertEquals("Monster HP should be untouched on whiff", 100, ghoul.getCurrentHP());
        // But monster is still alerted to the player's presence and direction of shot
        assertEquals("Monster must transition to HUNTING state on whiff", Monster.MonsterState.HUNTING, ghoul.getState());
        assertEquals("Monster last known target position must be player position",
                new GridPoint2(5, 5), ghoul.getLastKnownTargetPos());

        // Process a world turn
        float initialX = ghoul.getPosition().x;
        turnManager.processTurn(maze, player, aiManager, combatManager, null, eventManager);

        assertTrue("Monster must advance toward player even after missed shot (current x: " + ghoul.getPosition().x + ")",
                ghoul.getPosition().x < initialX);
    }

    @Test
    public void testMonsterPursuesPlayerAfterSpellDamage() {
        com.bpm.minotaur.gamedata.spells.SpellDataManager.getInstance().load();
        player.getStats().setMaxMP(100);
        player.getStats().setCurrentMP(100);

        // Ghoul 4 tiles east of player
        Monster ghoul = new Monster(Monster.MonsterType.GHOUL, 100, 10, 9f, 5f);
        ghoul.setState(Monster.MonsterState.IDLE);
        maze.addMonster(ghoul);

        // Cast MAGIC_MISSILE down facing
        boolean cast = com.bpm.minotaur.gamedata.spells.SpellExecutionEngine.castSpell(
                "MAGIC_MISSILE", player, maze, eventManager, combatManager);
        assertTrue("Spell cast must succeed", cast);

        assertTrue("Monster should have taken spell damage", ghoul.getCurrentHP() < 100);
        assertEquals("Monster must transition to HUNTING state", Monster.MonsterState.HUNTING, ghoul.getState());
        assertEquals("Monster last known target position must be player position",
                new GridPoint2(5, 5), ghoul.getLastKnownTargetPos());

        // Process a world turn
        float initialX = ghoul.getPosition().x;
        turnManager.processTurn(maze, player, aiManager, combatManager, null, eventManager);

        assertTrue("Monster must advance toward player on world turn after spell damage",
                ghoul.getPosition().x < initialX);
    }
}
