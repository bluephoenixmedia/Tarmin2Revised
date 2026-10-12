package com.bpm.minotaur.action;

import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ActionPrototypeTest {

    @Test
    public void testArenaBuilderCreatesValidLayout() {
        ActionArenaBuilder.ArenaData arena = ActionArenaBuilder.buildCrucibleArena();
        assertNotNull(arena);
        assertNotNull(arena.maze);
        assertEquals(ActionArenaBuilder.ARENA_SIZE, arena.solidGrid.length);
        assertEquals(ActionArenaBuilder.ARENA_SIZE, arena.solidGrid[0].length);

        // Outer boundaries must be solid walls
        for (int i = 0; i < ActionArenaBuilder.ARENA_SIZE; i++) {
            assertTrue("North perimeter must be solid", arena.solidGrid[0][i]);
            assertTrue("South perimeter must be solid", arena.solidGrid[ActionArenaBuilder.ARENA_SIZE - 1][i]);
            assertTrue("West perimeter must be solid", arena.solidGrid[i][0]);
            assertTrue("East perimeter must be solid", arena.solidGrid[i][ActionArenaBuilder.ARENA_SIZE - 1]);
        }

        // Center must be open floor
        int mid = ActionArenaBuilder.ARENA_SIZE / 2;
        assertFalse("Center spawn must be walkable", arena.solidGrid[mid][mid]);

        // Spikes and spawn points must exist
        assertFalse("Spike hazards must be populated", arena.spikeHazards.isEmpty());
        assertFalse("Monster spawns must be populated", arena.monsterSpawnPoints.isEmpty());
    }

    @Test
    public void testPlayerControllerMovementAndCollision() {
        ActionArenaBuilder.ArenaData arena = ActionArenaBuilder.buildCrucibleArena();
        ActionPlayerController player = new ActionPlayerController(arena.playerSpawn);

        assertEquals(arena.playerSpawn.x, player.position.x, 0.001f);
        assertEquals(arena.playerSpawn.z, player.position.z, 0.001f);

        // Test mouse look turns direction
        player.processMouseLook(10f, 5f);
        assertTrue("Yaw must change on mouse look", player.yaw != 90.0f);

        // Test collision sliding: move towards North wall
        player.position.set(2.0f, ActionPlayerController.EYE_HEIGHT_STAND, -1.8f);
        player.velocity.set(0f, 0f, 5.0f); // Moving North towards wall at z = -1.0
        player.update(0.1f, arena.solidGrid);

        // Player must not penetrate solid wall (wall minZ = -1.0)
        assertTrue("Player must be pushed outside wall radius", player.position.z <= -1.0f - ActionPlayerController.COLLISION_RADIUS + 0.05f);
    }

    @Test
    public void testCombatEngineMeleeHitAndHitstop() {
        ActionPlayerController player = new ActionPlayerController(new Vector3(10f, 0.65f, -10f));
        ActionCombatEngine combat = new ActionCombatEngine();

        // Spawn a monster directly in front of the player (facing North, -Z)
        ActionMonsterEntity skeleton = new ActionMonsterEntity(Monster.MonsterType.SKELETON, new Vector3(10f, 0.65f, -11.5f));
        List<ActionMonsterEntity> monsters = new ArrayList<>();
        monsters.add(skeleton);

        float initialHp = skeleton.health;

        // Player attacks with broadsword (direction North)
        player.yaw = 90.0f; // Standard North direction along -Z
        combat.equippedWeapon = ActionCombatEngine.WeaponType.BROADSWORD;

        // Simulate frame update with attack input
        // Weapon trace reach = 2.3m, distance = 1.5m -> within reach!
        float dist = (float) Math.hypot(skeleton.position.x - player.position.x, skeleton.position.z - player.position.z);
        assertTrue("Monster must be in melee reach", dist <= combat.equippedWeapon.reach + skeleton.radius);

        skeleton.takeDamage(combat.equippedWeapon.damage, new Vector3(0, 0, -1), false);
        assertTrue("Monster must have taken damage", skeleton.health < initialHp);
    }

    @Test
    public void testKickPhysicsLaunchesMonster() {
        ActionMonsterEntity monster = new ActionMonsterEntity(Monster.MonsterType.GHOUL, new Vector3(5f, 0.65f, -5f));
        Vector3 kickImpulse = new Vector3(0f, 3.5f, 15f);

        monster.launchKick(kickImpulse);
        assertEquals(ActionMonsterEntity.State.LAUNCHED, monster.state);
        assertTrue("Velocity Z must be positive from kick", monster.velocity.z > 10f);
        assertTrue("Velocity Y must have upward arc from kick", monster.velocity.y > 2f);
    }

    @Test
    public void testParryDeflectsAndPoiseBreaks() {
        ActionPlayerController player = new ActionPlayerController(new Vector3(10f, 0.65f, -10f));
        ActionCombatEngine combat = new ActionCombatEngine();
        ActionMonsterEntity minotaur = new ActionMonsterEntity(Monster.MonsterType.MINOTAUR, new Vector3(10f, 0.65f, -11f));

        // Activate parry
        combat.isParrying = true;
        combat.parryTimer = ActionCombatEngine.PARRY_WINDOW;
        float playerHpBefore = combat.playerHealth;

        // Monster attack lands during parry window
        combat.onMonsterAttackHitsPlayer(minotaur, player);

        // Player takes 0 damage and minotaur is staggered
        assertEquals("Player HP must not drop on parry", playerHpBefore, combat.playerHealth, 0.001f);
        assertEquals("Monster must be staggered on parry", ActionMonsterEntity.State.STAGGERED, minotaur.state);
        assertTrue("Parry flash timer must be triggered", combat.parryFlashTimer > 0f);
    }
}
