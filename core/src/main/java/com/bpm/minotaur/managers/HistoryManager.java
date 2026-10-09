package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistorySaveData;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.PlayerDeed;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Owns the world's history at runtime: ticks a season on every shelter sleep, records what the
 * player does to it, and saves it as a replay (ADR 0004). Pure Java, so the whole lifecycle is
 * testable headless.
 */
public final class HistoryManager {

    private final HistoryWorld world;
    private final DoctrineCatalog catalog;
    private final List<PlayerDeed> deeds = new ArrayList<>();
    private final Set<Integer> unlocked = new LinkedHashSet<>();

    private HistoryManager(HistoryWorld world, DoctrineCatalog catalog) {
        this.world = world;
        this.catalog = catalog;
    }

    /** A new world's history: prehistory only. */
    public static HistoryManager create(long worldSeed, DoctrineCatalog catalog) {
        return new HistoryManager(HistorySimulator.prehistory(worldSeed, catalog), catalog);
    }

    /**
     * Rebuilds a saved history by replaying its seasons and deeds over prehistory.
     * {@code fallbackSeed} seeds a save written before the history existed.
     */
    public static HistoryManager fromSave(long fallbackSeed, HistorySaveData save, DoctrineCatalog catalog) {
        if (save == null) return create(fallbackSeed, catalog);
        HistoryManager m = create(save.seed != null ? save.seed : fallbackSeed, catalog);
        for (int season = 0; season <= save.liveSeasons; season++) {
            for (PlayerDeed d : save.deeds) {
                if (d.liveSeason == season) m.apply(d);
            }
            if (season < save.liveSeasons) HistorySimulator.tickSeason(m.world, catalog);
        }
        if (save.unlockedEvents != null) m.unlocked.addAll(save.unlockedEvents);
        return m;
    }

    public HistoryWorld world() {
        return world;
    }

    /** Advances one season. Returns what happened in it, oldest first. */
    public List<HistoryEvent> onSleep() {
        int from = world.events().size();
        HistorySimulator.tickSeason(world, catalog);
        return new ArrayList<>(world.events().subList(from, world.events().size()));
    }

    /** The player killed a named figure of the history. */
    public void recordKill(int figureId) {
        apply(new PlayerDeed(PlayerDeed.Kind.SLEW_FIGURE, figureId, world.liveSeasons()));
    }

    /** An expedition ended in death; {@code killerHouseId} is -1 when no house did it. */
    public void recordSeekerFell(int killerHouseId) {
        apply(new PlayerDeed(PlayerDeed.Kind.SEEKER_FELL, killerHouseId, world.liveSeasons()));
    }

    public void unlock(int eventId) {
        unlocked.add(eventId);
    }

    public boolean isUnlocked(int eventId) {
        return unlocked.contains(eventId);
    }

    public Set<Integer> unlockedEvents() {
        return java.util.Collections.unmodifiableSet(unlocked);
    }

    public HistorySaveData toSave() {
        HistorySaveData save = new HistorySaveData();
        save.seed = world.seed;
        save.liveSeasons = world.liveSeasons();
        save.deeds = new ArrayList<>(deeds);
        save.unlockedEvents = new ArrayList<>(unlocked);
        return save;
    }

    private void apply(PlayerDeed deed) {
        deeds.add(deed);
        HistorySimulator.applyDeed(world, deed);
    }
}
