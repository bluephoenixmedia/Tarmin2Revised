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
 * player closes, #239), --passive (model the wielded Family's Adept Passive, #239), --chain=N
 * (back-to-back runs), --seed=S, --csv=path.
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
    /** Model the wielded Family's Adept Passive (#227); the game does not have the Arts yet. */
    private boolean passive = false;
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
        int passiveProcs;
        int hpLost;
        int maxHp;
    }

    private static final class Tally {
        final String name;
        int n, wins, draws, swingsOnWin, playerTurns, swings, fullHits, glancing, monsterActions, monsterHits,
                approachShots, passiveProcs;
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
            passiveProcs += d.passiveProcs;
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
        System.out.printf(Locale.ROOT, "approach=%s passive=%s%n%n", approach, passive
                ? AdeptPassive.of(family) + " x" + Mastery.procMultiplier(Mastery.Rank.ADEPT, masteryRank) : "off");

        System.out.printf(Locale.ROOT, "%-18s %3s %4s %6s %9s %9s %7s %7s %8s %7s %6s %6s%n",
                "monster", "lvl", "spd", "win%", "hpLost%", "swings", "hit%", "glnc%", "mHit%", "mAct/t", "m/p", "procs");
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

    /**
     * The Adept Passive of each melee Weapon Family, as #227 designed it. The game has no Combat
     * Arts yet, so the simulator models them here as a measuring instrument: the chances are the
     * catalogue's starting values, and each effect is approximated where the game lacks it.
     */
    enum AdeptPassive {
        /** Blade, Answering Steel: a monster's missed melee attack draws a free counter-cut, 30%. */
        ANSWERING_STEEL(0.30f),
        /** Dagger, Cut the Sinew: a full hit makes the target bleed and be Slowed for 2 turns, 25%. */
        CUT_THE_SINEW(0.25f),
        /** Axe, Wide Chop: strikes the other adjacent hostiles; a duel has none, so it never fires. */
        WIDE_CHOP(0.30f),
        /** Blunt, Ringing Blow: a full hit Dazes, and a Dazed target is Stunned instead, 25%. */
        RINGING_BLOW(0.25f),
        /** Polearm, Hold the Line: the first time the monster is adjacent, a free strike comes first. */
        HOLD_THE_LINE(1f),
        /** Unarmed, Quick Knuckles: a full hit adds a free half-damage strike, 30%. */
        QUICK_KNUCKLES(0.30f),
        /** Ranged Families and anything else: the duel swings in melee, so nothing is modelled. */
        NONE(0f);

        final float baseChance;

        AdeptPassive(float baseChance) {
            this.baseChance = baseChance;
        }

        static AdeptPassive of(com.bpm.minotaur.gamedata.item.WeaponFamily family) {
            switch (family) {
                case BLADE: return ANSWERING_STEEL;
                case DAGGER: return CUT_THE_SINEW;
                case AXE: return WIDE_CHOP;
                case BLUNT: return RINGING_BLOW;
                case POLEARM: return HOLD_THE_LINE;
                case UNARMED: return QUICK_KNUCKLES;
                default: return NONE;
            }
        }
    }

    /**
     * Dazed (#227) is -2 to-hit, ten points on a d20, which the game has no hook for yet. Monsters
     * hit about 62% of the time, so the Daze spoils 0.10 / 0.62 of the blows that would have landed.
     */
    private static final float DAZED_SPOILS_OF_HITS = 0.10f / 0.62f;
    private static final int DAZED_ACTIONS = 2;

    /** The player's tile; the monster stands {@code gap} tiles east of it. */
    private static final int PLAYER_X = 3, ROW = 3;

    private Duel duel(Player player, Monster.MonsterType type) {
        Duel d = new Duel();
        int[][] walls = new int[8][8];
        Maze maze = new Maze(1, walls);
        player.setPosition(new com.badlogic.gdx.math.GridPoint2(PLAYER_X, ROW));
        player.setFacing(Direction.EAST);

        Monster monster = new Monster(type, PLAYER_X + 1, ROW, colorFor(type), monsterDataManager, assetManager);
        monster.scaleStats(edl); // MonsterFactory scales every spawn to its chunk's EDL
        monster.setCurrentHP(monster.getMaxHP());
        // A ranged monster is met at its preferred distance and shoots while the player closes.
        int gap = approach && monster.hasRangedAttack() ? approachGap(monster) : 1;
        placeMonster(maze, monster, gap);

        CombatManager combat = new CombatManager(player, maze, null, animationManager, eventManager, soundManager,
                itemDataManager, null, null, null, null);

        AdeptPassive art = passive
                ? AdeptPassive.of(player.masteryFamilyOf(player.getInventory().getRightHand(), false))
                : AdeptPassive.NONE;
        float chance = art.baseChance * Mastery.procMultiplier(Mastery.Rank.ADEPT, masteryRank);
        int[] dazed = { 0 };
        boolean lineHeld = false;

        d.maxHp = player.getMaxHP();
        int hpAtStart = player.getCurrentHP();
        int playerSpeed = Math.max(1, player.getEffectiveSpeed());
        float energy = rng.nextFloat() * TURN_COST; // who strikes first is a matter of footing

        while (d.playerTurns < MAX_TURNS) {
            d.playerTurns++;
            if (gap > 1) {
                gap--; // the player's turn is a step closer
                placeMonster(maze, monster, gap);
            } else {
                if (art == AdeptPassive.HOLD_THE_LINE && !lineHeld) {
                    lineHeld = true;
                    d.passiveProcs++;
                    strike(combat, monster, d, AdeptPassive.NONE, 0f, dazed); // the free strike comes first
                }
                if (monster.getCurrentHP() > 0) strike(combat, monster, d, art, chance, dazed);
                if (monster.getCurrentHP() <= 0) {
                    d.won = true;
                    break;
                }
            }

            energy += TURN_COST / playerSpeed * monster.getEffectiveSpeed();
            while (energy >= TURN_COST && player.getCurrentHP() > 0 && monster.getCurrentHP() > 0) {
                energy -= TURN_COST;
                // The game ticks these in MonsterAiManager; the duel calls the strikes directly. Only
                // in the realistic modes: honouring stuns the game already deals lifts the plain duel
                // that checkCombatBalance guards (95% to 98%), so the guard keeps its old model.
                if (approach || passive) {
                    if (monster.getStatusManager() != null) monster.getStatusManager().updateTurn();
                    if (monster.getBleedTurns() > 0) monster.applyBleedTick();
                    if (monster.getCurrentHP() <= 0) break;
                    monster.decrementStunImmunity();
                    if (monster.isStunned()) {
                        monster.decrementStun();
                        continue;
                    }
                }
                int hp = player.getCurrentHP();
                if (gap > 1) {
                    if (combat.performMonsterRangedAttack(monster)) {
                        d.monsterActions++;
                        d.approachShots++;
                    }
                } else {
                    combat.monsterMeleeStrike(monster);
                    d.monsterActions++;
                    if (dazed[0] > 0) {
                        dazed[0]--;
                        if (player.getCurrentHP() < hp && rng.nextFloat() < DAZED_SPOILS_OF_HITS) {
                            player.getStats().setCurrentHP(hp); // the Daze spoils a blow that would have landed
                        }
                    }
                }
                if (player.getCurrentHP() < hp) {
                    d.monsterHits++;
                } else if (gap <= 1 && art == AdeptPassive.ANSWERING_STEEL && rng.nextFloat() < chance) {
                    d.passiveProcs++;
                    strike(combat, monster, d, AdeptPassive.NONE, 0f, dazed); // the counter-cut
                }
            }
            if (monster.getCurrentHP() <= 0) {
                d.won = true;
                break;
            }
            if (player.getCurrentHP() <= 0) break;
        }
        d.draw = !d.won && player.getCurrentHP() > 0;
        d.hpLost = Math.max(0, hpAtStart - player.getCurrentHP());
        return d;
    }

    /** One player strike; a full hit may fire {@code art}. Free strikes pass NONE, so procs never chain. */
    private void strike(CombatManager combat, Monster monster, Duel d, AdeptPassive art, float chance, int[] dazed) {
        int before = monster.getCurrentHP();
        eventManager.glanced = false;
        combat.setCurrentState(CombatManager.CombatState.INACTIVE);
        combat.playerMeleeStrike(monster);
        d.swings++;
        int dealt = before - monster.getCurrentHP();
        if (dealt <= 0) return;
        if (wasGlancing(combat)) {
            d.glancing++;
            return;
        }
        d.fullHits++;
        if (monster.getCurrentHP() <= 0 || art == AdeptPassive.NONE || rng.nextFloat() >= chance) return;
        switch (art) {
            case CUT_THE_SINEW:
                d.passiveProcs++;
                monster.applyBleed(2, 1);
                monster.getStatusManager().addEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.SLOWED, 2, 1, false);
                break;
            case RINGING_BLOW:
                d.passiveProcs++;
                if (dazed[0] > 0 && monster.applyStun(1)) dazed[0] = 0;
                else dazed[0] = DAZED_ACTIONS;
                break;
            case QUICK_KNUCKLES:
                // A second knuckle behind the first, at half its damage. Applied straight, not swung,
                // so a full-damage free strike can't overkill and then be refunded.
                d.passiveProcs++;
                monster.setCurrentHP(Math.max(0, monster.getCurrentHP() - Math.max(1, dealt / 2)));
                break;
            default:
                break;
        }
    }

    /** How far out a ranged monster opens fire: its preferred distance, 2 to 4 tiles in this room. */
    private static int approachGap(Monster monster) {
        float preferred = monster.getRangedPreferredDistance();
        int gap = preferred > 0 ? Math.round(preferred) : Math.min(4, monster.getAttackRange());
        return Math.max(2, Math.min(4, gap));
    }

    private static void placeMonster(Maze maze, Monster monster, int gap) {
        maze.getMonsters().values().remove(monster);
        monster.getPosition().set(PLAYER_X + gap + 0.5f, ROW + 0.5f);
        maze.getMonsters().put(new com.badlogic.gdx.math.GridPoint2(PLAYER_X + gap, ROW), monster);
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
        p.getStats().getMastery().setRank(p.masteryFamilyOf(p.getInventory().getRightHand(), false), masteryRank);
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
                t.monsterActions / (float) Math.max(1, t.swings), t.passiveProcs);
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
