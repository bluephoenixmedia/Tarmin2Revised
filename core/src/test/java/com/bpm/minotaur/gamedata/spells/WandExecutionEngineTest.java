package com.bpm.minotaur.gamedata.spells;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.WandEffectType;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.DiscoveryManager;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class WandExecutionEngineTest {

    private Player player;
    private Maze maze;
    private DiscoveryManager discoveryManager;
    private GameEventManager eventManager;

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> null);
        }

        // 12x12 maze filled with floor (0) and perimeter walls (1)
        int[][] walls = new int[12][12];
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) {
                if (x == 0 || x == 11 || y == 0 || y == 11) {
                    walls[y][x] = 1;
                } else {
                    walls[y][x] = 0;
                }
            }
        }
        maze = new Maze(1, walls);
        player = new Player(2f, 2f);
        player.setFacing(Direction.EAST);
        eventManager = new GameEventManager();
        discoveryManager = new DiscoveryManager(eventManager);
    }

    private Item createWand(WandEffectType effect, int charges) {
        Item item = new Item(Item.ItemType.WAND, 0, 0, null, null, null);
        item.setName("Wand");
        item.setWandEffect(effect);
        item.setCharges(charges);
        return item;
    }

    @Test
    public void testItemIsWandHelper() {
        Item wand1 = new Item(Item.ItemType.WAND, 0, 0, null, null, null);
        assertTrue("ItemType.WAND must report isWand() true", wand1.isWand());

        Item wandMM = new Item(Item.ItemType.WAND_OF_MAGIC_MISSILES, 0, 0, null, null, null);
        assertTrue("ItemType.WAND_OF_MAGIC_MISSILES must report isWand() true", wandMM.isWand());

        Item wandA = new Item(Item.ItemType.WAND_A, 0, 0, null, null, null);
        assertTrue("ItemType.WAND_A must report isWand() true", wandA.isWand());

        Item sword = new Item(Item.ItemType.SWORD, 0, 0, null, null, null);
        assertFalse("Sword must not be a wand", sword.isWand());

        Item customWand = new Item(Item.ItemType.STICK, 0, 0, null, null, null);
        customWand.setWandEffect(WandEffectType.FIRE);
        assertTrue("Item with wandEffect must report isWand() true", customWand.isWand());
    }

    @Test
    public void testZapWandDepletedFails() {
        Item wand = createWand(WandEffectType.FIRE, 0);
        boolean fired = WandExecutionEngine.zapWand(wand, player, Direction.EAST, maze,
                discoveryManager, eventManager, null);
        assertFalse("Depleted wand must not fire", fired);
        assertEquals("Charges must remain 0", 0, wand.getCharges());
    }

    @Test
    public void testZapWandDecrementsCharges() {
        Item wand = createWand(WandEffectType.FIRE, 5);
        boolean fired = WandExecutionEngine.zapWand(wand, player, Direction.EAST, maze,
                discoveryManager, eventManager, null);
        assertTrue("Wand with charges must fire", fired);
        assertEquals("Charges must decrement by 1", 4, wand.getCharges());
    }

    @Test
    public void testZapWandFireMonsterDamage() {
        Item wand = createWand(WandEffectType.FIRE, 3);
        // Place goblin at (5, 2) directly in front of player facing EAST at (2, 2)
        Monster goblin = new Monster(Monster.MonsterType.GOBLIN, 25, 10, 5f, 2f);
        maze.getMonsters().put(new GridPoint2(5, 2), goblin);

        int initialHp = goblin.getCurrentHP();
        boolean fired = WandExecutionEngine.zapWand(wand, player, Direction.EAST, maze,
                discoveryManager, eventManager, null);

        assertTrue("Wand must fire", fired);
        assertTrue("Goblin should take damage from fire blast", goblin.getCurrentHP() < initialHp);
    }

    @Test
    public void testZapWandColdAppliesFrostbite() {
        Item wand = createWand(WandEffectType.COLD, 3);
        Monster orc = new Monster(Monster.MonsterType.ORC, 40, 10, 5f, 2f);
        maze.getMonsters().put(new GridPoint2(5, 2), orc);

        boolean fired = WandExecutionEngine.zapWand(wand, player, Direction.EAST, maze,
                discoveryManager, eventManager, null);

        assertTrue("Wand must fire", fired);
        assertTrue("Orc should have FREEZING status effect",
                orc.getStatusManager().hasEffect(StatusEffectType.FREEZING));
    }

    @Test
    public void testZapWandMagicMissileMultipleHits() {
        Item wand = createWand(WandEffectType.MAGIC_MISSILE, 3);
        Monster troll = new Monster(Monster.MonsterType.TROLL, 60, 10, 5f, 2f);
        maze.getMonsters().put(new GridPoint2(5, 2), troll);

        int initialHp = troll.getCurrentHP();
        boolean fired = WandExecutionEngine.zapWand(wand, player, Direction.EAST, maze,
                discoveryManager, eventManager, null);

        assertTrue("Wand must fire", fired);
        int damageDealt = initialHp - troll.getCurrentHP();
        assertTrue("Magic Missiles should deal at least 6 damage (3 x 1d4+1)", damageDealt >= 6);
    }

    @Test
    public void testZapWandDiggingCrumblesWall() {
        Item wand = createWand(WandEffectType.DIGGING, 3);
        // Place an interior wall at (5, 2)
        maze.setTile(5, 2, 1);
        assertEquals("Tile should initially be wall", 1, maze.getWallDataAt(5, 2));

        boolean fired = WandExecutionEngine.zapWand(wand, player, Direction.EAST, maze,
                discoveryManager, eventManager, null);

        assertTrue("Wand must fire", fired);
        assertEquals("Digging wand must crumble the wall into floor (0)", 0, maze.getWallDataAt(5, 2));
    }

    @Test
    public void testZapWandLightIlluminatesAndBuffs() {
        Item wand = createWand(WandEffectType.LIGHT, 3);
        int initialLights = maze.getLights().size;

        boolean fired = WandExecutionEngine.zapWand(wand, player, Direction.EAST, maze,
                discoveryManager, eventManager, null);

        assertTrue("Wand must fire", fired);
        assertTrue("Wand of Light should add a point light to maze", maze.getLights().size > initialLights);
        assertTrue("Player should gain MOTE_OF_LIGHT status buff",
                player.getStatusManager().hasEffect(StatusEffectType.MOTE_OF_LIGHT));
    }

    @Test
    public void testZapWandTeleportationMovesMonster() {
        Item wand = createWand(WandEffectType.TELEPORTATION, 3);
        GridPoint2 originalPos = new GridPoint2(5, 2);
        Monster ogre = new Monster(Monster.MonsterType.OGRE, 50, 10, 5f, 2f);
        maze.getMonsters().put(originalPos, ogre);

        boolean fired = WandExecutionEngine.zapWand(wand, player, Direction.EAST, maze,
                discoveryManager, eventManager, null);

        assertTrue("Wand must fire", fired);
        assertFalse("Monster should no longer be at original position",
                maze.getMonsters().containsKey(originalPos));
        assertTrue("Monster should exist at a new location in the maze",
                maze.getMonsters().containsValue(ogre));
    }

    @Test
    public void testZapWandIdentifiesInDiscoveryManager() {
        Item wand = createWand(WandEffectType.FIRE, 3);
        assertFalse("Wand should initially be unidentified in DiscoveryManager",
                discoveryManager.isWandIdentified(WandEffectType.FIRE));

        WandExecutionEngine.zapWand(wand, player, Direction.EAST, maze,
                discoveryManager, eventManager, null);

        assertTrue("Wand of Fire should now be identified in DiscoveryManager",
                discoveryManager.isWandIdentified(WandEffectType.FIRE));
        assertTrue("Wand item itself should be marked identified", wand.isIdentified());
        assertEquals("Wand of Fire", wand.getDisplayName());
    }

    @Test
    public void testPlayerZapDelegatesProperly() {
        Item wand = createWand(WandEffectType.LIGHT, 2);
        player.zap(wand, Direction.EAST, discoveryManager, eventManager, maze);

        assertEquals("Player.zap must decrement wand charges", 1, wand.getCharges());
        assertTrue("Player.zap must identify wand effect",
                discoveryManager.isWandIdentified(WandEffectType.LIGHT));
    }
}
