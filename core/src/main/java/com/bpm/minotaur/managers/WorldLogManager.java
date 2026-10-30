package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.PlayerDeed;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.Megabeast;
import com.bpm.minotaur.gamedata.worldlog.Fact;
import com.bpm.minotaur.gamedata.worldlog.WorldLog;

/**
 * The active slot's World Log (#280), kept in {@code world_log.json} beside the history but apart
 * from it: the history replays deeds, and the log only reads them as they happen.
 *
 * <p>A slot from before the log gets one, on first load, that remembers only how many lives it
 * forgot (the Hunger) and how deep the soul had been, so the next descent is not a false record.
 */
public final class WorldLogManager implements SlotScopedState {

    private static WorldLogManager instance;

    private WorldLog log;

    private WorldLogManager() {
        SaveManager.register(this);
    }

    public static WorldLogManager getInstance() {
        if (instance == null) {
            instance = new WorldLogManager();
        }
        return instance;
    }

    public WorldLog log() {
        if (log == null) load();
        return log;
    }

    /** The names of every life the log holds, which no new prisoner may share (#286). */
    public java.util.Set<String> lifeNames() {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (com.bpm.minotaur.gamedata.worldlog.LifeRecord life : log().lives()) names.add(life.name);
        return names;
    }

    /** The life being lived gains a line, if a life is being lived. */
    public void note(Fact fact) {
        log().note(fact);
    }

    public void noteDepth(int level) {
        log().noteDepth(level);
    }

    public void noteKill(String monster, boolean heavy, boolean boss) {
        log().noteKill(monster, heavy, boss);
    }

    /**
     * Keeps the log level with the player: a life the player is living but the log has not
     * woken -- a new body, a reroll, or a save from before the log -- is woken now.
     */
    public void keepUpWith(com.bpm.minotaur.gamedata.history.Life life) {
        if (life == null) return;
        com.bpm.minotaur.gamedata.worldlog.LifeRecord open = log().current();
        if (open == null || !life.title().equals(open.title)) {
            log().wake(life, DoomManager.getInstance().getDeathCount());
        }
    }

    /**
     * The Houses tell the life that has just ended (#285): every House it touched, and the Knell
     * when its death brought the Hunger to a new stage.
     */
    public void tellLastLife(com.bpm.minotaur.gamedata.history.HistoryWorld world,
                             java.util.List<com.bpm.minotaur.gamedata.history.PlayerDeed> deeds, int hunger) {
        // Only the life this death closed: if no life was open, no earlier one is told again.
        com.bpm.minotaur.gamedata.worldlog.LifeRecord life = log().last();
        if (life == null || life.number != justDied || !life.tellings.isEmpty()) return;
        log().tell(life.number, com.bpm.minotaur.gamedata.worldlog.Tellings.forLife(world, deeds, life, hunger));
        save();
    }

    /** Gives up {@code houseId}'s oldest unfound account, marked found and saved; null if none is left. */
    public com.bpm.minotaur.gamedata.worldlog.Telling findTelling(int houseId) {
        com.bpm.minotaur.gamedata.worldlog.Telling t = log().findTelling(houseId);
        if (t != null) save();
        return t;
    }

    /** The Warden fell: its Bridge closes with the Knell's Reckoning, and the history keeps it (#290). */
    public void closeBridgeByWarden(HistoryManager history) {
        // The log and the history count the same ends: with no history, neither closes.
        if (history == null) return;
        com.bpm.minotaur.gamedata.worldlog.LifeRecord slayer = log().current() != null ? log().current() : log().last();
        log().closeBridge(com.bpm.minotaur.gamedata.worldlog.Reckonings.wardenFell(bridgeName(), slayer));
        history.recordWardenFell();
        save();
    }

    /** {@code god} is spent dragging Tarmin back: its shrines go dark, and the Bridge closes (#290). */
    public void spendGodForCrossing(HistoryManager history, com.bpm.minotaur.gamedata.history.OldGod god) {
        history.recordGodSpent(god);
        com.bpm.minotaur.gamedata.history.House rose = risenHouse(history.world());
        if (rose != null) history.recordHouseRose(rose.id);
        com.bpm.minotaur.gamedata.worldlog.LifeRecord last = log().last();
        log().closeBridge(com.bpm.minotaur.gamedata.worldlog.Reckonings.crossed(bridgeName(), god, risen(history.world()), last));
        // The House that rose tells the life whose death let Tarmin cross (#290).
        if (rose != null && last != null) {
            log().tell(last.number, java.util.Collections.singletonList(com.bpm.minotaur.gamedata.worldlog.Tellings.tell(
                    new com.bpm.minotaur.gamedata.worldlog.Tellings.Teller(rose.id,
                            com.bpm.minotaur.gamedata.worldlog.Tellings.Role.ROSE), last, history.world(), -1)));
        }
        save();
    }

    /** The Champion slew Tarmin: the Age ends with its Reckoning (#291). */
    public void closeAgeByChampion(com.bpm.minotaur.gamedata.history.AgeEnding ending) {
        log().closeAge(com.bpm.minotaur.gamedata.worldlog.Reckonings.ageEnded(
                com.bpm.minotaur.gamedata.worldlog.Age.nameOf(log().currentAge()), log().last(), ending));
        save();
    }

    /**
     * A monster slew Tarmin off-screen (#292): the log learns of it at the Shifting and ends the
     * Age. Returns whether any Age ended, so the caller can begin the new reign's Bridge.
     */
    public boolean closeAgesEnded(HistoryManager history) {
        if (history == null) return false;
        boolean ended = log().closeAgesEnded(history.world().ageEndings()) > 0;
        if (ended) save();
        return ended;
    }

    /** No god was left to hold Tarmin: the world's last Reckoning (#278, #290). */
    public com.bpm.minotaur.gamedata.worldlog.Reckoning lastCrossing(HistoryManager history) {
        com.bpm.minotaur.gamedata.worldlog.Reckoning r = com.bpm.minotaur.gamedata.worldlog.Reckonings.lastCrossing(
                bridgeName(), risen(history.world()), log().last());
        log().closeBridge(r);
        save();
        return r;
    }

    private String bridgeName() {
        return com.bpm.minotaur.gamedata.worldlog.Bridge.nameOf(log().currentBridge());
    }

    /** The House that rose while Tarmin was gone, by name. */
    private static String risen(HistoryWorld world) {
        House best = risenHouse(world);
        return best != null ? best.name : "a house of the Maze";
    }

    /** The proudest House of the Maze but his own: the one that takes the most while he is gone. */
    private static House risenHouse(HistoryWorld world) {
        House best = null;
        for (House h : world.livingHouses()) {
            if (h.holdsCastle) continue;
            if (best == null || h.prestige > best.prestige) best = h;
        }
        return best;
    }

    /** The life being lived ends; the log is written at once, before anything else can go wrong. */
    public void die(String epitaph, String killer, String place, int turn) {
        com.bpm.minotaur.gamedata.worldlog.LifeRecord open = log().current();
        justDied = open != null ? open.number : -1;
        if (open != null) {
            try {
                ChaosManager chaos = ChaosManager.getInstance();
                if (chaos != null && chaos.isSurgeActive()) {
                    ChaosSurgeEvent active = chaos.getActiveEvent();
                    String surgeName = active != null ? active.getName() : "Chaos Surge";
                    open.motleyReckoning = surgeName;
                    open.note(Fact.of(Fact.Kind.MOTLEY_RECKONING, surgeName));
                }
            } catch (Throwable ignored) {}
        }
        log().die(epitaph, killer, place, turn);
        log().dropUncollectedTellings();
        save();
    }

    /** The number of the life the last death closed, or -1 if it closed none. */
    private int justDied = -1;

    /** A deed of the player's own, as a line of the life being lived; the run's other deeds are not. */
    public void noteDeed(PlayerDeed deed, HistoryWorld world) {
        Fact fact = factFor(deed, world);
        if (fact != null) log().note(fact);
    }

    static Fact factFor(PlayerDeed deed, HistoryWorld world) {
        switch (deed.kind) {
            case SLEW_FIGURE: {
                Figure f = world.figure(deed.target);
                if (f == null) return null;
                // A Figure that was a Nemesis is told once, by its NEMESIS_SLAIN line (#284).
                for (com.bpm.minotaur.gamedata.history.Nemesis n : world.nemeses()) {
                    if (n.figureId == f.id) return null;
                }
                House h = world.house(f.houseId);
                return Fact.of(Fact.Kind.SLEW_FIGURE, f.name, h != null ? h.name : null);
            }
            case SLEW_MEGABEAST:
                return Fact.of(Fact.Kind.SLEW_MEGABEAST, beastName(world, deed.target));
            case PACIFIED_MEGABEAST:
                return Fact.of(Fact.Kind.PACIFIED_MEGABEAST, beastName(world, deed.target));
            case BROKE_BEAST_PEACE:
                return Fact.of(Fact.Kind.BROKE_BEAST_PEACE, beastName(world, deed.target));
            case QUEST_DONE:
                return Fact.of(Fact.Kind.QUEST_DONE, deed.note);
            case BATTLE_WITNESSED:
                return Fact.of(Fact.Kind.BATTLE_WITNESSED, houseName(world, deed.target));
            case ARMY_BATTLE:
                return deed.witnessed ? Fact.of(Fact.Kind.BATTLE_WITNESSED, houseName(world, deed.target)) : null;
            case SKIRMISH:
                return Fact.of(Fact.Kind.SKIRMISH, houseName(world, deed.target));
            case SWORE_OATH:
                return Fact.of(Fact.Kind.SWORE_OATH, houseName(world, deed.target));
            case BROKE_OATH:
                return Fact.of(Fact.Kind.BROKE_OATH, houseName(world, deed.target));
            case SLEW_NEMESIS: {
                com.bpm.minotaur.gamedata.history.Nemesis n = world.nemesis(deed.target);
                if (n == null) return null;
                String taken = n.tally() == 1 ? n.victims.get(0)
                        : com.bpm.minotaur.ui.UiNames.plural(n.tally(), "life", "lives");
                return Fact.of(Fact.Kind.NEMESIS_SLAIN, n.name, taken);
            }
            case SEAL_GRANTED:
                return Fact.of(Fact.Kind.SEAL_GRANTED, null, houseName(world, deed.target));
            default:
                // A death, a Doom stage, a war or a lord's fall the run only saw: not this life's deed.
                return null;
        }
    }

    private static String houseName(HistoryWorld world, int id) {
        House h = world.house(id);
        return h != null ? h.name : null;
    }

    private static String beastName(HistoryWorld world, int id) {
        Megabeast b = world.megabeast(id);
        return b != null ? b.name : null;
    }

    private String getSaveFilePath() {
        return SaveManager.getInstance().getActiveSlotFilePath("world_log.json");
    }

    public void save() {
        if (log == null) return;
        try {
            Json json = new Json();
            json.setOutputType(OutputType.json);
            FileHandle file = SaveManager.getInstance().getFileHandle(getSaveFilePath());
            if (!file.parent().exists()) {
                file.parent().mkdirs();
            }
            file.writeString(json.prettyPrint(log), false);
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("WorldLogManager", "Failed to save the World Log", e);
            }
        }
    }

    private void load() {
        try {
            FileHandle file = SaveManager.getInstance().getFileHandle(getSaveFilePath());
            if (file.exists()) {
                log = new Json().fromJson(WorldLog.class, file);
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("WorldLogManager", "Failed to load the World Log", e);
            }
        }
        if (log == null) {
            log = WorldLog.upgradedFrom(DoomManager.getInstance().getDeathCount(), deepestLevel());
        }
    }

    private static int deepestLevel() {
        try {
            com.bpm.minotaur.gamedata.UnlockData data = UnlockManager.getInstance().getData();
            return data != null ? data.deepestLevelReached : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Override
    public void reloadForActiveSlot() {
        log = null;
    }

    @Override
    public void resetForNewGame() {
        log = new WorldLog();
        save();
    }
}
