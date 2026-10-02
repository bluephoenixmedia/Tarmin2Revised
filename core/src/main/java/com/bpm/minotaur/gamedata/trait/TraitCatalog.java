package com.bpm.minotaur.gamedata.trait;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Every trait, read from {@code assets/data/traits.json}, and the rules for offering them.
 *
 * <p>A trait is offered three at a time, never the one the player already holds, and only if it is
 * {@link TraitDefinition#enabled}. The choice comes on a new game and again after every
 * {@value #RESPAWNS_BETWEEN_CHOICES} respawns.
 */
public final class TraitCatalog {

    public static final int RESPAWNS_BETWEEN_CHOICES = 5;
    public static final int OFFER_SIZE = 3;

    private final Map<String, TraitDefinition> traits = new LinkedHashMap<>();

    public static TraitCatalog parse(JsonValue root) {
        TraitCatalog catalog = new TraitCatalog();
        JsonValue list = root == null ? null : root.get("traits");
        for (JsonValue t = list == null ? null : list.child; t != null; t = t.next) {
            Map<String, Float> mods = new LinkedHashMap<>();
            JsonValue m = t.get("modifiers");
            for (JsonValue e = m == null ? null : m.child; e != null; e = e.next) {
                mods.put(e.name, e.asFloat());
            }
            Set<StatusEffectType> blocked = EnumSet.noneOf(StatusEffectType.class);
            JsonValue b = t.get("blocks");
            for (JsonValue e = b == null ? null : b.child; e != null; e = e.next) {
                blocked.add(StatusEffectType.valueOf(e.asString()));
            }
            String id = t.getString("id");
            catalog.traits.put(id, new TraitDefinition(id, t.getString("name"), t.getString("good"),
                    t.getString("bad"), t.getBoolean("enabled", true), mods, blocked));
        }
        return catalog;
    }

    public TraitDefinition get(String id) {
        return id == null ? null : traits.get(id);
    }

    public List<TraitDefinition> all() {
        return Collections.unmodifiableList(new ArrayList<>(traits.values()));
    }

    public List<TraitDefinition> offerable() {
        List<TraitDefinition> out = new ArrayList<>();
        for (TraitDefinition t : traits.values()) {
            if (t.enabled) {
                out.add(t);
            }
        }
        return out;
    }

    /** Three random offerable traits other than the current one (fewer only if the pool is that small). */
    public List<String> pickOffer(String currentId, Random rng) {
        List<TraitDefinition> pool = offerable();
        List<String> ids = new ArrayList<>();
        for (TraitDefinition t : pool) {
            if (!t.id.equals(currentId)) {
                ids.add(t.id);
            }
        }
        Collections.shuffle(ids, rng);
        return new ArrayList<>(ids.subList(0, Math.min(OFFER_SIZE, ids.size())));
    }

    /** Counts one respawn; true when the player is due a new choice. */
    public static boolean choiceDue(int respawnsSinceChoice) {
        return respawnsSinceChoice >= RESPAWNS_BETWEEN_CHOICES;
    }

    // --- runtime ---

    private static TraitCatalog instance;

    public static synchronized TraitCatalog getInstance() {
        if (instance == null) {
            instance = loadOrEmpty();
        }
        return instance;
    }

    static TraitCatalog loadOrEmpty() {
        try {
            if (Gdx.files != null) {
                return parse(new JsonReader().parse(Gdx.files.internal("data/traits.json")));
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("TraitCatalog", "Cannot read data/traits.json; no traits will be offered", e);
            }
        }
        return new TraitCatalog();
    }
}
