package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.ui.AffinityText;
import com.bpm.minotaur.ui.UiGlyphs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages first-time onboarding hints (#169).
 *
 * <p>Hints are show-once log messages posted to the event chronicle the first time
 * something happens in a save slot. They are not a tutorial system: each hint is
 * recorded in {@code hints.json} in the active slot's directory, and the player can
 * toggle hints on/off in Settings ({@link SettingsManager#isShowHints()}).
 */
public class HintManager implements SlotScopedState {

    public enum HintId {
        ARRIVAL,
        BAD_MONSTER,
        NASTY_MONSTER,
        HUNGER,
        THIRST,
        COLD,
        DIVINITY,
        DOOM,
        LOCKED_SPELL_SLOT,
        HEAL_READY,
        /** The Living War (A51): the first of each, once. */
        WAR_TABLE,
        WAR_TABLE_OPENED,
        WAR_SIGHTING,
        WAR_EARSHOT,
        WAR_TRAIL,
        WAR_FIGHT,
        WAR_TOLERATED
    }

    private static HintManager instance;

    private final Set<String> seenHints = new HashSet<>();

    private HintManager() {
        SaveManager.register(this);
        load();
    }

    public static synchronized HintManager getInstance() {
        if (instance == null) {
            instance = new HintManager();
        }
        return instance;
    }

    private String getSaveFilePath() {
        return SaveManager.getInstance().getActiveSlotFilePath("hints.json");
    }

    /** A hint is shown only while hints are on and it hasn't been seen this save. */
    private boolean shouldShow(HintId id) {
        return SettingsManager.getInstance().isShowHints() && !hasSeen(id);
    }

    public synchronized boolean hasSeen(HintId id) {
        return id != null && seenHints.contains(id.name());
    }

    public synchronized boolean hasSeen(String id) {
        return id != null && seenHints.contains(id);
    }

    /**
     * Attempts to post a hint to the event log. Returns true if posted, false if suppressed
     * (already seen, hints disabled in settings, or null event manager).
     */
    public synchronized boolean postHint(HintId id, GameEventManager eventManager, String message, float duration) {
        if (id == null || eventManager == null || message == null || message.isEmpty()) {
            return false;
        }
        if (!SettingsManager.getInstance().isShowHints()) {
            return false;
        }
        if (seenHints.contains(id.name())) {
            return false;
        }

        seenHints.add(id.name());
        save();

        eventManager.addEvent(new GameEvent(UiGlyphs.sanitize(message), duration));
        return true;
    }

    /**
     * Posts a multi-line arrival hint.
     */
    public synchronized boolean postMultiLineHint(HintId id, GameEventManager eventManager, List<String> lines, float duration) {
        if (id == null || eventManager == null || lines == null || lines.isEmpty()) {
            return false;
        }
        if (!SettingsManager.getInstance().isShowHints()) {
            return false;
        }
        if (seenHints.contains(id.name())) {
            return false;
        }

        seenHints.add(id.name());
        save();

        for (String line : lines) {
            eventManager.addEvent(new GameEvent(UiGlyphs.sanitize(line), duration));
        }
        return true;
    }

    // --- Specialized Hint Checkers ---

    /**
     * New save, first shelter spawn arrival message (#169 / #189): revenge story,
     * delve into the maze, return to shelter to rest and save.
     */
    public boolean checkArrival(GameEventManager eventManager) {
        if (!shouldShow(HintId.ARRIVAL)) return false;
        List<String> lines = new ArrayList<>();
        lines.add("You were dragged into the maze in chains. You want vengeance on Castle Tarmin.");
        lines.add("Delve into the depths to slay monsters and grow in power.");
        lines.add("Return to this shelter to rest, craft and save your progress.");
        return postMultiLineHint(HintId.ARRIVAL, eventManager, lines, 6.0f);
    }

    /**
     * First Bad monster in view: names category and effect (resists War, weak to Spiritual).
     */
    public boolean checkBadMonster(Monster monster, GameEventManager eventManager) {
        if (!shouldShow(HintId.BAD_MONSTER)) return false;
        String name = monster != null ? AffinityText.nameOf(monster) : "Monster";
        String msg = name + " is Bad: War weapons barely hurt them. Spiritual weapons and spells do more.";
        return postHint(HintId.BAD_MONSTER, eventManager, msg, 4.0f);
    }

    /**
     * First Nasty monster in view: names category and effect (resists Spiritual, weak to War).
     */
    public boolean checkNastyMonster(Monster monster, GameEventManager eventManager) {
        if (!shouldShow(HintId.NASTY_MONSTER)) return false;
        String name = monster != null ? AffinityText.nameOf(monster) : "Monster";
        String msg = name + " is Nasty: Spiritual attacks barely hurt them. War weapons do more.";
        return postHint(HintId.NASTY_MONSTER, eventManager, msg, 4.0f);
    }

    /**
     * Below half health with the starting heal ready: name the key (#263). The heal sat unused for
     * four lives because nothing said it was there.
     */
    public boolean checkHealReady(com.bpm.minotaur.gamedata.player.Player player, GameEventManager eventManager) {
        if (player == null || !shouldShow(HintId.HEAL_READY)) return false;
        if (player.getCurrentHP() * 2 >= player.getStats().getMaxHP() || !player.healReady()) return false;
        com.bpm.minotaur.gamedata.spells.SpellTemplate heal = com.bpm.minotaur.gamedata.spells.SpellDataManager
                .getInstance().getSpell(com.bpm.minotaur.gamedata.player.Player.STARTING_HEAL_SPELL);
        String quickHeal = ActionKeys.live().keyName(Action.QUICK_HEAL);
        String slotKey = player.startingHealSlotKey(); // wherever the player has put it
        String msg = heal.getName() + " is ready: press " + quickHeal + (slotKey != null ? " (or " + slotKey + ")." : ".");
        return postHint(HintId.HEAL_READY, eventManager, msg, 4f);
    }

    /**
     * First hunger state: explains what the state means and the fix.
     */

    public boolean checkHunger(GameEventManager eventManager) {
        if (!shouldShow(HintId.HUNGER)) return false;
        String msg = "You are hungry: eat food from your pack before hunger drains your strength.";
        return postHint(HintId.HUNGER, eventManager, msg, 3.5f);
    }

    /**
     * First thirst state: explains what the state means and the fix.
     */
    public boolean checkThirst(GameEventManager eventManager) {
        if (!shouldShow(HintId.THIRST)) return false;
        String msg = "You are thirsty: drink from your waterskin, or face water to refill it.";
        return postHint(HintId.THIRST, eventManager, msg, 3.5f);
    }

    /**
     * First cold state: explains what the state means and the fix.
     */
    public boolean checkCold(GameEventManager eventManager) {
        if (!shouldShow(HintId.COLD)) return false;
        String msg = "You are cold: find shelter, build a fire with tinder, or wear warm clothing.";
        return postHint(HintId.COLD, eventManager, msg, 3.5f);
    }

    /**
     * First Divinity gained: explains what Divinity is used for.
     */
    public boolean checkDivinity(GameEventManager eventManager) {
        if (!shouldShow(HintId.DIVINITY)) return false;
        String msg = "Divinity gained: offer Divinity at the Shelter Altar to unlock permanent blessings.";
        return postHint(HintId.DIVINITY, eventManager, msg, 4.0f);
    }

    /**
     * First Doom change: explains what Doom/Hunger means.
     */
    public boolean checkDoom(GameEventManager eventManager) {
        if (!shouldShow(HintId.DOOM)) return false;
        String msg = "Tarmin's Hunger grows: each death feeds him. At " + DoomManager.maxDeaths()
                + " deaths, the Bridge falls.";
        return postHint(HintId.DOOM, eventManager, msg, 4.0f);
    }

    /**
     * First locked spell slot selected: explains that slots unlock by level.
     */
    public boolean checkLockedSpellSlot(GameEventManager eventManager) {
        if (!shouldShow(HintId.LOCKED_SPELL_SLOT)) return false;
        String msg = "Locked spell slot: spell slots unlock at levels 2, 5, 8 and 11.";
        return postHint(HintId.LOCKED_SPELL_SLOT, eventManager, msg, 3.5f);
    }

    /** The first return to the Shelter: the Table is pointed out (A51). */
    public boolean checkWarTable(GameEventManager eventManager) {
        if (livingWarUnlocked() || !shouldShow(HintId.WAR_TABLE)) return false;
        return postHint(HintId.WAR_TABLE, eventManager, "The War Table stands by the Lectern. Study it to hear what the Shelter knows of the houses' wars.", 5.0f);
    }

    /**
     * The War Table is being opened: true the first time, for its three lines. They are how the Table
     * explains itself rather than a hint, so they and the Codex page come whatever the setting.
     */
    public synchronized boolean noteWarTableOpened() {
        boolean first = seenHints.add(HintId.WAR_TABLE_OPENED.name());
        if (first) save();
        return first;
    }

    /** The "Living War" Codex page unlocks on the first Table visit (A51). */
    public boolean livingWarUnlocked() {
        return hasSeen(HintId.WAR_TABLE_OPENED);
    }

    /** @param heading "north-east" and the like; null for a host standing still */
    public boolean checkWarSighting(String houseName, String heading, GameEventManager eventManager) {
        if (!shouldShow(HintId.WAR_SIGHTING)) return false;
        String first = "A host of " + houseName + (heading != null ? " marches " + heading : " has made camp");
        return postHint(HintId.WAR_SIGHTING, eventManager, first + ". Armies move whether you watch or not; the map remembers where you saw them.", 6.0f);
    }

    /** @param heading "east" and the like */
    public boolean checkWarEarshot(String heading, GameEventManager eventManager) {
        if (!shouldShow(HintId.WAR_EARSHOT)) return false;
        String first = heading != null ? "Horns to the " + heading : "Horns close by";
        return postHint(HintId.WAR_EARSHOT, eventManager, first + ": a battle. Follow the sound, and choose a side when you arrive.", 6.0f);
    }

    /** @param heading "north-east" and the like */
    public boolean checkWarTrail(String heading, GameEventManager eventManager) {
        if (!shouldShow(HintId.WAR_TRAIL)) return false;
        String first = "Tracks: a host passed here" + (heading != null ? ", heading " + heading : "");
        return postHint(HintId.WAR_TRAIL, eventManager, first + ". Following them updates where you think it is.", 6.0f);
    }

    public boolean checkWarTolerated(String houseName, GameEventManager eventManager) {
        if (!shouldShow(HintId.WAR_TOLERATED)) return false;
        return postHint(HintId.WAR_TOLERATED, eventManager, houseName + " tolerates you. At Friend, kneel at its banner to swear to it.", 6.0f);
    }

    /** The side-choice prompt explains itself, the first time: its words, or null (A51). */
    public synchronized String takeFirstFightWords() {
        if (!shouldShow(HintId.WAR_FIGHT)) return null;
        seenHints.add(HintId.WAR_FIGHT.name());
        save();
        return UiGlyphs.sanitize("Choose, and that side's soldiers fight beside you until it ends. Or keep away, and get off this ground.");
    }

    // --- Persistence & SlotScopedState ---

    public static class SaveData {
        public ArrayList<String> seenHints = new ArrayList<>();
    }

    public synchronized void save() {
        try {
            FileHandle file = SaveManager.getInstance().getFileHandle(getSaveFilePath());
            SaveData data = new SaveData();
            data.seenHints.addAll(seenHints);
            SaveManager.getInstance().atomicWriteJson(file, data);
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("HintManager", "Failed to save hints: " + e.getMessage());
            }
        }
    }

    public synchronized void load() {
        seenHints.clear();
        try {
            FileHandle file = SaveManager.getInstance().getFileHandle(getSaveFilePath());
            if (file.exists()) {
                Json json = new Json();
                json.setUsePrototypes(false);
                SaveData data = json.fromJson(SaveData.class, file.readString());
                if (data != null && data.seenHints != null) {
                    seenHints.addAll(data.seenHints);
                }
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("HintManager", "Failed to load hints: " + e.getMessage());
            }
        }
    }

    @Override
    public synchronized void reloadForActiveSlot() {
        load();
    }

    @Override
    public synchronized void resetForNewGame() {
        seenHints.clear();
        save();
    }
}
