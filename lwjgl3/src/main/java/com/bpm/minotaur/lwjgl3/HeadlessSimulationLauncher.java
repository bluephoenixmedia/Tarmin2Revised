package com.bpm.minotaur.lwjgl3;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager; // NEW
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.*;
import com.bpm.minotaur.rendering.AnimationManager;
import com.bpm.minotaur.headless.HeadlessAnimationManager;
import com.bpm.minotaur.headless.HeadlessSoundManager;

public class HeadlessSimulationLauncher implements ApplicationListener {

    private Player player;
    private Maze maze;
    private CombatManager combatManager;
    private ItemDataManager itemDataManager;
    private MonsterDataManager monsterDataManager; // NEW
    private SoundManager soundManager;
    private AnimationManager animationManager;
    private StochasticManager stochasticManager;
    private GameEventManager eventManager;
    private DoomManager doomManager;
    private AssetManager assetManager;

    private int turns = 0;
    private int maxTurns = 2000;
    private boolean isRunning = true;

    private int encounters = 0;
    private int victories = 0;
    private int deaths = 0;

    public static void main(String[] arg) {
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        new HeadlessApplication(new HeadlessSimulationLauncher(), config);
    }

    @Override
    public void create() {
        System.out.println("--- Starting Headless Simulation ---");

        // 1. Initialize AssetManager (needed for constructors, even if we don't load
        // graphics)
        assetManager = new AssetManager();

        // 2. Initialize Data Managers
        itemDataManager = new ItemDataManager();
        itemDataManager.load();
        itemDataManager.loadWeapons();
        itemDataManager.loadArmor();
        System.out.println("Item Templates Loaded.");

        monsterDataManager = new MonsterDataManager();
        monsterDataManager.load();
        System.out.println("Monster Templates Loaded.");

        // QUEUE ASSETS from Managers (if they weren't already queued by load())
        // ItemDataManager.load() usually parses JSON.
        // We need to ensure referenced assets are queue/loaded.
        // Assuming ItemDataManager queues them? If not, we might be in trouble if Item
        // constructor requests them immediately.
        // But regardless, we try to finish loading whatever is queued.
        assetManager.finishLoading();
        System.out.println("Assets finished loading (Headless).");

        // 3. Null/Stub Managers
        soundManager = new HeadlessSoundManager();
        animationManager = new HeadlessAnimationManager();
        eventManager = new GameEventManager();

        // Physics
        try {
            com.badlogic.gdx.physics.bullet.Bullet.init();
            stochasticManager = new StochasticManager(); // No args
        } catch (Exception e) {
            System.out.println(
                    "Bullet Physics Init Failed (Expected if natives missing in headless test env): " + e.getMessage());
        }

        // Doom Manager
        doomManager = DoomManager.getInstance();

        // 4. Create World/Player
        spawnPlayer();

        // Generate a dummy maze (Empty Room)
        int[][] walls = new int[20][20]; // 0 = Empty
        maze = new Maze(1, walls);

        // 5. Setup Combat
        // Passing 'null' for Tarmin2 (Game) instance.
        combatManager = new CombatManager(player, maze, null, animationManager, eventManager, soundManager,
                itemDataManager, stochasticManager);

        System.out.println("Simulation Initialized.");
    }

    private void spawnPlayer() {
        player = new Player(1.5f, 1.5f, Difficulty.MEDIUM, itemDataManager, monsterDataManager, assetManager);
        // player.setName("SimBot"); // Not defined in Player.java
    }

    @Override
    public void render() {
        if (!isRunning)
            return;

        if (turns >= maxTurns) {
            System.out.println("Max turns reached.");
            isRunning = false;
            report();
            Gdx.app.exit();
            return;
        }

        turns++;

        try {
            // --- Simulation Logic ---
            if (combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE) {
                handleCombat();
            } else {
                handleExploration();
            }

            // Tick managers
            combatManager.update(0.016f);

        } catch (Exception e) {
            System.out.println("Simulation Logic Error: " + e.getMessage());
            e.printStackTrace();
            isRunning = false;
            Gdx.app.exit();
        }
    }

    private void handleCombat() {
        CombatManager.CombatState state = combatManager.getCurrentState();

        if (state == CombatManager.CombatState.PLAYER_MENU ||
                state == CombatManager.CombatState.PLAYER_TURN) {

            // Bot Logic: Instant Attack
            combatManager.playerAttackInstant();
        }

        if (state == CombatManager.CombatState.VICTORY) {
            victories++;
            encounters++;
            System.out.println("Victory! Total: " + victories);
        }

        if (state == CombatManager.CombatState.DEFEAT) {
            deaths++;
            encounters++;
            System.out.println("DEATH! Total: " + deaths + " | Doom Count: " + doomManager.getDeathCount());

            // Respawn Player
            spawnPlayer();

            // Reset Combat Manager to clear state
            combatManager = new CombatManager(player, maze, null, animationManager, eventManager, soundManager,
                    itemDataManager, stochasticManager);
        }
    }

    private void handleExploration() {
        // Simple random walk
        int dir = MathUtils.random(0, 3);
        Direction d = Direction.NORTH;
        if (dir == 0)
            d = Direction.NORTH;
        if (dir == 1)
            d = Direction.SOUTH;
        if (dir == 2)
            d = Direction.EAST;
        if (dir == 3)
            d = Direction.WEST;

        // Assumes Player.move() is public now
        player.move(d, maze, eventManager, GameMode.CLASSIC);

        // Force encounter logic
        if (MathUtils.randomBoolean(0.1f)) { // 10% chance per turn
            System.out.println("Encounter triggered at turn " + turns);

            // Spawn a Goblin
            // Note: x, y are floats in Constructor
            Monster m = new Monster(MonsterType.GOBLIN, player.getPosition().x, player.getPosition().y,
                    MonsterColor.GREEN, monsterDataManager, assetManager);

            combatManager.startCombat(m);
        }
    }

    private void report() {
        System.out.println("=== Simulation Report ===");
        System.out.println("Turns: " + turns);
        System.out.println("Encounters: " + encounters);
        System.out.println("Victories: " + victories);
        System.out.println("Deaths: " + deaths);
        System.out.println("End Doom Count: " + doomManager.getDeathCount());
        System.out.println("=========================");
    }

    @Override
    public void resize(int width, int height) {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        System.out.println("Disposing Simulation.");
        if (stochasticManager != null)
            stochasticManager.dispose();
        if (assetManager != null)
            assetManager.dispose();
    }
}
