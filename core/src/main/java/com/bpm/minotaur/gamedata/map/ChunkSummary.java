package com.bpm.minotaur.gamedata.map;

import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Ladder;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the map can tell about one saved chunk: only what stands on tiles the player has seen.
 * See docs/DEsign/Requirements_ Expedition Map.md, sections 3 and 6.
 */
public final class ChunkSummary {

    /** A fallen hero's bones, as the map remembers them. */
    public static final class Grave {
        private final String name;
        private final String epitaph;
        private final boolean awakened;
        private final boolean defeated;

        Grave(String name, String epitaph, boolean awakened, boolean defeated) {
            this.name = name;
            this.epitaph = epitaph;
            this.awakened = awakened;
            this.defeated = defeated;
        }

        public String getName() { return name; }
        public String getEpitaph() { return epitaph; }
        public boolean isAwakened() { return awakened; }
        public boolean isDefeated() { return defeated; }
    }

    private int loot;
    private boolean returnPortal;
    private boolean upLadder;
    private boolean downLadder;
    private final List<Grave> graves = new ArrayList<>();
    private final List<String> stations = new ArrayList<>();

    private ChunkSummary() {
    }

    /** Summarises a chunk save; null (never saved) knows nothing. */
    public static ChunkSummary of(ChunkData data) {
        ChunkSummary s = new ChunkSummary();
        if (data == null) return s;
        if (data.items != null) {
            for (ChunkData.ItemData item : data.items) {
                if (item == null || !seen(data, item.x, item.y)) continue;
                if (item.type == Item.ItemType.BIOME_RETURN_PORTAL) {
                    s.returnPortal = true;
                } else {
                    s.loot++;
                }
            }
        }
        if (data.scenery != null) {
            for (ChunkData.SceneryData sc : data.scenery) {
                if (sc == null || sc.bonesData == null || !seen(data, sc.x, sc.y)) continue;
                s.graves.add(new Grave(sc.bonesData.playerName, sc.bonesData.epitaph,
                        sc.bonesData.awakened, sc.bonesData.defeated));
            }
        }
        if (data.ladders != null) {
            for (ChunkData.LadderData ladder : data.ladders) {
                if (ladder == null || !seen(data, ladder.x, ladder.y)) continue;
                if (ladder.type == Ladder.LadderType.UP) s.upLadder = true;
                else s.downLadder = true;
            }
        }
        if (data.stationSlots != null && data.hasShelter()) {
            for (ChunkData.StationSlotData slot : data.stationSlots) {
                if (slot != null && slot.station != null && !s.stations.contains(slot.station)) s.stations.add(slot.station);
            }
        }
        return s;
    }

    private static boolean seen(ChunkData data, int x, int y) {
        byte[][] state = data.explorationState;
        return state != null && y >= 0 && y < state.length && x >= 0 && x < state[y].length
                && state[y][x] == Maze.VISIBILITY_SEEN;
    }

    /** Items on seen tiles, return portals aside. */
    public int getLoot() { return loot; }
    public boolean hasReturnPortal() { return returnPortal; }
    public boolean hasUpLadder() { return upLadder; }
    public boolean hasDownLadder() { return downLadder; }
    public List<Grave> getGraves() { return Collections.unmodifiableList(graves); }
    /** The shelter's stations, as {@code ShelterAltar.Station} names. */
    public List<String> getStations() { return Collections.unmodifiableList(stations); }
}
