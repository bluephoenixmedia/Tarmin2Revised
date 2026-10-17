package com.bpm.minotaur.gamedata.events;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Every choice event in {@code data/events.json}, and the weighted pick that places them.
 *
 * <p>Kept apart from {@link com.bpm.minotaur.gamedata.encounters.EncounterManager}, which owns the
 * statue encounters; the two features share no data.
 */
public final class EventCatalog {

    public static final String DATA_PATH = "data/events.json";

    private static EventCatalog instance;

    private final Map<String, EventDefinition> byId = new LinkedHashMap<>();

    private EventCatalog() {
    }

    /** The game's catalog, loaded from {@link #DATA_PATH} on first use. Empty if the file is absent. */
    public static synchronized EventCatalog getInstance() {
        if (instance == null) {
            FileHandle file = Gdx.files != null ? Gdx.files.internal(DATA_PATH) : null;
            if (file != null && file.exists()) {
                instance = fromJson(file.readString("UTF-8"));
                if (Gdx.app != null) {
                    Gdx.app.log("EventCatalog", "Loaded " + instance.byId.size() + " choice events.");
                }
            } else {
                instance = new EventCatalog();
                if (Gdx.app != null) {
                    Gdx.app.error("EventCatalog", DATA_PATH + " not found; no choice events will appear.");
                }
            }
        }
        return instance;
    }

    public static EventCatalog fromJson(String jsonText) {
        EventCatalog catalog = new EventCatalog();
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        JsonValue root = new JsonReader().parse(jsonText);
        JsonValue list = root.get("events");
        if (list != null) {
            for (JsonValue entry : list) {
                EventDefinition def = json.readValue(EventDefinition.class, entry);
                if (def != null && def.id != null) {
                    catalog.byId.put(def.id, def);
                }
            }
        }
        return catalog;
    }

    public EventDefinition get(String id) {
        return id == null ? null : byId.get(id);
    }

    public Collection<EventDefinition> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /** Events allowed in this biome at this depth that this run has not placed yet. */
    public List<EventDefinition> eligible(String biome, int depth, Collection<String> seen) {
        List<EventDefinition> out = new ArrayList<>();
        for (EventDefinition def : byId.values()) {
            if (def.allowsBiome(biome) && def.allowsDepth(depth) && !seen.contains(def.id)) {
                out.add(def);
            }
        }
        return out;
    }

    /** A weighted pick from {@link #eligible}; null when nothing is left. */
    public EventDefinition pick(String biome, int depth, Collection<String> seen, Random rng) {
        List<EventDefinition> pool = eligible(biome, depth, seen);
        int total = 0;
        for (EventDefinition def : pool) {
            total += Math.max(0, def.weight);
        }
        if (total <= 0) {
            return null;
        }
        int roll = rng.nextInt(total);
        for (EventDefinition def : pool) {
            roll -= Math.max(0, def.weight);
            if (roll < 0) {
                return def;
            }
        }
        return null;
    }
}
