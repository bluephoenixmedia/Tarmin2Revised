package com.bpm.minotaur.gamedata.gore;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's own blood state: what is on their skin, and what has been splashed at
 * them but not yet settled onto a layer.
 *
 * Settling a splash means finding the topmost piece of the doll under it -- armour,
 * helmet or bare skin -- which needs the doll's layer art and calibration, and those
 * only exist while the inventory is open. So combat records splashes here in canvas
 * space and the paperdoll settles them the next time it is shown. Gear only changes
 * through the inventory, which settles first, so a splash always lands on what was
 * being worn when it happened.
 */
public class PlayerBlood {

    /** Past this, unsettled splashes only darken the skin rather than queueing forever. */
    static final int MAX_PENDING = 800;
    private static final float PENDING_OVERFLOW_SOAK = 0.002f;

    /** Blood on bare skin and the base clothing: the base_father layer and the head. */
    public BloodCoat body = new BloodCoat();
    /** Splashes in master-canvas pixels, awaiting the doll. */
    public ArrayList<BloodStain> pending = new ArrayList<BloodStain>();

    public void splatter(List<BloodStain> stains) {
        for (BloodStain s : stains) {
            if (pending.size() >= MAX_PENDING) {
                body.addSoak(PENDING_OVERFLOW_SOAK);
                continue;
            }
            pending.add(s);
        }
    }

    /** Hands over every unsettled splash and forgets them. */
    public List<BloodStain> drainPending() {
        List<BloodStain> out = new ArrayList<BloodStain>(pending);
        pending.clear();
        return out;
    }

    public void age(int turns) {
        body.age(turns);
        for (BloodStain s : pending) {
            s.age += turns;
        }
    }

    public void clear() {
        body.clear();
        pending.clear();
    }
}
