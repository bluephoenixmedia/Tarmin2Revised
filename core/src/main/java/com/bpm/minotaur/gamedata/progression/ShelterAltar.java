package com.bpm.minotaur.gamedata.progression;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.managers.DivinityManager;
import com.bpm.minotaur.managers.SaveManager;

/**
 * Persistent Shelter Altar meta-progression: three upgrade trees purchased with
 * banked Divinities. Like Divinities themselves, altar tiers survive death and
 * world resets, and are only cleared by a full Apocalypse wipe.
 */
public class ShelterAltar implements com.bpm.minotaur.managers.SlotScopedState {

    public enum Tree { PROVISIONS, REPERTOIRE, MONUMENT, ARCANE_ATTUNEMENT, ASCENSION }

    public enum StatType {
        STRENGTH("Strength", "Melee damage, physical carry weight, and heavy weapon efficiency."),
        DEXTERITY("Dexterity", "Accuracy, ranged bow precision, and reflex evasions."),
        CONSTITUTION("Constitution", "Maximum hit points and resistance to poison/toxic hazards."),
        INTELLIGENCE("Intelligence", "Maximum mana, spell damage, and runic deciphering."),
        WISDOM("Wisdom", "Spiritual energy, divine boons, and secret perception."),
        AGILITY("Agility", "Movement speed and defense evasion against attacks.");

        private final String displayName;
        private final String description;

        StatType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    public static final int MAX_ASCENSION_TIER = 5;
    public static final int[] ASCENSION_COSTS = { 1, 2, 4, 7, 10 };

    private int crestsOfValor = 0;
    private final java.util.Map<StatType, Integer> ascensionTiers = new java.util.EnumMap<>(StatType.class);

    public enum Station {
        BED("Bed / Sleeping Bag", "Enables resting to restore 100% HP & MP, clearing ailments, and saving the game without delve cooldowns.", 15, com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_SLEEPING_BAG, null),
        STASH_CHEST("Stash Chest", "A secure chest to store surplus weapons, armor, and treasures safely between delves.", 20, com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_CHEST, null),
        CAMPFIRE("Shelter Fire Pot", "A warm hearth providing continuous illumination and an indoor cooking station. Also teaches you to pack Portable Cookware, letting you cook on expedition.", 20, com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_FIRE_POT, com.bpm.minotaur.gamedata.item.Item.ItemType.COOKING_KIT),
        CRAFTING_BENCH("Crafting Bench", "A permanent workstation for dismantling, forging, and upgrading gear. Also teaches you to pack a Field Crafting Toolkit, letting you work materials on expedition.", 30, com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_CRAFTING_BENCH, com.bpm.minotaur.gamedata.item.Item.ItemType.CRAFTING_TOOLKIT),
        LANTERN("Shelter Lantern", "A bright mounted brass lantern casting steady illumination across the shelter entrance. Its flame is kept for you: the lantern you carry burns wider and brighter (+1 light radius, +5% crit while you carry it).", 10, com.bpm.minotaur.gamedata.item.Item.ItemType.BRASS_LANTERN, null),
        TRAINING_DUMMY("Training Grounds", "A training post and martial weapons rack that unlocks the Player Skill Tree to spend banked skill points.", 25, com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_TRAINING_DUMMY, null),
        ARCHIVE_LECTERN("Archive Lectern", "An illuminated stone lectern holding the Chronicle of Tarmin. Review all unlocked armory, unsealed arcana, and camp renovations.", 15, com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_ARCHIVE_LECTERN, null),
        PORTAL_FOREST("Verdant Gate", "A rune-carved arch that tears open onto the forest eaves, eleven chunks beyond the maze. Saves the long walk out.", 25, com.bpm.minotaur.gamedata.item.Item.ItemType.BIOME_PORTAL_FOREST, null, 3),
        PORTAL_DESERT("Dune Gate", "A rune-carved arch shimmering with heat distortion that opens onto the sun-scorched desert dunes. Saves a grueling trek.", 35, com.bpm.minotaur.gamedata.item.Item.ItemType.BIOME_PORTAL_DESERT, null, 5),
        PORTAL_LAKELANDS("Mist Gate", "A rune-carved arch dripping with cold dew that opens onto the murky shallows of the Lakelands. Saves wading the long miles.", 45, com.bpm.minotaur.gamedata.item.Item.ItemType.BIOME_PORTAL_LAKELANDS, null, 7),
        PORTAL_TUNDRA("Frost Gate", "A rune-carved arch rimmed with perpetual hoarfrost that opens onto the frozen Siberian wastes of the Tundra. Saves crossing the bitter permafrost.", 55, com.bpm.minotaur.gamedata.item.Item.ItemType.BIOME_PORTAL_TUNDRA, null, 9),
        PORTAL_BLIGHT("Crimson Gate", "A rune-carved arch weeping ash and ember that opens onto the Blighted Marches, at the road to Castle Tarmin itself. Saves the long march through the far wastes.", 65, com.bpm.minotaur.gamedata.item.Item.ItemType.BIOME_PORTAL_BLIGHT, null, 11);

        private final String displayName;
        private final String description;
        private final int cost;
        private final com.bpm.minotaur.gamedata.item.Item.ItemType itemType;
        /**
         * The travelling counterpart of this station, or null if it has none. Owning the
         * station is what earns the kit -- see FieldKitGrant. Kept here beside itemType so
         * a station's two item facts live on one line.
         */
        private final com.bpm.minotaur.gamedata.item.Item.ItemType portableKit;

        /**
         * Deepest dungeon level the player must have reached before this station
         * is even offered for sale. 0 means available from the start.
         *
         * <p>Portals exist to skip a long overland walk, so buying one early
         * would delete the exploration it is meant to reward you for surviving.
         */
        private final int requiredDepth;

        Station(String displayName, String description, int cost,
                com.bpm.minotaur.gamedata.item.Item.ItemType itemType,
                com.bpm.minotaur.gamedata.item.Item.ItemType portableKit) {
            this(displayName, description, cost, itemType, portableKit, 0);
        }

        Station(String displayName, String description, int cost,
                com.bpm.minotaur.gamedata.item.Item.ItemType itemType,
                com.bpm.minotaur.gamedata.item.Item.ItemType portableKit,
                int requiredDepth) {
            this.portableKit = portableKit;
            this.displayName = displayName;
            this.description = description;
            this.cost = cost;
            this.itemType = itemType;
            this.requiredDepth = requiredDepth;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public int getCost() { return cost; }
        public com.bpm.minotaur.gamedata.item.Item.ItemType getItemType() { return itemType; }

        /** The portable counterpart this station teaches, or null if it has none. */
        public com.bpm.minotaur.gamedata.item.Item.ItemType getPortableKit() { return portableKit; }

        public int getRequiredDepth() { return requiredDepth; }

        /**
         * True when this station should appear in the Altar at all.
         *
         * <p>A station the player cannot yet reach the depth for stays hidden
         * rather than showing as an unaffordable row, so the shelter list
         * always reads as things you could actually buy.
         */
        public boolean isRevealed() {
            if (requiredDepth <= 0) return true;
            if (com.bpm.minotaur.gamedata.progression.BiomePortal.DEBUG_UNLOCK_ALL) return true;
            return com.bpm.minotaur.managers.UnlockManager.getInstance()
                    .getData().deepestLevelReached >= requiredDepth;
        }
    }

    public static final int MAX_TIER = 3;
    private static final int BASE_COST = 30;

    private static final float STATUE_FREQUENCY_BASE = 0.15f;
    private static final float STATUE_FREQUENCY_MAX = 0.35f;

    private static ShelterAltar instance;

    private int provisionsTier = 0;
    private int repertoireTier = 0;
    private int monumentTier = 0;
    private int arcaneTier = 0;

    private static final int[] ARCANE_COSTS = { 15, 25, 40 };
    public static final String[][] ARCANE_UNLOCKED_SPELLS = {
            { "MAGIC_MISSILE", "SHIELD", "BURNING_HANDS" },
            { "MISTY_STEP", "ACID_ARROW", "SCORCHING_RAY", "WORD_OF_RECALL" },
            { "FIREBALL", "LIGHTNING_BOLT" }
    };

    private final java.util.Set<Station> unlockedStations = new java.util.HashSet<>();
    private final java.util.Map<Station, java.util.List<com.badlogic.gdx.math.GridPoint2>> stationLocations = new java.util.EnumMap<>(Station.class);
    private boolean canCommune = true;

    private ShelterAltar() {
        load();
        // Follow the active slot; see SlotScopedState.
        com.bpm.minotaur.managers.SaveManager.register(this);
    }

    public static ShelterAltar getInstance() {
        if (instance == null) {
            instance = new ShelterAltar();
        }
        return instance;
    }

    public void reset() {
        provisionsTier = 0;
        repertoireTier = 0;
        monumentTier = 0;
        arcaneTier = 0;
        crestsOfValor = 0;
        ascensionTiers.clear();
        unlockedStations.clear();
        canCommune = true;
    }

    private boolean debugAllUnlocked = false;

    public boolean isDebugAllUnlocked() {
        return debugAllUnlocked;
    }

    public void setDebugAllUnlocked(boolean debugAllUnlocked) {
        this.debugAllUnlocked = debugAllUnlocked;
        BiomePortal.DEBUG_UNLOCK_ALL = debugAllUnlocked;
    }

    public boolean hasStation(Station station) {
        if (debugAllUnlocked) return true;
        return unlockedStations.contains(station);
    }

    public boolean isSkillTreeUnlocked() {
        if (debugAllUnlocked) return true;
        return hasStation(Station.TRAINING_DUMMY);
    }

    public void setStationUnlocked(Station station, boolean unlocked) {
        if (unlocked) {
            unlockedStations.add(station);
        } else {
            unlockedStations.remove(station);
        }
    }

    public java.util.Set<Station> getUnlockedStations() {
        return java.util.Collections.unmodifiableSet(unlockedStations);
    }

    public boolean canCommune() {
        return canCommune;
    }

    public void setCanCommune(boolean canCommune) {
        this.canCommune = canCommune;
    }

    public void rearmCommune() {
        if (!this.canCommune) {
            this.canCommune = true;
            save();
        }
    }

    public void registerStationLocation(Station station, int x, int y) {
        stationLocations.computeIfAbsent(station, k -> new java.util.ArrayList<>())
                .add(new com.badlogic.gdx.math.GridPoint2(x, y));
    }

    public java.util.List<com.badlogic.gdx.math.GridPoint2> getStationLocations(Station station) {
        return stationLocations.getOrDefault(station, java.util.Collections.emptyList());
    }

    /**
     * Materializes every shelter station into the current maze chunk.
     * Used by the debug F5 sandbox toggle to instantiate all amenities in-place.
     */
    public void materialiseAllStations(com.bpm.minotaur.gamedata.Maze currentMaze,
                                       com.bpm.minotaur.gamedata.item.ItemDataManager idm,
                                       com.badlogic.gdx.assets.AssetManager am) {
        if (currentMaze == null || idm == null || am == null) return;
        for (Station station : Station.values()) {
            placeStation(currentMaze, station, idm, am);
        }
    }

    /**
     * Stands every unlocked station in a lit shelter, and relights the ones already there.
     *
     * <p>Unlocks are global, so a station bought in one shelter appears in every
     * shelter the next time it loads. Lights are not saved with a chunk, so this
     * also restores the fire pot's and lantern's light on a reload.
     */
    public void furnish(com.bpm.minotaur.gamedata.Maze maze,
                        com.bpm.minotaur.gamedata.item.ItemDataManager idm,
                        com.badlogic.gdx.assets.AssetManager am) {
        if (maze == null || !maze.isSanctuary()) return;
        for (Station station : Station.values()) {
            if (hasStation(station)) {
                placeStation(maze, station, idm, am);
            }
        }
        com.badlogic.gdx.math.GridPoint2 altar = maze.getAltarTile();
        if (altar != null && idm != null && !maze.getItems().containsKey(altar)) {
            com.bpm.minotaur.gamedata.item.Item item = idm.createItem(com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_ALTAR,
                    altar.x, altar.y, com.bpm.minotaur.gamedata.item.ItemColor.GOLD, am);
            if (item != null) maze.addItem(item);
        }
    }

    /** Puts one station at each of its slots in this maze, skipping slots already occupied. */
    private void placeStation(com.bpm.minotaur.gamedata.Maze maze, Station station,
                              com.bpm.minotaur.gamedata.item.ItemDataManager idm,
                              com.badlogic.gdx.assets.AssetManager am) {
        java.util.List<com.badlogic.gdx.math.GridPoint2> points = maze.getStationSlots(station);
        if (points.isEmpty()) return;
        BiomePortal portal = BiomePortal.forStation(station);
        if (portal != null) {
            // Portals clear their placeholder archway and bring their own
            // light, so they cannot use the generic station placement.
            if (idm != null && am != null) {
                for (com.badlogic.gdx.math.GridPoint2 pt : points) {
                    portal.materialise(maze, pt, idm, am);
                }
            }
            return;
        }
        for (com.badlogic.gdx.math.GridPoint2 pt : points) {
            com.bpm.minotaur.gamedata.item.Item existing = maze.getItems().get(pt);
            if (existing == null && idm != null) {
                com.bpm.minotaur.gamedata.item.ItemColor color = (station == Station.LANTERN || station == Station.ARCHIVE_LECTERN)
                        ? com.bpm.minotaur.gamedata.item.ItemColor.GOLD
                        : com.bpm.minotaur.gamedata.item.ItemColor.TAN;
                com.bpm.minotaur.gamedata.item.Item item = idm.createItem(station.getItemType(), pt.x, pt.y, color, am);
                if (item != null) {
                    maze.addItem(item);
                    existing = item;
                }
            }
            if (existing == null || existing.getType() != station.getItemType()) continue;
            if (station == Station.CAMPFIRE) {
                maze.removeLight("shelter_cook_pot");
                maze.addLight(new com.bpm.minotaur.lighting.LightSource("shelter_cook_pot",
                        pt.x + 0.5f, pt.y + 0.5f,
                        com.bpm.minotaur.lighting.LightingManager.COLOR_CAMPFIRE, 4.5f, 1.2f,
                        com.bpm.minotaur.lighting.LightSource.FlickerProfile.CAMPFIRE_FLICKER));
            } else if (station == Station.LANTERN) {
                String id = "shelter_lantern_" + pt.x + "_" + pt.y;
                maze.removeLight(id);
                maze.addLight(new com.bpm.minotaur.lighting.LightSource(id,
                        pt.x + 0.5f, pt.y + 0.5f,
                        com.bpm.minotaur.lighting.LightingManager.COLOR_LANTERN, 5.0f,
                        com.bpm.minotaur.lighting.LightingManager.MOUNTED_LANTERN_INTENSITY,
                        com.bpm.minotaur.lighting.LightSource.FlickerProfile.LANTERN_BREATH));
            }
        }
    }

    public boolean unlockStation(Station station, com.bpm.minotaur.gamedata.Maze currentMaze,
                                 com.bpm.minotaur.gamedata.item.ItemDataManager idm,
                                 com.badlogic.gdx.assets.AssetManager am) {
        if (hasStation(station)) {
            return false;
        }
        if (!DivinityManager.getInstance().spendDivinities(station.getCost())) {
            return false;
        }
        unlockedStations.add(station);
        save();

        // Stands up in the shelter the player is in; every other shelter gets it on its next load.
        if (currentMaze != null && idm != null && am != null) {
            placeStation(currentMaze, station, idm, am);
        }
        return true;
    }

    public boolean commune(com.bpm.minotaur.gamedata.player.Player player, com.bpm.minotaur.managers.WorldManager worldManager) {
        if (!canCommune) {
            return false;
        }
        if (player != null && player.getStats() != null) {
            player.getStats().setCurrentHP(player.getStats().getMaxHP());
            player.getStats().setCurrentMP(player.getStats().getMaxMP());
            if (player.getStatusManager() != null) {
                player.getStatusManager().clearEffects();
            }
            com.bpm.minotaur.managers.DoomManager.getInstance().resetExpeditionTurns();
            if (worldManager != null) {
                SaveManager.getInstance().saveActiveSlot(player, worldManager);
                SaveManager.getInstance().backupActiveSlot();
            }
        }
        canCommune = false;
        save();
        return true;
    }

    public static int getSacrificeValue(com.bpm.minotaur.gamedata.item.Item item) {
        if (item == null) return 0;
        if (item.getType() != null && item.getType().name().startsWith("GIB_")) {
            return 1;
        }
        com.bpm.minotaur.gamedata.item.ItemColor col = item.getItemColor();
        if (col == null) col = com.bpm.minotaur.gamedata.item.ItemColor.TAN;
        switch (col) {
            case TAN: return 1;
            case GRAY: return 2;
            case GREEN: {
                int base = 4;
                int mods = (item.getModifiers() != null) ? item.getModifiers().size() : 0;
                return base + (mods * 2);
            }
            case BLUE: return 8;
            case GOLD: return 15;
            default: return 1;
        }
    }

    public boolean sacrificeItem(com.bpm.minotaur.gamedata.player.Player player, com.bpm.minotaur.gamedata.item.Item item) {
        if (player == null || item == null) return false;
        if (player.getInventory() != null && player.getInventory().getMainInventory().contains(item)) {
            player.getInventory().removeItem(item);
            int value = getSacrificeValue(item);
            DivinityManager.getInstance().addDivinities(value);
            return true;
        }
        return false;
    }

    public int getProvisionsTier() {
        return provisionsTier;
    }

    public int getRepertoireTier() {
        return repertoireTier;
    }

    public int getMonumentTier() {
        return monumentTier;
    }

    public int getTier(Tree tree) {
        if (debugAllUnlocked && tree != Tree.ASCENSION) {
            return MAX_TIER;
        }
        switch (tree) {
            case PROVISIONS: return provisionsTier;
            case REPERTOIRE: return repertoireTier;
            case MONUMENT: return monumentTier;
            case ARCANE_ATTUNEMENT: return arcaneTier;
            default: return 0;
        }
    }

    public boolean isMaxed(Tree tree) {
        return getTier(tree) >= MAX_TIER;
    }

    /** @return the Divinity cost of the next tier in this tree, or -1 if maxed. */
    public int getNextUpgradeCost(Tree tree) {
        if (isMaxed(tree)) {
            return -1;
        }
        if (tree == Tree.ARCANE_ATTUNEMENT) {
            return ARCANE_COSTS[getTier(tree)];
        }
        return BASE_COST * (getTier(tree) + 1);
    }

    public boolean purchaseUpgrade(Tree tree) {
        int cost = getNextUpgradeCost(tree);
        if (cost < 0) {
            return false;
        }
        if (!DivinityManager.getInstance().spendDivinities(cost)) {
            return false;
        }
        switch (tree) {
            case PROVISIONS: provisionsTier++; break;
            case REPERTOIRE: repertoireTier++; break;
            case MONUMENT: monumentTier++; break;
            case ARCANE_ATTUNEMENT: arcaneTier++; break;
        }
        save();
        return true;
    }

    /**
     * What Arcane Attunement adds to every Tome Choice: tier 1 shows 4 options,
     * tier 2 adds a reroll, tier 3 shows 5 options and lets the Tome of Tarmin
     * offer level 8 spells.
     */
    public com.bpm.minotaur.gamedata.spells.TomeChoice.Perks getTomeChoicePerks() {
        int options = 3 + (arcaneTier >= 1 ? 1 : 0) + (arcaneTier >= 3 ? 1 : 0);
        int rerolls = arcaneTier >= 2 ? 1 : 0;
        int tarminMaxLevel = com.bpm.minotaur.gamedata.spells.Tome.TARMIN.getMaxSpellLevel() + (arcaneTier >= 3 ? 1 : 0);
        return new com.bpm.minotaur.gamedata.spells.TomeChoice.Perks(options, rerolls, tarminMaxLevel);
    }

    /** Spell ids unsealed for loot spawning by the current Arcane Attunement tier. */
    public java.util.List<String> getUnsealedSpellIds() {
        java.util.List<String> unsealed = new java.util.ArrayList<>();
        for (int i = 0; i < arcaneTier && i < ARCANE_UNLOCKED_SPELLS.length; i++) {
            java.util.Collections.addAll(unsealed, ARCANE_UNLOCKED_SPELLS[i]);
        }
        return unsealed;
    }

    /** Spell ids that the next Arcane Attunement tier purchase would unseal. */
    public java.util.List<String> getNextTierSpells() {
        if (isMaxed(Tree.ARCANE_ATTUNEMENT)) {
            return java.util.Collections.emptyList();
        }
        return java.util.List.of(ARCANE_UNLOCKED_SPELLS[arcaneTier]);
    }

    /**
     * Spells are no longer hard-sealed by Arcane Attunement (retired in Phase 1 overhaul).
     * Always returns false.
     */
    public boolean isSpellSealed(String spellId) {
        return false;
    }

    /** Extra bread/waterskin/bandages granted at the start of each expedition. */
    public int getBonusProvisionCount() {
        return provisionsTier;
    }

    /** Per-chunk chance of a statue encounter event, scaling from a 15% baseline to 35% at max tier. */
    public float getStatueEventFrequency() {
        return STATUE_FREQUENCY_BASE + (STATUE_FREQUENCY_MAX - STATUE_FREQUENCY_BASE) * ((float) monumentTier / MAX_TIER);
    }

    // ---- Altar of Ascension (Stat Progression) ----

    public int getCrestsOfValor() {
        return crestsOfValor;
    }

    public void addCrestsOfValor(int amount) {
        this.crestsOfValor = Math.max(0, this.crestsOfValor + amount);
        save();
    }

    public void setCrestsOfValor(int amount) {
        this.crestsOfValor = Math.max(0, amount);
        save();
    }

    public int getAscensionTier(StatType stat) {
        if (debugAllUnlocked) return MAX_ASCENSION_TIER;
        return ascensionTiers.getOrDefault(stat, 0);
    }

    public int getAscensionBonus(StatType stat) {
        return getAscensionTier(stat);
    }

    public void setAscensionTier(StatType stat, int tier) {
        ascensionTiers.put(stat, Math.max(0, Math.min(MAX_ASCENSION_TIER, tier)));
        save();
    }

    public int getAscensionCost(StatType stat) {
        int tier = getAscensionTier(stat);
        if (tier >= MAX_ASCENSION_TIER) return -1;
        return ASCENSION_COSTS[tier];
    }

    public boolean canAscend(StatType stat) {
        int cost = getAscensionCost(stat);
        return cost > 0 && crestsOfValor >= cost;
    }

    public boolean purchaseAscension(StatType stat) {
        if (!canAscend(stat)) return false;
        int cost = getAscensionCost(stat);
        crestsOfValor -= cost;
        ascensionTiers.put(stat, getAscensionTier(stat) + 1);
        save();
        return true;
    }

    // ---- Persistence ----

    private String getSaveFilePath() {
        return SaveManager.getInstance().getActiveSlotFilePath("altar_progression.json");
    }

    public void save() {
        try {
            FileHandle dir = Gdx.files.local("saves/");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            FileHandle file = Gdx.files.local(getSaveFilePath());
            SaveData data = new SaveData();
            data.provisionsTier = provisionsTier;
            data.repertoireTier = repertoireTier;
            data.monumentTier = monumentTier;
            data.arcaneTier = arcaneTier;
            data.canCommune = canCommune;
            data.unlockedStations = new java.util.ArrayList<>();
            for (Station s : unlockedStations) {
                data.unlockedStations.add(s.name());
            }
            data.crestsOfValor = crestsOfValor;
            data.ascensionTiers = new java.util.HashMap<>();
            for (java.util.Map.Entry<StatType, Integer> entry : ascensionTiers.entrySet()) {
                data.ascensionTiers.put(entry.getKey().name(), entry.getValue());
            }
            SaveManager.getInstance().atomicWriteJson(file, data);
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("ShelterAltar", "Failed to save: " + e.getMessage());
            }
        }
    }

    private void load() {
        try {
            FileHandle file = Gdx.files.local(getSaveFilePath());
            if (file.exists()) {
                Json json = new Json();
                json.setUsePrototypes(false);
                SaveData data = json.fromJson(SaveData.class, file.readString());
                if (data != null) {
                    provisionsTier = data.provisionsTier;
                    repertoireTier = data.repertoireTier;
                    monumentTier = data.monumentTier;
                    arcaneTier = data.arcaneTier;
                    canCommune = data.canCommune;
                    unlockedStations.clear();
                    if (data.unlockedStations != null) {
                        for (String s : data.unlockedStations) {
                            try {
                                unlockedStations.add(Station.valueOf(s));
                            } catch (Exception ignored) {}
                        }
                    }
                    crestsOfValor = data.crestsOfValor;
                    ascensionTiers.clear();
                    if (data.ascensionTiers != null) {
                        for (java.util.Map.Entry<String, Integer> entry : data.ascensionTiers.entrySet()) {
                            try {
                                ascensionTiers.put(StatType.valueOf(entry.getKey()), entry.getValue());
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("ShelterAltar", "Failed to load: " + e.getMessage());
            }
        }
    }

    public static class SaveData {
        public int provisionsTier = 0;
        public int repertoireTier = 0;
        public int monumentTier = 0;
        public int arcaneTier = 0;
        public java.util.List<String> unlockedStations = new java.util.ArrayList<>();
        public boolean canCommune = true;
        public int crestsOfValor = 0;
        public java.util.Map<String, Integer> ascensionTiers = new java.util.HashMap<>();
    }

    @Override
    public void reloadForActiveSlot() {
        reset();
        load();
    }

    @Override
    public void resetForNewGame() {
        reset();
        save();
    }
}
