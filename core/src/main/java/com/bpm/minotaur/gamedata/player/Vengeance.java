package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.telemetry.TelemetryManager;

/**
 * Vengeance: the HP the protagonist takes back with each kill (#178, #190). It scales with
 * the threat of what died, so cutting through a Fodder pack keeps him going without
 * topping him up, and a monster far below him gives nothing.
 */
public final class Vengeance {

    /** The part a monster played in the fight, and the share of max HP its death restores. */
    public enum Role {
        TRIVIAL(0f), FODDER(0.02f), SOLDIER(0.08f), HEAVY(0.20f);

        public final float share;

        Role(float share) {
            this.share = share;
        }
    }

    /** An out-of-depth kill restores this much more, on top of its role. */
    public static final float OUT_OF_DEPTH_BONUS = 0.5f;

    /** A monster this many levels or more below the player restores nothing. */
    static final int TRIVIAL_LEVELS_BELOW = 3;
    /** Fallback: this many levels or more above the player counts as a Heavy. */
    static final int HEAVY_LEVELS_ABOVE = 3;
    /** Fallback: this many levels or more above the player counts as out of depth. */
    static final int OUT_OF_DEPTH_LEVELS_ABOVE = 5;

    private Vengeance() {
    }

    /**
     * The role a kill counts as. Monster roles do not exist yet, so this falls back on the
     * monster's level relative to the player; the difficulty curve (#185) swaps this method.
     */
    public static Role roleOf(Monster monster, int playerLevel) {
        return roleByLevel(monster.getLevel(), playerLevel, isElite(monster));
    }

    /** Whether a kill was out of depth. Same fallback, same seam for #185. */
    public static boolean isOutOfDepth(Monster monster, int playerLevel) {
        return outOfDepthByLevel(monster.getLevel(), playerLevel);
    }

    static Role roleByLevel(int monsterLevel, int playerLevel, boolean elite) {
        int delta = monsterLevel - playerLevel;
        if (delta <= -TRIVIAL_LEVELS_BELOW) return Role.TRIVIAL;
        if (elite || delta >= HEAVY_LEVELS_ABOVE) return Role.HEAVY;
        if (delta < 0) return Role.FODDER;
        return Role.SOLDIER;
    }

    static boolean outOfDepthByLevel(int monsterLevel, int playerLevel) {
        return monsterLevel - playerLevel >= OUT_OF_DEPTH_LEVELS_ABOVE;
    }

    /** Elites and bosses are the templates whose kill XP is lifted above their level's. */
    private static boolean isElite(Monster monster) {
        MonsterTemplate template = monster.getTemplate();
        return template != null && template.xpMultiplier > 1.0f;
    }

    /** HP restored for a kill of {@code role}; any kill worth something restores at least 1. */
    public static int amount(Role role, boolean outOfDepth, int maxHp) {
        if (role.share <= 0f || maxHp <= 0) return 0;
        float share = role.share * (outOfDepth ? 1f + OUT_OF_DEPTH_BONUS : 1f);
        return Math.max(1, Math.round(maxHp * share));
    }

    /** Pays out the Vengeance for {@code monster}'s death. Returns the HP actually restored. */
    public static int claim(Player player, Monster monster) {
        if (player == null || monster == null) return 0;
        int level = player.getLevel();
        return grant(player, roleOf(monster, level), isOutOfDepth(monster, level));
    }

    static int grant(Player player, Role role, boolean outOfDepth) {
        int amount = amount(role, outOfDepth, player.getStats().getMaxHP());
        if (amount <= 0) return 0;
        int before = player.getCurrentHP();
        player.heal(amount);
        int healed = Math.max(0, player.getCurrentHP() - before);
        TelemetryManager.getInstance().recordVengeance(healed);
        return healed;
    }
}
