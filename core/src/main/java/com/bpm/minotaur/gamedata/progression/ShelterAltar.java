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
public class ShelterAltar {

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
        LANTERN("Shelter Lantern", "A bright mounted brass lantern casting steady illumination across the shelter entrance.", 10, com.bpm.minotaur.gamedata.item.Item.ItemType.BRASS_LANTERN, null),
        TRAINING_DUMMY("Training Grounds", "A training post and martial weapons rack that unlocks the Player Skill Tree to spend banked skill points.", 25, com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_TRAINING_DUMMY, null),
        ARCHIVE_LECTERN("Archive Lectern", "An illuminated stone lectern holding the Chronicle of Tarmin. Review all unlocked armory, unsealed arcana, and camp renovations.", 15, com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_ARCHIVE_LECTERN, null);

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

        Station(String displayName, String description, int cost,
                com.bpm.minotaur.gamedata.item.Item.ItemType itemType,
                com.bpm.minotaur.gamedata.item.Item.ItemType portableKit) {
            this.portableKit = portableKit;
            this.displayName = displayName;
            this.description = description;
            this.cost = cost;
            this.itemType = itemType;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public int getCost() { return cost; }
        public com.bpm.minotaur.gamedata.item.Item.ItemType getItemType() { return itemType; }

        /** The portable counterpart this station teaches, or null if it has none. */
        public com.bpm.minotaur.gamedata.item.Item.ItemType getPortableKit() { return portableKit; }
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
    private static final String[][] ARCANE_UNLOCKED_SPELLS = {
            { "MAGIC_MISSILE", "SHIELD", "BURNING_HANDS" },
            { "MISTY_STEP", "ACID_ARROW", "SCORCHING_RAY" },
            { "FIREBALL", "LIGHTNING_BOLT" }
    };

    private final java.util.Set<Station> unlockedStations = new java.util.HashSet<>();
    private final java.util.Map<Station, java.util.List<com.badlogic.gdx.math.GridPoint2>> stationLocations = new java.util.EnumMap<>(Station.class);
    private boolean canCommune = true;

    private ShelterAltar() {
        load();
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

    public boolean hasStation(Station station) {
        return unlockedStations.contains(station);
    }

    public boolean isSkillTreeUnlocked() {
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

        if (currentMaze != null && idm != null && am != null) {
            java.util.List<com.badlogic.gdx.math.GridPoint2> points = stationLocations.get(station);
            if (points != null) {
                for (com.badlogic.gdx.math.GridPoint2 pt : points) {
                    com.bpm.minotaur.gamedata.item.ItemColor color = (station == Station.LANTERN || station == Station.ARCHIVE_LECTERN)
                            ? com.bpm.minotaur.gamedata.item.ItemColor.GOLD
                            : com.bpm.minotaur.gamedata.item.ItemColor.TAN;
                    com.bpm.minotaur.gamedata.item.Item item = idm.createItem(station.getItemType(), pt.x, pt.y, color, am);
                    currentMaze.addItem(item);

                    if (station == Station.CAMPFIRE) {
                        currentMaze.addLight(new com.bpm.minotaur.lighting.LightSource("shelter_cook_pot",
                                pt.x + 0.5f, pt.y + 0.5f,
                                com.bpm.minotaur.lighting.LightingManager.COLOR_CAMPFIRE, 4.5f, 1.2f,
                                com.bpm.minotaur.lighting.LightSource.FlickerProfile.CAMPFIRE_FLICKER));
                    } else if (station == Station.LANTERN) {
                        currentMaze.addLight(new com.bpm.minotaur.lighting.LightSource("shelter_lantern_" + pt.x + "_" + pt.y,
                                pt.x + 0.5f, pt.y + 0.5f,
                                com.bpm.minotaur.lighting.LightingManager.COLOR_LANTERN, 5.0f,
                                com.bpm.minotaur.lighting.LightingManager.MOUNTED_LANTERN_INTENSITY,
                                com.bpm.minotaur.lighting.LightSource.FlickerProfile.LANTERN_BREATH));
                    }
                }
            }
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
     * True if the given spell id belongs to one of the Arcane Attunement circles
     * but hasn't been unsealed yet by the current tier. Spells outside the
     * attunement circles entirely are never considered sealed.
     */
    public boolean isSpellSealed(String spellId) {
        if (spellId == null) return false;
        String upper = spellId.toUpperCase();
        for (int tier = 0; tier < ARCANE_UNLOCKED_SPELLS.length; tier++) {
            for (String id : ARCANE_UNLOCKED_SPELLS[tier]) {
                if (id.equals(upper)) {
                    return tier >= arcaneTier;
                }
            }
        }
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
}
