package com.bpm.minotaur.gamedata.player;

/**
 * How much a survival state needs the player's attention: the one graded scale the hunger and
 * thirst stages both report, so the UI maps it to a colour in one place instead of re-deriving
 * it from each meter's stage checks.
 */
public enum Severity {
    /** Nothing to act on. */
    NONE,
    /** Needs attention soon: Hungry, Thirsty. */
    WARNING,
    /** Costing the player now: Weak and worse, Dehydrated and worse. */
    DANGER
}
