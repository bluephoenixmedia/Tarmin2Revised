package com.bpm.minotaur.gamedata.prop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads and serves the flat prop catalogue from {@code assets/data/props.json}.
 *
 * <p>Themes reference props by id; this is the only place an id resolves to an
 * asset path. Lookup failures return null rather than throwing, so a bad id in
 * a theme table degrades to "no prop placed" in game and is caught loudly by
 * {@code ThemeContractTest} instead.
 */
public class PropCatalog {

    public static final String CATALOG_PATH = "data/props.json";

    private static PropCatalog instance;

    private final Map<String, PropDefinition> props = new LinkedHashMap<>();
    private boolean loaded = false;

    public static PropCatalog getInstance() {
        if (instance == null) {
            instance = new PropCatalog();
        }
        return instance;
    }

    /** Test seam: drops the singleton so a test can force a reload. */
    public static void resetInstance() {
        instance = null;
    }

    /**
     * Loads the catalogue. Safe to call repeatedly; only the first call does
     * work. Missing or malformed JSON leaves the catalogue empty rather than
     * bringing down chunk generation.
     */
    public void load() {
        if (loaded) return;
        loaded = true;

        FileHandle handle = resolve(CATALOG_PATH);
        if (handle == null || !handle.exists()) {
            log("props.json not found at " + CATALOG_PATH + "; prop catalogue is empty.");
            return;
        }

        try {
            JsonValue root = new JsonReader().parse(handle);
            JsonValue list = root.get("props");
            if (list == null) {
                log("props.json has no 'props' array.");
                return;
            }
            for (JsonValue entry = list.child; entry != null; entry = entry.next) {
                PropDefinition def = parse(entry);
                if (def != null) {
                    props.put(def.getId(), def);
                }
            }
            log("Loaded " + props.size() + " prop definitions.");
        } catch (Exception e) {
            log("Failed to parse props.json: " + e.getMessage());
        }
    }

    private PropDefinition parse(JsonValue entry) {
        String id = entry.getString("id", null);
        if (id == null || id.isEmpty()) return null;

        String asset = entry.getString("asset", null);
        String modeName = entry.getString("renderMode", "SPRITE");
        PropDefinition.RenderMode mode;
        try {
            mode = PropDefinition.RenderMode.valueOf(modeName);
        } catch (IllegalArgumentException e) {
            mode = PropDefinition.RenderMode.SPRITE;
        }

        float scaleX = entry.getFloat("scaleX", 1.0f);
        float scaleY = entry.getFloat("scaleY", 1.0f);
        boolean passable = entry.getBoolean("passable", false);
        float offsetY = entry.getFloat("pixelOffsetY", 0f);

        Color emissive = null;
        JsonValue tint = entry.get("emissiveTint");
        if (tint != null) {
            emissive = new Color(
                    tint.getFloat("r", 1f),
                    tint.getFloat("g", 1f),
                    tint.getFloat("b", 1f),
                    1f);
        }

        int burnDamage = entry.getInt("burnDamage", 0);

        return new PropDefinition(id, asset, mode, scaleX, scaleY, passable, emissive, offsetY, burnDamage);
    }

    /** Returns null when the id is unknown. */
    public PropDefinition get(String id) {
        load();
        if (id == null) return null;
        return props.get(id);
    }

    public boolean contains(String id) {
        return get(id) != null;
    }

    public Map<String, PropDefinition> all() {
        load();
        return Collections.unmodifiableMap(props);
    }

    public int size() {
        load();
        return props.size();
    }

    /**
     * Resolves a path through Gdx.files when a backend is up, and falls back to
     * the working-directory assets folder so headless tests can read the same
     * catalogue the game reads.
     */
    public static FileHandle resolve(String path) {
        if (Gdx.files != null) {
            FileHandle internal = Gdx.files.internal(path);
            if (internal.exists()) return internal;
        }
        java.io.File direct = new java.io.File("assets/" + path);
        if (direct.exists()) return new com.badlogic.gdx.files.FileHandle(direct);
        java.io.File nested = new java.io.File("../assets/" + path);
        if (nested.exists()) return new com.badlogic.gdx.files.FileHandle(nested);
        return null;
    }

    private static void log(String message) {
        if (Gdx.app != null) {
            Gdx.app.log("PropCatalog", message);
        }
    }
}
