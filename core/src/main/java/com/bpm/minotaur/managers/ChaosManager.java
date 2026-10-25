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

    private ChaosManager() {
        SaveManager.register(this);
        registerStarterEvent();
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

    private void registerStarterEvent() {
        registerEvent(new ChaosSurgeEvent() {
            @Override
            public String getId() {
                return "starter_cosmic_shimmer";
            }

            @Override
            public String getName() {
                return "Cosmic Shimmer";
            }

            @Override
            public String getDescription() {
                return "Reality ripples as The Laughing Void gazes upon the world.";
            }

            @Override
            public void onActivate(ChaosEventContext context) {
            }

            @Override
            public void onTurnTick(ChaosEventContext context) {
            }

            @Override
            public void onExpire(ChaosEventContext context) {
            }
        });
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
            registerStarterEvent();
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
