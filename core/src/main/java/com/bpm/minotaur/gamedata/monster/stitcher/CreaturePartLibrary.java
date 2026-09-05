package com.bpm.minotaur.gamedata.monster.stitcher;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and caches all part Model handles from parts_manifest.json.
 * Must be initialised after all AssetManager loads have completed.
 */
public class CreaturePartLibrary implements Disposable {

    private static final String MANIFEST_PATH = "data/creatures/parts/parts_manifest.json";

    private final AssetManager assetManager;
    private final Map<String, CreaturePartDef> partDefs = new HashMap<>();
    // Procedurally-built models registered at runtime (not loaded from disk).
    private final Map<String, Model> runtimeModels = new HashMap<>();

    public CreaturePartLibrary(AssetManager assetManager) {
        this.assetManager = assetManager;
        loadManifest();
    }

    /** Queue all part models for loading — call before AssetManager.finishLoading(). */
    public void queueAssets() {
        for (CreaturePartDef def : partDefs.values()) {
            if (def.model == null || def.model.isEmpty()) continue;
            if (runtimeModels.containsKey(def.id)) continue;
            if (!Gdx.files.internal(def.model).exists()) continue;
            if (assetManager.isLoaded(def.model, Model.class)) continue;
            assetManager.load(def.model, Model.class);
        }
    }

    /**
     * Returns the Model for the given part id.
     * Runtime-registered (procedural) models take priority over file-loaded ones.
     * Returns null if neither source has the part.
     */
    public Model get(String partId) {
        Model runtime = runtimeModels.get(partId);
        if (runtime != null) return runtime;

        CreaturePartDef def = partDefs.get(partId);
        if (def == null) {
            Gdx.app.error("CreaturePartLibrary", "Unknown part id: " + partId);
            return null;
        }
        if (!assetManager.isLoaded(def.model, Model.class)) {
            Gdx.app.error("CreaturePartLibrary", "Model not loaded for part: " + partId + " path: " + def.model);
            return null;
        }
        return assetManager.get(def.model, Model.class);
    }

    /**
     * Registers a procedurally-built Model under the given id.
     * The library takes ownership and will dispose it on {@link #dispose()}.
     * If a model is already registered under this id it is disposed and replaced.
     * The part will NOT appear in {@link #getPartIdsForSocket} results because
     * the synthetic def has an empty socket — use the overload below when socket
     * membership matters.
     */
    public void registerModel(String partId, Model model) {
        Model old = runtimeModels.put(partId, model);
        if (old != null) old.dispose();
        partDefs.putIfAbsent(partId, syntheticDef(partId));
    }

    /**
     * Registers a procedurally-built Model with an explicit socket so it is
     * returned by {@link #getPartIdsForSocket}.  Use this for canonical parts
     * (e.g. the oval torso) that must participate in random assembly.
     */
    public void registerModel(String partId, String socket, Model model) {
        Model old = runtimeModels.put(partId, model);
        if (old != null) old.dispose();
        if (!partDefs.containsKey(partId)) {
            CreaturePartDef def = syntheticDef(partId);
            def.socket = socket;
            partDefs.put(partId, def);
        } else {
            partDefs.get(partId).socket = socket;
        }
    }

    @Override
    public void dispose() {
        for (Model m : runtimeModels.values()) m.dispose();
        runtimeModels.clear();
    }

    public boolean hasPart(String partId) {
        return partDefs.containsKey(partId);
    }

    /** Returns the unit scale for a part (1.0 for procedural, 0.01 for Blender FBX parts). */
    public float getUnitScale(String partId) {
        CreaturePartDef def = partDefs.get(partId);
        return def != null ? def.unitScale : 1.0f;
    }

    /** Returns all known part IDs in sorted order. */
    public List<String> getPartIds() {
        List<String> ids = new ArrayList<>(partDefs.keySet());
        java.util.Collections.sort(ids);
        return ids;
    }

    public List<String> getPartIdsForSocket(String socket) {
        List<String> ids = new ArrayList<>();
        for (CreaturePartDef def : partDefs.values()) {
            if (socket.equals(def.socket)) ids.add(def.id);
        }
        return ids;
    }

    // -------------------------------------------------------------------------

    private static class Manifest {
        public List<CreaturePartDef> parts = new ArrayList<>();
    }

    private static CreaturePartDef syntheticDef(String partId) {
        CreaturePartDef def = new CreaturePartDef();
        def.id     = partId;
        def.model  = "";   // no file path — resolved via runtimeModels
        def.socket = "";
        return def;
    }

    private void loadManifest() {
        if (!Gdx.files.internal(MANIFEST_PATH).exists()) {
            Gdx.app.log("CreaturePartLibrary", "No parts manifest found at " + MANIFEST_PATH + " — stitcher disabled");
            return;
        }
        try {
            Json json = new Json();
            Manifest manifest = json.fromJson(Manifest.class, Gdx.files.internal(MANIFEST_PATH));
            for (CreaturePartDef def : manifest.parts) {
                partDefs.put(def.id, def);
            }
            Gdx.app.log("CreaturePartLibrary", "Loaded " + partDefs.size() + " part definitions");
        } catch (Exception e) {
            Gdx.app.error("CreaturePartLibrary", "Failed to parse " + MANIFEST_PATH + ": " + e.getMessage());
        }
    }
}
