package com.bpm.minotaur.ui;

import com.bpm.minotaur.gamedata.monster.Monster;

/**
 * Who shows Arms over their head and how large (Living War A45): a shield over every soldier with a
 * house near the player, a banner over a captain at any distance. Regular spawns have no house, even
 * the guards and knights who are Legion by name, so they show nothing.
 */
public final class ArmsInField {

    /** Wholly seen out to here; fades to nothing over the next stretch. */
    private static final float CLEAR_TILES = 3f;
    private static final float FADE_TILES = 1.5f;

    private ArmsInField() {
    }

    public static boolean bearsArms(Monster m) {
        return m != null && m.isAlive() && m.getHouseId() >= 0;
    }

    public static boolean bearsBanner(Monster m) {
        return bearsArms(m) && m.isStandardBearer();
    }

    /** In whole sizes by the tile, so a shield is drawn pixel for pixel rather than scaled to a blur. */
    public static int shieldSize(float tiles) {
        if (tiles <= 1.5f) return (int) UiTheme.ARMS_SHIELD_NEAR;
        if (tiles <= 2.5f) return (int) UiTheme.ARMS_SHIELD_MID;
        return (int) UiTheme.ARMS_SHIELD_FAR;
    }

    public static float shieldAlpha(float tiles) {
        if (tiles <= CLEAR_TILES) return 1f;
        return Math.max(0f, 1f - (tiles - CLEAR_TILES) / FADE_TILES);
    }
}
