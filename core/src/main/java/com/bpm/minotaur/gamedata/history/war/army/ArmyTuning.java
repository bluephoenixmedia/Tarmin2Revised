package com.bpm.minotaur.gamedata.history.war.army;

/** The army layer's starting values (Living War A3-A5), to re-measure with the war simulation. */
final class ArmyTuning {

    /** A house always keeps this share of its strength at home. */
    static final float GARRISON_SHARE = 0.25f;
    static final int ARMY_SIZE = 15;
    /** Below this a house fields no army. */
    static final int MIN_ARMY = 15;
    /** One host per this much strength. */
    static final int STRENGTH_PER_ARMY = 20;
    /** A vassal's levy is this share of its strength. */
    static final float LEVY_SHARE = 1f / 3f;
    /** What walls are worth when weighing a march on a seat (Living War A30). */
    static final float WALL_EDGE = 4f;
    /** How much distance puts a host off marching on a seat: the farther, the more it takes (tuned against A8). */
    static final float SEAT_DISTANCE = 120f;
    /** Chunks an army, or a seat, can see enemy armies from. */
    static final int SIGHT = 6;
    /** War turns between looks around for a new enemy. */
    static final int SIGHT_EVERY = 10;
    /** War turns between fresh routes after an army being hunted or followed. */
    static final int RETARGET_EVERY = 20;
    /** War turns an army with nowhere to go waits before thinking again. */
    static final int IDLE_EVERY = 25;
    /** War turns between checks for new musters. */
    static final int MUSTER_EVERY = 25;
    /** Morale below which a host goes home, whatever its numbers. */
    static final int RECOVER_MORALE = 40;

    /** Chance per soldier in the line, per turn, of killing one (Living War A20). */
    static final float KILL_RATE = 0.012f;
    /** Soldiers of a side that can strike at once. */
    static final int LINE = 16;
    static final int MORALE_PER_LOSS = 4;
    /** Chance per turn that a captain in a battle falls, and what that costs the side. */
    static final float CAPTAIN_FALL = 0.004f;
    static final int MORALE_CAPTAIN = 20;
    /** A routed captain's odds of being slain, and of being taken. */
    static final float ROUT_SLAIN = 0.30f;
    static final float ROUT_TAKEN = 0.25f;
    /** Turns before a battle that will not break is called for the stronger side. */
    static final int TURN_CAP = 200;
    /** Under this many soldiers in all, a battle is a skirmish. */
    static final int SKIRMISH_UNDER = 20;
    /** A beaten house sues once its strength falls below this share of what it began the war with. */
    static final float SUE_BELOW = 0.5f;

    /** Soldiers a raid or a Hunting Party takes (A11, A12). */
    static final int RAID_SIZE = 8;
    /** War turns between a host's looks at its numbers, and the least worth sending (A10). */
    static final int SUPPLY_EVERY = 60;
    static final int SUPPLY_MIN = 3;
    /** War turns between foraging parties, their size, and how long they stay out (A10). */
    static final int FORAGE_EVERY = 80;
    static final int FORAGE_SIZE = 4;
    static final int FORAGE_TURNS = 40;
    /** Fewer wars than this and another is declared (A9). */
    static final int WAR_FLOOR = 2;
    /** Each Season, a free house's chance of raiding one it bears a grudge against, and the grudge it takes (A9). */
    static final float PEACE_RAID_CHANCE = 0.25f;
    static final float PEACE_RAID_GRUDGE = 1f;
    /** Chance per chunk crossed of losing a soldier to the land: near the maze, and in the far bands (A27). */
    static final float ATTRITION_NEAR = 0.10f;
    static final float ATTRITION_FAR = 0.33f;
    static final int ATTRITION_NEAR_DISTANCE = 11;
    static final int ATTRITION_FAR_DISTANCE = 60;
    static final float BLIGHT_ATTRITION = 3f;
    /** Seasons a Hunting Party stays out. */
    static final int HUNT_SEASONS = 2;

    /** Seasons a herald has to reach the seat it walks to, and the most a Season's news sends. */
    static final int HERALD_SEASONS = 2;
    static final int HERALDS_A_SEASON = 2;

    private ArmyTuning() {
    }
}
