package com.bpm.minotaur.gamedata.history.beast;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.List;

/** The archetypes and rolled traits of megabeasts, from {@code data/megabeasts.json} (plan D33). */
public final class MegabeastCatalog {

    public static final String DATA_PATH = "data/megabeasts.json";

    public static class Archetype {
        public String id;
        public String title;
        /** A monsters.json type: the body, and so the sprite. */
        public String body;
        public int hp;
        public float scaleY = 2f;
    }

    public static class Material {
        public String id;
        public String adjective;
        public int armor;
        public float hpMult = 1f;
        public int damage;
    }

    public static class Breath {
        public String id;
        public String phrase;
        public int damage;
    }

    public static class Weakness {
        /** A {@code DamageType} name the beast takes extra harm from. */
        public String damageType;
        public String phrase;
    }

    private static MegabeastCatalog instance;

    public final List<Archetype> archetypes = new ArrayList<>();
    public final List<Material> materials = new ArrayList<>();
    public final List<Breath> breaths = new ArrayList<>();
    public final List<Weakness> weaknesses = new ArrayList<>();
    public final List<String> givenNames = new ArrayList<>();

    private MegabeastCatalog() {
    }

    public static synchronized MegabeastCatalog getInstance() {
        if (instance == null) {
            FileHandle file = Gdx.files != null ? Gdx.files.internal(DATA_PATH) : null;
            if (file == null || !file.exists()) throw new IllegalStateException(DATA_PATH + " not found");
            instance = fromJson(file.readString("UTF-8"));
        }
        return instance;
    }

    public static MegabeastCatalog fromJson(String text) {
        MegabeastCatalog c = new MegabeastCatalog();
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        JsonValue root = new JsonReader().parse(text);
        for (JsonValue v : root.get("archetypes")) c.archetypes.add(json.readValue(Archetype.class, v));
        for (JsonValue v : root.get("materials")) c.materials.add(json.readValue(Material.class, v));
        for (JsonValue v : root.get("breaths")) c.breaths.add(json.readValue(Breath.class, v));
        for (JsonValue v : root.get("weaknesses")) c.weaknesses.add(json.readValue(Weakness.class, v));
        for (JsonValue v : root.get("givenNames")) c.givenNames.add(v.asString());
        return c;
    }

    public Archetype archetype(String id) {
        for (Archetype a : archetypes) if (a.id.equals(id)) return a;
        return null;
    }

    public Material material(String id) {
        for (Material m : materials) if (m.id.equals(id)) return m;
        return null;
    }

    public Breath breath(String id) {
        for (Breath b : breaths) if (b.id.equals(id)) return b;
        return null;
    }
}
