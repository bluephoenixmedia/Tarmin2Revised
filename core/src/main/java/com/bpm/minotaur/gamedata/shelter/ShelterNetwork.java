package com.bpm.minotaur.gamedata.shelter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.generation.ShelterRoads;
import com.bpm.minotaur.managers.SaveManager;
import com.bpm.minotaur.managers.SlotScopedState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The shelters the player has claimed, where they last rested, and the seals they hold.
 *
 * <p>Every claimed shelter is a full shelter: the home shelter in the maze is
 * always claimed, and the last shelter rested in is where the player wakes after
 * a death. Seals are permanent; claimed shelters survive a death only as far as
 * {@link ShelterMemory} allows. See docs/DEsign/Requirements_ Shelter Roads.md.
 */
public final class ShelterNetwork implements SlotScopedState {

    private static final GridPoint2 HOME_CHUNK = new GridPoint2(0, 0);
    private static ShelterNetwork instance;

    private final Set<GridPoint2> claimed = new HashSet<>();
    /** Null while the last rest was the home shelter (or there has been none). */
    private GridPoint2 restChunk;
    private final Set<Integer> seals = new TreeSet<>();

    private ShelterNetwork() {
        load();
        SaveManager.register(this);
    }

    public static ShelterNetwork getInstance() {
        if (instance == null) {
            instance = new ShelterNetwork();
        }
        return instance;
    }

    /** True for the home shelter and for every shelter the player has lit. */
    public boolean isClaimed(GridPoint2 chunk) {
        return chunk != null && (HOME_CHUNK.equals(chunk) || claimed.contains(chunk));
    }

    /** Every lit shelter but home. */
    public Set<GridPoint2> getClaimed() {
        Set<GridPoint2> out = new HashSet<>();
        for (GridPoint2 c : claimed) out.add(new GridPoint2(c));
        return out;
    }

    public void claim(GridPoint2 chunk) {
        if (chunk != null && !HOME_CHUNK.equals(chunk) && claimed.add(new GridPoint2(chunk))) {
            save();
        }
    }

    /** Claims every shelter on one road (debug). */
    public void claimRoad(ShelterRoads.Road road) {
        if (road == null) return;
        for (GridPoint2 c : road.getShelters()) claimed.add(new GridPoint2(c));
        save();
    }

    /** Records a rest in a shelter bed: this shelter is now where the player wakes. */
    public void recordRest(GridPoint2 chunk) {
        restChunk = (chunk == null || HOME_CHUNK.equals(chunk)) ? null : new GridPoint2(chunk);
        save();
    }

    /** The chunk of the last rest, or null for the home shelter. */
    public GridPoint2 getRestChunk() {
        return restChunk == null ? null : new GridPoint2(restChunk);
    }

    public boolean hasSeal(int road) {
        return seals.contains(road);
    }

    public void awardSeal(int road) {
        if (seals.add(road)) save();
    }

    public int getSealCount() {
        return seals.size();
    }

    public Set<Integer> getSeals() {
        return new TreeSet<>(seals);
    }

    /**
     * Carries the road progress of a dying world into the new one.
     *
     * <p>Claimed shelters are counted road by road in the old layout, cut back by
     * {@link ShelterMemory}, and re-claimed from the maze outward in the new
     * layout. Off-road shelters are forgotten.
     *
     * @return the chunk to wake in, or null for the home shelter
     */
    public GridPoint2 carryOverDeath(ShelterRoads oldRoads, ShelterRoads newRoads) {
        List<Set<Integer>> places = new ArrayList<>();
        for (int r = 0; r < ShelterRoads.ROAD_COUNT; r++) places.add(new TreeSet<>());
        int restRoad = ShelterMemory.HOME;
        int restIndex = 0;
        if (oldRoads != null) {
            for (ShelterRoads.Road road : oldRoads.getRoads()) {
                List<GridPoint2> stops = road.getShelters();
                for (int i = 0; i < stops.size(); i++) {
                    if (claimed.contains(stops.get(i))) places.get(road.getIndex()).add(i);
                    if (stops.get(i).equals(restChunk)) {
                        restRoad = road.getIndex();
                        restIndex = i;
                    }
                }
            }
        }
        ShelterMemory.Result result = ShelterMemory.afterDeath(places, restRoad, restIndex, seals);

        claimed.clear();
        restChunk = null;
        GridPoint2 wake = null;
        if (newRoads != null) {
            for (ShelterRoads.Road road : newRoads.getRoads()) {
                List<GridPoint2> stops = road.getShelters();
                if (stops.isEmpty()) continue;
                int r = road.getIndex();
                for (int i = 0; i < stops.size(); i++) {
                    if (result.isWholeRoad(r) || result.getClaimed(r).contains(i)) {
                        claimed.add(new GridPoint2(stops.get(i)));
                    }
                }
                if (r == result.getRespawnRoad()) {
                    // The new road may be shorter: wake at the furthest claimed place it still has.
                    int at = Math.min(result.getRespawnIndex(), stops.size() - 1);
                    while (at >= 0 && !claimed.contains(stops.get(at))) at--;
                    if (at >= 0) wake = new GridPoint2(stops.get(at));
                }
            }
        }
        restChunk = wake;
        save();
        return wake == null ? null : new GridPoint2(wake);
    }

    // ---- Persistence ----

    public static class SaveData {
        public List<int[]> claimed = new ArrayList<>();
        public int[] restChunk;
        public List<Integer> seals = new ArrayList<>();
    }

    private String getSaveFilePath() {
        return SaveManager.getInstance().getActiveSlotFilePath("shelter_network.json");
    }

    private void save() {
        try {
            if (Gdx.files == null) return;
            SaveData data = new SaveData();
            for (GridPoint2 c : claimed) data.claimed.add(new int[]{c.x, c.y});
            data.restChunk = restChunk == null ? null : new int[]{restChunk.x, restChunk.y};
            data.seals.addAll(seals);
            SaveManager.getInstance().atomicWriteJson(Gdx.files.local(getSaveFilePath()), data);
        } catch (Exception e) {
            if (Gdx.app != null) Gdx.app.error("ShelterNetwork", "Failed to save: " + e.getMessage());
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
            if (data.claimed != null) {
                for (int[] c : data.claimed) {
                    if (c != null && c.length == 2) claimed.add(new GridPoint2(c[0], c[1]));
                }
            }
            if (data.restChunk != null && data.restChunk.length == 2) {
                restChunk = new GridPoint2(data.restChunk[0], data.restChunk[1]);
            }
            if (data.seals != null) seals.addAll(data.seals);
        } catch (Exception e) {
            if (Gdx.app != null) Gdx.app.error("ShelterNetwork", "Failed to load: " + e.getMessage());
        }
    }

    private void reset() {
        claimed.clear();
        restChunk = null;
        seals.clear();
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
