package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.managers.UnlockManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Loot tiers (#196). Tier 0 is the base pool, findable from life one; unlocks add tiers 1 and up.
 *
 * <p>Before #196 an item was gated when its score reached 50, which locked every real weapon and
 * every body armour, cloak and shield on a fresh save. Now the base pool in
 * {@code data/loot_pool.json} names the plain version of every category, and the score only
 * orders what is left: tier {@code n} is unlocked from Strata {@code n}.
 */
public final class LootTiers {

    public static final String BASE_POOL_FILE = "data/loot_pool.json";

    /** Score width of one tier above the base pool; tier n covers one band of this width. */
    public static final int SCORE_PER_TIER = 250;

    private static Set<String> basePool;

    private LootTiers() {
    }

    /** The type names listed in {@link #BASE_POOL_FILE}, loaded once. */
    public static synchronized Set<String> basePool() {
        if (basePool == null) {
            basePool = Collections.unmodifiableSet(load());
        }
        return basePool;
    }

    private static Set<String> load() {
        Set<String> names = new HashSet<>();
        if (Gdx.files == null) {
            return names;
        }
        try {
            FileHandle file = Gdx.files.local("assets/" + BASE_POOL_FILE);
            if (!file.exists()) {
                file = Gdx.files.internal(BASE_POOL_FILE);
            }
            if (!file.exists()) {
                return names;
            }
            JsonValue list = new JsonReader().parse(file).get("basePool");
            for (JsonValue e = (list != null) ? list.child : null; e != null; e = e.next) {
                names.add(e.asString());
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("LootTiers", "Could not read " + BASE_POOL_FILE, e);
            }
        }
        return names;
    }

    /**
     * The tier a template belongs to: its own {@code "tier"} if the data sets one, 0 for the
     * base pool and anything scoring under {@link UnlockManager#UNLOCK_SCORE_THRESHOLD}, else
     * one tier per {@link #SCORE_PER_TIER} of score above it.
     */
    public static int tierOf(String typeName, ItemTemplate t) {
        if (t == null) {
            return 0;
        }
        if (t.tier >= 0) {
            return t.tier;
        }
        if (typeName != null && basePool().contains(typeName)) {
            return 0;
        }
        return tierForScore(UnlockManager.calculateItemScore(t));
    }

    /** Tier by score alone: 0 under the threshold, then one tier per 250 points. */
    public static int tierForScore(int score) {
        if (score < UnlockManager.UNLOCK_SCORE_THRESHOLD) {
            return 0;
        }
        return (score - UnlockManager.UNLOCK_SCORE_THRESHOLD) / SCORE_PER_TIER + 1;
    }

    /** The highest tier the unlock roll may grant after reaching the given stratum. */
    public static int maxTierForStrata(int strataReached) {
        return Math.max(1, strataReached);
    }
}
