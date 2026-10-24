package com.bpm.minotaur.gamedata.arts;

import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.WeaponFamily;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.telemetry.TelemetryManager;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * The ten Master capstones (#248, spec section 2): each makes its Family's Adept Passive certain
 * for 5 turns, then puts the Expert Active on cooldown. A capstone cools for 50.
 */
public class CapstonesTest {

    private Player player;
    private Maze maze;
    private final List<String> log = new ArrayList<>();
    private final List<String> words = new ArrayList<>();
    private final AdeptPassives.FreeStrike strike = (target, word) -> words.add(word);

    private final ActiveArts.Arena arena = new ActiveArts.Arena() {
        @Override
        public Maze maze() {
            return maze;
        }

        @Override
        public Monster foeAhead(int tiles) {
            return null;
        }

        @Override
        public boolean stepForward() {
            return false;
        }

        @Override
        public boolean blow(Monster target, Item weapon, String word, int toHitBonus, int extraDamage) {
            return false;
        }

        @Override
        public void hurt(Monster target, int damage, String word) {
        }

        @Override
        public int roll(String dice) {
            return 1;
        }

        @Override
        public void log(String line) {
            log.add(line);
        }

        @Override
        public Monster foeInLine(Item weapon, boolean thrown) {
            return null;
        }

        @Override
        public boolean loaded(Item weapon) {
            return false;
        }

        @Override
        public boolean shoot(Monster target, Item weapon, String word, ActiveArts.Shot kind) {
            return false;
        }

        @Override
        public ActiveArts.Hurl hurl(Item weapon) {
            return ActiveArts.Hurl.MISSED;
        }

        @Override
        public void splash(Monster around, int damage, String word) {
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
    }

    /** The soul knows all three of {@code family}'s Arts and this body is a Master of it. */
    private Item master(WeaponFamily family) {
        for (Rank rank : new Rank[]{Rank.ADEPT, Rank.EXPERT, Rank.MASTER}) {
            player.learnArt(CombatArt.of(family, rank), null);
        }
        player.getStats().getMastery().setRank(family, Rank.MASTER);
        if (family == WeaponFamily.UNARMED) return null;
        ItemTemplate t = new ItemTemplate();
        t.isWeapon = true;
        t.isRanged = family == WeaponFamily.BOW || family == WeaponFamily.CROSSBOW || family == WeaponFamily.FIREARM;
        t.weaponFamily = family.name();
        Item weapon = Item.fromTemplate(Item.ItemType.SWORD, t);
        player.getInventory().setRightHand(weapon);
        return weapon;
    }

    private Monster foeAt(int x, int y) {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 1;
        t.maxHP = 40;
        Monster m = new Monster(Monster.MonsterType.KOBOLD, 40, 10, x, y);
        m.setTemplate(t);
        m.setCurrentHP(40);
        maze.addMonster(m);
        return m;
    }

    /** A roll that never comes in under any chance short of certain. */
    private static AdeptPassives neverByChance() {
        return new AdeptPassives(() -> 0.999);
    }

    @Test
    public void everyFamilyHasACapstoneThatSlotsSecond() {
        for (WeaponFamily family : WeaponFamily.values()) {
            if (family == WeaponFamily.NONE) continue;
            setUp();
            master(family);
            CombatArt capstone = CombatArt.of(family, Rank.MASTER);
            assertNotNull(family.name(), capstone);
            assertEquals(family.name(), capstone, ArtBar.contents(player)[1]);
        }
    }

    @Test
    public void usingACapstoneOpensTheWindowLogsALineCountsAndCoolsForFifty() {
        master(WeaponFamily.BLUNT);

        assertEquals(ActiveArts.Outcome.USED, ActiveArts.use(player, 1, arena));

        assertTrue(player.getArtCooldowns().isCertain(WeaponFamily.BLUNT));
        assertEquals(CombatArt.CAPSTONE_COOLDOWN, player.getArtCooldowns().turnsLeft(CombatArt.TOLL_THE_BELL));
        assertEquals(1, log.size());
        assertEquals(log.get(0), com.bpm.minotaur.ui.UiGlyphs.sanitize(log.get(0)));
        assertTrue(TelemetryManager.getInstance().toJson().contains("\"TOLL_THE_BELL\": 1"));
    }

    @Test
    public void inTheWindowThePassiveIsCertain() {
        Item mace = master(WeaponFamily.BLUNT);
        Monster foe = foeAt(4, 3);
        AdeptPassives passives = neverByChance();
        assertNull("by chance alone, it never rings", passives.onFullHit(player, mace, foe, maze, 3, strike));

        ActiveArts.use(player, 1, arena);

        assertEquals("DAZE", passives.onFullHit(player, mace, foe, maze, 3, strike));
        assertEquals("every full hit rings", "STUN", passives.onFullHit(player, mace, foe, maze, 3, strike));
    }

    @Test
    public void theWindowIsFiveTurnsAfterTheUseThenTheExpertActiveCools() {
        Item mace = master(WeaponFamily.BLUNT);
        Monster foe = foeAt(4, 3);
        AdeptPassives passives = neverByChance();
        ActiveArts.use(player, 1, arena);
        player.getArtCooldowns().tick(); // the turn of use

        for (int turn = 0; turn < CombatArt.CAPSTONE_TURNS; turn++) {
            assertTrue("turn " + turn, player.getArtCooldowns().isCertain(WeaponFamily.BLUNT));
            assertFalse("the Expert Active is free in the window", player.getArtCooldowns().isCooling(CombatArt.BATTERING_RAM));
            player.getArtCooldowns().tick();
        }

        assertFalse(player.getArtCooldowns().isCertain(WeaponFamily.BLUNT));
        assertNull(passives.onFullHit(player, mace, foe, maze, 3, strike));
        assertEquals("the hand-off: the Expert Active goes on its cooldown", CombatArt.ACTIVE_COOLDOWN,
                player.getArtCooldowns().turnsLeft(CombatArt.BATTERING_RAM));
    }

    @Test
    public void aKeptExpertActiveIsHandedOffAtItsDoubledCooldown() {
        master(WeaponFamily.BLUNT);
        player.getStats().getMastery().setRank(WeaponFamily.BLUNT, Rank.ADEPT); // a later body, below Expert
        ActiveArts.use(player, 1, arena);
        for (int turn = 0; turn <= CombatArt.CAPSTONE_TURNS; turn++) player.getArtCooldowns().tick();
        assertEquals(2 * CombatArt.ACTIVE_COOLDOWN, player.getArtCooldowns().turnsLeft(CombatArt.BATTERING_RAM));
    }

    @Test
    public void thicketOfPointsStrikesEveryFoeThatClosesEvenTwice() {
        Item spear = master(WeaponFamily.POLEARM);
        Monster foe = foeAt(4, 3);
        AdeptPassives passives = new AdeptPassives(() -> 0.0);
        passives.onMonsterStepsAdjacent(player, spear, foe, strike);
        passives.onMonsterStepsAdjacent(player, spear, foe, strike);
        assertEquals("Hold the Line alone strikes a foe once", 1, words.size());

        ActiveArts.use(player, 1, arena);
        passives.onMonsterStepsAdjacent(player, spear, foe, strike);

        assertEquals("in the Thicket, it closes and is struck again", 2, words.size());
    }

    @Test
    public void whirlingEdgeAnswersEveryMiss() {
        Item sword = master(WeaponFamily.BLADE);
        Monster foe = foeAt(4, 3);
        AdeptPassives passives = neverByChance();
        passives.onMonsterMeleeMiss(player, sword, foe, strike);
        assertTrue(words.isEmpty());

        ActiveArts.use(player, 1, arena);
        passives.onMonsterMeleeMiss(player, sword, foe, strike);

        assertEquals(List.of(AdeptPassives.COUNTER), words);
    }

    @Test
    public void hailOfIronSlowsEveryThrownHit() {
        Item dart = master(WeaponFamily.THROWN);
        Monster foe = foeAt(5, 3);
        AdeptPassives passives = neverByChance();
        assertNull(passives.onThrownHit(player, dart, foe));

        ActiveArts.use(player, 1, arena);

        assertEquals(AdeptPassives.TANGLE, passives.onThrownHit(player, dart, foe));
    }

    @Test
    public void everyCapstoneOpensItsOwnFamilysWindowAndNoOther() {
        for (WeaponFamily family : WeaponFamily.values()) {
            if (family == WeaponFamily.NONE) continue;
            setUp();
            master(family);
            assertEquals(family.name(), ActiveArts.Outcome.USED, ActiveArts.use(player, 1, arena));
            for (WeaponFamily other : WeaponFamily.values()) {
                assertEquals(family + " / " + other, other == family, player.getArtCooldowns().isCertain(other));
            }
        }
    }

    @Test
    public void anOpenWindowAndItsHandOffSurviveTheSave() {
        master(WeaponFamily.BLUNT);
        ActiveArts.use(player, 1, arena);
        player.getArtCooldowns().tick();
        player.getArtCooldowns().tick();
        com.badlogic.gdx.utils.Json json = new com.badlogic.gdx.utils.Json();
        json.setOutputType(com.badlogic.gdx.utils.JsonWriter.OutputType.json);
        com.bpm.minotaur.gamedata.save.PlayerSaveData loaded = json.fromJson(
                com.bpm.minotaur.gamedata.save.PlayerSaveData.class,
                json.toJson(new com.bpm.minotaur.gamedata.save.PlayerSaveData(player)));

        ArtCooldowns restored = new ArtCooldowns();
        restored.restore(loaded.artCooldowns);
        restored.restoreWindows(loaded.artWindows, loaded.artHandOffs);

        assertTrue(restored.isCertain(WeaponFamily.BLUNT));
        int ticks = 0;
        while (restored.isCertain(WeaponFamily.BLUNT) && ticks < 10) {
            restored.tick();
            ticks++;
        }
        assertEquals("the reload kept the window's four turns left", CombatArt.CAPSTONE_TURNS - 1, ticks);
        assertEquals("the hand-off came through the reload", CombatArt.ACTIVE_COOLDOWN,
                restored.turnsLeft(CombatArt.BATTERING_RAM));
    }

    @Test
    public void theHandOffNeverShortensALongerCooldown() {
        ArtCooldowns cooldowns = new ArtCooldowns();
        cooldowns.openWindow(WeaponFamily.BLUNT, 1, 5);
        cooldowns.start(CombatArt.BATTERING_RAM, 40);
        cooldowns.tick(); // the turn of use: neither counts
        cooldowns.tick(); // the window closes, handing off 5 against the 39 left
        assertEquals("kept at 39, not cut to 5", 39, cooldowns.turnsLeft(CombatArt.BATTERING_RAM));
    }

    @Test
    public void aCapstoneDescribesItsWindowWithItsOwnLength() {
        for (CombatArt art : CombatArt.values()) {
            if (art.kind != CombatArt.Kind.CAPSTONE) {
                assertEquals(art.summary, art.describe());
                continue;
            }
            assertTrue(art.describe(), art.describe().startsWith("For " + CombatArt.CAPSTONE_TURNS + " turns, "));
            assertFalse("the length is not typed into the summary", art.summary.contains("turns"));
        }
    }

    @Test
    public void aCoolingCapstoneCannotBeUsed() {
        master(WeaponFamily.AXE);
        ActiveArts.use(player, 1, arena);
        assertEquals(ActiveArts.Outcome.COOLING, ActiveArts.use(player, 1, arena));
    }
}
