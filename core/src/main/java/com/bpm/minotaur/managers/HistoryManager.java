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
    private com.bpm.minotaur.gamedata.history.war.EncounterLedger encounters = new com.bpm.minotaur.gamedata.history.war.EncounterLedger();
    private final com.bpm.minotaur.gamedata.history.favour.Favour favour = new com.bpm.minotaur.gamedata.history.favour.Favour();
    private com.bpm.minotaur.gamedata.history.favour.Oath oath = new com.bpm.minotaur.gamedata.history.favour.Oath();

    private HistoryManager(HistoryWorld world, DoctrineCatalog catalog) {
        this.world = world;
        this.catalog = catalog;
    }

    /** A new world's history: prehistory only. */
    public static HistoryManager create(long worldSeed, DoctrineCatalog catalog) {
        HistoryManager m = past(worldSeed, catalog);
        // A new game opens on a war, so there is one to hear from the first step (Living War W6).
        PlayerDeed opening = HistorySimulator.openingWar(m.world);
        if (opening != null) m.apply(opening);
        return m;
    }

    /** Prehistory and nothing more: what a save's deeds replay over. */
    private static HistoryManager past(long worldSeed, DoctrineCatalog catalog) {
        return new HistoryManager(HistorySimulator.prehistory(worldSeed, catalog), catalog);
    }

    /**
     * Rebuilds a saved history by replaying its seasons and deeds over prehistory.
     * {@code fallbackSeed} seeds a save written before the history existed.
     */
    public static HistoryManager fromSave(long fallbackSeed, HistorySaveData save, DoctrineCatalog catalog) {
        if (save == null) return create(fallbackSeed, catalog);
        HistoryManager m = past(save.seed != null ? save.seed : fallbackSeed, catalog);
        for (int season = 0; season <= save.liveSeasons; season++) {
            for (PlayerDeed d : save.deeds) {
                if (d.liveSeason == season) m.replay(d);
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
        if (save.encounters != null) m.encounters = save.encounters;
        if (save.favourHouses != null && save.favourValues != null) {
            for (int i = 0; i < Math.min(save.favourHouses.size(), save.favourValues.size()); i++) {
                m.favour.set(save.favourHouses.get(i), save.favourValues.get(i));
            }
        }
        if (save.oath != null) m.oath = save.oath;
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
        encounters.newExpedition(true);
        int from = world.events().size();
        HistorySimulator.tickSeason(world, catalog);
        return new ArrayList<>(world.events().subList(from, world.events().size()));
    }

    /** The player killed a named figure of the history. */
    public void recordKill(int figureId) {
        apply(new PlayerDeed(PlayerDeed.Kind.SLEW_FIGURE, figureId, world.liveSeasons()));
    }

    /**
     * An expedition ended in death; {@code killerHouseId} is -1 when no house did it, and
     * {@code killer} names what did ("a Kobold"), or is null when nothing did. The name rides
     * on the deed's note and is not replayed into the history (#280).
     */
    public void recordSeekerFell(int killerHouseId, String killer) {
        recordSeekerFell(com.bpm.minotaur.gamedata.history.SeekerDeath.by(killer, null, killerHouseId));
    }

    /**
     * An expedition ended in death, with all the history knows of it: its killer may become a
     * Nemesis (#283). Returns the Nemesis that took the life, or null when none did.
     */
    public com.bpm.minotaur.gamedata.history.Nemesis recordSeekerFell(com.bpm.minotaur.gamedata.history.SeekerDeath death) {
        encounters.newExpedition(false);
        java.util.Map<Integer, Integer> before = new java.util.HashMap<>();
        for (com.bpm.minotaur.gamedata.history.Nemesis n : world.nemeses()) before.put(n.id, n.tally());
        apply(death.deed(world.liveSeasons()));
        for (com.bpm.minotaur.gamedata.history.Nemesis n : world.nemeses()) {
            if (n.tally() > before.getOrDefault(n.id, 0)) return n;
        }
        return null;
    }

    /**
     * The deeds of the life that last ended (#285): those after the death before it, through its
     * own death. Empty before any death.
     */
    public List<PlayerDeed> lastLifeDeeds() {
        int end = -1;
        for (int i = deeds.size() - 1; i >= 0; i--) {
            if (deeds.get(i).kind == PlayerDeed.Kind.SEEKER_FELL) {
                end = i;
                break;
            }
        }
        if (end < 0) return new ArrayList<>();
        int start = end - 1;
        while (start >= 0 && deeds.get(start).kind != PlayerDeed.Kind.SEEKER_FELL) start--;
        return new ArrayList<>(deeds.subList(start + 1, end + 1));
    }

    /** The Bridge's Warden fell, and the Bridge with it (#290). */
    public void recordWardenFell() {
        apply(new PlayerDeed(PlayerDeed.Kind.WARDEN_FELL, -1, world.liveSeasons()));
    }

    /** Tarmin crossed the Bridge (#276, #290). */
    public void recordCrossing() {
        apply(new PlayerDeed(PlayerDeed.Kind.TARMIN_CROSSED, -1, world.liveSeasons()));
    }

    /** An old god is spent holding Tarmin back: its shrine is dark for good (#287). */
    public void recordGodSpent(com.bpm.minotaur.gamedata.history.OldGod god) {
        PlayerDeed deed = new PlayerDeed(PlayerDeed.Kind.GOD_SPENT, -1, world.liveSeasons());
        deed.note = god.name();
        apply(deed);
    }

    /** The player slew a Nemesis: it is placed no more (#283). */
    public void recordNemesisSlain(int nemesisId) {
        apply(new PlayerDeed(PlayerDeed.Kind.SLEW_NEMESIS, nemesisId, world.liveSeasons()));
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

    /**
     * A seal lord was killed in its court, before the seeker's eyes or not, by house
     * {@code killerHouseId} or by no house (-1): it dies in the history, and its house crowns an heir.
     */
    public void recordLordSlainInCourt(int figureId, int killerHouseId) {
        com.bpm.minotaur.gamedata.history.Figure f = world.figure(figureId);
        if (f == null || !f.isAlive()) return;
        PlayerDeed d = new PlayerDeed(PlayerDeed.Kind.LORD_SLAIN_IN_COURT, figureId, world.liveSeasons());
        d.other = killerHouseId;
        apply(d);
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

    /**
     * The seeker died: a megabeast hunting them loses the scent (#289). Its hunt named a chunk of
     * the maze the Shifting has just remade; the beast itself endures and keeps to its lair.
     */
    public void endHunt() {
        hunt = null;
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

    /**
     * The newest news a herald cries (Living War W29): the latest matter of state the player has not
     * yet heard, now learned. Null when they have heard it all.
     */
    public HistoryEvent freshNews() {
        for (int i = world.events().size() - 1; i >= 0; i--) {
            HistoryEvent e = world.events().get(i);
            if (com.bpm.minotaur.gamedata.history.FragmentKind.PROCLAMATION.tells(e.type) && !unlocked.contains(e.id)) {
                unlocked.add(e.id);
                return e;
            }
        }
        return null;
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
        // The Maze does not wait for the seeker to sleep (plan K1): every so often a season passes
        // mid-expedition, and a house may break while they are still underground. A season is a
        // pure function of the history, so the save replays it however it was set off.
        if (warClock % SEASON_TURNS == 0) HistorySimulator.tickSeason(world, catalog);
    }

    /** Turns of the war clock to a season of the Maze, when the seeker does not sleep. */
    public static final int SEASON_TURNS = 500;

    public long warClock() {
        return warClock;
    }

    /** Each house's regard for the player (Living War W18). */
    public com.bpm.minotaur.gamedata.history.favour.Favour favour() {
        return favour;
    }

    /** The player's oath (W20). */
    public com.bpm.minotaur.gamedata.history.favour.Oath oath() {
        return oath;
    }

    /** How house {@code houseId} treats the player now, counting the oath (W18, W20). */
    public com.bpm.minotaur.gamedata.history.favour.Favour.Tier stanceOf(int houseId) {
        return favour.stance(world, houseId, oath.houseId);
    }

    /** The player swears to {@code houseId} at its camp (W20); a former oath is broken by it (W21). */
    public void swear(int houseId) {
        if (oath.sworn() && oath.houseId != houseId) breakOath();
        oath.swear(houseId);
        apply(new PlayerDeed(PlayerDeed.Kind.SWORE_OATH, houseId, world.liveSeasons()));
    }

    /**
     * The player breaks its oath (W21): chronicled with their name on it, every house thinks less of
     * them, and the house betrayed counts them its enemy.
     */
    public void breakOath() {
        if (!oath.sworn()) return;
        int betrayed = oath.houseId;
        oath.forsake();
        apply(new PlayerDeed(PlayerDeed.Kind.BROKE_OATH, betrayed, world.liveSeasons()));
        favour.changeAll(world, com.bpm.minotaur.gamedata.history.favour.Favour.OATH_BROKEN_ALL);
        favour.set(betrayed, com.bpm.minotaur.gamedata.history.favour.Favour.MIN);
    }

    /**
     * The player killed a soldier of house {@code houseId}: the house thinks less of them (W19), and
     * a contract to kill one of its captains is kept if this was one ({@code figureId} at least 0).
     * True when that contract is done.
     */
    public boolean onSoldierSlain(int houseId, int figureId) {
        favour.change(world, houseId, com.bpm.minotaur.gamedata.history.favour.Favour.KILLED_SOLDIER);
        com.bpm.minotaur.gamedata.history.favour.Contract c = oath.contract;
        if (c != null && !c.done && c.kind == com.bpm.minotaur.gamedata.history.favour.Contract.Kind.KILL_CAPTAIN
                && c.targetId == houseId && figureId >= 0) {
            c.done = true;
            return true;
        }
        return false;
    }

    /**
     * The sworn house sets its next task (W25). A great house at Friend sets hard ones, toward the
     * seal of its gash (W26). Null when it is at war with no one.
     */
    public com.bpm.minotaur.gamedata.history.favour.Contract offerContract(
            java.util.List<com.bpm.minotaur.gamedata.history.war.Front> fronts, com.badlogic.gdx.math.GridPoint2 player) {
        if (!oath.sworn()) return null;
        com.bpm.minotaur.gamedata.history.House h = world.house(oath.houseId);
        boolean hard = h != null && h.isGreat() && !oath.sealGranted
                && favour.tier(oath.houseId) == com.bpm.minotaur.gamedata.history.favour.Favour.Tier.FRIEND;
        oath.contract = com.bpm.minotaur.gamedata.history.favour.Contract.offer(world, oath.houseId, oath.done, hard, fronts, player);
        return oath.contract;
    }

    /** The sworn house's lord grants the player its gash's seal (W26): chronicled. */
    public void recordSealGranted(int houseId) {
        oath.sealGranted = true;
        apply(new PlayerDeed(PlayerDeed.Kind.SEAL_GRANTED, houseId, world.liveSeasons()));
    }

    /** What of the surface war is saved (Living War W3). */
    public com.bpm.minotaur.gamedata.history.war.EncounterLedger encounterLedger() {
        return encounters;
    }

    /**
     * The surface war's encounters under way now (Living War W2): the ones the player can walk into
     * and the fights out of sight. {@code surface}: the player is on the overland.
     */
    public List<com.bpm.minotaur.gamedata.history.war.Encounter> encounters(com.badlogic.gdx.math.GridPoint2 playerChunk,
            boolean surface) {
        return encounters(playerChunk, surface, null);
    }

    /** As {@link #encounters(com.badlogic.gdx.math.GridPoint2, boolean)}; {@code seats} send columns toward their fronts. */
    public List<com.bpm.minotaur.gamedata.history.war.Encounter> encounters(com.badlogic.gdx.math.GridPoint2 playerChunk,
            boolean surface, com.bpm.minotaur.gamedata.history.war.SeatMap seats) {
        return com.bpm.minotaur.gamedata.history.war.EncounterScheduler.at(world, warClock, encounters, playerChunk, surface, seats);
    }

    /**
     * The player fought in a skirmish until it broke (Living War W12): {@code winner} held the ground,
     * {@code loser} routed; {@code captainFell} is a named captain who died in it, or -1.
     */
    public void recordSkirmish(int winner, int loser, int captainFell) {
        PlayerDeed d = new PlayerDeed(PlayerDeed.Kind.SKIRMISH, winner, world.liveSeasons());
        d.other = loser;
        d.figure = captainFell;
        apply(d);
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
        save.encounters = encounters;
        for (java.util.Map.Entry<Integer, Integer> e : favour.all().entrySet()) {
            save.favourHouses.add(e.getKey());
            save.favourValues.add(e.getValue());
        }
        save.oath = oath.sworn() ? oath : null;
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

    /** A deed done now: the history takes it, and the life being lived remembers it (#280). */
    private void apply(PlayerDeed deed) {
        replay(deed);
        WorldLogManager.getInstance().noteDeed(deed, world);
    }

    /** A deed the history takes again from the save; the World Log already holds it. */
    private void replay(PlayerDeed deed) {
        deeds.add(deed);
        HistorySimulator.applyDeed(world, deed);
    }
}
