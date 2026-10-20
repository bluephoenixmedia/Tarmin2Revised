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
     * The role a kill counts as (#185, decided in #161). Uses the monster's template role
     * (FODDER / SOLDIER / HEAVY), but monsters far below the player yield TRIVIAL (no HP).
     */
    public static Role roleOf(Monster monster, int playerLevel) {
        if (monster == null) return Role.TRIVIAL;
        int delta = monster.getLevel() - playerLevel;
        if (delta <= -TRIVIAL_LEVELS_BELOW) return Role.TRIVIAL;
        com.bpm.minotaur.gamedata.monster.MonsterRole mr = monster.getRole();
        if (mr == com.bpm.minotaur.gamedata.monster.MonsterRole.FODDER) return Role.FODDER;
        if (mr == com.bpm.minotaur.gamedata.monster.MonsterRole.HEAVY || isElite(monster)) return Role.HEAVY;
        return Role.SOLDIER;
    }

    /** Whether a kill was out of depth (#185, #161). */
    public static boolean isOutOfDepth(Monster monster, int playerLevel) {
        if (monster == null) return false;
        return monster.isOutOfDepth() || outOfDepthByLevel(monster.getLevel(), playerLevel);
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

    private static boolean isElite(Monster monster) {
        return monster.isElite();
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
