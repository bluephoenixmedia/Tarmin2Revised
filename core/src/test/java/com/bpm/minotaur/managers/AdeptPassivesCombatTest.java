package com.bpm.minotaur.managers;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** The Adept Passives fire inside real combat: bump melee (#243) and shots (#244). */
public class AdeptPassivesCombatTest {

    private final List<String> damageTexts = new ArrayList<>();
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
        AnimationManager animations = new AnimationManager() {
            @Override
            public void addAnimation(Animation animation) {
                if (animation.getDamageText() != null) damageTexts.add(animation.getDamageText());
            }
        };
        combat = new CombatManager(player, maze, null, animations, events,
                new HeadlessSoundManager(), new com.bpm.minotaur.gamedata.item.ItemDataManager(), null, null, null, null);
        combat.setArtRoll(() -> 0.0); // every Passive that can fire, fires
    }

    private static Item weapon(String family) {
        ItemTemplate t = new ItemTemplate();
        t.isWeapon = true;
        t.weaponFamily = family;
        t.damageDice = "1d2";
        return Item.fromTemplate(Item.ItemType.CLUB, t);
    }

    /** A sturdy foe beside the player, easy to hit: AC 0. */
    private Monster foeBeside() {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 1;
        t.maxHP = 200;
        Monster m = new Monster(Monster.MonsterType.KOBOLD, 200, 0, 4f, 3f);
        m.setTemplate(t);
        m.setCurrentHP(200);
        m.setArmorClass(0);
        maze.addMonster(m);
        return m;
    }

    private void know(CombatArt art) {
        player.learnArt(art, null);
        player.getStats().getMastery().setRank(art.family, Rank.ADEPT);
    }

    @Test
    public void aFullHitWithRingingBlowDazesAndSaysSoOnTheDamageNumber() {
        know(CombatArt.RINGING_BLOW);
        player.getInventory().setRightHand(weapon("BLUNT"));
        Monster foe = foeBeside();

        for (int i = 0; i < 20 && !foe.isDazed() && !foe.isStunned(); i++) {
            combat.setCurrentState(CombatManager.CombatState.INACTIVE);
            combat.playerMeleeStrike(foe);
        }

        assertTrue("dazed or stunned after a full hit", foe.isDazed() || foe.isStunned());
        assertTrue(damageTexts.toString(), damageTexts.stream().anyMatch(t -> t.startsWith("DAZE ") || t.startsWith("STUN ")));
    }

    @Test
    public void aMissedBlowDrawsACounterCutTheMonsterDoesNotAnswer() {
        know(CombatArt.ANSWERING_STEEL);
        player.getInventory().setRightHand(weapon("BLADE"));
        armourPlayer(); // every monster blow misses
        Monster foe = foeBeside();
        int hp = foe.getCurrentHP();
        float energy = foe.getEnergy();

        // Player AC caps at 25, so the odd blow still lands: swing until one misses.
        for (int i = 0; i < 60 && !missedOnce(); i++) combat.monsterMeleeStrike(foe);

        assertTrue("a blow missed: " + events.getMessageHistory(), missedOnce());
        assertTrue("the counter-cut landed", foe.getCurrentHP() < hp);
        assertEquals("a free strike draws no return blow", energy, foe.getEnergy(), 0f);
        assertTrue(damageTexts.toString(), damageTexts.stream().anyMatch(t -> t.startsWith("COUNTER ")));
    }

    @Test
    public void withoutTheArtAMissIsJustAMiss() {
        player.getInventory().setRightHand(weapon("BLADE"));
        armourPlayer();
        Monster foe = foeBeside();
        int hp = foe.getCurrentHP();

        combat.monsterMeleeStrike(foe);

        assertEquals(hp, foe.getCurrentHP());
    }

    @Test
    public void holdTheLineStrikesAMonsterThatClosesIn() {
        know(CombatArt.HOLD_THE_LINE);
        player.getInventory().setRightHand(weapon("POLEARM"));
        Monster foe = foeBeside();
        int hp = foe.getCurrentHP();

        combat.onMonsterClosed(foe);

        assertTrue(foe.getCurrentHP() < hp);
        assertTrue(damageTexts.toString(), damageTexts.stream().anyMatch(t -> t.startsWith("FIRST STRIKE ")));
    }

    @Test
    public void aMonsterThatWalksUpToAPolearmIsStruckFirst() {
        know(CombatArt.HOLD_THE_LINE);
        player.getInventory().setRightHand(weapon("POLEARM"));
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 1;
        t.maxHP = 200;
        Monster foe = new Monster(Monster.MonsterType.KOBOLD, 200, 0, 5f, 3f); // two tiles east
        foe.setTemplate(t);
        foe.setCurrentHP(200);
        foe.setArmorClass(0);
        foe.setState(Monster.MonsterState.HUNTING);
        foe.setLastKnownTargetPos(new com.badlogic.gdx.math.GridPoint2(3, 3));
        maze.addMonster(foe);

        new MonsterAiManager().updateMonster(foe, maze, player, true, combat);

        assertEquals("it stepped next to the player", 4, (int) foe.getPosition().x);
        assertTrue(damageTexts.toString(), damageTexts.stream().anyMatch(x -> x.startsWith("FIRST STRIKE ")));
    }

    private static Item shooter(Item.ItemType type, String family) {
        ItemTemplate t = new ItemTemplate();
        t.isWeapon = true;
        t.isRanged = true;
        t.range = 6;
        t.weaponFamily = family;
        t.damageDice = "1d2";
        return Item.fromTemplate(type, t);
    }

    /** A sturdy foe two tiles down the player's facing, easy to hit: AC 0. */
    private Monster foeDownrange() {
        Monster m = foeBeside();
        maze.getMonsters().remove(new com.badlogic.gdx.math.GridPoint2(4, 3));
        m.getPosition().set(5f, 3f);
        maze.addMonster(m);
        return m;
    }

    @Test
    public void swiftNockLoosesASecondArrowAndSpendsIt() {
        know(CombatArt.SWIFT_NOCK);
        player.getInventory().setRightHand(shooter(Item.ItemType.BOW, "BOW"));
        player.getStats().setArrows(10);
        foeDownrange();

        combat.playerAttackInstant();

        assertTrue(damageTexts.toString(), damageTexts.stream().anyMatch(t -> t.startsWith("SECOND ARROW ")));
        assertEquals("two arrows for two shots", 8, player.getArrows());
    }

    @Test
    public void aSecondArrowThatKillsTheTargetEndsTheShotCleanly() {
        // Found by the duel simulator (#250): the second arrow's kill cleared the fight's monster
        // while the first shot was still being resolved, and the rest of it fell over on null.
        // Each try is a fresh fight: a survivor would stay engaged and draw the next try's shot.
        for (int i = 0; i < 40; i++) {
            setUp();
            know(CombatArt.SWIFT_NOCK);
            player.getInventory().setRightHand(shooter(Item.ItemType.BOW, "BOW"));
            player.getStats().setArrows(40);
            Monster foe = foeDownrange();
            foe.setCurrentHP(3); // the first 1d2 arrow cannot kill it; the second may
            combat.playerAttackInstant();
            if (foe.getCurrentHP() <= 0) {
                assertFalse("the dead are cleared once", maze.getMonsters().containsValue(foe));
                return;
            }
        }
        fail("no second arrow ever finished the foe in 40 tries");
    }

    @Test
    public void swiftNockWithNoArrowLeftLoosesNothing() {
        know(CombatArt.SWIFT_NOCK);
        player.getInventory().setRightHand(shooter(Item.ItemType.BOW, "BOW"));
        player.getStats().setArrows(1);
        foeDownrange();

        combat.playerAttackInstant();

        assertEquals(0, player.getArrows());
        assertTrue(damageTexts.toString(), damageTexts.stream().noneMatch(t -> t.startsWith("SECOND ARROW")));
    }

    @Test
    public void practisedHandsShortensTheNextReloadByATurn() {
        know(CombatArt.PRACTISED_HANDS);
        player.getInventory().setRightHand(shooter(Item.ItemType.MUSKET, "FIREARM"));
        player.getStats().setShot(5);
        foeDownrange();
        int full = com.bpm.minotaur.gamedata.firearm.FirearmProfile.reloadTurns(Item.ItemType.MUSKET);

        combat.playerAttackInstant();

        assertEquals("the shot's own reload is the one cut", Math.max(1, full - 1),
                combat.getActiveReload().getTurnsRequired());
        assertTrue(damageTexts.toString(), damageTexts.stream().anyMatch(t -> t.startsWith("QUICK LOAD ")));
    }

    @Test
    public void staggeringBoltDazesTheTargetOfARealShot() {
        know(CombatArt.STAGGERING_BOLT);
        player.getInventory().setRightHand(shooter(Item.ItemType.CROSSBOW, "CROSSBOW"));
        player.getStats().setArrows(10);
        Monster foe = foeDownrange();

        combat.playerAttackInstant();

        assertTrue(foe.isDazed() || foe.isStunned());
        assertTrue(damageTexts.toString(), damageTexts.stream().anyMatch(t -> t.startsWith("DAZE ")));
    }

    @Test
    public void tangleSlowsWhatAThrownWeaponHits() {
        know(CombatArt.TANGLE);
        Monster foe = foeDownrange();

        for (int i = 0; i < 30 && !foe.getStatusManager().hasEffect(
                com.bpm.minotaur.gamedata.effects.StatusEffectType.SLOWED); i++) {
            combat.throwWeapon(weapon("BLADE"));
        }

        assertTrue(foe.getStatusManager().hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.SLOWED));
        assertTrue(damageTexts.toString(), damageTexts.stream().anyMatch(t -> t.startsWith("TANGLE ")));
    }

    private boolean missedOnce() {
        return events.getMessageHistory().stream().anyMatch(m -> m.contains("misses!"));
    }

    private void armourPlayer() {
        player.getStats().setMaxHP(999);
        player.getStats().setCurrentHP(999);
        ItemTemplate plate = new ItemTemplate();
        plate.friendlyName = "Test Plate";
        plate.isArmor = true;
        plate.armorClassBonus = 60;
        player.getEquipment().setWornChest(Item.fromTemplate(Item.ItemType.HAUBERK, plate));
    }
}
