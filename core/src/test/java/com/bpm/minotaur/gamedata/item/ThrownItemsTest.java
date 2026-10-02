package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
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

public class ThrownItemsTest {

    private Player player;
    private Maze maze;
    private CombatManager combat;

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
        SoundManager sound = new SoundManager() {
            @Override
            public void playWeaponSwing() {}
        };
        combat = new CombatManager(player, maze, null, new AnimationManager() {}, new GameEventManager(),
                sound, null, null, null, null, null);
    }

    private Item potion(PotionEffectType effect) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = "Test Potion";
        t.isPotion = true;
        Item item = Item.fromTemplate(Item.ItemType.POTION_BLUE, t);
        item.setTrueEffect(effect);
        return item;
    }

    private Monster monsterAt(float x, float y) {
        Monster m = new Monster(Monster.MonsterType.SKELETON, 20, 10, x, y);
        maze.addMonster(m);
        return m;
    }

    @Test
    public void aThrownHealingPotionHealsTheMonsterItHits() {
        Monster m = monsterAt(7f, 5f);
        m.takeDamage(10, com.bpm.minotaur.gamedata.DamageType.PHYSICAL, false);
        int before = m.getCurrentHP();
        Item p = potion(PotionEffectType.SUPREME_HEALING);
        player.getInventory().getQuickSlots()[0] = p;

        assertTrue(combat.throwItem(p));

        assertTrue("the monster is healed", m.getCurrentHP() > before);
        assertNull("the potion leaves the quick slot", player.getInventory().getQuickSlots()[0]);
        assertFalse("a potion shatters rather than landing", maze.getItems().containsValue(p));
    }

    @Test
    public void aThrownPoisonPotionPoisonsTheTargetAndItsNeighbours() {
        Monster target = monsterAt(7f, 5f);
        Monster neighbour = monsterAt(7f, 6f);
        Monster faraway = monsterAt(10f, 9f);
        Item p = potion(PotionEffectType.POISON);

        combat.throwItem(p);

        assertTrue(target.getStatusManager().hasEffect(StatusEffectType.POISONED));
        assertTrue("vapour reaches the next tile", neighbour.getStatusManager().hasEffect(StatusEffectType.POISONED));
        assertFalse(faraway.getStatusManager().hasEffect(StatusEffectType.POISONED));
    }

    @Test
    public void aSingleTargetPotionDoesNotSpreadToNeighbours() {
        Monster target = monsterAt(7f, 5f);
        Monster neighbour = monsterAt(7f, 6f);
        combat.throwItem(potion(PotionEffectType.SPEED));
        assertTrue(target.getStatusManager().hasEffect(StatusEffectType.HASTED));
        assertFalse(neighbour.getStatusManager().hasEffect(StatusEffectType.HASTED));
    }

    @Test
    public void aPotionThrownAtNothingJustShatters() {
        Item p = potion(PotionEffectType.POISON);
        player.getInventory().getQuickSlots()[1] = p;
        assertTrue(combat.throwItem(p));
        assertNull(player.getInventory().getQuickSlots()[1]);
        assertFalse(maze.getItems().containsValue(p));
    }

    @Test
    public void anOrdinaryItemLandsInTheMazeAndIsRecoverable() {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = "Ration";
        Item food = Item.fromTemplate(Item.ItemType.FOOD, t);
        player.getInventory().getQuickSlots()[2] = food;

        assertTrue(combat.throwItem(food));

        assertNull(player.getInventory().getQuickSlots()[2]);
        assertTrue("it lands where it can be picked up again", maze.getItems().containsValue(food));
    }

    @Test
    public void anItemLandingOnAnOccupiedTileIsPlacedBesideItNotLost() {
        ItemTemplate t = new ItemTemplate();
        Item first = Item.fromTemplate(Item.ItemType.FOOD, t);
        Item second = Item.fromTemplate(Item.ItemType.FOOD, t);
        combat.throwItem(first);
        GridPoint2 firstTile = null;
        for (java.util.Map.Entry<GridPoint2, Item> e : maze.getItems().entrySet()) {
            if (e.getValue() == first) firstTile = e.getKey();
        }
        assertNotNull(firstTile);

        combat.throwItem(second);

        assertTrue(maze.getItems().containsValue(first));
        assertTrue("the second item is not lost", maze.getItems().containsValue(second));
    }

    @Test
    public void aThrownLanternLightsTheFloorWhereItLands() {
        ItemTemplate t = new ItemTemplate();
        t.isBeltClip = true;
        Item lantern = Item.fromTemplate(Item.ItemType.BRASS_LANTERN, t);
        int lightsBefore = maze.getLights().size;
        combat.throwItem(lantern);
        assertTrue(maze.getItems().containsValue(lantern));
        assertEquals(lightsBefore + 1, maze.getLights().size);
    }

    @Test
    public void aWandIsNotHurledForDamageItJustLands() {
        ItemTemplate t = new ItemTemplate();
        t.isWeapon = true;
        t.damageDice = "1d6";
        Item wand = Item.fromTemplate(Item.ItemType.WAND, t);
        assertEquals(ThrowRules.Kind.OTHER, ThrowRules.kindOf(wand));
    }

    @Test
    public void anUnidentifiedPotionStillActsButKeepsItsSecret() {
        Monster m = monsterAt(7f, 5f);
        m.takeDamage(10, com.bpm.minotaur.gamedata.DamageType.PHYSICAL, false);
        int before = m.getCurrentHP();
        Item p = potion(PotionEffectType.SUPREME_HEALING);
        assertFalse(p.isIdentified());
        combat.throwItem(p);
        assertTrue(m.getCurrentHP() > before);
    }
}
