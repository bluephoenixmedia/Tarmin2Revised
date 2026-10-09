package com.bpm.minotaur.gamedata.history;

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

/** Every doctrine in {@code data/doctrines.json}, in file order. */
public final class DoctrineCatalog {

    public static final String DATA_PATH = "data/doctrines.json";
    /** The doctrine reserved for Tarmin-Zul's house. */
    public static final String TARMIN_ZUL_DOCTRINE = "antlered_host";

    private static DoctrineCatalog instance;

    private final Map<String, Doctrine> byId = new LinkedHashMap<>();

    private DoctrineCatalog() {
    }

    /** The game's catalog, loaded from {@link #DATA_PATH} on first use. */
    public static synchronized DoctrineCatalog getInstance() {
        if (instance == null) {
            FileHandle file = Gdx.files != null ? Gdx.files.internal(DATA_PATH) : null;
            if (file == null || !file.exists()) {
                throw new IllegalStateException(DATA_PATH + " not found; the Maze has no houses.");
            }
            instance = fromJson(file.readString("UTF-8"));
        }
        return instance;
    }

    public static DoctrineCatalog fromJson(String jsonText) {
        DoctrineCatalog catalog = new DoctrineCatalog();
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        JsonValue root = new JsonReader().parse(jsonText);
        JsonValue list = root.get("doctrines");
        if (list != null) {
            for (JsonValue entry : list) {
                Doctrine d = json.readValue(Doctrine.class, entry);
                JsonValue weights = entry.get("traitWeights");
                d.traitWeights = new LinkedHashMap<>();
                if (weights != null) {
                    for (JsonValue w : weights) d.traitWeights.put(w.name, w.asInt());
                }
                if (d.id != null) catalog.byId.put(d.id, d);
            }
        }
        return catalog;
    }

    public Doctrine get(String id) {
        return id == null ? null : byId.get(id);
    }

    public Collection<Doctrine> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /** Doctrines an ordinary generated house may take. */
    public List<Doctrine> selectable() {
        List<Doctrine> out = new ArrayList<>();
        for (Doctrine d : byId.values()) {
            if (!d.unique) out.add(d);
        }
        return out;
    }
}
