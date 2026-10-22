package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.Nemesis;
import com.bpm.minotaur.gamedata.monster.Monster;

/**
 * Puts a Nemesis of the history onto a monster of its kind (#283): its name and epithet, and a
 * level and a quarter more hit points for every life it took.
 */
public final class NemesisForge {

    /** Extra hit points per life taken, as a share of the body's own. */
    static final float HP_PER_LIFE = 0.25f;

    private NemesisForge() {
    }

    public static void dress(Monster m, Nemesis n) {
        m.setNemesisId(n.id);
        if (n.figureId >= 0) m.setFigureId(n.figureId);
        m.setDisplayName(n.title());
        int lives = n.tally();
        m.setLevel(m.getLevel() + lives);
        int hp = Math.round(m.getMaxHP() * (1f + HP_PER_LIFE * lives));
        m.setMaxHP(hp);
        m.setCurrentHP(hp);
    }
}
