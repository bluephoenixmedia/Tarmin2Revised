package com.bpm.minotaur.gamedata.arts;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterRole;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.telemetry.TelemetryManager;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** The Active framework and its tracer, Battering Ram (#245). */
public class ActiveArtsTest {

    private Player player;
    private Maze maze;
    private Monster foe;
    /** Whether the arena's next blow lands. */
    private boolean blowsLand = true;
    private int extraDamage;
    private final List<String> log = new ArrayList<>();

    private final ActiveArts.Arena arena = new ActiveArts.Arena() {
        @Override
        public Maze maze() {
            return maze;
        }

        @Override
        public Monster foeInFront() {
            return maze.getMonsters().get(new GridPoint2(4, 3));
        }

        @Override
        public boolean blow(Monster target, Item weapon, String word) {
            return blowsLand;
        }

        @Override
        public void hurt(Monster target, int damage, String word) {
            extraDamage += damage;
        }

        @Override
        public int roll(String dice) {
            return 4;
        }

        @Override
        public void log(String line) {
            log.add(line);
        }
    };

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        TelemetryManager.getInstance().startNewRun();
        player = new Player(3f, 3f);
        player.setFacing(Direction.EAST);
        maze = new Maze(1, new int[8][8]);
        ItemTemplate t = new ItemTemplate();
        t.isWeapon = true;
        t.weaponFamily = "BLUNT";
        player.getInventory().setRightHand(Item.fromTemplate(Item.ItemType.SWORD, t));
        player.learnArt(CombatArt.BATTERING_RAM, null);
        player.getStats().getMastery().setRank(CombatArt.BATTERING_RAM.family, Rank.EXPERT);
    }

    private Monster foeAt(int x, int y, MonsterRole role) {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 1;
        t.role = role;
        t.xpMultiplier = 1f;
        t.maxHP = 30;
        Monster m = new Monster(Monster.MonsterType.KOBOLD, 30, 10, x, y);
        m.setTemplate(t);
        m.setCurrentHP(30);
        maze.addMonster(m);
        return m;
    }

    private static GridPoint2 tile(Monster m) {
        return new GridPoint2((int) m.getPosition().x, (int) m.getPosition().y);
    }

    @Test
    public void batteringRamKnocksTheFoeBackAndDazesIt() {
        foe = foeAt(4, 3, MonsterRole.SOLDIER);

        assertEquals(ActiveArts.Outcome.USED, ActiveArts.use(player, 0, arena));

        assertEquals(new GridPoint2(5, 3), tile(foe));
        assertTrue(foe.isDazed());
        assertEquals(1, log.size());
        assertTrue(log.get(0), log.get(0).startsWith("Battering Ram knocks"));
    }

    @Test
    public void intoAWallItHurtsMoreAndStuns() {
        maze.setTile(4, 3, Direction.EAST.getWallMask());
        foe = foeAt(4, 3, MonsterRole.SOLDIER);

        ActiveArts.use(player, 0, arena);

        assertEquals(new GridPoint2(4, 3), tile(foe));
        assertEquals(4, extraDamage);
        assertTrue(foe.isStunned());
    }

    @Test
    public void aHeavyHoldsItsGroundButIsDazed() {
        foe = foeAt(4, 3, MonsterRole.HEAVY);

        ActiveArts.use(player, 0, arena);

        assertEquals(new GridPoint2(4, 3), tile(foe));
        assertTrue(foe.isDazed());
        assertEquals(0, extraDamage);
    }

    @Test
    public void usingItStartsTheCooldownAndCountsInTelemetry() {
        foe = foeAt(4, 3, MonsterRole.SOLDIER);
        ActiveArts.use(player, 0, arena);

        assertEquals(CombatArt.ACTIVE_COOLDOWN, player.getArtCooldowns().turnsLeft(CombatArt.BATTERING_RAM));
        assertTrue(TelemetryManager.getInstance().toJson().contains("\"BATTERING_RAM\": 1"));
        assertEquals("a cooling Art cannot be used", ActiveArts.Outcome.COOLING, ActiveArts.use(player, 0, arena));
    }

    @Test
    public void aMissIsStillAUse() {
        foe = foeAt(4, 3, MonsterRole.SOLDIER);
        blowsLand = false;

        assertEquals(ActiveArts.Outcome.USED, ActiveArts.use(player, 0, arena));

        assertEquals(new GridPoint2(4, 3), tile(foe));
        assertFalse(foe.isDazed());
        assertTrue(player.getArtCooldowns().isCooling(CombatArt.BATTERING_RAM));
    }

    @Test
    public void aKeptArtCoolsDoubled() {
        player.getStats().getMastery().setRank(CombatArt.BATTERING_RAM.family, Rank.NOVICE);
        foe = foeAt(4, 3, MonsterRole.SOLDIER);
        ActiveArts.use(player, 0, arena);
        assertEquals(2 * CombatArt.ACTIVE_COOLDOWN, player.getArtCooldowns().turnsLeft(CombatArt.BATTERING_RAM));
    }

    @Test
    public void withNoFoeInReachNothingIsSpent() {
        assertEquals(ActiveArts.Outcome.NO_TARGET, ActiveArts.use(player, 0, arena));
        assertFalse(player.getArtCooldowns().isCooling(CombatArt.BATTERING_RAM));
        assertFalse(TelemetryManager.getInstance().toJson().contains("BATTERING_RAM"));
    }

    @Test
    public void anEmptySlotDoesNothing() {
        assertEquals(ActiveArts.Outcome.EMPTY, ActiveArts.use(player, 1, arena));
        assertEquals(ActiveArts.Outcome.EMPTY, ActiveArts.use(player, 7, arena));
        assertTrue(log.isEmpty());
    }

    @Test
    public void everyLogLineSurvivesTheFont() {
        foe = foeAt(4, 3, MonsterRole.SOLDIER);
        ActiveArts.use(player, 0, arena);
        ActiveArts.use(player, 0, arena);
        for (String line : log) assertEquals(line, com.bpm.minotaur.ui.UiGlyphs.sanitize(line));
    }
}
