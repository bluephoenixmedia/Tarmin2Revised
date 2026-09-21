package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.SoundManager;
import com.bpm.minotaur.rendering.AnimationManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class QuickSlotWeaponThrowTest {

    private Player player;
    private Maze maze;
    private GameEventManager eventManager;
    private CombatManager combatManager;

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
        maze = new Maze(1, new int[12][12]);
        eventManager = new GameEventManager();

        AnimationManager animationManager = new AnimationManager() {};
        SoundManager soundManager = new SoundManager() {
            @Override
            public void playWeaponSwing() {}
            @Override
            public void playCombatStartSound() {}
            @Override
            public void playMonsterAttackSound(Monster monster) {}
        };

        combatManager = new CombatManager(player, maze, null, animationManager, eventManager,
                soundManager, null, null, null, null, null);
    }

    private Item createWeapon(String name, String damageDice) {
        ItemTemplate template = new ItemTemplate();
        template.friendlyName = name;
        template.isWeapon = true;
        template.damageDice = damageDice;
        return Item.fromTemplate(Item.ItemType.DAGGER, template);
    }

    private Item createShield(String name) {
        ItemTemplate template = new ItemTemplate();
        template.friendlyName = name;
        template.isShield = true;
        return Item.fromTemplate(Item.ItemType.SMALL_SHIELD, template);
    }

    @Test
    public void testQuickSlotWeaponThrowWithCombatManager() {
        // Player holds a main weapon in Right Hand
        Item mainSword = createWeapon("Broadsword", "1d8");
        player.getInventory().setRightHand(mainSword);

        // Quick slot 0 holds a throwing dagger
        Item dagger = createWeapon("Throwing Dagger", "1d4");
        player.getInventory().getQuickSlots()[0] = dagger;

        // Place a monster in front of player (facing EAST: player at (5,5), monster at (7,5))
        Monster monster = new Monster(Monster.MonsterType.SKELETON, 20, 10, 7f, 5f);
        maze.addMonster(monster);

        // Press quick slot 1 (index 0)
        boolean used = player.useQuickSlot(0, eventManager, null, maze, combatManager);

        assertTrue("Using quick slot containing weapon must succeed and throw", used);
        assertNull("Quick slot 0 must be empty after throwing weapon", player.getInventory().getQuickSlots()[0]);
        assertSame("Main weapon in Right Hand must remain equipped and not swapped", mainSword, player.getInventory().getRightHand());

        // The thrown weapon must have landed in the maze
        assertNotNull("Thrown weapon must be placed in the maze upon hit or landing", maze.getItems());
        assertTrue("Maze must contain the thrown weapon", maze.getItems().containsValue(dagger));
    }

    @Test
    public void testQuickSlotWeaponThrowWithoutCombatManager() {
        Item axe = createWeapon("Handaxe", "1d6");
        player.getInventory().getQuickSlots()[1] = axe;

        boolean used = player.useQuickSlot(1, eventManager, null, maze, null);

        assertTrue("Using quick slot containing weapon without combatManager must succeed", used);
        assertNull("Quick slot 1 must be emptied", player.getInventory().getQuickSlots()[1]);
        assertTrue("Maze must contain the thrown axe", maze.getItems().containsValue(axe));
    }

    @Test
    public void testShieldInQuickSlotStillSwaps() {
        Item shield = createShield("Round Shield");
        player.getInventory().getQuickSlots()[2] = shield;

        boolean used = player.useQuickSlot(2, eventManager, null, maze, combatManager);

        assertTrue(used);
        assertSame("Shield must be swapped to Left Hand", shield, player.getInventory().getLeftHand());
        assertNull("Quick slot 2 must hold previous left hand item (null)", player.getInventory().getQuickSlots()[2]);
    }

    @Test
    public void testEmptyQuickSlotReturnsFalse() {
        boolean used = player.useQuickSlot(0, eventManager, null, maze, combatManager);
        assertFalse("Empty quick slot must return false", used);
    }
}
