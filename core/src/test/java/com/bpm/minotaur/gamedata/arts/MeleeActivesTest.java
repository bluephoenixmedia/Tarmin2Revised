package com.bpm.minotaur.gamedata.arts;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
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

/** The melee Expert Actives (#246, spec section 2), against a stand-in for the combat code. */
public class MeleeActivesTest {

    private Player player;
    private Maze maze;
    private boolean blowsLand = true;
    /** What the last blow asked for. */
    private int lastToHit, lastExtra = -1;
    private final List<String> log = new ArrayList<>();

    /** A stand-in arena: real maze lookups, a step that just moves, blows that land as told. */
    private final ActiveArts.Arena arena = new ActiveArts.Arena() {
        @Override
        public Maze maze() {
            return maze;
        }

        @Override
        public Monster foeAhead(int tiles) {
            int x = (int) player.getPosition().x, y = (int) player.getPosition().y;
            Direction d = player.getFacing();
            for (int i = 1; i <= tiles; i++) {
                if (maze.isWallBlocking(x, y, d)) return null;
                x += (int) d.getVector().x;
                y += (int) d.getVector().y;
                Monster m = maze.getMonsters().get(new GridPoint2(x, y));
                if (i < tiles && m != null) return null;
                if (i == tiles) return m;
            }
            return null;
        }

        @Override
        public boolean stepForward() {
            player.getPosition().add(player.getFacing().getVector());
            return true;
        }

        @Override
        public boolean blow(Monster target, Item weapon, String word, int toHitBonus, int extraDamage) {
            lastToHit = toHitBonus;
            lastExtra = extraDamage;
            if (blowsLand) target.takeDamage(1 + extraDamage, false, true);
            return blowsLand;
        }

        @Override
        public void hurt(Monster target, int damage, String word) {
        }

        @Override
        public int roll(String dice) {
            // Every die shows 2: "3d4" is 6.
            String[] parts = dice.split("d");
            return Integer.parseInt(parts[0]) * 2;
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
        if (family != null) {
            ItemTemplate t = new ItemTemplate();
            t.isWeapon = true;
            t.weaponFamily = family;
            player.getInventory().setRightHand(Item.fromTemplate(Item.ItemType.SWORD, t));
        }
        player.learnArt(art, null);
        player.getStats().getMastery().setRank(art.family, Rank.EXPERT);
    }

    private Monster foeAt(int x, int y, MonsterRole role) {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 1;
        t.role = role;
        t.xpMultiplier = 1f;
        t.maxHP = 40;
        Monster m = new Monster(Monster.MonsterType.KOBOLD, 40, 10, x, y);
        m.setTemplate(t);
        m.setCurrentHP(40);
        maze.addMonster(m);
        return m;
    }

    private static GridPoint2 tile(Monster m) {
        return new GridPoint2((int) m.getPosition().x, (int) m.getPosition().y);
    }

    private GridPoint2 playerTile() {
        return new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);
    }

    // --- Closing Thrust (Blade) ---

    @Test
    public void closingThrustStepsInAndStrikesAFoeTwoTilesAheadAtPlusTwo() {
        wield("BLADE", CombatArt.CLOSING_THRUST);
        Monster foe = foeAt(5, 3, MonsterRole.SOLDIER);

        assertEquals(ActiveArts.Outcome.USED, ActiveArts.use(player, 0, arena));

        assertEquals("the player stepped in", new GridPoint2(4, 3), playerTile());
        assertEquals(ActiveArts.CLOSING_TO_HIT, lastToHit);
        assertTrue(foe.getCurrentHP() < 40);
        assertTrue(TelemetryManager.getInstance().toJson().contains("\"CLOSING_THRUST\": 1"));
    }

    @Test
    public void closingThrustOnAnAdjacentFoeStrikesWithoutAStep() {
        wield("BLADE", CombatArt.CLOSING_THRUST);
        foeAt(4, 3, MonsterRole.SOLDIER);

        assertEquals(ActiveArts.Outcome.USED, ActiveArts.use(player, 0, arena));
        assertEquals(new GridPoint2(3, 3), playerTile());
    }

    @Test
    public void closingThrustThroughAWallFindsNoFoe() {
        wield("BLADE", CombatArt.CLOSING_THRUST);
        maze.setTile(4, 3, Direction.EAST.getWallMask());
        foeAt(5, 3, MonsterRole.SOLDIER);

        assertEquals(ActiveArts.Outcome.NO_TARGET, ActiveArts.use(player, 0, arena));
        assertEquals(new GridPoint2(3, 3), playerTile());
    }

    // --- Gutting Stab (Dagger) ---

    @Test
    public void guttingStabAddsADieForEveryStatusOnTheTarget() {
        wield("DAGGER", CombatArt.GUTTING_STAB);
        Monster foe = foeAt(4, 3, MonsterRole.SOLDIER);
        foe.applyBleed(3, 1);
        foe.applyDaze();
        foe.applyPin();

        ActiveArts.use(player, 0, arena);

        assertEquals("three statuses, 3d4, every die a 2", 6, lastExtra);
    }

    @Test
    public void guttingStabOnAnUntouchedFoeIsAPlainStab() {
        wield("DAGGER", CombatArt.GUTTING_STAB);
        foeAt(4, 3, MonsterRole.SOLDIER);

        ActiveArts.use(player, 0, arena);

        assertEquals(0, lastExtra);
    }

    @Test
    public void everyStatusTheSpecNamesCounts() {
        Monster foe = foeAt(4, 3, MonsterRole.SOLDIER);
        assertEquals(0, ActiveArts.statusesOn(foe));
        foe.applyBleed(3, 1);
        foe.getStatusManager().addEffect(StatusEffectType.SLOWED, 2, 1, false);
        foe.applyDaze();
        foe.applyPin();
        foe.applyStun(2);
        assertEquals("Bleeding, Slowed, Dazed, Pinned, Stunned", 5, ActiveArts.statusesOn(foe));
    }

    // --- Hew Through (Axe) ---

    @Test
    public void hewThroughStripsArmourForFiveTurnsAndDrawsBlood() {
        wield("AXE", CombatArt.HEW_THROUGH);
        Monster foe = foeAt(4, 3, MonsterRole.SOLDIER);
        int ac = foe.getArmorClass();

        ActiveArts.use(player, 0, arena);

        assertEquals(ac - Monster.HEW_ARMOR, foe.getArmorClass());
        assertTrue(foe.getBleedTurns() > 0);
        for (int i = 0; i < Monster.HEW_TURNS; i++) foe.endTurn();
        assertEquals("the armour knits back", ac, foe.getArmorClass());
    }

    @Test
    public void aMissedHewStripsNothing() {
        wield("AXE", CombatArt.HEW_THROUGH);
        Monster foe = foeAt(4, 3, MonsterRole.SOLDIER);
        int ac = foe.getArmorClass();
        blowsLand = false;

        assertEquals(ActiveArts.Outcome.USED, ActiveArts.use(player, 0, arena));

        assertEquals(ac, foe.getArmorClass());
        assertEquals(0, foe.getBleedTurns());
    }

    // --- Reel In (Polearm) ---

    @Test
    public void reelInPullsAFoeTwoTilesOffIntoReachAndDazesIt() {
        wield("POLEARM", CombatArt.REEL_IN);
        Monster foe = foeAt(5, 3, MonsterRole.SOLDIER);

        assertEquals(ActiveArts.Outcome.USED, ActiveArts.use(player, 0, arena));

        assertEquals(new GridPoint2(4, 3), tile(foe));
        assertTrue(foe.isDazed());
    }

    @Test
    public void aHeavyCannotBeReeledInButIsDazed() {
        wield("POLEARM", CombatArt.REEL_IN);
        Monster foe = foeAt(5, 3, MonsterRole.HEAVY);

        ActiveArts.use(player, 0, arena);

        assertEquals(new GridPoint2(5, 3), tile(foe));
        assertTrue(foe.isDazed());
    }

    @Test
    public void aBossCannotBeReeledIn() {
        wield("POLEARM", CombatArt.REEL_IN);
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 1;
        t.maxHP = 40;
        Monster minotaur = new Monster(Monster.MonsterType.MINOTAUR, 40, 10, 5, 3);
        minotaur.setTemplate(t);
        maze.addMonster(minotaur);

        ActiveArts.use(player, 0, arena);

        assertEquals(new GridPoint2(5, 3), tile(minotaur));
    }

    @Test
    public void reelInWithNothingTwoTilesOffFindsNoFoe() {
        wield("POLEARM", CombatArt.REEL_IN);
        foeAt(4, 3, MonsterRole.SOLDIER);

        assertEquals("already in reach", ActiveArts.Outcome.NO_TARGET, ActiveArts.use(player, 0, arena));
    }

    // --- Iron Grip (Unarmed) ---

    @Test
    public void ironGripHoldsAFoeInPlaceAndSpoilsItsAimForThreeTurns() {
        wield(null, CombatArt.IRON_GRIP);
        Monster foe = foeAt(4, 3, MonsterRole.SOLDIER);

        assertEquals(ActiveArts.Outcome.USED, ActiveArts.use(player, 0, arena));

        assertTrue(foe.isPinned());
        assertTrue(foe.isGripped());
        for (int i = 0; i < Monster.GRIP_TURNS; i++) foe.endTurn();
        assertFalse(foe.isGripped());
        assertFalse(foe.isPinned());
    }

    @Test
    public void everyMeleeActiveLogsALineThatSurvivesTheFont() {
        for (CombatArt art : new CombatArt[]{CombatArt.CLOSING_THRUST, CombatArt.GUTTING_STAB,
                CombatArt.HEW_THROUGH, CombatArt.REEL_IN, CombatArt.IRON_GRIP}) {
            setUp();
            log.clear();
            String family = art == CombatArt.IRON_GRIP ? null : art.family.name();
            wield(family, art);
            foeAt(art == CombatArt.REEL_IN ? 5 : 4, 3, MonsterRole.SOLDIER);
            assertEquals(art.name(), ActiveArts.Outcome.USED, ActiveArts.use(player, 0, arena));
            assertEquals(art.name(), 1, log.size());
            assertEquals(log.get(0), com.bpm.minotaur.ui.UiGlyphs.sanitize(log.get(0)));
            assertTrue(art.name(), TelemetryManager.getInstance().toJson().contains("\"" + art.name() + "\": 1"));
        }
    }
}
