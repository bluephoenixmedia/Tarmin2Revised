package com.bpm.minotaur;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableEntry;
import com.bpm.minotaur.managers.BalanceLogger;
import com.bpm.minotaur.managers.MusicManager;
import com.bpm.minotaur.managers.SettingsManager;
import com.bpm.minotaur.screens.LoadingScreen;
import com.bpm.minotaur.screens.MainMenuScreen;

/**
 * The main game class. Manages shared resources (SpriteBatch, Viewport, Managers)
 * and controls which screen is currently active.
 */
public class Tarmin2 extends Game {

    // --- Shared Resources (now private) ---
    private SpriteBatch batch;
    private ModelBatch modelBatch;
    private Viewport viewport;

    // --- Asset and Data Managers ---
    private AssetManager assetManager;
    private MonsterDataManager monsterDataManager;
    private ItemDataManager itemDataManager;
    private SpawnTableData spawnTableData;

    // Constants for a consistent virtual resolution.
    private static final float VIRTUAL_WIDTH = 1920f;
    private static final float VIRTUAL_HEIGHT = 1080f;

    @Override
    public void create() {
        BalanceLogger.getInstance().log("SESSION", "Engine Start. Initializing systems...");

        Bullet.init();
        batch = new SpriteBatch();

        // --- NEW: Initialize the ModelBatch ---
        modelBatch = new ModelBatch();

        OrthographicCamera camera = new OrthographicCamera();
        viewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera);

        // --- 1. Create Managers ---
        assetManager = new AssetManager();
        monsterDataManager = new MonsterDataManager();
        itemDataManager = new ItemDataManager();

        // --- 2. Load Monster Definitions (Synchronous) ---
        // This is fast and needs to happen before queueing assets
        monsterDataManager.load();
        itemDataManager.load();

        Gdx.app.log("Tarmin2", "Loading SpawnTableData...");
        Json json = new Json();
        json.setIgnoreUnknownFields(true);

        FileHandle spawnTableFile = Gdx.files.internal("data/spawntables.json");
        spawnTableData = json.fromJson(SpawnTableData.class, spawnTableFile);

        // CRITICAL FIX: Manually convert containerLoot JsonValues to SpawnTableEntry objects
        if (spawnTableData.containerLoot != null) {
            for (ObjectMap.Entry<String, Array<SpawnTableEntry>> entry : spawnTableData.containerLoot) {
                Array<SpawnTableEntry> fixedArray = new Array<>();

                if (entry.value != null) {
                    for (int i = 0; i < entry.value.size; i++) {
                        Object obj = entry.value.get(i);

                        if (obj instanceof com.badlogic.gdx.utils.JsonValue) {
                            // Convert JsonValue to SpawnTableEntry
                            com.badlogic.gdx.utils.JsonValue jv = (com.badlogic.gdx.utils.JsonValue) obj;
                            SpawnTableEntry ste = json.readValue(SpawnTableEntry.class, jv);
                            fixedArray.add(ste);
                        } else if (obj instanceof SpawnTableEntry) {
                            // Already correct type
                            fixedArray.add((SpawnTableEntry) obj);
                        }
                    }
                }
                entry.value = fixedArray;
            }
        }

        Gdx.app.log("Tarmin2", "SpawnTableData loaded.");

        // --- 3. Queue All Assets for Loading (Asynchronous) ---

        // Queue music
        MusicManager.getInstance().loadMusic("sounds/music/tarmin_ambient.ogg");
        MusicManager.getInstance().loadMusic("sounds/music/tarmin_fuxx.ogg");
        MusicManager.getInstance().loadMusic("sounds/music/tarmin_maze.mp3");

        // Queue monster textures and 3D models (via DataManagers)
        monsterDataManager.queueAssets(assetManager);
        itemDataManager.queueAssets(assetManager);

        // --- 4. Load Settings (Synchronous) ---
        SettingsManager.getInstance().load();

        // --- 5. Set the Initial Screen to the LoadingScreen ---
        this.setScreen(new LoadingScreen(this));
    }

    /**
     * This is called by the LoadingScreen when all assets are finished.
     */
    public void proceedToMainMenu() {
        // Now that assets are loaded, we can finish setting up managers
        MusicManager.getInstance().finishLoading();

        // And finally, go to the main menu
        this.setScreen(new MainMenuScreen(this));
    }

    @Override
    public void render() {
        super.render();
    }

    @Override
    public void dispose() {
        batch.dispose();

        if (modelBatch != null) {
            modelBatch.dispose();
        }

        assetManager.dispose();

        if (getScreen() != null) {
            getScreen().dispose();
        }

        MusicManager.getInstance().dispose();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        batch.setProjectionMatrix(viewport.getCamera().combined);
    }

    // --- Public Getters ---

    public SpriteBatch getBatch() {
        return batch;
    }

    public ModelBatch getModelBatch() {
        return modelBatch;
    }

    public Viewport getViewport() {
        return viewport;
    }

    public AssetManager getAssetManager() {
        return assetManager;
    }

    public MonsterDataManager getMonsterDataManager() {
        return monsterDataManager;
    }

    public ItemDataManager getItemDataManager() {
        return itemDataManager;
    }

    public SpawnTableData getSpawnTableData() {
        return spawnTableData;
    }
}
