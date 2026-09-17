package com.bpm.minotaur.generation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and caches handcrafted ModularChunk prefabs and their 4-way rotations.
 */
public class ModularChunkLoader {

    public static final String[] DEFAULT_CHUNK_FILES = {
        "panopticon_arena_01.json",
        "choke_passage_01.json",
        "chasm_walkway_01.json",
        "furnace_trap_01.json",
        "blind_vault_01.json",
        "breather_sanctuary_01.json",
        "flooded_crossroads_01.json",
        "descent_shaft_01.json",
        "asymmetric_flank_01.json",
        "debris_field_01.json"
    };

    private static ModularChunkLoader instance;

    public static synchronized ModularChunkLoader getInstance() {
        if (instance == null) {
            instance = new ModularChunkLoader();
            instance.loadAllChunks();
        }
        return instance;
    }

    private final Map<String, ModularChunk> baseChunks = new HashMap<>();
    private final Map<String, List<ModularChunk>> chunksByArchetype = new HashMap<>();
    private final List<ModularChunk> allRotatedChunks = new ArrayList<>();

    public void loadAllChunks() {
        baseChunks.clear();
        chunksByArchetype.clear();
        allRotatedChunks.clear();

        for (String fileName : DEFAULT_CHUNK_FILES) {
            FileHandle handle = resolveFile("data/chunks/" + fileName);
            if (handle != null && handle.exists()) {
                ModularChunk chunk = parseChunk(handle);
                if (chunk != null) {
                    registerChunk(chunk);
                }
            } else {
                if (Gdx.app != null) {
                    Gdx.app.error("ModularChunkLoader", "Chunk file not found: " + fileName);
                }
            }
        }
    }

    private FileHandle resolveFile(String internalPath) {
        if (Gdx.files != null) {
            FileHandle h = Gdx.files.internal(internalPath);
            if (h.exists()) return h;
        }
        // Fallback for headless test environments or IDE execution
        FileHandle localHandle = new FileHandle("assets/" + internalPath);
        if (localHandle.exists()) return localHandle;

        File direct = new File(internalPath);
        if (direct.exists()) return new FileHandle(direct);

        return null;
    }

    public ModularChunk parseChunk(FileHandle handle) {
        try {
            JsonReader reader = new JsonReader();
            JsonValue root = reader.parse(handle);

            ModularChunk chunk = new ModularChunk();
            chunk.setId(root.getString("id", handle.nameWithoutExtension()));
            chunk.setArchetype(root.getString("archetype", "CHOKE").toUpperCase());
            chunk.setMinStrata(root.getInt("minStrata", 1));
            chunk.setMaxStrata(root.getInt("maxStrata", 10));
            chunk.setPacingWeight(root.getInt("pacingWeight", 100));

            JsonValue sockets = root.get("sockets");
            if (sockets != null) {
                Map<String, String> sockMap = new HashMap<>();
                for (JsonValue entry = sockets.child; entry != null; entry = entry.next) {
                    sockMap.put(entry.name.toUpperCase(), entry.asString().toUpperCase());
                }
                chunk.setSockets(sockMap);
            }

            JsonValue layoutVal = root.get("layout");
            if (layoutVal != null && layoutVal.isArray()) {
                String[] layout = new String[layoutVal.size];
                for (int i = 0; i < layoutVal.size; i++) {
                    layout[i] = layoutVal.getString(i);
                }
                chunk.setLayout(layout);
            }

            chunk.setHazardSlots(root.getInt("hazardSlots", 0));
            chunk.setDecalSlots(root.getInt("decalSlots", 0));

            return chunk;
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("ModularChunkLoader", "Failed to parse chunk " + handle.name(), e);
            }
            return null;
        }
    }

    public void registerChunk(ModularChunk base) {
        baseChunks.put(base.getId(), base);
        chunksByArchetype.computeIfAbsent(base.getArchetype(), k -> new ArrayList<>()).add(base);

        // Precompute 4-way rotations (0, 1, 2, 3)
        for (int r = 0; r < 4; r++) {
            ModularChunk rotated = base.createRotated(r);
            allRotatedChunks.add(rotated);
        }
    }

    public List<ModularChunk> getBaseChunks() {
        return new ArrayList<>(baseChunks.values());
    }

    public List<ModularChunk> getAllRotatedChunks() {
        return allRotatedChunks;
    }

    public List<ModularChunk> getRotatedChunksForArchetype(String archetype, int strata) {
        String archUpper = archetype.toUpperCase();
        List<ModularChunk> results = new ArrayList<>();
        for (ModularChunk chunk : allRotatedChunks) {
            if (chunk.getArchetype().equalsIgnoreCase(archUpper) &&
                strata >= chunk.getMinStrata() && strata <= chunk.getMaxStrata()) {
                results.add(chunk);
            }
        }
        return results;
    }

    /**
     * Finds candidates among all precomputed rotated chunks matching specified socket requirements.
     * Pass null or "*" to accept any socket type.
     */
    public List<ModularChunk> findMatchingChunks(String northReq, String eastReq, String southReq, String westReq,
                                                 String archetypeFilter, int strata) {
        List<ModularChunk> matches = new ArrayList<>();
        for (ModularChunk c : allRotatedChunks) {
            if (archetypeFilter != null && !c.getArchetype().equalsIgnoreCase(archetypeFilter)) {
                continue;
            }
            if (strata > 0 && (strata < c.getMinStrata() || strata > c.getMaxStrata())) {
                continue;
            }
            if (northReq != null && !northReq.equals("*") && !ModularChunk.areSocketsCompatible(c.getSocket("NORTH"), northReq)) {
                continue;
            }
            if (eastReq != null && !eastReq.equals("*") && !ModularChunk.areSocketsCompatible(c.getSocket("EAST"), eastReq)) {
                continue;
            }
            if (southReq != null && !southReq.equals("*") && !ModularChunk.areSocketsCompatible(c.getSocket("SOUTH"), southReq)) {
                continue;
            }
            if (westReq != null && !westReq.equals("*") && !ModularChunk.areSocketsCompatible(c.getSocket("WEST"), westReq)) {
                continue;
            }
            matches.add(c);
        }
        return matches;
    }
}
