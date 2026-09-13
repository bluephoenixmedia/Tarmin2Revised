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
import com.bpm.minotaur.gamedata.item.Item;
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
    private DiscoveryManager discoveryManager;

    private int turns = 0;
    private int maxTurns = 2000;
    private boolean isRunning = true;

    private int encounters = 0;
    private int victories = 0;
    private int deaths = 0;

    // Minimal bot upgrades: escalating challenge, self-preservation, and looting.
    private int consecutiveVictories = 0;
    private static final int VICTORIES_PER_TIER = 3;
    private static final MonsterType[] DIFFICULTY_TIERS = {
            MonsterType.GOBLIN, MonsterType.HOBGOBLIN, MonsterType.SKELETON,
            MonsterType.GHOUL, MonsterType.ORC, MonsterType.TROLL, MonsterType.OGRE
    };
    private static final float LOW_HP_RETREAT_THRESHOLD = 0.3f;

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

        eventManager = new GameEventManager();

        // Discovery: without this, potions never get a real effect assigned at
        // creation time (ItemDataManager silently skips it when null), so the bot's
        // "quaff to heal" logic would otherwise always be a no-op.
        discoveryManager = new DiscoveryManager(eventManager);
        discoveryManager.initializeNewGame(java.util.List.of(
                com.bpm.minotaur.gamedata.item.Item.ItemType.POTION_BLUE,
                com.bpm.minotaur.gamedata.item.Item.ItemType.POTION_PINK,
                com.bpm.minotaur.gamedata.item.Item.ItemType.POTION_GREEN,
                com.bpm.minotaur.gamedata.item.Item.ItemType.POTION_GOLD));
        itemDataManager.setDiscoveryManager(discoveryManager);

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

        // Telemetry: one exported run per simulation session (spans every simulated
        // life, not just the first), so headless runs are directly comparable to the
        // logs/telemetry/*.json produced by real playtests.
        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().startNewRun();

        // 4. Create World/Player
        spawnPlayer();

        // Generate a dummy maze (Empty Room)
        int[][] walls = new int[20][20]; // 0 = Empty
        maze = new Maze(1, walls);

        // 5. Setup Combat
        // Passing 'null' for Tarmin2 (Game) instance.
        combatManager = new CombatManager(player, maze, null, animationManager, eventManager, soundManager,
                itemDataManager, stochasticManager, null, null, null);

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
        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().setTurnsLived(turns);

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

            // Bot Logic: self-preservation first -- quaff a potion when badly hurt
            // instead of trading blows, otherwise attack.
            boolean isLowHp = player.getCurrentHP() < player.getStats().getMaxHP() * LOW_HP_RETREAT_THRESHOLD;
            if (isLowHp && quaffAnyPotion()) {
                // Spent this turn healing instead of attacking.
            } else {
                combatManager.playerAttackInstant();
            }
        }

        // Re-fetch: the attack above can resolve the fight synchronously (e.g. a
        // killing blow sets VICTORY inside playerAttackInstant itself), so the
        // pre-attack snapshot in `state` is stale by this point -- checking it here
        // instead of the fresh state meant victories/deaths were never counted.
        state = combatManager.getCurrentState();

        if (state == CombatManager.CombatState.VICTORY) {
            victories++;
            encounters++;
            consecutiveVictories++;
            System.out.println("Victory! Total: " + victories);
        }

        if (state == CombatManager.CombatState.DEFEAT) {
            deaths++;
            encounters++;
            consecutiveVictories = 0;
            System.out.println("DEATH! Total: " + deaths + " | Doom Count: " + doomManager.getDeathCount());

            // Respawn Player
            spawnPlayer();

            // Reset Combat Manager to clear state
            combatManager = new CombatManager(player, maze, null, animationManager, eventManager, soundManager,
                    itemDataManager, stochasticManager, null, null, null);
        }
    }

    /** Quaffs the first potion found in the backpack, if any. @return true if one was found and used. */
    private boolean quaffAnyPotion() {
        for (com.bpm.minotaur.gamedata.item.Item item : player.getInventory().getMainInventory()) {
            if (item != null && item.isPotion()) {
                player.quaff(item, discoveryManager, eventManager);
                return true;
            }
        }
        return false;
    }

    /** Proxy for "descending": the bot fights progressively tougher monster types
     *  after strings of consecutive victories, standing in for real strata depth
     *  since this harness runs a single static maze rather than the full world. */
    private MonsterType currentDifficultyTier() {
        int tierIndex = Math.min(DIFFICULTY_TIERS.length - 1, consecutiveVictories / VICTORIES_PER_TIER);
        return DIFFICULTY_TIERS[tierIndex];
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

        // Loot: a small chance per turn of stumbling on a potion or weapon, picked
        // up immediately (and equipped, if it's a clear weapon upgrade).
        if (MathUtils.randomBoolean(0.05f)) {
            pickUpRandomLoot();
        }

        // Force encounter logic -- monster toughness escalates with consecutive
        // victories, standing in for descending strata depth.
        if (MathUtils.randomBoolean(0.1f)) { // 10% chance per turn
            MonsterType tier = currentDifficultyTier();
            System.out.println("Encounter triggered at turn " + turns + " (" + tier + ")");

            Monster m = new Monster(tier, player.getPosition().x, player.getPosition().y,
                    MonsterColor.GREEN, monsterDataManager, assetManager);

            combatManager.startCombat(m);
        }
    }

    private static final Item.ItemType[] LOOTABLE_POTIONS = {
            Item.ItemType.POTION_BLUE, Item.ItemType.POTION_PINK,
            Item.ItemType.POTION_GREEN, Item.ItemType.POTION_GOLD
    };
    private static final Item.ItemType[] LOOTABLE_WEAPONS = {
            Item.ItemType.RUSTY_SWORD, Item.ItemType.AXE_BATTLE, Item.ItemType.MACE_GREAT
    };

    /** Spawns a random potion or weapon "underfoot" and immediately picks it up
     *  (equipping the weapon if it out-damages whatever is currently wielded). */
    private void pickUpRandomLoot() {
        boolean wantsWeapon = MathUtils.randomBoolean(0.4f);
        Item.ItemType[] pool = wantsWeapon ? LOOTABLE_WEAPONS : LOOTABLE_POTIONS;
        Item.ItemType type = pool[MathUtils.random(pool.length - 1)];
        Item item = itemDataManager.createItem(type, (int) player.getPosition().x, (int) player.getPosition().y,
                com.bpm.minotaur.gamedata.item.ItemColor.TAN, assetManager);
        if (item == null) {
            return;
        }

        if (item.isWeapon()) {
            Item current = player.getInventory().getRightHand();
            if (current == null || averageDice(item.getDamageDice()) > averageDice(current.getDamageDice())) {
                player.getInventory().setRightHand(item);
            }
        } else {
            player.pickupItem(item);
        }
    }

    /** Rough average value of a dice string like "2d6", used only to compare weapon upgrades. */
    private float averageDice(String dice) {
        if (dice == null || dice.isEmpty()) {
            return 0f;
        }
        try {
            int total = 0;
            for (int i = 0; i < 5; i++) {
                total += com.bpm.minotaur.utils.DiceRoller.roll(dice);
            }
            return total / 5f;
        } catch (Exception e) {
            return 0f;
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

        // Export the whole session (every simulated life, not just the last one) as
        // one telemetry run, directly comparable to logs/telemetry/*.json from real
        // playtests.
        String cause = String.format("Headless simulation completed: %d turns, %d victories, %d deaths",
                turns, victories, deaths);
        String json = com.bpm.minotaur.telemetry.TelemetryManager.getInstance().exportRun(cause);
        System.out.println("Telemetry exported:\n" + json);
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
