package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.MonsterAiManager;
import com.bpm.minotaur.managers.SoundManager;
import com.bpm.minotaur.rendering.Animation;
import com.bpm.minotaur.rendering.AnimationManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.Assert.*;

public class MonsterRangedAttackTest {

    private Player player;
    private Maze maze;
    private AnimationManager animationManager;
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
        int[][] walls = new int[12][12];
        maze = new Maze(1, walls);

        animationManager = new AnimationManager() {};
        eventManager = new GameEventManager();

        SoundManager silentSound = new SoundManager() {
            @Override
            public void playMonsterAttackSound(Monster monster) {}
            @Override
            public void playSound(String name) {}
        };

        combatManager = new CombatManager(player, maze, null, animationManager, eventManager,
                silentSound, null, null, null, null, null);
    }

    @Test
    public void testProjectileRegistryArchetypes() {
        Map<String, MonsterProjectileRegistry.MonsterProjectileDefinition> all = MonsterProjectileRegistry.getAll();
        assertNotNull(all);
        assertTrue("Registry should contain ARROW", all.containsKey("ARROW"));
        assertTrue("Registry should contain BOULDER", all.containsKey("BOULDER"));
        assertTrue("Registry should contain STONE", all.containsKey("STONE"));
        assertTrue("Registry should contain WEB", all.containsKey("WEB"));
        assertTrue("Registry should contain VENOM", all.containsKey("VENOM"));
        assertTrue("Registry should contain FIREBALL", all.containsKey("FIREBALL"));
        assertTrue("Registry should contain DEATH_RAY", all.containsKey("DEATH_RAY"));
        assertTrue("Registry should contain PSYCHIC_BOLT", all.containsKey("PSYCHIC_BOLT"));
        assertTrue("Registry should contain RADIANT_SPEAR", all.containsKey("RADIANT_SPEAR"));

        for (MonsterProjectileRegistry.MonsterProjectileDefinition def : all.values()) {
            assertNotNull("Projectile ID must not be null", def.getId());
            assertNotNull("Sprite data must not be null for " + def.getId(), def.getSpriteData());
            assertEquals("Sprite data must be 24 rows for " + def.getId(), 24, def.getSpriteData().length);
            for (String row : def.getSpriteData()) {
                assertEquals("Each row must be 24 columns for " + def.getId(), 24, row.length());
            }
            assertNotNull("Color must not be null for " + def.getId(), def.getColor());
            assertNotNull("Default damage type must not be null for " + def.getId(), def.getDefaultDamageType());
            assertTrue("Projectile speed must be positive for " + def.getId(), def.getSpeed() > 0);
        }

        // Test fallback for null or unknown projectile
        assertEquals("ARROW", MonsterProjectileRegistry.get(null).getId());
        assertEquals("ARROW", MonsterProjectileRegistry.get("UNKNOWN_ID").getId());
    }

    @Test
    public void testMonsterRangedPropertiesAndTemplateInitialization() {
        MonsterTemplate t = new MonsterTemplate();
        t.hasRangedAttack = true;
        t.attackRange = 7;
        t.rangedProjectile = "FIREBALL";
        t.rangedDamageDice = "3d10";
        t.rangedDamageType = DamageType.FIRE;
        t.rangedEffect = "BURNING";
        t.rangedEffectChance = 0.8f;
        t.rangedPreferredDistance = 5f;

        Monster dragon = new Monster(Monster.MonsterType.DRAGON, 50, 12, 5f, 9f);
        dragon.setTemplate(t);

        assertTrue(dragon.hasRangedAttack());
        assertEquals(7, dragon.getAttackRange());
        assertEquals("FIREBALL", dragon.getRangedProjectile());
        assertEquals("3d10", dragon.getRangedDamageDice());
        assertEquals(DamageType.FIRE, dragon.getRangedDamageType());
        assertEquals("BURNING", dragon.getRangedEffect());
        assertEquals(0.8f, dragon.getRangedEffectChance(), 0.001f);
        assertEquals(5f, dragon.getRangedPreferredDistance(), 0.001f);
    }

    @Test
    public void testMonsterTelegraphState() {
        Monster skeleton = new Monster(Monster.MonsterType.SKELETON, 20, 10, 5f, 8f);
        assertFalse(skeleton.isRangedTelegraphing());
        assertEquals(1.0f, skeleton.getRangedAttackTelegraphProgress(), 0.001f);

        skeleton.triggerRangedAttackTelegraph(Color.CHARTREUSE, 0.4f);
        assertTrue(skeleton.isRangedTelegraphing());
        assertEquals(Color.CHARTREUSE, skeleton.getRangedAttackTelegraphColor());
        assertTrue(skeleton.getRangedAttackTelegraphProgress() < 1.0f);
    }

    @Test
    public void testPerformMonsterRangedAttackExecution() {
        // Monster at (5, 8) firing South at Player at (5, 5) - orthogonal distance = 3
        Monster skeleton = new Monster(Monster.MonsterType.SKELETON, 30, 10, 5f, 8f);
        skeleton.setHasRangedAttack(true);
        skeleton.setAttackRange(6);
        skeleton.setRangedProjectile("ARROW");
        skeleton.setRangedDamageDice("1d6");
        skeleton.setRangedDamageType(DamageType.PHYSICAL);

        int initialAnimCount = animationManager.getAnimations().size();

        boolean executed = combatManager.performMonsterRangedAttack(skeleton);
        assertTrue("Ranged attack should succeed along clear orthogonal line", executed);

        // Verify animation was queued (projectile animation + optional hit impact explosion)
        assertTrue("Animations should be queued", animationManager.getAnimations().size() >= initialAnimCount + 1);
        boolean hasProjectileAnim = false;
        for (Animation anim : animationManager.getAnimations()) {
            if (anim.getType() == Animation.AnimationType.PROJECTILE_MONSTER) {
                hasProjectileAnim = true;
                break;
            }
        }
        assertTrue("Projectile animation should be queued", hasProjectileAnim);

        // Verify telegraph was activated
        assertTrue(skeleton.isRangedTelegraphing());
    }

    @Test
    public void testStatusEffectInflictionOnRangedHit() {
        // Monster at (5, 8) with 100% chance to inflict POISONED
        Monster snake = new Monster(Monster.MonsterType.GIANT_SNAKE, 25, 10, 5f, 8f);
        snake.setHasRangedAttack(true);
        snake.setAttackRange(5);
        snake.setRangedProjectile("VENOM");
        snake.setRangedDamageDice("1d4+5");
        snake.setRangedDamageType(DamageType.POISON);
        snake.setRangedEffect("POISONED");
        snake.setRangedEffectChance(1.0f);

        // Ensure attack hits by giving attacker high dexterity (bonus +10) vs player base AC (10)
        snake.setDexterity(50);

        boolean executed = combatManager.performMonsterRangedAttack(snake);
        assertTrue("Ranged attack should hit", executed);

        // Player should be afflicted with POISONED
        assertTrue("Player should have POISONED status effect",
                player.getStatusManager().hasEffect(StatusEffectType.POISONED));
    }

    /** A plain archer: shoots from afar, but closes in to fight once it is near. */
    private Monster plainArcher(float x, float y) {
        Monster archer = new Monster(Monster.MonsterType.SKELETON, 20, 10, x, y);
        archer.setHasRangedAttack(true);
        archer.setAttackRange(6);
        archer.setRangedPreferredDistance(4f);
        archer.setIntelligence(5);
        archer.setState(Monster.MonsterState.HUNTING);
        maze.addMonster(archer);
        return archer;
    }

    /** Intelligence and level high enough to fight at arm's length and never close. */
    private Monster eliteSkirmisher(float x, float y) {
        Monster elite = plainArcher(x, y);
        elite.setIntelligence(MonsterTactics.ELITE_INTELLIGENCE);
        elite.setLevel(MonsterTactics.ELITE_LEVEL);
        return elite;
    }

    @Test
    public void aPlainArcherClosesInInsteadOfBackingAway() {
        Monster archer = plainArcher(5f, 4f); // one tile south of the player
        MonsterAiManager aiManager = new MonsterAiManager();

        aiManager.updateMonster(archer, maze, player, true, combatManager);

        assertEquals("A plain archer holds its ground to fight rather than kiting",
                new Vector2(5.5f, 4.5f), archer.getPosition());
    }

    @Test
    public void aPlainArcherAdvancesTheTurnAfterItShoots() {
        Monster archer = plainArcher(5f, 1f); // four tiles south, lined up with the player
        MonsterAiManager aiManager = new MonsterAiManager();

        aiManager.updateMonster(archer, maze, player, true, combatManager);
        assertEquals("It shoots from range and stays put that turn",
                new Vector2(5.5f, 1.5f), archer.getPosition());

        aiManager.updateMonster(archer, maze, player, true, combatManager);
        assertEquals("The next turn it closes the gap instead of shooting again",
                new Vector2(5.5f, 2.5f), archer.getPosition());
    }

    @Test
    public void anEliteSkirmisherStillKitesWhenCrowded() {
        Monster elite = eliteSkirmisher(5f, 4f);
        MonsterAiManager aiManager = new MonsterAiManager();

        aiManager.updateMonster(elite, maze, player, true, combatManager);

        assertEquals("An elite skirmisher backs off down the open corridor",
                new Vector2(5.5f, 3.5f), elite.getPosition());
    }

    @Test
    public void tacticsGateKitingOnBothIntelligenceAndLevel() {
        Monster smartButGreen = plainArcher(1f, 1f);
        smartButGreen.setIntelligence(MonsterTactics.ELITE_INTELLIGENCE + 4);
        smartButGreen.setLevel(MonsterTactics.ELITE_LEVEL - 2);
        Monster veteranBrute = plainArcher(2f, 1f);
        veteranBrute.setIntelligence(MonsterTactics.ELITE_INTELLIGENCE - 2);
        veteranBrute.setLevel(MonsterTactics.ELITE_LEVEL + 5);

        assertFalse("Clever but low level: it still comes for you",
                MonsterTactics.isEliteSkirmisher(smartButGreen));
        assertFalse("High level but dull: it still comes for you",
                MonsterTactics.isEliteSkirmisher(veteranBrute));
        assertTrue(MonsterTactics.isEliteSkirmisher(eliteSkirmisher(3f, 1f)));
    }

    @Test
    public void tacticsSendAPlainArcherToMeleeOnceItIsClose() {
        Monster archer = plainArcher(1f, 1f);

        assertEquals(MonsterTactics.Move.SHOOT, MonsterTactics.decide(archer, 4, true));
        assertEquals("Within two tiles it wants melee, not another shot",
                MonsterTactics.Move.ADVANCE, MonsterTactics.decide(archer, 2, true));
        assertEquals(MonsterTactics.Move.ADVANCE, MonsterTactics.decide(archer, 5, false));

        archer.setRangedShotCooldown(1);
        assertEquals("A shot costs a turn of approach",
                MonsterTactics.Move.ADVANCE, MonsterTactics.decide(archer, 4, true));
    }

    @Test
    public void tacticsLetAnEliteRetreatThenShoot() {
        Monster elite = eliteSkirmisher(1f, 1f);

        assertEquals(MonsterTactics.Move.RETREAT, MonsterTactics.decide(elite, 1, true));
        assertEquals(MonsterTactics.Move.SHOOT, MonsterTactics.decide(elite, 4, true));
        assertEquals("An elite keeps its distance rather than charging",
                MonsterTactics.Move.RETREAT, MonsterTactics.decide(elite, 2, false));
    }
}
