package com.bpm.minotaur.gamedata.arts;

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

/**
 * The ranged Expert Actives (#247, spec sections 2 and 4) against a stand-in for the combat code,
 * including the readied-action cases from the #225 prototype.
 */
public class RangedActivesTest {

    private Player player;
    private Maze maze;
    /** What the stand-in arena sees down the line, and whether the weapon is loaded. */
    private Monster inLine;
    private boolean loaded = true;
    private boolean reloading = false;
    private boolean shotsLand = true;
    private final List<ActiveArts.Shot> shots = new ArrayList<>();
    private final List<Monster> shotAt = new ArrayList<>();
    private int splashDamage = -1;
    private Monster splashAround;
    private final List<String> log = new ArrayList<>();

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
        public Monster foeInLine(Item weapon, boolean thrown) {
            return inLine;
        }

        @Override
        public boolean loaded(Item weapon) {
            return loaded;
        }

        @Override
        public boolean reloading(Item weapon) {
            return reloading;
        }

        @Override
        public boolean shoot(Monster target, Item weapon, String word, ActiveArts.Shot kind) {
            shots.add(kind);
            shotAt.add(target);
            if (shotsLand) target.takeDamage(kind == ActiveArts.Shot.HEADSHOT ? 10 : 2, false, true);
            return shotsLand;
        }

        @Override
        public ActiveArts.Hurl hurl(Item weapon) {
            return inLine == null ? ActiveArts.Hurl.MISSED : new ActiveArts.Hurl(inLine, 8);
        }

        @Override
        public void splash(Monster around, int damage, String word) {
            splashAround = around;
            splashDamage = damage;
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

    private void wield(String family, CombatArt art) {
        ItemTemplate t = new ItemTemplate();
        t.isWeapon = true;
        t.isRanged = !"THROWN".equals(family);
        t.weaponFamily = family;
        player.getInventory().setRightHand(Item.fromTemplate(Item.ItemType.SWORD, t));
        player.learnArt(art, null);
        player.getStats().getMastery().setRank(art.family, Rank.EXPERT);
    }

    private Monster foe(int x, Monster.MonsterType type) {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 1;
        t.role = MonsterRole.SOLDIER;
        t.xpMultiplier = 1f;
        t.maxHP = 40;
        Monster m = new Monster(type, 40, 10, x, 3);
        m.setTemplate(t);
        m.setCurrentHP(40);
        maze.addMonster(m);
        return m;
    }

    private boolean cooling(CombatArt art) {
        return player.getArtCooldowns().isCooling(art);
    }

    // --- Held Arrow: the stance (section 4) ---

    @Test
    public void heldArrowReadiesTakesTheTurnAndStartsNoCooldown() {
        wield("BOW", CombatArt.HELD_ARROW);

        ActiveArts.Outcome readied = ActiveArts.use(player, 0, arena);

        assertEquals(ActiveArts.Outcome.READIED, readied);
        assertTrue(readied.takesTurn());
        assertEquals(CombatArt.HELD_ARROW, player.getHeldArt());
        assertFalse("the cooldown waits for the shot or the lapse", cooling(CombatArt.HELD_ARROW));
        assertEquals(ArtBar.State.HELD, ArtBar.slots(player)[0].state);
    }

    @Test
    public void pressingTheKeyAgainLowersTheBowWithoutSpendingTheShot() {
        wield("BOW", CombatArt.HELD_ARROW);
        ActiveArts.use(player, 0, arena);

        ActiveArts.Outcome lowered = ActiveArts.use(player, 0, arena);

        assertEquals(ActiveArts.Outcome.LOWERED, lowered);
        assertFalse(lowered.takesTurn());
        assertNull(player.getHeldArt());
        assertFalse(cooling(CombatArt.HELD_ARROW));
        assertTrue(shots.isEmpty());
    }

    @Test
    public void heldArrowNeedsSomethingToLoose() {
        wield("BOW", CombatArt.HELD_ARROW);
        loaded = false;
        assertEquals(ActiveArts.Outcome.NOT_LOADED, ActiveArts.use(player, 0, arena));
        assertNull(player.getHeldArt());
    }

    @Test
    public void waitingKeepsTheStanceAndAnyOtherActionLetsItLapseIntoTheCooldown() {
        wield("BOW", CombatArt.HELD_ARROW);
        ActiveArts.use(player, 0, arena);
        ActiveArts.turnPassed(player, arena); // the turn it was drawn
        player.keepHold(); // a wait
        ActiveArts.turnPassed(player, arena);
        assertEquals("still drawn after a wait", CombatArt.HELD_ARROW, player.getHeldArt());

        ActiveArts.turnPassed(player, arena); // a step, a swing: anything but a wait
        assertNull(player.getHeldArt());
        assertTrue("the cooldown starts when the stance lapses", cooling(CombatArt.HELD_ARROW));
    }

    // --- Held Arrow: the shot (the #225 walkthroughs) ---

    @Test
    public void theHappyPathAFoeEntersTheLineAndTakesTheShot() {
        wield("BOW", CombatArt.HELD_ARROW);
        Monster goblin = foe(6, Monster.MonsterType.GOBLIN);
        ActiveArts.use(player, 0, arena);
        inLine = goblin;

        assertTrue(ActiveArts.answerHeldShot(player, goblin, arena));

        assertEquals(List.of(ActiveArts.Shot.REACTION), shots);
        assertNull("one shot: the stance is spent", player.getHeldArt());
        assertTrue(cooling(CombatArt.HELD_ARROW));
        assertTrue(TelemetryManager.getInstance().toJson().contains("\"HELD_ARROW\": 1"));
    }

    @Test
    public void aFoeOutsideTheLineDrawsNoShot() {
        wield("BOW", CombatArt.HELD_ARROW);
        Monster goblin = foe(6, Monster.MonsterType.GOBLIN);
        ActiveArts.use(player, 0, arena);
        inLine = null;

        assertFalse(ActiveArts.answerHeldShot(player, goblin, arena));

        assertTrue(shots.isEmpty());
        assertEquals(CombatArt.HELD_ARROW, player.getHeldArt());
    }

    @Test
    public void aFoeBehindAnotherInTheLineIsNotTheOneShot() {
        wield("BOW", CombatArt.HELD_ARROW);
        Monster near = foe(5, Monster.MonsterType.GOBLIN);
        Monster far = foe(7, Monster.MonsterType.GOBLIN);
        ActiveArts.use(player, 0, arena);
        inLine = near;

        assertFalse("the shot would strike the nearer goblin", ActiveArts.answerHeldShot(player, far, arena));
        assertTrue(shots.isEmpty());
    }

    @Test
    public void twoGoblinsOneShot() {
        wield("BOW", CombatArt.HELD_ARROW);
        Monster first = foe(5, Monster.MonsterType.GOBLIN);
        Monster second = foe(6, Monster.MonsterType.GOBLIN);
        ActiveArts.use(player, 0, arena);

        inLine = first;
        assertTrue(ActiveArts.answerHeldShot(player, first, arena));
        inLine = second;
        assertFalse("the stance was spent on the first", ActiveArts.answerHeldShot(player, second, arena));

        assertEquals(List.of(first), shotAt);
    }

    @Test
    public void withNoArtDrawnNothingAnswers() {
        wield("BOW", CombatArt.HELD_ARROW);
        Monster goblin = foe(6, Monster.MonsterType.GOBLIN);
        inLine = goblin;
        assertFalse(ActiveArts.answerHeldShot(player, goblin, arena));
        assertTrue(shots.isEmpty());
    }

    // --- Nail in Place (Crossbow) ---

    @Test
    public void nailInPlacePinsWhatItHits() {
        wield("CROSSBOW", CombatArt.NAIL_IN_PLACE);
        Monster foe = foe(6, Monster.MonsterType.GOBLIN);
        inLine = foe;

        assertEquals(ActiveArts.Outcome.USED, ActiveArts.use(player, 0, arena));

        assertEquals(List.of(ActiveArts.Shot.NORMAL), shots);
        assertTrue(foe.isPinned());
        assertTrue(cooling(CombatArt.NAIL_IN_PLACE));
    }

    @Test
    public void aMissedNailPinsNothing() {
        wield("CROSSBOW", CombatArt.NAIL_IN_PLACE);
        Monster foe = foe(6, Monster.MonsterType.GOBLIN);
        inLine = foe;
        shotsLand = false;

        assertEquals(ActiveArts.Outcome.USED, ActiveArts.use(player, 0, arena));
        assertFalse(foe.isPinned());
    }

    @Test
    public void nailInPlaceWithNothingInTheLineIsNotSpent() {
        wield("CROSSBOW", CombatArt.NAIL_IN_PLACE);
        assertEquals(ActiveArts.Outcome.NO_TARGET, ActiveArts.use(player, 0, arena));
        assertFalse(cooling(CombatArt.NAIL_IN_PLACE));
    }

    // --- Marked Skull (Firearm) ---

    @Test
    public void markedSkullAimsForATurnThenTakesTheHead() {
        wield("FIREARM", CombatArt.MARKED_SKULL);
        Monster foe = foe(6, Monster.MonsterType.GOBLIN);
        inLine = foe;

        assertEquals(ActiveArts.Outcome.READIED, ActiveArts.use(player, 0, arena));
        assertTrue("aiming fires nothing", shots.isEmpty());
        assertEquals(ActiveArts.Outcome.USED, ActiveArts.use(player, 0, arena));

        assertEquals(List.of(ActiveArts.Shot.HEADSHOT), shots);
        assertTrue(foe.isDazed());
        assertNull(player.getHeldArt());
        assertTrue(cooling(CombatArt.MARKED_SKULL));
    }

    @Test
    public void aMonsterWithoutAHeadTakesANormalShot() {
        wield("FIREARM", CombatArt.MARKED_SKULL);
        Monster slime = foe(6, Monster.MonsterType.GELATINOUS_CUBE);
        assertFalse(slime.hasHead());
        inLine = slime;

        ActiveArts.use(player, 0, arena);
        ActiveArts.use(player, 0, arena);

        assertEquals(List.of(ActiveArts.Shot.NORMAL), shots);
        assertFalse(slime.isDazed());
    }

    @Test
    public void aGunWithNothingToLoadCannotAim() {
        wield("FIREARM", CombatArt.MARKED_SKULL);
        inLine = foe(6, Monster.MonsterType.GOBLIN);
        loaded = false;
        assertEquals(ActiveArts.Outcome.NOT_LOADED, ActiveArts.use(player, 0, arena));
        assertNull(player.getHeldArt());
    }

    @Test
    public void aimingWhileLoadingIsAllowedFiringIsNot() {
        wield("FIREARM", CombatArt.MARKED_SKULL);
        inLine = foe(6, Monster.MonsterType.GOBLIN);
        loaded = false;
        reloading = true;

        assertEquals(ActiveArts.Outcome.READIED, ActiveArts.use(player, 0, arena));
        assertEquals(ActiveArts.Outcome.NOT_LOADED, ActiveArts.use(player, 0, arena));
        assertTrue("no shot", shots.isEmpty());
        assertEquals("the aim is kept", CombatArt.MARKED_SKULL, player.getHeldArt());

        loaded = true;
        reloading = false;
        assertEquals(ActiveArts.Outcome.USED, ActiveArts.use(player, 0, arena));
        assertEquals(List.of(ActiveArts.Shot.HEADSHOT), shots);
    }

    @Test
    public void markedSkullsAimHoldsThroughAWaitAndLapsesOnAnythingElse() {
        wield("FIREARM", CombatArt.MARKED_SKULL);
        inLine = foe(6, Monster.MonsterType.GOBLIN);
        ActiveArts.use(player, 0, arena);
        ActiveArts.turnPassed(player, arena); // the aiming turn
        player.keepHold();
        ActiveArts.turnPassed(player, arena); // a wait
        assertEquals(CombatArt.MARKED_SKULL, player.getHeldArt());

        ActiveArts.turnPassed(player, arena);
        assertNull(player.getHeldArt());
        assertTrue(cooling(CombatArt.MARKED_SKULL));
    }

    @Test
    public void markedSkullsShotIsForTheFoeItAimedAt() {
        wield("FIREARM", CombatArt.MARKED_SKULL);
        Monster first = foe(6, Monster.MonsterType.GOBLIN);
        inLine = first;
        ActiveArts.use(player, 0, arena);
        Monster second = foe(5, Monster.MonsterType.GOBLIN);
        inLine = second; // another stepped in front

        assertEquals("the aim moves to the new foe, and the turn is spent again",
                ActiveArts.Outcome.READIED, ActiveArts.use(player, 0, arena));
        assertTrue(shots.isEmpty());
        assertEquals(ActiveArts.Outcome.USED, ActiveArts.use(player, 0, arena));
        assertEquals(List.of(second), shotAt);
    }

    @Test
    public void readyingOneArtWhileAnotherIsDrawnLetsTheFirstLapse() {
        wield("BOW", CombatArt.HELD_ARROW);
        ItemTemplate gun = new ItemTemplate();
        gun.isWeapon = true;
        gun.isRanged = true;
        gun.weaponFamily = "FIREARM";
        player.getInventory().setLeftHand(Item.fromTemplate(Item.ItemType.SWORD, gun));
        player.learnArt(CombatArt.MARKED_SKULL, null);
        player.getStats().getMastery().setRank(CombatArt.MARKED_SKULL.family, Rank.EXPERT);
        inLine = foe(6, Monster.MonsterType.GOBLIN);
        ActiveArts.use(player, 0, arena); // Held Arrow drawn

        assertEquals(ActiveArts.Outcome.READIED, ActiveArts.use(player, 2, arena)); // Marked Skull, left hand

        assertEquals(CombatArt.MARKED_SKULL, player.getHeldArt());
        assertTrue("the arrow's stance lapsed into its cooldown", cooling(CombatArt.HELD_ARROW));
    }

    // --- Shattering Throw (Thrown) ---

    @Test
    public void shatteringThrowBurstsForHalfOnEverythingBesideItsTarget() {
        wield("THROWN", CombatArt.SHATTERING_THROW);
        Monster foe = foe(4, Monster.MonsterType.GOBLIN);
        inLine = foe;

        assertEquals(ActiveArts.Outcome.USED, ActiveArts.use(player, 0, arena));

        assertSame(foe, splashAround);
        assertEquals("half the throw's 8", 4, splashDamage);
        assertTrue(TelemetryManager.getInstance().toJson().contains("\"SHATTERING_THROW\": 1"));
    }

    @Test
    public void aShatteringThrowThatStrikesNothingBurstsOnNothing() {
        wield("THROWN", CombatArt.SHATTERING_THROW);
        assertEquals(ActiveArts.Outcome.NO_TARGET, ActiveArts.use(player, 0, arena));
        assertEquals(-1, splashDamage);
    }

    @Test
    public void everyRangedLogLineSurvivesTheFont() {
        wield("CROSSBOW", CombatArt.NAIL_IN_PLACE);
        inLine = foe(6, Monster.MonsterType.GOBLIN);
        ActiveArts.use(player, 0, arena);
        for (String line : log) assertEquals(line, com.bpm.minotaur.ui.UiGlyphs.sanitize(line));
    }
}
