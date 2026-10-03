package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serves the death animations in {@code assets/data/death_animations.json},
 * keyed by the living monster's texture path.
 *
 * <p>A monster with no entry keeps the old behaviour (its sprite laid down), so
 * a missing or malformed file degrades rather than breaking a kill.
 */
public class DeathAnimationCatalog {

    public static final String CATALOG_PATH = "data/death_animations.json";

    private static DeathAnimationCatalog instance;

    private final Map<String, DeathAnimation> byMonsterTexture = new LinkedHashMap<>();
    private boolean loaded = false;

    public static DeathAnimationCatalog getInstance() {
        if (instance == null) {
            instance = new DeathAnimationCatalog();
        }
        return instance;
    }

    /** Test seam: drops the singleton so a test can force a reload. */
    public static void resetInstance() {
        instance = null;
    }

    public void load() {
        if (loaded) return;
        loaded = true;

        FileHandle handle = resolve(CATALOG_PATH);
        if (handle == null || !handle.exists()) {
            log(CATALOG_PATH + " not found; monsters fall back to a laid-down sprite.");
            return;
        }
        try {
            JsonValue root = new JsonReader().parse(handle);
            float defaultDuration = root.getFloat("frameDuration", 0.1f);
            JsonValue list = root.get("animations");
            if (list == null) return;
            for (JsonValue e = list.child; e != null; e = e.next) {
                String monster = e.getString("monster", null);
                String sheet = e.getString("sheet", null);
                JsonValue rects = e.get("frames");
                if (monster == null || sheet == null || rects == null || rects.size == 0) continue;
                int[][] frames = new int[rects.size][];
                int i = 0;
                for (JsonValue r = rects.child; r != null; r = r.next) {
                    frames[i++] = r.asIntArray();
                }
                byMonsterTexture.put(monster, new DeathAnimation(monster, sheet, frames,
                        e.getFloat("frameDuration", defaultDuration)));
            }
            log("Loaded " + byMonsterTexture.size() + " death animations.");
        } catch (Exception ex) {
            log("Failed to parse " + CATALOG_PATH + ": " + ex.getMessage());
        }
    }

    /** Null when this monster has no death art. */
    public DeathAnimation forMonsterTexture(String monsterTexturePath) {
        load();
        return monsterTexturePath == null ? null : byMonsterTexture.get(monsterTexturePath);
    }

    public Iterable<DeathAnimation> all() {
        load();
        return byMonsterTexture.values();
    }

    private static FileHandle resolve(String path) {
        return com.bpm.minotaur.gamedata.prop.PropCatalog.resolve(path);
    }

    private static void log(String msg) {
        if (Gdx.app != null) Gdx.app.log("DeathAnimationCatalog", msg);
    }
}
