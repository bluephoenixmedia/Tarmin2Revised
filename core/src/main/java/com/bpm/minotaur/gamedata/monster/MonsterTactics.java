package com.bpm.minotaur.gamedata.monster;

/**
 * What a monster that can shoot does on its turn. Ordinary archers and spitters
 * loose a shot across the room but keep walking in to fight the player face to
 * face, and a shot costs them a turn of approach. Only an elite skirmisher --
 * cunning and seasoned both -- holds the player at arm's length and backs away
 * when crowded.
 */
public final class MonsterTactics {

    public enum Move { SHOOT, RETREAT, ADVANCE }

    /** Intelligence a monster needs before it fights at range on purpose. */
    public static final int ELITE_INTELLIGENCE = 14;

    /** Level a monster needs before it fights at range on purpose. */
    public static final int ELITE_LEVEL = 10;

    /** At this distance or nearer, everything but an elite closes to melee. */
    public static final int MELEE_CLOSING_DISTANCE = 2;
    /**
     * After a shot, a monster spends this many of its own actions moving before it may shoot again
     * (#255): a shot is an event, not a stream. It was 1; depth-1 archers wore a fresh body down
     * on the approach.
     */
    public static final int SHOT_WAIT_ACTIONS = 2;

    /** An elite backs away while the player is nearer than this. */
    public static final int ELITE_STANDOFF_DISTANCE = 3;

    private MonsterTactics() {
    }

    public static boolean isEliteSkirmisher(Monster monster) {
        return monster != null
                && monster.hasRangedAttack()
                && monster.getIntelligence() >= ELITE_INTELLIGENCE
                && monster.getLevel() >= ELITE_LEVEL;
    }

    /**
     * @param distance    tiles between monster and player, on the grid
     * @param canShoot    the player is within range, lined up, and in sight
     */
    public static Move decide(Monster monster, int distance, boolean canShoot) {
        if (monster == null || !monster.hasRangedAttack()) {
            return Move.ADVANCE;
        }
        if (isEliteSkirmisher(monster)) {
            if (distance < ELITE_STANDOFF_DISTANCE) {
                return Move.RETREAT;
            }
            return canShoot && monster.getRangedShotCooldown() <= 0 ? Move.SHOOT : Move.ADVANCE;
        }
        if (canShoot && distance > MELEE_CLOSING_DISTANCE && monster.getRangedShotCooldown() <= 0) {
            return Move.SHOOT;
        }
        return Move.ADVANCE;
    }
}
