package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.utils.DiceRoller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Manages the turn lifecycle, cadence, and state persistence of Chaos Surges
 * invoked by The Laughing Void.
 */
public class ChaosManager implements SlotScopedState {

    private static final String TAG = "ChaosManager";
    private static ChaosManager instance;

    // --- Cadence & Duration Constants ---
    public static final float TRIGGER_PROBABILITY = 0.002f; // ~0.2% chance per step
    public static final int MIN_COOLDOWN_TURNS = 150;
    public static final int BASE_DURATION_TURNS = 15;
    public static final String DURATION_DICE = "2d12";

    // --- Active State ---
    private boolean surgeActive = false;
    private String activeSurgeId = null;
    private ChaosSurgeEvent activeEvent = null;
    private int remainingTurns = 0;
    private int totalDuration = 0;
    private int cooldownRemaining = 0;
    private int totalSurgesTriggered = 0;

    private final Map<String, ChaosSurgeEvent> registeredEvents = new LinkedHashMap<>();
    private final List<ChaosSurgeEvent> eventList = new ArrayList<>();
    private final Random rng = new Random();

    public interface ChaosSurgeListener {
        void onSurgeTriggered(ChaosSurgeEvent event, int duration);
        void onSurgeExpired();
    }

    private final List<ChaosSurgeListener> listeners = new ArrayList<>();

    public void addListener(ChaosSurgeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(ChaosSurgeListener listener) {
        listeners.remove(listener);
    }

    private ChaosManager() {
        SaveManager.register(this);
        ChaosSurgeTable.registerAll(this);
    }

    public static synchronized ChaosManager getInstance() {
        if (instance == null) {
            instance = new ChaosManager();
        }
        return instance;
    }

    /** Reset for isolated unit testing. */
    public static synchronized void resetInstanceForTesting() {
        instance = null;
    }

    public void registerAllSurgeTableEvents() {
        ChaosSurgeTable.registerAll(this);
    }

    public void registerEvent(ChaosSurgeEvent event) {
        if (event == null || event.getId() == null) return;
        if (!registeredEvents.containsKey(event.getId())) {
            registeredEvents.put(event.getId(), event);
            eventList.add(event);
        } else {
            registeredEvents.put(event.getId(), event);
            for (int i = 0; i < eventList.size(); i++) {
                if (eventList.get(i).getId().equals(event.getId())) {
                    eventList.set(i, event);
                    break;
                }
            }
        }
    }

    public ChaosSurgeEvent getEvent(String id) {
        return registeredEvents.get(id);
    }

    public List<ChaosSurgeEvent> getRegisteredEvents() {
        return new ArrayList<>(eventList);
    }

    public int rollDuration() {
        int bonus = DiceRoller.roll(DURATION_DICE);
        return BASE_DURATION_TURNS + (bonus > 0 ? bonus : 13);
    }

    /**
     * Called by TurnManager every discrete player turn.
     */
    public void onPlayerTurn(Maze maze, Player player, WorldManager worldManager, GameEventManager eventManager) {
        if (player == null) return;

        // Never trigger inside the Shelter Camp
        boolean inShelter = maze != null && maze.isHomeTile((int) player.getPosition().x, (int) player.getPosition().y);
        if (inShelter) return;

        if (surgeActive) {
            remainingTurns--;
            ChaosEventContext context = new ChaosEventContext(maze, player, worldManager, eventManager, remainingTurns, totalDuration);

            if (activeEvent != null) {
                activeEvent.onTurnTick(context);
            }

            if (remainingTurns <= 0) {
                endSurge(context);
            }
        } else {
            if (cooldownRemaining > 0) {
                cooldownRemaining--;
            } else {
                // Check random trigger probability
                if (MathUtils.random() < TRIGGER_PROBABILITY) {
                    triggerRandomSurge(maze, player, worldManager, eventManager);
                }
            }
        }
    }

    /**
     * Triggers a randomly selected registered chaos event.
     */
    public ChaosSurgeEvent triggerRandomSurge(Maze maze, Player player, WorldManager worldManager, GameEventManager eventManager) {
        if (eventList.isEmpty()) {
            ChaosSurgeTable.registerAll(this);
        }
        ChaosSurgeEvent event = eventList.get(rng.nextInt(eventList.size()));
        int duration = rollDuration();
        triggerSurge(event, duration, maze, player, worldManager, eventManager);
        return event;
    }

    /**
     * Explicitly triggers a surge with the given event and duration.
     */
    public void triggerSurge(ChaosSurgeEvent event, int duration, Maze maze, Player player, WorldManager worldManager, GameEventManager eventManager) {
        if (event == null) return;

        this.surgeActive = true;
        this.activeEvent = event;
        this.activeSurgeId = event.getId();
        this.remainingTurns = duration > 0 ? duration : rollDuration();
        this.totalDuration = this.remainingTurns;
        this.totalSurgesTriggered++;

        ChaosEventContext context = new ChaosEventContext(maze, player, worldManager, eventManager, remainingTurns, totalDuration);
        activeEvent.onActivate(context);

        if (eventManager != null) {
            eventManager.addEvent(new GameEvent("The Laughing Void manifests! Chaos Surge: " + event.getName() + " (" + remainingTurns + " turns)", 4.0f));
        }

        if (Gdx.app != null) {
            Gdx.app.log(TAG, "Chaos Surge triggered: " + event.getName() + " for " + remainingTurns + " turns.");
        }

        try {
            com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordChaosSurge();
        } catch (Throwable ignored) {}

        try {
            com.bpm.minotaur.managers.WorldLogManager.getInstance().note(
                    com.bpm.minotaur.gamedata.worldlog.Fact.of(
                            com.bpm.minotaur.gamedata.worldlog.Fact.Kind.CHAOS_SURGE_WITNESSED,
                            event.getName()
                    )
            );
        } catch (Throwable ignored) {}

        for (ChaosSurgeListener l : new ArrayList<>(listeners)) {
            try {
                l.onSurgeTriggered(event, remainingTurns);
            } catch (Throwable t) {
                if (Gdx.app != null) Gdx.app.error(TAG, "Error notifying chaos surge listener", t);
            }
        }

        save();
    }

    /**
     * Ends the current surge, resetting state and triggering cooldown.
     */
    public void endSurge(ChaosEventContext context) {
        if (!surgeActive) return;

        if (activeEvent != null && context != null) {
            activeEvent.onExpire(context);
        }

        if (context != null && context.eventManager != null) {
            context.eventManager.addEvent(new GameEvent("The Laughing Void dissolves... The reality distortions subside.", 3.0f));
        }

        this.surgeActive = false;
        this.activeEvent = null;
        this.activeSurgeId = null;
        this.remainingTurns = 0;
        this.totalDuration = 0;
        this.cooldownRemaining = MIN_COOLDOWN_TURNS;

        if (Gdx.app != null) {
            Gdx.app.log(TAG, "Chaos Surge expired. Cooldown set to " + cooldownRemaining + " turns.");
        }

        for (ChaosSurgeListener l : new ArrayList<>(listeners)) {
            try {
                l.onSurgeExpired();
            } catch (Throwable t) {
                if (Gdx.app != null) Gdx.app.error(TAG, "Error notifying chaos surge listener", t);
            }
        }

        save();
    }

    // --- State Accessors ---

    public boolean isSurgeActive() {
        return surgeActive;
    }

    public ChaosSurgeEvent getActiveEvent() {
        return activeEvent;
    }

    public String getActiveSurgeId() {
        return activeSurgeId;
    }

    public int getRemainingTurns() {
        return remainingTurns;
    }

    public int getTotalDuration() {
        return totalDuration;
    }

    public int getCooldownRemaining() {
        return cooldownRemaining;
    }

    public void setCooldownRemaining(int turns) {
        this.cooldownRemaining = Math.max(0, turns);
    }

    public int getTotalSurgesTriggered() {
        return totalSurgesTriggered;
    }

    // --- Surge Effect Queries ---

    public float getDamageDealtMultiplier() {
        if (!surgeActive) return 1.0f;
        if ("glass_cannons".equals(activeSurgeId)) return 4.0f;
        if ("pacifism_wave".equals(activeSurgeId)) return 0.25f;
        return 1.0f;
    }

    public float getDamageTakenMultiplier() {
        if (!surgeActive) return 1.0f;
        if ("glass_cannons".equals(activeSurgeId)) return 4.0f;
        return 1.0f;
    }

    public float getRecoilFraction() {
        if (!surgeActive) return 0f;
        if ("mirror_blood".equals(activeSurgeId)) return 0.5f;
        return 0f;
    }

    public boolean isInvertCombatStats() {
        return surgeActive && "inverted_duality".equals(activeSurgeId);
    }

    public boolean isJestersFarceActive() {
        return surgeActive && "jesters_farce".equals(activeSurgeId);
    }

    public int getVisionCap() {
        if (surgeActive && "dense_neon_fog".equals(activeSurgeId)) return 2;
        return -1;
    }

    public boolean isWandOverchargeActive() {
        return surgeActive && "wand_overcharge".equals(activeSurgeId);
    }

    public boolean isSlipstreamActive() {
        return surgeActive && "slipstream".equals(activeSurgeId);
    }

    public boolean isGravityInverted() {
        return surgeActive && "inverted_gravity".equals(activeSurgeId);
    }

    // --- Persistence & SlotScopedState ---

    private String getSaveFilePath() {
        return SaveManager.getInstance().getActiveSlotFilePath("chaos_state.json");
    }

    public void save() {
        try {
            FileHandle file = SaveManager.getInstance().getFileHandle(getSaveFilePath());
            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);

            ChaosState state = new ChaosState();
            state.surgeActive = this.surgeActive;
            state.activeSurgeId = this.activeSurgeId;
            state.remainingTurns = this.remainingTurns;
            state.totalDuration = this.totalDuration;
            state.cooldownRemaining = this.cooldownRemaining;
            state.totalSurgesTriggered = this.totalSurgesTriggered;

            file.writeString(json.prettyPrint(state), false);
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error(TAG, "Failed to save Chaos state", e);
            }
        }
    }

    public void load() {
        try {
            FileHandle file = SaveManager.getInstance().getFileHandle(getSaveFilePath());
            if (file.exists()) {
                Json json = new Json();
                ChaosState state = json.fromJson(ChaosState.class, file);
                if (state != null) {
                    this.surgeActive = state.surgeActive;
                    this.activeSurgeId = state.activeSurgeId;
                    this.remainingTurns = state.remainingTurns;
                    this.totalDuration = state.totalDuration;
                    this.cooldownRemaining = state.cooldownRemaining;
                    this.totalSurgesTriggered = state.totalSurgesTriggered;

                    if (this.activeSurgeId != null) {
                        this.activeEvent = registeredEvents.get(this.activeSurgeId);
                    } else {
                        this.activeEvent = null;
                    }
                }
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error(TAG, "Failed to load Chaos state", e);
            }
        }
    }

    @Override
    public void reloadForActiveSlot() {
        this.surgeActive = false;
        this.activeSurgeId = null;
        this.activeEvent = null;
        this.remainingTurns = 0;
        this.totalDuration = 0;
        this.cooldownRemaining = 0;
        this.totalSurgesTriggered = 0;
        load();
    }

    @Override
    public void resetForNewGame() {
        this.surgeActive = false;
        this.activeSurgeId = null;
        this.activeEvent = null;
        this.remainingTurns = 0;
        this.totalDuration = 0;
        this.cooldownRemaining = 0;
        this.totalSurgesTriggered = 0;
        save();
    }

    public static class ChaosState {
        public boolean surgeActive = false;
        public String activeSurgeId = null;
        public int remainingTurns = 0;
        public int totalDuration = 0;
        public int cooldownRemaining = 0;
        public int totalSurgesTriggered = 0;
    }
}
