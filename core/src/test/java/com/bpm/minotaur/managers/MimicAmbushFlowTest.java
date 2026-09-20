package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * End-to-end coverage of the reveal state machine, which the unit tests around
 * MimicReveal and MimicDetection cannot reach: the two-phase timing, the free blow,
 * and the hand-off into combat.
 */
public class MimicAmbushFlowTest {

    private static final GridPoint2 CHEST_TILE = new GridPoint2(6, 5);

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
        player.setFacing(Direction.EAST);
        maze = new Maze(1, new int[12][12]);
        com.bpm.minotaur.rendering.AnimationManager animationManager =
                new com.bpm.minotaur.rendering.AnimationManager() {};
        combatManager = new CombatManager(player, maze, null, animationManager, new GameEventManager(),
                silentSoundManager(), null, null, null, null, null);
    }

    // Both of these dereference DebugManager, which is null under SoundManager's
    // headless constructor -- the same pre-existing hazard CombatLoopInstrumentationTest
    // stubs around. The audio side-effects are not what this test is exercising.
    private SoundManager silentSoundManager() {
        return new SoundManager() {
            @Override
            public void playMonsterAttackSound(Monster monster) {
            }

            @Override
            public void playCombatStartSound() {
            }
        };
    }

    private Item placeMimicChest(boolean seen) {
        Item chest = new Item(Item.ItemType.REGULAR_CHEST, CHEST_TILE.x, CHEST_TILE.y, null, null, null);
        chest.setMimic(true);
        chest.setMimicSeen(seen);
        maze.addItem(chest);
        return chest;
    }

    @Test
    public void ambushEntersTheBlockingRevealStateWithTheChestStillStanding() {
        placeMimicChest(false);

        combatManager.triggerMimicAmbush(CHEST_TILE, 10);

        assertEquals(CombatManager.CombatState.MONSTER_REVEAL, combatManager.getCurrentState());
        assertNotNull("the chest must survive the shudder so there is something to shake",
                maze.getItems().get(CHEST_TILE));
        assertTrue(maze.getMonsters().isEmpty());
        assertEquals(CHEST_TILE, combatManager.getMimicRevealTile());
    }

    @Test
    public void theChestBecomesAMonsterOnceTheShudderEnds() {
        placeMimicChest(false);
        combatManager.triggerMimicAmbush(CHEST_TILE, 10);

        combatManager.update(CombatManager.MIMIC_SHUDDER_TIME + 0.01f);

        assertNull("the disguise is gone", maze.getItems().get(CHEST_TILE));
        Monster revealed = maze.getMonsters().get(CHEST_TILE);
        assertNotNull("the creature has taken its tile", revealed);
        assertEquals(Monster.MonsterType.MIMIC, revealed.getType());
        assertNull("the chest is no longer shuddering", combatManager.getMimicRevealTile());
    }

    @Test
    public void theRevealNeverLeavesBothHalvesOfTheCreatureOnOneTile() {
        placeMimicChest(false);
        combatManager.triggerMimicAmbush(CHEST_TILE, 10);

        for (int i = 0; i < 40; i++) {
            combatManager.update(0.05f);
            boolean hasChest = maze.getItems().get(CHEST_TILE) != null;
            boolean hasMonster = maze.getMonsters().get(CHEST_TILE) != null;
            assertFalse("a tile must never hold the chest and the monster at once",
                    hasChest && hasMonster);
        }
    }

    @Test
    public void theAmbushEndsInCombatWithTheMimic() {
        placeMimicChest(false);
        player.getStats().setCurrentHP(player.getStats().getMaxHP());

        combatManager.triggerMimicAmbush(CHEST_TILE, 10);
        for (int i = 0; i < 20 && combatManager.getCurrentState() == CombatManager.CombatState.MONSTER_REVEAL; i++) {
            combatManager.update(0.05f);
        }

        assertNotEquals("the reveal must not stall", CombatManager.CombatState.MONSTER_REVEAL,
                combatManager.getCurrentState());
        assertNotNull(combatManager.getMonster());
        assertEquals(Monster.MonsterType.MIMIC, combatManager.getMonster().getType());
    }

    @Test
    public void aKillingFreeBlowRoutesThroughDefeatRatherThanStranding() {
        placeMimicChest(false);
        player.getStats().setCurrentHP(1);

        combatManager.triggerMimicAmbush(CHEST_TILE, 10);
        // Drive past the full reveal; the free blow lands at the end of it.
        for (int i = 0; i < 20 && combatManager.getCurrentState() == CombatManager.CombatState.MONSTER_REVEAL; i++) {
            combatManager.update(0.05f);
        }

        if (player.getStats().getCurrentHP() <= 0) {
            assertNotEquals("a dead player must never be left sitting in the reveal",
                    CombatManager.CombatState.MONSTER_REVEAL, combatManager.getCurrentState());
        }
    }

    @Test
    public void ambushIsIgnoredOnATileWithNoMimic() {
        maze.addItem(new Item(Item.ItemType.REGULAR_CHEST, CHEST_TILE.x, CHEST_TILE.y, null, null, null));

        combatManager.triggerMimicAmbush(CHEST_TILE, 10);

        assertEquals(CombatManager.CombatState.INACTIVE, combatManager.getCurrentState());
        assertNotNull(maze.getItems().get(CHEST_TILE));
    }

    @Test
    public void preEmptiveRevealIsImmediateAndDoesNotBlock() {
        placeMimicChest(true);

        Monster mimic = combatManager.revealMimicPreEmptively(CHEST_TILE, 10);

        assertNotNull(mimic);
        assertEquals("striking first must not hand control away",
                CombatManager.CombatState.INACTIVE, combatManager.getCurrentState());
        assertNull(maze.getItems().get(CHEST_TILE));
        assertSame(mimic, maze.getMonsters().get(CHEST_TILE));
    }

    @Test
    public void anUndetectedMimicIsInvisibleToProjectiles() {
        placeMimicChest(false);

        CombatManager.HitResult hit = combatManager.raycastProjectile(
                player.getPosition(), Direction.EAST, 8, true, true);

        assertNotEquals("an unspotted mimic must not be shootable",
                CombatManager.HitResult.HitType.MONSTER, hit.type);
        assertNotNull("and must still be standing there as a chest", maze.getItems().get(CHEST_TILE));
    }

    @Test
    public void aDetectedMimicCanBeShotAndTheShotRevealsIt() {
        placeMimicChest(true);

        CombatManager.HitResult hit = combatManager.raycastProjectile(
                player.getPosition(), Direction.EAST, 8, true, true);

        assertEquals("spotting it should pay off for an archer too",
                CombatManager.HitResult.HitType.MONSTER, hit.type);
        assertNotNull(hit.hitMonster);
        assertEquals(Monster.MonsterType.MIMIC, hit.hitMonster.getType());
        assertNull(maze.getItems().get(CHEST_TILE));
    }

    /**
     * A plain trace must not spawn anything. The reveal is a world mutation, so asking
     * "is there a target in range?" cannot be allowed to answer by creating one.
     */
    @Test
    public void aSpeculativeTraceLeavesADetectedMimicAlone() {
        placeMimicChest(true);

        CombatManager.HitResult hit = combatManager.raycastProjectile(
                player.getPosition(), Direction.EAST, 8, true, false);

        assertNotEquals(CombatManager.HitResult.HitType.MONSTER, hit.type);
        assertNotNull("a non-attacking trace must not drop the disguise",
                maze.getItems().get(CHEST_TILE));
        assertTrue(maze.getMonsters().isEmpty());
    }

    @Test
    public void monsterFiredProjectilesDoNotRevealMimics() {
        placeMimicChest(true);

        combatManager.raycastProjectile(player.getPosition(), Direction.EAST, 8, false, true);

        assertNotNull("only the player's own shots pierce a disguise",
                maze.getItems().get(CHEST_TILE));
    }
}
