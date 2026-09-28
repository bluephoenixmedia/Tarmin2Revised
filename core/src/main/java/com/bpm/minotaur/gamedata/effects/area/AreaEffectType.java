package com.bpm.minotaur.gamedata.effects.area;

/**
 * What an {@link AreaEffectManager} tile is doing to whatever stands in it.
 *
 * <p>One entry per kind of lingering cloud. Six spells in {@code spells.json} want this --
 * Cloudkill, Stinking Cloud, Incendiary Cloud, Sleet Storm, Darkness and Fog Cloud -- and only
 * the first of them is built. The rest become a constant here plus a hook in the turn tick,
 * rather than each one reinventing a tile grid.
 */
public enum AreaEffectType {

    /**
     * Heavily obscured: sight does not cross it, in either direction.
     *
     * <p>Fog Cloud. Blocks acquisition and ranged attacks; does not block hearing, movement or
     * a melee swing from an adjacent square.
     */
    OBSCURING
}
