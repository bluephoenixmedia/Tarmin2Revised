package com.bpm.minotaur;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager; // Import this
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager; // Import this
import com.bpm.minotaur.gamedata.spawntables.LevelBudget;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableEntry;
import com.bpm.minotaur.managers.MusicManager;
import com.bpm.minotaur.managers.SettingsManager;
// Import your new loading screen and main menu
import com.bpm.minotaur.screens.LoadingScreen;
import com.bpm.minotaur.screens.MainMenuScreen;

/**
 * The main game class. Manages shared resources (SpriteBatch, Viewport, Managers)
 * and controls which screen is currently active.
 */
public class Tarmin2 extends Game {

    // --- Shared Resources (now private) ---
    private SpriteBatch batch;
    private Viewport viewport;

    // --- Asset and Data Managers ---
    private AssetManager assetManager;
    private MonsterDataManager monsterDataManager;
    private ItemDataManager itemDataManager; // <-- ADD THIS
    private SpawnTableData spawnTableData; // <-- ADD THIS

    // Constants for a consistent virtual resolution.
    private static final float VIRTUAL_WIDTH = 1920f;
    private static final float VIRTUAL_HEIGHT = 1080f;

    @Override
    public void create() {
        batch = new SpriteBatch();
        OrthographicCamera camera = new OrthographicCamera();
        viewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera);

        // --- 1. Create Managers ---
        assetManager = new AssetManager();
        monsterDataManager = new MonsterDataManager();
        itemDataManager = new ItemDataManager(); // <-- ADD THIS

        // --- 2. Load Monster Definitions (Synchronous) ---
        // This is fast and needs to happen before queueing assets
        monsterDataManager.load();
        itemDataManager.load(); // <-- ADD THIS

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

        // Queue monster textures (using the data we just loaded!)
        monsterDataManager.queueAssets(assetManager);
        itemDataManager.queueAssets(assetManager); // <-- ADD THIS

        // ... queue other assets here (fonts, UI skins, sound effects, etc.) ...
        // Example: assetManager.load("ui/uiskin.json", Skin.class);
        // Example: assetManager.load("fonts/myfont.fnt", BitmapFont.class);

        // --- 4. Load Settings (Synchronous) ---
        // Settings are small and usually needed right away
        SettingsManager.getInstance().load();

        // --- 5. Set the Initial Screen to the LoadingScreen ---
        // We pass "this" (the Game object) so the screen can access
        // managers and tell us when to switch screens.
        this.setScreen(new LoadingScreen(this));
    }

    /**
     * This is called by the LoadingScreen when all assets are finished.
     */
    public void proceedToMainMenu() {
        // Now that assets are loaded, we can finish setting up managers
        MusicManager.getInstance().finishLoading(); // Assign loaded music

        // And finally, go to the main menu
        this.setScreen(new MainMenuScreen(this));
    }

    @Override
    public void render() {
        // The Game class automatically calls the render method of the active screen.
        super.render();
    }

    @Override
    public void dispose() {
        // Dispose of shared resources when the game closes.
        batch.dispose();
        assetManager.dispose(); // Dispose the asset manager

        // Also dispose the current screen's resources.
        if (getScreen() != null) {
            getScreen().dispose();
        }

        MusicManager.getInstance().dispose();
    }

    @Override
    public void resize(int width, int height) {
        // Update the viewport when the window is resized.
        viewport.update(width, height, true);
        // Ensure the SpriteBatch uses the updated viewport projection.
        batch.setProjectionMatrix(viewport.getCamera().combined);
    }

    // --- Public Getters for Screens to Use ---

    public SpriteBatch getBatch() {
        return batch;
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
