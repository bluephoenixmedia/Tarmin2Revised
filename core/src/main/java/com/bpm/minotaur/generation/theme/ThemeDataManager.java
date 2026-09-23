package com.bpm.minotaur.generation.theme;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import com.bpm.minotaur.gamedata.prop.PropCatalog;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code assets/data/themes.json} into {@link ThemeDefinition}s.
 *
 * <p>A theme with no definition returns null, which the decorator treats as
 * "decorate nothing". {@code ThemeContractTest} asserts that never happens for
 * a shipped theme.
 */
public class ThemeDataManager {

    public static final String THEMES_PATH = "data/themes.json";

    private static ThemeDataManager instance;

    private final Map<ChunkTheme, ThemeDefinition> definitions = new EnumMap<>(ChunkTheme.class);
    private boolean loaded = false;

    public static ThemeDataManager getInstance() {
        if (instance == null) {
            instance = new ThemeDataManager();
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

        FileHandle handle = PropCatalog.resolve(THEMES_PATH);
        if (handle == null || !handle.exists()) {
            log("themes.json not found at " + THEMES_PATH + "; no themes defined.");
            return;
        }

        try {
            JsonValue root = new JsonReader().parse(handle);
            JsonValue list = root.get("themes");
            if (list == null) {
                log("themes.json has no 'themes' array.");
                return;
            }
            for (JsonValue entry = list.child; entry != null; entry = entry.next) {
                ThemeDefinition def = parse(entry);
                if (def != null) {
                    definitions.put(def.getTheme(), def);
                }
            }
            log("Loaded " + definitions.size() + " theme definitions.");
        } catch (Exception e) {
            log("Failed to parse themes.json: " + e.getMessage());
        }
    }

    private ThemeDefinition parse(JsonValue entry) {
        String id = entry.getString("id", null);
        if (id == null) return null;

        ChunkTheme theme;
        try {
            theme = ChunkTheme.valueOf(id);
        } catch (IllegalArgumentException e) {
            log("themes.json references unknown theme id '" + id + "'.");
            return null;
        }

        int crestAward = entry.getInt("crestAward", 1);

        ThemeObjectiveKind objective = null;
        String objName = entry.getString("objective", null);
        if (objName != null) {
            try {
                objective = ThemeObjectiveKind.valueOf(objName);
            } catch (IllegalArgumentException e) {
                log("Unknown objective '" + objName + "' on theme " + id);
            }
        }

        ThemeHazardKind hazard = null;
        float hazardDensity = 0f;
        JsonValue hazardNode = entry.get("hazard");
        if (hazardNode != null) {
            String kind = hazardNode.getString("kind", null);
            if (kind != null) {
                try {
                    hazard = ThemeHazardKind.valueOf(kind);
                } catch (IllegalArgumentException e) {
                    log("Unknown hazard '" + kind + "' on theme " + id);
                }
            }
            hazardDensity = hazardNode.getFloat("density", 0.2f);
        }

        Color fogTint = null;
        JsonValue fog = entry.get("fogTint");
        if (fog != null) {
            fogTint = new Color(
                    fog.getFloat("r", 1f),
                    fog.getFloat("g", 1f),
                    fog.getFloat("b", 1f),
                    1f);
        }

        float fogDistance = entry.getFloat("fogDistance", 0f);
        String stinger = entry.getString("stinger", null);
        String runeTexture = entry.getString("runeTexture", null);
        float propDensity = entry.getFloat("propDensity", 0.08f);
        int objectiveCount = entry.getInt("objectiveCount", 1);

        MonsterType championType = null;
        String champ = entry.getString("championType", null);
        if (champ != null) {
            try {
                championType = MonsterType.valueOf(champ);
            } catch (IllegalArgumentException e) {
                log("Unknown championType '" + champ + "' on theme " + id);
            }
        }
        int championHpBonus = entry.getInt("championHpBonus", 40);

        List<ThemeDefinition.PropEntry> props = new ArrayList<>();
        JsonValue propList = entry.get("props");
        if (propList != null) {
            for (JsonValue p = propList.child; p != null; p = p.next) {
                String propId = p.getString("propId", null);
                if (propId == null) continue;
                props.add(new ThemeDefinition.PropEntry(propId, p.getInt("weight", 10)));
            }
        }

        List<ThemeDefinition.MonsterEntry> monsters = new ArrayList<>();
        JsonValue monsterList = entry.get("monsters");
        if (monsterList != null) {
            for (JsonValue m = monsterList.child; m != null; m = m.next) {
                String typeName = m.getString("type", null);
                if (typeName == null) continue;
                try {
                    monsters.add(new ThemeDefinition.MonsterEntry(
                            MonsterType.valueOf(typeName),
                            m.getInt("weight", 10),
                            m.getInt("hpBonus", 0)));
                } catch (IllegalArgumentException e) {
                    log("Unknown monster type '" + typeName + "' on theme " + id);
                }
            }
        }

        return new ThemeDefinition(theme, crestAward, objective, hazard, hazardDensity,
                fogTint, fogDistance, stinger, propDensity, props, monsters, championType,
                championHpBonus, objectiveCount, runeTexture);
    }

    /** Returns null when the theme has no definition on disk. */
    public ThemeDefinition get(ChunkTheme theme) {
        load();
        if (theme == null) return null;
        return definitions.get(theme);
    }

    public int size() {
        load();
        return definitions.size();
    }

    private static void log(String message) {
        if (Gdx.app != null) {
            Gdx.app.log("ThemeDataManager", message);
        }
    }
}
