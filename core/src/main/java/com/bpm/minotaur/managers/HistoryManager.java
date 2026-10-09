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
    private long warClock;
    private final java.util.Map<Integer, Integer> beastHp = new java.util.HashMap<>();
    private com.bpm.minotaur.gamedata.history.beast.BeastTracks.Hunt hunt;
    private final java.util.Set<String> townsFound = new java.util.LinkedHashSet<>();
    /** Town key to the settlement it is, or -1 for a made-up town (ADR 0005). */
    private final java.util.Map<String, Integer> townSettlements = new java.util.LinkedHashMap<>();
    /** Town key to the figure id of the exile it took in. */
    private final java.util.Map<String, Integer> townExiles = new java.util.LinkedHashMap<>();
    private final com.bpm.minotaur.gamedata.history.town.Standing standing = new com.bpm.minotaur.gamedata.history.town.Standing();
    private final java.util.Map<String, com.bpm.minotaur.gamedata.history.town.Quest> quests = new java.util.LinkedHashMap<>();

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
        m.warClock = save.warClock;
        if (save.beastHpIds != null && save.beastHpValues != null) {
            for (int i = 0; i < Math.min(save.beastHpIds.size(), save.beastHpValues.size()); i++) {
                m.beastHp.put(save.beastHpIds.get(i), save.beastHpValues.get(i));
            }
        }
        m.hunt = save.hunt;
        if (save.townsFound != null) m.townsFound.addAll(save.townsFound);
        if (save.townSettlementKeys != null && save.townSettlementIds != null) {
            for (int i = 0; i < Math.min(save.townSettlementKeys.size(), save.townSettlementIds.size()); i++) {
                m.townSettlements.put(save.townSettlementKeys.get(i), save.townSettlementIds.get(i));
            }
        }
        if (save.townExiles != null) {
            for (String entry : save.townExiles) {
                int eq = entry.lastIndexOf('=');
                if (eq > 0) m.townExiles.put(entry.substring(0, eq), Integer.parseInt(entry.substring(eq + 1)));
            }
        }
        if (save.standingTowns != null) {
            for (int i = 0; i < Math.min(save.standingTowns.size(), save.standingTownValues.size()); i++) {
                m.standing.towns().put(save.standingTowns.get(i), save.standingTownValues.get(i));
            }
        }
        if (save.standingAllegiances != null) {
            for (int i = 0; i < Math.min(save.standingAllegiances.size(), save.standingAllegianceValues.size()); i++) {
                try {
                    m.standing.allegiances().put(com.bpm.minotaur.gamedata.history.town.Allegiance.valueOf(
                            save.standingAllegiances.get(i)), save.standingAllegianceValues.get(i));
                } catch (IllegalArgumentException ignored) {
                    // A power that no longer exists keeps no grudge.
                }
            }
        }
        if (save.quests != null) {
            for (com.bpm.minotaur.gamedata.history.town.Quest q : save.quests) m.quests.put(q.townKey, q);
        }
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

    /**
     * Reads a found fragment: unlocks one event of its kind the player has not learned, with the
     * event that caused it. Returns that event, or null once the kind has nothing left to tell.
     * The choice depends only on the history and what is already unlocked, so it survives a load.
     */
    public HistoryEvent readFragment(com.bpm.minotaur.gamedata.history.FragmentKind kind) {
        List<HistoryEvent> candidates = new ArrayList<>();
        for (HistoryEvent e : world.events()) {
            if (kind.tells(e.type) && !unlocked.contains(e.id)) candidates.add(e);
        }
        if (candidates.isEmpty()) return null;
        // A plot still in motion is what turns up first: a town bought and not yet turned (T4.6).
        for (HistoryEvent e : candidates) {
            com.bpm.minotaur.gamedata.history.town.Settlement s = e.type == com.bpm.minotaur.gamedata.history.EventType.TOWN_SUBORNED ? world.settlement(e.detail) : null;
            if (s != null && !s.betrayed && s.subornedBy == e.houseA) {
                unlocked.add(e.id);
                return e;
            }
        }
        java.util.Random rng = new java.util.Random(world.seed ^ (unlocked.size() * 0x9E3779B97F4A7C15L) ^ kind.ordinal());
        HistoryEvent e = candidates.get(rng.nextInt(candidates.size()));
        unlocked.add(e.id);
        if (e.causeEventId >= 0) unlocked.add(e.causeEventId);
        return e;
    }

    /**
     * Tells the history the Doom Clock's stage. Each stage the run has never reached before is
     * chronicled once, as Tarmin-Zul's ascendancy (plan D42, T1.14).
     */
    public void noteDoomStage(int stage) {
        for (int s = highestDoomStage() + 1; s <= stage; s++) {
            apply(new PlayerDeed(PlayerDeed.Kind.DOOM_STAGE, s, world.liveSeasons()));
        }
    }

    private int highestDoomStage() {
        int highest = 1;
        for (PlayerDeed d : deeds) {
            if (d.kind == PlayerDeed.Kind.DOOM_STAGE) highest = Math.max(highest, d.target);
        }
        return highest;
    }

    /**
     * A battle the player stood in has broken: {@code winner} held the field and {@code loser}
     * routed. It enters the history like any battle of the war (plan T2.6).
     */
    public void recordBattle(int winner, int loser) {
        PlayerDeed d = new PlayerDeed(PlayerDeed.Kind.BATTLE_WITNESSED, winner, world.liveSeasons());
        d.other = loser;
        apply(d);
    }

    /** The player bought a beast's peace: it hunts them no more, and the history says so. */
    public void recordMegabeastPacified(int beastId) {
        apply(new PlayerDeed(PlayerDeed.Kind.PACIFIED_MEGABEAST, beastId, world.liveSeasons()));
        if (hunt != null && hunt.beastId == beastId) hunt = null;
    }

    /** The player struck a beast they had bought off: the bargain is void, until another offering. */
    public void breakMegabeastPeace(int beastId) {
        com.bpm.minotaur.gamedata.history.Megabeast b = world.megabeast(beastId);
        if (b == null || !b.isPacified()) return;
        apply(new PlayerDeed(PlayerDeed.Kind.BROKE_BEAST_PEACE, beastId, world.liveSeasons()));
    }

    /** The player killed a megabeast: it is chronicled, and it never comes back. */
    public void recordMegabeastSlain(int beastId) {
        apply(new PlayerDeed(PlayerDeed.Kind.SLEW_MEGABEAST, beastId, world.liveSeasons()));
        beastHp.remove(beastId);
        if (hunt != null && hunt.beastId == beastId) hunt = null;
    }

    /** Hit points a megabeast has left, or {@code whole} if it has never been hurt. */
    public int beastHp(int beastId, int whole) {
        Integer hp = beastHp.get(beastId);
        return hp == null ? whole : hp;
    }

    public void setBeastHp(int beastId, int hp) {
        beastHp.put(beastId, hp);
    }

    /** A megabeast follows the player to {@code chunk} at {@code level}, a few turns behind (plan D34). */
    public void startHunt(int beastId, com.badlogic.gdx.math.GridPoint2 chunk, int level) {
        com.bpm.minotaur.gamedata.history.beast.BeastTracks.Hunt h = new com.bpm.minotaur.gamedata.history.beast.BeastTracks.Hunt();
        h.beastId = beastId;
        h.chunkX = chunk.x;
        h.chunkY = chunk.y;
        h.level = level;
        h.readyAt = warClock + com.bpm.minotaur.gamedata.history.beast.BeastTracks.HUNT_DELAY;
        h.until = h.readyAt + com.bpm.minotaur.gamedata.history.beast.BeastTracks.HUNT_LENGTH;
        hunt = h;
    }

    public com.bpm.minotaur.gamedata.history.beast.BeastTracks.Hunt hunt() {
        return hunt;
    }

    /**
     * The town at {@code key}: the same town, with the same folk, every time it is asked for. The
     * k-th town the player finds has taken in the k-th of the history's exiles, if they live
     * (plan D37); a town not yet found is asked about as the next one would be.
     */
    public com.bpm.minotaur.gamedata.history.town.Town town(String key) {
        com.bpm.minotaur.gamedata.history.town.Settlement settlement = bindSettlement(key);
        com.bpm.minotaur.gamedata.history.town.Town town = settlement != null
                ? com.bpm.minotaur.gamedata.history.town.Town.of(world, settlement, key)
                : com.bpm.minotaur.gamedata.history.town.Town.of(world.seed, key);
        Integer id = townExiles.get(key);
        com.bpm.minotaur.gamedata.history.Figure exile = id != null ? world.figure(id) : null;
        // An exile who has died, or gone home to take their house's seat, is no longer here.
        return world.isExile(exile) ? town.withExile(exile.id, exile.name) : town;
    }

    /** The settlement the town at {@code key} is, or null for a made-up town (ADR 0005). */
    public com.bpm.minotaur.gamedata.history.town.Settlement settlementOf(String key) {
        Integer id = townSettlements.get(key);
        return id != null ? world.settlement(id) : null;
    }

    /**
     * The first time the game asks for the town at {@code key} it becomes the first settlement of
     * the history not yet a town, and stays so (saved): a quest that names a town far off names the
     * town the player will walk into. Once all are spoken for, the town is made up (-1).
     */
    private com.bpm.minotaur.gamedata.history.town.Settlement bindSettlement(String key) {
        Integer id = townSettlements.get(key);
        if (id == null) {
            id = -1;
            for (com.bpm.minotaur.gamedata.history.town.Settlement s : world.settlements()) {
                if (!townSettlements.containsValue(s.id)) {
                    id = s.id;
                    break;
                }
            }
            townSettlements.put(key, id);
        }
        return world.settlement(id);
    }

    /**
     * The first time the player stands in the town at {@code key}, it has taken in one of the
     * history's exiles not already living in another, if there is one. Saved, so an exile never
     * moves; a town with none may take one in later, as houses fall.
     */
    public void seatExile(String key) {
        if (townExiles.containsKey(key)) return;
        for (com.bpm.minotaur.gamedata.history.Figure f : world.exiles()) {
            if (!townExiles.containsValue(f.id)) {
                townExiles.put(key, f.id);
                return;
            }
        }
    }

    public com.bpm.minotaur.gamedata.history.town.Standing standing() {
        return standing;
    }

    /** The task a town gave the player, or null if they have taken none there. */
    public com.bpm.minotaur.gamedata.history.town.Quest quest(String townKey) {
        return quests.get(townKey);
    }

    public java.util.Collection<com.bpm.minotaur.gamedata.history.town.Quest> quests() {
        return java.util.Collections.unmodifiableCollection(quests.values());
    }

    public void acceptQuest(com.bpm.minotaur.gamedata.history.town.Quest q) {
        q.accepted = true;
        quests.put(q.townKey, q);
    }

    /** A town's work is done: it enters the chronicle (plan D39). */
    public void completeQuest(com.bpm.minotaur.gamedata.history.town.Quest q, com.bpm.minotaur.gamedata.history.town.Town giver) {
        if (q.done) return;
        q.done = true;
        quests.put(q.townKey, q);
        PlayerDeed d = new PlayerDeed(PlayerDeed.Kind.QUEST_DONE, q.kind.ordinal(), world.liveSeasons());
        d.other = q.kind == com.bpm.minotaur.gamedata.history.town.Quest.Kind.SLAY_BEAST ? q.beastId : q.houseId;
        d.note = giver.name;
        apply(d);
    }

    /**
     * The player stands on a war's front: a message being carried through that war is carried
     * through (plan D39). Returns what is said, or null if nothing changed.
     */
    public String onFront(com.bpm.minotaur.gamedata.history.war.Front front) {
        if (front == null) return null;
        String said = null;
        for (com.bpm.minotaur.gamedata.history.town.Quest q : quests.values()) {
            if (q.accepted && !q.done && q.kind == com.bpm.minotaur.gamedata.history.town.Quest.Kind.CARRY_MESSAGE
                    && q.warId == front.warId && !q.crossedFront) {
                q.crossedFront = true;
                said = "You carry the message through the lines.";
            }
        }
        return said;
    }

    /** Marks a town found; true the first time. */
    public boolean findTown(String key) {
        return townsFound.add(key);
    }

    public java.util.Set<String> townsFound() {
        return java.util.Collections.unmodifiableSet(townsFound);
    }

    /** One player turn passes for the wars (plan D22): fronts move with this clock. */
    public void tickWarClock() {
        warClock++;
    }

    public long warClock() {
        return warClock;
    }

    /** Where every war is being fought now, given where the houses sit in this world. */
    public List<com.bpm.minotaur.gamedata.history.war.Front> fronts(com.bpm.minotaur.gamedata.history.war.SeatMap seats) {
        return com.bpm.minotaur.gamedata.history.war.FrontPlanner.fronts(world, seats, warClock);
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
        save.warClock = warClock;
        for (java.util.Map.Entry<Integer, Integer> e : beastHp.entrySet()) {
            save.beastHpIds.add(e.getKey());
            save.beastHpValues.add(e.getValue());
        }
        save.hunt = hunt;
        save.townsFound = new ArrayList<>(townsFound);
        save.townSettlementKeys = new ArrayList<>(townSettlements.keySet());
        save.townSettlementIds = new ArrayList<>(townSettlements.values());
        save.townExiles = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : townExiles.entrySet()) save.townExiles.add(e.getKey() + "=" + e.getValue());
        for (java.util.Map.Entry<String, Integer> e : standing.towns().entrySet()) {
            save.standingTowns.add(e.getKey());
            save.standingTownValues.add(e.getValue());
        }
        for (java.util.Map.Entry<com.bpm.minotaur.gamedata.history.town.Allegiance, Integer> e : standing.allegiances().entrySet()) {
            save.standingAllegiances.add(e.getKey().name());
            save.standingAllegianceValues.add(e.getValue());
        }
        save.quests = new ArrayList<>(quests.values());
        return save;
    }

    private void apply(PlayerDeed deed) {
        deeds.add(deed);
        HistorySimulator.applyDeed(world, deed);
    }
}
