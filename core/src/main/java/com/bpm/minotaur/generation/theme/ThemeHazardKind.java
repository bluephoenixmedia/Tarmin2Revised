package com.bpm.minotaur.generation.theme;

/**
 * Contract slot (c): the one environmental mechanic a theme uses to change how
 * the player moves or fights.
 *
 * <p>A hazard must change a decision. Tinted floor tiles are not a hazard.
 */
public enum ThemeHazardKind {

    /** Blood pools slow movement across the arena floor. */
    BLOOD_POOLS,

    /** Water, blood and necrotic muck: wading costs turns and applies effects. */
    LIQUID_FLOOD,

    /** Impassable props break sightlines and force the player around them. */
    BLOCKING_PROPS,

    /** Braziers and campfires burn anything that steps onto them. */
    BURNING_TILES,

    /** Brambles regrow each turn, re-blocking paths behind the player. */
    BRAMBLE_REGROWTH,

    /** Quicksand dunes that slow movement and drag down armor. */
    QUICKSAND_HAZARD,

    /** Rising tide that progressively submerges causeway paths. */
    RISING_TIDE
}
