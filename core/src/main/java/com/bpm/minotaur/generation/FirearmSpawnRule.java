package com.bpm.minotaur.generation;

import com.bpm.minotaur.gamedata.firearm.FirearmProfile;
import com.bpm.minotaur.gamedata.item.Item;

/**
 * Keeps firearms out of the shallows.
 *
 * <p>With their authored damage dice kept, an arquebus rolls 2d8 against a longbow's
 * 1d8. A level-1 character finding one would flatten the early game, so the roster is
 * spread by depth -- the depths themselves live on {@link FirearmProfile} beside each
 * weapon's reload and noise.
 *
 * <p><strong>Why this is a rule of its own.</strong> Those depths are already authored
 * in {@code spawntables.json}'s {@code itemSpawnTable}, a 401-entry, fully level-gated
 * table that no code reads. The obvious alternative, per-variant {@code minLevel}, does
 * not gate either: {@code ItemDataManager.getRandomVariantForItem} falls through to
 * {@code variants.get(0)} when no variant matches the level, so a minimum is silently
 * ignored. Fixing that fallback is the correct repair but would change spawning for
 * every item in the game. This gate is therefore deliberately narrow: it translates
 * between the spawner's string ids and the firearm depths, and affects nothing else.
 */
public final class FirearmSpawnRule {

    private FirearmSpawnRule() {
    }

    /**
     * The shallowest depth this item may be found at; 1 for anything ungated.
     *
     * @param itemId an {@code ItemType} name, since the spawner works in ids.
     */
    public static int minDepth(String itemId) {
        return FirearmProfile.minDepth(parse(itemId));
    }

    /** True if this item may be generated at the given depth. */
    public static boolean canSpawnAtDepth(String itemId, int depth) {
        return depth >= minDepth(itemId);
    }

    private static Item.ItemType parse(String itemId) {
        if (itemId == null) {
            return null;
        }
        try {
            return Item.ItemType.valueOf(itemId);
        } catch (IllegalArgumentException e) {
            return null; // Not a type we know; never our business to block it.
        }
    }
}
