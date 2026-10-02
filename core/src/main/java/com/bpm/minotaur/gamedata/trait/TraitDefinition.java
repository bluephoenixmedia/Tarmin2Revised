package com.bpm.minotaur.gamedata.trait;

import com.bpm.minotaur.gamedata.effects.StatusEffectType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * One personality trait: a good side, a bad side, and the numbers that make them true.
 *
 * <p>The numbers live in {@link #modifiers}. A key ending in {@code Mult} scales something (1 means no
 * change); every other key adds to it (0 means no change). {@link TraitEffects} reads them at the places
 * the game already has for that kind of number.
 */
public final class TraitDefinition {

    public final String id;
    public final String name;
    public final String good;
    public final String bad;
    /** A trait whose every effect is built. A disabled one stays in the data but is never offered. */
    public final boolean enabled;
    public final Map<String, Float> modifiers;
    /** Status effects the player cannot suffer while holding this trait. */
    public final Set<StatusEffectType> blocked;

    public TraitDefinition(String id, String name, String good, String bad, boolean enabled,
            Map<String, Float> modifiers, Set<StatusEffectType> blocked) {
        this.id = id;
        this.name = name;
        this.good = good;
        this.bad = bad;
        this.enabled = enabled;
        this.modifiers = Collections.unmodifiableMap(new HashMap<>(modifiers));
        this.blocked = blocked.isEmpty() ? Collections.<StatusEffectType>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(blocked));
    }

    public float mod(String key, float fallback) {
        Float v = modifiers.get(key);
        return v == null ? fallback : v;
    }
}
