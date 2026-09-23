package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.ShopkeeperNpc;
import com.bpm.minotaur.gamedata.bones.BonesData;
import com.bpm.minotaur.gamedata.encounters.Encounter;
import com.bpm.minotaur.gamedata.encounters.EncounterChoice;
import com.bpm.minotaur.gamedata.injury.BodyPart;
import com.bpm.minotaur.gamedata.injury.InjuryType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ShelterChest;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.DivinityManager;
import com.bpm.minotaur.screens.firstaid.FirstAidModal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Automated test harness screen that sequentially constructs, populates, and captures
 * pristine 1920x1080 screenshots of all 27 interactive screens, modals, and HUD components
 * in Tarmin 2.
 */
public class UXScreenCaptureScreen extends BaseScreen {

    private final String stageName;
    private FileHandle outputDirLocal;
    private FileHandle outputDirArtifact;

    private GameScreen sharedGameScreen;
    private Player sharedPlayer;
    private Maze sharedMaze;

    private final List<CaptureTask> tasks = new ArrayList<>();
    private int currentTaskIndex = 0;
    private int warmupCounter = 0;

    private enum StepState {
        PREPARE,
        WARMUP,
        CAPTURE
    }

    private StepState stepState = StepState.PREPARE;
    private Screen activeSubScreen = null;
    private Runnable activeCleanup = null;

    private interface CaptureSetup {
        void execute() throws Exception;
    }

    private static class CaptureTask {
        final String id;
        final String displayName;
        final CaptureSetup setup;

        CaptureTask(String id, String displayName, CaptureSetup setup) {
            this.id = id;
            this.displayName = displayName;
            this.setup = setup;
        }
    }

    public UXScreenCaptureScreen(Tarmin2 game, String stageName) {
        super(game);
        this.stageName = (stageName != null && !stageName.trim().isEmpty()) ? stageName : "baseline";
    }

    @Override
    public void show() {
        Gdx.app.log("UXCapture", "=== Initializing UX Screen Capture Harness [" + stageName.toUpperCase() + "] ===");

        outputDirLocal = Gdx.files.local("docs/ux/screenshots/" + stageName);
        if (!outputDirLocal.exists()) {
            outputDirLocal.mkdirs();
        }

        outputDirArtifact = Gdx.files.absolute("C:/Users/denni/.gemini/antigravity-ide/brain/1e8d53c9-8377-4cf2-8057-04c0d2d31436/screenshots/" + stageName);
        if (!outputDirArtifact.exists()) {
            outputDirArtifact.mkdirs();
        }

        // Initialize shared game screen and world state once
        try {
            sharedGameScreen = new GameScreen(game, 1, Difficulty.MEDIUM, GameMode.ADVANCED);
            sharedGameScreen.show();
            sharedGameScreen.resize(1920, 1080);
            sharedPlayer = sharedGameScreen.getPlayer();
            sharedMaze = sharedGameScreen.getMaze();

            populateMockPlayerState(sharedPlayer, sharedMaze);
        } catch (Exception e) {
            Gdx.app.error("UXCapture", "Failed to initialize shared GameScreen state", e);
        }

        buildTaskList();
        stepState = StepState.PREPARE;
    }

    private void populateMockPlayerState(Player player, Maze maze) {
        if (player == null) return;

        // Rich stats
        player.getStats().setCurrentHP(82);
        player.getStats().setMaxHP(100);
        player.getStats().setCurrentMP(48);
        player.getStats().setMaxMP(60);
        player.getStats().setStamina(3);
        player.getStats().setSatiety(85f);
        player.getStats().setHydration(90f);
        player.getStats().setBodyTemperature(37f);
        player.getStats().setTreasureScore(1450);
        DivinityManager.getInstance().addDivinities(350);

        // Equip primary gear
        try {
            Item sword = game.getItemDataManager().createItem(Item.ItemType.SWORD_BROAD, 0, 0, ItemColor.GRAY, game.getAssetManager());
            if (sword != null) player.getInventory().setRightHand(sword);

            Item shield = game.getItemDataManager().createItem(Item.ItemType.LARGE_SHIELD, 0, 0, ItemColor.GRAY, game.getAssetManager());
            if (shield != null) player.getInventory().setLeftHand(shield);

            Item armor = game.getItemDataManager().createItem(Item.ItemType.CHAIN_MAIL, 0, 0, ItemColor.GRAY, game.getAssetManager());
            if (armor != null) player.getEquipment().setWornChest(armor);

            // Populate rich pack
            addItemSafe(player, Item.ItemType.POTION_BLUE, ItemColor.BLUE);
            addItemSafe(player, Item.ItemType.POTION_PINK, ItemColor.PINK);
            addItemSafe(player, Item.ItemType.POTION_GREEN, ItemColor.GREEN);
            addItemSafe(player, Item.ItemType.CROSSBOW, ItemColor.GRAY);
            addItemSafe(player, Item.ItemType.RING_GOLD, ItemColor.GRAY);
            addItemSafe(player, Item.ItemType.GIB_BONE, ItemColor.GRAY);
            addItemSafe(player, Item.ItemType.GIB_FLESH, ItemColor.GRAY);
            addItemSafe(player, Item.ItemType.FOOD, ItemColor.GRAY);
            addItemSafe(player, Item.ItemType.INGOT, ItemColor.GRAY);
            addItemSafe(player, Item.ItemType.SCROLL, ItemColor.GRAY);
        } catch (Exception e) {
            Gdx.app.error("UXCapture", "Error equipping items", e);
        }

        // Spell slots
        try {
            player.setUnlockedSpellSlots(5);
            player.learnSpellId("FIREBALL");
            player.learnSpellId("HEAL");
            player.learnSpellId("SHIELD");
            player.learnSpellId("LIGHTNING");
            player.prepareSpell(0, "FIREBALL");
            player.prepareSpell(1, "HEAL");
            player.prepareSpell(2, "SHIELD");
            player.prepareSpell(3, "LIGHTNING");
        } catch (Exception e) {
            Gdx.app.error("UXCapture", "Error configuring spells", e);
        }

        // Wound for First Aid
        try {
            if (player.getInjuryManager() != null) {
                player.getInjuryManager().inflictInjury(BodyPart.ARMS, InjuryType.BONE_FRACTURE, 2);
                player.getInjuryManager().inflictInjury(BodyPart.TORSO, InjuryType.LACERATION_BLEEDING, 2);
            }
        } catch (Exception e) {
            Gdx.app.error("UXCapture", "Error configuring anatomy wound", e);
        }
    }

    private void addItemSafe(Player player, Item.ItemType type, ItemColor color) {
        Item item = game.getItemDataManager().createItem(type, 0, 0, color, game.getAssetManager());
        if (item != null) {
            player.getInventory().addItem(item);
        }
    }

    private void buildTaskList() {
        tasks.clear();

        // 01: Main Menu
        tasks.add(new CaptureTask("01_main_menu", "Main Menu Screen", () -> {
            MainMenuScreen s = new MainMenuScreen(game);
            setSubScreen(s, null);
        }));

        // 02: Save Slot Selection
        tasks.add(new CaptureTask("02_save_slots", "Save Slot Selection Screen", () -> {
            SaveSlotSelectScreen s = new SaveSlotSelectScreen(game, null, false);
            setSubScreen(s, null);
        }));

        // 03: Controls Legend / Rebinding
        tasks.add(new CaptureTask("03_controls", "Controls Screen", () -> {
            ControlsScreen s = new ControlsScreen(game, null);
            setSubScreen(s, null);
        }));

        // 04: Settings Menu
        tasks.add(new CaptureTask("04_settings", "Settings Screen", () -> {
            SettingsScreen s = new SettingsScreen(game);
            setSubScreen(s, null);
        }));

        // 05: Pause Screen
        tasks.add(new CaptureTask("05_pause", "Pause Screen", () -> {
            PauseScreen s = new PauseScreen(game, sharedGameScreen);
            setSubScreen(s, null);
        }));

        // 06: Loading Screen
        tasks.add(new CaptureTask("06_loading", "Loading Screen", () -> {
            LoadingScreen s = new LoadingScreen(game, false);
            setSubScreen(s, null);
        }));

        // 07: Game Over
        tasks.add(new CaptureTask("07_game_over", "Game Over Screen", () -> {
            GameOverScreen s = new GameOverScreen(game, false);
            setSubScreen(s, null);
        }));

        // 08: Player Death Screen
        tasks.add(new CaptureTask("08_player_death", "Player Death & Epitaph Screen", () -> {
            PlayerDeathScreen s = new PlayerDeathScreen(game, sharedGameScreen, 1, 50, 2f, 15, 0,
                    "Your physical vessel collapsed to venom in the catacombs...",
                    "Fell to a Harpy at Strata Depth 2 on turn 748", 2, 9, 9,
                    Arrays.asList("Javelin, Stone, Two-Handed", "Scythe", "Lance, Flight"),
                    Arrays.asList(com.bpm.minotaur.gamedata.item.Item.ItemType.JAVELIN_STONE_TWO_HANDED,
                            com.bpm.minotaur.gamedata.item.Item.ItemType.SCYTHE,
                            com.bpm.minotaur.gamedata.item.Item.ItemType.LANCE_FLIGHT));
            setSubScreen(s, null);
        }));

        // 08a: the case nobody sees while testing -- most deaths unlock nothing, so the empty
        // trophy row is the layout most likely to be wrong.
        tasks.add(new CaptureTask("08a_player_death_no_unlocks", "Player Death, No Discoveries", () -> {
            PlayerDeathScreen s = new PlayerDeathScreen(game, sharedGameScreen, 3, 50, 6f, 22, 2,
                    "Your physical vessel collapsed in the labyrinth...",
                    "Bled out from untended wounds at Strata Depth 1 on turn 210", 1, 2, 0,
                    java.util.Collections.emptyList());
            setSubScreen(s, null);
        }));

        // 08b: the far end of the doom curve, where Tarmin's Hunger should read as urgent.
        tasks.add(new CaptureTask("08b_player_death_high_doom", "Player Death, Near Apocalypse", () -> {
            PlayerDeathScreen s = new PlayerDeathScreen(game, sharedGameScreen, 47, 50, 94f, 31, 4,
                    "Your physical vessel collapsed in the labyrinth...",
                    "Fell to a Minotaur at Strata Depth 7 on turn 1902", 7, 88, 140,
                    Arrays.asList("Axe, Battle"),
                    Arrays.asList(com.bpm.minotaur.gamedata.item.Item.ItemType.AXE_BATTLE));
            setSubScreen(s, null);
        }));

        // 09: Primary Exploration HUD
        tasks.add(new CaptureTask("09_exploration_hud", "Exploration In-Game HUD", () -> {
            setSubScreen(sharedGameScreen, null);
        }));

        // 09a: a projectile mid-flight. Projectiles were invisible in the 3D engine for as long
        // as it has been the default, because only the raycaster ever drew them -- so this
        // captures the one thing that had no coverage at all.
        tasks.add(new CaptureTask("09a_projectile_in_flight", "Projectile In Flight (3D)", () -> {
            com.bpm.minotaur.gamedata.player.Player p = sharedGameScreen.getPlayer();
            com.bpm.minotaur.rendering.AnimationManager am = sharedGameScreen.getAnimationManager();
            if (p != null && am != null) {
                com.badlogic.gdx.math.Vector2 dir = p.getDirectionVector();
                com.badlogic.gdx.math.Vector2 muzzle = p.getPosition().cpy().add(dir.cpy().scl(1.4f));
                com.badlogic.gdx.math.Vector2 impact = p.getPosition().cpy().add(dir.cpy().scl(6f));
                // Long-lived on purpose: the capture grabs a single frame, and a 0.25s shot would
                // usually be over before the shutter opened.
                am.addAnimation(new com.bpm.minotaur.rendering.Animation(
                        com.bpm.minotaur.rendering.Animation.AnimationType.PROJECTILE_PLAYER,
                        muzzle, impact,
                        com.badlogic.gdx.graphics.Color.LIGHT_GRAY, 30f,
                        new String[] { "-" }));
            }
            setSubScreen(sharedGameScreen, null);
        }));

        // 10: Modern Grimoire Inventory
        tasks.add(new CaptureTask("10_inventory_modern", "Modern Inventory UI", () -> {
            InventoryScreen s = new InventoryScreen(game, sharedGameScreen, sharedPlayer, sharedMaze);
            setSubScreen(s, null);
        }));

        // 11: First Aid Triage Modal
        tasks.add(new CaptureTask("11_first_aid_modal", "Field Surgery & First Aid Modal", () -> {
            FirstAidModal s = new FirstAidModal(game, sharedGameScreen, sharedPlayer, sharedMaze,
                    sharedGameScreen.getSoundManager(), sharedGameScreen.getSpellCastOverlay());
            setSubScreen(s, null);
        }));

        // 12: Shopkeeper Bazaar Window
        tasks.add(new CaptureTask("12_shopkeeper_window", "Bazaar Shopkeeper Window", () -> {
            ShopkeeperNpc merchant = new ShopkeeperNpc(1f, 1f, game.getAssetManager());
            sharedGameScreen.getHud().getShopkeeperWindow().configure(
                    sharedPlayer, merchant, sharedGameScreen.getEventManager(), game.getItemDataManager(), null);
            sharedGameScreen.getHud().getShopkeeperWindow().show();
            setSubScreen(sharedGameScreen, () -> sharedGameScreen.getHud().getShopkeeperWindow().close());
        }));

        // 13: Combat Dice Overlay
        tasks.add(new CaptureTask("13_combat_dice_overlay", "Combat Dice Overlay", () -> {
            sharedGameScreen.getCombatManager().setCurrentState(CombatManager.CombatState.PLAYER_SELECT_DICE);
            setSubScreen(sharedGameScreen, () -> sharedGameScreen.getCombatManager().setCurrentState(CombatManager.CombatState.INACTIVE));
        }));

        // 14: Shelter Stash Chest
        tasks.add(new CaptureTask("14_shelter_chest", "Shelter Chest Stash Screen", () -> {
            ShelterChest chest = new ShelterChest(30);
            Item stashed1 = game.getItemDataManager().createItem(Item.ItemType.SWORD_TWO_HANDED, 0, 0, ItemColor.GRAY, game.getAssetManager());
            Item stashed2 = game.getItemDataManager().createItem(Item.ItemType.POTION_BLUE, 0, 0, ItemColor.BLUE, game.getAssetManager());
            Item stashed3 = game.getItemDataManager().createItem(Item.ItemType.INGOT, 0, 0, ItemColor.GRAY, game.getAssetManager());
            if (stashed1 != null) chest.addItem(stashed1);
            if (stashed2 != null) chest.addItem(stashed2);
            if (stashed3 != null) chest.addItem(stashed3);

            ShelterChestScreen s = new ShelterChestScreen(game, sharedGameScreen, sharedPlayer, chest);
            setSubScreen(s, null);
        }));

        // 15: Shelter Altar
        tasks.add(new CaptureTask("15_shelter_altar", "Shelter Altar & Blessings Screen", () -> {
            ShelterAltarScreen s = new ShelterAltarScreen(game, sharedGameScreen, sharedPlayer);
            setSubScreen(s, null);
        }));

        // 16: Crafting - Forge Tab
        tasks.add(new CaptureTask("16_crafting_forge", "Crafting Workshop (Forge Tab)", () -> {
            CraftingScreen s = new CraftingScreen(game, sharedGameScreen, sharedPlayer, sharedGameScreen.getCraftingManager());
            s.show();
            s.resize(1920, 1080);
            s.switchTab(0);
            setSubScreen(s, null);
        }));

        // 17: Crafting - Salvage Tab
        tasks.add(new CaptureTask("17_crafting_salvage", "Crafting Workshop (Salvage Tab)", () -> {
            CraftingScreen s = new CraftingScreen(game, sharedGameScreen, sharedPlayer, sharedGameScreen.getCraftingManager());
            s.show();
            s.resize(1920, 1080);
            s.switchTab(1);
            setSubScreen(s, null);
        }));

        // 18: Crafting - Ossuary Tab
        tasks.add(new CaptureTask("18_crafting_ossuary", "Crafting Workshop (Ossuary Tab)", () -> {
            CraftingScreen s = new CraftingScreen(game, sharedGameScreen, sharedPlayer, sharedGameScreen.getCraftingManager());
            s.show();
            s.resize(1920, 1080);
            s.switchTab(2);
            setSubScreen(s, null);
        }));

        // 19: Cooking - Whip Up Tab
        tasks.add(new CaptureTask("19_cooking_whip_up", "Cooking Hearth (Whip Up Tab)", () -> {
            CookingScreen s = new CookingScreen(game, sharedGameScreen, sharedPlayer, sharedGameScreen.getWorldManager());
            s.show();
            s.resize(1920, 1080);
            s.switchTab(0);
            setSubScreen(s, null);
        }));

        // 20: Cooking - Cauldron Tab
        tasks.add(new CaptureTask("20_cooking_cauldron", "Cooking Hearth (Cauldron Tab)", () -> {
            CookingScreen s = new CookingScreen(game, sharedGameScreen, sharedPlayer, sharedGameScreen.getWorldManager());
            s.show();
            s.resize(1920, 1080);
            s.switchTab(1);
            setSubScreen(s, null);
        }));

        // 21: Alchemy Screen
        tasks.add(new CaptureTask("21_alchemy", "Toxic Alchemy Laboratory Screen", () -> {
            AlchemyScreen s = new AlchemyScreen(game, sharedGameScreen, sharedPlayer);
            setSubScreen(s, null);
        }));

        // 22: Skill Tree Progression
        tasks.add(new CaptureTask("22_skill_tree", "Character Skill Tree Screen", () -> {
            SkillTreeScreen s = new SkillTreeScreen(game, sharedGameScreen, sharedPlayer, sharedMaze);
            setSubScreen(s, null);
        }));

        // 23: Spellbook Screen
        tasks.add(new CaptureTask("23_spellbook", "Spellbook Study Screen", () -> {
            SpellbookScreen s = new SpellbookScreen(game, sharedGameScreen, sharedPlayer, sharedMaze);
            setSubScreen(s, null);
        }));

        // 24: Castle Map
        tasks.add(new CaptureTask("24_castle_map", "Castle Overview & Exploration Map", () -> {
            CastleMapScreen s = new CastleMapScreen(game, sharedPlayer, sharedMaze, sharedGameScreen);
            setSubScreen(s, null);
        }));

        // 25: Encounter Window (Shrine)
        tasks.add(new CaptureTask("25_encounter_shrine", "Dungeon Shrine Encounter Modal", () -> {
            Encounter enc = new Encounter();
            enc.title = "ANCIENT SHRINE OF THE FORGOTTEN";
            enc.text = "You discover an obsidian altar nestled in the cavern recess. Runes pulse with an eerie violet luminescence, offering celestial blessings for a sacrifice of vital essence.";
            EncounterChoice c1 = new EncounterChoice();
            c1.text = "Offer 25 HP as vital essence for celestial sight";
            EncounterChoice c2 = new EncounterChoice();
            c2.text = "Whisper a prayer of fortitude to the Old Gods";
            EncounterChoice c3 = new EncounterChoice();
            c3.text = "Leave the altar untouched and step away";
            enc.choices = Arrays.asList(c1, c2, c3);

            sharedGameScreen.getHud().getEncounterWindow().configure(
                    sharedPlayer, game.getEncounterManager(), sharedGameScreen.getEventManager(),
                    game.getItemDataManager(), game.getMonsterDataManager(), game.getAssetManager(), sharedMaze, null);
            sharedGameScreen.getHud().getEncounterWindow().show(enc);

            setSubScreen(sharedGameScreen, () -> sharedGameScreen.getHud().getEncounterWindow().close());
        }));

        // 26: Bones Awaken Modal
        tasks.add(new CaptureTask("26_bones_awaken", "Hero Remains Bones Awakening Modal", () -> {
            Scenery corpse = new Scenery(Scenery.SceneryType.DECOMPOSING_CORPSE, (int) sharedPlayer.getPosition().x, (int) sharedPlayer.getPosition().y + 1);
            BonesData bData = new BonesData();
            bData.playerName = "Aldric the Bold";
            bData.epitaph = "Slain by Minotaur's Cleaver";
            bData.floorLevel = 3;
            bData.strataDepth = 2;
            bData.awakened = false;
            bData.defeated = false;
            corpse.setBonesData(bData);

            sharedGameScreen.getHud().getBonesAwakenModal().configureAndShow(
                    corpse, bData, sharedMaze, sharedPlayer, sharedGameScreen.getEventManager(),
                    sharedGameScreen.getSoundManager(), game.getAssetManager());

            setSubScreen(sharedGameScreen, () -> sharedGameScreen.getHud().getBonesAwakenModal().close());
        }));

        // 27: Torment Pact Screen
        tasks.add(new CaptureTask("27_torment_pacts", "Apocalyptic Torment Pacts Screen", () -> {
            TormentPactScreen s = new TormentPactScreen(game, null);
            setSubScreen(s, null);
        }));
    }

    private void setSubScreen(Screen screen, Runnable cleanup) {
        this.activeSubScreen = screen;
        this.activeCleanup = cleanup;
        if (activeSubScreen != null) {
            activeSubScreen.show();
            activeSubScreen.resize(1920, 1080);
        }
    }

    @Override
    public void render(float delta) {
        if (currentTaskIndex >= tasks.size()) {
            Gdx.app.log("UXCapture", "==========================================================");
            Gdx.app.log("UXCapture", "SUCCESS: All " + tasks.size() + " screens captured for [" + stageName.toUpperCase() + "]!");
            Gdx.app.log("UXCapture", "Local PNGs saved to: " + outputDirLocal.path());
            Gdx.app.log("UXCapture", "Artifact PNGs saved to: " + outputDirArtifact.path());
            Gdx.app.log("UXCapture", "==========================================================");
            Gdx.app.exit();
            return;
        }

        CaptureTask task = tasks.get(currentTaskIndex);

        switch (stepState) {
            case PREPARE:
                Gdx.app.log("UXCapture", "Preparing [" + (currentTaskIndex + 1) + "/" + tasks.size() + "] " + task.id + " (" + task.displayName + ")");
                try {
                    task.setup.execute();
                } catch (Exception e) {
                    Gdx.app.error("UXCapture", "Error preparing task " + task.id, e);
                }
                warmupCounter = 0;
                stepState = StepState.WARMUP;
                break;

            case WARMUP:
                if (activeSubScreen != null) {
                    try {
                        activeSubScreen.render(0.016f);
                    } catch (Exception e) {
                        Gdx.app.error("UXCapture", "Warmup render exception for " + task.id, e);
                    }
                }
                warmupCounter++;
                if (warmupCounter >= 3) {
                    stepState = StepState.CAPTURE;
                }
                break;

            case CAPTURE:
                ScreenUtils.clear(Color.BLACK);
                if (activeSubScreen != null) {
                    try {
                        activeSubScreen.render(0.016f);
                    } catch (Exception e) {
                        Gdx.app.error("UXCapture", "Capture render exception for " + task.id, e);
                    }
                }

                // Capture framebuffer
                try {
                    int width = Gdx.graphics.getBackBufferWidth();
                    int height = Gdx.graphics.getBackBufferHeight();
                    Pixmap rawPixmap = ScreenUtils.getFrameBufferPixmap(0, 0, width, height);
                    Pixmap pixmap = flipPixmapY(rawPixmap);
                    rawPixmap.dispose();

                    FileHandle localTarget = outputDirLocal.child(task.id + ".png");
                    PixmapIO.writePNG(localTarget, pixmap);

                    try {
                        FileHandle artTarget = outputDirArtifact.child(task.id + ".png");
                        PixmapIO.writePNG(artTarget, pixmap);
                    } catch (Exception e) {
                        Gdx.app.error("UXCapture", "Could not write artifact PNG", e);
                    }

                    pixmap.dispose();
                    Gdx.app.log("UXCapture", ">> Saved PNG: " + localTarget.path() + " (" + width + "x" + height + ")");
                } catch (Exception e) {
                    Gdx.app.error("UXCapture", "Error capturing pixmap for " + task.id, e);
                }

                // Cleanup
                if (activeCleanup != null) {
                    try {
                        activeCleanup.run();
                    } catch (Exception e) {
                        Gdx.app.error("UXCapture", "Cleanup exception for " + task.id, e);
                    }
                    activeCleanup = null;
                }

                if (activeSubScreen != null && activeSubScreen != sharedGameScreen) {
                    try {
                        activeSubScreen.hide();
                        activeSubScreen.dispose();
                    } catch (Exception e) {
                        Gdx.app.error("UXCapture", "SubScreen dispose exception for " + task.id, e);
                    }
                    activeSubScreen = null;
                }

                currentTaskIndex++;
                stepState = StepState.PREPARE;
                break;
        }
    }

    private static Pixmap flipPixmapY(Pixmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        Pixmap flipped = new Pixmap(w, h, src.getFormat());
        for (int y = 0; y < h; y++) {
            flipped.drawPixmap(src, 0, y, 0, h - 1 - y, w, 1);
        }
        return flipped;
    }

    @Override
    public void dispose() {
        if (sharedGameScreen != null) {
            sharedGameScreen.dispose();
        }
    }
}
