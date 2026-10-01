package com.bpm.minotaur.gamedata.spells;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which animation a spell shows, read from {@code assets/data/spellfx.json} so it can be retuned
 * without touching code.
 *
 * <p>An impact is chosen by the spell's {@link VisualArchetype}. A spell the player casts on
 * themselves is chosen by its name first (a Cure shows healing, a Haste shows speed lines) and by
 * its archetype only if the name says nothing, so a Detect Magic that shares the holy archetype
 * with Heal does not glow like one.
 */
public final class SpellFx {

    public static final class Impact {
        public final String clip;
        public final float scale;

        Impact(String clip, float scale) {
            this.clip = clip;
            this.scale = scale;
        }
    }

    private final Map<VisualArchetype, Impact> impacts = new EnumMap<>(VisualArchetype.class);
    private final List<String[]> nameWords = new ArrayList<>();
    private final List<List<String>> nameClips = new ArrayList<>();
    private final Map<VisualArchetype, List<String>> archetypeSelf = new EnumMap<>(VisualArchetype.class);
    private float selfSize = 420f;
    private String castClip;
    private float castSize = 220f;
    private String warpDepart;
    private String warpArrive;
    private float warpSize = 520f;

    private SpellFx() {
    }

    public static SpellFx parse(JsonValue root) {
        SpellFx fx = new SpellFx();

        JsonValue imp = root.get("impacts");
        for (JsonValue i = imp == null ? null : imp.child; i != null; i = i.next) {
            fx.impacts.put(VisualArchetype.valueOf(i.name), new Impact(i.getString("clip"), i.getFloat("scale", 1f)));
        }

        JsonValue self = root.get("self");
        if (self != null) {
            fx.selfSize = self.getFloat("size", fx.selfSize);
            JsonValue names = self.get("byName");
            for (JsonValue e = names == null ? null : names.child; e != null; e = e.next) {
                fx.nameWords.add(e.get("words").asStringArray());
                fx.nameClips.add(Collections.unmodifiableList(java.util.Arrays.asList(e.get("clips").asStringArray())));
            }
            JsonValue arch = self.get("byArchetype");
            for (JsonValue a = arch == null ? null : arch.child; a != null; a = a.next) {
                fx.archetypeSelf.put(VisualArchetype.valueOf(a.name),
                        Collections.unmodifiableList(java.util.Arrays.asList(a.asStringArray())));
            }
        }

        JsonValue cast = root.get("cast");
        if (cast != null) {
            fx.castClip = cast.getString("clip", null);
            fx.castSize = cast.getFloat("size", fx.castSize);
        }
        JsonValue warp = root.get("warp");
        if (warp != null) {
            fx.warpDepart = warp.getString("depart", null);
            fx.warpArrive = warp.getString("arrive", null);
            fx.warpSize = warp.getFloat("size", fx.warpSize);
        }
        return fx;
    }

    /** The impact effect for an archetype, or null if it has none. */
    public Impact impactFor(VisualArchetype archetype) {
        return archetype == null ? null : impacts.get(archetype);
    }

    /** The clips to play over the view when the player casts this on themselves; empty if none fit. */
    public List<String> selfClipsFor(String spellName, VisualArchetype archetype) {
        // Match whole-word prefixes, so "ward" finds Ward but not Toward and "might" not Almighty.
        String name = spellName == null ? "" : " " + spellName.toLowerCase().replaceAll("[^a-z]+", " ");
        for (int i = 0; i < nameWords.size(); i++) {
            for (String word : nameWords.get(i)) {
                if (name.contains(" " + word)) {
                    return nameClips.get(i);
                }
            }
        }
        List<String> byArchetype = archetype == null ? null : archetypeSelf.get(archetype);
        return byArchetype != null ? byArchetype : Collections.<String>emptyList();
    }

    public Set<String> allSelfClips() {
        Set<String> all = new LinkedHashSet<>();
        for (List<String> list : nameClips) {
            all.addAll(list);
        }
        for (List<String> list : archetypeSelf.values()) {
            all.addAll(list);
        }
        return all;
    }

    public float selfSize() {
        return selfSize;
    }

    public String castClip() {
        return castClip;
    }

    public float castSize() {
        return castSize;
    }

    public String warpDepartClip() {
        return warpDepart;
    }

    public String warpArriveClip() {
        return warpArrive;
    }

    public float warpSize() {
        return warpSize;
    }

    // --- runtime ---

    private static SpellFx instance;

    public static synchronized SpellFx getInstance() {
        if (instance == null) {
            instance = loadOrEmpty(); // cached even when empty, so a missing file is logged once, not per cast
        }
        return instance;
    }

    /** The shipped mapping, or an empty one if the data cannot be read: a missing effect never breaks a cast. */
    static SpellFx loadOrEmpty() {
        try {
            if (Gdx.files != null) {
                return parse(new JsonReader().parse(Gdx.files.internal("data/spellfx.json")));
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("SpellFx", "Cannot read data/spellfx.json; spell effects are disabled", e);
            }
        }
        return new SpellFx();
    }
}
