package com.bpm.minotaur.gamedata.progression;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.managers.DivinityManager;
import com.bpm.minotaur.managers.SaveManager;

/**
 * Persistent Shelter Altar meta-progression: three upgrade trees purchased with
 * banked Divinities. Like Divinities themselves, altar tiers survive death and
 * world resets, and are only cleared by a new game (a Tarmin crossing wipes nothing, #290).
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
        CRAFTING_BENCH("Crafting Bench", "A permanent workstation for dismantling, forging, and upgrading gear, with an Ossuary that carves the bones of slain creatures into new combat dice. Also teaches you to pack a Field Crafting Toolkit, letting you work materials on expedition.", 30, com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_CRAFTING_BENCH, com.bpm.minotaur.gamedata.item.Item.ItemType.CRAFTING_TOOLKIT),
        LANTERN("Shelter Lantern", "A bright mounted brass lantern casting steady illumination across the shelter entrance. Its flame is kept for you: the lantern you carry burns wider and brighter (+1 light radius, +5% crit while you carry it).", 10, com.bpm.minotaur.gamedata.item.Item.ItemType.BRASS_LANTERN, null),
        TRAINING_DUMMY("Training Grounds", "A training post and martial weapons rack that unlocks the Player Skill Tree to spend banked skill points.", 25, com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_TRAINING_DUMMY, null),
        ARCHIVE_LECTERN("Archive Lectern", "An illuminated stone lectern holding the Chronicle of Tarmin. Review all unlocked armory, unsealed arcana, and camp renovations.", 15, com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_ARCHIVE_LECTERN, null),
        CARTOGRAPHER_DESK("Cartographer's Desk", "A sturdy drafting desk strewn with parchment, ink, and calipers. Surveys the surrounding seeded world, revealing biomes, roads, war fronts, and monster dens.", 20, com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_CARTOGRAPHER_DESK, null),
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
    private final java.util.Set<StartingWeapon> unlockedStartingWeapons = new java.util.HashSet<>();
    private com.bpm.minotaur.gamedata.item.Item.ItemType selectedStartingWeapon = com.bpm.minotaur.gamedata.item.Item.ItemType.RUSTY_SWORD;

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
        cartographerDeskTier = 1;
        provisionsPacked = true;
        provisionsEarned = false;
        unlockedStartingWeapons.clear();
        unlockedStartingWeapons.add(StartingWeapon.RUSTY_SWORD);
        selectedStartingWeapon = com.bpm.minotaur.gamedata.item.Item.ItemType.RUSTY_SWORD;
    }

    public static final int MAX_CARTOGRAPHER_TIER = 3;
    private int cartographerDeskTier = 1;

    private boolean debugAllUnlocked = false;

    public boolean isDebugAllUnlocked() {
        return debugAllUnlocked;
    }

    public void setDebugAllUnlocked(boolean debugAllUnlocked) {
        this.debugAllUnlocked = debugAllUnlocked;
        BiomePortal.DEBUG_UNLOCK_ALL = debugAllUnlocked;
        if (debugAllUnlocked) {
            com.bpm.minotaur.telemetry.TelemetryManager.getInstance().markCheatsUsed();
            this.cartographerDeskTier = MAX_CARTOGRAPHER_TIER;
        }
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
        if (currentMaze == null || idm == null) return;
        for (Station station : Station.values()) {
            placeStation(currentMaze, station, idm, am);
        }
        java.util.List<com.badlogic.gdx.math.GridPoint2> lecterns = currentMaze.getStationSlots(Station.ARCHIVE_LECTERN);
        if (!lecterns.isEmpty()) {
            placeWarTable(currentMaze, lecterns.get(0), idm, am);
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
        if (altar == null) {
            // A shelter saved before its altar tile was kept: find the altar it already holds.
            for (java.util.Map.Entry<com.badlogic.gdx.math.GridPoint2, com.bpm.minotaur.gamedata.item.Item> e : maze.getItems().entrySet()) {
                if (e.getValue().getType() == com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_ALTAR) {
                    altar = e.getKey();
                    maze.setAltarTile(altar);
                    break;
                }
            }
        }
        if (altar != null && idm != null && !maze.getItems().containsKey(altar)) {
            com.bpm.minotaur.gamedata.item.Item item = idm.createItem(com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_ALTAR,
                    altar.x, altar.y, com.bpm.minotaur.gamedata.item.ItemColor.GOLD, am);
            if (item != null) maze.addItem(item);
        }
        // The old gods keep the record of the soul's lives, so their Book stands by their Altar from
        // the first day (#281): the death screen sends the player to it after the very first death.
        if (altar != null) {
            placeBeside(maze, altar, com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_BOOK_OF_LIVES,
                    com.bpm.minotaur.gamedata.item.ItemColor.GOLD, idm, am);
        }
    }

    /**
     * The War Table stands beside the Archive Lectern (Living War W27): the chronicle tells what the
     * houses did, the table where they are fighting now. On the first free floor beside it.
     */
    public void placeWarTable(com.bpm.minotaur.gamedata.Maze maze, com.badlogic.gdx.math.GridPoint2 lectern,
                               com.bpm.minotaur.gamedata.item.ItemDataManager idm,
                               com.badlogic.gdx.assets.AssetManager am) {
        placeBeside(maze, lectern, com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_WAR_TABLE,
                com.bpm.minotaur.gamedata.item.ItemColor.TAN, idm, am);
    }

    private static final int[][] AROUND = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    /**
     * Puts {@code type} on free floor beside {@code anchor}, unless one already stands there. A
     * piece that stands where it closes a way through the Shelter -- as the Book of Lives once
     * did, across the way out -- is taken up and stood somewhere it closes nothing.
     */
    private static void placeBeside(com.bpm.minotaur.gamedata.Maze maze, com.badlogic.gdx.math.GridPoint2 anchor,
                                    com.bpm.minotaur.gamedata.item.Item.ItemType type,
                                    com.bpm.minotaur.gamedata.item.ItemColor color,
                                    com.bpm.minotaur.gamedata.item.ItemDataManager idm,
                                    com.badlogic.gdx.assets.AssetManager am) {
        if (idm == null) return;
        for (int[] d : AROUND) {
            com.badlogic.gdx.math.GridPoint2 at = new com.badlogic.gdx.math.GridPoint2(anchor.x + d[0], anchor.y + d[1]);
            com.bpm.minotaur.gamedata.item.Item there = maze.getItems().get(at);
            if (there == null || there.getType() != type) continue;
            if (!blocks(maze, at, anchor)) return;
            maze.removeItem(there);
            break;
        }
        com.badlogic.gdx.math.GridPoint2 at = safeSpotBeside(maze, anchor);
        if (at == null) return;
        com.bpm.minotaur.gamedata.item.Item item = idm.createItem(type, at.x, at.y, color, am);
        if (item != null) maze.addItem(item);
    }

    /** The first floor beside {@code anchor} where a piece stands without closing any way through; null if none. */
    static com.badlogic.gdx.math.GridPoint2 safeSpotBeside(com.bpm.minotaur.gamedata.Maze maze,
                                                           com.badlogic.gdx.math.GridPoint2 anchor) {
        for (int[] d : AROUND) {
            int x = anchor.x + d[0], y = anchor.y + d[1];
            com.badlogic.gdx.math.GridPoint2 at = new com.badlogic.gdx.math.GridPoint2(x, y);
            if (!maze.isHomeTile(x, y) || !maze.isPassable(x, y) || maze.getItems().containsKey(at)
                    || isStationSlot(maze, at) || at.equals(maze.getAltarTile()) || at.equals(maze.getHearthTile())) continue;
            if (wouldCut(maze, at, anchor) || byTheWayOut(maze, at)) continue;
            return at;
        }
        return null;
    }

    /** Whether standing something on {@code at} splits the Shelter's floor in two (the anchor counts as blocked). */
    static boolean wouldCut(com.bpm.minotaur.gamedata.Maze maze, com.badlogic.gdx.math.GridPoint2 at,
                            com.badlogic.gdx.math.GridPoint2 anchor) {
        java.util.Set<com.badlogic.gdx.math.GridPoint2> blocked = new java.util.HashSet<>();
        blocked.add(anchor);
        int before = regions(maze, blocked, at);
        blocked.add(at);
        return regions(maze, blocked, at) > before;
    }

    /** Whether the piece already on {@code at} holds the Shelter's floor apart, or stands in the way out. */
    private static boolean blocks(com.bpm.minotaur.gamedata.Maze maze, com.badlogic.gdx.math.GridPoint2 at,
                                  com.badlogic.gdx.math.GridPoint2 anchor) {
        return wouldCut(maze, at, anchor) || byTheWayOut(maze, at);
    }

    /** Whether {@code at} is a door, or beside one or the Shelter's entry: the way out stays clear. */
    static boolean byTheWayOut(com.bpm.minotaur.gamedata.Maze maze, com.badlogic.gdx.math.GridPoint2 at) {
        com.badlogic.gdx.math.GridPoint2 entry = maze.getShelterEntry();
        if (maze.getGameObjectAt(at.x, at.y) instanceof com.bpm.minotaur.gamedata.Door) return true;
        for (int[] d : AROUND) {
            int x = at.x + d[0], y = at.y + d[1];
            if (entry != null && entry.x == x && entry.y == y) return true;
            if (maze.getGameObjectAt(x, y) instanceof com.bpm.minotaur.gamedata.Door) return true;
        }
        return false;
    }

    /**
     * How many separate stretches of Shelter floor there are, walking only between home tiles,
     * through no wall and over no impassable piece but {@code ignore} (the one being weighed);
     * doors count as open, since they open.
     */
    private static int regions(com.bpm.minotaur.gamedata.Maze maze,
                               java.util.Set<com.badlogic.gdx.math.GridPoint2> blocked,
                               com.badlogic.gdx.math.GridPoint2 ignore) {
        java.util.Set<com.badlogic.gdx.math.GridPoint2> seen = new java.util.HashSet<>();
        int regions = 0;
        for (com.badlogic.gdx.math.GridPoint2 start : maze.getHomeTiles()) {
            if (seen.contains(start) || !open(maze, start, blocked, ignore)) continue;
            regions++;
            java.util.ArrayDeque<com.badlogic.gdx.math.GridPoint2> queue = new java.util.ArrayDeque<>();
            queue.add(start);
            seen.add(start);
            while (!queue.isEmpty()) {
                com.badlogic.gdx.math.GridPoint2 here = queue.poll();
                for (com.bpm.minotaur.gamedata.Direction dir : com.bpm.minotaur.gamedata.Direction.values()) {
                    if (maze.isWallBlocking(here.x, here.y, dir)) continue;
                    com.badlogic.gdx.math.GridPoint2 next = new com.badlogic.gdx.math.GridPoint2(
                            here.x + (int) dir.getVector().x, here.y + (int) dir.getVector().y);
                    if (seen.contains(next) || !maze.isHomeTile(next.x, next.y) || !open(maze, next, blocked, ignore)) continue;
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return regions;
    }

    private static boolean open(com.bpm.minotaur.gamedata.Maze maze, com.badlogic.gdx.math.GridPoint2 at,
                                java.util.Set<com.badlogic.gdx.math.GridPoint2> blocked,
                                com.badlogic.gdx.math.GridPoint2 ignore) {
        if (blocked.contains(at)) return false;
        if (at.equals(ignore)) return true;
        com.bpm.minotaur.gamedata.item.Item item = maze.getItems().get(at);
        return item == null || !item.isImpassable();
    }

    private static boolean isStationSlot(com.bpm.minotaur.gamedata.Maze maze, com.badlogic.gdx.math.GridPoint2 at) {
        for (java.util.List<com.badlogic.gdx.math.GridPoint2> slots : maze.getAllStationSlots().values()) {
            if (slots.contains(at)) return true;
        }
        return false;
    }

    /** Puts one station at each of its slots in this maze, skipping slots already occupied. */
    private void placeStation(com.bpm.minotaur.gamedata.Maze maze, Station station,
                              com.bpm.minotaur.gamedata.item.ItemDataManager idm,
                              com.badlogic.gdx.assets.AssetManager am) {
        java.util.List<com.badlogic.gdx.math.GridPoint2> points = maze.getStationSlots(station);
        if (points.isEmpty() && station == Station.CARTOGRAPHER_DESK) {
            java.util.List<com.badlogic.gdx.math.GridPoint2> lecterns = maze.getStationSlots(Station.ARCHIVE_LECTERN);
            if (!lecterns.isEmpty()) {
                com.badlogic.gdx.math.GridPoint2 lectern = lecterns.get(0);
                com.badlogic.gdx.math.GridPoint2 kSlot = new com.badlogic.gdx.math.GridPoint2(lectern.x + 1, lectern.y - 1);
                if (maze.isHomeTile(kSlot.x, kSlot.y) && maze.isPassable(kSlot.x, kSlot.y)) {
                    maze.addStationSlot(Station.CARTOGRAPHER_DESK, kSlot.x, kSlot.y);
                    points = maze.getStationSlots(Station.CARTOGRAPHER_DESK);
                } else {
                    kSlot = new com.badlogic.gdx.math.GridPoint2(lectern.x, lectern.y - 1);
                    if (maze.isHomeTile(kSlot.x, kSlot.y) && maze.isPassable(kSlot.x, kSlot.y)) {
                        maze.addStationSlot(Station.CARTOGRAPHER_DESK, kSlot.x, kSlot.y);
                        points = maze.getStationSlots(Station.CARTOGRAPHER_DESK);
                    }
                }
            }
        }
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
            if (station == Station.ARCHIVE_LECTERN) placeWarTable(maze, pt, idm, am);
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

    // ---- Provisions: handed over at the start of each expedition (#101) ----
    // An expedition starts on the first turn outside the shelter. The pack for the
    // next one is earned by venturing out properly (the same trips that re-arm the
    // commune) and is packed on coming home, so stepping in and out of the door farms
    // nothing. A respawn is a new expedition and always gets one.

    /** A pack is waiting for the next step outside. */
    private boolean provisionsPacked = true;
    /** This expedition has gone far enough to earn the next pack once home. */
    private boolean provisionsEarned = false;

    /**
     * Called on every turn outside the shelter.
     *
     * @return how many bonus rations to hand over now: the Provisions tier on the
     *         first turn of an expedition, otherwise 0
     */
    public int claimExpeditionProvisions() {
        if (!provisionsPacked) return 0;
        provisionsPacked = false;
        save();
        return getBonusProvisionCount();
    }

    /** Called on every turn inside the shelter: a trip that earned it packs the next pack. */
    public void onShelterTurn() {
        if (provisionsEarned) {
            provisionsEarned = false;
            provisionsPacked = true;
            save();
        }
    }

    /** The player has ventured out far enough for this to count as an expedition. */
    public void noteVenturedOut() {
        if (!provisionsEarned) {
            provisionsEarned = true;
            save();
        }
    }

    /** A respawn starts a new expedition: pack for it whatever the last one did. */
    public void packProvisionsForNewExpedition() {
        provisionsPacked = true;
        provisionsEarned = false;
        save();
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

    // ---- Cartographer's Desk Progression ----

    public int getCartographerDeskTier() {
        if (debugAllUnlocked) return MAX_CARTOGRAPHER_TIER;
        return cartographerDeskTier;
    }

    public void setCartographerDeskTier(int tier) {
        this.cartographerDeskTier = Math.max(1, Math.min(MAX_CARTOGRAPHER_TIER, tier));
        save();
    }

    public int getCartographerSurveyRadius() {
        if (!hasStation(Station.CARTOGRAPHER_DESK)) return 0;
        return getCartographerDeskTier();
    }

    public int getCartographerUpgradeCrestCost() {
        int tier = getCartographerDeskTier();
        if (tier == 1) return 4;
        if (tier == 2) return 8;
        return -1;
    }

    public int getCartographerUpgradeDivinityCost() {
        int tier = getCartographerDeskTier();
        if (tier == 1) return 10;
        if (tier == 2) return 20;
        return -1;
    }

    public boolean canUpgradeCartographerDesk() {
        if (!hasStation(Station.CARTOGRAPHER_DESK)) return false;
        int crestCost = getCartographerUpgradeCrestCost();
        int divinityCost = getCartographerUpgradeDivinityCost();
        if (crestCost < 0 || divinityCost < 0) return false;
        return crestsOfValor >= crestCost && DivinityManager.getInstance().getDivinities() >= divinityCost;
    }

    public boolean upgradeCartographerDesk() {
        if (!canUpgradeCartographerDesk()) return false;
        int crestCost = getCartographerUpgradeCrestCost();
        int divinityCost = getCartographerUpgradeDivinityCost();
        if (DivinityManager.getInstance().spendDivinities(divinityCost)) {
            crestsOfValor -= crestCost;
            cartographerDeskTier++;
            save();
            return true;
        }
        return false;
    }

    // ---- Starting Arsenal (Weapon Meta-Progression) ----

    public boolean isStartingWeaponUnlocked(StartingWeapon weapon) {
        if (weapon == null) return false;
        if (weapon == StartingWeapon.RUSTY_SWORD || debugAllUnlocked) return true;
        return unlockedStartingWeapons.contains(weapon);
    }

    public boolean isStartingWeaponUnlocked(com.bpm.minotaur.gamedata.item.Item.ItemType type) {
        return isStartingWeaponUnlocked(StartingWeapon.fromItemType(type));
    }

    public java.util.Set<StartingWeapon> getUnlockedStartingWeapons() {
        java.util.Set<StartingWeapon> copy = new java.util.HashSet<>(unlockedStartingWeapons);
        copy.add(StartingWeapon.RUSTY_SWORD);
        return java.util.Collections.unmodifiableSet(copy);
    }

    public com.bpm.minotaur.gamedata.item.Item.ItemType getSelectedStartingWeapon() {
        if (selectedStartingWeapon == null || !isStartingWeaponUnlocked(selectedStartingWeapon)) {
            return com.bpm.minotaur.gamedata.item.Item.ItemType.RUSTY_SWORD;
        }
        return selectedStartingWeapon;
    }

    public void setSelectedStartingWeapon(com.bpm.minotaur.gamedata.item.Item.ItemType weaponType) {
        StartingWeapon sw = StartingWeapon.fromItemType(weaponType);
        if (isStartingWeaponUnlocked(sw)) {
            this.selectedStartingWeapon = sw.getItemType();
            save();
        }
    }

    public void setSelectedStartingWeapon(StartingWeapon weapon) {
        if (weapon != null && isStartingWeaponUnlocked(weapon)) {
            this.selectedStartingWeapon = weapon.getItemType();
            save();
        }
    }

    public boolean canUnlockStartingWeapon(StartingWeapon weapon) {
        if (weapon == null || isStartingWeaponUnlocked(weapon)) return false;
        return DivinityManager.getInstance().getCurrentDivinities() >= weapon.getDivinityCost();
    }

    public boolean unlockStartingWeapon(StartingWeapon weapon) {
        if (!canUnlockStartingWeapon(weapon)) return false;
        if (DivinityManager.getInstance().spendDivinities(weapon.getDivinityCost())) {
            unlockedStartingWeapons.add(weapon);
            save();
            return true;
        }
        return false;
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
            data.cartographerDeskTier = cartographerDeskTier;
            data.provisionsPacked = provisionsPacked;
            data.provisionsEarned = provisionsEarned;
            data.unlockedStartingWeapons = new java.util.ArrayList<>();
            for (StartingWeapon w : unlockedStartingWeapons) {
                data.unlockedStartingWeapons.add(w.name());
            }
            data.selectedStartingWeapon = (selectedStartingWeapon != null ? selectedStartingWeapon.name() : "RUSTY_SWORD");
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
                    cartographerDeskTier = data.cartographerDeskTier > 0 ? data.cartographerDeskTier : 1;
                    provisionsPacked = data.provisionsPacked;
                    provisionsEarned = data.provisionsEarned;
                    unlockedStartingWeapons.clear();
                    unlockedStartingWeapons.add(StartingWeapon.RUSTY_SWORD);
                    if (data.unlockedStartingWeapons != null) {
                        for (String s : data.unlockedStartingWeapons) {
                            try {
                                unlockedStartingWeapons.add(StartingWeapon.valueOf(s));
                            } catch (Exception ignored) {}
                        }
                    }
                    if (data.selectedStartingWeapon != null) {
                        try {
                            selectedStartingWeapon = com.bpm.minotaur.gamedata.item.Item.ItemType.valueOf(data.selectedStartingWeapon);
                        } catch (Exception ignored) {
                            selectedStartingWeapon = com.bpm.minotaur.gamedata.item.Item.ItemType.RUSTY_SWORD;
                        }
                    } else {
                        selectedStartingWeapon = com.bpm.minotaur.gamedata.item.Item.ItemType.RUSTY_SWORD;
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
        public int cartographerDeskTier = 1;
        /** Absent (true) in older saves: one pack owed, since none was ever handed over alive. */
        public boolean provisionsPacked = true;
        public boolean provisionsEarned = false;
        public java.util.List<String> unlockedStartingWeapons = new java.util.ArrayList<>();
        public String selectedStartingWeapon = "RUSTY_SWORD";
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
