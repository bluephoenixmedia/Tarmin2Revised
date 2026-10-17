package com.bpm.minotaur.gamedata.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.shelter.BeaconPlanner;
import com.bpm.minotaur.gamedata.shelter.ShelterNetwork;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.WorldConstants;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.SaveManager;
import com.bpm.minotaur.managers.SlotScopedState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the player has learned about the world beyond the chunks they entered.
 *
 * <p>A chunk the player entered already has a save file, so the map knows it in full.
 * This holds the rest: the chunks glimpsed across a seamless border, the shelters whose
 * beacons have been on the horizon, the player's pins and their waypoint. A death lays
 * out a new world, and all of it goes with the old one ({@link #forgetWorld}).
 * See docs/DEsign/Requirements_ Expedition Map.md, section 3.
 */
public final class MapKnowledge implements SlotScopedState {

    /** The six pin icons, in the order {@link #cyclePin} steps through them. */
    public enum Pin { DANGER, LOOT, RETURN, TRADER, LOCKED, UNKNOWN }

    /** A chunk on a floor: where the waypoint stands. */
    public static final class Spot {
        private final int floor;
        private final GridPoint2 chunk;

        public Spot(int floor, GridPoint2 chunk) {
            this.floor = floor;
            this.chunk = new GridPoint2(chunk);
        }

        public int getFloor() { return floor; }
        public GridPoint2 getChunk() { return new GridPoint2(chunk); }

        public boolean is(int floor, GridPoint2 chunk) {
            return this.floor == floor && this.chunk.equals(chunk);
        }
    }

    /** Key for a tile pin: floor, chunk coordinates, and tile coordinates within the chunk. */
    public static final class TileSpot {
        public final int floor;
        public final GridPoint2 chunk;
        public final int tileX;
        public final int tileY;

        public TileSpot(int floor, GridPoint2 chunk, int tileX, int tileY) {
            this.floor = floor;
            this.chunk = new GridPoint2(chunk);
            this.tileX = tileX;
            this.tileY = tileY;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TileSpot that = (TileSpot) o;
            return floor == that.floor && tileX == that.tileX && tileY == that.tileY && chunk.equals(that.chunk);
        }

        @Override
        public int hashCode() {
            int result = floor;
            result = 31 * result + chunk.hashCode();
            result = 31 * result + tileX;
            result = 31 * result + tileY;
            return result;
        }
    }

    private static MapKnowledge instance;

    private final Set<GridPoint2> glimpsed = new HashSet<>();
    private final Set<GridPoint2> sighted = new HashSet<>();
    private final Map<Integer, Map<GridPoint2, Pin>> pins = new HashMap<>();
    private final Map<TileSpot, Pin> tilePins = new HashMap<>();
    private Spot waypoint;

    private MapKnowledge() {
        load();
        SaveManager.register(this);
    }

    public static MapKnowledge getInstance() {
        if (instance == null) {
            instance = new MapKnowledge();
        }
        return instance;
    }

    /**
     * The player has entered {@code chunk} on {@code floor}.
     *
     * <p>On the surface, a seamless biome lets the player see into the eight chunks around,
     * and every shelter beacon in range is now known. Arriving at the waypoint clears it.
     */
    public void recordArrival(int floor, GridPoint2 chunk, BiomeManager biomes, ShelterNetwork network) {
        if (chunk == null) return;
        boolean changed = false;
        if (waypoint != null && waypoint.is(floor, chunk)) {
            waypoint = null;
            changed = true;
        }
        if (floor == 1 && biomes != null) {
            Biome here = biomes.getBiome(chunk);
            if (here != null && here.isSeamless()) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        changed |= glimpsed.add(new GridPoint2(chunk.x + dx, chunk.y + dy));
                    }
                }
            }
            if (network != null) {
                for (BeaconPlanner.Beacon b : BeaconPlanner.visible(biomes, network, chunk.x, chunk.y,
                        WorldConstants.BEACON_RANGE_CHUNKS)) {
                    if (b.getKind() == BeaconPlanner.Kind.PILLAR) continue; // a seal site, not a shelter
                    changed |= sighted.add(b.getChunk());
                }
            }
        }
        if (changed) save();
    }

    /** Seen across a seamless border on the surface, without being entered. */
    public boolean isGlimpsed(GridPoint2 chunk) {
        return chunk != null && glimpsed.contains(chunk);
    }

    /** A shelter whose beacon has been on the horizon. */
    public boolean isSighted(GridPoint2 chunk) {
        return chunk != null && sighted.contains(chunk);
    }

    /** Every glimpsed surface chunk. */
    public Set<GridPoint2> getGlimpsed() {
        Set<GridPoint2> out = new HashSet<>();
        for (GridPoint2 c : glimpsed) out.add(new GridPoint2(c));
        return out;
    }

    /** Every sighted shelter chunk. */
    public Set<GridPoint2> getSighted() {
        Set<GridPoint2> out = new HashSet<>();
        for (GridPoint2 c : sighted) out.add(new GridPoint2(c));
        return out;
    }

    public Pin getPin(int floor, GridPoint2 chunk) {
        Map<GridPoint2, Pin> onFloor = pins.get(floor);
        return onFloor == null || chunk == null ? null : onFloor.get(chunk);
    }

    /** Every pin on one floor. */
    public Map<GridPoint2, Pin> getPins(int floor) {
        Map<GridPoint2, Pin> onFloor = pins.get(floor);
        return onFloor == null ? new HashMap<>() : new HashMap<>(onFloor);
    }

    /** Steps this chunk's pin to the next icon; after the last, the pin is removed. */
    public void cyclePin(int floor, GridPoint2 chunk) {
        if (chunk == null) return;
        Pin current = getPin(floor, chunk);
        Pin[] all = Pin.values();
        Pin next = current == null ? all[0] : (current.ordinal() + 1 < all.length ? all[current.ordinal() + 1] : null);
        Map<GridPoint2, Pin> onFloor = pins.computeIfAbsent(floor, f -> new HashMap<>());
        if (next == null) {
            onFloor.remove(chunk);
            if (onFloor.isEmpty()) pins.remove(floor);
        } else {
            onFloor.put(new GridPoint2(chunk), next);
        }
        save();
    }

    public Pin getTilePin(int floor, GridPoint2 chunk, int tileX, int tileY) {
        if (chunk == null) return null;
        return tilePins.get(new TileSpot(floor, chunk, tileX, tileY));
    }

    public Pin getTilePin(int floor, GridPoint2 chunk, GridPoint2 tile) {
        return tile == null ? null : getTilePin(floor, chunk, tile.x, tile.y);
    }

    public void setTilePin(int floor, GridPoint2 chunk, int tileX, int tileY, Pin pin) {
        if (chunk == null) return;
        TileSpot spot = new TileSpot(floor, chunk, tileX, tileY);
        if (pin == null) {
            tilePins.remove(spot);
        } else {
            tilePins.put(spot, pin);
        }
        save();
    }

    public void setTilePin(int floor, GridPoint2 chunk, GridPoint2 tile, Pin pin) {
        if (tile == null) return;
        setTilePin(floor, chunk, tile.x, tile.y, pin);
    }

    public void cycleTilePin(int floor, GridPoint2 chunk, int tileX, int tileY) {
        if (chunk == null) return;
        Pin current = getTilePin(floor, chunk, tileX, tileY);
        Pin[] all = Pin.values();
        Pin next = current == null ? all[0] : (current.ordinal() + 1 < all.length ? all[current.ordinal() + 1] : null);
        setTilePin(floor, chunk, tileX, tileY, next);
    }

    public void cycleTilePin(int floor, GridPoint2 chunk, GridPoint2 tile) {
        if (tile == null) return;
        cycleTilePin(floor, chunk, tile.x, tile.y);
    }

    public Map<GridPoint2, Pin> getTilePins(int floor, GridPoint2 chunk) {
        Map<GridPoint2, Pin> result = new HashMap<>();
        if (chunk == null) return result;
        for (Map.Entry<TileSpot, Pin> entry : tilePins.entrySet()) {
            TileSpot s = entry.getKey();
            if (s.floor == floor && s.chunk.equals(chunk)) {
                result.put(new GridPoint2(s.tileX, s.tileY), entry.getValue());
            }
        }
        return result;
    }

    public Pin getHighestPriorityTilePin(int floor, GridPoint2 chunk) {
        Map<GridPoint2, Pin> pins = getTilePins(floor, chunk);
        if (pins.isEmpty()) return null;
        Pin best = null;
        for (Pin p : pins.values()) {
            if (best == null || p.ordinal() < best.ordinal()) {
                best = p;
            }
        }
        return best;
    }

    public Spot getWaypoint() {
        return waypoint;
    }

    public void setWaypoint(int floor, GridPoint2 chunk) {
        if (chunk == null) return;
        waypoint = new Spot(floor, chunk);
        save();
    }

    public void clearWaypoint() {
        if (waypoint == null) return;
        waypoint = null;
        save();
    }

    /** A death or an upgrade laid out a new world: nothing learned about the old one applies. */
    public void forgetWorld() {
        reset();
        save();
    }

    // ---- Persistence ----

    public static class SaveData {
        public List<int[]> glimpsed = new ArrayList<>();
        public List<int[]> sighted = new ArrayList<>();
        /** floor, x, y, pin ordinal. */
        public List<int[]> pins = new ArrayList<>();
        /** floor, chunkX, chunkY, tileX, tileY, pin ordinal. */
        public List<int[]> tilePins = new ArrayList<>();
        /** floor, x, y; null for none. */
        public int[] waypoint;
    }

    private String getSaveFilePath() {
        return SaveManager.getInstance().getActiveSlotFilePath("map_knowledge.json");
    }

    private void save() {
        try {
            if (Gdx.files == null) return;
            SaveData data = new SaveData();
            for (GridPoint2 c : glimpsed) data.glimpsed.add(new int[]{c.x, c.y});
            for (GridPoint2 c : sighted) data.sighted.add(new int[]{c.x, c.y});
            for (Map.Entry<Integer, Map<GridPoint2, Pin>> floor : pins.entrySet()) {
                for (Map.Entry<GridPoint2, Pin> p : floor.getValue().entrySet()) {
                    data.pins.add(new int[]{floor.getKey(), p.getKey().x, p.getKey().y, p.getValue().ordinal()});
                }
            }
            for (Map.Entry<TileSpot, Pin> p : tilePins.entrySet()) {
                TileSpot s = p.getKey();
                data.tilePins.add(new int[]{s.floor, s.chunk.x, s.chunk.y, s.tileX, s.tileY, p.getValue().ordinal()});
            }
            data.waypoint = waypoint == null ? null
                    : new int[]{waypoint.floor, waypoint.chunk.x, waypoint.chunk.y};
            SaveManager.getInstance().atomicWriteJson(Gdx.files.local(getSaveFilePath()), data);
        } catch (Exception e) {
            if (Gdx.app != null) Gdx.app.error("MapKnowledge", "Failed to save: " + e.getMessage());
        }
    }

    private void load() {
        reset();
        try {
            if (Gdx.files == null) return;
            FileHandle file = Gdx.files.local(getSaveFilePath());
            if (!file.exists()) return;
            Json json = new Json();
            json.setUsePrototypes(false);
            SaveData data = json.fromJson(SaveData.class, file.readString());
            if (data == null) return;
            readPoints(data.glimpsed, glimpsed);
            readPoints(data.sighted, sighted);
            if (data.pins != null) {
                Pin[] all = Pin.values();
                for (int[] p : data.pins) {
                    if (p == null || p.length != 4 || p[3] < 0 || p[3] >= all.length) continue;
                    pins.computeIfAbsent(p[0], f -> new HashMap<>()).put(new GridPoint2(p[1], p[2]), all[p[3]]);
                }
            }
            if (data.tilePins != null) {
                Pin[] all = Pin.values();
                for (int[] p : data.tilePins) {
                    if (p == null || p.length != 6 || p[5] < 0 || p[5] >= all.length) continue;
                    tilePins.put(new TileSpot(p[0], new GridPoint2(p[1], p[2]), p[3], p[4]), all[p[5]]);
                }
            }
            if (data.waypoint != null && data.waypoint.length == 3) {
                waypoint = new Spot(data.waypoint[0], new GridPoint2(data.waypoint[1], data.waypoint[2]));
            }
        } catch (Exception e) {
            if (Gdx.app != null) Gdx.app.error("MapKnowledge", "Failed to load: " + e.getMessage());
        }
    }

    private static void readPoints(List<int[]> from, Set<GridPoint2> into) {
        if (from == null) return;
        for (int[] c : from) {
            if (c != null && c.length == 2) into.add(new GridPoint2(c[0], c[1]));
        }
    }

    private void reset() {
        glimpsed.clear();
        sighted.clear();
        pins.clear();
        tilePins.clear();
        waypoint = null;
    }

    @Override
    public void reloadForActiveSlot() {
        load();
    }

    @Override
    public void resetForNewGame() {
        reset();
        save();
    }
}
