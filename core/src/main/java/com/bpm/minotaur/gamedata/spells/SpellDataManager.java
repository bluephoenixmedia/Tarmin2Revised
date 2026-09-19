package com.bpm.minotaur.gamedata.spells;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads and manages all spell definitions from assets/data/spells.json.
 */
public class SpellDataManager {

    private static SpellDataManager instance;

    public static SpellDataManager getInstance() {
        if (instance == null) {
            instance = new SpellDataManager();
        }
        return instance;
    }

    private final ObjectMap<String, SpellTemplate> spells = new ObjectMap<>();
    private boolean loaded = false;

    public SpellDataManager() {
    }

    public void load() {
        spells.clear();
        Json json = new Json();
        json.setIgnoreUnknownFields(true);

        FileHandle file = null;
        if (Gdx.files != null) {
            try {
                file = Gdx.files.local("assets/data/spells.json");
                if (file == null || !file.exists()) {
                    file = Gdx.files.internal("data/spells.json");
                }
                if (file == null || !file.exists()) {
                    file = Gdx.files.local("../assets/data/spells.json");
                }
            } catch (Exception ignored) {
            }
        }
        if (file == null || !file.exists()) {
            // Non-Gdx fallback (e.g. headless / plain java tests)
            java.io.File diskFile = new java.io.File("assets/data/spells.json");
            if (!diskFile.exists()) {
                diskFile = new java.io.File("../assets/data/spells.json");
            }
            if (diskFile.exists()) {
                file = new com.badlogic.gdx.files.FileHandle(diskFile);
            }
        }

        if (file == null || !file.exists()) {
            if (Gdx.app != null) {
                Gdx.app.error("SpellDataManager", "spells.json not found! Initializing fallback spells.");
            }
            initFallbackSpells();
            loaded = true;
            return;
        }

        try {
            JsonValue root = new JsonReader().parse(file);
            for (JsonValue entry = root.child; entry != null; entry = entry.next) {
                SpellTemplate template = json.readValue(SpellTemplate.class, entry);
                if (template.id == null || template.id.isEmpty()) {
                    template.id = entry.name;
                }
                spells.put(entry.name.toUpperCase(), template);
                spells.put(template.id.toUpperCase(), template);
            }
            if (Gdx.app != null) {
                Gdx.app.log("SpellDataManager", "Loaded " + spells.size + " spell entries from spells.json");
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("SpellDataManager", "Error parsing spells.json: " + e.getMessage(), e);
            }
            initFallbackSpells();
        }
        loaded = true;
    }

    private void initFallbackSpells() {
        SpellTemplate magicArrow = new SpellTemplate();
        magicArrow.id = "MAGIC_ARROW";
        magicArrow.name = "Magic Arrow";
        magicArrow.level = 1;
        magicArrow.school = "evocation";
        magicArrow.mpCost = 3;
        magicArrow.range = 8;
        magicArrow.targetType = "PROJECTILE";
        magicArrow.damageDice = "1d8+1";
        magicArrow.damageType = "FORCE";
        magicArrow.visualArchetype = "PROJECTILE";
        magicArrow.description = "Shoots a magical bolt of energy.";
        spells.put(magicArrow.id, magicArrow);

        SpellTemplate heal = new SpellTemplate();
        heal.id = "HEAL";
        heal.name = "Cure Wounds";
        heal.level = 1;
        heal.school = "evocation";
        heal.mpCost = 4;
        heal.range = 1;
        heal.targetType = "SELF";
        heal.damageDice = "1d8+3";
        heal.damageType = "RADIANT";
        heal.visualArchetype = "SELF_BUFF";
        heal.description = "Heals wounds with soothing divine light.";
        spells.put(heal.id, heal);

        SpellTemplate fireball = new SpellTemplate();
        fireball.id = "FIREBALL";
        fireball.name = "Fireball";
        fireball.level = 3;
        fireball.school = "evocation";
        fireball.mpCost = 8;
        fireball.range = 12;
        fireball.targetType = "BURST";
        fireball.damageDice = "8d6";
        fireball.damageType = "FIRE";
        fireball.visualArchetype = "BURST";
        fireball.description = "A bright streak flashes to a point and blooms into an explosion of flame.";
        spells.put(fireball.id, fireball);
    }

    public static SpellTemplate getSpell(String id) {
        return getInstance().getSpellById(id);
    }

    public SpellTemplate getSpellById(String id) {
        if (!loaded) {
            load();
        }
        if (id == null) return null;
        return spells.get(id.toUpperCase());
    }

    public List<SpellTemplate> getAllSpells() {
        if (!loaded) {
            load();
        }
        List<SpellTemplate> list = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (SpellTemplate t : spells.values()) {
            if (t != null && t.id != null && seen.add(t.id)) {
                list.add(t);
            }
        }
        return list;
    }

    public List<SpellTemplate> getSpellsByLevel(int level) {
        List<SpellTemplate> result = new ArrayList<>();
        for (SpellTemplate s : getAllSpells()) {
            if (s.level == level) {
                result.add(s);
            }
        }
        return result;
    }

    public List<SpellTemplate> getSpellsBySchoolAndMaxLevel(String school, int maxLevel) {
        List<SpellTemplate> result = new ArrayList<>();
        for (SpellTemplate s : getAllSpells()) {
            if (s.level <= maxLevel && (school == null || (s.school != null && s.school.equalsIgnoreCase(school)))) {
                result.add(s);
            }
        }
        return result;
    }

    public boolean isLoaded() {
        return loaded;
    }
}
