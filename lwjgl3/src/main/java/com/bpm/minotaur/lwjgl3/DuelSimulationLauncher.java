package com.bpm.minotaur.lwjgl3;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.monster.MonsterVariant;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.Mastery;
import com.bpm.minotaur.headless.HeadlessAnimationManager;
import com.bpm.minotaur.headless.HeadlessSoundManager;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.DiscoveryManager;
import com.bpm.minotaur.managers.GameEventManager;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Combat duel simulator for balance work (0.0.2 polish map, "Build the combat duel simulator").
 *
 * <p>Runs thousands of bump-combat duels between a fresh character and each monster of a level
 * band, through the real combat code: the player swings with {@link CombatManager#playerMeleeStrike}
 * and the monster answers with {@link CombatManager#monsterMeleeStrike}, so to-hit, glancing blows,
 * crits, resistance, damage and difficulty vulnerability are the game's own. Turn order follows
 * {@code TurnManager}: each player turn gives a monster {@code speed / playerSpeed} turns of energy,
 * and it acts once per 12 energy.
 *
 * <p>Reports, per monster and for the band: win rate, HP lost on a win, player swings to kill,
 * full-hit and glancing rates (glancing is not a hit), monster hit rate, monster actions per player
 * turn, and how many band fights a character survives back to back without healing.
 *
 * <pre>
 * ./gradlew :lwjgl3:runDuelSimulation -PduelArgs="--trials=2000 --minLevel=1 --maxLevel=1"
 * </pre>
 *
 * Options: --trials=N, --minLevel / --maxLevel (monster base level band), --playerLevel=L,
 * --edl=E (scale monsters to that Effective Difficulty Level, as a chunk at depth E does),
 * --difficulty=MEDIUM, --hp=N (override max HP), --weapon=ITEM_TYPE, --mastery=RANK (the wielded
 * weapon's Family starts at that Mastery rank, #232), --approach (ranged monsters shoot while the
 * player closes, #239), --passive (the soul knows the wielded Family's Adept Art, and the game's own
 * code fires it, #250), --arts (all three of the Family's Arts, the Active and capstone used by a
 * simple policy, #250), --chain=N (back-to-back runs), --seed=S, --csv=path, and --report (every
 * Family at depths 1 and 3, with and without Arts, written as a table to --reportOut=path).
 *
 * <p>A bow, crossbow, gun or thrown weapon fights from range: the monster starts four tiles off and
 * walks in, and the player shoots (or throws) each turn, waiting out a gun's reload.
 */
public class DuelSimulationLauncher implements ApplicationListener {

    /** A duel that lasts this many player turns is called a draw and stops. */
    private static final int MAX_TURNS = 200;
    private static final float TURN_COST = 12f;

    private final String[] args;

    private int trials = 2000;
    private int minLevel = 1;
    private int maxLevel = 1;
    private int playerLevel = 1;
    /** The Effective Difficulty Level the monster is scaled to, as the spawner does (#185). */
    private int edl = 1;
    private Difficulty difficulty = Difficulty.MEDIUM;
    private int hpOverride = -1;
    private int acOverride = -1;
    private Item.ItemType weaponOverride;
    /** The Mastery rank the wielded weapon's Family starts each duel at (#232). */
    private Mastery.Rank masteryRank = Mastery.Rank.UNTRAINED;
    /** Ranged monsters start at their preferred distance and shoot while the player closes (#239). */
    private boolean approach = false;
    /** The soul knows the wielded Family's Adept Art; the game's own code fires it (#250). */
    private boolean passive = false;
    /** The soul knows all three of the Family's Arts; the Active and capstone are used by policy (#250). */
    private boolean arts = false;
    /** Run every Family at depths 1 and 3, with and without Arts, and write the table (#250). */
    private boolean report = false;
    private String reportPath = "logs/duel-arts-report.md";
    /** --rows=Dagger,Unarmed: only these Families' rows of the report, for tuning (#313). Empty: all. */
    private final List<String> reportRows = new ArrayList<>();
    /** --columns=Untrained,Adept: only these columns of the report, by label (#313). Empty: all. */
    private final List<String> reportColumns = new ArrayList<>();
    /** --guardian=TYPE: duel that monster as a theme Guardian, cut to the EDL (#302). */
    private Monster.MonsterType guardian;
    /** --weapon=UNARMED: an empty right hand. */
    private boolean unarmed = false;
    private int chainRuns = 500;
    private long seed = 42L;
    private String csvPath = "logs/duel-sim.csv";
    /** Whether to fail if level-1 band metrics drift outside target bounds (#202). */
    private boolean guard = false;

    private AssetManager assetManager;
    private ItemDataManager itemDataManager;
    private MonsterDataManager monsterDataManager;
    private QuietEvents eventManager;
    private HeadlessSoundManager soundManager;
    private HeadlessAnimationManager animationManager;
    private Random rng;

    public DuelSimulationLauncher(String[] args) {
        this.args = args;
    }

    public static void main(String[] args) {
        new HeadlessApplication(new DuelSimulationLauncher(args), new HeadlessApplicationConfiguration());
    }

    /** What one duel produced. */
    private static final class Duel {
        boolean won;
        boolean draw;
        int playerTurns;
        int swings;
        int fullHits;
        int glancing;
        int monsterActions;
        int monsterHits;
        int approachShots;
        int artUses;
        int hpLost;
        int maxHp;
    }

    private static final class Tally {
        final String name;
        int n, wins, draws, swingsOnWin, playerTurns, swings, fullHits, glancing, monsterActions, monsterHits,
                approachShots, artUses;
        final List<Integer> hpLostPctOnWin = new ArrayList<>();
        final List<Integer> swingsToKill = new ArrayList<>();

        Tally(String name) {
            this.name = name;
        }

        void add(Duel d) {
            n++;
            if (d.draw) draws++;
            if (d.won) {
                wins++;
                hpLostPctOnWin.add(Math.round(100f * d.hpLost / Math.max(1, d.maxHp)));
                swingsToKill.add(d.swings);
            }
            playerTurns += d.playerTurns;
            swings += d.swings;
            fullHits += d.fullHits;
            glancing += d.glancing;
            monsterActions += d.monsterActions;
            monsterHits += d.monsterHits;
            approachShots += d.approachShots;
            artUses += d.artUses;
        }
    }

    @Override
    public void create() {
        parseArgs();
        Gdx.app.setLogLevel(com.badlogic.gdx.Application.LOG_NONE); // the combat code logs every swing
        rng = new Random(seed);
        com.badlogic.gdx.math.MathUtils.random.setSeed(seed);

        assetManager = new AssetManager();
        itemDataManager = new ItemDataManager();
        itemDataManager.load();
        itemDataManager.loadWeapons();
        itemDataManager.loadArmor();
        monsterDataManager = new MonsterDataManager();
        monsterDataManager.load();
        eventManager = new QuietEvents();
        DiscoveryManager discovery = new DiscoveryManager(eventManager);
        discovery.initializeNewGame(java.util.Collections.emptyList());
        itemDataManager.setDiscoveryManager(discovery);
        soundManager = new HeadlessSoundManager();
        animationManager = new HeadlessAnimationManager();

        List<Monster.MonsterType> band = new ArrayList<>();
        for (Monster.MonsterType type : monsterDataManager.getLoadedTypes()) {
            MonsterTemplate t = monsterDataManager.getTemplate(type);
            if (t != null && t.baseLevel >= minLevel && t.baseLevel <= maxLevel) {
                band.add(type);
            }
        }
        band.sort(java.util.Comparator.comparing(Enum::name));
        if (guardian != null) band = java.util.Collections.singletonList(guardian); // the one Guardian (#302)

        try {
            run(band);
        } catch (Throwable e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        Gdx.app.exit();
    }

    private void run(List<Monster.MonsterType> band) throws IOException {
        if (report) {
            report();
            return;
        }
        Player probe = newPlayer();
        Item held = probe.getInventory().getRightHand();
        System.out.printf(Locale.ROOT, "DUEL SIMULATION  difficulty=%s playerLevel=%d hp=%d weapon=%s AC=%d toHit=+%d speed=%d%n",
                difficulty, probe.getLevel(), probe.getMaxHP(), held != null ? held.getType() : "none",
                probe.getArmorClass(), probe.getToHitBonus(), probe.getEffectiveSpeed());
        com.bpm.minotaur.gamedata.item.WeaponFamily family = probe.masteryFamilyOf(held, false);
        System.out.printf(Locale.ROOT, "mastery: %s %s (+%d to-hit)%n", family,
                probe.getStats().getMastery().rankOf(family), probe.masteryToHitBonus(held, false));
        System.out.printf(Locale.ROOT, "monster band: base level %d-%d (%d species) scaled to EDL %d, %d duels each, seed %d%n",
                minLevel, maxLevel, band.size(), edl, trials, seed);
        System.out.printf(Locale.ROOT, "approach=%s passive=%s arts=%s ranged=%s%n%n", approach, passive, arts,
                shoots(probe));

        System.out.printf(Locale.ROOT, "%-18s %3s %4s %6s %9s %9s %7s %7s %8s %7s %6s %6s%n",
                "monster", "lvl", "spd", "win%", "hpLost%", "swings", "hit%", "glnc%", "mHit%", "mAct/t", "m/p", "arts");
        Tally all = new Tally("BAND");
        List<Tally> rows = new ArrayList<>();
        for (Monster.MonsterType type : band) {
            Tally tally = new Tally(type.name());
            for (int i = 0; i < trials; i++) {
                Duel d = duel(newPlayer(), type);
                tally.add(d);
                all.add(d);
            }
            rows.add(tally);
            print(tally, monsterDataManager.getTemplate(type));
        }
        System.out.println();
        print(all, null);

        if (guard) {
            float winPct = pct(all.wins, all.n);
            String medianStr = median(all.swingsToKill);
            int swings = "-".equals(medianStr) ? 0 : Integer.parseInt(medianStr);
            System.out.printf(Locale.ROOT, "%n[BALANCE GUARD #202] Level-%d band balance: win rate = %.1f%% (target: 90.0-98.0%%), median swings = %d (target: 2-4)%n",
                    minLevel, winPct, swings);
            if (winPct < 90.0f || winPct > 98.0f) {
                throw new AssertionError(String.format(Locale.ROOT,
                        "Balance regression (#202): band win rate %.1f%% is outside the 90.0-98.0%% target window", winPct));
            }
            if (swings < 2 || swings > 4) {
                throw new AssertionError(String.format(Locale.ROOT,
                        "Balance regression (#202): median swings to kill %d is outside the 2-4 target window", swings));
            }
            System.out.println("[BALANCE GUARD #202] PASSED: Combat balance within target bounds.");
        }

        // Back to back: random band monsters, no healing between fights.
        List<Integer> survived = new ArrayList<>();
        for (int r = 0; r < chainRuns && !band.isEmpty(); r++) {
            Player p = newPlayer();
            int fights = 0;
            while (fights < 50) {
                Duel d = duel(p, band.get(rng.nextInt(band.size())));
                if (!d.won) break;
                fights++;
            }
            survived.add(fights);
        }
        System.out.printf(Locale.ROOT, "%nBACK TO BACK (no healing, %d runs): fights survived %s%n", chainRuns, spread(survived));

        if (csvPath != null && !csvPath.isEmpty()) {
            new java.io.File(csvPath).getAbsoluteFile().getParentFile().mkdirs();
            try (PrintWriter w = new PrintWriter(new FileWriter(csvPath))) {
                w.println("monster,duels,winPct,drawPct,hpLostPctMedian,swingsToKillMedian,fullHitPct,glancingPct,monsterHitPct,monsterActionsPerTurn");
                rows.add(all);
                for (Tally t : rows) {
                    w.printf(Locale.ROOT, "%s,%d,%.1f,%.1f,%s,%s,%.1f,%.1f,%.1f,%.2f%n", t.name, t.n,
                            pct(t.wins, t.n), pct(t.draws, t.n), median(t.hpLostPctOnWin), median(t.swingsToKill),
                            pct(t.fullHits, t.swings), pct(t.glancing, t.swings), pct(t.monsterHits, t.monsterActions),
                            t.monsterActions / (float) Math.max(1, t.playerTurns));
                }
            }
            System.out.println("CSV written to " + csvPath);
        }
    }

    /** A shooter meets a monster this many tiles off; it walks in while the player shoots. */
    private static final int RANGED_GAP = 4;
    /** Arrows, bolts or shot a shooter carries into each duel. */
    private static final int SHOOTER_AMMO = 40;

    /** The player stands at the room's west end; the monster stands east of it, along the row. */
    private static final int PLAYER_X = 1, ROW = 3;

    /** Whether {@code player} fights from range: a bow, crossbow, gun or thrown weapon in the right hand. */
    private static boolean shoots(Player player) {
        com.bpm.minotaur.gamedata.item.WeaponFamily family = player.masteryFamilyOf(player.getInventory().getRightHand(), false);
        return family == com.bpm.minotaur.gamedata.item.WeaponFamily.BOW
                || family == com.bpm.minotaur.gamedata.item.WeaponFamily.CROSSBOW
                || family == com.bpm.minotaur.gamedata.item.WeaponFamily.FIREARM
                || family == com.bpm.minotaur.gamedata.item.WeaponFamily.THROWN;
    }

    /** Tiles between the player and the monster along the row. */
    private static int gapOf(Player player, Monster monster) {
        return (int) monster.getPosition().x - (int) player.getPosition().x;
    }

    private Duel duel(Player player, Monster.MonsterType type) {
        Duel d = new Duel();
        int[][] walls = new int[8][8];
        Maze maze = new Maze(1, walls);
        player.setPosition(new com.badlogic.gdx.math.GridPoint2(PLAYER_X, ROW));
        player.setFacing(Direction.EAST);

        Monster monster = new Monster(type, PLAYER_X + 1, ROW, colorFor(type), monsterDataManager, assetManager);
        if (type == guardian) {
            // As the theme decorator places it: its theme's HP bonus, then cut to the stratum (#302).
            com.bpm.minotaur.generation.theme.ThemeDefinition theme = guardedTheme(type);
            monster.setMaxHP(monster.getMaxHP() + (theme != null ? theme.getChampionHpBonus() : 0));
            monster.setThemeChampion(true);
            com.bpm.minotaur.gamedata.monster.GuardianScaling.apply(monster, edl, monsterDataManager,
                    theme != null ? theme.getGuardianStrength() : 1f);
        } else {
            monster.scaleStats(edl); // MonsterFactory scales every spawn to its chunk's EDL
        }
        monster.setCurrentHP(monster.getMaxHP());
        boolean shooter = shoots(player);
        // A ranged monster is met at its preferred distance and shoots while the player closes; a
        // shooter meets every monster at range and shoots while it closes.
        int gap = approach && monster.hasRangedAttack() ? approachGap(monster) : 1;
        if (shooter) gap = Math.max(gap, RANGED_GAP);
        placeMonster(maze, player, monster, gap);
        Item thrown = shooter && !player.getInventory().getRightHand().isRanged() ? player.getInventory().getRightHand() : null;

        CombatManager combat = new CombatManager(player, maze, null, animationManager, eventManager, soundManager,
                itemDataManager, null, null, null, null);
        boolean realistic = approach || passive || arts || shooter;
        int procsAtStart = com.bpm.minotaur.telemetry.TelemetryManager.getInstance().totalArtUses();
        // A monster met beside the player is taken to have just stepped in: Hold the Line's moment.
        if (gap == 1 && (passive || arts)) combat.onMonsterClosed(monster);

        d.maxHp = player.getMaxHP();
        int hpAtStart = player.getCurrentHP();
        int playerSpeed = Math.max(1, player.getEffectiveSpeed());
        float energy = rng.nextFloat() * TURN_COST; // who strikes first is a matter of footing

        while (d.playerTurns < MAX_TURNS) {
            d.playerTurns++;
            if (thrown != null && player.getInventory().getRightHand() == null) {
                player.getInventory().setRightHand(restock(thrown)); // the next from the bundle
            }
            playerTurn(combat, player, monster, maze, d, shooter);
            if (monster.getCurrentHP() <= 0) {
                d.won = true;
                break;
            }
            if (realistic) combat.tickPlayerTimers(); // reloads, cooldowns, a readied Art's lapse

            energy += TURN_COST / playerSpeed * monster.getEffectiveSpeed();
            while (energy >= TURN_COST && player.getCurrentHP() > 0 && monster.getCurrentHP() > 0) {
                energy -= TURN_COST;
                // The game ticks these in MonsterAiManager; the duel calls the strikes directly. Only
                // in the realistic modes: honouring stuns the game already deals lifts the plain duel
                // that checkCombatBalance guards (95% to 98%), so the guard keeps its old model.
                if (realistic) {
                    if (monster.getStatusManager() != null) monster.getStatusManager().updateTurn();
                    if (monster.getBleedTurns() > 0) monster.applyBleedTick();
                    if (monster.getCurrentHP() <= 0) break;
                    monster.decrementStunImmunity();
                    if (monster.isStunned()) {
                        monster.decrementStun();
                        monster.endTurn();
                        continue;
                    }
                }
                int hp = player.getCurrentHP();
                if (gapOf(player, monster) > 1) {
                    monsterAtRange(combat, player, monster, maze, d);
                } else {
                    combat.monsterMeleeStrike(monster);
                    d.monsterActions++;
                    if (realistic) monster.endTurn(); // Daze, Pin, a hewn hide and a grip wear down
                }
                if (player.getCurrentHP() < hp) d.monsterHits++;
            }
            if (monster.getCurrentHP() <= 0) {
                d.won = true;
                break;
            }
            if (player.getCurrentHP() <= 0) break;
        }
        d.artUses = com.bpm.minotaur.telemetry.TelemetryManager.getInstance().totalArtUses() - procsAtStart;
        d.draw = !d.won && player.getCurrentHP() > 0;
        d.hpLost = Math.max(0, hpAtStart - player.getCurrentHP());
        return d;
    }

    /**
     * A monster's action while it is not beside the player: the game's own tactics (#255) -- shoot,
     * then spend the wait closing in. A Pinned or gripped monster stays put. Every step is a move
     * the game would see, so a drawn Held Arrow may answer it, and the last one is Hold the Line's.
     */
    private void monsterAtRange(CombatManager combat, Player player, Monster monster, Maze maze, Duel d) {
        int gap = gapOf(player, monster);
        com.bpm.minotaur.gamedata.monster.MonsterTactics.Move move =
                com.bpm.minotaur.gamedata.monster.MonsterTactics.decide(monster, gap, monster.hasRangedAttack());
        if (move == com.bpm.minotaur.gamedata.monster.MonsterTactics.Move.SHOOT) {
            if (combat.performMonsterRangedAttack(monster)) {
                d.monsterActions++;
                d.approachShots++;
                monster.markShot();
            }
        } else if (move == com.bpm.minotaur.gamedata.monster.MonsterTactics.Move.ADVANCE && !monster.isPinned()) {
            placeMonster(maze, player, monster, gap - 1);
            combat.onMonsterMoved(monster);
            if (gap - 1 == 1 && monster.getCurrentHP() > 0) combat.onMonsterClosed(monster);
        } // RETREAT: an elite holds its distance; this room has nowhere to back into
        monster.endTurn();
    }

    /** The player's turn: an Art if the policy uses one, else a shot, a step in, or a swing. */
    private void playerTurn(CombatManager combat, Player player, Monster monster, Maze maze, Duel d, boolean shooter) {
        if (arts && useArt(combat, player, monster, d, shooter)) return;
        if (shooter) {
            if (combat.getActiveReload() != null) return; // the gun is loading: this turn is the reload
            shoot(combat, player, monster, d);
        } else if (gapOf(player, monster) > 1) {
            placeMonster(maze, player, monster, gapOf(player, monster) - 1); // the player's turn is a step closer
        } else {
            strike(combat, monster, d);
        }
    }

    /**
     * The simple policy for the Active and capstone (#250): a drawn Held Arrow waits while the
     * monster is still coming; an aimed Marked Skull fires; otherwise the capstone when it is ready
     * and the foe is in reach, then the Active. Anything an Art cannot act on falls through to an
     * ordinary attack.
     *
     * @return whether an Art took the turn
     */
    private boolean useArt(CombatManager combat, Player player, Monster monster, Duel d, boolean shooter) {
        com.bpm.minotaur.gamedata.arts.CombatArt[] bar = com.bpm.minotaur.gamedata.arts.ArtBar.contents(player);
        com.bpm.minotaur.gamedata.arts.ArtCooldowns cooldowns = player.getArtCooldowns();
        int gap = gapOf(player, monster);
        if (player.getHeldArt() == com.bpm.minotaur.gamedata.arts.CombatArt.HELD_ARROW) {
            if (gap <= 1) return false; // it came in without crossing the line: just shoot
            player.keepHold(); // wait, drawn
            return true;
        }
        boolean inReach = shooter || gap <= 1;
        com.bpm.minotaur.gamedata.arts.CombatArt capstone = bar[1], active = bar[0];
        if (capstone != null && inReach && !cooldowns.isCooling(capstone) && used(combat.useArt(1), d)) return true;
        if (active == null || cooldowns.isCooling(active)) return false;
        if (active == com.bpm.minotaur.gamedata.arts.CombatArt.HELD_ARROW && gap <= 1) return false;
        boolean reachable = shooter || gap <= 1
                || (gap == 2 && (active == com.bpm.minotaur.gamedata.arts.CombatArt.REEL_IN
                        || active == com.bpm.minotaur.gamedata.arts.CombatArt.CLOSING_THRUST));
        return reachable && used(combat.useArt(0), d);
    }

    private static boolean used(com.bpm.minotaur.gamedata.arts.ActiveArts.Outcome outcome, Duel d) {
        if (outcome == com.bpm.minotaur.gamedata.arts.ActiveArts.Outcome.USED) d.swings++;
        return outcome.takesTurn();
    }

    /** One shot, or one throw, down the row through the game's own ranged path. */
    private void shoot(CombatManager combat, Player player, Monster monster, Duel d) {
        int before = monster.getCurrentHP();
        eventManager.glanced = false;
        combat.setCurrentState(CombatManager.CombatState.INACTIVE);
        Item weapon = player.getInventory().getRightHand();
        if (weapon == null) return; // nothing left to throw: the turn passes
        if (weapon.isRanged()) combat.playerAttackInstant();
        else combat.throwWeapon(weapon);
        d.swings++;
        countHit(combat, monster, before, d);
    }

    /** One player swing through the game's bump path; a full hit may fire the soul's Passive. */
    private void strike(CombatManager combat, Monster monster, Duel d) {
        int before = monster.getCurrentHP();
        eventManager.glanced = false;
        combat.setCurrentState(CombatManager.CombatState.INACTIVE);
        combat.playerMeleeStrike(monster);
        d.swings++;
        countHit(combat, monster, before, d);
    }

    private void countHit(CombatManager combat, Monster monster, int before, Duel d) {
        if (before - monster.getCurrentHP() <= 0) return;
        if (wasGlancing(combat)) d.glancing++;
        else d.fullHits++;
    }

    /** Another of a thrown weapon's bundle: a thrown dart is gone from the hand. */
    private Item restock(Item like) {
        return itemDataManager.createItem(like.getType(), 0, 0, ItemColor.GRAY, assetManager);
    }

    /** One row of the report: a Family and the weapon it is measured with (null with bare hands false: the Father's Sword). */
    private static final class ReportFamily {
        final String name;
        final Item.ItemType weapon;
        final boolean bareHands;

        ReportFamily(String name, Item.ItemType weapon, boolean bareHands) {
            this.name = name;
            this.weapon = weapon;
            this.bareHands = bareHands;
        }

        String weaponName() {
            return bareHands ? "bare hands" : weapon != null ? weapon.name() : "Father's Sword";
        }
    }

    /** One column of the report: a Mastery rank, and which of the Family's Arts the soul knows. */
    private static final class ReportArm {
        final String label;
        final Mastery.Rank rank;
        final boolean passive;
        final boolean arts;

        ReportArm(String label, Mastery.Rank rank, boolean passive, boolean arts) {
            this.label = label;
            this.rank = rank;
            this.passive = passive;
            this.arts = arts;
        }
    }

    /** The weapon each Family is measured with: the ones #239 used, and a staple for the rest. */
    private static final ReportFamily[] REPORT_FAMILIES = {
            new ReportFamily("Blade", null, false), new ReportFamily("Dagger", Item.ItemType.DAGGER, false),
            new ReportFamily("Axe", Item.ItemType.AXE, false), new ReportFamily("Blunt", Item.ItemType.MACE_FOOTMANS, false),
            new ReportFamily("Polearm", Item.ItemType.SPEAR, false), new ReportFamily("Unarmed", null, true),
            new ReportFamily("Bow", Item.ItemType.BOW, false), new ReportFamily("Crossbow", Item.ItemType.CROSSBOW, false),
            new ReportFamily("Firearm", Item.ItemType.MUSKET, false), new ReportFamily("Thrown", Item.ItemType.DART, false)};

    private static final ReportArm[] REPORT_ARMS = {
            new ReportArm("Untrained", Mastery.Rank.UNTRAINED, false, false),
            new ReportArm("Adept", Mastery.Rank.ADEPT, false, false),
            new ReportArm("Adept + Passive", Mastery.Rank.ADEPT, true, false),
            new ReportArm("Master", Mastery.Rank.MASTER, false, false),
            new ReportArm("Master + all Arts", Mastery.Rank.MASTER, false, true)};

    /**
     * The #250 report: win rate per Family at depths 1 and 3, without and with its Arts. Depth d is
     * a level-d body against the monsters of base level 1 to d, scaled to EDL d, with --approach.
     * Each Arts column sits beside the same rank without them, so the difference between the two
     * is the Arts alone; the Untrained column is the fresh body the 0.0.2 targets are written for.
     * Results are reported, not tuned: the numbers in the game are left as they are.
     *
     * <p>It sets the run's options for each cell in turn, and the run ends after it.
     */
    private void report() throws IOException {
        StringBuilder md = new StringBuilder();
        md.append(String.format(Locale.ROOT, "Combat Arts in the duel simulator (#250): %d duels per species per cell,"
                + " --approach, seed %d. Win rates.%n", trials, seed));
        approach = true;
        for (int depth : new int[]{1, 3}) {
            playerLevel = depth;
            edl = depth;
            List<Monster.MonsterType> band = bandOf(1, depth);
            StringBuilder head = new StringBuilder("| Family | Weapon |");
            StringBuilder rule = new StringBuilder("|---|---|");
            for (ReportArm arm : REPORT_ARMS) {
                if (!reportColumns.isEmpty() && !reportColumns.contains(arm.label.toLowerCase(Locale.ROOT))) continue;
                head.append(' ').append(arm.label).append(" |");
                rule.append("---|");
            }
            md.append(String.format(Locale.ROOT, "%n### Depth %d (level %d, EDL %d, monsters of base level 1-%d: %d species)%n%n%s%n%s%n",
                    depth, depth, depth, depth, band.size(), head, rule));
            System.out.println("Depth " + depth + " (" + band.size() + " species)");
            for (ReportFamily family : REPORT_FAMILIES) {
                if (!reportRows.isEmpty() && !reportRows.contains(family.name.toLowerCase(Locale.ROOT))) continue;
                unarmed = family.bareHands;
                weaponOverride = family.weapon;
                StringBuilder line = new StringBuilder("| " + family.name + " | " + family.weaponName() + " |");
                for (ReportArm arm : REPORT_ARMS) {
                    if (!reportColumns.isEmpty() && !reportColumns.contains(arm.label.toLowerCase(Locale.ROOT))) continue;
                    masteryRank = arm.rank;
                    passive = arm.passive;
                    arts = arm.arts;
                    line.append(String.format(Locale.ROOT, " %.1f%% |", pct(bandTally(band).wins, trials * band.size())));
                }
                md.append(line).append(System.lineSeparator());
                System.out.println(line);
            }
        }
        new java.io.File(reportPath).getAbsoluteFile().getParentFile().mkdirs();
        try (PrintWriter w = new PrintWriter(new FileWriter(reportPath))) {
            w.print(md);
        }
        System.out.println("Report written to " + reportPath);
    }

    /** The loaded species of base level {@code lo} to {@code hi}, by name. */
    private List<Monster.MonsterType> bandOf(int lo, int hi) {
        List<Monster.MonsterType> band = new ArrayList<>();
        for (Monster.MonsterType type : monsterDataManager.getLoadedTypes()) {
            MonsterTemplate t = monsterDataManager.getTemplate(type);
            if (t != null && t.baseLevel >= lo && t.baseLevel <= hi) band.add(type);
        }
        band.sort(java.util.Comparator.comparing(Enum::name));
        return band;
    }

    private Tally bandTally(List<Monster.MonsterType> band) {
        Tally all = new Tally("BAND");
        for (Monster.MonsterType type : band) {
            for (int i = 0; i < trials; i++) all.add(duel(newPlayer(), type));
        }
        return all;
    }

    /** How far out a ranged monster opens fire: its preferred distance, 2 to 4 tiles in this room. */
    private static int approachGap(Monster monster) {
        float preferred = monster.getRangedPreferredDistance();
        int gap = preferred > 0 ? Math.round(preferred) : Math.min(4, monster.getAttackRange());
        return Math.max(2, Math.min(4, gap));
    }

    /** The theme whose Guardian {@code type} is, or null if no theme names it. */
    private static com.bpm.minotaur.generation.theme.ThemeDefinition guardedTheme(Monster.MonsterType type) {
        for (com.bpm.minotaur.generation.theme.ChunkTheme theme : com.bpm.minotaur.generation.theme.ChunkTheme.values()) {
            com.bpm.minotaur.generation.theme.ThemeDefinition def =
                    com.bpm.minotaur.generation.theme.ThemeDataManager.getInstance().get(theme);
            if (def != null && def.getChampionType() == type) return def;
        }
        return null;
    }

    /** Sets the monster {@code gap} tiles east of wherever the player now stands. */
    private static void placeMonster(Maze maze, Player player, Monster monster, int gap) {
        int x = (int) player.getPosition().x + gap;
        maze.getMonsters().values().remove(monster);
        monster.getPosition().set(x + 0.5f, ROW + 0.5f);
        maze.getMonsters().put(new com.badlogic.gdx.math.GridPoint2(x, ROW), monster);
    }

    /** The game tells a glancing blow apart only by its log line ("Glancing blow on ..."). */
    private boolean wasGlancing(CombatManager combat) {
        boolean glanced = eventManager.glanced;
        eventManager.glanced = false;
        return glanced;
    }

    /**
     * Swallows events instead of logging them: the real manager appends every message to a file,
     * which would be millions of writes here. It only notes whether a glancing blow was reported.
     */
    private static final class QuietEvents extends GameEventManager {
        boolean glanced;

        @Override
        public void addEvent(com.bpm.minotaur.gamedata.GameEvent event) {
            if (event != null && event.message != null && event.message.startsWith("Glancing blow")) {
                glanced = true;
            }
        }
    }

    private MonsterColor colorFor(Monster.MonsterType type) {
        MonsterTemplate t = monsterDataManager.getTemplate(type);
        if (t != null && t.variants != null && !t.variants.isEmpty()) {
            MonsterVariant v = t.variants.get(0);
            if (v.color != null) return v.color;
        }
        return MonsterColor.WHITE;
    }

    private Player newPlayer() {
        Player p = new Player(3f, 3f, difficulty, itemDataManager, monsterDataManager, assetManager);
        while (p.getLevel() < playerLevel) {
            p.getStats().addExperience(p.getStats().getExperienceToNextLevel() - p.getStats().getExperience());
        }
        if (hpOverride > 0) {
            p.getStats().setMaxHP(hpOverride);
        }
        p.getStats().setCurrentHP(p.getMaxHP());
        if (acOverride > 0) {
            ItemTemplate chest = new ItemTemplate();
            chest.friendlyName = "Sim Armor";
            chest.isArmor = true;
            chest.armorClassBonus = Math.max(0, acOverride - p.getArmorClass());
            Item armor = Item.fromTemplate(Item.ItemType.HAUBERK, chest);
            p.getEquipment().setWornChest(armor);
        }
        if (unarmed) {
            Item sword = p.getInventory().getRightHand();
            if (sword != null) p.getInventory().pickupToBackpack(sword);
            p.getInventory().setRightHand(null);
        } else if (weaponOverride != null) {
            Item sword = p.getInventory().getRightHand();
            if (sword != null) p.getInventory().pickupToBackpack(sword);
            p.getInventory().setRightHand(itemDataManager.createItem(weaponOverride, 0, 0, ItemColor.GRAY, assetManager));
        }
        com.bpm.minotaur.gamedata.item.WeaponFamily family = p.masteryFamilyOf(p.getInventory().getRightHand(), false);
        p.getStats().getMastery().setRank(family, masteryRank);
        // The soul's Arts (#250): the Adept Passive alone, or all three of the Family's.
        for (Mastery.Rank rank : new Mastery.Rank[]{Mastery.Rank.ADEPT, Mastery.Rank.EXPERT, Mastery.Rank.MASTER}) {
            com.bpm.minotaur.gamedata.arts.CombatArt art = com.bpm.minotaur.gamedata.arts.CombatArt.of(family, rank);
            if (art != null && (arts || (passive && rank == Mastery.Rank.ADEPT))) p.learnArt(art, null);
        }
        // A shooter's quiver or pouch: enough for any one fight, and no more -- ammunition has
        // weight, and a burdened body is a slower one, which would measure the load, not the bow.
        if (shoots(p)) {
            p.getStats().setArrows(SHOOTER_AMMO);
            p.getStats().setShot(SHOOTER_AMMO);
        }
        return p;
    }

    private void print(Tally t, MonsterTemplate template) {
        System.out.printf(Locale.ROOT, "%-18s %3s %4s %5.1f%% %9s %9s %6.1f%% %6.1f%% %7.1f%% %7.2f %6.2f %6d%n",
                t.name,
                template != null ? String.valueOf(template.baseLevel) : "",
                template != null ? String.valueOf(template.moveSpeed) : "",
                pct(t.wins, t.n), median(t.hpLostPctOnWin), median(t.swingsToKill),
                pct(t.fullHits, t.swings), pct(t.glancing, t.swings), pct(t.monsterHits, t.monsterActions),
                t.monsterActions / (float) Math.max(1, t.playerTurns),
                // Monster attacks per player attack: live depth-1 telemetry reads 1.83 (#233).
                t.monsterActions / (float) Math.max(1, t.swings), t.artUses);
    }

    private static float pct(int a, int b) {
        return b == 0 ? 0f : 100f * a / b;
    }

    private static String median(List<Integer> v) {
        if (v.isEmpty()) return "-";
        List<Integer> s = new ArrayList<>(v);
        s.sort(null);
        return String.valueOf(s.get(s.size() / 2));
    }

    private static String spread(List<Integer> v) {
        if (v.isEmpty()) return "-";
        List<Integer> s = new ArrayList<>(v);
        s.sort(null);
        return String.format(Locale.ROOT, "median %d [p25 %d - p75 %d]", s.get(s.size() / 2), s.get(s.size() / 4),
                s.get(s.size() * 3 / 4));
    }

    private void parseArgs() {
        List<String> all = new ArrayList<>();
        if (args != null) {
            for (String a : args) all.addAll(Arrays.asList(a.trim().split("\\s+")));
        }
        for (String a : all) {
            if (a.isEmpty()) continue;
            String[] kv = a.replaceFirst("^--", "").split("=", 2);
            String k = kv[0];
            String v = kv.length > 1 ? kv[1] : "";
            switch (k) {
                case "trials": trials = Integer.parseInt(v); break;
                case "minLevel": minLevel = Integer.parseInt(v); break;
                case "maxLevel": maxLevel = Integer.parseInt(v); break;
                case "playerLevel": playerLevel = Integer.parseInt(v); break;
                case "edl": edl = Integer.parseInt(v); break;
                case "difficulty": difficulty = Difficulty.valueOf(v.toUpperCase(Locale.ROOT)); break;
                case "hp": hpOverride = Integer.parseInt(v); break;
                case "ac": acOverride = Integer.parseInt(v); break;
                case "weapon":
                    unarmed = "UNARMED".equalsIgnoreCase(v); // bare hands, for the Unarmed Family
                    weaponOverride = unarmed ? null : Item.ItemType.valueOf(v.toUpperCase(Locale.ROOT));
                    break;
                case "mastery": masteryRank = Mastery.Rank.valueOf(v.toUpperCase(Locale.ROOT)); break;
                case "approach": approach = v.isEmpty() || Boolean.parseBoolean(v); break;
                case "passive": passive = v.isEmpty() || Boolean.parseBoolean(v); break;
                case "arts": arts = v.isEmpty() || Boolean.parseBoolean(v); break;
                case "report": report = v.isEmpty() || Boolean.parseBoolean(v); break;
                case "reportOut": reportPath = v; break;
                case "columns":
                    for (String col : v.split(",")) if (!col.isEmpty()) reportColumns.add(col.trim().toLowerCase(Locale.ROOT));
                    break;
                case "rows":
                    for (String row : v.split(",")) if (!row.isEmpty()) reportRows.add(row.trim().toLowerCase(Locale.ROOT));
                    break;
                case "guardian": guardian = Monster.MonsterType.valueOf(v.toUpperCase(Locale.ROOT)); break;
                case "chain": chainRuns = Integer.parseInt(v); break;
                case "seed": seed = Long.parseLong(v); break;
                case "csv": csvPath = v; break;
                case "guard": guard = v.isEmpty() || Boolean.parseBoolean(v); break;
                default: System.out.println("Unknown option: " + a);
            }
        }
    }

    @Override public void resize(int width, int height) { }
    @Override public void render() { }
    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void dispose() { }
}
