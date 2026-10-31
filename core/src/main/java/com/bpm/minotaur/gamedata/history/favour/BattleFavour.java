package com.bpm.minotaur.gamedata.history.favour;

/**
 * What a house makes of the player's part in a battle fought for it (Living War A40). Half of it,
 * reversed, reaches its enemies, as all favour does. A player who fled before the rout gets nothing,
 * and never reaches here.
 */
public final class BattleFavour {

    public static final int WON = 5;
    /** The most the player's share of the enemy dead can add, in full at a third. */
    public static final int FULL_SHARE = 15;
    static final float FULL_AT = 1f / 3f;
    public static final int CAPTAIN = 10;
    public static final int LOST_TO_THE_END = 3;
    /** A share of the enemy dead at which a winning side was turned by the player. */
    public static final float TURNED_AT = 1f / 5f;

    private BattleFavour() {
    }

    /**
     * @param won the house the player fought for held the field
     * @param share the player's share of the enemy dead, 0 to 1
     * @param captain the player slew or took a captain
     */
    public static int of(boolean won, float share, boolean captain) {
        if (!won) return LOST_TO_THE_END;
        int fromShare = Math.round(FULL_SHARE * Math.min(1f, Math.max(0f, share) / FULL_AT));
        return WON + fromShare + (captain ? CAPTAIN : 0);
    }

    public static boolean turned(boolean won, float share) {
        return won && share >= TURNED_AT;
    }
}
