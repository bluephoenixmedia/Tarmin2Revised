package com.bpm.minotaur.lwjgl3;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.*;
import com.bpm.minotaur.rendering.AnimationManager;
import com.bpm.minotaur.headless.HeadlessAnimationManager;
import com.bpm.minotaur.headless.HeadlessSoundManager;

import java.util.ArrayList;
import java.util.List;

public class UnlockSimulationLauncher implements ApplicationListener {

    private Player player;
    private Maze maze;
    private CombatManager combatManager;
    private ItemDataManager itemDataManager;
    private MonsterDataManager monsterDataManager;
    private SoundManager soundManager;
    private AnimationManager animationManager;
    private StochasticManager stochasticManager;
    private GameEventManager eventManager;
    private AssetManager assetManager;

    private int turns = 0;
    private int maxTurns = 10000; // Cap per run
    private boolean isRunning = true;

    private int currentRun = 0;
    private int totalRuns = 5;

    // Stats Accumulation
    private List<RunStats> runStats = new ArrayList<>();

    private static class RunStats {
        int steps;
        int kills;
        int doors;
        int maxLevel;
        int turns;

        @Override
        public String toString() {
            return String.format("Steps:%d Kills:%d Doors:%d Level:%d Turns:%d", steps, kills, doors, maxLevel, turns);
        }
    }

    private RunStats currentStats;

    public static void main(String[] arg) {
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        new HeadlessApplication(new UnlockSimulationLauncher(), config);
    }

    @Override
    public void create() {
        System.out.println("--- Starting Unlock Simulation (20 Runs) ---");

        // 1. Setup Data
        assetManager = new AssetManager();
        itemDataManager = new ItemDataManager();
        itemDataManager.load();
        itemDataManager.loadWeapons();
        itemDataManager.loadArmor();
        monsterDataManager = new MonsterDataManager();
        monsterDataManager.load();

        // 2. Setup UnlockManager in Sandbox Mode
        UnlockManager.getInstance().setItemDataManager(itemDataManager);
        UnlockManager.getInstance().setSaveFile("saves/simulation_profile.json");
        // Clear data for simulation start
        UnlockManager.getInstance().getData().totalSteps = 0;
        UnlockManager.getInstance().getData().totalDoorsOpened = 0;
        UnlockManager.getInstance().getData().monsterKills.clear();

        // 3. Fake Managers
        soundManager = new HeadlessSoundManager();
        animationManager = new HeadlessAnimationManager();
        eventManager = new GameEventManager();
        try {
            com.badlogic.gdx.physics.bullet.Bullet.init();
            stochasticManager = new StochasticManager();
        } catch (Exception e) {
        }

        // 4. Start first run
        startRun();
    }

    private void startRun() {
        currentRun++;
        turns = 0;
        currentStats = new RunStats();

        // Mock Player & World
        player = new Player(1.5f, 1.5f, Difficulty.MEDIUM, itemDataManager, monsterDataManager, assetManager);

        // We need to hook into Player to track stats or read from UnlockManager?
        // UnlockManager tracks CUMULATIVE global stats.
        // To get per-run stats, we should snapshot UnlockManager before and after, OR
        // track manually.
        // Let's track manually by hooking or just trusting the simulation loop counts?
        // Player actually calls UnlockManager.incrementStat().
        // So we can snapshot UnlockManager values.

        int[][] walls = new int[20][20];
        maze = new Maze(1, walls);

        combatManager = new CombatManager(player, maze, null, animationManager, eventManager, soundManager,
                itemDataManager, stochasticManager, null, null, null);

        System.out.println("Starting Run " + currentRun + "...");
    }

    @Override
    public void render() {
        if (!isRunning)
            return;

        // Run until completion or max turns
        turns++;
        currentStats.turns++;

        try {
            if (combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE) {
                handleCombat();
            } else {
                handleExploration();
            }
            combatManager.update(0.016f);
        } catch (Exception e) {
            e.printStackTrace();
            endRun(false); // Crashed
        }

        if (turns >= maxTurns) {
            endRun(true); // Timed out (survived)
        }
    }

    private void handleCombat() {
        CombatManager.CombatState state = combatManager.getCurrentState();
        if (state == CombatManager.CombatState.PLAYER_MENU || state == CombatManager.CombatState.PLAYER_TURN) {
            combatManager.playerAttackInstant();
        }
        if (state == CombatManager.CombatState.DEFEAT) {
            endRun(false); // Died
        }
        if (state == CombatManager.CombatState.VICTORY) {
            // Count kill?
            // UnlockManager already tracks it via recordKill called in CombatManager
            currentStats.kills++;
        }
    }

    private void handleExploration() {
        // Random Walk
        int dir = MathUtils.random(0, 3);
        Direction d = calculateDirection(dir);

        // Move
        Vector2 oldPos = new Vector2(player.getPosition());
        player.move(d, maze, eventManager, GameMode.CLASSIC);

        if (!player.getPosition().equals(oldPos)) {
            currentStats.steps++;
            // Emulate door?
            if (MathUtils.randomBoolean(0.05f)) { // 5% chance "door opened"
                currentStats.doors++;
                UnlockManager.getInstance().incrementStat("doors", 1);
            }
        }

        // Encounter
        if (MathUtils.randomBoolean(0.15f)) { // 15% encounter chance
            Monster m = new Monster(MonsterType.GOBLIN, player.getPosition().x, player.getPosition().y,
                    MonsterColor.GREEN, monsterDataManager, assetManager);
            combatManager.startCombat(m);
        }
    }

    private Direction calculateDirection(int dir) {
        if (dir == 0)
            return Direction.NORTH;
        if (dir == 1)
            return Direction.SOUTH;
        if (dir == 2)
            return Direction.EAST;
        return Direction.WEST;
    }

    private void endRun(boolean survived) {
        // Run Finished
        runStats.add(currentStats);
        System.out.println("Run " + currentRun + " Finished. " + currentStats);

        if (currentRun >= totalRuns) {
            reportAndExit();
        } else {
            startRun();
        }
    }

    private void reportAndExit() {
        System.out.println("\n=== UNLOCK SIMULATION REPORT (" + totalRuns + " Runs) ===");

        float avgSteps = 0;
        float avgKills = 0;
        float avgDoors = 0;
        float avgTurns = 0;

        for (RunStats s : runStats) {
            avgSteps += s.steps;
            avgKills += s.kills;
            avgDoors += s.doors;
            avgTurns += s.turns;
        }

        avgSteps /= totalRuns;
        avgKills /= totalRuns;
        avgDoors /= totalRuns;
        avgTurns /= totalRuns;

        System.out.println("[REPORT] Avg Turns/Run: " + avgTurns);
        System.out.println("[REPORT] Avg Steps/Run: " + avgSteps);
        System.out.println("[REPORT] Avg Kills/Run: " + avgKills);
        System.out.println("[REPORT] Avg Doors/Run: " + avgDoors);
        System.out.println("=============================================");

        isRunning = false;
        Gdx.app.exit();
    }

    @Override
    public void resize(int w, int h) {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
    }
}
