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

    private ArmyTuning() {
    }
}
