package com.bpm.minotaur.gamedata.progression;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.generation.Biome;

/**
 * A shelter portal that warps the player to a distant biome.
 *
 * <p>The central maze is 21x21 chunks, so the wilderness begins eleven chunks
 * out. Portals are the intended answer to that walk: unlocked late, bought with
 * Crests, and free to use thereafter.
 *
 * <p>Everything a portal is -- its station, its destination, its item, its
 * colour -- lives on one line here, so adding Desert later is a row rather than
 * a hunt through five files.
 */
public enum BiomePortal {

    FOREST(
            ShelterAltar.Station.PORTAL_FOREST,
            Biome.FOREST,
            Item.ItemType.BIOME_PORTAL_FOREST,
            new Color(0.25f, 0.85f, 0.35f, 1f),
            "Verdant Gate"),

    DESERT(
            ShelterAltar.Station.PORTAL_DESERT,
            Biome.DESERT,
            Item.ItemType.BIOME_PORTAL_DESERT,
            new Color(0.95f, 0.75f, 0.40f, 1f),
            "Dune Gate"),

    LAKELANDS(
            ShelterAltar.Station.PORTAL_LAKELANDS,
            Biome.LAKELANDS,
            Item.ItemType.BIOME_PORTAL_LAKELANDS,
            new Color(0.30f, 0.80f, 0.95f, 1f),
            "Mist Gate");

    /**
     * Tint for the return portal left at an arrival point. It has no biome of
     * its own -- it always leads home -- so it lives here rather than being
     * written out wherever a return portal is drawn or placed.
     */
    public static final Color RETURN_PORTAL_TINT = new Color(0.55f, 0.70f, 1.0f, 1f);

    private final ShelterAltar.Station station;
    private final Biome destination;
    private final Item.ItemType itemType;
    private final Color tint;
    private final String displayName;

    BiomePortal(ShelterAltar.Station station, Biome destination, Item.ItemType itemType,
                Color tint, String displayName) {
        this.station = station;
        this.destination = destination;
        this.itemType = itemType;
        this.tint = tint;
        this.displayName = displayName;
    }

    public ShelterAltar.Station getStation() {
        return station;
    }

    public Biome getDestination() {
        return destination;
    }

    public Item.ItemType getItemType() {
        return itemType;
    }

    /** Tints the rune circle, the vortex and the portal's light source. */
    public Color getTint() {
        return tint;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** The portal for this item type, or null if the item is not a portal. */
    public static BiomePortal forItem(Item.ItemType type) {
        if (type == null) return null;
        for (BiomePortal portal : values()) {
            if (portal.itemType == type) return portal;
        }
        return null;
    }

    /** The portal for this station, or null if the station is not a portal. */
    public static BiomePortal forStation(ShelterAltar.Station station) {
        if (station == null) return null;
        for (BiomePortal portal : values()) {
            if (portal.station == station) return portal;
        }
        return null;
    }

    /**
     * True when the player may use this portal right now.
     *
     * <p>The debug override grants every portal for the session without writing
     * anything to the save, so testing a biome never leaves a purchase behind.
     */
    public boolean isAvailable() {
        if (DEBUG_UNLOCK_ALL) return true;
        return ShelterAltar.getInstance().hasStation(station);
    }

    /**
     * Stands this portal up on a tile: clears the placeholder archway, adds the
     * portal item, and lights it in the destination's colour.
     *
     * <p>Three callers need identical behaviour -- chunk generation for an
     * already-owned portal, the Altar on purchase, and the debug override -- so
     * the light and the item cannot drift apart between them.
     *
     * <p>The stone archway placed in an empty niche is deliberately left in
     * place: it is the portal's frame, not a placeholder. An owned niche is the
     * same arch with a rift burning inside it.
     */
    public void materialise(com.bpm.minotaur.gamedata.Maze maze,
                            com.badlogic.gdx.math.GridPoint2 tile,
                            com.bpm.minotaur.gamedata.item.ItemDataManager itemDataManager,
                            com.badlogic.gdx.assets.AssetManager assetManager) {
        if (maze == null || tile == null || itemDataManager == null) return;

        if (!maze.getItems().containsKey(tile)) {
            com.bpm.minotaur.gamedata.item.Item portalItem = itemDataManager.createItem(
                    itemType, tile.x, tile.y,
                    com.bpm.minotaur.gamedata.item.ItemColor.GOLD, assetManager);
            if (portalItem != null) {
                maze.addItem(portalItem);
            }
        }

        maze.addLight(new com.bpm.minotaur.lighting.LightSource(
                "shelter_portal_" + name(), tile.x + 0.5f, tile.y + 0.5f,
                tint, 4.0f, 1.1f,
                com.bpm.minotaur.lighting.LightSource.FlickerProfile.LANTERN_BREATH));
    }

    /**
     * Stands up every portal the debug override grants, in the maze the player
     * is standing in.
     *
     * <p>Without this the override would only take effect the next time the
     * shelter chunk regenerated, which is exactly the slow loop it exists to
     * remove.
     */
    public static void materialiseDebugPortals(com.bpm.minotaur.gamedata.Maze maze,
                                               com.bpm.minotaur.gamedata.item.ItemDataManager itemDataManager,
                                               com.badlogic.gdx.assets.AssetManager assetManager) {
        if (!DEBUG_UNLOCK_ALL || maze == null) return;

        for (BiomePortal portal : values()) {
            for (com.badlogic.gdx.math.GridPoint2 tile
                    : ShelterAltar.getInstance().getStationLocations(portal.getStation())) {
                portal.materialise(maze, tile, itemDataManager, assetManager);
            }
        }
    }

    /**
     * Session-only override that unlocks every portal and waives the Crest cost.
     *
     * <p>Stays false in source and is never persisted: a debug state that
     * survives into a save is how a cheat ships by accident. Toggled live with
     * F5 in {@code GameScreen}.
     */
    public static boolean DEBUG_UNLOCK_ALL = false;
}
