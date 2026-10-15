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
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.monster.MonsterVariant;
import com.bpm.minotaur.gamedata.player.Player;
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
 * --difficulty=MEDIUM, --hp=N (override max HP), --weapon=ITEM_TYPE, --chain=N (back-to-back
 * runs), --seed=S, --csv=path.
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
    private Difficulty difficulty = Difficulty.MEDIUM;
    private int hpOverride = -1;
    private Item.ItemType weaponOverride;
    private int chainRuns = 500;
    private long seed = 42L;
    private String csvPath = "logs/duel-sim.csv";

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
        int hpLost;
        int maxHp;
    }

    private static final class Tally {
        final String name;
        int n, wins, draws, swingsOnWin, playerTurns, swings, fullHits, glancing, monsterActions, monsterHits;
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        Gdx.app.exit();
    }

    private void run(List<Monster.MonsterType> band) throws IOException {
        Player probe = newPlayer();
        System.out.printf(Locale.ROOT, "DUEL SIMULATION  difficulty=%s playerLevel=%d hp=%d weapon=%s AC=%d toHit=+%d speed=%d%n",
                difficulty, probe.getLevel(), probe.getStats().getMaxHP(),
                probe.getInventory().getRightHand() != null ? probe.getInventory().getRightHand().getType() : "none",
                probe.getArmorClass(), probe.getToHitBonus(), probe.getEffectiveSpeed());
        System.out.printf(Locale.ROOT, "monster band: base level %d-%d (%d species), %d duels each, seed %d%n%n",
                minLevel, maxLevel, band.size(), trials, seed);

        System.out.printf(Locale.ROOT, "%-18s %3s %4s %6s %9s %9s %7s %7s %8s %7s%n",
                "monster", "lvl", "spd", "win%", "hpLost%", "swings", "hit%", "glnc%", "mHit%", "mAct/t");
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

    private Duel duel(Player player, Monster.MonsterType type) {
        Duel d = new Duel();
        int[][] walls = new int[8][8];
        Maze maze = new Maze(1, walls);
        player.setPosition(new com.badlogic.gdx.math.GridPoint2(3, 3));
        player.setFacing(Direction.EAST);

        Monster monster = new Monster(type, 4, 3, colorFor(type), monsterDataManager, assetManager);
        maze.addMonster(monster);

        CombatManager combat = new CombatManager(player, maze, null, animationManager, eventManager, soundManager,
                itemDataManager, null, null, null, null);

        d.maxHp = player.getStats().getMaxHP();
        int hpAtStart = player.getCurrentHP();
        int playerSpeed = Math.max(1, player.getEffectiveSpeed());
        float energy = rng.nextFloat() * TURN_COST; // who strikes first is a matter of footing

        while (d.playerTurns < MAX_TURNS) {
            d.playerTurns++;
            int before = monster.getCurrentHP();
            eventManager.glanced = false;
            combat.setCurrentState(CombatManager.CombatState.INACTIVE);
            combat.playerMeleeStrike(monster);
            d.swings++;
            int dealt = before - monster.getCurrentHP();
            if (dealt > 0) {
                if (wasGlancing(combat)) d.glancing++; else d.fullHits++;
            }
            if (monster.getCurrentHP() <= 0) {
                d.won = true;
                break;
            }

            energy += TURN_COST / playerSpeed * monster.getEffectiveSpeed();
            while (energy >= TURN_COST && player.getCurrentHP() > 0) {
                energy -= TURN_COST;
                int hp = player.getCurrentHP();
                combat.monsterMeleeStrike(monster);
                d.monsterActions++;
                if (player.getCurrentHP() < hp) d.monsterHits++;
            }
            if (player.getCurrentHP() <= 0) break;
        }
        d.draw = !d.won && player.getCurrentHP() > 0;
        d.hpLost = Math.max(0, hpAtStart - player.getCurrentHP());
        return d;
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
        p.getStats().setCurrentHP(p.getStats().getMaxHP());
        if (weaponOverride != null) {
            p.getInventory().setRightHand(itemDataManager.createItem(weaponOverride, 0, 0, ItemColor.GRAY, assetManager));
        }
        return p;
    }

    private void print(Tally t, MonsterTemplate template) {
        System.out.printf(Locale.ROOT, "%-18s %3s %4s %5.1f%% %9s %9s %6.1f%% %6.1f%% %7.1f%% %7.2f%n",
                t.name,
                template != null ? String.valueOf(template.baseLevel) : "",
                template != null ? String.valueOf(template.moveSpeed) : "",
                pct(t.wins, t.n), median(t.hpLostPctOnWin), median(t.swingsToKill),
                pct(t.fullHits, t.swings), pct(t.glancing, t.swings), pct(t.monsterHits, t.monsterActions),
                t.monsterActions / (float) Math.max(1, t.playerTurns));
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
                case "difficulty": difficulty = Difficulty.valueOf(v.toUpperCase(Locale.ROOT)); break;
                case "hp": hpOverride = Integer.parseInt(v); break;
                case "weapon": weaponOverride = Item.ItemType.valueOf(v.toUpperCase(Locale.ROOT)); break;
                case "chain": chainRuns = Integer.parseInt(v); break;
                case "seed": seed = Long.parseLong(v); break;
                case "csv": csvPath = v; break;
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
